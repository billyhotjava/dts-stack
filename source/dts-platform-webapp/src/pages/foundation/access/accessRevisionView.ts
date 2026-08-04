import type { IngestionTaskDTO, IngestionTaskRevisionDTO } from "@/api/ingestion";

const latestRevisionNumber = (revisions: IngestionTaskRevisionDTO[], state: "ACTIVE" | "DRAFT") =>
	revisions
		.filter((revision) => revision.revisionState === state)
		.reduce<number | undefined>(
			(latest, revision) =>
				latest === undefined || revision.revisionNumber > latest ? revision.revisionNumber : latest,
			undefined,
		);

export const resolveAccessRevisionView = (
	task: Pick<IngestionTaskDTO, "status" | "revisionNumber" | "revisionState"> | null,
	revisions: IngestionTaskRevisionDTO[],
	revisionsError: boolean,
) => {
	const activeRevisionNumber = latestRevisionNumber(revisions, "ACTIVE");
	const draftRevisionNumber = latestRevisionNumber(revisions, "DRAFT");
	const hasDraftRevision = task?.revisionState === "DRAFT" || draftRevisionNumber !== undefined;
	const taskActive =
		String(task?.status || "")
			.trim()
			.toLowerCase() === "active";
	const canExecuteActiveRevision = taskActive && activeRevisionNumber !== undefined && !revisionsError;
	const executeReason = revisionsError
		? "有效配置状态加载失败，已禁止执行"
		: !taskActive
			? `任务状态 ${task?.status || "unknown"} 不允许执行`
			: activeRevisionNumber === undefined
				? "任务没有可执行的有效配置"
				: "将执行当前有效配置";
	return {
		activeRevisionNumber,
		draftRevisionNumber,
		hasDraftRevision,
		canExecuteActiveRevision,
		executeReason,
	};
};
