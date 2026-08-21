import { useEffect, useState } from 'react';
import { api, type Product } from '../api/client';

interface Props {
  busy: boolean;
  onStart: (productId: string, quantity: number, availableStock: number) => void;
  /** Sipariş bittikten sonra stokları tazelemek için artan sayaç */
  refreshKey: number;
}

/**
 * Diyagramın üstündeki tek satırlık kontrol.
 * İki senaryo tetikleyebilir:
 *  - Normal sipariş  → başarılı akış (ödeme %20 ihtimalle yine de patlayabilir)
 *  - Stok yetersiz   → miktarı kasten stoktan büyük seçip red/telafi yolunu gösterir
 */
export function OrderTrigger({ busy, onStart, refreshKey }: Props) {
  const [products, setProducts] = useState<Product[]>([]);
  const [selected, setSelected] = useState('');
  const [quantity, setQuantity] = useState(2);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    api.getProducts()
      .then((list) => {
        setProducts(list);
        setLoadError(null);
        setSelected((cur) => cur || list[0]?.id || '');
      })
      .catch((e: unknown) => setLoadError(e instanceof Error ? e.message : 'Ürünler yüklenemedi'));
  }, [refreshKey]);

  const current = products.find((p) => p.id === selected);
  const ready = !busy && !!selected;

  const field = 'rounded border border-line bg-raised px-3 py-2 text-sm text-ink ' +
                'focus:border-bus disabled:opacity-40 disabled:cursor-not-allowed';

  if (loadError) {
    return (
      <div className="rounded border border-bad/50 bg-bad/10 px-4 py-3 text-sm text-bad">
        Ürünler yüklenemedi: {loadError}
        <span className="ml-2 text-muted">
          inventory-service (:8082) çalışıyor mu?
        </span>
      </div>
    );
  }

  return (
    <div className="flex flex-wrap items-end gap-3">
      <label className="flex flex-col gap-1.5">
        <span className="font-mono text-[11px] uppercase tracking-wide text-muted">Ürün</span>
        <select
          value={selected}
          disabled={busy || products.length === 0}
          onChange={(e) => setSelected(e.target.value)}
          className={`${field} min-w-[260px]`}
        >
          {products.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name} — stok {p.availableStock}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1.5">
        <span className="font-mono text-[11px] uppercase tracking-wide text-muted">Adet</span>
        <input
          type="number" min={1} max={999} value={quantity}
          disabled={busy}
          onChange={(e) => setQuantity(Math.max(1, Number(e.target.value) || 1))}
          className={`${field} w-24`}
        />
      </label>

      {/* Birincil eylem: Fitts yasası — en büyük ve en belirgin hedef */}
      <button
        type="button"
        disabled={!ready}
        onClick={() => onStart(selected, quantity, current?.availableStock ?? 0)}
        className="rounded bg-bus px-5 py-2.5 text-sm font-semibold text-bg transition
                   hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-40"
      >
        {busy ? 'Akış çalışıyor…' : 'Örnek Sipariş Ver'}
      </button>

      <button
        type="button"
        disabled={!ready}
        onClick={() => onStart(selected, (current?.availableStock ?? 0) + 9999, current?.availableStock ?? 0)}
        title="Stoktan fazla adet isteyerek reddetme ve telafi yolunu gösterir"
        className="rounded border border-line px-4 py-2.5 text-sm text-muted transition
                   hover:border-bad hover:text-bad disabled:cursor-not-allowed disabled:opacity-40"
      >
        Stok yetersiz senaryosu
      </button>

      {current && (
        <span className="ml-auto font-mono text-xs text-muted">
          {current.name}: <span className="text-data">{current.availableStock}</span> uygun ·{' '}
          <span className="text-data">{current.reservedStock}</span> rezerve
        </span>
      )}
    </div>
  );
}
