import assert from "node:assert/strict";
import test from "node:test";
import { collectUnassignedModelIds, buildArchivePayload } from "./sqlModelArchive.helpers.ts";

// ── collectUnassignedModelIds ────────────────────────────────────────

test("collectUnassignedModelIds returns ids of models without planId", () => {
	const models = [
		{ id: "m1", planId: "p1", name: "assigned" },
		{ id: "m2", planId: undefined, name: "unassigned-1" },
		{ id: "m3", planId: "", name: "unassigned-2" },
		{ id: "m4", planId: "p2", name: "also-assigned" },
	];
	assert.deepEqual(collectUnassignedModelIds(models), ["m2", "m3"]);
});

test("collectUnassignedModelIds skips models without id", () => {
	const models = [
		{ planId: undefined, name: "no-id" },
		{ id: "m1", planId: undefined, name: "has-id" },
	];
	assert.deepEqual(collectUnassignedModelIds(models), ["m1"]);
});

test("collectUnassignedModelIds returns empty for all assigned", () => {
	const models = [
		{ id: "m1", planId: "p1", name: "a" },
		{ id: "m2", planId: "p2", name: "b" },
	];
	assert.deepEqual(collectUnassignedModelIds(models), []);
});

test("collectUnassignedModelIds handles empty input", () => {
	assert.deepEqual(collectUnassignedModelIds([]), []);
});

// ── buildArchivePayload ──────────────────────────────────────────────

test("buildArchivePayload copies model fields and overrides planId", () => {
	const model = {
		id: "m1",
		planId: "old-plan",
		name: "dim_node_type",
		alias: "node_type",
		layer: "DWD",
		sourceDataSourceId: "src-1",
		schemaName: "public",
		materialized: "table",
		tags: "project-management",
		description: "节点类型维度",
		sql: "select 1",
		enabled: true,
		status: "DRAFT",
	};
	const payload = buildArchivePayload(model, "new-plan");
	assert.equal(payload.planId, "new-plan");
	assert.equal(payload.name, "dim_node_type");
	assert.equal(payload.layer, "DWD");
	assert.equal(payload.sql, "select 1");
	assert.equal((payload as any).id, undefined);
});

test("buildArchivePayload handles model with minimal fields", () => {
	const model = { name: "bare_model" };
	const payload = buildArchivePayload(model, "p1");
	assert.equal(payload.planId, "p1");
	assert.equal(payload.name, "bare_model");
	assert.equal(payload.layer, undefined);
	assert.equal(payload.sql, undefined);
});
