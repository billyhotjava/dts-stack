import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");
const MAP_VIEW = readFileSync(new URL("./assets/AssetMapView.tsx", import.meta.url), "utf8");
const LEDGER_VIEW = readFileSync(new URL("./assets/AssetLedgerView.tsx", import.meta.url), "utf8");

test("asset map exposes remediation workspace for governance gaps and lineage failures", () => {
	assert.match(SOURCE, /治理缺口处置工作台/);
	assert.match(MAP_VIEW, /治理优先队列/);
	assert.match(SOURCE, /governanceGapRows/);
	assert.match(SOURCE, /lineageFailureRows/);
	assert.match(SOURCE, /处置缺口/);
	assert.match(SOURCE, /补治理字段/);
	assert.match(SOURCE, /同步血缘/);
});

test("remediation actions route to detail tabs and refresh lineage evidence", () => {
	assert.match(SOURCE, /syncCatalogAssetV2Lineage/);
	assert.match(SOURCE, /\/catalog\/datasets\/\$\{assetId\}\?tab=governance/);
	assert.match(SOURCE, /\/catalog\/datasets\/\$\{row\.id\}\?tab=lineage/);
	assert.match(SOURCE, /loadGovernanceSignals/);
});

test("asset map supports visual map and operational ledger table", () => {
	assert.match(SOURCE, /new URLSearchParams\(window\.location\.search\)\.get\("view"\) === "table"/);
	// 视图切换：Segmented 已由 进入台账/返回地图 显式动作按钮取代（Sprint-57 F4 基线）
	assert.match(SOURCE, /switchAssetView/);
	assert.match(SOURCE, /renderAssetVisualMap/);
	assert.match(SOURCE, /renderAssetTable/);
	assert.match(SOURCE, /进入台账/);
	assert.match(SOURCE, /返回地图/);
	assert.match(LEDGER_VIEW, /title: "治理状态"/);
	assert.match(LEDGER_VIEW, /tab=lineage-impact/);
	assert.doesNotMatch(SOURCE, /label: "卡片"/);
	assert.doesNotMatch(SOURCE, /DATASET_VIEW_MODE_STORAGE_KEY/);
	assert.doesNotMatch(SOURCE, /router\.push\("\/catalog\/asset-detail"\)/);
});
