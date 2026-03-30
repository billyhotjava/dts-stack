import assert from "node:assert/strict";
import test from "node:test";
import { findTreeNodeById, flattenProjectTree } from "./majorProjectTreeView.helpers";

const tree = [
	{
		id: "major-aurora",
		level: "major",
		name: "苍穹导航综合工程",
		children: [
			{
				id: "sub-aurora-nav",
				level: "subproject",
				name: "导航处理机",
				children: [
					{ id: "node-006", level: "node", name: "关键算法验证", children: [] },
				],
			},
		],
	},
];

test("flattenProjectTree preserves hierarchy order", () => {
	const flat = flattenProjectTree(tree as never);
	assert.deepEqual(
		flat.map((item) => item.id),
		["major-aurora", "sub-aurora-nav", "node-006"],
	);
});

test("findTreeNodeById returns nested node detail", () => {
	const node = findTreeNodeById(tree as never, "node-006");
	assert.equal(node?.name, "关键算法验证");
	assert.equal(node?.level, "node");
});
