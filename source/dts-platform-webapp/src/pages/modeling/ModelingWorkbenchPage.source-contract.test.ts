import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const entry = readFileSync(new URL("./ModelingWorkbenchPage.tsx", import.meta.url), "utf8");
const frame = readFileSync(new URL("./semantic-workspace/SemanticWorkspaceFrame.tsx", import.meta.url), "utf8");
const api = readFileSync(new URL("../../api/warehousePlanApi.ts", import.meta.url), "utf8");

test("modeling workbench is a canonical warehouse planning surface instead of a redirect", () => {
	assert.match(entry, /listWarehousePlans/);
	assert.match(entry, /getWarehousePlanStageProjection/);
	assert.match(entry, /createWarehousePlan/);
	assert.doesNotMatch(entry, /<Navigate|resolveModelingJourneyContext|modelingStagePath/);
	assert.doesNotMatch(entry, /resolveDataProductJourneyStageStates|JourneyContextBar/);
});

test("empty and active plans each expose one unambiguous primary action", () => {
	assert.match(entry, /data-testid="warehouse-plan-empty-primary-action"/);
	assert.match(entry, /data-testid="warehouse-plan-next-action"/);
	assert.match(entry, /StageProjection/);
	assert.match(entry, /primaryBlocker/);
	assert.match(entry, /nextAction/);
});

test("new planning uses two onboarding modes without creating two plan types", () => {
	assert.match(entry, /从业务目标开始/);
	assert.match(entry, /从现有数据开始/);
	assert.match(entry, /BUSINESS_FIRST/);
	assert.match(entry, /ASSET_FIRST/);
	assert.doesNotMatch(entry, /DATA_FIRST/);
	assert.doesNotMatch(entry, /businessPlan|dataPlan|planType/);
});

test("warehouse plan create flow is server coded, idempotent, dual-start and actor read-only", () => {
	assert.doesNotMatch(api, /CreateWarehousePlanInput[\s\S]{0,500}\bcode\s*:/);
	assert.match(api, /idempotencyKey:\s*string/);
	assert.match(api, /initialSourceRefs\??:\s*WarehousePlanSourceRef\[\]/);
	assert.doesNotMatch(entry, /warehouse-\$\{Date\.now/);
	assert.match(entry, /createWarehousePlanIdempotencyKey/);
	assert.match(entry, /data-testid="warehouse-plan-current-owner"/);
	assert.match(entry, /data-testid="warehouse-plan-initial-sources"/);
	assert.match(entry, /loadRequestedWarehousePlan\(\s*requestedPlanId,\s*getWarehousePlan/);
	assert.match(entry, /data-testid="warehouse-plan-requested-plan-recovery"/);
	assert.match(entry, /hasWarehousePlanCreateAccess/);
	assert.doesNotMatch(entry, /useCatalogManageAccess/);
	assert.match(entry, /navigate\(created\.nextAction\)/);
	assert.match(entry, /createLatestRequestGuard/);
	assert.match(entry, /if \(!isCurrent\(\)\) return/);
});

test("the workbench renders the server-owned nine-stage projection as read-only evidence", () => {
	assert.match(entry, /WAREHOUSE_STAGE_ORDER/);
	assert.match(entry, /data-testid="warehouse-plan-nine-stage-track"/);
	assert.match(entry, /计划负责人/);
	assert.match(entry, /生命周期/);
	assert.doesNotMatch(entry, /markComplete|setStageComplete|completeStage/);
});

test("semantic workspace exposes the generic four-stage journey", () => {
	for (const label of ["范围与来源", "逻辑模型", "实现与验证", "发布与运行"]) {
		assert.ok(frame.includes(label));
	}
	assert.doesNotMatch(frame, /index < activeIndex/);
	assert.doesNotMatch(frame, /ModelingConceptCards/);
});
