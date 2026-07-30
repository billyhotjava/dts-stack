import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const panelUrl = new URL("./ModelingWorkspacePanels.tsx", import.meta.url);
const panel = existsSync(panelUrl) ? readFileSync(panelUrl, "utf8") : "";
const workbench = readFileSync(new URL("./ModelingWorkbenchPage.tsx", import.meta.url), "utf8");
const planDetail = readFileSync(new URL("./WarehousePlanDetailPage.tsx", import.meta.url), "utf8");
const elements = readFileSync(new URL("../governance/ElementsPage.tsx", import.meta.url), "utf8");

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
