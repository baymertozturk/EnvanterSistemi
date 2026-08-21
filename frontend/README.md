# Frontend — Canlı Mimari Diyagramı

Projeyi bilmeyen birine **30 saniyede anlatmak** için tasarlanmış tek sayfalık arayüz.
Bir yönetim paneli değil; canlı bir anlatım ekranı.

Butona basılınca gerçek bir sipariş oluşturulur ve olayların 4 mikroservis arasında Kafka
üzerinden nasıl aktığı diyagram üzerinde adım adım canlanır.

## Teknoloji

React 19 · TypeScript · Vite 6 · Tailwind CSS 3 · üretimde nginx

Diyagram harici bir kütüphane kullanmaz — **inline SVG** ile çizilir.

## Çalıştırma

### Docker (önerilen)

Kök dizindeki `docker compose` her şeyi ayağa kaldırır:

```bash
docker compose up --build
```

→ **http://localhost:3001**

### Geliştirme sunucusu

```bash
npm install
npm run dev
```

→ http://localhost:5173 (backend'in `docker compose up -d` ile çalışıyor olması gerekir)

### Kubernetes

```bash
kind load docker-image --name supply-chain supply-chain-frontend:latest
kubectl apply -f ../k8s/24-frontend.yaml
kubectl port-forward -n supply-chain svc/frontend 3001:80
```

## Mimari kararlar

### CORS neden gerekmedi

nginx hem statik sayfayı sunar hem `/api/*` isteklerini servislere yönlendirir. Tarayıcı
açısından her şey tek origin'de (`localhost:3001`) olduğu için CORS hiç devreye girmez —
bu sayede **Java servislerine tek satır kod eklenmedi**.

| Yol | Hedef |
|---|---|
| `/api/orders` | order-service:8081/orders |
| `/api/products` | inventory-service:8082/products |
| `/api/health/{order,inventory,payment,notify}` | ilgili servisin `/actuator/health` ucu |

`vite.config.ts` içindeki dev proxy aynı yolları kullanır, böylece kodda ortam ayrımı yoktur.
Aynı `nginx.conf` hem compose hem Kubernetes'te çalışır, çünkü servis adları iki ortamda da aynıdır.

### Neden "oynatma" (playback) mekanizması var

**Bu arayüzün en kritik tasarım kararı.**

Saga uçtan uca ~500–900 ms sürüyor. Hangi aralıkla yoklanırsa yoklansın
`PENDING → STOCK_RESERVED → PAYMENT_COMPLETED` zincirinin **orta adımları kaçırılıyor**;
ekran PENDING'den doğrudan sonuca atlıyordu. Oysa bu arayüzün tek işi o ara adımları göstermek.

Çözüm ikiye ayrıldı:
- **Yoklayıcı** (250 ms) gerçek sonucu belirler.
- **Oynatıcı** diyagramı sürer ve her adımı en az **1200 ms** ekranda tutar.

Kaçırılan adımlar uydurulmaz; backend'in saga mantığından kesin olarak çıkarılır:

| Gözlenen sonuç | Çıkarım | Dayanağı |
|---|---|---|
| `PAYMENT_COMPLETED` | `STOCK_RESERVED` mutlaka olmuştur | Ödeme yalnızca `stock-reserved` olayıyla tetiklenir |
| `FAILED`, miktar > stok | Stok reddi (`stock-rejected`), stok hiç değişmedi | inventory-service'in kararıyla aynı kural |
| `FAILED`, miktar ≤ stok | Stok rezerve edildi, **ödeme** patladı, telafi çalıştı | payment-service'te %20 rastgele hata simülasyonu var |

Bu kural canlı sistemde doğrulandı: yetersiz stokta inventory hiç rezervasyon yapmıyor;
yeterli stokta başarısız olan siparişlerde rezervasyon yapılıp ardından geri alınıyor.

### Tasarım

Jenerik "SaaS" estetiğinden kaçınıldı: mor gradient yok, Inter font yok, her yerde kart yok.
Konsept **blueprint** — koyu mavi-arduvaz zemin üzerine teknik çizim.

- **Tipografi:** Space Grotesk (geometrik, mühendislik hissi) + JetBrains Mono (servis/topic adları)
- **Renk:** amber `#F5A524` = Kafka veri yolu ve aktif adım · yeşil `#2ED47A` = başarılı ·
  kırmızı `#FF5C5C` = hata · teal `#14B8A6` = veri katmanı
- Koyu tema, durum renkleri en yüksek kontrastla okunsun diye seçildi
- `prefers-reduced-motion` desteklenir; odak halkaları görünürdür (WCAG 2.4.7)

Servis kutularındaki **iki gösterge karıştırılmamalı**:
- *Kenarlık rengi* → o siparişteki rolü (bekliyor / çalışıyor / bitti / hata)
- *Sağ üstteki nokta* → servisin `/actuator/health` durumu (ayakta mı)

Bir servis ayakta olup akışta henüz sırası gelmemiş olabilir.
