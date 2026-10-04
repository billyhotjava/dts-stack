import { defineConfig, devices } from "@playwright/test";

const executablePath = process.env.PLAYWRIGHT_EXECUTABLE_PATH;
const reportDir = process.env.SPRINT82_E2E_REPORT_DIR ?? "/tmp/dts-sprint82-playwright-report";
const outputDir = process.env.SPRINT82_E2E_OUTPUT_DIR ?? "/tmp/dts-sprint82-playwright-results";

export default defineConfig({
	testDir: "./e2e",
	timeout: 30_000,
	retries: 0,
	workers: 1,
	reporter: [["list"], ["html", { open: "never", outputFolder: reportDir }]],
	outputDir,
	use: {
		baseURL: process.env.E2E_BASE_URL ?? "http://127.0.0.1:4182",
		trace: "retain-on-failure",
		screenshot: "only-on-failure",
		...(executablePath ? { launchOptions: { executablePath } } : {}),
	},
	projects: [
		{
			name: "chromium",
			use: { ...devices["Desktop Chrome"] },
		},
	],
});
