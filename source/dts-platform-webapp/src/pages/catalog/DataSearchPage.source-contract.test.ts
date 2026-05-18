import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DataSearchPage.tsx", import.meta.url), "utf8");

test("data search page searches assets-v2 as the primary asset source", () => {
	assert.match(SOURCE, /listCatalogAssetsV2/);
	assert.match(SOURCE, /normalizeAssetRows/);
	assert.match(SOURCE, /assetKind: "ASSET"/);
	assert.match(SOURCE, /assets-v2/);
	assert.match(SOURCE, /key: "ASSET"/);
});

test("data search page reuses the same asset filter cache as asset map", () => {
	assert.match(SOURCE, /catalog\.asset\.filter\.v2/);
	assert.doesNotMatch(SOURCE, /catalog\.asset\.filter\.v1/);
	assert.match(SOURCE, /setAssetType\(typeof saved\?\.assetType/);
	assert.match(SOURCE, /setKeyword\(typeof saved\?\.keyword/);
});
