import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./modelSpecImportApi.ts", import.meta.url), "utf8");

test("model import preview and apply truth are queried from separate canonical resources", () => {
	assert.match(source, /getModelSpecImportPreviewRun/);
	assert.match(source, /getModelSpecImportApplyResult/);
	assert.match(source, /encodeURIComponent\(runId\)}\/apply/);
	assert.match(source, /overallRun: ModelSpecImportRunResult/);
});

test("model import requests opt out of global toast without escaping type checking", () => {
	assert.match(source, /QuietAxiosRequestConfig/);
	assert.match(source, /quietRequest/);
	assert.doesNotMatch(source, /as any/);
});

test("dbt archive inspection uses the canonical multipart contract before JSON preview", () => {
	assert.match(source, /inspectDbtModelArchive/);
	assert.match(source, /new FormData\(\)/);
	assert.match(source, /data\.append\("archive", archive\)/);
	assert.match(source, /\/dbt\/archive\/inspect/);
	assert.match(source, /api\.post<DbtArchiveInspection>/);
	assert.match(source, /inspectionProof/);
	assert.match(source, /proofExpiresAt/);
	assert.match(source, /semanticOverrides/);
	assert.match(source, /renameMappings/);
});

test("apply response exposes only the canonical status algebra", () => {
	assert.match(source, /"RUNNING" \| "SUCCESS" \| "PARTIAL" \| "FAILED" \| "BLOCKED"/);
	assert.match(source, /selected: number/);
	assert.match(source, /pending: number/);
	assert.match(source, /succeeded: number/);
	for (const persistedFailureFact of [
		"stage",
		"category",
		"retryable",
		"correlationId",
		"dependencyUniqueId",
	]) {
		assert.match(source, new RegExp(`${persistedFailureFact}\\??:`));
	}
	assert.doesNotMatch(source, /"SUCCEEDED"|"REPLAYED"|replayed: number/);
});

test("reimport decisions and forward undo reuse the canonical import resources", () => {
	assert.match(source, /conflictResolutions: Record<string, ModelSpecImportConflictResolution>/);
	assert.match(source, /forwardUndoModelSpecImport/);
	assert.match(source, /\/dbt\/forward-undo/);
	assert.match(source, /expectedCurrentRevisions/);
});
