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
		deleteTask: async (taskId: number) => {
			calls.push(`delete:${taskId}`);
		},
	} as AccessPlanOperationApi;
	return { api, calls };
};

test("access plan operations call the existing ingestion contracts", async () => {
	const { api, calls } = createApi();

	await runAccessPlanOperation("admit", 19, api);
	await runAccessPlanOperation("execute", 19, api);
	await runAccessPlanOperation("delete", 19, api);

	assert.deepEqual(calls, ["admit:19", "execute:19", "delete:19"]);
});

test("access plan operations reject invalid task identifiers before making requests", () => {
	const { api, calls } = createApi();

	assert.throws(() => runAccessPlanOperation("execute", 0, api), /接入任务编号无效/);
	assert.deepEqual(calls, []);
});
