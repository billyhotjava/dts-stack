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
	assert.match(source, /export const createDimensionModel/);
	assert.match(source, /url: `\$\{MODEL_SPEC_RESOURCE\}\/dimension`/);
	assert.match(source, /definitionBinding:/);
	assert.match(source, /mode: "CREATE"/);
	assert.match(source, /mode: "EXISTING"/);
	assert.match(source, /currentModelSpec: CanonicalModelSpecView/);
	assert.match(source, /export const getDimensionModelOperation/);
	assert.match(source, /dimension\/operations\/\$\{encodeURIComponent\(operationId\)\}/);
	assert.match(source, /export const updateModelSpec/);
	assert.match(source, /headers: \{ "If-Match": toModelSpecEtag\(expected\) \}/);
});

test("canonical client carries the additive warehouse-layer selection through its command types", () => {
	assert.equal(existsSync(API_URL), true, "canonical ModelSpec client is missing");
	const source = readFileSync(API_URL, "utf8");
	assert.match(source, /CreateModelSpecCommand/);
	assert.match(source, /UpdateModelSpecCommand/);
	assert.match(source, /createDimensionModel = /);
	assert.match(source, /modelSpec:/);
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

test("plan-owned release candidate lifecycle exposes create lock retry and publish with CAS", () => {
	const source = readFileSync(API_URL, "utf8");
	assert.match(source, /export const createReleaseCandidate/);
	assert.match(source, /export const lockReleaseCandidate/);
	assert.match(source, /export const retryReleaseCandidate/);
	assert.match(source, /export const publishReleaseCandidate/);
	assert.match(source, /releaseCandidateItemUrl\(planId, expected\.id, "\/publish"\)/);
	assert.match(source, /headers: releaseCandidateWriteHeaders\(idempotencyKey, expected\)/);
});
