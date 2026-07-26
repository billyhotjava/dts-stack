import api from "@/api/apiClient";
import type {
	ModelImplementationCasToken,
	ModelImplementationView,
} from "@/pages/modeling/modelImplementationContract";
import { toModelImplementationEtag } from "@/pages/modeling/modelImplementationContract";
import type {
	CanonicalModelSpecView,
	CreateModelSpecCommand,
	ModelSpecCasToken,
	ModelSpecLayer,
	ModelSpecRevisionConflictDetails,
	ModelSpecType,
	ModelSpecView,
	UpdateModelSpecCommand,
} from "@/pages/modeling/modelSpecV2Contract";
import { toModelSpecEtag } from "@/pages/modeling/modelSpecV2Contract";

const MODEL_SPEC_RESOURCE = "/modeling/model-specs";

export type ModelSpecListParams = {
	planId?: string;
	domainId?: string;
	modelType?: ModelSpecType;
	layer?: ModelSpecLayer;
};

export type ModelSpecRevisionConflict = {
	code: "MODEL_SPEC_REVISION_CONFLICT";
	data: ModelSpecRevisionConflictDetails;
};

export type ModelSpecStage = "DRAFT_SAVE" | "IMPLEMENTATION_READY" | "RELEASE_READY";
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

export const getModelSpecDependencies = (id: string) =>
	api.get<ModelSpecDependencyGraph>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(id)}/dependencies`,
		_skipErrorToast: true,
	} as any);

export const createModelSpec = (data: CreateModelSpecCommand) =>
	api.post<CanonicalModelSpecView>({ url: MODEL_SPEC_RESOURCE, data, _skipErrorToast: true } as any);

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
	| "STALE";

export type ReleaseCandidateLifecycleAction =
	| "START_BUILD"
	| "RETRY_BUILD"
	| "RUN_QUALITY"
	| "SUBMIT_REVIEW"
	| "APPROVE"
	| "REJECT"
	| "CREATE_REPLACEMENT_CANDIDATE"
	| "PUBLISH"
	| "RETRY_REGISTRATION"
	| "ROLLBACK";

export type ReleaseCandidateWorkspaceAction =
	| "CREATE_CANDIDATE"
	| "UPDATE_SCOPE"
	| "REFRESH_CANDIDATE"
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
};

export type ReleaseCandidateEvidenceSummary = {
	type: ReleaseCandidateEvidenceType;
	state: ReleaseCandidateEvidenceState;
	code?: string | null;
	message?: string | null;
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
	primaryBlocker: ReleaseCandidateBlocker | null;
	allowedActions: ReleaseCandidateWorkspaceAction[];
	etag: string | null;
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

const releaseCandidateItemUrl = (planId: string, candidateId: string, suffix = "") =>
	`${releaseCandidateResource(planId)}/${encodeURIComponent(candidateId)}${suffix}`;

const releaseCandidateWriteHeaders = (idempotencyKey: string, expected?: ReleaseCandidateCasToken) => ({
	"Idempotency-Key": idempotencyKey,
	...(expected ? { "If-Match": toReleaseCandidateEtag(expected) } : {}),
});

export const getReleaseCandidateWorkbench = (planId: string) =>
	api.get<ReleaseCandidateWorkbench>({
		url: `${releaseCandidateResource(planId)}/workspace`,
		_skipErrorToast: true,
	} as any);

export const createReleaseCandidate = (
	planId: string,
	idempotencyKey: string,
	data: {
		environment: string;
		entries: ReleaseCandidateScopeEntryInput[];
		reason: string;
	},
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
	data: {
		environment: string;
		entries: ReleaseCandidateScopeEntryInput[];
		reason: string;
	},
) =>
	api.post<ReleaseCandidateCommandResult>({
		url: releaseCandidateItemUrl(planId, expected.id, "/replacement"),
		headers: releaseCandidateWriteHeaders(idempotencyKey, expected),
		data,
		_skipErrorToast: true,
	} as any);
