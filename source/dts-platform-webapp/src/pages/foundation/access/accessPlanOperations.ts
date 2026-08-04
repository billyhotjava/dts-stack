import type { ingestionTaskAPI } from "@/api/ingestion";

export type AccessPlanOperation = "execute" | "delete";

export type AccessPlanOperationApi = Pick<typeof ingestionTaskAPI, "executeTaskAsync" | "deleteTask">;

export const runAccessPlanOperation = (operation: AccessPlanOperation, taskId: number, api: AccessPlanOperationApi) => {
	if (!Number.isSafeInteger(taskId) || taskId <= 0) {
		throw new Error("接入任务编号无效");
	}
	if (operation === "execute") return api.executeTaskAsync(taskId);
	return api.deleteTask(taskId);
};
