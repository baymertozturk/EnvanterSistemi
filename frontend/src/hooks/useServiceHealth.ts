import { useEffect, useState } from 'react';
import { api, type HealthState, type ServiceKey } from '../api/client';

const SERVICES: ServiceKey[] = ['order', 'inventory', 'payment', 'notify'];

/**
 * 4 servisin /actuator/health durumunu periyodik olarak yoklar.
 * Diyagramdaki saglik isiklarini besler.
 */
export function useServiceHealth(intervalMs = 10_000) {
  const [health, setHealth] = useState<Record<ServiceKey, HealthState>>({
    order: 'unknown', inventory: 'unknown', payment: 'unknown', notify: 'unknown',
  });

  useEffect(() => {
    let cancelled = false;

    const check = async () => {
      const results = await Promise.all(SERVICES.map((s) => api.getHealth(s)));
      if (cancelled) return;
      setHealth(Object.fromEntries(SERVICES.map((s, i) => [s, results[i]])) as Record<ServiceKey, HealthState>);
    };

    check();
    const id = setInterval(check, intervalMs);
    return () => { cancelled = true; clearInterval(id); };
  }, [intervalMs]);

  return health;
}
