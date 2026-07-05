import { describe, expect, it } from "vitest";
import {
	addMetricDependencyToFormulaJson,
	buildMetricCanvasEdges,
	buildMetricCanvasNodes,
	buildMetricCanvasPreflightIssues,
	buildSemanticMetricUpdatePayload,
	findMetricDropTargetObject,
	insertMetricDslToken,
	removeMetricDependencyFromFormulaJson,
	parseMetricDragPayload,
	resolveMetricBinding,
	resolveMetricConnection,
	resolveMetricNodeDropBinding,
	serializeMetricDragPayload,
	type MetricCanvasNodePositionMap,
} from "./metricCanvas.helpers";

const OBJECTS = [
	{
		id: "object-1",
		code: "ORDER",
		name: "订单",
	},
	{
		id: "object-2",
		code: "CUSTOMER",
		name: "客户",
	},
];

const METRICS = [
	{
		id: "metric-1",
		objectId: "object-1",
		code: "GMV",
		name: "成交金额",
		formulaType: "aggregation/sum",
		status: "ACTIVE",
	},
	{
		id: "metric-2",
		code: "ORDER_COUNT",
		name: "订单数",
		formulaType: "aggregation/count_distinct",
		status: "DRAFT",
	},
];

const DERIVED_METRICS = [
	{
		id: "metric-1",
		objectId: "object-1",
		code: "GMV",
		name: "成交金额",
		formulaType: "aggregation/sum",
		formulaJson: '{"field":"amount","type":"sum"}',
		status: "ACTIVE",
	},
	{
		id: "metric-2",
		code: "ORDER_COUNT",
		name: "订单数",
		formulaType: "aggregation/count_distinct",
		formulaJson: '{"field":"order_id","dependsOnMetricIds":["metric-1"]}',
		status: "DRAFT",
	},
];

describe("metric canvas helpers", () => {
	it("keeps dragged positions while placing new nodes predictably", () => {
		const positions: MetricCanvasNodePositionMap = {
			"obj-object-1": { x: 48, y: 72 },
			"metric-metric-2": { x: 640, y: 180 },
		};

		const nodes = buildMetricCanvasNodes(OBJECTS, METRICS, "metric-metric-2", positions);

		expect(nodes.map((node) => ({ id: node.id, position: node.position, selected: node.selected }))).toEqual([
			{ id: "obj-object-1", position: { x: 48, y: 72 }, selected: false },
			{ id: "obj-object-2", position: { x: 0, y: 180 }, selected: false },
			{ id: "metric-metric-1", position: { x: 420, y: 0 }, selected: false },
			{ id: "metric-metric-2", position: { x: 640, y: 180 }, selected: true },
		]);
	});

	it("accepts object-to-metric and metric-to-object connections", () => {
		expect(resolveMetricBinding({ source: "obj-object-1", target: "metric-metric-2" })).toEqual({
			objectId: "object-1",
			metricId: "metric-2",
		});
		expect(resolveMetricBinding({ source: "metric-metric-2", target: "obj-object-1" })).toEqual({
			objectId: "object-1",
			metricId: "metric-2",
		});
		expect(resolveMetricBinding({ source: "obj-object-1", target: "obj-object-2" })).toBeNull();
	});

	it("marks persisted metric-object bindings", () => {
		const edges = buildMetricCanvasEdges(METRICS);

		expect(edges).toEqual([
			{
				id: "edge-metric-1",
				source: "obj-object-1",
				target: "metric-metric-1",
				type: "binding",
				data: { relationType: "OBJECT_METRIC" },
			},
		]);
	});

	it("marks persisted metric derivation bindings from formula JSON", () => {
		const edges = buildMetricCanvasEdges(DERIVED_METRICS);

		expect(edges).toEqual([
			{
				id: "edge-metric-1",
				source: "obj-object-1",
				target: "metric-metric-1",
				type: "binding",
				data: { relationType: "OBJECT_METRIC" },
			},
			{
				id: "edge-derives-metric-1-metric-2",
				source: "metric-metric-1",
				target: "metric-metric-2",
				type: "binding",
				data: {
					relationType: "METRIC_DERIVES",
					sourceMetricId: "metric-1",
					targetMetricId: "metric-2",
				},
			},
		]);
	});

	it("accepts metric-to-metric derivation connections without breaking object binding", () => {
		expect(resolveMetricConnection({ source: "obj-object-1", target: "metric-metric-2" })).toEqual({
			relationType: "OBJECT_METRIC",
			objectId: "object-1",
			metricId: "metric-2",
		});
		expect(resolveMetricConnection({ source: "metric-metric-1", target: "metric-metric-2" })).toEqual({
			relationType: "METRIC_DERIVES",
			sourceMetricId: "metric-1",
			targetMetricId: "metric-2",
		});
		expect(resolveMetricConnection({ source: "metric-metric-1", target: "metric-metric-1" })).toBeNull();
	});

	it("round-trips metric drag payloads and rejects unrelated data", () => {
		const payload = serializeMetricDragPayload("metric-2");

		expect(parseMetricDragPayload(payload)).toBe("metric-2");
		expect(parseMetricDragPayload("not-json")).toBeNull();
		expect(parseMetricDragPayload(JSON.stringify({ kind: "object", metricId: "metric-2" }))).toBeNull();
	});

	it("finds the business object target for a dropped metric without binding blank canvas drops", () => {
		const nodes = buildMetricCanvasNodes(OBJECTS, METRICS, null, {
			"obj-object-2": { x: 96, y: 240 },
		});

		expect(findMetricDropTargetObject(nodes, { x: 150, y: 270 })).toBe("object-2");
		expect(findMetricDropTargetObject(nodes, { x: 760, y: 420 })).toBeNull();
	});

	it("resolves a metric node dragged onto a business object node", () => {
		const nodes = buildMetricCanvasNodes(OBJECTS, METRICS, null, {
			"obj-object-2": { x: 96, y: 240 },
		});

		expect(
			resolveMetricNodeDropBinding(
				{ id: "metric-metric-2", type: "metric", position: { x: 150, y: 270 } },
				nodes,
			),
		).toEqual({
			objectId: "object-2",
			metricId: "metric-2",
		});
		expect(
			resolveMetricNodeDropBinding(
				{ id: "obj-object-1", type: "bizObject", position: { x: 150, y: 270 } },
				nodes,
			),
		).toBeNull();
	});

	it("builds full metric update payloads for the backend full-update contract", () => {
		expect(buildSemanticMetricUpdatePayload(METRICS[0], { objectId: "object-2", unit: "元" })).toEqual({
			objectId: "object-2",
			code: "GMV",
			name: "成交金额",
			formulaType: "aggregation/sum",
			formulaJson: undefined,
			format: undefined,
			unit: "元",
			status: "ACTIVE",
		});
	});

	it("adds and removes metric dependencies without overwriting formula fields", () => {
		const added = addMetricDependencyToFormulaJson('{"field":"amount","type":"sum"}', "metric-2");

		expect(added).toEqual({
			ok: true,
			formulaJson: '{"field":"amount","type":"sum","dependsOnMetricIds":["metric-2"]}',
		});

		const removed = removeMetricDependencyFromFormulaJson(
			'{"field":"amount","type":"sum","dependsOnMetricIds":["metric-1","metric-2"]}',
			"metric-1",
		);

		expect(removed).toEqual({
			ok: true,
			formulaJson: '{"field":"amount","type":"sum","dependsOnMetricIds":["metric-2"]}',
		});
	});

	it("does not silently overwrite invalid formula JSON", () => {
		expect(addMetricDependencyToFormulaJson("{bad-json", "metric-2")).toEqual({
			ok: false,
			error: "INVALID_FORMULA_JSON",
		});
	});

	it("inserts metric DSL tokens at cursor or selected range", () => {
		expect(insertMetricDslToken("{{metric:gmv}} / ", "{{metric:order_cnt}}", 17, 17)).toBe(
			"{{metric:gmv}} / {{metric:order_cnt}}",
		);
		expect(insertMetricDslToken("old_expression", "{{metric:gmv}}", 0, "old_expression".length)).toBe(
			"{{metric:gmv}}",
		);
		expect(insertMetricDslToken("{{metric:gmv}} / ", "{{metric:order_cnt}}")).toBe(
			"{{metric:gmv}} / {{metric:order_cnt}}",
		);
	});

	it("preflights isolated metrics, cyclic dependencies, draft dependencies and invalid JSON", () => {
		const issues = buildMetricCanvasPreflightIssues(OBJECTS, [
			{
				id: "metric-1",
				objectId: "object-1",
				code: "GMV",
				name: "成交金额",
				formulaJson: '{"dependsOnMetricIds":["metric-2"]}',
				status: "ACTIVE",
			},
			{
				id: "metric-2",
				code: "ORDER_COUNT",
				name: "订单数",
				formulaJson: '{"dependsOnMetricIds":["metric-1"]}',
				status: "DRAFT",
			},
			{
				id: "metric-3",
				code: "AOV",
				name: "客单价",
				formulaJson: '{"field":"amount"}',
				status: "ACTIVE",
			},
			{
				id: "metric-4",
				code: "BAD",
				name: "坏公式",
				formulaJson: "{bad-json",
				status: "ACTIVE",
			},
		]);

		expect(issues.map((issue) => issue.code)).toEqual(
			expect.arrayContaining([
				"ACTIVE_DEPENDS_ON_DRAFT",
				"CYCLIC_METRIC_DEPENDENCY",
				"INVALID_FORMULA_JSON",
				"ISOLATED_METRIC",
			]),
		);
		expect(issues.every((issue) => issue.nodeId || issue.edgeId)).toBe(true);
	});
});
