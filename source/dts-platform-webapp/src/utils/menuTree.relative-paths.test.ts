import assert from "node:assert/strict";
import test from "node:test";
import type { MenuTree } from "#/entity";
import {
	findBestMenuMatch,
	firstAccessibleChildPath,
	firstAccessibleMenuPath,
	isExternalPath,
	resolveMenuPath,
} from "./menuTree.ts";

const BI_MENU_TREE: MenuTree[] = [
	{
		id: "bi-root",
		parentId: "",
		name: "BI 分析",
		code: "bi",
		path: "bi",
		type: 0 as any,
		children: [
			{
				id: "bi-home",
				parentId: "bi-root",
				name: "分析首页",
				code: "bi.home",
				path: "home",
				type: 1 as any,
				component: "/analytics/pages/HomePage",
			},
			{
				id: "bi-screens",
				parentId: "bi-root",
				name: "数据大屏",
				code: "bi.screens",
				path: "screens",
				type: 1 as any,
				component: "/analytics/pages/screens/ScreensPage",
			},
		],
	},
];

test("resolveMenuPath joins relative child paths with parent path", () => {
	const root = BI_MENU_TREE[0];
	const child = root.children?.[0];

	assert.equal(resolveMenuPath(root, null), "/bi");
	assert.equal(resolveMenuPath(child!, null, "/bi"), "/bi/home");
});

test("firstAccessibleMenuPath returns canonical nested BI path", () => {
	assert.equal(firstAccessibleMenuPath(BI_MENU_TREE), "/bi/home");
});

test("firstAccessibleChildPath resolves nested child path for container menu", () => {
	assert.equal(firstAccessibleChildPath(BI_MENU_TREE[0]), "/bi/home");
});

test("findBestMenuMatch matches nested BI routes using canonical joined path", () => {
	const match = findBestMenuMatch(BI_MENU_TREE, "/bi/screens");

	assert.equal(match?.id, "bi-screens");
});

test("metrics service paths are treated as reverse-proxy external navigation", () => {
	assert.equal(isExternalPath("/metrics/center"), true);
	assert.equal(isExternalPath("/metrics/semantic/publish"), true);
	assert.equal(isExternalPath("/bi-apps/metrics/center"), false);
});
