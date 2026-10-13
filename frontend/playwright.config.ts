import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  testMatch: "**/*.spec.ts",
  workers: 1,
  timeout: 90000,
  use: {
    baseURL: "http://localhost:5173",
    actionTimeout: 15000,
    navigationTimeout: 20000,
    browserName: "chromium",
    launchOptions: { executablePath: process.env.CHROMIUM_BIN },
    trace: "off",
    screenshot: "off",
    video: "off",
  },
  webServer: {
    command: "npm run dev",
    url: "http://localhost:5173",
    reuseExistingServer: !process.env.CI,
    timeout: 30000,
  },
});
