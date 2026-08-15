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
		columns?: Array<{
			name: string;
			description?: string | null;
			dataType?: string | null;
			role?: string | null;
		}>;
		dependencies?: string[];
		semantics?: {
			modelType?: string | null;
			layer?: string | null;
			domainCode?: string | null;
			grain?: { statement?: string | null; keys?: string[] } | null;
			sourceRefs?: Array<{ kind?: string | null; ref?: string | null; layer?: string | null }>;
			fieldRoles?: Record<string, string>;
			consumptionScenarios?: string[];
		} | null;
		conversion?: { mode?: string | null; reasonCodes?: string[] } | null;
	}>;
	issues?: ModelSpecImportIssue[];
	[key: string]: unknown;
};

export type ModelSpecImportSeverity = "ERROR" | "WARNING" | "INFO";
export type ModelSpecImportAction = "CREATE" | "UPDATE" | "SKIP" | "CONFLICT" | "BLOCKED";
export type ModelSpecImportConversionMode = "DESIGNER_GENERATED" | "DBT_BACKED" | "BLOCKED";
export type ModelSpecImportResultStatus = "CREATED" | "UPDATED" | "SKIPPED" | "FAILED" | "BLOCKED";

export type DbtCompatibilityIssue = {
	code: string;
	stage: string;
	category: string;
	message: string;
	retryable: boolean;
	recoveryAction: string;
	correlationId: string;
};

export type DbtPackageProfile = "ARTIFACT_RICH" | "SOURCE_ONLY" | "LEGACY_TSV";
export type DbtImportCandidateEligibility = "ELIGIBLE" | "REQUIRES_MAPPING" | "BLOCKED";

export type DbtInspectionReport = {
	packageProfile: DbtPackageProfile;
	summary: {
		discovered: number;
		technicalOnly: number;
		eligible: number;
		requiresMapping: number;
		blocked: number;
	};
	candidates: Array<{
		dbtUniqueId: string;
		eligibility: DbtImportCandidateEligibility;
		diagnosticCodes: string[];
	}>;
	diagnostics: Array<{
		code: string;
		severity: ModelSpecImportSeverity;
		axis: "INSPECTION" | "IMPORT_PROJECTION" | "MATERIALIZATION";
		blocksImport: boolean;
		modelUniqueId?: string | null;
		affectedUniqueIds: string[];
		message: string;
		recoveryAction?: string | null;
		retryable: boolean;
		correlationId?: string | null;
	}>;
};

export type DbtArchiveInspection = {
	package: ModelPackageJson;
	compatibility: {
		inspection: "SUPPORTED" | "UNSUPPORTED" | "UNKNOWN";
		importProjection: "IMPORTABLE" | "STRUCTURE_VIEW_ONLY" | "BLOCKED";
		materialization: "CERTIFIED" | "NOT_CERTIFIED" | "UNSUPPORTED" | "UNKNOWN";
		dbtCoreVersion?: string | null;
		manifestSchemaVersion?: string | null;
		adapterType?: string | null;
		adapterPackageVersion?: string | null;
		certificationProfileId?: string | null;
		issues: DbtCompatibilityIssue[];
	};
	report?: DbtInspectionReport;
	inspectionProof: string;
	proofExpiresAt: string;
};

export type ModelSpecImportIssue = {
	code: string;
	severity: ModelSpecImportSeverity | string;
	stage?: string | null;
	category?: string | null;
	retryable?: boolean;
	fieldPath?: string | null;
	modelUniqueId?: string | null;
	dependencyUniqueId?: string | null;
	message: string;
	recoveryAction?: string | null;
	correlationId?: string | null;
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
	inspectionProof: string;
	context: {
		planId: string;
		domainMappings: Record<string, string>;
		sourceMappings: Record<string, string>;
	};
	selectedUniqueIds: string[];
	semanticOverrides: ModelSpecImportSemanticOverride[];
	renameMappings: Array<{ oldUniqueId: string; newUniqueId: string }>;
};

export type ModelSpecImportSemanticOverride = {
	modelUniqueId: string;
	businessProcessId?: string;
	dataMartId?: string;
	subjectDomainId?: string;
	modelType?: string;
	layer?: string;
	businessName?: string;
	businessDefinition?: string;
	grain?: { statement?: string; keys: string[] };
	fieldRoles?: Record<string, string>;
	businessKeys?: string[];
	standardBindings?: Array<{
		fieldName: string;
		standardElementId?: string;
		standardElementVersion?: number;
		referenceCode?: string;
		referenceCodeVersion?: number;
		measurementUnitId?: string;
		measurementUnitVersion?: number;
		securityLevel?: string;
	}>;
	consumptionScenarios?: string[];
};

export type ModelSpecImportApplySummary = {
	selected: number;
	pending: number;
	succeeded: number;
	created: number;
	updated: number;
	skipped: number;
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
	appliedAction?: string | null;
	artifactCount: number;
	issues: ModelSpecImportIssue[];
	recordedAt?: string;
};

export type ModelSpecImportRunResult = {
	rootAttemptId: string;
	status: "RUNNING" | "SUCCESS" | "PARTIAL" | "FAILED" | "BLOCKED";
	summary: ModelSpecImportApplySummary;
	items: ModelSpecImportResultItem[];
};

export type ModelSpecImportApplyResult = {
	attemptId: string;
	runId: string;
	disposition?: "STARTED" | "REPLAY" | "RUNNING";
	status: "RUNNING" | "SUCCESS" | "PARTIAL" | "FAILED" | "BLOCKED";
	summary: ModelSpecImportApplySummary;
	items: ModelSpecImportResultItem[];
	overallRun: ModelSpecImportRunResult;
};

export type ModelSpecImportConflictResolution = "KEEP_CURRENT" | "ACCEPT_INCOMING" | "CANCEL";

export type ModelSpecImportRevisionPins = {
	modelRevision: number;
	modelChecksum: string;
	implementationRevision: number;
	implementationChecksum: string;
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
	return api.post<DbtArchiveInspection>(
		quietRequest({
			url: `${MODEL_SPEC_IMPORT_RESOURCE}/dbt/archive/inspect`,
			data,
			_skipErrorToast: true,
		}),
	);
};

export const previewModelSpecImport = (data: ModelSpecImportPreviewRequest) =>
	api.post<ModelSpecImportPreview>(
		quietRequest({
			url: `${MODEL_SPEC_IMPORT_RESOURCE}/dbt/preview`,
			data,
			_skipErrorToast: true,
		}),
	);

export const applyModelSpecImport = (data: {
	runId: string;
	previewHash: string;
	selectedUniqueIds: string[];
	idempotencyKey: string;
	conflictResolutions: Record<string, ModelSpecImportConflictResolution>;
}) =>
	api.post<ModelSpecImportApplyResult>(
		quietRequest({
			url: `${MODEL_SPEC_IMPORT_RESOURCE}/dbt/apply`,
			data,
			_skipErrorToast: true,
		}),
	);

export const getModelSpecImportPreviewRun = (runId: string) =>
	api.get<ModelSpecImportPreview>(
		quietRequest({
			url: `${MODEL_SPEC_IMPORT_RESOURCE}/${encodeURIComponent(runId)}`,
			_skipErrorToast: true,
		}),
	);

export const getModelSpecImportApplyResult = (runId: string) =>
	api.get<ModelSpecImportApplyResult>(
		quietRequest({
			url: `${MODEL_SPEC_IMPORT_RESOURCE}/${encodeURIComponent(runId)}/apply`,
			_skipErrorToast: true,
		}),
	);

export const retryModelSpecImport = (runId: string, data: { previewHash: string; idempotencyKey: string }) =>
	api.post<ModelSpecImportApplyResult>(
		quietRequest({
			url: `${MODEL_SPEC_IMPORT_RESOURCE}/${encodeURIComponent(runId)}/retry`,
			data,
			_skipErrorToast: true,
		}),
	);

export const forwardUndoModelSpecImport = (data: {
	targetAttemptId: string;
	selectedItemIds: string[];
	expectedCurrentRevisions: Record<string, ModelSpecImportRevisionPins>;
	idempotencyKey: string;
}) =>
	api.post<ModelSpecImportApplyResult>(
		quietRequest({
			url: `${MODEL_SPEC_IMPORT_RESOURCE}/dbt/forward-undo`,
			data,
			_skipErrorToast: true,
		}),
	);
