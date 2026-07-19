import type { WarehousePlanningContext } from "../governance/warehousePlanningContext";
import { sanitizeModelingReturnTo } from "./modelingCompatibilityRoute.ts";

export type BusinessModelingContext = {
	domainId?: string;
	domainName?: string;
	/** @deprecated Compatibility context only. New plan journeys must not require processId. */
	processId?: string;
	processName?: string;
	/** @deprecated Read-only alias migrated into planId. */
	planningId?: string;
	/** Canonical WarehousePlan context for all new modeling routes. */
	planId?: string;
	objectId?: string;
	modelSpecId?: string;
	revision?: string;
	modelType?: string;
	returnTo?: string;
	/** @deprecated Compatibility context only. */
	warehouseLayer?: string;
	/** @deprecated Compatibility context only. */
	modelingMode?: string;
	projectSpaceId?: string;
	projectSpaceMode: "implicit" | "project";
};

type ContextInput = Partial<Omit<BusinessModelingContext, "projectSpaceMode">> & {
	projectSpaceMode?: BusinessModelingContext["projectSpaceMode"];
};

const value = (input: string | null | undefined) => {
	const trimmed = input?.trim();
	return trimmed || undefined;
};

export const resolveBusinessModelingContext = (
	searchParams: URLSearchParams,
	planningContext?: Partial<WarehousePlanningContext> | null,
): BusinessModelingContext => {
	const context: ContextInput = {
		domainId: value(searchParams.get("domainId")) || value(planningContext?.domainId),
		domainName: value(searchParams.get("domainName")) || value(planningContext?.domainName),
		processId: value(searchParams.get("processId")) || value(planningContext?.processId),
		processName: value(searchParams.get("processName")),
		planningId: value(searchParams.get("planningId")) || value(planningContext?.planningId),
		planId: value(searchParams.get("planId")) || value(searchParams.get("planningId")) || value(planningContext?.planningId),
		objectId: value(searchParams.get("objectId")),
		modelSpecId: value(searchParams.get("modelSpecId")),
		revision: value(searchParams.get("revision")),
		modelType: value(searchParams.get("modelType")),
		returnTo: sanitizeModelingReturnTo(searchParams.get("returnTo")),
		warehouseLayer: value(searchParams.get("warehouseLayer")) || value(planningContext?.warehouseLayer),
		modelingMode: value(searchParams.get("modelingMode")) || value(planningContext?.modelingMode),
		projectSpaceId: value(searchParams.get("projectSpaceId")),
	};
	return {
		...context,
		projectSpaceMode: context.projectSpaceId ? "project" : "implicit",
	};
};

export const buildBusinessModelingRoute = (
	route: string,
	context: Partial<BusinessModelingContext>,
): string => {
	const [path, query = ""] = route.split("?");
	const params = new URLSearchParams(query);
	const entries: Record<string, string | undefined> = {
		domainId: context.domainId,
		planId: context.planId || context.planningId,
		modelSpecId: context.modelSpecId,
		revision: context.revision,
		modelType: context.modelType,
		returnTo: sanitizeModelingReturnTo(context.returnTo),
	};
	for (const [key, nextValue] of Object.entries(entries)) {
		if (nextValue) params.set(key, nextValue);
	}
	return params.toString() ? `${path}?${params.toString()}` : path;
};
