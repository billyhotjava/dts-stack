import api from "@/api/apiClient";

const MODEL_SPEC_IMPORT_RESOURCE = "/modeling/model-spec-imports";

export type ModelPackageJson = {
	schemaVersion: string;
	packageId: string;
	packageChecksum: string;
	dbt: {
		projectName: string;
		projectVersion?: string | null;
		manifestVersion?: string | null;
		adapterType?: string | null;
	};
	defaults?: { planRef?: string | null; domainRef?: string | null } | null;
	sources: Array<{
		dbtUniqueId: string;
		name: string;
		resourcePath?: string | null;
	}>;
	technicalNodes?: Array<{ dbtUniqueId: string; name: string; resourceType?: string | null }>;
	models: Array<{
		dbtUniqueId: string;
		name: string;
		description?: string | null;
		dependencies?: string[];
		semantics?: {
			modelType?: string | null;
			layer?: string | null;
			domainCode?: string | null;
			grain?: { statement?: string | null; keys?: string[] } | null;
			sourceRefs?: Array<{ kind?: string | null; ref?: string | null; layer?: string | null }>;
		} | null;
		conversion?: { mode?: string | null; reasonCodes?: string[] } | null;
	}>;
	issues?: ModelSpecImportIssue[];
	[key: string]: unknown;
};

export type ModelSpecImportSeverity = "ERROR" | "WARNING" | "INFO";
export type ModelSpecImportAction = "CREATE" | "UPDATE" | "SKIP" | "CONFLICT" | "BLOCKED";
export type ModelSpecImportConversionMode = "DESIGNER_GENERATED" | "DBT_BACKED" | "BLOCKED";
export type ModelSpecImportResultStatus = "CREATED" | "UPDATED" | "SKIPPED" | "REPLAYED" | "FAILED" | "BLOCKED";

export type ModelSpecImportIssue = {
	code: string;
	severity: ModelSpecImportSeverity | string;
	fieldPath?: string | null;
	modelUniqueId?: string | null;
	message: string;
	recoveryAction?: string | null;
};

export type ModelSpecImportPreviewSummary = {
	total: number;
	ready: number;
	blocked: number;
	create: number;
	update: number;
	skip: number;
	conflict: number;
};

export type ModelSpecImportPreviewItem = {
	dbtUniqueId: string;
	action: ModelSpecImportAction;
	conversionMode: ModelSpecImportConversionMode;
	proposedModelSpec?: Record<string, unknown> | null;
	proposedImplementation?: Record<string, unknown> | null;
	issues: ModelSpecImportIssue[];
};

export type ModelSpecImportPreview = {
	runId: string;
	planId?: string;
	previewHash: string;
	applyPayloadChecksum?: string;
	status?: "PREVIEWED" | "BLOCKED" | "EXPIRED";
	expiresAt?: string;
	summary: ModelSpecImportPreviewSummary;
	items: ModelSpecImportPreviewItem[];
};

export type ModelSpecImportPreviewRequest = {
	package: ModelPackageJson;
	context: {
		planId: string;
		domainMappings: Record<string, string>;
		sourceMappings: Record<string, string>;
	};
	selectedUniqueIds: string[];
};

export type ModelSpecImportApplySummary = {
	total: number;
	created: number;
	updated: number;
	skipped: number;
	replayed: number;
	failed: number;
	blocked: number;
};

export type ModelSpecImportResultItem = {
	resultId?: string;
	sequence?: number;
	dbtUniqueId: string;
	status: ModelSpecImportResultStatus;
	modelSpecId?: string | null;
	revision?: number | null;
	modelChecksum?: string | null;
	implementationRevision?: number | null;
	implementationChecksum?: string | null;
	artifactCount: number;
	issues: ModelSpecImportIssue[];
	recordedAt?: string;
};

export type ModelSpecImportApplyResult = {
	attemptId: string;
	runId: string;
	disposition?: "STARTED" | "REPLAY" | "RUNNING";
	status: "RUNNING" | "SUCCEEDED" | "PARTIAL" | "FAILED";
	summary: ModelSpecImportApplySummary;
	items: ModelSpecImportResultItem[];
};

type QuietAxiosRequestConfig = {
	url: string;
	data?: unknown;
	_skipErrorToast: true;
};

const quietRequest = <T extends QuietAxiosRequestConfig>(config: T): T => config;

export const inspectDbtModelArchive = (archive: File) => {
	const data = new FormData();
	data.append("archive", archive);
	return api.post<ModelPackageJson>(quietRequest({
		url: `${MODEL_SPEC_IMPORT_RESOURCE}/dbt/archive/inspect`,
		data,
		_skipErrorToast: true,
	}));
};

export const previewModelSpecImport = (data: ModelSpecImportPreviewRequest) =>
	api.post<ModelSpecImportPreview>(quietRequest({
		url: `${MODEL_SPEC_IMPORT_RESOURCE}/dbt/preview`,
		data,
		_skipErrorToast: true,
	}));

export const applyModelSpecImport = (data: {
	runId: string;
	previewHash: string;
	selectedUniqueIds: string[];
	idempotencyKey: string;
}) =>
	api.post<ModelSpecImportApplyResult>(quietRequest({
		url: `${MODEL_SPEC_IMPORT_RESOURCE}/dbt/apply`,
		data,
		_skipErrorToast: true,
	}));

export const getModelSpecImportPreviewRun = (runId: string) =>
	api.get<ModelSpecImportPreview>(quietRequest({
		url: `${MODEL_SPEC_IMPORT_RESOURCE}/${encodeURIComponent(runId)}`,
		_skipErrorToast: true,
	}));

export const getModelSpecImportApplyResult = (runId: string) =>
	api.get<ModelSpecImportApplyResult>(quietRequest({
		url: `${MODEL_SPEC_IMPORT_RESOURCE}/${encodeURIComponent(runId)}/apply`,
		_skipErrorToast: true,
	}));

export const retryModelSpecImport = (runId: string, data: { previewHash: string; idempotencyKey: string }) =>
	api.post<ModelSpecImportApplyResult>(quietRequest({
		url: `${MODEL_SPEC_IMPORT_RESOURCE}/${encodeURIComponent(runId)}/retry`,
		data,
		_skipErrorToast: true,
	}));
