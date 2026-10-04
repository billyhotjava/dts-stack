import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SEARCH = readFileSync(new URL("./DataSearchPage.tsx", import.meta.url), "utf8");
const LEDGER = readFileSync(new URL("./assets/AssetLedgerView.tsx", import.meta.url), "utf8");

test("Sprint-97 exposes data tags as a visible in-page workspace without adding a second menu", () => {
	assert.match(SEARCH, /Tabs/);
	assert.match(SEARCH, /label:\s*"资产目录"/);
	assert.match(SEARCH, /label:\s*"数据标签"/);
	assert.match(SEARCH, /key:\s*"catalog-tags"/);
	assert.match(SEARCH, /next\.set\("tab",\s*"catalog-tags"\)/);
	assert.match(SEARCH, /next\.delete\("tab"\)/);
});

test("Sprint-97 keeps asset family separate from source type", () => {
	assert.match(SEARCH, /ASSET_FAMILY_OPTIONS/);
	assert.match(SEARCH, /id="catalog-search-asset-family"/);
	assert.match(SEARCH, /id="catalog-search-source-type"/);
	assert.match(SEARCH, /assetFamily/);
	assert.match(LEDGER, /row\.assetType !== "DATASET" \? "不适用"/);
	assert.match(LEDGER, /label="资产子类型"/);
});

test("Sprint-97 opens non-table assets in a unified detail surface and limits domain assignment to datasets", () => {
	assert.match(LEDGER, /UnifiedAssetDetailDrawer/);
	assert.match(LEDGER, /row\.assetType\s*!==\s*"DATASET"/);
	assert.match(LEDGER, /仅数据表资产支持批量归域/);
	assert.match(LEDGER, /GovernedAssetTagPanel/);
	assert.equal(LEDGER.match(/归域选中资产/g)?.length, 1);
	assert.doesNotMatch(LEDGER, /上游\/来源|下游\/消费/);
	assert.match(LEDGER, /RELATION_TYPE_LABELS\[String\(relation\.relationType\)\]/);
});
