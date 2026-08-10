import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// 백엔드가 세션 쿠키(JSESSIONID) 기반이므로, 개발 중에도 브라우저 입장에서
// same-origin 이 되도록 API 경로를 8080 으로 프록시한다.
// 이렇게 하면 CORS / SameSite 쿠키 설정을 건드릴 필요가 없다.
// 백엔드 API 는 전부 /api/** 아래에 있다 (SPA 라우트와 충돌 방지).
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
