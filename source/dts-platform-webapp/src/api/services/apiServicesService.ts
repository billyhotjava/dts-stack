import apiClient from "../apiClient";

export type ApiHttpMethod = "GET" | "POST" | "PUT" | "DELETE" | "PATCH";

export type ApiServiceStatus = "PUBLISHED" | "OFFLINE";

export interface ApiServiceSummary {
	id: string;
	name: string;
	dataset: string; // 绑定数据集/视图
	method: ApiHttpMethod;
	path: string;
	classification: string; // 最低密级（公开/内部/机密/绝密 等）
	qps: number; // 当前QPS
	qpsLimit: number; // 上限QPS
	dailyLimit: number; // 日调用上限
	status: ApiServiceStatus;
	recentCalls: number; // 最近调用总量
	sparkline: number[]; // 最近调用量趋势
}

export interface ApiFieldDef {
	name: string;
	type: string;
	masked?: boolean;
	description?: string;
}

export interface ApiServiceDetail extends ApiServiceSummary {
	input: ApiFieldDef[];
	output: ApiFieldDef[];
	policy: {
		minLevel: string;
		maskedColumns: string[];
		rowFilter: string;
	};
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
	levelDistribution: { label: string; value: number }[]; // 公开/内部/机密/绝密
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
