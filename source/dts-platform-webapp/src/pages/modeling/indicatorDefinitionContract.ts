export type IndicatorStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED" | "DEPRECATED" | string;

export type IndicatorDefinition = {
	id?: string;
	code?: string | null;
	name?: string | null;
	category?: string | null;
	definition?: string | null;
	expressionSql?: string | null;
	datasetId?: string | null;
	owner?: string | null;
	ownerDept?: string | null;
	dataLevel?: string | null;
	status?: IndicatorStatus | null;
	version?: string | null;
	versionNotes?: string | null;
	tags?: string | null;
	aggregationType?: string | null;
	measureField?: string | null;
	numeratorExpression?: string | null;
	denominatorExpression?: string | null;
	staticFilter?: string | null;
	dynamicFilterConfig?: string | null;
	isDerived?: boolean | null;
	dependencyIndicators?: string | null;
	windowFunction?: string | null;
	dimensionFields?: string | null;
	dateColumn?: string | null;
	timeGrain?: string | null;
	granularity?: string | null;
	sourceTable?: string | null;
	joinConfig?: string | null;
	sourceLayer?: string | null;
	targetLayer?: string | null;
	targetModelName?: string | null;
	unit?: string | null;
	precisionScale?: number | null;
	thresholdMin?: number | null;
	thresholdMax?: number | null;
	direction?: string | null;
	businessOwner?: string | null;
	dataPrivacy?: string | null;
	llmGenerated?: boolean | null;
	llmConfidence?: number | null;
	llmSourceRef?: string | null;
	humanVerified?: boolean | null;
	domain?: string | null;
	icon?: string | null;
	displayOrder?: number | null;
	templateId?: string | null;
	lastValidationStatus?: string | null;
	lastValidationMessage?: string | null;
	lastValidatedAt?: string | null;
	createdBy?: string | null;
	createdDate?: string | null;
	lastModifiedBy?: string | null;
	lastModifiedDate?: string | null;
};

export type IndicatorUpsertPayload = {
	[K in Exclude<
		keyof IndicatorDefinition,
		| "id"
		| "lastValidationStatus"
		| "lastValidationMessage"
		| "lastValidatedAt"
		| "createdBy"
		| "createdDate"
		| "lastModifiedBy"
		| "lastModifiedDate"
	>]: IndicatorDefinition[K];
};

export type IndicatorEditValues = Partial<IndicatorUpsertPayload> & {
	dependencyCodes?: string[];
};

export type IndicatorMutationPayload = IndicatorUpsertPayload & {
	expectedLastModifiedDate: string;
};

type IndicatorFormEditValues = IndicatorEditValues & {
	dimensionCodes?: string[];
};

type IndicatorDimensionField = {
	name: string;
	comment?: string | null;
};

const UPSERT_FIELDS = [
	"code",
	"name",
	"category",
	"definition",
	"expressionSql",
	"datasetId",
	"owner",
	"ownerDept",
	"dataLevel",
	"status",
	"version",
	"versionNotes",
	"tags",
	"aggregationType",
	"measureField",
	"numeratorExpression",
	"denominatorExpression",
	"staticFilter",
	"dynamicFilterConfig",
	"isDerived",
	"dependencyIndicators",
	"windowFunction",
	"dimensionFields",
	"dateColumn",
	"timeGrain",
	"granularity",
	"sourceTable",
	"joinConfig",
	"sourceLayer",
	"targetLayer",
	"targetModelName",
	"unit",
	"precisionScale",
	"thresholdMin",
	"thresholdMax",
	"direction",
	"businessOwner",
	"dataPrivacy",
	"llmGenerated",
	"llmConfidence",
	"llmSourceRef",
	"humanVerified",
	"domain",
	"icon",
	"displayOrder",
	"templateId",
] as const satisfies readonly (keyof IndicatorUpsertPayload)[];

const cleanCodes = (values: unknown[]): string[] => {
	const seen = new Set<string>();
	const result: string[] = [];
	for (const raw of values) {
		const code = String(raw ?? "").trim();
		const key = code.toUpperCase();
		if (!code || seen.has(key)) continue;
		seen.add(key);
		result.push(code);
	}
	return result;
};

export function parseIndicatorDependencyCodes(value: unknown): string[] {
	if (Array.isArray(value)) return cleanCodes(value);
	if (typeof value !== "string" || !value.trim()) return [];
	const text = value.trim();
	if (text.startsWith("[")) {
		try {
			const parsed = JSON.parse(text);
			return Array.isArray(parsed) ? cleanCodes(parsed) : [];
		} catch {
			return [];
		}
	}
	if (text.startsWith("{")) return [];
	return cleanCodes(text.split(","));
}

export function nextIndicatorVersion(versions: Array<string | null | undefined>): string {
	const highest = versions.reduce((current, value) => {
		const match = String(value ?? "")
			.trim()
			.match(/^v([1-9][0-9]*)$/i);
		return match ? Math.max(current, Number(match[1])) : current;
	}, 0);
	return `v${highest + 1}`;
}

const copyUpsertFields = (source: IndicatorDefinition): IndicatorUpsertPayload => {
	const result: Record<string, unknown> = {};
	for (const field of UPSERT_FIELDS) {
		result[field] = source[field] ?? null;
	}
	return result as IndicatorUpsertPayload;
};

export function buildIndicatorUpsertPayload(
	current: IndicatorDefinition,
	changes: IndicatorEditValues,
	knownVersions: Array<string | null | undefined>,
): IndicatorUpsertPayload {
	const result = copyUpsertFields(current);
	for (const field of UPSERT_FIELDS) {
		if (field === "dependencyIndicators") continue;
		if (Object.hasOwn(changes, field)) {
			(result as Record<string, unknown>)[field] = changes[field] ?? null;
		}
	}
	if (Object.hasOwn(changes, "dependencyCodes")) {
		const dependencies = cleanCodes(changes.dependencyCodes ?? []);
		result.dependencyIndicators = dependencies.length ? JSON.stringify(dependencies) : null;
	} else if (Object.hasOwn(changes, "dependencyIndicators")) {
		result.dependencyIndicators = changes.dependencyIndicators ?? null;
	}

	const status = String(current.status ?? "").toUpperCase();
	if (current.id && status && status !== "DRAFT") {
		result.status = "DRAFT";
		result.version = nextIndicatorVersion([...knownVersions, current.version]);
	} else {
		result.status = result.status || "DRAFT";
		result.version = result.version || nextIndicatorVersion(knownVersions);
	}
	return result;
}

export function buildIndicatorFormChanges(
	allValues: IndicatorFormEditValues,
	editedFieldNames: ReadonlySet<string>,
	datasetFields: readonly IndicatorDimensionField[],
): IndicatorEditValues {
	const changes: Record<string, unknown> = {};
	for (const field of UPSERT_FIELDS) {
		if (field === "dependencyIndicators" || field === "dimensionFields") continue;
		if (editedFieldNames.has(field)) {
			changes[field] = allValues[field] ?? null;
		}
	}
	if (editedFieldNames.has("dependencyCodes")) {
		changes.dependencyCodes = cleanCodes(allValues.dependencyCodes ?? []);
	}
	if (editedFieldNames.has("dimensionCodes")) {
		const dimensionCodes = cleanCodes(allValues.dimensionCodes ?? []);
		changes.dimensionFields = dimensionCodes.length
			? JSON.stringify(
					dimensionCodes.map((field) => ({
						field,
						displayName: datasetFields.find((item) => item.name === field)?.comment || field,
					})),
				)
			: null;
	}
	if (editedFieldNames.has("isDerived") && !allValues.isDerived) {
		changes.aggregationType = allValues.aggregationType ?? null;
	}
	return normalizeIndicatorEditValues(changes as IndicatorEditValues);
}

export function buildExistingIndicatorMutationPayload(
	baseline: IndicatorDefinition,
	latest: IndicatorDefinition,
	changes: IndicatorEditValues,
	knownVersions: Array<string | null | undefined>,
): IndicatorMutationPayload {
	if (!baseline.id || !latest.id || baseline.id !== latest.id) {
		throw new Error("指标详情已失效，请重新加载后再编辑");
	}
	const expectedLastModifiedDate = String(baseline.lastModifiedDate ?? "").trim();
	if (!expectedLastModifiedDate) {
		throw new Error("指标缺少并发版本信息，请重新加载后再编辑");
	}
	if (expectedLastModifiedDate !== String(latest.lastModifiedDate ?? "").trim()) {
		throw new Error("指标已被其他用户更新，请重新加载最新版本后再编辑");
	}
	return {
		...buildIndicatorUpsertPayload(latest, changes, knownVersions),
		expectedLastModifiedDate,
	};
}

export function validateIndicatorDefinition(values: IndicatorEditValues): string[] {
	const issues: string[] = [];
	const code = String(values.code ?? "").trim();
	const name = String(values.name ?? "").trim();
	if (!code) issues.push("指标编码不能为空");
	if (!name) issues.push("指标名称不能为空");

	if (values.isDerived) {
		const dependencies = cleanCodes(values.dependencyCodes ?? []);
		const nonSelfDependencies = dependencies.filter((item) => item.toUpperCase() !== code.toUpperCase());
		if (!nonSelfDependencies.length) issues.push("派生指标至少选择一个非自身依赖指标");
		if (!String(values.expressionSql ?? "").trim()) issues.push("派生指标必须填写受控派生表达式或计算 SQL");
	} else {
		if (!String(values.aggregationType ?? "").trim()) issues.push("原子指标必须选择聚合方式");
		if (
			String(values.aggregationType ?? "")
				.trim()
				.toUpperCase() === "DERIVED"
		) {
			issues.push("原子指标聚合方式不能为 DERIVED");
		}
		if (!String(values.measureField ?? "").trim()) issues.push("原子指标必须选择度量字段");
	}
	return issues;
}

export function normalizeIndicatorEditValues(values: IndicatorEditValues): IndicatorEditValues {
	if (!Object.hasOwn(values, "isDerived")) return { ...values };
	if (values.isDerived) {
		return {
			...values,
			aggregationType: "DERIVED",
			datasetId: null,
			measureField: null,
			numeratorExpression: null,
			denominatorExpression: null,
		};
	}
	return {
		...values,
		aggregationType: String(values.aggregationType ?? "").toUpperCase() === "DERIVED" ? null : values.aggregationType,
		dependencyCodes: [],
	};
}
