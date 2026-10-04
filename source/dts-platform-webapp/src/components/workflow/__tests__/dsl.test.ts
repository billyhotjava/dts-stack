import { describe, expect, it } from "vitest";
import type { WorkflowEdge, WorkflowNode } from "../store/types";
import { deserializeDsl, serializeDsl } from "../utils/dsl";

const nodes: WorkflowNode[] = [
	{
		id: "start-1",
		type: "start",
		position: { x: 0, y: 0 },
		data: {
			kind: "start",
			title: "开始",
			config: { trigger: "manual" },
			isDragging: true,
			status: "running",
			error: "runtime only",
		},
	},
	{
		id: "source-1",
		type: "source",
		position: { x: 280, y: 0 },
		data: {
			kind: "source",
			title: "数据源",
			config: { datasetName: "订单" },
		},
	},
	{
		id: "note-1",
		type: "note",
		position: { x: 80, y: 180 },
		data: {
			kind: "note",
			title: "便签",
			config: { content: "处理前确认口径", color: "#dbeafe", width: 240, height: 150 },
		},
	},
	{
		id: "iteration-1",
		type: "iteration",
		position: { x: 560, y: 0 },
		data: {
			kind: "iteration",
			title: "迭代",
			config: {
				inputArray: "$.tables",
				itemAlias: "table",
				children: [
					{
						id: "child-transform-1",
						type: "transform",
						position: { x: 0, y: 0 },
						data: { kind: "transform", title: "标准化", config: { language: "sql", code: "select 1" } },
					},
				],
				childEdges: [],
			},
		},
	},
];

const edges: WorkflowEdge[] = [
	{
		id: "edge-1",
		type: "custom",
		source: "start-1",
		target: "source-1",
		sourceHandle: "out",
		targetHandle: "in",
	},
];

describe("workflow DSL", () => {
	it("serializes graph state and strips runtime node fields", () => {
		const dsl = serializeDsl({ nodes, edges, viewport: { x: 1, y: 2, zoom: 0.8 } });

		expect(dsl.dslVersion).toBe("1.0");
		expect(dsl.viewport).toEqual({ x: 1, y: 2, zoom: 0.8 });
		expect(dsl.nodes[0].data.isDragging).toBeUndefined();
		expect(dsl.nodes[0].data.status).toBeUndefined();
		expect(dsl.nodes[0].data.error).toBeUndefined();
		expect(dsl.nodes[2].type).toBe("note");
		expect(dsl.nodes[2].data.config).toEqual({
			content: "处理前确认口径",
			color: "#dbeafe",
			width: 240,
			height: 150,
		});
		expect(dsl.nodes[3].children?.[0].type).toBe("transform");
		expect(dsl.nodes[3].data.config).toMatchObject({ inputArray: "$.tables", itemAlias: "table" });
		expect(dsl.edges[0].sourceHandle).toBe("out");
	});

	it("deserializes and preserves serialize/deserialize idempotency for graph shape", () => {
		const dsl = serializeDsl({
			nodes,
			edges,
			viewport: { x: 0, y: 0, zoom: 1 },
			metadata: { createdAt: "2026-05-05T00:00:00.000Z", updatedAt: "2026-05-05T00:00:00.000Z" },
		});
		const restored = deserializeDsl(dsl);

		expect(restored.success).toBe(true);
		if (!restored.success) return;
		const reSerialized = serializeDsl(restored.state);
		expect(reSerialized.nodes).toEqual(dsl.nodes);
		expect(reSerialized.edges).toEqual(dsl.edges);
		expect(reSerialized.viewport).toEqual(dsl.viewport);
	});

	it("rejects nested iteration or loop nodes under a subflow node", () => {
		const result = deserializeDsl({
			dslVersion: "1.0",
			nodes: [
				{
					id: "iteration-1",
					type: "iteration",
					position: { x: 0, y: 0 },
					data: { kind: "iteration", title: "迭代", config: {} },
					children: [
						{
							id: "loop-1",
							type: "loop",
							position: { x: 0, y: 0 },
							data: { kind: "loop", title: "循环", config: {} },
						},
					],
				},
			],
			edges: [],
			viewport: { x: 0, y: 0, zoom: 1 },
		});

		expect(result.success).toBe(false);
		expect(result.success ? "" : result.error).toContain("Nested iteration/loop");
	});

	it("rejects unsupported versions", () => {
		const result = deserializeDsl({ dslVersion: "9.9", nodes: [], edges: [], viewport: { x: 0, y: 0, zoom: 1 } });
		expect(result.success).toBe(false);
		expect(result.success ? "" : result.error).toContain("Unsupported DSL version");
	});
});
