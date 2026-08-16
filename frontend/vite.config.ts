import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // @ -> src 路径别名（与 tsconfig paths 保持一致）
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      // 开发环境代理：/api -> 后端 8080
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
