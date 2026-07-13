import type { WarehousePlanningContext } from "../governance/warehousePlanningContext";

export type BusinessModelingContext = {
	domainId?: string;
	domainName?: string;
	processId?: string;
	processName?: string;
	planningId?: string;
	warehouseLayer?: string;
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
		domainName: context.domainName,
		processId: context.processId,
		processName: context.processName,
		planningId: context.planningId,
		warehouseLayer: context.warehouseLayer,
		modelingMode: context.modelingMode,
		projectSpaceId: context.projectSpaceId,
	};
	for (const [key, nextValue] of Object.entries(entries)) {
		if (nextValue) params.set(key, nextValue);
	}
	return params.toString() ? `${path}?${params.toString()}` : path;
};
