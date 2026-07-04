import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");
const MAP_VIEW = readFileSync(new URL("./assets/AssetMapView.tsx", import.meta.url), "utf8");

test("asset toolbar keeps only three primary actions with diagnostics folded into a menu", () => {
	// 主操作：进入台账（主 CTA）、刷新、同步与诊断菜单
	assert.match(SOURCE, /asset-ops-menu/);
	assert.match(SOURCE, /同步与诊断/);
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
	assert.match(SOURCE, /asset-filters-toggle/);
	// 搜索框常驻，Select 组折叠
	assert.match(SOURCE, /搜索资产名称 \/ 描述/);
});

test("enter-ledger CTA is unique: duplicates removed from layer tabs row and map overview", () => {
	const pageMatches = SOURCE.match(/进入台账/g) || [];
	assert.equal(pageMatches.length, 1, `DatasetsPage 应只保留 1 处进入台账，实际 ${pageMatches.length}`);
	// AssetMapView 不再有进入台账按钮（矩阵格下钻仍在）
	assert.doesNotMatch(MAP_VIEW, /onEnterLedger/);
	assert.match(MAP_VIEW, /onDrillToLedger/);
});
