import api from "@/api/apiClient";
export type ModelIngestionTarget = {
	schemaVersion: 1;
	modelSpecId: string; modelRevision: number; modelChecksum: string;
	implementationRevision: number; implementationChecksum: string;
	environment: string; candidateId: string; runGroupId: string; dataSourceId: string;
	databaseName: string; schemaName: string; tableName: string;
	columns: Array<{ name: string; dataType: string; nullable: boolean; primaryKey: boolean }>;
};
export const getModelIngestionTarget = (id: string, environment: string) => api.get<ModelIngestionTarget>({
	url: `/modeling/model-specs/${encodeURIComponent(id)}/ingestion-target`, params: { environment }, _skipErrorToast: true,
} as any);

export const registerModelData = (id: string, command: { candidateId: string; candidateVersion: number; modelRevision: number; modelChecksum: string }) => api.post<string[]>({
	url: `/modeling/model-specs/${encodeURIComponent(id)}/data-registration`, data: command, _skipErrorToast: true,
} as any);
