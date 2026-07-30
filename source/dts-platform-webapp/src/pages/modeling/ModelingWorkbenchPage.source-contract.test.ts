import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const entry = readFileSync(new URL("./ModelingWorkbenchPage.tsx", import.meta.url), "utf8");
const shellUrl = new URL("./ModelingWorkspaceShell.tsx", import.meta.url);
const shell = existsSync(shellUrl) ? readFileSync(shellUrl, "utf8") : "";
const createModal = readFileSync(new URL("./WarehousePlanCreateModal.tsx", import.meta.url), "utf8");
const createSurface = `${entry}\n${createModal}`;
const frame = readFileSync(new URL("./semantic-workspace/SemanticWorkspaceFrame.tsx", import.meta.url), "utf8");
const api = readFileSync(new URL("../../api/warehousePlanApi.ts", import.meta.url), "utf8");

test("workbench mounts one compact seven-module shell without moving business ownership", () => {
	assert.equal(existsSync(shellUrl), true);
	assert.match(entry, /ModelingWorkspaceShell/);
	assert.match(entry, /parseModelingWorkspaceRouteState/);
	assert.match(entry, /updateModelingWorkspaceSearch/);
	assert.match(shell, /data-testid="modeling-workspace-shell"/);
	assert.match(shell, /role="tablist"/);
	assert.match(shell, /aria-selected/);
	for (const label of ["首页", "数仓规划", "数据标准", "维度建模", "数据指标", "通用工具", "关系图"]) {
		assert.match(shell, new RegExp(label));
	}
	assert.doesNotMatch(shell, /listWarehousePlans|createWarehousePlan|localStorage|sessionStorage/);
	assert.doesNotMatch(entry, /linear-gradient/);
});

test("modeling workbench is a canonical warehouse planning surface instead of a redirect", () => {
	assert.match(entry, /listWarehousePlans/);
	assert.match(entry, /getWarehousePlanStageProjection/);
	assert.match(entry, /createWarehousePlan/);
	assert.doesNotMatch(entry, /<Navigate|resolveModelingJourneyContext|modelingStagePath/);
	assert.doesNotMatch(entry, /resolveDataProductJourneyStageStates|JourneyContextBar/);
});

test("workbench routes view and edit actions to the canonical plan overview", () => {
	assert.doesNotMatch(entry, /WarehousePlanHeaderEditor/);
	assert.match(shell, /全部规划/);
	assert.match(entry, /onOpenAllPlans=\{\(\) => navigate\("\/modeling\/plans"\)\}/);
	assert.match(entry, /编辑规划/);
	assert.match(entry, /buildWarehousePlanRoute\(selectedPlan\.id,\s*"overview",\s*\{\s*mode:\s*"view"\s*\}\)/);
	assert.match(entry, /buildWarehousePlanRoute\(selectedPlan\.id,\s*"overview",\s*\{\s*mode:\s*"edit"\s*\}\)/);
	assert.match(entry, /canEditWarehousePlanHeader/);
	assert.doesNotMatch(entry, /replaceWarehousePlanHeader/);
});

test("ledger create handoff opens the existing create flow without creating a second form", () => {
	assert.match(entry, /searchParams\.get\("create"\)/);
	assert.match(entry, /requestedCreate/);
	assert.match(entry, /createRouteHandledRef/);
	assert.match(entry, /openCreate\(\)/);
	assert.doesNotMatch(entry, /WarehousePlanLedgerPage/);
});

test("empty and active plans each expose one unambiguous primary action", () => {
	assert.match(entry, /data-testid="warehouse-plan-empty-primary-action"/);
	assert.match(entry, /data-testid="warehouse-plan-next-action"/);
	assert.match(entry, /description="暂无建设规划"/);
	assert.doesNotMatch(entry, /Data construction|围绕一个建设计划|先建立一个数据建设计划|九站证据链/);
	assert.match(entry, /StageProjection/);
	assert.match(entry, /primaryBlocker/);
	assert.match(entry, /nextAction/);
});

test("new planning uses two onboarding modes without creating two plan types", () => {
	assert.match(createSurface, /从业务目标开始/);
	assert.match(createSurface, /从现有数据开始/);
	assert.match(createSurface, /BUSINESS_FIRST/);
	assert.match(createSurface, /ASSET_FIRST/);
	assert.doesNotMatch(createSurface, /DATA_FIRST/);
	assert.doesNotMatch(createSurface, /businessPlan|dataPlan|planType/);
});

test("warehouse plan create flow is server coded, idempotent, dual-start and actor read-only", () => {
	assert.doesNotMatch(api, /CreateWarehousePlanInput[\s\S]{0,500}\bcode\s*:/);
	assert.match(api, /idempotencyKey:\s*string/);
	assert.match(api, /initialSourceRefs\??:\s*WarehousePlanSourceRef\[\]/);
	assert.doesNotMatch(entry, /warehouse-\$\{Date\.now/);
	assert.match(entry, /createWarehousePlanIdempotencyKey/);
	assert.match(createModal, /data-testid="warehouse-plan-current-owner"/);
	assert.match(createModal, /data-testid="warehouse-plan-initial-sources"/);
	assert.match(entry, /loadRequestedWarehousePlan\(\s*requestedPlanId,\s*getWarehousePlan/);
	assert.match(entry, /data-testid="warehouse-plan-requested-plan-recovery"/);
	assert.match(entry, /hasWarehousePlanCreateAccess/);
	assert.doesNotMatch(entry, /useCatalogManageAccess/);
	assert.match(entry, /navigate\(created\.nextAction\)/);
	assert.match(entry, /createLatestRequestGuard/);
	assert.match(entry, /if \(!isCurrent\(\)\) return/);
});

test("lifecycle labels match the backend contract exactly", () => {
	for (const status of [
		"DRAFT",
		"BASELINE_READY",
		"DESIGNING",
		"VALIDATING",
		"READY_TO_PUBLISH",
		"PUBLISHED",
		"ARCHIVED",
	]) {
		assert.match(api, new RegExp(`\\|?\\s*"${status}"`));
		assert.match(entry, new RegExp(`${status}:`));
	}
	assert.doesNotMatch(api, /\|\s*"MODELING"|\|\s*"IMPLEMENTING"/);
});

test("create modal protects its active request session and cannot close while submitting", () => {
	assert.match(entry, /const createRequestGuard = useMemo\(\(\) => createLatestRequestGuard\(\), \[\]\)/);
	assert.match(entry, /const isCurrentCreate = createRequestGuard\.begin\(\)/);
	assert.match(entry, /if \(!isCurrentCreate\(\)\) return/);
	assert.match(createModal, /closable=!\{creating\}|closable=\{!creating\}/);
	assert.match(createModal, /maskClosable=\{!creating\}/);
	assert.match(createModal, /keyboard=\{!creating\}/);
	assert.match(createModal, /cancelButtonProps=\{\{ disabled: creating \}\}/);
});

test("exact plan restoration degrades list failure separately and retries only the list", () => {
	assert.match(entry, /useState\(false\).*planListFailed|\[planListFailed, setPlanListFailed\] = useState\(false\)/);
	assert.match(entry, /setPlanListFailed\(restored\.listFailed\)/);
	assert.match(entry, /const retryPlanList = useCallback/);
	assert.match(entry, /data-testid="warehouse-plan-list-recovery"/);
	assert.match(entry, /mergeWarehousePlanLists/);
	assert.match(entry, /planListRetryGuard\.invalidate\(\);\s*setRetryingPlanList\(false\)/);
});

test("failed exact-plan restoration stays recoverable instead of falling through to the create empty state", () => {
	assert.match(entry, /指定的建设计划不可用/);
	assert.match(entry, /不存在、无权访问或暂时网络异常/);
	assert.match(entry, /onClick=\{\(\) => void loadPlans\(\)\}[\s\S]{0,120}重新加载指定计划/);
	assert.match(entry, /plans\.length === 0 && !requestedPlanFailed/);
});

test("URL plan selection waits for the exact plan read before projection", () => {
	assert.match(entry, /const \[selectedPlanId, setSelectedPlanId\] = useState\(""\)/);
});

test("asset-first inputs enforce the same identity and length limits as the backend", () => {
	assert.match(createModal, /validateWarehousePlanInitialSources/);
	assert.match(createModal, /max:\s*256/);
	assert.match(createModal, /maxLength=\{256\}/);
	assert.match(createModal, /max:\s*128/);
	assert.match(createModal, /maxLength=\{128\}/);
});

test("idempotency conflict copy stays in customer language", () => {
	assert.doesNotMatch(createSurface, /请求标识|幂等/);
	assert.match(createModal, /作为新计划重新提交/);
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
	assert.match(frame, /description\?: string/);
	assert.doesNotMatch(frame, /index < activeIndex/);
	assert.doesNotMatch(frame, /ModelingConceptCards/);
});
