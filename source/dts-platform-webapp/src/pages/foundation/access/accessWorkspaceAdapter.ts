import type { IngestionTaskDTO } from "../../../api/ingestion.ts";
import type { DataSourceSelectionItem } from "../../../api/services/dataSourcesService.ts";

export type AccessKind = "overview" | "database" | "api" | "file";
export type AccessSourceKind = Exclude<AccessKind, "overview">;
export type AccessLifecycle = "draft" | "active" | "paused" | "deleted" | "unknown";
export type AccessHealth = "healthy" | "attention" | "running" | "not_evaluated";

export type AccessWorkspaceRow = {
	key: string;
	taskId?: number;
	name: string;
	kind: AccessSourceKind;
	sourceName: string;
	sourceType: string;
	resourceSummary: string;
	syncMode: string;
	lifecycle: AccessLifecycle;
	health: AccessHealth;
	healthReason: string;
	lastExecutionStatus?: string;
	lastExecutedAt?: string;
	owner: string;
	classification?: string;
	versionState: "versioned" | "legacy-unversioned";
	versionLabel: string;
	versionHint: string;
};

const API_TYPES = new Set(["api", "http", "https", "http_api", "api_http", "rest", "rest_api", "httpreader"]);
const FILE_TYPES = new Set(["file", "excel", "csv", "json", "txt", "txtfilereader", "excelreader", "csvreader"]);

const normalize = (value: unknown) =>
	String(value ?? "")
		.trim()
		.toLowerCase();

export const parseAccessKind = (value: string | null | undefined): AccessKind => {
	const normalized = normalize(value);
	if (normalized === "files") return "file";
	if (normalized === "database" || normalized === "api" || normalized === "file") return normalized;
	return "overview";
};

const resolveKind = (task: IngestionTaskDTO, source?: DataSourceSelectionItem): AccessSourceKind => {
	const candidates = [task.sourceType, task.sourceConfig?.readerType, task.sourceConfig?.type, source?.type]
		.map(normalize)
		.filter(Boolean);
	if (candidates.some((candidate) => API_TYPES.has(candidate) || candidate.includes("http"))) return "api";
	if (candidates.some((candidate) => FILE_TYPES.has(candidate) || candidate.includes("file"))) return "file";
	return "database";
};

const resolveLifecycle = (status?: string): AccessLifecycle => {
	const normalized = normalize(status);
	if (normalized === "draft" || normalized === "active" || normalized === "paused" || normalized === "deleted") {
		return normalized;
	}
	return "unknown";
};

const isSourceUnhealthy = (source?: DataSourceSelectionItem) => {
	if (!source) return false;
	const heartbeat = normalize(source.heartbeatStatus);
	if (["down", "failed", "failure", "error", "unhealthy"].includes(heartbeat)) return true;
	const status = normalize(source.status);
	return ["inactive", "disabled", "failed", "error"].includes(status);
};

const resolveHealth = (
	task: IngestionTaskDTO,
	source?: DataSourceSelectionItem,
): Pick<AccessWorkspaceRow, "health" | "healthReason"> => {
	if (isSourceUnhealthy(source)) return { health: "attention", healthReason: "数据源心跳异常" };
	const runStatus = normalize(task.lastExecutionStatus);
	if (["failed", "failure", "error", "timeout", "cancelled", "canceled"].includes(runStatus)) {
		return { health: "attention", healthReason: "最近一次运行异常" };
	}
	if (["preparing", "queued", "running"].includes(runStatus)) {
		return { health: "running", healthReason: "任务正在运行" };
	}
	if (["success", "succeeded", "completed"].includes(runStatus)) {
		return { health: "healthy", healthReason: "最近一次运行成功" };
	}
	return { health: "not_evaluated", healthReason: "尚无运行结果" };
};

const recordValue = (value: unknown): Record<string, unknown> =>
	value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : {};

const arrayValue = (value: unknown): unknown[] => (Array.isArray(value) ? value : []);

const resolveResourceSummary = (task: IngestionTaskDTO, kind: AccessSourceKind) => {
	const config = recordValue(task.sourceConfig);
	if (kind === "file") {
		return String(config.originalName || config.fileName || config.sourceFile || "离线文件");
	}
	if (kind === "api") {
		const method = String(config.method || config.httpMethod || "GET").toUpperCase();
		const path = String(config.resourcePath || config.apiResourcePath || config.path || config.endpoint || "API 资源");
		return `${method} ${path}`;
	}
	const mappings = arrayValue(task.tableMapping);
	const selectedTables = arrayValue(config.selectedTables || config.tables);
	const count = mappings.length || selectedTables.length;
	return count ? `${count} 张表` : "数据库资源";
};

export const toAccessWorkspaceRows = (tasks: IngestionTaskDTO[], sources: DataSourceSelectionItem[]): AccessWorkspaceRow[] => {
	const sourceById = new Map(sources.map((source) => [String(source.id), source]));
	return tasks.map((task, index) => {
		const source = task.sourceDataSourceId ? sourceById.get(String(task.sourceDataSourceId)) : undefined;
		const kind = resolveKind(task, source);
		const health = resolveHealth(task, source);
		const revisionNumber = Number(task.revisionNumber);
		const versioned = Number.isInteger(revisionNumber) && revisionNumber > 0;
		return {
			key: task.id == null ? `legacy-${index}-${task.name}` : String(task.id),
			taskId: task.id,
			name: task.name,
			kind,
			sourceName: source?.name || (kind === "file" ? "离线文件" : "来源连接未匹配"),
			sourceType: source?.connectorName || source?.type || task.sourceType || "-",
			resourceSummary: resolveResourceSummary(task, kind),
			syncMode: task.syncMode || "-",
			lifecycle: resolveLifecycle(task.status),
			...health,
			lastExecutionStatus: task.lastExecutionStatus,
			lastExecutedAt: task.lastExecutedAt,
			owner: task.createdBy || task.lastModifiedBy || "未记录",
			classification: task.classificationSeal?.effectiveLevel,
			versionState: versioned ? "versioned" : "legacy-unversioned",
			versionLabel: versioned ? `R${revisionNumber}` : "未版本化",
			versionHint: versioned
				? `${task.revisionState || "UNKNOWN"} · 策略 v${task.defaultPolicyVersion || "未记录"}`
				: "存量任务待后台迁移",
		};
	});
};
