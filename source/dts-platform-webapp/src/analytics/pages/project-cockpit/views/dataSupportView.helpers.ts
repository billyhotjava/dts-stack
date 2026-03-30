type CoverageRow = {
	label?: string;
	value?: string;
};

type ChecklistRow = {
	id?: string;
	status?: string;
	title?: string;
};

type DataSourceRow = {
	name?: string;
	description?: string;
};

type BatchSummary = {
	batchId?: string;
	status?: string;
	issueRows?: number;
};

type QualitySummary = {
	issueCount?: number;
	unmappedSubprojectCount?: number;
	unknownDelayReasonCount?: number;
};

export function buildDataSupportSnapshot(
	lastUpdatedAt: string,
	batch: BatchSummary | null | undefined,
	quality: QualitySummary | null | undefined,
	coverage: CoverageRow[],
	missingChecklist: ChecklistRow[],
	dataSources: DataSourceRow[],
) {
	return {
		lastUpdatedAt,
		batchId: batch?.batchId ?? "",
		batchStatus: batch?.status ?? "",
		coverageCount: coverage.length,
		pendingChecklistCount: missingChecklist.filter((item) =>
			String(item.status ?? "").includes("待"),
		).length,
		latestSourceName: dataSources.at(-1)?.name ?? "",
		issueCount: Number(quality?.issueCount ?? 0),
		unmappedSubprojectCount: Number(quality?.unmappedSubprojectCount ?? 0),
		unknownDelayReasonCount: Number(quality?.unknownDelayReasonCount ?? 0),
		issueRows: Number(batch?.issueRows ?? 0),
	};
}
