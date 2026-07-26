import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (relativePath: string) => readFileSync(new URL(relativePath, import.meta.url), "utf8");

test("data mart is a planning object with management and warehouse baseline entry", () => {
	const workspace = read("../governance/DataMartWorkspace.tsx");
	const subjects = read("../governance/SubjectAreasPage.tsx");
	const baseline = read("./components/WarehousePlanDataMartBaseline.tsx");
	const api = read("../../api/dataMartApi.ts");

	assert.match(subjects, /tab=data-marts|data-marts/);
	assert.match(subjects, /<DataMartWorkspace/);
	assert.match(workspace, /这里只管理规划对象，发布后才登记为数据资产/);
	assert.match(workspace, /新建数据集市/);
	assert.match(workspace, /confirmDataMart/);
	assert.match(workspace, /retireDataMart/);
	assert.match(workspace, /搜索数据集市名称、编码、用途或责任人/);
	assert.match(baseline, /当前规划的数据集市/);
	assert.match(baseline, /getWarehousePlanCategories/);
	assert.match(baseline, /需先确认业务分类/);
	assert.match(api, /\/modeling\/data-marts/);
	assert.match(api, /baseline\/data-marts/);
});

test("dimension definition owns scope attributes and business hierarchy semantics", () => {
	const drawer = read("./components/DimensionDefinitionCreateDrawer.tsx");
	const catalog = read("./DimensionCatalogPage.tsx");
	const contract = read("./dimensionDefinitionContract.ts");

	for (const label of ["适用范围类型", "所属数据集市", "业务属性", "分析层级（可选）"]) {
		assert.match(drawer, new RegExp(label));
	}
	assert.match(drawer, /业务主键最多只能选择一个/);
	assert.match(drawer, /属性编码不能重复/);
	assert.match(drawer, /标准引用和标准版本需同时填写/);
	assert.match(drawer, /hierarchies/);
	assert.match(catalog, /数据集市/);
	assert.match(catalog, /业务属性/);
	assert.match(contract, /DimensionDefinitionScopeType = "DOMAIN" \\| "DATA_MART"/);
	assert.match(contract, /DimensionDefinitionAttribute/);
});

test("dimension table maps pinned attributes and separates implementation policies", () => {
	const createDrawer = read("./components/ModelSpecCreateDrawer.tsx");
	const fields = read("./components/ModelSpecFieldsTab.tsx");
	const logical = read("./components/ModelSpecLogicalDesignStage.tsx");
	const api = read("../../api/modelSpecApi.ts");

	assert.match(createDrawer, /数据集市（可选）/);
	assert.match(createDrawer, /实现变体（可选）/);
	assert.match(createDrawer, /查看已有维度表/);
	assert.match(fields, /维度属性编码/);
	assert.match(fields, /冗余维度字段/);
	assert.match(fields, /冗余来源依据/);
	assert.match(logical, /getDimensionDefinitionRevision/);
	for (const label of ["物理表名", "装载策略", "数据保留天数（可选）", "SCD 逻辑策略"]) {
		assert.match(logical, new RegExp(label));
	}
	assert.match(logical, /max=\{36000\}/);
	assert.match(api, /warehouse-plans\/\$\{encodeURIComponent\(planId\)\}\/naming\/validate/);
});
