import { useCallback, useEffect, useRef, useState } from 'react';
import { api, type Order, type OrderStatus } from '../api/client';

const POLL_MS = 250;      // gerçek durumu yakalamak için sık yoklama
const STEP_MS = 1200;     // her adımın ekranda kalacağı en az süre
const TIMEOUT_MS = 30_000;

const isTerminal = (s: OrderStatus) =>
  s === 'PAYMENT_COMPLETED' || s === 'FAILED' || s === 'COMPLETED';

/**
 * Siparişi oluşturur, durumunu yoklar ve diyagramı ADIM ADIM oynatır.
 *
 * ── Neden "oynatma" (playback) gerekiyor? ────────────────────────────────
 * Saga bu sistemde uçtan uca ~500-900 ms sürüyor. Hangi aralıkla yoklarsak
 * yoklayalım, PENDING → STOCK_RESERVED → PAYMENT_COMPLETED zincirinin ORTA
 * adımlarını çoğu zaman kaçırıyoruz; ekranda PENDING'den doğrudan sonuca
 * atlıyor. Oysa bu arayüzün tek işi o ara adımları göstermek.
 *
 * Çözüm: yoklama gerçek sonucu belirler, ama diyagram ayrı bir "oynatıcı"
 * tarafından sürülür ve her adım en az STEP_MS boyunca ekranda kalır.
 *
 * ── Kaçırılan adımlar nasıl doğru şekilde geri kazanılıyor? ──────────────
 * Uydurma yapılmıyor; backend'in saga mantığından kesin çıkarım yapılıyor:
 *  - PAYMENT_COMPLETED'a ulaşıldıysa STOCK_RESERVED mutlaka gerçekleşmiştir
 *    (ödeme yalnızca stock-reserved olayıyla tetiklenir).
 *  - FAILED ise iki ihtimal var ve istenen miktar bunları ayırır:
 *      miktar > mevcut stok  → inventory reddetti (stock-rejected), stok hiç değişmedi
 *      miktar ≤ mevcut stok  → stok rezerve edildi, ödeme patladı (payment-failed)
 *                              ve telafi çalışıp stoğu geri açtı
 *    Bu, inventory-service'in kararıyla birebir aynı kuraldır.
 */
export function useOrderTracking() {
  const [order, setOrder] = useState<Order | null>(null);
  const [path, setPath] = useState<OrderStatus[]>([]);
  const [index, setIndex] = useState(-1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [timedOut, setTimedOut] = useState(false);
  const [paymentFailed, setPaymentFailed] = useState(false);

  const poller = useRef<number | null>(null);
  const player = useRef<number | null>(null);
  /** Oynatıcı bu listeyi takip eder; state'ten bağımsız tutulur ki interval güncel kalsın */
  const pathRef = useRef<OrderStatus[]>([]);
  const doneRef = useRef(false);

  const clearTimers = useCallback(() => {
    if (poller.current !== null) { clearInterval(poller.current); poller.current = null; }
    if (player.current !== null) { clearInterval(player.current); player.current = null; }
  }, []);

  useEffect(() => clearTimers, [clearTimers]);

  const start = useCallback(
    async (productId: string, quantity: number, availableStock: number) => {
      clearTimers();
      setError(null);
      setTimedOut(false);
      setPaymentFailed(false);
      setOrder(null);
      setPath([]);
      setIndex(-1);
      pathRef.current = [];
      doneRef.current = false;
      setBusy(true);

      let created: Order;
      try {
        created = await api.createOrder(productId, quantity);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Sipariş oluşturulamadı');
        setBusy(false);
        return;
      }

      setOrder(created);

      // inventory-service'in uygulayacağı kuralın aynısı
      const stockSufficient = quantity <= availableStock;

      const pushStep = (s: OrderStatus) => {
        if (pathRef.current.includes(s)) return;
        pathRef.current = [...pathRef.current, s];
        setPath(pathRef.current);
      };

      pushStep('PENDING');
      setIndex(0);

      /** Terminal duruma göre kaçırılmış ara adımları tamamla */
      const completePath = (finalStatus: OrderStatus) => {
        if (finalStatus === 'PAYMENT_COMPLETED' || finalStatus === 'COMPLETED') {
          pushStep('STOCK_RESERVED');   // ödeme ancak stok rezerve edildiyse çalışır
        } else if (finalStatus === 'FAILED' && stockSufficient) {
          pushStep('STOCK_RESERVED');   // stok yetiyordu → hata ödemede, telafi çalıştı
          setPaymentFailed(true);
        }
        pushStep(finalStatus);
        doneRef.current = true;
      };

      const startedAt = Date.now();

      // ── Yoklayıcı: gerçek durumu izler ────────────────────────────────
      poller.current = window.setInterval(async () => {
        try {
          const fresh = await api.getOrder(created.id);
          setOrder(fresh);
          if (isTerminal(fresh.status)) {
            completePath(fresh.status);
            if (poller.current !== null) { clearInterval(poller.current); poller.current = null; }
          } else {
            pushStep(fresh.status);
          }
        } catch {
          // geçici ağ hatası — zaman aşımına kadar denemeye devam
        }
        if (Date.now() - startedAt > TIMEOUT_MS) {
          setTimedOut(true);
          doneRef.current = true;
          if (poller.current !== null) { clearInterval(poller.current); poller.current = null; }
        }
      }, POLL_MS);

      // ── Oynatıcı: diyagramı rahat okunur hızda ilerletir ──────────────
      player.current = window.setInterval(() => {
        setIndex((i) => {
          const next = i + 1;
          if (next >= pathRef.current.length) {
            // Bilinen tüm adımlar gösterildi; akış da bittiyse dur
            if (doneRef.current) { clearTimers(); setBusy(false); }
            return i;
          }
          return next;
        });
      }, STEP_MS);
    },
    [clearTimers],
  );

  const displayStatus: OrderStatus | null = index >= 0 ? (path[index] ?? null) : null;
  /** Telafi bilgisi yalnızca son adım ekrana geldiğinde anlamlı */
  const showPaymentFailed = paymentFailed && displayStatus === 'FAILED';

  return {
    order,
    /** Diyagramın göstereceği adım (gerçek duruma değil, oynatmaya bağlı) */
    displayStatus,
    /** O ana kadar ekranda gösterilmiş adımlar */
    shownSteps: index >= 0 ? path.slice(0, index + 1) : [],
    paymentFailed: showPaymentFailed,
    busy,
    error,
    timedOut,
    start,
  };
}
