import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");

test("asset map is a visual operating map, not only card and ledger views", () => {
	assert.match(SOURCE, /renderAssetVisualMap/);
	assert.match(SOURCE, /renderAssetMapNode/);
	assert.match(SOURCE, /asset-map-stage/);
	assert.match(SOURCE, /asset-map-toolbar/);
	assert.match(SOURCE, /asset-map-card/);
	assert.match(SOURCE, /资产链路总览/);
	assert.match(SOURCE, /全部资产工作区/);
	assert.match(SOURCE, /治理优先队列/);
	assert.match(SOURCE, /主题域覆盖/);
	assert.match(SOURCE, /进入台账/);
	assert.doesNotMatch(SOURCE, /gridTemplateColumns:\s*`repeat\(\$\{LAYER_ORDER\.length\}/);
	assert.doesNotMatch(SOURCE, /minmax\(138px/);
	assert.doesNotMatch(SOURCE, /label: "卡片"/);
});

test("asset map defaults to visual map unless the URL explicitly requests ledger view", () => {
	assert.match(SOURCE, /new URLSearchParams\(window\.location\.search\)\.get\("view"\) === "table"/);
	assert.match(SOURCE, /return "map"/);
	assert.doesNotMatch(SOURCE, /localStorage\.getItem\(DATASET_VIEW_MODE_STORAGE_KEY\)/);
	assert.doesNotMatch(SOURCE, /localStorage\.setItem\(DATASET_VIEW_MODE_STORAGE_KEY/);
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
