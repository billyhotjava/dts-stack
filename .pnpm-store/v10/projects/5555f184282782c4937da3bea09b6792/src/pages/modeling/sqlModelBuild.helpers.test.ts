import assert from "node:assert/strict";
import test from "node:test";
import {
	buildCommandHint,
	buildOperationCompletedMessage,
	buildOperationQueuedMessage,
	buildOperationSkippedMessage,
	buildReleaseSelector,
	inferBuildOperationFromCommand,
	createPendingBuildSummary,
	createFailedBuildSummary,
	matchesTriggeredBuildSummary,
	describeBuildSummary,
} from "./sqlModelBuild.helpers.ts";

// ── buildCommandHint ─────────────────────────────────────────────────

test("buildCommandHint returns bare dbt command when no selector", () => {
	assert.equal(buildCommandHint("compile"), "dbt compile");
	assert.equal(buildCommandHint("test"), "dbt test");
});

test("buildCommandHint appends --select when selector provided", () => {
	assert.equal(buildCommandHint("compile", "tag:project"), "dbt compile --select tag:project");
});

test("buildCommandHint ignores whitespace-only selector", () => {
	assert.equal(buildCommandHint("build", "  "), "dbt build");
});

// ── buildReleaseSelector ─────────────────────────────────────────────

test("buildReleaseSelector returns all for empty input", () => {
	assert.equal(buildReleaseSelector(""), "all");
	assert.equal(buildReleaseSelector(undefined), "all");
});

test("buildReleaseSelector prepends + for tag/model/source selectors", () => {
	assert.equal(buildReleaseSelector("tag:project"), "+tag:project");
	assert.equal(buildReleaseSelector("model:dim_node"), "+model:dim_node");
	assert.equal(buildReleaseSelector("source:erp"), "+source:erp");
});

test("buildReleaseSelector keeps existing + prefix", () => {
	assert.equal(buildReleaseSelector("+tag:project"), "+tag:project");
});

test("buildReleaseSelector passes through plain selector", () => {
	assert.equal(buildReleaseSelector("dim_node_type"), "dim_node_type");
});

// ── inferBuildOperationFromCommand ───────────────────────────────────

test("inferBuildOperationFromCommand detects compile", () => {
	assert.equal(inferBuildOperationFromCommand("dbt compile --select tag:x"), "compile");
	assert.equal(inferBuildOperationFromCommand("compile"), "compile");
});

test("inferBuildOperationFromCommand detects test", () => {
	assert.equal(inferBuildOperationFromCommand("dbt test"), "test");
});

test("inferBuildOperationFromCommand detects run", () => {
	assert.equal(inferBuildOperationFromCommand("dbt run --select m1"), "run");
});

test("inferBuildOperationFromCommand returns null for empty", () => {
	assert.equal(inferBuildOperationFromCommand(""), null);
	assert.equal(inferBuildOperationFromCommand(undefined), null);
});

test("inferBuildOperationFromCommand returns null for unknown command", () => {
	assert.equal(inferBuildOperationFromCommand("dbt seed"), null);
});

// ── createPendingBuildSummary ────────────────────────────────────────

test("createPendingBuildSummary creates RUNNING summary", () => {
	const summary = createPendingBuildSummary("compile", "tag:project");
	assert.equal(summary.present, true);
	assert.equal(summary.status, "RUNNING");
	assert.equal(summary.command, "dbt compile --select tag:project");
	assert.equal(summary.total, 0);
	assert.equal(summary.failed, 0);
	assert.deepEqual(summary.failures, []);
});

// ── createFailedBuildSummary ─────────────────────────────────────────

test("createFailedBuildSummary creates FAILED summary with message", () => {
	const summary = createFailedBuildSummary("test", "m1", "连接超时");
	assert.equal(summary.status, "FAILED");
	assert.equal(summary.failed, 1);
	assert.equal(summary.failures?.[0]?.message, "连接超时");
});

test("createFailedBuildSummary uses default message when omitted", () => {
	const summary = createFailedBuildSummary("build");
	assert.equal(summary.failures?.[0]?.message, "dbt build 失败，请查看执行日志");
});

// ── matchesTriggeredBuildSummary ─────────────────────────────────────

test("matchesTriggeredBuildSummary returns true for matching new build", () => {
	const result = matchesTriggeredBuildSummary(
		{
			present: true,
			command: "dbt compile --select tag:project",
			invocationId: "inv-2",
			generatedAt: "2026-03-29T10:00:00Z",
		},
		{
			operation: "compile",
			selector: "tag:project",
			baselineInvocationId: "inv-1",
			baselineGeneratedAt: "2026-03-29T09:00:00Z",
		},
	);
	assert.equal(result, true);
});

test("matchesTriggeredBuildSummary returns false for non-present summary", () => {
	const result = matchesTriggeredBuildSummary(
		{ present: false, command: "dbt compile" },
		{ operation: "compile" },
	);
	assert.equal(result, false);
});

test("matchesTriggeredBuildSummary returns false for mismatched operation", () => {
	const result = matchesTriggeredBuildSummary(
		{ present: true, command: "dbt test", invocationId: "inv-2" },
		{ operation: "compile", baselineInvocationId: "inv-1" },
	);
	assert.equal(result, false);
});

test("matchesTriggeredBuildSummary matches when no selector constraint", () => {
	const result = matchesTriggeredBuildSummary(
		{ present: true, command: "dbt compile", invocationId: "inv-2" },
		{ operation: "compile", baselineInvocationId: "inv-1" },
	);
	assert.equal(result, true);
});

// ── describeBuildSummary ─────────────────────────────────────────────

test("describeBuildSummary returns success for passing build", () => {
	const result = describeBuildSummary(
		{ status: "SUCCESS", failures: [] },
		{ operationLabel: "编译", fallbackMessage: "未执行" },
	);
	assert.equal(result.type, "success");
	assert.ok(result.message.includes("通过"));
});

test("describeBuildSummary returns error for failed build", () => {
	const result = describeBuildSummary(
		{ status: "FAILED" },
		{ operationLabel: "测试", fallbackMessage: "未执行" },
	);
	assert.equal(result.type, "error");
	assert.ok(result.message.includes("失败"));
});

test("describeBuildSummary returns info for running build", () => {
	const result = describeBuildSummary(
		{ status: "RUNNING" },
		{ operationLabel: "编译", fallbackMessage: "未执行" },
	);
	assert.equal(result.type, "info");
	assert.ok(result.message.includes("进行中"));
});

test("describeBuildSummary returns warning for skipped build", () => {
	const result = describeBuildSummary(
		{ status: "SKIPPED" },
		{ operationLabel: "测试", fallbackMessage: "未执行" },
	);
	assert.equal(result.type, "warning");
});

test("describeBuildSummary returns fallback for null summary", () => {
	const result = describeBuildSummary(null, {
		operationLabel: "编译",
		fallbackMessage: "未执行",
	});
	assert.equal(result.type, "info");
	assert.equal(result.message, "未执行");
});

test("describeBuildSummary returns info for unknown status", () => {
	const result = describeBuildSummary(
		{ status: "PENDING" },
		{ operationLabel: "编译", fallbackMessage: "未执行" },
	);
	assert.equal(result.type, "info");
	assert.ok(result.message.includes("尚未就绪"));
});

test("buildOperationQueuedMessage explains docs as artifact generation", () => {
	assert.equal(buildOperationQueuedMessage("docs"), "文档产物生成任务已提交，正在等待结果");
});

test("buildOperationCompletedMessage explains docs does not provide inline browsing", () => {
	assert.equal(buildOperationCompletedMessage("docs"), "文档产物已生成，当前页面不提供内嵌浏览");
});

test("buildOperationSkippedMessage uses docs-specific wording", () => {
	assert.equal(buildOperationSkippedMessage("docs"), "文档产物生成返回 SKIPPED，请重新执行");
});
