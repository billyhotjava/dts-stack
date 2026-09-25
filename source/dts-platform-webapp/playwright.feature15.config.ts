import { defineConfig } from "@playwright/test";
export default defineConfig({
    testDir: "./e2e", testMatch: "feature15-isolation.mock.spec.ts", timeout: 90_000,
    expect: { timeout: 15_000 }, workers: 1, retries: 0, reporter: [["list"]],
    outputDir: "/tmp/dts-f15-browser",
    webServer: { command: "pnpm exec vite preview --host 127.0.0.1 --port 4195", url: "http://127.0.0.1:4195", reuseExistingServer: true },
    use: { baseURL: "http://127.0.0.1:4195", headless: true, viewport: { width: 1366, height: 768 },
        launchOptions: { executablePath: process.env.CHROME95_EXECUTABLE_PATH || process.env.PLAYWRIGHT_EXECUTABLE_PATH || "/usr/bin/google-chrome", args: ["--no-sandbox", "--disable-dev-shm-usage"] },
        trace: "retain-on-failure", screenshot: "only-on-failure" },
});
