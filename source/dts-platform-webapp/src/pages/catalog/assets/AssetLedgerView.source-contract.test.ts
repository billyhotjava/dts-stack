import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./AssetLedgerView.tsx", import.meta.url), "utf8");

test("asset directory is a selectable compact table with integrated pagination", () => {
	assert.match(SOURCE, /<CompactTable<AssetDirectoryRow>/);
	assert.match(SOURCE, /rowSelection=\{\{/);
	assert.match(SOURCE, /pagination=\{\{/);
	assert.match(SOURCE, /current: page/);
	assert.match(SOURCE, /pageSize/);
	assert.match(SOURCE, /total/);
	assert.match(SOURCE, /onChange: onPageChange/);
});

test("asset directory keeps only the existing bounded batch actions", () => {
	assert.match(SOURCE, /assignAssetsToDomain/);
	assert.match(SOURCE, /批量归域/);
	assert.match(SOURCE, /batchTagAssets/);
	assert.match(SOURCE, /关联选中资产/);
	assert.match(SOURCE, /单批最多选择 100 个资产/);
	assert.match(SOURCE, /manageTag/);
});

test("asset rows navigate to the canonical detail page and expose no inline action column", () => {
	assert.match(SOURCE, /router\.push\(`\/catalog\/datasets\/\$\{row\.id\}`\)/);
	assert.match(SOURCE, /title: "资产名称"/);
	assert.match(SOURCE, /title: "数据源类型"/);
	assert.match(SOURCE, /title: "来源系统"/);
	assert.match(SOURCE, /title: "主题域"/);
	assert.match(SOURCE, /title: "数据分层"/);
	assert.match(SOURCE, /title: "密级"/);
	assert.match(SOURCE, /title: "责任归属"/);
	assert.match(SOURCE, /title: "更新时间"/);
	assert.doesNotMatch(SOURCE, /title: "操作"|row-request-access|治理资产|申请权限/);
});

test("asset directory removes projections, metric tiles and inline workbench drawers", () => {
	assert.doesNotMatch(SOURCE, /getCatalogAssetStatsProjection|getCatalogClassificationFacts/);
	assert.doesNotMatch(SOURCE, /AssetGovernanceWorkbenchDrawer|AssetLifecycleWorkbenchDrawer/);
	assert.doesNotMatch(SOURCE, /登记核验|待补字段|可消费资产|资产统计投影/);
	assert.doesNotMatch(SOURCE, /ToolOutlined|ASSET_ACTION_COLUMN_WIDTH|resolveAssetReadiness/);
});
