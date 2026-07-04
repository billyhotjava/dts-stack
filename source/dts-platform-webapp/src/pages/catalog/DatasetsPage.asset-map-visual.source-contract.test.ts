import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");
const SHARED = readFileSync(new URL("./assets/assetPageShared.tsx", import.meta.url), "utf8");

test("asset page shares types/constants/helpers via a dedicated module", () => {
	assert.match(SHARED, /export const LAYER_ORDER/);
	assert.match(SHARED, /export const LAYER_META/);
	assert.match(SHARED, /export const LEDGER_PAGE_SIZE = 10/);
	assert.match(SHARED, /export const normalizeLayer/);
	assert.match(SHARED, /export const MetricTile/);
	assert.match(SOURCE, /from "\.\/assets\/assetPageShared"/);
});

test("datasets page is ledger-only: map lives in AssetOverviewPage", () => {
	// Sprint-57 F4-T08：地图/台账彻底分离，本页不再有视图开关与地图分支
	assert.doesNotMatch(SOURCE, /viewMode/);
	assert.doesNotMatch(SOURCE, /isLedgerView/);
	assert.doesNotMatch(SOURCE, /AssetMapView/);
	assert.doesNotMatch(SOURCE, /renderAssetVisualMap/);
	assert.match(SOURCE, /资产台账/);
	assert.match(SOURCE, /<AssetLedgerView/);
	// 返回统计地图的入口
	assert.match(SOURCE, /返回地图/);
	assert.match(SOURCE, /\/catalog\/assets/);
});

test("ledger accepts drill-down deep links from the overview matrix", () => {
	assert.match(SOURCE, /\.get\("layer"\)/);
	assert.match(SOURCE, /\.get\("domain"\)/);
});

test("asset ledger exports CSV and follows the 10-per-page pagination convention", () => {
	assert.match(SOURCE, /exportLedgerCsv/);
	assert.match(SOURCE, /asset-ledger-export/);
	assert.match(SOURCE, /LEDGER_PAGE_SIZE/);
	assert.match(SOURCE, /pageSizeOptions=\{\[10, 20, 50, 100\]\}/);
});

test("asset ledger has its own registration and verification shell", () => {
	assert.match(SOURCE, /asset-ledger-toolbar/);
	assert.match(SOURCE, /asset-ledger-card/);
	assert.match(SOURCE, /asset-ledger-filter-strip/);
	assert.match(SOURCE, /资产登记台账/);
	assert.match(SOURCE, /登记核验/);
	assert.match(SOURCE, /待补字段/);
});
