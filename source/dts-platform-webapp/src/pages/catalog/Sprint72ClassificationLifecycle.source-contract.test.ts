import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const LEDGER = readFileSync(new URL("./assets/AssetLedgerView.tsx", import.meta.url), "utf8");
const DATASETS = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");
const WORKBENCH = readFileSync(new URL("./assets/AssetLifecycleWorkbenchDrawer.tsx", import.meta.url), "utf8");
const FACTS = readFileSync(new URL("./assets/AssetClassificationFactPanel.tsx", import.meta.url), "utf8");
const SCREEN_CLASSIFICATION = readFileSync(
	new URL("../../analytics/pages/screens/components/ClassificationSelect.tsx", import.meta.url),
	"utf8",
);
const SCREEN_SOURCE = readFileSync(
	new URL("../../analytics/pages/screens/components/propertyPanel/DataSourceConfigSection.tsx", import.meta.url),
	"utf8",
);
const API = readFileSync(new URL("../../api/platformApi.ts", import.meta.url), "utf8");

test("Sprint-72 asset ledger explains immutable classification facts separately from tags", () => {
	for (const label of ["有效密级", "最高来源", "待封存", "密级与生命周期"]) {
		assert.match(LEDGER, new RegExp(label));
	}
	for (const label of ["来源声明", "识别结果", "人工下限", "字段有效密级", "继承来源"]) {
		assert.match(FACTS, new RegExp(label));
	}
	assert.match(FACTS, /数据标签与合规密级相互独立/);
	assert.doesNotMatch(FACTS, /降低密级|降密原因/);
	assert.match(DATASETS, /breakpoint="md"/);
	assert.match(DATASETS, /collapsedWidth=\{0\}/);
});

test("Sprint-72 lifecycle workbench closes approval, trash, restoration and proof journeys", () => {
	for (const label of [
		"申请归档",
		"申请临时销毁",
		"申请恢复",
		"申请永久销毁",
		"批准",
		"驳回",
		"销毁证明",
	]) {
		assert.match(WORKBENCH, new RegExp(label));
	}
	assert.match(WORKBENCH, /永久销毁不可恢复/);
	assert.match(WORKBENCH, /只删除 DTS 管理副本/);
});

test("Sprint-72 migration UI keeps dry-run, batch apply, reconciliation and freeze ordered", () => {
	for (const label of ["新建 Dry-run", "应用下一批", "暂停", "恢复", "双读对账", "冻结旧降密入口"]) {
		assert.match(WORKBENCH, new RegExp(label));
	}
	for (const apiName of [
		"createClassificationMigrationDryRun",
		"applyClassificationMigrationBatch",
		"reconcileClassificationMigration",
		"freezeLegacyClassificationWrites",
	]) {
		assert.match(API, new RegExp(apiName));
	}
});

test("Sprint-72 screen classification is derived and API sources require a canonical asset key", () => {
	assert.match(SCREEN_CLASSIFICATION, /人工密级下限/);
	assert.match(SCREEN_CLASSIFICATION, /所有展示数据最高密级/);
	assert.match(SCREEN_CLASSIFICATION, /不能降低/);
	assert.doesNotMatch(SCREEN_CLASSIFICATION, /降级原因|确认降级/);
	assert.match(SCREEN_SOURCE, /密级资产键/);
	assert.match(SCREEN_SOURCE, /未绑定时禁止发布和导出/);
});
