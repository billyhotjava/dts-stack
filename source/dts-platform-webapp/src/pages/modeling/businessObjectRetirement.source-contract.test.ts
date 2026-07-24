import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const read = (path: string) => readFileSync(new URL(path, import.meta.url), "utf8");

test("retired creation pages, clients and canvas consumers are physically absent", () => {
	for (const path of [
		"../../api/semanticModelingApi.ts",
		"../../api/modelingApi.ts",
		"./SemanticObjectsPage.tsx",
		"./SemanticMetricsPage.tsx",
		"./SemanticModelsPage.tsx",
		"./SemanticPublishPage.tsx",
		"./SemanticModelingCenterPage.tsx",
		"./LowCodeDevelopmentPage.tsx",
		"./metric-workbench/MetricCanvas.tsx",
		"./metric-workbench/SubjectBrowserPanel.tsx",
	]) {
		assert.equal(existsSync(new URL(path, import.meta.url)), false, `${path} must stay retired`);
	}
});

test("canonical customer pages use model and metric references only", () => {
	const sources = [
		"./MetricWorkbenchPage.tsx",
		"./DimensionCatalogPage.tsx",
		"./ModelCenterPage.tsx",
		"./ModelSpecDetailPage.tsx",
		"../../api/modelSpecApi.ts",
		"../../api/dimensionDefinitionApi.ts",
	].map(read).join("\n");

	assert.match(sources, /listDimensionDefinitions/);
	assert.match(sources, /listModelSpecs/);
	assert.match(sources, /metricRefs/);
	assert.doesNotMatch(sources, /businessObjectId|listSemanticBusinessObjects|\/business-objects/);
});

test("old deep links remain isolated in one read-only compatibility page", () => {
	const routes = read("../../routes/sections/dashboard/static-routes.tsx");
	const compatibility = read("./ModelingCompatibilityPage.tsx");
	assert.match(routes, /path: "modeling\/semantic\/objects"[\s\S]{0,200}<ModelingCompatibilityPage \/>/);
	assert.match(compatibility, /listModelSpecs/);
	assert.doesNotMatch(compatibility, /post\(|put\(|createSemantic|updateSemantic/);
});

test("BI semantic exploration remains a separate supported surface", () => {
	const routes = read("../../routes/sections/dashboard/static-routes.tsx");
	assert.match(routes, /SemanticExplorePage/);
	assert.match(routes, /SemanticCardEditorPage/);
	assert.match(routes, /SemanticVirtualDatasetsPage/);
});
