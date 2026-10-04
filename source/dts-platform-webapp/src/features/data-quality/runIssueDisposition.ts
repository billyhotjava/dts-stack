export type RunIssueAction = {
	id?: string;
	actionType?: string;
	actor?: string;
	notes?: string;
	createdDate?: string;
};

export type RunIssue = {
	id: string;
	sourceType?: string;
	sourceId?: string;
	datasetId?: string;
	title?: string;
	summary?: string;
	status?: string;
	severity?: string;
	priority?: string;
	dataLevel?: string;
	owner?: string;
	assignedTo?: string;
	dueAt?: string;
	resolution?: string;
	tags?: string[];
	overdue?: boolean;
	ownerDept?: string;
	actions?: RunIssueAction[];
};

export const findRunIssue = (issues: RunIssue[], runId: string) =>
	issues.find(
		(issue) => String(issue.sourceType || "").toUpperCase() === "QUALITY_RUN" && String(issue.sourceId || "") === runId,
	);

export const runNeedsDisposition = (status?: string) =>
	["FAILED", "ERROR", "ABORTED", "CANCELLED", "TIMED_OUT"].includes(String(status || "").toUpperCase());

export const buildIssueUpdatePayload = (issue: RunIssue, patch: Partial<RunIssue>) => {
	const next = { ...issue, ...patch };
	return {
		datasetId: next.datasetId,
		title: next.title,
		summary: next.summary,
		status: next.status,
		severity: next.severity,
		priority: next.priority,
		dataLevel: next.dataLevel,
		owner: next.owner,
		assignedTo: next.assignedTo,
		dueAt: next.dueAt,
		resolution: next.resolution,
		tags: next.tags,
	};
};
