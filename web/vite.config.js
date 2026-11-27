import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    proxy: {
      '/api': { target: 'http://localhost:8081', changeOrigin: true, rewrite: p => p.replace(/^\/api/, '') },
      '/auth': { target: 'http://localhost:8081', changeOrigin: true },
      '/chem': { target: 'http://localhost:8081', changeOrigin: true },
      '/system': { target: 'http://localhost:8081', changeOrigin: true },
      '/biz': { target: 'http://localhost:8081', changeOrigin: true },
      // 健康检查探针端点（生产由 jar 同源提供）
      '/health': { target: 'http://localhost:8081', changeOrigin: true }
    }
  }
})
