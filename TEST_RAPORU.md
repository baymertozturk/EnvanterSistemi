# Test Raporu — Dağıtık Sipariş & Envanter Yönetim Sistemi

**Tarih:** 21 Ağustos 2026
**Kapsam:** Java 25 geçişi, konteynerleştirme (Dockerfile + Compose), CI pipeline ve uçtan uca sipariş akışı doğrulaması
**Repo:** https://github.com/baymertozturk/EnvanterSistemi

---

## 1. Özet

| Aşama | Sonuç |
|---|---|
| Java 17 → 25 geçişi (dokümantasyon + build) | ✅ Tamamlandı |
| 4 servis için multi-stage Dockerfile | ✅ Tamamlandı |
| `docker compose up --build` ile tek komutla ayağa kaldırma | ✅ Çalışıyor (bkz. §7 yol kısıtı) |
| Uçtan uca sipariş akışı (başarılı senaryo) | ✅ Geçti |
| Uçtan uca sipariş akışı (stok yetersiz / telafi) | ✅ Geçti |
| Birim + entegrasyon testleri (30 test) | ✅ 0 hata |
| GitHub Actions pipeline | ✅ Yeşil (5/5 job) |

Bu çalışma sırasında **4 adet gerçek hata** tespit edilip düzeltildi (§6). Bunlardan ikisi
sistemin konteyner ortamında hiç çalışmamasına, biri saga akışının ortada takılmasına
sebep oluyordu.

---

## 2. Test Ortamı

| Bileşen | Sürüm |
|---|---|
| Java (runtime + build) | Eclipse Temurin **25** (class file major 69) |
| Spring Boot | **3.5.6** (3.3.5'ten yükseltildi — §6.1) |
| Docker Engine | 29.7.2 (Docker Desktop, Windows 10) |
| Docker Compose | v5.3.1 |
| Maven | 3.9.11 (konteyner içi), 3.9.16 (host) |
| PostgreSQL / Redis / Kafka | 16-alpine / 7-alpine / apache-kafka 3.8.0 (KRaft) |
| CI runner | ubuntu-latest (GitHub Actions) |

---

## 3. Birim ve Entegrasyon Testleri

`mvn clean test` — JDK 25 konteyneri içinde ve CI'da çalıştırıldı.

| Modül | Test | Hata | Atlanan |
|---|---:|---:|---:|
| order-service | 15 | 0 | 3¹ |
| inventory-service | 13 | 0 | 0 |
| payment-service | 1 | 0 | 0 |
| notification-service | 1 | 0 | 0 |
| **Toplam** | **30** | **0** | **3¹** |

**Sonuç:** `BUILD SUCCESS` (yerel çalışma süresi 01:43 dk)

¹ Atlanan 3 test, Testcontainers tabanlı `OrderServiceIntegrationTest` testleridir.
Yerel çalıştırmada Maven konteyner içinde koştuğu için Testcontainers Docker soketine
ulaşamadı ve testler `@Testcontainers(disabledWithoutDocker = true)` gereği SKIPPED oldu.
**CI'da (ubuntu-latest, Docker yerel) bu 3 test gerçekten çalışır ve geçer** — nitekim ilk
CI çalıştırmasında bu testlerden biri patladı ve gerçek bir hata ortaya çıkardı (§6.4).

Kod kapsamı (JaCoCo) her modülde `target/site/jacoco/index.html` altında üretiliyor.

---

## 4. Docker İmajları

Her servis için multi-stage build: `maven:3.9.11-eclipse-temurin-25-alpine` ile derleme →
`eclipse-temurin:25-jre-alpine` üzerine yalnızca jar kopyalanıyor. Runtime aşamasında
root olmayan `appuser` kullanılıyor.

| İmaj | Boyut |
|---|---:|
| supply-chain-order-service | 470 MB |
| supply-chain-inventory-service | 470 MB |
| supply-chain-payment-service | 467 MB |
| supply-chain-notification-service | 467 MB |

Karşılaştırma: build aşamasının temel imajı tek başına 452 MB — yani builder katmanı
son imaja taşınmıyor, multi-stage amacına ulaşıyor.

---

## 5. Uçtan Uca Sipariş Akışı

Tüm sistem `docker compose up --build` ile **sıfırdan** (volume'lar silinerek) ayağa
kaldırıldı: 11 konteyner (4 mikroservis + Postgres, Redis, Kafka, Kafka UI, Prometheus,
Grafana, Zipkin). 4 servisin tamamı ~20 saniyede `/actuator/health` üzerinden `UP` döndü.

### 5.1 Başarılı Senaryo (happy path)

**İstek:** `POST /orders` → `{"customerId":"CUST-E2E-001", "productId":"a1b2c3d4-…", "quantity":3}`

| Adım | Servis | Gözlem |
|---|---|---|
| 1 | order-service | Sipariş oluştu, durum `PENDING`, `order-created` event'i yayınlandı |
| 2 | inventory-service | Stok rezerve edildi → `stock-reserved` |
| 3 | order-service | Durum `STOCK_RESERVED` |
| 4 | payment-service | Ödeme `SUCCESS`, Redis'e idempotency kaydı (TTL 24s) → `payment-completed` |
| 5 | order-service | Durum `PAYMENT_COMPLETED` |
| 6 | notification-service | "Siparişiniz hazırlandı" bildirimi (simülasyon) |

**Stok doğrulaması:** MacBook Pro 16" — `availableStock` 50 → **47**, `reservedStock` 0 → **3**,
`version` 0 → **1** (JPA optimistic locking çalışıyor).

**Süre:** Sipariş oluşturmadan `PAYMENT_COMPLETED`'a kadar < 3 saniye.

> **Not:** `OrderStatus` enum'unda `COMPLETED` değeri tanımlı ancak kodun hiçbir yerinde
> atanmıyor. Dolayısıyla başarılı akışın fiilî son durumu `PAYMENT_COMPLETED`. Bu bir akış
> hatası değil, tamamlanmamış bir tasarım adımı (§8).

### 5.2 Başarısız Senaryo (stok yetersiz)

**İstek:** `POST /orders` → Apple Watch Ultra (stok 75), `quantity: 9999`

| Adım | Gözlem |
|---|---|
| 1 | inventory-service stok yetersizliğini tespit etti → `stock-rejected` |
| 2 | order-service durumu **`FAILED`** yaptı (< 3 sn) |
| 3 | notification-service "Stok tükendi" bildirimi gönderdi, sebep mesajında `mevcut=75, istenen=9999` |
| 4 | **Stok değişmedi:** 75/0, `version` 0 — hatalı rezervasyon sızıntısı yok |

### 5.3 Gözlemlenebilirlik

- **Prometheus:** 4 servisin tamamı `health: up` olarak scrape ediliyor (`/actuator/prometheus`).
- **Distributed tracing:** `traceId` servisler arasında korunuyor. Örnek: başarılı akışta
  payment-service ve notification-service logları aynı `traceId` (`6a8739d69a6653ff…`) taşıyor.
- **Yapılandırılmış loglama:** Tüm servisler JSON formatında, `serviceName` ve `orderId`
  (MDC) alanlarıyla log üretiyor.
- Kafka UI (8080), Grafana (3000), Zipkin (9411) erişilebilir.

**Kararlılık:** Sistem 6 saat kesintisiz ayakta kaldı, her iki sipariş de doğru son
durumunu korudu.

---

## 6. Tespit Edilen ve Düzeltilen Hatalar

### 6.1 Java 25 + Spring Boot 3.3.5 uyumsuzluğu — *servisler hiç ayağa kalkmıyordu*

Java 25 sınıf dosyalarını (major version 69) Spring Boot 3.3.5'in içindeki ASM sürümü
okuyamıyor. Konteynerdeki her servis başlangıçta çöküyordu:

```
Unsupported class file major version 69
BeanDefinitionStoreException: Incompatible class format ... OrderRepository.class
```

**Düzeltme:** Spring Boot parent 3.3.5 → **3.5.6**. (İlginç şekilde
`spring-boot-maven-plugin.version` zaten 3.5.6 idi; sadece parent geride kalmıştı.)
Java 25'e geçiş, Spring Boot yükseltmesi olmadan tamamlanamıyor.

### 6.2 Geçersiz Docker temel imajı

Tüm Dockerfile'lar `maven:3.9.9-eclipse-temurin-25-alpine` imajını kullanıyordu; bu etiket
Docker Hub'da **mevcut değil** (`not found`). **Düzeltme:** `maven:3.9.11-eclipse-temurin-25-alpine`.

### 6.3 `ADD_TYPE_INFO_HEADERS=false` — *saga `STOCK_RESERVED`'da takılıyordu*

En kritik hata. payment-service'in producer yapılandırmasında:

```java
props.put(JsonSerializer.TYPE_MAPPINGS, "PaymentCompletedEvent:…");
props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);   // ← hata
```

`ADD_TYPE_INFO_HEADERS=false`, `__TypeId__` header'ını **tamamen kapatır** — oysa
`TYPE_MAPPINGS` tam da o header'a kısa tip adını yazan mekanizmadır. İkisi birlikte
kullanılamaz. Sonuç: order-service `payment-completed` event'ini deserialize edemedi:

```
IllegalStateException: No type information in headers and no default type provided
RecordDeserializationException: Error deserializing VALUE for partition payment-completed-0 at offset 0
```

Ödeme başarıyla işleniyor ama sipariş sonsuza dek `STOCK_RESERVED` durumunda kalıyordu.
inventory-service aynı ayara sahip olmadığı için `stock-reserved` adımı çalışıyor,
bu da hatayı ilk bakışta gizliyordu.

**Düzeltme:** Satır payment-service ve notification-service producer'larından kaldırıldı.
(notification-service terminal bir tüketici olsa da `@RetryableTopic` retry/DLT kayıtlarını
aynı producer üzerinden yeniden yayınlıyor — aynı hata oradan da vururdu.)

### 6.4 Kafka entegrasyon testinde sıra bağımlılığı — *sadece CI'da görünen hata*

`shouldPublishOrderCreatedEventToRealKafka` topic'teki **ilk** mesajı okuyup kendi
siparişiyle karşılaştırıyordu. `order-created` topic'i aynı sınıftaki diğer testlerle
paylaşıldığı ve consumer `earliest` offset'ten okuduğu için, önce çalışan testin event'i
geliyordu:

```
AssertionFailedError: Event'teki orderId, siparişin ID'si ile eşleşmeli
  ==> expected: <eeec08cd-…> but was: <d4c928ff-…>
```

Bu hata yerelde hiç görünmedi çünkü Testcontainers testleri Docker'a erişemeyip SKIPPED
oluyordu; CI'da ilk kez gerçekten çalıştı. **Düzeltme:** Test artık poll edilen tüm
kayıtlar arasından kendi siparişine ait event'i arıyor (sıra bağımsız).

### 6.5 Diğer düzeltmeler

- **notification-service Redis bağlantısı:** compose'da Redis env değişkenleri eksikti;
  servis `localhost:6379`'a bağlanmaya çalışıp `/actuator/health` üzerinden `DOWN`
  dönüyordu. `SPRING_DATA_REDIS_HOST/PORT` ve `depends_on: redis` eklendi.
- **compose `version` alanı:** artık geçersiz (uyarı üretiyordu), kaldırıldı.

---

## 7. Bilinen Kısıt: Proje Yolundaki Türkçe Karakterler

Proje dizini `…\Dağıtık Sipariş Yönetim Sistemi` adını taşıyor. Docker Compose v5, çoklu
servis build'ini **bake** oturumuyla yürütüyor ve bu oturumun paylaşılan anahtarını dizin
adından türetiyor. ASCII olmayan karakterler HTTP header'ına yazılamadığı için build
başarısız oluyor:

```
failed to dial gRPC: header key "x-docker-expose-session-sharedkey"
contains value with non-printable ASCII characters
```

Bu bir proje hatası değil, ortam kısıtı — tek servis build'i (`docker compose build
order-service`) sorunsuz çalışıyor, 2+ servis birlikte build edildiğinde patlıyor.
GitHub Actions'ta yol ASCII olduğu için CI hiç etkilenmiyor.

**Bu testlerde kullanılan geçici çözüm:** ASCII bir junction üzerinden çalıştırmak.

```powershell
# Bir kez:
New-Item -ItemType Junction -Path C:\sc-build -Target "C:\Users\mert\Desktop\Dağıtık Sipariş Yönetim Sistemi"
# Sonra:
docker compose -f C:\sc-build\docker-compose.yml up --build
```

**Kalıcı çözüm (önerilir):** Proje klasörünü ASCII bir yola taşımak
(ör. `C:\Users\mert\Desktop\supply-chain`). O zaman `docker compose up --build` hiçbir ek
ayar olmadan çalışır.

---

## 8. CI/CD Pipeline

`.github/workflows/ci.yml` — her push ve PR'da çalışır.

| Job | İçerik | Sonuç |
|---|---|---|
| `test` | JDK 25 kurulumu + `mvn clean test` (30 test) | ✅ success |
| `build-docker` (×4) | Her servis için multi-stage Docker image build | ✅ success |

**Son çalıştırma:** [run 32428331889](https://github.com/baymertozturk/EnvanterSistemi/actions/runs/32428331889) — **5/5 job yeşil**.
Tüm çalıştırmalar: [Actions sekmesi](https://github.com/baymertozturk/EnvanterSistemi/actions)

Pipeline'a ayrıca eklenenler:

- **Hata görünürlüğü:** Test adımı patlarsa surefire raporları hem `::error::` annotation'ı
  hem de artifact olarak yayınlanıyor. (§6.4'teki hata tam olarak bu sayede, log indirme
  yetkisi olmadan teşhis edildi.)
- Action sürümleri güncellendi: `checkout@v5`, `setup-java@v5`, `build-push-action@v6`
  (Node.js 20 deprecation uyarıları giderildi).

Docker Hub'a push yapılmıyor — istendiği gibi yalnızca build'in başarılı olması yeterli.

---

## 9. Öneriler (bu çalışmanın kapsamı dışında)

1. **Poison-pill koruması yok.** Deserialize edilemeyen tek bir mesaj, consumer'ı sonsuz
   döngüye sokup o topic'i tamamen kilitliyor (§6.3'te birebir yaşandı — offset 0'ı geçemedi).
   Spring'in kendi hata mesajı da bunu öneriyor: value deserializer'ı
   `ErrorHandlingDeserializer` ile sarmalayıp bozuk kayıtların DLT'ye düşmesini sağlamak.
   `@RetryableTopic` altyapısı zaten mevcut olduğu için maliyeti düşük.
2. **`OrderStatus.COMPLETED` hiç kullanılmıyor.** Ya akışa son bir adım eklenmeli
   (ör. kargo/teslimat onayı) ya da enum'dan kaldırılmalı.
3. **Testcontainers testleri yerelde sessizce atlanıyor.** `disabledWithoutDocker = true`
   geliştirici makinesinde pratik, ancak §6.4'teki hatanın CI'ya kadar fark edilmemesine
   sebep oldu. En azından build sonunda "N entegrasyon testi atlandı" uyarısı verilmesi
   faydalı olur.
4. **Docker imajları 467-470 MB.** JRE yerine `jlink`/`jdeps` ile özelleştirilmiş runtime
   veya Spring Boot layered jar kullanılarak ciddi ölçüde küçültülebilir.

---

## 10. Sonuç

Sistem, tek komutla sıfırdan ayağa kalkıyor ve hem başarılı hem de telafi (compensation)
senaryolarında uçtan uca doğru çalışıyor. Java 25 geçişi tamamlandı; ancak bu geçişin
Spring Boot yükseltmesini zorunlu kıldığı ve mevcut kodda saga akışını kesen bir Kafka
serileştirme hatası bulunduğu tespit edilip giderildi. CI pipeline'ı 5/5 job ile yeşil.

Açık tek konu, proje klasörünün yolundaki Türkçe karakterlerin yerel çoklu-servis Docker
build'ini kırması (§7); CI'da böyle bir sorun yok.
