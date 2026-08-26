import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  // ✅ 核心修改 1：设置基础路径为相对路径
  // 这样打包后的 APK 才能通过 file:// 协议正确加载 JS 和 CSS 资源
  base: './',

  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },

  // 打包配置
  build: {
    // 确保输出目录与 Capacitor 配置的 webDir 一致（默认为 dist）
    outDir: 'dist',
  },

  server: {
    // 这里保留 proxy 仅用于你在电脑浏览器上进行本地调试
    proxy: {
      '/api': {
        // ✅ 核心修改 2：调试时可以指向你的 ngrok 地址
        target: 'https://xxxx-xxx.ngrok-free.app',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      '/static': {
        target: 'https://xxxx-xxx.ngrok-free.app',
        changeOrigin: true,
      }
    }
  }
})