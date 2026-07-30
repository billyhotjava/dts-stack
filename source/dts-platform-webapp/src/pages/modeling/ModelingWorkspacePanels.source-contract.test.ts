import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const panelUrl = new URL("./ModelingWorkspacePanels.tsx", import.meta.url);
const panel = existsSync(panelUrl) ? readFileSync(panelUrl, "utf8") : "";
const workbench = readFileSync(new URL("./ModelingWorkbenchPage.tsx", import.meta.url), "utf8");
const planDetail = readFileSync(new URL("./WarehousePlanDetailPage.tsx", import.meta.url), "utf8");
const elements = readFileSync(new URL("../governance/ElementsPage.tsx", import.meta.url), "utf8");
const relationshipGraph = readFileSync(new URL("./RelationshipGraphPanel.tsx", import.meta.url), "utf8");

test("workspace panels lazy-compose canonical owners instead of copying their APIs", () => {
	assert.equal(existsSync(panelUrl), true);
	for (const owner of [
		"WarehousePlanDetailPage",
		"ElementsPage",
		"ReferenceCodesPage",
		"GlossaryPage",
		"MeasurementUnitsPage",
		"DimensionCatalogPage",
		"ModelCenterPage",
		"MetricWorkbenchPage",
	]) {
		assert.match(panel, new RegExp(`lazy\\([\\s\\S]*${owner}`));
	}
	assert.doesNotMatch(panel, /from ["']@\/api\//);
	assert.doesNotMatch(panel, /listWarehousePlans|listModelSpecs|listIndicators|listMetadataStandards/);
});

test("workbench renders active canonical panels with URL-owned subview state", () => {
	assert.match(workbench, /ModelingWorkspacePanels/);
	assert.match(workbench, /workspaceView=\{workspaceRoute\.workspaceView\}/);
	assert.match(workbench, /workspaceView:\s*view/);
	assert.doesNotMatch(workbench, /ModelingWorkspaceModuleLanding/);
});

test("canonical planning and data-element pages expose compact embedded adapters while retaining deep links", () => {
	assert.match(planDetail, /embedded\??:\s*boolean/);
	assert.match(planDetail, /planIdOverride\??:\s*string/);
	assert.match(planDetail, /baselineTabOverride/);
	assert.match(planDetail, /onWorkspaceViewChange/);
	assert.match(elements, /embedded\??:\s*boolean/);
	assert.match(elements, /embedded\s*\?\s*/);
});

test("panel navigation is accessible and does not manufacture new business owners", () => {
	assert.match(panel, /aria-label="当前模块功能"/);
	assert.match(panel, /aria-current/);
	assert.match(panel, /data-testid="modeling-workspace-canonical-panel"/);
	assert.doesNotMatch(panel, /localStorage|sessionStorage|fetch\(/);
});

test("model import tool delegates to the workbench context-preserving import action", () => {
	assert.match(panel, /onOpenModelImport/);
	assert.match(panel, /onClick:\s*onOpenModelImport/);
	assert.match(workbench, /onOpenModelImport=\{\(\) => setImportRoute\(true\)\}/);
	assert.doesNotMatch(panel, /\/modeling\/workbench\?modelImport=open/);
});

test("metrics is embedded under workspace-owned views and graph uses the plan projection", () => {
	assert.match(panel, /MetricWorkbenchPage[\s\S]*embedded/);
	assert.match(panel, /metricViewFromWorkspace/);
	assert.match(panel, /workspaceViewFromMetric/);
	assert.match(panel, /selectedDimensionIdOverride=\{assetKind === "dimension" \? assetId : undefined\}/);
	assert.match(panel, /indicatorIdOverride=\{assetKind === "indicator" \? assetId : undefined\}/);
	assert.match(panel, /onSelectedIndicatorChange=\{onOpenIndicator\}/);
	assert.match(panel, /RelationshipGraphPanel/);
	assert.doesNotMatch(panel, /LineageGraphPage/);
	assert.match(relationshipGraph, /getWarehousePlanRelationshipGraph/);
	assert.match(relationshipGraph, /kind:\s*kind \|\| undefined/);
	assert.match(relationshipGraph, /query:\s*query \|\| undefined/);
	assert.match(relationshipGraph, /nextHint/);
	assert.match(relationshipGraph, /请选择节点类型/);
	assert.match(relationshipGraph, /currentRequest !== requestSequence\.current/);
	assert.match(relationshipGraph, /setLoading\(true\);\s*setError\(""\);\s*setGraph\(null\)/);
	assert.match(relationshipGraph, /routeByIdRef\.current/);
	assert.match(relationshipGraph, /onNavigateRef\.current/);
	assert.match(relationshipGraph, /onNodeClick=\{handleNodeClick\}/);
	assert.match(relationshipGraph, /<LineageGraph/);
	assert.doesNotMatch(relationshipGraph, /listDatasets|fetch\(/);
});
