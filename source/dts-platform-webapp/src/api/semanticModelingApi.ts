import api from "@/api/apiClient";

const quiet = <T>(config: Record<string, unknown>) =>
	api.get<T>({
		...config,
		_skipErrorToast: true,
	} as any);

export type SemanticSubjectDomain = {
	id: string;
	code: string;
	name: string;
	description?: string;
	status?: string;
	governanceDomainId?: string;
	governanceDomainCode?: string;
	governanceDomainName?: string;
};

export type SemanticBusinessObject = {
	id: string;
	domainId?: string;
	code: string;
	name: string;
	description?: string;
	primaryKey?: string;
	mainTable?: string;
};

export type SemanticObjectTableMapping = {
	id?: string;
	objectId?: string;
	tableName: string;
	tableRole?: string;
	joinExpression?: string;
	sortOrder?: number;
};

export type SemanticDimension = {
	id: string;
	objectId?: string;
	code: string;
	name: string;
	fieldName?: string;
	dataType?: string;
	semanticType?: string;
	status?: string;
};

export type SemanticMetric = {
	id: string;
	objectId?: string | null;
	code: string;
	name: string;
	formulaType?: string;
	formulaJson?: string;
	format?: string;
	unit?: string;
	status?: string;
};

export type SemanticModel = {
	id: string;
	objectId?: string;
	type?: "DWS" | "ADS" | string;
	name: string;
	tableName?: string;
	description?: string;
	grain?: string;
	materialization?: string;
	refreshCycle?: string;
	status?: string;
	reviewStatus?: string;
	submittedBy?: string;
	submittedAt?: string;
	reviewedBy?: string;
	reviewedAt?: string;
	reviewComment?: string;
};

export type SemanticGeneratedArtifact = {
	id: string;
	modelId?: string;
	artifactType?: string;
	path?: string;
	content?: string;
	status?: string;
};

export type SemanticModelBinding = {
	modelId: string;
	dimensionIds: string[];
	metricIds: string[];
};

export type SemanticModelPreview = {
	success: boolean;
	sql: string;
	headers: string[];
	rows: Array<Record<string, any>>;
	rowCount?: number;
	durationMs?: number;
	errorMessage?: string;
};

export type SemanticModelReviewLog = {
	id: string;
	modelId: string;
	action: string;
	actor?: string;
	comment?: string;
	createdDate?: string;
};

export type SemanticModelRun = {
	id: string;
	modelId: string;
	runType?: string;
	selector?: string;
	dagId?: string;
	externalRunId?: string;
	status?: string;
	triggeredBy?: string;
	startedAt?: string;
	finishedAt?: string;
	durationMs?: number;
	message?: string;
	payloadJson?: string;
	createdDate?: string;
};

export type SemanticWorkbenchStep = {
	key: string;
	title: string;
	path: string;
	primaryApi?: string;
	total?: number;
	ready?: number;
	blocked?: number;
	status?: string;
	nextAction?: string;
};

export type SemanticMenuDiagnostic = {
	key: string;
	title: string;
	path: string;
	apis?: string[];
	status?: string;
};

export type SemanticWorkbenchOverview = {
	summary?: Record<string, number>;
	steps?: SemanticWorkbenchStep[];
	menus?: SemanticMenuDiagnostic[];
	generatedAt?: string;
};

export const getSemanticWorkbenchOverview = () =>
	quiet<SemanticWorkbenchOverview>({ url: "/semantic/workbench" });
export const getSemanticMenuDiagnostics = () =>
	quiet<SemanticMenuDiagnostic[]>({ url: "/semantic/menu-diagnostics" });

export const listSemanticSubjectDomains = () =>
	quiet<SemanticSubjectDomain[]>({ url: "/semantic/subject-domains" });
export const createSemanticSubjectDomain = (data: Partial<SemanticSubjectDomain>) =>
	api.post<SemanticSubjectDomain>({ url: "/semantic/subject-domains", data });
export const updateSemanticSubjectDomain = (id: string, data: Partial<SemanticSubjectDomain>) =>
	api.put<SemanticSubjectDomain>({ url: `/semantic/subject-domains/${encodeURIComponent(id)}`, data });

export const listSemanticBusinessObjects = (params?: { domainId?: string }) =>
	quiet<SemanticBusinessObject[]>({ url: "/semantic/business-objects", params });
export const createSemanticBusinessObject = (data: Partial<SemanticBusinessObject>) =>
	api.post<SemanticBusinessObject>({ url: "/semantic/business-objects", data });
export const updateSemanticBusinessObject = (id: string, data: Partial<SemanticBusinessObject>) =>
	api.put<SemanticBusinessObject>({ url: `/semantic/business-objects/${encodeURIComponent(id)}`, data });
export const listSemanticObjectTableMappings = (objectId: string) =>
	quiet<SemanticObjectTableMapping[]>({ url: `/semantic/business-objects/${encodeURIComponent(objectId)}/table-mappings` });
export const saveSemanticObjectTableMappings = (objectId: string, mappings: SemanticObjectTableMapping[]) =>
	api.put<SemanticObjectTableMapping[]>({
		url: `/semantic/business-objects/${encodeURIComponent(objectId)}/table-mappings`,
		data: { mappings },
	});

export const listSemanticDimensions = (params?: { objectId?: string }) =>
	quiet<SemanticDimension[]>({ url: "/semantic/dimensions", params });
export const createSemanticDimension = (data: Partial<SemanticDimension>) =>
	api.post<SemanticDimension>({ url: "/semantic/dimensions", data });
export const updateSemanticDimension = (id: string, data: Partial<SemanticDimension>) =>
	api.put<SemanticDimension>({ url: `/semantic/dimensions/${encodeURIComponent(id)}`, data });

export const listSemanticMetrics = (params?: { objectId?: string }) =>
	quiet<SemanticMetric[]>({ url: "/semantic/metrics", params });
export const createSemanticMetric = (data: Partial<SemanticMetric> & { formulaJson?: string; unit?: string }) =>
	api.post<SemanticMetric>({ url: "/semantic/metrics", data });
export const updateSemanticMetric = (id: string, data: Partial<SemanticMetric> & { formulaJson?: string; unit?: string }) =>
	api.put<SemanticMetric>({ url: `/semantic/metrics/${encodeURIComponent(id)}`, data });

export const listSemanticModels = (params?: { type?: "DWS" | "ADS" }) =>
	quiet<SemanticModel[]>({ url: "/semantic/models", params });
export const createSemanticModel = (data: Partial<SemanticModel> & {
	objectId?: string;
	description?: string;
	grain?: string;
	materialization?: string;
	refreshCycle?: string;
}) => api.post<SemanticModel>({ url: "/semantic/models", data });
export const updateSemanticModel = (id: string, data: Partial<SemanticModel> & {
	objectId?: string;
	description?: string;
	grain?: string;
	materialization?: string;
	refreshCycle?: string;
}) => api.put<SemanticModel>({ url: `/semantic/models/${encodeURIComponent(id)}`, data });
export const getSemanticModelBindings = (modelId: string) =>
	quiet<SemanticModelBinding>({ url: `/semantic/models/${encodeURIComponent(modelId)}/bindings` });
export const saveSemanticModelBindings = (modelId: string, data: { dimensionIds: string[]; metricIds: string[] }) =>
	api.put<SemanticModelBinding>({ url: `/semantic/models/${encodeURIComponent(modelId)}/bindings`, data });
export const generateSemanticModelArtifacts = (modelId: string) =>
	api.post<{ model: SemanticModel; artifacts: SemanticGeneratedArtifact[] }>({
		url: `/semantic/models/${encodeURIComponent(modelId)}/generate-artifacts`,
	});
export const previewSemanticModelData = (modelId: string, limit = 100) =>
	api.post<SemanticModelPreview>({
		url: `/semantic/models/${encodeURIComponent(modelId)}/preview-data`,
		params: { limit },
	});
export const listSemanticModelReviewLogs = (modelId: string) =>
	quiet<SemanticModelReviewLog[]>({ url: `/semantic/models/${encodeURIComponent(modelId)}/review-logs` });
export const submitSemanticModelReview = (modelId: string, comment?: string) =>
	api.post<SemanticModel>({ url: `/semantic/models/${encodeURIComponent(modelId)}/submit-review`, data: { comment } });
export const approveSemanticModelReview = (modelId: string, comment?: string) =>
	api.post<SemanticModel>({ url: `/semantic/models/${encodeURIComponent(modelId)}/approve-review`, data: { comment } });
export const rejectSemanticModelReview = (modelId: string, comment: string) =>
	api.post<SemanticModel>({ url: `/semantic/models/${encodeURIComponent(modelId)}/reject-review`, data: { comment } });
export const listSemanticModelRuns = (modelId: string) =>
	quiet<SemanticModelRun[]>({ url: `/semantic/models/${encodeURIComponent(modelId)}/runs` });
export const triggerSemanticModelRun = (modelId: string, data?: { runType?: string; dagSelector?: string; target?: string; vars?: Record<string, any> }) =>
	api.post<SemanticModelRun>({ url: `/semantic/models/${encodeURIComponent(modelId)}/runs`, data: data || {} });
export const updateSemanticModelRun = (modelId: string, runId: string, data: {
	status?: string;
	externalRunId?: string;
	durationMs?: number;
	message?: string;
	payload?: Record<string, any>;
}) =>
	api.put<SemanticModelRun>({
		url: `/semantic/models/${encodeURIComponent(modelId)}/runs/${encodeURIComponent(runId)}`,
		data,
	});
export const publishSemanticModelToDbt = (modelId: string) =>
	api.post<{ modelId: string; publishedPaths: string[] }>({
		url: `/semantic/models/${encodeURIComponent(modelId)}/publish-dbt`,
	});
export const registerSemanticBiDataset = (modelId: string) =>
	api.post<{ modelId: string; datasetId: string; datasetName: string; versionNo: number }>({
		url: `/semantic/models/${encodeURIComponent(modelId)}/register-bi-dataset`,
	});
export const registerSemanticLineage = (modelId: string) =>
	api.post<{ modelId: string; lineageId: string; upstreamDatasetId: string; downstreamDatasetId: string }>({
		url: `/semantic/models/${encodeURIComponent(modelId)}/register-lineage`,
	});

export const listSemanticGeneratedArtifacts = (params?: { modelId?: string }) =>
	quiet<SemanticGeneratedArtifact[]>({ url: "/semantic/generated-artifacts", params });
