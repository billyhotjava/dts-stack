import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const INGESTION_API_SOURCE = readFileSync(new URL("../../../api/ingestion.ts", import.meta.url), "utf8");

test("file staging client exposes the supported parse, quality and correction lifecycle", () => {
	for (const contract of [
		/parseStagingFile[\s\S]*\/ingestion\/tasks\/\$\{taskId\}\/parse/,
		/preCheckStaging[\s\S]*\/ingestion\/tasks\/\$\{taskId\}\/pre-check/,
		/reCheckStaging[\s\S]*\/ingestion\/tasks\/\$\{taskId\}\/re-check/,
		/updateStagingCell[\s\S]*\/ingestion\/tasks\/\$\{taskId\}\/staging\/\$\{rowNum\}/,
		/getStagingRows[\s\S]*\/ingestion\/tasks\/\$\{taskId\}\/staging/,
		/dropStaging[\s\S]*\/ingestion\/tasks\/\$\{taskId\}\/staging/,
	]) {
		assert.match(INGESTION_API_SOURCE, contract);
	}
});

test("file staging client does not revive the removed submit endpoint", () => {
	assert.doesNotMatch(INGESTION_API_SOURCE, /submitStaging|\/staging\/submit|\/tasks\/\$\{taskId\}\/submit/);
});
