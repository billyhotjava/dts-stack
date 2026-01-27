import apiClient from "../apiClient";

export type DataProductSummary = {
	id: string;
	code: string;
	name: string;
	productType?: string;
	classification?: string;
	status?: string;
	sla?: string;
	refreshFrequency?: string;
	currentVersion?: string;
	subscriptions?: number;
	datasets?: string[];
};

export type DataProductDetail = {
	id: string;
	code: string;
	name: string;
	productType?: string;
	classification?: string;
	status?: string;
	sla?: string;
	refreshFrequency?: string;
	latencyObjective?: string;
	failurePolicy?: string;
	subscriptions?: number;
	description?: string;
	datasets?: string[];
	versions?: DataProductVersion[];
};

export type DataProductVersion = {
	version: string;
	status?: string;
	releasedAt?: string;
	diffSummary?: string;
	fields?: DataProductField[];
	consumption?: any;
	metadata?: any;
};

export type DataProductField = {
	name: string;
	type?: string;
	term?: string;
	masked?: boolean;
	description?: string;
};

export type DataProductUpsert = {
	code: string;
	name: string;
	productType?: string;
	classification?: string;
	status?: string;
	sla?: string;
	refreshFrequency?: string;
	latencyObjective?: string;
	failurePolicy?: string;
	description?: string;
	datasets?: { datasetId?: string; datasetName?: string }[];
};

export type DataProductVersionRequest = {
	version: string;
	status?: string;
	diffSummary?: string;
	fields?: DataProductField[];
	consumption?: any;
	metadata?: any;
};

export default {
	list: (params?: { keyword?: string; type?: string; status?: string }) =>
		apiClient.get<DataProductSummary[]>({ url: "/services/products", params }),
	detail: (id: string) => apiClient.get<DataProductDetail>({ url: `/services/products/${id}` }),
	create: (payload: DataProductUpsert) => apiClient.post<DataProductDetail>({ url: "/services/products", data: payload }),
	update: (id: string, payload: DataProductUpsert) =>
		apiClient.put<DataProductDetail>({ url: `/services/products/${id}`, data: payload }),
	addVersion: (id: string, payload: DataProductVersionRequest) =>
		apiClient.post<DataProductVersion>({ url: `/services/products/${id}/versions`, data: payload }),
	remove: (id: string) => apiClient.delete<boolean>({ url: `/services/products/${id}` }),
};
