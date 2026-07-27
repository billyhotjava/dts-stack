import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const RULES_PAGE = readFileSync(new URL("./QualityRulesPage.tsx", import.meta.url), "utf8");
const REPORT_PAGE = readFileSync(new URL("./QualityReportPage.tsx", import.meta.url), "utf8");
const REPORT_TAB = readFileSync(new URL("./components/QualityReportTab.tsx", import.meta.url), "utf8");
const CREATE_WIZARD = readFileSync(new URL("./components/RuleCreateWizard.tsx", import.meta.url), "utf8");
const PLATFORM_API = readFileSync(new URL("../../api/platformApi.ts", import.meta.url), "utf8");

test("F5-T02 quality rule execution opens the standalone report page with dataset context", () => {
	assert.match(RULES_PAGE, /triggerRun/);
	assert.match(RULES_PAGE, /triggerQualityRun\(\{ ruleId: rule\.id \}\)/);
	assert.match(RULES_PAGE, /params\.set\("datasetId", String\(rule\.datasetId\)\)/);
	assert.match(RULES_PAGE, /navigate\(`\/governance\/quality\?\$\{params\.toString\(\)\}`\)/);
	assert.match(RULES_PAGE, /检测任务已提交，最终结果请在质量报告查看/);
});

test("F5-T03 quality report can be deep-linked by datasetId", () => {
	assert.match(REPORT_TAB, /useSearchParams/);
	assert.match(REPORT_TAB, /searchParams\.get\("datasetId"\)/);
	assert.match(REPORT_TAB, /handleDatasetChange/);
	assert.match(REPORT_TAB, /params\.set\("datasetId", nextDatasetId\)/);
	assert.match(REPORT_TAB, /onChange=\{handleDatasetChange\}/);
	assert.match(REPORT_TAB, /params\.delete\("tab"\)/);
	assert.doesNotMatch(REPORT_TAB, /params\.set\("tab", "report"\)/);
});

test("quality report is owned by the menu page and is absent from quality-control tabs", () => {
	assert.match(REPORT_PAGE, /<PageHeader title="数据治理中心 · 质量报告"/);
	assert.match(REPORT_PAGE, /<QualityReportTab/);
	assert.doesNotMatch(REPORT_PAGE, /<Navigate/);
	assert.doesNotMatch(RULES_PAGE, /label: "质量报告"/);
	assert.doesNotMatch(RULES_PAGE, /<QualityReportTab/);
});

test("legacy report-tab deep links redirect to the standalone report page", () => {
	assert.match(RULES_PAGE, /if \(activeTab === "report"\)/);
	assert.match(RULES_PAGE, /reportParams\.delete\("tab"\)/);
	assert.match(RULES_PAGE, /<Navigate to=\{`\/governance\/quality/);
	assert.match(RULES_PAGE, /replace/);
});

test("quality report distinguishes no effective runs from a real zero score", () => {
	assert.match(REPORT_TAB, /hasEffectiveRuns/);
	assert.match(REPORT_TAB, /暂无有效检测结果/);
});

test("template-created rules persist the rendered SQL as an executable definition", () => {
	assert.match(CREATE_WIZARD, /definition = \{ sql: renderedSql \}/);
	assert.match(RULES_PAGE, /definition: resolvedDefinition/);
});

test("skipped legacy runs are displayed as skipped instead of failed", () => {
	assert.match(REPORT_TAB, /v === "SKIPPED"/);
	assert.match(REPORT_TAB, />跳过</);
	assert.match(PLATFORM_API, /passRate: number \\| null/);
});
