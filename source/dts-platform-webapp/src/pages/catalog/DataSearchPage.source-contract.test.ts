import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DataSearchPage.tsx", import.meta.url), "utf8");

test("data asset directory consumes assets-v2 as its single list source", () => {
	assert.match(SOURCE, /listCatalogAssetsV2/);
	assert.match(SOURCE, /normalizeAssetRows/);
	assert.match(SOURCE, /listCatalogAssetsV2\(\s*buildAssetV2Query\(/);
	assert.doesNotMatch(SOURCE, /searchCatalogAssetsByTags|searchCatalog\(/);
});

test("data asset directory opens with empty filters and loads all visible assets", () => {
	assert.match(SOURCE, /useState\(""\)/);
	assert.match(SOURCE, /useState<string \| undefined>\(undefined\)/);
	assert.match(SOURCE, /void loadAssets\(urlFilters, 1, LEDGER_PAGE_SIZE\)/);
	assert.doesNotMatch(SOURCE, /localStorage|SEARCH_FORM_STORAGE_KEY|readStoredSearchForm/);
	assert.doesNotMatch(SOURCE, /请输入关键词或选择业务数据标签后再搜索|开始检索/);
});

test("data asset directory contains one compact search module and one table", () => {
	assert.match(SOURCE, /<PageHeader title="数据资产目录"/);
	for (const id of [
		"catalog-search-keyword",
		"catalog-search-domain",
		"catalog-search-source-type",
		"catalog-search-classification",
		"catalog-search-layer",
	]) {
		assert.match(SOURCE, new RegExp(`id="${id}"`));
	}
	assert.match(SOURCE, /<AssetTagFilter/);
	assert.match(SOURCE, />\s*重置\s*</);
	assert.match(SOURCE, />\s*查询\s*</);
	assert.match(SOURCE, /<AssetLedgerView/);
	assert.match(SOURCE, /records=\{assetRows\}/);
	assert.match(SOURCE, /loading=\{loading\}/);
	assert.match(SOURCE, /total=\{pageState\.total\}/);
	assert.match(SOURCE, /onPageChange=\{/);
});

test("data asset directory removes the former card and workbench presentation", () => {
	assert.doesNotMatch(SOURCE, /Segmented|view === "card"|view === "table"/);
	assert.doesNotMatch(SOURCE, /资产名片|治理视图|保存当前条件|恢复已存条件|应用资产清单筛选/);
	assert.doesNotMatch(SOURCE, /resolveAssetReadiness|DatabaseOutlined|AssetTagChips/);
	assert.doesNotMatch(SOURCE, /<Link|<Pagination/);
});

test("data asset directory preserves filter deep links and provides an explicit reset", () => {
	assert.match(SOURCE, /readTagIds\(searchParams\)/);
	assert.match(SOURCE, /writeTagIds\(next, selectedTagIds\)/);
	assert.match(SOURCE, /searchParams\.get\("layer"\)/);
	assert.match(SOURCE, /searchParams\.get\("governance"\)/);
	assert.match(SOURCE, /searchParams\.get\("unclassified"\) === "1"/);
	assert.match(SOURCE, /searchParams\.get\("stale"\) === "1"/);
	assert.match(SOURCE, /params\.delete\("unclassified"\)/);
	assert.match(SOURCE, /params\.delete\("stale"\)/);
	assert.match(SOURCE, /const handleReset/);
});

test("the legacy tag workspace remains a compatible branch without conditional hooks", () => {
	assert.match(SOURCE, /function DataAssetDirectoryPage\(\)/);
	assert.match(SOURCE, /searchParams\.get\("tab"\) === "catalog-tags"/);
	assert.match(SOURCE, /return <AssetTagsWorkspace \/>/);
	const wrapperStart = SOURCE.indexOf("export default function DataSearchPage");
	const directoryStart = SOURCE.indexOf("function DataAssetDirectoryPage");
	assert.ok(wrapperStart >= 0 && directoryStart > wrapperStart);
	assert.doesNotMatch(SOURCE.slice(wrapperStart, directoryStart), /useMemo|useState|useEffect/);
});
