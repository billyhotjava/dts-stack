import assert from "node:assert/strict";
import test from "node:test";
import type { MenuTree } from "#/entity";
import { hasSemanticModelingMenuAccess } from "./semanticAccess";

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

	assert.equal(hasSemanticModelingMenuAccess(menus), true);
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

	assert.equal(hasSemanticModelingMenuAccess(menus), true);
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

	assert.equal(hasSemanticModelingMenuAccess(menus), false);
});
