import assert from "node:assert/strict";
import test from "node:test";
import {
	createAsyncRunInitialProgress,
	createAsyncRunRetryProgress,
	createAsyncRunTimeoutProgress,
	mapExecutionToProgressView,
	resolveAsyncRunPollHint,
	resolveCreatedTaskId,
} from "./transformCreateAsyncRun.helpers";

test("resolveCreatedTaskId reads supported payload shapes and rejects invalid ids", () => {
	assert.equal(resolveCreatedTaskId({ task: { id: 42 } }), 42);
	assert.equal(resolveCreatedTaskId({ taskId: 21 }), 21);
	assert.equal(resolveCreatedTaskId({ id: 7 }), 7);
	assert.equal(resolveCreatedTaskId({ task: { taskId: 9 } }), 9);
	assert.equal(resolveCreatedTaskId({ task: { id: 0 } }), undefined);
	assert.equal(resolveCreatedTaskId({ taskId: "bad" }), undefined);
});

test("createAsyncRunInitialProgress returns the submitted state", () => {
	assert.deepEqual(createAsyncRunInitialProgress(), {
		progress: 10,
		status: "active",
		stage: "任务已提交",
		detail: "正在后台触发执行。",
		terminal: false,
	});
});

test("mapExecutionToProgressView marks success as terminal success", () => {
	assert.deepEqual(
		mapExecutionToProgressView(
			{
				status: "SUCCESS",
				executionId: 1001,
			} as any,
			18_000
		),
		{
			progress: 100,
			status: "success",
			stage: "执行成功",
			detail: "入湖任务已执行完成。",
			terminal: true,
		}
	);
});

test("mapExecutionToProgressView marks failures as terminal exception with message fallback", () => {
	assert.deepEqual(
		mapExecutionToProgressView(
			{
				status: "failed",
				errorMessage: "reader schema mismatch",
			} as any,
			22_000
		),
		{
			progress: 100,
			status: "exception",
			stage: "执行失败",
			detail: "reader schema mismatch",
			terminal: true,
		}
	);
});

test("mapExecutionToProgressView keeps waiting state before the first execution record arrives", () => {
	assert.deepEqual(mapExecutionToProgressView(null, 15_000), {
		progress: 30,
		status: "active",
		stage: "等待执行记录",
		detail: "任务已提交，系统正在准备 DAG 和作业参数。",
		terminal: false,
	});
});

test("createAsyncRunTimeoutProgress and retry progress keep user-facing fallback messages stable", () => {
	assert.deepEqual(createAsyncRunTimeoutProgress(), {
		progress: 100,
		status: "exception",
		stage: "状态同步超时",
		detail: "超出等待时间，请进入任务详情页继续查看执行状态。",
		terminal: true,
	});

	assert.deepEqual(
		createAsyncRunRetryProgress({
			progress: 55,
			status: "active",
			stage: "准备执行",
			detail: "上一条消息",
			terminal: false,
		}),
		{
			progress: 55,
			status: "active",
			stage: "准备执行",
			detail: "状态同步中，稍后自动重试。",
			terminal: false,
		}
	);
});

test("resolveAsyncRunPollHint prefers execution hint and ignores non-finite values", () => {
	assert.equal(resolveAsyncRunPollHint({ execution: { pollIntervalMs: 5000 } }), 5000);
	assert.equal(resolveAsyncRunPollHint({ pollIntervalMs: 3000 }), 3000);
	assert.equal(resolveAsyncRunPollHint({ execution: { pollIntervalMs: "bad" } }), undefined);
});
