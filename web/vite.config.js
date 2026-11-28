import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    // 首屏不再是一个整包：重型图表/3D/表格库各自成 chunk，路由页面按需加载（见 src/router/index.js）
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('echarts') || id.includes('zrender')) return 'echarts'
          if (id.includes('three')) return 'three'
          if (id.includes('xlsx')) return 'xlsx'
          if (id.includes('element-plus')) return 'element-plus'
          if (id.includes('vue-router') || id.includes('@vue/') || /[\\/]node_modules[\\/]vue[\\/]/.test(id)) return 'vue'
          return 'vendor'
        }
      }
    }
  },
  server: {
    port: 5174,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true, rewrite: p => p.replace(/^\/api/, '') },
      '/auth': { target: 'http://localhost:8080', changeOrigin: true },
      '/chem': { target: 'http://localhost:8080', changeOrigin: true },
      '/system': { target: 'http://localhost:8080', changeOrigin: true },
      '/biz': { target: 'http://localhost:8080', changeOrigin: true },
      // 健康检查探针端点（生产由 jar 同源提供）
      '/health': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
})
