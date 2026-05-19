import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");

test("asset map is a visual operating map, not only card and ledger views", () => {
	assert.match(SOURCE, /renderAssetVisualMap/);
	assert.match(SOURCE, /renderAssetMapNode/);
	assert.match(SOURCE, /asset-map-stage/);
	assert.match(SOURCE, /数据流向/);
	assert.match(SOURCE, /业务视角/);
	assert.match(SOURCE, /进入台账/);
	assert.doesNotMatch(SOURCE, /label: "卡片"/);
});

test("asset map defaults to visual map unless the URL explicitly requests ledger view", () => {
	assert.match(SOURCE, /new URLSearchParams\(window\.location\.search\)\.get\("view"\) === "table"/);
	assert.match(SOURCE, /return "map"/);
	assert.doesNotMatch(SOURCE, /localStorage\.getItem\(DATASET_VIEW_MODE_STORAGE_KEY\)/);
	assert.doesNotMatch(SOURCE, /localStorage\.setItem\(DATASET_VIEW_MODE_STORAGE_KEY/);
});
