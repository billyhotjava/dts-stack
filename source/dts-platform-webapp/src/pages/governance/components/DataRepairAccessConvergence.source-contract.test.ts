import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const REPAIR_SOURCE = readFileSync(new URL("./DataRepairTab.tsx", import.meta.url), "utf8");
const INGESTION_API_SOURCE = readFileSync(new URL("../../../api/ingestion.ts", import.meta.url), "utf8");

test("file pre-check hands off to the canonical data access workflow", () => {
	assert.match(REPAIR_SOURCE, /foundation\/data-sources\/access\/new\?kind=file/);
	assert.match(REPAIR_SOURCE, /foundation\/data-sources\/access\/\$\{encodeURIComponent/);
	assert.doesNotMatch(REPAIR_SOURCE, /uploadFile\(|submitFromStaging\(|StagingDataEditor/);
});

test("browser API no longer exposes legacy unclassified upload or direct staging submit", () => {
	assert.doesNotMatch(INGESTION_API_SOURCE, /\/ingestion\/files\/upload["`]/);
	assert.doesNotMatch(INGESTION_API_SOURCE, /\/ingestion\/files\/parse["`]/);
	assert.doesNotMatch(INGESTION_API_SOURCE, /\/submit["`]/);
	assert.match(INGESTION_API_SOURCE, /\/ingestion\/files\/upload-and-parse/);
});
