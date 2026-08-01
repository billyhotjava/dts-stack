import type { IngestionTaskDTO, ManagedApiConnectionTestRequestDTO, ManagedFileUploadResult } from "@/api/ingestion";
import type { DataSourceSelectionItem } from "@/api/services/dataSourcesService";
import {
	applyReaderTypeToConfig,
	buildApiReaderConfig,
	buildFileBaseName,
	buildReaderConfig,
	buildSyncConfigFromValues,
	buildSyncScheduleSpec,
	buildSyncScheduleText,
	mapTaskToForm,
	normalizeTableName,
	normalizeText,
	resolveReaderTypeFromDataSource,
} from "./shared/ingestionFormHelpers";
import { parseTableEntries } from "./shared/transformTableSelection.helpers";
import {
	buildManagedFileAdmissionFields,
	buildManagedFileSourceConfig,
	extractManagedFileFromTask,
} from "./accessManagedFile";
import type {
	AccessKind,
	AccessPlanApiResourceDTO,
	AccessPlanApiSourceConfigDTO,
	AccessPlanCreateRequest,
	AccessPlanFormValues,
	AccessPlanPayloadContext,
} from "./accessPlan.types";

const FILE_READER_TYPES = new Set(["txtfilereader", "excelreader", "csv", "excel", "file"]);

export const normalizeAccessKind = (value?: string | null): AccessKind => {
	const normalized = normalizeText(value).toLowerCase();
	return normalized === "api" || normalized === "file" ? normalized : "database";
};

export const inferAccessKind = (task: Pick<IngestionTaskDTO, "sourceType" | "sourceConfig">): AccessKind => {
	const sourceType = normalizeText(task.sourceType).toLowerCase();
	const sourceConfig = task.sourceConfig || {};
	const marker = normalizeText(sourceConfig.connectorType || sourceConfig.sourceCategory).toLowerCase();
	if (sourceType === "httpreader" || sourceType === "api" || marker === "api") return "api";
	if (FILE_READER_TYPES.has(sourceType)) return "file";
	return "database";
};

const resolveWriterType = (target?: DataSourceSelectionItem | null, fallback?: string) => {
	const marker =
		`${target?.type || ""} ${target?.connectorKey || ""} ${target?.connectorName || ""} ${target?.defaultEngine || ""}`.toLowerCase();
	if (marker.includes("postgres")) return "postgresqlwriter";
	if (marker.includes("mysql") || marker.includes("mariadb")) return "mysqlwriter";
	if (marker.includes("oracle")) return "oraclewriter";
	if (marker.includes("sqlserver") || marker.includes("mssql")) return "sqlserverwriter";
	if (marker.includes("clickhouse")) return "clickhousewriter";
	if (marker.includes("hive")) return "hivewriter";
	if (marker.includes("dameng") || marker.includes("jdbc:dm:")) return "rdbmswriter";
	return normalizeText(fallback) || "postgresqlwriter";
};

const requireText = (value: unknown, message: string) => {
	const normalized = normalizeText(value == null ? undefined : String(value));
	if (!normalized) throw new Error(message);
	return normalized;
};

const isUnknownRecord = (value: unknown): value is Record<string, unknown> =>
	Boolean(value) && typeof value === "object" && !Array.isArray(value);

const SENSITIVE_QUERY_KEY = /pass(?:word|wd)?|secret|token|authorization|credential|api[-_]?key|access[-_]?key/i;

export const requireSafeApiResourcePath = (value: unknown) => {
	const path = requireText(value, "缺少 API 资源路径");
	if (!path.startsWith("/") || path.startsWith("//") || path.includes("#") || path.includes("://")) {
		throw new Error("API 资源路径必须是站内相对路径");
	}
	let parsed: URL;
	try {
		parsed = new URL(path, "https://dts.invalid");
	} catch {
		throw new Error("API 资源路径格式无效");
	}
	for (const key of parsed.searchParams.keys()) {
		if (SENSITIVE_QUERY_KEY.test(key)) {
			throw new Error("API 资源路径不能包含凭据参数，请在托管连接中配置认证信息");
		}
	}
	return `${parsed.pathname}${parsed.search}`;
};

const requireApiResource = (value: unknown): AccessPlanApiResourceDTO => {
	if (!isUnknownRecord(value)) throw new Error("API 资源配置无效");
	return {
		...value,
		resourceId: requireText(value.resourceId, "缺少 API 资源标识"),
		path: requireSafeApiResourcePath(value.path),
		method: requireText(value.method, "缺少 API 请求方法"),
		targetTable: requireText(value.targetTable, "缺少 API 目标表"),
	};
};

const buildDestination = (context: AccessPlanPayloadContext) => {
	const targetDataSourceId = requireText(context.values.targetDataSourceId, "请选择目标数据源");
	if (!context.defaultDestination?.available) throw new Error("平台默认数据湖不可用");
	if (!context.defaultDestination.writerTypeReady || !context.defaultDestination.writerConfigReady) {
		throw new Error("平台默认数据湖配置不完整");
	}
	return {
		usePlatformDefault: true as const,
		type: resolveWriterType(context.selectedTarget, context.defaultDestination.writerType),
		config: { targetDataSourceId },
	};
};

export const buildAccessPlanApiSourceConfig = (values: AccessPlanFormValues): AccessPlanApiSourceConfigDTO => {
	const pagination =
		values.apiPageParam || values.apiSizeParam || values.apiPageSize
			? {
					type: "page",
					pageParam: normalizeText(values.apiPageParam) || "page",
					sizeParam: normalizeText(values.apiSizeParam) || "pageSize",
					pageSize: Number(values.apiPageSize) > 0 ? Number(values.apiPageSize) : 100,
				}
			: undefined;
	const cursor = values.apiCursorField
		? {
				type: "field",
				field: normalizeText(values.apiCursorField),
				injectInto: "query",
				parameterName: normalizeText(values.apiCursorParam) || normalizeText(values.apiCursorField),
			}
		: undefined;
	const rawConfig: unknown = buildApiReaderConfig({
		...values,
		apiPaginationJson: pagination ? JSON.stringify(pagination) : undefined,
		apiCursorJson: cursor ? JSON.stringify(cursor) : undefined,
	});
	if (!isUnknownRecord(rawConfig)) throw new Error("API 来源配置无效");
	const resource = requireApiResource(rawConfig.resource);
	return {
		...rawConfig,
		readerType: requireText(rawConfig.readerType, "缺少 API Reader 类型"),
		connectorType: "api",
		sourceCategory: "api",
		resource,
		resources: [resource],
	};
};

export const buildManagedApiConnectionTestRequest = (
	values: AccessPlanFormValues,
): ManagedApiConnectionTestRequestDTO => ({
	dataSourceId: requireText(values.sourceDataSourceId, "请选择 API 连接"),
	resource: {
		path: requireSafeApiResourcePath(values.apiResourcePath),
		method: "GET",
		resourceId: normalizeText(values.apiResourceId) || undefined,
		displayName: normalizeText(values.apiResourceDisplayName) || undefined,
		recordPath: normalizeText(values.apiRecordPath) || undefined,
	},
});

const buildSync = (values: AccessPlanFormValues, file: boolean) => {
	const config = buildSyncConfigFromValues(values, file);
	return {
		mode: file ? "full_refresh" : values.syncMode || "full_refresh",
		schedule: buildSyncScheduleSpec(values),
		prefix: normalizeText(values.syncPrefix) || undefined,
		incrementalColumn: config?.incrementalColumn,
		incrementalType: config?.incrementalType,
		initialWatermark: config?.initialWatermark,
	};
};

const buildDatabaseRequest = (context: AccessPlanPayloadContext): AccessPlanCreateRequest => {
	const { values } = context;
	const sourceDataSourceId = requireText(values.sourceDataSourceId, "请选择数据库连接");
	const selectedTables = values.selectedTables.map(normalizeText).filter(Boolean);
	if (values.tableSelectionMode === "manual" && !selectedTables.length) throw new Error("请选择需要接入的表");
	const readerType =
		normalizeText(values.readerType) || resolveReaderTypeFromDataSource(context.selectedSource) || "rdbmsreader";
	const readerConfig = buildReaderConfig({
		readerTables: values.tableSelectionMode === "manual" ? selectedTables.join("\n") : "",
		readerColumns: "*",
		sourceSystem: values.sourceSystem,
	});
	applyReaderTypeToConfig(readerConfig, readerType);
	return {
		name: requireText(values.name, "请输入任务名称"),
		description: normalizeText(values.description) || undefined,
		owner: context.owner,
		source: { dataSourceId: sourceDataSourceId, type: readerType, config: readerConfig },
		destination: buildDestination(context),
		sync: buildSync(values, false),
		streams: {
			selection: values.tableSelectionMode,
			include: values.tableSelectionMode === "manual" ? selectedTables : undefined,
			schema: normalizeText(values.readerSchema) || undefined,
			tablePattern: normalizeText(values.readerTablePattern) || undefined,
		},
		airflow: { enabled: values.airflowEnabled },
		runNow: false,
		draft: true,
	};
};

const buildApiRequest = (context: AccessPlanPayloadContext): AccessPlanCreateRequest => {
	const { values } = context;
	const sourceDataSourceId = requireText(values.sourceDataSourceId, "请选择 API 连接");
	const config = buildAccessPlanApiSourceConfig(values);
	const resource = config.resource;
	return {
		name: requireText(values.name, "请输入任务名称"),
		description: normalizeText(values.description) || undefined,
		owner: context.owner,
		source: { dataSourceId: sourceDataSourceId, type: "httpreader", config },
		destination: buildDestination(context),
		sync: buildSync(values, false),
		streams: { selection: "manual", include: [resource.resourceId] },
		airflow: { enabled: values.airflowEnabled },
		runNow: false,
		draft: true,
	};
};

const resolveFileTable = (values: AccessPlanFormValues, file: ManagedFileUploadResult) => {
	const requested = normalizeTableName(values.fileTargetTable);
	if (normalizeText(values.fileTargetTable) && !requested) throw new Error("目标表名格式不合法");
	const generated = normalizeTableName(`${normalizeText(values.syncPrefix)}${buildFileBaseName(file.originalName)}`);
	return requested || generated || "ods_file_import";
};

const buildFileRequest = (context: AccessPlanPayloadContext): AccessPlanCreateRequest => {
	const { values, fileUploadResult } = context;
	if (!fileUploadResult) throw new Error("请先上传并解析文件");
	const table = resolveFileTable(values, fileUploadResult);
	const admission = buildManagedFileAdmissionFields(fileUploadResult);
	return {
		name: requireText(values.name, "请输入任务名称"),
		description: normalizeText(values.description) || undefined,
		owner: context.owner,
		source: {
			type: "txtfilereader",
			config: buildManagedFileSourceConfig(fileUploadResult, values.fileAutoId),
		},
		destination: buildDestination(context),
		sync: buildSync(values, true),
		streams: { selection: "manual", include: [table] },
		airflow: { enabled: values.airflowEnabled },
		runNow: false,
		draft: true,
		classificationSeal: admission.classificationSeal,
		fieldClassifications: admission.fieldClassifications,
	};
};

export const buildAccessPlanCreateRequest = (context: AccessPlanPayloadContext): AccessPlanCreateRequest => {
	if (context.kind === "api") return buildApiRequest(context);
	if (context.kind === "file") return buildFileRequest(context);
	return buildDatabaseRequest(context);
};

export const buildAccessPlanUpdateDTO = (
	existing: IngestionTaskDTO,
	context: AccessPlanPayloadContext,
): IngestionTaskDTO => {
	if (!existing.id) throw new Error("编辑任务缺少 ID");
	const request = buildAccessPlanCreateRequest(context);
	const resource = context.kind === "api" ? requireApiResource(request.source.config.resource) : undefined;
	const sourceTables = request.streams.include || [];
	const targetTables =
		context.kind === "api"
			? [normalizeText(resource?.targetTable) || `ods_api_${sourceTables[0] || "resource"}`]
			: context.kind === "file"
				? sourceTables
				: sourceTables.map((table) => `${normalizeText(context.values.syncPrefix)}${table.split(".").pop() || table}`);
	const syncConfig = buildSyncConfigFromValues(context.values, context.kind === "file");
	const sourceChanged =
		context.kind !== "file" &&
		normalizeText(existing.sourceDataSourceId) !== normalizeText(request.source.dataSourceId);
	return {
		...existing,
		id: existing.id,
		name: request.name,
		description: request.description,
		// Editing always creates a new draft plan.  A published task must be
		// admitted again before the revised execution configuration can run.
		status: "draft",
		sourceType: request.source.type,
		sourceDataSourceId: context.kind === "file" ? undefined : request.source.dataSourceId,
		sourceConfig: request.source.config,
		destinationType: request.destination.type,
		destinationConfig: request.destination.config,
		syncMode: String(request.sync.mode || "full_refresh"),
		syncSchedule: buildSyncScheduleText(context.values),
		syncConfig,
		syncPrefix: normalizeText(context.values.syncPrefix) || undefined,
		tableMapping: sourceTables.map((source, index) => ({ source, target: targetTables[index] || source })),
		// A seal is bound to its source identity.  Omitting the old evidence on a
		// source switch lets the platform issue evidence for the newly selected
		// managed connection; retaining it would admit the wrong source.
		classificationSeal:
			context.kind === "file" ? request.classificationSeal : sourceChanged ? undefined : existing.classificationSeal,
		fieldClassifications:
			context.kind === "file"
				? request.fieldClassifications
				: sourceChanged
					? undefined
					: existing.fieldClassifications,
		airflowEnabled: context.values.airflowEnabled,
		addaxJobPath: context.kind === "api" ? undefined : existing.addaxJobPath,
		addaxConfig: context.kind === "api" ? undefined : existing.addaxConfig,
		dbtModelSelector: context.kind === "api" ? undefined : existing.dbtModelSelector,
		dbtDagSelector: context.kind === "api" ? undefined : existing.dbtDagSelector,
	};
};

const parseObject = (value?: string): Record<string, unknown> => {
	try {
		const parsed = value ? JSON.parse(value) : null;
		return isUnknownRecord(parsed) ? parsed : {};
	} catch {
		return {};
	}
};

const optionalText = (value: unknown) => normalizeText(typeof value === "string" ? value : undefined) || undefined;

export const toAccessPlanFormValues = (task: IngestionTaskDTO): Partial<AccessPlanFormValues> => {
	const legacy = mapTaskToForm(task);
	const pagination = parseObject(legacy.apiPaginationJson);
	const cursor = parseObject(legacy.apiCursorJson);
	return {
		...(legacy as Omit<Partial<AccessPlanFormValues>, "tableSelectionMode" | "selectedTables">),
		tableSelectionMode: legacy.tableSelectionMode === "manual" ? "manual" : "all",
		selectedTables: parseTableEntries(legacy.selectedTables || legacy.readerTables),
		apiPageParam: optionalText(pagination.pageParam),
		apiSizeParam: optionalText(pagination.sizeParam),
		apiPageSize: Number(pagination.pageSize) > 0 ? Number(pagination.pageSize) : undefined,
		apiCursorField: optionalText(cursor.field),
		apiCursorParam: optionalText(cursor.parameterName),
		fileTargetTable: normalizeText(legacy.fileTableName) || undefined,
		fileClassification: extractManagedFileFromTask(task)?.classification || "INTERNAL",
	};
};
