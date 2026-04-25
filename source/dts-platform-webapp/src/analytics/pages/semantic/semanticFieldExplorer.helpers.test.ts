import assert from "node:assert/strict";
import test from "node:test";
import type { SemanticModelMeta } from "../../api/analyticsApi";
import {
	buildSemanticFieldExplorerTree,
	collectSemanticFieldExplorerExpandedKeys,
	filterSemanticFieldExplorerTree,
} from "./semanticFieldExplorer.helpers";

const MODELS: SemanticModelMeta[] = [
	{
		id: "sales",
		label: "销售事实",
		subject_area: "销售",
		security_level: "INTERNAL",
		metrics: [
			{ id: "sales.revenue", label: "收入" },
			{ id: "sales.order_count", label: "订单数" },
		],
		dimensions: [{ id: "sales.order_date", label: "订单日期" }],
	},
	{
		id: "customer",
		label: "客户维度",
		subject_area: "客户",
		security_level: "CONFIDENTIAL",
		metrics: [],
		dimensions: [{ id: "customer.region", label: "客户区域" }],
	},
];

test("buildSemanticFieldExplorerTree groups fields by subject and model", () => {
	const tree = buildSemanticFieldExplorerTree(
		MODELS,
		["sales", "customer"],
		"sales",
		["sales.revenue"],
		["customer.region"],
	);

	assert.equal(tree.length, 2);
	assert.equal(tree[0]?.label, "客户");
	assert.equal(tree[1]?.label, "销售");
	assert.equal(tree[1]?.children?.[0]?.label, "销售事实");
	assert.equal(tree[1]?.children?.[0]?.isBase, true);
	assert.equal(tree[1]?.children?.[0]?.selectedCount, 1);
	assert.equal(tree[0]?.children?.[0]?.selectedCount, 1);
});

test("filterSemanticFieldExplorerTree keeps matching lineage", () => {
	const tree = buildSemanticFieldExplorerTree(MODELS, ["sales", "customer"], "sales", ["sales.revenue"], []);
	const filtered = filterSemanticFieldExplorerTree(tree, "收入");

	assert.equal(filtered.length, 1);
	assert.equal(filtered[0]?.label, "销售");
	assert.equal(filtered[0]?.children?.[0]?.label, "销售事实");
	assert.equal(filtered[0]?.children?.[0]?.children?.[0]?.label, "指标");
	assert.equal(filtered[0]?.children?.[0]?.children?.[0]?.children?.[0]?.label, "收入");
});

test("collectSemanticFieldExplorerExpandedKeys ignores field nodes", () => {
	const tree = buildSemanticFieldExplorerTree(MODELS, ["sales"], "sales", [], []);
	const expandedKeys = collectSemanticFieldExplorerExpandedKeys(tree);

	assert.deepEqual(expandedKeys, ["subject:销售", "model:sales", "group:sales:metric", "group:sales:dimension"]);
});
