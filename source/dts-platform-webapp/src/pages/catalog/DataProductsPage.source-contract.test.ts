import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DataProductsPage.tsx", import.meta.url), "utf8");
const MENU_SEED = readFileSync(
	new URL("../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
	"utf8",
);
const ROLE_DEFAULTS = readFileSync(
	new URL("../../../../dts-admin/src/main/resources/config/data/role-menu-defaults.json", import.meta.url),
	"utf8",
);

test("data products page configures member assets and metrics instead of text-only CRUD", () => {
	assert.match(SOURCE, /listCatalogAssetsV2/);
	assert.match(SOURCE, /listIndicators/);
	assert.match(SOURCE, /name="datasetIds"/);
	assert.match(SOURCE, /name="indicatorCodes"/);
	assert.match(SOURCE, /成员资产/);
	assert.match(SOURCE, /核心指标/);
});

test("data products page exposes enterprise product contract fields", () => {
	assert.match(SOURCE, /name="classification"/);
	assert.match(SOURCE, /name="freshnessSla"/);
	assert.match(SOURCE, /name="lifecycleStatus"/);
	assert.match(SOURCE, /name="visibility"/);
	assert.match(SOURCE, /name="consumerEntry"/);
	assert.match(SOURCE, /刷新 SLA/);
	assert.match(SOURCE, /消费可见性/);
});

test("data products page is reachable from the data asset portal menu", () => {
	assert.match(MENU_SEED, /"title": "数据产品"/);
	assert.match(MENU_SEED, /"externalLink": "\/catalog\/data-products"/);
	assert.match(ROLE_DEFAULTS, /"code": "sys\.nav\.portal\.dataPortalProducts"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/catalog\/data-products"/);
});
