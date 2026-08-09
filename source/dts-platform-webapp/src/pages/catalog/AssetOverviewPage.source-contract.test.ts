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
const DONUT = readFileSync(new URL("./assets/AssetGovernanceDonut.tsx", import.meta.url), "utf8");
const BARS = readFileSync(new URL("./assets/AssetDomainBars.tsx", import.meta.url), "utf8");

test("overview is a zero-input statistics dashboard with a single search exit", () => {
	assert.match(PAGE, /getCatalogAssetsOverview/);
	assert.match(PAGE, /getDomainTree\(\{ withStats: true \}\)/);
	assert.match(PAGE, /DomainScopeNav/);
	assert.match(PAGE, /testId="kpi-total"/);
	assert.match(PAGE, /data-testid="kpi-attention"/);
	assert.match(PAGE, /data-testid="kpi-unclassified"/);
	assert.match(PAGE, /testId="kpi-tag-coverage"/);
	assert.match(PAGE, /AssetGovernanceDonut/);
	assert.match(PAGE, /AssetDomainBars/);
	assert.match(PAGE, /\/catalog\/search/);
	assert.match(PAGE, /truncated/);
	assert.match(PAGE, /searchParams\.get\("domain"\)/);
	assert.match(PAGE, /searchParams\.get\("tab"\) === "catalog-tags"/);
	assert.match(PAGE, /router\.replace/);

	// 零输入控件
	assert.doesNotMatch(PAGE, /<Select/);
	assert.doesNotMatch(PAGE, /<Pagination/);
	assert.doesNotMatch(PAGE, /SearchOutlined/);
	assert.doesNotMatch(PAGE, /<Tree/);
});

test("the layer matrix and gap panel are removed", () => {
	assert.doesNotMatch(PAGE, /分层×主题域矩阵/);
	assert.doesNotMatch(PAGE, /asset-overview-matrix/);
	assert.doesNotMatch(PAGE, /matrixHeatTone/);
	assert.doesNotMatch(PAGE, /MERGED_DOMAIN_KEY/);
	assert.doesNotMatch(PAGE, /GovernanceGapPanel/);
	assert.doesNotMatch(PAGE, /listCatalogAssetsV2/);
	assert.doesNotMatch(PAGE, /resolveAssetReadiness/);
	assert.doesNotMatch(PAGE, /待处置 Top/);
});

test("overview keeps exactly one explicit exit: the data query page", () => {
	assert.doesNotMatch(PAGE, /\/catalog\/lineage\/graph/);
	assert.doesNotMatch(PAGE, /dataset-access-approval/);
	// 旧深链重定向（router.replace）保留；push 出口不得指向旧台账路由
	assert.doesNotMatch(PAGE, /router\.push\([^)]*ledger/);
	const pushes = [...PAGE.matchAll(/router\.push\((`|["'])([^`"']+)/g)].map((m) => m[2]);
	const literalPaths = new Set(pushes.filter((value) => value.includes("/catalog/") || value.includes("/security/")));
	assert.equal(literalPaths.size, 1, `必须只跳数据查询，实际: ${[...literalPaths].join(", ")}`);
	assert.ok([...literalPaths][0]?.includes("/catalog/search"), "唯一出口必须是 /catalog/search");
});

test("overview chrome stays minimal", () => {
	assert.ok((PAGE.match(/<Button\b/g) || []).length <= 2, "1 主 CTA + 1 刷新 icon");
	assert.equal((PAGE.match(/<MetricTile/g) || []).length, 4, "四张 KPI 卡");
});

test("overview page stays within the repository file-size convention", () => {
	assert.ok(PAGE.split(/\r?\n/).length - 1 <= 300, "AssetOverviewPage.tsx 不得超过 300 行（目标 260）");
});

test("donut and bars expose sr-only summaries and clickable slices", () => {
	assert.match(DONUT, /sr-only/);
	assert.match(DONUT, /onSliceClick/);
	assert.match(DONUT, /onEvents/);
	assert.match(BARS, /sr-only/);
	assert.match(BARS, /onBarClick/);
	assert.match(BARS, /onEvents/);
});
