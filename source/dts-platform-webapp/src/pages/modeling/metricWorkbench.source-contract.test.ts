import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const WORKBENCH = readFileSync(
	new URL("./MetricWorkbenchPage.tsx", import.meta.url),
	"utf8",
);
const CANVAS = readFileSync(
	new URL("./metric-workbench/MetricCanvas.tsx", import.meta.url),
	"utf8",
);
const DETAIL_PANEL = readFileSync(
	new URL("./metric-workbench/MetricDetailPanel.tsx", import.meta.url),
	"utf8",
);
const SUBJECT_BROWSER = readFileSync(
	new URL("./metric-workbench/SubjectBrowserPanel.tsx", import.meta.url),
	"utf8",
);
const SUBJECTS = readFileSync(
	new URL("./SemanticSubjectsPage.tsx", import.meta.url),
	"utf8",
);
const OBJECTS = readFileSync(
	new URL("./SemanticObjectsPage.tsx", import.meta.url),
	"utf8",
);
const METRICS_PAGE = readFileSync(
	new URL("./SemanticMetricsPage.tsx", import.meta.url),
	"utf8",
);
const MODELS_PAGE = readFileSync(
	new URL("./SemanticModelsPage.tsx", import.meta.url),
	"utf8",
);
const PUBLISH = readFileSync(
	new URL("./SemanticPublishPage.tsx", import.meta.url),
	"utf8",
);
const RUNS = readFileSync(
	new URL("./SemanticRunsPage.tsx", import.meta.url),
	"utf8",
);
const LEGACY_CENTER = readFileSync(
	new URL("./SemanticModelingCenterPage.tsx", import.meta.url),
	"utf8",
);
const WORKSPACE_FRAME = readFileSync(
	new URL("./semantic-workspace/SemanticWorkspaceFrame.tsx", import.meta.url),
	"utf8",
);

test("MetricWorkbenchPage uses React Flow canvas and semantic API", () => {
	assert.match(WORKBENCH, /listSemanticSubjectDomains/);
	assert.match(WORKBENCH, /listSemanticBusinessObjects/);
	assert.match(WORKBENCH, /listSemanticMetrics/);
	assert.match(WORKBENCH, /listSemanticModels/);
	assert.match(WORKBENCH, /SemanticWorkspaceFrame/);
	assert.match(WORKBENCH, /metric-workbench-page/);
	assert.match(WORKBENCH, /xl:grid-cols-\[300px_minmax\(0,1fr\)\]/);
	assert.match(WORKBENCH, /metric-workbench-main/);
	assert.match(WORKBENCH, /metric-detail-dock/);
	assert.doesNotMatch(WORKBENCH, /window\.location\.replace/);
	assert.doesNotMatch(WORKBENCH, /oklch|:has\(|@container/);
	assert.match(SUBJECT_BROWSER, /\/governance\/subjects/);
	assert.match(SUBJECT_BROWSER, /未归属治理主题域/);
	assert.match(SUBJECT_BROWSER, /未绑定业务对象指标/);
	assert.match(SUBJECT_BROWSER, /serializeMetricDragPayload/);
	assert.doesNotMatch(SUBJECT_BROWSER, /\/modeling\/semantic\/subjects/);
});

test("MetricCanvas uses @xyflow/react with custom nodes and edges", () => {
	assert.match(CANVAS, /ReactFlow/);
	assert.match(CANVAS, /BizObjectNode/);
	assert.match(CANVAS, /MetricNode/);
	assert.match(CANVAS, /MetricBindingEdge/);
	assert.match(CANVAS, /nodesDraggable/);
	assert.match(CANVAS, /onNodeDragStop/);
	assert.match(CANVAS, /拖指标节点到业务对象节点上完成绑定/);
	assert.match(CANVAS, /业务对象页选择治理主题域/);
	assert.doesNotMatch(CANVAS, /主题域页创建业务对象/);
	assert.doesNotMatch(CANVAS, /oklch|:has\(|@container/);
});

test("semantic workspace frame shows step progress and next action", () => {
	assert.match(WORKSPACE_FRAME, /semantic-workspace-flow/);
	assert.match(WORKSPACE_FRAME, /aria-label="指标建模导航"/);
	assert.match(WORKSPACE_FRAME, /\/governance\/subjects/);
	assert.doesNotMatch(WORKSPACE_FRAME, /\/modeling\/semantic\/subjects/);
	assert.match(WORKSPACE_FRAME, /\/ops\/instances\?entryKey=DBT_RUN/);
	assert.doesNotMatch(WORKSPACE_FRAME, /事实源|下一步|完成发布闭环/);
	assert.doesNotMatch(WORKSPACE_FRAME, /oklch|:has\(|@container/);
});

test("Semantic modeling pages are real implementations except retired compatibility routes", () => {
	for (const [name, src] of [
		["SemanticObjectsPage", OBJECTS],
		["SemanticMetricsPage", METRICS_PAGE],
		["SemanticModelsPage", MODELS_PAGE],
		["SemanticPublishPage", PUBLISH],
	]) {
		assert.doesNotMatch(src, /window\.location\.replace/, `${name} should not be a redirect shell`);
		assert.doesNotMatch(src, /SemanticModelingCenterPage/, `${name} should not import SemanticModelingCenterPage`);
		assert.match(src, /SemanticWorkspaceFrame/, `${name} should use the unified metric modeling workspace`);
	}
});

test("semantic subjects route redirects to governance subject areas", () => {
	assert.match(SUBJECTS, /Navigate/);
	assert.match(SUBJECTS, /\/governance\/subjects/);
	assert.doesNotMatch(SUBJECTS, /createSemanticSubjectDomain|updateSemanticSubjectDomain|listSemanticSubjectDomains/);
	assert.match(LEGACY_CENTER, /subjects: "\/governance\/subjects"/);
	assert.doesNotMatch(LEGACY_CENTER, /\/metrics\/semantic\/subjects/);
});

test("publish page calls dbt + BI registration + lineage in sequence", () => {
	assert.match(PUBLISH, /publishSemanticModelToDbt/);
	assert.match(PUBLISH, /registerSemanticBiDataset/);
	assert.match(PUBLISH, /registerSemanticLineage/);
	assert.match(PUBLISH, /semantic-publish-page/);
	assert.match(PUBLISH, /await publishSemanticModelToDbt/);
	assert.match(PUBLISH, /await registerSemanticBiDataset/);
	assert.match(PUBLISH, /await registerSemanticLineage/);
});

test("semantic objects page closes table mapping edit/save loop", () => {
	assert.match(OBJECTS, /listSemanticSubjectDomains/);
	assert.match(OBJECTS, /name="domainId"/);
	assert.match(OBJECTS, /治理主题域/);
	assert.match(OBJECTS, /saveSemanticObjectTableMappings/);
	assert.match(OBJECTS, /semantic-object-mappings-drawer/);
	assert.match(OBJECTS, /Form\.List/);
	assert.match(OBJECTS, /mappingLoading/);
	assert.match(OBJECTS, /暂无表映射/);
	assert.match(OBJECTS, /VisualFlowCanvas/);
});

test("publish flow does not claim full success when BI or lineage registration fails", () => {
	for (const [name, src] of [
		["SemanticPublishPage", PUBLISH],
		["MetricDetailPanel", DETAIL_PANEL],
	]) {
		assert.doesNotMatch(src, /Promise\.allSettled/, `${name} must check every publish step`);
		assert.match(src, /dbt 已发布，BI 数据集注册失败/, `${name} must surface BI registration failure`);
		assert.match(src, /dbt 与 BI 数据集已完成，血缘注册失败/, `${name} must surface lineage registration failure`);
		assert.match(src, /toast\.error/, `${name} must not show success-only publish state`);
		assert.match(src, /Alert/, `${name} must render customer-visible partial failure state`);
	}
});

test("semantic runs route is retired into task operations center", () => {
	assert.match(RUNS, /Navigate/);
	assert.match(RUNS, /\/ops\/instances\?entryKey=DBT_RUN/);
	assert.doesNotMatch(RUNS, /POLL_INTERVAL_MS|setInterval|listSemanticModelRuns/);
	assert.match(DETAIL_PANEL, /任务运维中心/);
	assert.match(DETAIL_PANEL, /\/ops\/instances\?entryKey=DBT_RUN/);
});
