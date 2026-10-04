import { defineConfig } from "@playwright/test";

export default defineConfig({
	testDir: "./e2e",
	testMatch: "sprint95-industrial-bi-authoring.mock.spec.ts",
	timeout: 90_000,
	expect: { timeout: 10_000 },
	retries: 0,
	workers: 1,
	reporter: [["list"]],
	outputDir: "/tmp/dts-sprint95-playwright-results",
	use: {
		actionTimeout: 10_000,
		baseURL: process.env.E2E_BASE_URL ?? "http://127.0.0.1:4195",
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
