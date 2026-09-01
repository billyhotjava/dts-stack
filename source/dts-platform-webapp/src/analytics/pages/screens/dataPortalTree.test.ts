import assert from "node:assert/strict";
import test from "node:test";
import type { DashboardListItem, ScreenListItem } from "../../api/analyticsApi";
import {
	buildDataPortalTree,
	countContentLeaves,
	findContentNodeByBinding,
	type DataPortalBinding,
	type DataPortalDirectory,
} from "./dataPortalTree.ts";

const directories: DataPortalDirectory[] = [
	{ id: 11, name: "一月", parent_id: null, sort_order: 10 },
	{ id: 12, name: "项目管理", parent_id: 11, sort_order: 10 },
	{ id: 13, name: "财务管理", parent_id: 11, sort_order: 20 },
	{ id: 21, name: "二月", parent_id: null, sort_order: 20 },
];

const screens: ScreenListItem[] = [
	{ id: 101, name: "项目进度总览", description: "项目节点", publishedVersionNo: 3 },
];

const dashboards: DashboardListItem[] = [
	{
		id: 201,
		name: "财务执行看板",
		description: "预算执行",
		lifecycle_status: "PUBLISHED",
		published_revision_id: 9,
		registration_status: "AVAILABLE",
	},
];

const bindings: DataPortalBinding[] = [
	{ id: 31, directory_id: 12, content_type: "SCREEN", content_id: 101, sort_order: 10 },
	{ id: 32, directory_id: 13, content_type: "DASHBOARD", content_id: 201, sort_order: 10 },
];

test("builds arbitrary nested portal menus with screen and dashboard leaves", () => {
	const tree = buildDataPortalTree(directories, bindings, screens, dashboards);

	assert.deepEqual(tree.map((node) => node.title), ["一月", "二月"]);
	assert.deepEqual(tree[0]?.children?.map((node) => node.title), ["项目管理", "财务管理"]);
	assert.equal(tree[0]?.children?.[0]?.children?.[0]?.key, "binding:31");
	assert.equal(tree[0]?.children?.[1]?.children?.[0]?.key, "binding:32");
	assert.equal(countContentLeaves(tree), 2);
});

test("filters by directory or content name while retaining the complete ancestor chain", () => {
	const byContent = buildDataPortalTree(directories, bindings, screens, dashboards, "进度");
	assert.deepEqual(byContent.map((node) => node.title), ["一月"]);
	assert.deepEqual(byContent[0]?.children?.map((node) => node.title), ["项目管理"]);

	const byDirectory = buildDataPortalTree(directories, bindings, screens, dashboards, "财务管理");
	assert.equal(byDirectory[0]?.children?.[0]?.children?.[0]?.title, "财务执行看板");
});

test("hides inaccessible bindings for viewers and marks them invalid for portal editors", () => {
	const staleBindings: DataPortalBinding[] = [
		...bindings,
		{ id: 33, directory_id: 12, content_type: "SCREEN", content_id: 999, sort_order: 20 },
	];

	const viewerTree = buildDataPortalTree(directories, staleBindings, screens, dashboards);
	assert.equal(countContentLeaves(viewerTree), 2);

	const editorTree = buildDataPortalTree(directories, staleBindings, screens, dashboards, "", {
		includeUnavailable: true,
	});
	const unavailable = editorTree[0]?.children?.[0]?.children?.[1];
	assert.equal(unavailable?.key, "binding:33");
	assert.equal(unavailable?.availability, "UNAVAILABLE");
});

test("ignores orphan and cyclic directories instead of recursing forever", () => {
	const malformed: DataPortalDirectory[] = [
		...directories,
		{ id: 91, name: "孤立目录", parent_id: 999, sort_order: 1 },
		{ id: 92, name: "循环甲", parent_id: 93, sort_order: 1 },
		{ id: 93, name: "循环乙", parent_id: 92, sort_order: 1 },
	];

	const tree = buildDataPortalTree(malformed, bindings, screens, dashboards);
	assert.deepEqual(tree.map((node) => node.title), ["一月", "二月"]);
});

test("locates the exact portal menu when the same content is bound in multiple directories", () => {
	const duplicateBindings: DataPortalBinding[] = [
		...bindings,
		{ id: 41, directory_id: 21, content_type: "SCREEN", content_id: 101, sort_order: 10 },
	];
	const tree = buildDataPortalTree(directories, duplicateBindings, screens, dashboards);

	assert.equal(findContentNodeByBinding(tree, "41")?.key, "binding:41");
	assert.equal(findContentNodeByBinding(tree, "41")?.title, "项目进度总览");
});
