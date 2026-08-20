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

### 1. Altyapıyı Başlat

```bash
docker compose up -d
```

Bu komut PostgreSQL (4 ayrı veritabanı), Redis, Kafka (KRaft) ve Kafka UI'ı ayağa kaldırır.

### 2. Servisleri Çalıştır

Her servis kendi dizininden çalıştırılır:

```bash
cd order-service && mvn spring-boot:run
cd inventory-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
```

### 3. Tüm Projeyi Derle

```bash
mvn clean install
```

### Faydalı Linkler

- **Kafka UI:** http://localhost:8080

## Proje Yapısı

```
├── order-service/           # Sipariş yönetimi servisi
├── inventory-service/       # Envanter yönetimi servisi
├── payment-service/         # Ödeme servisi
├── notification-service/    # Bildirim servisi
├── docker/                  # Docker yardımcı dosyaları
│   └── postgres/
│       └── init-databases.sh
├── docker-compose.yml       # Altyapı container tanımları
├── pom.xml                  # Parent (aggregator) POM
└── README.md
```
