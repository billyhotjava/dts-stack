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
