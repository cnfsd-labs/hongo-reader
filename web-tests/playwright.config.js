// @ts-check
const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    // A phone-sized Chromium viewport, like the Android WebView the scripts run in.
    ...devices['Pixel 7'],
    trace: 'retain-on-failure',
  },
});
