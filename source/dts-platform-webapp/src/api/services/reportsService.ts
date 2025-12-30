import apiClient from "../apiClient";

export type ReportLink = {
	id: string;
	code: string;
	title: string;
	engine: string;
	reportType?: string | null;
	deptCodes?: string[];
	roleCodes?: string[];
	classification: string;
	url: string;
	enabled?: boolean;
	sortOrder?: number | null;
	owner?: string | null;
	updatedAt?: string | null;
};

export type ReportVisitPayload = {
	id?: string;
	code?: string;
	title?: string;
	url?: string;
	engine?: string;
	classification?: string;
};

function getPublishedReports(params?: { keyword?: string; deptCode?: string; type?: string }) {
	return apiClient.get<ReportLink[]>({ url: "/reports/published", params });
}

export type ReportLinkUpsertRequest = {
	code: string;
	title: string;
	url: string;
	engine?: string;
	reportType?: string;
	deptCodes?: string[];
	roleCodes?: string[];
	classification: string;
	enabled?: boolean;
	sortOrder?: number;
};

export default {
	getPublishedReports,
	visit: (payload: ReportVisitPayload) => apiClient.post<{ ok: boolean }>({ url: "/reports/visit", data: payload }),
	listAll: (params?: { keyword?: string; deptCode?: string; type?: string; enabledOnly?: boolean }) =>
		apiClient.get<ReportLink[]>({ url: "/reports", params }),
	create: (payload: ReportLinkUpsertRequest) =>
		apiClient.post<ReportLink>({ url: "/reports", data: payload }),
	update: (id: string, payload: ReportLinkUpsertRequest) =>
		apiClient.put<ReportLink>({ url: `/reports/${id}`, data: payload }),
	disable: (id: string) => apiClient.delete<{ ok: boolean }>({ url: `/reports/${id}` }),
	purge: (id: string) => apiClient.delete<{ ok: boolean }>({ url: `/reports/${id}/purge` }),
};
