import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE = readFileSync(new URL("./AssetOverviewPage.tsx", import.meta.url), "utf8");
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
	assert.match(PAGE, /tagCoveragePercent/);
	assert.match(PAGE, /标签覆盖/);
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

test("asset map is summary-only and relocates the tag dictionary to the ledger", () => {
	assert.doesNotMatch(PAGE, /TagManagementTab/);
	assert.doesNotMatch(PAGE, /useCatalogTagGovernanceAccess/);
	assert.doesNotMatch(PAGE, /key:\s*"catalog-tags"[\s\S]*label:\s*"数据标签"/);
	assert.match(PAGE, /searchParams\.get\("tab"\) === "catalog-tags"/);
	assert.match(PAGE, /\/catalog\/assets\/ledger/);
	assert.match(PAGE, /params\.set\("tab",\s*"catalog-tags"\)/);
	assert.match(PAGE, /router\.replace/);
});

test("overview and ledger are two routes", () => {
	assert.match(STATIC_ROUTES, /path:\s*"catalog\/assets"[\s\S]*?<AssetOverviewPage \/>/);
	assert.match(STATIC_ROUTES, /path:\s*"catalog\/assets\/ledger"[\s\S]*?<DatasetsPage \/>/);
	assert.match(DYNAMIC_RESOLVER, /"\/catalog\/assets": "\/pages\/catalog\/AssetOverviewPage"/);
	assert.match(DYNAMIC_RESOLVER, /"\/catalog\/assets\/ledger": "\/pages\/catalog\/DatasetsPage"/);
});

test("范围选择进入 URL 而非组件内部 state", () => {
	assert.match(PAGE, /searchParams\.get\("domain"\)/);
	assert.doesNotMatch(PAGE, /useState<string \| undefined>\(\)/);
});

test("使用共享导航组件，不再自建 antd Tree", () => {
	assert.match(PAGE, /DomainScopeNav/);
	assert.doesNotMatch(PAGE, /<Tree\b/);
});

test("域树请求带上统计参数，且不再单独调用 listDomains", () => {
	assert.match(PAGE, /getDomainTree\(\{\s*withStats:\s*true\s*\}\)/);
	// 只断言"没有调用"，注释里提及旧实现是允许的
	assert.doesNotMatch(PAGE, /listDomains\s*\(/);
	assert.doesNotMatch(PAGE, /import\s*\{[^}]*listDomains/);
});

test("不再存在 fallback- 静默降级", () => {
	assert.doesNotMatch(PAGE, /fallback-/);
});

test("地图页 chrome 按钮不超过 1 个", () => {
	const buttons = [...PAGE.matchAll(/<Button\b/g)];
	assert.ok(buttons.length <= 1, `地图页应只保留 1 个 icon-only 刷新按钮，实际 ${buttons.length} 个`);
});

test("不再有进入台账与去台账处置按钮", () => {
	// 只断言不存在这两个按钮；说明文字里出现「进入台账查看明细」是允许的
	assert.doesNotMatch(PAGE, /<Button[^>]*>\s*进入台账/);
	assert.doesNotMatch(PAGE, /去台账处置\s*<\/Button>/);
	assert.doesNotMatch(PAGE, /去台账处置/);
});

test("矩阵列不再硬编码截断且单域时退化", () => {
	assert.doesNotMatch(PAGE, /\.slice\(0,\s*8\)/);
	assert.match(PAGE, /isSingleDomainScope/);
	assert.match(PAGE, /其他域（\$\{rest\.length\}）/);
	assert.match(PAGE, /打开该层全部主题域的台账/);
});

test("分层呈现带中文 label 与弱化代号", () => {
	assert.match(PAGE, /meta\.code/);
});

test("截断警告文案反映真实原因", () => {
	assert.doesNotMatch(PAGE, /资产数量超过扫描上限/);
	assert.match(PAGE, /统计上限/);
});

test("五张 KPI 卡已收敛为总量卡 + 缺口面板", () => {
	assert.match(PAGE, /GovernanceGapPanel/);
	assert.equal([...PAGE.matchAll(/<MetricTile/g)].length, 1);
	// 旧的 chips 渲染块已删除（注释中提及合并口径是允许的）
	assert.doesNotMatch(PAGE, /governanceChips/);
});
