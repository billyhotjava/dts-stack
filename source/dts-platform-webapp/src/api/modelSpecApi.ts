import api from "@/api/apiClient";
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
export type ModelImplementationOwner = {
	id: string;
	modelSpecId: string;
	planId: string;
	revision: number;
	modelChecksum: string;
	ownership: CanonicalModelSpecView["implementationMode"];
	projectKey: string;
	dbtUniqueId: string;
	status: string;
};
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

export const claimModelImplementation = (
	expected: ModelSpecCasToken,
	data: {
		ownership: CanonicalModelSpecView["implementationMode"];
		projectKey: string;
		dbtUniqueId: string;
		idempotencyKey: string;
	},
) =>
	api.put<ModelImplementationOwner>({
		url: `${MODEL_SPEC_RESOURCE}/${encodeURIComponent(expected.id)}/implementation`,
		headers: lifecycleHeaders(expected),
		data,
		_skipErrorToast: true,
	} as any);

export const compileModelLifecycle = (expected: ModelSpecCasToken, idempotencyKey: string) =>
	api.post<{
		implementation: ModelImplementationOwner;
		event: ModelLifecycleEvent;
		artifacts: ModelLifecycleArtifact[];
	}>({
		url: lifecycleUrl(expected.id, "/compile"),
		headers: lifecycleHeaders(expected),
		data: { idempotencyKey },
		_skipErrorToast: true,
	} as any);

export const recordModelTestEvidence = (
	expected: ModelSpecCasToken,
	data: { status?: "PASSED" | "FAILED"; externalRunId: string; comment?: string; idempotencyKey: string },
) =>
	api.post<ModelLifecycleEvent>({
		url: lifecycleUrl(expected.id, "/tests"),
		headers: lifecycleHeaders(expected),
		data,
		_skipErrorToast: true,
	} as any);

export const submitModelReview = (expected: ModelSpecCasToken, data: { comment?: string; idempotencyKey: string }) =>
	api.post<ModelLifecycleEvent>({
		url: lifecycleUrl(expected.id, "/reviews"),
		headers: lifecycleHeaders(expected),
		data,
		_skipErrorToast: true,
	} as any);

export const approveModelReview = (expected: ModelSpecCasToken, data: { comment?: string; idempotencyKey: string }) =>
	api.post<ModelLifecycleEvent>({
		url: lifecycleUrl(expected.id, "/reviews/approve"),
		headers: lifecycleHeaders(expected),
		data,
		_skipErrorToast: true,
	} as any);

export const publishModelLifecycle = (
	expected: ModelSpecCasToken,
	data: { comment?: string; idempotencyKey: string },
) =>
	api.post<ModelLifecycleRelease>({
		url: lifecycleUrl(expected.id, "/publish"),
		headers: lifecycleHeaders(expected),
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
