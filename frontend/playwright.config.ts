import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: { channel: 'chrome', viewport: { width: 402, height: 874 }, baseURL: 'http://127.0.0.1:5178', screenshot: 'only-on-failure' },
  webServer: { command: 'npm run dev -- --port 5178 --strictPort', url: 'http://127.0.0.1:5178', reuseExistingServer: true },
})
