import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const backend = process.env.RENTPILOT_API_PROXY_TARGET || 'http://127.0.0.1:8080'
const proxy = { '/api': { target: backend, changeOrigin: true }, '/_AMapService': { target: backend, changeOrigin: true } }
export default defineConfig({ plugins: [vue()], server: { host: '127.0.0.1', proxy }, preview: { proxy } })
