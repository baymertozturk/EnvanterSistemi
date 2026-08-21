import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev sunucusu, uretimdeki nginx ile AYNI yollari kullanir (/api/...).
// Boylece "npm run dev" ile de CORS sorunu yasanmaz ve kodda ortam ayrimi olmaz.
const proxy = (target: string, rewriteTo: string) => ({
  target,
  changeOrigin: true,
  rewrite: (p: string) => p.replace(/^\/api\/[^/]+(\/[^?]*)?/, (_m, rest) => rewriteTo + (rest ?? '')),
});

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/health/order':     proxy('http://localhost:8081', '/actuator/health'),
      '/api/health/inventory': proxy('http://localhost:8082', '/actuator/health'),
      '/api/health/payment':   proxy('http://localhost:8083', '/actuator/health'),
      '/api/health/notify':    proxy('http://localhost:8084', '/actuator/health'),
      '/api/orders':   { target: 'http://localhost:8081', changeOrigin: true, rewrite: (p: string) => p.replace(/^\/api/, '') },
      '/api/products': { target: 'http://localhost:8082', changeOrigin: true, rewrite: (p: string) => p.replace(/^\/api/, '') },
    },
  },
});
