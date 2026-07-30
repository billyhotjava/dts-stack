import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const read = (relativePath: string) => readFileSync(new URL(relativePath, import.meta.url), "utf8");

test("canonical dimension and model pages exist and stay on their respective API boundaries", () => {
	for (const file of ["./DimensionCatalogPage.tsx", "./ModelCenterPage.tsx", "./ModelSpecDetailPage.tsx"]) {
		assert.equal(existsSync(new URL(file, import.meta.url)), true, `${file} is missing`);
		const source = read(file);
		assert.doesNotMatch(source, /semanticModelingApi|listSemanticBusinessObjects|createSemanticBusinessObject/);
		assert.doesNotMatch(source, /\bobjectId\b|\bprocessId\b/);
	}

	assert.match(read("./DimensionCatalogPage.tsx"), /listDimensionDefinitions/);
	assert.doesNotMatch(read("./DimensionCatalogPage.tsx"), /listModelSpecs|ModelSpecCreateDrawer/);
	assert.match(read("./ModelCenterPage.tsx"), /createModelSpec/);
	assert.match(
		read("./ModelCenterPage.tsx"),
		/listModelSpecs\(/,
		"model center must load the candidate set with its active plan and domain filters",
	);
	assert.match(read("./ModelSpecDetailPage.tsx"), /getModelSpec/);
	assert.match(read("./ModelSpecDetailPage.tsx"), /updateModelSpec/);
});

test("canonical pages expose one customer-facing primary action and no fake release gate", () => {
	const dimensions = read("./DimensionCatalogPage.tsx");
	const models = read("./ModelCenterPage.tsx");
	const detail = read("./ModelSpecDetailPage.tsx");
	const detailHeader = read("./components/ModelSpecDetailHeader.tsx");
	const detailPrimaryAction = read("./components/ModelSpecPrimaryActionButton.tsx");
	const stageProjection = read("./modelSpecDetailStageProjection.ts");

	assert.match(dimensions, /type="primary"[\s\S]*登记维度/);
	assert.match(models, /type="primary"[\s\S]*新建模型/);
	assert.match(detailHeader, /ModelSpecPrimaryActionButton/);
	assert.match(detailPrimaryAction, /type="primary"[\s\S]*action\.label/);
	assert.match(stageProjection, /保存逻辑设计/);
	assert.doesNotMatch(
		`${dimensions}\n${models}\n${detail}\n${detailHeader}\n${detailPrimaryAction}\n${stageProjection}`,
		/getModelSpecReleaseGate|可发布|发布模型/,
	);
});

test("model center handles compatibility views without restoring retired pages", () => {
	const models = read("./ModelCenterPage.tsx");
	assert.match(models, /searchParams\.get\("view"\)/);
	assert.match(models, /compatibilityView === "guided"/);
	assert.match(models, /setCreateOpen\(true\)/);
	assert.match(models, /compatibilityView === "release"/);
	assert.doesNotMatch(models, /LowCodeDevelopmentPage|SemanticPublishPage/);
});

test("static and dynamic routing resolve canonical list and detail pages to the same targets", () => {
	const staticRoutes = read("../../routes/sections/dashboard/static-routes.tsx");
	const dynamicRoutes = read("../../routes/sections/dashboard/dynamic-resolver.tsx");

	for (const path of ["modeling/dimensions", "modeling/models", "modeling/models/:modelSpecId"]) {
		assert.match(staticRoutes, new RegExp(`path: "${path.replace(/[.*+?^${}()|[\\]\\]/g, "\\$&")}"`));
	}
	assert.match(dynamicRoutes, /"\/modeling\/dimensions": "\/pages\/modeling\/DimensionCatalogPage"/);
	assert.match(dynamicRoutes, /"\/modeling\/models": "\/pages\/modeling\/ModelCenterPage"/);
	assert.match(dynamicRoutes, /"\/modeling\/models\/:modelSpecId": "\/pages\/modeling\/ModelSpecDetailPage"/);
	assert.match(
		dynamicRoutes,
		/directOverridePath &&[\s\S]*pathname !== resolvedPath[\s\S]*renderDashboardComponent\(directOverridePath,\s*pathname\)/,
	);
});

test("warehouse plan facts and dimensions section opens both canonical catalogs with plan context", () => {
	const planDetail = read("./WarehousePlanDetailPage.tsx");

	assert.match(
		planDetail,
		/const openSpecialist = \(route: string\) => navigate\(withWarehousePlanContext\(route, planId\)\)/,
	);
	assert.match(planDetail, /label: "维度目录", route: "\/modeling\/dimensions"/);
	assert.match(planDetail, /label: "模型中心", route: "\/modeling\/models"/);
});
