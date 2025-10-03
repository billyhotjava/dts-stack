import api from "@/api/apiClient";
import type { HiveConnectionTestRequest, HiveConnectionTestResult } from "#/infra";

export interface InfraDataSource {
	id: string;
	name: string;
	type: string;
	jdbcUrl: string;
	username?: string;
	description?: string;
	props: Record<string, any>;
	createdAt?: string;
	hasSecrets: boolean;
}

export interface InfraDataStorage {
	id: string;
	name: string;
	type: string;
	location: string;
	description?: string;
	props: Record<string, any>;
	createdAt?: string;
	hasSecrets: boolean;
}

export interface InfraFeatureFlags {
	multiSourceEnabled: boolean;
}

export interface ConnectionTestLog {
	id: string;
	dataSourceId?: string;
	result: "SUCCESS" | "FAILURE";
	message?: string;
	elapsedMs?: number;
	createdAt?: string;
}

export const listInfraDataSources = () => api.get<InfraDataSource[]>({ url: "/infra/data-sources" });

export const listInfraDataStorages = () => api.get<InfraDataStorage[]>({ url: "/infra/data-storages" });

export const fetchInfraFeatures = () => api.get<InfraFeatureFlags>({ url: "/infra/features" });

export const listConnectionTestLogs = (dataSourceId?: string) =>
	api.get<ConnectionTestLog[]>({ url: "/infra/data-sources/test-logs", params: dataSourceId ? { dataSourceId } : undefined });

export const testHiveConnection = (data: HiveConnectionTestRequest) =>
	api.post<HiveConnectionTestResult>({ url: "/infra/data-sources/test-connection", data });
