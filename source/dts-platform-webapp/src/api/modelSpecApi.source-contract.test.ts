import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const API_URL = new URL("./modelSpecApi.ts", import.meta.url);

test("canonical ModelSpec client owns one non-vnext CRUD surface and strong CAS updates", () => {
	assert.equal(existsSync(API_URL), true, "canonical ModelSpec client is missing");
	const source = readFileSync(API_URL, "utf8");
	assert.match(source, /const MODEL_SPEC_RESOURCE = "\/modeling\/model-specs"/);
	assert.doesNotMatch(source, /\/modeling\/vnext\/model-specs/);
	assert.match(source, /export const listModelSpecs/);
	assert.match(source, /export const getModelSpec/);
	assert.match(source, /export const getModelSpecRevision/);
	assert.match(
		source,
		/`\$\{MODEL_SPEC_RESOURCE\}\/\$\{encodeURIComponent\(id\)\}\/revisions\/\$\{encodeURIComponent\(String\(revision\)\)\}`/,
	);
	assert.match(source, /export const createModelSpec/);
	assert.match(source, /export const updateModelSpec/);
	assert.match(source, /headers: \{ "If-Match": toModelSpecEtag\(expected\) \}/);
});

test("canonical client keeps legacy reads in the response union but excludes them from writes", () => {
	assert.equal(existsSync(API_URL), true, "canonical ModelSpec client is missing");
	const source = readFileSync(API_URL, "utf8");
	assert.match(source, /ModelSpecView/);
	assert.match(source, /CreateModelSpecCommand/);
	assert.match(source, /UpdateModelSpecCommand/);
	assert.match(source, /ModelSpecCasToken/);
	assert.match(source, /ModelSpecRevisionConflictDetails/);
});
