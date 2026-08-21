/**
 * Backend ile tek temas noktasi.
 * Tum istekler /api/... uzerinden gider; bu yolu uretimde nginx, gelistirmede
 * Vite dev proxy karsilar. Bu sayede tarayici acisindan her sey ayni origin'de
 * kalir ve Java servislerine CORS yapilandirmasi eklemeye gerek kalmaz.
 */

/** inventory-service /products yaniti */
export interface Product {
  id: string;
  name: string;
  availableStock: number;
  reservedStock: number;
  version: number;
}

/** order-service OrderStatus enum'u (COMPLETED kodda hic atanmiyor) */
export type OrderStatus = 'PENDING' | 'STOCK_RESERVED' | 'PAYMENT_COMPLETED' | 'COMPLETED' | 'FAILED';

/** order-service /orders yaniti */
export interface Order {
  id: string;
  customerId: string;
  productId: string;
  quantity: number;
  status: OrderStatus;
  createdAt: string | null;
  updatedAt: string | null;
}

/** Diyagramdaki 4 servis */
export type ServiceKey = 'order' | 'inventory' | 'payment' | 'notify';

export type HealthState = 'up' | 'down' | 'unknown';

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  if (!res.ok) {
    // Backend GlobalExceptionHandler JSON dondurur; okunabilirse onu goster
    let detail = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      detail = body.message ?? body.error ?? detail;
    } catch { /* govde JSON degilse varsayilan mesaj kalir */ }
    throw new Error(detail);
  }
  return res.json() as Promise<T>;
}

export const api = {
  getProducts: () => request<Product[]>('/api/products'),

  getProduct: (id: string) => request<Product>(`/api/products/${id}`),

  createOrder: (productId: string, quantity: number, customerId = 'DEMO-UI') =>
    request<Order>('/api/orders', {
      method: 'POST',
      body: JSON.stringify({ customerId, productId, quantity }),
    }),

  getOrder: (id: string) => request<Order>(`/api/orders/${id}`),

  /** Servis sagligi — /actuator/health yalnizca status alanini kullaniyoruz */
  getHealth: async (key: ServiceKey): Promise<HealthState> => {
    try {
      const r = await fetch(`/api/health/${key}`);
      // Spring, bagimlilik DOWN oldugunda 503 + govde dondurur; govdeyi yine okuyalim
      const body = await r.json().catch(() => null);
      return body?.status === 'UP' ? 'up' : 'down';
    } catch {
      return 'down';
    }
  },
};
