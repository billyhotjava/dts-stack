import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import Editor from "@monaco-editor/react";
import { toast } from "sonner";
import ModelPipeline from "./ModelPipeline";
import {
	Alert,
	Badge,
	Button,
	Card,
	Checkbox,
	Divider,
	Drawer,
	Dropdown,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Switch,
	Table,
	Tabs,
	Tag,
	Tooltip,
	Tree,
	Typography,
	Upload,
	Popconfirm,
} from "antd";
import {
	PlusOutlined,
	EditOutlined,
	DeleteOutlined,
	SaveOutlined,
	SettingOutlined,
	DownOutlined,
	ImportOutlined,
	CodeOutlined,
	TableOutlined,
	LinkOutlined,
	SyncOutlined,
	RocketOutlined,
	ReloadOutlined,
	FileTextOutlined,
	UndoOutlined,
	CheckCircleOutlined,
} from "@ant-design/icons";
import type { UploadFile } from "antd/es/upload/interface";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import {
	getDbtConfig,
	listDbtRuns,
	listAirflowJobRuns,
	listSqlModels,
	listSqlModelColumns,
	getSqlModelContractImpact,
	createSqlModel,
	updateSqlModel,
	deleteSqlModel,
	importSqlModel,
	generateSqlModelsFromOds,
	listModelingPlans,
	syncDbtModels,
	getDbtSyncStatus,
	triggerDbtRun,
	triggerDbtCompile,
	triggerDbtTest,
	triggerDbtDocs,
	checkDbtQualityGate,
	checkDbtReleaseGate,
	updateDbtConfig,
	listDbtSources,
	listDbtRefs,
	listTemplateLayers,
	getDbtRunLog,
	previewDbtModel,
	getDbtGitStatus,
	commitDbtChanges,
	getDbtGitLog,
	getDbtGitDiff,
	revertDbtFile,
	getRollbackAuditLog,
} from "@/api/platformApi";
import dataSourcesService, { type InfraDataSource } from "@/api/services/dataSourcesService";
import RollbackImpactModal, { type RollbackRequest } from "@/components/rollback/RollbackImpactModal";
import BatchImportModal from "./BatchImportModal";
import { useRouter } from "@/routes/hooks";
import { buildArchivePayload, collectUnassignedModelIds } from "./sqlModelArchive.helpers";
import {
	buildReleaseSelector,
	createFailedBuildSummary,
	createPendingBuildSummary,
	describeBuildSummary,
	inferBuildOperationFromCommand,
	matchesTriggeredBuildSummary,
} from "./sqlModelBuild.helpers";

const { Text } = Typography;
const { DirectoryTree } = Tree;

const formatDateTime = (value?: string) => {
	if (!value) return "-";
	try {
		return new Date(value).toLocaleString();
	} catch {
		return value;
	}
};

const syncTag = (synced?: boolean) => {
	if (synced == null) return <Tag>未知</Tag>;
	return synced ? <Tag color="green">已同步</Tag> : <Tag color="red">失败</Tag>;
};

const normalizeText = (value?: string) => String(value || "").trim();
const normalizeUpper = (value?: string) => normalizeText(value).toUpperCase();
const normalizeLower = (value?: string) => normalizeText(value).toLowerCase();

const tryParseJsonObject = (raw: string | undefined) => {
	const text = normalizeText(raw);
	if (!text) return undefined;
	try {
		const parsed = JSON.parse(text);
		if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
			return parsed as Record<string, any>;
		}
	} catch {
		return undefined;
	}
	return undefined;
};

const prettyJson = (raw?: string) => {
	const text = normalizeText(raw);
	if (!text) return "";
	try {
		return JSON.stringify(JSON.parse(text), null, 2);
	} catch {
		return text;
	}
};

type DbtConfigView = {
	enabled?: boolean;
	config?: {
		enabled?: boolean;
		projectDir?: string;
		profilesDir?: string;
		profileName?: string;
		targetName?: string;
		targetDataSourceId?: string;
		database?: string;
		schema?: string;
		vars?: Record<string, any>;
	};
	profileStatus?: { generated?: boolean; message?: string; profilePath?: string };
	workspaceStatus?: { ok?: boolean; message?: string; detail?: Record<string, any> };
	target?: { id?: string; name?: string; type?: string };
};

type DbtSyncArtifactStatus = {
	lastSyncAt?: string;
	lastModifiedAt?: number;
	synced?: boolean;
	message?: string;
};

type DbtSyncStats = {
	lastSyncAt?: string;
	datasetsCreated?: number;
	datasetsUpdated?: number;
	odsUpdated?: number;
	columnsUpdated?: number;
	lineageCreated?: number;
	lineageRemoved?: number;
	message?: string;
};

type DbtRunFailure = {
	uniqueId?: string;
	name?: string;
	resourceType?: string;
	path?: string;
	status?: string;
	message?: string;
	executionTime?: number;
};

type DbtRunSummary = {
	present?: boolean;
	projectDir?: string;
	runResultsPath?: string;
	manifestPath?: string;
	invocationId?: string;
	generatedAt?: string;
	command?: string;
	status?: string;
	total?: number;
	success?: number;
	failed?: number;
	skipped?: number;
	failures?: DbtRunFailure[];
	dagRunId?: string;
	dagId?: string;
};

type DbtSyncStatus = {
	manifest?: DbtSyncArtifactStatus | null;
	runResults?: DbtSyncArtifactStatus | null;
	stats?: DbtSyncStats | null;
	latestRun?: DbtRunSummary | null;
};

type SqlModel = {
	id?: string;
	planId?: string;
	planName?: string;
	name?: string;
	alias?: string;
	layer?: string;
	sourceDataSourceId?: string;
	sourceDataSourceName?: string;
	sourceSystem?: string;
	dagSelector?: string;
	tags?: string;
	materialized?: string;
	schemaName?: string;
	description?: string;
	sql?: string;
	enabled?: boolean;
	modelPath?: string;
	ownerDept?: string;
	status?: string;
	semanticContract?: string;
	contractVersion?: string;
	contractUpdatedAt?: string;
	metricCount?: number;
	dimensionCount?: number;
	createdDate?: string;
	lastModifiedDate?: string;
};

type ProjectSpace = {
	id?: string;
	name?: string;
	domain?: string;
	scope?: string;
	status?: string;
	version?: string;
	versionNotes?: string;
	owner?: string;
	ownerDept?: string;
	tags?: string;
	content?: string;
	createdDate?: string;
	lastModifiedDate?: string;
};

type DagRun = {
	dag_id?: string;
	dag_run_id?: string;
	state?: string;
	execution_date?: string;
	start_date?: string;
	end_date?: string;
	conf?: {
		models?: string;
		target?: string;
		operation?: string;
		[key: string]: any;
	};
};

type ModelColumn = {
	name?: string;
	dataType?: string;
	comment?: string;
	status?: string;
};

type DbtSourceItem = {
	id?: string;
	schema?: string;
	table?: string;
	description?: string;
	systemCode?: string;
	bizCode?: string;
	entityCode?: string;
	sourceDataSourceId?: string;
	sourceDataSourceName?: string;
	sourceSnippet?: string;
};

type DbtRefItem = {
	id?: string;
	name?: string;
	layer?: string;
	description?: string;
	tags?: string;
	sourceSystem?: string;
	refSnippet?: string;
};

type SqlModelOdsGenerateResult = {
	mappingsTotal?: number;
	modelsCreated?: number;
	modelsUpdated?: number;
	createdModels?: string[];
	updatedModels?: string[];
	skipped?: string[];
	qualityTemplatesGenerated?: number;
	qualitySkipped?: string[];
};

type DbtQualityGateResult = {
	selector?: string;
	selectedModels?: string[];
	blocking?: boolean;
	warning?: boolean;
	latestStatus?: string;
	latestCommand?: string;
	latestGeneratedAt?: string;
	latestFailedCount?: number;
	blockers?: string[];
	warnings?: string[];
};

type DbtReleaseGateResult = {
	selector?: string;
	strictMode?: boolean;
	gitRef?: string;
	commitSha?: string;
	decision?: string;
	blocking?: boolean;
	warning?: boolean;
	blockers?: string[];
	warnings?: string[];
	buildEvidence?: {
		invocationId?: string;
		command?: string;
		status?: string;
		generatedAt?: string;
		runResultsPath?: string;
	};
};

type SqlModelContractImpact = {
	modelId?: string;
	modelName?: string;
	contractVersion?: string;
	contractUpdatedAt?: string;
	metricCount?: number;
	dimensionCount?: number;
	fieldCount?: number;
	impactedDatasetCount?: number;
	impactedReportCount?: number;
	impactedDatasets?: Array<{
		id?: string;
		name?: string;
		status?: string;
		publishedVersion?: number;
	}>;
	impactedReports?: Array<{
		id?: string;
		title?: string;
		code?: string;
		queryDatasetId?: string;
		enabled?: boolean;
	}>;
};

type OdsSkippedSeverity = "error" | "warn" | "info";

type OdsSkippedEntry = {
	raw: string;
	reason: string;
	severity: OdsSkippedSeverity;
};

const parseOdsSkippedEntry = (item?: string): OdsSkippedEntry => {
	const raw = normalizeText(item);
	const match = raw.match(/\(([^()]*)\)\s*$/);
	const reason = normalizeText(match?.[1]) || "其他";
	const combined = `${raw} ${reason}`;
	if (combined.includes("已存在，未覆盖")) {
		return { raw, reason, severity: "info" };
	}
	const errorTokens = ["失败", "不可用", "不存在", "过期", "无法", "异常", "错误", "未找到", "为空", "无权"];
	const hasError = errorTokens.some((token) => combined.includes(token));
	if (hasError) {
		return { raw, reason, severity: "error" };
	}
	return { raw, reason, severity: "warn" };
};

const skippedSeverityTag = (severity: OdsSkippedSeverity) => {
	if (severity === "error") return <Tag color="red">失败</Tag>;
	if (severity === "info") return <Tag>提示</Tag>;
	return <Tag color="gold">告警</Tag>;
};

const inferLayer = (name?: string) => {
	const normalized = (name || "").toLowerCase();
	if (normalized.startsWith("ods_")) return "ODS";
	if (normalized.startsWith("dwd_")) return "DWD";
	if (normalized.startsWith("dws_")) return "DWS";
	if (normalized.startsWith("ads_")) return "ADS";
	return "其他";
};

const layerTag = (layer?: string) => {
	if (!layer) return <Tag>未分层</Tag>;
	const color = layer === "ODS" ? "blue" : layer === "DWD" ? "cyan" : layer === "DWS" ? "purple" : layer === "ADS" ? "geekblue" : "default";
	return <Tag color={color}>{layer}</Tag>;
};

const resolveModelKey = (model: SqlModel, fallback: string) => model.id || model.name || fallback;
const resolveSpaceKey = (space: ProjectSpace, index: number) => `space-${space.id || index}`;
const UNASSIGNED_SPACE_KEY = "space-unassigned";

export default function SqlModelingPage() {
	const router = useRouter();
	const [configLoading, setConfigLoading] = useState(false);
	const [configSaving, setConfigSaving] = useState(false);
	const [configOpen, setConfigOpen] = useState(false);
	const [dbtConfig, setDbtConfig] = useState<DbtConfigView | null>(null);
	const [dbtSyncStatus, setDbtSyncStatus] = useState<DbtSyncStatus | null>(null);
	const [spacesLoading, setSpacesLoading] = useState(false);
	const [spaces, setSpaces] = useState<ProjectSpace[]>([]);
	const [activeSpaceKey, setActiveSpaceKey] = useState<string | null>(null);
	const [modelsLoading, setModelsLoading] = useState(false);
	const [sqlModels, setSqlModels] = useState<SqlModel[]>([]);
	const [dataSources, setDataSources] = useState<InfraDataSource[]>([]);
	const [layers, setLayers] = useState<{ layer: string; name: string; description: string }[]>([]);
	const [modelDrawerOpen, setModelDrawerOpen] = useState(false);
	const [modelSubmitting, setModelSubmitting] = useState(false);
	const [editingModel, setEditingModel] = useState<SqlModel | null>(null);
	const [sqlDraft, setSqlDraft] = useState("");
	const [importOpen, setImportOpen] = useState(false);
	const [batchImportOpen, setBatchImportOpen] = useState(false);
	const [singleArchiveOpen, setSingleArchiveOpen] = useState(false);
	const [batchArchiveOpen, setBatchArchiveOpen] = useState(false);
	const [importSubmitting, setImportSubmitting] = useState(false);
	const [archiveSubmitting, setArchiveSubmitting] = useState(false);
	const [sqlFileList, setSqlFileList] = useState<UploadFile[]>([]);
	const [csvFileList, setCsvFileList] = useState<UploadFile[]>([]);
	const [batchArchiveSelection, setBatchArchiveSelection] = useState<string[]>([]);
	const [odsGenerateOpen, setOdsGenerateOpen] = useState(false);
	const [odsGenerateSubmitting, setOdsGenerateSubmitting] = useState(false);
	const [syncingModels, setSyncingModels] = useState(false);
	const [runsLoading, setRunsLoading] = useState(false);
	const [runs, setRuns] = useState<DagRun[]>([]);
	const [runOpen, setRunOpen] = useState(false);
	const [runSubmitting, setRunSubmitting] = useState(false);
	const [buildTriggering, setBuildTriggering] = useState<"compile" | "test" | "docs" | null>(null);
	const [compileResult, setCompileResult] = useState<DbtRunSummary | null>(null);
	const [testResult, setTestResult] = useState<DbtRunSummary | null>(null);
	const [runResult, setRunResult] = useState<DbtRunSummary | null>(null);
	const [columnsLoading, setColumnsLoading] = useState(false);
	const [modelColumns, setModelColumns] = useState<ModelColumn[]>([]);
	const [contractImpactLoading, setContractImpactLoading] = useState(false);
	const [contractImpact, setContractImpact] = useState<SqlModelContractImpact | null>(null);
	const [bottomTab, setBottomTab] = useState("preview");
	const [keyword, setKeyword] = useState("");
	const [activeModelKey, setActiveModelKey] = useState<string | null>(null);
	const [dbtSources, setDbtSources] = useState<DbtSourceItem[]>([]);
	const [dbtRefs, setDbtRefs] = useState<DbtRefItem[]>([]);
	const [sourcesLoading, setSourcesLoading] = useState(false);
	const [refsLoading, setRefsLoading] = useState(false);
	const [snippetDrawerOpen, setSnippetDrawerOpen] = useState(false);
	const [snippetTab, setSnippetTab] = useState<"source" | "ref">("source");
	const [snippetKeyword, setSnippetKeyword] = useState("");
	// FE-004: Execution log state
	const [execLog, setExecLog] = useState<string>("");
	const [execLogLoading, setExecLogLoading] = useState(false);
	// FE-007: Data preview state
	const [previewData, setPreviewData] = useState<{ columns: string[]; rows: any[][] } | null>(null);
	const [previewLoading, setPreviewLoading] = useState(false);
	const [previewLimit, setPreviewLimit] = useState(100);
	// FE-006: Git state
	const [gitStatus, setGitStatus] = useState<{ initialized?: boolean; clean?: boolean; staged?: string[]; unstaged?: string[]; untracked?: string[] } | null>(null);
	const [gitLog, setGitLog] = useState<Array<{ hash?: string; shortMessage?: string; author?: string; date?: string }>>([]);
	const [, setGitDiff] = useState<string>("");
	const [gitLoading, setGitLoading] = useState(false);
	const [gitCommitMsg, setGitCommitMsg] = useState("");
	const [gitCommitting, setGitCommitting] = useState(false);
	const [gitReverting, setGitReverting] = useState<string | null>(null);
	// Rollback state
	const [rollbackOpen, setRollbackOpen] = useState(false);
	const [rollbackRequest, setRollbackRequest] = useState<RollbackRequest | null>(null);
	const [auditLogs, setAuditLogs] = useState<any[]>([]);
	const [auditLogsLoading, setAuditLogsLoading] = useState(false);
	const [form] = Form.useForm();
	const [runForm] = Form.useForm();
	const [modelForm] = Form.useForm();
	const [importForm] = Form.useForm();
	const [odsGenerateForm] = Form.useForm();
	const [singleArchiveForm] = Form.useForm();
	const [batchArchiveForm] = Form.useForm();
	const selectedOdsSourceDataSourceId = Form.useWatch("sourceDataSourceId", odsGenerateForm);
	const dbtSourcesReqSeqRef = useRef(0);

	const loadConfig = useCallback(async () => {
		setConfigLoading(true);
		try {
			const resp = (await getDbtConfig()) as DbtConfigView;
			setDbtConfig(resp || null);
			const cfg = resp?.config;
			form.setFieldsValue({
				projectDir: cfg?.projectDir,
				profilesDir: cfg?.profilesDir,
				profileName: cfg?.profileName,
				targetName: cfg?.targetName,
				database: cfg?.database,
				schema: cfg?.schema,
				vars: cfg?.vars ? JSON.stringify(cfg.vars, null, 2) : "",
			});
		} catch (err: any) {
			toast.error(err?.message || "加载 dbt 配置失败");
		} finally {
			setConfigLoading(false);
		}
	}, [form]);

	const loadSyncStatus = useCallback(async (selector?: string) => {
		try {
			const resp = (await getDbtSyncStatus(selector ? { models: selector } : undefined)) as DbtSyncStatus;
			setDbtSyncStatus(resp || null);
			// Seed per-operation result from latest run on initial load
			const latest = resp?.latestRun || null;
			if (latest?.present) {
				const operation = inferBuildOperationFromCommand(latest.command);
				if (operation === "compile") {
					setCompileResult((prev) => prev || latest);
				} else if (operation === "test") {
					setTestResult((prev) => prev || latest);
				} else if (operation === "run" || operation === "build") {
					setRunResult((prev) => prev || latest);
				}
			}
		} catch {
			setDbtSyncStatus(null);
		}
	}, []);

	const loadModels = useCallback(async () => {
		setModelsLoading(true);
		try {
			const resp = (await listSqlModels()) as SqlModel[];
			setSqlModels(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "加载模型失败");
		} finally {
			setModelsLoading(false);
		}
	}, []);

	const loadSources = useCallback(async () => {
		try {
			const resp = await dataSourcesService.list();
			setDataSources(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "加载数据源失败");
		}
	}, []);

	const loadLayers = useCallback(async () => {
		try {
			const resp = await listTemplateLayers();
			setLayers(Array.isArray(resp) ? resp : []);
		} catch {
			// 如果获取分层失败，使用默认值
			setLayers([
				{ layer: "ODS", name: "ODS", description: "操作数据层（原始数据）" },
				{ layer: "DWD", name: "DWD", description: "明细数据层（清洗数据）" },
				{ layer: "DWS", name: "DWS", description: "汇总数据层（轻度聚合）" },
				{ layer: "ADS", name: "ADS", description: "应用数据层（报表数据）" },
			]);
		}
	}, []);

	const loadSpaces = useCallback(async () => {
		setSpacesLoading(true);
		try {
			const resp = (await listModelingPlans()) as ProjectSpace[];
			setSpaces(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "加载项目空间失败");
		} finally {
			setSpacesLoading(false);
		}
	}, []);

	const loadRuns = useCallback(async () => {
		setRunsLoading(true);
		try {
			const resp = (await listDbtRuns(20)) as Record<string, any>;
			const list = Array.isArray(resp?.dag_runs) ? (resp.dag_runs as DagRun[]) : [];
			setRuns(list);
		} catch (err: any) {
			toast.error(err?.message || "加载运行记录失败");
		} finally {
			setRunsLoading(false);
		}
	}, []);

	const loadModelColumns = useCallback(async (modelId?: string) => {
		if (!modelId) {
			setModelColumns([]);
			return;
		}
		setColumnsLoading(true);
		try {
			const resp = (await listSqlModelColumns(modelId)) as ModelColumn[];
			setModelColumns(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "加载模型字段失败");
			setModelColumns([]);
		} finally {
			setColumnsLoading(false);
		}
	}, []);

	const loadContractImpact = useCallback(async (modelId?: string) => {
		if (!modelId) {
			setContractImpact(null);
			return;
		}
		setContractImpactLoading(true);
		try {
			const resp = (await getSqlModelContractImpact(modelId)) as SqlModelContractImpact;
			setContractImpact(resp || null);
		} catch (err: any) {
			toast.error(err?.message || "加载语义契约影响面失败");
			setContractImpact(null);
		} finally {
			setContractImpactLoading(false);
		}
	}, []);

	const loadDbtSources = useCallback(async (sourceDataSourceId?: string) => {
		const requestSeq = ++dbtSourcesReqSeqRef.current;
		setSourcesLoading(true);
		try {
			const normalizedSourceId = normalizeText(sourceDataSourceId);
			const params = normalizedSourceId ? { sourceDataSourceId: normalizedSourceId } : undefined;
			const resp = (await listDbtSources(params)) as DbtSourceItem[];
			if (requestSeq !== dbtSourcesReqSeqRef.current) return;
			setDbtSources(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			if (requestSeq !== dbtSourcesReqSeqRef.current) return;
			toast.error(err?.message || "加载源表列表失败");
			setDbtSources([]);
		} finally {
			if (requestSeq === dbtSourcesReqSeqRef.current) {
				setSourcesLoading(false);
			}
		}
	}, []);

	const loadDbtRefs = useCallback(async () => {
		setRefsLoading(true);
		try {
			const resp = (await listDbtRefs()) as DbtRefItem[];
			setDbtRefs(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "加载引用模型列表失败");
			setDbtRefs([]);
		} finally {
			setRefsLoading(false);
		}
	}, []);

	// FE-004: Load execution log from Airflow
	const loadExecLog = useCallback(async (dagRunId?: string, dagId?: string) => {
		if (!dagRunId) {
			setExecLog("");
			return;
		}
		setExecLogLoading(true);
		try {
			const resp = await getDbtRunLog(dagRunId, dagId ? { dagId } : undefined);
			setExecLog(typeof resp === "string" ? resp : (resp as any)?.log || (resp as any)?.content || JSON.stringify(resp, null, 2));
		} catch {
			setExecLog("日志加载失败，请稍后重试。");
		} finally {
			setExecLogLoading(false);
		}
	}, []);

	// FE-007: Load data preview
	const loadPreview = useCallback(async (modelName?: string, limit = 100) => {
		if (!modelName) {
			setPreviewData(null);
			return;
		}
		setPreviewLoading(true);
		try {
			const resp = (await previewDbtModel(modelName, limit)) as any;
			const columns: string[] = Array.isArray(resp?.columns) ? resp.columns : [];
			const rows: any[][] = Array.isArray(resp?.rows) ? resp.rows : [];
			setPreviewData({ columns, rows });
		} catch (err: any) {
			toast.error(err?.message || "数据预览失败");
			setPreviewData(null);
		} finally {
			setPreviewLoading(false);
		}
	}, []);

	// FE-006: Load git status + log + diff
	const loadGitInfo = useCallback(async () => {
		setGitLoading(true);
		try {
			const [statusResp, logResp, diffResp] = await Promise.all([
				getDbtGitStatus().catch(() => null),
				getDbtGitLog(20).catch(() => []),
				getDbtGitDiff().catch(() => ""),
			]);
			setGitStatus(statusResp as any);
			setGitLog(Array.isArray(logResp) ? logResp : (logResp as any)?.commits || []);
			setGitDiff(typeof diffResp === "string" ? diffResp : (diffResp as any)?.diff || "");
		} catch {
			setGitStatus(null);
		} finally {
			setGitLoading(false);
		}
	}, []);

	const handleGitCommit = useCallback(async () => {
		if (!gitCommitMsg.trim()) {
			toast.error("请输入提交信息");
			return;
		}
		setGitCommitting(true);
		try {
			await commitDbtChanges({ message: gitCommitMsg.trim() });
			toast.success("提交成功");
			setGitCommitMsg("");
			void loadGitInfo();
		} catch (err: any) {
			toast.error(err?.message || "Git 提交失败");
		} finally {
			setGitCommitting(false);
		}
	}, [gitCommitMsg, loadGitInfo]);

	const handleGitRevert = useCallback(async (path: string) => {
		setGitReverting(path);
		try {
			await revertDbtFile(path);
			toast.success(`已还原: ${path}`);
			void loadGitInfo();
		} catch (err: any) {
			toast.error(err?.message || "还原失败");
		} finally {
			setGitReverting(null);
		}
	}, [loadGitInfo]);

	// --- Rollback helpers ---
	const loadAuditLogs = async (dataSourceId?: string) => {
		setAuditLogsLoading(true);
		try {
			const resp: any = await getRollbackAuditLog({ dataSourceId });
			const data = resp?.data || resp;
			setAuditLogs(Array.isArray(data) ? data : []);
		} catch {
			// global interceptor
		} finally {
			setAuditLogsLoading(false);
		}
	};

	const openRollback = (level: number, rebuildDbt?: boolean) => {
		if (!activeModel?.name) {
			toast.error("请先选择一个模型");
			return;
		}
		const tables = [activeModel.name];
		const dsId = dbtConfig?.config?.targetDataSourceId;
		setRollbackRequest({
			level,
			scope: "task",
			dataSourceId: dsId,
			tables,
			rebuildDbt: rebuildDbt || false,
		});
		setRollbackOpen(true);
		if (dsId) void loadAuditLogs(dsId);
	};

	useEffect(() => {
		void loadConfig();
		void loadSyncStatus();
		void loadModels();
		void loadRuns();
		void loadSpaces();
		void loadSources();
		void loadLayers();
		void loadDbtSources();
		void loadDbtRefs();
	}, [loadConfig, loadModels, loadRuns, loadSpaces, loadSources, loadLayers, loadSyncStatus, loadDbtSources, loadDbtRefs]);

	useEffect(() => {
		if (spaces.length === 0 && sqlModels.every((model) => !!model.planId)) {
			if (activeSpaceKey) {
				setActiveSpaceKey(null);
			}
			return;
		}
		const hasActiveSpace = !!activeSpaceKey && spaces.some((space, idx) => resolveSpaceKey(space, idx) === activeSpaceKey);
		const hasUnassigned = sqlModels.some((model) => !model.planId);
		const hasActive = hasActiveSpace || (hasUnassigned && activeSpaceKey === UNASSIGNED_SPACE_KEY);
		if (!hasActive) {
			if (spaces.length > 0) {
				setActiveSpaceKey(resolveSpaceKey(spaces[0], 0));
			} else if (hasUnassigned) {
				setActiveSpaceKey(UNASSIGNED_SPACE_KEY);
			}
		}
	}, [activeSpaceKey, spaces, sqlModels]);

	const saveConfig = async () => {
		setConfigSaving(true);
		try {
			const values = await form.validateFields(["projectDir", "profilesDir"]);
			const payload = {
				projectDir: normalizeText(values.projectDir),
				profilesDir: normalizeText(values.profilesDir),
				profileName: normalizeText(form.getFieldValue("profileName")) || undefined,
				targetName: normalizeText(form.getFieldValue("targetName")) || undefined,
				targetDataSourceId: dbtConfig?.config?.targetDataSourceId ?? undefined,
				database: normalizeText(form.getFieldValue("database")) || undefined,
				schema: normalizeText(form.getFieldValue("schema")) || undefined,
				vars: tryParseJsonObject(form.getFieldValue("vars")),
			};
			await updateDbtConfig(payload);
			toast.success("dbt 配置已保存");
			await loadConfig();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setConfigSaving(false);
		}
	};

	const openRun = () => {
		runForm.resetFields();
		const dagSelector = normalizeText(activeModel?.dagSelector);
		const selector = dagSelector.startsWith("tab:")
			? `tag:${dagSelector.slice(4)}`
			: dagSelector || (activeModel?.name ? `model:${activeModel.name}` : "");
		runForm.setFieldsValue({
			models: selector,
			target: dbtConfig?.config?.targetName || "",
			vars: "",
			gitRef: "",
			commitSha: "",
			strictMode: false,
		});
		setRunOpen(true);
	};

	const waitForBuildResult = useCallback(
		async (
			operation: "compile" | "test" | "docs",
			selector: string,
			dagId: string | undefined,
			dagRunId: string | undefined,
			baselineRun: DbtRunSummary | null,
		) => {
			let lastStatus: DbtSyncStatus | null = null;
			let lastSummary: DbtRunSummary | null = null;
			let lastDagState = "";
			for (let attempt = 0; attempt < 30; attempt += 1) {
				if (dagId && dagRunId) {
					try {
						const runsPayload = (await listAirflowJobRuns(dagId, 20)) as { dag_runs?: DagRun[] };
						const dagRuns = Array.isArray(runsPayload?.dag_runs) ? runsPayload.dag_runs : [];
						const matchedRun = dagRuns.find((run) => {
							const runId = normalizeText(run?.dag_run_id);
							return runId && runId === normalizeText(dagRunId);
						});
						lastDagState = normalizeLower(matchedRun?.state);
					} catch {
						lastDagState = "";
					}
				}
				try {
					lastStatus = (await getDbtSyncStatus(selector ? { models: selector } : undefined)) as DbtSyncStatus;
					lastSummary = lastStatus?.latestRun || null;
				} catch {
					lastStatus = null;
					lastSummary = null;
				}

				if (
					matchesTriggeredBuildSummary(lastSummary, {
						operation,
						selector,
						baselineGeneratedAt: baselineRun?.generatedAt,
						baselineInvocationId: baselineRun?.invocationId,
					})
				) {
					return { syncStatus: lastStatus, latestRun: lastSummary, dagState: lastDagState, timedOut: false };
				}
				if (lastDagState === "failed") {
					return { syncStatus: lastStatus, latestRun: lastSummary, dagState: lastDagState, timedOut: false };
				}
				await new Promise((resolve) => setTimeout(resolve, 2000));
			}
			return { syncStatus: lastStatus, latestRun: lastSummary, dagState: lastDagState, timedOut: true };
		},
		[],
	);

	const submitRun = async () => {
		setRunSubmitting(true);
		try {
			const values = await runForm.validateFields(["models"]);
			const modelsSelector = normalizeText(values.models).startsWith("tab:")
				? `tag:${normalizeText(values.models).slice(4)}`
				: normalizeText(values.models);
			const gate = (await checkDbtQualityGate({ models: modelsSelector })) as DbtQualityGateResult;
			if (gate?.blocking) {
				Modal.error({
					title: "质量门禁阻断",
					content: (
						<div style={{ fontSize: 12 }}>
							<p>当前不满足发布条件，请先修复后重试。</p>
							<ul style={{ paddingLeft: 18, margin: 0 }}>
								{(gate.blockers || []).map((item, idx) => (
									<li key={`${item}-${idx}`}>{item}</li>
								))}
							</ul>
						</div>
					),
				});
				return;
			}
			if (gate?.warning) {
				const confirmed = await new Promise<boolean>((resolve) =>
					Modal.confirm({
						title: "质量门禁告警",
						content: (
							<div style={{ fontSize: 12 }}>
								<p>检测到以下告警，是否继续提交变更？</p>
								<ul style={{ paddingLeft: 18, margin: 0 }}>
									{(gate.warnings || []).map((item, idx) => (
										<li key={`${item}-${idx}`}>{item}</li>
									))}
								</ul>
							</div>
						),
						okText: "继续上线",
						cancelText: "取消",
						onOk: () => resolve(true),
						onCancel: () => resolve(false),
					}),
				);
				if (!confirmed) {
					return;
				}
			}
			const releaseGate = (await checkDbtReleaseGate({
				models: modelsSelector,
				gitRef: normalizeText(values.gitRef) || undefined,
				commitSha: normalizeText(values.commitSha) || undefined,
				strictMode: values.strictMode !== false,
			})) as DbtReleaseGateResult;
			if (releaseGate?.blocking) {
				Modal.error({
					title: "发布门禁阻断",
					content: (
						<div style={{ fontSize: 12 }}>
							<p>当前不满足发布门禁，请先修复后重试。</p>
							<ul style={{ paddingLeft: 18, margin: 0 }}>
								{(releaseGate.blockers || []).map((item, idx) => (
									<li key={`${item}-${idx}`}>{item}</li>
								))}
							</ul>
						</div>
					),
				});
				return;
			}
			if (releaseGate?.warning) {
				const confirmed = await new Promise<boolean>((resolve) =>
					Modal.confirm({
						title: "发布门禁告警",
						content: (
							<div style={{ fontSize: 12 }}>
								<p>检测到以下告警，是否继续提交变更？</p>
								<ul style={{ paddingLeft: 18, margin: 0 }}>
									{(releaseGate.warnings || []).map((item, idx) => (
										<li key={`${item}-${idx}`}>{item}</li>
									))}
								</ul>
							</div>
						),
						okText: "继续上线",
						cancelText: "取消",
						onOk: () => resolve(true),
						onCancel: () => resolve(false),
					}),
				);
				if (!confirmed) {
					return;
				}
			}
			await triggerDbtRun({
				models: buildReleaseSelector(modelsSelector),
				dagSelector: modelsSelector,
				operation: "build",
				target: normalizeText(values.target) || undefined,
				vars: tryParseJsonObject(values.vars),
				gitRef: normalizeText(values.gitRef) || undefined,
				commitSha: normalizeText(values.commitSha) || undefined,
				buildInvocationId: normalizeText(releaseGate?.buildEvidence?.invocationId) || undefined,
			});
			toast.success("dbt build 已提交");
			setRunOpen(false);
			await loadRuns();
		} catch (err: any) {
			toast.error(err?.message || "触发失败");
		} finally {
			setRunSubmitting(false);
		}
	};

	const triggerBuildOperation = async (operation: "compile" | "test" | "docs") => {
		setBuildTriggering(operation);
		try {
			const selector = normalizeText(activeModel?.dagSelector) || (activeModel?.name ? `model:${activeModel.name}` : "all");
			const baselineStatus = (await getDbtSyncStatus(selector ? { models: selector } : undefined)) as DbtSyncStatus;
			const baselineRun = baselineStatus?.latestRun || null;
			const payload = {
				models: selector,
				target: normalizeText(dbtConfig?.config?.targetName) || "dev",
			};
			let triggerResp: any;
			if (operation === "compile") {
				triggerResp = await triggerDbtCompile(payload);
			} else if (operation === "test") {
				triggerResp = await triggerDbtTest(payload);
			} else {
				triggerResp = await triggerDbtDocs(payload);
			}
			const dagRunId = normalizeText(triggerResp?.dag_run_id || triggerResp?.dagRunId);
			const dagId = normalizeText(triggerResp?.dag_id || triggerResp?.dagId);
			const pendingRun = {
				...createPendingBuildSummary(operation, selector),
				dagRunId,
				dagId,
			} as DbtRunSummary;
			toast.success(`dbt ${operation} 已提交，正在等待结果`);
			setExecLog("");
			if (operation === "compile") {
				setCompileResult(pendingRun);
				setBottomTab("compile");
			} else if (operation === "test") {
				setTestResult(pendingRun);
				setBottomTab("test");
			} else {
				setRunResult(pendingRun);
				setBottomTab("execlog");
			}
			const settled = await waitForBuildResult(operation, selector, dagId || undefined, dagRunId || undefined, baselineRun);
			if (settled.syncStatus) {
				setDbtSyncStatus(settled.syncStatus);
			} else {
				await loadSyncStatus(selector);
			}
			await loadRuns();

			let resolvedRun = settled.latestRun || null;
			if (resolvedRun) {
				resolvedRun = {
					...resolvedRun,
					dagRunId: dagRunId || resolvedRun.dagRunId,
					dagId: dagId || resolvedRun.dagId,
				};
			}
			if (!resolvedRun && settled.dagState === "failed") {
				resolvedRun = {
					...createFailedBuildSummary(operation, selector, `dbt ${operation} 失败，请查看执行日志`),
					dagRunId,
					dagId,
				} as DbtRunSummary;
			}
			if (operation === "compile") {
				setCompileResult(resolvedRun || pendingRun);
			} else if (operation === "test") {
				setTestResult(resolvedRun || pendingRun);
			} else {
				setRunResult(resolvedRun || pendingRun);
			}

			const finalStatus = normalizeUpper(resolvedRun?.status);
			if (settled.dagState === "failed") {
				if (dagRunId) {
					await loadExecLog(dagRunId, dagId || undefined);
					setBottomTab("execlog");
				}
				toast.error(`dbt ${operation} 失败`);
				return;
			}
			if (finalStatus === "SUCCESS") {
				toast.success(`dbt ${operation} 已完成`);
				return;
			}
			if (finalStatus === "SKIPPED") {
				toast.warning(`dbt ${operation} 返回 SKIPPED，请重新执行后再提交变更`);
				return;
			}
			if (settled.timedOut) {
				toast.warning(`dbt ${operation} 仍在运行，请稍后刷新结果`);
			}
		} catch (err: any) {
			toast.error(err?.message || `dbt ${operation} 触发失败`);
		} finally {
			setBuildTriggering(null);
		}
	};

	const handleSyncModels = async () => {
		setSyncingModels(true);
		try {
			const result: any = await syncDbtModels();
			const message = result?.message || result?.summary || "模型已同步至资产目录";
			const stats = result?.stats;
			if (stats) {
				const detail = `新增${stats.created ?? 0}，更新${stats.updated ?? 0}，字段${stats.columnsUpdated ?? 0}，血缘+${stats.lineageCreated ?? 0}/-${stats.lineageRemoved ?? 0}`;
				toast.success(`${message}（${detail}）`);
			} else {
				toast.success(message);
			}
			void loadSyncStatus();
			await loadModels();
		} catch (err: any) {
			toast.error(err?.message || "同步模型失败");
		} finally {
			setSyncingModels(false);
		}
	};

	const openSingleArchive = () => {
		if (!canArchiveActiveModel) return;
		singleArchiveForm.resetFields();
		singleArchiveForm.setFieldsValue({ planId: defaultArchivePlanId });
		setSingleArchiveOpen(true);
	};

	const openBatchArchive = () => {
		if (!canBatchArchive) return;
		batchArchiveForm.resetFields();
		batchArchiveForm.setFieldsValue({ planId: defaultArchivePlanId });
		setBatchArchiveSelection(collectUnassignedModelIds(unassignedModels));
		setBatchArchiveOpen(true);
	};

	const archiveModelToPlan = async (model: SqlModel, planId: string) => {
		if (!model?.id) {
			throw new Error("模型缺少 ID，无法归档");
		}
		await updateSqlModel(model.id, buildArchivePayload(model, planId));
	};

	const submitSingleArchive = async () => {
		if (!activeModel) return;
		setArchiveSubmitting(true);
		try {
			const values = await singleArchiveForm.validateFields(["planId"]);
			const planId = String(values.planId || "");
			await archiveModelToPlan(activeModel, planId);
			toast.success("模型已归档到项目空间");
			setSingleArchiveOpen(false);
			setActiveSpaceKey(`space-${planId}`);
			await loadModels();
		} catch (err: any) {
			toast.error(err?.message || "归档失败");
		} finally {
			setArchiveSubmitting(false);
		}
	};

	const submitBatchArchive = async () => {
		setArchiveSubmitting(true);
		try {
			const values = await batchArchiveForm.validateFields(["planId"]);
			const planId = String(values.planId || "");
			const selectedIds = batchArchiveSelection.filter(Boolean);
			if (selectedIds.length === 0) {
				throw new Error("请至少选择一个未归档模型");
			}
			const byId = new Map(unassignedModels.filter((model) => model.id).map((model) => [model.id as string, model]));
			const results = await Promise.allSettled(
				selectedIds.map((id) => {
					const model = byId.get(id);
					if (!model) {
						return Promise.reject(new Error(`模型不存在: ${id}`));
					}
					return archiveModelToPlan(model, planId);
				}),
			);
			const successCount = results.filter((result) => result.status === "fulfilled").length;
			const failedCount = results.length - successCount;
			if (successCount === 0) {
				throw new Error("批量归档全部失败");
			}
			if (failedCount === 0) {
				toast.success(`已归档 ${successCount} 个模型`);
			} else {
				toast.warning(`已归档 ${successCount} 个模型，失败 ${failedCount} 个`);
			}
			setBatchArchiveOpen(false);
			setActiveSpaceKey(`space-${planId}`);
			await loadModels();
		} catch (err: any) {
			toast.error(err?.message || "批量归档失败");
		} finally {
			setArchiveSubmitting(false);
		}
	};

	const openCreateModel = () => {
		setEditingModel(null);
		modelForm.resetFields();
		modelForm.setFieldsValue({
			planId: activeSpace?.id || undefined,
			layer: "DWD",
			materialized: "table",
			enabled: true,
			semanticContract: "",
			sql: "select\n  *\nfrom {{ source('ods', 'your_table') }}\n",
		});
		setModelDrawerOpen(true);
	};

	const openImportModel = () => {
		importForm.resetFields();
		setSqlFileList([]);
		setCsvFileList([]);
		importForm.setFieldsValue({
			planId: activeSpace?.id || undefined,
			layer: "DWD",
			materialized: "table",
			enabled: true,
		});
		setImportOpen(true);
	};

	const openOdsGenerateModel = () => {
		odsGenerateForm.resetFields();
		odsGenerateForm.setFieldsValue({
			planId: activeSpace?.id || undefined,
			materialized: "table",
			enabled: true,
			status: "DRAFT",
			createDwd: true,
			createDws: true,
			createAds: true,
			overwriteExisting: true,
		});
		setOdsGenerateOpen(true);
	};

	const openEditModel = () => {
		if (!activeModel) return;
		setEditingModel(activeModel);
		modelForm.setFieldsValue({
			planId: activeModel.planId,
			name: activeModel.name,
			alias: activeModel.alias,
			layer: activeModel.layer || inferLayer(activeModel.name),
			sourceDataSourceId: activeModel.sourceDataSourceId,
			schemaName: activeModel.schemaName,
			materialized: activeModel.materialized,
			tags: activeModel.tags,
			description: activeModel.description,
			sql: activeModel.sql,
			enabled: activeModel.enabled,
			status: activeModel.status,
			semanticContract: prettyJson(activeModel.semanticContract),
		});
		setModelDrawerOpen(true);
	};

	const submitModel = async () => {
		setModelSubmitting(true);
		try {
			const values = await modelForm.validateFields([
				"planId",
				"layer",
				"name",
				"sourceDataSourceId",
				"sql",
			]);
			const payload = {
				planId: values.planId,
				name: normalizeText(values.name),
				alias: normalizeText(values.alias) || undefined,
				layer: normalizeText(values.layer) || undefined,
				sourceDataSourceId: values.sourceDataSourceId,
				schemaName: normalizeText(values.schemaName) || undefined,
				materialized: normalizeText(values.materialized) || undefined,
				tags: normalizeText(values.tags) || undefined,
				description: normalizeText(values.description) || undefined,
				sql: values.sql,
				enabled: values.enabled ?? true,
				status: normalizeText(values.status) || undefined,
				semanticContract: values.semanticContract !== undefined ? String(values.semanticContract) : "",
			};
			if (editingModel?.id) {
				await updateSqlModel(editingModel.id, payload);
				toast.success("模型已更新");
			} else {
				await createSqlModel(payload);
				toast.success("模型已创建");
			}
			setModelDrawerOpen(false);
			await loadModels();
		} catch (err: any) {
			toast.error(err?.message || "保存模型失败");
		} finally {
			setModelSubmitting(false);
		}
	};

	const submitImport = async () => {
		setImportSubmitting(true);
		try {
			const values = await importForm.validateFields([
				"planId",
				"layer",
				"name",
				"sourceDataSourceId",
			]);
			if (sqlFileList.length === 0 || !sqlFileList[0]?.originFileObj) {
				throw new Error("请选择 SQL 文件");
			}
			const formData = new FormData();
			formData.append("planId", values.planId);
			formData.append("name", normalizeText(values.name));
			formData.append("layer", normalizeText(values.layer));
			formData.append("sourceDataSourceId", values.sourceDataSourceId);
			if (values.alias) formData.append("alias", normalizeText(values.alias));
			if (values.schemaName) formData.append("schemaName", normalizeText(values.schemaName));
			if (values.materialized) formData.append("materialized", normalizeText(values.materialized));
			if (values.tags) formData.append("tags", normalizeText(values.tags));
			if (values.description) formData.append("description", normalizeText(values.description));
			if (values.status) formData.append("status", normalizeText(values.status));
			if (values.ownerDept) formData.append("ownerDept", normalizeText(values.ownerDept));
			formData.append("enabled", String(values.enabled ?? true));
			formData.append("sql", sqlFileList[0].originFileObj as File);
			if (csvFileList.length > 0 && csvFileList[0]?.originFileObj) {
				formData.append("csv", csvFileList[0].originFileObj as File);
			}
			await importSqlModel(formData);
			toast.success("模型已导入");
			setImportOpen(false);
			await loadModels();
		} catch (err: any) {
			toast.error(err?.message || "导入失败");
		} finally {
			setImportSubmitting(false);
		}
	};

	const submitOdsGenerate = async () => {
		setOdsGenerateSubmitting(true);
		try {
			const values = await odsGenerateForm.validateFields(["planId", "mappingIds"]);
			const payload = {
				planId: values.planId,
				sourceDataSourceId: values.sourceDataSourceId || undefined,
				mappingIds: Array.isArray(values.mappingIds) ? values.mappingIds : [],
				schemaName: normalizeText(values.schemaName) || undefined,
				materialized: normalizeText(values.materialized) || undefined,
				tags: normalizeText(values.tags) || undefined,
				ownerDept: normalizeText(values.ownerDept) || undefined,
				enabled: values.enabled ?? true,
				status: normalizeText(values.status) || undefined,
				createDwd: values.createDwd !== false,
				createDws: values.createDws !== false,
				createAds: values.createAds !== false,
				overwriteExisting: values.overwriteExisting !== false,
			};
			const result = (await generateSqlModelsFromOds(payload)) as SqlModelOdsGenerateResult;
			const summary = `已处理 ${result?.mappingsTotal || 0} 个 ODS 表，新增 ${result?.modelsCreated || 0}，更新 ${result?.modelsUpdated || 0}`;
			const skippedEntries = (result?.skipped || []).map((item) => parseOdsSkippedEntry(item));
			const failedEntries = skippedEntries.filter((item) => item.severity === "error");
			const reasonCount = new Map<string, number>();
			for (const entry of skippedEntries) {
				reasonCount.set(entry.reason, (reasonCount.get(entry.reason) || 0) + 1);
			}
			const reasonSummary = Array.from(reasonCount.entries())
				.map(([reason, count]) => ({ reason, count }))
				.sort((a, b) => b.count - a.count || a.reason.localeCompare(b.reason, "zh-CN"));

			Modal.info({
				title: "一键生成结果",
				width: 760,
				content: (
					<div>
						<p>{summary}</p>
						<p style={{ marginTop: 8, color: "rgba(0,0,0,0.65)" }}>
							系统已按所选映射生成或更新 DWD / DWS / ADS 模型模板。
						</p>
						<p style={{ marginTop: 8, color: "rgba(0,0,0,0.65)" }}>
							质量模板：已生成 {result?.qualityTemplatesGenerated || 0}
							{(result?.qualitySkipped || []).length > 0 ? `，跳过 ${(result?.qualitySkipped || []).length}` : ""}
						</p>
						{(result?.qualitySkipped || []).length > 0 && (
							<div
								style={{
									marginTop: 10,
									padding: "8px 10px",
									border: "1px solid #f0f0f0",
									borderRadius: 6,
									background: "#fafafa",
								}}
							>
								<p style={{ marginBottom: 6, fontWeight: 600 }}>质量模板跳过项</p>
								<ul style={{ maxHeight: 120, overflow: "auto", fontSize: 12, paddingLeft: 20 }}>
									{(result?.qualitySkipped || []).map((item, idx) => (
										<li key={`${item}-${idx}`}>{item}</li>
									))}
								</ul>
							</div>
						)}
						{skippedEntries.length > 0 && (
							<>
								<div
									style={{
										marginTop: 10,
										padding: "8px 10px",
										border: "1px solid #f0f0f0",
										borderRadius: 6,
										background: "#fafafa",
									}}
								>
									<p style={{ marginBottom: 6, fontWeight: 600 }}>跳过原因统计</p>
									<div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
										{reasonSummary.map((item) => (
											<Tag key={item.reason}>{`${item.reason}: ${item.count}`}</Tag>
										))}
									</div>
								</div>
								<Tabs
									size="small"
									style={{ marginTop: 10 }}
									items={[
										{
											key: "all",
											label: `全部跳过 (${skippedEntries.length})`,
											children: (
												<ul style={{ maxHeight: 220, overflow: "auto", fontSize: 12, paddingLeft: 20 }}>
													{skippedEntries.map((entry, idx) => (
														<li key={`${entry.raw}-${idx}`}>
															{skippedSeverityTag(entry.severity)}
															<span>{entry.raw}</span>
														</li>
													))}
												</ul>
											),
										},
										{
											key: "failed",
											label: `仅失败项 (${failedEntries.length})`,
											children:
												failedEntries.length > 0 ? (
													<ul style={{ maxHeight: 220, overflow: "auto", fontSize: 12, paddingLeft: 20 }}>
														{failedEntries.map((entry, idx) => (
															<li key={`${entry.raw}-${idx}`}>
															{skippedSeverityTag(entry.severity)}
															<span>{entry.raw}</span>
														</li>
														))}
													</ul>
												) : (
													<div style={{ fontSize: 12, color: "rgba(0,0,0,0.45)" }}>无失败项</div>
												),
										},
									]}
								/>
							</>
						)}
						<div
							style={{
								marginTop: 12,
								padding: "8px 10px",
								border: "1px solid #f0f0f0",
								borderRadius: 6,
								background: "#fafafa",
							}}
						>
							<p style={{ marginBottom: 6, fontWeight: 600 }}>下一步建议</p>
							<ol style={{ margin: 0, paddingLeft: 20, fontSize: 12 }}>
								<li>检查模型列表中的命名与分层是否符合预期。</li>
								<li>进入 dbt 文件浏览器按业务口径微调 SQL 并运行 dbt。</li>
								<li>完成验证后回到逻辑建模执行“提交变更”。</li>
							</ol>
						</div>
					</div>
				),
			});
			setOdsGenerateOpen(false);
			setActiveSpaceKey(`space-${values.planId}`);
			await loadModels();
		} catch (err: any) {
			toast.error(err?.message || "生成失败");
		} finally {
			setOdsGenerateSubmitting(false);
		}
	};

	const removeModel = () => {
		if (!activeModel?.id) return;
		Modal.confirm({
			title: "删除模型？",
			content: `确认删除模型 ${activeModel.name || ""} 吗？`,
			onOk: async () => {
				try {
					await deleteSqlModel(activeModel.id as string);
					toast.success("模型已删除");
					setActiveModelKey(null);
					await loadModels();
				} catch (err: any) {
					toast.error(err?.message || "删除失败");
				}
			},
		});
	};

	const saveSqlDraft = async () => {
		if (!activeModel?.id) return;
		try {
			const payload = {
				planId: activeModel.planId,
				name: activeModel.name,
				alias: activeModel.alias,
				layer: activeModel.layer,
				sourceDataSourceId: activeModel.sourceDataSourceId,
				schemaName: activeModel.schemaName,
				materialized: activeModel.materialized,
				tags: activeModel.tags,
				description: activeModel.description,
				sql: sqlDraft,
				enabled: activeModel.enabled ?? true,
				status: activeModel.status,
			};
			await updateSqlModel(activeModel.id, payload);
			toast.success("SQL 已保存");
			await loadModels();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		}
	};

	const insertSnippet = (snippet: string) => {
		const newSql = sqlDraft + "\n" + snippet;
		setSqlDraft(newSql);
		setSnippetDrawerOpen(false);
		toast.success("代码片段已插入");
	};

	const openSnippetDrawer = (tab: "source" | "ref") => {
		setSnippetTab(tab);
		setSnippetKeyword("");
		setSnippetDrawerOpen(true);
	};

	const filteredDbtSources = useMemo(() => {
		if (!snippetKeyword.trim()) return dbtSources;
		const kw = snippetKeyword.toLowerCase();
		return dbtSources.filter(
			(s) =>
				(s.table || "").toLowerCase().includes(kw) ||
				(s.schema || "").toLowerCase().includes(kw) ||
				(s.description || "").toLowerCase().includes(kw) ||
				(s.systemCode || "").toLowerCase().includes(kw),
		);
	}, [dbtSources, snippetKeyword]);

	const filteredDbtRefs = useMemo(() => {
		if (!snippetKeyword.trim()) return dbtRefs;
		const kw = snippetKeyword.toLowerCase();
		return dbtRefs.filter(
			(r) =>
				(r.name || "").toLowerCase().includes(kw) ||
				(r.layer || "").toLowerCase().includes(kw) ||
				(r.description || "").toLowerCase().includes(kw) ||
				(r.tags || "").toLowerCase().includes(kw),
		);
	}, [dbtRefs, snippetKeyword]);

	const odsSourceOptions = useMemo(() => {
		return dbtSources
			.filter((item) => !!item.id)
			.map((item) => {
				const schema = normalizeText(item.schema) || "public";
				const rawTable = normalizeText(item.table);
				const prefix = schema + ".";
				const table = rawTable.toLowerCase().startsWith(prefix.toLowerCase()) ? rawTable.slice(prefix.length) : rawTable;
				return {
					label: table ? schema + "." + table : schema,
					value: item.id as string,
				};
			});
	}, [dbtSources]);

	const odsSourceFilterOptions = useMemo(() => {
		const dataSourceNameById = new Map<string, string>();
		for (const ds of dataSources) {
			const id = normalizeText(String(ds?.id || ""));
			if (!id) continue;
			const name = normalizeText(ds.name);
			if (name) {
				dataSourceNameById.set(id, name);
			}
		}

		const optionsById = new Map<string, string>();
		for (const item of dbtSources) {
			const sourceId = normalizeText(item.sourceDataSourceId);
			if (!sourceId || optionsById.has(sourceId)) continue;
			const label =
				dataSourceNameById.get(sourceId) ||
				normalizeText(item.sourceDataSourceName) ||
				normalizeText(item.systemCode) ||
				"未知来源";
			optionsById.set(sourceId, label);
		}

		return Array.from(optionsById.entries())
			.map(([value, label]) => ({ value, label }))
			.sort((a, b) => a.label.localeCompare(b.label, "zh-CN"));
	}, [dataSources, dbtSources]);

	useEffect(() => {
		if (!odsGenerateOpen) return;
		const selectedSourceId = normalizeText(selectedOdsSourceDataSourceId) || undefined;
		void loadDbtSources(selectedSourceId);
	}, [loadDbtSources, odsGenerateOpen, selectedOdsSourceDataSourceId]);

	useEffect(() => {
		if (!odsGenerateOpen) return;
		const selectedSourceId = normalizeText(selectedOdsSourceDataSourceId);
		if (!selectedSourceId) return;
		const exists = odsSourceFilterOptions.some((item) => normalizeText(item.value) === selectedSourceId);
		if (!exists) {
			odsGenerateForm.setFieldValue("sourceDataSourceId", undefined);
			void loadDbtSources(undefined);
		}
	}, [loadDbtSources, odsGenerateForm, odsGenerateOpen, odsSourceFilterOptions, selectedOdsSourceDataSourceId]);

	useEffect(() => {
		if (!odsGenerateOpen) return;
		const current = odsGenerateForm.getFieldValue("mappingIds");
		if (!Array.isArray(current) || current.length === 0) return;
		const allowed = new Set(odsSourceOptions.map((item) => String(item.value)));
		const next = current.map((item: any) => String(item)).filter((item: string) => allowed.has(item));
		if (next.length !== current.length) {
			odsGenerateForm.setFieldValue("mappingIds", next);
		}
	}, [odsGenerateOpen, odsGenerateForm, odsSourceOptions]);

	const models = sqlModels || [];
	const configEnabled = dbtConfig?.enabled !== false;
	const profileStatus = dbtConfig?.profileStatus;
	const workspaceStatus = dbtConfig?.workspaceStatus;
	const workspaceOk = workspaceStatus?.ok !== false;

	const modelKeyMap = useMemo(() => {
		const map = new Map<string, SqlModel>();
		models.forEach((model, idx) => {
			const key = resolveModelKey(model, `model-${idx}`);
			map.set(key, model);
		});
		return map;
	}, [models]);

	useEffect(() => {
		if (!activeModelKey && models.length > 0) {
			const key = resolveModelKey(models[0], "model-0");
			setActiveModelKey(key);
		}
	}, [activeModelKey, models]);

	useEffect(() => {
		if (activeModelKey && !modelKeyMap.has(activeModelKey) && models.length > 0) {
			const key = resolveModelKey(models[0], "model-0");
			setActiveModelKey(key);
		}
	}, [activeModelKey, modelKeyMap, models]);

	const activeModel = useMemo(() => {
		if (!activeModelKey) return null;
		return modelKeyMap.get(activeModelKey) || null;
	}, [activeModelKey, modelKeyMap]);

	useEffect(() => {
		setSqlDraft(activeModel?.sql || "");
	}, [activeModel?.id]);

	useEffect(() => {
		void loadModelColumns(activeModel?.id);
	}, [activeModel?.id, loadModelColumns]);

	useEffect(() => {
		void loadContractImpact(activeModel?.id);
	}, [activeModel?.id, loadContractImpact]);

	const sqlDirty = !!activeModel && sqlDraft !== (activeModel?.sql || "");

	const filteredModels = useMemo(() => {
		const key = normalizeText(keyword).toLowerCase();
		if (!key) return models;
		return models.filter((model) => {
			const name = (model.name || "").toLowerCase();
			const alias = (model.alias || "").toLowerCase();
			return name.includes(key) || alias.includes(key);
		});
	}, [keyword, models]);

	const unassignedModels = useMemo(
		() => filteredModels.filter((model) => !model.planId),
		[filteredModels],
	);


	const buildLayerNodes = useCallback((input: SqlModel[]) => {
		const layers = new Map<string, SqlModel[]>();
		input.forEach((model) => {
			const layer = model.layer || inferLayer(model.name);
			const list = layers.get(layer) || [];
			list.push(model);
			layers.set(layer, list);
		});
		const order = ["ODS", "DWD", "DWS", "ADS", "其他"];
		return Array.from(layers.entries())
			.sort((a, b) => order.indexOf(a[0]) - order.indexOf(b[0]))
			.map(([layer, list]) => ({
				title: `${layer}_层 (${list.length})`,
				key: `layer-${layer}`,
				children: list.map((model, idx) => ({
					title: <span className="flex items-center gap-1"><span className="truncate">{model.name || model.alias || "未命名模型"}</span>{model.status && model.status !== "DRAFT" && <span className={`inline-block rounded px-1 text-[10px] leading-4 ${model.status === "PUBLISHED" ? "bg-green-500/15 text-green-600" : model.status === "TESTED" ? "bg-orange-500/15 text-orange-600" : "bg-blue-500/15 text-blue-600"}`}>{model.status === "PUBLISHED" ? "已发布" : model.status === "TESTED" ? "已测试" : model.status === "COMMITTED" ? "已提交" : model.status}</span>}</span>,
					key: `model:${resolveModelKey(model, `${layer}-${idx}`)}`,
					isLeaf: true,
				})),
			}));
	}, []);

	const runColumns: ColumnsType<DagRun> = useMemo(
		() => [
			{ title: "运行 ID", dataIndex: "dag_run_id", key: "dag_run_id", width: 220, ellipsis: true },
			{
				title: "状态",
				dataIndex: "state",
				key: "state",
				width: 120,
				render: (v) => {
					const label = normalizeUpper(v) || "UNKNOWN";
					const color = label === "SUCCESS" ? "green" : label === "FAILED" ? "red" : "gold";
					return <Tag color={color}>{label}</Tag>;
				},
			},
			{
				title: "操作",
				key: "operation",
				width: 100,
				render: (_value, row) => normalizeUpper(row?.conf?.operation) || "RUN",
			},
			{
				title: "Selector",
				key: "selector",
				width: 220,
				ellipsis: true,
				render: (_value, row) => normalizeText(row?.conf?.models) || "all",
			},
			{
				title: "Target",
				key: "target",
				width: 120,
				render: (_value, row) => normalizeText(row?.conf?.target) || "dev",
			},
			{ title: "计划时间", dataIndex: "execution_date", key: "execution_date", width: 180, render: (v) => formatDateTime(v) },
			{ title: "开始时间", dataIndex: "start_date", key: "start_date", width: 180, render: (v) => formatDateTime(v) },
			{ title: "结束时间", dataIndex: "end_date", key: "end_date", width: 180, render: (v) => formatDateTime(v) },
			{
				title: "操作",
				key: "actions",
				width: 80,
				render: (_value, row) => (
					<Button
						size="small"
						type="link"
						icon={<FileTextOutlined />}
						onClick={() => {
							if (row.dag_run_id) {
								setBottomTab("execlog");
								loadExecLog(row.dag_run_id, normalizeText(row?.dag_id) || undefined);
							}
						}}
					>
						日志
					</Button>
				),
			},
		],
		[loadExecLog],
	);

	const modelColumnColumns: ColumnsType<ModelColumn> = useMemo(
		() => [
			{ title: "字段", dataIndex: "name", key: "name", ellipsis: true },
			{ title: "类型", dataIndex: "dataType", key: "dataType", width: 120, ellipsis: true },
			{
				title: "状态",
				dataIndex: "status",
				key: "status",
				width: 100,
				render: (value) => {
					const label = normalizeUpper(value);
					if (!label) return <Tag>未知</Tag>;
					if (label === "DRAFT") return <Tag color="orange">草稿</Tag>;
					if (label === "ACTIVE") return <Tag color="green">正式</Tag>;
					return <Tag>{value}</Tag>;
				},
			},
		],
		[],
	);

	const spaceKeyMap = useMemo(() => {
		const map = new Map<string, ProjectSpace>();
		spaces.forEach((space, idx) => {
			map.set(resolveSpaceKey(space, idx), space);
		});
		return map;
	}, [spaces]);

	const activeSpace = useMemo(() => {
		if (!activeSpaceKey) return null;
		return spaceKeyMap.get(activeSpaceKey) || null;
	}, [activeSpaceKey, spaceKeyMap]);

	const defaultArchivePlanId = useMemo(() => {
		if (activeSpace?.id) {
			return activeSpace.id;
		}
		return spaces[0]?.id;
	}, [activeSpace?.id, spaces]);

	const activeSpaceModels = useMemo(() => {
		if (activeSpaceKey === UNASSIGNED_SPACE_KEY) {
			return unassignedModels;
		}
		if (!activeSpace) return filteredModels;
		return filteredModels.filter((model) => model.planId === activeSpace.id);
	}, [activeSpace, activeSpaceKey, filteredModels, unassignedModels]);

	const activeLayerNodes = useMemo(() => buildLayerNodes(activeSpaceModels), [buildLayerNodes, activeSpaceModels]);
	const canArchiveActiveModel = !!activeModel?.id && !activeModel?.planId && spaces.length > 0;
	const canBatchArchive = unassignedModels.length > 0 && spaces.length > 0;

	const treeData = useMemo(() => {
		const nodes = spaces.map((space, idx) => {
			const key = resolveSpaceKey(space, idx);
			const spaceModels = filteredModels.filter((model) => model.planId === space.id);
			return {
				title: space.name || "未命名项目空间",
				key,
				children: key === activeSpaceKey ? buildLayerNodes(spaceModels) : [],
			};
		});
		if (unassignedModels.length > 0) {
			nodes.push({
				title: `未归档工作区模型 (${unassignedModels.length})`,
				key: UNASSIGNED_SPACE_KEY,
				children: activeSpaceKey === UNASSIGNED_SPACE_KEY ? buildLayerNodes(unassignedModels) : [],
			});
		}
		return nodes;
	}, [spaces, activeSpaceKey, filteredModels, buildLayerNodes, unassignedModels]);

	// 模型操作下拉菜单
	const modelMenuItems = [
		{
			key: "create",
			icon: <PlusOutlined />,
			label: "新建模型",
			disabled: !workspaceOk,
			onClick: openCreateModel,
		},
		{
			key: "import",
			icon: <ImportOutlined />,
			label: "导入模型",
			disabled: !workspaceOk,
			onClick: openImportModel,
		},
		{
			key: "batch-import",
			icon: <ImportOutlined />,
			label: "批量导入",
			disabled: !workspaceOk,
			onClick: () => setBatchImportOpen(true),
		},
		{
			key: "generate-ods",
			icon: <ImportOutlined />,
			label: "从 ODS 一键生成",
			disabled: !workspaceOk,
			onClick: openOdsGenerateModel,
		},
		{
			key: "edit",
			icon: <EditOutlined />,
			label: "编辑模型",
			disabled: !activeModel || !workspaceOk,
			onClick: openEditModel,
		},
		{ type: "divider" as const },
		{
			key: "delete",
			icon: <DeleteOutlined />,
			label: "删除模型",
			disabled: !activeModel,
			danger: true,
			onClick: removeModel,
		},
	];

	// 插入代码下拉菜单
	const insertMenuItems = [
		{
			key: "source",
			icon: <TableOutlined />,
			label: "插入源表 (ODS)",
			disabled: !activeModel,
			onClick: () => openSnippetDrawer("source"),
		},
		{
			key: "ref",
			icon: <LinkOutlined />,
			label: "插入模型引用",
			disabled: !activeModel,
			onClick: () => openSnippetDrawer("ref"),
		},
	];

	const latestRun = dbtSyncStatus?.latestRun || null;
	const latestBuildStatus = normalizeUpper(latestRun?.status) || "UNKNOWN";
	const latestBuildColor = latestBuildStatus === "SUCCESS" ? "green" : latestBuildStatus === "FAILED" ? "red" : "gold";

	return (
		<div className="space-y-6">
			<Card
				title="逻辑建模工作区"
				extra={
					<Space wrap>
						<Button className="rounded-2xl" onClick={() => router.push("/modeling/dbt-files")}>
							打开 DBT 文件
						</Button>
						<Button className="rounded-2xl" onClick={() => router.push("/foundation/data-sources")}>
							去 ODS 接入
						</Button>
						<Button className="rounded-2xl" type="primary" onClick={openOdsGenerateModel} disabled={!workspaceOk}>
							一键生成模型
						</Button>
					</Space>
				}
			/>

			<div className="flex min-h-[calc(100vh-120px)] flex-col overflow-hidden rounded-[30px] border border-border/70 bg-card shadow-sm">
			{/* 顶部工具栏 */}
			<div className="flex h-14 items-center justify-between border-b border-border bg-card px-4">
				<div className="flex items-center gap-4">
					<div className="flex items-center gap-2">
						<span className="text-base font-semibold text-foreground">逻辑建模</span>
						<Badge
							status={configEnabled ? "success" : "error"}
							text={
								<span className="text-xs text-muted-foreground">
									{configEnabled ? "dbt 已连接" : "dbt 未启用"}
								</span>
							}
						/>
					</div>
					<Divider type="vertical" className="h-6" />
					{/* 模型操作 */}
					<Dropdown menu={{ items: modelMenuItems }} trigger={["click"]}>
						<Button icon={<PlusOutlined />}>
							模型 <DownOutlined className="text-xs" />
						</Button>
					</Dropdown>
					<Button onClick={openBatchArchive} disabled={!canBatchArchive}>
						批量归档
					</Button>
					{/* 保存按钮 */}
					<Button
						icon={<SaveOutlined />}
						onClick={saveSqlDraft}
						disabled={!activeModel || !sqlDirty || !workspaceOk}
					>
						保存
					</Button>
					{/* 插入代码 */}
					<Dropdown menu={{ items: insertMenuItems }} trigger={["click"]}>
						<Button icon={<CodeOutlined />} disabled={!activeModel}>
							插入 <DownOutlined className="text-xs" />
						</Button>
					</Dropdown>
				</div>
				<div className="flex items-center gap-2">
					{/* 同步按钮 */}
					<Tooltip title="同步模型到资产目录">
						<Button
							icon={<SyncOutlined spin={syncingModels} />}
							onClick={handleSyncModels}
							loading={syncingModels}
							disabled={!configEnabled || !workspaceOk}
						>
							同步
						</Button>
					</Tooltip>
					<Button
						onClick={() => triggerBuildOperation("compile")}
						loading={buildTriggering === "compile"}
						disabled={!configEnabled || !workspaceOk}
					>
						编译
					</Button>
					<Button
						onClick={() => triggerBuildOperation("test")}
						loading={buildTriggering === "test"}
						disabled={!configEnabled || !workspaceOk}
					>
						测试
					</Button>
					<Tooltip title="生成 dbt docs 产物">
						<Button
							onClick={() => triggerBuildOperation("docs")}
							loading={buildTriggering === "docs"}
							disabled={!configEnabled || !workspaceOk}
						>
							文档
						</Button>
					</Tooltip>
					{/* 回退操作 */}
					<Dropdown
						menu={{
							items: [
								{
									key: "truncate",
									label: "清空产出表 (Level 1)",
									icon: <DeleteOutlined />,
									onClick: () => openRollback(1),
								},
								{
									key: "rebuild",
									label: "重建产出表 (Level 2)",
									icon: <UndoOutlined />,
									danger: true,
									onClick: () => openRollback(2, true),
								},
							],
						}}
						disabled={!configEnabled || !workspaceOk || !activeModel}
					>
						<Button danger icon={<UndoOutlined />}>
							回退 <DownOutlined />
						</Button>
					</Dropdown>
					{/* 提交变更 */}
					<Button
						type="primary"
						icon={<RocketOutlined />}
						onClick={openRun}
						disabled={!configEnabled || !workspaceOk || buildTriggering != null}
					>
						提交变更
					</Button>
					{/* 配置按钮 */}
					<Tooltip title="工作区配置">
						<Button icon={<SettingOutlined />} onClick={() => setConfigOpen(true)} />
					</Tooltip>
				</div>
			</div>
			{/* 紧凑的同步状态栏 */}
			{dbtSyncStatus && (
				<div className="flex items-center justify-between border-b border-border bg-muted/30 px-4 py-2 text-xs">
					<div className="flex items-center gap-4">
						<span className="text-muted-foreground">同步状态:</span>
						<span className="flex items-center gap-1">
							manifest {syncTag(dbtSyncStatus.manifest?.synced)}
						</span>
						<span className="flex items-center gap-1">
							run_results {syncTag(dbtSyncStatus.runResults?.synced)}
						</span>
						{latestRun?.present && (
							<span className="flex items-center gap-1">
								最近构建 <Tag color={latestBuildColor}>{latestBuildStatus}</Tag>
							</span>
						)}
						{dbtSyncStatus.stats && (
							<span className="text-muted-foreground">
								| 模型 +{dbtSyncStatus.stats.datasetsCreated ?? 0}/~{dbtSyncStatus.stats.datasetsUpdated ?? 0}
								, 字段 {dbtSyncStatus.stats.columnsUpdated ?? 0}
								, 血缘 +{dbtSyncStatus.stats.lineageCreated ?? 0}/-{dbtSyncStatus.stats.lineageRemoved ?? 0}
							</span>
						)}
					</div>
					<span className="text-muted-foreground">
						上次同步: {formatDateTime(dbtSyncStatus.stats?.lastSyncAt || dbtSyncStatus.manifest?.lastSyncAt)}
					</span>
				</div>
			)}
			<div className="flex flex-1 min-h-0 overflow-hidden">
				<div className="w-64 border-r border-border bg-muted p-4">
					<div className="mb-3 text-xs font-bold uppercase text-muted-foreground">项目目录</div>
					<Input
						size="small"
						placeholder="搜索模型..."
						value={keyword}
						onChange={(event) => setKeyword(event.target.value)}
						className="mb-3"
					/>
					{spacesLoading ? (
						<Card size="small" className="border-dashed text-center text-xs text-muted-foreground">
							加载项目空间中...
						</Card>
					) : treeData.length === 0 ? (
						<EmptyState title="暂无项目空间" description="请先在项目空间管理中创建项目空间。" />
					) : (
						<>
							<DirectoryTree
								defaultExpandAll
								treeData={treeData}
								selectedKeys={
									activeModelKey
										? [`model:${activeModelKey}`]
										: activeSpaceKey
											? [activeSpaceKey]
											: []
								}
								onSelect={(keys) => {
									const key = String(keys[0] || "");
									if (!key) return;
									if (key.startsWith("space-")) {
										setActiveSpaceKey(key);
										setActiveModelKey(null);
										return;
									}
									if (key.startsWith("layer-")) return;
									if (key.startsWith("model:")) {
										setActiveModelKey(key.replace("model:", ""));
										return;
									}
									setActiveModelKey(key);
								}}
							/>
							{modelsLoading ? (
								<div className="mt-3 text-xs text-muted-foreground">加载模型中...</div>
							) : activeLayerNodes.length === 0 ? (
								<div className="mt-3 text-xs text-muted-foreground">当前项目暂无模型。</div>
							) : null}
						</>
					)}
				</div>

				<div className="flex flex-1 flex-col">
					<div className="flex items-center justify-between border-b border-border bg-muted/20 px-4 py-1.5">
						<div className="flex items-center gap-2">
							<Text strong className="text-sm">{activeModel?.name || "未选择模型"}</Text>
							{activeModel && layerTag(activeModel?.layer || inferLayer(activeModel.name))}
							{activeModel && !activeModel.planId && <Tag color="gold">未归档</Tag>}
							{activeModel?.modelPath && (
								<Text type="secondary" className="text-xs">{activeModel.modelPath}</Text>
							)}
							{sqlDirty && <Tag color="orange" className="text-xs">未保存</Tag>}
						</div>
						<Space size="small">
							{canArchiveActiveModel && (
								<Button size="small" onClick={openSingleArchive}>
									归档到项目空间
								</Button>
							)}
							<Tooltip title="刷新模型列表">
								<Button size="small" icon={<SyncOutlined />} onClick={loadModels} loading={modelsLoading} />
							</Tooltip>
						</Space>
					</div>
					{activeModel && (
						<ModelPipeline
							modelStatus={activeModel.status}
							modelSelector={activeModel.tags ? `tag:${activeModel.tags.split(",")[0]?.trim()}` : undefined}
							disabled={!workspaceOk || buildTriggering != null}
							onStatusChange={(s) => {
								if (activeModel) activeModel.status = s;
								loadModels();
							}}
							onRefresh={loadModels}
						/>
					)}
						<div className="flex-1 overflow-auto bg-muted/10 px-6 py-4">
							{activeModel ? (
							<Suspense
								fallback={
									<div className="flex h-full items-center justify-center text-center text-xs text-muted-foreground">
										加载 SQL 编辑器中...
									</div>
								}
							>
								<Editor
									height="100%"
									language="sql"
									theme="vs"
									value={sqlDraft}
									onChange={(value) => setSqlDraft(value || "")}
									options={{
										fontSize: 13,
										minimap: { enabled: false },
										automaticLayout: true,
										wordWrap: "on",
										scrollBeyondLastLine: false,
										tabSize: 2,
									}}
								/>
							</Suspense>
						) : (
							<div className="flex h-full items-center justify-center text-center text-xs text-muted-foreground">
								从左侧选择模型后开始校验 SQL、契约字段和发布影响。
								</div>
							)}
						</div>
					</div>

				<div className="w-64 border-l border-border bg-card overflow-y-auto">
					<Tabs
						size="small"
						className="px-2"
						items={[
							{
								key: "model",
								label: "模型",
								children: (
									<div className="px-2 pb-4">
										{activeModel ? (
											<div className="space-y-3">
												<div className="space-y-1 text-xs">
													<div className="flex justify-between">
														<span className="text-muted-foreground">数据源</span>
														<span className="font-medium">{activeModel.sourceDataSourceName || "-"}</span>
													</div>
													<div className="flex justify-between">
														<span className="text-muted-foreground">来源系统</span>
														<span className="font-medium">{activeModel.sourceSystem || "-"}</span>
													</div>
													<div className="flex justify-between">
														<span className="text-muted-foreground">物化方式</span>
														<span className="font-medium">{activeModel.materialized || "table"}</span>
													</div>
													<div className="flex justify-between">
														<span className="text-muted-foreground">Schema</span>
														<span className="font-medium">{activeModel.schemaName || "默认"}</span>
													</div>
													{activeModel.tags && (
														<div className="flex justify-between">
															<span className="text-muted-foreground">标签</span>
															<span className="font-medium">{activeModel.tags}</span>
														</div>
													)}
													<div className="flex justify-between">
														<span className="text-muted-foreground">契约版本</span>
														<span className="font-medium">
															{activeModel.contractVersion || contractImpact?.contractVersion || "-"}
														</span>
													</div>
													<div className="flex justify-between">
														<span className="text-muted-foreground">指标/维度</span>
														<span className="font-medium">
															{activeModel.metricCount ?? contractImpact?.metricCount ?? 0} /{" "}
															{activeModel.dimensionCount ?? contractImpact?.dimensionCount ?? 0}
														</span>
													</div>
													<div className="flex justify-between">
														<span className="text-muted-foreground">影响报表</span>
														<span className="font-medium">
															{contractImpactLoading ? "计算中..." : (contractImpact?.impactedReportCount ?? 0)}
														</span>
													</div>
													<div className="flex justify-between">
														<span className="text-muted-foreground">影响字段</span>
														<span className="font-medium">
															{contractImpactLoading ? "计算中..." : (contractImpact?.fieldCount ?? modelColumns.length)}
														</span>
													</div>
												</div>
												<Divider className="my-2" />
												<div className="text-xs font-medium text-muted-foreground mb-2">字段列表</div>
												{modelColumns.length ? (
													<Table
														rowKey={(row, idx) => `${row.name || "col"}-${idx}`}
														size="small"
														pagination={false}
														columns={modelColumnColumns}
														dataSource={modelColumns}
														loading={columnsLoading}
														scroll={{ y: 180 }}
													/>
												) : (
													<div className="text-xs text-muted-foreground">暂无字段配置</div>
												)}
												{contractImpact?.impactedReports?.length ? (
													<>
														<Divider className="my-2" />
														<div className="text-xs font-medium text-muted-foreground mb-1">受影响报表</div>
														<div className="max-h-24 overflow-auto text-xs text-muted-foreground space-y-1">
															{contractImpact.impactedReports.slice(0, 6).map((item) => (
																<div key={item.id || `${item.code}-${item.title}`}>
																	{item.title || item.code || item.id}
																</div>
															))}
														</div>
													</>
												) : null}
											</div>
										) : (
											<div className="text-xs text-muted-foreground py-4 text-center">请选择模型</div>
										)}
									</div>
								),
							},
							{
								key: "project",
								label: "项目",
								children: (
									<div className="px-2 pb-4 space-y-3">
										{activeSpace ? (
											<div className="space-y-1 text-xs">
												<div className="flex justify-between">
													<span className="text-muted-foreground">名称</span>
													<span className="font-medium">{activeSpace.name || "-"}</span>
												</div>
												<div className="flex justify-between">
													<span className="text-muted-foreground">业务域</span>
													<span className="font-medium">{activeSpace.domain || "-"}</span>
												</div>
												<div className="flex justify-between">
													<span className="text-muted-foreground">负责人</span>
													<span className="font-medium">{activeSpace.owner || "-"}</span>
												</div>
												<div className="flex justify-between">
													<span className="text-muted-foreground">状态</span>
													<span className="font-medium">{activeSpace.status || "-"}</span>
												</div>
											</div>
										) : (
											<div className="text-xs text-muted-foreground py-4 text-center">请选择项目</div>
										)}
										<Divider className="my-2" />
										<div className="text-xs font-medium text-muted-foreground mb-2">dbt 配置</div>
										<div className="space-y-1 text-xs">
											<div className="flex justify-between">
												<span className="text-muted-foreground">项目目录</span>
												<span className="font-medium truncate max-w-[120px]" title={dbtConfig?.config?.projectDir}>
													{dbtConfig?.config?.projectDir ? "已配置" : "未配置"}
												</span>
											</div>
											<div className="flex justify-between">
												<span className="text-muted-foreground">Target</span>
												<span className="font-medium">{dbtConfig?.config?.targetName || "未配置"}</span>
											</div>
											<div className="flex justify-between">
												<span className="text-muted-foreground">状态</span>
												<Tag color={workspaceStatus?.ok ? "green" : "red"} className="text-xs">
													{workspaceStatus?.ok ? "可用" : "不可用"}
												</Tag>
											</div>
										</div>
										{workspaceStatus && !workspaceStatus.ok && (
											<div className="mt-2 rounded border border-destructive/30 bg-destructive/10 px-2 py-1 text-xs text-destructive">
												{workspaceStatus.message || "工作区不可用"}
											</div>
										)}
									</div>
								),
							},
						]}
						/>
					</div>
				</div>

				<div className="h-72 border-t border-border bg-card">
					<Tabs
						activeKey={bottomTab}
						onChange={setBottomTab}
						size="small"
						className="px-4"
						items={[
							{
								key: "preview",
								label: "数据预览",
								children: (() => {
									if (!activeModel) {
										return (
											<div className="p-4">
												<Alert type="info" showIcon message="请先选择一个模型" />
											</div>
										);
									}
									const previewColumns = previewData?.columns?.map((col, i) => ({
										title: col,
										dataIndex: i,
										key: col,
										ellipsis: true,
										width: 150,
									})) || [];
									const previewRows = previewData?.rows?.map((row, idx) => {
										const record: Record<string, any> = { _key: idx };
										row.forEach((val, i) => { record[i] = val != null ? String(val) : "NULL"; });
										return record;
									}) || [];
									return (
										<div className="p-2 space-y-2">
											<div className="flex items-center gap-2 px-2">
												<Button
													size="small"
													icon={<ReloadOutlined />}
													loading={previewLoading}
													onClick={() => loadPreview(activeModel?.name, previewLimit)}
												>
													加载预览
												</Button>
												<Select
													size="small"
													value={previewLimit}
													onChange={setPreviewLimit}
													options={[
														{ label: "50 行", value: 50 },
														{ label: "100 行", value: 100 },
														{ label: "200 行", value: 200 },
														{ label: "500 行", value: 500 },
													]}
													style={{ width: 100 }}
												/>
												{previewData && (
													<span className="text-xs text-muted-foreground">
														{previewRows.length} 行 × {previewColumns.length} 列
													</span>
												)}
											</div>
											{previewData ? (
												<Table
													rowKey="_key"
													size="small"
													pagination={false}
													columns={previewColumns}
													dataSource={previewRows}
													scroll={{ x: previewColumns.length * 150, y: 170 }}
													loading={previewLoading}
												/>
											) : (
												<div className="p-4 text-center">
													<EmptyState title="暂无数据" description="点击「加载预览」查询模型输出数据" compact />
												</div>
											)}
										</div>
									);
								})(),
							},
							{
								key: "compile",
								label: "编译结果",
								children: (() => {
									const cr = compileResult;
									const crFailures = Array.isArray(cr?.failures) ? cr.failures : [];
									const crStatus = normalizeUpper(cr?.status) || "UNKNOWN";
									const crColor = crStatus === "SUCCESS" ? "green" : crStatus === "FAILED" ? "red" : "gold";
									const crAlert = describeBuildSummary(cr, {
										operationLabel: "编译",
										fallbackMessage: "请先执行编译操作",
									});
									if (!cr) {
										return (
											<div className="p-4">
												<Alert type="info" showIcon message="请先执行编译操作" />
											</div>
										);
									}
									return (
										<div className="p-4 space-y-3 text-xs">
											<div className="flex items-center gap-2">
												<Tag color={crColor}>{crStatus}</Tag>
												<span className="text-muted-foreground">
													时间：{formatDateTime(cr.generatedAt)}
												</span>
											</div>
											<div className="text-muted-foreground">
												总计 {cr.total ?? 0}，成功 {cr.success ?? 0}，失败 {cr.failed ?? 0}，跳过 {cr.skipped ?? 0}
											</div>
											{crFailures.length > 0 ? (
												<div className="max-h-[145px] overflow-auto rounded border border-border bg-muted/20 p-2">
													{crFailures.map((item, idx) => (
														<div key={`${item.uniqueId || item.name || "f"}-${idx}`} className="mb-2 last:mb-0">
															<div className="font-medium">
																{item.uniqueId || item.name || "UNKNOWN_NODE"}
																{item.resourceType ? ` (${item.resourceType})` : ""}
															</div>
															<div className="text-muted-foreground">{item.message || "编译失败"}</div>
														</div>
													))}
												</div>
											) : (
												<Alert type={crAlert.type} showIcon message={crAlert.message} />
											)}
										</div>
									);
								})(),
							},
							{
								key: "test",
								label: "测试结果",
								children: (() => {
									const tr = testResult;
									const trFailures = Array.isArray(tr?.failures) ? tr.failures : [];
									const trStatus = normalizeUpper(tr?.status) || "UNKNOWN";
									const trColor = trStatus === "SUCCESS" ? "green" : trStatus === "FAILED" ? "red" : "gold";
									const trAlert = describeBuildSummary(tr, {
										operationLabel: "测试",
										fallbackMessage: "请先执行测试操作",
									});
									if (!tr) {
										return (
											<div className="p-4">
												<Alert type="info" showIcon message="请先执行测试操作" />
											</div>
										);
									}
									const testDetails = (tr as any)?.testDetails as Array<{
										uniqueId?: string;
										name?: string;
										testType?: string;
										testedColumn?: string;
										testedModel?: string;
										status?: string;
										message?: string;
										compiledSql?: string;
										executionTime?: number;
										failuresCount?: number;
									}> | undefined;
									if (testDetails && testDetails.length > 0) {
										const testTableColumns: ColumnsType<typeof testDetails[number]> = [
											{ title: "测试名称", dataIndex: "name", key: "name", ellipsis: true, width: 200 },
											{ title: "类型", dataIndex: "testType", key: "testType", width: 100,
												render: (v) => <Tag>{v || "generic"}</Tag>,
											},
											{ title: "被测模型", dataIndex: "testedModel", key: "testedModel", width: 120, ellipsis: true, render: (v) => v || "-" },
											{ title: "被测列", dataIndex: "testedColumn", key: "testedColumn", width: 100, render: (v) => v || "-" },
											{
												title: "状态", dataIndex: "status", key: "status", width: 80,
												render: (v) => {
													const s = normalizeUpper(v);
													const c = s === "PASS" || s === "SUCCESS" ? "green" : s === "FAIL" || s === "FAILED" || s === "ERROR" ? "red" : "gold";
													return <Tag color={c}>{s || "UNKNOWN"}</Tag>;
												},
											},
											{
												title: "耗时", dataIndex: "executionTime", key: "executionTime", width: 70,
												render: (v) => (v != null ? `${Number(v).toFixed(1)}s` : "-"),
											},
											{
												title: "失败数", dataIndex: "failuresCount", key: "failuresCount", width: 70,
												render: (v) => (v != null && v > 0 ? <Tag color="red">{v}</Tag> : "-"),
											},
											{ title: "错误信息", dataIndex: "message", key: "message", ellipsis: true, render: (v) => v || "-" },
										];
										return (
											<div className="p-2 space-y-2 text-xs">
												<div className="flex items-center gap-2 px-2">
													<Tag color={trColor}>{trStatus}</Tag>
													<span className="text-muted-foreground">
														总计 {tr.total ?? 0}，成功 {tr.success ?? 0}，失败 {tr.failed ?? 0}
													</span>
												</div>
												<Table
													rowKey={(row, idx) => `${row.uniqueId || row.name || "t"}-${idx}`}
													size="small"
													pagination={false}
													columns={testTableColumns}
													dataSource={testDetails}
													scroll={{ x: 900, y: 160 }}
												/>
											</div>
										);
									}
									return (
										<div className="p-4 space-y-3 text-xs">
											<div className="flex items-center gap-2">
												<Tag color={trColor}>{trStatus}</Tag>
												<span className="text-muted-foreground">
													时间：{formatDateTime(tr.generatedAt)}
												</span>
											</div>
											<div className="text-muted-foreground">
												总计 {tr.total ?? 0}，成功 {tr.success ?? 0}，失败 {tr.failed ?? 0}，跳过 {tr.skipped ?? 0}
											</div>
											{trFailures.length > 0 ? (
												<div className="max-h-[145px] overflow-auto rounded border border-border bg-muted/20 p-2">
													{trFailures.map((item, idx) => (
														<div key={`${item.uniqueId || item.name || "f"}-${idx}`} className="mb-2 last:mb-0">
															<div className="font-medium">
																{item.uniqueId || item.name || "UNKNOWN_NODE"}
																{item.resourceType ? ` (${item.resourceType})` : ""}
															</div>
															<div className="text-muted-foreground">{item.message || "测试失败"}</div>
														</div>
													))}
												</div>
											) : (
												<Alert type={trAlert.type} showIcon message={trAlert.message} />
											)}
										</div>
									);
								})(),
							},
							{
								key: "execlog",
								label: "执行日志",
								children: (() => {
									const rr = runResult || latestRun;
									if (!rr?.present) {
										return (
											<div className="p-4">
												<Alert type="info" showIcon message="执行完成后可查看完整日志" />
											</div>
										);
									}
									const rrStatus = normalizeUpper(rr.status) || "UNKNOWN";
									const rrColor = rrStatus === "SUCCESS" ? "green" : rrStatus === "FAILED" ? "red" : "gold";
									// Extract dagRunId from invocationId or latest run
									const dagRunId = (rr as any)?.dagRunId || rr.invocationId || "";
									return (
										<div className="p-2 space-y-2 text-xs">
											<div className="flex items-center gap-2 px-2">
												<Tag color={rrColor}>{rrStatus}</Tag>
												<span className="text-muted-foreground">
													命令：{rr.command || "N/A"} | 时间：{formatDateTime(rr.generatedAt)}
												</span>
												<span className="text-muted-foreground">
													总计 {rr.total ?? 0}，成功 {rr.success ?? 0}，失败 {rr.failed ?? 0}
												</span>
												<Button
													size="small"
													icon={<FileTextOutlined />}
													loading={execLogLoading}
													disabled={!dagRunId}
													onClick={() => loadExecLog(dagRunId, rr.dagId)}
												>
													加载日志
												</Button>
											</div>
											{execLog ? (
												<pre className="max-h-[180px] overflow-auto rounded border border-border bg-muted/20 p-2 text-xs font-mono whitespace-pre-wrap break-all">
													{execLog}
												</pre>
											) : (
												<div className="px-2 text-muted-foreground">
													{dagRunId ? "点击「加载日志」查看 Airflow 执行日志" : "无可用的 DAG 运行 ID"}
												</div>
											)}
										</div>
									);
								})(),
							},
							{
								key: "runs",
								label: "运行记录",
								children: runs.length === 0 && !runsLoading ? (
									<div className="p-4 text-sm text-muted-foreground">暂无运行记录。</div>
								) : (
									<Table
										rowKey={(row) => row.dag_run_id || Math.random().toString(36)}
										size="small"
										pagination={false}
										columns={runColumns}
										dataSource={runs}
										loading={runsLoading}
										scroll={{ y: 170 }}
									/>
								),
							},
							{
								key: "gitstatus",
								label: "变更状态",
								children: (() => {
									const allChanges = [
										...(gitStatus?.staged || []).map((f) => ({ file: f, type: "staged" as const })),
										...(gitStatus?.unstaged || []).map((f) => ({ file: f, type: "modified" as const })),
										...(gitStatus?.untracked || []).map((f) => ({ file: f, type: "untracked" as const })),
									];
									const typeTag = (t: string) => {
										if (t === "staged") return <Tag color="green">暂存</Tag>;
										if (t === "modified") return <Tag color="orange">修改</Tag>;
										return <Tag>新增</Tag>;
									};
									return (
										<div className="p-2 flex gap-3" style={{ height: 210, overflow: "hidden" }}>
											{/* Left: file changes + commit */}
											<div className="flex-1 flex flex-col gap-2 min-w-0">
												<div className="flex items-center gap-2">
													<Button size="small" icon={<ReloadOutlined />} loading={gitLoading} onClick={loadGitInfo}>
														刷新
													</Button>
													{gitStatus?.clean && <Tag color="green" icon={<CheckCircleOutlined />}>工作区干净</Tag>}
													{!gitStatus && !gitLoading && <span className="text-xs text-muted-foreground">点击刷新加载 Git 状态</span>}
												</div>
												{allChanges.length > 0 && (
													<div className="flex-1 overflow-auto text-xs border border-border rounded bg-muted/20 p-1">
														{allChanges.map(({ file, type }) => (
															<div key={`${type}-${file}`} className="flex items-center justify-between py-0.5 px-1 hover:bg-muted/30">
																<span className="flex items-center gap-1 truncate">
																	{typeTag(type)}
																	<span className="truncate">{file}</span>
																</span>
																<Popconfirm title={`还原 ${file}？`} onConfirm={() => handleGitRevert(file)} okText="确认" cancelText="取消">
																	<Button size="small" type="text" icon={<UndoOutlined />} loading={gitReverting === file} />
																</Popconfirm>
															</div>
														))}
													</div>
												)}
												{allChanges.length > 0 && (
													<div className="flex items-center gap-2">
														<Input
															size="small"
															placeholder="提交信息"
															value={gitCommitMsg}
															onChange={(e) => setGitCommitMsg(e.target.value)}
															onPressEnter={handleGitCommit}
															className="flex-1"
														/>
														<Button
															size="small"
															type="primary"
															loading={gitCommitting}
															disabled={!gitCommitMsg.trim()}
															onClick={handleGitCommit}
														>
															提交
														</Button>
													</div>
												)}
											</div>
											{/* Right: recent commits */}
											<div className="w-72 flex flex-col gap-1 overflow-hidden">
												<div className="text-xs font-medium text-muted-foreground">最近提交</div>
												<div className="flex-1 overflow-auto text-xs border border-border rounded bg-muted/20 p-1">
													{gitLog.length === 0 ? (
														<div className="p-2 text-muted-foreground">暂无提交记录</div>
													) : (
														gitLog.map((c) => (
															<div key={c.hash} className="py-0.5 px-1 hover:bg-muted/30">
																<span className="font-mono text-blue-600">{(c.hash || "").substring(0, 7)}</span>
																{" "}
																<span className="truncate">{c.shortMessage}</span>
																<div className="text-muted-foreground">{c.author} · {formatDateTime(c.date)}</div>
															</div>
														))
													)}
												</div>
											</div>
										</div>
									);
								})(),
							},
							{
								key: "audit",
								label: "回退记录",
								children: (
									<div className="p-2">
										<div className="flex items-center gap-2 mb-2">
											<Button
												size="small"
												icon={<ReloadOutlined />}
												loading={auditLogsLoading}
												onClick={() => loadAuditLogs(dbtConfig?.config?.targetDataSourceId)}
											>
												加载
											</Button>
										</div>
										{auditLogs.length === 0 ? (
											<div className="text-xs text-muted-foreground p-2">暂无回退记录</div>
										) : (
											<Table
												size="small"
												rowKey="id"
												dataSource={auditLogs}
												pagination={false}
												scroll={{ y: 160 }}
												columns={[
													{ title: "时间", dataIndex: "executedAt", key: "executedAt", width: 170, render: formatDateTime },
													{ title: "级别", dataIndex: "level", key: "level", width: 80, render: (v: number) => <Tag color={v >= 2 ? "red" : "orange"}>Level {v}</Tag> },
													{ title: "范围", dataIndex: "scope", key: "scope", width: 80 },
													{ title: "表", dataIndex: "tables", key: "tables", ellipsis: true, render: (v: string[]) => (v || []).join(", ") },
													{ title: "操作人", dataIndex: "executedBy", key: "executedBy", width: 100 },
													{ title: "状态", dataIndex: "status", key: "status", width: 80, render: (v: string) => <Tag color={v === "SUCCESS" ? "green" : "red"}>{v}</Tag> },
												]}
											/>
										)}
									</div>
								),
							},
							{
								key: "brief",
								label: "使用指南",
								children: (
									<div className="p-4 text-sm text-foreground">
										<div className="mb-3 font-medium">主流程：ODS 接入 -&gt; 选择映射 -&gt; 一键生成 DWD/DWS/ADS -&gt; 校验并上线</div>
										<ol className="list-decimal pl-5 text-xs text-muted-foreground">
											<li>先在数据集成完成 ODS 表接入或源库映射。</li>
											<li>在本页选择项目空间和 ODS 映射后执行一键生成。</li>
											<li>系统自动产出 dwd_ / dws_ / ads_ 模型模板。</li>
											<li>在 dbt 文件浏览器微调并运行，最后提交变更。</li>
										</ol>
										<Space className="mt-4" size={8}>
											<Button size="small" onClick={() => router.push("/foundation/data-sources")}>
												去 ODS 接入
											</Button>
											<Button size="small" type="primary" ghost onClick={openOdsGenerateModel} disabled={!workspaceOk}>
												打开一键生成
											</Button>
										</Space>
									</div>
								),
							},
						]}
					/>
				</div>

				<Drawer
					open={configOpen}
					title="dbt 工作区配置"
					width={520}
					onClose={() => setConfigOpen(false)}
					footer={
						<Space>
							<Button onClick={() => setConfigOpen(false)}>取消</Button>
							<Button type="primary" onClick={saveConfig} loading={configSaving} disabled={!configEnabled}>
								保存配置
							</Button>
						</Space>
					}
				>
					<Form layout="vertical" form={form} disabled={!configEnabled || configLoading}>
						<Form.Item name="projectDir" label="项目目录" rules={[{ required: true, message: "请输入项目目录" }]}>
							<Input placeholder="/opt/dts/dbt-project" />
						</Form.Item>
						<Form.Item name="profilesDir" label="profiles 目录" rules={[{ required: true, message: "请输入 profiles 目录" }]}>
							<Input placeholder="/opt/dts/dbt-profiles" />
						</Form.Item>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="profileName" label="Profile 名称">
							<Input placeholder="dts" />
						</Form.Item>
						<Form.Item name="targetName" label="Target 名称">
							<Input placeholder="dev" />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="database" label="数据库">
							<Input placeholder="目标数据库名称" />
						</Form.Item>
						<Form.Item name="schema" label="默认 Schema">
							<Input placeholder="例如：analytics" />
						</Form.Item>
					</div>
					<Form.Item label="Profiles 状态">
						<Space>
							<Tag color={profileStatus?.generated ? "green" : "default"}>
								{profileStatus?.generated ? "已生成" : "未生成"}
							</Tag>
							<Text type="secondary">{profileStatus?.message || "—"}</Text>
						</Space>
					</Form.Item>
					<Form.Item label="工作区状态">
						<Space>
							<Tag color={workspaceStatus?.ok ? "green" : "red"}>
								{workspaceStatus?.ok ? "可用" : "不可用"}
							</Tag>
							<Text type="secondary">{workspaceStatus?.message || "—"}</Text>
						</Space>
					</Form.Item>
					<Form.Item name="vars" label="全局变量 (vars)">
						<Input.TextArea rows={3} placeholder='JSON 结构，例如 {"schema":"analytics"}' />
					</Form.Item>
				</Form>
			</Drawer>

			<Modal
				open={runOpen}
				title="提交上线 (dbt build)"
				onCancel={() => setRunOpen(false)}
				onOk={submitRun}
				okText="提交"
				cancelText="取消"
				confirmLoading={runSubmitting}
				width={560}
			>
				<Form layout="vertical" form={runForm}>
					<Form.Item name="models" label="模型选择器" rules={[{ required: true, message: "请输入模型选择器" }]}>
						<Input placeholder="例如：tag:crm 或 model:xxx" />
					</Form.Item>
					<Form.Item name="target" label="Target">
						<Input placeholder="dev" />
					</Form.Item>
					<div className="grid gap-3 md:grid-cols-2">
						<Form.Item
							name="gitRef"
							label="Git 分支"
							tooltip="可选。仅在环境维护 Git 版本追溯时填写，允许 main/master/release/*/hotfix/*"
						>
							<Input placeholder="可选，例如：release/2.2.1" />
						</Form.Item>
						<Form.Item
							name="commitSha"
							label="Commit SHA"
							tooltip="可选。用于将本次发布与具体代码版本绑定"
						>
							<Input placeholder="可选，例如：a1b2c3d4" />
						</Form.Item>
					</div>
					<Form.Item name="strictMode" valuePropName="checked">
						<Checkbox>启用严格发布门禁（客户环境无 Git 时可关闭）</Checkbox>
					</Form.Item>
					<Form.Item name="vars" label="运行变量">
						<Input.TextArea rows={3} placeholder='JSON 结构，例如 {"run_date":"2026-01-19"}' />
					</Form.Item>
				</Form>
			</Modal>

			</div>

			<Drawer
				open={modelDrawerOpen}
				title={editingModel ? "编辑模型" : "新建模型"}
				width={720}
				onClose={() => setModelDrawerOpen(false)}
				footer={
					<Space>
						<Button onClick={() => setModelDrawerOpen(false)}>取消</Button>
						<Button type="primary" onClick={submitModel} loading={modelSubmitting}>
							保存
						</Button>
					</Space>
				}
			>
				<Form layout="vertical" form={modelForm} disabled={modelSubmitting}>
					{/* 基本信息 */}
					<div className="mb-4 pb-2 border-b border-border">
						<div className="text-sm font-semibold text-foreground">基本信息</div>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="planId" label="项目空间" rules={[{ required: true, message: "请选择项目空间" }]}>
							<Select
								placeholder="选择项目空间"
								options={spaces.map((space) => ({ label: space.name || "未命名", value: space.id }))}
							/>
						</Form.Item>
						<Form.Item
							name="layer"
							label="数仓分层"
							rules={[{ required: true, message: "请选择分层" }]}
							tooltip="选择模型所在的数仓层级，系统会自动添加对应标签"
						>
							<Select
								placeholder="选择分层"
								options={layers.map((l) => ({
									label: `${l.layer} - ${l.description || l.name}`,
									value: l.layer,
								}))}
							/>
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item
							name="name"
							label="模型名称"
							rules={[{ required: true, message: "请输入模型名称" }]}
							tooltip="建议以分层前缀开头，如 dwd_sales_order"
						>
							<Input placeholder="例如 dwd_sales_order" />
						</Form.Item>
						<Form.Item
							name="sourceDataSourceId"
							label="来源数据源"
							tooltip="选择数据来源系统，用于自动生成调度标签；Excel/手工录入场景可不选"
						>
							<Select
								placeholder="选择来源数据源（可选）"
								showSearch
								allowClear
								optionFilterProp="label"
								options={dataSources.map((ds) => ({
									label: ds?.name || ds?.id,
									value: ds?.id,
								}))}
							/>
						</Form.Item>
					</div>
					<Form.Item name="description" label="模型说明">
						<Input.TextArea rows={2} placeholder="描述模型的业务含义和用途" />
					</Form.Item>

					{/* dbt 配置 */}
					<div className="mb-4 mt-6 pb-2 border-b border-border">
						<div className="text-sm font-semibold text-foreground">dbt 配置</div>
						<div className="text-xs text-muted-foreground mt-1">
							以下配置会自动生成 dbt 的 config 块，您无需手动编写
						</div>
					</div>
					<div className="grid gap-4 md:grid-cols-3">
						<Form.Item
							name="materialized"
							label="物化方式"
							tooltip="table: 全量重建表；view: 视图；incremental: 增量更新"
						>
							<Select
								placeholder="选择物化方式"
								options={[
									{ label: "table（推荐）", value: "table" },
									{ label: "view", value: "view" },
									{ label: "incremental", value: "incremental" },
								]}
							/>
						</Form.Item>
						<Form.Item
							name="alias"
							label="物理表别名"
							tooltip="如果物理表名需要与模型名不同，在此指定"
						>
							<Input placeholder="可选，默认使用模型名" />
						</Form.Item>
						<Form.Item
							name="schemaName"
							label="目标 Schema"
							tooltip="模型输出的目标 Schema，留空使用默认配置"
						>
							<Input placeholder="留空使用默认" />
						</Form.Item>
					</div>
					<Form.Item
						name="tags"
						label="标签"
						tooltip="用于调度选择器和分组管理，系统会自动添加来源系统和分层标签"
					>
						<Input placeholder="多个标签用逗号分隔，如: daily,core" />
					</Form.Item>
					<Form.Item
						name="semanticContract"
						label="语义契约 (JSON)"
						tooltip="可选：定义 metrics/dimensions 元信息，发布与看板绑定会展示契约版本"
					>
						<Input.TextArea
							rows={4}
							className="font-mono text-sm"
							placeholder='{"metrics":[{"code":"order_cnt","name":"订单数"}],"dimensions":[{"code":"dept","name":"部门"}]}'
						/>
					</Form.Item>

					{/* SQL 编辑 */}
					<div className="mb-4 mt-6 pb-2 border-b border-border">
						<div className="text-sm font-semibold text-foreground">SQL 定义</div>
						<div className="text-xs text-muted-foreground mt-1">
							只需编写 SELECT 语句，使用 {"{{ source('schema', 'table') }}"} 引用源表，使用 {"{{ ref('model') }}"} 引用其他模型
						</div>
					</div>
					<Form.Item name="sql" rules={[{ required: true, message: "请输入 SQL" }]}>
						<Input.TextArea
							rows={12}
							className="font-mono text-sm"
							placeholder={`SELECT
  id,
  name,
  created_at
FROM {{ source('public', 'ods_your_table') }}
WHERE status = 'active'`}
						/>
					</Form.Item>

					{/* 状态管理 */}
					<div className="mb-4 mt-6 pb-2 border-b border-border">
						<div className="text-sm font-semibold text-foreground">状态管理</div>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="status" label="模型状态">
							<Select
								placeholder="选择状态"
								options={[
									{ label: "草稿 - 开发中", value: "DRAFT" },
									{ label: "就绪 - 可上线", value: "READY" },
									{ label: "暂停 - 暂停调度", value: "PAUSED" },
								]}
							/>
						</Form.Item>
						<Form.Item name="enabled" label="启用调度" valuePropName="checked">
							<Switch checkedChildren="启用" unCheckedChildren="禁用" />
						</Form.Item>
					</div>
				</Form>
			</Drawer>

			<Modal
				open={importOpen}
				title="导入模型 (SQL + CSV)"
				onCancel={() => setImportOpen(false)}
				footer={
					<Space>
						<Button onClick={() => setImportOpen(false)}>取消</Button>
						<Button type="primary" onClick={submitImport} loading={importSubmitting}>
							导入
						</Button>
					</Space>
				}
			>
				<Form layout="vertical" form={importForm} disabled={importSubmitting}>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="planId" label="项目空间" rules={[{ required: true, message: "请选择项目空间" }]}>
							<Select
								placeholder="选择项目空间"
								options={spaces.map((space) => ({ label: space.name || "未命名", value: space.id }))}
							/>
						</Form.Item>
						<Form.Item name="layer" label="分层" rules={[{ required: true, message: "请选择分层" }]}>
							<Select
								placeholder="选择分层"
								options={[
									{ label: "ODS", value: "ODS" },
									{ label: "DWD", value: "DWD" },
									{ label: "DWS", value: "DWS" },
									{ label: "ADS", value: "ADS" },
								]}
							/>
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="name" label="模型名称" rules={[{ required: true, message: "请输入模型名称" }]}>
							<Input placeholder="例如 dwd_sales_order" />
						</Form.Item>
						<Form.Item name="alias" label="物理表别名">
							<Input placeholder="可选" />
						</Form.Item>
					</div>
					<Form.Item
						name="sourceDataSourceId"
						label="来源数据源"
						rules={[{ required: true, message: "请选择来源数据源" }]}
					>
						<Select
							placeholder="选择来源数据源"
							options={dataSources.map((ds) => ({
								label: ds?.name || ds?.id,
								value: ds?.id,
							}))}
						/>
					</Form.Item>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="schemaName" label="目标 Schema">
							<Input placeholder="例如 ods" />
						</Form.Item>
						<Form.Item name="materialized" label="物化方式">
							<Select
								placeholder="选择物化方式"
								options={[
									{ label: "table", value: "table" },
									{ label: "view", value: "view" },
									{ label: "incremental", value: "incremental" },
								]}
							/>
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="tags" label="标签 (逗号分隔)">
							<Input placeholder="如 sales,ods" />
						</Form.Item>
						<Form.Item name="status" label="状态">
							<Select
								placeholder="选择状态"
								options={[
									{ label: "草稿", value: "DRAFT" },
									{ label: "已发布", value: "PUBLISHED" },
								]}
							/>
						</Form.Item>
					</div>
					<Form.Item name="description" label="描述">
						<Input.TextArea rows={2} placeholder="模型说明" />
					</Form.Item>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="enabled" label="启用" valuePropName="checked">
							<Switch />
						</Form.Item>
						<Form.Item name="ownerDept" label="归属部门">
							<Input placeholder="可选" />
						</Form.Item>
					</div>
					<Form.Item label="SQL 文件" required>
						<Upload
							accept=".sql"
							beforeUpload={() => false}
							maxCount={1}
							fileList={sqlFileList}
							onChange={({ fileList }) => setSqlFileList(fileList.slice(-1))}
						>
							<Button>选择 SQL</Button>
						</Upload>
					</Form.Item>
					<Form.Item label="CSV 文件 (可选)">
						<Upload
							accept=".csv"
							beforeUpload={() => false}
							maxCount={1}
							fileList={csvFileList}
							onChange={({ fileList }) => setCsvFileList(fileList.slice(-1))}
						>
							<Button>选择 CSV</Button>
						</Upload>
					</Form.Item>
				</Form>
			</Modal>

			<BatchImportModal
				open={batchImportOpen}
				onClose={() => setBatchImportOpen(false)}
				onSuccess={() => { setBatchImportOpen(false); loadModels(); }}
				spaces={spaces}
				dataSources={dataSources}
				activeSpaceId={activeSpace?.id}
			/>

			<Modal
				open={singleArchiveOpen}
				title="归档模型到项目空间"
				onCancel={() => setSingleArchiveOpen(false)}
				onOk={submitSingleArchive}
				okText="归档"
				cancelText="取消"
				confirmLoading={archiveSubmitting}
			>
				<Form layout="vertical" form={singleArchiveForm} disabled={archiveSubmitting}>
					<Form.Item label="模型名称">
						<Input value={activeModel?.name || ""} disabled />
					</Form.Item>
					<Form.Item name="planId" label="目标项目空间" rules={[{ required: true, message: "请选择目标项目空间" }]}>
						<Select
							placeholder="选择目标项目空间"
							options={spaces.map((space) => ({ label: space.name || "未命名", value: space.id }))}
						/>
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={batchArchiveOpen}
				title="批量归档未归档模型"
				onCancel={() => setBatchArchiveOpen(false)}
				onOk={submitBatchArchive}
				okText="批量归档"
				cancelText="取消"
				confirmLoading={archiveSubmitting}
				width={720}
			>
				<Form layout="vertical" form={batchArchiveForm} disabled={archiveSubmitting}>
					<Form.Item name="planId" label="目标项目空间" rules={[{ required: true, message: "请选择目标项目空间" }]}>
						<Select
							placeholder="选择目标项目空间"
							options={spaces.map((space) => ({ label: space.name || "未命名", value: space.id }))}
						/>
					</Form.Item>
					<Form.Item label={`待归档模型 (${batchArchiveSelection.length}/${unassignedModels.length})`}>
						<div className="max-h-[320px] space-y-2 overflow-y-auto rounded border border-border p-3">
							<Checkbox
								checked={batchArchiveSelection.length > 0 && batchArchiveSelection.length === unassignedModels.length}
								indeterminate={
									batchArchiveSelection.length > 0 && batchArchiveSelection.length < unassignedModels.length
								}
								onChange={(event) =>
									setBatchArchiveSelection(event.target.checked ? collectUnassignedModelIds(unassignedModels) : [])
								}
							>
								全选未归档模型
							</Checkbox>
							<div className="space-y-2 pt-2">
								{unassignedModels.map((model, index) => {
									const modelId = model.id || `unassigned-${index}`;
									const checked = batchArchiveSelection.includes(modelId);
									return (
										<label
											key={modelId}
											className="flex cursor-pointer items-start gap-3 rounded border border-border/70 px-3 py-2"
										>
											<Checkbox
												checked={checked}
												disabled={!model.id}
												onChange={(event) => {
													setBatchArchiveSelection((current) =>
														event.target.checked
															? [...current, modelId]
															: current.filter((item) => item !== modelId),
													);
												}}
											/>
											<div className="min-w-0 flex-1">
												<div className="flex items-center gap-2">
													<span className="truncate text-sm font-medium">{model.name || "未命名模型"}</span>
													{layerTag(model.layer || inferLayer(model.name))}
												</div>
												<div className="truncate text-xs text-muted-foreground">{model.modelPath || "未生成路径"}</div>
											</div>
										</label>
									);
								})}
							</div>
						</div>
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={odsGenerateOpen}
				title="从 ODS 一键生成 DWD / DWS / ADS"
				onCancel={() => setOdsGenerateOpen(false)}
				footer={
					<Space>
						<Button onClick={() => setOdsGenerateOpen(false)}>取消</Button>
						<Button type="primary" onClick={submitOdsGenerate} loading={odsGenerateSubmitting}>
							开始生成
						</Button>
					</Space>
				}
			>
				<Form layout="vertical" form={odsGenerateForm} disabled={odsGenerateSubmitting}>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="planId" label="项目空间" rules={[{ required: true, message: "请选择项目空间" }]}>
							<Select
								placeholder="选择项目空间"
								options={spaces.map((space) => ({ label: space.name || "未命名", value: space.id }))}
							/>
						</Form.Item>
						<Form.Item name="sourceDataSourceId" label="来源数据源（可选）">
							<Select
								allowClear
								placeholder={sourcesLoading ? "加载可用来源..." : "按 ODS 映射自动识别"}
								options={odsSourceFilterOptions}
							/>
						</Form.Item>
					</div>
					<Form.Item
						name="mappingIds"
						label="选择 ODS 表"
						rules={[{ required: true, message: "请至少选择一个 ODS 表" }]}
					>
						<Select
							mode="multiple"
							showSearch
							optionFilterProp="label"
							placeholder={sourcesLoading ? "ODS 列表加载中..." : "选择一个或多个 ODS 表"}
							options={odsSourceOptions}
						/>
					</Form.Item>
					{!sourcesLoading && odsSourceOptions.length === 0 && (
						<Alert
							type="warning"
							showIcon
							message="未发现 ODS 映射"
							description={
								<div>
									请先在数据集成中完成 ODS 接入，然后回到本页刷新后选择映射。
									<Button type="link" size="small" onClick={() => router.push("/foundation/data-sources")}>
										去 ODS 接入
									</Button>
								</div>
							}
							className="mb-4"
						/>
					)}
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="schemaName" label="目标 Schema">
							<Input placeholder="可选，留空使用默认 schema" />
						</Form.Item>
						<Form.Item name="materialized" label="物化方式">
							<Select
								allowClear
								placeholder="默认 table"
								options={[
									{ label: "table", value: "table" },
									{ label: "view", value: "view" },
									{ label: "incremental", value: "incremental" },
								]}
							/>
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="status" label="状态">
							<Select
								allowClear
								placeholder="默认 DRAFT"
								options={[
									{ label: "草稿", value: "DRAFT" },
									{ label: "就绪", value: "READY" },
									{ label: "已发布", value: "PUBLISHED" },
								]}
							/>
						</Form.Item>
						<Form.Item name="enabled" label="启用" valuePropName="checked">
							<Switch />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="tags" label="额外标签 (逗号分隔)">
							<Input placeholder="可选，如 finance,patent" />
						</Form.Item>
						<Form.Item name="ownerDept" label="归属部门">
							<Input placeholder="可选" />
						</Form.Item>
					</div>
					<Form.Item label="生成分层">
						<Space size={24}>
							<Form.Item name="createDwd" valuePropName="checked" noStyle>
								<Checkbox>DWD</Checkbox>
							</Form.Item>
							<Form.Item name="createDws" valuePropName="checked" noStyle>
								<Checkbox>DWS</Checkbox>
							</Form.Item>
							<Form.Item name="createAds" valuePropName="checked" noStyle>
								<Checkbox>ADS</Checkbox>
							</Form.Item>
						</Space>
					</Form.Item>
					<Form.Item name="overwriteExisting" valuePropName="checked">
						<Checkbox>已存在模型时覆盖更新</Checkbox>
					</Form.Item>
					<div className="rounded border border-border bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
						<div className="font-medium text-foreground">生成说明</div>
						<ol className="mt-1 list-decimal pl-4">
							<li>先选择项目空间与 ODS 映射，至少选择 1 张 ODS 表。</li>
							<li>默认按映射自动生成 DWD / DWS / ADS 三层模型。</li>
							<li>建议先勾选 DWD，再按需勾选 DWS、ADS。</li>
						</ol>
					</div>
				</Form>
			</Modal>

			<Drawer
				open={snippetDrawerOpen}
				title={snippetTab === "source" ? "插入源表 (ODS)" : "插入模型引用"}
				width={560}
				onClose={() => setSnippetDrawerOpen(false)}
			>
				<div className="mb-4">
					<Input
						placeholder={snippetTab === "source" ? "搜索源表..." : "搜索模型..."}
						value={snippetKeyword}
						onChange={(e) => setSnippetKeyword(e.target.value)}
					/>
				</div>
				<Tabs
					activeKey={snippetTab}
					onChange={(key) => setSnippetTab(key as "source" | "ref")}
					items={[
						{
							key: "source",
							label: "ODS 源表",
							children: sourcesLoading ? (
								<div className="text-center text-sm text-muted-foreground py-8">加载中...</div>
							) : filteredDbtSources.length === 0 ? (
								<EmptyState title="暂无源表" description="请先在数据集成中配置 ODS 表映射。" compact />
							) : (
								<div className="max-h-[400px] overflow-y-auto space-y-2">
									{filteredDbtSources.map((item, idx) => (
										<Card
											key={`${item.schema}-${item.table}-${idx}`}
											size="small"
											className="cursor-pointer hover:border-primary transition-colors"
											onClick={() => insertSnippet(item.sourceSnippet || `{{ source('${item.schema}', '${item.table}') }}`)}
										>
											<div className="flex items-center justify-between">
												<div>
													<div className="font-medium text-foreground">
														{item.table}
													</div>
													<div className="text-xs text-muted-foreground">
														Schema: {item.schema} {item.systemCode ? `· 系统: ${item.systemCode}` : ""}
													</div>
													{item.description && (
														<div className="text-xs text-muted-foreground mt-1">{item.description}</div>
													)}
												</div>
												<Button size="small" type="link">
													插入
												</Button>
											</div>
											<div className="mt-2 rounded bg-muted px-2 py-1 font-mono text-xs text-muted-foreground">
												{item.sourceSnippet || `{{ source('${item.schema}', '${item.table}') }}`}
											</div>
										</Card>
									))}
								</div>
							),
						},
						{
							key: "ref",
							label: "模型引用",
							children: refsLoading ? (
								<div className="text-center text-sm text-muted-foreground py-8">加载中...</div>
							) : filteredDbtRefs.length === 0 ? (
								<EmptyState title="暂无模型" description="请先创建 SQL 模型。" compact />
							) : (
								<div className="max-h-[400px] overflow-y-auto space-y-2">
									{filteredDbtRefs.map((item, idx) => (
										<Card
											key={`${item.id || item.name}-${idx}`}
											size="small"
											className="cursor-pointer hover:border-primary transition-colors"
											onClick={() => insertSnippet(item.refSnippet || `{{ ref('${item.name}') }}`)}
										>
											<div className="flex items-center justify-between">
												<div>
													<div className="flex items-center gap-2">
														<span className="font-medium text-foreground">{item.name}</span>
														{layerTag(item.layer)}
													</div>
													<div className="text-xs text-muted-foreground">
														{item.sourceSystem ? `来源: ${item.sourceSystem}` : ""}
														{item.tags ? ` · 标签: ${item.tags}` : ""}
													</div>
													{item.description && (
														<div className="text-xs text-muted-foreground mt-1">{item.description}</div>
													)}
												</div>
												<Button size="small" type="link">
													插入
												</Button>
											</div>
											<div className="mt-2 rounded bg-muted px-2 py-1 font-mono text-xs text-muted-foreground">
												{item.refSnippet || `{{ ref('${item.name}') }}`}
											</div>
										</Card>
									))}
								</div>
							),
						},
					]}
				/>
			</Drawer>
			<RollbackImpactModal
				open={rollbackOpen}
				request={rollbackRequest}
				onClose={() => setRollbackOpen(false)}
				onSuccess={() => {
					void loadAuditLogs(dbtConfig?.config?.targetDataSourceId);
					setBottomTab("audit");
				}}
			/>
		</div>
	);
}
