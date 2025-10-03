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
	subscriptions: number;
	datasets: string[];
};

export type DataProductField = {
	name: string;
	type?: string;
	term?: string;
	masked?: boolean;
	description?: string;
};

export type DataProductVersion = {
	version: string;
	status?: string;
	releasedAt?: string;
	diffSummary?: string;
	fields: DataProductField[];
	consumption: {
		rest?: { endpoint?: string; auth?: string } | null;
		jdbc?: { driver?: string; url?: string } | null;
		file?: { objectStorePath?: string; sharedPath?: string; formats?: string[] } | null;
	};
	metadata: {
		bloodlineSummary?: string;
		classificationStrategy?: string;
		maskingStrategy?: string;
		latencyObjective?: string;
		failurePolicy?: string;
	};
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
	subscriptions: number;
	datasets: string[];
	versions: DataProductVersion[];
	description?: string;
};

export function listDataProducts(params?: { keyword?: string; type?: string; status?: string }) {
	return apiClient.get<DataProductSummary[]>({ url: "/services/products", params });
}

export function getDataProductDetail(id: string) {
	return apiClient.get<DataProductDetail>({ url: `/services/products/${id}` });
}
