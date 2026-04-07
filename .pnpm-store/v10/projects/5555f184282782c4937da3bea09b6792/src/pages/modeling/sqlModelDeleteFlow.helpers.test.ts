import assert from "node:assert/strict";
import test from "node:test";
import {
	applyDeletedModelSelection,
	applyBatchDeletedModelSelection,
	applyManualModelSelection,
	resolveRunsRequestAfterSelection,
} from "./sqlModelDeleteFlow.helpers.ts";

// ── applyDeletedModelSelection ───────────────────────────────────────

test("applyDeletedModelSelection clears active when deleted model matches", () => {
	const result = applyDeletedModelSelection({
		activeModelKey: "m1",
		deletedModelKey: "m1",
	});
	assert.equal(result.nextActiveModelKey, null);
	assert.equal(result.suppressAutoSelect, true);
});

test("applyDeletedModelSelection keeps active when deleted model differs", () => {
	const result = applyDeletedModelSelection({
		activeModelKey: "m1",
		deletedModelKey: "m2",
	});
	assert.equal(result.nextActiveModelKey, "m1");
	assert.equal(result.suppressAutoSelect, false);
});

test("applyDeletedModelSelection keeps null active unchanged", () => {
	const result = applyDeletedModelSelection({
		activeModelKey: null,
		deletedModelKey: "m1",
	});
	assert.equal(result.nextActiveModelKey, null);
	assert.equal(result.suppressAutoSelect, false);
});

test("applyDeletedModelSelection handles null deleted key", () => {
	const result = applyDeletedModelSelection({
		activeModelKey: "m1",
		deletedModelKey: null,
	});
	assert.equal(result.nextActiveModelKey, "m1");
	assert.equal(result.suppressAutoSelect, false);
});

// ── applyBatchDeletedModelSelection ──────────────────────────────────

test("applyBatchDeletedModelSelection clears active when it is among deleted keys", () => {
	const result = applyBatchDeletedModelSelection({
		activeModelKey: "m2",
		deletedModelKeys: ["m1", "m2", "m3"],
	});
	assert.equal(result.nextActiveModelKey, null);
	assert.equal(result.suppressAutoSelect, true);
});

test("applyBatchDeletedModelSelection keeps active when not in deleted set", () => {
	const result = applyBatchDeletedModelSelection({
		activeModelKey: "m4",
		deletedModelKeys: ["m1", "m2", "m3"],
	});
	assert.equal(result.nextActiveModelKey, "m4");
	assert.equal(result.suppressAutoSelect, false);
});

test("applyBatchDeletedModelSelection handles empty deleted keys", () => {
	const result = applyBatchDeletedModelSelection({
		activeModelKey: "m1",
		deletedModelKeys: [],
	});
	assert.equal(result.nextActiveModelKey, "m1");
	assert.equal(result.suppressAutoSelect, false);
});

test("applyBatchDeletedModelSelection handles null active key", () => {
	const result = applyBatchDeletedModelSelection({
		activeModelKey: null,
		deletedModelKeys: ["m1"],
	});
	assert.equal(result.nextActiveModelKey, null);
	assert.equal(result.suppressAutoSelect, false);
});

// ── applyManualModelSelection ────────────────────────────────────────

test("applyManualModelSelection sets new active key and clears suppress", () => {
	const result = applyManualModelSelection("m5");
	assert.equal(result.nextActiveModelKey, "m5");
	assert.equal(result.suppressAutoSelect, false);
});

test("applyManualModelSelection accepts null", () => {
	const result = applyManualModelSelection(null);
	assert.equal(result.nextActiveModelKey, null);
	assert.equal(result.suppressAutoSelect, false);
});

// ── resolveRunsRequestAfterSelection ─────────────────────────────────

test("resolveRunsRequestAfterSelection enables loading for valid selector", () => {
	const result = resolveRunsRequestAfterSelection("tag:project-management");
	assert.equal(result.shouldLoadRuns, true);
	assert.equal(result.selector, "tag:project-management");
});

test("resolveRunsRequestAfterSelection disables loading for empty string", () => {
	const result = resolveRunsRequestAfterSelection("  ");
	assert.equal(result.shouldLoadRuns, false);
	assert.equal(result.selector, undefined);
});

test("resolveRunsRequestAfterSelection disables loading for null", () => {
	const result = resolveRunsRequestAfterSelection(null);
	assert.equal(result.shouldLoadRuns, false);
	assert.equal(result.selector, undefined);
});

test("resolveRunsRequestAfterSelection disables loading for undefined", () => {
	const result = resolveRunsRequestAfterSelection(undefined);
	assert.equal(result.shouldLoadRuns, false);
	assert.equal(result.selector, undefined);
});
