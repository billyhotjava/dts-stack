import { describe, expect, it } from "vitest";
import type { ModelingRelationshipGraph } from "@/api/services/modelingRelationshipGraphService";
import { projectModelingRelationshipGraph } from "./relationshipGraphProjection";

const graph = (
	nodes: ModelingRelationshipGraph["nodes"],
	edges: ModelingRelationshipGraph["edges"],
): ModelingRelationshipGraph => ({
	planId: "plan-1",
	nodes,
	edges,
	truncated: false,
});

describe("projectModelingRelationshipGraph", () => {
	it("shows only model dependencies and dimension references on the model relationship page", () => {
		const result = projectModelingRelationshipGraph(
			graph(
				[
					{ id: "model:order", kind: "MODEL", label: "订单明细", status: "CURRENT" },
					{ id: "model:summary", kind: "MODEL", label: "订单汇总", status: "CURRENT" },
					{ id: "dimension:customer", kind: "DIMENSION", label: "客户维度", status: "CURRENT" },
					{ id: "model:isolated", kind: "MODEL", label: "无关系模型", status: "ARCHIVED" },
					{ id: "standard:amount", kind: "STANDARD", label: "金额标准", status: "CURRENT" },
				],
				[
					{ source: "model:summary", target: "model:order", kind: "DEPENDS_ON" },
					{ source: "model:order", target: "dimension:customer", kind: "DIMENSION_DEFINITION_REFERENCE" },
					{ source: "model:order", target: "standard:amount", kind: "STANDARD_BINDING" },
				],
			),
			"models",
			"",
		);

		expect(result.nodes.map((node) => node.id)).toEqual(["dimension:customer", "model:order", "model:summary"]);
		expect(result.edges.map((edge) => edge.relationType)).toEqual(["依赖", "引用维度定义"]);
	});

	it("does not render unrelated models when a standard or indicator relationship is absent", () => {
		const source = graph([{ id: "model:isolated", kind: "MODEL", label: "无关系模型", status: "ARCHIVED" }], []);

		expect(projectModelingRelationshipGraph(source, "standards", "").nodes).toEqual([]);
		expect(projectModelingRelationshipGraph(source, "metrics", "").nodes).toEqual([]);
	});

	it("keeps both endpoints for standard bindings and metric lineage", () => {
		const source = graph(
			[
				{ id: "model:order", kind: "MODEL", label: "订单明细", status: "CURRENT" },
				{ id: "standard:amount", kind: "STANDARD", label: "金额标准", status: "CURRENT" },
				{ id: "indicator:amount", kind: "INDICATOR", label: "订单金额", status: "PUBLISHED" },
			],
			[
				{ source: "model:order", target: "standard:amount", kind: "STANDARD_BINDING" },
				{ source: "model:order", target: "indicator:amount", kind: "INDICATOR_REFERENCE" },
			],
		);

		expect(projectModelingRelationshipGraph(source, "standards", "").nodes.map((node) => node.id)).toEqual([
			"model:order",
			"standard:amount",
		]);
		expect(projectModelingRelationshipGraph(source, "metrics", "").nodes.map((node) => node.id)).toEqual([
			"indicator:amount",
			"model:order",
		]);
	});

	it("focuses a search result and its directly connected neighbours", () => {
		const result = projectModelingRelationshipGraph(
			graph(
				[
					{ id: "model:order", kind: "MODEL", label: "订单明细", status: "CURRENT" },
					{ id: "model:summary", kind: "MODEL", label: "订单汇总", status: "CURRENT" },
					{ id: "model:other", kind: "MODEL", label: "库存汇总", status: "CURRENT" },
				],
				[
					{ source: "model:summary", target: "model:order", kind: "DEPENDS_ON" },
					{ source: "model:other", target: "model:order", kind: "DEPENDS_ON" },
				],
			),
			"models",
			"订单汇总",
		);

		expect(result.nodes.map((node) => node.id)).toEqual(["model:order", "model:summary"]);
		expect(result.edges).toHaveLength(1);
	});

	it("caps large projections and reports that the local view is limited", () => {
		const nodes = Array.from({ length: 100 }, (_, index) => ({
			id: `model:${index}`,
			kind: "MODEL" as const,
			label: `模型${index}`,
			status: "CURRENT",
		}));
		const edges = Array.from({ length: 99 }, (_, index) => ({
			source: `model:${index}`,
			target: `model:${index + 1}`,
			kind: "DEPENDS_ON",
		}));

		const result = projectModelingRelationshipGraph(graph(nodes, edges), "models", "");

		expect(result.nodes.length).toBeLessThanOrEqual(80);
		expect(result.limited).toBe(true);
		expect(result.edges.every((edge) => result.nodes.some((node) => node.id === edge.fromId))).toBe(true);
		expect(result.edges.every((edge) => result.nodes.some((node) => node.id === edge.toId))).toBe(true);
	});
});
