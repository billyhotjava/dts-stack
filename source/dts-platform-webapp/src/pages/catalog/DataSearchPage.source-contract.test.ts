import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DataSearchPage.tsx", import.meta.url), "utf8");

test("data search page consumes assets-v2 as the single data source", () => {
	assert.match(SOURCE, /listCatalogAssetsV2/);
	assert.match(SOURCE, /normalizeAssetRows/);
	assert.match(SOURCE, /assetKind: "ASSET"/);
	assert.match(SOURCE, /assets-v2/);
	assert.doesNotMatch(SOURCE, /searchCatalogAssetsByTags/);
	assert.doesNotMatch(SOURCE, /searchCatalog\(/);
	assert.doesNotMatch(SOURCE, /normalizeExactTagRows/);
	assert.doesNotMatch(SOURCE, /标签索引/);
});

test("data search keeps the shared asset filter cache and URL tag protocol", () => {
	assert.match(SOURCE, /catalog\.asset\.filter\.v2/);
	assert.doesNotMatch(SOURCE, /catalog\.asset\.filter\.v1/);
	assert.match(SOURCE, /setAssetType\(typeof saved\?\.assetType/);
	assert.match(SOURCE, /setKeyword\(typeof saved\?\.keyword/);
	assert.match(SOURCE, /useSearchParams/);
	assert.match(SOURCE, /const selectedTagIds = useMemo\([\s\S]*?readTagIds\(searchParams\)/);
	assert.match(SOURCE, /<AssetTagFilter/);
	assert.match(SOURCE, /writeTagIds\(searchParams,\s*nextIds\)/);
	assert.doesNotMatch(SOURCE, /if \(!trimmed && effectiveSelectedTagIds\.length === 0\)/);
});

test("data search hosts the ledger table view with pagination shared by both views", () => {
	assert.match(SOURCE, /<AssetLedgerView/);
	assert.match(SOURCE, /records=\{assetRows\}/);
	assert.match(SOURCE, /view === "table"/);
	assert.match(SOURCE, /Segmented/);
	assert.match(SOURCE, /searchParams\.get\("view"\) === "table"/);
	assert.match(SOURCE, /if \(view === "table"\) params\.set\("view", "table"\)/);
	assert.match(SOURCE, /<Pagination/);
	assert.match(SOURCE, /onChange=\{\(page, size\) => void runSearch\(page, size\)\}/);
	assert.match(SOURCE, /共 \{pageState\.total\} 条/);
});

test("data search keeps the governance-gap deep-link protocol", () => {
	assert.match(SOURCE, /searchParams\.get\("unclassified"\) === "1"/);
	assert.match(SOURCE, /searchParams\.get\("stale"\) === "1"/);
	assert.match(SOURCE, /governanceStatus: governanceFilter/);
	assert.match(SOURCE, /unclassified: unclassifiedFilter/);
	assert.match(SOURCE, /stale: staleFilter/);
	assert.match(SOURCE, /治理缺口筛选/);
	assert.match(SOURCE, /清除 URL 参数 \?unclassified\/\?stale/);
});

test("data search result cards are keyboard-native links without tag-list buttons", () => {
	assert.equal(SOURCE.match(/<Link/g)?.length, 1);
	assert.equal(SOURCE.match(/<\/Link>/g)?.length, 1);
	assert.match(SOURCE, /to=\{`\/catalog\/datasets\/\$\{row\.id\}`\}/);
	assert.doesNotMatch(
		SOURCE,
		/<button[\s\S]*?<AssetTagChips tags=\{row\.assetTags \|\| \[\]\} variant="inline" \/>[\s\S]*?<\/button>/,
	);
});

test("data search starts empty and saved conditions require explicit restoration", () => {
	assert.match(SOURCE, /function readStoredSearchForm\(\): StoredSearchForm/);
	assert.doesNotMatch(SOURCE, /useState\(readStoredSearchForm\)/);
	assert.match(SOURCE, /useState\(EMPTY_SEARCH_FORM\.keyword\)/);
	assert.match(SOURCE, /useState<string \| undefined>\(EMPTY_SEARCH_FORM\.domain\)/);
	assert.match(SOURCE, /useState<string \| undefined>\(EMPTY_SEARCH_FORM\.assetType\)/);
	assert.match(SOURCE, /useState<string \| undefined>\(EMPTY_SEARCH_FORM\.datasetType\)/);
	assert.match(SOURCE, /useState<string \| undefined>\(EMPTY_SEARCH_FORM\.classification\)/);
	assert.match(SOURCE, /useState<string \| undefined>\(EMPTY_SEARCH_FORM\.warehouseLayer\)/);
	assert.match(SOURCE, /const saved = readStoredSearchForm\(\)/);
	assert.match(SOURCE, /placeholder="请选择主题域"[\s\S]*?value=\{domain\}/);
	assert.match(SOURCE, /placeholder="请选择密级"[\s\S]*?value=\{classification\}/);
	assert.match(SOURCE, /placeholder="请选择分层"[\s\S]*?value=\{warehouseLayer\}/);
	assert.match(SOURCE, /placeholder="请选择资产类型"[\s\S]*?value=\{assetType\}/);
	assert.match(SOURCE, /placeholder="请选择数据源类型"[\s\S]*?value=\{datasetType\}/);
});

test("data search automatically queries all assets when no filters are selected", () => {
	assert.match(SOURCE, /const runSearch = async \(page = 1, size = LEDGER_PAGE_SIZE\)/);
	assert.match(
		SOURCE,
		/useEffect\(\(\) => \{\s*void selectedTagIdsKey;\s*void runSearchRef\.current\(\);\s*\}, \[selectedTagIdsKey\]\)/,
	);
	assert.doesNotMatch(SOURCE, /请输入关键词或选择业务数据标签后再搜索/);
	assert.doesNotMatch(SOURCE, /<EmptyState title="开始检索"/);
	const runSearchStart = SOURCE.indexOf("const runSearch = async");
	const runSearchEnd = SOURCE.indexOf("const runSearchRef", runSearchStart);
	assert.ok(runSearchStart >= 0 && runSearchEnd > runSearchStart);
	assert.doesNotMatch(SOURCE.slice(runSearchStart, runSearchEnd), /persistCurrentQuery\(\)/);
});

test("data search shares the asset-v2 query builder as the single source of truth", () => {
	assert.match(SOURCE, /import \{ buildAssetV2Query \} from "\.\/assets\/assetV2Query"/);
	assert.match(SOURCE, /listCatalogAssetsV2\(\s*buildAssetV2Query\(/);
	assert.match(SOURCE, /buildAssetV2Query\(\s*\{[\s\S]*?keyword: trimmed/);
	assert.match(SOURCE, /buildAssetV2Query\(\s*\{[\s\S]*?tagIds: effectiveSelectedTagIds/);
});

test("data search consumes the same URL deep-link protocol as the retired ledger", () => {
	assert.match(SOURCE, /searchParams\.get\("layer"\)/);
	assert.match(SOURCE, /searchParams\.get\("governance"\)/);
	assert.match(SOURCE, /setUnclassifiedFilter\(true\)/);
	assert.match(SOURCE, /setStaleFilter\(true\)/);
	assert.match(SOURCE, /params\.set\("domain", domain\)/);
	assert.match(SOURCE, /params\.set\("layer", warehouseLayer\)/);
	assert.match(SOURCE, /setSearchParams\(params, \{ replace: true \}\)/);
});

test("the domain filter never echoes a raw id when the option list is not ready", () => {
	assert.match(SOURCE, /回显兜底/);
	assert.match(SOURCE, /!options\.some\(\(option\) => option\.value === domain\)/);
	assert.match(SOURCE, /options\.push\(\{ label: matched \? matched\.name : "主题域", value: domain \}\)/);
});

test("data search is presented as a governed data asset directory", () => {
	assert.match(SOURCE, /<PageHeader title="数据资产目录"/);
	assert.match(SOURCE, /识别资产的业务归属、治理状态和技术来源/);
	assert.match(SOURCE, /主题域/);
	assert.match(SOURCE, /责任归属/);
	assert.match(SOURCE, /治理状态/);
	assert.match(SOURCE, /平台登记/);
	assert.doesNotMatch(SOURCE, /row\.source \|\| "assets-v2"/);
	assert.doesNotMatch(SOURCE, /label: "应用台账筛选"/);
});

test("data search keeps only the source type filter supported by assets-v2", () => {
	assert.match(SOURCE, /id="catalog-search-source-type"/);
	assert.match(SOURCE, /assetType: datasetType/);
	assert.doesNotMatch(SOURCE, /id="catalog-search-asset-type"/);
	assert.doesNotMatch(SOURCE, /placeholder="请选择资产类型"/);
});
