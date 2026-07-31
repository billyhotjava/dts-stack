import { normalizeCanonicalWarehousePlanId } from "@/features/modeling/navigation/warehousePlanViewModel";

export type StandardOwnerReturnTarget = {
	href: string;
	label: "返回模型字段标准" | "返回建设规划";
};

export type StandardPackageSource = "elements" | "glossary" | "reference" | "units";

export type StandardPackageSourceMeta = {
	source: StandardPackageSource;
	path: string;
	label: string;
};

const STANDARD_OWNER_ORIGIN = "http://dts.local";
const OWNER_CONTEXT_KEYS = ["modelSpecId", "revision", "planId"] as const;
const PLAN_BASELINE_TABS = new Set(["categories", "layers", "sources"]);
const STANDARD_PACKAGE_SOURCES: Record<StandardPackageSource, StandardPackageSourceMeta> = {
	elements: { source: "elements", path: "/governance/standards/elements", label: "返回数据元" },
	glossary: { source: "glossary", path: "/governance/standards/glossary", label: "返回业务术语" },
	reference: { source: "reference", path: "/governance/standards/reference", label: "返回公共码表" },
	units: { source: "units", path: "/governance/standards/units", label: "返回计量单位" },
};

const normalized = (value: string | null | undefined) => value?.trim() || "";
const singleValue = (params: URLSearchParams, key: string) =>
	params.getAll(key).length === 1 ? normalized(params.get(key)) : "";

const parseLocalReturnTo = (rawReturnTo: string | null): URL | null => {
	if (!rawReturnTo || !rawReturnTo.startsWith("/") || rawReturnTo.startsWith("//") || rawReturnTo.includes("\\")) {
		return null;
	}
	try {
		const target = new URL(rawReturnTo, STANDARD_OWNER_ORIGIN);
		return target.origin === STANDARD_OWNER_ORIGIN && !target.hash ? target : null;
	} catch {
		return null;
	}
};

const hasOnlyKeys = (params: URLSearchParams, allowed: ReadonlySet<string>) =>
	[...params.keys()].every((key) => allowed.has(key));

const exactParam = (params: URLSearchParams, key: string, expected: string) =>
	params.getAll(key).length === 1 && params.get(key) === expected;

export const resolveStandardOwnerReturnTo = (searchParams: URLSearchParams): StandardOwnerReturnTarget | null => {
	if (["returnTo", ...OWNER_CONTEXT_KEYS].some((key) => searchParams.getAll(key).length > 1)) return null;
	const target = parseLocalReturnTo(singleValue(searchParams, "returnTo"));
	if (!target) return null;

	const modelSpecId = singleValue(searchParams, "modelSpecId");
	const revision = singleValue(searchParams, "revision");
	const rawPlanId = singleValue(searchParams, "planId");
	const planId = rawPlanId ? normalizeCanonicalWarehousePlanId(rawPlanId) : "";
	if (rawPlanId && !planId) return null;
	if (modelSpecId) {
		if (!/^[1-9]\d*$/.test(revision)) return null;
		if (target.pathname !== "/data-modeling/dimensions/workbench") return null;
		if (!exactParam(target.searchParams, "modelSpecId", modelSpecId)) return null;
		if (!exactParam(target.searchParams, "tab", "standards")) return null;
		if (!hasOnlyKeys(target.searchParams, new Set(["modelSpecId", "tab", "planId"]))) return null;
		if (planId && !exactParam(target.searchParams, "planId", planId)) return null;
		if (!planId && target.searchParams.has("planId")) return null;
		return { href: `${target.pathname}?${target.searchParams.toString()}`, label: "返回模型字段标准" };
	}

	if (revision) return null;
	if (!planId) return null;
	if (target.pathname !== "/data-modeling/planning/spaces") return null;
	if (!exactParam(target.searchParams, "view", "baseline")) return null;
	if (!exactParam(target.searchParams, "planId", planId)) return null;
	if (target.searchParams.getAll("tab").length !== 1) return null;
	if (!PLAN_BASELINE_TABS.has(target.searchParams.get("tab") || "")) return null;
	if (!hasOnlyKeys(target.searchParams, new Set(["view", "tab", "planId"]))) return null;
	return { href: `${target.pathname}?${target.searchParams.toString()}`, label: "返回建设规划" };
};

const copyOwnerContext = (source: URLSearchParams, target: URLSearchParams) => {
	const returnTarget = resolveStandardOwnerReturnTo(source);
	if (!returnTarget) return;
	for (const key of OWNER_CONTEXT_KEYS) {
		const value = singleValue(source, key);
		if (value) target.set(key, value);
	}
	target.set("returnTo", returnTarget.href);
};

export const getStandardPackageSourceMeta = (source: string | null | undefined): StandardPackageSourceMeta | null =>
	source && source in STANDARD_PACKAGE_SOURCES ? STANDARD_PACKAGE_SOURCES[source as StandardPackageSource] : null;

export const buildStandardPackageImportRoute = (
	searchParams: URLSearchParams,
	source: StandardPackageSource = "elements",
): string => {
	const params = new URLSearchParams({ from: source });
	if (source === "elements") copyOwnerContext(searchParams, params);
	return `/foundation/standard-package?${params.toString()}`;
};

export const buildStandardPackageReturnRoute = (
	searchParams: URLSearchParams,
	options: { applied?: boolean } = {},
): string | null => {
	const source = getStandardPackageSourceMeta(singleValue(searchParams, "from"));
	if (!source) return null;
	const params = new URLSearchParams({ from: "standard-package" });
	if (source.source === "elements") copyOwnerContext(searchParams, params);
	if (options.applied) params.set("applied", "1");
	return `${source.path}?${params.toString()}`;
};

export const buildDataElementReturnRoute = (
	searchParams: URLSearchParams,
	options: { applied?: boolean } = {},
): string => {
	const params = new URLSearchParams(searchParams);
	params.set("from", "elements");
	return buildStandardPackageReturnRoute(params, options) as string;
};
