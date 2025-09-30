import apiClient from "../apiClient";

export type PublishedReport = {
	id: string;
	title: string;
	biTool: string;
	owner: string;
	domain?: string;
	tags?: string[];
	updatedAt: string;
	url: string; // absolute URL to BI system
};

function getPublishedReports(params?: { keyword?: string; tool?: string }) {
	return apiClient.get<PublishedReport[]>({ url: "/reports/published", params });
}

export default {
	getPublishedReports,
};
