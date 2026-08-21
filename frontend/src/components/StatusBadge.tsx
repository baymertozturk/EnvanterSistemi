import type { OrderStatus } from '../api/client';

const LABEL: Record<OrderStatus, string> = {
  PENDING:           'Sipariş alındı',
  STOCK_RESERVED:    'Stok rezerve edildi',
  PAYMENT_COMPLETED: 'Ödeme tamamlandı',
  COMPLETED:         'Tamamlandı',
  FAILED:            'Başarısız',
};

const TONE: Record<OrderStatus, string> = {
  PENDING:           'border-bus/50 text-bus',
  STOCK_RESERVED:    'border-bus/50 text-bus',
  PAYMENT_COMPLETED: 'border-ok/50 text-ok',
  COMPLETED:         'border-ok/50 text-ok',
  FAILED:            'border-bad/50 text-bad',
};

export function StatusBadge({ status }: { status: OrderStatus }) {
  return (
    <span className={`inline-flex items-center gap-2 rounded border px-2.5 py-1 font-mono text-xs ${TONE[status]}`}>
      <span className="h-1.5 w-1.5 rounded-full bg-current" />
      {status}
      <span className="text-muted">·</span>
      <span className="font-display text-muted">{LABEL[status]}</span>
    </span>
  );
}
