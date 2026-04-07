type ExecutionTask = {
	name?: string;
	isOverdue?: boolean;
	delayDays?: number;
	majorProjectName?: string;
};

type MilestoneRow = {
	name?: string;
	status?: string;
	planDate?: string;
};

type DueRow = {
	name?: string;
	dept?: string;
	delayDays?: number;
};

type WorkloadRow = {
	dept?: string;
	activeCount?: number;
	overdueCount?: number;
	highRiskCount?: number;
};

export function buildExecutionSnapshot(
	tasks: ExecutionTask[],
	milestones: MilestoneRow[],
	dueList: DueRow[],
	workload: WorkloadRow[],
) {
	const overdueTasks = tasks.filter((item) => item.isOverdue);
	const sortedMilestones = [...milestones].sort((left, right) =>
		String(left.planDate ?? "").localeCompare(String(right.planDate ?? "")),
	);
	const busiestDept = [...workload].sort((left, right) => {
		const rightScore =
			(right.activeCount ?? 0) * 10 + (right.overdueCount ?? 0) * 5 + (right.highRiskCount ?? 0) * 3;
		const leftScore =
			(left.activeCount ?? 0) * 10 + (left.overdueCount ?? 0) * 5 + (left.highRiskCount ?? 0) * 3;
		return rightScore - leftScore;
	})[0];

	return {
		overdueTaskCount: overdueTasks.length,
		maxDelayDays: overdueTasks.reduce((max, item) => Math.max(max, item.delayDays ?? 0), 0),
		nextMilestoneName: sortedMilestones[0]?.name ?? "",
		dueSoonCount: dueList.length,
		busiestDept: busiestDept?.dept ?? "",
	};
}
