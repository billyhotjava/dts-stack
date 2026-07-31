import assert from "node:assert/strict";
import test from "node:test";
import type { AccessPlanOperationApi } from "./accessPlanOperations";
import { runAccessPlanOperation } from "./accessPlanOperations";

const createApi = () => {
	const calls: string[] = [];
	const api = {
		admitTask: async (taskId: number) => {
			calls.push(`admit:${taskId}`);
			return { id: taskId };
		},
		executeTaskAsync: async (taskId: number) => {
			calls.push(`execute:${taskId}`);
			return { taskId };
		},
		rebuildDag: async (taskId: number) => {
			calls.push(`rebuild:${taskId}`);
			return { id: taskId };
		},
	} as AccessPlanOperationApi;
	return { api, calls };
};

test("access plan operations call the existing ingestion contracts", async () => {
	const { api, calls } = createApi();

	await runAccessPlanOperation("admit", 19, api);
	await runAccessPlanOperation("execute", 19, api);
	await runAccessPlanOperation("rebuildDag", 19, api);

	assert.deepEqual(calls, ["admit:19", "execute:19", "rebuild:19"]);
});

test("access plan operations reject invalid task identifiers before making requests", async () => {
	const { api, calls } = createApi();

	await assert.rejects(() => runAccessPlanOperation("execute", 0, api), /接入任务编号无效/);
	assert.deepEqual(calls, []);
});
