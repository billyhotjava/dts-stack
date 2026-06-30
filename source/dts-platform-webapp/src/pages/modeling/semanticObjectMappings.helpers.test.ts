import { describe, expect, it } from "vitest";
import { buildSemanticObjectJoinGraph, isPrimaryTableRole } from "./semanticObjectMappings.helpers";

describe("semantic object mapping helpers", () => {
	it("recognizes platform and legacy primary table roles", () => {
		expect(isPrimaryTableRole("main")).toBe(true);
		expect(isPrimaryTableRole("PRIMARY")).toBe(true);
		expect(isPrimaryTableRole("fact")).toBe(true);
		expect(isPrimaryTableRole("dimension")).toBe(false);
	});

	it("uses the primary role as join graph source even when it is not first", () => {
		const graph = buildSemanticObjectJoinGraph([
			{
				id: "dim",
				objectId: "object",
				tableName: "dim_customer",
				tableRole: "dimension",
				joinExpression: "fact.customer_id = dim_customer.customer_id",
				sortOrder: 0,
			},
			{
				id: "fact",
				objectId: "object",
				tableName: "dwd_order_detail",
				tableRole: "main",
				sortOrder: 1,
			},
		]);

		expect(graph.nodes.map((node) => node.id)).toEqual(["dim", "fact"]);
		expect(graph.edges).toHaveLength(1);
		expect(graph.edges[0]).toMatchObject({ source: "fact", target: "dim" });
	});
});
