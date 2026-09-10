import api from "@/api/apiClient";
import type {
	CreateDimensionDefinitionCommand,
	DimensionDefinitionView,
} from "@/features/modeling/contracts/dimensionDefinitionContract";
import type {
	ModelImplementationCasToken,
	ModelImplementationView,
	ModelImplementationWriteCommand,
} from "@/features/modeling/contracts/modelImplementationContract";
import { toModelImplementationEtag } from "@/features/modeling/contracts/modelImplementationContract";
import type {
	CanonicalModelSpecView,
	CreateModelSpecCommand,
	ModelSpecCasToken,
	ModelSpecCollections,
	ModelSpecDimensionHierarchy,
	ModelSpecLayer,
	ModelSpecRevisionConflictDetails,
	ModelSpecScdPolicy,
	ModelSpecType,
	ModelSpecView,
	UpdateModelSpecCommand,
} from "@/features/modeling/contracts/modelSpecV2Contract";
import { toModelSpecEtag } from "@/features/modeling/contracts/modelSpecV2Contract";

const MODEL_SPEC_RESOURCE = "/modeling/model-specs";

export type ModelSpecListParams = {
	planId?: string;
	domainId?: string;
	modelType?: ModelSpecType;
	layer?: ModelSpecLayer;
};

export type ModelWorkbenchCatalogEntry = {
	kind: "DIMENSION_DEFINITION" | "MODEL_SPEC";
	id: string;
	name: string;
	code: string;
	planId: string | null;
	domainId: string;
	objectType: "DIMENSION_DEFINITION" | ModelSpecType;
	layer: ModelSpecLayer | null;
	status: string;
	revision: number;
};

export type ModelWorkbenchCatalogPage = {
	content: ModelWorkbenchCatalogEntry[];
	totalElements: number;
	page: number;
	size: number;
	totalPages: number;
};

export type ModelWorkbenchCatalogPageParams = {
	page: number;
	size: number;
	query?: string;
	planId?: string;
	domainId?: string;
	objectType?: ModelWorkbenchCatalogEntry["objectType"];
	layer?: ModelSpecLayer;
	status?: string;
};

export type CreateDimensionModelCommand = {
	operationId: string;
	definitionBinding:
		| {
				mode: "CREATE";
				definition: Omit<CreateDimensionDefinitionCommand, "idempotencyKey">;
		  }
		| {
				mode: "EXISTING";
				dimensionDefinitionRef: { dimensionDefinitionId: string; revision: number };
		  };
	modelSpec: Omit<
		UpdateModelSpecCommand,
		"modelType" | "layer" | "implementationMode" | "dimensionProfile" | keyof ModelSpecCollections
	> &
		ModelSpecCollections & {
			modelType: "DIMENSION";
			layer: "DWD";
			implementationMode: "DESIGNER_GENERATED";
			dimensionProfile: {
				hierarchies: ModelSpecDimensionHierarchy[];
				scdPolicy: ModelSpecScdPolicy;
			};
		};
};

export type CreateDimensionModelResult = {
	operationId: string;
	bindingMode: "CREATE" | "EXISTING";
	dimensionDefinitionRevision: DimensionDefinitionView;
	modelSpecRevision: CanonicalModelSpecView;
	currentModelSpec: CanonicalModelSpecView;
	replayed: boolean;
};

export type DimensionModelOperationView = CreateDimensionModelResult;

export type ModelDraftOperationCommand = {
	saveMode?: "DEFINITION_ONLY" | "WITH_IMPLEMENTATION";
	create: CreateModelSpecCommand;
	modelSpec: UpdateModelSpecCommand;
	implementation: ModelImplementationWriteCommand | null;
};

export type ModelDraftOperationResult = {
	model: CanonicalModelSpecView;
	implementation: ModelImplementationView | null;
	replayed: boolean;
};

export type ModelSpecRevisionConflict = {
	code: "MODEL_SPEC_REVISION_CONFLICT";
	data: ModelSpecRevisionConflictDetails;
};

export type ModelSpecStage = "DRAFT_SAVE" | "DESIGNED" | "IMPLEMENTATION_READY" | "RELEASE_READY";
export type ModelSpecGateStatus = "READY" | "BLOCKED";
export type ModelSpecGateBlocker = {
	code: string;
	field: string;
	message: string;
	repairRoute: string;
};
export type ModelSpecStageGate = {
	modelSpecId: string;
	revision: number;
	checksum: string;
	stage: ModelSpecStage;
	status: ModelSpecGateStatus;
	blockers: ModelSpecGateBlocker[];
};

export type ModelServingSyncStatus = {
	modelSpecId: string;
	catalogAssetKey?: string | null;
	syncStatus: "NOT_REGISTERED" | "SYNC_PENDING" | "SYNCED" | "SYNC_FAILED";
	syncAttempts: number;
	lastSyncError?: string | null;
	nextSyncAt?: string | null;
	updatedAt?: string | null;
	version: number;
	servingReady: boolean;
	latestPublishedRef?: Record<string, unknown> | null;
	servingRef?: Record<string, unknown> | null;
};

export type ModelServingSyncRetryResult = {
	status: ModelServingSyncStatus;
	replayed: boolean;
	correlationId?: string | null;
};

export type ModelSpecReclassificationPreview = {
	eligible: boolean;
	fromType: ModelSpecType;
	toType: ModelSpecType;
	targetLayer: ModelSpecLayer;
	retainedFields: string[];
	requiredFields: string[];
	clearFields: string[];
	reasonCodes: string[];
	currentRevision: number;
	checksum: string;
};

export type ModelSpecReclassificationRequest = {
	targetType: ModelSpecType;
	dimensionDefinitionRef?: { dimensionDefinitionId: string; revision: number } | null;
};

export type ModelImplementationMigrationStatus = "ELIGIBLE" | "CONFLICT" | "ORPHAN" | "SKIPPED";
export type ModelImplementationMigrationDecision = {
	modelSpecId?: string | null;
	status: ModelImplementationMigrationStatus;
	reasonCode: string;
	modelRevision: number;
	currentImplementationRevision?: number | null;
	currentImplementationChecksum?: string | null;
	targetInputMode?: "PHYSICAL_ASSET" | "UPSTREAM_MODEL" | "GENERATED" | null;
	targetSettings: Record<string, unknown>;
};
export type ModelImplementationMigrationResult = {
	decision: ModelImplementationMigrationDecision;
	applied: boolean;
	targetImplementationRevision?: number | null;
	targetImplementationChecksum?: string | null;
};
export type ModelImplementationMigrationBatch = {
	previewChecksum: string;
	total: number;
	eligible: number;
	conflict: number;
	orphan: number;
	skipped: number;
	applied: number;
	results: ModelImplementationMigrationResult[];
};
export type ModelImplementationMigrationRollback = {
	previewChecksum: string;
	compatibilityReadRetained: boolean;
	deletedImplementationRevisions: number;
	reasonCode: string;
};

export type ModelSpecNamingValidation = {
	valid: boolean;
	normalizedName?: string | null;
	issues: Array<{ field: string; code: string; message: string }>;
};

export type ModelSpecDependencyState = "CURRENT" | "STALE" | "UNKNOWN";
export type ModelSpecDependencyNode = {
	modelSpecId: string;
	pinnedRevision: number;
	currentRevision: number | null;
	name: string | null;
	modelType: ModelSpecType | null;
	status: string | null;
	restricted: boolean;
};
export type ModelSpecDependencyEdge = {
	fromModelSpecId: string;
	toModelSpecId: string;
	pinnedRevision: number;
	currentRevision: number | null;
	state: ModelSpecDependencyState;
};
export type ModelSpecDependencyGraph = {
	rootModelSpecId: string;
	nodes: ModelSpecDependencyNode[];
	edges: ModelSpecDependencyEdge[];
};

export type ModelLifecycleEventType =
	| "COMPILE"
	| "TEST"
	| "REVIEW_SUBMITTED"
	| "REVIEW_APPROVED"
	| "RELEASE"
	| "ROLLBACK"
	| "RUN";
export type ModelLifecycleEvent = {
	id: string;
	modelSpecId: string;
	planId: string;
	revision: number;
	modelChecksum: string;
	eventType: ModelLifecycleEventType;
	status: string;
	idempotencyKey: string;
	actorId?: string | null;
	comment?: string | null;
	externalRef?: string | null;
	details: Record<string, unknown>;
	createdAt: string;
};
export type ModelImplementationOwner = ModelImplementationView;
export type ModelLifecycleArtifact = {
	id: string;
	modelSpecId: string;
	planId: string;
	revision: number;
	modelChecksum: string;
	ownership: CanonicalModelSpecView["implementationMode"];
	artifactType: string;
	path: string;
	checksum: string;
	status: string;
	implementationRevision: number;
	nodeKind: string;
	materialization: string;
	physicalAssetRef?: string | null;
};
export type ModelLifecycleTimeline = {
	implementation: ModelImplementationOwner | null;
	artifacts: ModelLifecycleArtifact[];
	events: ModelLifecycleEvent[];
};
export type ModelLifecycleRelease = {
	release: ModelLifecycleEvent;
	status: string;
	publishedRevision: number;
	modelChecksum: string;
	registrations: Array<{
		id: string;
		releaseEventId: string;
		step: "CATALOG_ASSET" | "BI_DATASET" | "LINEAGE";
		status: string;
		externalRef?: string | null;
		attemptCount: number;
		errorMessage?: string | null;
		lastAttemptAt: string;
	}>;
};

export const listModelSpecs = (params?: ModelSpecListParams) =>
	api.get<ModelSpecView[]>({ url: MODEL_SPEC_RESOURCE, params, _skipErrorToast: true } as any);

export const listModelWorkbenchCatalogPage = (params: ModelWorkbenchCatalogPageParams) =>
	api.get<ModelWorkbenchCatalogPage>({
		url: `${MODEL_SPEC_RESOURCE}/workbench`,
		params,
		_skipErrorToast: true,
	} as any);

export const getModelSpec = (id: string) =>
	api.get<ModelSpecView>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(id)}`,
		_skipErrorToast: true,
	} as any);

export const getModelSpecRevision = (id: string, revision: number) =>
	api.get<ModelSpecView>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(id)}/revisions/${encodeURIComponent(String(revision))}`,
		_skipErrorToast: true,
	} as any);

export const getModelSpecStageGates = (id: string) =>
	api.get<ModelSpecStageGate[]>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(id)}/stage-gates`,
		_skipErrorToast: true,
	} as any);

export const getModelServingSyncStatus = (id: string) =>
	api.get<ModelServingSyncStatus>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(id)}/serving-sync`,
		_skipErrorToast: true,
	} as any);

export const getModelServingSyncStatuses = (modelSpecIds: string[]) =>
	api.get<ModelServingSyncStatus[]>({
		url: `${MODEL_SPEC_RESOURCE}/serving-sync`,
		params: { modelSpecIds: modelSpecIds.join(",") },
		_skipErrorToast: true,
	} as any);

export const retryModelServingSync = (status: ModelServingSyncStatus) =>
	api.post<ModelServingSyncRetryResult>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(status.modelSpecId)}/serving-sync/retry`,
		headers: { "If-Match": `"model-serving-sync:${status.modelSpecId}:${status.version}"` },
		_skipErrorToast: true,
	} as any);

export const getModelSpecDependencies = (id: string) =>
	api.get<ModelSpecDependencyGraph>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(id)}/dependencies`,
		_skipErrorToast: true,
	} as any);

export const createModelSpec = (data: CreateModelSpecCommand) =>
	api.post<CanonicalModelSpecView>({ url: MODEL_SPEC_RESOURCE, data, _skipErrorToast: true } as any);

export const saveModelDraftOperation = (data: ModelDraftOperationCommand) =>
	api.post<ModelDraftOperationResult>({
		url: `${MODEL_SPEC_RESOURCE}/draft-operations`,
		data: {
			...data,
			create: { ...data.create, planId: data.create.planId || undefined },
			modelSpec: { ...data.modelSpec, planId: data.modelSpec.planId || undefined },
		},
		_skipErrorToast: true,
	} as any);

export const createDimensionModel = (data: CreateDimensionModelCommand, signal?: AbortSignal) =>
	api.post<CreateDimensionModelResult>({
		url: `${MODEL_SPEC_RESOURCE}/dimension`,
		data,
		signal,
		_skipErrorToast: true,
	} as any);

export const getDimensionModelOperation = (operationId: string, signal?: AbortSignal) =>
	api.get<DimensionModelOperationView>({
		url: `${MODEL_SPEC_RESOURCE}/dimension/operations/${encodeURIComponent(operationId)}`,
		signal,
		_skipErrorToast: true,
	} as any);

export const validateModelSpecPhysicalName = (
	planId: string,
	modelType: ModelSpecType,
	layer: ModelSpecLayer,
	physicalName: string,
) =>
	api.post<ModelSpecNamingValidation>({
		url: `/modeling/warehouse-plans/${encodeURIComponent(planId)}/naming/validate`,
		data: { modelType, layer, physicalName },
		_skipErrorToast: true,
	} as any);

export const updateModelSpec = (expected: ModelSpecCasToken, data: UpdateModelSpecCommand) =>
	api.put<CanonicalModelSpecView>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(expected.id)}`,
		headers: { "If-Match": toModelSpecEtag(expected) },
		data,
		_skipErrorToast: true,
	} as any);

export type StandardElementBindingPatch = {
	fieldName: string;
	standardElementId: string;
	standardElementVersion: number;
};

export const applyModelSpecStandardElementBindings = (
	expected: ModelSpecCasToken,
	bindings: StandardElementBindingPatch[],
) =>
	api.post<CanonicalModelSpecView>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(expected.id)}/standard-element-bindings`,
		headers: { "If-Match": toModelSpecEtag(expected) },
		data: { bindings },
		_skipErrorToast: true,
	} as any);

export const previewModelSpecReclassification = (id: string, data: ModelSpecReclassificationRequest) =>
	api.post<ModelSpecReclassificationPreview>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(id)}/reclassify-preview`,
		data,
		_skipErrorToast: true,
	} as any);

export const reclassifyModelSpec = (
	expected: ModelSpecCasToken,
	data: ModelSpecReclassificationRequest & { acceptedClearFields: string[]; idempotencyKey: string },
) =>
	api.post<CanonicalModelSpecView>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(expected.id)}/reclassify`,
		headers: { "If-Match": toModelSpecEtag(expected) },
		data,
		_skipErrorToast: true,
	} as any);

export const previewModelImplementationMigrations = (modelSpecIds: string[]) =>
	api.post<ModelImplementationMigrationBatch>({
		url: `${MODEL_SPEC_RESOURCE}/implementation-migrations/dry-run`,
		data: { modelSpecIds },
		_skipErrorToast: true,
	} as any);

export const applyModelImplementationMigrations = (modelSpecIds: string[], previewChecksum: string) =>
	api.post<ModelImplementationMigrationBatch>({
		url: `${MODEL_SPEC_RESOURCE}/implementation-migrations/apply`,
		data: { modelSpecIds, previewChecksum },
		_skipErrorToast: true,
	} as any);

export const rollbackModelImplementationMigration = (previewChecksum: string) =>
	api.post<ModelImplementationMigrationRollback>({
		url: `${MODEL_SPEC_RESOURCE}/implementation-migrations/rollback`,
		data: { previewChecksum },
		_skipErrorToast: true,
	} as any);

export const deleteModelSpec = (expected: ModelSpecCasToken) =>
	api.delete<void>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(expected.id)}`,
		headers: { "If-Match": toModelSpecEtag(expected) },
		_skipErrorToast: true,
	} as any);

export const archiveModelSpec = (expected: ModelSpecCasToken) =>
	api.post<CanonicalModelSpecView>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(expected.id)}/archive`,
		headers: { "If-Match": toModelSpecEtag(expected) },
		_skipErrorToast: true,
	} as any);

export const bindModelSpecMetricRef = (expected: ModelSpecCasToken, data: { metricId: string; version: number }) =>
	api.put<CanonicalModelSpecView>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(expected.id)}/metric-refs`,
		headers: { "If-Match": toModelSpecEtag(expected) },
		data,
		_skipErrorToast: true,
	} as any);

const lifecycleUrl = (id: string, suffix = "") => `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(id)}/lifecycle${suffix}`;
const lifecycleHeaders = (expected: ModelSpecCasToken) => ({ "If-Match": toModelSpecEtag(expected) });
const lifecycleWriteHeaders = (expected: ModelSpecCasToken, implementation: ModelImplementationCasToken | null) => ({
	...lifecycleHeaders(expected),
	"If-Match-Implementation": implementation ? toModelImplementationEtag(implementation) : "*",
});

export const claimModelImplementation = (
	expected: ModelSpecCasToken,
	implementation: ModelImplementationCasToken | null,
	data: {
		ownership: CanonicalModelSpecView["implementationMode"];
		projectKey: string;
		dbtUniqueId: string;
		idempotencyKey: string;
	},
) =>
	api.put<ModelImplementationOwner>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(expected.id)}/implementation`,
		headers: lifecycleWriteHeaders(expected, implementation),
		data,
		_skipErrorToast: true,
	} as any);

export const compileModelLifecycle = (
	expected: ModelSpecCasToken,
	implementation: ModelImplementationCasToken,
	idempotencyKey: string,
) =>
	api.post<{
		implementation: ModelImplementationOwner;
		event: ModelLifecycleEvent;
		artifacts: ModelLifecycleArtifact[];
	}>({
		url: lifecycleUrl(expected.id, "/compile"),
		headers: lifecycleWriteHeaders(expected, implementation),
		data: { idempotencyKey },
		_skipErrorToast: true,
	} as any);

export const recordModelTestEvidence = (
	expected: ModelSpecCasToken,
	implementation: ModelImplementationCasToken,
	data: { status?: "PASSED" | "FAILED"; externalRunId: string; comment?: string; idempotencyKey: string },
) =>
	api.post<ModelLifecycleEvent>({
		url: lifecycleUrl(expected.id, "/tests"),
		headers: lifecycleWriteHeaders(expected, implementation),
		data,
		_skipErrorToast: true,
	} as any);

export const submitModelReview = (
	expected: ModelSpecCasToken,
	implementation: ModelImplementationCasToken,
	data: { comment?: string; idempotencyKey: string },
) =>
	api.post<ModelLifecycleEvent>({
		url: lifecycleUrl(expected.id, "/reviews"),
		headers: lifecycleWriteHeaders(expected, implementation),
		data,
		_skipErrorToast: true,
	} as any);

export const approveModelReview = (
	expected: ModelSpecCasToken,
	implementation: ModelImplementationCasToken,
	data: { comment?: string; idempotencyKey: string },
) =>
	api.post<ModelLifecycleEvent>({
		url: lifecycleUrl(expected.id, "/reviews/approve"),
		headers: lifecycleWriteHeaders(expected, implementation),
		data,
		_skipErrorToast: true,
	} as any);

export const publishModelLifecycle = (
	expected: ModelSpecCasToken,
	implementation: ModelImplementationCasToken,
	data: { comment?: string; idempotencyKey: string },
) =>
	api.post<ModelLifecycleRelease>({
		url: lifecycleUrl(expected.id, "/publish"),
		headers: lifecycleWriteHeaders(expected, implementation),
		data,
		_skipErrorToast: true,
	} as any);

export const retryModelReleaseRegistration = (modelSpecId: string, releaseId: string) =>
	api.post<ModelLifecycleRelease>({
		url: lifecycleUrl(modelSpecId, `/releases/${encodeURIComponent(releaseId)}/retry`),
		_skipErrorToast: true,
	} as any);

export const rollbackModelLifecycle = (
	expected: ModelSpecCasToken,
	data: { comment?: string; idempotencyKey: string },
) =>
	api.post<ModelLifecycleEvent>({
		url: lifecycleUrl(expected.id, "/rollback"),
		headers: lifecycleHeaders(expected),
		data,
		_skipErrorToast: true,
	} as any);

export const runModelLifecycle = (
	expected: ModelSpecCasToken,
	data: {
		idempotencyKey: string;
		sourceBatchId?: string;
		addaxTaskId?: string;
		airflowDagId?: string;
		airflowRunId?: string;
		dbtRunId?: string;
		dbtSelector: string;
		targetTable: string;
	},
) =>
	api.post<{
		id: string;
		modelSpecId: string;
		revision: number;
		state: string;
		planId?: string | null;
		modelChecksum: string;
		repairPath: string;
	}>({
		url: lifecycleUrl(expected.id, "/runs"),
		headers: lifecycleHeaders(expected),
		data,
		_skipErrorToast: true,
	} as any);

export const getModelLifecycle = (modelSpecId: string) =>
	api.get<ModelLifecycleTimeline>({
		url: lifecycleUrl(modelSpecId),
		_skipErrorToast: true,
	} as any);

export type ReleaseCandidateDeliveryStatus =
	| "DRAFT"
	| "BUILDING"
	| "BUILD_FAILED"
	| "BUILT"
	| "QUALITY_RUNNING"
	| "QUALITY_FAILED"
	| "QUALITY_PASSED"
	| "REVIEW_PENDING"
	| "REJECTED"
	| "APPROVED"
	| "PUBLISHING"
	| "PARTIAL"
	| "PUBLISHED"
	| "ROLLED_BACK"
	| "CANCELLED"
	| "STALE";

export type ReleaseCandidateLifecycleAction =
	| "START_BUILD"
	| "RETRY_BUILD"
	| "RUN_QUALITY"
	| "SUBMIT_REVIEW"
	| "CANCEL_CANDIDATE"
	| "APPROVE"
	| "REJECT"
	| "CREATE_REPLACEMENT_CANDIDATE"
	| "PUBLISH"
	| "RETRY_PUBLICATION"
	| "ROLLBACK";

export type ReleaseCandidateWorkspaceAction =
	| "CREATE_CANDIDATE"
	| "UPDATE_SCOPE"
	| "REFRESH_CANDIDATE"
	| "REMATERIALIZE"
	| ReleaseCandidateLifecycleAction;

export type ReleaseCandidateEvidenceType =
	| "ARTIFACT"
	| "BUILD_RUN"
	| "QUALITY_RUN"
	| "REVIEW"
	| "PUBLICATION"
	| "REGISTRATION"
	| "ROLLBACK";

export type ReleaseCandidateEvidenceState = "UNAVAILABLE" | "RUNNING" | "PASSED" | "FAILED" | "STALE";
export type ReleaseCandidateWorkbenchState = "EMPTY" | "READY" | "BLOCKED" | "STALE";
export type ReleaseCandidateRelationEvidenceState =
	| "NOT_STARTED"
	| "PENDING"
	| "PROBING"
	| "VERIFIED"
	| "FAILED"
	| "UNKNOWN";

export type ReleaseCandidateScopeEntryInput = {
	modelSpecId: string;
	sortOrder: number;
	selectedReason?: string | null;
};

export type ReleaseCandidateEntry = {
	id: string;
	tenantId: string;
	candidateId: string;
	planId: string;
	modelSpecId: string;
	revision: number;
	checksum: string;
	implementationId?: string | null;
	implementationMode: CanonicalModelSpecView["implementationMode"];
	status: ReleaseCandidateDeliveryStatus;
	sortOrder: number;
	selectedReason?: string | null;
};

export type ReleaseCandidateAudit = {
	createdBy: string;
	createdAt: string;
	submittedBy?: string | null;
	submittedAt?: string | null;
	approvedBy?: string | null;
	approvedAt?: string | null;
	publishedBy?: string | null;
	publishedAt?: string | null;
};

export type ReleaseCandidate = {
	id: string;
	tenantId: string;
	planId: string;
	environment: string;
	status: ReleaseCandidateDeliveryStatus;
	version: number;
	idempotencyKey?: string | null;
	requestHash?: string | null;
	audit: ReleaseCandidateAudit;
	lastModifiedBy: string;
	lastModifiedAt: string;
	entries: ReleaseCandidateEntry[];
	origin: "SINGLE_MODEL_INTENT" | "SCHEMA_ONLY_INTENT" | "BATCH_WORKBENCH";
	executionTargetKey?: string | null;
	adapter?: string | null;
	profileKey?: string | null;
	targetName?: string | null;
};

export type ModelBuildRun = {
	id: string;
	candidateEntryId: string;
	modelSpecId: string;
	pipelineRunGroupId: string;
	dbtInvocationId: string;
	airflowDagId: string;
	airflowRunId: string;
	dbtSelector: string;
	targetIdentifier: string;
	status: string;
	attempt: number;
	artifactBundleChecksum: string;
};

export type ModelBuildGroup = {
	candidateId: string;
	candidateVersion: number;
	pipelineRunGroupId: string;
	dbtInvocationId: string;
	executionTargetKey: string;
	airflowDagId: string;
	airflowRunId: string;
	artifactBundleChecksum: string;
	runs: ModelBuildRun[];
};

export type ModelBuildIntentResult = {
	candidate: ReleaseCandidate;
	build: ModelBuildGroup | null;
	replayed: boolean;
};

export type ModelPublicationOutcome =
	| "QUALITY_RUNNING"
	| "QUALITY_FAILED"
	| "PUBLICATION_READY"
	| "REVIEW_SUBMISSION_PENDING"
	| "REVIEW_PENDING"
	| "APPROVED"
	| "PUBLISHING"
	| "PARTIAL"
	| "PUBLISHED";

export type ModelPublicationNextHumanAction = "NONE" | "RETRY_QUALITY" | "REVIEW" | "PUBLISH" | "REPAIR_REGISTRATION";
export type ModelOnlineReadiness = "NOT_READY" | "PROCESSING" | "DEGRADED" | "READY";

export type ModelPublicationIntentResult = {
	candidateId: string;
	candidateVersion: number;
	candidateStatus: ReleaseCandidateDeliveryStatus;
	outcome: ModelPublicationOutcome;
	nextHumanAction: ModelPublicationNextHumanAction;
	blocker?: ReleaseCandidateBlocker | null;
	onlineReadiness: ModelOnlineReadiness;
	workbenchUrl: string;
	replayed: boolean;
};

export type ReleaseCandidateEvidenceSummary = {
	type: ReleaseCandidateEvidenceType;
	state: ReleaseCandidateEvidenceState;
	code?: string | null;
	message?: string | null;
};

export type ReleaseCandidateEntryEvidence = {
	candidateEntryId: string;
	modelSpecId: string;
	modelName: string;
	modelRevision: number;
	implementationRevision?: number | null;
	targetRelation?: string | null;
	runStatus?: string | null;
	relationState: ReleaseCandidateRelationEvidenceState;
	pipelineRunGroupId?: string | null;
	dbtInvocationId?: string | null;
	airflowDagId?: string | null;
	airflowRunId?: string | null;
	attempt?: number | null;
	startedAt?: string | null;
	finishedAt?: string | null;
	observedAt?: string | null;
	repairCode?: string | null;
	failureMessage?: string | null;
};

export type ReleaseCandidateGovernanceQualityEvidence = {
	assetKey: string;
	ruleId?: string | null;
	ruleVersionId?: string | null;
	bindingId?: string | null;
	runId?: string | null;
	status: string;
	finishedAt?: string | null;
	evidenceChecksum?: string | null;
	violations: string[];
};

export type ReleaseCandidateGovernanceQuality = {
	required: boolean;
	state: ReleaseCandidateEvidenceState;
	code?: string | null;
	message?: string | null;
	maxAgeSeconds: number;
	evidence: ReleaseCandidateGovernanceQualityEvidence[];
};

export type GovernanceQualityRerunResult = {
	candidateId: string;
	replayed: boolean;
	runs: Array<{
		ruleId: string;
		ruleVersionId: string;
		bindingId: string;
		runId: string;
		status: string;
	}>;
};

export type ModelMaterializationStatus = {
	modelSpecId: string;
	candidateId: string;
	candidateVersion: number;
	environment: string;
	candidateStatus: ReleaseCandidateDeliveryStatus;
	candidateUpdatedAt: string;
	currentImplementationRevision: number | null;
	evidence: ReleaseCandidateEntryEvidence;
};

export type MaterializationPlanStrategy = "WITH_MISSING_UPSTREAMS" | "CURRENT_ONLY";
export type MaterializationPlanAction = "BUILD" | "REUSE";
export type MaterializationPlanDependencyRole = "ROOT" | "UPSTREAM" | "DIMENSION" | "MIXED";
export type MaterializationPlanEntry = {
	modelSpecId: string;
	modelName: string;
	modelRevision: number;
	modelChecksum: string;
	implementationRevision: number;
	implementationChecksum: string;
	dependencyChecksum: string;
	layer: ModelSpecLayer;
	dependencyRole: MaterializationPlanDependencyRole;
	topologyLevel: number;
	action: MaterializationPlanAction;
	reasonCode: string;
	relationEvidenceId?: string | null;
	targetRelation?: string | null;
};
export type MaterializationPlanBlocker = {
	code: string;
	modelSpecId?: string | null;
	message: string;
	details: Record<string, unknown>;
};
export type MaterializationPlanPreview = {
	planId: string;
	environment: string;
	strategy: MaterializationPlanStrategy;
	planChecksum: string;
	canStart: boolean;
	requestedModelSpecIds: string[];
	orderedEntries: MaterializationPlanEntry[];
	blockers: MaterializationPlanBlocker[];
};
export type MaterializationPlanPreviewRequest = {
	environment: string;
	requestedModelSpecIds: string[];
	strategy: MaterializationPlanStrategy;
};

export type ReleaseCandidateMaterializationRequest = {
	environment: string;
	entries: ReleaseCandidateScopeEntryInput[];
	reason: string;
	materializationPlanChecksum?: string;
	strategy?: MaterializationPlanStrategy;
};

export type ReleaseCandidateBlocker = {
	code: string;
	message: string;
};

export type ReleaseCandidateWorkbench = {
	planId: string;
	state: ReleaseCandidateWorkbenchState;
	candidate: ReleaseCandidate | null;
	evidence: ReleaseCandidateEvidenceSummary[];
	entryEvidence: ReleaseCandidateEntryEvidence[];
	governanceQuality?: ReleaseCandidateGovernanceQuality | null;
	primaryBlocker: ReleaseCandidateBlocker | null;
	allowedActions: ReleaseCandidateWorkspaceAction[];
	etag: string | null;
};

export type PlanExecutionDagRun = {
	dagRunId?: string | null;
	state?: string | null;
	logicalDate?: string | null;
	startedAt?: string | null;
	finishedAt?: string | null;
};

export type PlanOperationalRun = {
	pipelineRunGroupId?: string | null;
	airflowRunId?: string | null;
	triggerType?: string | null;
	status?: string | null;
	errorCode?: string | null;
	createdAt?: string | null;
	startedAt?: string | null;
	finishedAt?: string | null;
};

export type PlanExecutionRelationEvidence = {
	verified?: boolean | null;
	exists?: boolean | null;
	errorCode?: string | null;
	physicalRelation?: string | null;
	observedAt?: string | null;
};

export type PlanExecutionBlocker = {
	code: string;
	message: string;
	owner: "MODEL_MAINTAINER" | "RELEASE_OPERATOR" | "PLATFORM_OPERATOR";
};

export type PlanExecutionBinding = {
	id: string;
	version: number;
	environment: string;
	state: "ONLINE" | "DEPLOYING" | "DISABLED" | "DEGRADED" | "UNKNOWN";
	scheduleMode: "MANUAL_ONLY" | "CRON_ENABLED";
	desiredSchedule?: string | null;
	desiredTimezone?: string | null;
	effectiveSchedule?: string | null;
	effectiveTimezone?: string | null;
	deploymentStatus: string;
	desiredDeploymentChecksum: string;
	deployedChecksum?: string | null;
	airflowDagId: string;
	airflowState: "OBSERVED" | "NOT_REGISTERED" | "UNKNOWN";
	actualSchedule?: string | null;
	actualTimezone?: string | null;
	airflowPaused?: boolean | null;
	nextRunAt?: string | null;
	latestDagRun?: PlanExecutionDagRun | null;
	latestOperationalRun: PlanOperationalRun;
	latestRelation: PlanExecutionRelationEvidence;
	primaryBlocker?: PlanExecutionBlocker | null;
	allowedActions: ("RUN_NOW" | "REPAIR_DEPLOYMENT")[];
};

export type PlanExecutionWorkspace = {
	planId: string;
	state: "NOT_DEPLOYED" | "READY";
	bindings: PlanExecutionBinding[];
};

export type PlanOperationalRunResult = {
	pipelineRunGroupId: string;
	bindingId: string;
	bindingVersion: number;
	triggerType: "MANUAL" | "CRON";
	airflowDagId: string;
	airflowRunId: string;
	status: string;
	replayed: boolean;
};

export type PlanExecutionRepairResult = {
	bindingId: string;
	bindingVersion: number;
	deploymentStatus: "DEPLOYING";
};

export type ReleaseCandidateDriftReason = {
	modelSpecId: string;
	lockedRevision: number;
	lockedChecksum: string;
	currentRevision?: number | null;
	currentChecksum?: string | null;
	code: string;
	message: string;
};

export type ReleaseCandidateCommandResult = {
	candidate: ReleaseCandidate;
	replayed: boolean;
	driftReasons: ReleaseCandidateDriftReason[];
	allowedActions: ReleaseCandidateLifecycleAction[];
};

export type ReleaseCandidateCasToken = {
	id: string;
	version: number;
};

/**
 * Transport and server state remain separate so the workbench never treats a 403 or failed request as an empty plan.
 */
export type ReleaseCandidateWorkbenchScreenState =
	| { kind: "loading" }
	| { kind: "empty"; data: ReleaseCandidateWorkbench }
	| { kind: "forbidden"; code: string; message: string }
	| { kind: "error"; code: string; message: string }
	| { kind: "ready"; data: ReleaseCandidateWorkbench };

export const toReleaseCandidateEtag = (expected: ReleaseCandidateCasToken) =>
	`"release-candidate:${expected.id}:${expected.version}"`;

export const toReleaseCandidateWorkbenchScreenState = (
	data: ReleaseCandidateWorkbench,
): ReleaseCandidateWorkbenchScreenState => (data.state === "EMPTY" ? { kind: "empty", data } : { kind: "ready", data });

export const releaseCandidateWorkbenchLoading = (): ReleaseCandidateWorkbenchScreenState => ({ kind: "loading" });

export const releaseCandidateWorkbenchForbidden = (
	code: string,
	message: string,
): ReleaseCandidateWorkbenchScreenState => ({ kind: "forbidden", code, message });

export const releaseCandidateWorkbenchError = (
	code: string,
	message: string,
): ReleaseCandidateWorkbenchScreenState => ({ kind: "error", code, message });

const releaseCandidateResource = (planId: string) => `/modeling/plans/${encodeURIComponent(planId)}/release-candidates`;

const materializationPlanResource = (planId: string) =>
	`/modeling/plans/${encodeURIComponent(planId)}/materialization-plans`;

const releaseCandidateItemUrl = (planId: string, candidateId: string, suffix = "") =>
	`${releaseCandidateResource(planId)}/${encodeURIComponent(candidateId)}${suffix}`;

const releaseCandidateWriteHeaders = (idempotencyKey: string, expected?: ReleaseCandidateCasToken) => ({
	"Idempotency-Key": idempotencyKey,
	...(expected ? { "If-Match": toReleaseCandidateEtag(expected) } : {}),
});

export const getReleaseCandidateWorkbench = (planId: string, scope?: { environment: string; modelSpecIds: string[] }) =>
	api.get<ReleaseCandidateWorkbench>({
		url: `${releaseCandidateResource(planId)}/workspace${scope ? "/scope" : ""}`,
		...(scope ? { params: { environment: scope.environment, modelSpecIds: scope.modelSpecIds.join(",") } } : {}),
		_skipErrorToast: true,
	} as any);

export const getModelMaterializationStatuses = (planId: string, modelSpecIds: string[]) =>
	api.get<ModelMaterializationStatus[]>({
		url: releaseCandidateItemUrl(planId, "materializations"),
		params: { modelSpecIds: modelSpecIds.join(",") },
		_skipErrorToast: true,
	} as any);

export const previewMaterializationPlan = (planId: string, data: MaterializationPlanPreviewRequest) =>
	api.post<MaterializationPlanPreview>({
		url: `${materializationPlanResource(planId)}/preview`,
		data,
		_skipErrorToast: true,
	} as any);

const planExecutionBindingResource = (planId: string) =>
	`/modeling/plans/${encodeURIComponent(planId)}/execution-bindings`;

export const getPlanExecutionWorkspace = (planId: string) =>
	api.get<PlanExecutionWorkspace>({
		url: `${planExecutionBindingResource(planId)}/workspace`,
		_skipErrorToast: true,
	} as any);

export const runPlanExecutionNow = (planId: string, bindingId: string, idempotencyKey: string) =>
	api.post<PlanOperationalRunResult>({
		url: `${planExecutionBindingResource(planId)}/${encodeURIComponent(bindingId)}/runs`,
		headers: { "Idempotency-Key": idempotencyKey },
		_skipErrorToast: true,
	} as any);

export const repairPlanExecutionBinding = (planId: string, bindingId: string, bindingVersion: number) =>
	api.post<PlanExecutionRepairResult>({
		url: `${planExecutionBindingResource(planId)}/${encodeURIComponent(bindingId)}/repair`,
		headers: { "If-Match": `"plan-execution-binding:${bindingId}:${bindingVersion}"` },
		_skipErrorToast: true,
	} as any);

export const startModelBuildIntent = (
	expected: ModelSpecCasToken,
	idempotencyKey: string,
	data: { planId: string; environment: string; buildMode?: "SCHEMA_ONLY" | "DATA_BUILD" },
) =>
	api.post<ModelBuildIntentResult>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(expected.id)}/build-intents`,
		headers: {
			"If-Match": toModelSpecEtag(expected),
			"Idempotency-Key": idempotencyKey,
		},
		data,
		_skipErrorToast: true,
	} as any);

export const startModelPublicationIntent = (
	modelSpecId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	reason: string,
) =>
	api.post<ModelPublicationIntentResult>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(modelSpecId)}/publish-intents`,
		headers: releaseCandidateWriteHeaders(idempotencyKey, expected),
		data: { candidateId: expected.id, reason },
		_skipErrorToast: true,
	} as any);

export const createReleaseCandidate = (
	planId: string,
	idempotencyKey: string,
	data: ReleaseCandidateMaterializationRequest,
) =>
	api.post<ReleaseCandidateCommandResult>({
		url: releaseCandidateResource(planId),
		headers: releaseCandidateWriteHeaders(idempotencyKey),
		data,
		_skipErrorToast: true,
	} as any);

export const updateReleaseCandidateScope = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	data: { entries: ReleaseCandidateScopeEntryInput[]; reason: string },
) =>
	api.put<ReleaseCandidateCommandResult>({
		url: releaseCandidateItemUrl(planId, expected.id, "/scope"),
		headers: releaseCandidateWriteHeaders(idempotencyKey, expected),
		data,
		_skipErrorToast: true,
	} as any);

export const lockReleaseCandidate = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	reason: string,
) =>
	api.post<ReleaseCandidateCommandResult>({
		url: releaseCandidateItemUrl(planId, expected.id, "/lock"),
		headers: releaseCandidateWriteHeaders(idempotencyKey, expected),
		data: { reason },
		_skipErrorToast: true,
	} as any);

export const retryReleaseCandidate = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	reason: string,
) =>
	api.post<ReleaseCandidateCommandResult>({
		url: releaseCandidateItemUrl(planId, expected.id, "/retry"),
		headers: releaseCandidateWriteHeaders(idempotencyKey, expected),
		data: { reason },
		_skipErrorToast: true,
	} as any);

export const cancelReleaseCandidate = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	reason: string,
) =>
	api.post<ReleaseCandidateCommandResult>({
		url: releaseCandidateItemUrl(planId, expected.id, "/cancel"),
		headers: releaseCandidateWriteHeaders(idempotencyKey, expected),
		data: { reason },
		_skipErrorToast: true,
	} as any);

export const publishReleaseCandidate = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	reason: string,
) =>
	api.post<ReleaseCandidateCommandResult>({
		url: releaseCandidateItemUrl(planId, expected.id, "/publish"),
		headers: releaseCandidateWriteHeaders(idempotencyKey, expected),
		data: { reason },
		_skipErrorToast: true,
	} as any);

const runReleaseCandidateCommand = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	reason: string,
	suffix: string,
) =>
	api.post<ReleaseCandidateCommandResult>({
		url: releaseCandidateItemUrl(planId, expected.id, suffix),
		headers: releaseCandidateWriteHeaders(idempotencyKey, expected),
		data: { reason },
		_skipErrorToast: true,
	} as any);

export const runReleaseCandidateQuality = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	reason: string,
) => runReleaseCandidateCommand(planId, expected, idempotencyKey, reason, "/quality");

export const rerunReleaseCandidateGovernanceQuality = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
) =>
	api.post<GovernanceQualityRerunResult>({
		url: releaseCandidateItemUrl(planId, expected.id, "/governance-quality/runs"),
		headers: releaseCandidateWriteHeaders(idempotencyKey, expected),
		_skipErrorToast: true,
	} as any);

export const submitReleaseCandidateReview = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	reason: string,
) => runReleaseCandidateCommand(planId, expected, idempotencyKey, reason, "/reviews");

export const approveReleaseCandidateReview = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	reason: string,
) => runReleaseCandidateCommand(planId, expected, idempotencyKey, reason, "/reviews/approve");

export const rejectReleaseCandidateReview = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	reason: string,
) => runReleaseCandidateCommand(planId, expected, idempotencyKey, reason, "/reviews/reject");

export const retryReleaseCandidateRegistration = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	reason: string,
) => runReleaseCandidateCommand(planId, expected, idempotencyKey, reason, "/publication/retry");

export const rollbackReleaseCandidate = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	reason: string,
) => runReleaseCandidateCommand(planId, expected, idempotencyKey, reason, "/rollback");

export const refreshReleaseCandidate = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	reason: string,
) =>
	api.post<ReleaseCandidateCommandResult>({
		url: releaseCandidateItemUrl(planId, expected.id, "/refresh"),
		headers: releaseCandidateWriteHeaders(idempotencyKey, expected),
		data: { reason },
		_skipErrorToast: true,
	} as any);

export const createReplacementReleaseCandidate = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	data: ReleaseCandidateMaterializationRequest,
) =>
	api.post<ReleaseCandidateCommandResult>({
		url: releaseCandidateItemUrl(planId, expected.id, "/replacement"),
		headers: releaseCandidateWriteHeaders(idempotencyKey, expected),
		data,
		_skipErrorToast: true,
	} as any);

export const rematerializeReleaseCandidate = (
	planId: string,
	expected: ReleaseCandidateCasToken,
	idempotencyKey: string,
	data: ReleaseCandidateMaterializationRequest,
) =>
	api.post<ReleaseCandidateCommandResult>({
		url: releaseCandidateItemUrl(planId, expected.id, "/rematerialize"),
		headers: releaseCandidateWriteHeaders(idempotencyKey, expected),
		data,
		_skipErrorToast: true,
	} as any);
