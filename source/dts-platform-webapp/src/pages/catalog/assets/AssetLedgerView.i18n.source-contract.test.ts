import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const LEDGER = readFileSync(new URL("./AssetLedgerView.tsx", import.meta.url), "utf8");
const PAGE = readFileSync(new URL("./AssetLedgerView.tsx", import.meta.url), "utf8");
const MAP = readFileSync(new URL("../AssetOverviewPage.tsx", import.meta.url), "utf8");

test("台账表格不再直显资产类型原值", () => {
	assert.doesNotMatch(LEDGER, /<Tag>\{row\.type\b/);
	assert.match(LEDGER, /resolveEnumLabel\(ASSET_TYPE_DICT/);
});

test("台账不再以治理状态原值兜底", () => {
	assert.doesNotMatch(LEDGER, /\|\|\s*row\.governanceStatus\b/);
	assert.match(LEDGER, /resolveEnumLabel\(GOVERNANCE_STATUS_DICT/);
});

test("CSV 导出的类型与状态列经过枚举字典", () => {
	assert.doesNotMatch(PAGE, /^\s*row\.type,\s*$/m);
	assert.match(PAGE, /resolveEnumLabel\(ASSET_TYPE_DICT/);
	assert.match(PAGE, /resolveEnumLabel\(GOVERNANCE_STATUS_DICT/);
});

test("地图页治理状态经字典解析而非原值兜底", () => {
	assert.match(MAP, /resolveEnumLabel\(GOVERNANCE_STATUS_DICT/);
	assert.doesNotMatch(MAP, /GOVERNANCE_STATUS_LABELS\[[^\]]+\]\s*\|\|/);
});
