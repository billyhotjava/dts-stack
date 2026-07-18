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
