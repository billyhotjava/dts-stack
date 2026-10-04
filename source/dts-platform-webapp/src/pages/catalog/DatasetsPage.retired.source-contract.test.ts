import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const PAGE_PATH = new URL("./DatasetsPage.tsx", import.meta.url).pathname;
const SEARCH = readFileSync(new URL("./DataSearchPage.tsx", import.meta.url), "utf8");
const REDIRECT = readFileSync(new URL("./LegacyAssetLedgerRedirect.tsx", import.meta.url), "utf8");
const RESOLVER = readFileSync(new URL("../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url), "utf8");
const STATIC_ROUTES = readFileSync(
	new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url),
	"utf8",
);
const MENU_SEED = readFileSync(
	new URL("../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
	"utf8",
);

test("the legacy asset ledger page is retired", () => {
	assert.ok(!existsSync(PAGE_PATH), "DatasetsPage.tsx must be deleted");
});

test("ledger deep links converge to the search page table view with filters preserved", () => {
	assert.match(REDIRECT, /params\.set\("view", "table"\)/);
	assert.match(REDIRECT, /Navigate to=\{`\/catalog\/search/);
	assert.match(RESOLVER, /"\/catalog\/assets\/ledger": "\/pages\/catalog\/LegacyAssetLedgerRedirect"/);
	assert.match(STATIC_ROUTES, /<LegacyAssetLedgerRedirect \/>/);
	assert.doesNotMatch(STATIC_ROUTES, /<DatasetsPage \/>/);
});

test("the ledger menu entry is removed from the portal seed", () => {
	assert.doesNotMatch(MENU_SEED, /assets\/ledger/);
	assert.doesNotMatch(MENU_SEED, /"title": "资产台账"/);
});

test("the search page hosts one paginated asset directory table", () => {
	assert.match(SEARCH, /<AssetLedgerView/);
	assert.match(SEARCH, /records=\{assetRows\}/);
	assert.match(SEARCH, /loading=\{loading\}/);
	assert.match(SEARCH, /onAssetChanged=\{\(\) => void loadAssets\(urlFilters, pageState\.page, pageState\.size\)\}/);
	assert.doesNotMatch(SEARCH, /view === "table"|Segmented|治理视图/);
});
