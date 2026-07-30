import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (relativePath: string) => readFileSync(new URL(relativePath, import.meta.url), "utf8");

const workbench = read("./ModelingWorkbenchPage.tsx");
const panels = read("./ModelingWorkspacePanels.tsx");
const center = read("./ModelCenterPage.tsx");
const detail = read("./ModelSpecDetailPage.tsx");
const detailRouteAdapter = read("./modelSpecDetailRouteAdapter.ts");
const detailSurface = `${detail}\n${detailRouteAdapter}`;
const detailHeader = read("./components/ModelSpecDetailHeader.tsx");

test("workbench owns model asset URL state without copying selected model business state", () => {
	assert.match(workbench, /assetKind=\{workspaceRoute\.assetKind\}/);
	assert.match(workbench, /assetId=\{workspaceRoute\.assetId\}/);
	assert.match(
		workbench,
		/assetKind:\s*"model"[\s\S]{0,160}assetId:\s*modelSpecId[\s\S]{0,160}activeStage:\s*"logical"/,
	);
	assert.match(workbench, /activeStage:\s*stage/);
	assert.match(workbench, /assetKind:\s*null[\s\S]{0,120}assetId:\s*null[\s\S]{0,120}activeStage:\s*null/);
	assert.match(workbench, /if\s*\(loadingPlans\s*&&\s*!modelAssetOpen\)/);
	assert.match(workbench, /if\s*\(plansFailed\s*&&\s*!modelAssetOpen\)/);
	assert.match(workbench, /const planOptions = useMemo\(\(\) => plans\.map/);
	assert.match(workbench, /planOptions=\{planOptions\}/);
	assert.match(workbench, /canImportModels=\{canImportModels\}/);
	assert.doesNotMatch(workbench, /selectedModel(?:Spec)?(?:Id)?\s*,\s*setSelectedModel/);
});

test("workspace panels exact-match model assets and compose the canonical detail owner", () => {
	assert.match(panels, /lazy\(\(\)\s*=>\s*import\("\.\/ModelSpecDetailPage"\)\)/);
	assert.match(panels, /assetKind\s*===\s*"model"/);
	assert.match(panels, /<ModelSpecDetailPage[\s\S]*embedded[\s\S]*modelSpecIdOverride=\{assetId\}/);
	assert.match(panels, /<ModelCenterPage[\s\S]*embedded[\s\S]*onOpenModel=/);
	assert.match(panels, /planOptionsOverride=\{planOptions\}/);
	assert.match(panels, /canImportOverride=\{canImportModels\}/);
	assert.doesNotMatch(panels, /from ["']@\/api\//);
	assert.doesNotMatch(panels, /listModelSpecs|getModelSpec|updateModelSpec/);
});

test("model center delegates embedded open and import actions while retaining standalone fallbacks", () => {
	assert.match(center, /embedded\??:\s*boolean/);
	assert.match(center, /planIdOverride\??:\s*string/);
	assert.match(center, /onOpenModel\??:/);
	assert.match(center, /onOpenModelImport\??:/);
	assert.match(center, /planOptionsOverride\??:/);
	assert.match(center, /canImportOverride\??:/);
	assert.match(center, /embedded\s*\?\s*Promise\.resolve/);
	assert.match(center, /onOpenModel\(model\.id,\s*model\.planId\s*\|\|\s*undefined\)/);
	assert.match(center, /onOpenModel\(model\.id,\s*planId\s*\|\|\s*undefined\)/);
	assert.match(center, /navigate\(modelSpecDetailPath/);
	assert.match(center, /embedded\s*\?\s*null\s*:\s*\(\s*<ImportModelPackageWizard/);
});

test("model detail exposes only a controlled embedded adapter around the existing owner", () => {
	assert.match(detailSurface, /embedded\??:\s*boolean/);
	assert.match(detailSurface, /modelSpecIdOverride\??:\s*string/);
	assert.match(detailSurface, /onBack\??:/);
	assert.match(detailSurface, /onStageChange\??:/);
	assert.match(detailSurface, /onResolvedContext\??:/);
	assert.match(detailSurface, /modelSpecIdOverride\s*\|\|\s*routeModelSpecId/);
	assert.match(detailSurface, /onResolvedContextRef\.current\?\./);
	assert.match(detailSurface, /if\s*\(onStageChange\)[\s\S]*onStageChange\(stage\)/);
	assert.match(detailSurface, /if\s*\(onBack\)[\s\S]*onBack\(\)/);
	assert.match(detail, /backLabel=\{embedded\s*\?\s*"返回逻辑模型"\s*:\s*undefined\}/);
	assert.match(detail, /\{embedded\s*\?\s*"返回逻辑模型"\s*:\s*"返回模型中心"\}/);
	assert.match(detailHeader, /backLabel\??:\s*string/);
	assert.match(detailHeader, /\{backLabel\s*\|\|/);
});
