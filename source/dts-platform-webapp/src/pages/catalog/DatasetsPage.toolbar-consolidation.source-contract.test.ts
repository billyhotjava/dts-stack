import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");
const OVERVIEW = readFileSync(new URL("./AssetOverviewPage.tsx", import.meta.url), "utf8");
const TOOLBAR = readFileSync(new URL("./assets/AssetLedgerToolbar.tsx", import.meta.url), "utf8");

test("asset toolbar keeps only three primary actions with diagnostics folded into a menu", () => {
	// 主操作：返回地图、刷新、同步与诊断菜单（台账=执行工作台）
	assert.match(TOOLBAR, /asset-ops-menu/);
	assert.match(TOOLBAR, /同步与诊断/);
	// 四个诊断动作以菜单项存在，且每项带说明文案
	assert.match(SOURCE, /同步 OpenMetadata/);
	assert.match(SOURCE, /从元数据平台拉取最新资产清单/);
	assert.match(SOURCE, /映射诊断/);
	assert.match(SOURCE, /统计已映射\/未匹配\/待人工确认/);
	assert.match(SOURCE, /解析失败记录/);
	assert.match(SOURCE, /发布前核对/);
	// 不再是顶层平铺按钮（原文案不允许以独立 Button 文案出现）
	assert.doesNotMatch(SOURCE, />\s*同步OpenMetadata\s*</);
	assert.doesNotMatch(SOURCE, />\s*刷新核对\s*</);
	assert.doesNotMatch(SOURCE, />\s*刷新资产\s*</);
});

test("asset filters are collapsed behind a toggle with an active-filter badge", () => {
	assert.match(SOURCE, /filtersOpen/);
	assert.match(SOURCE, /activeFilterCount/);
	assert.match(TOOLBAR, /asset-filters-toggle/);
	// 搜索框常驻，Select 组折叠
	assert.match(TOOLBAR, /搜索资产名称 \/ 描述/);
});

test("enter-ledger CTA lives on the overview page only", () => {
	assert.doesNotMatch(SOURCE, /进入台账/);
	assert.match(OVERVIEW, /进入台账/);
});
