import assert from "node:assert/strict";
import test from "node:test";
import { filterRelationshipGraph, toLineageGraph } from "./relationshipGraphAdapter.ts";

const graph = {
	planId: "plan-79",
	nodes: [
		{ id: "plan:plan-79", kind: "PLAN", label: "财务数仓", status: "ACTIVE", route: "/modeling/workbench" },
		{ id: "model:model-1", kind: "MODEL", label: "预算事实", status: "DRAFT", route: "/modeling/models/model-1" },
		{ id: "metric:metric-1", kind: "INDICATOR", label: "预算执行率", status: "PUBLISHED" },
	],
	edges: [
		{ source: "plan:plan-79", target: "model:model-1", kind: "CONTAINS", label: "包含模型" },
		{ source: "model:model-1", target: "metric:metric-1", kind: "USES_METRIC", label: "引用指标" },
	],
	truncated: false,
};

test("adapts canonical plan nodes and edges without manufacturing catalog datasets", () => {
	const adapted = toLineageGraph(graph);
	assert.deepEqual(
		adapted.nodes.map((node) => [node.id, node.name, node.kind, node.type]),
		[
			["plan:plan-79", "财务数仓", "PLAN", "PLAN"],
			["model:model-1", "预算事实", "MODEL", "MODEL"],
			["metric:metric-1", "预算执行率", "INDICATOR", "INDICATOR"],
		],
	);
	assert.deepEqual(
		adapted.edges.map((edge) => [edge.fromId, edge.toId, edge.relationType, edge.notes]),
		[
			["plan:plan-79", "model:model-1", "CONTAINS", "包含模型"],
			["model:model-1", "metric:metric-1", "USES_METRIC", "引用指标"],
		],
	);
});

test("filters nodes by label while retaining only edges whose endpoints remain visible", () => {
	const filtered = filterRelationshipGraph(graph, "预算");
	assert.deepEqual(
		filtered.nodes.map((node) => node.id),
		["model:model-1", "metric:metric-1"],
	);
	assert.deepEqual(
		filtered.edges.map((edge) => [edge.source, edge.target]),
		[["model:model-1", "metric:metric-1"]],
	);
});
