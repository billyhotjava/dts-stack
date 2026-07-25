import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const pageUrl = new URL("./WarehousePlanLedgerPage.tsx", import.meta.url);
const page = existsSync(pageUrl) ? readFileSync(pageUrl, "utf8") : "";

test("warehouse plan ledger reads only the canonical plan aggregate", () => {
	assert.equal(existsSync(pageUrl), true, "warehouse plan ledger page is missing");
	assert.match(page, /listWarehousePlans/);
	assert.match(page, /getWarehousePlanStageProjection/);
	assert.match(page, /buildWarehousePlanRoute\(plan\.id,\s*"baseline"\)/);
	assert.doesNotMatch(page, /WarehousePlanHeaderEditor/);
	assert.doesNotMatch(page, /listModelingPlans|getModelingPlan|updateModelingPlan|deleteModelingPlan/);
	assert.doesNotMatch(page, /"\/modeling\/plans"|`\/modeling\/plans`/);
});

test("ledger filters default to active plans and keep archived plans explicitly discoverable", () => {
	assert.match(page, /useState<WarehousePlanLedgerLifecycleFilter>\("ACTIVE"\)/);
	assert.match(page, /filterWarehousePlans/);
	assert.match(page, /关键字/);
	assert.match(page, /生命周期/);
	assert.match(page, /负责人/);
	assert.match(page, /已归档/);
});

test("stage projection failure keeps rows visible as unknown evidence", () => {
	assert.match(page, /Promise\.allSettled/);
	assert.match(page, /projectionFailures/);
	assert.match(page, /证据未知/);
	assert.match(page, /首要阻塞/);
	assert.doesNotMatch(page, /filter\([^\n]*projection|plans\.filter\([^\n]*projection/);
});

test("ledger exposes one page primary action and lifecycle-aware row actions", () => {
	assert.match(page, /data-testid="warehouse-plan-ledger-primary-action"/);
	assert.match(page, /\/modeling\/workbench\?create=1/);
	for (const label of ["查看", "编辑", "归档"]) assert.match(page, new RegExp(label));
	assert.match(page, /canEditWarehousePlanHeader/);
	assert.match(page, /canArchiveWarehousePlan/);
	assert.match(page, /hasWarehousePlanCreateAccess/);
	assert.doesNotMatch(page, /永久删除|批量归档|复制规划/);
});

test("ledger edit opens the plan content workspace instead of the header-only editor", () => {
	assert.match(page, /navigate\(buildWarehousePlanRoute\(plan\.id,\s*"baseline"\)\)/);
	assert.match(page, /buildWarehousePlanRoute\(plan\.id,\s*"overview",\s*\{\s*mode:\s*"view"\s*\}\)/);
	assert.doesNotMatch(page, /openEditor\(plan\)/);
});

test("archive uses confirmation, CAS conflict reload and a second explicit confirmation", () => {
	assert.match(page, /archiveWarehousePlan/);
	assert.match(page, /Modal\.confirm/);
	assert.match(page, /resolveWarehousePlanConflictVersion/);
	assert.match(page, /getWarehousePlan/);
	assert.match(page, /规划已变化，请重新确认归档/);
	assert.match(page, /createLatestRequestGuard/);
	assert.match(page, /if \(!isCurrent\(\)\) return/);
});

test("a server-side object permission denial makes that plan read-only for the current page session", () => {
	assert.match(page, /writeDeniedPlanIds/);
	assert.match(page, /response\?\.status === 403/);
	assert.match(page, /只读/);
});

test("ledger distinguishes initial error, retained-data retry and empty state", () => {
	assert.match(page, /warehouse-plan-ledger-load-error/);
	assert.match(page, /warehouse-plan-ledger-retry-warning/);
	assert.match(page, /warehouse-plan-ledger-empty/);
	assert.match(page, /已有规划仍保留/);
	assert.match(page, /暂无建设规划/);
});

test("late list snapshots merge monotonically with successful local mutations", () => {
	assert.match(page, /mergeWarehousePlanHeadersMonotonic/);
	assert.match(page, /setPlans\(\(current\)/);
});

test("ledger constrains wide tables inside the page for Chrome 95 and narrow viewports", () => {
	assert.match(page, /scroll=\{\{ x:\s*1120 \}\}/);
	assert.match(page, /overflow-hidden/);
	assert.match(page, /flex-col[\s\S]{0,120}md:flex-row|grid-cols-1[\s\S]{0,120}md:grid-cols/);
});
