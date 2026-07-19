import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (url: URL) => readFileSync(url, "utf8");
const DETAIL = read(new URL("./ModelSpecDetailPage.tsx", import.meta.url));
const SQL = read(new URL("./SqlModelingPage.tsx", import.meta.url));
const OPS = read(new URL("../ops/OpsInstancesPage.tsx", import.meta.url));
const API = read(new URL("../../api/modelSpecApi.ts", import.meta.url));

test("implementation handoff preserves canonical model identity and revision", () => {
	assert.match(DETAIL, /model-spec-enter-implementation/);
	assert.match(DETAIL, /modelSpecId=.*revision=.*implementationMode=/);
	assert.match(SQL, /canonical-model-implementation-context/);
	assert.match(SQL, /requestedModelSpecId/);
	assert.match(SQL, /window\.location\.hash\.indexOf\("\?"\)/);
	assert.match(API, /lifecycleUrl\(expected\.id, "\/compile"\)/);
	assert.match(API, /lifecycleUrl\(expected\.id, "\/tests"\)/);
	assert.match(API, /lifecycleUrl\(expected\.id, "\/publish"\)/);
});

test("failed dbt ops returns to the exact ModelSpec repair route", () => {
	assert.match(OPS, /searchParams\.get\("modelSpecId"\)/);
	assert.match(OPS, /ops-return-model-repair/);
	assert.match(OPS, /\/modeling\/models\/\$\{encodeURIComponent\(modelSpecId\)\}\?tab=design/);
	assert.doesNotMatch(OPS, /&modelId=/);
});
