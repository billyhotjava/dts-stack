import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const helperUrl = new URL("./modelSpecDetailNavigation.ts", import.meta.url);
const pageUrl = new URL("./ModelSpecDetailPage.tsx", import.meta.url);
const fieldsUrl = new URL("./components/ModelSpecFieldsTab.tsx", import.meta.url);
const standardsUrl = new URL("./components/ModelSpecStandardsTab.tsx", import.meta.url);
const dependenciesUrl = new URL("./components/ModelSpecDependencyPanel.tsx", import.meta.url);
const logicalStageUrl = new URL("./components/ModelSpecLogicalDesignStage.tsx", import.meta.url);
const modelCenterUrl = new URL("./ModelCenterPage.tsx", import.meta.url);
const platformApiUrl = new URL("../../api/platformApi.ts", import.meta.url);
const modelSpecApiUrl = new URL("../../api/modelSpecApi.ts", import.meta.url);
const read = (url: URL) => readFileSync(url, "utf8");

test("model detail stage navigation reads legacy deep links and writes only activeStage", async () => {
	assert.equal(existsSync(helperUrl), true, "model detail navigation helper is missing");
	const navigation = await import(helperUrl.href);
	const { modelSpecDetailPath, modelSpecPlanModelsPath, resolveModelSpecDetailStage, resolveModelSpecDetailTab } = navigation;

	assert.equal(resolveModelSpecDetailTab(new URLSearchParams()), "design");
	assert.equal(resolveModelSpecDetailTab(new URLSearchParams("tab=fields")), "fields");
	assert.equal(resolveModelSpecDetailTab(new URLSearchParams("tab=standards")), "standards");
	assert.equal(resolveModelSpecDetailTab(new URLSearchParams("tab=release")), "design");
	assert.equal(resolveModelSpecDetailStage(new URLSearchParams("tab=design")), "logical");
	assert.equal(resolveModelSpecDetailStage(new URLSearchParams("tab=fields")), "logical");
	assert.equal(resolveModelSpecDetailStage(new URLSearchParams("tab=standards")), "logical");
	assert.equal(resolveModelSpecDetailStage(new URLSearchParams("tab=release")), "physical");
	assert.equal(resolveModelSpecDetailStage(new URLSearchParams("activeStage=implementation&tab=release")), "implementation");
	assert.equal(resolveModelSpecDetailStage(new URLSearchParams("activeStage=unexpected&tab=release")), "logical");
	assert.equal(
		modelSpecDetailPath("model / 1", "implementation", "plan / 1"),
		"/modeling/models/model%20%2F%201?activeStage=implementation&planId=plan+%2F+1",
	);
	assert.equal(
		modelSpecDetailPath("model / 1", "standards", "plan / 1"),
		"/modeling/models/model%20%2F%201?activeStage=logical&planId=plan+%2F+1",
	);
	assert.equal(modelSpecPlanModelsPath("plan / 1"), "/modeling/models?planId=plan+%2F+1");
	assert.equal(modelSpecPlanModelsPath("  "), "/modeling/models");
	assert.equal(typeof navigation.modelSpecCatalogPath, "function");
	assert.equal(
		navigation.modelSpecCatalogPath?.("DIMENSION", "plan / 1", "domain / 1"),
		"/modeling/dimensions?planId=plan+%2F+1&domainId=domain+%2F+1",
	);
	assert.equal(
		navigation.modelSpecCatalogPath?.("FACT", "plan / 1", "ignored domain"),
		"/modeling/models?planId=plan+%2F+1",
	);
	const modelCenter = read(modelCenterUrl);
	assert.match(modelCenter, /\?activeStage=logical/);
	assert.match(modelCenter, /initialDimensionDefinitionId=\{searchParams\.get\("dimensionDefinitionId"\)/);
	assert.match(modelCenter, /initialDimensionDefinitionRevision=\{Number\(searchParams\.get\("dimensionDefinitionRevision"\)\) \|\| undefined\}/);
});

test("model detail separates design fields and standards without trusting query plan context", () => {
	assert.equal(existsSync(fieldsUrl), true, "model fields tab is missing");
	assert.equal(existsSync(standardsUrl), true, "model standards tab is missing");
	assert.equal(existsSync(dependenciesUrl), true, "model dependency panel is missing");
	assert.equal(existsSync(logicalStageUrl), true, "logical design stage is missing");
	const page = read(pageUrl);
	const fields = read(fieldsUrl);
	const standards = read(standardsUrl);
	const dependencies = read(dependenciesUrl);
	const logicalStage = read(logicalStageUrl);
	const platformApi = read(platformApiUrl);
	const modelSpecApi = read(modelSpecApiUrl);

	assert.match(page, /ModelSpecLogicalDesignStage/);
	assert.match(page, /ModelSpecImplementationStage/);
	assert.match(page, /ModelSpecPhysicalAssetStage/);
	assert.doesNotMatch(page, /ModelSpecEditorFields/);
	assert.match(logicalStage, /ModelSpecFieldsTab/);
	assert.match(logicalStage, /ModelSpecStandardsTab/);
	assert.match(logicalStage, /ModelSpecDependencyPanel/);
	assert.match(
		page,
		/await form\.validateFields\(\);[\s\S]*form\.getFieldsValue\(true\)/,
		"full-replacement saves must include unmounted metric and standard collections",
	);
	assert.match(page, /modelSpecCatalogPath\(model\.modelType,\s*model\.planId,\s*model\.domainId\)/);
	assert.doesNotMatch(page, /searchParams\.get\(["']planId["']\)/);
	assert.match(fields, /Form\.List[\s\S]*name="fields"/);
	assert.match(standards, /standardBindings/);
	assert.match(standards, /前往数据元/);
	assert.match(standards, /配置字段标准/);
	assert.match(standards, /listMeasurementUnits/);
	assert.match(page, /onSaveStandardBindings/);
	assert.match(page, /readOnly=!canEdit/);
	assert.match(platformApi, /\/governance\/measurement-units/);
	assert.match(modelSpecApi, /\/dependencies/);
	assert.match(dependencies, /当前版本/);
	assert.match(dependencies, /版本漂移/);
	assert.match(dependencies, /引用不可用/);
	assert.doesNotMatch(standards, /(standardElementVersion|referenceCodeVersion|measurementUnitVersion)\s*:\s*1/);
});
