export const STANDARD_BINDING_DRAFT_STORAGE_KEY = "dts.standardBindingDrafts.v1";

export type StandardBindingDraftField = {
	columnName?: string;
	standardId?: string;
	standardCode?: string;
	standardName?: string;
	dataType?: string;
	nullable?: boolean;
	codeSet?: string;
	securityLevel?: string;
	description?: string;
	domain?: string;
	sourceSystem?: string;
	isPk?: boolean;
};

export type StandardBindingDraft = {
	id: string;
	source: "metadata-elements" | "standard-package" | string;
	title: string;
	createdAt: string;
	fields: StandardBindingDraftField[];
	metadata?: Record<string, string | number | boolean | undefined>;
};

export type StandardBindingDraftInput = Omit<StandardBindingDraft, "id" | "createdAt">;

export type StandardBindingDraftSummary = {
	fieldCount: number;
	missingStandardCount: number;
	sourceLabel: string;
	createdAt: string;
};

const MAX_DRAFTS = 12;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const normalizeText = (value?: unknown) => String(value ?? "").trim();

const safeStorage = () => {
	if (typeof window === "undefined") return null;
	try {
		return window.sessionStorage || window.localStorage;
	} catch {
		return null;
	}
};

const readDrafts = (): StandardBindingDraft[] => {
	const storage = safeStorage();
	if (!storage) return [];
	try {
		const raw = storage.getItem(STANDARD_BINDING_DRAFT_STORAGE_KEY);
		const parsed = raw ? JSON.parse(raw) : [];
		return Array.isArray(parsed) ? parsed : [];
	} catch {
		return [];
	}
};

const writeDrafts = (drafts: StandardBindingDraft[]) => {
	const storage = safeStorage();
	if (!storage) return;
	storage.setItem(STANDARD_BINDING_DRAFT_STORAGE_KEY, JSON.stringify(drafts.slice(0, MAX_DRAFTS)));
};

const normalizeIdentifier = (value?: string, fallback = "field") => {
	const normalized = normalizeText(value)
		.toLowerCase()
		.replace(/[^a-z0-9_]+/g, "_")
		.replace(/^_+|_+$/g, "");
	if (!normalized) return fallback;
	return /^[a-z_]/.test(normalized) ? normalized : `f_${normalized}`;
};

export const createStandardBindingDraft = (input: StandardBindingDraftInput) => {
	const fields = (input.fields || [])
		.map((field, index) => {
			const columnName = normalizeIdentifier(field.columnName || field.standardCode, `field_${index + 1}`);
			return {
				...field,
				columnName,
				standardCode: normalizeText(field.standardCode || columnName),
				standardName: normalizeText(field.standardName || field.columnName || columnName),
				dataType: normalizeText(field.dataType || "STRING").toUpperCase(),
			};
		})
		.filter((field) => field.columnName);
	const draft: StandardBindingDraft = {
		...input,
		id: `standard-draft-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
		createdAt: new Date().toISOString(),
		fields,
	};
	const drafts = readDrafts().filter((item) => item.id !== draft.id);
	writeDrafts([draft, ...drafts]);
	return draft;
};

export const isBackendStandardBindingDraftId = (draftId?: string | null) => UUID_PATTERN.test(normalizeText(draftId));

export const getStandardBindingDraft = (draftId?: string | null) => {
	const normalizedId = normalizeText(draftId);
	if (!normalizedId) return null;
	return readDrafts().find((draft) => draft.id === normalizedId) || null;
};

export const buildStandardBindingDraftSummary = (draft?: StandardBindingDraft | null): StandardBindingDraftSummary => {
	const fields = draft?.fields || [];
	return {
		fieldCount: fields.length,
		missingStandardCount: fields.filter((field) => !normalizeText(field.standardId) && !normalizeText(field.standardCode)).length,
		sourceLabel:
			draft?.source === "standard-package"
				? "标准包"
				: draft?.source === "metadata-elements"
					? "数据元"
					: normalizeText(draft?.source) || "标准管理",
		createdAt: normalizeText(draft?.createdAt),
	};
};

export const buildStandardBindingsFromDraft = (draft?: StandardBindingDraft | null) => {
	return (draft?.fields || []).map((field) => ({
		columnName: field.columnName,
		standardId: field.standardId || undefined,
		standardCode: field.standardCode || field.columnName,
		standardName: field.standardName || field.standardCode || field.columnName,
		dataType: field.dataType || "STRING",
		nullable: typeof field.nullable === "boolean" ? field.nullable : undefined,
		codeSet: field.codeSet || undefined,
		securityLevel: field.securityLevel || undefined,
		bindingSource: draft?.source || "standard-draft",
		status: field.standardCode || field.standardId ? "active" : "missing",
		driftReason: field.standardCode || field.standardId ? undefined : "标准草稿缺少数据元编码",
	}));
};

export const buildSqlFromStandardBindingDraft = (draft?: StandardBindingDraft | null) => {
	const fields = draft?.fields || [];
	const selectLines = fields.length
		? fields.map((field, index) => {
			const columnName = normalizeIdentifier(field.columnName, `field_${index + 1}`);
			return `  ${columnName}`;
		})
		: ["  *"];
	return [
		`-- 由 ${draft?.title || "字段落标草稿"} 生成的模型草稿，可按业务表关联关系继续微调`,
		"select",
		selectLines.join(",\n"),
		"from {{ source('ods', 'replace_with_source_table') }}",
		"",
	].join("\n");
};

export const buildModelNameFromStandardBindingDraft = (draft?: StandardBindingDraft | null) => {
	const title = normalizeIdentifier(draft?.title || "standard_model", "standard_model");
	return `dwd_${title}`;
};

export const withStandardDraftRoute = (route: string, draftId?: string | null) => {
	const normalizedId = normalizeText(draftId);
	if (!normalizedId) return route;
	const [path, query = ""] = route.split("?");
	const params = new URLSearchParams(query);
	params.set("standardDraftId", normalizedId);
	return `${path}?${params.toString()}`;
};
