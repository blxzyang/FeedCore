import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        proxy: {
            // 代理所有 /api 请求到 Java 后端
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            }
        }
    }
})