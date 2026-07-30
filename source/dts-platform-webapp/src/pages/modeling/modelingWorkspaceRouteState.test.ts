import assert from "node:assert/strict";
import test from "node:test";

import {
	isModelingWorkspaceModelAssetOpen,
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

test("only the canonical model-spec workspace recognizes an open model asset", () => {
	assert.equal(
		isModelingWorkspaceModelAssetOpen(
			parseModelingWorkspaceRouteState(
				new URLSearchParams(
					"module=models&workspaceView=model-specs&assetKind=model&assetId=model-79&activeStage=logical",
				),
			),
		),
		true,
	);
	assert.equal(
		isModelingWorkspaceModelAssetOpen(
			parseModelingWorkspaceRouteState(
				new URLSearchParams("module=models&workspaceView=dimensions&assetKind=model&assetId=model-79"),
			),
		),
		false,
	);
	assert.equal(
		isModelingWorkspaceModelAssetOpen(
			parseModelingWorkspaceRouteState(
				new URLSearchParams("module=standards&workspaceView=elements&assetKind=model&assetId=model-79"),
			),
		),
		false,
	);
});

test("workspace view is accepted only by the owning module", () => {
	assert.deepEqual(
		parseModelingWorkspaceRouteState(new URLSearchParams("module=planning&planId=plan-79&workspaceView=%20sources%20")),
		{
			module: "planning",
			planId: "plan-79",
			workspaceView: "sources",
		},
	);
	assert.deepEqual(parseModelingWorkspaceRouteState(new URLSearchParams("module=standards&workspaceView=sources")), {
		module: "standards",
	});
	assert.deepEqual(parseModelingWorkspaceRouteState(new URLSearchParams("module=standards&workspaceView=unknown")), {
		module: "standards",
	});
});

test("metrics owns four stable workbench views without accepting them in other modules", () => {
	for (const workspaceView of ["definitions", "model", "templates", "consumption"]) {
		assert.deepEqual(
			parseModelingWorkspaceRouteState(new URLSearchParams(`module=metrics&workspaceView=${workspaceView}`)),
			{ module: "metrics", workspaceView },
		);
	}
	assert.deepEqual(parseModelingWorkspaceRouteState(new URLSearchParams("module=models&workspaceView=templates")), {
		module: "models",
	});
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
		updateModelingWorkspaceSearch(new URLSearchParams("planId=plan-79&module=planning&workspaceView=categories"), {
			module: "standards",
		}),
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
			new URLSearchParams(
				"planId=plan-old&module=models&workspaceView=model-specs&assetKind=model&assetId=model-old&activeStage=physical",
			),
			{ planId: "plan-new" },
		),
	);
	assert.equal(changed.get("planId"), "plan-new");
	assert.equal(changed.has("assetKind"), false);
	assert.equal(changed.has("assetId"), false);
	assert.equal(changed.has("activeStage"), false);

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

test("model asset navigation opens, stages and closes inside the canonical workspace query", () => {
	const opened = new URLSearchParams(
		updateModelingWorkspaceSearch(
			new URLSearchParams("module=planning&workspaceView=sources&planId=plan-stale&returnTo=ledger"),
			{
				module: "models",
				workspaceView: "model-specs",
				planId: "plan-real",
				assetKind: "model",
				assetId: "model-79",
				activeStage: "logical",
			},
		),
	);
	assert.equal(opened.get("module"), "models");
	assert.equal(opened.get("workspaceView"), "model-specs");
	assert.equal(opened.get("planId"), "plan-real");
	assert.equal(opened.get("assetKind"), "model");
	assert.equal(opened.get("assetId"), "model-79");
	assert.equal(opened.get("activeStage"), "logical");
	assert.equal(opened.get("returnTo"), "ledger");

	const implementation = new URLSearchParams(updateModelingWorkspaceSearch(opened, { activeStage: "implementation" }));
	assert.equal(implementation.get("activeStage"), "implementation");
	assert.equal(implementation.get("assetId"), "model-79");
	assert.equal(implementation.get("planId"), "plan-real");

	const closed = new URLSearchParams(
		updateModelingWorkspaceSearch(implementation, {
			assetKind: null,
			assetId: null,
			activeStage: null,
		}),
	);
	assert.equal(closed.has("assetKind"), false);
	assert.equal(closed.has("assetId"), false);
	assert.equal(closed.has("activeStage"), false);
	assert.equal(closed.get("module"), "models");
	assert.equal(closed.get("workspaceView"), "model-specs");
	assert.equal(closed.get("planId"), "plan-real");
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
