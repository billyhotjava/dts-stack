import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./router-link.tsx", import.meta.url), "utf8");

test("metrics routes use browser navigation instead of the platform React router", () => {
	assert.match(SOURCE, /href === "\/metrics" \|\| href\.startsWith\("\/metrics\/"\)/);
	assert.match(SOURCE, /target=\{isSameOriginProxyEscape \? undefined : "_blank"\}/);
});
