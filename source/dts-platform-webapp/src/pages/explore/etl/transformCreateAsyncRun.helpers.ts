import type { IngestionExecutionDTO } from "@/api/ingestion";
import { normalizeText } from "@/utils/textUtils";

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

export const extractAsyncRunSubmitErrorMessage = (error: any): string => {
	const responseData = error?.response?.data;
	const detail = responseData?.detail || responseData?.message || responseData?.title || responseData?.error;
	return normalizeText(detail || error?.message);
};

export const isDagNotReadySubmitError = (error: any): boolean => {
	const message = extractAsyncRunSubmitErrorMessage(error).toUpperCase();
	return message.includes("AIRFLOW_DAG_NOT_READY_TIMEOUT") || message.includes("DAG 未就绪");
};

export const resolveAsyncRunSubmitFeedback = (
	error: any,
	action: "execute" | "retry" = "execute",
): { level: "warning" | "error"; message: string } => {
	if (isDagNotReadySubmitError(error)) {
		return {
			level: "warning",
			message:
				action === "retry"
					? "DAG 正在准备中，暂时还不能重试。请等待约 30 秒后再试。"
					: "DAG 正在准备中，暂时还不能执行。请等待约 30 秒后再试。",
		};
	}
	const fallback = extractAsyncRunSubmitErrorMessage(error) || "未知错误";
	return {
		level: "error",
		message: action === "retry" ? `重试失败: ${fallback}` : `执行失败: ${fallback}`,
	};
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

/**
 * Map execution status + elapsed time to a user-friendly progress view.
 *
 * The "preparing" phase can take up to ~135s (DAG file write → Airflow scheduler pickup → trigger).
 * We show phased messages so the user knows the system is still working:
 *   0-15s: 作业准备  |  15-45s: DAG 注册  |  45-90s: 调度器扫描  |  90s+: 即将触发
 */
export const mapExecutionToProgressView = (
	execution: IngestionExecutionDTO | null,
	elapsedMs: number,
): AsyncRunProgressView => {
	if (!execution) {
		const dynamicProgress = Math.min(45, 15 + Math.floor(elapsedMs / 5000) * 5);
		return {
			progress: dynamicProgress,
			status: "active",
			stage: "等待执行记录",
			detail: "任务已提交，系统正在创建执行记录。",
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
		return mapPreparingPhase(elapsedMs);
	}
	return {
		progress: 80,
		status: "active",
		stage: "执行中",
		detail: "Addax 任务运行中，正在同步状态。",
		terminal: false,
	};
};

/** Phased progress for the "preparing" state (up to ~135s). */
const mapPreparingPhase = (elapsedMs: number): AsyncRunProgressView => {
	if (elapsedMs < 15_000) {
		const progress = 20 + Math.floor(elapsedMs / 1000);
		return {
			progress,
			status: "active",
			stage: "正在准备作业",
			detail: "生成 Addax 作业配置、校验目标表结构…",
			terminal: false,
		};
	}
	if (elapsedMs < 45_000) {
		const progress = 35 + Math.floor((elapsedMs - 15_000) / 2000);
		return {
			progress: Math.min(progress, 49),
			status: "active",
			stage: "注册 DAG 文件",
			detail: "DAG 文件已写入，等待 Airflow 调度器识别…",
			terminal: false,
		};
	}
	if (elapsedMs < 90_000) {
		const progress = 50 + Math.floor((elapsedMs - 45_000) / 3000);
		return {
			progress: Math.min(progress, 64),
			status: "active",
			stage: "等待调度器扫描",
			detail: "Airflow 调度器正在扫描 DAG 目录，通常需要 30-60 秒。请耐心等待…",
			terminal: false,
		};
	}
	const progress = 65 + Math.floor((elapsedMs - 90_000) / 5000);
	return {
		progress: Math.min(progress, 74),
		status: "active",
		stage: "即将触发执行",
		detail: "DAG 注册中，即将触发 Airflow 执行。如长时间未启动，可查看任务详情页。",
		terminal: false,
	};
};
