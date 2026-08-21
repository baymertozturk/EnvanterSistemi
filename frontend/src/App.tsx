import { useEffect, useState } from 'react';
import type { OrderStatus } from './api/client';
import { ArchitectureDiagram } from './components/ArchitectureDiagram';
import { OrderTrigger } from './components/OrderTrigger';
import { StatusBadge } from './components/StatusBadge';
import { useOrderTracking } from './hooks/useOrderTracking';
import { useServiceHealth } from './hooks/useServiceHealth';

/** Diyagramın altında beliren tek satırlık anlatım */
const NARRATION: Record<OrderStatus, string> = {
  PENDING:
    'order-service siparişi PENDING olarak kaydetti ve order-created olayını Kafka’ya yayınladı. ' +
    'Diğer servisleri doğrudan çağırmadı — sadece olayı duyurdu.',
  STOCK_RESERVED:
    'inventory-service olayı aldı, stoğu rezerve etti ve stock-reserved olayını yayınladı. ' +
    'order-service bu olayı duyup siparişin durumunu güncelledi.',
  PAYMENT_COMPLETED:
    'payment-service ödemeyi işledi ve payment-completed olayını yayınladı. ' +
    'Bu olayı hem order-service (durumu günceller) hem notification-service (müşteriye bildirir) dinliyor.',
  COMPLETED:
    'Saga tamamlandı — tüm adımlar başarılı.',
  FAILED:
    'Saga başarısız sonlandı ve telafi (compensation) çalıştı.',
};

const FAILED_STOCK =
  'inventory-service yeterli stok bulamadı ve stock-rejected olayını yayınladı. ' +
  'Sipariş FAILED oldu, stok hiç değişmedi — dağıtık işlem geri alınmış oldu.';

const FAILED_PAYMENT =
  'payment-service ödemeyi reddetti (%20 rastgele hata simülasyonu) ve payment-failed olayını yayınladı. ' +
  'Bu olayı inventory-service de dinliyor ve rezerve ettiği stoğu geri açıyor — işte telafi (compensation) budur.';

export default function App() {
  const health = useServiceHealth();
  const { order, displayStatus, shownSteps, paymentFailed, busy, error, timedOut, start } = useOrderTracking();
  const [refreshKey, setRefreshKey] = useState(0);

  // Akış bitince stok sayıları değişmiş olur — ürün listesini tazele
  useEffect(() => {
    if (!busy && order) setRefreshKey((k) => k + 1);
  }, [busy, order]);

  const status = displayStatus;

  const narration = !status
    ? null
    : status === 'FAILED'
      ? (paymentFailed ? FAILED_PAYMENT : FAILED_STOCK)
      : NARRATION[status];

  return (
    <div className="mx-auto flex min-h-screen max-w-[1400px] flex-col gap-5 px-6 py-6">

      {/* ── Başlık — sola hizalı (yatay dikkat sola kayar) ──────────────── */}
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">
            Dağıtık Sipariş Yönetim Sistemi
          </h1>
          <p className="mt-1 max-w-2xl text-sm text-muted">
            Dört bağımsız mikroservis, birbirini <em>çağırmadan</em>, yalnızca Kafka üzerinden olay
            alışverişiyle tek bir siparişi tamamlıyor. Aşağıdaki butona basın — akışı canlı izleyin.
          </p>
        </div>
        <a
          href="https://github.com/baymertozturk/EnvanterSistemi"
          target="_blank" rel="noreferrer"
          className="font-mono text-xs text-muted underline decoration-line underline-offset-4 hover:text-ink"
        >
          kaynak kod ↗
        </a>
      </header>

      {/* ── Kontrol satırı ──────────────────────────────────────────────── */}
      <section className="rounded-lg border border-line bg-surface/70 px-5 py-4 backdrop-blur">
        <OrderTrigger busy={busy} onStart={start} refreshKey={refreshKey} />
      </section>

      {/* ── Diyagram ────────────────────────────────────────────────────── */}
      <main className="rounded-lg border border-line bg-surface/40 px-4 py-3">
        <ArchitectureDiagram
          status={status}
          paymentFailed={paymentFailed}
          health={health}
          quantity={order?.quantity ?? 0}
        />
      </main>

      {/* ── Durum + anlatım ─────────────────────────────────────────────── */}
      <footer className="min-h-[86px] rounded-lg border border-line bg-surface/70 px-5 py-4">
        {error && (
          <p className="text-sm text-bad">
            Sipariş oluşturulamadı: {error}
            <span className="ml-2 text-muted">order-service (:8081) çalışıyor mu?</span>
          </p>
        )}

        {!error && !status && (
          <p className="text-sm text-muted">
            Henüz sipariş verilmedi. Servis kutularındaki noktalar canlı sağlık durumunu gösterir:{' '}
            <span className="text-ok">yeşil</span> = çalışıyor, <span className="text-bad">kırmızı</span> = erişilemiyor.
          </p>
        )}

        {!error && status && (
          <div className="animate-fadeUp space-y-2.5">
            <div className="flex flex-wrap items-center gap-3">
              <StatusBadge status={status} />
              <span className="font-mono text-[11px] text-muted">#{order?.id.slice(0, 8)}</span>
              {busy && <span className="font-mono text-[11px] text-bus">akış sürüyor…</span>}
              {timedOut && (
                <span className="font-mono text-[11px] text-bad">
                  30 sn içinde tamamlanmadı — takip durduruldu
                </span>
              )}
            </div>
            <p className="max-w-4xl text-sm leading-relaxed text-ink/90">{narration}</p>
            <p className="font-mono text-[11px] text-muted">
              {shownSteps.join('  →  ')}
            </p>
          </div>
        )}
      </footer>
    </div>
  );
}
