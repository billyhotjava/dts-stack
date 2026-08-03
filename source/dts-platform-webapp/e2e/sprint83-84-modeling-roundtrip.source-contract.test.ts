import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./sprint83-84-modeling-roundtrip.spec.ts", import.meta.url), "utf8");

test("authorized modeling E2E fails closed before any write", () => {
	for (const contract of [
		'E2E_MODELING_WRITE_ALLOWED !== "true"',
		"E2E_MODELING_PLAN_OPTION",
		"E2E_MODELING_PREFIX",
		"E2E_MODELING_CLEANUP_MODE",
		'cleanupMode !== "retain"',
	]) {
		assert.match(source, new RegExp(contract.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
	}
	assert.doesNotMatch(source, /test\.skip|describe\.skip/);
});

test("roundtrip E2E covers the frozen Sprint-83 canonical chain", () => {
	for (const evidence of [
		"model-spec-imports/dbt/archive/inspect",
		"model-spec-imports/dbt/preview",
		"model-spec-imports/dbt/apply",
		"创建高级草稿",
		"提交实现",
		"release-candidates",
		"发布与物化",
		"physical-preview",
		"security/audit-logs",
	]) {
		assert.match(source, new RegExp(evidence));
	}
});

test("roundtrip evidence is pinned to this SQL draft, candidate and audit resources", () => {
	assert.ok(source.includes("name: /models\\/.*\\.sql$/i"));
	for (const evidence of [
		"releaseCandidateId",
		"candidateEntry",
		"implementationId",
		"resource: resourceId",
		"operationCode === action",
		"item?.resourceId === resourceId",
	]) {
		assert.ok(source.includes(evidence), `missing pinned evidence contract: ${evidence}`);
	}
});

test("roundtrip E2E detects every retired dbt browser control plane", () => {
	for (const retired of ["/api/etl/dbt/run", "/api/etl/dbt/preview", "/api/etl/dbt/files"]) {
		assert.match(source, new RegExp(retired.replaceAll("/", "\\/")));
	}
});
