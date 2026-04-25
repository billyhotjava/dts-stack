import apiClient from "../apiClient";

export type WorkbenchOverview = {
	generatedAt?: string;
	myAssets?: number;
	todayNewAssets?: number;
};

export type WorkbenchTodoItem = {
	type: string;
	title?: string;
	status?: string;
	createdAt?: string;
	taskId?: string;
	requestId?: string;
	datasetId?: string;
	message?: string;
	requester?: string;
};

/**
 * Sprint-15 F1/T03 — Leader overview KPI block.
 *
 * `classification` values in `topReports` / `topAssets` are normalized to
 * `"S1" | "S2" | "S3" | "S4"` by the backend (e.g. TOP_SECRET → S1).
 *
 * `domainMatrix` may contain synthetic buckets `"__OTHER__"` and
 * `"__UNCATEGORIZED__"` which the UI renders differently.
 */
export interface LeaderOverviewKpis {
	reportsTotal: number;
	reportsNewInPeriod: number;
	visitsInPeriod: number;
	visitsMoM: number | null;
	assetsTotal: number;
	assetsNewInPeriod: number;
	assetsS1: number;
	assetsS1S2: number;
	assetsS1Ratio: number | null;
}

export interface LeaderOverviewTopReport {
	id: string;
	title: string;
	visits: number;
	bizDomain: string | null;
	classification: string;
	lastVisitedAt: string | null;
}

export interface LeaderOverviewTopAsset {
	id: string;
	name: string;
	classification: string;
	updatedAt: string;
	bizDomain: string | null;
}

export interface LeaderOverviewDomainCell {
	domain: string;
	domainName: string;
	visits: number;
}

export interface LeaderOverviewResponse {
	generatedAt: string;
	scope: "MINE" | "DEPT" | "ALL";
	effectiveDeptCode: string | null;
	timeRange: "MONTH" | "QUARTER" | "YEAR";
	kpis: LeaderOverviewKpis;
	topReports: LeaderOverviewTopReport[];
	topAssets: LeaderOverviewTopAsset[];
	domainMatrix: LeaderOverviewDomainCell[];
}

export interface LeaderOverviewParams {
	scope: "MINE" | "DEPT" | "ALL";
	deptCode?: string | null;
	bizDomain?: string | null;
	timeRange: "MONTH" | "QUARTER" | "YEAR";
}

export default {
	overview: () => apiClient.get<WorkbenchOverview>({ url: "/workbench/overview" }),
	todos: () => apiClient.get<WorkbenchTodoItem[]>({ url: "/workbench/todos" }),
	leaderOverview: (params: LeaderOverviewParams) =>
		apiClient.get<LeaderOverviewResponse>({
			url: "/workbench/leader-overview",
			params: {
				scope: params.scope,
				deptCode: params.deptCode ?? undefined,
				bizDomain: params.bizDomain ?? undefined,
				timeRange: params.timeRange,
			},
		}),
};
