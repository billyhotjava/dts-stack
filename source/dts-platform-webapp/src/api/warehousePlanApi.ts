import api from "@/api/apiClient";
import { withModelingRequestTimeout } from "@/api/modelingRequestTimeout";

const WAREHOUSE_PLAN_RESOURCE = "/modeling/warehouse-plans";

export type WarehousePlanOnboardingMode = "BUSINESS_FIRST" | "ASSET_FIRST";
export type WarehousePlanSourceType = "CONNECTION_TABLE" | "CATALOG_TABLE" | "EXCEL_FILE" | "DBT_NODE";
export type WarehousePlanLifecycleStatus =
	| "DRAFT"
	| "BASELINE_READY"
	| "MODELING"
	| "IMPLEMENTING"
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

export const getWarehousePlanningBaseline = (planId: string) =>
	api.get<PlanningBaseline>(
		withModelingRequestTimeout({ url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}/baseline` }),
	);

export const getWarehousePlanStageProjection = (planId: string) =>
	api.get<WarehousePlanStageProjection>(
		withModelingRequestTimeout({ url: `${WAREHOUSE_PLAN_RESOURCE}/${planId}/stage-projection` }),
	);
