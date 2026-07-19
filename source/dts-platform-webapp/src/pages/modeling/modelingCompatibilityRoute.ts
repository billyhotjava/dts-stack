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

const canonicalContext = (searchParams: URLSearchParams, mapped?: LegacyObjectTarget) => ({
	planId: value(mapped?.planId) || value(searchParams.get("planId")) || value(searchParams.get("planningId")),
	domainId: value(mapped?.domainId) || value(searchParams.get("domainId")) || value(searchParams.get("active")),
	modelSpecId:
		value(mapped?.modelSpecId) || value(searchParams.get("modelSpecId")) || value(searchParams.get("modelId")),
	revision: value(mapped?.revision) || value(searchParams.get("revision")),
	modelType: value(mapped?.modelType) || value(searchParams.get("modelType")),
	returnTo: sanitizeModelingReturnTo(searchParams.get("returnTo")),
});

const buildTarget = (
	path: string,
	searchParams: URLSearchParams,
	mapped?: LegacyObjectTarget,
	fixed: Record<string, string> = {},
) => {
	const params = new URLSearchParams(fixed);
	const context = canonicalContext(searchParams, mapped);
	for (const [key, nextValue] of Object.entries(context)) {
		if (nextValue) params.set(key, nextValue);
	}
	const query = params.toString();
	return { kind: "redirect", to: query ? `${path}?${query}` : path } as const;
};

export const resolveModelingCompatibilityTarget = (
	pathname: string,
	searchParams: URLSearchParams,
	mappedObject?: LegacyObjectTarget,
): ModelingCompatibilityTarget => {
	switch (pathname) {
		case "/modeling/semantic/subjects":
			return buildTarget("/governance/subjects", searchParams, mappedObject);
		case "/modeling/semantic/objects": {
			const legacyObjectId = value(searchParams.get("objectId"));
			if (legacyObjectId && !mappedObject) {
				return { kind: "recovery", code: "NEEDS_CLASSIFICATION", legacyObjectId };
			}
			if (mappedObject && mappedObject.modelType !== "DIMENSION") {
				return buildTarget(`/modeling/models/${encodeURIComponent(mappedObject.modelSpecId)}`, searchParams, mappedObject);
			}
			return buildTarget("/modeling/dimensions", searchParams, mappedObject);
		}
		case "/modeling/semantic/models":
			return buildTarget("/modeling/models", searchParams, mappedObject);
		case "/modeling/semantic/metrics":
			return buildTarget("/modeling/metric-workbench", searchParams, mappedObject);
		case "/modeling/semantic/publish":
			return buildTarget("/modeling/models", searchParams, mappedObject, { view: "release" });
		case "/modeling/semantic/runs":
			return buildTarget("/ops/instances", searchParams, mappedObject, { entryKey: "DBT_RUN" });
		case "/studio/low-code-development":
			return buildTarget("/modeling/models", searchParams, mappedObject, { view: "guided" });
		case "/modeling/dbt-files":
			return buildTarget("/studio/sql-modeling", searchParams, mappedObject, { view: "files" });
		default:
			return buildTarget("/modeling/workbench", searchParams, mappedObject);
	}
};
