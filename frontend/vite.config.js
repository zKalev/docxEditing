import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // The backend's CORS config allows exactly this origin.
    strictPort: true,
  },
})
