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

/** F15 K3: the registration task created when a build is confirmed. */
export type ModelDataRegistrationStatus = {
	state: "UNTRACKED" | "MISSING" | "PENDING" | "SUCCEEDED" | "FAILED";
	errorCode: string | null;
	errorMessage: string | null;
	attempts: number;
};

export type ModelRegistrationTask = {
	id: string; candidateId: string; buildVersion: number; environment: string; models: string[];
	state: string; attempts: number; errorMessage: string | null; updatedAt: string; canRetry: boolean;
};
export const listModelRegistrationTasks = (offset = 0) => api.get<{ items: ModelRegistrationTask[]; nextOffset: number | null }>({
	url: "/modeling/model-specs/registration-tasks", params: { offset }, _skipErrorToast: true,
} as any);
export const retryModelRegistrationTask = (task: ModelRegistrationTask) => api.post<void>({
	url: `/modeling/model-specs/registration-tasks/${encodeURIComponent(task.id)}/retry`,
	data: { buildVersion: task.buildVersion }, _skipErrorToast: true,
} as any);
export const getModelDataRegistrationStatus = (id: string, candidateId: string) =>
	api.get<ModelDataRegistrationStatus>({
		url: `/modeling/model-specs/${encodeURIComponent(id)}/data-registration`,
		params: { candidateId },
		_skipErrorToast: true,
	} as any);
export const registerModelData = (id: string, command: { candidateId: string; candidateVersion: number; modelRevision: number; modelChecksum: string }) => api.post<string[]>({
	url: `/modeling/model-specs/${encodeURIComponent(id)}/data-registration`, data: command, _skipErrorToast: true,
} as any);
