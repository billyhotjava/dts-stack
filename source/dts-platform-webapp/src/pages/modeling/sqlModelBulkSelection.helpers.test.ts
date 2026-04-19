import assert from "node:assert/strict";
import test from "node:test";
import {
	applyBulkSelectionChange,
	clearBulkSelectionSource,
	clearDeletedBulkSelection,
	deriveSelectedModelIdsFromCheckedKeys,
	resolveSelectedModelIdsFromTreeKeys,
	summarizeBulkSelection,
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

test("clearBulkSelectionSource only clears the requested source bucket", () => {
	const state: BulkSelectionState = {
		selectedIds: ["111", "222", "333"],
		sourceSelections: {
			tree: ["111"],
			governance: ["222"],
			list: ["333"],
		},
		selectedSource: "mixed",
		lastChangedAt: 10,
	};

	assert.deepEqual(clearBulkSelectionSource(state, "governance", 20), {
		selectedIds: ["111", "333"],
		sourceSelections: {
			tree: ["111"],
			governance: [],
			list: ["333"],
		},
		selectedSource: "mixed",
		lastChangedAt: 20,
	});
});

test("summarizeBulkSelection returns per-source counts and total", () => {
	const state: BulkSelectionState = {
		selectedIds: ["111", "222", "333"],
		sourceSelections: {
			tree: ["111"],
			governance: ["222"],
			list: ["333"],
		},
		selectedSource: "mixed",
		lastChangedAt: 10,
	};

	assert.deepEqual(summarizeBulkSelection(state), {
		total: 3,
		tree: 1,
		governance: 1,
		list: 1,
	});
});

// ── Edge cases ───────────────────────────────────────────────────────

test("deriveSelectedModelIdsFromCheckedKeys deduplicates ids", () => {
	const result = deriveSelectedModelIdsFromCheckedKeys([
		"model:111",
		"model:111",
		"model:222",
	]);
	assert.deepEqual(result, ["111", "222"]);
});

test("deriveSelectedModelIdsFromCheckedKeys handles empty array", () => {
	assert.deepEqual(deriveSelectedModelIdsFromCheckedKeys([]), []);
});

test("deriveSelectedModelIdsFromCheckedKeys trims whitespace in ids", () => {
	const result = deriveSelectedModelIdsFromCheckedKeys(["model: 111 "]);
	assert.deepEqual(result, ["111"]);
});

test("resolveSelectedModelIdsFromTreeKeys expands space, layer, and leaf selections", () => {
	const result = resolveSelectedModelIdsFromTreeKeys(["space-fin", "layer-DWD", "model:ads-1"], {
		models: [
			{ id: "dwd-1", planId: "fin", layer: "DWD", name: "biz_dwd_project_fund" },
			{ id: "ads-1", planId: "fin", layer: "ADS", name: "biz_ads_project_fund_kpi" },
			{ id: "other-1", planId: "pm", layer: "DWD", name: "fact_progress_measure_v3" },
		],
		activeSpaceModels: [
			{ id: "dwd-1", planId: "fin", layer: "DWD", name: "biz_dwd_project_fund" },
			{ id: "ads-1", planId: "fin", layer: "ADS", name: "biz_ads_project_fund_kpi" },
		],
		unassignedModels: [],
		spaceKeyToPlanId: {
			"space-fin": "fin",
		},
		unassignedSpaceKey: "space-unassigned",
		inferLayer: (name?: string) => (name?.startsWith("biz_dwd_") ? "DWD" : "ADS"),
	});

	assert.deepEqual(result, ["ads-1", "dwd-1"]);
});

test("resolveSelectedModelIdsFromTreeKeys supports unassigned space selection", () => {
	const result = resolveSelectedModelIdsFromTreeKeys(["space-unassigned"], {
		models: [],
		activeSpaceModels: [],
		unassignedModels: [
			{ id: "u-1", name: "stg_fin__own_fund" },
			{ id: "u-2", name: "biz_ads_own_fund_kpi" },
		],
		spaceKeyToPlanId: {},
		unassignedSpaceKey: "space-unassigned",
		inferLayer: (name?: string) => (name?.startsWith("stg_") ? "STG" : "ADS"),
	});

	assert.deepEqual(result, ["u-1", "u-2"]);
});

test("clearDeletedBulkSelection returns same state when no ids to delete", () => {
	const state: BulkSelectionState = {
		selectedIds: ["111"],
		sourceSelections: { tree: ["111"], governance: [], list: [] },
		selectedSource: "tree",
		lastChangedAt: 10,
	};
	const result = clearDeletedBulkSelection(state, []);
	assert.equal(result, state);
});

test("clearDeletedBulkSelection returns same state when selection is already empty", () => {
	const state: BulkSelectionState = {
		selectedIds: [],
		sourceSelections: { tree: [], governance: [], list: [] },
		selectedSource: null,
		lastChangedAt: null,
	};
	const result = clearDeletedBulkSelection(state, ["111"]);
	assert.equal(result, state);
});

test("applyBulkSelectionChange deduplicates overlapping ids across sources", () => {
	const state = applyBulkSelectionChange(
		{
			selectedIds: ["111"],
			sourceSelections: { tree: ["111"], governance: [], list: [] },
			selectedSource: "tree",
			lastChangedAt: 10,
		},
		["111"],
		"governance",
		20,
	);
	assert.deepEqual(state.selectedIds, ["111"]);
	assert.equal(state.selectedSource, "mixed");
});
