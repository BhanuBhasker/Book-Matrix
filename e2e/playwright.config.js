// Real-browser tests. Start the app first (mvn spring-boot:run), then: cd e2e && npx playwright test
const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  globalTeardown: require.resolve('./global-teardown.js'),
  timeout: 90_000,
  expect: { timeout: 8_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }], ['json', { outputFile: 'test-results/results.json' }]],
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    // Uses the Google Chrome installed on this Mac; set PW_CHANNEL=chromium to use Playwright's own build
    channel: process.env.PW_CHANNEL || 'chrome',
    headless: process.env.HEADED ? false : true,
    viewport: { width: 1366, height: 900 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
});
