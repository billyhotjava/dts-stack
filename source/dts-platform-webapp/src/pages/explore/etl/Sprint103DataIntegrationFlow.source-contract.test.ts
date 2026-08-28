import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE = readFileSync(new URL("./OrchestrationPage.tsx", import.meta.url), "utf8");
const TASK_LIST = readFileSync(new URL("./OrchestrationTaskList.tsx", import.meta.url), "utf8");
const RUNS = readFileSync(new URL("./OrchestrationRunsTab.tsx", import.meta.url), "utf8");
const API = readFileSync(new URL("../../../api/ingestion.ts", import.meta.url), "utf8");

test("orchestration landing is a server-paged task list and never auto-opens the first task", () => {
	assert.match(PAGE, /!requestedTaskId[\s\S]*<OrchestrationTaskList/);
	assert.doesNotMatch(PAGE, /loadedTasks\[0\]/);
	assert.doesNotMatch(PAGE, /getTasks\(\{ page: 0, size: 200/);

	assert.match(TASK_LIST, /page: pagination\.current - 1/);
	assert.match(TASK_LIST, /size: pagination\.pageSize/);
	assert.match(TASK_LIST, /pageSize: 10/);
	assert.match(TASK_LIST, /任务名称、业务说明、来源类型或负责人/);
	assert.match(TASK_LIST, /目标资产/);
	assert.match(TASK_LIST, /任务版本/);
	assert.match(TASK_LIST, /最近运行/);
	assert.match(TASK_LIST, /onOpenTask\(taskId, "design"\)/);
	assert.match(TASK_LIST, /onOpenTask\(taskId, "runs"\)/);
	assert.match(TASK_LIST, /foundation\/data-sources\/access\/new\?kind=/);
	assert.doesNotMatch(TASK_LIST, /listAirflowJobs|listAirflowJobRuns|triggerAirflowJob/i);
});

test("task design is versioned server state rather than a local editable DAG", () => {
	for (const contract of [
		"getTaskDesign",
		"updateTaskDesign",
		"validateTaskDesign",
		"getTaskTopology",
		"admitTask",
		"setTaskSchedule",
	]) {
		assert.match(PAGE, new RegExp(`ingestionTaskAPI\\.${contract}`));
	}
	assert.match(PAGE, /targetDatasetId/);
	assert.match(PAGE, /dataSourcesService\.list\(\)/);
	assert.match(PAGE, /listDatasets\(\{ page: 0, size: 200, enabledOnly: true \}\)/);
	assert.match(PAGE, /onSearch=\{handleDatasetSearch\}/);
	assert.match(PAGE, /getDataset\(selectedDatasetId\)/);
	assert.match(PAGE, /data-testid="readonly-topology"/);
	assert.match(PAGE, /If-Match|planChecksum/);
	assert.match(PAGE, /撤销未保存修改/);
	assert.doesNotMatch(PAGE, /WorkflowCanvas|BlockSelectorPanel|useWorkflowStore|serializeDsl|localStorage/);
});

test("run history is task scoped and exposes durable commands and connector-neutral logs", () => {
	for (const contract of [
		"getExecutions",
		"getExecution",
		"executeTaskAsync",
		"retryExecutionAsync",
		"cancelExecution",
		"getExecutionLog",
	]) {
		assert.match(RUNS, new RegExp(`ingestionTaskAPI\\.${contract}`));
	}
	assert.match(RUNS, /scope: "all"/);
	assert.match(RUNS, /commandKey\("execute"/);
	assert.match(RUNS, /commandKey\("retry"/);
	assert.match(RUNS, /resolveExecutionPollIntervalMs\(\)/);
	assert.match(RUNS, /parentExecutionId/);
	assert.match(RUNS, /revisionNumber: revisionScope === "CURRENT"/);
	assert.match(RUNS, /仅显示当前 DTS 接入任务/);
	assert.match(API, /revisionNumber\?: number/);
	assert.doesNotMatch(RUNS, /listAirflowJobs|listAirflowJobRuns|triggerAirflowJob|dbt/i);
});

test("asset, quality, evidence freshness and trusted usability remain distinct", () => {
	for (const term of ["目标资产", "证据时效", "质量结果", "资产可消费性", "可信可用"]) {
		assert.match(RUNS, new RegExp(term));
	}
	assert.match(RUNS, /evidence\.trustedUsable \? "可信可用" : "未形成可信结论"/);
	assert.match(RUNS, /\/catalog\/datasets\//);
	assert.match(RUNS, /\/governance\/rules\/runs/);
	assert.match(PAGE, /质量策略将在数据写入成功后运行/);
	assert.match(PAGE, /质量未通过不会把资产标记为可信可用/);
});

test("frontend API carries target identity, concurrency and idempotency headers", () => {
	assert.match(API, /targetDatasetId\?: string/);
	assert.match(API, /"If-Match": planChecksum/);
	assert.match(API, /"X-Expected-Plan-Checksum": planChecksum/);
	assert.match(API, /"Idempotency-Key": idempotencyKey/);
	for (const path of ["/design", "/design/validate", "/topology", "/schedule/", "/execute/async", "/retry/async"]) {
		assert.match(API, new RegExp(path.replaceAll("/", "\\/")));
	}
});
