import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const staticRoutes = readFileSync(
	new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url),
	"utf8",
);
const dynamicResolver = readFileSync(
	new URL("../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url),
	"utf8",
);
const menuSeed = readFileSync(
	new URL("../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
	"utf8",
);
const standardBindingDraftUrl = new URL("./standardBindingDraft.ts", import.meta.url);
const standardBindingDraft = existsSync(standardBindingDraftUrl) ? readFileSync(standardBindingDraftUrl, "utf8") : "";
const sqlModelingPage = readFileSync(new URL("./SqlModelingPage.tsx", import.meta.url), "utf8");
const dbtFileBrowserPage = readFileSync(new URL("./DbtFileBrowserPage.tsx", import.meta.url), "utf8");
const modelCenterPage = readFileSync(new URL("./ModelCenterPage.tsx", import.meta.url), "utf8");
const elementsPage = readFileSync(new URL("../governance/ElementsPage.tsx", import.meta.url), "utf8");
const standardPackagePage = readFileSync(new URL("../foundation/StandardPackagePage.tsx", import.meta.url), "utf8");
const referenceCodesPage = readFileSync(new URL("../governance/ReferenceCodesPage.tsx", import.meta.url), "utf8");
const platformApi = readFileSync(new URL("../../api/platformApi.ts", import.meta.url), "utf8");
const sprint64Api = readFileSync(new URL("../../api/sprint64GovernanceApi.ts", import.meta.url), "utf8");
const workbenchPage = readFileSync(new URL("../workbench/DataManagementWorkbenchPage.tsx", import.meta.url), "utf8");
const projectSpacePage = readFileSync(new URL("./ModelTemplatesPage.tsx", import.meta.url), "utf8");
const businessModelingContextBar = readFileSync(new URL("./semantic-workspace/BusinessModelingContextBar.tsx", import.meta.url), "utf8");

test("data development workbench routes converge on canonical pages", () => {
	assert.doesNotMatch(menuSeed, /"key": "low-code-development"|"key": "dbt-files"/);
	assert.match(menuSeed, /"title": "维度建模"[\s\S]*?"title": "高级建模（SQL\/dbt）"/);
	assert.match(menuSeed, /"key": "modeling-workbench"[\s\S]*?"title": "建模工作台"[\s\S]*?"externalLink": "\/modeling\/workbench"/);
	assert.match(menuSeed, /"title": "维度建模"[\s\S]*?"externalLink": "\/studio\/sql-modeling"/);
	assert.match(menuSeed, /"title": "数据标准"[\s\S]*?"externalLink": "\/governance\/standards\/elements"/);
	assert.match(menuSeed, /"title": "公共码表"[\s\S]*?"externalLink": "\/governance\/standards\/reference"/);
	assert.match(staticRoutes, /const SqlModelingPage = lazy\(\(\) => import\("@\/pages\/modeling\/SqlModelingPage"\)\)/);
	assert.match(staticRoutes, /const ModelingCompatibilityPage = lazy\(\(\) => import\("@\/pages\/modeling\/ModelingCompatibilityPage"\)\)/);
	assert.match(staticRoutes, /path: "studio\/low-code-development"[\s\S]*<ModelingCompatibilityPage/);
	assert.match(staticRoutes, /path: "studio\/sql-modeling"[\s\S]*<SqlModelingPage/);
	assert.match(staticRoutes, /path: "modeling\/dbt-files"[\s\S]*<ModelingCompatibilityPage/);
	assert.match(dynamicResolver, /"\/studio\/low-code-development": "\/pages\/modeling\/ModelingCompatibilityPage"/);
	assert.match(dynamicResolver, /"\/studio\/sql-modeling": "\/pages\/modeling\/SqlModelingPage"/);
	assert.match(dynamicResolver, /"\/modeling\/dbt-files": "\/pages\/modeling\/ModelingCompatibilityPage"/);
	assert.match(dynamicResolver, /"\/governance\/standards\/elements": "\/pages\/governance\/ElementsPage"/);
	assert.match(dynamicResolver, /"\/governance\/standards\/reference": "\/pages\/governance\/ReferenceCodesPage"/);
});

test("business process management converges on canonical category and dimension routes", () => {
	assert.match(projectSpacePage, /数据开发中心 · 项目空间管理/);
	assert.match(projectSpacePage, /data-testid="business-process-workspace-nav"/);
	assert.match(projectSpacePage, /项目空间可选|项目空间仅用于/);
	assert.match(projectSpacePage, /\/governance\/subjects\?focus=business-processes/);
	assert.match(businessModelingContextBar, /项目空间：未启用（默认上下文）/);
	assert.match(staticRoutes, /path: "modeling\/dimensions"/);
	assert.match(staticRoutes, /path: "modeling\/models"/);
});

test("workbench consumes the backend-owned Sprint 64 layer registry", () => {
	assert.match(sprint64Api, /listWarehouseLayersApi/);
	assert.match(workbenchPage, /listWarehouseLayersApi/);
	assert.match(workbenchPage, /WAREHOUSE_LAYER_PLAN/);
});

test("sql modeling page exposes stable workbench actions for standard and dbt linkage", () => {
	assert.match(sqlModelingPage, /data-testid="platform-sql-modeling-page"/);
	assert.match(sqlModelingPage, /data-testid="platform-sql-modeling-compile"/);
	assert.match(sqlModelingPage, /data-testid="platform-sql-modeling-test"/);
	assert.match(sqlModelingPage, /data-testid="platform-sql-modeling-build"/);
	assert.match(sqlModelingPage, /data-testid="platform-sql-modeling-release"/);
	assert.match(
		sqlModelingPage,
		/sqlModels\.length === 0 \|\|[\s\S]{0,120}!configEnabled \|\|[\s\S]{0,120}!workspaceOk \|\|[\s\S]{0,120}buildTriggering != null/,
	);
	assert.doesNotMatch(sqlModelingPage, /建模与上线操作流程|key: "guide"|label: "操作说明"/);
	assert.match(sqlModelingPage, /质量门禁/);
	assert.match(sqlModelingPage, /data-testid="platform-sql-modeling-standard-readiness"/);
	assert.match(sqlModelingPage, /data-testid="platform-sql-modeling-standard-gate-check"/);
	assert.match(sqlModelingPage, /data-testid="platform-sql-modeling-schema-yml-generate"/);
	assert.match(sqlModelingPage, /listSqlModelStandardBindings/);
	assert.match(sqlModelingPage, /saveSqlModelStandardBindings/);
	assert.match(sqlModelingPage, /listMetadataStandards/);
	assert.match(sqlModelingPage, /checkSqlModelStandardGate/);
	assert.match(sqlModelingPage, /generateSqlModelSchemaYml/);
	assert.match(sqlModelingPage, /字段标准绑定/);
	assert.match(sqlModelingPage, /自动匹配数据元/);
	assert.match(sqlModelingPage, /标准门禁/);
	assert.match(sqlModelingPage, /生成 schema\.yml/);
	assert.match(sqlModelingPage, /公共码表 seed/);
	assert.match(platformApi, /url: `\/modeling\/sql-models\/\$\{id\}\/standard-bindings`/);
	assert.match(platformApi, /url: `\/modeling\/sql-models\/\$\{id\}\/dbt\/schema-yml`/);
	assert.match(platformApi, /url: `\/modeling\/sql-models\/\$\{id\}\/standard-gate\/check`/);
});

test("governance standards pages expose stable controls for model field standards", () => {
	assert.match(elementsPage, /title="数据治理中心 · 标准管理 \/ 数据元"/);
	assert.match(elementsPage, /data-testid="governance-elements-create"/);
	assert.match(elementsPage, /data-testid="governance-elements-view-references"/);
	assert.match(elementsPage, /listMetadataStandards/);
	assert.match(platformApi, /url: "\/modeling\/metadata-standards"/);
	assert.match(elementsPage, /listReferenceCodes/);
	assert.match(elementsPage, /模型字段引用/);
	assert.match(referenceCodesPage, /title="公共码表"/);
	assert.match(referenceCodesPage, /data-testid="governance-reference-sync-seeds"/);
	assert.match(referenceCodesPage, /loading=\{seedSyncing\}/);
	assert.match(referenceCodesPage, /syncReferenceCodeSeeds/);
	assert.match(referenceCodesPage, /更新 dbt Seeds/);
	assert.match(referenceCodesPage, /dbt Seeds 同步/);
	assert.match(platformApi, /url: "\/governance\/reference-codes\/seeds"/);
});

test("model fields own canonical standards while legacy SQL drafts remain read-only compatible", () => {
	assert.match(standardBindingDraft, /STANDARD_BINDING_DRAFT_STORAGE_KEY/);
	assert.match(standardBindingDraft, /getStandardBindingDraft/);
	assert.match(standardBindingDraft, /isBackendStandardBindingDraftId/);
	assert.match(standardBindingDraft, /buildSqlFromStandardBindingDraft/);
	assert.match(standardBindingDraft, /buildStandardBindingsFromDraft/);
	assert.match(platformApi, /url: "\/modeling\/standard-binding-drafts"/);
	assert.match(platformApi, /url: `\/modeling\/standard-binding-drafts\/\$\{id\}`/);

	assert.match(standardPackagePage, /标准包已应用/);
	assert.match(standardPackagePage, /buildDataElementReturnRoute/);
	assert.doesNotMatch(standardPackagePage, /查看数据元并生成落标草稿|bindingDraft=1/);

	assert.doesNotMatch(elementsPage, /createStandardBindingDraft|createStandardBindingDraftSnapshot/);
	assert.doesNotMatch(elementsPage, /standardDraftId|warehousePlanningContext/);

	assert.match(modelCenterPage, /data-testid="model-center-page"/);
	assert.match(modelCenterPage, /旧低代码入口已并入模型中心/);
	assert.match(modelCenterPage, /ModelSpecCreateDrawer/);
	assert.doesNotMatch(modelCenterPage, /直接创建维度表、明细表、汇总表和应用表|还没有模型，点击/);

	assert.match(sqlModelingPage, /getStandardBindingDraft/);
	assert.match(sqlModelingPage, /getStandardBindingDraftSnapshot/);
	assert.match(sqlModelingPage, /buildStandardBindingsFromDraft/);
	assert.match(sqlModelingPage, /buildSqlFromStandardBindingDraft/);
	assert.match(sqlModelingPage, /data-testid="platform-sql-modeling-standard-draft"/);
	assert.match(sqlModelingPage, /应用到当前模型/);
	assert.match(sqlModelingPage, /创建模型草稿/);
	assert.match(sqlModelingPage, /saveSqlModelStandardBindings/);
});

test("standard binding drafts expose provenance and field gap evidence", () => {
	assert.match(standardBindingDraft, /buildStandardBindingDraftSummary/);
	assert.match(standardBindingDraft, /missingStandardCount/);
	assert.match(sqlModelingPage, /标准来源|标准草稿来源/);
	assert.match(sqlModelingPage, /待补标准|缺失标准/);
});

test("sql modeling exposes release gates and routes run evidence back to ops", () => {
	assert.match(sqlModelingPage, /发布门禁汇总|发布前门禁/);
	assert.match(sqlModelingPage, /标准门禁/);
	assert.match(sqlModelingPage, /质量门禁/);
	assert.match(sqlModelingPage, /权限门禁/);
	assert.match(sqlModelingPage, /const buildOpsInstanceRoute/);
	assert.match(sqlModelingPage, /journey:\s*"e2e-data-product"/);
	assert.match(sqlModelingPage, /return `\/ops\/instances\?\$\{params\.toString\(\)\}`/);
});

test("dbt file browser is a file evidence surface and hands publishing back to SQL modeling", () => {
	assert.match(dbtFileBrowserPage, /title="DBT 文件工作区"/);
	assert.match(dbtFileBrowserPage, /data-testid="dbt-file-browser-preview-model"/);
	assert.match(dbtFileBrowserPage, /data-testid="dbt-file-browser-save"/);
	assert.match(dbtFileBrowserPage, /title="dbt 文件发布需先通过 SQL 建模页发布门禁"/);
	assert.match(dbtFileBrowserPage, /\/studio\/sql-modeling/);
});

test("metric workbench route is registered in static routes and dynamic resolver", () => {
	assert.match(staticRoutes, /path: "modeling\/metric-workbench"/);
	assert.match(dynamicResolver, /"\/modeling\/metric-workbench"/);
	assert.match(staticRoutes, /path: "modeling\/semantic\/subjects"/);
	assert.match(staticRoutes, /path: "modeling\/semantic\/objects"/);
	assert.match(staticRoutes, /path: "modeling\/semantic\/metrics"/);
	assert.match(staticRoutes, /path: "modeling\/semantic\/models"/);
	assert.match(staticRoutes, /path: "modeling\/semantic\/publish"/);
	assert.match(staticRoutes, /path: "modeling\/semantic\/runs"/);
});
