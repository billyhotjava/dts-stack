import assert from "node:assert/strict";
import test from "node:test";

import {
	parseModelingWorkspaceRouteState,
	updateModelingWorkspaceSearch,
} from "./modelingWorkspaceRouteState.ts";

test("workspace route defaults invalid modules to home and trims stable context", () => {
	assert.deepEqual(
		parseModelingWorkspaceRouteState(
			new URLSearchParams("module=unknown&planId=%20plan-79%20&assetKind=model&assetId=%20model-1%20"),
		),
		{
			module: "home",
			planId: "plan-79",
			assetKind: "model",
			assetId: "model-1",
		},
	);
});

test("workspace route drops incomplete or unsupported asset pairs", () => {
	assert.deepEqual(parseModelingWorkspaceRouteState(new URLSearchParams("module=models&assetKind=model")), {
		module: "models",
	});
	assert.deepEqual(
		parseModelingWorkspaceRouteState(new URLSearchParams("module=models&assetKind=table&assetId=ods_order")),
		{ module: "models" },
	);
});

test("workspace view is accepted only by the owning module", () => {
	assert.deepEqual(
		parseModelingWorkspaceRouteState(
			new URLSearchParams("module=planning&planId=plan-79&workspaceView=%20sources%20"),
		),
		{
			module: "planning",
			planId: "plan-79",
			workspaceView: "sources",
		},
	);
	assert.deepEqual(
		parseModelingWorkspaceRouteState(new URLSearchParams("module=standards&workspaceView=sources")),
		{ module: "standards" },
	);
	assert.deepEqual(
		parseModelingWorkspaceRouteState(new URLSearchParams("module=standards&workspaceView=unknown")),
		{ module: "standards" },
	);
});

test("module navigation preserves plan and active import session", () => {
	const next = new URLSearchParams(
		updateModelingWorkspaceSearch(
			new URLSearchParams(
				"planId=plan-79&module=home&modelImport=open&importRunId=run-1&assetKind=model&assetId=model-1",
			),
			{ module: "standards" },
		),
	);

	assert.equal(next.get("module"), "standards");
	assert.equal(next.get("planId"), "plan-79");
	assert.equal(next.get("modelImport"), "open");
	assert.equal(next.get("importRunId"), "run-1");
	assert.equal(next.get("assetKind"), "model");
	assert.equal(next.get("assetId"), "model-1");
});

test("changing module clears an incompatible workspace view and accepts an explicit compatible replacement", () => {
	const cleared = new URLSearchParams(
		updateModelingWorkspaceSearch(
			new URLSearchParams("planId=plan-79&module=planning&workspaceView=categories"),
			{ module: "standards" },
		),
	);
	assert.equal(cleared.get("module"), "standards");
	assert.equal(cleared.has("workspaceView"), false);

	const replaced = parseModelingWorkspaceRouteState(
		new URLSearchParams(
			updateModelingWorkspaceSearch(cleared, {
				module: "models",
				workspaceView: "dimensions",
			}),
		),
	);
	assert.equal(replaced.module, "models");
	assert.equal(replaced.workspaceView, "dimensions");
});

test("changing plan clears an asset from the previous plan unless a replacement pair is supplied", () => {
	const changed = new URLSearchParams(
		updateModelingWorkspaceSearch(
			new URLSearchParams("planId=plan-old&module=models&assetKind=model&assetId=model-old"),
			{ planId: "plan-new" },
		),
	);
	assert.equal(changed.get("planId"), "plan-new");
	assert.equal(changed.has("assetKind"), false);
	assert.equal(changed.has("assetId"), false);

	const replaced = parseModelingWorkspaceRouteState(
		new URLSearchParams(
			updateModelingWorkspaceSearch(changed, {
				assetKind: "dimension",
				assetId: "dim-date",
			}),
		),
	);
	assert.equal(replaced.assetKind, "dimension");
	assert.equal(replaced.assetId, "dim-date");
});

test("clearing optional workspace context does not remove unrelated query keys", () => {
	const next = new URLSearchParams(
		updateModelingWorkspaceSearch(
			new URLSearchParams("planId=plan-79&module=graph&create=1&assetKind=standard&assetId=std-1"),
			{ planId: null, assetKind: null, assetId: null },
		),
	);

	assert.equal(next.has("planId"), false);
	assert.equal(next.has("assetKind"), false);
	assert.equal(next.has("assetId"), false);
	assert.equal(next.get("module"), "graph");
	assert.equal(next.get("create"), "1");
});
