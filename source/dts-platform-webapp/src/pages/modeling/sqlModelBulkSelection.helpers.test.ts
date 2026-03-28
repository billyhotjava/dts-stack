import assert from "node:assert/strict";
import test from "node:test";
import {
	applyBulkSelectionChange,
	clearDeletedBulkSelection,
	deriveSelectedModelIdsFromCheckedKeys,
	type BulkSelectionState,
} from "./sqlModelBulkSelection.helpers.ts";

test("deriveSelectedModelIdsFromCheckedKeys only keeps model leaf keys", () => {
	const result = deriveSelectedModelIdsFromCheckedKeys([
		"space-1",
		"layer-DWD",
		"model:111",
		"model:222",
		"other",
	]);

	assert.deepEqual(result, ["111", "222"]);
});

test("applyBulkSelectionChange keeps source when changes come from same origin", () => {
	const state = applyBulkSelectionChange(
		{
			selectedIds: ["111"],
			selectedSource: "tree",
			lastChangedAt: 10,
		},
		["111", "222"],
		"tree",
		20,
	);

	assert.deepEqual(state, {
		selectedIds: ["111", "222"],
		selectedSource: "tree",
		lastChangedAt: 20,
	});
});

test("applyBulkSelectionChange promotes source to mixed when origins differ", () => {
	const state = applyBulkSelectionChange(
		{
			selectedIds: ["111"],
			selectedSource: "tree",
			lastChangedAt: 10,
		},
		["111", "333"],
		"governance",
		20,
	);

	assert.deepEqual(state, {
		selectedIds: ["111", "333"],
		selectedSource: "mixed",
		lastChangedAt: 20,
	});
});

test("clearDeletedBulkSelection removes deleted ids and resets source when empty", () => {
	const state: BulkSelectionState = {
		selectedIds: ["111", "222"],
		selectedSource: "mixed",
		lastChangedAt: 10,
	};

	assert.deepEqual(clearDeletedBulkSelection(state, ["111"]), {
		selectedIds: ["222"],
		selectedSource: "mixed",
		lastChangedAt: 10,
	});

	assert.deepEqual(clearDeletedBulkSelection(state, ["111", "222"]), {
		selectedIds: [],
		selectedSource: null,
		lastChangedAt: 10,
	});
});
