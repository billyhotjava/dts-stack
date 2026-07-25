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

test("data search sends the same repeated tag filter to every compatible search backend", () => {
	assert.match(SOURCE, /useSearchParams/);
	assert.match(SOURCE, /const selectedTagIds = useMemo\([\s\S]*?readTagIds\(searchParams\)/);
	assert.match(SOURCE, /<AssetTagFilter/);
	assert.match(SOURCE, /writeTagIds\(searchParams,\s*nextIds\)/);
	const calls = SOURCE.match(/tagIds:\s*effectiveSelectedTagIds/g) || [];
	assert.equal(calls.length, 3, "exact, assets-v2 and legacy search must receive the same effective tagIds");
	assert.match(SOURCE, /if \(!trimmed && effectiveSelectedTagIds\.length === 0\)/);
});

test("data search consumes hydrated tags without per-result requests", () => {
	assert.match(SOURCE, /assetType:\s*item\.assetType/);
	assert.match(SOURCE, /assetTags:\s*Array\.isArray\(item\.assetTags\)/);
	assert.match(SOURCE, /datasetAssetKey:\s*item\.datasetAssetKey/);
	assert.match(SOURCE, /datasetAssetTags:\s*Array\.isArray\(item\.datasetAssetTags\)/);
	assert.match(SOURCE, /所属数据集标签/);
	assert.match(SOURCE, /<AssetTagChips/);
	assert.doesNotMatch(SOURCE, /listAssetTags/);
});

test("data search renders exact tag hits for every backend-supported asset type", () => {
	assert.match(SOURCE, /searchCatalogAssetsByTags/);
	assert.match(SOURCE, /normalizeExactTagRows/);
	assert.match(SOURCE, /assetType:\s*String\(item\.assetType/);
	assert.match(SOURCE, /assetKey:\s*String\(item\.assetKey/);
	assert.match(SOURCE, /source:\s*"标签索引"/);
	assert.match(SOURCE, /tagSearchResult\.status === "fulfilled"/);
	assert.doesNotMatch(SOURCE, /SUPPORTED_TAG_ASSET_TYPES|TAG_ASSET_TYPE_ALLOWLIST/);
});

test("legacy asset portal disables tag filtering with an explicit explanation", () => {
	assert.match(SOURCE, /ASSET_PORTAL_V2_ENABLED/);
	assert.match(SOURCE, /const effectiveSelectedTagIds = ASSET_PORTAL_V2_ENABLED \? selectedTagIds : \[\]/);
	assert.match(SOURCE, /disabled=\{!ASSET_PORTAL_V2_ENABLED\}/);
	assert.match(SOURCE, /旧版资产门户未启用业务数据标签筛选/);
});

test("asset result cards use keyboard-native links instead of buttons that contain tag lists", () => {
	assert.match(SOURCE, /import \{ Link, useSearchParams \} from "react-router"/);
	assert.equal(SOURCE.match(/<Link/g)?.length, 2);
	assert.equal(SOURCE.match(/<\/Link>/g)?.length, 2);
	assert.match(SOURCE, /to=\{`\/catalog\/datasets\/\$\{row\.id\}`\}/);
	assert.doesNotMatch(
		SOURCE,
		/<button[\s\S]*?<AssetTagChips tags=\{row\.assetTags \|\| \[\]\} variant="inline" \/>[\s\S]*?<\/button>/,
	);
});

test("data search ignores stale responses and clears the last URL-backed tag filter", () => {
	assert.match(SOURCE, /searchRequestSequence/);
	assert.match(SOURCE, /sequence !== searchRequestSequence\.current/);
	assert.match(SOURCE, /runSearchRef\.current\(false\)/);
	assert.match(SOURCE, /setResults\(\[\]\)/);
	assert.doesNotMatch(SOURCE, /if \(selectedTagIds\.length > 0\)/);
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
