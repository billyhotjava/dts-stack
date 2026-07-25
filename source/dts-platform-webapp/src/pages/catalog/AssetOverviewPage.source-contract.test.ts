import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE = readFileSync(new URL("./AssetOverviewPage.tsx", import.meta.url), "utf8");
const ACCESS_HOOK_SOURCE = readFileSync(new URL("../../hooks/useModuleManageAccess.ts", import.meta.url), "utf8");
const STATIC_ROUTES = readFileSync(
	new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url),
	"utf8",
);
const DYNAMIC_RESOLVER = readFileSync(
	new URL("../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url),
	"utf8",
);

test("asset map tab remains a pure statistics view backed by the aggregate endpoint", () => {
	// 数据源 = 全量聚合端点，不是分页列表的假统计
	assert.match(PAGE, /getCatalogAssetsOverview/);
	assert.match(PAGE, /asset-overview-matrix/);
	assert.match(PAGE, /分层×主题域矩阵/);
	// 统计到执行的唯一通道：带参下钻台账
	assert.match(PAGE, /\/catalog\/assets\/ledger/);
	// 零输入控件：无筛选 Select、无搜索、无诊断菜单、无分页
	assert.doesNotMatch(PAGE, /<Select/);
	assert.doesNotMatch(PAGE, /asset-ops-menu/);
	assert.doesNotMatch(PAGE, /Pagination/);
	assert.doesNotMatch(PAGE, /SearchOutlined/);
	// truncated 提示
	assert.match(PAGE, /truncated/);
});

test("asset overview keeps ?view=table deep links working via ledger redirect", () => {
	assert.match(PAGE, /view.*table/);
	assert.match(PAGE, /params\.delete\("tab"\)/);
	assert.match(PAGE, /router\.replace/);
});

test("data asset page hosts the tag dictionary as a shareable sibling tab", () => {
	assert.match(PAGE, /TagManagementTab/);
	assert.match(PAGE, /useCatalogTagGovernanceAccess/);
	assert.match(PAGE, /key:\s*"asset-map"[\s\S]*label:\s*"资产地图"/);
	assert.match(PAGE, /key:\s*"catalog-tags"[\s\S]*label:\s*"数据标签"/);
	assert.match(PAGE, /searchParams\.get\("tab"\) === "catalog-tags"/);
	assert.match(PAGE, /activeTab === "asset-map"/);
	assert.match(PAGE, /<TagManagementTab canManage=\{canManageCatalog\}/);
	assert.match(PAGE, /标签字典用于数据资产打标/);
	const tagGovernanceHook = ACCESS_HOOK_SOURCE.match(
		/export const useCatalogTagGovernanceAccess = \(\) => \{[\s\S]*?^};/m,
	);
	assert.ok(tagGovernanceHook, "data tag governance must keep its dedicated access hook");
	assert.match(tagGovernanceHook[0], /hasMaintainerRole\(roles \|\| \[\]\)/);
	assert.doesNotMatch(tagGovernanceHook[0], /permissions|hasModulePermission/);
});

test("overview and ledger are two routes", () => {
	assert.match(STATIC_ROUTES, /path:\s*"catalog\/assets"[\s\S]*?<AssetOverviewPage \/>/);
	assert.match(STATIC_ROUTES, /path:\s*"catalog\/assets\/ledger"[\s\S]*?<DatasetsPage \/>/);
	assert.match(DYNAMIC_RESOLVER, /"\/catalog\/assets": "\/pages\/catalog\/AssetOverviewPage"/);
	assert.match(DYNAMIC_RESOLVER, /"\/catalog\/assets\/ledger": "\/pages\/catalog\/DatasetsPage"/);
});
