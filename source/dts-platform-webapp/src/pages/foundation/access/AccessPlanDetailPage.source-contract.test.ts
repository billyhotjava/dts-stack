import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./AccessPlanDetailPage.tsx", import.meta.url), "utf8");

test("access plan detail loads one task and its latest execution without list fan-out", () => {
	assert.match(SOURCE, /ingestionTaskAPI\.getTask\(taskId\)/);
	assert.match(SOURCE, /ingestionTaskAPI\.getLatestExecution\(taskId\)/);
	assert.match(SOURCE, /ingestionTaskAPI\.getTaskRevisions\(taskId\)/);
	assert.doesNotMatch(SOURCE, /getTasks\(|dataSourcesService\.list\(/);
});

test("access plan detail exposes the agreed operational tabs on real contracts", () => {
	for (const label of ["概览", "运行历史", "密级准入", "变更记录"]) {
		assert.match(SOURCE, new RegExp(`label:\\s*"${label}"`));
	}
	assert.match(SOURCE, /label: inferAccessKind\(task\) === "file" \? "文件预检" : "异常数据"/);
	assert.match(SOURCE, /<ExecutionHistoryTable taskId=\{taskId\}/);
	assert.match(SOURCE, /<AccessQualityPanel[\s\S]{0,180}task=\{task\}[\s\S]{0,180}onTaskChanged=/);
	assert.match(SOURCE, /<TaskAdmissionBasis\s+task=\{admissionTask \|\| task\}\s*\/>/);
	assert.match(SOURCE, /ingestionTaskAPI\.getChangeLogs\(\{/);
	assert.match(SOURCE, /taskId,/);
	assert.doesNotMatch(SOURCE, /结构漂移|AccessStructureDriftPanel|key:\s*"drift"/);
});

test("access plan detail preserves admission and execution while exposing evidence-preserving plan deletion", () => {
	assert.match(SOURCE, /runAccessPlanOperation\("admit", operationTaskId, ingestionTaskAPI\)/);
	assert.match(SOURCE, /runAccessPlanOperation\("execute", operationTaskId, ingestionTaskAPI\)/);
	assert.equal(
		SOURCE.match(/if \(!acquireOwnedSingleFlight\(operationLockRef, operationOwner\)\) return;/g)?.length,
		3,
	);
	assert.equal(
		SOURCE.match(/onCancel:\s*\(\) => releaseOwnedSingleFlight\(operationLockRef, operationOwner\)/g)?.length,
		2,
	);
	assert.match(SOURCE, /resetOwnedSingleFlight\(operationLockRef\)/);
	assert.match(SOURCE, /!ownsSingleFlight\(operationLockRef, operationOwner\)/);
	assert.match(SOURCE, /const released = releaseOwnedSingleFlight\(operationLockRef, operationOwner\)/);
	assert.match(SOURCE, /runAccessPlanOperation\("delete", operationTaskId, ingestionTaskAPI\)/);
	assert.match(SOURCE, /准入草稿/);
	assert.match(SOURCE, /立即执行/);
	assert.match(SOURCE, /删除计划/);
	assert.match(SOURCE, /title:\s*"确认删除接入计划"/);
	assert.match(SOURCE, /ODS 数据不会被清空/);
	assert.match(SOURCE, /运行历史、变更记录和审计证据仍会保留/);
	assert.match(SOURCE, /router\.push\("\/foundation\/data-sources"\)/);
	assert.doesNotMatch(SOURCE, /更多操作|重建 DAG|数据回退 Level|RollbackImpactModal|rebuildDag/);
});

test("access plan detail requires an explicit execution confirmation with a safe task summary", () => {
	assert.match(SOURCE, /title:\s*"确认立即执行"/);
	assert.match(SOURCE, /okText:\s*"确认并立即执行"/);
	assert.match(SOURCE, /label="任务"/);
	assert.match(SOURCE, /label="目标表"/);
	assert.match(SOURCE, /label="写入策略"/);
	assert.match(SOURCE, /onOk:\s*async \(\) =>/);
	assert.match(SOURCE, /runAccessPlanOperation\("execute", operationTaskId, ingestionTaskAPI\)/);
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

test("access plan detail separates the active revision from a pending draft and keeps seal version separate", () => {
	assert.match(SOURCE, /任务版本/);
	assert.match(SOURCE, /task\.revisionNumber/);
	assert.match(SOURCE, /task\.revisionState/);
	assert.match(SOURCE, /activeRevisionNumber/);
	assert.match(SOURCE, /draftRevisionNumber/);
	assert.match(SOURCE, /resolveAccessRevisionView\(task, revisions, revisionsError\)/);
	assert.match(SOURCE, /待准入草稿/);
	assert.match(SOURCE, /执行生效版本/);
	assert.match(SOURCE, /task\.defaultPolicyVersion/);
	assert.match(SOURCE, /task\.effectiveConfigChecksum/);
	assert.match(SOURCE, /未版本化（存量任务）/);
	assert.match(SOURCE, /密级封存版本/);
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
