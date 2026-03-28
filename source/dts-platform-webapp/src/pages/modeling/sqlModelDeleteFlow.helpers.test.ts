import assert from "node:assert/strict";
import test from "node:test";
import {
	applyBatchDeletedModelSelection,
	applyDeletedModelSelection,
	applyManualModelSelection,
	resolveRunsRequestAfterSelection,
} from "./sqlModelDeleteFlow.helpers.ts";

test("applyDeletedModelSelection clears selection and suppresses auto-select when deleting active model", () => {
	const result = applyDeletedModelSelection({
		activeModelKey: "model-1",
		deletedModelKey: "model-1",
	});

	assert.deepEqual(result, {
		nextActiveModelKey: null,
		suppressAutoSelect: true,
	});
});

test("applyDeletedModelSelection preserves selection when deleting another model", () => {
	const result = applyDeletedModelSelection({
		activeModelKey: "model-1",
		deletedModelKey: "model-2",
	});

	assert.deepEqual(result, {
		nextActiveModelKey: "model-1",
		suppressAutoSelect: false,
	});
});

test("applyManualModelSelection restores normal auto-select behavior", () => {
	const result = applyManualModelSelection("model-9");

	assert.deepEqual(result, {
		nextActiveModelKey: "model-9",
		suppressAutoSelect: false,
	});
});

test("resolveRunsRequestAfterSelection skips runs loading when there is no active model", () => {
	const result = resolveRunsRequestAfterSelection(undefined);

	assert.deepEqual(result, {
		shouldLoadRuns: false,
		selector: undefined,
	});
});

test("resolveRunsRequestAfterSelection keeps selector when active model exists", () => {
	const result = resolveRunsRequestAfterSelection("model:dwd_patent");

	assert.deepEqual(result, {
		shouldLoadRuns: true,
		selector: "model:dwd_patent",
	});
});

test("applyBatchDeletedModelSelection clears active model when it is included in deleted ids", () => {
	const result = applyBatchDeletedModelSelection({
		activeModelKey: "model-2",
		deletedModelKeys: ["model-1", "model-2"],
	});

	assert.deepEqual(result, {
		nextActiveModelKey: null,
		suppressAutoSelect: true,
	});
});
