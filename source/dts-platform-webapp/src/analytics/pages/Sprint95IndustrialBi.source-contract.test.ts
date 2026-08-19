import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const EDITOR = readFileSync(new URL("./AnalysisEditorPage.tsx", import.meta.url), "utf8");
const DASHBOARD = readFileSync(new URL("./DashboardEditorPage.tsx", import.meta.url), "utf8");
const DASHBOARD_CARD = readFileSync(new URL("./dashboard/DashboardEditorCard.tsx", import.meta.url), "utf8");
const API = readFileSync(new URL("../api/analysisApi.ts", import.meta.url), "utf8");

test("analysis editor exposes the complete governed authoring chain", () => {
	for (const token of [
		"AnalysisWorkspace",
		"autoPreview",
		"ChartRenderer",
		"派生指标",
		"字段货架",
		"导出 Excel",
		"exportAnalysis",
	]) {
		assert.match(`${EDITOR}\n${API}`, new RegExp(token));
	}
	assert.doesNotMatch(EDITOR, /\["table", "bar", "line", "area", "pie", "number", "scatter"\]/);
});

test("dashboard editor wires authored parameter mappings and targeted linkage", () => {
	assert.match(DASHBOARD_CARD, /ParameterMappingPopover/);
	assert.match(DASHBOARD_CARD, /InteractionSettingsPopover/);
	assert.match(DASHBOARD, /onParameterMappingsChange/);
	assert.match(DASHBOARD, /targetCardIds/);
	assert.match(DASHBOARD, /mapWithConcurrency/);
});

test("analysis client downloads CSV and XLSX from the canonical analysis route", () => {
	assert.match(API, /exportAnalysis/);
	assert.match(API, /query\/\$\{format\}/);
	assert.match(API, /content-disposition/i);
});
