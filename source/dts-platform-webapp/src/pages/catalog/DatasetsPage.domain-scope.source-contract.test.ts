import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const LEDGER = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");
const MAP = readFileSync(new URL("./AssetOverviewPage.tsx", import.meta.url), "utf8");
const SHARED = readFileSync(new URL("./assets/assetPageShared.tsx", import.meta.url), "utf8");

test("台账与地图共用同一范围导航组件，不再各自建 antd Tree", () => {
	assert.match(LEDGER, /DomainScopeNav/);
	assert.match(MAP, /DomainScopeNav/);
	assert.doesNotMatch(LEDGER, /<Tree\b/);
	assert.doesNotMatch(MAP, /<Tree\b/);
});

test("台账范围选择进入 URL 且不覆盖其他筛选参数", () => {
	assert.match(LEDGER, /searchParams\.get\("domain"\)/);
	// 必须基于当前 searchParams 复制后再改，台账还有标签等参数
	assert.match(LEDGER, /new URLSearchParams\(searchParams\)/);
	assert.doesNotMatch(LEDGER, /window\.location\.search/);
});

test("fallback- 静默降级已从共享层与两个页面移除", () => {
	assert.doesNotMatch(SHARED, /fallback-/);
	assert.doesNotMatch(LEDGER, /fallback-/);
	assert.doesNotMatch(MAP, /fallback-/);
	assert.doesNotMatch(SHARED, /buildTreeNodes/);
});

test("缺少标识的主题域保留 null 而非编造 key", () => {
	assert.match(SHARED, /const id = node\.id \? String\(node\.id\) : null;/);
});
