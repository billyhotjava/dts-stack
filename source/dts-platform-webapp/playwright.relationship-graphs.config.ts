import { defineConfig, devices } from "@playwright/test";

const executablePath = process.env.PLAYWRIGHT_EXECUTABLE_PATH;

export default defineConfig({
	testDir: "./e2e",
	testMatch: "relationship-graphs-usability.mock.spec.ts",
	timeout: 60_000,
	expect: { timeout: 15_000 },
	retries: 0,
	workers: 1,
	reporter: [["list"]],
	outputDir: "/tmp/dts-relationship-graphs-results",
	use: {
		...devices["Desktop Chrome"],
		baseURL: process.env.E2E_BASE_URL ?? "http://127.0.0.1:4182",
		headless: true,
		...(executablePath ? { launchOptions: { executablePath } } : {}),
		trace: "retain-on-failure",
		screenshot: "only-on-failure",
	},
});
