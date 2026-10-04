import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const CONFIG = readFileSync(new URL("./playwright.config.ts", import.meta.url), "utf8");
const SPRINT67_CONFIG = readFileSync(new URL("./playwright.sprint67.chrome95.config.ts", import.meta.url), "utf8");

test("Playwright can use an explicitly supplied Chrome executable", () => {
	assert.match(CONFIG, /PLAYWRIGHT_EXECUTABLE_PATH/);
	assert.match(CONFIG, /launchOptions/);
	assert.match(CONFIG, /executablePath/);
});

test("Sprint 67 Chrome 95 can use real auth state and a local host resolver", () => {
	assert.match(SPRINT67_CONFIG, /E2E_STORAGE_STATE/);
	assert.match(SPRINT67_CONFIG, /E2E_HOST_RESOLVER_RULES/);
	assert.match(SPRINT67_CONFIG, /sprint67-f2-real\.spec\.ts/);
	assert.match(SPRINT67_CONFIG, /sprint67-f3-real\.spec\.ts/);
	assert.match(SPRINT67_CONFIG, /sprint67-f4-menu-convergence\.spec\.ts/);
	assert.match(SPRINT67_CONFIG, /sprint67-f6-real-journeys\.spec\.ts/);
});
