import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const page = readFileSync(new URL("./WarehousePlanDetailPage.tsx", import.meta.url), "utf8");
const routes = readFileSync(new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url), "utf8");

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

test("detail reads the canonical header, baseline and StageProjection", () => {
	assert.match(page, /getWarehousePlan/);
	assert.match(page, /getWarehousePlanningBaseline/);
	assert.match(page, /getWarehousePlanStageProjection/);
	assert.match(page, /primaryBlocker/);
	assert.match(page, /nextAction/);
});

test("detail derives the baseline tab from onboarding mode when the URL has no tab", () => {
	assert.match(page, /plan\?\.onboardingMode\s*===\s*"ASSET_FIRST"\s*\?\s*"sources"\s*:\s*"business-scope"/);
	assert.match(page, /data-testid="warehouse-plan-load-recovery"/);
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
		"/modeling/semantic/models",
		"/studio/sql-modeling",
		"/modeling/dbt-files",
		"/catalog/assets",
		"/modeling/metric-workbench",
		"/ops/instances",
	]) {
		assert.match(page, new RegExp(route.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
	}
});
