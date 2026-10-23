import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'

export default defineConfig(({ mode }) => {
  const rootEnv = loadEnv(mode, resolve(process.cwd(), '..'), '')
  return {
    envDir: resolve(process.cwd(), '..'),
    envPrefix: ['VITE_', 'FRONTEND_'],
    plugins: [react()],
    server: {
      host: '127.0.0.1',
      port: Number(rootEnv.FRONTEND_PORT || 5173),
      strictPort: true,
    },
  }
})
