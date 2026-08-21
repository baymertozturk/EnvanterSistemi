import type { HealthState, OrderStatus, ServiceKey } from '../api/client';
import { ServiceNode, type NodeState } from './ServiceNode';

/* ────────────────────────────────────────────────────────────────────────────
   Yerleşim sabitleri.
   Akış soldan sağa okunur: order → inventory → payment → notification.
   Servisler ÜSTTE, Kafka veri yolu ORTADA, veritabanları ALTTA.
   Her olay oku "kaynak kutudan aşağı → veri yolu boyunca yatay → hedef kutuya
   yukarı" şeklinde çizilir; böylece hiçbir olayın servisler arasında doğrudan
   gitmediği, hepsinin Kafka üzerinden aktığı görsel olarak anlaşılır.
   ──────────────────────────────────────────────────────────────────────────── */
const VB = { w: 1000, h: 500 };
const BOX = { y: 52, w: 180, h: 78 };
const BOX_BOTTOM = BOX.y + BOX.h;            // 130
const BUS = { x: 40, y: 236, w: 920, h: 66 };
const DB = { y: 384, h: 62 };

/** Olay oklarının veri yolu içindeki şeritleri — üst üste binmesinler diye */
const LANE = { a: 254, b: 271, c: 288 };

const NODES = [
  { key: 'order'     as ServiceKey, x: 40,  name: 'order-service',        port: 8081, role: 'siparişi oluşturur' },
  { key: 'inventory' as ServiceKey, x: 280, name: 'inventory-service',    port: 8082, role: 'stoğu rezerve eder' },
  { key: 'payment'   as ServiceKey, x: 520, name: 'payment-service',      port: 8083, role: 'ödemeyi işler' },
  { key: 'notify'    as ServiceKey, x: 760, name: 'notification-service', port: 8084, role: 'müşteriyi bilgilendirir' },
];

const CX: Record<ServiceKey, number> = {
  order: 40 + BOX.w / 2, inventory: 280 + BOX.w / 2, payment: 520 + BOX.w / 2, notify: 760 + BOX.w / 2,
};

type Tone = 'flow' | 'ok' | 'bad';
const TONE_COLOR: Record<Tone, string> = { flow: '#F5A524', ok: '#2ED47A', bad: '#FF5C5C' };

interface EventArrow {
  from: ServiceKey;
  to: ServiceKey;
  lane: number;
  topic: string;
  tone: Tone;
}

/** Sipariş durumuna göre o an akan olaylar */
function arrowsFor(status: OrderStatus | null, paymentFailed: boolean): EventArrow[] {
  switch (status) {
    case 'PENDING':
      return [{ from: 'order', to: 'inventory', lane: LANE.a, topic: 'order-created', tone: 'flow' }];
    case 'STOCK_RESERVED':
      return [{ from: 'inventory', to: 'payment', lane: LANE.b, topic: 'stock-reserved', tone: 'flow' }];
    case 'PAYMENT_COMPLETED':
    case 'COMPLETED':
      return [
        { from: 'payment', to: 'notify', lane: LANE.c, topic: 'payment-completed', tone: 'ok' },
        { from: 'payment', to: 'order',  lane: LANE.c, topic: 'payment-completed', tone: 'ok' },
      ];
    case 'FAILED':
      return paymentFailed
        ? [
            { from: 'payment', to: 'order',     lane: LANE.c, topic: 'payment-failed', tone: 'bad' },
            // Telafi (compensation): rezerve edilen stok geri açılır
            { from: 'payment', to: 'inventory', lane: LANE.a, topic: 'payment-failed', tone: 'bad' },
          ]
        : [{ from: 'inventory', to: 'order', lane: LANE.b, topic: 'stock-rejected', tone: 'bad' }];
    default:
      return [];
  }
}

/** Kutuların saga akışındaki rolü */
function nodeStates(status: OrderStatus | null, paymentFailed: boolean): Record<ServiceKey, NodeState> {
  const s: Record<ServiceKey, NodeState> = { order: 'idle', inventory: 'idle', payment: 'idle', notify: 'idle' };
  if (!status) return s;

  s.order = 'done';
  if (status === 'PENDING') { s.inventory = 'active'; return s; }

  if (status === 'STOCK_RESERVED') { s.inventory = 'done'; s.payment = 'active'; return s; }

  if (status === 'PAYMENT_COMPLETED' || status === 'COMPLETED') {
    s.inventory = 'done'; s.payment = 'done'; s.notify = 'done'; return s;
  }

  // FAILED
  if (paymentFailed) { s.inventory = 'active'; s.payment = 'failed'; s.notify = 'done'; }
  else { s.inventory = 'failed'; }
  s.order = 'failed';
  return s;
}

/** Kaynak kutudan veri yoluna inip hedef kutuya çıkan yol */
function pathFor(a: EventArrow): string {
  const from = CX[a.from];
  const to = CX[a.to];
  // Kutunun tam ortasından çıkmak yerine hafif kaydır: gidiş-dönüş okları ayrışsın
  const off = from < to ? 16 : -16;
  return `M ${from + off} ${BOX_BOTTOM} V ${a.lane} H ${to - off} V ${BOX_BOTTOM}`;
}

interface Props {
  status: OrderStatus | null;
  paymentFailed: boolean;
  health: Record<ServiceKey, HealthState>;
  quantity: number;
}

export function ArchitectureDiagram({ status, paymentFailed, health, quantity }: Props) {
  const arrows = arrowsFor(status, paymentFailed);
  const states = nodeStates(status, paymentFailed);
  const busLive = arrows.length > 0;

  return (
    <svg viewBox={`0 0 ${VB.w} ${VB.h}`} className="w-full" role="img"
         aria-label="Sistem mimarisi: dört mikroservis Kafka üzerinden haberleşiyor">
      <defs>
        {(['flow', 'ok', 'bad'] as Tone[]).map((t) => (
          <marker key={t} id={`head-${t}`} viewBox="0 0 10 10" refX="8" refY="5"
                  markerWidth="5" markerHeight="5" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill={TONE_COLOR[t]} />
          </marker>
        ))}
      </defs>

      {/* ── Kafka veri yolu ─────────────────────────────────────────────── */}
      <rect x={BUS.x} y={BUS.y} width={BUS.w} height={BUS.h} rx={6}
            fill={busLive ? 'rgba(245,165,36,0.07)' : 'rgba(28,38,52,0.85)'}
            stroke={busLive ? 'rgba(245,165,36,0.45)' : '#2A3646'} strokeWidth={1.25}
            style={{ transition: 'fill 300ms, stroke 300ms' }} />
      <text x={BUS.x + 14} y={BUS.y - 9} fill={busLive ? '#F5A524' : '#8FA0B5'}
            fontSize={11.5} fontWeight={600} className="font-mono"
            style={{ transition: 'fill 300ms' }}>
        Apache Kafka · event bus
      </text>
      <text x={BUS.x + BUS.w} y={BUS.y - 9} fill="#8FA0B5" fontSize={10} textAnchor="end" className="font-mono">
        KRaft · 5 topic
      </text>

      {/* ── Servis kutuları ─────────────────────────────────────────────── */}
      {NODES.map((n) => (
        <ServiceNode key={n.key} x={n.x} y={BOX.y} w={BOX.w} h={BOX.h}
                     name={n.name} port={n.port} role={n.role}
                     state={states[n.key]} health={health[n.key]} />
      ))}

      {/* ── Olay okları ─────────────────────────────────────────────────── */}
      {arrows.map((a, i) => {
        const color = TONE_COLOR[a.tone];
        const mid = (CX[a.from] + CX[a.to]) / 2;
        return (
          <g key={`${a.topic}-${a.from}-${a.to}-${i}`} className="animate-fadeUp">
            <path d={pathFor(a)} fill="none" stroke={color} strokeWidth={2}
                  strokeDasharray="7 5" markerEnd={`url(#head-${a.tone})`}
                  className="animate-flow" opacity={0.95} />
            <rect x={mid - 58} y={a.lane - 10} width={116} height={19} rx={4}
                  fill="#0F1620" stroke={color} strokeWidth={0.75} opacity={0.95} />
            <text x={mid} y={a.lane + 3.5} fill={color} fontSize={10} textAnchor="middle" className="font-mono">
              {a.topic}
            </text>
          </g>
        );
      })}

      {/* ── Veri katmanı ────────────────────────────────────────────────── */}
      {NODES.map((n) => {
        const dbName = ['orders_db', 'inventory_db', 'payments_db', 'notifications_db'][NODES.indexOf(n)];
        const touched = states[n.key] === 'done' || states[n.key] === 'active' || states[n.key] === 'failed';
        return (
          <g key={`db-${n.key}`}>
            <line x1={CX[n.key]} y1={BUS.y + BUS.h} x2={CX[n.key]} y2={DB.y}
                  stroke="#2A3646" strokeWidth={1} strokeDasharray="2 4" />
            <rect x={n.x + 20} y={DB.y} width={BOX.w - 40} height={DB.h} rx={5}
                  fill="rgba(20,184,166,0.07)"
                  stroke={touched ? 'rgba(20,184,166,0.75)' : '#2A3646'} strokeWidth={1}
                  style={{ transition: 'stroke 300ms' }} />
            <text x={CX[n.key]} y={DB.y + 25} fill="#14B8A6" fontSize={10.5} textAnchor="middle" className="font-mono">
              {dbName}
            </text>
            <text x={CX[n.key]} y={DB.y + 43} fill="#8FA0B5" fontSize={9.5} textAnchor="middle">
              PostgreSQL
            </text>
          </g>
        );
      })}

      {/* Stok göstergesi — inventory hangi miktarı işlediği görünsün */}
      {status && (
        <text x={CX.inventory} y={DB.y - 12} fill="#14B8A6" fontSize={10.5} textAnchor="middle"
              className="font-mono animate-fadeUp">
          {status === 'FAILED' && !paymentFailed ? 'stok değişmedi' : `${quantity} adet rezerve`}
        </text>
      )}

      {/* Redis — yalnızca payment-service kullanıyor (idempotency, 24s TTL) */}
      <g>
        <line x1={CX.payment + 60} y1={BUS.y + BUS.h} x2={CX.payment + 60} y2={DB.y + DB.h + 18}
              stroke="#2A3646" strokeWidth={1} strokeDasharray="2 4" />
        <rect x={CX.payment + 6} y={DB.y + DB.h + 18} width={108} height={30} rx={5}
              fill="rgba(20,184,166,0.07)" stroke="#2A3646" strokeWidth={1} />
        <text x={CX.payment + 60} y={DB.y + DB.h + 37} fill="#14B8A6" fontSize={10} textAnchor="middle" className="font-mono">
          Redis · idempotency
        </text>
      </g>
    </svg>
  );
}
