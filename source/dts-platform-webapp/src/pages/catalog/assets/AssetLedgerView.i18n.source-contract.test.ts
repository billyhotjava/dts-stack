import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const LEDGER = readFileSync(new URL("./AssetLedgerView.tsx", import.meta.url), "utf8");
const GOVERNANCE_DONUT = readFileSync(new URL("./AssetGovernanceDonut.tsx", import.meta.url), "utf8");

test("目录表格不再直显数据源类型原值", () => {
	assert.doesNotMatch(LEDGER, /<Tag>\{row\.type\b/);
	assert.match(LEDGER, /resolveEnumLabel\(DATA_SOURCE_TYPE_DICT/);
});

test("目录表格不再承担治理状态明细", () => {
	assert.doesNotMatch(LEDGER, /\|\|\s*row\.governanceStatus\b/);
	assert.doesNotMatch(LEDGER, /title: "治理状态"|GOVERNANCE_STATUS_DICT/);
});

test("资产概览治理状态经字典解析而非原值兜底", () => {
	assert.match(GOVERNANCE_DONUT, /resolveEnumLabel\(GOVERNANCE_STATUS_DICT/);
	assert.doesNotMatch(GOVERNANCE_DONUT, /GOVERNANCE_STATUS_LABELS\[[^\]]+\]\s*\|\|/);
});
