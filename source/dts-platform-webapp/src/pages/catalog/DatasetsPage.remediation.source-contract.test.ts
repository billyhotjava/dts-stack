import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");
const LEDGER_VIEW = readFileSync(new URL("./assets/AssetLedgerView.tsx", import.meta.url), "utf8");
const DIALOGS = readFileSync(new URL("./assets/AssetLedgerDialogs.tsx", import.meta.url), "utf8");

test("asset map exposes remediation workspace for governance gaps and lineage failures", () => {
	assert.match(DIALOGS, /治理缺口处置工作台/);
	assert.match(SOURCE, /governanceGapRows/);
	assert.match(SOURCE, /lineageFailureRows/);
	assert.match(SOURCE, /处置缺口/);
	assert.match(DIALOGS, /补治理字段/);
	assert.match(DIALOGS, /同步血缘/);
});

test("remediation actions route to detail tabs and refresh lineage evidence", () => {
	assert.match(SOURCE, /syncCatalogAssetV2Lineage/);
	assert.match(SOURCE, /\/catalog\/datasets\/\$\{assetId\}\?tab=governance/);
	assert.match(LEDGER_VIEW, /\/catalog\/datasets\/\$\{row\.id\}\?tab=lineage-impact/);
	assert.match(SOURCE, /loadGovernanceSignals/);
});

test("asset ledger keeps the operational table shell (map split into AssetOverviewPage)", () => {
	assert.match(SOURCE, /renderAssetTable/);
	assert.match(SOURCE, /返回地图/);
	assert.match(LEDGER_VIEW, /title: "治理状态"/);
	assert.match(LEDGER_VIEW, /tab=lineage-impact/);
	assert.doesNotMatch(SOURCE, /label: "卡片"/);
	assert.doesNotMatch(SOURCE, /DATASET_VIEW_MODE_STORAGE_KEY/);
	assert.doesNotMatch(SOURCE, /router\.push\("\/catalog\/asset-detail"\)/);
});
