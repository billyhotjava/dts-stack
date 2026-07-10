import { buildJourneyUrl } from "@/components/journey";

export const WAREHOUSE_PLANNING_CONTEXT_VERSION = 1 as const;
export const WAREHOUSE_PLANNING_STORAGE_KEY = "dts.warehouse-planning.v1";

export type WarehouseLayer = "ODS_RAW" | "ODS_STANDARDIZED" | "DWD" | "DWS" | "ADS";
export type WarehouseModelingMode = "dimension";
export type WarehousePlanningStatus = "draft" | "ready" | "blocked";
export type WarehousePlanningSource = "session" | "url-fallback" | "missing";

export type WarehousePlanningContext = {
	version: typeof WAREHOUSE_PLANNING_CONTEXT_VERSION;
	planningId: string;
	domainId: string;
	domainName?: string;
	warehouseLayer: WarehouseLayer;
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

const WAREHOUSE_LAYERS: WarehouseLayer[] = ["ODS_RAW", "ODS_STANDARDIZED", "DWD", "DWS", "ADS"];

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
		value.version === WAREHOUSE_PLANNING_CONTEXT_VERSION &&
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
				warehouseLayer: value.warehouseLayer as WarehouseLayer,
				modelingMode: "dimension",
				sourceId: value.sourceId ? String(value.sourceId) : undefined,
				standardDraftId: value.standardDraftId ? String(value.standardDraftId) : undefined,
				createdAt: String(value.createdAt),
				updatedAt: String(value.updatedAt),
			}
		: null;

export const createWarehousePlanningContext = (
	input: Omit<WarehousePlanningContext, "version" | "createdAt" | "updatedAt"> & {
		createdAt?: string;
		updatedAt?: string;
	},
	now: () => string = () => new Date().toISOString(),
): WarehousePlanningContext => {
	const createdAt = input.createdAt || now();
	return {
		version: WAREHOUSE_PLANNING_CONTEXT_VERSION,
		planningId: input.planningId,
		domainId: input.domainId,
		domainName: input.domainName,
		warehouseLayer: input.warehouseLayer,
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
	planningId: context.planningId,
	domainId: context.domainId,
	warehouseLayer: context.warehouseLayer,
	modelingMode: context.modelingMode,
	...(context.sourceId ? { sourceId: context.sourceId } : {}),
	...(context.standardDraftId ? { standardDraftId: context.standardDraftId } : {}),
});

export const buildPlanningRoute = (route: string, context: WarehousePlanningContext): string =>
	buildJourneyUrl(route, planningRouteParams(context));

const routePlanningParams = (searchParams: URLSearchParams) => ({
	planningId: searchParams.get("planningId") || "",
	domainId: searchParams.get("domainId") || "",
	warehouseLayer: searchParams.get("warehouseLayer") || "",
	modelingMode: searchParams.get("modelingMode") || "",
	sourceId: searchParams.get("sourceId") || undefined,
	standardDraftId: searchParams.get("standardDraftId") || undefined,
});

const routeMatchesContext = (searchParams: URLSearchParams, context: WarehousePlanningContext): boolean => {
	const route = routePlanningParams(searchParams);
	return (
		(!route.planningId || route.planningId === context.planningId) &&
		(!route.domainId || route.domainId === context.domainId) &&
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
		modelingMode: "dimension",
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
	const routePlanningId = searchParams.get("planningId");
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
