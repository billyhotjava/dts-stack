import assert from "node:assert/strict";
import test from "node:test";
import {
	createFailedBuildSummary,
	createPendingBuildSummary,
	describeBuildSummary,
	inferBuildOperationFromCommand,
	matchesTriggeredBuildSummary,
} from "./sqlModelBuild.helpers";

test("matchesTriggeredBuildSummary accepts newer matching compile summary", () => {
	const matched = matchesTriggeredBuildSummary(
		{
			present: true,
			invocationId: "inv-2",
			generatedAt: "2026-03-16T02:30:00Z",
			command: "dbt compile --target dev --select tag:project-management",
			status: "SUCCESS",
		},
		{
			operation: "compile",
			selector: "tag:project-management",
			baselineGeneratedAt: "2026-03-16T02:20:00Z",
			baselineInvocationId: "inv-1",
		},
	);

	assert.equal(matched, true);
});

test("matchesTriggeredBuildSummary rejects unchanged summary", () => {
	const matched = matchesTriggeredBuildSummary(
		{
			present: true,
			invocationId: "inv-1",
			generatedAt: "2026-03-16T02:20:00Z",
			command: "dbt compile --target dev --select tag:project-management",
			status: "SKIPPED",
		},
		{
			operation: "compile",
			selector: "tag:project-management",
			baselineGeneratedAt: "2026-03-16T02:20:00Z",
			baselineInvocationId: "inv-1",
		},
	);

	assert.equal(matched, false);
});

test("describeBuildSummary returns info for pending build and warning for skipped build", () => {
	const pending = describeBuildSummary(createPendingBuildSummary("compile", "tag:project-management"), {
		operationLabel: "编译",
		fallbackMessage: "请先执行编译操作",
	});
	const skipped = describeBuildSummary(
		{
			present: true,
			command: "dbt compile --target dev --select tag:project-management",
			status: "SKIPPED",
		},
		{
			operationLabel: "编译",
			fallbackMessage: "请先执行编译操作",
		},
	);

	assert.deepEqual(pending, {
		type: "info",
		message: "编译进行中，请等待 Airflow 任务完成",
	});
	assert.deepEqual(skipped, {
		type: "warning",
		message: "最近一次编译结果为 SKIPPED，不能作为发布依据，请重新执行",
	});
});

test("createFailedBuildSummary synthesizes a visible failure entry", () => {
	const summary = createFailedBuildSummary("test", "model:demo", "Airflow 任务失败");

	assert.equal(summary.status, "FAILED");
	assert.equal(summary.failed, 1);
	assert.equal(summary.failures?.[0]?.message, "Airflow 任务失败");
});

test("inferBuildOperationFromCommand recognizes full dbt commands", () => {
	assert.equal(inferBuildOperationFromCommand("dbt compile --target dev --select tag:project-management"), "compile");
	assert.equal(inferBuildOperationFromCommand("dbt test --target dev --select tag:project-management"), "test");
	assert.equal(inferBuildOperationFromCommand("dbt build --target dev --select tag:project-management"), "build");
	assert.equal(inferBuildOperationFromCommand("dbt docs generate"), "docs");
});
