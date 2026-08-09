import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { classificationRank, normalizeClassification } from "../../utils/classification.ts";

const PAGE_URL = new URL("./MetadataManagementPage.tsx", import.meta.url);
const PAGE_PATH = fileURLToPath(PAGE_URL);
const PAGE_SOURCE = existsSync(PAGE_PATH) ? readFileSync(PAGE_PATH, "utf8") : "";
const STATIC_ROUTES = readFileSync(
	new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url),
	"utf8",
);
const DYNAMIC_RESOLVER = readFileSync(
	new URL("../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url),
	"utf8",
);
const ZH_LOCALE = readFileSync(new URL("../../locales/lang/zh_CN/sys.json", import.meta.url), "utf8");
const EN_LOCALE = readFileSync(new URL("../../locales/lang/en_US/sys.json", import.meta.url), "utf8");

test("metadata management has a real data asset page and route", () => {
	assert.notEqual(PAGE_SOURCE, "", "MetadataManagementPage.tsx must exist");
	assert.match(STATIC_ROUTES, /MetadataManagementPage/);
	assert.match(STATIC_ROUTES, /path: "catalog\/metadata-management"/);
	assert.match(DYNAMIC_RESOLVER, /"\/catalog\/metadata-management": "\/pages\/catalog\/MetadataManagementPage"/);
	assert.match(ZH_LOCALE, /"dataPortalMetadataManagement":\s*"元数据管理"/);
	assert.match(EN_LOCALE, /"dataPortalMetadataManagement":\s*"Metadata management"/);
});

test("metadata management is asset semantic governance, not collection console", () => {
	assert.match(PAGE_SOURCE, /listCatalogAssetsV2/);
	assert.match(PAGE_SOURCE, /listCatalogGovernanceIntakeAssets/);
	assert.match(PAGE_SOURCE, /useCatalogMaintainerAccess/);
	assert.doesNotMatch(PAGE_SOURCE, /syncCatalogAssetsV2|同步 OpenMetadata/);
	assert.match(PAGE_SOURCE, /元数据管理/);
	assert.match(PAGE_SOURCE, /当前页待补齐/);
	assert.match(PAGE_SOURCE, /当前页缺负责人/);
	assert.match(PAGE_SOURCE, /当前页缺密级/);
	assert.match(PAGE_SOURCE, /当前页缺主题域/);
	assert.match(PAGE_SOURCE, /OpenMetadata 未映射/);
	assert.match(PAGE_SOURCE, /资产元数据加载失败/);
	// B3 收敛：字段契约入口移入「更多」菜单，未定密时仍不提供该入口（条件语义保留）
	assert.match(PAGE_SOURCE, /isBlank\(row\.classification\)\s*\?\s*\[\]/);
	assert.match(PAGE_SOURCE, /updateCatalogAssetV2Governance/);
	assert.match(PAGE_SOURCE, /补齐密级/);
	assert.match(PAGE_SOURCE, /保存密级/);
	assert.match(PAGE_SOURCE, /密级只允许升高、不能降级/);
	assert.match(PAGE_SOURCE, /disabled: currentRank !== undefined/);
	assert.match(PAGE_SOURCE, /loadAssetsImmediately/);
	assert.match(PAGE_SOURCE, /clearTimeout/);
	assert.match(PAGE_SOURCE, /\/catalog\/metadata/);
	assert.doesNotMatch(PAGE_SOURCE, /采集任务与触发|采集历史|Schema 漂移工单/);
});

test("metadata management no longer hosts the data-asset tag dictionary", () => {
	assert.doesNotMatch(PAGE_SOURCE, /TagManagementTab/);
	assert.doesNotMatch(PAGE_SOURCE, /catalog-tags|label:\s*"数据标签"/);
	assert.doesNotMatch(PAGE_SOURCE, /useCatalogTagGovernanceAccess/);
	assert.equal((PAGE_SOURCE.match(/同步 OpenMetadata/g) || []).length, 0);
});

test("classification rank accepts backend-compatible legacy aliases", () => {
	assert.equal(normalizeClassification("DATA_SECRET", undefined), "SECRET");
	assert.equal(normalizeClassification("IMPORTANT", undefined), "SECRET");
	assert.equal(normalizeClassification("CORE", undefined), "CONFIDENTIAL");
	assert.equal(normalizeClassification("3", undefined), "CONFIDENTIAL");
	assert.equal(normalizeClassification("机密级", undefined), "CONFIDENTIAL");
	assert.equal(classificationRank("DATA_SECRET"), 2);
	assert.equal(classificationRank("CORE"), 3);
});

test("metadata management shares the URL filter protocol with the ledger", () => {
	assert.match(PAGE_SOURCE, /searchParams\.get\("classification"\) \|\| "ALL"/);
	assert.match(PAGE_SOURCE, /searchParams\.get\("governance"\) \|\| "ALL"/);
	assert.match(PAGE_SOURCE, /searchParams\.get\("match"\) \|\| "ALL"/);
	assert.match(PAGE_SOURCE, /params\.set\("classification", classification\)/);
	assert.match(PAGE_SOURCE, /params\.set\("governance", governanceStatus\)/);
	assert.match(PAGE_SOURCE, /params\.set\("match", matchStatus\)/);
	assert.match(PAGE_SOURCE, /setSearchParams\(params, \{ replace: true \}\)/);
});
