import { buildJourneyUrl } from "@/components/journey";
import {
	DEFAULT_WAREHOUSE_LAYER_SCHEME,
	WAREHOUSE_LAYER_REGISTRY,
	type WarehouseLayer,
} from "./warehouseLayerRegistry";

export const WAREHOUSE_PLANNING_CONTEXT_VERSION = 2 as const;
const LEGACY_WAREHOUSE_PLANNING_CONTEXT_VERSION = 1 as const;
export const WAREHOUSE_PLANNING_STORAGE_KEY = "dts.warehouse-planning.v1";

export type WarehouseModelingMode = "dimension";
export type WarehousePlanningStatus = "draft" | "ready" | "blocked";
export type WarehousePlanningSource = "session" | "url-fallback" | "missing";

export type WarehousePlanningContext = {
	version: typeof WAREHOUSE_PLANNING_CONTEXT_VERSION | typeof LEGACY_WAREHOUSE_PLANNING_CONTEXT_VERSION;
	planningId: string;
	domainId: string;
	domainName?: string;
	processId?: string;
	warehouseLayer: WarehouseLayer;
	layerSchemeId: string;
	layerSchemeVersion: number;
	enabledLayers: WarehouseLayer[];
	outputLayers: WarehouseLayer[];
	modelingMode: WarehouseModelingMode;
	sourceId?: string;
	standardDraftId?: string;
	createdAt: string;
	updatedAt: string;
};

export type WarehousePlanningStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export type WarehousePlanningResolution = {
	context: WarehousePlanningContext | null;
	source: WarehousePlanningSource;
	status: WarehousePlanningStatus;
	reason?: string;
};

export type WarehousePlanningStatusDeps = {
	source: WarehousePlanningSource;
	standardFieldCount: number;
};

export type StandardDraftBlocker = "planning" | "data-elements" | "field-binding" | "permission";

export type StandardDraftGateInput = {
	planningContext: WarehousePlanningContext | null;
	planningSource: WarehousePlanningSource;
	planningBlockedReason?: string;
	canManage: boolean;
	dataElementCount: number;
};

export type StandardDraftGateResult = {
	status: "ready" | "blocked";
	blocker?: StandardDraftBlocker;
	title: string;
	reason: string;
	repairLabel?: string;
	repairRoute?: string;
	canCreateDraft: boolean;
};

const WAREHOUSE_LAYERS = WAREHOUSE_LAYER_REGISTRY.map((layer) => layer.key);

const normalizeLayers = (value: unknown, fallback: readonly WarehouseLayer[]): WarehouseLayer[] => {
	if (!Array.isArray(value)) return [...fallback];
	const layers = value.filter((item): item is WarehouseLayer => WAREHOUSE_LAYERS.includes(item as WarehouseLayer));
	return layers.length ? [...new Set(layers)] : [...fallback];
};

const resolveDefaultStorage = (): WarehousePlanningStorage | undefined => {
	try {
		return typeof window === "undefined" ? undefined : window.sessionStorage;
	} catch {
		return undefined;
	}
};

const isWarehouseLayer = (value: unknown): value is WarehouseLayer =>
	WAREHOUSE_LAYERS.includes(value as WarehouseLayer);

const isDimensionMode = (value: unknown): value is WarehouseModelingMode => value === "dimension";

const hasRequiredFields = (value: Partial<WarehousePlanningContext>): boolean =>
	Boolean(
		(value.version === WAREHOUSE_PLANNING_CONTEXT_VERSION || value.version === LEGACY_WAREHOUSE_PLANNING_CONTEXT_VERSION) &&
			value.planningId &&
			value.domainId &&
			isWarehouseLayer(value.warehouseLayer) &&
			isDimensionMode(value.modelingMode) &&
			value.createdAt &&
			value.updatedAt,
	);

const toContext = (value: Partial<WarehousePlanningContext>): WarehousePlanningContext | null =>
	hasRequiredFields(value)
		? {
				version: WAREHOUSE_PLANNING_CONTEXT_VERSION,
				planningId: String(value.planningId),
				domainId: String(value.domainId),
				domainName: value.domainName ? String(value.domainName) : undefined,
				processId: value.processId ? String(value.processId) : undefined,
				warehouseLayer: value.warehouseLayer as WarehouseLayer,
				layerSchemeId: value.layerSchemeId ? String(value.layerSchemeId) : DEFAULT_WAREHOUSE_LAYER_SCHEME.id,
				layerSchemeVersion: Number(value.layerSchemeVersion || DEFAULT_WAREHOUSE_LAYER_SCHEME.version),
				enabledLayers: normalizeLayers(value.enabledLayers, DEFAULT_WAREHOUSE_LAYER_SCHEME.enabledLayers),
				outputLayers: normalizeLayers(value.outputLayers, DEFAULT_WAREHOUSE_LAYER_SCHEME.outputLayers),
				modelingMode: "dimension",
				sourceId: value.sourceId ? String(value.sourceId) : undefined,
				standardDraftId: value.standardDraftId ? String(value.standardDraftId) : undefined,
				createdAt: String(value.createdAt),
				updatedAt: String(value.updatedAt),
			}
		: null;

export const createWarehousePlanningContext = (
	input: Omit<WarehousePlanningContext, "version" | "createdAt" | "updatedAt" | "layerSchemeId" | "layerSchemeVersion" | "enabledLayers" | "outputLayers"> & {
		createdAt?: string;
		updatedAt?: string;
		layerSchemeId?: string;
		layerSchemeVersion?: number;
		enabledLayers?: WarehouseLayer[];
		outputLayers?: WarehouseLayer[];
	},
	now: () => string = () => new Date().toISOString(),
): WarehousePlanningContext => {
	const createdAt = input.createdAt || now();
	return {
		version: WAREHOUSE_PLANNING_CONTEXT_VERSION,
		planningId: input.planningId,
		domainId: input.domainId,
		domainName: input.domainName,
		processId: input.processId,
		warehouseLayer: input.warehouseLayer,
		layerSchemeId: input.layerSchemeId || DEFAULT_WAREHOUSE_LAYER_SCHEME.id,
		layerSchemeVersion: input.layerSchemeVersion || DEFAULT_WAREHOUSE_LAYER_SCHEME.version,
		enabledLayers: normalizeLayers(input.enabledLayers, DEFAULT_WAREHOUSE_LAYER_SCHEME.enabledLayers),
		outputLayers: normalizeLayers(input.outputLayers, DEFAULT_WAREHOUSE_LAYER_SCHEME.outputLayers),
		modelingMode: input.modelingMode,
		sourceId: input.sourceId,
		standardDraftId: input.standardDraftId,
		createdAt,
		updatedAt: input.updatedAt || createdAt,
	};
};

export const saveWarehousePlanningContext = (
	context: WarehousePlanningContext,
	storage: WarehousePlanningStorage | undefined = resolveDefaultStorage(),
): boolean => {
	if (!storage || !toContext(context)) return false;
	try {
		storage.setItem(WAREHOUSE_PLANNING_STORAGE_KEY, JSON.stringify(context));
		return true;
	} catch {
		return false;
	}
};

export const clearWarehousePlanningContext = (
	storage: WarehousePlanningStorage | undefined = resolveDefaultStorage(),
): void => {
	if (!storage) return;
	try {
		storage.removeItem(WAREHOUSE_PLANNING_STORAGE_KEY);
	} catch {
		// 隐私模式或配额异常时静默降级，不阻塞页面。
	}
};

export const loadWarehousePlanningContext = (
	storage: WarehousePlanningStorage | undefined = resolveDefaultStorage(),
): WarehousePlanningContext | null => {
	if (!storage) return null;
	let raw: string | null = null;
	try {
		raw = storage.getItem(WAREHOUSE_PLANNING_STORAGE_KEY);
	} catch {
		return null;
	}
	if (!raw) return null;
	try {
		const context = toContext(JSON.parse(raw) as Partial<WarehousePlanningContext>);
		if (!context) clearWarehousePlanningContext(storage);
		return context;
	} catch {
		clearWarehousePlanningContext(storage);
		return null;
	}
};

const planningRouteParams = (context: WarehousePlanningContext): Record<string, string> => ({
	planId: context.planningId,
	domainId: context.domainId,
});

export const buildPlanningRoute = (route: string, context: WarehousePlanningContext): string =>
	buildJourneyUrl(route, planningRouteParams(context));

const routePlanningParams = (searchParams: URLSearchParams) => ({
	planningId: searchParams.get("planId") || searchParams.get("planningId") || "",
	domainId: searchParams.get("domainId") || "",
	processId: searchParams.get("processId") || undefined,
	warehouseLayer: searchParams.get("warehouseLayer") || "",
	layerSchemeId: searchParams.get("layerSchemeId") || undefined,
	layerSchemeVersion: searchParams.get("layerSchemeVersion") || undefined,
	modelingMode: searchParams.get("modelingMode") || "",
	sourceId: searchParams.get("sourceId") || undefined,
	standardDraftId: searchParams.get("standardDraftId") || undefined,
});

const routeMatchesContext = (searchParams: URLSearchParams, context: WarehousePlanningContext): boolean => {
	const route = routePlanningParams(searchParams);
	return (
		(!route.planningId || route.planningId === context.planningId) &&
		(!route.domainId || route.domainId === context.domainId) &&
		(!route.processId || route.processId === context.processId) &&
		(!route.warehouseLayer || route.warehouseLayer === context.warehouseLayer) &&
		(!route.modelingMode || route.modelingMode === context.modelingMode)
	);
};

const fallbackContextFromRoute = (searchParams: URLSearchParams): WarehousePlanningContext | null => {
	const route = routePlanningParams(searchParams);
	if (
		!route.planningId ||
		!route.domainId ||
		!isWarehouseLayer(route.warehouseLayer) ||
		!isDimensionMode(route.modelingMode)
	) {
		return null;
	}
	const now = new Date().toISOString();
	return createWarehousePlanningContext({
		planningId: route.planningId,
		domainId: route.domainId,
		warehouseLayer: route.warehouseLayer,
		layerSchemeId: route.layerSchemeId,
		layerSchemeVersion: route.layerSchemeVersion ? Number(route.layerSchemeVersion) : undefined,
		modelingMode: "dimension",
		processId: route.processId,
		sourceId: route.sourceId,
		standardDraftId: route.standardDraftId,
		createdAt: now,
		updatedAt: now,
	});
};

export const resolveWarehousePlanningContext = (
	searchParams: URLSearchParams,
	storage: WarehousePlanningStorage | undefined = resolveDefaultStorage(),
): WarehousePlanningResolution => {
	const sessionContext = loadWarehousePlanningContext(storage);
	const routePlanningId = searchParams.get("planId") || searchParams.get("planningId");
	if (sessionContext) {
		if (!routeMatchesContext(searchParams, sessionContext)) {
			return {
				context: sessionContext,
				source: "session",
				status: "blocked",
				reason: "URL 规划参数与 session 草稿不一致，请以规划草稿为准或重新创建规划",
			};
		}
		return { context: sessionContext, source: "session", status: "draft" };
	}

	if (routePlanningId) {
		return {
			context: fallbackContextFromRoute(searchParams),
			source: "url-fallback",
			status: "blocked",
			reason: "规划草稿已失效，请重新确认规划上下文",
		};
	}

	return { context: null, source: "missing", status: "blocked", reason: "缺少数仓规划上下文" };
};

export const resolveWarehousePlanningStatus = (
	context: WarehousePlanningContext | null,
	deps: WarehousePlanningStatusDeps,
): { status: WarehousePlanningStatus; reason?: string } => {
	if (deps.source !== "session") {
		return { status: "blocked", reason: "规划草稿不可用，请重新确认规划" };
	}
	if (!context) return { status: "blocked", reason: "缺少数仓规划上下文" };
	if (!context.domainId) return { status: "blocked", reason: "规划缺少主题域" };
	if (context.warehouseLayer !== "DWD" || context.modelingMode !== "dimension") {
		return { status: "blocked", reason: "当前规划不是 DWD 维度建模" };
	}
	if (!context.standardDraftId) return { status: "draft", reason: "待生成数据标准草稿" };
	if (deps.standardFieldCount <= 0) return { status: "blocked", reason: "标准草稿没有可用字段" };
	return { status: "ready" };
};

export const getWarehousePlanningRouteParams = (context: WarehousePlanningContext) => planningRouteParams(context);

const buildStandardDraftRepairRoute = (route: string, context: WarehousePlanningContext | null): string =>
	context ? buildPlanningRoute(route, context) : route;

export const resolveStandardDraftGate = ({
	planningContext,
	planningSource,
	planningBlockedReason,
	canManage,
	dataElementCount,
}: StandardDraftGateInput): StandardDraftGateResult => {
	if (!planningContext) {
		return {
			status: "blocked",
			blocker: "planning",
			title: "缺少数仓规划",
			reason: planningBlockedReason || "缺少数仓规划，请先选择主题域并创建 DWD 维度建模规划",
			repairLabel: "返回主题域规划",
			repairRoute: "/governance/subjects",
			canCreateDraft: false,
		};
	}
	if (planningBlockedReason || planningSource !== "session") {
		return {
			status: "blocked",
			blocker: "planning",
			title: "规划上下文不可用",
			reason: planningBlockedReason || "规划草稿不可用，请重新确认规划",
			repairLabel: "返回主题域规划",
			repairRoute: buildStandardDraftRepairRoute(
				`/governance/subjects?active=${encodeURIComponent(planningContext.domainId)}`,
				planningContext,
			),
			canCreateDraft: false,
		};
	}
	if (!canManage) {
		return {
			status: "blocked",
			blocker: "permission",
			title: "缺少治理维护权限",
			reason: "当前账号无治理维护权限，请联系管理员授权后再生成字段落标草稿",
			repairLabel: "返回主题域规划",
			repairRoute: buildStandardDraftRepairRoute(
				`/governance/subjects?active=${encodeURIComponent(planningContext.domainId)}`,
				planningContext,
			),
			canCreateDraft: false,
		};
	}
	if (dataElementCount <= 0) {
		return {
			status: "blocked",
			blocker: "data-elements",
			title: "缺少可落标的数据元",
			reason: "当前主题域没有可输出的数据元，请先新增数据元规范",
			repairLabel: "新增数据元",
			repairRoute: buildStandardDraftRepairRoute("/governance/standards/elements?create=1", planningContext),
			canCreateDraft: false,
		};
	}
	if (!planningContext.standardDraftId) {
		return {
			status: "blocked",
			blocker: "field-binding",
			title: "字段尚未落标",
			reason: "数据元已就绪，请生成字段落标草稿后进入维度建模",
			repairLabel: "生成字段落标草稿",
			repairRoute: buildStandardDraftRepairRoute("/governance/standards/elements?bindingDraft=1", planningContext),
			canCreateDraft: true,
		};
	}
	return {
		status: "ready",
		title: "标准草稿已就绪",
		reason: "规划、治理权限、数据元和字段落标草稿均已就绪",
		canCreateDraft: true,
	};
};
