import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const EDITOR = readFileSync(new URL("./AnalysisEditorPage.tsx", import.meta.url), "utf8");
const WORKSPACE = readFileSync(new URL("./analysis/AnalysisWorkspace.tsx", import.meta.url), "utf8");
const DASHBOARD = readFileSync(new URL("./DashboardEditorPage.tsx", import.meta.url), "utf8");
const DASHBOARD_CARD = readFileSync(new URL("./dashboard/DashboardEditorCard.tsx", import.meta.url), "utf8");
const DASHBOARD_QUERIES = readFileSync(new URL("./dashboard/useDashboardCardQueries.ts", import.meta.url), "utf8");
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
		assert.match(`${EDITOR}\n${WORKSPACE}\n${API}`, new RegExp(token));
	}
	assert.doesNotMatch(EDITOR, /\["table", "bar", "line", "area", "pie", "number", "scatter"\]/);
});

test("analysis editor keeps every supported chart type visible as an icon matrix", () => {
	for (const token of [
		"图表类型",
		"analysis-visualization-grid",
		"aria-pressed",
		"TableOutlined",
		"BarChartOutlined",
		"LineChartOutlined",
		"AreaChartOutlined",
		"PieChartOutlined",
		"FieldNumberOutlined",
	]) {
		assert.match(WORKSPACE, new RegExp(token));
	}
	assert.doesNotMatch(WORKSPACE, /<Select[\s\S]{0,240}value=\{spec\.visualization\.type\}/);
});

test("dashboard editor wires authored parameter mappings and targeted linkage", () => {
	assert.match(DASHBOARD_CARD, /ParameterMappingPopover/);
	assert.match(DASHBOARD_CARD, /InteractionSettingsPopover/);
	assert.match(DASHBOARD, /onParameterMappingsChange/);
	assert.match(DASHBOARD, /targetCardIds/);
	assert.match(`${DASHBOARD}\n${DASHBOARD_QUERIES}`, /mapWithConcurrency/);
});

test("analysis client downloads CSV and XLSX from the canonical analysis route", () => {
	assert.match(API, /exportAnalysis/);
	assert.match(API, /query\/\$\{format\}/);
	assert.match(API, /content-disposition/i);
});
