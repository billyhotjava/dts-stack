import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const read = (path: string) => readFileSync(new URL(path, import.meta.url), "utf8");

const STATIC_ROUTES = read("../../routes/sections/dashboard/static-routes.tsx");
const DYNAMIC_RESOLVER = read("../../routes/sections/dashboard/dynamic-resolver.tsx");
const RULES_ROOT = read("../../pages/governance/QualityRulesPage.tsx");
const REPORT_ROOT = read("../../pages/governance/QualityReportPage.tsx");
const CATALOG_COMPATIBILITY = read("../../pages/catalog/QualityPage.tsx");
const WORKSPACE = read("./QualityWorkspace.tsx");
const MONITORS = read("./MonitorPages.tsx");
const REPORTS = read("./ReportPages.tsx");
const RULE_EDITOR = read("./RuleEditorPage.tsx");
const RULES = read("./RulesPages.tsx");
const RUN_ISSUES = read("./RunIssueDisposition.tsx");
const RUNS = read("./RunPages.tsx");
const OVERVIEW = read("./OverviewPage.tsx");
const WORKFLOW_CENTER = read("../../pages/workbench/WorkflowCenterPage.tsx");
const MENU_SEED = read("../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json");

test("formal menu roots own the two data-quality entry pages", () => {
	assert.match(DYNAMIC_RESOLVER, /"\/governance\/rules": "\/pages\/governance\/QualityRulesPage"/);
	assert.match(DYNAMIC_RESOLVER, /"\/governance\/quality": "\/pages\/governance\/QualityReportPage"/);
	assert.match(RULES_ROOT, /routeKey="overview"/);
	assert.match(REPORT_ROOT, /routeKey="report"/);
	assert.match(MENU_SEED, /"title": "质量管控"[\s\S]*?"externalLink": "\/governance\/rules"/);
	assert.match(MENU_SEED, /"title": "质量报告"[\s\S]*?"externalLink": "\/governance\/quality"/);
});

test("quality control does not recreate the retired report tab", () => {
	assert.doesNotMatch(WORKSPACE, /<Tabs\b/);
	assert.match(WORKSPACE, /qualityPrimaryRoutesForOwner\(current\?\.menuOwner/);
	assert.match(WORKSPACE, /showControlNavigation/);
	assert.doesNotMatch(RULES_ROOT, /QualityReportTab|tab=report|<Tabs\b/);
});

test("all prototype deep links resolve through the shared formal workspace", () => {
	const routeKeys = [
		"rule-list",
		"rule-editor",
		"rule-detail",
		"rule-template",
		"template-detail",
		"rule-by-table",
		"table-detail",
		"rule-by-template",
		"batch-wizard",
		"monitor",
		"monitor-editor",
		"monitor-detail",
		"run-records",
		"run-detail",
		"noise",
		"report-editor",
		"report-preview",
	];
	for (const routeKey of routeKeys) {
		assert.match(STATIC_ROUTES, new RegExp(`<QualityRoutePage routeKey="${routeKey}"`), routeKey);
	}
	// Three editors support both /new and /:id/edit compatibility variants.
	assert.equal((STATIC_ROUTES.match(/<QualityRoutePage routeKey=/g) || []).length, 20);
});

test("legacy links converge and old data-quality implementations are physically retired", () => {
	assert.match(CATALOG_COMPATIBILITY, /Navigate to=\{`\/governance\/quality/);
	assert.doesNotMatch(WORKFLOW_CENTER, /\/governance\/quality-rules/);
	assert.match(WORKFLOW_CENTER, /\/governance\/rules\/catalog/);

	for (const file of [
		"QualityReportTab.tsx",
		"QualityDashboard.tsx",
		"QualityTasksTab.tsx",
		"DataRepairTab.tsx",
		"RuleCreateWizard.tsx",
		"TemplatesTab.tsx",
		"CleansingTab.tsx",
		"QualityTasksPanel.tsx",
		"IssueWorkflowPanel.tsx",
		"ComplianceCenterPanel.tsx",
		"DataEditorTab.tsx",
	]) {
		assert.equal(
			existsSync(new URL(`../../pages/governance/components/${file}`, import.meta.url)),
			false,
			`${file} must stay retired`,
		);
	}
});

test("monitor rule selection is reset on dataset changes and exposes only executable rules for that dataset", () => {
	assert.match(MONITORS, /onChange=\{\(\) => form\.setFieldValue\("ruleId", undefined\)\}/);
	assert.match(MONITORS, /isExecutableQualityRule\(rule, selectedDataset\)/);
});

test("rule detail clears stale route state and gates version mutations on the loaded rule identity", () => {
	assert.match(RULES, /setRule\(undefined\);[\s\S]{0,120}setVersions\(\[\]\);[\s\S]{0,120}setHistory\(\[\]\)/);
	assert.match(RULES, /setLoadError\(""\)/);
	assert.match(RULES, /String\(rule\.id\) === ruleId/);
	assert.match(RULES, /loadedVersion\?\.version === version/);
});

test("issue creation stays closed unless a total-backed lookup completes and is repeated immediately before create", () => {
	assert.match(RUN_ISSUES, /collectCompletePages<RunIssue>/);
	assert.match(RUN_ISSUES, /creationBlockedReason/);
	assert.match(RUN_ISSUES, /const verification = await lookupRunIssue\(\)/);
	assert.match(RUN_ISSUES, /if \(!verification\.complete\)/);
});

test("unfinished or empty runs and a dashboard day without runs render explicit empty statistics", () => {
	assert.match(RUNS, /hasStatistics \? total\.toLocaleString\(\) : "暂无统计"/);
	assert.match(RUNS, /hasStatistics \? `\$\{passRate\}%` : "暂无统计"/);
	assert.match(OVERVIEW, /totalToday \? `\$\{passRate\}%` : "暂无运行"/);
});

test("run detail ignores stale responses after the route run id changes", () => {
	assert.match(RUNS, /const requestedRunId = runId/);
	assert.match(RUNS, /sequence !== loadSequence\.current \|\| activeRunId\.current !== requestedRunId/);
	assert.match(RUNS, /String\(detail\.id\) !== requestedRunId/);
});

test("rule editor isolates route loads and saves only the loaded rule identity and version", () => {
	assert.match(RULE_EDITOR, /const requestedRuleId = ruleId/);
	assert.match(RULE_EDITOR, /sequence !== loadSequence\.current \|\| activeRuleId\.current !== requestedRuleId/);
	assert.match(RULE_EDITOR, /String\(operationRule\.id\) === operationRuleId/);
	assert.match(RULE_EDITOR, /loadedIdentity\.version === Number\(operationRule\.latestVersion\?\.version\)/);
	assert.match(RULE_EDITOR, /loadedIdentity\.versionId === String\(operationRule\.latestVersion\?\.id \|\| ""\)/);
	assert.match(RULE_EDITOR, /if \(operationRuleId && !isLoadedRuleCurrent\(operationRuleId, operationRule\)\)/);
});

test("rule editor isolates template route loads and saves only the current loaded template snapshot", () => {
	assert.match(RULE_EDITOR, /const requestedTemplateId = linkedTemplateId/);
	assert.match(RULE_EDITOR, /setSelectedTemplate\(undefined\);[\s\S]{0,100}setPreviewSql\(""\)/);
	assert.match(
		RULE_EDITOR,
		/sequence !== templateLoadSequence\.current \|\|[\s\S]{0,80}activeTemplateId\.current !== requestedTemplateId/,
	);
	assert.match(RULE_EDITOR, /loadedTemplateIdentity\.current = \{ templateId: requestedTemplateId, sequence \}/);
	assert.match(RULE_EDITOR, /loadedIdentity\.sequence === templateLoadSequence\.current/);
	assert.match(RULE_EDITOR, /const operationTemplateId = linkedTemplateId/);
	assert.match(RULE_EDITOR, /if \(!isTemplateSelectionCurrent\(operationTemplateId, operationTemplate\)\)/);
	assert.match(
		RULE_EDITOR,
		/await renderTemplate\(operationTemplate, values\.templateParams \|\| "\{\}", values\.datasetId\)/,
	);
});

test("monitor editor isolates route loads and saves only the currently loaded task", () => {
	assert.match(MONITORS, /const requestedTaskId = taskId/);
	assert.match(MONITORS, /sequence !== loadSequence\.current \|\| activeTaskId\.current !== requestedTaskId/);
	assert.match(MONITORS, /activeTaskId\.current === operationTaskId && loadedTaskId\.current === operationTaskId/);
	assert.match(MONITORS, /if \(operationTaskId && !isLoadedTaskCurrent\(operationTaskId\)\)/);
});

test("quality report commits score and query only for the current dataset and period request", () => {
	assert.match(REPORTS, /const requestedDatasetId = datasetId/);
	assert.match(REPORTS, /const requestedPeriodDays = periodDays/);
	assert.match(REPORTS, /const sequence = \+\+loadSequence\.current/);
	assert.match(
		REPORTS,
		/activeDatasetId\.current === requestedDatasetId &&[\s\S]{0,120}activePeriodDays\.current === requestedPeriodDays/,
	);
	assert.match(
		REPORTS,
		/await getQualityScore\(requestedDatasetId, requestedPeriodDays\);[\s\S]{0,180}if \(!isCurrentRequest\(\)\) return;[\s\S]{0,180}setScore\(nextScore\);[\s\S]{0,240}setSearchParams\(next/,
	);
});

test("unsafe SQL repair and cleansing writes stay explicit and fail closed", () => {
	assert.match(RUNS, /SQL 修复与数据清洗暂未开放/);
	assert.match(RUNS, /<Button disabled>执行 SQL 修复（暂未开放）<\/Button>/);
	assert.match(RUNS, /<Button disabled>执行数据清洗（暂未开放）<\/Button>/);
	assert.doesNotMatch(RUNS, /previewSqlRepair|executeSqlRepair|previewCleansing|executeCleansing/);
});
