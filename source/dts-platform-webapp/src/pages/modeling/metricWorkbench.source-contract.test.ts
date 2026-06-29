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

test("MetricWorkbenchPage uses React Flow canvas and semantic API", () => {
	assert.match(WORKBENCH, /listSemanticSubjectDomains/);
	assert.match(WORKBENCH, /listSemanticBusinessObjects/);
	assert.match(WORKBENCH, /listSemanticMetrics/);
	assert.match(WORKBENCH, /metric-workbench-page/);
	assert.doesNotMatch(WORKBENCH, /window\.location\.replace/);
	assert.doesNotMatch(WORKBENCH, /oklch|:has\(|@container/);
});

test("MetricCanvas uses @xyflow/react with custom nodes and edges", () => {
	assert.match(CANVAS, /ReactFlow/);
	assert.match(CANVAS, /BizObjectNode/);
	assert.match(CANVAS, /MetricNode/);
	assert.match(CANVAS, /MetricBindingEdge/);
	assert.doesNotMatch(CANVAS, /oklch|:has\(|@container/);
});

test("all SemanticXxxPages are real implementations (no redirect shells)", () => {
	for (const [name, src] of [
		["SemanticSubjectsPage", SUBJECTS],
		["SemanticObjectsPage", OBJECTS],
		["SemanticMetricsPage", METRICS_PAGE],
		["SemanticModelsPage", MODELS_PAGE],
		["SemanticPublishPage", PUBLISH],
		["SemanticRunsPage", RUNS],
	]) {
		assert.doesNotMatch(src, /window\.location\.replace/, `${name} should not be a redirect shell`);
		assert.doesNotMatch(src, /SemanticModelingCenterPage/, `${name} should not import SemanticModelingCenterPage`);
	}
});

test("publish page calls dbt + BI registration + lineage in sequence", () => {
	assert.match(PUBLISH, /publishSemanticModelToDbt/);
	assert.match(PUBLISH, /registerSemanticBiDataset/);
	assert.match(PUBLISH, /registerSemanticLineage/);
	assert.match(PUBLISH, /semantic-publish-page/);
});

test("runs page polls RUNNING status", () => {
	assert.match(RUNS, /POLL_INTERVAL_MS/);
	assert.match(RUNS, /setInterval/);
	assert.match(RUNS, /clearInterval/);
	assert.match(RUNS, /semantic-runs-page/);
});
