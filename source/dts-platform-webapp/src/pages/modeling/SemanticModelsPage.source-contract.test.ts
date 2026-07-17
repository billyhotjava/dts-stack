import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE = readFileSync(new URL("./SemanticModelsPage.tsx", import.meta.url), "utf8");

test("model ledger keeps an explicit advanced dbt SQL hand-off", () => {
	assert.match(PAGE, /data-testid="semantic-model-dbt-entry"/);
	assert.match(PAGE, /\/modeling\/dbt-files\?modelId=\$\{encodeURIComponent\(row\.id\)\}/);
	assert.match(PAGE, /高级 dbt SQL/);
});

test("model ledger keeps one visible row action and moves specialist actions into more", () => {
	assert.match(PAGE, /Dropdown/);
	assert.match(PAGE, /更多/);
	assert.match(PAGE, /查看模型/);
	assert.doesNotMatch(PAGE, />\s*生成制品\s*<\/Button>/);
	assert.doesNotMatch(PAGE, />\s*触发运行\s*<\/Button>/);
	assert.doesNotMatch(PAGE, />\s*提交审核\s*<\/Button>/);
});

test("model ledger removes the permanent specialist-entry card", () => {
	assert.doesNotMatch(PAGE, /模型台账 · 专业入口/);
	assert.doesNotMatch(PAGE, /semantic-model-ledger-links/);
});

test("model ledger exposes server-side release gate evidence", () => {
	assert.match(PAGE, /getModelSpecReleaseGate/);
	assert.match(PAGE, /releaseGates/);
	assert.match(PAGE, /发布门禁/);
});

test("model ledger presents all vNext layers and keeps STG foldable", () => {
	assert.match(PAGE, /ODS/);
	assert.match(PAGE, /STG/);
	assert.match(PAGE, /DWD/);
	assert.match(PAGE, /DWS/);
	assert.match(PAGE, /ADS/);
	assert.match(PAGE, /STG.*折叠|折叠.*STG/);
	assert.match(PAGE, /modeling-layer-filter/);
});
