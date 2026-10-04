import assert from "node:assert/strict";
import test from "node:test";
import { bindTemplateTargetTable, buildBatchTemplatePreviewTargets } from "./templateBindings.ts";

test("overrides the target table parameter with the selected dataset schema-qualified physical table", () => {
	const params = bindTemplateTargetTable(
		{ table: "other_table", column: "order_id" },
		{ hiveDatabase: "public", hiveTable: "ods_orders" },
		[
			{ name: "table", label: "目标表", type: "table_select" },
			{ name: "column", label: "检查列", type: "column_select" },
		],
	);
	assert.deepEqual(params, { table: "public.ods_orders", column: "order_id" });
});

test("requires a physical table only when the template declares a bound target", () => {
	assert.throws(
		() => bindTemplateTargetTable({}, {}, [{ name: "table", label: "目标表", type: "table_select" }]),
		/物理表/,
	);
	assert.deepEqual(bindTemplateTargetTable({ expression: "1=1" }, {}, []), { expression: "1=1" });
	assert.deepEqual(
		bindTemplateTargetTable({}, { hiveDatabase: "public", hiveTable: "public.ods_orders" }, [
			{ name: "table", label: "目标表", type: "table_select" },
		]),
		{ table: "public.ods_orders" },
	);
});

test("builds one template preview target per selected dataset with its physical table", () => {
	const targets = buildBatchTemplatePreviewTargets(
		["dataset-a", "dataset-b"],
		[
			{ id: "dataset-a", name: "订单", hiveDatabase: "ods", hiveTable: "ods_orders" },
			{ id: "dataset-b", name: "客户", schemaName: "public", tableName: "dim_customers" },
		],
		{ table: "untrusted", column: "id" },
		[{ name: "table", label: "目标表", type: "table_select" }],
	);

	assert.deepEqual(targets, [
		{ datasetId: "dataset-a", label: "订单", params: { table: "ods.ods_orders", column: "id" } },
		{ datasetId: "dataset-b", label: "客户", params: { table: "public.dim_customers", column: "id" } },
	]);
});
