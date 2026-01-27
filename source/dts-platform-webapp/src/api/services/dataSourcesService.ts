import apiClient from "../apiClient";

export type InfraDataSource = {
	id: string;
	name: string;
	type: string;
	jdbcUrl?: string;
	username?: string;
	description?: string;
	ownerDept?: string;
	props?: Record<string, any>;
	createdAt?: string;
	lastUpdatedAt?: string;
	lastVerifiedAt?: string;
	status?: string;
	hasSecrets?: boolean;
	engineVersion?: string;
	driverVersion?: string;
	lastTestElapsedMillis?: number;
	lastHeartbeatAt?: string;
	heartbeatStatus?: string;
	heartbeatFailureCount?: number;
	lastError?: string;
};

export type DataSourceUpsertPayload = {
	name: string;
	type: string;
	jdbcUrl?: string;
	username?: string;
	description?: string;
	props?: Record<string, any>;
	secrets?: Record<string, any>;
};

export default {
	list: () => apiClient.get<InfraDataSource[]>({ url: "/infra/data-sources" }),
	detail: (id: string) => apiClient.get<InfraDataSource>({ url: `/infra/data-sources/${id}` }),
	create: (payload: DataSourceUpsertPayload) =>
		apiClient.post<InfraDataSource>({ url: "/infra/data-sources", data: payload }),
	update: (id: string, payload: DataSourceUpsertPayload) =>
		apiClient.put<InfraDataSource>({ url: `/infra/data-sources/${id}`, data: payload }),
	remove: (id: string) => apiClient.delete<void>({ url: `/infra/data-sources/${id}` }),
	test: (id: string) => apiClient.post<any>({ url: `/infra/data-sources/${id}/test` }),
};
