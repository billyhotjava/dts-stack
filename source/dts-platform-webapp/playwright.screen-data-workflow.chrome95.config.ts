import { defineConfig } from "@playwright/test";

const executablePath = process.env.CHROME95_EXECUTABLE_PATH;

if (!executablePath) {
	throw new Error("CHROME95_EXECUTABLE_PATH is required for the screen data workflow regression");
}

export default defineConfig({
	testDir: "./e2e",
	testMatch: "screen-data-workflow.chrome95.spec.ts",
	timeout: 60_000,
	expect: { timeout: 10_000 },
	retries: 0,
	workers: 1,
	reporter: [["list"]],
	outputDir: "test-results/screen-data-workflow-chrome95",
	use: {
		baseURL: process.env.E2E_BASE_URL ?? "http://127.0.0.1:4173",
		headless: true,
		ignoreHTTPSErrors: true,
		launchOptions: {
			executablePath,
			args: ["--no-sandbox", "--disable-dev-shm-usage"],
		},
		viewport: { width: 1366, height: 768 },
	},
});
