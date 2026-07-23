import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./SubjectAreasPage.tsx", import.meta.url), "utf8");
const tabsSource = readFileSync(new URL("./SubjectWorkspaceTabs.tsx", import.meta.url), "utf8");

test("subject areas remain a global catalog and never create a browser-only warehouse plan", () => {
	assert.match(source, /getDomainTree/);
	assert.match(source, /buildWarehousePlanRoute/);
	assert.doesNotMatch(source, /warehousePlanningContext|saveWarehousePlanningContext/);
	assert.doesNotMatch(source, /warehouse-plan-.*Date\.now|governance\/standards\/elements\?bindingDraft=1/);
});

test("category planning actions return to the canonical plan baseline", () => {
	assert.match(source, /buildWarehousePlanRoute\(returnPlanId, "baseline", \{ tab: "categories" \}\)/);
	assert.match(source, /业务分类是全局目录/);
	assert.match(source, /前往建设规划|返回建设计划/);
});

test("business modeling never invents a plan when no canonical planId exists", () => {
	assert.match(source, /\/modeling\/plans/);
	assert.match(source, /resolveWarehousePlanPageContext/);
	assert.match(source, /planId: returnPlanId/);
	assert.doesNotMatch(source, /planningId:|warehouseLayer:|modelingMode:/);
});

test("subject area details expose business processes and process planning entry", () => {
	assert.match(source, /businessProcess/);
	assert.match(source, /业务过程/);
	assert.match(source, /新增业务过程/);
	assert.doesNotMatch(source, /从示例创建|采用示例|补充示例/);
	assert.match(source, /processId/);
	assert.match(source, /进入业务建模/);
	assert.match(source, /modeling\/dimensions/);
	assert.doesNotMatch(source, /modeling\/semantic\/objects/);
});

test("subject areas separate modeling scope, domain details and governance overview", () => {
	assert.match(source, /SubjectWorkspaceTabs/);
	assert.match(tabsSource, /建模范围/);
	assert.match(tabsSource, /主题域信息/);
	assert.match(tabsSource, /治理概览/);
	assert.match(source, /tab/);
});

test("dimensional-only helpers are on demand and bus matrix is not in the primary UI", () => {
	assert.match(source, /DimensionalModelingAssist/);
	assert.match(source, /维度建模辅助/);
	assert.doesNotMatch(source, /saveBusMatrixLinkApi/);
	assert.doesNotMatch(source, /title="总线矩阵"/);
});

test("selected domain exposes one primary continuation action", () => {
	assert.match(source, /继续逻辑模型/);
	assert.match(source, /modelingStagePath\("LOGICAL"\)/);
	assert.doesNotMatch(source, /\+ 挂载术语/);
});

test("subject administration converges into one create action and one overflow menu", () => {
	assert.match(source, /<Dropdown[\s\S]*?新增业务分类[\s\S]*?<\/Dropdown>/);
	assert.match(source, /key:\s*"edit"[\s\S]*?key:\s*"delete"/);
	assert.doesNotMatch(source, /<Button[^>]*>\s*编辑域属性\s*<\/Button>/);
});

test("selected domain resumes from the generic modeling context", () => {
	assert.match(source, /searchParams\.get\("domainId"\)\s*\|\|\s*searchParams\.get\("active"\)/);
	assert.match(source, /params\.set\("domainId", nextActive\)/);
});

test("business category administration only returns to a whitelisted plan baseline", () => {
	assert.match(source, /resolveWarehousePlanPageContext\(searchParams\)/);
	assert.match(source, /返回建设计划/);
	assert.doesNotMatch(source, /window\.location\s*=|location\.href\s*=/);
});

test("subject administration primary surfaces use business category language", () => {
	assert.match(source, /title="业务分类"/);
	assert.match(source, />\s*新增业务分类\s*</);
	assert.match(source, /placeholder="搜索业务分类/);
	assert.match(source, /title="暂无业务分类"/);
	assert.match(source, /label="业务分类名称"/);
	assert.match(source, /label="上级业务分类"/);
	assert.doesNotMatch(source, /title="主题域管理"|>\s*新增主题域\s*<|placeholder="搜索主题域/);
});

test("subject area modeling facts use the backend as the only saved source of truth", () => {
	assert.match(source, /loadDomainModelingFacts/);
	assert.doesNotMatch(source, /loadBusinessProcesses|saveBusinessProcesses|removeBusinessProcess/);
	assert.doesNotMatch(source, /loadBusMatrix|toggleBusMatrixLink/);
	assert.doesNotMatch(source, /session draft|session 草稿|offline fallback|本地草稿/);
});

test("subject area exposes candidate review before downstream modeling", () => {
	assert.match(source, /ConformedDimensionCatalogCard/);
	assert.match(source, /pendingCandidateCount/);
	assert.match(source, /confirmModelingCandidatesApi/);
	assert.match(source, /待确认候选/);
	assert.match(source, /确认后使用/);
});

test("subject area collapses the domain directory at narrow viewports", () => {
	assert.match(source, /breakpoint="lg"/);
	assert.match(source, /collapsedWidth=\{0\}/);
	assert.match(source, /<Sider[\s\S]*?className="[^"]*overflow-hidden/);
	assert.match(source, /defaultExpandedKeys=\{\[ROOT_KEY\]\}/);
	assert.match(source, /flex flex-col gap-3 sm:flex-row/);
	assert.match(source, /min-w-0/);
});
