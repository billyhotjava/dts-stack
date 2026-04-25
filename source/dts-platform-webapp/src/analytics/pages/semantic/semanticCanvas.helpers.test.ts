import assert from "node:assert/strict";
import test from "node:test";
import type { SemanticModelMeta } from "../../api/analyticsApi";
import { buildSemanticCanvasGraph, buildSemanticJoinOptions } from "./semanticCanvas.helpers";

const MODELS: SemanticModelMeta[] = [
	{
		id: "sales",
		label: "销售事实",
		metrics: [{ id: "sales.revenue" }],
		dimensions: [{ id: "sales.order_date" }],
		joins: [
			{ to: "customer", type: "many_to_one" },
			{ to: "product", type: "many_to_one", approval_required: true },
		],
	},
	{
		id: "customer",
		label: "客户维度",
		metrics: [],
		dimensions: [{ id: "customer.region" }],
		joins: [{ to: "region", type: "many_to_one", fanout_warning: true }],
	},
	{
		id: "product",
		label: "产品维度",
		metrics: [],
		dimensions: [{ id: "product.category" }],
		joins: [],
	},
	{
		id: "region",
		label: "区域维度",
		metrics: [],
		dimensions: [{ id: "region.name" }],
		joins: [],
	},
];

test("buildSemanticJoinOptions exposes next-hop joins from selected models", () => {
	const options = buildSemanticJoinOptions(MODELS, "sales", ["customer"]);

	assert.deepEqual(
		options.map((item) => `${item.sourceId}->${item.targetId}`).sort(),
		["customer->region", "sales->customer", "sales->product"],
	);
});

test("buildSemanticCanvasGraph marks base selected and candidate nodes distinctly", () => {
	const graph = buildSemanticCanvasGraph(MODELS, "sales", ["customer"]);
	const nodeState = new Map(graph.nodes.map((node) => [node.id, node.state]));
	const edgeState = new Map(graph.edges.map((edge) => [edge.id, edge]));

	assert.equal(nodeState.get("sales"), "base");
	assert.equal(nodeState.get("customer"), "selected");
	assert.equal(nodeState.get("product"), "candidate");
	assert.equal(nodeState.get("region"), "candidate");
	assert.equal(edgeState.get("sales->customer")?.selected, true);
	assert.equal(edgeState.get("sales->product")?.approvalRequired, true);
	assert.equal(edgeState.get("customer->region")?.fanoutWarning, true);
});
