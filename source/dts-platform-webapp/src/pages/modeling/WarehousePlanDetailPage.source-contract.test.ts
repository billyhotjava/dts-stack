import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const page = readFileSync(new URL("./WarehousePlanDetailPage.tsx", import.meta.url), "utf8");
const routes = readFileSync(new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url), "utf8");
const api = readFileSync(new URL("../../api/warehousePlanApi.ts", import.meta.url), "utf8");
const viewModel = readFileSync(new URL("./warehousePlanViewModel.ts", import.meta.url), "utf8");

test("warehouse plan detail is registered as a stable planId wildcard route", () => {
	assert.match(routes, /const WarehousePlanDetailPage = lazy/);
	assert.match(routes, /path: "modeling\/plans\/:planId\/\*"[\s\S]*<WarehousePlanDetailPage/);
	assert.match(page, /useParams/);
	assert.match(page, /planId/);
});

test("six tabs organize editing without becoming a second completion state", () => {
	for (const label of ["规划概览", "规划基线", "数仓架构", "事实与维度", "实现与验证", "发布成果"]) {
		assert.match(page, new RegExp(label));
	}
	assert.doesNotMatch(page, /setTabComplete|completedTabs|tabProgress|markComplete/);
});

test("editing follows the backend maintainer-role gate as well as lifecycle", () => {
	assert.match(page, /useUserRoles/);
	assert.match(page, /hasWarehousePlanCreateAccess\(userRoles\)/);
	assert.match(page, /planEditable\s*=\s*canMaintainPlan\s*&&/);
});

test("category and policy mutations are plan-scoped before form validation", () => {
	assert.match(page, /const categoryMutationGuard\s*=\s*useMemo\(\(\) => createLatestRequestGuard\(\)/);
	assert.match(page, /const policyMutationGuard\s*=\s*useMemo\(\(\) => createLatestRequestGuard\(\)/);
	for (const [mutationName, guardName] of [
		["saveCategories", "categoryMutationGuard"],
		["savePolicy", "policyMutationGuard"],
	] as const) {
		const mutation = page.match(new RegExp(`const ${mutationName}[\\s\\S]*?\\n\\t};`))?.[0] || "";
		assert.ok(
			mutation.indexOf(`${guardName}.begin()`) < mutation.indexOf("validateFields()"),
			`${mutationName} must claim its plan-scoped mutation before async form validation`,
		);
		assert.match(mutation, /if \(!isCurrent\(\)\) return/);
	}
	const planEffect = page.match(/useEffect\(\(\) => \{[\s\S]*?void load\(\);[\s\S]*?\}, \[[\s\S]*?\]\);/)?.[0] || "";
	assert.match(planEffect, /categoryMutationGuard\.invalidate\(\)/);
	assert.match(planEffect, /policyMutationGuard\.invalidate\(\)/);
});

test("detail reads the canonical header, baseline and StageProjection", () => {
	assert.match(page, /getWarehousePlan/);
	assert.match(page, /getWarehousePlanningBaseline/);
	assert.match(page, /getWarehousePlanStageProjection/);
	assert.match(page, /primaryBlocker/);
	assert.match(page, /nextAction/);
});

test("detail derives the baseline tab from onboarding mode when the URL has no tab", () => {
	assert.match(page, /plan\?\.onboardingMode\s*===\s*"ASSET_FIRST"\s*\?\s*"sources"\s*:\s*"categories"/);
	assert.match(page, /data-testid="warehouse-plan-load-recovery"/);
});

test("baseline is a three-step workspace with real category and layer forms", () => {
	for (const label of ["业务分类", "数仓分层", "来源盘点"]) assert.match(page, new RegExp(label));
	assert.match(page, /getWarehousePlanCategories/);
	assert.match(page, /saveWarehousePlanCategories/);
	assert.match(page, /getWarehousePlanPolicy/);
	assert.match(page, /saveWarehousePlanPolicy/);
	assert.match(page, /categoryForm/);
	assert.match(page, /policyForm/);
	assert.match(page, /保存业务分类/);
	assert.match(page, /保存分层策略/);
	assert.doesNotMatch(page, /业务过程|业务对象/);
});

test("versioned baseline writes preserve dirty forms on conflict and refresh four server projections on success", () => {
	assert.match(api, /type VersionedWarehousePlanValue/);
	assert.match(api, /"If-Match"/);
	assert.match(page, /resolveWarehousePlanConflictVersion/);
	assert.match(page, /currentVersion/);
	assert.match(page, /保留/);
	assert.match(page, /refreshBaselineWorkspace/);
	const refresher = page.match(/const refreshBaselineWorkspace[\s\S]*?\n\t\}, \[[^\]]*\]\);/)?.[0] || "";
	for (const loader of ["loadBaselineInputs", "loadEvidence"]) assert.match(refresher, new RegExp(loader));
	assert.doesNotMatch(refresher, /setLifecycle|deriveNext|localStorage|sessionStorage/);
});

test("baseline input requests suppress duplicate global errors in favor of one page recovery", () => {
	const categoriesApi = api.match(/export const getWarehousePlanCategories[\s\S]*?\n\t\);/)?.[0] || "";
	const policyApi = api.match(/export const getWarehousePlanPolicy[\s\S]*?\n\t\);/)?.[0] || "";
	assert.match(categoriesApi, /_skipErrorToast:\s*true/);
	assert.match(policyApi, /_skipErrorToast:\s*true/);
	assert.match(page, /部分规划输入暂时不可用/);
});

test("business category management uses a plan-bound safe return route", () => {
	assert.match(page, /buildBusinessCategoryManagementRoute\(planId\)/);
	assert.match(page, /管理业务分类/);
});

test("baseline editing hides the header next action so each form keeps one primary action", () => {
	assert.match(page, /activeSection\s*!==\s*"baseline"\s*&&\s*nextAction/);
	assert.match(page, /保存业务分类/);
	assert.match(page, /保存分层策略/);
});

test("conflicts offer explicit current-version retry and discard choices", () => {
	assert.match(page, /保留当前输入并基于版本/);
	assert.match(page, /saveCategories\(categoryConflictVersion\)/);
	assert.match(page, /savePolicy\(policyConflictVersion\)/);
	assert.match(page, /放弃并加载最新版/);
});

test("four-way refresh preserves the other dirty tab while replacing the saved unit", () => {
	assert.match(page, /categoryDirty/);
	assert.match(page, /policyDirty/);
	assert.match(page, /onValuesChange=.*setCategoryDirty/);
	assert.match(page, /onValuesChange=.*setPolicyDirty/);
	assert.match(page, /refreshBaselineWorkspace\("categories"\)/);
	assert.match(page, /refreshBaselineWorkspace\("policy"\)/);
	assert.match(page, /categoryDirtyRef\.current/);
	assert.match(page, /policyDirtyRef\.current/);
});

test("mutation failures use stable response codes instead of raw technical messages", () => {
	assert.match(page, /warehousePlanMutationErrorMessage/);
	const categorySave = page.match(/const saveCategories[\s\S]*?\n\t};/)?.[0] || "";
	const policySave = page.match(/const savePolicy[\s\S]*?\n\t};/)?.[0] || "";
	assert.doesNotMatch(categorySave, /error\?\.message|error\.message/);
	assert.doesNotMatch(policySave, /error\?\.message|error\.message/);
});

test("category resolution copy marks archived bindings and never displays forbidden details", () => {
	assert.match(page, /buildWarehouseCategoryOptions/);
	assert.match(viewModel, /resolutionStatus\s*===\s*"ARCHIVED"[\s\S]{0,260}已归档/);
	assert.match(viewModel, /resolutionStatus\s*===\s*"FORBIDDEN"[\s\S]{0,180}"不可访问分类"/);
	assert.match(page, /warehousePlanIssueMessage\(issue\.code, issue\.message\)/);
});

test("detail restores the exact plan independently from baseline and stage evidence", () => {
	assert.match(page, /const header = await getWarehousePlan\(planId\)/);
	assert.match(page, /Promise\.allSettled/);
	assert.match(page, /data-testid="warehouse-plan-evidence-recovery"/);
	assert.match(page, /"状态未知"/);
	assert.match(page, /createLatestRequestGuard/);
	assert.match(page, /if \(!isCurrent\(\)\) return/);
});

test("evidence retry clears only failed evidence and keeps the restored plan header", () => {
	assert.match(page, /const loadEvidence = useCallback/);
	assert.match(page, /action=\{<Button onClick=\{\(\) => void loadEvidence\(\)\}>重新加载证据<\/Button>\}/);
	const evidenceLoader = page.match(/const loadEvidence = useCallback\([\s\S]*?\n\t\}, \[[^\]]*\]\);/)?.[0] || "";
	assert.match(
		evidenceLoader,
		/setBaseline\(baselineResult\.status === "fulfilled"\s*\?\s*baselineResult\.value\s*:\s*null\)/,
	);
	assert.match(
		evidenceLoader,
		/setProjection\(projectionResult\.status === "fulfilled"\s*\?\s*projectionResult\.value\s*:\s*null\)/,
	);
	assert.doesNotMatch(evidenceLoader, /getWarehousePlan\(|setPlan\(null\)/);
});

test("specialist capabilities remain deep links instead of copied forms", () => {
	for (const route of [
		"/governance/subjects",
		"/catalog/metadata-management",
		"/modeling/models",
		"/studio/sql-modeling",
		"/modeling/dbt-files",
		"/catalog/assets",
		"/modeling/metric-workbench",
		"/ops/instances",
	]) {
		assert.match(page, new RegExp(route.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
	}
});
