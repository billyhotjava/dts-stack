import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./LegacyDataIntegrationRedirect.tsx", import.meta.url), "utf8");

test("legacy ingestion redirect keeps explicit intent without propagating arbitrary query or hash values", () => {
	assert.match(SOURCE, /foundation\/data-sources\/access\/new/);
	assert.match(SOURCE, /foundation\/data-sources\/access\/\$\{encodeURIComponent\(id\)\}/);
	assert.match(SOURCE, /query\.set\("tab", "history"\)/);
	assert.match(SOURCE, /query\.set\("mode", "edit"\)/);
	assert.match(SOURCE, /newTaskSearch\(location\.search\)/);
	assert.match(SOURCE, /kind === "database" \|\| kind === "api" \|\| kind === "file"/);
	assert.match(SOURCE, /new URLSearchParams\(\)/);
	assert.doesNotMatch(SOURCE, /location\.hash/);
	assert.doesNotMatch(SOURCE, /\$\{location\.search\}/);
	assert.doesNotMatch(SOURCE, /const query = new URLSearchParams\(location\.search\)/);
});
