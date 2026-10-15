import { readFileSync } from 'node:fs';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The app's version is the phone's (version.properties at the repository root): one product, one
// number, sent to the backends as X-App-Version.
function appVersion(): string {
  try {
    const props = readFileSync(new URL('../version.properties', import.meta.url), 'utf8');
    const field = (k: string) => new RegExp(`^${k}=(.*)$`, 'm').exec(props)?.[1]?.trim() ?? '';
    const pre = field('PRE_RELEASE');
    return `${field('MAJOR')}.${field('MINOR')}.${field('PATCH')}${pre ? `-${pre}` : ''}`;
  } catch {
    return '0.0.0';
  }
}

// Served beside the current terminal while it is built: pro-chart.com/next/. When it replaces the
// terminal, build with WEB_BASE=/terminal/. In development the relay routes (/up, /api) are
// proxied to pro-chart.com so the dev server talks to the same backends the site does.
const upstream = process.env.WEB_UPSTREAM ?? 'https://pro-chart.com';

export default defineConfig({
  base: process.env.WEB_BASE ?? '/next/',
  plugins: [react()],
  define: { __APP_VERSION__: JSON.stringify(appVersion()) },
  build: {
    outDir: 'dist',
    target: 'es2022',
    sourcemap: true,
    chunkSizeWarningLimit: 900,
  },
  server: {
    port: 5180,
    proxy: {
      '/up': { target: upstream, changeOrigin: true, secure: true, ws: true },
      '/api': { target: upstream, changeOrigin: true, secure: true },
    },
  },
});
