import api from "@/api/apiClient";
import { withModelingRequestTimeout } from "@/api/modelingRequestTimeout";
import type {
	CanonicalModelSpecView,
	CreateModelSpecCommand,
	ModelSpecType,
} from "@/features/modeling/contracts/modelSpecV2Contract";

const WAREHOUSE_PLAN_RESOURCE = "/modeling/warehouse-plans";

export type WarehousePlanOnboardingMode = "BUSINESS_FIRST" | "ASSET_FIRST";
export type WarehousePlanSourceType = "CONNECTION_TABLE" | "CATALOG_TABLE" | "EXCEL_FILE" | "DBT_NODE";
export type WarehousePlanLifecycleStatus =
	| "DRAFT"
	| "BASELINE_READY"
	| "DESIGNING"
	| "VALIDATING"
	| "READY_TO_PUBLISH"
	| "PUBLISHED"
	| "ARCHIVED";

export type WarehousePlanHeader = {
	id: string;
	tenantId: string;
	code: string;
	name: string;
	objective?: string | null;
	scope?: string | null;
	ownerId: string;
	ownerDepartmentId?: string | null;
	onboardingMode: WarehousePlanOnboardingMode;
	lifecycleStatus: WarehousePlanLifecycleStatus;
	version: number;
};

export type CreateWarehousePlanInput = {
	name: string;
	objective?: string;
	scope?: string;
	ownerId?: string;
	ownerDepartmentId?: string;
	onboardingMode: WarehousePlanOnboardingMode;
	initialSourceRefs?: WarehousePlanSourceRef[];
	idempotencyKey: string;
};

export type UpdateWarehousePlanInput = {
	name: string;
	objective?: string | null;
	scope?: string | null;
	ownerId: string;
	ownerDepartmentId?: string | null;
};

export type WarehousePlanSourceRef = {
	sourceType: WarehousePlanSourceType;
	sourceId: string;
	sourceVersion?: string;
};

export type WarehousePlanSourceBinding = WarehousePlanSourceRef & {
	id: string;
	confirmationStatus: "CANDIDATE" | "CONFIRMED" | "EXCLUDED";
	exclusionReason?: string | null;
};

export type WarehousePlanSourceLocator = {
	assetId?: string | null;
	fileId?: string | null;
	projectKey?: string | null;
	uniqueId?: string | null;
	connectionId?: string | null;
	namespace?: string | null;
	objectName?: string | null;
};

export type WarehousePlanSourceBindingInput = {
	bindingId?: string | null;
	sourceType?: WarehousePlanSourceType | null;
	locator?: WarehousePlanSourceLocator | null;
	confirmationStatus: WarehousePlanConfirmationStatus;
	exclusionReason?: string | null;
	action?: WarehousePlanSourceAction | null;
	expectedConfirmedVersion?: string | null;
	expectedCurrentVersion?: string | null;
};

export type WarehousePlanSourceResolutionStatus = "AVAILABLE" | "MISSING" | "FORBIDDEN" | "PROVIDER_ERROR";
export type WarehousePlanSourceFreshness = "CURRENT" | "STALE" | "UNKNOWN";
export type WarehousePlanSourceInventoryReadiness = "DRAFT" | "READY" | "BLOCKED" | "NOT_REQUIRED_YET";
export type WarehousePlanSourceAction = "CONFIRM" | "RECONFIRM" | "EXCLUDE";
export type WarehousePlanSourceChangeImpact = "NONE" | "COMPATIBLE" | "BREAKING" | "REVIEW_REQUIRED";

export type WarehousePlanSourceDiffSummary = {
	added: number;
	removed: number;
	changed: number;
};

export type WarehousePlanSourceSchemaChange = {
	field?: string | null;
	kind?: string | null;
	before?: unknown;
	after?: unknown;
	impact?: string | null;
};

export type WarehousePlanSourceBindingView = {
	bindingId: string;
	sourceType: WarehousePlanSourceType;
	locator: WarehousePlanSourceLocator | null;
	sourceId?: string | null;
	confirmationStatus: WarehousePlanConfirmationStatus;
	exclusionReason?: string | null;
	displayName?: string | null;
	confirmedVersion?: string | null;
	resolvedVersion?: string | null;
	resolutionStatus: WarehousePlanSourceResolutionStatus;
	freshness: WarehousePlanSourceFreshness;
	lastValidatedAt?: string | null;
	currentVersion?: string | null;
	changeImpact?: WarehousePlanSourceChangeImpact;
	diffSummary?: WarehousePlanSourceDiffSummary;
	changes?: WarehousePlanSourceSchemaChange[];
	allowedActions?: WarehousePlanSourceAction[];
	reasonCode?: string | null;
	statusSummary?: string | null;
};

export type WarehousePlanSourceInventoryView = {
	bindings: WarehousePlanSourceBindingView[];
	readiness: WarehousePlanSourceInventoryReadiness;
	issues: WarehousePlanIssue[];
	version: number;
	etag: string;
	checkedAt: string;
	page?: number;
	size?: number;
	totalElements?: number;
	totalPages?: number;
};

export type CreateWarehousePlanResult = {
	planId: string;
	plan: WarehousePlanHeader;
	version: number;
	etag: string;
	initialSourceBindings: WarehousePlanSourceBinding[];
	nextAction: string;
	replayed: boolean;
};

export type PlanningBaseline = {
	ready: boolean;
	missingCodes: string[];
};

export type VersionedWarehousePlanValue<T> = {
	value: T;
	version: number;
};

export type WarehousePlanConfirmationStatus = "CANDIDATE" | "CONFIRMED" | "EXCLUDED";
export type WarehousePlanCategoryResolutionStatus = "AVAILABLE" | "MISSING" | "ARCHIVED" | "FORBIDDEN";
export type WarehousePlanCategoryReadiness = "DRAFT" | "READY" | "BLOCKED";

export type WarehousePlanIssue = {
	code: string;
	message: string;
	field?: string | null;
};

export type WarehousePlanCategoryBindingInput = {
	domainId: string;
	confirmationStatus: WarehousePlanConfirmationStatus;
};

export type WarehousePlanCategoryBindingView = WarehousePlanCategoryBindingInput & {
	resolutionStatus: WarehousePlanCategoryResolutionStatus;
	name?: string | null;
	code?: string | null;
	lastValidatedAt?: string | null;
};

export type WarehousePlanCategoryScopeView = {
	domainBindings: WarehousePlanCategoryBindingView[];
	readiness: WarehousePlanCategoryReadiness;
	issues: WarehousePlanIssue[];
	lastValidatedAt?: string | null;
};

export type WarehousePlanLayerScheme = "CLASSIC_ODS_DWD_DWS_ADS";
export type WarehousePlanNamingPolicy = "CLASSIC_LOWER_SNAKE" | "CLASSIC_UPPER_SNAKE";
export type WarehousePlanHistoryPolicy = "PRESERVE_BUSINESS_HISTORY" | "LATEST_STATE_ONLY";
export type WarehousePlanStandardCoverage = "NONE" | "KEY_AND_MEASURE" | "ALL_FIELDS";
export type WarehousePlanQualityGate = "ADVISORY" | "BLOCKING";
export type WarehousePlanBusinessCategoryMode = "SINGLE_DEFAULT" | "MULTI_SELECT";
export type WarehousePlanBusinessProcessMode = "AUTO_SELECT_SINGLE" | "MANAGED";
export type WarehousePlanPolicyReadiness = "DRAFT" | "MODEL_DESIGN_READY" | "IMPLEMENTATION_READY";

export type WarehousePlanPolicyInput = {
	layerScheme: WarehousePlanLayerScheme | null;
	namingPolicy: WarehousePlanNamingPolicy | null;
	historyPolicy: WarehousePlanHistoryPolicy | null;
	defaultTimeZone?: string | null;
	conceptualDesignAllowed: boolean;
	standardCoverage: WarehousePlanStandardCoverage;
	qualityGate: WarehousePlanQualityGate;
	businessCategoryMode?: WarehousePlanBusinessCategoryMode | null;
	defaultBusinessCategoryId?: string | null;
	businessProcessMode?: WarehousePlanBusinessProcessMode | null;
};

export type WarehousePlanPolicyView = WarehousePlanPolicyInput & {
	readiness: WarehousePlanPolicyReadiness;
	issues: WarehousePlanIssue[];
};

export type WarehousePlanStageCode =
	| "DATA_CONNECTION"
	| "SOURCE_INVENTORY"
	| "WAREHOUSE_PLANNING"
	| "DATA_STANDARD"
	| "MODEL_DESIGN"
	| "BUILD_QUALITY_RELEASE"
	| "DATA_ASSET"
	| "METRIC_SYSTEM"
	| "DATA_SERVICE_OPERATIONS";

export type WarehousePlanStageStatus = "NOT_STARTED" | "IN_PROGRESS" | "BLOCKED" | "COMPLETE" | "UNKNOWN";
export type WarehousePlanEvidenceFreshness = "CURRENT" | "STALE" | "UNAVAILABLE";

export type WarehousePlanStageView = {
	code: WarehousePlanStageCode;
	status: WarehousePlanStageStatus;
	freshness: WarehousePlanEvidenceFreshness;
	evidenceCount: number;
	blockerCode?: string | null;
	blockerMessage?: string | null;
	actionLabel: string;
	actionPath: string;
};

export type WarehousePlanStageProjection = {
	planId: string;
	currentStage?: WarehousePlanStageCode | null;
	primaryBlocker?: {
		stageCode: WarehousePlanStageCode;
		code: string;
		message?: string | null;
	} | null;
	nextAction?: { label: string; path: string } | null;
	stages: WarehousePlanStageView[];
	computedAt: string;
};

export type WarehousePlanModelCandidate = {
	candidateId: string;
	sourceBindingId: string;
	sourceType: WarehousePlanSourceType;
	sourceId: string;
	resolvedVersion: string;
	suggestedModelType: ModelSpecType;
	suggestedName: string;
};

export type WarehousePlanModelCandidatePreview = {
	planId: string;
	sourcesVersion: number;
	inferenceVersion: string;
	candidates: WarehousePlanModelCandidate[];
};

export type WarehousePlanRelationshipGraphNode = {
	id: string;
	kind: WarehousePlanRelationshipGraphKind;
	label: string;
	status?: string | null;
	route?: string | null;
};

export type WarehousePlanRelationshipGraphEdge = {
	source: string;
	target: string;
	kind: string;
	label?: string | null;
};

export type WarehousePlanRelationshipGraph = {
	planId: string;
	nodes: WarehousePlanRelationshipGraphNode[];
	edges: WarehousePlanRelationshipGraphEdge[];
	truncated: boolean;
	nextHint?: string | null;
	nextCursor?: string | null;
};

export type WarehousePlanRelationshipGraphKind = "PLAN" | "DIMENSION" | "MODEL" | "STANDARD" | "INDICATOR";

export type WarehousePlanRelationshipGraphQuery = {
	kind?: WarehousePlanRelationshipGraphKind;
	query?: string;
	limit?: number;
	cursor?: string;
};

export const listWarehousePlans = (lifecycleStatus?: WarehousePlanLifecycleStatus) =>
	api.get<WarehousePlanHeader[]>(
		withModelingRequestTimeout({
			url: WAREHOUSE_PLAN_RESOURCE,
			params: lifecycleStatus ? { lifecycleStatus } : undefined,
		}),
	);

export const getWarehousePlan = (planId: string) =>
	api.get<WarehousePlanHeader>(withModelingRequestTimeout({ url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}` }));

export const createWarehousePlan = (data: CreateWarehousePlanInput) =>
	api.post<CreateWarehousePlanResult>({ url: WAREHOUSE_PLAN_RESOURCE, data });

export const updateWarehousePlan = (planId: string, version: number, data: UpdateWarehousePlanInput) =>
	api.patch<WarehousePlanHeader>(
		withModelingRequestTimeout({
			url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}`,
			headers: { "If-Match": `"plan-head:${version}"` },
			data,
			_skipErrorToast: true,
		}),
	);

export const archiveWarehousePlan = (planId: string, version: number) =>
	api.post<WarehousePlanHeader>(
		withModelingRequestTimeout({
			url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}/archive`,
			headers: { "If-Match": `"plan-head:${version}"` },
			_skipErrorToast: true,
		}),
	);

export const getWarehousePlanningBaseline = (planId: string) =>
	api.get<PlanningBaseline>(withModelingRequestTimeout({ url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}/baseline` }));

export const getWarehousePlanCategories = (planId: string) =>
	api.get<VersionedWarehousePlanValue<WarehousePlanCategoryScopeView>>(
		withModelingRequestTimeout({
			url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}/baseline/categories`,
			_skipErrorToast: true,
		}),
	);

export const saveWarehousePlanCategories = (
	planId: string,
	version: number,
	domainBindings: WarehousePlanCategoryBindingInput[],
) =>
	api.put<VersionedWarehousePlanValue<WarehousePlanCategoryScopeView>>(
		withModelingRequestTimeout({
			url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}/baseline/categories`,
			headers: { "If-Match": `"category-scope:${version}"` },
			data: { domainBindings },
			_skipErrorToast: true,
		}),
	);

export const getWarehousePlanSources = (planId: string, page?: number, size?: number) =>
	api.get<WarehousePlanSourceInventoryView>(
		withModelingRequestTimeout({
			url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}/baseline/sources`,
			params: page === undefined && size === undefined ? undefined : { page: page ?? 0, size: size ?? 200 },
			_skipErrorToast: true,
		}),
	);

export const saveWarehousePlanSources = (
	planId: string,
	version: number,
	bindings: WarehousePlanSourceBindingInput[],
) =>
	api.put<WarehousePlanSourceInventoryView>(
		withModelingRequestTimeout({
			url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}/baseline/sources`,
			headers: { "If-Match": `"sources:${version}"` },
			data: { bindings },
			_skipErrorToast: true,
		}),
	);

export const getWarehousePlanPolicy = (planId: string) =>
	api.get<VersionedWarehousePlanValue<WarehousePlanPolicyView>>(
		withModelingRequestTimeout({
			url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}/baseline/policy`,
			_skipErrorToast: true,
		}),
	);

export const saveWarehousePlanPolicy = (planId: string, version: number, data: WarehousePlanPolicyInput) =>
	api.put<VersionedWarehousePlanValue<WarehousePlanPolicyView>>(
		withModelingRequestTimeout({
			url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}/baseline/policy`,
			headers: { "If-Match": `"policy:${version}"` },
			data,
			_skipErrorToast: true,
		}),
	);

export const getWarehousePlanStageProjection = (planId: string) =>
	api.get<WarehousePlanStageProjection>(
		withModelingRequestTimeout({ url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}/stage-projection` }),
	);

export const getWarehousePlanRelationshipGraph = (planId: string, params: WarehousePlanRelationshipGraphQuery = {}) =>
	api.get<WarehousePlanRelationshipGraph>(
		withModelingRequestTimeout({
			url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}/relationship-graph`,
			params,
			_skipErrorToast: true,
		}),
	);

export const previewWarehousePlanModelCandidates = (planId: string) =>
	api.get<WarehousePlanModelCandidatePreview>(
		withModelingRequestTimeout({ url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}/model-candidates/preview` }),
	);

export const confirmWarehousePlanModelCandidate = (
	planId: string,
	candidateId: string,
	modelSpec: CreateModelSpecCommand,
) =>
	api.post<CanonicalModelSpecView>({
		url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}/model-candidates/confirm`,
		data: { candidateId, modelSpec },
	});
