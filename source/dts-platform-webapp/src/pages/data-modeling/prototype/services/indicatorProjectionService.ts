import {
	archiveIndicator,
	createIndicator,
	getIndicator,
	getIndicatorPublishPreview,
	listIndicators,
	listIndicatorVersions,
	publishIndicator,
	publishIndicatorRevision,
	updateIndicator,
	validateIndicator,
	validateIndicatorDerivation,
} from "@/api/services/indicatorGovernanceService";
import {
	buildExistingIndicatorMutationPayload,
	buildIndicatorUpsertPayload,
	type IndicatorDefinition,
	type IndicatorEditValues,
	normalizeIndicatorEditValues,
	validateIndicatorDefinition,
} from "@/features/modeling/indicators/indicatorDefinitionContract";
import {
	type IndicatorPreflightResult,
	publishIndicatorWithPreview,
	runIndicatorPreflight,
} from "@/features/modeling/indicators/indicatorDefinitionWorkflow";

export type MetricType = "复合指标" | "派生指标" | "原子指标" | "修饰词" | "时间周期";

export type MetricSelection = Omit<IndicatorDefinition, "code" | "name" | "domain"> & {
	code: string;
	name: string;
	domain: string;
	isNew?: boolean;
};

type IndicatorPage = { content?: IndicatorDefinition[]; totalPages?: number };

const unwrap = (value: unknown): unknown => {
	if (!value || typeof value !== "object") return value;
	const record = value as Record<string, unknown>;
	if (record.data && typeof record.data === "object") return record.data;
	return value;
};

const page = (value: unknown): IndicatorPage => {
	const payload = unwrap(value);
	if (Array.isArray(payload)) return { content: payload as IndicatorDefinition[], totalPages: 1 };
	return payload && typeof payload === "object" ? (payload as IndicatorPage) : { content: [], totalPages: 1 };
};

const indicator = (value: unknown): IndicatorDefinition => {
	const payload = unwrap(value);
	if (!payload || typeof payload !== "object" || Array.isArray(payload)) throw new Error("指标服务未返回有效对象");
	return payload as IndicatorDefinition;
};

export async function loadIndicatorCatalog(): Promise<IndicatorDefinition[]> {
	const result: IndicatorDefinition[] = [];
	let current = 0;
	let totalPages = 1;
	do {
		const payload = page(await listIndicators({ page: current, size: 200 }));
		result.push(...(payload.content || []));
		totalPages = Math.max(1, Number(payload.totalPages || 1));
		current += 1;
	} while (current < totalPages && current < 100);
	return result;
}

export function classifyIndicator(row: Pick<IndicatorDefinition, "metricType" | "category" | "isDerived">): MetricType {
	const metricType = String(row.metricType || "")
		.trim()
		.toUpperCase();
	if (metricType === "COMPOSITE") return "复合指标";
	if (metricType === "DERIVED") return "派生指标";
	if (metricType === "ATOMIC") return "原子指标";
	const category = String(row.category || "")
		.trim()
		.toUpperCase();
	if (category === "COMPOSITE" || category === "复合指标") return "复合指标";
	if (category === "MODIFIER" || category === "修饰词") return "修饰词";
	if (["TIME_PERIOD", "PERIOD", "时间周期"].includes(category)) return "时间周期";
	return row.isDerived ? "派生指标" : "原子指标";
}

export function metricSelection(row: IndicatorDefinition): MetricSelection {
	return { ...row, code: String(row.code || ""), name: String(row.name || ""), domain: String(row.domain || "") };
}

export function filterIndicators(
	rows: IndicatorDefinition[],
	options: { type: MetricType; domain: string; businessCategoryId?: string; query: string },
) {
	const query = options.query.trim().toLocaleLowerCase();
	return rows.filter((row) => {
		if (classifyIndicator(row) !== options.type) return false;
		if (options.businessCategoryId && String(row.businessCategoryId || "") !== options.businessCategoryId) return false;
		if (options.domain && String(row.dataDomainId || row.domain || "") !== options.domain) return false;
		if (!query) return true;
		return [row.code, row.name, row.definition, row.owner].some((value) =>
			String(value || "")
				.toLocaleLowerCase()
				.includes(query),
		);
	});
}

export const supportsIndicatorCreation = (type: MetricType) =>
	type === "原子指标" || type === "派生指标" || type === "复合指标";

export function createIndicatorDraft(type: MetricType, domain = ""): MetricSelection {
	const isDerived = type === "派生指标" || type === "复合指标";
	const category =
		type === "复合指标" ? "COMPOSITE" : type === "修饰词" ? "MODIFIER" : type === "时间周期" ? "TIME_PERIOD" : null;
	return {
		code: "",
		name: "",
		domain,
		category,
		metricType: type === "复合指标" ? "COMPOSITE" : type === "派生指标" ? "DERIVED" : "ATOMIC",
		status: "DRAFT",
		version: "v1",
		isDerived,
		aggregationType: isDerived ? "DERIVED" : "SUM",
		measureField: null,
		expressionSql: null,
		dependencyIndicators: null,
		dataLevel: "DATA_INTERNAL",
		precisionScale: 0,
		isNew: true,
	};
}

const versions = (value: unknown): string[] => {
	const payload = unwrap(value);
	if (!Array.isArray(payload)) return [];
	return payload.map((item) => String((item as Record<string, unknown>)?.version || "")).filter(Boolean);
};

const assertValid = (values: IndicatorEditValues) => {
	const issues = validateIndicatorDefinition(values);
	if (issues.length) throw new Error(issues.join("；"));
};

export async function saveIndicatorDraft(
	baseline: MetricSelection,
	changes: IndicatorEditValues,
): Promise<MetricSelection> {
	const normalized = normalizeIndicatorEditValues(changes);
	assertValid({ ...baseline, ...normalized });
	if (!baseline.id) {
		return metricSelection(indicator(await createIndicator(buildIndicatorUpsertPayload(baseline, normalized, []))));
	}
	const [latestRaw, versionsRaw] = await Promise.all([getIndicator(baseline.id), listIndicatorVersions(baseline.id)]);
	const latest = indicator(latestRaw);
	if (String(latest.status || "").toUpperCase() === "PUBLISHED")
		throw new Error("已发布指标不可直接保存，请发布新版本");
	return metricSelection(
		indicator(
			await updateIndicator(
				baseline.id,
				buildExistingIndicatorMutationPayload(baseline, latest, normalized, versions(versionsRaw)),
			),
		),
	);
}

export const validateIndicatorDraft = (selected: MetricSelection): Promise<IndicatorPreflightResult> =>
	runIndicatorPreflight(selected, {
		validateAtomic: (id) => validateIndicator(id) as Promise<{ status?: string; message?: string }>,
		validateDerivation: (id) => validateIndicatorDerivation(id) as Promise<IndicatorPreflightResult>,
	});

export async function saveAndValidateIndicatorDraft(
	baseline: MetricSelection,
	changes: IndicatorEditValues,
): Promise<{ saved: MetricSelection; validation: IndicatorPreflightResult }> {
	const saved = await saveIndicatorDraft(baseline, changes);
	return { saved, validation: await validateIndicatorDraft(saved) };
}

export async function publishIndicatorDraft(
	baseline: MetricSelection,
	changes: IndicatorEditValues,
): Promise<MetricSelection> {
	if (!baseline.id) throw new Error("请先保存指标草稿");
	const normalized = normalizeIndicatorEditValues(changes);
	assertValid({ ...baseline, ...normalized });
	const [latestRaw, versionsRaw] = await Promise.all([getIndicator(baseline.id), listIndicatorVersions(baseline.id)]);
	const latest = indicator(latestRaw);
	const payload = buildExistingIndicatorMutationPayload(baseline, latest, normalized, versions(versionsRaw));
	if (String(latest.status || "").toUpperCase() === "PUBLISHED") {
		return metricSelection(indicator(await publishIndicatorRevision(baseline.id, payload)));
	}
	const saved = metricSelection(indicator(await updateIndicator(baseline.id, payload)));
	if (!saved.id) throw new Error("指标保存成功但服务端未返回标识");
	return metricSelection(
		indicator(
			await publishIndicatorWithPreview(saved.id, {
				getPublishPreview: (id) =>
					getIndicatorPublishPreview(id) as Promise<{
						readyToPublish?: boolean;
						blockingIssues?: Array<{ code?: string; message?: string }>;
					}>,
				publish: (id) => publishIndicator(id),
			}),
		),
	);
}

export async function archiveIndicatorDraft(selected: MetricSelection): Promise<MetricSelection> {
	if (!selected.id) throw new Error("未保存的指标无需归档");
	return metricSelection(indicator(await archiveIndicator(selected.id)));
}

export function normalizeIndicatorFailure(error: unknown): { kind: "permission" | "request"; message: string } {
	const response = (error as { response?: { status?: number } } | null)?.response;
	const status = Number(response?.status || 0);
	if (status === 401 || status === 403)
		return { kind: "permission", message: "当前账号无权访问或维护指标，请联系管理员授权。" };
	if (response) return { kind: "request", message: "指标服务请求失败，请稍后重试。" };
	const detail = error instanceof Error ? error.message.trim() : "";
	return { kind: "request", message: detail || "指标服务请求失败，请稍后重试。" };
}

export type { IndicatorDefinition, IndicatorEditValues, IndicatorPreflightResult };
