import assert from "node:assert/strict";
import test from "node:test";
import type { ModelFileBrowserTreeNode } from "./modelFileBrowserTree.helpers.ts";
import {
	collectExpandableTreeKeys,
	deriveDefaultExpandedTreeKeys,
	mergeExpandedTreeKeys,
} from "./modelFileBrowserTree.helpers.ts";

const treeData: ModelFileBrowserTreeNode[] = [
	{
		key: "space-a",
		title: "项目空间 A",
		nodeType: "space",
		children: [
			{
				key: "layer-DWD",
				title: "DWD",
				nodeType: "layer",
				children: [
					{ key: "model:1", title: "m1", nodeType: "model", isLeaf: true },
				],
			},
			{
				key: "layer-ADS",
				title: "ADS",
				nodeType: "layer",
				children: [
					{ key: "model:2", title: "m2", nodeType: "model", isLeaf: true },
				],
			},
		],
	},
	{
		key: "space-b",
		title: "项目空间 B",
		nodeType: "space",
		children: [
			{
				key: "layer-ODS",
				title: "ODS",
				nodeType: "layer",
				children: [{ key: "model:3", title: "m3", nodeType: "model", isLeaf: true }],
			},
		],
	},
];

test("collectExpandableTreeKeys returns only expandable nodes", () => {
	assert.deepEqual(collectExpandableTreeKeys(treeData), [
		"space-a",
		"layer-DWD",
		"layer-ADS",
		"space-b",
		"layer-ODS",
	]);
});

test("deriveDefaultExpandedTreeKeys expands active space and its layer groups", () => {
	assert.deepEqual(deriveDefaultExpandedTreeKeys(treeData, "space-a"), [
		"space-a",
		"layer-DWD",
		"layer-ADS",
	]);
	assert.deepEqual(deriveDefaultExpandedTreeKeys(treeData, "space-b"), ["space-b", "layer-ODS"]);
});

test("mergeExpandedTreeKeys prunes stale keys and preserves valid preferences", () => {
	assert.deepEqual(
		mergeExpandedTreeKeys(["space-a", "stale-key"], treeData, ["space-b", "layer-ODS"]),
		["space-a", "space-b", "layer-ODS"],
	);
});
