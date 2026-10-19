import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: '127.0.0.1',
    // Same-origin in the browser: /api/* is proxied to the Spring Boot API,
    // so the frontend never needs CORS configuration. Playwright's baseURL is
    // http://localhost:5173 and page.waitForResponse(...'/api/orders'...) sees
    // the proxied URLs.
    proxy: {
      '/api': {
        target: 'http://localhost:8080', // http://localhost:5173/api/... -> http://localhost:8080/api... (same origin) ==> no cors needed.
        changeOrigin: false,
      },
    },
  },
});

