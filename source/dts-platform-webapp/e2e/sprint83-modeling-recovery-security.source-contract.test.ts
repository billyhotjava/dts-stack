import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./sprint83-modeling-recovery-security.spec.ts", import.meta.url), "utf8");

test("recovery acceptance uses the pinned source-only and three-way fixtures", () => {
	for (const fixture of ["fx02-source-only-enforced", "fx04-three-way-drift/base", "fx04-three-way-drift/incoming"]) {
		assert.ok(source.includes(fixture), `missing fixture: ${fixture}`);
	}
	for (const contract of ["DECLARED", "DBT_MANAGED", "SOURCE_SEMANTICS_INCOMPLETE", "effectiveSqlChecksum"]) {
		assert.ok(source.includes(contract), `missing source-only contract: ${contract}`);
	}
});

test("recovery acceptance proves canonical preview, replay, retry and forward undo", () => {
	for (const endpoint of ["dbt/archive/inspect", "dbt/preview", "dbt/apply", "/retry", "dbt/forward-undo"]) {
		assert.ok(source.includes(endpoint), `missing canonical endpoint: ${endpoint}`);
	}
	for (const evidence of ["REPLAY", "preAttemptPins", "expectedCurrentRevisions", "overallRun"]) {
		assert.ok(source.includes(evidence), `missing recovery evidence: ${evidence}`);
	}
});

test("security acceptance fails closed and checks the retired control plane", () => {
	for (const guard of [
		"E2E_MODELING_WRITE_ALLOWED",
		"E2E_MODELING_CONTEXT_OPTION",
		"E2E_MODELING_PREFIX",
		"E2E_MODELING_CLEANUP_MODE",
		"E2E_MODELING_DOMAIN_OPTION",
	]) {
		assert.ok(source.includes(guard), `missing authorization guard: ${guard}`);
	}
	for (const retired of ["/api/etl/dbt/run", "/api/etl/dbt/preview", "/api/etl/dbt/files"]) {
		assert.ok(source.includes(retired), `missing retired boundary assertion: ${retired}`);
	}
	assert.ok(source.includes("malicious-model-name"));
	assert.doesNotMatch(source, /test\.skip|describe\.skip/);
});
