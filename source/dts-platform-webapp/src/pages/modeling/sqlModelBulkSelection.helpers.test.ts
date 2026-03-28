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
			sourceSelections: {
				tree: ["111"],
				governance: [],
				list: [],
			},
			selectedSource: "tree",
			lastChangedAt: 10,
		},
		["111", "222"],
		"tree",
		20,
	);

	assert.deepEqual(state, {
		selectedIds: ["111", "222"],
		sourceSelections: {
			tree: ["111", "222"],
			governance: [],
			list: [],
		},
		selectedSource: "tree",
		lastChangedAt: 20,
	});
});

test("applyBulkSelectionChange merges selections across different origins", () => {
	const state = applyBulkSelectionChange(
		{
			selectedIds: ["111"],
			sourceSelections: {
				tree: ["111"],
				governance: [],
				list: [],
			},
			selectedSource: "tree",
			lastChangedAt: 10,
		},
		["333"],
		"governance",
		20,
	);

	assert.deepEqual(state, {
		selectedIds: ["111", "333"],
		sourceSelections: {
			tree: ["111"],
			governance: ["333"],
			list: [],
		},
		selectedSource: "mixed",
		lastChangedAt: 20,
	});
});

test("clearDeletedBulkSelection removes deleted ids and resets source when empty", () => {
	const state: BulkSelectionState = {
		selectedIds: ["111", "222"],
		sourceSelections: {
			tree: ["111"],
			governance: ["222"],
			list: [],
		},
		selectedSource: "mixed",
		lastChangedAt: 10,
	};

	assert.deepEqual(clearDeletedBulkSelection(state, ["111"]), {
		selectedIds: ["222"],
		sourceSelections: {
			tree: [],
			governance: ["222"],
			list: [],
		},
		selectedSource: "governance",
		lastChangedAt: 10,
	});

	assert.deepEqual(clearDeletedBulkSelection(state, ["111", "222"]), {
		selectedIds: [],
		sourceSelections: {
			tree: [],
			governance: [],
			list: [],
		},
		selectedSource: null,
		lastChangedAt: 10,
	});
});
