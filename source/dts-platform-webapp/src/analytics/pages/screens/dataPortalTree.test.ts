import assert from "node:assert/strict";
import test from "node:test";
import type { ScreenListItem } from "../../api/analyticsApi";
import { buildDataPortalTree, countScreenLeaves } from "./dataPortalTree";

const domains = [
	{
		id: "quality",
		name: "质量管理",
		children: [{ id: "quality-monthly", name: "月度质量" }],
	},
	{ id: "operation", name: "经营管理" },
];

test("builds governance-domain branches with stable screen-id leaves and an uncategorized bucket", () => {
	const screens: ScreenListItem[] = [
		{ id: 2, name: "质量月报", domainId: "quality-monthly", publishedVersionNo: 1 },
		{ id: 1, name: "经营态势", domainId: "operation", publishedVersionNo: 3 },
		{ id: 3, name: "综合总览", domainId: "missing-domain", publishedVersionNo: 2 },
	];

	const tree = buildDataPortalTree(domains, screens);
	assert.equal(countScreenLeaves(tree), 3);
	assert.deepEqual(
		tree.map((node) => node.title),
		["质量管理", "经营管理", "未归类"],
	);
	assert.equal(tree[0]?.children?.[0]?.children?.[0]?.key, "screen:2");
	assert.equal(tree[1]?.children?.[0]?.key, "screen:1");
	assert.equal(tree[2]?.children?.[0]?.key, "screen:3");
});

test("filters by screen name, keeps ancestors, and sorts sibling screens by Chinese name", () => {
	const screens: ScreenListItem[] = [
		{ id: 4, name: "质量周报", domainId: "quality-monthly", publishedVersionNo: 1 },
		{ id: 2, name: "质量月报", domainId: "quality-monthly", publishedVersionNo: 1 },
		{ id: 5, name: "经营态势", domainId: "operation", publishedVersionNo: 1 },
	];

	const qualityTree = buildDataPortalTree(domains, screens, "质量");
	assert.equal(qualityTree.length, 1);
	assert.equal(qualityTree[0]?.title, "质量管理");
	assert.deepEqual(
		qualityTree[0]?.children?.[0]?.children?.map((node) => node.title),
		["质量月报", "质量周报"],
	);
});

test("handles one thousand visible leaves without dropping stable identities", () => {
	const screens: ScreenListItem[] = Array.from({ length: 1_000 }, (_, index) => ({
		id: index + 1,
		name: `大屏-${String(index + 1).padStart(4, "0")}`,
		domainId: "operation",
		publishedVersionNo: 1,
	}));

	const tree = buildDataPortalTree(domains, screens);
	assert.equal(countScreenLeaves(tree), 1_000);
	assert.equal(tree[1]?.children?.[999]?.key, "screen:1000");
});
