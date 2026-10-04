import { defineConfig } from "@playwright/test";

export default defineConfig({
	testDir: "./e2e",
	testMatch: "sprint94-governed-bi-publication.mock.spec.ts",
	timeout: 45_000,
	retries: 0,
	workers: 1,
	reporter: [["list"]],
	outputDir: "/tmp/dts-sprint94-playwright-results",
	use: {
		baseURL: process.env.E2E_BASE_URL ?? "http://127.0.0.1:4194",
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
