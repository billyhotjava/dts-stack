import api from "@/api/apiClient";
import type { BusinessObject, ModelSpec } from "@/pages/modeling/modelingVnextContract";

const quiet = <T>(config: Record<string, unknown>) =>
	api.get<T>({
		...config,
		headers: { "X-Modeling-Contract": "vnext" },
		_skipErrorToast: true,
	} as any);

const vnext = (config: Record<string, unknown>) => ({
	...config,
	headers: { "X-Modeling-Contract": "vnext" },
});

export type ModelingPlan = {
	id: string;
	domainId?: string;
	processId: string;
	layer: "ODS_RAW" | "ODS_STANDARDIZED" | "STG" | "DWD" | "DWS" | "ADS";
	modelingMode: "DESIGNER_GENERATED" | "DBT_MANAGED";
	targetGrain: string;
	status: string;
	revision: number;
};

export type ModelingWriteRequest<T> = T & {
	revision: number;
	idempotencyKey: string;
};

export type ModelingErrorCode =
	| "MODEL_REVISION_CONFLICT"
	| "MODEL_GRAIN_REQUIRED"
	| "MODEL_LAYER_INVALID"
	| "MODEL_OBJECT_NOT_FOUND"
	| "MODEL_PROCESS_MISMATCH"
	| "DBT_MANIFEST_INVALID"
	| "DBT_MODEL_NOT_FOUND"
	| "DBT_ARTIFACT_UNREADABLE";

export type ModelingApiError = {
	code: ModelingErrorCode;
	message: string;
	issues?: string[];
};

export type DbtManifestModel = {
	uniqueId: string;
	name: string;
	resourceType: "model" | "source" | "seed" | "snapshot";
	originalFilePath?: string;
	columns?: Record<string, { name: string; dataType?: string }>;
};

export type DbtManifestImportRequest = {
	projectId: string;
	manifestVersion: string;
	modelUniqueId: string;
	manifest: Record<string, unknown>;
	sql?: string;
	idempotencyKey: string;
};

export type DbtManifestImportResult = {
	modelSpecId: string;
	dbtUniqueId: string;
	status: "IMPORTED" | "LEGACY_READONLY";
	artifactCount: number;
};

export type ModelingArtifact = {
	artifactType: "SQL" | "SCHEMA" | "TEST" | "DOC";
	path: string;
	checksum?: string;
	status?: string;
};

export type ModelingDrift = {
	modelSpecId: string;
	status: "CLEAN" | "DRIFTED";
	issues: string[];
};

export type ModelingReleaseGate = {
	modelSpecId: string;
	publishable: boolean;
	status: "RELEASE_READY" | "BLOCKED";
	blockers: string[];
};

export type ModelingCompileResult = {
	modelSpecId: string;
	revision: number;
	status: "COMPILED" | "FAILED";
	artifacts: ModelingArtifact[];
	issues: string[];
};

export type ModelingRunState = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED" | "TIMED_OUT";

export type ModelingRun = {
	id: string;
	modelSpecId: string;
	revision: number;
	state: ModelingRunState;
	sourceBatchId?: string;
	addaxTaskId?: string;
	airflowDagId?: string;
	dbtRunId?: string;
	dbtSelector?: string;
	targetTable?: string;
	message?: string;
	logUrl?: string;
};

export type ModelingRunRequest = {
	modelSpecId: string;
	revision: number;
	sourceBatchId?: string;
	addaxTaskId?: string;
	airflowDagId?: string;
	dbtSelector?: string;
	targetTable?: string;
	idempotencyKey: string;
};

export type ModelingRunCallback = {
	state: ModelingRunState;
	addaxTaskId?: string;
	airflowRunId?: string;
	dbtRunId?: string;
	message?: string;
};

export type ModelingLineage = {
	modelSpecId: string;
	nodes: Array<{ id: string; name: string; layer?: string; kind?: string }>;
	edges: Array<{ from: string; to: string; type: string }>;
};

export type ModelingModelSpec = ModelSpec & {
	status?: string;
	compileStatus?: string;
};

export const listModelingBusinessObjects = (params?: { processId?: string; status?: string }) =>
	quiet<BusinessObject[]>({ url: "/modeling/vnext/business-objects", params });

export const createModelingBusinessObject = (data: ModelingWriteRequest<BusinessObject>) =>
	api.post<BusinessObject>(vnext({ url: "/modeling/vnext/business-objects", data }));

export const updateModelingBusinessObject = (id: string, data: ModelingWriteRequest<BusinessObject>) =>
	api.put<BusinessObject>(vnext({ url: `/modeling/vnext/business-objects/${encodeURIComponent(id)}`, data }));

export const listModelingPlans = (params?: { processId?: string; layer?: ModelingPlan["layer"] }) =>
	quiet<ModelingPlan[]>({ url: "/modeling/vnext/plans", params });

export const createModelingPlan = (data: ModelingWriteRequest<ModelingPlan>) =>
	api.post<ModelingPlan>(vnext({ url: "/modeling/vnext/plans", data }));

export const updateModelingPlan = (id: string, data: ModelingWriteRequest<ModelingPlan>) =>
	api.put<ModelingPlan>(vnext({ url: `/modeling/vnext/plans/${encodeURIComponent(id)}`, data }));

export const listModelingModelSpecs = (params?: { objectId?: string; processId?: string; layer?: ModelSpec["layer"] }) =>
	quiet<ModelingModelSpec[]>({ url: "/modeling/vnext/model-specs", params });

export const createModelingModelSpec = (data: ModelingWriteRequest<ModelingModelSpec>) =>
	api.post<ModelingModelSpec>(vnext({ url: "/modeling/vnext/model-specs", data }));

export const updateModelingModelSpec = (id: string, data: ModelingWriteRequest<ModelingModelSpec>) =>
	api.put<ModelingModelSpec>(vnext({ url: `/modeling/vnext/model-specs/${encodeURIComponent(id)}`, data }));

export const getModelSpecDependencies = (id: string) =>
	quiet<ModelingModelSpec[]>({ url: `/modeling/vnext/model-specs/${encodeURIComponent(id)}/dependencies` });

export const importModelingDbtManifest = (data: DbtManifestImportRequest) =>
	api.post<DbtManifestImportResult>(vnext({ url: "/modeling/vnext/dbt/import", data }));

export const listModelSpecArtifacts = (id: string) =>
	quiet<ModelingArtifact[]>({ url: `/modeling/vnext/model-specs/${encodeURIComponent(id)}/artifacts` });

export const getModelSpecDrift = (id: string) =>
	quiet<ModelingDrift>({ url: `/modeling/vnext/model-specs/${encodeURIComponent(id)}/drift` });

export const getModelSpecReleaseGate = (id: string) =>
	quiet<ModelingReleaseGate>({ url: `/modeling/vnext/model-specs/${encodeURIComponent(id)}/release-gate` });

export const compileModelSpec = (id: string, data: { revision: number; idempotencyKey: string }) =>
	api.post<ModelingCompileResult>(vnext({ url: `/modeling/vnext/model-specs/${encodeURIComponent(id)}/compile`, data }));

export const createModelingRun = (data: ModelingRunRequest) =>
	api.post<ModelingRun>(vnext({ url: "/modeling/vnext/runs", data }));

export const getModelingRun = (id: string) =>
	quiet<ModelingRun>({ url: `/modeling/vnext/runs/${encodeURIComponent(id)}` });

export const callbackModelingRun = (id: string, data: ModelingRunCallback) =>
	api.post<ModelingRun>(vnext({ url: `/modeling/vnext/runs/${encodeURIComponent(id)}/callback`, data }));

export const getModelingLineage = (id: string) =>
	quiet<ModelingLineage>({ url: `/modeling/vnext/lineage/${encodeURIComponent(id)}` });
