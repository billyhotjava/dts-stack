import apiClient from "../apiClient";

export type OpsOverview = {
	generatedAt?: string;
	totalRuns?: number;
	successRate?: number | null;
	alerts?: number;
	running?: number;
};

export type OpsInstance = {
	id: string;
	entryKey?: string;
	artifactId?: string;
	artifactType?: string;
	artifactName?: string;
	externalRunId?: string;
	externalUrl?: string;
	status?: string;
	startedAt?: string;
	finishedAt?: string;
	durationMs?: number;
	message?: string;
	logPath?: string;
	dagId?: string;
};

export type OpsAlert = {
	type: string;
	status?: string;
	severity?: string;
	datasetId?: string;
	ruleId?: string;
	ruleName?: string;
	message?: string;
	createdAt?: string;
};

export type OpsBackfill = {
	id: string;
	dagId: string;
	name?: string;
	dateFrom?: string;
	dateTo?: string;
	status?: string;
	message?: string;
	triggeredAt?: string;
	externalRunId?: string;
};

export type OpsBackfillRequest = {
	dagId: string;
	dateFrom?: string;
	dateTo?: string;
	note?: string;
};

export type OpsDevCenterMetricSummary = {
	totalRuns?: number;
	successRuns?: number;
	failedRuns?: number;
	runningRuns?: number;
	successRate?: number | null;
	failureRate?: number | null;
	retryRuns?: number;
	retryRate?: number | null;
	mttrMinutes?: number | null;
	releaseRuns?: number;
	releaseSuccessRate?: number | null;
	rollbackRate?: number | null;
};

export type OpsDevCenterTrend = {
	date: string;
	totalRuns?: number;
	successRuns?: number;
	failedRuns?: number;
	runningRuns?: number;
	successRate?: number | null;
	avgDurationMs?: number | null;
};

export type OpsDevCenterTopFailure = {
	entryKey?: string;
	artifactName?: string;
	artifactId?: string;
	planId?: string;
	planName?: string;
	totalRuns?: number;
	failedRuns?: number;
	failureRate?: number | null;
};

export type OpsDevCenterPlanOption = {
	id: string;
	name?: string;
	ownerDept?: string;
	status?: string;
	runCount?: number;
};

export type OpsDevCenterMetrics = {
	generatedAt?: string;
	summary?: OpsDevCenterMetricSummary;
	trend?: OpsDevCenterTrend[];
	topFailures?: OpsDevCenterTopFailure[];
	availableOwnerDepts?: string[];
	availablePlans?: OpsDevCenterPlanOption[];
	filters?: {
		entryKey?: string | null;
		ownerDept?: string | null;
		artifactId?: string | null;
		artifactName?: string | null;
		planId?: string | null;
		windowDays?: number;
	};
};

export default {
	overview: () => apiClient.get<OpsOverview>({ url: "/ops/overview" }),
	instances: (params?: { entryKey?: string; status?: string; keyword?: string; limit?: number }) =>
		apiClient.get<OpsInstance[]>({ url: "/ops/instances", params }),
	alerts: (params?: { limit?: number }) => apiClient.get<OpsAlert[]>({ url: "/ops/alerts", params }),
	devCenterMetrics: (params?: { days?: number; entryKey?: string; ownerDept?: string; artifactId?: string; artifactName?: string; planId?: string }) =>
		apiClient.get<OpsDevCenterMetrics>({ url: "/ops/metrics/dev-center", params }),
	backfills: () => apiClient.get<OpsBackfill[]>({ url: "/ops/backfills" }),
	createBackfill: (payload: OpsBackfillRequest) =>
		apiClient.post<OpsBackfill>({ url: "/ops/backfills", data: payload }),
};
