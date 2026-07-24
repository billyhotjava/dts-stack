import type { IndicatorDefinition } from "./indicatorDefinitionContract.ts";

export type DerivationIssue = { code: string; message: string };
export type IndicatorPreflightResult = {
	valid: boolean;
	compiledExpression: string | null;
	issues: DerivationIssue[];
	dependencyCodes: string[];
	message?: string | null;
};

type PreflightApi = {
	validateAtomic: (id: string) => Promise<{ status?: string; message?: string }>;
	validateDerivation: (id: string) => Promise<IndicatorPreflightResult>;
};

export async function runIndicatorPreflight(
	indicator: Pick<IndicatorDefinition, "id" | "code" | "isDerived" | "dependencyIndicators" | "expressionSql">,
	api: PreflightApi,
): Promise<IndicatorPreflightResult> {
	if (!indicator.id) throw new Error("请先保存指标草稿");
	if (indicator.isDerived) return api.validateDerivation(indicator.id);
	const result = await api.validateAtomic(indicator.id);
	const valid = String(result?.status ?? "").toUpperCase() === "SUCCESS";
	return {
		valid,
		compiledExpression: null,
		issues: valid
			? []
			: [{ code: "INDICATOR_VALIDATION_FAILED", message: result?.message || "指标计算规则校验未通过" }],
		dependencyCodes: [],
		message: result?.message ?? null,
	};
}

type PublishApi<T> = {
	getPublishPreview: (id: string) => Promise<{
		readyToPublish?: boolean;
		blockingIssues?: Array<{ code?: string; message?: string }>;
	}>;
	publish: (id: string) => Promise<T>;
};

export async function publishIndicatorWithPreview<T>(indicatorId: string, api: PublishApi<T>): Promise<T> {
	const preview = await api.getPublishPreview(indicatorId);
	if (!preview?.readyToPublish) {
		const first = preview?.blockingIssues?.[0];
		throw new Error(first?.message || "指标发布预检未通过");
	}
	return api.publish(indicatorId);
}

export function resolveIndicatorDetailRequest(requestedId: string | null, currentId: string | null): string | null {
	const requested = String(requestedId ?? "").trim();
	return requested && requested !== String(currentId ?? "").trim() ? requested : null;
}

export function shouldApplyIndicatorDetailResponse(input: {
	requestSequence: number;
	activeRequestSequence: number;
	formRevisionAtRequest: number;
	currentFormRevision: number;
}): boolean {
	return (
		input.requestSequence === input.activeRequestSequence && input.formRevisionAtRequest === input.currentFormRevision
	);
}

type RollbackPublishResult<T> = {
	published?: boolean;
	indicator?: T;
};

type RollbackPublishApi<T> = {
	rollback: (
		id: string,
		version: string,
		data: { reason: string; publishAfterRollback: boolean },
	) => Promise<RollbackPublishResult<T>>;
};

export async function rollbackIndicatorAndPublish<T extends { status?: string | null }>(
	indicatorId: string,
	version: string,
	reason: string,
	api: RollbackPublishApi<T>,
): Promise<T> {
	const result = await api.rollback(indicatorId, version, { reason, publishAfterRollback: true });
	const indicator = result?.indicator;
	if (!result?.published || !indicator || String(indicator.status ?? "").toUpperCase() !== "PUBLISHED") {
		throw new Error("指标回滚未完成原子发布，当前发布版本保持不变");
	}
	return indicator;
}

export function buildMetricWorkbenchLocation(search: string, hash: string): string {
	const normalizedSearch = search ? (search.startsWith("?") ? search : `?${search}`) : "";
	const normalizedHash = hash ? (hash.startsWith("#") ? hash : `#${hash}`) : "";
	return `/modeling/metric-workbench${normalizedSearch}${normalizedHash}`;
}
