import fs from "node:fs";
import { defineConfig } from "@playwright/test";

const executablePath = process.env.CHROME95_EXECUTABLE_PATH;
const storageStatePath = process.env.E2E_STORAGE_STATE;
const cookieJarPath = process.env.E2E_COOKIE_JAR;
const hostResolverRules = process.env.E2E_HOST_RESOLVER_RULES;

if (!executablePath) {
	throw new Error("CHROME95_EXECUTABLE_PATH is required for the Sprint-74 Chrome 95 acceptance suite");
}

const cookieStorageState = cookieJarPath
	? {
			cookies: fs
				.readFileSync(cookieJarPath, "utf8")
				.split(/\r?\n/)
				.filter((line) => line && (!line.startsWith("#") || line.startsWith("#HttpOnly_")))
				.map((line) => {
					const httpOnly = line.startsWith("#HttpOnly_");
					const columns = line.replace(/^#HttpOnly_/, "").split("\t");
					return {
						name: columns[5],
						value: columns[6],
						domain: columns[0],
						path: columns[2],
						expires: Number(columns[4]) || -1,
						httpOnly,
						secure: columns[3] === "TRUE",
						sameSite: "Lax" as const,
					};
				}),
			origins: [],
		}
	: undefined;

export default defineConfig({
	testDir: "./e2e",
	testMatch: ["sprint74-modeling-journey-real.spec.ts"],
	timeout: 90_000,
	expect: { timeout: 15_000 },
	retries: 0,
	workers: 1,
	reporter: [["list"]],
	outputDir: "test-results/chrome95-sprint74",
	use: {
		baseURL: process.env.E2E_BASE_URL ?? "https://bi.yuzhicloud.com",
		...(storageStatePath ? { storageState: storageStatePath } : cookieStorageState ? { storageState: cookieStorageState } : {}),
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
		viewport: { width: 1366, height: 900 },
	},
});
