# Dağıtık Sipariş & Envanter Yönetim Sistemi
## Proje Teknik Dökümanı & Yol Haritası

> Bu doküman, projeyi baştan sona (mimari, teknoloji yığını, geliştirme ortamı, kod yapısı ve haftalık yol haritası) tanımlar. AI destekli kod üretim araçlarına (vibe coding) bağlam olarak verilebilir.

---

## 1. Proje Amacı

E-ticaret / tedarik zinciri senaryosunu simüle eden, birbirinden bağımsız **4 mikroservisten** oluşan, Kafka ile olay güdümlü (event-driven) haberleşen bir sipariş-envanter yönetim platformu.

**Öğrenme hedefleri:**
- Mikroservis mimarisi ve servisler arası iletişim (senkron + asenkron)
- Kafka ile event-driven architecture
- Saga pattern (distributed transaction yönetimi)
- Idempotency, retry, eventual consistency
- Redis ile caching ve distributed locking
- Docker + Kubernetes ile container orkestrasyon
- Observability (loglama, metrik, izleme)

---

## 2. Mimari Genel Bakış

```
                        ┌─────────────────┐
                        │   API Gateway    │  (opsiyonel, Spring Cloud Gateway)
                        └────────┬─────────┘
                                 │
        ┌────────────────────────┼─────────────────────────┐
        │                        │                          │
┌───────▼────────┐     ┌─────────▼────────┐      ┌──────────▼────────┐
│  order-service  │     │ inventory-service │      │  payment-service   │
│  (REST API)     │     │  (REST + Kafka)   │      │  (Kafka consumer)  │
└───────┬────────┘     └─────────┬────────┘      └──────────┬────────┘
        │                        │                          │
        │        ┌───────────────▼──────────────┐           │
        └───────►│         Kafka Cluster          │◄─────────┘
                 │  Topics: order-created,        │
                 │  stock-reserved, payment-done, │
                 │  order-failed, order-completed │
                 └───────────────┬─────────────────┘
                                 │
                        ┌────────▼─────────┐
                        │ notification-svc  │
                        │  (Kafka consumer) │
                        └────────────────────┘

  Her serviste kendi veritabanı vardır (Database per Service):
  - order-service      → PostgreSQL (orders_db)
  - inventory-service   → PostgreSQL (inventory_db) + Redis (stok cache)
  - payment-service     → PostgreSQL (payments_db)
  - notification-service→ MongoDB veya sade log (opsiyonel)
```

**Akış (Saga - Choreography Pattern):**
1. Kullanıcı `order-service`'e REST ile sipariş oluşturur (`POST /orders`)
2. `order-service` siparişi `PENDING` durumunda kaydeder ve `order-created` event'ini Kafka'ya yayınlar
3. `inventory-service` bu event'i dinler, stok kontrolü yapar:
   - Yeterliyse stok rezerve eder → `stock-reserved` event'i yayınlar
   - Yetersizse → `stock-rejected` event'i yayınlar
4. `payment-service` `stock-reserved` event'ini dinler, ödemeyi simüle eder:
   - Başarılıysa → `payment-completed` event'i yayınlar
   - Başarısızsa → `payment-failed` event'i yayınlar (bu durumda `inventory-service` stoğu geri almalı — **compensating transaction**)
5. `order-service` tüm event'leri dinleyip siparişin son durumunu günceller (`COMPLETED` / `FAILED`)
6. `notification-service` her durum değişikliğinde kullanıcıya bildirim event'i işler (loglar / mock email)

---

## 3. Teknoloji Yığını

### Backend Çekirdek
| Teknoloji | Kullanım Amacı |
|---|---|
| **Java 25** | Ana dil |
| **Spring Boot 3.x** | Servis framework'ü |
| **Spring Web (MVC)** | RESTful API'ler |
| **Spring Data JPA** | Veritabanı erişimi (ORM) |
| **Spring Kafka** | Kafka producer/consumer entegrasyonu |
| **Spring Validation** | İstek (request) doğrulama |
| **Spring Data Redis** | Cache ve distributed lock |
| **Lombok** | Boilerplate kod azaltma (getter/setter/constructor) |
| **MapStruct** (opsiyonel) | DTO ↔ Entity dönüşümleri |

### Mesajlaşma & Event Streaming
- **Apache Kafka** (KRaft modu, Zookeeper'sız — güncel yaklaşım)
- **Kafka UI** (opsiyonel, topic/mesaj gözlemlemek için: `provectuslabs/kafka-ui`)

### Veritabanı & Cache
- **PostgreSQL** (her mikroservis için ayrı database — database-per-service)
- **Redis** (stok cache + idempotency key kaydı + distributed lock)
- **Flyway** veya **Liquibase** — veritabanı migration yönetimi (şiddetle tavsiye edilir, "temiz kod" pratiği olarak da mülakatlarda çok iyi izlenim bırakır)

### Test
- **JUnit 5** — birim testler
- **Mockito** — mock nesneler
- **Testcontainers** — Kafka, PostgreSQL, Redis için gerçekçi entegrasyon testleri (container üzerinde)
- **REST Assured** (opsiyonel) — API testleri

### Container & Orkestrasyon
- **Docker** + **Docker Compose** — yerel geliştirme ortamı (tüm servisleri + Kafka + PostgreSQL + Redis'i tek komutla ayağa kaldırmak için)
- **Kubernetes (Minikube veya Kind)** — yerelde k8s deneyimi için (ileri aşama, isteğe bağlı ama ilanda geçiyor)
- **Helm** (opsiyonel, ileri seviye)

### CI/CD
- **GitHub Actions** — build, test, docker image push pipeline'ı

### Observability (Gözlemlenebilirlik)
| Araç | Amaç |
|---|---|
| **SLF4J + Logback** | Yapılandırılmış (structured/JSON) loglama |
| **Micrometer** | Metrik toplama |
| **Prometheus** | Metrik depolama |
| **Grafana** | Metrik görselleştirme (dashboard) |
| **Zipkin** veya **OpenTelemetry** | Dağıtık izleme (distributed tracing) — bir isteğin 4 servis arasında nasıl dolaştığını görmek için |

### Cloud (opsiyonel, ileri aşama)
- **AWS** — en azından local'de **LocalStack** ile simüle edilebilir (S3, SQS vb.), gerçek AWS hesabı şart değil, ilanın "AWS bilgisi" beklentisini karşılamak için kavramsal bilgi + LocalStack denemesi yeterli olur.

---

## 4. Geliştirme Ortamı: VSCode Kurulumu

**Evet, VSCode bu proje için gayet uygun.** Java + Spring Boot + Docker + Kubernetes ekosistemini VSCode üzerinden rahatlıkla yönetebilirsin. (IntelliJ IDEA da alternatif olabilir ama VSCode + eklentiler yeterli ve hafif.)

### Kurulman Gereken Programlar (sistem seviyesi)
1. **JDK 25** (Eclipse Temurin önerilir)
2. **Docker Desktop** (Windows/Mac) veya Docker Engine (Linux)
3. **Maven** veya **Gradle** (proje build aracı — ikisinden birini seç, Maven daha yaygın öğretilir)
4. **Git**

### VSCode Eklentileri (Extensions)

**Zorunlu / Çekirdek:**
- `Extension Pack for Java` (Microsoft) — Java dil desteği, debugger, test runner hepsi bunun içinde
- `Spring Boot Extension Pack` (VMware) — Spring Boot dashboard, otomatik tamamlama, `application.yml` desteği
- `Docker` (Microsoft) — Dockerfile ve docker-compose dosyalarını yönetmek için
- `YAML` (Red Hat) — application.yml / docker-compose.yml için syntax desteği

**Faydalı ek eklentiler:**
- `Lombok Annotations Support for VS Code` — Lombok kullanacaksan zorunlu
- `REST Client` — Postman'e alternatif, `.http` dosyalarıyla API test etmek için (opsiyonel, Postman de kullanılabilir)
- `Kubernetes` (Microsoft) — k8s manifest dosyaları için
- `GitLens` — Git geçmişini daha rahat okumak için
- `SonarLint` — kod kalitesi/SOLID ihlallerini anlık gösterir (ilandaki "temiz kod" pratiği için gayet uygun)

> Not: "vibe coding" ile Antigravity üzerinden kod üreteceğin için bu eklentiler zorunlu değil ama üretilen kodu **anlamak ve debug etmek** için (senin asıl amacın da bu) yukarıdaki paket şiddetle tavsiye edilir.

---

## 5. Proje Klasör Yapısı (Monorepo Yaklaşımı)

```
supply-chain-system/
├── order-service/
│   ├── src/main/java/com/example/order/
│   │   ├── controller/       # REST endpoint'ler
│   │   ├── service/          # İş mantığı
│   │   ├── repository/       # JPA repository'ler
│   │   ├── entity/           # Veritabanı modelleri
│   │   ├── dto/               # İstek/cevap modelleri
│   │   ├── kafka/
│   │   │   ├── producer/     # Event yayınlayıcılar
│   │   │   └── consumer/     # Event dinleyiciler
│   │   ├── config/           # Kafka, Redis, DB config
│   │   └── exception/        # Özel hata yönetimi
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/     # Flyway SQL script'leri
│   ├── src/test/java/...
│   └── Dockerfile
│
├── inventory-service/         (aynı yapı)
├── payment-service/            (aynı yapı)
├── notification-service/       (aynı yapı)
│
├── docker-compose.yml          # Tüm servisler + Kafka + PostgreSQL + Redis + Kafka UI
├── k8s/                        # Kubernetes manifest dosyaları (deployment, service, configmap)
│   ├── order-service-deployment.yaml
│   ├── inventory-service-deployment.yaml
│   └── ...
├── .github/workflows/
│   └── ci.yml                 # GitHub Actions pipeline
└── README.md
```

---

## 6. Veritabanı Şeması (Özet)

**orders_db (order-service)**
```
orders
├── id (UUID, PK)
├── customer_id
├── product_id
├── quantity
├── status (PENDING, STOCK_RESERVED, PAYMENT_COMPLETED, COMPLETED, FAILED)
├── created_at
└── updated_at
```

**inventory_db (inventory-service)**
```
products
├── id (UUID, PK)
├── name
├── available_stock
├── reserved_stock
└── version (optimistic locking için)
```

**payments_db (payment-service)**
```
payments
├── id (UUID, PK)
├── order_id
├── amount
├── status (SUCCESS, FAILED)
└── created_at
```

**idempotency_keys (Redis, tüm servislerde ortak mantık)**
```
key: "event:{eventId}"  → value: "processed" (TTL: 24 saat)
```

---

## 7. Kafka Topic Tasarımı

| Topic Adı | Producer | Consumer | Partition Key |
|---|---|---|---|
| `order-created` | order-service | inventory-service | `orderId` |
| `stock-reserved` | inventory-service | payment-service | `orderId` |
| `stock-rejected` | inventory-service | order-service | `orderId` |
| `payment-completed` | payment-service | order-service, notification-service | `orderId` |
| `payment-failed` | payment-service | order-service, inventory-service | `orderId` |

> **Neden `orderId` partition key?** Aynı sipariş için üretilen tüm event'lerin aynı partition'a gitmesini garanti eder → **event sıralaması (ordering)** korunur.

---

## 8. Kritik Teknik Konseptler (Bunları Öğrenmen Gereken Kısımlar)

1. **Idempotency:** Aynı Kafka event'i (örn. network hatası sonrası retry ile) iki kez tüketilirse, `inventory-service` stoğu iki kez düşürmemeli. Çözüm: her event'in benzersiz ID'sini Redis'te işaretlemek.
2. **Optimistic Locking:** İki sipariş aynı anda aynı ürünün son stoğunu almaya çalışırsa, JPA `@Version` alanı ile çakışmayı yakalayıp birini reddetmek.
3. **Saga / Compensating Transaction:** Ödeme başarısız olursa, daha önce rezerve edilen stoğun geri iade edilmesi gerekir — bu "telafi işlemi"dir.
4. **Retry + Dead Letter Queue (DLQ):** Bir consumer event'i işlerken hata alırsa, Spring Kafka'nın `@RetryableTopic` mekanizmasıyla belirli sayıda tekrar denenir; hepsi başarısız olursa mesaj DLQ topic'ine düşer.
5. **Eventual Consistency:** `order-service`'teki sipariş durumu, diğer servislerdeki event'ler işlenene kadar anlık olarak güncel olmayabilir — bu normaldir ve sistem tasarımının bir parçasıdır.

---

## 9. Önerilen Geliştirme Yol Haritası (6 Hafta)

| Hafta | Hedef |
|---|---|
| **1** | Ortam kurulumu (Docker, Kafka, PostgreSQL, Redis lokal ayağa kaldırma). `order-service` iskeleti + basit CRUD REST API |
| **2** | `inventory-service` iskeleti + Kafka producer/consumer entegrasyonu (order-created → stock-reserved akışı) |
| **3** | `payment-service` eklenip tam Saga akışının uçtan uca çalışması. Idempotency + Redis entegrasyonu |
| **4** | `notification-service`, hata senaryoları (stok yetersiz, ödeme başarısız → compensating transaction), retry/DLQ mekanizması |
| **5** | Test yazımı (JUnit + Testcontainers), Docker Compose ile tüm sistemi tek komutla ayağa kaldırma, GitHub Actions CI pipeline |
| **6** | Observability (Prometheus + Grafana + Zipkin), Kubernetes manifest'leri, README + mimari diyagram ile projeyi sunuma hazır hale getirme |

---

## 10. Antigravity İçin Not

Bu dosyayı Antigravity'ye (veya benzer bir AI kod üretim aracına) verirken şu şekilde kullanabilirsin:
- Önce **1 servisi** (örn. `order-service`) tek başına ürettir, çalıştığından emin ol, sonra diğerine geç. Tüm sistemi tek seferde üretmeye çalışmak, hem anlaman hem de debug etmen açısından zor olur.
- Her servis üretildikten sonra üretilen kodu VSCode'da açıp `controller → service → repository → kafka` akışını satır satır takip ederek oku; bu senin "analiz ederek öğrenme" hedefine hizmet eder.
- Docker Compose dosyasını en başta oluşturttur — böylece Kafka/PostgreSQL/Redis'i lokal makinende kurulum derdi olmadan tek komutla (`docker compose up`) ayağa kaldırabilirsin.
