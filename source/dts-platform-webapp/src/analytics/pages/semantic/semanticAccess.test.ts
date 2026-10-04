import type { MenuTree } from "#/entity";
import { expect, test } from "vitest";
import { canPromoteSemanticModel, hasSemanticModelingMenuAccess } from "./semanticAccess";

test("semantic modeling access follows analytics card menu visibility", () => {
	const menus: MenuTree[] = [
		{
			id: "bi",
			parentId: "",
			name: "BI",
			code: "BI_ROOT",
			type: 0 as any,
			path: "/bi",
			children: [
				{
					id: "questions",
					parentId: "bi",
					name: "分析卡片",
					code: "sys.nav.portal.biQuestions",
					type: 2 as any,
					path: "questions",
				},
			],
		},
	];

	expect(hasSemanticModelingMenuAccess(menus)).toBe(true);
});

test("semantic modeling access follows dedicated semantic routes when present", () => {
	const menus: MenuTree[] = [
		{
			id: "semantic-explore",
			parentId: "",
			name: "语义探索",
			code: "SEMANTIC_EXPLORE",
			type: 2 as any,
			path: "/bi/explore",
		},
	];

	expect(hasSemanticModelingMenuAccess(menus)).toBe(true);
});

test("semantic modeling access is denied without matching menus", () => {
	const menus: MenuTree[] = [
		{
			id: "dashboards",
			parentId: "",
			name: "分析看板",
			code: "sys.nav.portal.biDashboards",
			type: 2 as any,
			path: "/bi/dashboards",
		},
	];

	expect(hasSemanticModelingMenuAccess(menus)).toBe(false);
});

test("institute and department data owners can promote semantic models without another approval role", () => {
	expect(canPromoteSemanticModel(["ROLE_INST_DATA_OWNER"])).toBe(true);
	expect(canPromoteSemanticModel(["ROLE_DEPT_DATA_OWNER"])).toBe(true);
});

test("existing engineering and operation roles retain semantic promotion access", () => {
	expect(canPromoteSemanticModel(["ROLE_BI_DATA_ENGINEER"])).toBe(true);
	expect(canPromoteSemanticModel(["OP_ADMIN"])).toBe(true);
});

test("ordinary report consumers cannot promote semantic models", () => {
	expect(canPromoteSemanticModel(["ROLE_EMP"])).toBe(false);
});
