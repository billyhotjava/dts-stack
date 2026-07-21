import { defineConfig } from "@playwright/test";

const executablePath = process.env.CHROME95_EXECUTABLE_PATH;
const storageState = process.env.E2E_STORAGE_STATE;
const hostResolverRules = process.env.E2E_HOST_RESOLVER_RULES;

if (!executablePath) {
	throw new Error("CHROME95_EXECUTABLE_PATH is required for the Sprint-67 Chrome 95 regression suite");
}

export default defineConfig({
	testDir: "./e2e",
	testMatch: [
		"sprint67-warehouse-plan.spec.ts",
		"sprint67-f6-lifecycle.spec.ts",
		"sprint67-f2-real.spec.ts",
		"sprint67-f3-real.spec.ts",
		"sprint67-f4-menu-convergence.spec.ts",
		"sprint67-f6-real-journeys.spec.ts",
	],
	timeout: 60_000,
	expect: { timeout: 10_000 },
	retries: 0,
	workers: 1,
	reporter: [["list"]],
	outputDir: "test-results/chrome95-sprint67",
	use: {
		baseURL: process.env.E2E_BASE_URL ?? "http://127.0.0.1:4173",
		...(storageState ? { storageState } : {}),
		headless: true,
		ignoreHTTPSErrors: true,
		launchOptions: {
			executablePath,
			args: [
				"--no-sandbox",
				"--disable-dev-shm-usage",
				...(hostResolverRules ? [`--host-resolver-rules=${hostResolverRules}`] : []),
			],
		},
		viewport: { width: 1366, height: 768 },
	},
});
