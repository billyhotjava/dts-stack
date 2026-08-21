import { defineConfig } from "@playwright/test";

export default defineConfig({
	testDir: "./e2e",
	testMatch: "sprint98-dashboard-authoring.mock.spec.ts",
	timeout: 90_000,
	expect: { timeout: 10_000 },
	retries: 0,
	workers: 1,
	reporter: [["list"]],
	outputDir: "/tmp/dts-sprint98-playwright-results",
	webServer: {
		command: "pnpm exec vite preview --host 127.0.0.1 --port 4198",
		url: "http://127.0.0.1:4198",
		reuseExistingServer: true,
		timeout: 30_000,
	},
	use: {
		baseURL: "http://127.0.0.1:4198",
		headless: true,
		ignoreHTTPSErrors: true,
		trace: "retain-on-failure",
		screenshot: "only-on-failure",
		launchOptions: process.env.PLAYWRIGHT_EXECUTABLE_PATH
			? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH, args: ["--no-sandbox", "--disable-dev-shm-usage"] }
			: undefined,
		viewport: { width: 1366, height: 768 },
	},
});
