import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./utils.tsx", import.meta.url), "utf8");

test("dynamic page imports exclude test modules from production bundles", () => {
	assert.match(SOURCE, /!\/src\/pages\/\*\*\/\*\.test\.tsx/);
	assert.match(SOURCE, /!\/src\/pages\/\*\*\/\*\.spec\.tsx/);
});
