import api from "@/api/apiClient";
import type { HiveConnectionPersistRequest, HiveConnectionTestRequest, HiveConnectionTestResult } from "#/infra";

export interface InfraDataSource {
	id: string;
	name: string;
	type: string;
	jdbcUrl: string;
	username?: string;
	description?: string;
	props: Record<string, any>;
	createdAt?: string;
	lastVerifiedAt?: string;
	status?: string;
	hasSecrets: boolean;
}

export interface ModuleStatus {
	module: string;
	status: string;
	message?: string;
	updatedAt?: string;
}

export interface IntegrationStatus {
	lastSyncAt?: string;
	reason?: string;
	actions?: string[];
	catalogDatasetCount?: number;
}

export interface InfraFeatureFlags {
	multiSourceEnabled: boolean;
	hasActiveInceptor: boolean;
	inceptorStatus: string;
	syncInProgress?: boolean;
	defaultJdbcUrl?: string;
	loginPrincipal?: string;
	lastVerifiedAt?: string;
	lastUpdatedAt?: string;
	dataSourceName?: string;
	description?: string;
	authMethod?: string;
	database?: string;
	proxyUser?: string;
	engineVersion?: string;
	driverVersion?: string;
	lastTestElapsedMillis?: number;
	moduleStatuses?: ModuleStatus[];
	integrationStatus?: IntegrationStatus;
}

export interface ConnectionTestLog {
	id: string;
	dataSourceId?: string;
	result: "SUCCESS" | "FAILURE";
	message?: string;
	elapsedMs?: number;
	createdAt?: string;
}

export interface UpsertInfraDataSourcePayload {
	name: string;
	type: string;
	jdbcUrl: string;
	username?: string;
	description?: string;
	props?: Record<string, any>;
	secrets?: Record<string, any>;
}

export const listInfraDataSources = () => api.get<InfraDataSource[]>({ url: "/infra/data-sources" });

export const fetchInfraFeatures = () => api.get<InfraFeatureFlags>({ url: "/infra/features" });

export const refreshInceptorRegistry = () =>
	api.post<InfraFeatureFlags>({ url: "/infra/data-sources/inceptor/refresh" });

export const listConnectionTestLogs = (dataSourceId?: string) =>
	api.get<ConnectionTestLog[]>({
		url: "/infra/data-sources/test-logs",
		params: dataSourceId ? { dataSourceId } : undefined,
	});

export const testHiveConnection = (data: HiveConnectionTestRequest) =>
	api.post<HiveConnectionTestResult>({ url: "/infra/data-sources/test-connection", data });

export const publishInceptorDataSource = (data: HiveConnectionPersistRequest) =>
	api.post<InfraDataSource>({ url: "/infra/data-sources/inceptor/publish", data });

export const deleteInfraDataSource = (id: string) => api.delete<boolean>({ url: `/infra/data-sources/${id}` });

export const createInfraDataSource = (data: UpsertInfraDataSourcePayload) =>
	api.post<InfraDataSource>({ url: "/infra/data-sources", data });

export const updateInfraDataSource = (id: string, data: UpsertInfraDataSourcePayload) =>
	api.put<InfraDataSource>({ url: `/infra/data-sources/${id}`, data });
