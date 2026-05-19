import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");

test("asset map exposes remediation workspace for governance gaps and lineage failures", () => {
	assert.match(SOURCE, /治理缺口处置工作台/);
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
	assert.match(SOURCE, /Segmented/);
	assert.match(SOURCE, /renderAssetVisualMap/);
	assert.match(SOURCE, /renderAssetTable/);
	assert.match(SOURCE, /label: "地图"/);
	assert.match(SOURCE, /label: "台账"/);
	assert.match(SOURCE, /title: "治理状态"/);
	assert.match(SOURCE, /tab=lineage-impact/);
	assert.doesNotMatch(SOURCE, /label: "卡片"/);
	assert.doesNotMatch(SOURCE, /DATASET_VIEW_MODE_STORAGE_KEY/);
	assert.doesNotMatch(SOURCE, /router\.push\("\/catalog\/asset-detail"\)/);
});
