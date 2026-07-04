import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE = readFileSync(new URL("./AssetOverviewPage.tsx", import.meta.url), "utf8");
const STATIC_ROUTES = readFileSync(new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url), "utf8");
const DYNAMIC_RESOLVER = readFileSync(new URL("../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url), "utf8");

test("asset overview page is a pure statistics view backed by the aggregate endpoint", () => {
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
	assert.match(PAGE, /router\.replace/);
});

test("overview and ledger are two routes", () => {
	assert.match(STATIC_ROUTES, /path: "catalog\/assets", element: <S><AssetOverviewPage \/><\/S>/);
	assert.match(STATIC_ROUTES, /path: "catalog\/assets\/ledger", element: <S><DatasetsPage \/><\/S>/);
	assert.match(DYNAMIC_RESOLVER, /"\/catalog\/assets": "\/pages\/catalog\/AssetOverviewPage"/);
	assert.match(DYNAMIC_RESOLVER, /"\/catalog\/assets\/ledger": "\/pages\/catalog\/DatasetsPage"/);
});
