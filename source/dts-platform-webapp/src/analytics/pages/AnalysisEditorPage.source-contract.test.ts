import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const ROUTES_SOURCE = readFileSync(
	new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url),
	"utf8",
);
const EDITOR_SOURCE = readFileSync(new URL("./AnalysisEditorPage.tsx", import.meta.url), "utf8");
const WORKSPACE_SOURCE = readFileSync(new URL("./analysis/AnalysisWorkspace.tsx", import.meta.url), "utf8");
const AUTHORING_SOURCE = `${EDITOR_SOURCE}\n${WORKSPACE_SOURCE}`;
const API_SOURCE = readFileSync(new URL("../api/analysisApi.ts", import.meta.url), "utf8");

test("canonical question create and edit routes use the governed analysis editor", () => {
	assert.match(ROUTES_SOURCE, /const AnalysisEditorPage = lazy/);
	assert.match(ROUTES_SOURCE, /path: "bi\/questions\/new"[\s\S]*?<AnalysisEditorPage/);
	assert.match(ROUTES_SOURCE, /path: "bi\/questions\/:id\/edit"[\s\S]*?<AnalysisEditorPage/);
});

test("legacy card and virtual dataset routes remain on the frozen semantic editor", () => {
	for (const route of ["bi/card/new", "bi/card/:id/edit", "bi/virtual-datasets/new", "bi/virtual-datasets/:id"]) {
		const escaped = route.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
		assert.match(ROUTES_SOURCE, new RegExp(`path: "${escaped}"[\\s\\S]*?<SemanticCardEditorPage`));
	}
});

test("analysis editor pins the published dataset contract and never exposes raw query languages", () => {
	for (const token of ["datasetId", "version", "checksum", "semanticContractVersion", "dts.analysis/v1"]) {
		assert.match(EDITOR_SOURCE, new RegExp(token));
	}
	assert.match(EDITOR_SOURCE, /getPublishedQueryDataset/);
	assert.doesNotMatch(EDITOR_SOURCE, /rawSql|rawSQL|sqlText|MBQL|dataset_query/);
});

test("analysis API and editor cover draft persistence, conflict recovery, and four-state UX", () => {
	assert.match(API_SOURCE, /\/api\/analysis/);
	assert.match(API_SOURCE, /Idempotency-Key/);
	assert.match(API_SOURCE, /createAnalysis/);
	assert.match(API_SOURCE, /updateAnalysis/);
	for (const state of ["正在加载分析契约", "暂无可分析字段", "分析加载失败", "分析草稿已保存", "契约已变化"]) {
		assert.match(AUTHORING_SOURCE, new RegExp(state));
	}
});

test("editor executes only through the governed query gateway and exposes bounded query states", () => {
	assert.match(API_SOURCE, /previewAnalysis/);
	assert.match(API_SOURCE, /\/api\/analysis\/preview/);
	assert.match(API_SOURCE, /cancelAnalysisQuery/);
	assert.match(AUTHORING_SOURCE, /执行查询/);
	assert.match(AUTHORING_SOURCE, /取消查询/);
	for (const state of ["排队中", "执行中", "执行成功", "结果已截断", "无权访问", "查询超时", "已取消"]) {
		assert.match(AUTHORING_SOURCE, new RegExp(state));
	}
	assert.doesNotMatch(AUTHORING_SOURCE, /native_form|sql_preview/);
});

test("analysis publication is a validated and versioned action separate from saving a draft", () => {
	for (const token of [
		"validateAnalysisPublication",
		"publishAnalysis",
		"listAnalysisVersions",
		"createAnalysisDraftFromVersion",
	]) {
		assert.match(API_SOURCE, new RegExp(token));
	}
	for (const token of ["保存草稿", "发布分析", "重新校验", "依赖快照", "版本历史", "基于此版本创建草稿"]) {
		assert.match(EDITOR_SOURCE, new RegExp(token));
	}
	for (const audienceField of ["deptCodes", "roleCodes", "classification", "expiresAt"]) {
		assert.match(EDITOR_SOURCE, new RegExp(audienceField));
	}
});

test("analysis publication audience comes from the platform directory and blockers are readable", () => {
	assert.match(EDITOR_SOURCE, /analyticsApi[\s\S]{0,40}\.listPlatformOrgs\(\)/);
	assert.match(EDITOR_SOURCE, /analyticsApi[\s\S]{0,40}\.listPlatformRoles\(\)/);
	assert.doesNotMatch(EDITOR_SOURCE, /mode="tags"[\s\S]{0,180}audience\.deptCodes/);
	assert.doesNotMatch(EDITOR_SOURCE, /mode="tags"[\s\S]{0,180}audience\.roleCodes/);
	assert.match(EDITOR_SOURCE, /analysisPublicationIssueMessage\(blocker/);
	assert.doesNotMatch(EDITOR_SOURCE, /message=\{blocker\.code\}/);
	assert.match(EDITOR_SOURCE, /publicationClassificationFloor\(contract\?\.dataset\.classification\)/);
});
