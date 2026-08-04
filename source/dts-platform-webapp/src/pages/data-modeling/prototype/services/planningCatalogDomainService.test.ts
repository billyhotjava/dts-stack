// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import { normalizePlanningCatalogDomains } from "./planningCatalogDomainService";

describe("normalizePlanningCatalogDomains", () => {
	it("keeps stable ids and parent relationships for planning writes", () => {
		expect(
			normalizePlanningCatalogDomains([
				{
					id: "category-1",
					code: "FINANCE",
					name: "财务管理",
					children: [
						{
							id: "domain-1",
							code: "FIN",
							name: "财务域",
							description: "财务分析数据域",
						},
					],
				},
			]),
		).toEqual([
			{
				id: "category-1",
				code: "FINANCE",
				name: "财务管理",
				owner: "",
				description: "",
				parentId: null,
				parentCode: null,
			},
			{
				id: "domain-1",
				code: "FIN",
				name: "财务域",
				owner: "",
				description: "财务分析数据域",
				parentId: "category-1",
				parentCode: "FINANCE",
			},
		]);
	});
});
