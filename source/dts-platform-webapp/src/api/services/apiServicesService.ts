import apiClient from "../apiClient";

export type ApiServiceSummary = {
	id: string;
	code: string;
	name: string;
	datasetId?: string;
	datasetName?: string;
	method?: string;
	path?: string;
	classification?: string;
	qps?: number;
	qpsLimit?: number;
	dailyLimit?: number;
	status?: string;
	recentCalls?: number;
	sparkline?: number[];
};

export type ApiServiceDetail = {
	id: string;
	code: string;
	name: string;
	datasetId?: string;
	datasetName?: string;
	method?: string;
	path?: string;
	classification?: string;
	qps?: number;
	qpsLimit?: number;
	dailyLimit?: number;
	status?: string;
	policy?: any;
	input?: any[];
	output?: any[];
	quotas?: any;
	audit?: any;
	latestVersion?: string;
	lastPublishedAt?: string;
	description?: string;
};

export type ApiServiceUpsert = {
	code: string;
	name: string;
	datasetId?: string;
	method?: string;
	path?: string;
	classification?: string;
	qpsLimit?: number;
	dailyLimit?: number;
	policy?: any;
	requestSchema?: any;
	responseSchema?: any;
	tags?: string;
	description?: string;
};

export type ApiTryInvokeRequest = {
	params?: Record<string, string>;
	headers?: Record<string, string>;
	body?: Record<string, unknown> | string | null;
};

export default {
	list: (params?: { keyword?: string; method?: string; status?: string }) =>
		apiClient.get<ApiServiceSummary[]>({ url: "/services/apis", params }),
	detail: (id: string) => apiClient.get<ApiServiceDetail>({ url: `/services/apis/${id}` }),
	create: (payload: ApiServiceUpsert) => apiClient.post<ApiServiceDetail>({ url: "/services/apis", data: payload }),
	update: (id: string, payload: ApiServiceUpsert) =>
		apiClient.put<ApiServiceDetail>({ url: `/services/apis/${id}`, data: payload }),
	disable: (id: string) => apiClient.post<ApiServiceDetail>({ url: `/services/apis/${id}/disable` }),
	metrics: (id: string) => apiClient.get<any>({ url: `/services/apis/${id}/metrics` }),
	tryInvoke: (id: string, payload?: ApiTryInvokeRequest) =>
		apiClient.post<any>({ url: `/services/apis/${id}/try`, data: payload || {} }),
};
