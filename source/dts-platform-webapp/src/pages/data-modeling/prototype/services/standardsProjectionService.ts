import {
	archiveStandard,
	createGlossaryTerm,
	createReferenceCode,
	createStandard,
	createWordRoot,
	listGlossaryTerms,
	listMetadataStandards,
	listReferenceCodes,
	listStandards,
	listWordRoots,
	updateGlossaryTerm,
	updateReferenceCode,
	updateStandard,
	updateWordRoot,
} from "@/api/modelingStandardsApi";
import { applyModelSpecStandardElementBindings, listModelSpecs, updateModelSpec } from "@/api/modelSpecApi";
import type {
	CanonicalModelSpecView,
	ModelSpecStandardBinding,
	ModelSpecView,
	UpdateModelSpecCommand,
} from "@/features/modeling/contracts/modelSpecV2Contract";

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
	status?: string;
	code: string;
	name: string;
	dataType: string;
	definition: string;
	domain: string;
	scope: string;
	version: string;
};

export type StandardMappingValues = {
	modelId: string;
	fieldName: string;
	standardId: string;
};

export type StandardMappingOptions = {
	models: Array<{
		id: string;
		name: string;
		status: string;
		fields: Array<{ name: string; displayName: string; dataType: string }>;
	}>;
	standards: Array<{ id: string; code: string; name: string; version: number }>;
};

export type StandardMappingSuggestion = {
	id: string;
	modelId: string;
	modelName: string;
	modelStatus: "DRAFT" | "PUBLISHED";
	modelRevision: number;
	modelChecksum: string;
	fieldName: string;
	fieldLabel: string;
	fieldDataType: string;
	standardId: string;
	standardCode: string;
	standardName: string;
	standardVersion: number;
	createsDraft: boolean;
};

export type StandardMappingApplyResult = {
	modelId: string;
	modelName: string;
	success: boolean;
	revision?: number;
	message?: string;
};

const STATUS_LABELS: Record<string, string> = {
	ACTIVE: "已生效",
	DRAFT: "草稿",
	DESIGNING: "设计中",
	VALIDATING: "校验中",
	READY_TO_PUBLISH: "待发布",
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

const adaptWordRoot = (row: Record<string, unknown>): StandardsRow => ({
	id: text(row.id),
	code: text(row.code),
	name: text(row.nameCn),
	dataType: "—",
	definition: text(row.nameEn, "—"),
	domain: text(row.domain, "—"),
	scope: text(row.abbreviation, "—"),
	version: text(row.version, "—"),
	state: status(row.status),
	valueCount: "—",
	source: row,
});

const isCanonicalModel = (model: ModelSpecView): model is CanonicalModelSpecView =>
	model.contractVersion === 2 && model.compatibilityMode === "CANONICAL";

const positiveVersion = (value: unknown) => {
	const version = Number(value);
	return Number.isInteger(version) && version > 0 ? version : null;
};

const normalizedCode = (value: unknown) => text(value).normalize("NFKC").toLowerCase();

const normalizedDataType = (value: unknown) => {
	const base = text(value).toUpperCase().replace(/\(.*/, "").trim();
	if (["CHAR", "CHARACTER", "VARCHAR", "TEXT", "STRING"].includes(base)) return "STRING";
	if (["INT", "INTEGER", "INT4"].includes(base)) return "INTEGER";
	if (["BIGINT", "INT8", "LONG"].includes(base)) return "BIGINT";
	if (["DECIMAL", "NUMERIC", "NUMBER"].includes(base)) return "DECIMAL";
	if (["BOOL", "BOOLEAN"].includes(base)) return "BOOLEAN";
	return base;
};

const loadMappingOwners = async () => {
	const [modelPayload, standardPayload] = await Promise.all([
		listModelSpecs(),
		listMetadataStandards({ page: 0, size: 500 }),
	]);
	return {
		models: modelPayload.filter(isCanonicalModel),
		standards: content(standardPayload),
	};
};

const standardLabel = (standard?: Record<string, unknown>, fallback = "未解析标准") => {
	if (!standard) return fallback;
	const code = text(standard.fieldNameEn);
	const name = text(standard.fieldNameCn, code);
	return code && name !== code ? `${name}（${code}）` : name || fallback;
};

const mappingRows = (
	models: CanonicalModelSpecView[],
	standards: Record<string, unknown>[],
	keyword?: string,
): StandardsRow[] => {
	const standardsById = new Map(standards.map((standard) => [text(standard.id), standard]));
	const rows = models.flatMap((model) => {
		const fieldsByName = new Map(model.fields.map((field) => [field.name, field]));
		return model.standardBindings.map((binding) => {
			const field = fieldsByName.get(binding.fieldName);
			const standardId = text(binding.standardElementId);
			const standard = standardId ? standardsById.get(standardId) : undefined;
			const referenceLabel = binding.referenceCode ? `码表 ${binding.referenceCode}` : "未绑定数据元";
			const version = binding.standardElementVersion || binding.referenceCodeVersion;
			return {
				id: `${model.id}:${binding.fieldName}`,
				code: binding.fieldName,
				name: text(field?.displayName, binding.fieldName),
				dataType: text(field?.dataType, "—"),
				definition: standardLabel(standard, referenceLabel),
				domain: model.name,
				scope: model.id,
				version: version ? `v${version}` : "—",
				state: status(model.status),
				valueCount: "—",
				source: {
					modelId: model.id,
					fieldName: binding.fieldName,
					standardId,
				},
			};
		});
	});
	const normalized = text(keyword).toLowerCase();
	if (!normalized) return rows;
	return rows.filter((row) =>
		[row.code, row.name, row.definition, row.domain].some((value) => value.toLowerCase().includes(normalized)),
	);
};

export function standardsCapability(view: StandardsView): StandardsCapability {
	if (view === "roots") {
		return {
			list: true,
			create: true,
			edit: true,
			archive: false,
			importPackage: true,
		};
	}
	if (view === "mappings") {
		return {
			list: true,
			create: true,
			edit: true,
			archive: false,
			importPackage: true,
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
	if (view === "mappings") {
		const owners = await loadMappingOwners();
		return mappingRows(owners.models, owners.standards, normalized);
	}
	const payload =
		view === "fields"
			? await listStandards({ page: 0, size: 200, keyword: normalized })
			: view === "codes"
				? await listReferenceCodes({ page: 0, size: 200, keyword: normalized })
				: view === "roots"
					? await listWordRoots({ keyword: normalized })
					: await listGlossaryTerms({ keyword: normalized });
	const mapper =
		view === "fields" ? adaptField : view === "codes" ? adaptCode : view === "roots" ? adaptWordRoot : adaptGlossary;
	return content(payload)
		.map(mapper)
		.filter((row) => Boolean(row.id || row.code || row.name));
}

export async function loadStandardMappingOptions(): Promise<StandardMappingOptions> {
	const owners = await loadMappingOwners();
	return {
		models: owners.models
			.filter((model) => model.status === "DRAFT")
			.map((model) => ({
				id: model.id,
				name: model.name,
				status: status(model.status),
				fields: model.fields.map((field) => ({
					name: field.name,
					displayName: text(field.displayName, field.name),
					dataType: field.dataType,
				})),
			})),
		standards: owners.standards.flatMap((standard) => {
			const version = positiveVersion(standard.version);
			if (!text(standard.id) || !text(standard.fieldNameEn) || version == null) return [];
			return [
				{
					id: text(standard.id),
					code: text(standard.fieldNameEn),
					name: text(standard.fieldNameCn, text(standard.fieldNameEn)),
					version,
				},
			];
		}),
	};
}

export async function previewSuggestedStandardMappings(): Promise<StandardMappingSuggestion[]> {
	const owners = await loadMappingOwners();
	const standardsByCode = new Map<string, Record<string, unknown>[]>();
	for (const standard of owners.standards) {
		const code = normalizedCode(standard.fieldNameEn);
		const version = positiveVersion(standard.version);
		if (!code || !text(standard.id) || version == null) continue;
		standardsByCode.set(code, [...(standardsByCode.get(code) || []), standard]);
	}

	return owners.models.flatMap((model) => {
		const modelStatus = model.status === "DRAFT" ? "DRAFT" : model.status === "PUBLISHED" ? "PUBLISHED" : null;
		if (!modelStatus) return [];
		const bindingsByField = new Map(model.standardBindings.map((binding) => [binding.fieldName, binding]));
		return model.fields.flatMap((field): StandardMappingSuggestion[] => {
			const existing = bindingsByField.get(field.name);
			if (existing?.standardElementId) return [];
			const matches = standardsByCode.get(normalizedCode(field.name)) || [];
			if (matches.length !== 1) return [];
			const standard = matches[0];
			if (normalizedDataType(field.dataType) !== normalizedDataType(standard.dataType)) return [];
			const standardId = text(standard.id);
			const standardVersion = positiveVersion(standard.version);
			if (!standardId || standardVersion == null) return [];
			return [
				{
					id: `${model.id}:${field.name}:${standardId}@${standardVersion}`,
					modelId: model.id,
					modelName: model.name,
					modelStatus,
					modelRevision: model.revision,
					modelChecksum: model.checksum,
					fieldName: field.name,
					fieldLabel: text(field.displayName, field.name),
					fieldDataType: field.dataType,
					standardId,
					standardCode: text(standard.fieldNameEn),
					standardName: text(standard.fieldNameCn, text(standard.fieldNameEn)),
					standardVersion,
					createsDraft: modelStatus === "PUBLISHED",
				},
			];
		});
	});
}

export async function applySuggestedStandardMappings(
	suggestions: StandardMappingSuggestion[],
): Promise<StandardMappingApplyResult[]> {
	const groups = new Map<string, StandardMappingSuggestion[]>();
	for (const suggestion of suggestions) {
		groups.set(suggestion.modelId, [...(groups.get(suggestion.modelId) || []), suggestion]);
	}
	const results: StandardMappingApplyResult[] = [];
	for (const group of groups.values()) {
		const first = group[0];
		try {
			const updated = await applyModelSpecStandardElementBindings(
				{ id: first.modelId, revision: first.modelRevision, checksum: first.modelChecksum },
				group.map((item) => ({
					fieldName: item.fieldName,
					standardElementId: item.standardId,
					standardElementVersion: item.standardVersion,
				})),
			);
			results.push({ modelId: first.modelId, modelName: first.modelName, success: true, revision: updated.revision });
		} catch (cause) {
			results.push({
				modelId: first.modelId,
				modelName: first.modelName,
				success: false,
				message: cause instanceof Error ? cause.message : "标准映射保存失败",
			});
		}
	}
	return results;
}

const modelUpdateCommand = (
	model: CanonicalModelSpecView,
	standardBindings: ModelSpecStandardBinding[],
): UpdateModelSpecCommand => ({
	planId: model.planId,
	domainId: model.domainId,
	modelType: model.modelType,
	layer: model.layer,
	warehouseLayerCode: model.warehouseLayerCode,
	name: model.name,
	description: model.description,
	implementationMode: model.implementationMode,
	materialization: model.materialization,
	businessActivityRef: model.businessActivityRef,
	businessProcessId: model.businessProcessId,
	consumptionScenario: model.consumptionScenario,
	grain: model.grain,
	factShape: model.factShape,
	timeSemantics: model.timeSemantics,
	generationStrategy: model.generationStrategy,
	dimensionProfile: model.dimensionProfile,
	dataMartId: model.dataMartId,
	subjectDomainId: model.subjectDomainId,
	variantCode: model.variantCode,
	implementationPolicy: model.implementationPolicy,
	fields: model.fields,
	sourceRefs: model.sourceRefs,
	dependsOn: model.dependsOn,
	dimensionRefs: model.dimensionRefs,
	metricRefs: model.metricRefs,
	standardBindings,
});

export async function saveStandardMapping(values: StandardMappingValues) {
	const modelId = values.modelId.trim();
	const fieldName = values.fieldName.trim();
	const standardId = values.standardId.trim();
	if (!modelId || !fieldName || !standardId) throw new Error("请选择模型、模型字段和数据元标准");

	const owners = await loadMappingOwners();
	const model = owners.models.find((candidate) => candidate.id === modelId && candidate.status === "DRAFT");
	if (!model) throw new Error("所选模型不存在或不是可编辑草稿");
	if (!model.fields.some((field) => field.name === fieldName)) throw new Error("所选字段不属于当前模型");
	const standard = owners.standards.find((candidate) => text(candidate.id) === standardId);
	if (!standard) throw new Error("所选数据元标准不存在");
	const standardVersion = positiveVersion(standard.version);
	if (standardVersion == null) throw new Error("所选数据元标准缺少有效版本，不能建立映射");

	const existing = model.standardBindings.find((binding) => binding.fieldName === fieldName);
	const nextBinding: ModelSpecStandardBinding = {
		...existing,
		fieldName,
		standardElementId: standardId,
		standardElementVersion: standardVersion,
	};
	const standardBindings = [
		...model.standardBindings.filter((binding) => binding.fieldName !== fieldName),
		nextBinding,
	];
	return updateModelSpec(model, modelUpdateCommand(model, standardBindings));
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
	status: nextStatus ?? Number(values.status ?? current?.status ?? 0),
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

const wordRootPayload = (values: StandardsEditorValues) => ({
	code: values.code.trim(),
	nameCn: values.name.trim(),
	nameEn: values.definition.trim(),
	abbreviation: values.scope.trim(),
	domain: values.domain.trim() || undefined,
	version: values.version.trim() || "v1",
});

export async function saveStandardsRow(view: StandardsView, values: StandardsEditorValues, row?: StandardsRow | null) {
	if (!values.code.trim() || !values.name.trim()) throw new Error("编码和名称不能为空");
	if (view === "roots" && (!values.definition.trim() || !values.scope.trim())) {
		throw new Error("英文全称和英文缩写不能为空");
	}
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
	if (view === "roots") {
		const payload = wordRootPayload(values);
		return row?.id ? updateWordRoot(row.id, payload) : createWordRoot(payload);
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
