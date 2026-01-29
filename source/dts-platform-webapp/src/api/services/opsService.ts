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

export default {
	overview: () => apiClient.get<OpsOverview>({ url: "/ops/overview" }),
	instances: (params?: { entryKey?: string; status?: string; keyword?: string; limit?: number }) =>
		apiClient.get<OpsInstance[]>({ url: "/ops/instances", params }),
	alerts: (params?: { limit?: number }) => apiClient.get<OpsAlert[]>({ url: "/ops/alerts", params }),
	backfills: () => apiClient.get<OpsBackfill[]>({ url: "/ops/backfills" }),
	createBackfill: (payload: OpsBackfillRequest) =>
		apiClient.post<OpsBackfill>({ url: "/ops/backfills", data: payload }),
};
