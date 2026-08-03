import {
	archiveStandard,
	createGlossaryTerm,
	createReferenceCode,
	createStandard,
	listGlossaryTerms,
	listMetadataStandards,
	listReferenceCodes,
	listStandards,
	updateGlossaryTerm,
	updateReferenceCode,
	updateStandard,
} from "@/api/modelingStandardsApi";

export type StandardsView = "fields" | "codes" | "roots" | "dictionary" | "mappings";

export type StandardsRow = {
	id: string;
	code: string;
	name: string;
	dataType: string;
	definition: string;
	domain: string;
	scope: string;
	version: string;
	state: string;
	valueCount: string;
	source: Record<string, unknown>;
};

export type StandardsCapability = {
	list: boolean;
	create: boolean;
	edit: boolean;
	archive: boolean;
	importPackage: boolean;
	disabledReason?: string;
	archiveDisabledReason?: string;
};

export type StandardsEditorValues = {
	code: string;
	name: string;
	dataType: string;
	definition: string;
	domain: string;
	scope: string;
	version: string;
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

const text = (value: unknown, fallback = "") => String(value ?? "").trim() || fallback;
const status = (value: unknown) => STATUS_LABELS[text(value).toUpperCase()] || text(value, "未知");

const content = (payload: unknown): Record<string, unknown>[] => {
	if (Array.isArray(payload))
		return payload.filter((item): item is Record<string, unknown> => Boolean(item && typeof item === "object"));
	if (!payload || typeof payload !== "object") return [];
	const record = payload as Record<string, unknown>;
	if (Array.isArray(record.content)) return content(record.content);
	if (record.data && typeof record.data === "object") return content(record.data);
	return [];
};

const adaptField = (row: Record<string, unknown>): StandardsRow => ({
	id: text(row.id),
	code: text(row.code),
	name: text(row.name),
	dataType: text(row.dataType, "—"),
	definition: text(row.description, "—"),
	domain: text(row.domain, "—"),
	scope: text(row.scope, "—"),
	version: text(row.currentVersion, "—"),
	state: status(row.status),
	valueCount: "—",
	source: row,
});

const adaptCode = (row: Record<string, unknown>): StandardsRow => ({
	id: text(row.codeTypeId),
	code: text(row.codeTypeCode),
	name: text(row.codeTypeName),
	dataType: text(row.dataType, "—"),
	definition: "—",
	domain: text(row.bizCatalog, "—"),
	scope: text(row.stdLevel, "—"),
	version: text(row.version, "—"),
	state: status(row.status),
	valueCount: String(Number(row.itemCount ?? 0)),
	source: row,
});

const adaptGlossary = (row: Record<string, unknown>): StandardsRow => ({
	id: text(row.id),
	code: text(row.code, "—"),
	name: text(row.name),
	dataType: "—",
	definition: text(row.definition, "—"),
	domain: text(row.domain, "—"),
	scope: text(row.aliases, "—"),
	version: text(row.version, "—"),
	state: status(row.status),
	valueCount: "—",
	source: row,
});

const adaptMapping = (row: Record<string, unknown>): StandardsRow => ({
	id: text(row.id),
	code: text(row.fieldNameEn),
	name: text(row.fieldNameCn),
	dataType: text(row.dataType, "—"),
	definition: text(row.codeSet, "未绑定码表"),
	domain: text(row.domain, "—"),
	scope: "模型字段引用",
	version: row.version == null ? "—" : `v${row.version}`,
	state: "有效",
	valueCount: "—",
	source: row,
});

export function standardsCapability(view: StandardsView): StandardsCapability {
	if (view === "roots") {
		return {
			list: false,
			create: false,
			edit: false,
			archive: false,
			importPackage: true,
			disabledReason: "当前服务端没有独立词根契约，不能将业务术语冒充词根。",
		};
	}
	if (view === "mappings") {
		return {
			list: true,
			create: false,
			edit: false,
			archive: false,
			importPackage: true,
			disabledReason: "映射由模型字段保存稳定标准 ID 和版本，本页只读展示引用证据。",
		};
	}
	if (view === "dictionary") {
		return {
			list: true,
			create: true,
			edit: true,
			archive: false,
			importPackage: true,
			archiveDisabledReason: "命名词条 owner 目前只有永久删除契约；为避免数据丢失，归档入口已关闭。",
		};
	}
	return {
		list: true,
		create: true,
		edit: true,
		archive: view === "fields" || view === "codes",
		importPackage: true,
	};
}

export async function loadStandardsRows(view: StandardsView, keyword: string): Promise<StandardsRow[]> {
	if (!standardsCapability(view).list) return [];
	const normalized = keyword.trim() || undefined;
	const payload =
		view === "fields"
			? await listStandards({ page: 0, size: 200, keyword: normalized })
			: view === "codes"
				? await listReferenceCodes({ page: 0, size: 200, keyword: normalized })
				: view === "dictionary"
					? await listGlossaryTerms({ keyword: normalized })
					: await listMetadataStandards({ page: 0, size: 200, keyword: normalized });
	const mapper =
		view === "fields"
			? adaptField
			: view === "codes"
				? adaptCode
				: view === "dictionary"
					? adaptGlossary
					: adaptMapping;
	return content(payload)
		.map(mapper)
		.filter((row) => Boolean(row.id || row.code || row.name));
}

const standardPayload = (values: StandardsEditorValues, current?: Record<string, unknown>) => ({
	code: values.code.trim(),
	name: values.name.trim(),
	domain: values.domain.trim() || undefined,
	scope: values.scope.trim() || undefined,
	status: text(current?.status, "DRAFT"),
	securityLevel: current?.securityLevel || "INTERNAL",
	owner: current?.owner,
	tags: current?.tags,
	version: values.version.trim() || text(current?.currentVersion, "v1"),
	description: values.definition.trim() || undefined,
	dataType: values.dataType.trim() || undefined,
	nullable: current?.nullable,
	codeSet: current?.codeSet,
});

const referenceCodePayload = (
	values: StandardsEditorValues,
	current?: Record<string, unknown>,
	nextStatus?: number,
) => ({
	codeTypeId: current?.codeTypeId,
	codeTypeCode: values.code.trim(),
	codeTypeName: values.name.trim(),
	stdLevel: values.scope.trim() || current?.stdLevel,
	bizCatalog: values.domain.trim() || current?.bizCatalog,
	dataType: values.dataType.trim() || current?.dataType,
	status: nextStatus ?? Number(current?.status ?? 0),
	ownerDept: current?.ownerDept,
	version: values.version.trim() || text(current?.version, "v1"),
});

const glossaryPayload = (values: StandardsEditorValues, current?: Record<string, unknown>) => ({
	name: values.name.trim(),
	code: values.code.trim() || undefined,
	aliases: values.scope.trim() || undefined,
	definition: values.definition.trim() || undefined,
	domain: values.domain.trim() || undefined,
	owner: current?.owner,
	ownerDept: current?.ownerDept,
	tags: current?.tags,
	version: values.version.trim() || text(current?.version, "v1"),
	status: text(current?.status, "DRAFT"),
});

export async function saveStandardsRow(view: StandardsView, values: StandardsEditorValues, row?: StandardsRow | null) {
	if (!values.code.trim() || !values.name.trim()) throw new Error("编码和名称不能为空");
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
	throw new Error(standardsCapability(view).disabledReason || "当前目录不支持编辑");
}

export async function archiveStandardsRow(view: StandardsView, row: StandardsRow) {
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
					definition: row.definition,
					domain: row.domain,
					scope: row.scope,
					version: row.version,
				},
				row.source,
				2,
			),
		);
	}
	if (view === "dictionary") throw new Error("命名词条没有可恢复的归档契约，已禁止执行永久删除");
	throw new Error(standardsCapability(view).disabledReason || "当前目录没有归档契约");
}
