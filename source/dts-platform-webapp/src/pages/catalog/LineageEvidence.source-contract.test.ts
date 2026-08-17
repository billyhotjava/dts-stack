import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SHARED = readFileSync(new URL("./lineageShared.tsx", import.meta.url), "utf8");
const IMPACT = readFileSync(new URL("./LineageImpactPage.tsx", import.meta.url), "utf8");
const GRAPH = readFileSync(new URL("./LineageGraphPage.tsx", import.meta.url), "utf8");
const COLUMNS = readFileSync(new URL("./LineageColumnsPage.tsx", import.meta.url), "utf8");

test("lineage sections preserve the selected dataset and filter context", () => {
	assert.match(SHARED, /const query = searchParams\.toString\(\)/);
	for (const page of [IMPACT, GRAPH, COLUMNS]) {
		assert.match(page, /searchParams\.get\("datasetId"\)/);
		assert.match(page, /setSearchParams\(params, \{ replace: true \}\)/);
	}
});

test("lineage pages explain missing evidence instead of presenting an invented graph", () => {
	for (const page of [IMPACT, GRAPH, COLUMNS]) {
		assert.match(page, /lineageEvidenceDescription/);
		assert.match(page, /血缘证据不完整/);
	}
	assert.match(IMPACT, /不补造 ODS 或字段关系/);
});
