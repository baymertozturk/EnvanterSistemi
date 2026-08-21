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
| Kubernetes manifestleri (Deployment/Service/ConfigMap/probe) | ✅ Tamamlandı |
| `kubectl apply -f k8s/` ile deploy (8 Deployment + 8 Service) | ✅ Çalışıyor |
| Uçtan uca sipariş akışı (Kubernetes içinde) | ✅ Geçti |
| Pod silme → otomatik yeniden oluşturma | ✅ Doğrulandı (~12 sn) |
| Konteyner çökmesi → otomatik restart | ✅ Doğrulandı (~14 sn) |

Bu çalışma sırasında **4 adet gerçek hata** tespit edilip düzeltildi (§6). Bunlardan ikisi
sistemin konteyner ortamında hiç çalışmamasına, biri saga akışının ortada takılmasına
sebep oluyordu. Kubernetes doğrulaması sırasında ayrıca **düzeltilmemiş bir yarış durumu**
(race condition) ortaya çıktı — ayrıntısı ve önerilen çözümü §11'de.

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
| Kubernetes | kind v0.30.0 → cluster v1.34.0 |
| kubectl | v1.36.1 |

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
- **Zipkin bellek taşması:** Zipkin trace'leri varsayılan olarak bellekte ve **sınırsız**
  saklar. 16 saat kesintisiz çalışmanın ardından `OutOfMemoryError: Java heap space` ile
  çöktü (uygulama servisleri etkilenmedi, yalnızca tracing durdu). `JAVA_OPTS=-Xmx512m`,
  `MEM_MAX_SPANS=100000`, `restart: unless-stopped` ve bir healthcheck eklendi.

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

## 9. Kubernetes Dağıtımı

`k8s/` klasörü altında her servis için Deployment + Service, ortak ayarlar için ConfigMap
ve kimlik bilgileri için Secret tanımlandı. Ayrıntılı kullanım: [k8s/README.md](k8s/README.md)

### 9.1 Cluster

Sistemde minikube veya kind kurulu değildi; Docker Desktop'ın gömülü Kubernetes'i de hiç
etkinleştirilmemişti. **kind v0.30.0** kuruldu ve tek düğümlü bir cluster oluşturuldu.

| Bileşen | Değer |
|---|---|
| Cluster aracı | kind v0.30.0 |
| Kubernetes | v1.34.0 |
| Düğüm | `supply-chain-control-plane` (tek düğüm, control-plane) |
| Namespace | `supply-chain` |

### 9.2 Kaynaklar

`kubectl apply -f k8s/` ile **8 Deployment + 8 Service** oluşturuldu:

| Kaynak | Tür | Not |
|---|---|---|
| postgres | Deployment + Service | 4 veritabanı init ConfigMap'i ile oluşturuluyor |
| redis | Deployment + Service | |
| kafka | Deployment + Service | KRaft, tek broker |
| zipkin | Deployment + Service | tracing collector |
| order / inventory / payment / notification-service | Deployment + Service | ClusterIP |

**Veritabanı doğrulaması:** ConfigMap içindeki init script çalıştı, dört veritabanı da
oluştu (`orders_db`, `inventory_db`, `payments_db`, `notifications_db`).

### 9.3 Konfigürasyon Yönetimi

Ortam değişkenleri iki kaynaktan geliyor:

- **ConfigMap `supply-chain-config`** — hassas olmayan değerler: `POSTGRES_HOST`,
  `POSTGRES_PORT`, `SPRING_KAFKA_BOOTSTRAP_SERVERS` (`kafka:9092`),
  `SPRING_DATA_REDIS_HOST`, `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT`, probe ayarları.
  Servislere `envFrom` ile toplu olarak veriliyor.
- **Secret `supply-chain-secret`** — `POSTGRES_USER` / `POSTGRES_PASSWORD`.
  Parolaların ConfigMap'te durmaması için ayrıldı.

Adresler k8s Service DNS adlarıyla (`postgres`, `kafka`, `redis`) verildiği için
docker-compose'daki host adları birebir karşılanıyor; imaj içindeki `application.yml`
değerleri `SPRING_*` env değişkenleriyle eziliyor.

> **Dikkat edilen nokta:** JDBC URL'i
> `jdbc:postgresql://$(POSTGRES_HOST):$(POSTGRES_PORT)/orders_db` şeklinde kuruluyor.
> Kubernetes'in `$(VAR)` yerine koyma özelliği **yalnızca aynı `env` listesinde daha önce
> tanımlanmış** değişkenleri görür — `envFrom` ile gelenleri görmez. Bu yüzden
> `POSTGRES_HOST` / `POSTGRES_PORT`, `envFrom`'a ek olarak `configMapKeyRef` ile açıkça
> tekrar tanımlandı.

### 9.4 Health Probe'ları

Her mikroservis üç prob kullanıyor, hepsi `/actuator/health` altında:

| Prob | Yol | Periyot | Başarısız olursa |
|---|---|---|---|
| `startupProbe` | `/actuator/health/readiness` | 5 sn × 60 deneme | Bekler; JVM açılışına süre tanır |
| `readinessProbe` | `/actuator/health/readiness` | 10 sn | Service endpoint'lerinden çıkarılır, **öldürülmez** |
| `livenessProbe` | `/actuator/health/liveness` | 15 sn | Konteyner **yeniden başlatılır** |

Liveness için düz `/actuator/health` yerine bilinçli olarak `/actuator/health/liveness`
seçildi. Düz uç bağımlılıkların (DB, Redis, Kafka) durumunu da içerir; Redis birkaç saniye
düşse **sağlıklı** pod'lar restart döngüsüne girerdi. `/liveness` yalnızca uygulamanın
kendi canlılık durumunu yansıtır. Alt uçların açık olması için ConfigMap'te
`MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED: "true"` verildi.

Dört servisin tamamında her iki uç da doğrulandı:

```
order-service          liveness={"status":"UP"} readiness={"status":"UP"}
inventory-service      liveness={"status":"UP"} readiness={"status":"UP"}
payment-service        liveness={"status":"UP"} readiness={"status":"UP"}
notification-service   liveness={"status":"UP"} readiness={"status":"UP"}
```

**Kafka probe'unda çözülen kilitlenme:** İlk tanımda readiness probe'u
`kafka-topics.sh --bootstrap-server localhost:9092 --list` çalıştırıyordu ve pod hiçbir
zaman `Ready` olmuyordu. Sebep: broker, bootstrap sonrası metadata olarak *advertised
listener*'ı (`kafka:9092` = Service ClusterIP) döndürüyor; pod henüz `Ready` olmadığı için
Service'in endpoint listesi boş ve çağrı timeout oluyor — prob kendi `Ready` olmasını
bekliyor. `tcpSocket: 9092` ile değiştirildi (port dinlemeye zaten "Enabling request
processing" aşamasından sonra başlıyor).

---

## 10. Kubernetes Doğrulama Testleri

### 10.1 Uçtan uca sipariş akışı (cluster içinde)

`POST /orders` → MacBook Pro 16", `quantity: 4`

| Gözlem | Sonuç |
|---|---|
| Sipariş durumu | `PENDING` → `PAYMENT_COMPLETED` (< 3 sn) |
| Stok | 50/0 → **46/4**, `version` 0 → 1 |
| notification-service | "Siparişiniz hazırlandı" bildirimi alındı |
| Tracing | `traceId` servisler arasında korundu |

### 10.2 Stok yetersiz senaryosu (cluster içinde)

Apple Watch Ultra (stok 75), `quantity: 9999` → durum **`FAILED`**, stok değişmedi
(75/0, `version` 0).

### 10.3 Pod'u kasıtlı silme — otomatik yeniden oluşturma

```
SILINECEK POD:  order-service-8c5dd7f87-jzcfm   (09:15:01)
YENI POD HAZIR: order-service-8c5dd7f87-kftmv   (09:15:13)
```

Kubernetes olayları (`kubectl get events`):

```
Normal  Killing           pod/order-service-8c5dd7f87-jzcfm    Stopping container order-service
Normal  SuccessfulCreate  replicaset/order-service-8c5dd7f87   Created pod: order-service-8c5dd7f87-kftmv
Normal  Scheduled         pod/order-service-8c5dd7f87-kftmv    Successfully assigned to supply-chain-control-plane
Normal  Pulled            pod/order-service-8c5dd7f87-kftmv    Image "supply-chain-order-service:latest" already present on machine
```

**Sonuç:** ReplicaSet controller, `replicas: 1` beklentisini korumak için pod'u anında
yeniden oluşturdu; **~12 saniyede** `1/1 Running` oldu. Deployment `1/1 AVAILABLE`
durumuna döndü. Yeni pod üzerinden gönderilen sipariş başarıyla işlendi.

Olaylarda görünen `Startup probe failed: connection refused` uyarıları beklenen
davranıştır: JVM henüz portu dinlemeye başlamamışken prob deneme yapar. `startupProbe`
tam da bunun için vardır — bu süre boyunca liveness devreye girmez ve pod boşuna
öldürülmez.

### 10.4 Konteyner çökmesi — otomatik restart

Pod silmekten farklı olarak, konteyner içindeki JVM (PID 1) öldürüldü:

```
ÖNCE:  notification-service-75ccf8f777-pr7tq  1/1  Running  restarts=0  age=5m19s
SONRA: notification-service-75ccf8f777-pr7tq  0/1  Running  restarts=1  age=5m25s
       notification-service-75ccf8f777-pr7tq  1/1  Running  restarts=1  age=5m35s
```

**Pod adı ve yaşı aynı kaldı, `RESTARTS` 0 → 1 oldu** — yani yeni bir pod oluşturulmadı,
kubelet aynı pod içindeki konteyneri yeniden başlattı (~14 sn). İki mekanizmanın farkı:

| Senaryo | Mekanizma | Pod adı | RESTARTS |
|---|---|---|---|
| Pod silme | ReplicaSet controller yeni pod oluşturur | **değişir** | 0 (yeni pod) |
| Konteyner çökmesi / liveness hatası | kubelet konteyneri yeniden başlatır | aynı kalır | **artar** |

### 10.5 Son durum

8 pod'un tamamı `1/1 Running`. Yalnızca notification-service'te `RESTARTS=1` var — o da
§10.4'teki kasıtlı testten kaynaklanıyor.

---

## 11. Yeni Tespit Edilen Hata: Saga Durum Yarışı (Race Condition)

Kubernetes testleri sırasında, ödeme simülasyonunun **%20 rastgele başarısızlık**
ihtimaline denk gelen bir sipariş hatalı bir son durumda kaldı.

**Beklenen:** Ödeme başarısız → sipariş `FAILED`, stok telafi edilir.
**Gerçekleşen:** Stok doğru şekilde telafi edildi (200/0, `version` 2), ancak sipariş
`STOCK_RESERVED` durumunda takılı kaldı.

Loglar sebebi net gösteriyor — **aynı milisaniyede, iki farklı listener thread'i**:

```
06:16:51.343         PaymentFailedEvent alındı              (thread: ...Container#3-0-C-1)
06:16:51.345         StockReservedEvent alındı              (thread: ...Container#0-0-C-1)
06:16:51.421633534   yeniDurum=FAILED (ödeme başarısız)     (thread #3)
06:16:51.421633534   yeniDurum=STOCK_RESERVED               (thread #0)
```

`payment-failed` işlenip sipariş `FAILED` yapıldıktan sonra, geç kalan `stock-reserved`
olayı aynı kaydın üzerine yazdı ve **terminal durumu ara duruma geri döndürdü**.

**Kök sebep:** `OrderEventConsumer` içindeki dört listener (`stock-reserved`,
`stock-rejected`, `payment-completed`, `payment-failed`) bağımsız thread'lerde çalışıyor ve
her biri koşulsuz olarak `order.setStatus(X); save()` yapıyor. Ayrıca `Order` entity'sinde
`@Version` alanı **yok** — yani optimistic locking de devrede değil (karşılaştırma:
`Product` entity'sinde var ve düzgün çalışıyor). Sonuç klasik bir *lost update*:
son yazan kazanıyor.

Bu hata Kubernetes'e özgü değildir; docker-compose ortamında da oluşabilir. Yalnızca %20'lik
ödeme hatasının olay sıralamasıyla çakışması gerektiği için seyrek görülür.

**Önerilen düzeltme** (bu çalışmanın kapsamı dışında bırakıldı; saga semantiğine dair bir
tasarım kararı gerektiriyor):

1. **Durum geçiş koruması:** Terminal durumdaki (`FAILED`, `PAYMENT_COMPLETED`) bir sipariş
   daha erken aşamalı bir duruma geri döndürülmemeli. En küçük ve en güvenli düzeltme budur.
2. **`@Version` ile optimistic locking:** `Order` entity'sine eklenip çakışmada retry.
3. **Sıralama garantisi:** Tüm saga olaylarını tek topic'te veya `orderId` partition key'i
   ile tek listener üzerinden işlemek.

---

## 12. Öneriler (bu çalışmanın kapsamı dışında)

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

## 13. Sonuç

Sistem üç ortamda da uçtan uca doğru çalışıyor:

- **docker compose** — tek komutla sıfırdan ayağa kalkıyor, başarılı ve telafi
  senaryolarının ikisi de geçiyor.
- **GitHub Actions** — 30 test + 4 Docker imaj build'i, 5/5 job yeşil.
- **Kubernetes (kind)** — 8 Deployment + 8 Service, ConfigMap/Secret ile
  yapılandırma, `/actuator/health` tabanlı üç katmanlı prob yapısı. Pod silindiğinde
  ReplicaSet ~12 saniyede yenisini oluşturuyor; konteyner çöktüğünde kubelet ~14 saniyede
  yeniden başlatıyor.

Java 25 geçişi tamamlandı. Bu geçişin Spring Boot yükseltmesini zorunlu kıldığı ve mevcut
kodda saga akışını tamamen kesen bir Kafka serileştirme hatası bulunduğu tespit edilip
giderildi (§6).

**Açık konular:**

1. **Saga durum yarışı (§11)** — düzeltilmedi. Ödeme başarısızlığı ile `stock-reserved`
   olayının çakıştığı seyrek durumda sipariş yanlış durumda kalıyor. `replicas > 1`
   yapılmadan önce mutlaka giderilmeli; ölçeklendirme bu hatayı daha görünür kılar.
2. **Proje yolundaki Türkçe karakterler (§7)** — yerel çoklu servis Docker build'ini
   kırıyor. CI ve Kubernetes bu kısıttan etkilenmiyor.
3. **Poison-pill koruması ve kullanılmayan `COMPLETED` durumu (§12)** — öneri düzeyinde.
