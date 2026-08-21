import type { HealthState } from '../api/client';

/** Saga akışı içindeki rolü — kutunun rengini belirler */
export type NodeState = 'idle' | 'active' | 'done' | 'failed';

const STROKE: Record<NodeState, string> = {
  idle:   '#425268',
  active: '#F5A524',
  done:   '#2ED47A',
  failed: '#FF5C5C',
};

const FILL: Record<NodeState, string> = {
  idle:   '#1C2634',
  active: 'rgba(245, 165, 36, 0.12)',
  done:   'rgba(46, 212, 122, 0.10)',
  failed: 'rgba(255, 92, 92, 0.12)',
};

const HEALTH_COLOR: Record<HealthState, string> = {
  up: '#2ED47A', down: '#FF5C5C', unknown: '#425268',
};

const HEALTH_LABEL: Record<HealthState, string> = {
  up: 'çalışıyor', down: 'erişilemiyor', unknown: 'durum bilinmiyor',
};

interface Props {
  x: number;
  y: number;
  w: number;
  h: number;
  /** Servis adı, ör. "order-service" */
  name: string;
  port: number;
  /** Servisin bu adımdaki görevi, ör. "stok rezerve eder" */
  role: string;
  state: NodeState;
  health: HealthState;
}

/**
 * Diyagramdaki tek bir mikroservis kutusu (SVG grubu).
 *
 * İki bağımsız gösterge taşır ve bunlar karıştırılmamalı:
 *  - Kenarlık rengi  → servisin O ANKİ SİPARİŞ akışındaki rolü (idle/active/done/failed)
 *  - Sağ üstteki nokta → servisin /actuator/health durumu (ayakta mı, değil mi)
 * Bir servis "ayakta" olup akışta henüz sırası gelmemiş olabilir.
 */
export function ServiceNode({ x, y, w, h, name, port, role, state, health }: Props) {
  const active = state === 'active';

  return (
    <g>
      <title>{`${name}:${port} — ${HEALTH_LABEL[health]}`}</title>

      {/* Aktif kutunun arkasındaki yumuşak parıltı */}
      {active && (
        <rect
          x={x - 4} y={y - 4} width={w + 8} height={h + 8} rx={10}
          fill="none" stroke={STROKE.active} strokeWidth={1} opacity={0.35}
          className="animate-pulse"
        />
      )}

      <rect
        x={x} y={y} width={w} height={h} rx={7}
        fill={FILL[state]} stroke={STROKE[state]} strokeWidth={active ? 2 : 1.25}
        style={{ transition: 'fill 260ms ease-out, stroke 260ms ease-out, stroke-width 260ms ease-out' }}
      />

      {/* Sağlık ışığı */}
      <circle cx={x + w - 14} cy={y + 15} r={4} fill={HEALTH_COLOR[health]}>
        {health === 'down' && (
          <animate attributeName="opacity" values="1;0.25;1" dur="1.2s" repeatCount="indefinite" />
        )}
      </circle>

      <text x={x + 14} y={y + 21} fill="#E8EDF4" fontSize={13} fontWeight={600} className="font-mono">
        {name}
      </text>
      <text x={x + 14} y={y + 38} fill="#8FA0B5" fontSize={10.5} className="font-mono">
        :{port}
      </text>
      <text x={x + 14} y={y + 58} fill={state === 'idle' ? '#8FA0B5' : STROKE[state]} fontSize={11}>
        {role}
      </text>
    </g>
  );
}
