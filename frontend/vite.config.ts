import { defineConfig } from 'vite'
import path from 'path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'


function figmaAssetResolver() {
  return {
    name: 'figma-asset-resolver',
    resolveId(id) {
      if (id.startsWith('figma:asset/')) {
        const filename = id.replace('figma:asset/', '')
        return path.resolve(__dirname, 'src/assets', filename)
      }
    },
  }
}

export default defineConfig({
  plugins: [
    figmaAssetResolver(),
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },

  // sockjs-client가 Node.js의 global 변수를 참조하므로
  // 브라우저 환경에서 globalThis로 대체해주는 폴리필
  define: {
    global: 'globalThis',
  },

  assetsInclude: ['**/*.svg', '**/*.csv'],

  server: {
    // 클라우드플레어 터널을 통한 외부 도메인 접근 허용
    // 이 설정 없으면 Vite가 보안상 외부 도메인 요청을 403으로 차단
    allowedHosts: [
      'app.dogpedia.store',
    ],
  },
})