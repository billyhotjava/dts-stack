import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SETUP = readFileSync(new URL("./auth.setup.ts", import.meta.url), "utf8");

test("Playwright auth setup supports cookie-only portal sessions", () => {
	assert.match(SETUP, /res\.headers\.getSetCookie\(\)/);
	assert.match(SETUP, /authenticated: true/);
	assert.match(SETUP, /cookies[,\:]/);
	assert.match(SETUP, /portal_session/);
});
