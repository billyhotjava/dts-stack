import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./search-bar.tsx", import.meta.url), "utf8");

test("search results leave the platform router when opening metrics service pages", () => {
	assert.match(SOURCE, /path === "\/metrics" \|\| path\.startsWith\("\/metrics\/"\)/);
	assert.match(SOURCE, /window\.location\.assign\(path\)/);
});
