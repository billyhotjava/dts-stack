import type {
	FileUploadResult,
	IngestionConnectorCapabilityDTO,
	IngestionExecutionDTO,
	IngestionTaskDTO,
	TableInfo,
} from "@/api/ingestion";
import type { InfraDataSource } from "@/api/services/dataSourcesService";
import { normalizeText } from "@/utils/textUtils";

export { normalizeText };

export const DRAFT_STORAGE_KEY = "ingestion_task_draft";
export const CONNECTION_KEYS = [
	"jdbcUrl",
	"host",
	"port",
	"username",
	"password",
	"database",
	"schema",
	"connectionString",
	"dsn",
];

export const JDBC_READER_BY_TYPE: Record<string, string> = {
	dm: "rdbmsreader",
	dameng: "rdbmsreader",
	dm8: "rdbmsreader",
	dameng8: "rdbmsreader",
	postgres: "postgresqlreader",
	postgresql: "postgresqlreader",
	pg: "postgresqlreader",
	mysql: "mysqlreader",
	mariadb: "mysqlreader",
	oracle: "oraclereader",
	sqlserver: "sqlserverreader",
	mssql: "sqlserverreader",
	clickhouse: "clickhousereader",
	hive: "hivereader",
	db2: "db2reader",
	sqlite: "sqlitereader",
};

export const FILE_READER_BY_TYPE: Record<string, string> = {
	excel: "txtfilereader",
	csv: "txtfilereader",
	json: "jsonreader",
	api: "httpreader",
	http: "httpreader",
	file: "txtfilereader",
};

export const API_SOURCE_TYPES = new Set(["api", "http", "https", "http_api", "api_http", "rest", "rest_api", "httpreader"]);

export const GENERIC_JDBC_READER = "rdbmsreader";

export const JDBC_READER_BY_URL: Record<string, string> = {
	"jdbc:dm:": "rdbmsreader",
	"jdbc:postgresql:": "postgresqlreader",
	"jdbc:mysql:": "mysqlreader",
	"jdbc:mariadb:": "mysqlreader",
	"jdbc:oracle:": "oraclereader",
	"jdbc:sqlserver:": "sqlserverreader",
	"jdbc:clickhouse:": "clickhousereader",
	"jdbc:hive2:": "hivereader",
	"jdbc:db2:": "db2reader",
	"jdbc:sqlite:": "sqlitereader",
};

export const TABLE_PLACEHOLDER = "${table}";

export type AsyncRunProgressStatus = "active" | "success" | "exception";

export type AsyncRunProgressView = {
	progress: number;
	status: AsyncRunProgressStatus;
	stage: string;
	detail: string;
	terminal: boolean;
};

export const resolveCreatedTaskId = (payload: any): number | undefined => {
	const candidate = payload?.task?.id ?? payload?.taskId ?? payload?.id ?? payload?.task?.taskId;
	const value = Number(candidate);
	return Number.isFinite(value) && value > 0 ? value : undefined;
};

export const mapExecutionToProgressView = (
	execution: IngestionExecutionDTO | null,
	elapsedMs: number
): AsyncRunProgressView => {
	if (!execution) {
		const dynamicProgress = Math.min(45, 15 + Math.floor(elapsedMs / 5000) * 5);
		return {
			progress: dynamicProgress,
			status: "active",
			stage: "等待执行记录",
			detail: "任务已提交，系统正在准备 DAG 和作业参数。",
			terminal: false,
		};
	}
	const rawStatus = normalizeText(execution.status).toLowerCase();
	if (rawStatus === "success") {
		return {
			progress: 100,
			status: "success",
			stage: "执行成功",
			detail: "入湖任务已执行完成。",
			terminal: true,
		};
	}
	if (rawStatus === "failed" || rawStatus === "error") {
		return {
			progress: 100,
			status: "exception",
			stage: "执行失败",
			detail: normalizeText(execution.errorMessage) || "任务执行失败，请查看日志定位原因。",
			terminal: true,
		};
	}
	if (rawStatus === "preparing") {
		return {
			progress: 55,
			status: "active",
			stage: "准备执行",
			detail: "正在生成/校验 Addax 作业并等待 DAG 就绪。",
			terminal: false,
		};
	}
	return {
		progress: 80,
		status: "active",
		stage: "执行中",
		detail: "已触发执行，正在同步运行状态。",
		terminal: false,
	};
};

export const normalizeIdentifier = (value?: string) => {
	const text = normalizeText(value).toLowerCase();
	if (!text) return "";
	let safe = text.replace(/[^a-z0-9_]+/g, "_").replace(/^_+|_+$/g, "").replace(/_+/g, "_");
	if (!safe) return "";
	if (/^\d/.test(safe)) {
		safe = `col_${safe}`;
	}
	return safe;
};

export const normalizeTableName = (value?: string) => {
	const text = normalizeText(value);
	if (!text) return "";
	const parts = text.split(".").filter(Boolean);
	if (!parts.length) return "";
	const normalized = parts.map((part) => normalizeIdentifier(part)).filter(Boolean);
	if (normalized.length !== parts.length) return "";
	return normalized.join(".");
};

export const buildFileBaseName = (filename?: string) => {
	const base = normalizeText(filename || "file").replace(/\.[^.]+$/, "");
	const safe = normalizeIdentifier(base);
	return safe || "file";
};

export const normalizeReaderType = (value?: string) => {
	const text = normalizeText(value);
	if (!text) return "";
	const lower = text.toLowerCase();
	if (lower === "dmreader" || lower === "dm" || lower === "dameng" || lower === "dm8" || lower === "dameng8") {
		return GENERIC_JDBC_READER;
	}
	if (lower === "rdbms" || lower === "jdbc") {
		return GENERIC_JDBC_READER;
	}
	return text;
};

export const normalizeType = (value?: string) => normalizeText(value).toLowerCase();

export const normalizeTag = (value?: string) => normalizeText(value).toLowerCase().replace(/[^a-z0-9-]+/g, "-").replace(/^-+|-+$/g, "");

export const isApiSourceType = (value?: string) => {
	const type = normalizeType(value);
	return Boolean(type && API_SOURCE_TYPES.has(type));
};

export const isApiReaderType = (value?: string) => isApiSourceType(value) || normalizeType(value) === "httpreader";

export const isApiDataSource = (source?: InfraDataSource | null) => {
	if (!source) return false;
	const type = normalizeType(source.type);
	const props = source.props || {};
	return (
		isApiSourceType(type) ||
		normalizeType(String(props.connectorType || "")) === "api" ||
		normalizeType(String(props.sourceCategory || "")) === "api"
	);
};

export type SyncModeValue = "full_refresh" | "incremental" | "cdc" | "backfill";
export type SyncModeOption = { value: SyncModeValue; label: string; disabled?: boolean };

export const SYNC_MODE_LABELS: Record<SyncModeValue, string> = {
	full_refresh: "全量同步",
	incremental: "增量同步",
	cdc: "实时同步(CDC)",
	backfill: "历史回灌",
};

export const CAPABILITY_TO_SYNC_MODE: Record<string, SyncModeValue> = {
	FULL: "full_refresh",
	INCREMENTAL: "incremental",
	CDC: "cdc",
	BACKFILL: "backfill",
};

export const normalizeSyncModeValue = (value?: string): SyncModeValue | null => {
	const text = normalizeText(value).toLowerCase();
	if (!text) return null;
	if (text === "full" || text === "full_refresh" || text === "fullrefresh") return "full_refresh";
	if (text === "incremental" || text === "incr" || text === "delta") return "incremental";
	if (text === "cdc" || text === "realtime" || text === "real_time") return "cdc";
	if (text === "backfill" || text === "history_backfill" || text === "historical_backfill") return "backfill";
	return null;
};

export const asBoolean = (value: any): boolean | null => {
	if (typeof value === "boolean") return value;
	if (typeof value === "string") {
		const text = value.trim().toLowerCase();
		if (text === "true") return true;
		if (text === "false") return false;
	}
	return null;
};

export const deriveSyncModeOptions = (
	connectorType: string,
	capability: IngestionConnectorCapabilityDTO | null,
	isFileFlow: boolean
): SyncModeOption[] => {
	const allowed = new Set<SyncModeValue>();
	const syncModes = capability?.constraints && typeof capability.constraints === "object"
		? (capability.constraints as any).syncModes
		: null;
	if (syncModes && typeof syncModes === "object" && !Array.isArray(syncModes)) {
		Object.entries(syncModes).forEach(([rawMode, cfg]) => {
			const mode = normalizeSyncModeValue(rawMode);
			if (!mode) return;
			const enabled = asBoolean((cfg as any)?.enabled);
			if (enabled === false) return;
			allowed.add(mode);
		});
	}
	if (!allowed.size) {
		(capability?.capabilities || []).forEach((cap) => {
			const mapped = CAPABILITY_TO_SYNC_MODE[normalizeText(cap).toUpperCase()];
			if (mapped) allowed.add(mapped);
		});
	}
	if (!allowed.size) {
		if (isFileFlow || normalizeType(connectorType) === "file") {
			allowed.add("full_refresh");
		} else if (normalizeType(connectorType) === "airbyte") {
			allowed.add("full_refresh");
			allowed.add("incremental");
			allowed.add("cdc");
			allowed.add("backfill");
		} else {
			allowed.add("full_refresh");
			allowed.add("incremental");
		}
	}
	return (["full_refresh", "incremental", "cdc", "backfill"] as SyncModeValue[])
		.filter((mode) => allowed.has(mode))
		.map((mode) => ({ value: mode, label: SYNC_MODE_LABELS[mode] }));
};

export type SyncScheduleFormState = {
	scheduleType: "manual" | "interval" | "cron";
	scheduleCron?: string;
	scheduleIntervalMinutes?: number;
};

export const parseSyncSchedule = (value?: string): SyncScheduleFormState => {
	const raw = normalizeText(value);
	const lower = raw.toLowerCase();
	if (!raw) {
		return { scheduleType: "manual", scheduleIntervalMinutes: 60 };
	}
	if (lower.startsWith("cron:")) {
		return {
			scheduleType: "cron",
			scheduleCron: normalizeText(raw.slice(5)),
			scheduleIntervalMinutes: 60,
		};
	}
	if (lower.startsWith("interval:")) {
		const parsed = Number.parseInt(lower.slice(9), 10);
		return {
			scheduleType: "interval",
			scheduleIntervalMinutes: Number.isFinite(parsed) && parsed > 0 ? parsed : 60,
		};
	}
	if (raw.split(/\s+/).length >= 5) {
		return {
			scheduleType: "cron",
			scheduleCron: raw,
			scheduleIntervalMinutes: 60,
		};
	}
	return { scheduleType: "manual", scheduleIntervalMinutes: 60 };
};

export const buildSyncScheduleSpec = (values: Record<string, any>) => {
	const scheduleType = normalizeText(values?.scheduleType).toLowerCase();
	if (scheduleType === "cron") {
		const cron = normalizeText(values?.scheduleCron);
		return cron ? { type: "cron", cron } : undefined;
	}
	if (scheduleType === "interval") {
		const interval = Number(values?.scheduleIntervalMinutes);
		if (Number.isFinite(interval) && interval > 0) {
			return { type: "interval", intervalMinutes: Math.floor(interval) };
		}
	}
	return undefined;
};

export const buildSyncScheduleText = (values: Record<string, any>) => {
	const spec = buildSyncScheduleSpec(values);
	if (!spec) return undefined;
	if (spec.type === "cron") return `cron:${spec.cron}`;
	if (spec.type === "interval") return `interval:${spec.intervalMinutes}`;
	return undefined;
};

export const toOptionalNumber = (value: any) => {
	if (value === undefined || value === null || value === "") return undefined;
	const parsed = Number(value);
	return Number.isFinite(parsed) ? parsed : undefined;
};

export const buildGovernanceSyncFields = (values: Record<string, any>): Record<string, any> => {
	const result: Record<string, any> = {};
	const taskConcurrency = toOptionalNumber(values.taskConcurrency);
	if (taskConcurrency !== undefined && taskConcurrency > 0) {
		result.taskConcurrency = Math.floor(taskConcurrency);
	}
	const sourceConcurrency = toOptionalNumber(values.sourceConcurrency);
	if (sourceConcurrency !== undefined && sourceConcurrency >= 0) {
		result.sourceConcurrency = Math.floor(sourceConcurrency);
	}
	const projectConcurrency = toOptionalNumber(values.projectConcurrency);
	if (projectConcurrency !== undefined && projectConcurrency >= 0) {
		result.projectConcurrency = Math.floor(projectConcurrency);
	}
	const projectKey = normalizeText(values.projectKey);
	if (projectKey) {
		result.projectKey = projectKey;
	}
	const priority = normalizeText(values.priority);
	if (priority) {
		result.priority = priority.toUpperCase();
	}
	const rejectPolicy = normalizeText(values.rejectPolicy);
	if (rejectPolicy) {
		result.rejectPolicy = rejectPolicy.toUpperCase();
	}
	const windowStart = normalizeText(values.windowStart);
	const windowEnd = normalizeText(values.windowEnd);
	if ((windowStart && !windowEnd) || (!windowStart && windowEnd)) {
		throw new Error("执行窗口需同时填写开始和结束时间（HH:mm）");
	}
	if (windowStart && windowEnd) {
		result.windowStart = windowStart;
		result.windowEnd = windowEnd;
		const windowTimezone = normalizeText(values.windowTimezone) || "Asia/Shanghai";
		result.windowTimezone = windowTimezone;
	}
	return result;
};

export const validateCronExpression = (_: unknown, value: string) => {
	const cron = normalizeText(value);
	if (!cron) {
		return Promise.reject(new Error("请输入 Cron 表达式"));
	}
	const parts = cron.split(/\s+/);
	if (parts.length < 5 || parts.length > 7) {
		return Promise.reject(new Error("Cron 表达式格式不正确（需 5~7 段）"));
	}
	return Promise.resolve();
};

export const resolveFileTypeFromName = (name?: string) => {
	const normalized = normalizeText(name).toLowerCase();
	if (!normalized) return "csv";
	if (normalized.endsWith(".xlsx") || normalized.endsWith(".xls")) return "excel";
	if (normalized.endsWith(".csv")) return "csv";
	return "csv";
};

export const resolveSourceSystemFromDataSource = (source?: InfraDataSource | null) => {
	if (!source) return "";
	const props = source.props || {};
	const direct = normalizeText(
		String(
			props.sourceSystem ||
			props.sourceName ||
			props.system ||
			props.app ||
			props.appCode ||
			props.name ||
			"",
		),
	);
	return direct || normalizeText(source.name);
};

export const buildAutoSyncPrefix = (source?: InfraDataSource | null): string => {
	const systemCode = resolveSourceSystemFromDataSource(source);
	if (!systemCode) return "";
	const normalized = systemCode
		.toLowerCase()
		.replace(/[^a-z0-9]/g, "_")
		.replace(/_+/g, "_")
		.replace(/^_|_$/g, "");
	return normalized ? `ods_${normalized}_` : "";
};

export const resolveReaderTypeFromDataSource = (source?: InfraDataSource | null) => {
	if (!source) return "";
	const props = source.props || {};
	const direct = normalizeReaderType(String(props.readerType || props.reader || props.type || ""));
	if (direct) return direct;
	const type = normalizeType(source.type);
	if (type === "jdbc") {
		return GENERIC_JDBC_READER;
	}
	if (type && type.includes("jdbc")) {
		return GENERIC_JDBC_READER;
	}
	if (type && JDBC_READER_BY_TYPE[type]) {
		return normalizeReaderType(JDBC_READER_BY_TYPE[type]);
	}
	if (type === "jdbc" || type === "rdbms") {
		return GENERIC_JDBC_READER;
	}
	if (type && FILE_READER_BY_TYPE[type]) {
		return FILE_READER_BY_TYPE[type];
	}
	const jdbcUrl = normalizeText(source.jdbcUrl).toLowerCase();
	if (jdbcUrl) {
		for (const [prefix, reader] of Object.entries(JDBC_READER_BY_URL)) {
			if (jdbcUrl.startsWith(prefix)) {
				return normalizeReaderType(reader);
			}
		}
		return GENERIC_JDBC_READER;
	}
	return "";
};

export const resolveReaderTypeFromSourceId = (
	sourceId?: string,
	candidates?: InfraDataSource[],
	fallback?: InfraDataSource | null
) => {
	if (fallback) {
		const inferred = resolveReaderTypeFromDataSource(fallback);
		if (inferred) return inferred;
	}
	if (!sourceId || !Array.isArray(candidates)) return "";
	const hit = candidates.find((item) => normalizeText(item.id) === normalizeText(sourceId));
	return resolveReaderTypeFromDataSource(hit || null);
};

export const extractMappingTables = (mapping?: Array<{ source?: string; target?: string }>) =>
	(Array.isArray(mapping) ? mapping : [])
		.map((item) => normalizeText(item?.source))
		.filter(Boolean);

export const applyReaderTypeToConfig = (config: Record<string, any> | undefined, readerType?: string) => {
	const resolved = normalizeText(readerType);
	if (!resolved || resolved.toLowerCase() === "auto" || !config) return config;
	if (!normalizeText(config.readerType || config.reader || config.type || config.name)) {
		config.readerType = resolved;
	}
	return config;
};

export const resolveReaderTypeFromValues = (values: Record<string, any>) => {
	const direct = normalizeReaderType(values?.readerType || values?.reader || values?.sourceType || "");
	if (direct) return direct;
	const safeParse = (raw?: string) => {
		const text = normalizeText(raw);
		if (!text) return undefined;
		try {
			return JSON.parse(text);
		} catch {
			return undefined;
		}
	};
	const readerConfig = safeParse(values?.readerConfig);
	const fromConfig = normalizeReaderType(readerConfig?.readerType || readerConfig?.type || readerConfig?.name || "");
	if (fromConfig) return fromConfig;
	const jobConfig = safeParse(values?.jobConfig);
	const fromJob = normalizeReaderType(jobConfig?.job?.content?.[0]?.reader?.name || "");
	if (fromJob) return fromJob;
	return "";
};

export const isJdbcSource = (source?: InfraDataSource | null) => {
	if (!source) return false;
	if (normalizeText(source.jdbcUrl)) return true;
	const type = normalizeType(source.type);
	return Boolean(type && (type === "jdbc" || JDBC_READER_BY_TYPE[type]));
};

export const resolveTaskName = (values: Record<string, any>, form?: any) => {
	const fallback = form ? normalizeText(form.getFieldValue("name")) : "";
	return (
		normalizeText(values?.name) ||
		normalizeText(values?.taskName) ||
		normalizeText(values?.title) ||
		fallback
	);
};

export const splitLines = (value?: string) =>
	normalizeText(value)
		.split(/\r?\n/)
		.map((item) => item.trim())
		.filter(Boolean);

export const splitColumns = (value?: string) => {
	const text = normalizeText(value);
	if (!text) return ["*"];
	if (text === "*") return ["*"];
	return text
		.split(",")
		.map((item) => item.trim())
		.filter(Boolean);
};

export const hasTableEntries = (tables: string[]) => tables.some((table) => Boolean(normalizeText(table)));

export const mergeConfig = (base: Record<string, any>, extra?: Record<string, any>) => {
	if (!extra || Object.keys(extra).length === 0) return base;
	return { ...base, ...extra };
};

export const parseJson = (value?: string, label?: string) => {
	const text = normalizeText(value);
	if (!text) return undefined;
	try {
		return JSON.parse(text);
	} catch {
		throw new Error(`${label || "配置"} JSON 格式错误`);
	}
};

export const normalizeList = (value: any): string[] => {
	if (Array.isArray(value)) {
		return value.map((item) => normalizeText(item)).filter(Boolean);
	}
	if (value == null) return [];
	const text = normalizeText(value);
	if (!text) return [];
	return [text];
};

export const normalizeTableList = (value: any): string[] => {
	if (Array.isArray(value)) {
		return value.map((item) => normalizeText(item)).filter(Boolean);
	}
	if (typeof value === "string") {
		return splitLines(value);
	}
	return [];
};

export const mergeTableSelections = (...sources: any[]): string[] => {
	const merged: string[] = [];
	sources.forEach((source) => {
		normalizeTableList(source).forEach((item) => {
			const text = normalizeText(item);
			if (!text) return;
			if (!merged.includes(text)) {
				merged.push(text);
			}
		});
	});
	return merged;
};

export const isSameTableList = (left: string[], right: string[]) => {
	if (left.length !== right.length) return false;
	return left.every((value, index) => value === right[index]);
};

export const extractWriterTables = (config?: Record<string, any>) => {
	if (!config) return [];
	const direct = normalizeList(config.table || config.tables);
	if (direct.length) return direct;
	const connection = config.connection;
	if (Array.isArray(connection) && connection.length) {
		return normalizeList(connection[0]?.table || connection[0]?.tables);
	}
	if (connection && typeof connection === "object") {
		return normalizeList((connection as any).table || (connection as any).tables);
	}
	return [];
};

export const extractReaderTables = (config?: Record<string, any>) => {
	if (!config) return [];
	const direct = normalizeList(config.table || config.tables);
	if (direct.length) return direct;
	const connection = config.connection;
	if (Array.isArray(connection) && connection.length) {
		return normalizeList(connection[0]?.table || connection[0]?.tables);
	}
	if (connection && typeof connection === "object") {
		return normalizeList((connection as any).table || (connection as any).tables);
	}
	return [];
};

export const extractWriterJdbcUrls = (config?: Record<string, any>) => {
	if (!config) return [];
	const direct = normalizeList(config.jdbcUrl || config.jdbcUrls);
	if (direct.length) return direct;
	const connection = config.connection;
	if (Array.isArray(connection) && connection.length) {
		return normalizeList(connection[0]?.jdbcUrl || connection[0]?.jdbcUrls);
	}
	if (connection && typeof connection === "object") {
		return normalizeList((connection as any).jdbcUrl || (connection as any).jdbcUrls);
	}
	return [];
};

export const extractWriterColumns = (config?: Record<string, any>) => {
	if (!config) return "";
	const value = config.column ?? config.columns;
	if (Array.isArray(value)) {
		const cols = value.map((item) => normalizeText(item)).filter(Boolean);
		return cols.length ? cols.join(",") : "";
	}
	return normalizeText(value);
};

export const extractWriterSqlList = (value?: any) => {
	const list = normalizeList(value);
	return list.join("\n");
};

export const buildWriterExtraConfig = (config?: Record<string, any>) => {
	if (!config) return "";
	const knownKeys = new Set([
		"connection",
		"jdbcUrl",
		"jdbcUrls",
		"table",
		"tables",
		"column",
		"columns",
		"writeMode",
		"username",
		"password",
		"schema",
		"database",
		"preSql",
		"postSql",
		"writerType",
		"tablePrefix",
		"prefix",
		"targetPrefix",
	]);
	const extra: Record<string, any> = {};
	Object.keys(config).forEach((key) => {
		if (knownKeys.has(key)) return;
		extra[key] = config[key];
	});
	if (!Object.keys(extra).length) return "";
	try {
		return JSON.stringify(extra, null, 2);
	} catch {
		return "";
	}
};

export const tryParseJson = (value: any) => {
	if (value == null) return undefined;
	if (typeof value === "string") {
		const text = value.trim();
		if (!text) return undefined;
		try {
			return JSON.parse(text);
		} catch {
			return undefined;
		}
	}
	return value;
};

const asRecord = (value: any): Record<string, any> | undefined =>
	value && typeof value === "object" && !Array.isArray(value) ? value : undefined;

export const extractWriterFromAddax = (config?: Record<string, any>) =>
	config?.job?.content?.[0]?.writer?.parameter;

export const extractWriterTypeFromAddax = (config?: Record<string, any>) =>
	normalizeText(config?.job?.content?.[0]?.writer?.name);

export const hasConnectionOverride = (config: any): boolean => {
	if (!config) return false;
	if (Array.isArray(config)) {
		return config.some((item) => hasConnectionOverride(item));
	}
	if (typeof config !== "object") return false;
	for (const key of CONNECTION_KEYS) {
		if (key in config && normalizeText((config as any)[key])) {
			return true;
		}
	}
	const connection = (config as any).connection;
	if (connection) {
		return hasConnectionOverride(connection);
	}
	return false;
};

export const buildTableKey = (table: TableInfo) =>
	normalizeText(table.schema) ? `${table.schema}.${table.name}` : table.name;

export const normalizeDiscoveredTable = (table: TableInfo): TableInfo => {
	const schema = normalizeText(table.schema);
	const name = normalizeText(table.name);
	if (!schema || !name) return table;
	const prefix = `${schema}.`;
	if (!name.toLowerCase().startsWith(prefix.toLowerCase())) {
		return table;
	}
	const trimmedName = name.slice(prefix.length).trim();
	if (!trimmedName) {
		return table;
	}
	return {
		...table,
		name: trimmedName,
	};
};

export const inferPrefixFromMapping = (mapping?: { source?: string; target?: string } | null) => {
	if (!mapping?.source || !mapping?.target) return "";
	const source = normalizeText(mapping.source).split(".").pop() || "";
	const target = normalizeText(mapping.target).split(".").pop() || "";
	if (!source || !target) return "";
	if (target.endsWith(source)) {
		return target.slice(0, target.length - source.length);
	}
	return "";
};

export const buildModelSelectorFromNames = (names: string[]) =>
	(names || []).filter(Boolean).map((name) => `model:${name}`).join(" ");

export const applyTablesToConfig = (rawConfig: Record<string, any> | undefined, tables: string[]) => {
	if (!rawConfig) return rawConfig;
	const config = { ...rawConfig };
	if (Array.isArray(config.connection) && config.connection.length) {
		const first = { ...config.connection[0], table: tables };
		config.connection = [first, ...config.connection.slice(1)];
		return config;
	}
	if (config.connection && typeof config.connection === "object") {
		config.connection = { ...(config.connection as Record<string, any>), table: tables };
		return config;
	}
	config.table = tables;
	return config;
};

export const shouldApplyWriterTables = (
	writerConfig: Record<string, any> | undefined,
	values: Record<string, any>,
) => {
	const mode = normalizeText(values.tableSelectionMode) || "all";
	if (mode === "manual") {
		return true;
	}
	const explicitTables = extractWriterTables(writerConfig).length > 0 || splitLines(values.writerTables).length > 0;
	if (explicitTables) return true;
	const prefix = normalizeText(values.syncPrefix);
	return !prefix;
};

export const applyPrefixToTables = (tables: string[], prefix?: string) => {
	const normalizedPrefix = normalizeText(prefix);
	return tables
		.map((table) => {
			const normalized = normalizeText(table);
			if (!normalized) return "";
			const base = normalized.includes(".") ? normalized.split(".").pop() || normalized : normalized;
			return normalizedPrefix ? `${normalizedPrefix}${base}` : base;
		})
		.filter(Boolean);
};

export const stripSchemaFromTable = (table?: string) => {
	const normalized = normalizeText(table);
	if (!normalized) return "";
	return normalized.includes(".") ? normalized.split(".").pop() || normalized : normalized;
};

export const isSourceAlignedTables = (sourceTables: string[], writerTables: string[]) => {
	if (!sourceTables.length || sourceTables.length !== writerTables.length) return false;
	for (let i = 0; i < sourceTables.length; i += 1) {
		const source = normalizeText(sourceTables[i]).toLowerCase();
		const writer = normalizeText(writerTables[i]).toLowerCase();
		if (!source || !writer) return false;
		if (source === writer) continue;
		if (stripSchemaFromTable(source) !== stripSchemaFromTable(writer)) return false;
	}
	return true;
};

export const buildReaderConfig = (values: Record<string, any>) => {
	const tables = splitLines(values.readerTables);
	const columns = splitColumns(values.readerColumns);
	const querySql = splitLines(values.readerQuerySql);
	const extra = parseJson(values.readerExtraConfig, "Reader 扩展配置") as Record<string, any> | undefined;
	const sourceSystem = normalizeText(values.sourceSystem);

	const config: Record<string, any> = {
		column: columns,
	};
	const where = normalizeText(values.readerWhere);
	if (where) config.where = where;
	if (sourceSystem) config.sourceSystem = sourceSystem;
	if (tables.length) config.table = tables;
	if (querySql.length) config.querySql = querySql;
	return mergeConfig(config, extra);
};

const parseOptionalJson = (value: any, label: string) => {
	const text = normalizeText(value);
	if (!text) return undefined;
	return parseJson(text, label);
};

const parseOptionalJsonObject = (value: any, label: string) => {
	const parsed = parseOptionalJson(value, label);
	if (parsed === undefined) return undefined;
	if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
		throw new Error(`${label} 必须是 JSON Object`);
	}
	return parsed as Record<string, any>;
};

const parseOptionalJsonArray = (value: any, label: string) => {
	const parsed = parseOptionalJson(value, label);
	if (parsed === undefined) return undefined;
	if (!Array.isArray(parsed)) {
		throw new Error(`${label} 必须是 JSON Array`);
	}
	return parsed;
};

export const buildApiReaderConfig = (values: Record<string, any>) => {
	const path = normalizeText(values.apiResourcePath);
	if (!path) {
		throw new Error("请填写 API 资源路径");
	}
	const method = normalizeText(values.apiMethod).toUpperCase() || "GET";
	const resourceId =
		normalizeText(values.apiResourceId) ||
		normalizeIdentifier(path.replace(/^\//, "").replace(/[/?#].*$/, "")) ||
		"api_resource";
	const resource: Record<string, any> = {
		resourceId,
		path,
		method,
	};
	const displayName = normalizeText(values.apiResourceDisplayName);
	const recordPath = normalizeText(values.apiRecordPath);
	if (displayName) resource.displayName = displayName;
	if (recordPath) resource.recordPath = recordPath;
	const query = parseOptionalJsonObject(values.apiQueryJson, "API Query 参数");
	const pagination = parseOptionalJsonObject(values.apiPaginationJson, "API 分页配置");
	const cursor = parseOptionalJsonObject(values.apiCursorJson, "API 增量游标");
	const fields = parseOptionalJsonArray(values.apiFieldsJson, "API 字段映射");
	const bodyTemplate = parseOptionalJson(values.apiBodyTemplateJson, "API Body 模板");
	if (query) resource.query = query;
	if (bodyTemplate !== undefined) resource.bodyTemplate = bodyTemplate;
	if (pagination) resource.pagination = pagination;
	if (cursor) resource.cursor = cursor;
	if (fields) resource.fields = fields;
	return {
		readerType: "httpreader",
		connectorType: "api",
		sourceCategory: "api",
		resource,
		resources: [resource],
	};
};

export const buildWriterConfig = (values: Record<string, any>) => {
	const jdbcUrls = splitLines(values.writerJdbcUrls);
	const tables = splitLines(values.writerTables);
	const columns = splitColumns(values.writerColumns);
	const preSql = splitLines(values.writerPreSql);
	const postSql = splitLines(values.writerPostSql);
	const extra = parseJson(values.writerExtraConfig, "Writer 扩展配置") as Record<string, any> | undefined;

	const connection: Record<string, any> = {};
	if (jdbcUrls.length) connection.jdbcUrl = jdbcUrls;
	if (tables.length) connection.table = tables;

	const config: Record<string, any> = {
		column: columns,
	};
	const username = normalizeText(values.writerUsername);
	const password = normalizeText(values.writerPassword);
	const schema = normalizeText(values.writerSchema);
	const writeMode = normalizeText(values.writerWriteMode);
	if (username) config.username = username;
	if (password) config.password = password;
	if (schema) config.schema = schema;
	if (writeMode) config.writeMode = writeMode;
	if (preSql.length) config.preSql = preSql;
	if (postSql.length) config.postSql = postSql;
	if (Object.keys(connection).length) config.connection = [connection];
	return mergeConfig(config, extra);
};

export const buildSyncConfigFromValues = (
	values: Record<string, any>,
	isFileSource: boolean,
): Record<string, any> | undefined => {
	const config: Record<string, any> = {};
	const syncMode = normalizeText(values.syncMode) || "full_refresh";
	if (!isFileSource && syncMode === "incremental") {
		const incrementalColumn = normalizeText(values.incrementalColumn);
		if (!incrementalColumn) {
			throw new Error("增量同步请填写增量列");
		}
		const incrementalType = normalizeText(values.incrementalType) || "datetime";
		const initialWatermark = normalizeText(values.initialWatermark);
		config.incrementalColumn = incrementalColumn;
		config.incrementalType = incrementalType;
		if (initialWatermark) {
			config.initialWatermark = initialWatermark;
		}
	}
	const governance = buildGovernanceSyncFields(values);
	if (Object.keys(governance).length) {
		config.governance = governance;
	}
	return Object.keys(config).length ? config : undefined;
};

export const buildJobPreview = (values: Record<string, any>, editorMode?: string, readerFallback?: string) => {
	const jobConfig = parseJson(values.jobConfig, "作业参数") as Record<string, any> | undefined;
	if (jobConfig) return jobConfig;

	let readerType = normalizeReaderType(values.readerType);
	if (!readerType) {
		readerType = resolveReaderTypeFromValues(values);
	}
	if (!readerType) {
		readerType = normalizeReaderType(readerFallback) || GENERIC_JDBC_READER;
	}

	const writerType = normalizeText(values.writerType);
	if (!writerType) throw new Error("Writer 类型不能为空");

	let readerConfig: Record<string, any> | undefined;
	let writerConfig: Record<string, any> | undefined;
	const sourceSystem = normalizeText(values.sourceSystem);

	if (editorMode === "json") {
		readerConfig = parseJson(values.readerConfig, "Reader 配置") as Record<string, any> | undefined;
		if (sourceSystem && readerConfig && !readerConfig.sourceSystem) {
			readerConfig.sourceSystem = sourceSystem;
		}
		writerConfig = parseJson(values.writerConfig, "Writer 配置") as Record<string, any> | undefined;
	} else {
		readerConfig = buildReaderConfig(values);
		writerConfig = buildWriterConfig(values);
	}
	applyReaderTypeToConfig(readerConfig, readerType);
	const selectedTables = mergeTableSelections(
		values.selectedTables,
		values.readerTables,
		extractReaderTables(readerConfig),
	);
	if (selectedTables.length) {
		readerConfig = applyTablesToConfig((readerConfig as Record<string, any>) ?? {}, selectedTables) ?? {};
	}
	const explicitWriterTables = splitLines(values.writerTables);
	const normalizedExplicitWriterTables =
		selectedTables.length && isSourceAlignedTables(selectedTables, explicitWriterTables)
			? applyPrefixToTables(selectedTables, values.syncPrefix)
			: explicitWriterTables;
	const existingWriterTables = extractWriterTables(writerConfig);
	const shouldDeriveWriterTables =
		!normalizedExplicitWriterTables.length &&
		(!existingWriterTables.length || isSourceAlignedTables(selectedTables, existingWriterTables));
	const resolvedWriterTables = normalizedExplicitWriterTables.length
		? normalizedExplicitWriterTables
		: shouldDeriveWriterTables
			? applyPrefixToTables(selectedTables, values.syncPrefix)
			: existingWriterTables;
	if (resolvedWriterTables.length) {
		writerConfig = applyTablesToConfig((writerConfig as Record<string, any>) ?? {}, resolvedWriterTables) ?? {};
	}

	return {
		job: {
			setting: {
				speed: {
					channel: 1,
				},
			},
			content: [
				{
					reader: {
						name: readerType,
						parameter: readerConfig || {},
					},
					writer: {
						name: writerType,
						parameter: writerConfig || {},
					},
				},
			],
		},
	};
};

export const jsonValidator = (label: string, forbidConnection = false) => (_: any, value: string) => {
	if (!normalizeText(value)) return Promise.resolve();
	try {
		const parsed = JSON.parse(value);
		if (forbidConnection && hasConnectionOverride(parsed)) {
			return Promise.reject(new Error(`${label} 不允许包含连接信息，请仅填写表/字段/过滤等覆盖参数`));
		}
		return Promise.resolve();
	} catch {
		return Promise.reject(new Error(`${label} JSON 格式错误`));
	}
};

export const writerConfigValidator = (_: any, value: string) => {
	if (!normalizeText(value)) return Promise.resolve();
	try {
		JSON.parse(value);
		return Promise.resolve();
	} catch {
		return Promise.reject(new Error("Writer 配置 JSON 格式错误"));
	}
};

export const loadDraft = (): Record<string, any> | null => {
	try {
		const stored = localStorage.getItem(DRAFT_STORAGE_KEY);
		if (!stored) return null;
		const draft = JSON.parse(stored);
		if (draft && typeof draft === "object" && draft.savedAt) {
			return draft;
		}
		return null;
	} catch {
		return null;
	}
};

export const saveDraft = (values: Record<string, any>) => {
	try {
		const draft = { ...values, savedAt: new Date().toISOString() };
		localStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(draft));
		return true;
	} catch {
		return false;
	}
};

export const clearDraft = () => {
	try {
		localStorage.removeItem(DRAFT_STORAGE_KEY);
	} catch {
		// ignore
	}
};

export const mapTaskToForm = (task: IngestionTaskDTO) => {
	const sourceConfig = tryParseJson(task.sourceConfig) || {};
	const syncConfig = tryParseJson(task.syncConfig) || {};
	const governanceConfig = (syncConfig.governance || {}) as Record<string, any>;
	const rawDestinationConfig = tryParseJson(task.destinationConfig);
	const destinationConfig = rawDestinationConfig || {};
	const addaxConfig = tryParseJson(task.addaxConfig) as Record<string, any> | undefined;
	const addaxWriterConfig = extractWriterFromAddax(addaxConfig);
	const readerTypeFromConfig = normalizeReaderType(
		sourceConfig.readerType || sourceConfig.reader || sourceConfig.type || sourceConfig.name,
	);
	const readerTypeFromAddax = normalizeReaderType(addaxConfig?.job?.content?.[0]?.reader?.name);
	const writerTypeFromConfig = normalizeText(
		destinationConfig.writerType || destinationConfig.writer || destinationConfig.type,
	);
	const writerType =
		normalizeText(task.destinationType) ||
		writerTypeFromConfig ||
		extractWriterTypeFromAddax(addaxConfig);
	const resolvedReaderType =
		normalizeReaderType(task.sourceType) || readerTypeFromConfig || readerTypeFromAddax || undefined;
	const isFileReader =
		resolvedReaderType && ["txtfilereader", "excelreader", "csv", "excel"].includes(resolvedReaderType.toLowerCase());
	const isApiReader =
		isApiReaderType(resolvedReaderType) ||
		isApiSourceType(task.sourceType) ||
		normalizeType(String(sourceConfig.connectorType || sourceConfig.sourceCategory || "")) === "api";
	const apiResource =
		(asRecord(sourceConfig.resource) as Record<string, any> | undefined) ||
		(Array.isArray(sourceConfig.resources) ? (sourceConfig.resources[0] as Record<string, any> | undefined) : undefined) ||
		{};
	const syncPrefix =
		normalizeText(
			destinationConfig.tablePrefix ||
			destinationConfig.prefix ||
			destinationConfig.targetPrefix ||
			inferPrefixFromMapping(task.tableMapping?.[0]),
		) || undefined;
	const resolvedWriterConfig =
		(rawDestinationConfig && Object.keys(rawDestinationConfig).length ? rawDestinationConfig : undefined) ||
		(addaxWriterConfig && Object.keys(addaxWriterConfig).length ? addaxWriterConfig : undefined);
	const writerTables = extractWriterTables(resolvedWriterConfig);
	const writerJdbcUrls = extractWriterJdbcUrls(resolvedWriterConfig);
	const writerColumns = extractWriterColumns(resolvedWriterConfig);
	const writerPreSql = extractWriterSqlList(resolvedWriterConfig?.preSql);
	const writerPostSql = extractWriterSqlList(resolvedWriterConfig?.postSql);
	const writerExtraConfig = buildWriterExtraConfig(resolvedWriterConfig);
	const writerUsername = normalizeText(resolvedWriterConfig?.username);
	const writerPassword = normalizeText(resolvedWriterConfig?.password);
	const writerSchema = normalizeText(resolvedWriterConfig?.schema || resolvedWriterConfig?.database);
	const writerWriteMode = normalizeText(resolvedWriterConfig?.writeMode);
	const selector = normalizeText(task.dbtModelSelector);
	const dbtModels = selector
		? selector
			.split(/\s+/)
			.filter((item) => item.startsWith("model:"))
			.map((item) => item.replace("model:", ""))
			.filter(Boolean)
		: [];
	const hasMapping = Array.isArray(task.tableMapping) && task.tableMapping.length > 0;
	const mappingTables = hasMapping ? extractMappingTables(task.tableMapping) : [];
	const fileAutoId = typeof sourceConfig._autoId === "boolean" ? sourceConfig._autoId : true;
	const columnPrefix = normalizeText(destinationConfig._columnPrefix);
	const columnSuffix = normalizeText(destinationConfig._columnSuffix);
	const scheduleState = parseSyncSchedule(task.syncSchedule);
	return {
		ownerDept: (task as any).ownerDept || undefined,
		editorMode: isFileReader || isApiReader ? "visual" : "json",
		sourceCategory: isFileReader ? "file" : isApiReader ? "api" : "database",
		syncMode: task.syncMode || "full_refresh",
		incrementalColumn: normalizeText(syncConfig.incrementalColumn) || undefined,
		incrementalType: normalizeText(syncConfig.incrementalType) || "datetime",
		initialWatermark: normalizeText(syncConfig.initialWatermark) || undefined,
		tableSelectionMode: hasMapping ? "manual" : "all",
		airflowEnabled: task.airflowEnabled ?? true,
		runNow: false,
		name: task.name,
		description: task.description,
		sourceSystem:
			sourceConfig.sourceSystem ||
			sourceConfig.sourceApp ||
			sourceConfig.appCode ||
			sourceConfig.system ||
			sourceConfig.name,
		sourceDataSourceId: task.sourceDataSourceId,
		readerType: resolvedReaderType,
		readerConfig: JSON.stringify(sourceConfig, null, 2),
		apiResourceId: normalizeText(apiResource.resourceId) || undefined,
		apiResourceDisplayName: normalizeText(apiResource.displayName) || undefined,
		apiResourcePath: normalizeText(apiResource.path) || undefined,
		apiMethod: normalizeText(apiResource.method) || "GET",
		apiRecordPath: normalizeText(apiResource.recordPath) || undefined,
		apiQueryJson: apiResource.query ? JSON.stringify(apiResource.query, null, 2) : undefined,
		apiBodyTemplateJson: apiResource.bodyTemplate ? JSON.stringify(apiResource.bodyTemplate, null, 2) : undefined,
		apiPaginationJson: apiResource.pagination ? JSON.stringify(apiResource.pagination, null, 2) : undefined,
		apiCursorJson: apiResource.cursor ? JSON.stringify(apiResource.cursor, null, 2) : undefined,
		apiFieldsJson: apiResource.fields ? JSON.stringify(apiResource.fields, null, 2) : undefined,
		fileAutoId: fileAutoId,
		fileTableName: isFileReader && writerTables.length ? writerTables[0] : undefined,
		selectedTables: mappingTables.length ? mappingTables.join("\n") : undefined,
		readerTables:
			mappingTables.length && !extractReaderTables(sourceConfig).length
				? mappingTables.join("\n")
				: undefined,
		syncPrefix,
		writerType: writerType || undefined,
		writerJdbcUrls: writerJdbcUrls.length ? writerJdbcUrls.join("\n") : undefined,
		writerTables: writerTables.length ? writerTables.join("\n") : undefined,
		writerColumns: writerColumns || undefined,
		writerWriteMode: writerWriteMode || undefined,
		writerUsername: writerUsername || undefined,
		writerPassword: writerPassword || undefined,
		writerSchema: writerSchema || undefined,
		writerPreSql: writerPreSql || undefined,
		writerPostSql: writerPostSql || undefined,
		writerExtraConfig: writerExtraConfig || undefined,
		writerConfig: resolvedWriterConfig ? JSON.stringify(resolvedWriterConfig, null, 2) : undefined,
		jobConfig: addaxConfig ? JSON.stringify(addaxConfig, null, 2) : undefined,
		dbtModels,
		dbtModelSelector: task.dbtModelSelector,
		dbtDagSelector: task.dbtDagSelector,
		columnPrefix: columnPrefix || undefined,
		columnSuffix: columnSuffix || undefined,
		scheduleType: scheduleState.scheduleType,
		scheduleCron: scheduleState.scheduleCron,
		scheduleIntervalMinutes: scheduleState.scheduleIntervalMinutes,
		taskConcurrency: toOptionalNumber(governanceConfig.maxConcurrentRuns),
		sourceConcurrency: toOptionalNumber(governanceConfig.sourceConcurrencyLimit),
		projectConcurrency: toOptionalNumber(governanceConfig.projectConcurrencyLimit),
		projectKey: normalizeText(governanceConfig.projectKey) || undefined,
		priority: normalizeText(governanceConfig.priority) || undefined,
		rejectPolicy: normalizeText(governanceConfig.rejectPolicy) || undefined,
		windowStart: normalizeText(governanceConfig.windowStart) || undefined,
		windowEnd: normalizeText(governanceConfig.windowEnd) || undefined,
		windowTimezone: normalizeText(governanceConfig.windowTimezone) || undefined,
	};
};

export const extractFileUploadResult = (task: IngestionTaskDTO): FileUploadResult | null => {
	if (!task) return null;
	const sourceConfig = tryParseJson(task.sourceConfig) || {};
	const rawPath = sourceConfig._filePath || sourceConfig.filePath || sourceConfig.path;
	const hostPath = normalizeText(Array.isArray(rawPath) ? rawPath[0] : rawPath);
	const containerPath = normalizeText(sourceConfig._containerPath) || hostPath;
	if (!hostPath && !containerPath) return null;
	const originalName = normalizeText(sourceConfig._originalName) || normalizeText(task.name) || "uploaded_file";
	const fileType = normalizeText(sourceConfig._fileType) || "csv";
	const sourceFileType = resolveFileTypeFromName(originalName);
	const rawColumns = Array.isArray(sourceConfig._fileColumns) ? sourceConfig._fileColumns : [];
	const columns = rawColumns
		.map((col: any) => ({
			name: normalizeText(col?.safeName || col?.name || col?.column || col?.field),
			type: normalizeText(col?.type || col?.dataType) || "string",
			label: normalizeText(col?.label || col?.name || col?.column || col?.field),
		}))
		.filter((col: any) => col.name);
	return {
		hostPath: hostPath || containerPath,
		containerPath: containerPath || hostPath,
		fileType: fileType || "csv",
		sourceFileType,
		columns,
		originalName,
	};
};
