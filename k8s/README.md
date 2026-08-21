# Kubernetes Manifestleri

Sistemin tamamı (4 mikroservis + PostgreSQL, Redis, Kafka, Zipkin) `supply-chain`
namespace'i altında çalışır.

## Dosyalar

| Dosya | İçerik |
|---|---|
| `00-namespace.yaml` | `supply-chain` namespace'i |
| `01-configmap.yaml` | Hassas olmayan ortam değişkenleri (DB host, Kafka broker, Redis, Zipkin, probe ayarları) |
| `02-secret.yaml` | Veritabanı kullanıcı adı / parolası |
| `10-postgres.yaml` | PostgreSQL + 4 veritabanını oluşturan init ConfigMap'i |
| `11-redis.yaml` | Redis |
| `12-kafka.yaml` | Kafka (KRaft, tek broker) |
| `13-zipkin.yaml` | Zipkin (tracing) |
| `20..23-*.yaml` | 4 mikroservisin Deployment + Service tanımları |

Dosyalar numara sırasıyla uygulanır; `kubectl apply -f k8s/` bu sırayı kendiliğinden korur.

## Kurulum

### 1. Cluster oluştur (kind)

```bash
kind create cluster --name supply-chain
```

### 2. İmajları derle ve cluster'a yükle

Servis imajları bir registry'de olmadığı için kind düğümüne elle yüklenir
(manifest'lerde `imagePullPolicy: IfNotPresent` bu yüzden kullanılır):

```bash
docker compose build           # 4 imajı üretir
kind load docker-image --name supply-chain \
  supply-chain-order-service:latest \
  supply-chain-inventory-service:latest \
  supply-chain-payment-service:latest \
  supply-chain-notification-service:latest
```

> Windows'ta proje yolunda Türkçe karakter varsa `docker compose build` için
> [ana README'deki](../README.md) junction çözümüne bakın.

### 3. Deploy et

```bash
kubectl apply -f k8s/
kubectl get pods -n supply-chain -w
```

Altyapı hazır olduktan sonra servisler ~30 saniyede `Ready` duruma gelir.

### 4. Eriş

```bash
kubectl port-forward -n supply-chain svc/order-service 8081:8081
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"C1","productId":"a1b2c3d4-e5f6-7890-abcd-ef1234567890","quantity":2}'
```

## Health Probe Tasarımı

Her mikroservis üç prob kullanır — hepsi `/actuator/health` altındadır:

| Prob | Yol | Başarısız olursa |
|---|---|---|
| `startupProbe` | `/actuator/health/readiness` | Henüz bir şey yapılmaz; JVM'e açılması için süre tanır (max 5 dk) |
| `readinessProbe` | `/actuator/health/readiness` | Pod Service endpoint listesinden çıkarılır, **öldürülmez** |
| `livenessProbe` | `/actuator/health/liveness` | Konteyner **yeniden başlatılır** |

Liveness için bilinçli olarak düz `/actuator/health` yerine `/actuator/health/liveness`
kullanılır: düz uç, DB/Redis/Kafka gibi bağımlılıkların durumunu da içerir. Redis geçici
olarak düşerse düz uç `DOWN` döner ve k8s **sağlıklı** pod'ları restart döngüsüne sokardı.
`/liveness` yalnızca uygulamanın kendi canlılığını (LivenessState) yansıtır.

Bu alt uçların açık olması için ConfigMap'te
`MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED: "true"` verilmiştir.

## Temizlik

```bash
kubectl delete -f k8s/          # kaynakları sil
kind delete cluster --name supply-chain
```

## Bilinen Sınırlamalar (demo cluster)

- **Kalıcılık yok:** PostgreSQL `emptyDir` kullanır; pod silinirse veri kaybolur.
  Gerçek ortamda `PersistentVolumeClaim` gerekir.
- **Secret repo'da:** Demo amaçlı. Gerçek ortamda Sealed Secrets / External Secrets
  veya bulut sağlayıcı secret yöneticisi kullanılmalıdır.
- **Tek replika:** Tüm Deployment'lar `replicas: 1`. Servisler ölçeklenebilir ancak
  `replicas > 1` yapmadan önce [TEST_RAPORU.md](../TEST_RAPORU.md) §11'deki
  saga yarış durumu (race condition) giderilmelidir.
- **Kafka/PostgreSQL Deployment olarak tanımlı;** üretimde StatefulSet daha uygundur.
