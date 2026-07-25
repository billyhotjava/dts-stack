import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";
import { fileURLToPath } from "node:url";

const PAGE_URL = new URL("./MetadataManagementPage.tsx", import.meta.url);
const PAGE_PATH = fileURLToPath(PAGE_URL);
const PAGE_SOURCE = existsSync(PAGE_PATH) ? readFileSync(PAGE_PATH, "utf8") : "";
const ACCESS_HOOK_SOURCE = readFileSync(new URL("../../hooks/useModuleManageAccess.ts", import.meta.url), "utf8");
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
	assert.match(PAGE_SOURCE, /getCatalogAssetsV2GovernanceGaps/);
	assert.match(PAGE_SOURCE, /syncCatalogAssetsV2/);
	assert.match(PAGE_SOURCE, /元数据管理/);
	assert.match(PAGE_SOURCE, /资产语义元数据/);
	assert.match(PAGE_SOURCE, /待补齐/);
	assert.match(PAGE_SOURCE, /缺负责人/);
	assert.match(PAGE_SOURCE, /缺密级/);
	assert.match(PAGE_SOURCE, /缺主题域/);
	assert.match(PAGE_SOURCE, /OpenMetadata 未映射/);
	assert.match(PAGE_SOURCE, /\/catalog\/metadata/);
	assert.doesNotMatch(PAGE_SOURCE, /采集任务与触发|采集历史|Schema 漂移工单/);
});

test("metadata management hosts data tags without adding another route", () => {
	assert.match(PAGE_SOURCE, /TagManagementTab/);
	assert.match(PAGE_SOURCE, /key:\s*"asset-metadata"/);
	assert.match(PAGE_SOURCE, /label:\s*"资产元数据"/);
	assert.match(PAGE_SOURCE, /key:\s*"catalog-tags"/);
	assert.match(PAGE_SOURCE, /label:\s*"数据标签"/);
	assert.match(PAGE_SOURCE, /useCatalogTagGovernanceAccess/);
	assert.doesNotMatch(PAGE_SOURCE, /useCatalogManageAccess/);
	assert.match(PAGE_SOURCE, /activeTab === "asset-metadata"/);
	assert.match(PAGE_SOURCE, /<TagManagementTab canManage=\{canManageCatalog\}/);
	const tagGovernanceHook = ACCESS_HOOK_SOURCE.match(
		/export const useCatalogTagGovernanceAccess = \(\) => \{[\s\S]*?^};/m,
	);
	assert.ok(tagGovernanceHook, "data tag governance must have a dedicated access hook");
	assert.match(tagGovernanceHook[0], /hasMaintainerRole\(roles \|\| \[\]\)/);
	assert.doesNotMatch(tagGovernanceHook[0], /permissions|hasModulePermission/);
	assert.equal(
		(PAGE_SOURCE.match(/同步 OpenMetadata/g) || []).length,
		1,
		"the synchronization action must remain in the asset metadata tab only",
	);
});
