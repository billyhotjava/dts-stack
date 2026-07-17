import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./SubjectAreasPage.tsx", import.meta.url), "utf8");
const tabsSource = readFileSync(new URL("./SubjectWorkspaceTabs.tsx", import.meta.url), "utf8");

test("subject areas expose a DWD dimension planning entry", () => {
	assert.match(source, /warehousePlanningContext/);
	assert.match(source, /warehouse-planning-card/);
	assert.match(source, /warehouseLayer.*DWD|DWD.*warehouseLayer/);
	assert.match(source, /modelingMode.*dimension|dimension.*modelingMode/);
	assert.ok(source.includes("governance/standards/elements"));
	assert.match(source, /planningId/);
});

test("warehouse planning explains STG as an optional dbt technical transition layer", () => {
	assert.match(source, /输出分层方案/);
	assert.match(source, /STG/);
	assert.match(source, /技术过渡层/);
	assert.match(source, /dbt/);
	assert.match(source, /可选|启用/);
});

test("subject area planning exposes blocked and restore states", () => {
	assert.match(source, /resolveWarehousePlanningContext/);
	assert.match(source, /resolveWarehousePlanningStatus/);
	assert.match(source, /planningResolution\.status\s*===\s*"blocked"/);
	assert.match(source, /规划草稿已失效|规划上下文不可用|重新确认规划/);
	assert.match(source, /维度建模/);
});

test("subject area details expose business processes and process planning entry", () => {
	assert.match(source, /businessProcess/);
	assert.match(source, /业务过程/);
	assert.match(source, /新增业务过程/);
	assert.doesNotMatch(source, /从示例创建|采用示例|补充示例/);
	assert.match(source, /processId/);
	assert.match(source, /进入业务建模/);
	assert.match(source, /modeling\/semantic\/objects/);
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
	assert.match(source, /<Dropdown[\s\S]*?新增主题域[\s\S]*?<\/Dropdown>/);
	assert.match(source, /key:\s*"edit"[\s\S]*?key:\s*"delete"/);
	assert.doesNotMatch(source, /<Button[^>]*>\s*编辑域属性\s*<\/Button>/);
});

test("selected domain resumes from the generic modeling context", () => {
	assert.match(source, /searchParams\.get\("domainId"\)\s*\|\|\s*searchParams\.get\("active"\)/);
	assert.match(source, /params\.set\("domainId", nextActive\)/);
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
