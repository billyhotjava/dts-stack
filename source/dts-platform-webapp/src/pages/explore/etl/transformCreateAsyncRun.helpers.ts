import type { IngestionExecutionDTO } from "@/api/ingestion";

const normalizeText = (value?: string) => String(value || "").trim();

export type AsyncRunProgressStatus = "active" | "success" | "exception";

export type AsyncRunProgressView = {
	progress: number;
	status: AsyncRunProgressStatus;
	stage: string;
	detail: string;
	terminal: boolean;
};

export const resolveCreatedTaskId = (payload: any): number | undefined => {
	const candidate = payload?.task?.id ?? payload?.taskId ?? payload?.id ?? payload?.task?.taskId;
	const value = Number(candidate);
	return Number.isFinite(value) && value > 0 ? value : undefined;
};

export const resolveAsyncRunPollHint = (payload: any): number | undefined => {
	const hint = Number(payload?.execution?.pollIntervalMs ?? payload?.pollIntervalMs);
	return Number.isFinite(hint) ? hint : undefined;
};

export const createAsyncRunInitialProgress = (): AsyncRunProgressView => ({
	progress: 10,
	status: "active",
	stage: "任务已提交",
	detail: "正在后台触发执行。",
	terminal: false,
});

export const createAsyncRunTimeoutProgress = (): AsyncRunProgressView => ({
	progress: 100,
	status: "exception",
	stage: "状态同步超时",
	detail: "超出等待时间，请进入任务详情页继续查看执行状态。",
	terminal: true,
});

export const createAsyncRunRetryProgress = (previous: AsyncRunProgressView): AsyncRunProgressView => ({
	...previous,
	detail: "状态同步中，稍后自动重试。",
});

export const mapExecutionToProgressView = (
	execution: IngestionExecutionDTO | null,
	elapsedMs: number
): AsyncRunProgressView => {
	if (!execution) {
		const dynamicProgress = Math.min(45, 15 + Math.floor(elapsedMs / 5000) * 5);
		return {
			progress: dynamicProgress,
			status: "active",
			stage: "等待执行记录",
			detail: "任务已提交，系统正在准备 DAG 和作业参数。",
			terminal: false,
		};
	}
	const rawStatus = normalizeText(execution.status).toLowerCase();
	if (rawStatus === "success") {
		return {
			progress: 100,
			status: "success",
			stage: "执行成功",
			detail: "入湖任务已执行完成。",
			terminal: true,
		};
	}
	if (rawStatus === "failed" || rawStatus === "error") {
		return {
			progress: 100,
			status: "exception",
			stage: "执行失败",
			detail: normalizeText(execution.errorMessage) || "任务执行失败，请查看日志定位原因。",
			terminal: true,
		};
	}
	if (rawStatus === "preparing") {
		return {
			progress: 55,
			status: "active",
			stage: "准备执行",
			detail: "正在生成/校验 Addax 作业并等待 DAG 就绪。",
			terminal: false,
		};
	}
	return {
		progress: 80,
		status: "active",
		stage: "执行中",
		detail: "已触发执行，正在同步运行状态。",
		terminal: false,
	};
};
