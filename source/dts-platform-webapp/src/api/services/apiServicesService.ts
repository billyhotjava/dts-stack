import apiClient from "../apiClient";

export type ApiHttpMethod = "GET" | "POST" | "PUT" | "DELETE" | "PATCH";

export type ApiServiceStatus = "PUBLISHED" | "OFFLINE";

export interface ApiServiceSummary {
	id: string;
	code: string;
	name: string;
	datasetId?: string;
	datasetName?: string;
	method: ApiHttpMethod;
	path: string;
	classification?: string;
	qps: number;
	qpsLimit: number;
	dailyLimit: number;
	status: ApiServiceStatus;
	recentCalls: number;
	sparkline: number[];
}

export interface ApiFieldDef {
	name: string;
	type: string;
	masked?: boolean;
	description?: string;
}

export interface ApiServiceDetail extends ApiServiceSummary {
	policy: {
		minLevel?: string | null;
		maskedColumns: string[];
		rowFilter?: string | null;
	};
	input: ApiFieldDef[];
	output: ApiFieldDef[];
	quotas: {
		qpsLimit: number;
		dailyLimit: number;
		dailyRemaining: number;
	};
	audit: {
		last24hCalls: number;
		maskedHits: number;
		denies: number;
	};
	latestVersion?: string | null;
	lastPublishedAt?: string | null;
	description?: string | null;
}

export interface TryInvokeRequest {
	params: Record<string, any>;
	identity?: { type: "user" | "role"; id: string; level?: string };
}

export interface TryInvokeResponse {
	columns: string[];
	maskedColumns: string[];
	rows: Array<Record<string, any>>;
	filteredRowCount: number;
	policyHits: string[];
}

export interface ApiMetricsResponse {
	series: { timestamp: number; calls: number; qps: number }[];
	levelDistribution: { label: string; value: number }[];
	recentCalls: { user: string; level: string; rowCount: number; policy: string }[];
}

function listApiServices(params?: { keyword?: string; method?: ApiHttpMethod; status?: ApiServiceStatus | "all" }) {
	return apiClient.get<ApiServiceSummary[]>({ url: "/services/apis", params });
}

function getApiServiceById(id: string) {
	return apiClient.get<ApiServiceDetail>({ url: `/services/apis/${id}` });
}

export default {
	listApiServices,
	getApiServiceById,
	tryInvoke(id: string, data: TryInvokeRequest) {
		return apiClient.post<TryInvokeResponse>({ url: `/services/apis/${id}/try`, data });
	},
	getMetrics(id: string) {
		return apiClient.get<ApiMetricsResponse>({ url: `/services/apis/${id}/metrics` });
	},
};
