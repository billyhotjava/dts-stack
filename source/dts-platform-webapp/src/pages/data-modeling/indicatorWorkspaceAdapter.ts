import {
	archiveIndicator,
	createIndicator,
	getIndicator,
	getIndicatorPublishPreview,
	listIndicatorReferences,
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
	parseIndicatorDependencyCodes,
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

export type IndicatorVersionSummary = {
	id?: string;
	version?: string;
	status?: string;
	changeSummary?: string;
	createdDate?: string;
	createdBy?: string;
};

export type IndicatorReferenceSummary = {
	id?: string;
	refType?: string;
	refTarget?: string;
	refName?: string;
	notes?: string;
};

export type IndicatorGovernanceContext = {
	versions: IndicatorVersionSummary[];
	references: IndicatorReferenceSummary[];
};

export type IndicatorPublishPreviewSummary = {
	readyToPublish: boolean;
	blockingIssues: Array<{ code?: string; message?: string }>;
	warningIssues: Array<{ code?: string; message?: string }>;
};

export type IndicatorWorkspaceError = {
	kind: "permission" | "request";
	message: string;
};

type IndicatorPage = {
	content?: IndicatorDefinition[];
	totalPages?: number;
	total?: number;
	page?: number;
	size?: number;
};

type IndicatorPageFetcher = (params: { page: number; size: number }) => Promise<unknown>;

const DEFAULT_PAGE_SIZE = 200;
const MAX_PAGES = 100;

const unwrapPayload = (value: unknown): unknown => {
	if (!value || typeof value !== "object") return value;
	const record = value as Record<string, unknown>;
	if (record.data && typeof record.data === "object") {
		const data = record.data as Record<string, unknown>;
		if (Array.isArray(data.content) || Array.isArray(record.content))
			return Array.isArray(record.content) ? record : data;
	}
	return value;
};

const asPage = (value: unknown): IndicatorPage => {
	const payload = unwrapPayload(value);
	if (Array.isArray(payload)) return { content: payload as IndicatorDefinition[], totalPages: 1 };
	if (!payload || typeof payload !== "object") return { content: [], totalPages: 1 };
	return payload as IndicatorPage;
};

const asArray = <T>(value: unknown): T[] => {
	const payload = unwrapPayload(value);
	return Array.isArray(payload) ? (payload as T[]) : [];
};

const asIndicator = (value: unknown): IndicatorDefinition => {
	const payload = unwrapPayload(value);
	if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
		throw new Error("指标服务未返回有效对象");
	}
	return payload as IndicatorDefinition;
};

export async function loadAllIndicators(
	fetchPage: IndicatorPageFetcher,
	options: { pageSize?: number; maxPages?: number } = {},
): Promise<IndicatorDefinition[]> {
	const pageSize = Math.max(1, options.pageSize ?? DEFAULT_PAGE_SIZE);
	const maxPages = Math.max(1, options.maxPages ?? MAX_PAGES);
	const result: IndicatorDefinition[] = [];
	let page = 0;
	let totalPages = 1;
	do {
		const payload = asPage(await fetchPage({ page, size: pageSize }));
		const rows = Array.isArray(payload.content) ? payload.content : [];
		result.push(...rows);
		const declaredPages = Number(payload.totalPages);
		if (Number.isFinite(declaredPages) && declaredPages >= 0) {
			totalPages = Math.max(1, Math.floor(declaredPages));
		} else {
			totalPages = rows.length < pageSize ? page + 1 : page + 2;
		}
		page += 1;
	} while (page < totalPages && page < maxPages);
	return result;
}

export const loadIndicatorCatalog = (): Promise<IndicatorDefinition[]> =>
	loadAllIndicators((params) => listIndicators(params));

const normalizedCategory = (indicator: Pick<IndicatorDefinition, "category">): string =>
	String(indicator.category ?? "")
		.trim()
		.toUpperCase();

export function classifyIndicator(indicator: Pick<IndicatorDefinition, "category" | "isDerived">): MetricType {
	const category = normalizedCategory(indicator);
	if (category === "COMPOSITE" || category === "复合指标") return "复合指标";
	if (category === "MODIFIER" || category === "修饰词") return "修饰词";
	if (["TIME_PERIOD", "PERIOD", "时间周期"].includes(category)) return "时间周期";
	return indicator.isDerived ? "派生指标" : "原子指标";
}

export function toMetricSelection(indicator: IndicatorDefinition): MetricSelection {
	return {
		...indicator,
		code: String(indicator.code ?? ""),
		name: String(indicator.name ?? ""),
		domain: String(indicator.domain ?? ""),
	};
}

export function filterIndicatorCatalog(
	rows: IndicatorDefinition[],
	filter: { type: MetricType; domain?: string; query?: string },
): IndicatorDefinition[] {
	const domain = String(filter.domain ?? "").trim();
	const query = String(filter.query ?? "")
		.trim()
		.toLocaleLowerCase();
	return rows.filter((row) => {
		if (classifyIndicator(row) !== filter.type) return false;
		if (domain && String(row.domain ?? "").trim() !== domain) return false;
		if (!query) return true;
		return [row.code, row.name, row.definition, row.owner]
			.map((value) => String(value ?? "").toLocaleLowerCase())
			.some((value) => value.includes(query));
	});
}

export function createIndicatorDraft(type: MetricType, domain: string | null): MetricSelection {
	const isDerived = type === "派生指标" || type === "复合指标";
	const category =
		type === "复合指标" ? "COMPOSITE" : type === "修饰词" ? "MODIFIER" : type === "时间周期" ? "TIME_PERIOD" : null;
	return {
		code: "",
		name: "",
		domain: String(domain ?? ""),
		category,
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

export const supportsIndicatorCreation = (type: MetricType): boolean => type !== "修饰词" && type !== "时间周期";

const versionsOf = (value: unknown): string[] =>
	asArray<IndicatorVersionSummary>(value)
		.map((item) => String(item.version ?? ""))
		.filter(Boolean);

const validateChanges = (values: IndicatorEditValues) => {
	const issues = validateIndicatorDefinition(values);
	if (issues.length) throw new Error(issues.join("；"));
};

export async function saveIndicatorDraft(
	baseline: MetricSelection,
	changes: IndicatorEditValues,
): Promise<MetricSelection> {
	const normalized = normalizeIndicatorEditValues(changes);
	validateChanges({ ...baseline, ...normalized });
	if (!baseline.id) {
		const payload = buildIndicatorUpsertPayload(baseline, normalized, []);
		return toMetricSelection(asIndicator(await createIndicator(payload)));
	}

	const [latestRaw, versionsRaw] = await Promise.all([getIndicator(baseline.id), listIndicatorVersions(baseline.id)]);
	const latest = asIndicator(latestRaw);
	if (String(latest.status ?? "").toUpperCase() === "PUBLISHED") {
		throw new Error("已发布指标不可直接保存，请使用“发布新版本”");
	}
	const payload = buildExistingIndicatorMutationPayload(baseline, latest, normalized, versionsOf(versionsRaw));
	return toMetricSelection(asIndicator(await updateIndicator(baseline.id, payload)));
}

export async function validateIndicatorDraft(indicator: MetricSelection): Promise<IndicatorPreflightResult> {
	return runIndicatorPreflight(indicator, {
		validateAtomic: (id) => validateIndicator(id) as Promise<{ status?: string; message?: string }>,
		validateDerivation: (id) => validateIndicatorDerivation(id) as Promise<IndicatorPreflightResult>,
	});
}

export async function previewIndicatorPublication(id: string): Promise<IndicatorPublishPreviewSummary> {
	const payload = unwrapPayload(await getIndicatorPublishPreview(id));
	if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
		throw new Error("指标发布预检未返回有效结果");
	}
	const preview = payload as Record<string, unknown>;
	return {
		readyToPublish: preview.readyToPublish === true,
		blockingIssues: Array.isArray(preview.blockingIssues)
			? (preview.blockingIssues as Array<{ code?: string; message?: string }>)
			: [],
		warningIssues: Array.isArray(preview.warningIssues)
			? (preview.warningIssues as Array<{ code?: string; message?: string }>)
			: [],
	};
}

export async function publishIndicatorDraft(
	baseline: MetricSelection,
	changes: IndicatorEditValues,
): Promise<MetricSelection> {
	if (!baseline.id) throw new Error("请先保存指标草稿");
	const normalized = normalizeIndicatorEditValues(changes);
	validateChanges({ ...baseline, ...normalized });
	const [latestRaw, versionsRaw] = await Promise.all([getIndicator(baseline.id), listIndicatorVersions(baseline.id)]);
	const latest = asIndicator(latestRaw);
	const payload = buildExistingIndicatorMutationPayload(baseline, latest, normalized, versionsOf(versionsRaw));
	if (String(latest.status ?? "").toUpperCase() === "PUBLISHED") {
		return toMetricSelection(asIndicator(await publishIndicatorRevision(baseline.id, payload)));
	}
	const saved = toMetricSelection(asIndicator(await updateIndicator(baseline.id, payload)));
	if (!saved.id) throw new Error("指标保存成功但服务端未返回标识");
	const published = await publishIndicatorWithPreview(saved.id, {
		getPublishPreview: (id) =>
			getIndicatorPublishPreview(id) as Promise<{
				readyToPublish?: boolean;
				blockingIssues?: Array<{ code?: string; message?: string }>;
			}>,
		publish: (id) => publishIndicator(id),
	});
	return toMetricSelection(asIndicator(published));
}

export async function archiveIndicatorDraft(indicator: MetricSelection): Promise<MetricSelection> {
	if (!indicator.id) throw new Error("未保存的指标无需归档");
	return toMetricSelection(asIndicator(await archiveIndicator(indicator.id)));
}

export async function loadIndicatorGovernanceContext(id: string): Promise<IndicatorGovernanceContext> {
	const [versions, references] = await Promise.all([listIndicatorVersions(id), listIndicatorReferences(id)]);
	return {
		versions: asArray<IndicatorVersionSummary>(versions),
		references: asArray<IndicatorReferenceSummary>(references),
	};
}

export const dependencyCodesOf = (indicator: Pick<IndicatorDefinition, "dependencyIndicators">): string[] =>
	parseIndicatorDependencyCodes(indicator.dependencyIndicators);

export function normalizeIndicatorError(error: unknown): IndicatorWorkspaceError {
	const status = Number((error as { response?: { status?: number } } | null)?.response?.status ?? 0);
	if (status === 401 || status === 403) {
		return { kind: "permission", message: "当前账号无权访问或维护指标，请联系管理员授权" };
	}
	const message =
		error instanceof Error ? error.message : String((error as { message?: unknown } | null)?.message ?? "");
	return { kind: "request", message: message.trim() || "指标服务请求失败，请稍后重试" };
}
