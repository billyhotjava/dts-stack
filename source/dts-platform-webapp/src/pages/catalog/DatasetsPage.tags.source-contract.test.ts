import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");
const SHARED = readFileSync(new URL("./assets/assetPageShared.tsx", import.meta.url), "utf8");
const LEDGER = readFileSync(new URL("./assets/AssetLedgerView.tsx", import.meta.url), "utf8");
const DIALOGS = readFileSync(new URL("./assets/AssetLedgerDialogs.tsx", import.meta.url), "utf8");
const TOOLBAR = readFileSync(new URL("./assets/AssetLedgerToolbar.tsx", import.meta.url), "utf8");
const RECONCILIATION = readFileSync(new URL("./assets/AssetReconciliationPanel.tsx", import.meta.url), "utf8");
const API = readFileSync(new URL("../../api/platformApi.ts", import.meta.url), "utf8");
const DATASETS_PAGE_MAX_LINES = 800;

test("asset ledger keeps repeated tag ids in URL state and only adds them to the list query", () => {
	assert.match(SOURCE, /useSearchParams/);
	assert.match(SOURCE, /readTagIds\(searchParams\)/);
	assert.match(SOURCE, /writeTagIds\(searchParams,\s*nextIds\)/);
	assert.match(SOURCE, /const buildAssetListQuery/);
	assert.match(SOURCE, /tagIds:\s*selectedTagIds\.length/);
	assert.match(SOURCE, /listCatalogAssetsV2\(buildAssetListQuery\(page,\s*size\)\)/);
	assert.match(
		SOURCE,
		/const query = buildAssetQuery\(1,\s*20\)/,
		"governance endpoints must keep using the query without tagIds",
	);
	assert.match(API, /tagIds\?: string\[\]/);
	assert.match(API, /paramsSerializer:\s*\{\s*indexes:\s*null\s*\}/);
});

test("asset ledger uses the shared tag filter, ten-row paging and batch-hydrated tags", () => {
	assert.match(TOOLBAR, /<AssetTagFilter/);
	assert.match(SOURCE, /selectedTagIds\.length > 0/);
	assert.match(SOURCE, /assetType:\s*item\.assetType/);
	assert.match(SOURCE, /assetKey:\s*item\.assetKey/);
	assert.match(SOURCE, /assetTags:\s*Array\.isArray\(item\.assetTags\)/);
	assert.match(SOURCE, /const buildAssetQuery = \(page = 1,\s*size = LEDGER_PAGE_SIZE\)/);
	assert.match(SOURCE, /const loadDatasets = async \(page = 1,\s*size = LEDGER_PAGE_SIZE\)/);
	assert.match(SOURCE, /nextSize !== pageState\.size \? 1 : page/);
	assert.match(SOURCE, /const pageSizeRef = useRef\(LEDGER_PAGE_SIZE\)/);
	assert.match(SOURCE, /loadDatasets\(1,\s*pageSizeRef\.current\)/);
	assert.doesNotMatch(SOURCE, /\[selectedTagIdsKey,\s*pageState\.size\]/);
	assert.match(SHARED, /assetTags\?: CatalogTagDto\[\]/);
	assert.match(LEDGER, /<AssetTagChips tags=\{row\.assetTags \|\| \[\]\} variant="inline"/);
	assert.doesNotMatch(LEDGER, /listAssetTags|\/asset-tags/);
});

test("asset ledger keeps operational components extracted and the route page below 800 lines", () => {
	assert.ok(SOURCE.split(/\r?\n/).length - 1 < DATASETS_PAGE_MAX_LINES);
	assert.match(SOURCE, /<AssetLedgerDialogs/);
	assert.match(SOURCE, /<AssetLedgerToolbar/);
	assert.match(SOURCE, /<AssetReconciliationPanel/);
	assert.match(DIALOGS, /治理缺口处置工作台/);
	assert.match(DIALOGS, /资产身份解析失败/);
	assert.match(RECONCILIATION, /核心页面回归清单/);
	assert.doesNotMatch(SOURCE, /<CompactTable/);
});
