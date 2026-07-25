import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./modelSpecImportApi.ts", import.meta.url), "utf8");

test("model import preview and apply truth are queried from separate canonical resources", () => {
	assert.match(source, /getModelSpecImportPreviewRun/);
	assert.match(source, /getModelSpecImportApplyResult/);
	assert.match(source, /encodeURIComponent\(runId\)}\/apply/);
	assert.doesNotMatch(source, /latestAttempt|applyResult/);
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
	assert.match(source, /api\.post<ModelPackageJson>/);
});
