import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  server: { host: true, allowedHosts: ['gillianjin0926.online', 'app.gillianjin0926.online'] },
  preview: { host: true, allowedHosts: ['gillianjin0926.online', 'app.gillianjin0926.online'] },
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
})
