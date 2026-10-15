import { defineConfig } from 'vitest/config';

export default defineConfig({
  base: '/next/',
  define: { __APP_VERSION__: JSON.stringify('0.0.0-test') },
  test: { environment: 'jsdom', include: ['src/**/*.test.ts', 'src/**/*.test.tsx'] },
});
