import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const CONFIG = readFileSync(new URL("./playwright.config.ts", import.meta.url), "utf8");

test("Playwright can use an explicitly supplied Chrome executable", () => {
	assert.match(CONFIG, /PLAYWRIGHT_EXECUTABLE_PATH/);
	assert.match(CONFIG, /launchOptions/);
	assert.match(CONFIG, /executablePath/);
});
