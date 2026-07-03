import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");
const SHARED = readFileSync(new URL("./assets/assetPageShared.tsx", import.meta.url), "utf8");
const MAP_VIEW = readFileSync(new URL("./assets/AssetMapView.tsx", import.meta.url), "utf8");

test("asset page shares types/constants/helpers via a dedicated module", () => {
	assert.match(SHARED, /export const LAYER_ORDER/);
	assert.match(SHARED, /export const LAYER_META/);
	assert.match(SHARED, /export const LEDGER_PAGE_SIZE = 10/);
	assert.match(SHARED, /export const normalizeLayer/);
	assert.match(SHARED, /export const MetricTile/);
	assert.match(SOURCE, /from "\.\/assets\/assetPageShared"/);
});

test("asset map is a navigation matrix, not a paginated detail list", () => {
	// 页面容器保留 renderAssetVisualMap 包装，视图实体在 AssetMapView 组件
	assert.match(SOURCE, /renderAssetVisualMap/);
	assert.match(SOURCE, /<AssetMapView/);
	assert.match(SOURCE, /asset-map-toolbar/);
	assert.match(SOURCE, /asset-map-card/);
	assert.match(SOURCE, /进入台账/);
	assert.match(MAP_VIEW, /asset-map-stage/);
	assert.match(MAP_VIEW, /资产链路总览/);
	assert.match(MAP_VIEW, /治理优先队列/);
	assert.match(MAP_VIEW, /主题域覆盖/);
	// 地图去明细化：不再渲染 per-asset 卡片网格，明细职责归台账
	assert.doesNotMatch(SOURCE, /renderAssetMapNode/);
	assert.doesNotMatch(MAP_VIEW, /renderAssetMapNode/);
	assert.doesNotMatch(MAP_VIEW, /全部资产工作区/);
	// 地图的主体是 分层×主题域 导航矩阵
	assert.match(MAP_VIEW, /renderAssetMatrix/);
	assert.match(MAP_VIEW, /asset-map-matrix/);
	assert.match(MAP_VIEW, /分层×主题域矩阵/);
	assert.doesNotMatch(SOURCE, /gridTemplateColumns:\s*`repeat\(\$\{LAYER_ORDER\.length\}/);
	assert.doesNotMatch(SOURCE, /minmax\(138px/);
	assert.doesNotMatch(SOURCE, /label: "卡片"/);
});

test("asset map matrix drills down into the ledger with layer and domain filters", () => {
	assert.match(SOURCE, /drillToLedger/);
	assert.match(MAP_VIEW, /onDrillToLedger/);
	assert.match(SOURCE, /params\.set\("layer"/);
	assert.match(SOURCE, /params\.set\("domain"/);
	// 台账深链可携带 layer/domain 初始化筛选
	assert.match(SOURCE, /\.get\("layer"\)/);
	assert.match(SOURCE, /\.get\("domain"\)/);
});

test("asset map defaults to visual map unless the URL explicitly requests ledger view", () => {
	assert.match(SOURCE, /new URLSearchParams\(window\.location\.search\)\.get\("view"\) === "table"/);
	assert.match(SOURCE, /return "map"/);
	assert.doesNotMatch(SOURCE, /localStorage\.getItem\(DATASET_VIEW_MODE_STORAGE_KEY\)/);
	assert.doesNotMatch(SOURCE, /localStorage\.setItem\(DATASET_VIEW_MODE_STORAGE_KEY/);
});

test("asset ledger exports CSV and follows the 10-per-page pagination convention", () => {
	assert.match(SOURCE, /exportLedgerCsv/);
	assert.match(SOURCE, /asset-ledger-export/);
	assert.match(SOURCE, /LEDGER_PAGE_SIZE/);
	// 台账分页 10/20/50/100，地图保持原档位
	assert.match(SOURCE, /isLedgerView \? \[10, 20, 50, 100\] : \[12, 18, 30, 48\]/);
});

test("asset ledger has its own registration and verification shell", () => {
	assert.match(SOURCE, /const isLedgerView = viewMode === "table"/);
	assert.match(SOURCE, /const pageTitle = isLedgerView \? "资产台账" : "资产地图"/);
	assert.match(SOURCE, /asset-ledger-toolbar/);
	assert.match(SOURCE, /asset-ledger-card/);
	assert.match(SOURCE, /asset-ledger-filter-strip/);
	assert.match(SOURCE, /asset-ledger-workbench/);
	assert.match(SOURCE, /资产登记台账/);
	assert.match(SOURCE, /登记核验/);
	assert.match(SOURCE, /待补字段/);
	assert.match(SOURCE, /返回地图/);
});
