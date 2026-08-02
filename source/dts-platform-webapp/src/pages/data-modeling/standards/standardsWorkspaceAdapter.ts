import {
	archiveStandard,
	createGlossaryTerm,
	createReferenceCode,
	createStandard,
	deleteGlossaryTerm,
	getGlossaryTermReferences,
	getMetadataStandardReferences,
	getReferenceCode,
	getReferenceCodeReferences,
	getStandard,
	listGlossaryTermReviews,
	listGlossaryTerms,
	listGlossaryTermVersions,
	listMetadataStandards,
	listReferenceCodes,
	listStandards,
	listStandardVersions,
	updateGlossaryTerm,
	updateReferenceCode,
	updateStandard,
} from "@/api/modelingStandardsApi";

export type StandardsView = "fields" | "codes" | "roots" | "dictionary" | "mappings";
export type StandardsCatalogPayload =
	| Array<Record<string, unknown>>
	| {
			content?: Array<Record<string, unknown>>;
			total?: number;
			page?: number;
			size?: number;
	  }
	| null
	| undefined;

export type StandardsCatalogRow = {
	id: string;
	code: string;
	name: string;
	state: string;
	dataType?: string;
	definition?: string;
	version?: string;
	valueCount?: number;
	scope?: string;
	standard?: string;
	model?: string;
	field?: string;
	method?: string;
	aliases?: string;
	domain?: string;
	source: Record<string, unknown>;
};

export type StandardsCatalog = {
	rows: StandardsCatalogRow[];
	total: number;
};

export type StandardsCapability = {
	list: boolean;
	create: boolean;
	edit: boolean;
	archive: boolean;
	details: boolean;
	importPackage: boolean;
	disabledReason?: string;
};

export type StandardsEditorValues = {
	code: string;
	name: string;
	dataType?: string;
	definition?: string;
	domain?: string;
	version?: string;
	aliases?: string;
	scope?: string;
};

export type StandardsDetail = {
	title: string;
	primary: Record<string, unknown>;
	sections: Array<{ title: string; rows: Array<Record<string, unknown>> }>;
};

const STATUS_LABELS: Record<string, string> = {
	ACTIVE: "已生效",
	DRAFT: "草稿",
	IN_REVIEW: "评审中",
	PUBLISHED: "已发布",
	DEPRECATED: "已弃用",
	RETIRED: "已退役",
	ARCHIVED: "已归档",
	INACTIVE: "已停用",
	"0": "草稿",
	"1": "已发布",
	"2": "已废弃",
};

const asText = (value: unknown, fallback = "") => {
	const text = String(value ?? "").trim();
	return text || fallback;
};

const statusLabel = (value: unknown) => {
	const normalized = asText(value).toUpperCase();
	return STATUS_LABELS[normalized] || asText(value, "未知");
};

const contentOf = (payload: StandardsCatalogPayload) => {
	if (Array.isArray(payload)) return payload;
	return Array.isArray(payload?.content) ? payload.content : [];
};

const totalOf = (payload: StandardsCatalogPayload, rows: Array<Record<string, unknown>>) => {
	if (Array.isArray(payload)) return rows.length;
	const total = Number(payload?.total);
	return Number.isFinite(total) ? total : rows.length;
};

const fieldsRow = (row: Record<string, unknown>): StandardsCatalogRow => ({
	id: asText(row.id),
	code: asText(row.code),
	name: asText(row.name),
	dataType: asText(row.dataType, "—"),
	definition: asText(row.description, "—"),
	version: asText(row.currentVersion, "—"),
	state: statusLabel(row.status),
	domain: asText(row.domain, "—"),
	scope: asText(row.scope, "—"),
	source: row,
});

const codeRow = (row: Record<string, unknown>): StandardsCatalogRow => ({
	id: asText(row.codeTypeId),
	code: asText(row.codeTypeCode),
	name: asText(row.codeTypeName),
	valueCount: Number(row.itemCount ?? 0),
	domain: asText(row.bizCatalog, "—"),
	scope: asText(row.stdLevel, "—"),
	dataType: asText(row.dataType, "—"),
	version: asText(row.version, "—"),
	state: statusLabel(row.status),
	source: row,
});

const dictionaryRow = (row: Record<string, unknown>): StandardsCatalogRow => ({
	id: asText(row.id),
	code: asText(row.code, "—"),
	name: asText(row.name),
	aliases: asText(row.aliases, "—"),
	definition: asText(row.definition, "—"),
	domain: asText(row.domain, "—"),
	version: asText(row.version, "—"),
	state: statusLabel(row.status),
	source: row,
});

const mappingRow = (row: Record<string, unknown>): StandardsCatalogRow => ({
	id: asText(row.id),
	code: asText(row.fieldNameEn),
	name: asText(row.fieldNameCn),
	standard: asText(row.codeSet, "未绑定码表"),
	model: asText(row.domain, "—"),
	field: asText(row.fieldNameEn),
	method: row.codeSet ? "码表引用" : "数据元引用",
	dataType: asText(row.dataType, "—"),
	version: row.version == null ? "—" : `v${row.version}`,
	state: "有效",
	source: row,
});

export function adaptStandardsCatalog(view: StandardsView, payload: StandardsCatalogPayload): StandardsCatalog {
	const content = contentOf(payload);
	const mapper =
		view === "fields" ? fieldsRow : view === "codes" ? codeRow : view === "dictionary" ? dictionaryRow : mappingRow;
	const rows = view === "roots" ? [] : content.map(mapper).filter((row) => row.id || row.code || row.name);
	return { rows, total: totalOf(payload, content) };
}

const ROOT_DISABLED_REASON = "当前服务端没有独立词根契约，不能将业务术语冒充词根；该入口暂只保留能力说明。";

export function standardsCapabilityFor(view: StandardsView): StandardsCapability {
	if (view === "roots") {
		return {
			list: false,
			create: false,
			edit: false,
			archive: false,
			details: false,
			importPackage: true,
			disabledReason: ROOT_DISABLED_REASON,
		};
	}
	if (view === "mappings") {
		return {
			list: true,
			create: false,
			edit: false,
			archive: false,
			details: true,
			importPackage: true,
			disabledReason: "映射关系由模型字段保存稳定标准 ID 和版本，本页只读展示引用证据。",
		};
	}
	return {
		list: true,
		create: true,
		edit: true,
		archive: view === "fields" || view === "codes",
		details: true,
		importPackage: true,
		disabledReason: view === "dictionary" ? "业务术语当前没有归档契约；存在引用时服务端会拦截删除。" : undefined,
	};
}

export async function loadStandardsCatalog(view: StandardsView, keyword: string): Promise<StandardsCatalog> {
	if (!standardsCapabilityFor(view).list) return { rows: [], total: 0 };
	const normalized = keyword.trim() || undefined;
	const payload =
		view === "fields"
			? await listStandards({ page: 0, size: 200, keyword: normalized })
			: view === "codes"
				? await listReferenceCodes({ page: 0, size: 200, keyword: normalized })
				: view === "dictionary"
					? await listGlossaryTerms({ keyword: normalized })
					: await listMetadataStandards({ page: 0, size: 200, keyword: normalized });
	return adaptStandardsCatalog(view, payload as StandardsCatalogPayload);
}

const standardPayload = (values: StandardsEditorValues, current?: Record<string, unknown>) => ({
	code: values.code.trim(),
	name: values.name.trim(),
	domain: values.domain?.trim() || undefined,
	scope: values.scope?.trim() || undefined,
	status: asText(current?.status, "DRAFT"),
	securityLevel: current?.securityLevel || "INTERNAL",
	owner: current?.owner,
	tags: current?.tags,
	version: values.version?.trim() || asText(current?.currentVersion, "v1"),
	versionNotes: current?.versionNotes,
	description: values.definition?.trim() || undefined,
	dataType: values.dataType?.trim() || undefined,
	nullable: current?.nullable,
	codeSet: current?.codeSet,
	reviewCycle: current?.reviewCycle,
	lastReviewAt: current?.lastReviewAt,
});

const referenceCodePayload = (values: StandardsEditorValues, current?: Record<string, unknown>, status?: number) => ({
	codeTypeId: current?.codeTypeId,
	codeTypeCode: values.code.trim(),
	codeTypeName: values.name.trim(),
	stdLevel: values.scope?.trim() || current?.stdLevel,
	bizCatalog: values.domain?.trim() || current?.bizCatalog,
	dataType: values.dataType?.trim() || current?.dataType,
	status: status ?? Number(current?.status ?? 0),
	ownerDept: current?.ownerDept,
	version: values.version?.trim() || asText(current?.version, "v1"),
});

const glossaryPayload = (values: StandardsEditorValues, current?: Record<string, unknown>) => ({
	name: values.name.trim(),
	code: values.code.trim() || undefined,
	aliases: values.aliases?.trim() || undefined,
	definition: values.definition?.trim() || undefined,
	domain: values.domain?.trim() || undefined,
	owner: current?.owner,
	ownerDept: current?.ownerDept,
	tags: current?.tags,
	version: values.version?.trim() || asText(current?.version, "v1"),
	status: asText(current?.status, "DRAFT"),
	versionNotes: current?.versionNotes,
});

export async function saveStandardsRow(
	view: StandardsView,
	values: StandardsEditorValues,
	row?: StandardsCatalogRow | null,
) {
	if (view === "fields") {
		const payload = standardPayload(values, row?.source);
		return row?.id ? updateStandard(row.id, payload) : createStandard(payload);
	}
	if (view === "codes") {
		const payload = referenceCodePayload(values, row?.source);
		return row?.id ? updateReferenceCode(row.id, payload) : createReferenceCode(payload);
	}
	if (view === "dictionary") {
		const payload = glossaryPayload(values, row?.source);
		return row?.id ? updateGlossaryTerm(row.id, payload) : createGlossaryTerm(payload);
	}
	throw new Error(standardsCapabilityFor(view).disabledReason || "当前目录不支持编辑");
}

export async function archiveStandardsRow(view: StandardsView, row: StandardsCatalogRow) {
	if (!row.id) throw new Error("记录缺少稳定标识，不能归档");
	if (view === "fields") return archiveStandard(row.id);
	if (view === "codes") {
		return updateReferenceCode(
			row.id,
			referenceCodePayload(
				{
					code: row.code,
					name: row.name,
					dataType: row.dataType,
					domain: row.domain,
					version: row.version,
					scope: row.scope,
				},
				row.source,
				2,
			),
		);
	}
	throw new Error(standardsCapabilityFor(view).disabledReason || "当前目录没有归档契约");
}

const records = (value: unknown): Array<Record<string, unknown>> =>
	Array.isArray(value)
		? value.filter((item): item is Record<string, unknown> => Boolean(item && typeof item === "object"))
		: [];

export async function loadStandardsDetail(view: StandardsView, row: StandardsCatalogRow): Promise<StandardsDetail> {
	if (view === "fields") {
		const [primary, versions] = await Promise.all([getStandard(row.id), listStandardVersions(row.id)]);
		return {
			title: `${row.name} · 标准详情`,
			primary: (primary || row.source) as Record<string, unknown>,
			sections: [{ title: "版本记录", rows: records(versions) }],
		};
	}
	if (view === "codes") {
		const [primary, references] = await Promise.all([getReferenceCode(row.id), getReferenceCodeReferences(row.id)]);
		return {
			title: `${row.name} · 码表详情`,
			primary: (primary || row.source) as Record<string, unknown>,
			sections: [{ title: "引用关系", rows: records((references as Record<string, unknown>)?.items) }],
		};
	}
	if (view === "dictionary") {
		const [references, versions, reviews] = await Promise.all([
			getGlossaryTermReferences(row.id),
			listGlossaryTermVersions(row.id),
			listGlossaryTermReviews(row.id),
		]);
		return {
			title: `${row.name} · 术语详情`,
			primary: row.source,
			sections: [
				{ title: "引用关系", rows: records((references as Record<string, unknown>)?.items) },
				{ title: "版本记录", rows: records(versions) },
				{ title: "评审记录", rows: records(reviews) },
			],
		};
	}
	if (view === "mappings") {
		const references = (await getMetadataStandardReferences(row.id)) as Record<string, unknown>;
		return {
			title: `${row.name} · 模型字段引用`,
			primary: row.source,
			sections: [{ title: "引用关系", rows: records(references?.items) }],
		};
	}
	throw new Error(standardsCapabilityFor(view).disabledReason || "当前目录没有详情契约");
}

export async function deleteDictionaryTerm(row: StandardsCatalogRow) {
	if (!row.id) throw new Error("记录缺少稳定标识，不能删除");
	return deleteGlossaryTerm(row.id);
}
