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
	assert.match(SOURCE, /if \(!trimmed && effectiveSelectedTagIds\.length === 0\)/);
});

test("data search hosts the ledger table view with pagination shared by both views", () => {
	assert.match(SOURCE, /<AssetLedgerView/);
	assert.match(SOURCE, /records=\{assetRows\}/);
	assert.match(SOURCE, /view === "table"/);
	assert.match(SOURCE, /Segmented/);
	assert.match(SOURCE, /searchParams\.get\("view"\) === "table"/);
	assert.match(SOURCE, /if \(view === "table"\) params\.set\("view", "table"\)/);
	assert.match(SOURCE, /<Pagination/);
	assert.match(SOURCE, /onChange=\{\(page, size\) => void runSearch\(false, page, size\)\}/);
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

test("URL-backed tag search starts with the synchronously hydrated saved form", () => {
	assert.match(SOURCE, /function readStoredSearchForm\(\): StoredSearchForm/);
	assert.match(SOURCE, /const \[initialSearchForm\] = useState\(readStoredSearchForm\)/);
	assert.match(SOURCE, /useState\(initialSearchForm\.keyword\)/);
	assert.match(SOURCE, /useState<string \| undefined>\(initialSearchForm\.domain\)/);
	assert.match(SOURCE, /useState<string>\(initialSearchForm\.assetType\)/);
	assert.match(SOURCE, /useState<string>\(initialSearchForm\.datasetType\)/);
	assert.match(SOURCE, /useState<string>\(initialSearchForm\.classification\)/);
	assert.match(SOURCE, /useState<string>\(initialSearchForm\.warehouseLayer\)/);

	const hydration = SOURCE.indexOf("const [initialSearchForm] = useState(readStoredSearchForm)");
	const urlSearch = SOURCE.indexOf("void runSearchRef.current(false)");
	assert.ok(hydration >= 0 && urlSearch > hydration, "saved fields must hydrate before the URL tag effect searches");
	const initialEffectStart = SOURCE.indexOf("useEffect(() => {", hydration);
	const initialEffectEnd = SOURCE.indexOf("\n\t}, []);", initialEffectStart);
	assert.ok(initialEffectStart > hydration && initialEffectEnd > initialEffectStart);
	const initialEffect = SOURCE.slice(initialEffectStart, initialEffectEnd);
	assert.doesNotMatch(initialEffect, /SEARCH_FORM_STORAGE_KEY|setKeyword/);
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
