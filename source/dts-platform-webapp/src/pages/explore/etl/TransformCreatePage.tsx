import { useEffect, useMemo, useRef, useState } from "react";
import { Alert, Button, Card, Collapse, Divider, Form, Input, InputNumber, Modal, Progress, Radio, Select, Space, Steps, Switch, Table, Tag, Typography, Upload } from "antd";
import { SaveOutlined, InboxOutlined, PlusOutlined, DeleteOutlined } from "@ant-design/icons";
import { toast } from "sonner";
import { PageHeader } from "@/components/page-header";
import { createIngestionTask, listSqlModels } from "@/api/platformApi";
import { useUserInfo } from "@/store/userStore";
import { useParams, useRouter } from "@/routes/hooks";
import {
	ingestionTaskAPI,
	type DefaultDestinationStatus,
	type FileUploadResult,
	type IngestionConnectorCapabilityDTO,
	type IngestionExecutionDTO,
	type IngestionTaskDTO,
	type IngestionTaskTemplateDTO,
	type TableInfo,
	resolveExecutionPollIntervalMs,
} from "@/api/ingestion";
import dataSourcesService, { type ExcelImportErrorRow, type InfraDataSource } from "@/api/services/dataSourcesService";

const { Text } = Typography;

const DRAFT_STORAGE_KEY = "ingestion_task_draft";
const CONNECTION_KEYS = [
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

const JDBC_READER_BY_TYPE: Record<string, string> = {
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

const FILE_READER_BY_TYPE: Record<string, string> = {
	excel: "txtfilereader",
	csv: "txtfilereader",
	json: "jsonreader",
	api: "httpreader",
	http: "httpreader",
	file: "txtfilereader",
};

const GENERIC_JDBC_READER = "rdbmsreader";

const JDBC_READER_BY_URL: Record<string, string> = {
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

const TABLE_PLACEHOLDER = "${table}";

const normalizeText = (value?: string) => String(value || "").trim();

type AsyncRunProgressStatus = "active" | "success" | "exception";

type AsyncRunProgressView = {
	progress: number;
	status: AsyncRunProgressStatus;
	stage: string;
	detail: string;
	terminal: boolean;
};

const resolveCreatedTaskId = (payload: any): number | undefined => {
	const candidate = payload?.task?.id ?? payload?.taskId ?? payload?.id ?? payload?.task?.taskId;
	const value = Number(candidate);
	return Number.isFinite(value) && value > 0 ? value : undefined;
};

const mapExecutionToProgressView = (
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

const normalizeIdentifier = (value?: string) => {
	const text = normalizeText(value).toLowerCase();
	if (!text) return "";
	let safe = text.replace(/[^a-z0-9_]+/g, "_").replace(/^_+|_+$/g, "").replace(/_+/g, "_");
	if (!safe) return "";
	if (/^\d/.test(safe)) {
		safe = `col_${safe}`;
	}
	return safe;
};

const normalizeTableName = (value?: string) => {
	const text = normalizeText(value);
	if (!text) return "";
	const parts = text.split(".").filter(Boolean);
	if (!parts.length) return "";
	const normalized = parts.map((part) => normalizeIdentifier(part)).filter(Boolean);
	if (normalized.length !== parts.length) return "";
	return normalized.join(".");
};

const buildFileBaseName = (filename?: string) => {
	const base = normalizeText(filename || "file").replace(/\.[^.]+$/, "");
	const safe = normalizeIdentifier(base);
	return safe || "file";
};

const normalizeReaderType = (value?: string) => {
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

const normalizeType = (value?: string) => normalizeText(value).toLowerCase();

const normalizeTag = (value?: string) => normalizeText(value).toLowerCase().replace(/[^a-z0-9-]+/g, "-").replace(/^-+|-+$/g, "");

type SyncModeValue = "full_refresh" | "incremental" | "cdc" | "backfill";
type SyncModeOption = { value: SyncModeValue; label: string; disabled?: boolean };

const SYNC_MODE_LABELS: Record<SyncModeValue, string> = {
	full_refresh: "全量同步",
	incremental: "增量同步",
	cdc: "实时同步(CDC)",
	backfill: "历史回灌",
};

const CAPABILITY_TO_SYNC_MODE: Record<string, SyncModeValue> = {
	FULL: "full_refresh",
	INCREMENTAL: "incremental",
	CDC: "cdc",
	BACKFILL: "backfill",
};

const normalizeSyncModeValue = (value?: string): SyncModeValue | null => {
	const text = normalizeText(value).toLowerCase();
	if (!text) return null;
	if (text === "full" || text === "full_refresh" || text === "fullrefresh") return "full_refresh";
	if (text === "incremental" || text === "incr" || text === "delta") return "incremental";
	if (text === "cdc" || text === "realtime" || text === "real_time") return "cdc";
	if (text === "backfill" || text === "history_backfill" || text === "historical_backfill") return "backfill";
	return null;
};

const asBoolean = (value: any): boolean | null => {
	if (typeof value === "boolean") return value;
	if (typeof value === "string") {
		const text = value.trim().toLowerCase();
		if (text === "true") return true;
		if (text === "false") return false;
	}
	return null;
};

const deriveSyncModeOptions = (
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

type SyncScheduleFormState = {
	scheduleType: "manual" | "interval" | "cron";
	scheduleCron?: string;
	scheduleIntervalMinutes?: number;
};

const parseSyncSchedule = (value?: string): SyncScheduleFormState => {
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

const buildSyncScheduleSpec = (values: Record<string, any>) => {
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

const buildSyncScheduleText = (values: Record<string, any>) => {
	const spec = buildSyncScheduleSpec(values);
	if (!spec) return undefined;
	if (spec.type === "cron") return `cron:${spec.cron}`;
	if (spec.type === "interval") return `interval:${spec.intervalMinutes}`;
	return undefined;
};

const toOptionalNumber = (value: any) => {
	if (value === undefined || value === null || value === "") return undefined;
	const parsed = Number(value);
	return Number.isFinite(parsed) ? parsed : undefined;
};

const buildGovernanceSyncFields = (values: Record<string, any>): Record<string, any> => {
	const result: Record<string, any> = {};
	const taskConcurrency = toOptionalNumber(values.taskConcurrency);
	if (taskConcurrency !== undefined && taskConcurrency > 0) {
		result.taskConcurrency = Math.floor(taskConcurrency);
	}
	const sourceConcurrency = toOptionalNumber(values.sourceConcurrency);
	if (sourceConcurrency !== undefined && sourceConcurrency >= 0) {
		result.sourceConcurrency = Math.floor(sourceConcurrency);
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

const validateCronExpression = (_: unknown, value: string) => {
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

const resolveFileTypeFromName = (name?: string) => {
	const normalized = normalizeText(name).toLowerCase();
	if (!normalized) return "csv";
	if (normalized.endsWith(".xlsx") || normalized.endsWith(".xls")) return "excel";
	if (normalized.endsWith(".csv")) return "csv";
	return "csv";
};

const resolveSourceSystemFromDataSource = (source?: InfraDataSource | null) => {
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

const resolveReaderTypeFromDataSource = (source?: InfraDataSource | null) => {
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

const resolveReaderTypeFromSourceId = (
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

const extractMappingTables = (mapping?: Array<{ source?: string; target?: string }>) =>
	(Array.isArray(mapping) ? mapping : [])
		.map((item) => normalizeText(item?.source))
		.filter(Boolean);

const applyReaderTypeToConfig = (config: Record<string, any> | undefined, readerType?: string) => {
	const resolved = normalizeText(readerType);
	if (!resolved || resolved.toLowerCase() === "auto" || !config) return config;
	if (!normalizeText(config.readerType || config.reader || config.type || config.name)) {
		config.readerType = resolved;
	}
	return config;
};

const resolveReaderTypeFromValues = (values: Record<string, any>) => {
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

const isJdbcSource = (source?: InfraDataSource | null) => {
	if (!source) return false;
	if (normalizeText(source.jdbcUrl)) return true;
	const type = normalizeType(source.type);
	return Boolean(type && (type === "jdbc" || JDBC_READER_BY_TYPE[type]));
};

const resolveTaskName = (values: Record<string, any>, form?: any) => {
	const fallback = form ? normalizeText(form.getFieldValue("name")) : "";
	return (
		normalizeText(values?.name) ||
		normalizeText(values?.taskName) ||
		normalizeText(values?.title) ||
		fallback
	);
};

const splitLines = (value?: string) =>
	normalizeText(value)
		.split(/\r?\n/)
		.map((item) => item.trim())
		.filter(Boolean);

const splitColumns = (value?: string) => {
	const text = normalizeText(value);
	if (!text) return ["*"];
	if (text === "*") return ["*"];
	return text
		.split(",")
		.map((item) => item.trim())
		.filter(Boolean);
};

const hasTableEntries = (tables: string[]) => tables.some((table) => Boolean(normalizeText(table)));

const mergeConfig = (base: Record<string, any>, extra?: Record<string, any>) => {
	if (!extra || Object.keys(extra).length === 0) return base;
	return { ...base, ...extra };
};

const parseJson = (value?: string, label?: string) => {
	const text = normalizeText(value);
	if (!text) return undefined;
	try {
		return JSON.parse(text);
	} catch {
		throw new Error(`${label || "配置"} JSON 格式错误`);
	}
};

const normalizeList = (value: any): string[] => {
	if (Array.isArray(value)) {
		return value.map((item) => normalizeText(item)).filter(Boolean);
	}
	if (value == null) return [];
	const text = normalizeText(value);
	if (!text) return [];
	return [text];
};

const normalizeTableList = (value: any): string[] => {
	if (Array.isArray(value)) {
		return value.map((item) => normalizeText(item)).filter(Boolean);
	}
	if (typeof value === "string") {
		return splitLines(value);
	}
	return [];
};

const mergeTableSelections = (...sources: any[]): string[] => {
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

const isSameTableList = (left: string[], right: string[]) => {
	if (left.length !== right.length) return false;
	return left.every((value, index) => value === right[index]);
};

const extractWriterTables = (config?: Record<string, any>) => {
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

const extractReaderTables = (config?: Record<string, any>) => {
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

const extractWriterJdbcUrls = (config?: Record<string, any>) => {
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

const extractWriterColumns = (config?: Record<string, any>) => {
	if (!config) return "";
	const value = config.column ?? config.columns;
	if (Array.isArray(value)) {
		const cols = value.map((item) => normalizeText(item)).filter(Boolean);
		return cols.length ? cols.join(",") : "";
	}
	return normalizeText(value);
};

const extractWriterSqlList = (value?: any) => {
	const list = normalizeList(value);
	return list.join("\n");
};

const buildWriterExtraConfig = (config?: Record<string, any>) => {
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

const tryParseJson = (value: any) => {
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

const extractWriterFromAddax = (config?: Record<string, any>) =>
	config?.job?.content?.[0]?.writer?.parameter;

const extractWriterTypeFromAddax = (config?: Record<string, any>) =>
	normalizeText(config?.job?.content?.[0]?.writer?.name);

const hasConnectionOverride = (config: any): boolean => {
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

const buildTableKey = (table: TableInfo) =>
	normalizeText(table.schema) ? `${table.schema}.${table.name}` : table.name;

const normalizeDiscoveredTable = (table: TableInfo): TableInfo => {
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

const inferPrefixFromMapping = (mapping?: { source?: string; target?: string } | null) => {
	if (!mapping?.source || !mapping?.target) return "";
	const source = normalizeText(mapping.source).split(".").pop() || "";
	const target = normalizeText(mapping.target).split(".").pop() || "";
	if (!source || !target) return "";
	if (target.endsWith(source)) {
		return target.slice(0, target.length - source.length);
	}
	return "";
};

const buildModelSelectorFromNames = (names: string[]) =>
	(names || []).filter(Boolean).map((name) => `model:${name}`).join(" ");

const applyTablesToConfig = (rawConfig: Record<string, any> | undefined, tables: string[]) => {
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

const shouldApplyWriterTables = (
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

const applyPrefixToTables = (tables: string[], prefix?: string) => {
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

const stripSchemaFromTable = (table?: string) => {
	const normalized = normalizeText(table);
	if (!normalized) return "";
	return normalized.includes(".") ? normalized.split(".").pop() || normalized : normalized;
};

const isSourceAlignedTables = (sourceTables: string[], writerTables: string[]) => {
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

const buildReaderConfig = (values: Record<string, any>) => {
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

const buildWriterConfig = (values: Record<string, any>) => {
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

const buildSyncConfigFromValues = (
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

const buildJobPreview = (values: Record<string, any>, editorMode?: string, readerFallback?: string) => {
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

const jsonValidator = (label: string, forbidConnection = false) => (_: any, value: string) => {
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

const writerConfigValidator = (_: any, value: string) => {
	if (!normalizeText(value)) return Promise.resolve();
	try {
		JSON.parse(value);
		return Promise.resolve();
	} catch {
		return Promise.reject(new Error("Writer 配置 JSON 格式错误"));
	}
};

const loadDraft = (): Record<string, any> | null => {
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

const saveDraft = (values: Record<string, any>) => {
	try {
		const draft = { ...values, savedAt: new Date().toISOString() };
		localStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(draft));
		return true;
	} catch {
		return false;
	}
};

const clearDraft = () => {
	try {
		localStorage.removeItem(DRAFT_STORAGE_KEY);
	} catch {
		// ignore
	}
};

const mapTaskToForm = (task: IngestionTaskDTO) => {
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
		editorMode: isFileReader ? "visual" : "json",
		sourceCategory: isFileReader ? "file" : "database",
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
		priority: normalizeText(governanceConfig.priority) || undefined,
		rejectPolicy: normalizeText(governanceConfig.rejectPolicy) || undefined,
		windowStart: normalizeText(governanceConfig.windowStart) || undefined,
		windowEnd: normalizeText(governanceConfig.windowEnd) || undefined,
		windowTimezone: normalizeText(governanceConfig.windowTimezone) || undefined,
	};
};

const extractFileUploadResult = (task: IngestionTaskDTO): FileUploadResult | null => {
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

export default function TransformCreatePage() {
	const [saving, setSaving] = useState(false);
	const [savingDraft, setSavingDraft] = useState(false);
	const [hasDraft, setHasDraft] = useState(false);
	const [loadingTask, setLoadingTask] = useState(false);
	const [editingTask, setEditingTask] = useState<IngestionTaskDTO | null>(null);
	const [discoveringTables, setDiscoveringTables] = useState(false);
	const [discoveredTables, setDiscoveredTables] = useState<TableInfo[]>([]);
	const [selectedTableKeys, setSelectedTableKeys] = useState<string[]>([]);
	const selectedTableKeysRef = useRef<string[]>([]);
	const [discoverError, setDiscoverError] = useState("");
	const [tablePageSize, setTablePageSize] = useState(8);
	const [defaultDestinationStatus, setDefaultDestinationStatus] = useState<DefaultDestinationStatus | null>(null);
	const [loadingDefaultDestination, setLoadingDefaultDestination] = useState(false);
	const [defaultDestinationError, setDefaultDestinationError] = useState("");
	const [currentStep, setCurrentStep] = useState(0);
	const [dataSources, setDataSources] = useState<InfraDataSource[]>([]);
	const [loadingDataSources, setLoadingDataSources] = useState(false);
	const [connectorCapabilities, setConnectorCapabilities] = useState<IngestionConnectorCapabilityDTO[]>([]);
	const [capabilityLoadFailed, setCapabilityLoadFailed] = useState(false);
	const [taskTemplates, setTaskTemplates] = useState<IngestionTaskTemplateDTO[]>([]);
	const [loadingTaskTemplates, setLoadingTaskTemplates] = useState(false);
	const [selectedTemplateId, setSelectedTemplateId] = useState<string | undefined>(undefined);
	const [sqlModels, setSqlModels] = useState<Array<{ id?: string; name?: string; alias?: string }>>([]);
	const [loadingSqlModels, setLoadingSqlModels] = useState(false);
	const [fileUploadResult, setFileUploadResult] = useState<FileUploadResult | null>(null);
	const [extraColumns, setExtraColumns] = useState<Array<{ name: string; label: string; type: string; defaultValue: string }>>([]);
	const [uploadingFile, setUploadingFile] = useState(false);
	const [filePreviewRows, setFilePreviewRows] = useState(20);
	const [filePreviewCols, setFilePreviewCols] = useState(8);
	const [previewRefreshing, setPreviewRefreshing] = useState(false);
	const [errorPreviewOpen, setErrorPreviewOpen] = useState(false);
	const [errorPreviewLoading, setErrorPreviewLoading] = useState(false);
	const [errorPreviewRows, setErrorPreviewRows] = useState<ExcelImportErrorRow[]>([]);
	const [errorPreviewLimit, setErrorPreviewLimit] = useState(50);
	const [asyncRunModalOpen, setAsyncRunModalOpen] = useState(false);
	const [asyncRunTaskId, setAsyncRunTaskId] = useState<number | null>(null);
	const [asyncRunTaskName, setAsyncRunTaskName] = useState("");
	const [asyncRunExecution, setAsyncRunExecution] = useState<IngestionExecutionDTO | null>(null);
	const [asyncRunProgress, setAsyncRunProgress] = useState<AsyncRunProgressView>({
		progress: 0,
		status: "active",
		stage: "等待提交",
		detail: "",
		terminal: false,
	});
	const asyncRunPollTimerRef = useRef<number | null>(null);
	const asyncRunStartedAtRef = useRef<number>(0);
	const [form] = Form.useForm();
	const lastAutoDiscoveryKeyRef = useRef("");
	const router = useRouter();
	const params = useParams();
	const editId = params?.id ? Number(params.id) : undefined;
	const isEdit = Number.isFinite(editId);
	const userInfo = useUserInfo() as any;
	const editorMode = Form.useWatch("editorMode", form);
	const [sourceCategory, setSourceCategory] = useState<string>("database");
	const syncMode = Form.useWatch("syncMode", form);
	const scheduleType = Form.useWatch("scheduleType", form);
	const tableSelectionMode = Form.useWatch("tableSelectionMode", form);
	const formValues = Form.useWatch([], form);
	const selectedTablesValue = Form.useWatch("selectedTables", form);
	const selectedDataSourceId = Form.useWatch("sourceDataSourceId", form);
	const selectedDataSource = useMemo(
		() => dataSources.find((item) => String(item.id) === String(selectedDataSourceId)),
		[dataSources, selectedDataSourceId]
	);
	const discoveredTableKeys = useMemo(
		() => discoveredTables.map((item) => buildTableKey(item)),
		[discoveredTables]
	);

	useEffect(() => {
		const normalized = mergeTableSelections(selectedTablesValue);
		const currentMode = normalizeText(form.getFieldValue("tableSelectionMode")) || normalizeText(tableSelectionMode) || "all";
		if (!normalized.length) {
			if (selectedTableKeys.length && currentMode === "all") {
				setSelectedTableKeys([]);
			}
			selectedTableKeysRef.current = normalized;
			return;
		}
		if (!isSameTableList(normalized, selectedTableKeys)) {
			setSelectedTableKeys(normalized);
		}
		selectedTableKeysRef.current = normalized;
	}, [selectedTablesValue, selectedTableKeys, tableSelectionMode, form]);

	const initialValues = useMemo(
		() => ({
			editorMode: "visual",
			sourceCategory: "database",
			syncMode: "full_refresh",
			scheduleType: "manual",
			scheduleIntervalMinutes: 60,
			tableSelectionMode: "all",
			airflowEnabled: true,
			runNow: true,
			readerColumns: "*",
			writerColumns: "*",
			incrementalType: "datetime",
			syncPrefix: "",
			fileAutoId: true,
			priority: "MEDIUM",
			rejectPolicy: "REJECT",
			windowTimezone: "Asia/Shanghai",
		}),
		[]
	);

	const stopAsyncRunPolling = () => {
		if (asyncRunPollTimerRef.current !== null) {
			window.clearInterval(asyncRunPollTimerRef.current);
			asyncRunPollTimerRef.current = null;
		}
	};

	useEffect(() => {
		return () => {
			if (asyncRunPollTimerRef.current !== null) {
				window.clearInterval(asyncRunPollTimerRef.current);
				asyncRunPollTimerRef.current = null;
			}
		};
	}, []);

	const closeAsyncRunModal = () => {
		stopAsyncRunPolling();
		setAsyncRunModalOpen(false);
	};

	const startAsyncRunProgress = (taskId: number, taskName: string, pollIntervalMs?: number) => {
		stopAsyncRunPolling();
		asyncRunStartedAtRef.current = Date.now();
		setAsyncRunTaskId(taskId);
		setAsyncRunTaskName(taskName);
		setAsyncRunExecution(null);
		setAsyncRunModalOpen(true);
		setAsyncRunProgress({
			progress: 10,
			status: "active",
			stage: "任务已提交",
			detail: "正在后台触发执行。",
			terminal: false,
		});

		const pollExecution = async () => {
			const elapsedMs = Date.now() - asyncRunStartedAtRef.current;
			if (elapsedMs > 5 * 60 * 1000) {
				stopAsyncRunPolling();
				setAsyncRunProgress({
					progress: 100,
					status: "exception",
					stage: "状态同步超时",
					detail: "超出等待时间，请进入任务详情页继续查看执行状态。",
					terminal: true,
				});
				return;
			}
			try {
				const latest = await ingestionTaskAPI.getLatestExecution(taskId);
				setAsyncRunExecution(latest);
				const nextProgress = mapExecutionToProgressView(latest, elapsedMs);
				setAsyncRunProgress(nextProgress);
				if (nextProgress.terminal) {
					stopAsyncRunPolling();
				}
			} catch {
				setAsyncRunProgress((prev) => ({
					...prev,
					detail: "状态同步中，稍后自动重试。",
				}));
			}
		};

		void pollExecution();
		asyncRunPollTimerRef.current = window.setInterval(() => {
			void pollExecution();
		}, resolveExecutionPollIntervalMs(pollIntervalMs));
	};

	const handleCreateTaskResult = (result: any, runNow: boolean, taskName: string) => {
		const createdTaskId = resolveCreatedTaskId(result);
		const pollHint = Number(result?.execution?.pollIntervalMs ?? result?.pollIntervalMs);
		clearDraft();
		setHasDraft(false);
		if (runNow && createdTaskId) {
			toast.success("任务已提交，正在后台执行");
			startAsyncRunProgress(createdTaskId, taskName, Number.isFinite(pollHint) ? pollHint : undefined);
			return;
		}
		toast.success("入湖任务已提交");
		router.push("/explore/etl/transform");
	};

	const syncSelectedTablesToForm = (tables: string[], opts?: { silent?: boolean }) => {
		const nextTables = mergeTableSelections(tables);
		setSelectedTableKeys(nextTables);
		selectedTableKeysRef.current = nextTables;
		form.setFieldValue("selectedTables", nextTables.join("\n"));
		if (!nextTables.length) {
			form.setFieldValue("tableSelectionMode", "all");
			form.setFieldValue("readerTables", "");
			form.setFieldValue("writerTables", "");
			if (!opts?.silent) {
				toast.info("已清空表清单");
			}
			return;
		}
		const values = form.getFieldsValue(true);
		const isJsonMode = values.editorMode === "json";
		const writerTables = applyPrefixToTables(nextTables, values.syncPrefix);
		try {
			if (isJsonMode) {
				const readerConfig = parseJson(values.readerConfig, "Reader 配置") as Record<string, any> | undefined;
				const nextReader = applyTablesToConfig(readerConfig || {}, nextTables);
				form.setFieldValue("readerConfig", JSON.stringify(nextReader || {}, null, 2));
				const writerConfig = parseJson(values.writerConfig, "Writer 配置") as Record<string, any> | undefined;
				const nextWriter = applyTablesToConfig(writerConfig || {}, writerTables);
				form.setFieldValue("writerConfig", JSON.stringify(nextWriter || {}, null, 2));
			}
			// Always sync visual-mode fields so they persist across steps
			form.setFieldValue("readerTables", nextTables.join("\n"));
			form.setFieldValue("writerTables", writerTables.join("\n"));
			form.setFieldValue("tableSelectionMode", "manual");
			if (!opts?.silent) {
				toast.success("已更新表清单");
			}
		} catch (err: any) {
			if (!opts?.silent) {
				toast.error(err?.message || "更新表清单失败");
			}
		}
	};

	const resolveSelectedTables = (values?: Record<string, any>) => {
		const snapshot = values ?? form.getFieldsValue(true);
		const selectionMode = normalizeText(snapshot?.tableSelectionMode) || "all";
		const selected = snapshot?.selectedTables ?? form.getFieldValue("selectedTables");
		const keys = mergeTableSelections(selectedTableKeysRef.current, selectedTableKeys);
		if (selectionMode !== "manual") {
			return mergeTableSelections(selected, keys);
		}
		return mergeTableSelections(selected, keys, snapshot?.readerTables, snapshot?.writerTables);
	};

	useEffect(() => {
		const loadSources = async () => {
			try {
				setLoadingDataSources(true);
				const list = await dataSourcesService.list();
				setDataSources(Array.isArray(list) ? list : []);
			} catch (error: any) {
				toast.error(error?.message || "数据源列表加载失败");
				setDataSources([]);
			} finally {
				setLoadingDataSources(false);
			}
		};
		loadSources();
	}, []);

	useEffect(() => {
		const loadCapabilities = async () => {
			try {
				const rows = await ingestionTaskAPI.getConnectorCapabilities();
				setConnectorCapabilities(Array.isArray(rows) ? rows : []);
				setCapabilityLoadFailed(false);
			} catch {
				setConnectorCapabilities([]);
				setCapabilityLoadFailed(true);
			}
		};
		void loadCapabilities();
	}, []);

	useEffect(() => {
		const loadTemplates = async () => {
			try {
				setLoadingTaskTemplates(true);
				const rows = await ingestionTaskAPI.getTaskTemplates();
				const list = Array.isArray(rows) ? rows : [];
				setTaskTemplates(list);
				if (!selectedTemplateId && list.length) {
					setSelectedTemplateId(String(list[0].id));
				}
			} catch {
				setTaskTemplates([]);
			} finally {
				setLoadingTaskTemplates(false);
			}
		};
		void loadTemplates();
	}, []);

	useEffect(() => {
		let active = true;
		const loadDefaultDestination = async () => {
			try {
				setLoadingDefaultDestination(true);
				setDefaultDestinationError("");
				const status = await ingestionTaskAPI.getDefaultDestinationStatus();
				if (active) {
					setDefaultDestinationStatus(status);
					if (status?.writerType) {
						form.setFieldValue("writerType", status.writerType);
					}
				}
			} catch (error: any) {
				if (active) {
					setDefaultDestinationError(error?.message || "无法获取默认数据湖配置");
				}
			} finally {
				if (active) {
					setLoadingDefaultDestination(false);
				}
			}
		};
		loadDefaultDestination();
		return () => {
			active = false;
		};
	}, [form]);

	useEffect(() => {
		const loadModels = async () => {
			try {
				setLoadingSqlModels(true);
				const list = (await listSqlModels()) as Array<{ id?: string; name?: string; alias?: string }>;
				setSqlModels(Array.isArray(list) ? list : []);
			} catch (error: any) {
				toast.error(error?.message || "模型列表加载失败");
				setSqlModels([]);
			} finally {
				setLoadingSqlModels(false);
			}
		};
		loadModels();
	}, []);

	useEffect(() => {
		if (!selectedDataSource) {
			if (!selectedDataSourceId && sourceCategory !== "file" && form.getFieldValue("readerType")) {
				form.setFieldValue("readerType", undefined);
			}
			if (selectedDataSourceId && sourceCategory !== "file") {
				form.setFieldValue("readerType", GENERIC_JDBC_READER);
			}
			return;
		}
		const readerType = resolveReaderTypeFromDataSource(selectedDataSource);
		if (readerType) {
			if (form.getFieldValue("readerType") !== readerType) {
				form.setFieldValue("readerType", readerType);
			}
		} else if (isJdbcSource(selectedDataSource)) {
			form.setFieldValue("readerType", GENERIC_JDBC_READER);
		}
		const currentSourceSystem = normalizeText(form.getFieldValue("sourceSystem"));
		if (!currentSourceSystem) {
			const resolved = resolveSourceSystemFromDataSource(selectedDataSource);
			if (resolved) {
				form.setFieldValue("sourceSystem", resolved);
			}
		}
		const currentDagSelector = normalizeText(form.getFieldValue("dbtDagSelector"));
		if (!currentDagSelector) {
			const sourceSystem = normalizeText(form.getFieldValue("sourceSystem")) || resolveSourceSystemFromDataSource(selectedDataSource);
			if (sourceSystem) {
				form.setFieldValue("dbtDagSelector", `tab:${normalizeTag(sourceSystem)}`);
			}
		}
	}, [form, selectedDataSource, selectedDataSourceId, sourceCategory]);

	useEffect(() => {
		setDiscoveredTables([]);
		setSelectedTableKeys([]);
		setDiscoverError("");
		lastAutoDiscoveryKeyRef.current = "";
	}, [selectedDataSourceId]);

	useEffect(() => {
		if (tableSelectionMode !== "manual") return;
		if (!selectedDataSourceId) return;
		if (!selectedDataSource || !isJdbcSource(selectedDataSource)) return;
		if (discoveringTables) return;
		if (discoveredTables.length > 0) return;
		const schema = normalizeText(form.getFieldValue("readerSchema"));
		const pattern = normalizeText(form.getFieldValue("readerTablePattern"));
		const autoKey = `${selectedDataSourceId || ""}:${schema}:${pattern}`;
		if (lastAutoDiscoveryKeyRef.current === autoKey) return;
		lastAutoDiscoveryKeyRef.current = autoKey;
		void handleDiscoverTables();
	}, [
		tableSelectionMode,
		selectedDataSourceId,
		selectedDataSource,
		discoveringTables,
		discoveredTables.length,
		form,
	]);

	// Selection clearing is handled explicitly when用户切换到“全部表”

	// Sync selected tables to writer fields when entering Step 2
	// This ensures values persist even when writerTables/writerConfig Form.Items
	// were not mounted (on Step 1) when the selection was made
	useEffect(() => {
		if (isFileFlow || currentStep !== 2) return;
		const selected = mergeTableSelections(
			selectedTableKeysRef.current,
			selectedTableKeys,
		);
		if (!selected.length) return;
		const values = form.getFieldsValue(true);
		const isJsonMode = values.editorMode === "json";
		const writerTables = applyPrefixToTables(selected, values.syncPrefix);
		if (isJsonMode) {
			const writerConfig = tryParseJson(values.writerConfig) || {};
			const existingTables = extractWriterTables(writerConfig);
			if (!existingTables.length) {
				const nextWriter = applyTablesToConfig(writerConfig, writerTables);
				form.setFieldValue("writerConfig", JSON.stringify(nextWriter || {}, null, 2));
			}
		} else {
			const current = splitLines(values.writerTables);
			if (!current.length) {
				form.setFieldValue("writerTables", writerTables.join("\n"));
			}
		}
	}, [currentStep, selectedTableKeys, form]);

	const isFileFlow = sourceCategory === "file";
	const resolvedConnectorType = useMemo(() => {
		if (isFileFlow) return "file";
		const sourceType = normalizeType(selectedDataSource?.type);
		if (sourceType === "airbyte") return "airbyte";
		return "addax";
	}, [isFileFlow, selectedDataSource?.type]);
	const activeConnectorCapability = useMemo(
		() =>
			connectorCapabilities.find(
				(item) => normalizeType(item.connectorType) === normalizeType(resolvedConnectorType)
			) || null,
		[connectorCapabilities, resolvedConnectorType]
	);
	const activeCapabilitySet = useMemo(() => {
		const set = new Set<string>();
		(activeConnectorCapability?.capabilities || []).forEach((item) => {
			const text = normalizeText(item);
			if (text) set.add(text.toUpperCase());
		});
		return set;
	}, [activeConnectorCapability]);
	const syncModeOptions = useMemo(
		() => deriveSyncModeOptions(resolvedConnectorType, activeConnectorCapability, isFileFlow),
		[resolvedConnectorType, activeConnectorCapability, isFileFlow]
	);
	const supportedSyncModes = useMemo(
		() => new Set(syncModeOptions.map((item) => item.value)),
		[syncModeOptions]
	);
	const fallbackSyncMode = useMemo<SyncModeValue>(() => {
		const fromContract = normalizeSyncModeValue(
			activeConnectorCapability?.constraints
				? String((activeConnectorCapability.constraints as any).fallbackSyncMode || "")
				: ""
		);
		if (fromContract && supportedSyncModes.has(fromContract)) {
			return fromContract;
		}
		if (supportedSyncModes.has("full_refresh")) {
			return "full_refresh";
		}
		return syncModeOptions[0]?.value || "full_refresh";
	}, [activeConnectorCapability, supportedSyncModes, syncModeOptions]);
	const supportsIncremental = useMemo(() => {
		if (isFileFlow) return false;
		return supportedSyncModes.has("incremental");
	}, [supportedSyncModes, isFileFlow]);
	const supportsCdc = useMemo(() => supportedSyncModes.has("cdc"), [supportedSyncModes]);
	const supportsBackfill = useMemo(() => supportedSyncModes.has("backfill"), [supportedSyncModes]);
	const dbStepItems = useMemo(
		() => [
			{ key: "basic", title: "基础信息" },
			{ key: "reader", title: "源端配置" },
			{ key: "writer", title: "目标配置" },
			{ key: "review", title: "预览与执行" },
		],
		[]
	);
	const fileStepItems = useMemo(
		() => [
			{ key: "basic", title: "基础信息" },
			{ key: "writer", title: "目标配置" },
			{ key: "review", title: "预览与执行" },
		],
		[]
	);
	const stepItems = isFileFlow ? fileStepItems : dbStepItems;

	useEffect(() => {
		setCurrentStep(0);
	}, [sourceCategory]);

	useEffect(() => {
		if (!isFileFlow) return;
		const currentMode = normalizeSyncModeValue(syncMode) || "full_refresh";
		if (!supportedSyncModes.has(currentMode)) {
			form.setFieldValue("syncMode", fallbackSyncMode);
		}
	}, [isFileFlow, syncMode, form, supportedSyncModes, fallbackSyncMode]);

	useEffect(() => {
		const currentMode = normalizeSyncModeValue(syncMode) || "full_refresh";
		if (!supportedSyncModes.has(currentMode)) {
			form.setFieldValue("syncMode", fallbackSyncMode);
			toast.warning(`当前连接器不支持 ${currentMode}，已切换为 ${SYNC_MODE_LABELS[fallbackSyncMode]}`);
		}
	}, [form, supportedSyncModes, syncMode, fallbackSyncMode]);

	// Load draft on mount
	useEffect(() => {
		if (isEdit) {
			return;
		}
		const draft = loadDraft();
		if (draft) {
			setHasDraft(true);
			const { savedAt, ...formValues } = draft;
			form.setFieldsValue(formValues);
			if (formValues.sourceCategory) {
				setSourceCategory(formValues.sourceCategory);
			}
			toast.info(`已恢复草稿 (${new Date(savedAt).toLocaleString()})`);
		}
	}, [form, isEdit]);

	useEffect(() => {
		if (!isEdit || !editId) {
			return;
		}
		const loadTask = async () => {
			try {
				setLoadingTask(true);
				const task = await ingestionTaskAPI.getTask(editId);
				setEditingTask(task);
				form.setFieldsValue(mapTaskToForm(task));
				const fileMeta = extractFileUploadResult(task);
				if (fileMeta) {
					setFileUploadResult(fileMeta);
					form.setFieldValue("sourceCategory", "file");
					setSourceCategory("file");
					form.setFieldValue("readerType", "txtfilereader");
				}
				// Restore extra columns from destinationConfig
				const destConfig = tryParseJson(task.destinationConfig) || {};
				if (Array.isArray(destConfig._extraColumns) && destConfig._extraColumns.length) {
					setExtraColumns(destConfig._extraColumns);
				}
				const mappingTables = extractMappingTables(task.tableMapping);
				if (mappingTables.length) {
					setSelectedTableKeys(mappingTables);
					form.setFieldValue("selectedTables", mappingTables.join("\n"));
					syncSelectedTablesToForm(mappingTables, { silent: true });
				}
			} catch (error: any) {
				toast.error(`加载任务失败: ${error?.message || "未知错误"}`);
			} finally {
				setLoadingTask(false);
			}
		};
		loadTask();
	}, [editId, form, isEdit]);

	const applyTemplate = () => {
		const template = taskTemplates.find((item) => String(item.id) === String(selectedTemplateId));
		if (!template) {
			toast.warning("请选择模板");
			return;
		}
		const defaults = (template.defaults || {}) as Record<string, any>;
		form.setFieldsValue(defaults);
		const sourceCategoryFromTemplate =
			normalizeText(defaults.sourceCategory || template.sourceCategory).toLowerCase() || "database";
		if (sourceCategoryFromTemplate === "file" || sourceCategoryFromTemplate === "database") {
			setSourceCategory(sourceCategoryFromTemplate);
		}
		if (Array.isArray(template.warnings) && template.warnings.length) {
			toast.info(template.warnings[0]);
		}
		toast.success(`已应用模板：${template.name}`);
	};

	const handleSaveDraft = async () => {
		if (isEdit) {
			toast.info("编辑模式不支持保存草稿");
			return;
		}
		let values: Record<string, any> = {};
		try {
			setSavingDraft(true);
			values = form.getFieldsValue(true) ?? {};
			let name = resolveTaskName(values, form);
			if (!name && selectedDataSource) {
				name = normalizeText(selectedDataSource.name);
			}
			if (!name) {
				throw new Error("请填写任务名称");
			}
			const isFileDraft = values.sourceCategory === "file";
			const sourceDataSourceId = normalizeText(values.sourceDataSourceId);
			if (!isFileDraft && !sourceDataSourceId) {
				throw new Error("请选择数据源连接");
			}
			let resolvedReaderType = normalizeReaderType(values.readerType);
			if (isFileDraft && fileUploadResult) {
				resolvedReaderType = "txtfilereader";
			} else {
				if (!resolvedReaderType) {
					resolvedReaderType = resolveReaderTypeFromValues(values);
				}
				if (!resolvedReaderType) {
					resolvedReaderType = resolveReaderTypeFromSourceId(sourceDataSourceId, dataSources, selectedDataSource);
				}
				if (!resolvedReaderType && sourceDataSourceId) {
					resolvedReaderType = GENERIC_JDBC_READER;
				}
			}
			if (resolvedReaderType) {
				values.readerType = resolvedReaderType;
			}
			const isJsonMode = values.editorMode === "json";
			const safeParse = (raw: string, label: string) => {
				if (!normalizeText(raw)) return undefined;
				try {
					return parseJson(raw, label) as Record<string, any>;
				} catch {
					toast.warning(`${label} 格式不完整，已忽略该部分内容`);
					return undefined;
				}
			};
			let readerConfig: Record<string, any> | undefined;
			let writerConfig: Record<string, any> | undefined;
			if (isFileDraft && fileUploadResult) {
				readerConfig = {
					_filePath: fileUploadResult.hostPath,
					_containerPath: fileUploadResult.containerPath,
					_fileType: "csv",
					_fileColumns: fileUploadResult.columns,
					_originalName: fileUploadResult.originalName,
					_autoId: Boolean(values?.fileAutoId ?? true),
				};
			} else if (isJsonMode) {
				readerConfig = safeParse(values.readerConfig, "Reader 配置");
				writerConfig = safeParse(values.writerConfig, "Writer 配置");
			} else {
				try {
					readerConfig = buildReaderConfig(values);
				} catch {
					toast.warning("Reader 配置尚未完整，已忽略该部分内容");
				}
				try {
					writerConfig = buildWriterConfig(values);
				} catch {
					toast.warning("Writer 配置尚未完整，已忽略该部分内容");
				}
			}
			readerConfig = applyReaderTypeToConfig(readerConfig, resolvedReaderType);
			const jobConfig = safeParse(values.jobConfig, "作业参数");
			const selectedTables = mergeTableSelections(
				values.selectedTables,
				selectedTableKeysRef.current,
				resolveSelectedTables(values)
			);
			let selectionMode = normalizeText(values.tableSelectionMode) || "all";
			if (selectedTables.length) {
				selectionMode = "manual";
			}
			const includeTables =
				selectionMode === "manual"
					? mergeTableSelections(selectedTables, selectedTableKeysRef.current)
					: [];
			const excludeTables = selectionMode === "all" ? splitLines(values.tableExclude) : [];
			if (selectionMode === "manual" && includeTables.length) {
				readerConfig = applyTablesToConfig(readerConfig, includeTables);
				if (shouldApplyWriterTables(writerConfig, values)) {
					writerConfig = applyTablesToConfig(writerConfig, includeTables);
				}
			}
				const draftPayload: Record<string, any> = {
				draft: true,
				name,
				description: normalizeText(values.description) || undefined,
				owner: userInfo?.username || userInfo?.login,
				source: {
					dataSourceId: isFileDraft ? undefined : sourceDataSourceId,
					type: resolvedReaderType || undefined,
					config: readerConfig || {},
				},
					sync: {
						mode: normalizeText(values.syncMode) || "full_refresh",
						schedule: buildSyncScheduleSpec(values),
						prefix: normalizeText(values.syncPrefix) || undefined,
						incrementalColumn: normalizeText(values.incrementalColumn) || undefined,
						incrementalType: normalizeText(values.incrementalType) || undefined,
						initialWatermark: normalizeText(values.initialWatermark) || undefined,
						...buildGovernanceSyncFields(values),
					},
				streams: {
					selection: selectionMode,
					include: selectionMode === "manual" && includeTables.length ? includeTables : undefined,
					exclude: selectionMode === "all" && excludeTables.length ? excludeTables : undefined,
					schema: normalizeText(values.readerSchema) || undefined,
					tablePattern: normalizeText(values.readerTablePattern) || undefined,
				},
				airflow: {
					enabled: values.airflowEnabled ?? true,
				},
				dbt: {
					modelSelector: normalizeText(values.dbtModelSelector) || undefined,
					dagSelector: normalizeText(values.dbtDagSelector) || undefined,
				},
				jobConfig: jobConfig || undefined,
			};
			if (writerConfig && Object.keys(writerConfig).length > 0) {
				draftPayload.destination = {
					usePlatformDefault: true,
					config: writerConfig,
				};
			}
			const draftResult: any = await createIngestionTask(draftPayload);
			const taskId =
				draftResult?.task?.id ?? draftResult?.taskId ?? draftResult?.task?.taskId ?? undefined;
			clearDraft();
			setHasDraft(false);
			toast.success("草稿已保存");
			if (taskId) {
				router.push(`/explore/etl/transform/${taskId}/edit`);
			}
		} catch (err: any) {
			if (values && saveDraft(values)) {
				setHasDraft(true);
			}
			toast.error(err?.message || "保存草稿失败");
		} finally {
			setSavingDraft(false);
		}
	};

	const buildFileUploadResult = (
		fileName: string,
		batchCode: string,
		fileId: string,
		sheets: Array<{ index: number; name: string }> | undefined,
		parseResult: {
			csvPath: string;
			csvContainerPath: string;
			errorPath?: string;
			errorContainerPath?: string;
			delimiter?: string;
			columns: Array<{ name: string; dataType?: string; label?: string }>;
			preview?: string[][];
			rowCount?: number;
			errorCount?: number;
			sheetName?: string;
		},
		selectedSheet?: { index?: number; name?: string }
	): FileUploadResult => {
		const sourceFileType = resolveFileTypeFromName(fileName);
		const columns = (parseResult.columns || [])
			.map((col) => ({
				name: normalizeText(col.name),
				type: normalizeText(col.dataType) || "string",
				label: normalizeText(col.label),
			}))
			.filter((col) => col.name);
		return {
			hostPath: parseResult.csvPath,
			containerPath: parseResult.csvContainerPath,
			fileType: "csv",
			sourceFileType,
			columns,
			originalName: fileName,
			fileId,
			batchCode,
			sheets,
			sheetName: parseResult.sheetName || selectedSheet?.name,
			sheetIndex: selectedSheet?.index,
			csvPath: parseResult.csvPath,
			csvContainerPath: parseResult.csvContainerPath,
			errorPath: parseResult.errorPath,
			errorContainerPath: parseResult.errorContainerPath,
			delimiter: parseResult.delimiter,
			preview: parseResult.preview,
			rowCount: parseResult.rowCount,
			errorCount: parseResult.errorCount,
		};
	};

	const ensureFileTableName = (parsed: FileUploadResult) => {
		const current = normalizeText(form.getFieldValue("fileTableName"));
		if (current) return;
		const prefix = normalizeText(form.getFieldValue("syncPrefix"));
		const baseName = buildFileBaseName(parsed.originalName);
		const suggested = normalizeTableName(`${prefix}${baseName}`) || `${prefix}${baseName}`;
		if (suggested) {
			form.setFieldValue("fileTableName", suggested);
		}
	};

	const parseFile = async (
		fileId: string,
		fileName: string,
		batchCode: string,
		sheets: Array<{ index: number; name: string }> | undefined,
		selectedSheet?: { index?: number; name?: string },
		previewLimit: number = filePreviewRows
	) => {
		const sheetIndex = selectedSheet?.index;
		const sheetName = selectedSheet?.name;
		const parseResult = await dataSourcesService.excelParse({
			fileId,
			sheetIndex,
			sheetName,
			headerRow: 1,
			dataStartRow: 2,
			previewLimit: previewLimit || 20,
			delimiter: ",",
			skipErrors: true,
			fillMerged: true,
			dateFormat: "yyyy-MM-dd HH:mm:ss",
		});
		return buildFileUploadResult(fileName, batchCode, fileId, sheets, parseResult, selectedSheet);
	};

	const refreshFilePreview = async () => {
		if (!fileUploadResult?.fileId) {
			return;
		}
		try {
			setPreviewRefreshing(true);
			const selectedSheet = fileUploadResult.sheetName
				? { name: fileUploadResult.sheetName, index: fileUploadResult.sheetIndex }
				: undefined;
			const parsed = await parseFile(
				fileUploadResult.fileId,
				fileUploadResult.originalName,
				fileUploadResult.batchCode || "",
				fileUploadResult.sheets,
				selectedSheet,
				filePreviewRows
			);
			setFileUploadResult(parsed);
			toast.success("预览已刷新");
		} catch (err: any) {
			toast.error(err?.message || "刷新预览失败");
		} finally {
			setPreviewRefreshing(false);
		}
	};

	const openErrorPreview = async () => {
		if (!fileUploadResult?.fileId) {
			toast.error("缺少文件标识，无法查看错误行");
			return;
		}
		try {
			setErrorPreviewLoading(true);
			const resp = await dataSourcesService.excelErrors({
				fileId: fileUploadResult.fileId,
				limit: errorPreviewLimit,
			});
			setErrorPreviewRows(resp.rows || []);
			setErrorPreviewOpen(true);
		} catch (err: any) {
			toast.error(err?.message || "获取错误行失败");
		} finally {
			setErrorPreviewLoading(false);
		}
	};

	const handleDiscoverTables = async () => {
		try {
			setDiscoverError("");
			setDiscoveringTables(true);
			const values = form.getFieldsValue(true);
			const dataSourceId = normalizeText(values.sourceDataSourceId);
			if (!dataSourceId) {
				throw new Error("请先选择数据源连接");
			}
			if (!isJdbcSource(selectedDataSource)) {
				throw new Error("当前数据源不支持表发现");
			}
			const schema = normalizeText(values.readerSchema);
			const tablePattern = normalizeText(values.readerTablePattern);
			const rawTables = await ingestionTaskAPI.discoverTables({
				source: {
					dataSourceId,
				},
				filter: {
					schema: schema || undefined,
					tablePattern: tablePattern || undefined,
					limit: 0,
				},
			});
			const tables = Array.isArray(rawTables) ? rawTables.map((item) => normalizeDiscoveredTable(item)) : [];
			setDiscoveredTables(tables);
			setSelectedTableKeys([]);
			form.setFieldValue("selectedTables", "");
			if (tables.length === 0) {
				setDiscoverError("未发现可用表");
			}
		} catch (err: any) {
			setDiscoverError(err?.message || "获取表清单失败");
		} finally {
			setDiscoveringTables(false);
		}
	};

	const handleApplyTables = () => {
		if (!selectedTableKeys.length) {
			toast.error("请先选择表");
			return;
		}
		syncSelectedTablesToForm(selectedTableKeys);
	};

	const readerTablesValidator = (_: any, value: string) => {
		const mode = normalizeText(form.getFieldValue("tableSelectionMode")) || "all";
		if (mode === "all") {
			return Promise.resolve();
		}
		if (resolveSelectedTables().length) {
			return Promise.resolve();
		}
		const tables = splitLines(value);
		if (tables.length) {
			return Promise.resolve();
		}
		return Promise.reject(new Error("请输入表名"));
	};

	const writerTablesValidator = (_: any, value: string) => {
		const mode = normalizeText(form.getFieldValue("tableSelectionMode")) || "all";
		if (mode === "all") {
			return Promise.resolve();
		}
		if (resolveSelectedTables().length) {
			return Promise.resolve();
		}
		const tables = splitLines(value);
		if (tables.length) {
			return Promise.resolve();
		}
		return Promise.reject(new Error("请填写目标表名"));
	};

	const fileTableNameValidator = (_: any, value: string) => {
		const text = normalizeText(value);
		if (!text) {
			return Promise.resolve();
		}
		const normalized = normalizeTableName(text);
		if (!normalized) {
			return Promise.reject(new Error("目标表名仅支持字母、数字、下划线，可包含 schema"));
		}
		return Promise.resolve();
	};

	const readerTypeValidator = (_: any, value: string) => {
		const direct = normalizeText(value);
		if (direct) {
			return Promise.resolve();
		}
		const dataSourceId = normalizeText(form.getFieldValue("sourceDataSourceId"));
		if (!dataSourceId) {
			return Promise.reject(new Error("请选择数据源连接"));
		}
		const inferred = resolveReaderTypeFromDataSource(selectedDataSource);
		if (inferred) {
			form.setFieldValue("readerType", inferred);
			return Promise.resolve();
		}
		if (!selectedDataSource && dataSourceId) {
			form.setFieldValue("readerType", GENERIC_JDBC_READER);
			return Promise.resolve();
		}
		if (selectedDataSource && isJdbcSource(selectedDataSource)) {
			form.setFieldValue("readerType", GENERIC_JDBC_READER);
			return Promise.resolve();
		}
		form.setFieldValue("readerType", GENERIC_JDBC_READER);
		return Promise.resolve();
	};

	const resolveStepFields = (stepIndex: number, values: Record<string, any>) => {
		const isJsonMode = values?.editorMode === "json";
		if (values?.sourceCategory === "file") {
			switch (stepIndex) {
				case 0:
					return ["name"];
				case 1:
					return ["writerType", "fileTableName"];
				case 2:
					return ["airflowEnabled", "runNow"];
				default:
					return [];
			}
		}
		switch (stepIndex) {
			case 0:
				return ["editorMode", "name", "description", "sourceSystem"];
			case 1:
				return isJsonMode
					? ["sourceDataSourceId", "readerType", "readerConfig"]
					: ["sourceDataSourceId", "readerType", "readerTables"];
			case 2:
				return isJsonMode
					? ["writerType", "writerConfig"]
					: ["writerType", "writerTables"];
			case 3:
				return ["jobConfig", "airflowEnabled", "runNow"];
			default:
				return [];
		}
	};

	const validateStep = async (stepIndex: number) => {
		const values = form.getFieldsValue(true);
		const fields = resolveStepFields(stepIndex, values);
		if (!fields.length) return true;
		try {
			await form.validateFields(fields);
			return true;
		} catch {
			return false;
		}
	};

	const handleStepChange = async (nextStep: number) => {
		if (nextStep <= currentStep) {
			setCurrentStep(nextStep);
			return;
		}
		const ok = await validateStep(currentStep);
		if (!ok) {
			toast.error("请先完成当前步骤必填项");
			return;
		}
		setCurrentStep(nextStep);
	};

	const handleNextStep = async () => {
		if (currentStep >= stepItems.length - 1) return;
		const vals = form.getFieldsValue(true);
		const fileFlow = vals.sourceCategory === "file";
		if (fileFlow) {
			if (currentStep === 0) {
				if (!normalizeText(vals.name)) {
					toast.error("请输入任务名称");
					return;
				}
				if (!fileUploadResult) {
					toast.error("请先上传文件");
					return;
				}
			}
			setCurrentStep((prev) => prev + 1);
			return;
		}
		const ok = await validateStep(currentStep);
		if (!ok) {
			toast.error("请先完成当前步骤必填项");
			return;
		}
		setCurrentStep((prev) => prev + 1);
	};

	const handlePrevStep = () => {
		setCurrentStep((prev) => Math.max(prev - 1, 0));
	};

	const handleSubmit = async (values: any) => {
		try {
			setSaving(true);
			const mergedValues = { ...form.getFieldsValue(true), ...(values || {}) };
			let taskName = resolveTaskName(mergedValues, form);
			if (!taskName && selectedDataSource) {
				taskName = normalizeText(selectedDataSource.name);
			}
			if (!taskName) {
				throw new Error("请填写任务名称");
			}
			mergedValues.name = taskName;
			const isFileSource = mergedValues.sourceCategory === "file" && fileUploadResult;
			const sourceDataSourceId = normalizeText(mergedValues.sourceDataSourceId);
			if (!isFileSource && !sourceDataSourceId) {
				throw new Error("请选择数据源连接");
			}
			if (isFileSource && !fileUploadResult) {
				throw new Error("请先上传文件");
			}
			const syncConfig = buildSyncConfigFromValues(mergedValues, Boolean(isFileSource));
			let resolvedReaderType = normalizeReaderType(mergedValues.readerType);
			if (isFileSource) {
				resolvedReaderType = "txtfilereader";
			} else {
				if (!resolvedReaderType) {
					resolvedReaderType = resolveReaderTypeFromValues(mergedValues);
				}
				if (!resolvedReaderType) {
					resolvedReaderType = resolveReaderTypeFromSourceId(sourceDataSourceId, dataSources, selectedDataSource);
				}
				if (!resolvedReaderType && sourceDataSourceId) {
					resolvedReaderType = GENERIC_JDBC_READER;
				}
			}
			if (resolvedReaderType) {
				mergedValues.readerType = resolvedReaderType;
			}
			const airflowEnabled = mergedValues.airflowEnabled ?? editingTask?.airflowEnabled ?? true;
			const isJsonMode = mergedValues.editorMode === "json";
			let readerConfig: Record<string, any>;
			if (isFileSource) {
				readerConfig = {
					_filePath: fileUploadResult.hostPath,
					_containerPath: fileUploadResult.containerPath,
					_fileType: "csv",
					_fileColumns: fileUploadResult.columns,
					_originalName: fileUploadResult.originalName,
					_autoId: Boolean(mergedValues?.fileAutoId ?? true),
				};
			} else {
				readerConfig = (isJsonMode
					? parseJson(mergedValues.readerConfig, "Reader 配置")
					: buildReaderConfig(mergedValues)) as Record<string, any>;
				applyReaderTypeToConfig(readerConfig, resolvedReaderType);
				if (hasConnectionOverride(readerConfig)) {
					throw new Error("入湖任务必须使用已配置的数据源连接，Reader 配置中不可包含连接信息");
				}
			}
			if (!defaultDestinationStatus?.available) {
				throw new Error("默认数据湖未配置，请先在管理端设置默认数据湖");
			}
			if (!defaultDestinationStatus.writerTypeReady) {
				throw new Error("默认数据湖未配置写入器类型");
			}
			if (!defaultDestinationStatus.writerConfigReady) {
				throw new Error("默认数据湖未配置写入器参数");
			}
			const defaultWriterType = normalizeText(defaultDestinationStatus.writerType);
			if (!defaultWriterType) {
				throw new Error("默认数据湖写入器类型不可用");
			}
			const sourceSystem = normalizeText(mergedValues.sourceSystem);
			if (sourceSystem && readerConfig && typeof readerConfig === "object" && !readerConfig.sourceSystem) {
				readerConfig.sourceSystem = sourceSystem;
			}
			if (isFileSource) {
				const baseName = buildFileBaseName(fileUploadResult.originalName);
				const fileTableInput = normalizeText(mergedValues.fileTableName);
				const requestedFileTable = normalizeTableName(fileTableInput);
				if (fileTableInput && !requestedFileTable) {
					throw new Error("目标表名格式不合法，仅支持字母、数字、下划线，可包含 schema");
				}
				const autoTableName = normalizeTableName(`${normalizeText(mergedValues.syncPrefix)}${baseName}`);
				const fileTableName = requestedFileTable || autoTableName || `${normalizeText(mergedValues.syncPrefix)}${baseName}`;
				if (!fileTableName) {
					throw new Error("请填写目标表名");
				}
				const fileIncludeTables = [fileTableName];
				const writerConfig: Record<string, any> = {};
				const fileWriterUsername = normalizeText(mergedValues.writerUsername);
				const fileWriterPassword = normalizeText(mergedValues.writerPassword);
				const fileWriterSchema = normalizeText(mergedValues.writerSchema);
				const fileWriterJdbc = normalizeText(mergedValues.writerJdbcUrls);
				if (fileWriterUsername) writerConfig.username = fileWriterUsername;
				if (fileWriterPassword) writerConfig.password = fileWriterPassword;
				if (fileWriterSchema) writerConfig.schema = fileWriterSchema;
				const fileConn: Record<string, any> = { table: [fileTableName] };
				if (fileWriterJdbc) {
					fileConn.jdbcUrl = splitLines(fileWriterJdbc);
				}
				writerConfig.connection = [fileConn];
				// Inject column rules into file writer config
				const fileColumnPrefix = normalizeText(mergedValues.columnPrefix);
				const fileColumnSuffix = normalizeText(mergedValues.columnSuffix);
				const fileExtraCols = extraColumns.filter((c) => normalizeText(c.name));
				if (fileColumnPrefix) writerConfig._columnPrefix = fileColumnPrefix;
				if (fileColumnSuffix) writerConfig._columnSuffix = fileColumnSuffix;
				if (fileExtraCols.length) writerConfig._extraColumns = fileExtraCols;
				const jobConfig = parseJson(mergedValues.jobConfig, "作业参数");
				const modelSelector = normalizeText(mergedValues.dbtModelSelector) || buildModelSelectorFromNames(mergedValues.dbtModels || []);
				const dagSelector = normalizeText(mergedValues.dbtDagSelector);
				if (isEdit && editId) {
					const updatePayload: IngestionTaskDTO = {
						...(editingTask || {}),
						id: editId,
						name: taskName,
						description: normalizeText(mergedValues.description) || undefined,
						sourceType: resolvedReaderType,
						sourceConfig: readerConfig,
						destinationType: defaultWriterType,
						destinationConfig: writerConfig,
						syncMode: "full_refresh",
						syncSchedule: buildSyncScheduleText(mergedValues),
						syncConfig: (syncConfig ?? null) as any,
						syncPrefix: normalizeText(mergedValues.syncPrefix) || undefined,
						addaxConfig: (jobConfig as Record<string, any>) || editingTask?.addaxConfig,
						airflowEnabled: Boolean(airflowEnabled),
						tableMapping: [{ source: baseName, target: fileTableName }],
						dbtModelSelector: modelSelector || undefined,
						dbtDagSelector: dagSelector || undefined,
					};
					await ingestionTaskAPI.updateTask(editId, updatePayload);
					toast.success("入湖任务已更新");
					router.push(`/explore/etl/transform/${editId}`);
				} else {
					const payload = {
						taskName,
						name: taskName,
						description: normalizeText(mergedValues.description) || undefined,
						owner: userInfo?.username || userInfo?.login,
						source: {
							type: resolvedReaderType,
							config: readerConfig,
						},
						destination: {
							usePlatformDefault: true,
							type: defaultWriterType,
							config: writerConfig,
						},
							sync: {
								mode: "full_refresh",
								schedule: buildSyncScheduleSpec(mergedValues),
								prefix: normalizeText(mergedValues.syncPrefix) || undefined,
								...buildGovernanceSyncFields(mergedValues),
							},
						streams: {
							selection: "manual",
							include: fileIncludeTables,
						},
						airflow: { enabled: airflowEnabled },
						dbt: {
							modelSelector: modelSelector || undefined,
							dagSelector: dagSelector || undefined,
						},
						runNow: Boolean(mergedValues.runNow),
						jobConfig: jobConfig || undefined,
					};
					const createResult = await createIngestionTask(payload);
					handleCreateTaskResult(createResult, Boolean(payload.runNow), taskName);
				}
				return;
			}
			const selectedTables = mergeTableSelections(
				mergedValues.selectedTables,
				selectedTableKeysRef.current,
				resolveSelectedTables(mergedValues)
			);
			let selectionMode = normalizeText(mergedValues.tableSelectionMode) || "all";
			if (selectedTables.length || selectedTableKeysRef.current.length) {
				selectionMode = "manual";
				mergedValues.tableSelectionMode = "manual";
			}
			let writerConfig = isJsonMode
				? parseJson(mergedValues.writerConfig, "Writer 配置")
				: buildWriterConfig(mergedValues);
			// Inject column rules into writer config
			const columnPrefix = normalizeText(mergedValues.columnPrefix);
			const columnSuffix = normalizeText(mergedValues.columnSuffix);
			const validExtraCols = extraColumns.filter((c) => normalizeText(c.name));
			if (columnPrefix || columnSuffix || validExtraCols.length) {
				if (!writerConfig || typeof writerConfig !== "object") writerConfig = {};
				if (columnPrefix) (writerConfig as Record<string, any>)._columnPrefix = columnPrefix;
				if (columnSuffix) (writerConfig as Record<string, any>)._columnSuffix = columnSuffix;
				if (validExtraCols.length) (writerConfig as Record<string, any>)._extraColumns = validExtraCols;
			}
			const inferredManualTables = mergeTableSelections(
				selectedTables,
				extractReaderTables(readerConfig),
				extractWriterTables(writerConfig),
				mergedValues.readerTables,
				mergedValues.writerTables
			);
			if (selectionMode === "manual" && !selectedTables.length && inferredManualTables.length) {
				mergedValues.selectedTables = inferredManualTables.join("\n");
			}
			if (selectionMode === "all" && inferredManualTables.length) {
				selectionMode = "manual";
				mergedValues.tableSelectionMode = "manual";
			}
			let includeTables: string[] = [];
			if (selectionMode === "manual") {
				includeTables = mergeTableSelections(selectedTables, selectedTableKeysRef.current, inferredManualTables);
				if (!includeTables.length) {
					includeTables = mergeTableSelections(selectedTableKeysRef.current, selectedTableKeys);
				}
				if (!includeTables.length) {
					if (isJsonMode) {
						includeTables = extractReaderTables(readerConfig);
					} else {
						includeTables = splitLines(mergedValues.readerTables);
					}
				}
				if (!includeTables.length) {
					includeTables = extractWriterTables(writerConfig);
				}
				if (!includeTables.length && !isJsonMode) {
					includeTables = splitLines(mergedValues.writerTables);
				}
				if (!includeTables.length) {
					throw new Error("请选择需要入湖的表");
				}
					readerConfig = applyTablesToConfig((readerConfig as Record<string, any>) ?? {}, includeTables) ?? {};
					if (shouldApplyWriterTables((writerConfig as Record<string, any>) ?? {}, mergedValues)) {
						const explicitWriterTables = splitLines(mergedValues.writerTables);
						const normalizedExplicitWriterTables =
							includeTables.length && isSourceAlignedTables(includeTables, explicitWriterTables)
								? applyPrefixToTables(includeTables, mergedValues.syncPrefix)
								: explicitWriterTables;
						const existingWriterTables = extractWriterTables(writerConfig as Record<string, any>);
						const derivedWriterTables = normalizedExplicitWriterTables.length
							? normalizedExplicitWriterTables
							: (!existingWriterTables.length || isSourceAlignedTables(includeTables, existingWriterTables))
								? applyPrefixToTables(includeTables, mergedValues.syncPrefix)
								: existingWriterTables;
						writerConfig = applyTablesToConfig((writerConfig as Record<string, any>) ?? {}, derivedWriterTables) ?? {};
					}
				}
			const excludeTables = selectionMode === "all" ? splitLines(mergedValues.tableExclude) : [];
			if (writerConfig && selectionMode !== "all" && !hasTableEntries(extractWriterTables(writerConfig))) {
				throw new Error("Writer 配置缺少目标表，请填写表清单");
			}
			const jobConfig = parseJson(mergedValues.jobConfig, "作业参数");
			const modelSelector = normalizeText(mergedValues.dbtModelSelector) || buildModelSelectorFromNames(mergedValues.dbtModels || []);
			const dagSelector = normalizeText(mergedValues.dbtDagSelector);
			if (modelSelector && !dagSelector) {
				toast.warning("未填写 DAG 族选择器，将使用默认 DAG 触发 dbt");
			}
			if (isEdit && editId) {
				const updatePayload: IngestionTaskDTO = {
					...(editingTask || {}),
					id: editId,
					name: taskName,
					description: normalizeText(mergedValues.description) || undefined,
					sourceType: normalizeText(resolvedReaderType) || "",
					sourceDataSourceId: sourceDataSourceId,
					sourceConfig: (readerConfig as Record<string, any>) || {},
						destinationType: defaultWriterType,
						destinationConfig: writerConfig as Record<string, any> | undefined,
						syncMode: mergedValues.syncMode || editingTask?.syncMode || "full_refresh",
						syncSchedule: buildSyncScheduleText(mergedValues),
						syncConfig: (syncConfig ?? null) as any,
						syncPrefix: normalizeText(mergedValues.syncPrefix) || undefined,
					addaxConfig: (jobConfig as Record<string, any>) || editingTask?.addaxConfig,
					airflowEnabled: Boolean(mergedValues.airflowEnabled),
					dbtModelSelector: modelSelector || undefined,
					dbtDagSelector: dagSelector || undefined,
				};
				updatePayload.airflowEnabled = airflowEnabled;
				await ingestionTaskAPI.updateTask(editId, updatePayload);
				toast.success("入湖任务已更新");
				router.push(`/explore/etl/transform/${editId}`);
			} else {
				const payload = {
					taskName: taskName,
					name: taskName,
					description: normalizeText(mergedValues.description) || undefined,
					owner: userInfo?.username || userInfo?.login,
					source: {
						dataSourceId: isFileSource ? undefined : sourceDataSourceId,
						type: normalizeText(resolvedReaderType) || undefined,
						config: readerConfig || {},
					},
					destination: {
						usePlatformDefault: true,
						type: defaultWriterType,
						config: writerConfig || undefined,
					},
						sync: {
							mode: mergedValues.syncMode || "full_refresh",
							schedule: buildSyncScheduleSpec(mergedValues),
							prefix: normalizeText(mergedValues.syncPrefix) || undefined,
							incrementalColumn: syncConfig?.incrementalColumn,
							incrementalType: syncConfig?.incrementalType,
							initialWatermark: syncConfig?.initialWatermark,
							...buildGovernanceSyncFields(mergedValues),
						},
					streams: {
						selection: selectionMode,
						include: selectionMode === "manual" ? includeTables : undefined,
						exclude: selectionMode === "all" ? excludeTables : undefined,
						schema: normalizeText(mergedValues.readerSchema) || undefined,
						tablePattern: normalizeText(mergedValues.readerTablePattern) || undefined,
					},
					airflow: {
						enabled: airflowEnabled,
					},
					dbt: {
						modelSelector: modelSelector || undefined,
						dagSelector: dagSelector || undefined,
					},
					runNow: Boolean(mergedValues.runNow),
					jobConfig: jobConfig || undefined,
				};
				const createResult = await createIngestionTask(payload);
				handleCreateTaskResult(createResult, Boolean(payload.runNow), taskName);
			}
		} catch (err: any) {
			toast.error(err?.message || (isEdit ? "更新入湖任务失败" : "创建入湖任务失败"));
		} finally {
			setSaving(false);
		}
	};

	const previewState = useMemo(() => {
		const snapshot = form.getFieldsValue(true);
		const mergedValues = { ...snapshot, ...(formValues || {}) } as Record<string, any>;
		const isFilePreview = mergedValues.sourceCategory === "file" && fileUploadResult;
		if (isFilePreview) {
			const fileTableName = normalizeTableName(mergedValues.fileTableName) ||
				normalizeTableName(`${normalizeText(mergedValues.syncPrefix)}${buildFileBaseName(fileUploadResult.originalName)}`);
			if (fileTableName) {
				mergedValues.writerTables = fileTableName;
			}
			mergedValues.readerType = "txtfilereader";
			mergedValues.readerConfig = JSON.stringify(
				{
					_filePath: fileUploadResult.hostPath,
					_containerPath: fileUploadResult.containerPath,
					_fileType: "csv",
					_fileColumns: fileUploadResult.columns,
					_originalName: fileUploadResult.originalName,
					_autoId: Boolean(mergedValues?.fileAutoId ?? true),
				},
				null,
				2
			);
		}
		if (!normalizeText(mergedValues.readerType) && selectedDataSource) {
			const inferredReader = resolveReaderTypeFromDataSource(selectedDataSource);
			if (inferredReader) {
				mergedValues.readerType = inferredReader;
			}
		}
		if (!normalizeText(mergedValues.writerType) && defaultDestinationStatus?.writerType) {
			mergedValues.writerType = normalizeText(defaultDestinationStatus.writerType);
		}
		try {
			const readerFallback = resolveReaderTypeFromSourceId(
				mergedValues.sourceDataSourceId,
				dataSources,
				selectedDataSource
			);
			const config = buildJobPreview(mergedValues, isFilePreview ? "json" : editorMode, readerFallback);
			return { config, error: "" };
		} catch (error: any) {
			return { config: null, error: error?.message || "无法生成预览" };
		}
	}, [
		formValues,
		editorMode,
		form,
		currentStep,
		selectedDataSource,
		defaultDestinationStatus,
		fileUploadResult,
		dataSources,
	]);

	return (
		<div className="flex flex-col gap-6">
			<PageHeader
				title={isEdit ? "编辑入湖任务" : "创建入湖任务"}
				description="使用 Addax 生成作业配置，并由 Airflow 触发执行。"
				actions={
					<Space>
						<Button
							onClick={() => {
								if (isEdit && editingTask) {
									const mapped = mapTaskToForm(editingTask);
									form.setFieldsValue(mapped);
									setSourceCategory(mapped.sourceCategory || "database");
								} else {
									form.resetFields();
									setSourceCategory("database");
								}
								setCurrentStep(0);
							}}
						>
							重置表单
						</Button>
						<Button
							icon={<SaveOutlined />}
							loading={savingDraft}
							onClick={handleSaveDraft}
							disabled={isEdit}
						>
							保存草稿{hasDraft ? " ✓" : ""}
						</Button>
					</Space>
				}
			/>
			<Card>
				<Alert
					message="提示"
					description="入湖任务将使用管理端默认数据湖；请填写 Reader 配置与 Writer 覆盖参数（表名必填）。"
					type="info"
					showIcon
					className="mb-6"
				/>
				<Form
					form={form}
					layout="vertical"
					initialValues={initialValues}
					onFinish={handleSubmit}
					requiredMark
				>
					<Steps
						current={currentStep}
						items={stepItems}
						onChange={handleStepChange}
						className="mb-6"
					/>
					<Form.Item name="selectedTables" hidden>
						<Input type="hidden" />
					</Form.Item>
					<Form.Item name="writerType" hidden rules={[{ required: true, message: "请选择 Writer 类型" }]}>
						<Input type="hidden" />
					</Form.Item>
					{currentStep === 0 ? (
						<Card size="small" className="mb-4" title="快速模板">
							<Space wrap>
								<Select
									style={{ minWidth: 280 }}
									placeholder="选择模板"
									loading={loadingTaskTemplates}
									value={selectedTemplateId}
									options={taskTemplates.map((item) => ({
										label: `${item.name}${item.version ? ` (v${item.version})` : ""}`,
										value: item.id,
									}))}
									onChange={(value) => setSelectedTemplateId(value)}
								/>
								<Button onClick={applyTemplate} disabled={!selectedTemplateId}>
									应用模板
								</Button>
								{selectedTemplateId ? (
									<Text type="secondary">
										{taskTemplates.find((item) => String(item.id) === String(selectedTemplateId))?.description || ""}
									</Text>
								) : null}
							</Space>
						</Card>
					) : null}
					{isFileFlow ? (
						<>
							{/* ===== 文件上传模式 ===== */}
							{currentStep === 0 && (
								<>
									<Form.Item
										name="name"
										label="任务名称"
										rules={[{ required: true, message: "请输入任务名称" }]}
									>
										<Input placeholder="例如：csv-import-task" />
									</Form.Item>
									<Form.Item name="description" label="任务描述">
										<Input.TextArea rows={2} placeholder="可选，说明任务用途" />
									</Form.Item>
										<Form.Item name="syncMode" label="同步模式" tooltip="同步模式由连接器能力契约驱动">
											<Radio.Group>
												{syncModeOptions.map((item) => (
													<Radio.Button key={item.value} value={item.value} disabled={item.disabled}>
														{item.label}
													</Radio.Button>
												))}
											</Radio.Group>
										</Form.Item>
										<div className="grid gap-4 md:grid-cols-3">
											<Form.Item name="scheduleType" label="调度策略">
												<Select
													options={[
														{ label: "手动触发", value: "manual" },
														{ label: "按间隔执行", value: "interval" },
														{ label: "按 Cron 执行", value: "cron" },
													]}
												/>
											</Form.Item>
											{(normalizeText(scheduleType) || "manual") === "interval" ? (
												<Form.Item
													name="scheduleIntervalMinutes"
													label="执行间隔(分钟)"
													rules={[{ required: true, message: "请输入执行间隔" }]}
												>
													<InputNumber min={1} precision={0} className="w-full" />
												</Form.Item>
											) : null}
											{(normalizeText(scheduleType) || "manual") === "cron" ? (
												<Form.Item
													name="scheduleCron"
													label="Cron 表达式"
													rules={[{ validator: validateCronExpression }]}
												>
													<Input placeholder="例如：0 */30 * * * *" />
												</Form.Item>
											) : null}
										</div>
										<Collapse
											size="small"
											className="mb-4"
											items={[
												{
													key: "governance-file",
													label: "运行治理策略（可选）",
													children: (
														<div className="grid gap-4 md:grid-cols-3">
															<Form.Item name="taskConcurrency" label="任务并发上限">
																<InputNumber min={1} precision={0} className="w-full" placeholder="默认 1" />
															</Form.Item>
															<Form.Item name="sourceConcurrency" label="来源并发上限">
																<InputNumber min={0} precision={0} className="w-full" placeholder="0 表示不限" />
															</Form.Item>
															<Form.Item name="priority" label="队列优先级">
																<Select
																	allowClear
																	options={[
																		{ label: "HIGH", value: "HIGH" },
																		{ label: "MEDIUM", value: "MEDIUM" },
																		{ label: "LOW", value: "LOW" },
																	]}
																/>
															</Form.Item>
															<Form.Item name="rejectPolicy" label="限流策略">
																<Select
																	allowClear
																	options={[
																		{ label: "REJECT", value: "REJECT" },
																		{ label: "QUEUE", value: "QUEUE" },
																	]}
																/>
															</Form.Item>
															<Form.Item name="windowStart" label="执行窗口开始">
																<Input placeholder="HH:mm，例如 01:00" />
															</Form.Item>
															<Form.Item name="windowEnd" label="执行窗口结束">
																<Input placeholder="HH:mm，例如 06:00" />
															</Form.Item>
															<Form.Item name="windowTimezone" label="执行窗口时区">
																<Select
																	allowClear
																	options={[
																		{ label: "Asia/Shanghai", value: "Asia/Shanghai" },
																		{ label: "UTC", value: "UTC" },
																	]}
																/>
															</Form.Item>
														</div>
													),
												},
											]}
										/>
								<Space size={[8, 8]} wrap className="mb-3">
									<Text type="secondary">当前连接器能力：</Text>
									{["FULL", "INCREMENTAL", "CDC", "BACKFILL"].map((cap) => (
										<Tag key={cap} color={activeCapabilitySet.has(cap) ? "green" : "default"}>
											{cap}
										</Tag>
									))}
									{capabilityLoadFailed ? <Text type="warning">能力探测失败，已使用保守降级策略</Text> : null}
								</Space>
									<Form.Item name="sourceCategory" label="数据来源">
										<Radio.Group onChange={(e) => setSourceCategory(e.target.value)}>
											<Radio.Button value="database">数据库</Radio.Button>
											<Radio.Button value="file">文件上传</Radio.Button>
										</Radio.Group>
									</Form.Item>
									<Form.Item label="上传文件" required>
										<Upload.Dragger
											accept=".xlsx,.csv"
											maxCount={1}
											showUploadList={false}
											customRequest={async ({ file, onSuccess, onError }) => {
												try {
													setUploadingFile(true);
													setFileUploadResult(null);
													const prepare = await dataSourcesService.excelPrepare(file as File);
													const sheets = prepare.sheets || [];
													const defaultSheet = sheets.length ? sheets[0] : undefined;
													const parsed = await parseFile(
														prepare.fileId,
														prepare.fileName,
														prepare.batchCode,
														sheets,
														defaultSheet,
														filePreviewRows
													);
													setFileUploadResult(parsed);
													form.setFieldValue("readerType", "txtfilereader");
													ensureFileTableName(parsed);
													onSuccess?.(parsed);
													toast.success(`文件解析成功，检测到 ${parsed.columns?.length || 0} 列`);
												} catch (err: any) {
													onError?.(err);
													toast.error(err?.message || "文件上传失败");
												} finally {
													setUploadingFile(false);
												}
											}}
											disabled={uploadingFile}
										>
											<p className="ant-upload-drag-icon">
												<InboxOutlined />
											</p>
											<p className="ant-upload-text">
												{uploadingFile ? "上传中..." : "点击或拖拽上传 Excel / CSV 文件"}
											</p>
											<p className="ant-upload-hint">支持 .xlsx, .csv 格式</p>
										</Upload.Dragger>
									</Form.Item>
									{fileUploadResult && (
										<Card type="inner" title={`已解析文件: ${fileUploadResult.originalName}`} className="mb-4">
											<Text type="secondary" className="block mb-2">
												文件类型: <Tag>{fileUploadResult.sourceFileType || fileUploadResult.fileType}</Tag>
												检测到 {fileUploadResult.columns?.length || 0} 列
												{typeof fileUploadResult.rowCount === "number" && (
													<>
														{" · "}预览总行数: <Tag color="blue">{fileUploadResult.rowCount}</Tag>
													</>
												)}
												{typeof fileUploadResult.errorCount === "number" && (
													<>
														{" · "}错误行:{" "}
														<Tag color={fileUploadResult.errorCount > 0 ? "red" : "green"}>
															{fileUploadResult.errorCount}
														</Tag>
													</>
												)}
											</Text>
											{Array.isArray(fileUploadResult.sheets) && fileUploadResult.sheets.length > 1 && (
												<Space className="mb-3" wrap>
													<Text type="secondary">选择 Sheet：</Text>
													<Select
														style={{ minWidth: 200 }}
														value={fileUploadResult.sheetIndex}
														options={fileUploadResult.sheets.map((sheet) => ({
															label: sheet.name,
															value: sheet.index,
														}))}
														onChange={async (value) => {
															const targetSheet = fileUploadResult.sheets?.find((item) => item.index === value);
															if (!targetSheet || !fileUploadResult.fileId) return;
															try {
																setUploadingFile(true);
																const parsed = await parseFile(
																	fileUploadResult.fileId,
																	fileUploadResult.originalName,
																	fileUploadResult.batchCode || "",
																	fileUploadResult.sheets,
																	{ index: targetSheet.index, name: targetSheet.name },
																	filePreviewRows
																);
																setFileUploadResult(parsed);
																form.setFieldValue("readerType", "txtfilereader");
																ensureFileTableName(parsed);
																toast.success(`已切换到 ${targetSheet.name}，检测到 ${parsed.columns?.length || 0} 列`);
															} catch (err: any) {
																toast.error(err?.message || "解析 Sheet 失败");
															} finally {
																setUploadingFile(false);
															}
														}}
													/>
												</Space>
											)}
											<Space className="mb-3" wrap>
												<Text type="secondary">预览行数</Text>
												<InputNumber
													min={1}
													max={2000}
													value={filePreviewRows}
													onChange={(value) => setFilePreviewRows(value ? Number(value) : 20)}
												/>
												<Text type="secondary">预览列数</Text>
												<InputNumber
													min={1}
													max={50}
													value={filePreviewCols}
													onChange={(value) => setFilePreviewCols(value ? Number(value) : 8)}
												/>
												<Button size="small" onClick={refreshFilePreview} loading={previewRefreshing}>
													刷新预览
												</Button>
												{(fileUploadResult.errorCount || 0) > 0 && (
													<Button size="small" onClick={openErrorPreview} loading={errorPreviewLoading}>
														查看错误行
													</Button>
												)}
											</Space>
											<Table
												size="small"
												dataSource={fileUploadResult.columns || []}
												rowKey={(_: any, index: any) => String(index)}
												pagination={false}
												columns={[
													{
														title: "显示名称",
														dataIndex: "label",
														render: (value: string, _: any, index: number) => (
															<Input
																size="small"
																value={value || ""}
																placeholder="中文名/显示名"
																onChange={(e) => {
																	const cols = [...(fileUploadResult.columns || [])];
																	cols[index] = { ...cols[index], label: e.target.value };
																	setFileUploadResult({ ...fileUploadResult, columns: cols });
																}}
															/>
														),
													},
													{
														title: "字段名",
														dataIndex: "name",
														render: (value: string, _: any, index: number) => (
															<Input
																size="small"
																value={value}
																placeholder="英文字段名"
																onChange={(e) => {
																	const cols = [...(fileUploadResult.columns || [])];
																	cols[index] = { ...cols[index], name: e.target.value };
																	setFileUploadResult({ ...fileUploadResult, columns: cols });
																}}
															/>
														),
													},
													{
														title: "数据类型",
														dataIndex: "type",
														width: 170,
														render: (value: string, _: any, index: number) => (
															<Select
																size="small"
																value={value}
																style={{ width: "100%" }}
																onChange={(v) => {
																	const cols = [...(fileUploadResult.columns || [])];
																	const patch: Record<string, any> = { type: v };
																	if (v === "string" && !cols[index].length) patch.length = 500;
																	if (v === "numeric" && !cols[index].precision) { patch.precision = 18; patch.scale = 2; }
																	if (v !== "string") patch.length = undefined;
																	if (v !== "numeric") { patch.precision = undefined; patch.scale = undefined; }
																	cols[index] = { ...cols[index], ...patch };
																	setFileUploadResult({ ...fileUploadResult, columns: cols });
																}}
																options={[
																	{ label: "VARCHAR", value: "string" },
																	{ label: "TEXT", value: "text" },
																	{ label: "INTEGER", value: "integer" },
																	{ label: "BIGINT", value: "long" },
																	{ label: "NUMERIC", value: "numeric" },
																	{ label: "DOUBLE PRECISION", value: "double" },
																	{ label: "BOOLEAN", value: "boolean" },
																	{ label: "DATE", value: "date" },
																	{ label: "TIMESTAMP", value: "timestamp" },
																	{ label: "JSONB", value: "jsonb" },
																]}
															/>
														),
													},
													{
														title: "类型参数",
														dataIndex: "length",
														width: 180,
														render: (_: any, record: any, index: number) => {
															if (record.type === "string") {
																return (
																	<InputNumber
																		size="small"
																		min={1}
																		max={10485760}
																		value={record.length ?? 500}
																		addonBefore="长度"
																		style={{ width: "100%" }}
																		onChange={(v) => {
																			const cols = [...(fileUploadResult.columns || [])];
																			cols[index] = { ...cols[index], length: v ?? 500 };
																			setFileUploadResult({ ...fileUploadResult, columns: cols });
																		}}
																	/>
																);
															}
															if (record.type === "numeric") {
																return (
																	<Space size={4}>
																		<InputNumber
																			size="small"
																			min={1}
																			max={1000}
																			value={record.precision ?? 18}
																			addonBefore="精度"
																			style={{ width: 110 }}
																			onChange={(v) => {
																				const cols = [...(fileUploadResult.columns || [])];
																				cols[index] = { ...cols[index], precision: v ?? 18 };
																				setFileUploadResult({ ...fileUploadResult, columns: cols });
																			}}
																		/>
																		<InputNumber
																			size="small"
																			min={0}
																			max={100}
																			value={record.scale ?? 2}
																			addonBefore="标度"
																			style={{ width: 110 }}
																			onChange={(v) => {
																				const cols = [...(fileUploadResult.columns || [])];
																				cols[index] = { ...cols[index], scale: v ?? 2 };
																				setFileUploadResult({ ...fileUploadResult, columns: cols });
																			}}
																		/>
																	</Space>
																);
															}
															return <Text type="secondary">-</Text>;
														},
													},
													{
														title: "操作",
														width: 60,
														align: "center" as const,
														render: (_: any, __: any, index: number) => (
															<Button
																type="text"
																danger
																size="small"
																icon={<DeleteOutlined />}
																onClick={() => {
																	const cols = [...(fileUploadResult.columns || [])];
																	cols.splice(index, 1);
																	setFileUploadResult({ ...fileUploadResult, columns: cols });
																}}
															/>
														),
													},
												]}
											/>
											<Button
												type="dashed"
												size="small"
												icon={<PlusOutlined />}
												className="mt-2"
												onClick={() => {
													const cols = [...(fileUploadResult.columns || [])];
													const idx = cols.length + 1;
													cols.push({ name: `col_${idx}`, type: "string", label: "", length: 500 });
													setFileUploadResult({ ...fileUploadResult, columns: cols });
												}}
											>
												添加列
											</Button>
											{Array.isArray(fileUploadResult.preview) && fileUploadResult.preview.length > 0 && (
												<>
													<Divider orientation="left" className="mt-4">
														预览数据（最多 {filePreviewRows} 行）
													</Divider>
													<Table
														size="small"
														pagination={false}
														rowKey="__row"
														scroll={{ x: true }}
														dataSource={fileUploadResult.preview.map((row, index) => {
															const record: Record<string, any> = { __row: index + 1 };
															(fileUploadResult.columns || []).forEach((col, colIndex) => {
																if (colIndex >= Math.max(1, filePreviewCols)) return;
																record[col.name] = row?.[colIndex] ?? "";
															});
															return record;
														})}
														columns={[
															{ title: "行号", dataIndex: "__row", width: 80 },
															...(fileUploadResult.columns || [])
																.slice(0, Math.max(1, filePreviewCols))
																.map((col) => ({
																	title: col.label || col.name,
																	dataIndex: col.name,
																	ellipsis: true,
																})),
														]}
													/>
													{(fileUploadResult.columns || []).length > Math.max(1, filePreviewCols) && (
														<Text type="secondary" className="block mt-2">
															仅展示前 {Math.max(1, filePreviewCols)} 列，剩余列已省略。
														</Text>
													)}
												</>
											)}
										</Card>
									)}
								</>
							)}
							{currentStep === 1 && (
								<>
									<Divider orientation="left">目标配置</Divider>
									{defaultDestinationStatus ? (
										<Alert
											type={defaultDestinationStatus.available ? "success" : "warning"}
											showIcon
											message="默认数据湖"
											description={[
												defaultDestinationStatus.destinationName
													? `数据湖：${defaultDestinationStatus.destinationName}`
													: null,
												defaultDestinationStatus.writerType
													? `Writer：${defaultDestinationStatus.writerType}`
													: null,
												defaultDestinationStatus.message ? defaultDestinationStatus.message : null,
											]
												.filter(Boolean)
												.join(" · ")}
											className="mb-4"
										/>
									) : null}
									<Form.Item
										name="fileTableName"
										label="目标表名"
										rules={[{ validator: fileTableNameValidator }]}
										tooltip="仅允许字母、数字、下划线，可包含 schema.table"
									>
										<Input placeholder="例如：ods_patent_info" />
									</Form.Item>
									<Form.Item name="syncPrefix" label="目标表前缀">
										<Input placeholder="例如：ods_erp_" />
									</Form.Item>
									<Form.Item
										name="fileAutoId"
										label="自动生成ID"
										valuePropName="checked"
										tooltip="为文件入湖的目标表追加自增 ID 字段（默认开启）"
									>
										<Switch />
									</Form.Item>
									<Text type="secondary" className="block -mt-3 mb-4">
										未填写目标表名时，系统将使用：前缀 + 文件名（去除扩展名）。例如：ods_erp_ + sales_data → ods_erp_sales_data
									</Text>
									<Collapse
										ghost
										className="mb-4"
										items={[
											{
												key: "file-column-rules",
												label: "字段规则（可选）",
												children: (
													<div className="space-y-4">
														<div className="grid gap-4 md:grid-cols-2">
															<Form.Item name="columnPrefix" label="字段名前缀">
																<Input placeholder="例如：src_" />
															</Form.Item>
															<Form.Item name="columnSuffix" label="字段名后缀">
																<Input placeholder="例如：_raw" />
															</Form.Item>
														</div>
														<Text type="secondary" className="block -mt-2 mb-2">
															对目标表所有字段统一添加前缀/后缀。留空则使用文件原始字段名。
														</Text>
														<Divider orientation="left" plain>
															追加字段
														</Divider>
														<Table
															size="small"
															dataSource={extraColumns}
															rowKey={(_: any, index: any) => String(index)}
															pagination={false}
															locale={{ emptyText: "暂无追加字段" }}
															columns={[
																{
																	title: "字段名",
																	dataIndex: "name",
																	render: (value: string, _: any, index: number) => (
																		<Input
																			size="small"
																			value={value}
																			placeholder="英文字段名"
																			onChange={(e) => {
																				const cols = [...extraColumns];
																				cols[index] = { ...cols[index], name: e.target.value };
																				setExtraColumns(cols);
																			}}
																		/>
																	),
																},
																{
																	title: "显示名称",
																	dataIndex: "label",
																	render: (value: string, _: any, index: number) => (
																		<Input
																			size="small"
																			value={value}
																			placeholder="中文名"
																			onChange={(e) => {
																				const cols = [...extraColumns];
																				cols[index] = { ...cols[index], label: e.target.value };
																				setExtraColumns(cols);
																			}}
																		/>
																	),
																},
																{
																	title: "数据类型",
																	dataIndex: "type",
																	width: 160,
																	render: (value: string, _: any, index: number) => (
																		<Select
																			size="small"
																			value={value}
																			style={{ width: "100%" }}
																			onChange={(v) => {
																				const cols = [...extraColumns];
																				cols[index] = { ...cols[index], type: v };
																				setExtraColumns(cols);
																			}}
																			options={[
																				{ label: "VARCHAR", value: "string" },
																				{ label: "TEXT", value: "text" },
																				{ label: "INTEGER", value: "integer" },
																				{ label: "BIGINT", value: "long" },
																				{ label: "TIMESTAMP", value: "timestamp" },
																				{ label: "BOOLEAN", value: "boolean" },
																			]}
																		/>
																	),
																},
																{
																	title: "默认值 (SQL)",
																	dataIndex: "defaultValue",
																	width: 180,
																	render: (value: string, _: any, index: number) => (
																		<Input
																			size="small"
																			value={value}
																			placeholder="CURRENT_TIMESTAMP"
																			onChange={(e) => {
																				const cols = [...extraColumns];
																				cols[index] = { ...cols[index], defaultValue: e.target.value };
																				setExtraColumns(cols);
																			}}
																		/>
																	),
																},
																{
																	title: "操作",
																	width: 50,
																	align: "center" as const,
																	render: (_: any, __: any, index: number) => (
																		<Button
																			type="text"
																			danger
																			size="small"
																			icon={<DeleteOutlined />}
																			onClick={() => {
																				const cols = [...extraColumns];
																				cols.splice(index, 1);
																				setExtraColumns(cols);
																			}}
																		/>
																	),
																},
															]}
														/>
														<Button
															type="dashed"
															size="small"
															icon={<PlusOutlined />}
															onClick={() => {
																setExtraColumns([
																	...extraColumns,
																	{ name: "", label: "", type: "string", defaultValue: "" },
																]);
															}}
														>
															添加字段
														</Button>
													</div>
												),
											},
										]}
									/>
									{fileUploadResult && (
										<Alert
											type="info"
											showIcon
											message={`目标表预览：${normalizeText(form.getFieldValue("fileTableName")) || (normalizeText(form.getFieldValue("syncPrefix")) + buildFileBaseName(fileUploadResult.originalName))}`}
											className="mb-4"
										/>
									)}
									<Divider orientation="left">数据湖连接（可选覆盖）</Divider>
									<Text type="secondary" className="block mb-4">
										若默认数据湖凭据不可用，可在此处手动指定目标库连接信息。留空则使用默认数据湖配置。
									</Text>
									<div className="grid gap-4 md:grid-cols-2">
										<Form.Item name="writerUsername" label="用户名">
											<Input placeholder="数据库账号" />
										</Form.Item>
										<Form.Item name="writerPassword" label="密码">
											<Input.Password placeholder="数据库密码" />
										</Form.Item>
									</div>
									<Form.Item name="writerJdbcUrls" label="JDBC URL（可选覆盖）">
										<Input placeholder="jdbc:postgresql://host:5432/db" />
									</Form.Item>
									<Form.Item name="writerSchema" label="Schema（可选）">
										<Input placeholder="例如 public" />
									</Form.Item>
								</>
							)}
							{currentStep === 2 && (
								<>
									<Form.Item name="jobConfig" label="作业参数 (JSON，可选)" rules={[{ validator: jsonValidator("作业参数") }]}>
										<Input.TextArea rows={4} placeholder='{"setting":{"speed":{"channel":3}}}' />
									</Form.Item>
									<Card type="inner" title="Airflow 触发">
										<Form.Item name="airflowEnabled" label="启用 Airflow" valuePropName="checked">
											<Switch />
										</Form.Item>
										<Form.Item name="runNow" label="立即触发" valuePropName="checked">
											<Switch />
										</Form.Item>
										<Text type="secondary">
											若未勾选立即触发，仅保存作业配置，后续可在 Airflow 中手动运行。
										</Text>
									</Card>
								</>
							)}
						</>
					) : (
						<>
							{/* ===== 数据库模式 ===== */}
							{currentStep === 0 && (
								<>
									<Form.Item name="editorMode" label="配置方式">
										<Radio.Group>
											<Radio.Button value="visual">可视化</Radio.Button>
											<Radio.Button value="json">JSON</Radio.Button>
										</Radio.Group>
									</Form.Item>
									<Form.Item
										name="name"
										label="任务名称"
										rules={[{ required: true, message: "请输入任务名称" }]}
									>
										<Input placeholder="例如：pg-lake-task1" />
									</Form.Item>
									<Form.Item name="description" label="任务描述">
										<Input.TextArea rows={2} placeholder="可选，说明任务用途" />
									</Form.Item>
									<Form.Item name="sourceSystem" label="源系统标识">
										<Input placeholder="可选，例如：erp、crm（用于绑定 DAG）" />
									</Form.Item>
										<Form.Item name="syncMode" label="同步模式" tooltip="同步模式由连接器能力契约驱动">
											<Radio.Group>
												{syncModeOptions.map((item) => (
													<Radio.Button key={item.value} value={item.value} disabled={item.disabled}>
														{item.label}
													</Radio.Button>
												))}
											</Radio.Group>
										</Form.Item>
								<div className="grid gap-4 md:grid-cols-3">
									<Form.Item name="scheduleType" label="调度策略">
										<Select
											options={[
												{ label: "手动触发", value: "manual" },
												{ label: "按间隔执行", value: "interval" },
												{ label: "按 Cron 执行", value: "cron" },
											]}
										/>
									</Form.Item>
									{(normalizeText(scheduleType) || "manual") === "interval" ? (
										<Form.Item
											name="scheduleIntervalMinutes"
											label="执行间隔(分钟)"
											rules={[{ required: true, message: "请输入执行间隔" }]}
										>
											<InputNumber min={1} precision={0} className="w-full" />
										</Form.Item>
									) : null}
									{(normalizeText(scheduleType) || "manual") === "cron" ? (
										<Form.Item
											name="scheduleCron"
											label="Cron 表达式"
											rules={[{ validator: validateCronExpression }]}
										>
											<Input placeholder="例如：0 */30 * * * *" />
										</Form.Item>
									) : null}
								</div>
								<Space size={[8, 8]} wrap className="mb-3">
									<Text type="secondary">当前连接器能力：</Text>
									{["FULL", "INCREMENTAL", "CDC", "BACKFILL"].map((cap) => (
										<Tag key={cap} color={activeCapabilitySet.has(cap) ? "green" : "default"}>
											{cap}
										</Tag>
									))}
									{!supportsIncremental ? <Text type="warning">当前连接器不支持增量</Text> : null}
									{supportsCdc ? <Text type="secondary">已支持 CDC 模式</Text> : null}
									{supportsBackfill ? <Text type="secondary">已支持历史回灌模式</Text> : null}
									{capabilityLoadFailed ? <Text type="warning">能力探测失败，已使用保守降级策略</Text> : null}
								</Space>
									{(normalizeSyncModeValue(syncMode) || "full_refresh") === "incremental" ? (
										<div className="grid gap-4 md:grid-cols-3">
											<Form.Item
												name="incrementalColumn"
												label="增量列"
												rules={[{ required: true, message: "请输入增量列名" }]}
											>
												<Input placeholder="例如 updated_at 或 id" />
											</Form.Item>
											<Form.Item name="incrementalType" label="增量类型">
												<Select
													options={[
														{ label: "datetime", value: "datetime" },
														{ label: "number", value: "number" },
														{ label: "string", value: "string" },
													]}
												/>
											</Form.Item>
											<Form.Item name="initialWatermark" label="初始水位（可选）">
												<Input placeholder="首次运行起点，如 2025-01-01 00:00:00" />
											</Form.Item>
										</div>
									) : null}
									<Collapse
										size="small"
										className="mb-4"
										items={[
											{
												key: "governance-db",
												label: "运行治理策略（可选）",
												children: (
													<div className="grid gap-4 md:grid-cols-3">
														<Form.Item name="taskConcurrency" label="任务并发上限">
															<InputNumber min={1} precision={0} className="w-full" placeholder="默认 1" />
														</Form.Item>
														<Form.Item name="sourceConcurrency" label="来源并发上限">
															<InputNumber min={0} precision={0} className="w-full" placeholder="0 表示不限" />
														</Form.Item>
														<Form.Item name="priority" label="队列优先级">
															<Select
																allowClear
																options={[
																	{ label: "HIGH", value: "HIGH" },
																	{ label: "MEDIUM", value: "MEDIUM" },
																	{ label: "LOW", value: "LOW" },
																]}
															/>
														</Form.Item>
														<Form.Item name="rejectPolicy" label="限流策略">
															<Select
																allowClear
																options={[
																	{ label: "REJECT", value: "REJECT" },
																	{ label: "QUEUE", value: "QUEUE" },
																]}
															/>
														</Form.Item>
														<Form.Item name="windowStart" label="执行窗口开始">
															<Input placeholder="HH:mm，例如 01:00" />
														</Form.Item>
														<Form.Item name="windowEnd" label="执行窗口结束">
															<Input placeholder="HH:mm，例如 06:00" />
														</Form.Item>
														<Form.Item name="windowTimezone" label="执行窗口时区">
															<Select
																allowClear
																options={[
																	{ label: "Asia/Shanghai", value: "Asia/Shanghai" },
																	{ label: "UTC", value: "UTC" },
																]}
															/>
														</Form.Item>
													</div>
												),
											},
										]}
									/>
									<Form.Item name="sourceCategory" label="数据来源">
										<Radio.Group onChange={(e) => setSourceCategory(e.target.value)}>
											<Radio.Button value="database">数据库</Radio.Button>
											<Radio.Button value="file">文件上传</Radio.Button>
										</Radio.Group>
									</Form.Item>

								</>
							)}
							{currentStep === 1 && (
								<>
									<Divider orientation="left">Reader 配置</Divider>
									<Form.Item
										name="sourceDataSourceId"
										label="数据源连接"
										rules={[{ required: true, message: "请选择数据源连接" }]}
									>
										<Select
											loading={loadingDataSources}
											placeholder={loadingDataSources ? "加载中..." : "请选择数据源连接"}
											options={dataSources.map((item) => ({
												label: `${item.name} (${item.type || "unknown"})`,
												value: item.id,
											}))}
											showSearch
											optionFilterProp="label"
										/>
									</Form.Item>
									<Form.Item
										name="readerType"
										label="Reader 类型"
										rules={[{ validator: readerTypeValidator }]}
									>
										<Input placeholder="将根据数据源自动生成" disabled />
									</Form.Item>
									<Form.Item name="tableSelectionMode" label="入湖表选择">
										<Radio.Group
											onChange={(e) => {
												const next = normalizeText(e.target?.value) || "all";
												if (next === "all") {
													syncSelectedTablesToForm([], { silent: true });
												}
											}}
										>
											<Radio.Button value="all">全部表（默认）</Radio.Button>
											<Radio.Button value="manual">手动选择</Radio.Button>
										</Radio.Group>
									</Form.Item>
									{tableSelectionMode === "all" ? (
										<Form.Item name="tableExclude" label="排除表（每行一个，可选）">
											<Input.TextArea rows={2} placeholder="schema.table 或 table_name" />
										</Form.Item>
									) : null}
									{editorMode === "json" ? (
										<Form.Item
											name="readerConfig"
											label="Reader 配置 (JSON)"
											rules={[
												{ required: true, message: "请输入 Reader 配置" },
												{ validator: jsonValidator("Reader 配置", true) },
											]}
										>
											<Input.TextArea rows={6} placeholder='{"column":["*"],"table":["table_a"]}' />
										</Form.Item>
									) : (
										<>
											<div className="grid gap-4 md:grid-cols-2">
												<Form.Item
													name="readerTables"
													label="Reader 表（每行一个）"
													dependencies={["tableSelectionMode"]}
													rules={[{ validator: readerTablesValidator }]}
												>
													<Input.TextArea rows={3} placeholder="source_table" />
												</Form.Item>
												<Form.Item name="readerColumns" label="Reader 字段（逗号分隔）">
													<Input placeholder="* 或 id,name,created_at" />
												</Form.Item>
											</div>
											<div className="grid gap-4 md:grid-cols-2">
												<Form.Item name="readerWhere" label="Reader 过滤条件">
													<Input placeholder="可选，例如：status = 1" />
												</Form.Item>
											</div>
											<Collapse
												ghost
												items={[
													{
														key: "reader-advanced",
														label: "Reader 高级参数",
														children: (
															<div className="space-y-4">
																<Form.Item name="readerQuerySql" label="Reader 查询 SQL（每行一条）">
																	<Input.TextArea rows={3} placeholder="select * from t where ..." />
																</Form.Item>
																<Form.Item name="readerExtraConfig" label="Reader 扩展配置 JSON">
																	<Input.TextArea rows={4} placeholder='{"splitPk":"id"}' />
																</Form.Item>
															</div>
														),
													},
												]}
											/>
										</>
									)}
									<Divider orientation="left">源端表发现</Divider>
									<Card type="inner">
										<div className="grid gap-4 md:grid-cols-3">
											<Form.Item name="readerSchema" label="Schema（可选）">
												<Input placeholder="例如 public" />
											</Form.Item>
											<Form.Item name="readerTablePattern" label="表名筛选（可选）">
												<Input placeholder="支持 SQL LIKE，例如 ods_%" />
											</Form.Item>
											<Form.Item label="操作">
												<Space>
													<Button onClick={handleDiscoverTables} loading={discoveringTables}>
														获取表清单
													</Button>
													<Button
														onClick={() => {
															setSelectedTableKeys(discoveredTableKeys);
															syncSelectedTablesToForm(discoveredTableKeys, { silent: true });
														}}
														disabled={!discoveredTableKeys.length}
													>
														全选
													</Button>
													<Button
														onClick={() => {
															setSelectedTableKeys([]);
															syncSelectedTablesToForm([], { silent: true });
														}}
														disabled={!selectedTableKeys.length}
													>
														清空
													</Button>
													<Button onClick={handleApplyTables} disabled={!selectedTableKeys.length}>
														应用选择
													</Button>
												</Space>
											</Form.Item>
										</div>
										{discoverError ? (
											<Alert type="warning" message={discoverError} showIcon className="mb-3" />
										) : null}
										<Table
											rowKey={(record) => buildTableKey(record)}
											size="small"
											loading={discoveringTables}
											dataSource={discoveredTables}
											rowSelection={{
												selectedRowKeys: selectedTableKeys,
												onChange: (keys) => {
													const nextKeys = keys.map((key) => String(key));
													syncSelectedTablesToForm(nextKeys, { silent: true });
												},
											}}
											columns={[
												{ title: "Schema", dataIndex: "schema", width: 140 },
												{ title: "表名", dataIndex: "name" },
												{ title: "类型", dataIndex: "type", width: 120 },
											]}
											pagination={{
												pageSize: tablePageSize,
												showSizeChanger: true,
												pageSizeOptions: [8, 20, 50, 100],
												onShowSizeChange: (_current: number, size: number) => setTablePageSize(size),
											}}
										/>
										<Text type="secondary" className="block mt-2">
											已发现 {discoveredTables.length} 张表，已选择 {selectedTableKeys.length} 张表
										</Text>
									</Card>
								</>
							)}
							{currentStep === 2 && (
								<>
									<Divider orientation="left">目标端配置</Divider>
									{loadingDefaultDestination ? (
										<Alert
											type="info"
											showIcon
											message="正在加载默认数据湖配置"
											className="mb-4"
										/>
									) : null}
									{defaultDestinationError ? (
										<Alert
											type="error"
											showIcon
											message="默认数据湖不可用"
											description={defaultDestinationError}
											className="mb-4"
										/>
									) : null}
									{defaultDestinationStatus ? (
										<Alert
											type={defaultDestinationStatus.available ? "success" : "warning"}
											showIcon
											message="默认数据湖"
											description={[
												defaultDestinationStatus.destinationName
													? `数据湖：${defaultDestinationStatus.destinationName}`
													: null,
												defaultDestinationStatus.writerType
													? `Writer：${defaultDestinationStatus.writerType}`
													: null,
												defaultDestinationStatus.message ? defaultDestinationStatus.message : null,
											]
												.filter(Boolean)
												.join(" · ")}
											className="mb-4"
										/>
									) : null}
									<Form.Item name="syncPrefix" label="目标表前缀">
										<Input placeholder="例如：ods_erp_" />
									</Form.Item>
									<Text type="secondary" className="block -mt-3 mb-4">
										用于自动生成 ODS 表名（如：ods_erp_ + 源表名）。若 Writer 已指定目标表，可留空。
									</Text>
									<Collapse
										ghost
										className="mb-4"
										items={[
											{
												key: "column-rules",
												label: "字段规则（可选）",
												children: (
													<div className="space-y-4">
														<div className="grid gap-4 md:grid-cols-2">
															<Form.Item name="columnPrefix" label="字段名前缀">
																<Input placeholder="例如：src_" />
															</Form.Item>
															<Form.Item name="columnSuffix" label="字段名后缀">
																<Input placeholder="例如：_raw" />
															</Form.Item>
														</div>
														<Text type="secondary" className="block -mt-2 mb-2">
															对源表所有字段统一添加前缀/后缀，例如 src_ + id → src_id。留空则不变。
														</Text>
														<Divider orientation="left" plain>
															追加字段
														</Divider>
														<Table
															size="small"
															dataSource={extraColumns}
															rowKey={(_: any, index: any) => String(index)}
															pagination={false}
															locale={{ emptyText: "暂无追加字段" }}
															columns={[
																{
																	title: "字段名",
																	dataIndex: "name",
																	render: (value: string, _: any, index: number) => (
																		<Input
																			size="small"
																			value={value}
																			placeholder="英文字段名"
																			onChange={(e) => {
																				const cols = [...extraColumns];
																				cols[index] = { ...cols[index], name: e.target.value };
																				setExtraColumns(cols);
																			}}
																		/>
																	),
																},
																{
																	title: "显示名称",
																	dataIndex: "label",
																	render: (value: string, _: any, index: number) => (
																		<Input
																			size="small"
																			value={value}
																			placeholder="中文名"
																			onChange={(e) => {
																				const cols = [...extraColumns];
																				cols[index] = { ...cols[index], label: e.target.value };
																				setExtraColumns(cols);
																			}}
																		/>
																	),
																},
																{
																	title: "数据类型",
																	dataIndex: "type",
																	width: 160,
																	render: (value: string, _: any, index: number) => (
																		<Select
																			size="small"
																			value={value}
																			style={{ width: "100%" }}
																			onChange={(v) => {
																				const cols = [...extraColumns];
																				cols[index] = { ...cols[index], type: v };
																				setExtraColumns(cols);
																			}}
																			options={[
																				{ label: "VARCHAR", value: "string" },
																				{ label: "TEXT", value: "text" },
																				{ label: "INTEGER", value: "integer" },
																				{ label: "BIGINT", value: "long" },
																				{ label: "TIMESTAMP", value: "timestamp" },
																				{ label: "BOOLEAN", value: "boolean" },
																			]}
																		/>
																	),
																},
																{
																	title: "默认值 (SQL)",
																	dataIndex: "defaultValue",
																	width: 180,
																	render: (value: string, _: any, index: number) => (
																		<Input
																			size="small"
																			value={value}
																			placeholder="CURRENT_TIMESTAMP"
																			onChange={(e) => {
																				const cols = [...extraColumns];
																				cols[index] = { ...cols[index], defaultValue: e.target.value };
																				setExtraColumns(cols);
																			}}
																		/>
																	),
																},
																{
																	title: "操作",
																	width: 50,
																	align: "center" as const,
																	render: (_: any, __: any, index: number) => (
																		<Button
																			type="text"
																			danger
																			size="small"
																			icon={<DeleteOutlined />}
																			onClick={() => {
																				const cols = [...extraColumns];
																				cols.splice(index, 1);
																				setExtraColumns(cols);
																			}}
																		/>
																	),
																},
															]}
														/>
														<Button
															type="dashed"
															size="small"
															icon={<PlusOutlined />}
															onClick={() => {
																setExtraColumns([
																	...extraColumns,
																	{ name: "", label: "", type: "string", defaultValue: "" },
																]);
															}}
														>
															添加字段
														</Button>
														<Text type="secondary" className="block mt-2">
															追加字段会在数据加载完成后通过 ALTER TABLE 添加到目标表，默认值使用 SQL 表达式（如 CURRENT_TIMESTAMP、&apos;erp&apos;）。
														</Text>
													</div>
												),
											},
										]}
									/>
									<Divider orientation="left">Writer 配置</Divider>
									<Form.Item label="Writer 类型" required>
										<Input value={formValues?.writerType || ""} placeholder="由默认数据湖自动提供" disabled />
									</Form.Item>
									{editorMode === "json" ? (
										<Form.Item
											name="writerConfig"
											label="Writer 配置 (JSON)"
											required
											rules={[
												{ required: true, message: "请输入 Writer 配置" },
												{ validator: writerConfigValidator },
											]}
										>
											<Input.TextArea
												rows={6}
												placeholder='{"connection":[{"table":["target_table"]}],"column":["*"]}'
											/>
										</Form.Item>
									) : (
										<>
											<div className="grid gap-4 md:grid-cols-2">
												<Form.Item
													name="writerJdbcUrls"
													label="Writer JDBC URL（每行一个，可选覆盖）"
												>
													<Input.TextArea rows={3} placeholder="jdbc:postgresql://host:5432/db" />
												</Form.Item>
												<Form.Item
													name="writerTables"
													label="Writer 表（每行一个）"
													rules={[
														{ validator: writerTablesValidator },
													]}
												>
													<Input.TextArea rows={3} placeholder="target_table" />
												</Form.Item>
											</div>
											<div className="grid gap-4 md:grid-cols-2">
												<Form.Item name="writerColumns" label="Writer 字段（逗号分隔）">
													<Input placeholder="* 或 id,name,created_at" />
												</Form.Item>
												<Form.Item name="writerWriteMode" label="Writer 写入模式">
													<Input placeholder="insert / replace / update" />
												</Form.Item>
											</div>
											<div className="grid gap-4 md:grid-cols-2">
												<Form.Item
													name="writerUsername"
													label="Writer 用户名"
												>
													<Input placeholder="数据库账号" />
												</Form.Item>
												<Form.Item
													name="writerPassword"
													label="Writer 密码"
												>
													<Input.Password placeholder="******" />
												</Form.Item>
											</div>
											<Form.Item name="writerSchema" label="Writer Schema">
												<Input placeholder="可选，例如 public" />
											</Form.Item>
											<Collapse
												ghost
												items={[
													{
														key: "writer-advanced",
														label: "Writer 高级参数",
														children: (
															<div className="space-y-4">
																<Form.Item name="writerPreSql" label="Writer 前置 SQL（每行一条）">
																	<Input.TextArea rows={3} placeholder="delete from t where ..." />
																</Form.Item>
																<Form.Item name="writerPostSql" label="Writer 后置 SQL（每行一条）">
																	<Input.TextArea rows={3} placeholder="analyze table t" />
																</Form.Item>
																<Form.Item name="writerExtraConfig" label="Writer 扩展配置 JSON">
																	<Input.TextArea rows={4} placeholder='{"batchSize":1000}' />
																</Form.Item>
															</div>
														),
													},
												]}
											/>
										</>
									)}
									<Text type="secondary" className="block mt-2">
										入湖任务需要提供目标表名，可使用 {TABLE_PLACEHOLDER} 占位符或具体表名。
									</Text>
								</>
							)}
							{currentStep === 3 && (
								<>
									<Card
										type="inner"
										title="dbt 模型联动"
										extra={
											<Button size="small" onClick={() => router.push("/modeling/sql")}>
												进入建模
											</Button>
										}
										className="mb-4"
									>
										<Form.Item name="dbtModels" label="选择模型（可选）">
											<Select
												mode="multiple"
												allowClear
												loading={loadingSqlModels}
												placeholder={loadingSqlModels ? "模型加载中..." : "选择需要联动的模型"}
												options={sqlModels.map((model) => ({
													label: model.alias ? `${model.name} (${model.alias})` : model.name,
													value: model.name,
												}))}
												showSearch
												optionFilterProp="label"
											/>
										</Form.Item>
										<Form.Item name="dbtModelSelector" label="模型选择器（可选）">
											<Input placeholder="例如：model:ods_xxx model:dwd_xxx" />
										</Form.Item>
										<Form.Item name="dbtDagSelector" label="DAG 族选择器（可选）">
											<Input placeholder="例如：tab:erp" />
										</Form.Item>
										<Text type="secondary">
											若未填写模型选择器，将根据选中的模型生成 model:xxx 选择器；DAG 族建议使用 tab:源系统。
										</Text>
									</Card>
									<Form.Item name="jobConfig" label="作业参数 (JSON，可选)" rules={[{ validator: jsonValidator("作业参数") }]}>
										<Input.TextArea rows={4} placeholder='{"setting":{"speed":{"channel":3}}}' />
									</Form.Item>
									<Card type="inner" title="作业预览">
										{previewState.error ? (
											<Alert type="warning" message={previewState.error} showIcon />
										) : (
											<pre className="bg-muted p-4 rounded overflow-auto">
												{JSON.stringify(previewState.config, null, 2)}
											</pre>
										)}
									</Card>
									<Card type="inner" title="Airflow 触发">
										<Form.Item name="airflowEnabled" label="启用 Airflow" valuePropName="checked">
											<Switch />
										</Form.Item>
										<Form.Item name="runNow" label="立即触发" valuePropName="checked">
											<Switch />
										</Form.Item>
										<Text type="secondary">
											若未勾选立即触发，仅保存作业配置，后续可在 Airflow 中手动运行。
										</Text>
									</Card>
								</>
							)}
						</>
					)}
					<Divider />
					<Space>
						<Button onClick={handlePrevStep} disabled={currentStep === 0}>
							上一步
						</Button>
						{currentStep < stepItems.length - 1 ? (
							<Button type="primary" onClick={handleNextStep}>
								下一步
							</Button>
						) : (
							<Button type="primary" loading={saving} onClick={() => form.submit()} disabled={loadingTask}>
								{isEdit ? "保存修改" : "提交任务"}
							</Button>
						)}
					</Space>
				</Form>
			</Card>
			<Modal
				title="错误行明细"
				open={errorPreviewOpen}
				onCancel={() => setErrorPreviewOpen(false)}
				footer={null}
				width={720}
			>
				<Space className="mb-3" wrap>
					<Text type="secondary">预览条数</Text>
					<InputNumber
						min={1}
						max={500}
						value={errorPreviewLimit}
						onChange={(value) => setErrorPreviewLimit(value ? Number(value) : 50)}
					/>
					<Button size="small" onClick={openErrorPreview} loading={errorPreviewLoading}>
						刷新
					</Button>
				</Space>
				<Table
					size="small"
					rowKey={(record, index) => `${record?.rowIndex || "row"}-${index}`}
					pagination={false}
					loading={errorPreviewLoading}
					dataSource={errorPreviewRows || []}
					locale={{ emptyText: "暂无错误行" }}
					columns={[
						{ title: "行号", dataIndex: "rowIndex", width: 100 },
						{ title: "错误信息", dataIndex: "message", ellipsis: true },
					]}
				/>
			</Modal>
			<Modal
				title="任务启动进度"
				open={asyncRunModalOpen}
				onCancel={closeAsyncRunModal}
				maskClosable={false}
				footer={
					<Space>
						{asyncRunTaskId ? (
							<Button
								onClick={() => {
									closeAsyncRunModal();
									router.push(`/explore/etl/transform/${asyncRunTaskId}`);
								}}
							>
								查看任务详情
							</Button>
						) : null}
						{!asyncRunProgress.terminal ? (
							<Button
								onClick={() => {
									closeAsyncRunModal();
									router.push("/explore/etl/transform");
								}}
							>
								后台查看列表
							</Button>
						) : null}
						<Button type="primary" onClick={closeAsyncRunModal}>
							{asyncRunProgress.terminal ? "关闭" : "最小化"}
						</Button>
					</Space>
				}
				width={640}
			>
				<Space direction="vertical" size={16} className="w-full">
					<div className="flex items-center justify-between">
						<Text>任务：{asyncRunTaskName || "-"}</Text>
						{asyncRunExecution?.executionId ? (
							<Tag color="blue">执行ID: {asyncRunExecution.executionId}</Tag>
						) : null}
					</div>
					<Progress percent={asyncRunProgress.progress} status={asyncRunProgress.status} />
					<Alert
						type={
							asyncRunProgress.status === "success"
								? "success"
								: asyncRunProgress.status === "exception"
									? "error"
									: "info"
						}
						showIcon
						message={asyncRunProgress.stage}
						description={asyncRunProgress.detail}
					/>
					{asyncRunExecution ? (
						<Space size={8}>
							<Text type="secondary">最新状态：</Text>
							<Tag
								color={
									normalizeText(asyncRunExecution.status).toLowerCase() === "success"
										? "success"
										: normalizeText(asyncRunExecution.status).toLowerCase() === "failed"
											? "error"
											: "processing"
								}
							>
								{normalizeText(asyncRunExecution.status) || "running"}
							</Tag>
						</Space>
					) : null}
				</Space>
			</Modal>
		</div>
	);
}
