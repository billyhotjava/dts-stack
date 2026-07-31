import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./AccessPlanDetailPage.tsx", import.meta.url), "utf8");

test("access plan detail loads one task and its latest execution without list fan-out", () => {
	assert.match(SOURCE, /ingestionTaskAPI\.getTask\(taskId\)/);
	assert.match(SOURCE, /ingestionTaskAPI\.getLatestExecution\(taskId\)/);
	assert.doesNotMatch(SOURCE, /getTasks\(|dataSourcesService\.list\(/);
});

test("access plan detail exposes the agreed operational tabs on real contracts", () => {
	for (const label of ["概览", "运行历史", "结构漂移", "密级准入", "变更记录"]) {
		assert.match(SOURCE, new RegExp(`label:\\s*"${label}"`));
	}
	assert.match(SOURCE, /label: inferAccessKind\(task\) === "file" \? "文件预检" : "异常数据"/);
	assert.match(SOURCE, /<ExecutionHistoryTable taskId=\{taskId\}/);
	assert.match(SOURCE, /<AccessStructureDriftPanel task=\{task\}/);
	assert.match(SOURCE, /<AccessQualityPanel task=\{task\} latestExecution=\{latestExecution\}/);
	assert.match(SOURCE, /<TaskAdmissionBasis task=\{task\}/);
	assert.match(SOURCE, /ingestionTaskAPI\.getChangeLogs\(\{/);
	assert.match(SOURCE, /taskId,/);
});

test("access plan detail preserves admission, execution, DAG rebuild and guarded rollback operations", () => {
	assert.match(SOURCE, /runAccessPlanOperation\("admit", operationTaskId\)/);
	assert.match(SOURCE, /runAccessPlanOperation\("execute", operationTaskId\)/);
	assert.match(SOURCE, /runAccessPlanOperation\("rebuildDag", operationTaskId\)/);
	assert.match(SOURCE, /<RollbackImpactModal/);
	assert.match(SOURCE, /确认密级并准入/);
	assert.match(SOURCE, /立即执行/);
	assert.match(SOURCE, /setRollbackOpen\(false\);\s*setRollbackRequest\(null\);/);
	assert.match(SOURCE, /rollbackRequest\?\.taskId === taskId/);
	assert.match(SOURCE, /key=\{`access-plan-rollback-\$\{taskId\}`\}/);
});

test("access plan detail requires an explicit execution confirmation with a safe task summary", () => {
	assert.match(SOURCE, /title:\s*"确认立即执行"/);
	assert.match(SOURCE, /okText:\s*"确认并立即执行"/);
	assert.match(SOURCE, /label="任务"/);
	assert.match(SOURCE, /label="目标表"/);
	assert.match(SOURCE, /label="写入策略"/);
	assert.match(SOURCE, /onOk:\s*async \(\) =>/);
	assert.match(SOURCE, /runAccessPlanOperation\("execute", operationTaskId\)/);
	assert.doesNotMatch(SOURCE, /toast\.error\([^)]*(?:error|response|message)\./i);
});

test("access plan detail ignores stale task and change-log responses", () => {
	assert.match(SOURCE, /detailRequestIdRef\.current !== requestId/);
	assert.match(SOURCE, /changesRequestIdRef\.current !== requestId/);
	assert.match(SOURCE, /detailRequestIdRef\.current \+= 1/);
	assert.match(SOURCE, /routeTaskIdRef\.current !== operationTaskId/);
	assert.match(SOURCE, /taskBelongsToRoute/);
});

test("access plan detail routes legacy and explicit edits to the same-kind wizard", () => {
	assert.match(SOURCE, /searchParams\.get\("mode"\) === "edit"/);
	assert.match(SOURCE, /inferAccessKind\(task\)/);
	assert.match(SOURCE, /access\/new\?kind=\$\{inferAccessKind\(task\)\}&editId=/);
});

test("access plan detail shows real task revision with an honest legacy fallback and keeps seal version separate", () => {
	assert.match(SOURCE, /任务版本/);
	assert.match(SOURCE, /task\.revisionNumber/);
	assert.match(SOURCE, /task\.revisionState/);
	assert.match(SOURCE, /task\.defaultPolicyVersion/);
	assert.match(SOURCE, /task\.effectiveConfigChecksum/);
	assert.match(SOURCE, /未版本化（存量任务）/);
	assert.match(SOURCE, /密级封存版本/);
	assert.doesNotMatch(SOURCE, /activeRevision|draftRevision/);
});

test("access plan detail does not render raw or sensitive task configuration", () => {
	assert.doesNotMatch(SOURCE, /JSON\.stringify/);
	assert.doesNotMatch(SOURCE, /password|secret|token|authorization/i);
	assert.doesNotMatch(SOURCE, /task\.sourceConfig|task\.destinationConfig/);
});

test("access plan detail fails closed for invalid identifiers and API failures", () => {
	assert.match(SOURCE, /Number\.isSafeInteger\(parsedTaskId\)/);
	assert.match(SOURCE, /setTask\(null\)/);
	assert.match(SOURCE, /setLoadError\("接入任务加载失败，请稍后重试。"\)/);
	assert.match(SOURCE, /type="error"/);
});
