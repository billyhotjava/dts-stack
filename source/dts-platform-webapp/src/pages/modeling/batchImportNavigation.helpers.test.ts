import assert from "node:assert/strict";
import test from "node:test";
import { extractImportedModelNames, resolveBatchImportNavigation } from "./batchImportNavigation.helpers.ts";

// ── extractImportedModelNames ────────────────────────────────────────

test("extractImportedModelNames filters only imported status", () => {
	const details = [
		{ name: "model_a", status: "imported" },
		{ name: "model_b", status: "skipped" },
		{ name: "model_c", status: "imported" },
		{ name: "model_d", status: "validation_failed" },
	];
	assert.deepEqual(extractImportedModelNames(details), ["model_a", "model_c"]);
});

test("extractImportedModelNames skips entries without name", () => {
	const details = [
		{ status: "imported" },
		{ name: "", status: "imported" },
		{ name: "valid", status: "imported" },
	];
	assert.deepEqual(extractImportedModelNames(details), ["valid"]);
});

test("extractImportedModelNames returns empty for non-array input", () => {
	assert.deepEqual(extractImportedModelNames(null as any), []);
	assert.deepEqual(extractImportedModelNames(undefined as any), []);
});

test("extractImportedModelNames handles empty array", () => {
	assert.deepEqual(extractImportedModelNames([]), []);
});

// ── resolveBatchImportNavigation ─────────────────────────────────────

test("resolveBatchImportNavigation selects first imported model in plan", () => {
	const models = [
		{ id: "id-a", name: "model_a", planId: "p1" },
		{ id: "id-b", name: "model_b", planId: "p1" },
		{ id: "id-c", name: "model_c", planId: "p2" },
	];
	const result = resolveBatchImportNavigation("p1", ["model_b", "model_a"], models);
	assert.equal(result.activeSpaceKey, "space-p1");
	assert.equal(result.activeModelKey, "id-b");
});

test("resolveBatchImportNavigation returns null keys when planId is empty", () => {
	const result = resolveBatchImportNavigation("", ["model_a"], []);
	assert.equal(result.activeSpaceKey, null);
	assert.equal(result.activeModelKey, null);
});

test("resolveBatchImportNavigation returns null model when no match found", () => {
	const models = [
		{ id: "id-a", name: "other_model", planId: "p1" },
	];
	const result = resolveBatchImportNavigation("p1", ["nonexistent"], models);
	assert.equal(result.activeSpaceKey, "space-p1");
	assert.equal(result.activeModelKey, null);
});

test("resolveBatchImportNavigation filters models by planId", () => {
	const models = [
		{ id: "id-a", name: "model_a", planId: "p2" },
	];
	const result = resolveBatchImportNavigation("p1", ["model_a"], models);
	assert.equal(result.activeSpaceKey, "space-p1");
	assert.equal(result.activeModelKey, null);
});

test("resolveBatchImportNavigation is case-insensitive on name matching", () => {
	const models = [
		{ id: "id-a", name: "Model_A", planId: "p1" },
	];
	const result = resolveBatchImportNavigation("p1", ["model_a"], models);
	assert.equal(result.activeModelKey, "id-a");
});
