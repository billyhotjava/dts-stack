import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

// Sprint-57 F4 拆分：表格布局常量移入共享层
const ASSET_PAGE_SHARED = readFileSync(new URL("./assets/assetPageShared.tsx", import.meta.url), "utf8");
const ASSET_LEDGER_VIEW = readFileSync(new URL("./assets/AssetLedgerView.tsx", import.meta.url), "utf8");
const DATA_PRODUCTS_SOURCE = readFileSync(new URL("./DataProductsPage.tsx", import.meta.url), "utf8");
const WORKBENCH_SOURCE = readFileSync(new URL("../workbench/index.tsx", import.meta.url), "utf8");
const DATA_MANAGEMENT_SOURCE = readFileSync(
	new URL("../workbench/DataManagementWorkbenchPage.tsx", import.meta.url),
	"utf8",
);
const STATIC_ROUTES = readFileSync(
	new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url),
	"utf8",
);
const DYNAMIC_RESOLVER = readFileSync(
	new URL("../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url),
	"utf8",
);

test("Sprint-49 F2 keeps asset ledger table stable while exposing the consumption chain", () => {
	assert.match(ASSET_PAGE_SHARED, /const ASSET_TABLE_SCROLL_X = 1440/);
	assert.match(ASSET_PAGE_SHARED, /const ASSET_ACTION_COLUMN_WIDTH = 320/);
	assert.match(ASSET_LEDGER_VIEW, /tableLayout="fixed"/);
	assert.match(ASSET_LEDGER_VIEW, /className="catalog-assets-table"/);
	assert.match(ASSET_LEDGER_VIEW, /className="catalog-assets-actions"/);
	assert.match(ASSET_LEDGER_VIEW, /width: ASSET_ACTION_COLUMN_WIDTH/);
	assert.match(ASSET_LEDGER_VIEW, /Dropdown/);
	assert.match(ASSET_LEDGER_VIEW, /更多/);
	assert.match(ASSET_LEDGER_VIEW, /router\.push\(`\/catalog\/data-products\?assetId=\$\{row\.id\}`\)/);
	assert.match(ASSET_LEDGER_VIEW, /router\.push\(`\/services\/apis\?assetId=\$\{row\.id\}`\)/);
});

test("Sprint-49 F2 data products always have an enterprise consumption handoff", () => {
	assert.match(DATA_PRODUCTS_SOURCE, /const DEFAULT_CONSUMER_ENTRY = "\/workbench\?section=consumption"/);
	assert.match(DATA_PRODUCTS_SOURCE, /resolveConsumerEntryRoute/);
	assert.match(DATA_PRODUCTS_SOURCE, /product\.consumerEntry \|\| DEFAULT_CONSUMER_ENTRY/);
	assert.match(DATA_PRODUCTS_SOURCE, /productId=\$\{encodeURIComponent\(String\(product\.id\)\)\}/);
	assert.match(DATA_PRODUCTS_SOURCE, /Tooltip[\s\S]*title=\{readiness\.ready/);
	assert.match(DATA_PRODUCTS_SOURCE, /查看消费/);
	assert.doesNotMatch(DATA_PRODUCTS_SOURCE, /disabled=\{!product\.consumerEntry\}/);
});

test("Sprint-49 F2 routes business consumption into the single personalized workbench", () => {
	assert.match(WORKBENCH_SOURCE, /activeSection === "data-management" \|\| activeSection === "consumption"/);
	assert.match(WORKBENCH_SOURCE, /const productId = searchParams\.get\("productId"\)/);
	assert.match(WORKBENCH_SOURCE, /focus=\{activeSection === "consumption" \? "consumption" : "data-management"\}/);
	assert.match(DATA_MANAGEMENT_SOURCE, /focus\?:\s*"data-management" \| "consumption"/);
	assert.match(DATA_MANAGEMENT_SOURCE, /productId\?:\s*string \| null/);
	assert.match(DATA_MANAGEMENT_SOURCE, /data-testid=\{isConsumptionFocus/);
	assert.match(DATA_MANAGEMENT_SOURCE, /"data-consumption-workbench-section"/);
	assert.match(DATA_MANAGEMENT_SOURCE, /消费发布/);
	assert.match(DATA_MANAGEMENT_SOURCE, /productId/);
});

test("Sprint-49 F2 preserves legacy services consumption query context during redirects", () => {
	assert.match(STATIC_ROUTES, /WorkbenchSectionRedirect/);
	assert.match(STATIC_ROUTES, /section="consumption"/);
	assert.match(STATIC_ROUTES, /next\.set\("section", section\)/);
	assert.match(DYNAMIC_RESOLVER, /buildDirectRedirectPath/);
	assert.match(DYNAMIC_RESOLVER, /next\.set\("section", section\)/);
	assert.match(DYNAMIC_RESOLVER, /location\.search/);
});
