# Dağıtık Sipariş & Envanter Yönetim Sistemi

E-ticaret / tedarik zinciri senaryosunu simüle eden, **4 mikroservisten** oluşan, Kafka ile olay güdümlü (event-driven) haberleşen bir sipariş-envanter yönetim platformu.

## Mimari

```
                        ┌─────────────────┐
                        │   API Gateway    │
                        └────────┬─────────┘
                                 │
        ┌────────────────────────┼─────────────────────────┐
        │                        │                          │
┌───────▼────────┐     ┌─────────▼────────┐      ┌──────────▼────────┐
│  order-service  │     │ inventory-service │      │  payment-service   │
│    :8081        │     │     :8082         │      │     :8083          │
└───────┬────────┘     └─────────┬────────┘      └──────────┬────────┘
        │                        │                          │
        └───────────────►  Kafka Cluster  ◄─────────────────┘
                                 │
                        ┌────────▼─────────┐
                        │ notification-svc  │
                        │     :8084         │
                        └──────────────────┘
```

## Servisler

| Servis | Port | Veritabanı | Açıklama |
|---|---|---|---|
| order-service | 8081 | orders_db | Sipariş yönetimi |
| inventory-service | 8082 | inventory_db | Envanter/stok yönetimi |
| payment-service | 8083 | payments_db | Ödeme işlemleri |
| notification-service | 8084 | notifications_db | Bildirim servisi |

## Altyapı Bileşenleri

| Bileşen | Port | Açıklama |
|---|---|---|
| PostgreSQL | 5432 | İlişkisel veritabanı (her servis için ayrı DB) |
| Redis | 6379 | Cache & distributed lock |
| Kafka (KRaft) | 9094 | Event streaming (host erişimi) |
| Kafka UI | 8080 | Kafka topic/mesaj izleme arayüzü |

## Teknoloji Yığını

- **Java 25** + **Spring Boot 3.3.x**
- **Spring Data JPA** + **PostgreSQL** + **Flyway**
- **Spring Kafka** (event-driven messaging)
- **Spring Data Redis** (cache + distributed lock)
- **Docker** + **Docker Compose**
- **Lombok**

## Hızlı Başlangıç

### Ön Koşullar

- JDK 25+
- Docker & Docker Compose
- Maven 3.8+

### 1. Tüm Sistemi Tek Komutla Başlat

```bash
docker compose up --build
```

Bu komut 4 mikroservisi ve **web arayüzünü** kendi imajlarından derleyip; PostgreSQL
(4 ayrı veritabanı), Redis, Kafka (KRaft), Kafka UI, Prometheus, Grafana ve Zipkin ile
birlikte ayağa kaldırır. Servisler altyapı `healthy` olana kadar bekler.

Ardından tarayıcıda **http://localhost:3001** adresini açın — sistemin canlı mimari
diyagramı gelir. "Örnek Sipariş Ver" butonuna basınca olayların 4 servis arasında
Kafka üzerinden nasıl aktığını adım adım izleyebilirsiniz.
Ayrıntı: [frontend/README.md](frontend/README.md)

> **⚠️ Windows'ta proje yolu ASCII olmalı.** Docker Compose çoklu servis build'inde
> oturum anahtarını dizin adından türetir; yolda Türkçe karakter varsa build
> `x-docker-expose-session-sharedkey ... non-printable ASCII characters` hatasıyla
> başarısız olur. Projeyi ASCII bir yola koyun (ör. `C:\...\supply-chain`) veya bir
> junction üzerinden çalıştırın:
>
> ```powershell
> New-Item -ItemType Junction -Path C:\sc-build -Target "<proje-yolu>"
> docker compose -f C:\sc-build\docker-compose.yml up --build
> ```
>
> Linux/macOS ve GitHub Actions bu kısıttan etkilenmez. Ayrıntı: [TEST_RAPORU.md](TEST_RAPORU.md) §7

Durdurmak için:

```bash
docker compose down          # konteynerleri durdur
docker compose down -v       # volume'ları da sil (sıfırdan başlamak için)
```

### 2. Servisleri Yerelde Çalıştırma (geliştirme)

Alternatif olarak yalnızca altyapıyı konteynerde tutup servisleri IDE'den
çalıştırabilirsiniz:

```bash
docker compose up -d postgres redis kafka kafka-ui
cd order-service && mvn spring-boot:run
```

### 3. Testleri Çalıştır

```bash
mvn clean test
```

30 test (birim + Testcontainers entegrasyon) çalışır, JaCoCo kapsam raporu
`*/target/site/jacoco/index.html` altında üretilir.

### 4. Kubernetes'e Deploy Et (opsiyonel)

```bash
kind create cluster --name supply-chain
kind load docker-image --name supply-chain \
  supply-chain-order-service:latest supply-chain-inventory-service:latest \
  supply-chain-payment-service:latest supply-chain-notification-service:latest
kubectl apply -f k8s/
```

Manifestler, probe tasarımı ve ayrıntılı adımlar: **[k8s/README.md](k8s/README.md)**

### Faydalı Linkler

| Arayüz | Adres |
|---|---|
| **Arayüz (canlı mimari diyagramı)** | **http://localhost:3001** |
| Kafka UI | http://localhost:8080 |
| Grafana | http://localhost:3000 (admin/admin) |
| Prometheus | http://localhost:9090 |
| Zipkin (tracing) | http://localhost:9411 |
| order-service | http://localhost:8081 |
| inventory-service | http://localhost:8082 |
| payment-service | http://localhost:8083 |
| notification-service | http://localhost:8084 |

Uçtan uca test sonuçları, tespit edilen hatalar ve bilinen kısıtlar için:
**[TEST_RAPORU.md](TEST_RAPORU.md)**

## Proje Yapısı

```
├── order-service/           # Sipariş yönetimi servisi
├── inventory-service/       # Envanter yönetimi servisi
├── payment-service/         # Ödeme servisi
├── notification-service/    # Bildirim servisi
├── docker/                  # Docker yardımcı dosyaları
│   └── postgres/
│       └── init-databases.sh
├── frontend/                # Web arayüzü (React + Vite, nginx ile sunulur)
├── k8s/                     # Kubernetes manifestleri (Deployment/Service/ConfigMap)
├── .github/workflows/ci.yml # CI pipeline (test + Docker build)
├── docker-compose.yml       # Tüm sistem (altyapı + 4 mikroservis)
├── pom.xml                  # Parent (aggregator) POM
├── TEST_RAPORU.md           # Uçtan uca test raporu
└── README.md
```
