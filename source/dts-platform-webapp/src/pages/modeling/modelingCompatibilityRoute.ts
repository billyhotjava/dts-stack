import type { ModelSpecType } from "./modelSpecV2Contract";

export const LEGACY_MODELING_PATHS = [
	"/modeling/semantic/subjects",
	"/modeling/semantic/objects",
	"/modeling/semantic/models",
	"/modeling/semantic/metrics",
	"/modeling/semantic/publish",
	"/modeling/semantic/runs",
	"/studio/low-code-development",
	"/modeling/dbt-files",
] as const;

export type LegacyObjectTarget = {
	modelSpecId: string;
	modelType: ModelSpecType;
	planId?: string | null;
	domainId?: string | null;
	revision?: string | null;
};

export type ModelingCompatibilityTarget =
	| { kind: "redirect"; to: string }
	| { kind: "recovery"; code: "NEEDS_CLASSIFICATION"; legacyObjectId: string };

type CanonicalContextOverride = Partial<LegacyObjectTarget>;

const RETURN_TO_PREFIXES = [
	"/modeling",
	"/governance",
	"/studio",
	"/ops",
	"/workbench",
	"/foundation",
	"/catalog",
	"/services",
	"/bi",
] as const;

const value = (input: string | null | undefined) => input?.trim() || undefined;

export const sanitizeModelingReturnTo = (input: string | null | undefined): string | undefined => {
	const candidate = value(input);
	if (!candidate || !candidate.startsWith("/") || candidate.startsWith("//") || candidate.includes("\\")) {
		return undefined;
	}
	try {
		const url = new URL(candidate, "http://dts.local");
		if (url.origin !== "http://dts.local") return undefined;
		if (!RETURN_TO_PREFIXES.some((prefix) => url.pathname === prefix || url.pathname.startsWith(`${prefix}/`))) {
			return undefined;
		}
		return `${url.pathname}${url.search}${url.hash}`;
	} catch {
		return undefined;
	}
};

const canonicalContext = (
	searchParams: URLSearchParams,
	mapped?: LegacyObjectTarget,
	override: CanonicalContextOverride = {},
) => ({
	planId:
		value(override.planId) ||
		value(mapped?.planId) ||
		value(searchParams.get("planId")) ||
		value(searchParams.get("planningId")),
	domainId:
		value(override.domainId) ||
		value(mapped?.domainId) ||
		value(searchParams.get("domainId")) ||
		value(searchParams.get("active")),
	modelSpecId:
		value(override.modelSpecId) ||
		value(mapped?.modelSpecId) ||
		value(searchParams.get("modelSpecId")) ||
		value(searchParams.get("modelId")),
	revision: value(override.revision) || value(mapped?.revision) || value(searchParams.get("revision")),
	modelType: value(override.modelType) || value(mapped?.modelType) || value(searchParams.get("modelType")),
	returnTo: sanitizeModelingReturnTo(searchParams.get("returnTo")),
});

const safeHash = (hash: string) => (/^#[A-Za-z0-9_.:-]{1,128}$/.test(hash) ? hash : "");

const buildTarget = (
	path: string,
	searchParams: URLSearchParams,
	mapped?: LegacyObjectTarget,
	fixed: Record<string, string> = {},
	override: CanonicalContextOverride = {},
	passthrough: readonly string[] = [],
	hash = "",
) => {
	const params = new URLSearchParams(fixed);
	const context = canonicalContext(searchParams, mapped, override);
	for (const [key, nextValue] of Object.entries(context)) {
		if (nextValue) params.set(key, nextValue);
	}
	for (const key of passthrough) {
		const nextValue = value(searchParams.get(key));
		if (nextValue) params.set(key, nextValue);
	}
	const query = params.toString();
	const anchor = safeHash(hash);
	return { kind: "redirect", to: `${query ? `${path}?${query}` : path}${anchor}` } as const;
};

const pathIdentifier = (pathname: string, prefix: string) => {
	if (!pathname.startsWith(`${prefix}/`)) return undefined;
	const encoded = pathname.slice(prefix.length + 1).split("/", 1)[0];
	if (!encoded) return undefined;
	try {
		return value(decodeURIComponent(encoded));
	} catch {
		return undefined;
	}
};

const modelWorkbenchTarget = (
	searchParams: URLSearchParams,
	mapped?: LegacyObjectTarget,
	modelSpecId?: string,
	fixed: Record<string, string> = {},
	hash = "",
) => {
	const resolvedModelSpecId =
		value(modelSpecId) ||
		value(mapped?.modelSpecId) ||
		value(searchParams.get("modelSpecId")) ||
		value(searchParams.get("modelId"));
	return buildTarget(
		"/modeling/workbench",
		searchParams,
		mapped,
		{
			module: "models",
			workspaceView: "model-specs",
			...(resolvedModelSpecId ? { assetKind: "model", assetId: resolvedModelSpecId } : {}),
			...fixed,
		},
		resolvedModelSpecId ? { modelSpecId: resolvedModelSpecId } : {},
		[],
		hash,
	);
};

const dimensionWorkbenchTarget = (searchParams: URLSearchParams, mapped?: LegacyObjectTarget, hash = "") => {
	const dimensionId = value(mapped?.modelSpecId);
	return buildTarget(
		"/modeling/workbench",
		searchParams,
		mapped,
		{
			module: "models",
			workspaceView: "dimensions",
			...(dimensionId ? { assetKind: "dimension", assetId: dimensionId } : {}),
		},
		{},
		[],
		hash,
	);
};

const metricWorkbenchTarget = (searchParams: URLSearchParams, mapped?: LegacyObjectTarget, hash = "") => {
	const requestedView = value(searchParams.get("view"));
	const workspaceView =
		requestedView === "model" || requestedView === "templates" || requestedView === "consumption"
			? requestedView
			: requestedView === "definition"
				? "definitions"
				: searchParams.has("modelSpecId") || searchParams.has("modelId")
					? "model"
					: "definitions";
	const indicatorId = workspaceView === "definitions" ? value(searchParams.get("indicatorId")) : undefined;
	return buildTarget(
		"/modeling/workbench",
		searchParams,
		mapped,
		{
			module: "metrics",
			workspaceView,
			...(indicatorId ? { assetKind: "indicator", assetId: indicatorId } : {}),
		},
		{},
		["tab", "draft"],
		hash,
	);
};

const planningWorkspaceView = (pathname: string, searchParams: URLSearchParams) => {
	if (!pathname.includes("/baseline")) return "overview";
	const tab = value(searchParams.get("tab"));
	return tab === "categories" || tab === "data-marts" || tab === "layers" || tab === "sources" ? tab : "categories";
};

export const resolveModelingCompatibilityTarget = (
	pathname: string,
	searchParams: URLSearchParams,
	mappedObject?: LegacyObjectTarget,
	hash = "",
): ModelingCompatibilityTarget => {
	if (pathname === "/modeling/plans" || pathname.startsWith("/modeling/plans/")) {
		const planId = pathIdentifier(pathname, "/modeling/plans");
		return buildTarget(
			"/modeling/workbench",
			searchParams,
			mappedObject,
			{
				module: "planning",
				workspaceView: planningWorkspaceView(pathname, searchParams),
			},
			planId ? { planId } : {},
			[],
			hash,
		);
	}
	if (pathname === "/modeling/dimensions") {
		return dimensionWorkbenchTarget(searchParams, mappedObject, hash);
	}
	if (pathname === "/modeling/models" || pathname.startsWith("/modeling/models/")) {
		return modelWorkbenchTarget(searchParams, mappedObject, pathIdentifier(pathname, "/modeling/models"), {}, hash);
	}
	if (pathname === "/modeling/metric-workbench") {
		return metricWorkbenchTarget(searchParams, mappedObject, hash);
	}

	switch (pathname) {
		case "/modeling/semantic/subjects":
			return buildTarget("/governance/subjects", searchParams, mappedObject);
		case "/modeling/semantic/objects": {
			const legacyObjectId = value(searchParams.get("objectId"));
			if (legacyObjectId && !mappedObject) {
				return { kind: "recovery", code: "NEEDS_CLASSIFICATION", legacyObjectId };
			}
			if (mappedObject && mappedObject.modelType !== "DIMENSION") {
				return modelWorkbenchTarget(searchParams, mappedObject, mappedObject.modelSpecId, {}, hash);
			}
			return dimensionWorkbenchTarget(searchParams, mappedObject, hash);
		}
		case "/modeling/semantic/models":
			return modelWorkbenchTarget(searchParams, mappedObject, undefined, {}, hash);
		case "/modeling/semantic/metrics":
			return metricWorkbenchTarget(searchParams, mappedObject, hash);
		case "/modeling/semantic/publish":
			return modelWorkbenchTarget(searchParams, mappedObject, undefined, { view: "release" }, hash);
		case "/modeling/semantic/runs":
			return buildTarget("/ops/instances", searchParams, mappedObject, { entryKey: "DBT_RUN" });
		case "/studio/low-code-development":
			return modelWorkbenchTarget(searchParams, mappedObject, undefined, { view: "guided" }, hash);
		case "/modeling/dbt-files":
			return buildTarget("/studio/sql-modeling", searchParams, mappedObject, { view: "files" });
		default:
			return buildTarget("/modeling/workbench", searchParams, mappedObject);
	}
};
