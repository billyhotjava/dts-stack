import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const API_URL = new URL("./modelingApi.ts", import.meta.url);

test("modeling api exposes the vNext object, plan and ModelSpec endpoints", () => {
	assert.equal(existsSync(API_URL), true, "modelingApi.ts should exist");
	const source = readFileSync(API_URL, "utf8");

	for (const endpoint of ["/modeling/vnext/business-objects", "/modeling/vnext/plans", "/modeling/vnext/model-specs"]) {
		assert.equal(source.includes(`url: "${endpoint}"`), true, `${endpoint} should be declared`);
	}
	assert.equal(source.includes("/modeling/vnext/model-specs/${encodeURIComponent(id)}/dependencies"), true);
	for (const name of [
		"listModelingBusinessObjects",
		"createModelingBusinessObject",
		"updateModelingBusinessObject",
		"listModelingPlans",
		"createModelingPlan",
		"listModelingModelSpecs",
		"createModelingModelSpec",
		"updateModelingModelSpec",
		"getModelSpecDependencies",
	]) {
		assert.match(source, new RegExp(`export const ${name}`));
	}
});

test("modeling write requests carry revision and idempotency key", () => {
	const source = readFileSync(API_URL, "utf8");
	assert.match(source, /revision:\s*number/);
	assert.match(source, /idempotencyKey:\s*string/);
	assert.match(source, /MODEL_REVISION_CONFLICT/);
});

test("modeling api exposes dbt import, artifacts and drift endpoints", () => {
	const source = readFileSync(API_URL, "utf8");
	for (const endpoint of ["/modeling/vnext/dbt/import", "/modeling/vnext/model-specs/${encodeURIComponent(id)}/artifacts", "/modeling/vnext/model-specs/${encodeURIComponent(id)}/drift"]) {
		assert.equal(source.includes(endpoint), true, `${endpoint} should be declared`);
	}
	for (const code of ["DBT_MANIFEST_INVALID", "DBT_MODEL_NOT_FOUND", "DBT_ARTIFACT_UNREADABLE"]) {
		assert.match(source, new RegExp(code));
	}
});

test("modeling api exposes compile, run evidence and lineage endpoints", () => {
	const source = readFileSync(API_URL, "utf8");
	for (const endpoint of [
		"/modeling/vnext/model-specs/${encodeURIComponent(id)}/compile",
		"/modeling/vnext/runs",
		"/modeling/vnext/runs/${encodeURIComponent(id)}",
		"/modeling/vnext/lineage/${encodeURIComponent(id)}",
	]) {
		assert.equal(source.includes(endpoint), true, `${endpoint} should be declared`);
	}
	for (const name of ["compileModelSpec", "createModelingRun", "getModelingRun", "getModelingLineage"]) {
		assert.match(source, new RegExp(`export const ${name}`));
	}
});

test("modeling api exposes the external run callback contract", () => {
	const source = readFileSync(API_URL, "utf8");
	assert.match(source, /export type ModelingRunCallback/);
	assert.match(source, /callbackModelingRun/);
	assert.equal(source.includes("/modeling/vnext/runs/${encodeURIComponent(id)}/callback"), true);
	for (const field of ["state", "addaxTaskId", "airflowRunId", "dbtRunId", "message"]) {
		assert.match(source, new RegExp(`${field}\\??:`));
	}
});

test("modeling api exposes the controlled release gate", () => {
	const source = readFileSync(API_URL, "utf8");
	assert.match(source, /export type ModelingReleaseGate/);
	assert.match(source, /getModelSpecReleaseGate/);
	assert.equal(source.includes("/modeling/vnext/model-specs/${encodeURIComponent(id)}/release-gate"), true);
});
