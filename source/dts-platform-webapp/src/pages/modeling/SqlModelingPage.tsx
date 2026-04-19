import { Suspense, useCallback, useEffect, useMemo, useRef, useState, type Key } from "react";
import Editor from "@monaco-editor/react";
import { toast } from "sonner";
import { registerDbtLanguage, DBT_SQL_LANGUAGE_ID } from "./dbt-monaco-lang";
import ModelPipeline from "./ModelPipeline";
import {
	Alert,
	Badge,
	Button,
	Checkbox,
	Divider,
	Drawer,
	Dropdown,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Table,
	Tabs,
	Tag,
	Tooltip,
	Typography,
	Popconfirm,
	Segmented,
	Skeleton,
} from "antd";
import {
	PlusOutlined,
	DeleteOutlined,
	SaveOutlined,
	SettingOutlined,
	DownOutlined,
	CodeOutlined,
	TableOutlined,
	LinkOutlined,
	SyncOutlined,
	RocketOutlined,
	CloudUploadOutlined,
	ReloadOutlined,
	FileTextOutlined,
	UndoOutlined,
	CheckCircleOutlined,
	ThunderboltOutlined,
	SafetyCertificateOutlined,
	InboxOutlined,
	BugOutlined,
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
	batchDeleteSqlModels,
	importSqlModel,
	generateSqlModelsFromOds,
	previewSqlModelGovernance,
	executeSqlModelGovernance,
	listModelingPlans,
	syncDbtModels,
	getDbtSyncStatus,
	submitDbtRelease,
	triggerDbtCompile,
	triggerDbtTest,
	triggerDbtDocs,
	triggerDbtRun,
	updateDbtConfig,
	listDbtSources,
	listDbtRefs,
	listTemplateLayers,
	getDbtRunLog,
	previewDbtModel,
	getDbtModelDiagnostics,
	truncateDbtOutputRelation,
	rebuildDbtOutputRelation,
	getDbtGitStatus,
	commitDbtChanges,
	getDbtGitLog,
	getDbtGitDiff,
	revertDbtFile,
	getRollbackAuditLog,
} from "@/api/platformApi";
import dataSourcesService, { type InfraDataSource } from "@/api/services/dataSourcesService";
import BatchImportModal from "./BatchImportModal";
import BatchDeleteResultModal from "./components/BatchDeleteResultModal";
import GovernanceModal from "./components/GovernanceModal";
import ModelFileBrowser from "./components/ModelFileBrowser";
import ModelEditDrawer from "./components/ModelEditDrawer";
import ImportModelModal from "./components/ImportModelModal";
import OdsGenerateModal from "./components/OdsGenerateModal";
import SnippetDrawer from "./components/SnippetDrawer";
import OutputRelationModal from "./components/OutputRelationModal";
import DbtModelDiagnosticsDrawer from "./components/DbtModelDiagnosticsDrawer";
import { useRouter } from "@/routes/hooks";
import { buildArchivePayload, collectUnassignedModelIds } from "./sqlModelArchive.helpers";
import { resolveBatchImportNavigation } from "./batchImportNavigation.helpers";
import {
	buildOperationCompletedMessage,
	buildOperationQueuedMessage,
	buildOperationSkippedMessage,
	buildReleaseSelector,
	createFailedBuildSummary,
	createPendingBuildSummary,
	describeBuildSummary,
	inferBuildOperationFromCommand,
	matchesTriggeredBuildSummary,
} from "./sqlModelBuild.helpers";
import {
	buildTruncateOutputRelationPreview,
	buildRebuildOutputRelationPreview,
} from "./sqlModelOutputAction.helpers";
import {
	applyBatchDeletedModelSelection,
	applyDeletedModelSelection,
	applyManualModelSelection,
	resolveRunsRequestAfterSelection,
} from "./sqlModelDeleteFlow.helpers";
import {
	EMPTY_BULK_SELECTION,
	applyBulkSelectionChange,
	clearBulkSelectionSource,
	clearDeletedBulkSelection,
	resolveSelectedModelIdsFromTreeKeys,
	summarizeBulkSelection,
	type BulkSelectionState,
} from "./sqlModelBulkSelection.helpers";
import {
	type ModelFileBrowserTreeNode,
	deriveDefaultExpandedTreeKeys,
	mergeExpandedTreeKeys,
} from "./modelFileBrowserTree.helpers";
import {
	buildSqlModelBatchDeleteDetail,
	buildSqlModelGovernanceDetail,
	type SqlModelBatchDeleteDetail,
} from "./sqlModelBatchDeleteResult.helpers";
import {
	createPrimaryModelingActions,
	createSecondaryModelingActions,
	shouldRenderInlineGitCommit,
} from "./modelingToolbar.helpers";
import type {
	DbtConfigView,
	DbtRunSummary,
	DbtSyncStatus,
	DbtOutputRelation,
	DbtModelDiagnostics,
	SqlModel,
	ProjectSpace,
	DagRun,
	ModelColumn,
	DbtSourceItem,
	DbtRefItem,
	SqlModelOdsGenerateResult,
	DbtReleaseSubmitResult,
	SqlModelContractImpact,
	SqlModelGovernancePreviewItem,
	SqlModelGovernancePreviewResult,
	SqlModelGovernanceExecuteResult,
	SqlModelBatchDeleteResult,
	OdsSkippedSeverity,
	OdsSkippedEntry,
} from "./sqlModeling.types";
import { resolveReleaseSubmitOutcome } from "./sqlModelReleaseSubmit.helpers";

import { normalizeText, formatDateTime } from "@/utils/textUtils";

const { Text } = Typography;

const syncTag = (synced?: boolean) => {
	if (synced == null) return <Tag>未知</Tag>;
	return synced ? <Tag color="green">已同步</Tag> : <Tag color="red">失败</Tag>;
};

const normalizeUpper = (value?: string) => normalizeText(value).toUpperCase();
const normalizeLower = (value?: string) => normalizeText(value).toLowerCase();

const resolveDbtSelector = (value?: string) => {
	const selector = normalizeText(value);
	if (selector.startsWith("tab:")) {
		return `tag:${selector.slice(4)}`;
	}
	return selector || undefined;
};

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

const buildRunModalTitle = (mode: "compile" | "test" | "build" | "release") => {
	if (mode === "compile") return "编译 (dbt compile)";
	if (mode === "test") return "测试 (dbt test)";
	if (mode === "build") return "构建 (dbt build)";
	return "上线 (dbt build)";
};

const buildRunModalOkText = (mode: "compile" | "test" | "build" | "release") => {
	if (mode === "compile") return "开始编译";
	if (mode === "test") return "开始测试";
	if (mode === "build") return "开始构建";
	return "提交";
};

const resolveModelKey = (model: SqlModel, fallback: string) => model.id || model.name || fallback;
const resolveSpaceKey = (space: ProjectSpace, index: number) => `space-${space.id || index}`;
const UNASSIGNED_SPACE_KEY = "space-unassigned";

export default function SqlModelingPage() {
	const router = useRouter();
	const [pageLoadError, setPageLoadError] = useState(false);
	const [configLoading, setConfigLoading] = useState(false);
	const [configSaving, setConfigSaving] = useState(false);
	const [configOpen, setConfigOpen] = useState(false);
	const [dbtConfig, setDbtConfig] = useState<DbtConfigView | null>(null);
	const [dbtSyncStatus, setDbtSyncStatus] = useState<DbtSyncStatus | null>(null);
	const [spacesLoading, setSpacesLoading] = useState(false);
	const [spaces, setSpaces] = useState<ProjectSpace[]>([]);
	const [activeSpaceKey, setActiveSpaceKey] = useState<string | null>(null);
	const [expandedTreeKeys, setExpandedTreeKeys] = useState<string[]>([]);
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
	const [odsGenerateOpen, setOdsGenerateOpen] = useState(false);
	const [odsGenerateSubmitting, setOdsGenerateSubmitting] = useState(false);
	const [syncingModels, setSyncingModels] = useState(false);
	const [runsLoading, setRunsLoading] = useState(false);
	const [runs, setRuns] = useState<DagRun[]>([]);
	const [runOpen, setRunOpen] = useState(false);
	const [runMode, setRunMode] = useState<"compile" | "test" | "build" | "release">("release");
	const [runSubmitting, setRunSubmitting] = useState(false);
	const [runSelectedModelIds, setRunSelectedModelIds] = useState<string[]>([]);
	const [runModelKeyword, setRunModelKeyword] = useState("");
	const [runSpaceFilter, setRunSpaceFilter] = useState<string | undefined>(undefined);
	const [buildTriggering, setBuildTriggering] = useState<"compile" | "test" | "docs" | "build" | null>(null);
	const [compileResult, setCompileResult] = useState<DbtRunSummary | null>(null);
	const [testResult, setTestResult] = useState<DbtRunSummary | null>(null);
	const [runResult, setRunResult] = useState<DbtRunSummary | null>(null);
	const [columnsLoading, setColumnsLoading] = useState(false);
	const [modelColumns, setModelColumns] = useState<ModelColumn[]>([]);
	const [contractImpactLoading, setContractImpactLoading] = useState(false);
	const [contractImpact, setContractImpact] = useState<SqlModelContractImpact | null>(null);
	const [bottomTab, setBottomTab] = useState("preview");
	const [opsSubTab, setOpsSubTab] = useState("compile");
	const [keyword, setKeyword] = useState("");
	const [activeModelKey, setActiveModelKey] = useState<string | null>(null);
	const [suppressAutoSelect, setSuppressAutoSelect] = useState(false);
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
	const [gitCommitOpen, setGitCommitOpen] = useState(false);
	const [gitCommitMsg, setGitCommitMsg] = useState("");
	const [gitCommitting, setGitCommitting] = useState(false);
	const [gitReverting, setGitReverting] = useState<string | null>(null);
	const [outputAction, setOutputAction] = useState<"truncate" | "rebuild" | null>(null);
	const [outputModalOpen, setOutputModalOpen] = useState(false);
	const [outputRelationLoading, setOutputRelationLoading] = useState(false);
	const [outputRelationSubmitting, setOutputRelationSubmitting] = useState(false);
	const [outputRelation, setOutputRelation] = useState<DbtOutputRelation | null>(null);
	const [outputRelationError, setOutputRelationError] = useState<string | null>(null);
	const [diagnosticsOpen, setDiagnosticsOpen] = useState(false);
	const [diagnosticsLoading, setDiagnosticsLoading] = useState(false);
	const [diagnosticsModel, setDiagnosticsModel] = useState<SqlModel | null>(null);
	const [diagnosticsResult, setDiagnosticsResult] = useState<DbtModelDiagnostics | null>(null);
	const [diagnosticsError, setDiagnosticsError] = useState<string | null>(null);
	const [governanceOpen, setGovernanceOpen] = useState(false);
	const [governancePreviewLoading, setGovernancePreviewLoading] = useState(false);
	const [governanceExecuting, setGovernanceExecuting] = useState(false);
	const [governancePreview, setGovernancePreview] = useState<SqlModelGovernancePreviewItem[]>([]);
	const [bulkSelection, setBulkSelection] = useState<BulkSelectionState>(EMPTY_BULK_SELECTION);
	const [batchDeleteResult, setBatchDeleteResult] = useState<SqlModelBatchDeleteDetail | null>(null);
	const [batchDeleteResultTitle, setBatchDeleteResultTitle] = useState("删除结果");
	const [batchDeleteResultOpen, setBatchDeleteResultOpen] = useState(false);
	const [auditLogs, setAuditLogs] = useState<any[]>([]);
	const [auditLogsLoading, setAuditLogsLoading] = useState(false);
	const [form] = Form.useForm();
	const [runForm] = Form.useForm();
	const [modelForm] = Form.useForm();
	const [importForm] = Form.useForm();
	const [governanceForm] = Form.useForm();
	const [odsGenerateForm] = Form.useForm();
	const [singleArchiveForm] = Form.useForm();
	const [batchArchiveForm] = Form.useForm();
	const selectedOdsSourceDataSourceId = Form.useWatch("sourceDataSourceId", odsGenerateForm);
	const dbtSourcesReqSeqRef = useRef(0);
	const buildPollAbortRef = useRef<AbortController | null>(null);

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
			setPageLoadError(true);
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
		} catch (err: any) {
			setDbtSyncStatus(null);
		}
	}, []);

	const loadModels = useCallback(async () => {
		setModelsLoading(true);
		try {
			const resp = (await listSqlModels()) as SqlModel[];
			const nextModels = Array.isArray(resp) ? resp : [];
			setSqlModels(nextModels);
			return nextModels;
		} catch (err: any) {
			setPageLoadError(true);
			return [];
		} finally {
			setModelsLoading(false);
		}
	}, []);

	const handleBatchImportSuccess = useCallback(
		async (payload: { planId?: string; importedModelNames: string[] }) => {
			setBatchImportOpen(false);
			const nextModels = await loadModels();
			const nextState = resolveBatchImportNavigation(payload.planId, payload.importedModelNames, nextModels);
			if (nextState.activeSpaceKey) {
				setActiveSpaceKey(nextState.activeSpaceKey);
			}
			setActiveModelKey(nextState.activeModelKey);
		},
		[loadModels],
	);

	const loadSources = useCallback(async () => {
		try {
			const resp = await dataSourcesService.list();
			setDataSources(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
		}
	}, []);

	const openBatchImportModal = useCallback(async () => {
		await loadSources();
		setBatchImportOpen(true);
	}, [loadSources]);

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
			setPageLoadError(true);
		} finally {
			setSpacesLoading(false);
		}
	}, []);

	const loadRuns = useCallback(async (options?: { dagId?: string; selector?: string }) => {
		setRunsLoading(true);
		try {
			const resp = (await listDbtRuns(20, {
				dagId: normalizeText(options?.dagId) || undefined,
				selector: resolveDbtSelector(options?.selector) || undefined,
			})) as Record<string, any>;
			const list = Array.isArray(resp?.dag_runs) ? (resp.dag_runs as DagRun[]) : [];
			setRuns(list);
		} catch (err: any) {
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
			setDbtRefs([]);
		} finally {
			setRefsLoading(false);
		}
	}, []);

	// FE-004: Load execution log from Airflow
	const loadExecLog = useCallback(async (dagRunId?: string, dagId?: string, taskId?: string) => {
		if (!dagRunId) {
			setExecLog("");
			return;
		}
		setExecLogLoading(true);
		try {
			const resp = await getDbtRunLog(dagRunId, dagId || taskId ? { dagId, taskId } : undefined);
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
			setPreviewData(null);
		} finally {
			setPreviewLoading(false);
		}
	}, []);

	const loadDiagnostics = useCallback(async (model?: SqlModel | null) => {
		const targetModel = model || null;
		if (!targetModel?.name) {
			setDiagnosticsResult(null);
			setDiagnosticsError("请先选择一个模型");
			return;
		}
		setDiagnosticsLoading(true);
		setDiagnosticsResult(null);
		setDiagnosticsError(null);
		try {
			const resp = (await getDbtModelDiagnostics(targetModel.name)) as DbtModelDiagnostics;
			setDiagnosticsResult(resp || null);
		} catch (err: any) {
			setDiagnosticsResult(null);
			setDiagnosticsError(normalizeText(err?.message) || "诊断加载失败，请稍后重试。");
		} finally {
			setDiagnosticsLoading(false);
		}
	}, []);

	const openDiagnosticsDrawer = useCallback(async (model?: SqlModel | null) => {
		const targetModel = model || null;
		if (!targetModel?.name) {
			toast.error("请先选择一个模型");
			return;
		}
		setDiagnosticsModel(targetModel);
		setDiagnosticsOpen(true);
		await loadDiagnostics(targetModel);
	}, [loadDiagnostics]);

	const openDiagnosticsPreview = useCallback(() => {
		if (!diagnosticsModel?.name) {
			return;
		}
		setBottomTab("preview");
		void loadPreview(diagnosticsModel.name, previewLimit);
	}, [diagnosticsModel, loadPreview, previewLimit]);

	const openDiagnosticsLogs = useCallback(() => {
		setBottomTab("operations");
		setOpsSubTab("execlog");
		const dagRunId = normalizeText(diagnosticsResult?.runtime?.airflowRun?.dagRunId);
		const taskId = normalizeText(diagnosticsResult?.runtime?.airflowRun?.taskId);
		if (dagRunId) {
			void loadExecLog(dagRunId, undefined, taskId || undefined);
		}
	}, [diagnosticsResult, loadExecLog]);

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
		if (gitCommitting) return;
		if (!gitCommitMsg.trim()) {
			toast.error("请输入提交信息");
			return;
		}
		setGitCommitting(true);
		try {
			await commitDbtChanges({ message: gitCommitMsg.trim() });
			toast.success("提交成功");
			setGitCommitMsg("");
			setGitCommitOpen(false);
			void loadGitInfo();
		} catch (err: any) {
		} finally {
			setGitCommitting(false);
		}
	}, [gitCommitMsg, loadGitInfo]);

	const openGitCommitModal = useCallback(() => {
		setGitCommitMsg("");
		setGitCommitOpen(true);
		void loadGitInfo();
	}, [loadGitInfo]);

	const handleGitRevert = useCallback(async (path: string) => {
		setGitReverting(path);
		try {
			await revertDbtFile(path);
			toast.success(`已还原: ${path}`);
			void loadGitInfo();
		} catch (err: any) {
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

	const openOutputAction = async (action: "truncate" | "rebuild") => {
		if (!activeModel?.id) {
			toast.error("请先选择一个模型");
			return;
		}
		setOutputAction(action);
		setOutputModalOpen(true);
		setOutputRelation(null);
		setOutputRelationError(null);
		if (action === "truncate") {
			setOutputRelation(buildTruncateOutputRelationPreview(activeModel, dbtConfig));
			setOutputRelationLoading(false);
			return;
		}
		if (action === "rebuild") {
			setOutputRelation(buildRebuildOutputRelationPreview(activeModel, dbtConfig));
			setOutputRelationLoading(false);
			return;
		}
		setOutputRelationLoading(false);
	};

	const submitOutputAction = async () => {
		if (!activeModel?.id || !outputAction) {
			return;
		}
		setOutputRelationSubmitting(true);
		try {
			if (outputAction === "truncate") {
				const selector =
					resolveDbtSelector(outputRelation?.selector) ||
					resolveDbtSelector(activeModel.dagSelector) ||
					(activeModel?.name ? `model:${activeModel.name}` : "all");
				const resp: any = await truncateDbtOutputRelation({
					modelId: activeModel.id,
					target: normalizeText(dbtConfig?.config?.targetName) || "dev",
				});
				const dagRunId = normalizeText(resp?.dag_run_id || resp?.dagRunId);
				const dagId = normalizeText(resp?.dag_id || resp?.dagId);
				const pendingRun = {
					present: true,
					command: "dbt run-operation truncate_relation",
					status: "RUNNING",
					generatedAt: new Date().toISOString(),
					total: 0,
					success: 0,
					failed: 0,
					skipped: 0,
					failures: [],
					dagRunId,
					dagId,
				} as DbtRunSummary;
				setRunResult(pendingRun);
				setBottomTab("operations"); setOpsSubTab("execlog");
				toast.success(resp?.message || "已提交清空产出表任务");
				setOutputModalOpen(false);
				setOutputAction(null);
				void loadAuditLogs(dbtConfig?.config?.targetDataSourceId);
				await loadRuns({ dagId: dagId || undefined, selector });
				return;
			}

			const selector =
				resolveDbtSelector(outputRelation?.selector) ||
				resolveDbtSelector(activeModel.dagSelector) ||
				(activeModel?.name ? `model:${activeModel.name}` : "all");
			const baselineStatus = (await getDbtSyncStatus(selector ? { models: selector } : undefined)) as DbtSyncStatus;
			const baselineRun = baselineStatus?.latestRun || null;
			const resp: any = await rebuildDbtOutputRelation({
				modelId: activeModel.id,
				target: normalizeText(dbtConfig?.config?.targetName) || "dev",
			});
			const dagRunId = normalizeText(resp?.dag_run_id || resp?.dagRunId);
			const dagId = normalizeText(resp?.dag_id || resp?.dagId);
			const pendingRun = {
				...createPendingBuildSummary("build", selector),
				dagRunId,
				dagId,
			} as DbtRunSummary;
			setRunResult(pendingRun);
			setBottomTab("operations"); setOpsSubTab("execlog");
			setOutputModalOpen(false);
			setOutputAction(null);
			toast.success(resp?.dropMessage || "已提交重建产出表任务");

			const settled = await waitForBuildResult("build", selector, dagId || undefined, dagRunId || undefined, baselineRun);
			if (settled.syncStatus) {
				setDbtSyncStatus(settled.syncStatus);
			} else {
				await loadSyncStatus(selector);
			}
			await loadRuns({ dagId: dagId || undefined, selector });

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
					...createFailedBuildSummary("build", selector, "重建产出表失败，请查看执行日志"),
					dagRunId,
					dagId,
				} as DbtRunSummary;
			}
			setRunResult(resolvedRun || pendingRun);
			if (settled.dagState === "failed" && dagRunId) {
				await loadExecLog(dagRunId, dagId || undefined);
				setBottomTab("operations"); setOpsSubTab("execlog");
			}
			void loadAuditLogs(dbtConfig?.config?.targetDataSourceId);
		} catch (err: any) {
		} finally {
			setOutputRelationSubmitting(false);
		}
	};

	const loadInitialData = useCallback(() => {
		setPageLoadError(false);
		void loadConfig();
		void loadModels();
		void loadSpaces();
		// Non-critical loads
		void loadSyncStatus();
		void loadSources();
		void loadLayers();
		void loadDbtSources();
		void loadDbtRefs();
	}, [loadConfig, loadModels, loadSpaces, loadSyncStatus, loadSources, loadLayers, loadDbtSources, loadDbtRefs]);

	useEffect(() => {
		loadInitialData();
		return () => { buildPollAbortRef.current?.abort(); };
	}, [loadInitialData]);

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
		} finally {
			setConfigSaving(false);
		}
	};

	const openRun = (mode: "compile" | "test" | "build" | "release" = "release") => {
		runForm.resetFields();
		runForm.setFieldsValue({
			target: dbtConfig?.config?.targetName || "",
			vars: "",
			gitRef: "",
			commitSha: "",
			strictMode: false,
		});
		const activeId = String(activeModel?.id || "").trim();
		const checkedIds = Array.from(
			new Set((bulkSelection.selectedIds || []).map((id) => String(id || "").trim()).filter(Boolean)),
		);
		let prefillIds: string[];
		if (checkedIds.length > 0) {
			prefillIds = checkedIds;
		} else if (activeId) {
			prefillIds = [activeId];
		} else if (activeSpace && activeSpaceModels.length > 0) {
			prefillIds = activeSpaceModels
				.map((m) => String(m.id || "").trim())
				.filter(Boolean);
		} else {
			prefillIds = [];
		}
		setRunSelectedModelIds(prefillIds);
		setRunModelKeyword("");
		setRunSpaceFilter(
			activeModel?.planId
				? String(activeModel.planId)
				: activeSpace?.id
					? String(activeSpace.id)
					: undefined,
		);
		setRunMode(mode);
		setRunOpen(true);
	};

		const waitForBuildResult = useCallback(
			async (
				operation: "compile" | "test" | "docs" | "build",
				selector: string,
				dagId: string | undefined,
				dagRunId: string | undefined,
			baselineRun: DbtRunSummary | null,
		) => {
		buildPollAbortRef.current?.abort();
		const ctrl = new AbortController();
		buildPollAbortRef.current = ctrl;
			let lastStatus: DbtSyncStatus | null = null;
			let lastSummary: DbtRunSummary | null = null;
			let lastDagState = "";
			for (let attempt = 0; attempt < 30; attempt += 1) {
			if (ctrl.signal.aborted) return { syncStatus: lastStatus, latestRun: lastSummary, dagState: lastDagState, timedOut: true };
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
		if (runSelectedModelIds.length === 0) {
			toast.error("请至少勾选一个模型");
			return;
		}
		setRunSubmitting(true);
		try {
			const values = await runForm.validateFields();
			const selectedIdSet = new Set(runSelectedModelIds);
			const selectedModels = sqlModels.filter((model) => selectedIdSet.has(String(model.id || "").trim()));
			if (selectedModels.length === 0) {
				toast.error("勾选的模型已失效，请重新选择");
				return;
			}
			type Group = { dagSelector?: string; names: string[]; planName?: string };
			const groupMap = new Map<string, Group>();
			selectedModels.forEach((m) => {
				const ds = normalizeText(m.dagSelector) || "";
				const key = ds || "__none__";
				let g = groupMap.get(key);
				if (!g) {
					g = { dagSelector: ds || undefined, names: [], planName: m.planName || undefined };
					groupMap.set(key, g);
				}
				const nm = normalizeText(m.name);
				if (nm) g.names.push(nm);
			});
			const groups = Array.from(groupMap.values()).filter((g) => g.names.length > 0);
			if (groups.length === 0) {
				toast.error("勾选的模型已失效，请重新选择");
				return;
			}
			if (runMode === "compile" || runMode === "test" || runMode === "build") {
				const target = normalizeText(values.target) || "dev";
				const vars = tryParseJsonObject(values.vars);
				let lastDagId: string | undefined;
				let lastDagRunId: string | undefined;
				let lastSelector = "";
				const operation = runMode;
				for (const g of groups) {
					const modelsSelector = g.names.join(" ");
					let triggerResp: any;
					if (operation === "compile") {
						triggerResp = await triggerDbtCompile({
							models: modelsSelector,
							dagSelector: g.dagSelector,
							target,
							vars,
						});
					} else if (operation === "test") {
						triggerResp = await triggerDbtTest({
							models: modelsSelector,
							dagSelector: g.dagSelector,
							target,
							vars,
						});
					} else {
						triggerResp = await triggerDbtRun({
							models: modelsSelector,
							dagSelector: g.dagSelector,
							target,
							vars,
							operation: "build",
						});
					}
					lastDagRunId = normalizeText(triggerResp?.dag_run_id || triggerResp?.dagRunId);
					lastDagId = normalizeText(triggerResp?.dag_id || triggerResp?.dagId);
					lastSelector = modelsSelector;
				}
				const pendingRun = {
					...createPendingBuildSummary(operation, lastSelector),
					dagRunId: lastDagRunId,
					dagId: lastDagId,
				} as DbtRunSummary;
				if (operation === "compile") {
					setCompileResult(pendingRun);
				} else if (operation === "test") {
					setTestResult(pendingRun);
				} else {
					setRunResult(pendingRun);
				}
				toast.success(
					groups.length > 1
						? `已提交 ${groups.length} 个项目空间的 dbt ${operation}`
						: buildOperationQueuedMessage(operation),
				);
				setRunOpen(false);
				setExecLog("");
				setBottomTab("operations");
				setOpsSubTab(operation === "compile" ? "compile" : operation === "test" ? "test" : "execlog");
				void loadRuns({ dagId: lastDagId || undefined, selector: lastSelector });
				return;
			}
			if (groups.length > 1) {
				Modal.warning({
					title: "跨项目空间上线",
					content: (
						<div style={{ fontSize: 12 }}>
							<p>勾选的模型分布在多个项目空间，上线需按空间分别提交。当前勾选分组：</p>
							<ul style={{ paddingLeft: 18, margin: 0 }}>
								{groups.map((g, idx) => (
									<li key={`${g.dagSelector || "none"}-${idx}`}>
										{(g.planName || g.dagSelector || "未分配")} · {g.names.length} 个模型
									</li>
								))}
							</ul>
						</div>
					),
				});
				return;
			}
			const onlyGroup = groups[0];
			const modelsSelector = onlyGroup.names.join(" ");
			const payload = {
				models: modelsSelector,
				dagSelector: onlyGroup.dagSelector,
				target: normalizeText(values.target) || undefined,
				vars: tryParseJsonObject(values.vars),
				gitRef: normalizeText(values.gitRef) || undefined,
				commitSha: normalizeText(values.commitSha) || undefined,
				strictMode: values.strictMode !== false,
			};
			const submitRelease = async (confirmWarnings = false) =>
				(await submitDbtRelease({
					...payload,
					confirmWarnings,
				})) as DbtReleaseSubmitResult;

			let releaseResult = await submitRelease(false);
			const firstOutcome = resolveReleaseSubmitOutcome(releaseResult);
			if (firstOutcome === "blocked") {
				Modal.error({
					title: "上线阻断",
					content: (
						<div style={{ fontSize: 12 }}>
							<p>当前不满足上线条件，请先修复后重试。</p>
							<ul style={{ paddingLeft: 18, margin: 0 }}>
								{(releaseResult.blockers || []).map((item, idx) => (
									<li key={`${item}-${idx}`}>{item}</li>
								))}
							</ul>
						</div>
					),
				});
				return;
			}
			if (firstOutcome === "warning") {
				const confirmed = await new Promise<boolean>((resolve) =>
					Modal.confirm({
						title: "上线告警",
						content: (
							<div style={{ fontSize: 12 }}>
								<p>检测到以下告警，是否继续提交变更？</p>
								<ul style={{ paddingLeft: 18, margin: 0 }}>
									{(releaseResult.warnings || []).map((item, idx) => (
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
				releaseResult = await submitRelease(true);
				if (resolveReleaseSubmitOutcome(releaseResult) !== "submitted") {
					Modal.error({
						title: "上线提交失败",
						content: (
							<div style={{ fontSize: 12 }}>
								<p>后端未返回成功提交结果，请检查以下信息后重试。</p>
									<ul style={{ paddingLeft: 18, margin: 0 }}>
										{[...(releaseResult.blockers || []), ...(releaseResult.warnings || [])].map((item, idx) => (
											<li key={`${item}-${idx}`}>{item}</li>
										))}
									</ul>
							</div>
						),
					});
					return;
				}
			}
			const dagRunId = normalizeText(releaseResult?.dagRunId);
			const dagId = normalizeText(releaseResult?.dagId);
			setRunResult({
				...createPendingBuildSummary("build", buildReleaseSelector(modelsSelector)),
				dagRunId,
				dagId,
			});
			toast.success("dbt build 已提交");
			setRunOpen(false);
			void loadRuns({ dagId: dagId || undefined, selector: modelsSelector });
		} catch (err: any) {
		} finally {
			setRunSubmitting(false);
		}
	};

	const triggerBuildOperation = async (operation: "compile" | "test" | "docs" | "build") => {
		setBuildTriggering(operation);
		try {
			let selector: string;
			let dagSelector: string | undefined;
			const checkedIdSet = new Set(
				(bulkSelection.selectedIds || []).map((id) => String(id || "").trim()).filter(Boolean),
			);
			const checkedModels =
				checkedIdSet.size > 0
					? sqlModels.filter((m) => checkedIdSet.has(String(m.id || "").trim()))
					: [];
			if (checkedModels.length > 0) {
				const names = checkedModels
					.map((m) => normalizeText(m.name))
					.filter(Boolean) as string[];
				selector = names.length > 0 ? names.join(" ") : "all";
				dagSelector =
					checkedModels.map((m) => normalizeText(m.dagSelector)).find(Boolean) || undefined;
			} else if (activeModel) {
				selector = normalizeText(activeModel.name) || "all";
				dagSelector = normalizeText(activeModel.dagSelector) || undefined;
			} else if (activeSpace && activeSpaceModels.length > 0) {
				const names = activeSpaceModels
					.map((m) => normalizeText(m.name))
					.filter(Boolean) as string[];
				selector = names.length > 0 ? names.join(" ") : "all";
				dagSelector = activeSpaceModels
					.map((m) => normalizeText(m.dagSelector))
					.find(Boolean) || undefined;
			} else {
				selector = "all";
				dagSelector = undefined;
			}
			const baselineStatus = (await getDbtSyncStatus(selector ? { models: selector } : undefined)) as DbtSyncStatus;
			const baselineRun = baselineStatus?.latestRun || null;
			const payload = {
				models: selector,
				dagSelector,
				target: normalizeText(dbtConfig?.config?.targetName) || "dev",
			};
			let triggerResp: any;
			if (operation === "compile") {
				triggerResp = await triggerDbtCompile(payload);
			} else if (operation === "test") {
				triggerResp = await triggerDbtTest(payload);
			} else if (operation === "build") {
				triggerResp = await triggerDbtRun({ ...payload, operation: "build" });
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
			toast.success(buildOperationQueuedMessage(operation));
			setExecLog("");
			if (operation === "compile") {
				setCompileResult(pendingRun);
				setBottomTab("operations"); setOpsSubTab("compile");
			} else if (operation === "test") {
				setTestResult(pendingRun);
				setBottomTab("operations"); setOpsSubTab("test");
			} else {
				setRunResult(pendingRun);
				setBottomTab("operations"); setOpsSubTab("execlog");
			}
			const settled = await waitForBuildResult(operation, selector, dagId || undefined, dagRunId || undefined, baselineRun);
			if (settled.syncStatus) {
				setDbtSyncStatus(settled.syncStatus);
			} else {
				await loadSyncStatus(selector);
			}
			await loadRuns({ dagId: dagId || undefined, selector });

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
					setBottomTab("operations"); setOpsSubTab("execlog");
				}
				toast.error(`dbt ${operation} 失败`);
				return;
			}
			if (finalStatus === "SUCCESS") {
				toast.success(buildOperationCompletedMessage(operation));
				return;
			}
			if (finalStatus === "SKIPPED") {
				toast.warning(buildOperationSkippedMessage(operation));
				return;
			}
			if (settled.timedOut) {
				toast.warning(`dbt ${operation} 仍在运行，请稍后刷新结果`);
			}
		} catch (err: any) {
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
		setBulkSelection((current) =>
			applyBulkSelectionChange(
				current,
				(current.sourceSelections?.list || []).filter((id) => unassignedModelIds.includes(id)),
				"list",
			),
		);
		setBatchArchiveOpen(true);
	};

	const openGovernanceModal = () => {
		governanceForm.resetFields();
		governanceForm.setFieldsValue({
			planId: activeSpace?.id || spaces[0]?.id,
			ruleKeys: ["duplicate-model", "preset:project-management-legacy-program"],
			deleteFiles: true,
		});
		setGovernancePreview([]);
		setBulkSelection((current) => clearBulkSelectionSource(current, "governance"));
		setGovernanceOpen(true);
	};

	const handleGovernancePreview = async () => {
		const values = await governanceForm.validateFields();
		setGovernancePreviewLoading(true);
		try {
			const sqlKeywords = normalizeText(values.sqlKeywords)
				.split(/\n|,/)
				.map((item) => normalizeText(item))
				.filter(Boolean);
			const resp = (await previewSqlModelGovernance({
				planId: values.planId,
				ruleKeys: values.ruleKeys || [],
				sqlKeywords,
				namePattern: normalizeText(values.namePattern) || undefined,
				modelPathPattern: normalizeText(values.modelPathPattern) || undefined,
				tag: normalizeText(values.tag) || undefined,
				layer: normalizeText(values.layer) || undefined,
			})) as SqlModelGovernancePreviewResult;
			const items = Array.isArray(resp?.items) ? resp.items : [];
			setGovernancePreview(items);
			setBulkSelection((current) =>
				applyBulkSelectionChange(
					current,
					items.map((item) => String(item.modelId || "").trim()).filter(Boolean),
					"governance",
				),
			);
			if (!items.length) {
				toast.info("未命中待治理模型");
			}
		} catch (err: any) {
		} finally {
			setGovernancePreviewLoading(false);
		}
	};

	const handleGovernanceExecute = async () => {
		if (!bulkSelection.selectedIds.length) {
			toast.error("请至少选择一个待治理模型");
			return;
		}
		const values = governanceForm.getFieldsValue();
		setGovernanceExecuting(true);
		try {
			const requestedIds = [...bulkSelection.selectedIds];
			const resp = (await executeSqlModelGovernance({
				modelIds: requestedIds,
				deleteFiles: values.deleteFiles !== false,
			})) as SqlModelGovernanceExecuteResult;
			const deleted = Number(resp?.deleted || 0);
			const failed = Number(resp?.failed || 0);
			const skipped = Number(resp?.skipped || 0);
			const deletedIds = (resp?.items || [])
				.filter((item) => String(item?.result || "").toUpperCase() === "DELETED")
				.map((item) => String(item?.modelId || "").trim())
				.filter(Boolean);
			setBulkSelection((current) =>
				clearBulkSelectionSource(clearDeletedBulkSelection(current, deletedIds), "governance"),
			);
			setBatchDeleteResult(
				buildSqlModelGovernanceDetail({
					requestedIds,
					preview: governancePreview,
					result: resp,
				}),
			);
			setBatchDeleteResultTitle("治理结果");
			setBatchDeleteResultOpen(true);
			toast[failed > 0 || skipped > 0 ? "warning" : "success"](
				`治理完成：成功 ${deleted} 个，失败 ${failed} 个，跳过 ${skipped} 个`,
			);
			await loadModels();
			setGovernancePreview([]);
			setGovernanceOpen(false);
		} catch (err: any) {
		} finally {
			setGovernanceExecuting(false);
		}
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
		} finally {
			setImportSubmitting(false);
		}
	};

	const submitOdsGenerate = async () => {
		setOdsGenerateSubmitting(true);
		try {
			const values = await odsGenerateForm.validateFields(["planId", "mappingIds"]);
			if (!Array.isArray(values.mappingIds) || values.mappingIds.length === 0) {
				toast.error("请至少选择一个 ODS 数据源映射");
				setOdsGenerateSubmitting(false);
				return;
			}
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
								<li>完成验证后回到逻辑建模执行"提交变更"。</li>
							</ol>
						</div>
					</div>
				),
			});
			setOdsGenerateOpen(false);
			setActiveSpaceKey(`space-${values.planId}`);
			await loadModels();
		} catch (err: any) {
		} finally {
			setOdsGenerateSubmitting(false);
		}
	};

	const removeModel = () => {
		if (!activeModel?.id) return;
		const requestedIds = [String(activeModel.id)];
		const selectedSnapshot = [
			{
				id: activeModel.id,
				name: activeModel.name,
				layer: activeModel.layer,
				planName: activeModel.planName,
				modelPath: activeModel.modelPath,
			},
		];
		Modal.confirm({
			title: "删除模型？",
			content: `确认删除模型 ${activeModel.name || ""} 吗？`,
			okText: "确认删除",
			okButtonProps: { danger: true },
			onOk: async () => {
				try {
					const resp = (await batchDeleteSqlModels({
						modelIds: requestedIds,
					})) as SqlModelBatchDeleteResult;
					const failedIds = new Set(
						(resp?.failures || []).map((failure) => String(failure?.modelId || "").trim()).filter(Boolean),
					);
					const deletedIds = requestedIds.filter((id) => !failedIds.has(id));
					const nextSelection = applyDeletedModelSelection({
						activeModelKey,
						deletedModelKey: deletedIds[0] || null,
					});
					setActiveModelKey(nextSelection.nextActiveModelKey);
					setSuppressAutoSelect(nextSelection.suppressAutoSelect);
					setBulkSelection((current) => clearDeletedBulkSelection(current, deletedIds));
					setBatchDeleteResult(
						buildSqlModelBatchDeleteDetail({
							requestedIds,
							models: selectedSnapshot,
							result: resp,
						}),
					);
					setBatchDeleteResultTitle("删除结果");
					setBatchDeleteResultOpen(true);
					const failed = Number(resp?.failed || 0);
					toast[failed > 0 ? "warning" : "success"](
						failed > 0 ? `模型删除失败，请查看结果明细` : `模型已删除`,
					);
					await loadModels();
				} catch (err: any) {
				}
			},
		});
	};

	const removeSelectedModels = () => {
		if (!bulkSelection.selectedIds.length) {
			toast.error("请至少选择一个模型");
			return;
		}
		const requestedIds = [...bulkSelection.selectedIds];
		const selectedSnapshot = selectedModels.map((model) => ({
			id: model.id,
			name: model.name,
			layer: model.layer,
			planName: model.planName,
			modelPath: model.modelPath,
		}));
		const layerSummary =
			Array.from(new Set(selectedModels.map((model) => model.layer || inferLayer(model.name)))).join("、") || "未分层";
		Modal.confirm({
			title: "批量删除模型？",
			content: `确认删除已选 ${bulkSelection.selectedIds.length} 个模型吗？涉及分层：${layerSummary}。`,
			okText: "确认删除",
			okButtonProps: { danger: true },
			onOk: async () => {
				try {
					const resp = (await batchDeleteSqlModels({
						modelIds: requestedIds,
					})) as SqlModelBatchDeleteResult;
					const failedIds = new Set(
						(resp?.failures || []).map((failure) => String(failure?.modelId || "").trim()).filter(Boolean),
					);
					const deletedIds = requestedIds.filter((id) => !failedIds.has(id));
					const nextSelection = applyBatchDeletedModelSelection({
						activeModelKey,
						deletedModelKeys: deletedIds,
					});
					setActiveModelKey(nextSelection.nextActiveModelKey);
					setSuppressAutoSelect(nextSelection.suppressAutoSelect);
					setBulkSelection((current) => clearDeletedBulkSelection(current, deletedIds));
					setBatchDeleteResult(
						buildSqlModelBatchDeleteDetail({
							requestedIds,
							models: selectedSnapshot,
							result: resp,
						}),
					);
					setBatchDeleteResultTitle("删除结果");
					setBatchDeleteResultOpen(true);
					const deleted = Number(resp?.deleted || 0);
					const failed = Number(resp?.failed || 0);
					toast[failed > 0 ? "warning" : "success"](`已删除 ${deleted} 个模型${failed > 0 ? `，失败 ${failed} 个` : ""}`);
					await loadModels();
				} catch (err: any) {
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
		if (suppressAutoSelect) {
			return;
		}
		if (!activeModelKey && models.length > 0) {
			const key = resolveModelKey(models[0], "model-0");
			setActiveModelKey(key);
		}
	}, [activeModelKey, models, suppressAutoSelect]);

	useEffect(() => {
		if (suppressAutoSelect) {
			return;
		}
		if (activeModelKey && !modelKeyMap.has(activeModelKey) && models.length > 0) {
			const key = resolveModelKey(models[0], "model-0");
			setActiveModelKey(key);
		}
	}, [activeModelKey, modelKeyMap, models, suppressAutoSelect]);

	const activeModel = useMemo(() => {
		if (!activeModelKey) return null;
		return modelKeyMap.get(activeModelKey) || null;
	}, [activeModelKey, modelKeyMap]);

	const activeRunsSelector = useMemo(
		() => resolveDbtSelector(activeModel?.dagSelector) || (activeModel?.name ? `model:${activeModel.name}` : undefined),
		[activeModel?.dagSelector, activeModel?.name],
	);
	const activeRunsRequest = useMemo(() => resolveRunsRequestAfterSelection(activeRunsSelector), [activeRunsSelector]);

	useEffect(() => {
		setSqlDraft(activeModel?.sql || "");
	}, [activeModel?.id]);

	useEffect(() => {
		void loadModelColumns(activeModel?.id);
	}, [activeModel?.id, loadModelColumns]);

	useEffect(() => {
		void loadContractImpact(activeModel?.id);
	}, [activeModel?.id, loadContractImpact]);

	useEffect(() => {
		if (!activeRunsRequest.shouldLoadRuns) {
			setRuns([]);
			return;
		}
		void loadRuns({ selector: activeRunsRequest.selector });
	}, [activeRunsRequest.selector, activeRunsRequest.shouldLoadRuns, loadRuns]);

	const sqlDirty = !!activeModel && sqlDraft !== (activeModel?.sql || "");

	useEffect(() => {
		if (!sqlDirty) return;
		const handler = (e: BeforeUnloadEvent) => {
			e.preventDefault();
			e.returnValue = "";
		};
		window.addEventListener("beforeunload", handler);
		return () => window.removeEventListener("beforeunload", handler);
	}, [sqlDirty]);

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
	const unassignedModelIds = useMemo(() => collectUnassignedModelIds(unassignedModels), [unassignedModels]);
	const unassignedModelIdSet = useMemo(() => new Set(unassignedModelIds), [unassignedModelIds]);
	const selectionSummary = useMemo(() => summarizeBulkSelection(bulkSelection), [bulkSelection]);


	const buildLayerNodes = useCallback((input: SqlModel[]): ModelFileBrowserTreeNode[] => {
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
				nodeType: "layer" as const,
				children: list.map((model, idx) => ({
					title: <span className="inline-flex items-center gap-1">{model.name || model.alias || "未命名模型"}{model.status && model.status !== "DRAFT" && <span className={`inline-block rounded px-1 text-[10px] leading-4 ${model.status === "PUBLISHED" ? "bg-green-500/15 text-green-600" : model.status === "TESTED" ? "bg-orange-500/15 text-orange-600" : "bg-blue-500/15 text-blue-600"}`}>{model.status === "PUBLISHED" ? "已发布" : model.status === "TESTED" ? "已测试" : model.status === "COMMITTED" ? "已提交" : model.status}</span>}</span>,
					key: `model:${resolveModelKey(model, `${layer}-${idx}`)}`,
					nodeType: "model" as const,
					isLeaf: true,
					icon: <></>,
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
				title: "选择器",
				key: "selector",
				width: 220,
				ellipsis: true,
				render: (_value, row) => normalizeText(row?.conf?.models) || "all",
			},
			{
				title: "目标",
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
								setBottomTab("operations"); setOpsSubTab("execlog");
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

	const runFilteredModels = useMemo(() => {
		const keyword = runModelKeyword.trim().toLowerCase();
		const bySpace = sqlModels.filter((model) => {
			if (!runSpaceFilter) return true;
			if (runSpaceFilter === UNASSIGNED_SPACE_KEY) return !model.planId;
			return String(model.planId || "") === runSpaceFilter;
		});
		if (!keyword) return bySpace;
		return bySpace.filter((model) => {
			const fields = [model.name, model.planName, model.layer, model.alias]
				.map((value) => normalizeText(value).toLowerCase())
				.filter(Boolean);
			return fields.some((value) => value.includes(keyword));
		});
	}, [sqlModels, runModelKeyword, runSpaceFilter]);
	const canArchiveActiveModel = !!activeModel?.id && !activeModel?.planId && spaces.length > 0;
	const canBatchArchive = unassignedModels.length > 0 && spaces.length > 0;
	const checkedModelKeys = useMemo(
		() => (bulkSelection.sourceSelections?.tree || []).map((id) => `model:${id}`),
		[bulkSelection.sourceSelections],
	);
	const batchArchiveSelection = useMemo(
		() => (bulkSelection.sourceSelections?.list || []).filter((id) => unassignedModelIdSet.has(id)),
		[bulkSelection.sourceSelections, unassignedModelIdSet],
	);
	const governanceSelection = useMemo(
		() => bulkSelection.sourceSelections?.governance || [],
		[bulkSelection.sourceSelections],
	);
	const visibleModelIds = useMemo(
		() => activeSpaceModels.map((model) => String(model.id || "").trim()).filter(Boolean),
		[activeSpaceModels],
	);
	const selectedModels = useMemo(() => {
		const selectedIdSet = new Set(bulkSelection.selectedIds);
		return sqlModels.filter((model) => {
			const id = String(model.id || "").trim();
			return !!id && selectedIdSet.has(id);
		});
	}, [bulkSelection.selectedIds, sqlModels]);

	const treeData = useMemo<ModelFileBrowserTreeNode[]>(() => {
		const nodes = spaces.map((space, idx) => {
			const key = resolveSpaceKey(space, idx);
			const spaceModels = filteredModels.filter((model) => model.planId === space.id);
			return {
				title: space.name || "未命名项目空间",
				key,
				nodeType: "space" as const,
				children: key === activeSpaceKey ? buildLayerNodes(spaceModels) : [],
			};
		});
		if (unassignedModels.length > 0) {
			nodes.push({
				title: `未归档工作区模型 (${unassignedModels.length})`,
				key: UNASSIGNED_SPACE_KEY,
				nodeType: "space" as const,
				children: activeSpaceKey === UNASSIGNED_SPACE_KEY ? buildLayerNodes(unassignedModels) : [],
			});
		}
		return nodes;
	}, [spaces, activeSpaceKey, filteredModels, buildLayerNodes, unassignedModels]);

	const defaultExpandedTreeKeys = useMemo(
		() => deriveDefaultExpandedTreeKeys(treeData, activeSpaceKey),
		[treeData, activeSpaceKey],
	);

	useEffect(() => {
		setExpandedTreeKeys((current) => mergeExpandedTreeKeys(current, treeData, defaultExpandedTreeKeys));
	}, [treeData, defaultExpandedTreeKeys]);

	useEffect(() => {
		const existingIds = new Set(sqlModels.map((model) => String(model.id || "").trim()).filter(Boolean));
		setBulkSelection((current) => {
			const deletedIds = current.selectedIds.filter((id) => !existingIds.has(id));
			return deletedIds.length ? clearDeletedBulkSelection(current, deletedIds) : current;
		});
	}, [sqlModels]);

	// 模型操作下拉菜单
	const modelMenuItems = [
		{ key: "create", label: "新建模型", onClick: openCreateModel },
		{ key: "edit", label: "编辑模型", onClick: openEditModel },
		{ type: "divider" as const },
		{ key: "import", label: "导入模型", onClick: openImportModel },
		{ key: "batch-import", label: "批量导入", onClick: openBatchImportModal },
		{ key: "generate-ods", label: "从 ODS 一键生成", onClick: openOdsGenerateModel },
		{ type: "divider" as const },
		{ key: "governance", icon: <SafetyCertificateOutlined />, label: "模型治理", onClick: openGovernanceModal },
		{ key: "batch-archive", icon: <InboxOutlined />, label: "批量归档", onClick: openBatchArchive },
		{ type: "divider" as const },
		{ key: "delete", label: "删除模型", danger: true, onClick: removeModel },
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
		<div className="space-y-6" data-testid="platform-sql-modeling-page">
			{pageLoadError && (
				<Alert
					type="error"
					showIcon
					message="数据加载失败"
					description="网络连接不稳定，部分数据未能加载。"
					action={<Button size="small" onClick={loadInitialData}>重新加载</Button>}
					className="mb-4"
				/>
			)}
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
					<Button danger icon={<DeleteOutlined />} onClick={removeSelectedModels} disabled={!bulkSelection.selectedIds.length}>
						批量删除
					</Button>
					{/* 保存按钮 */}
					<Button
						icon={<SaveOutlined />}
						onClick={saveSqlDraft}
						disabled={!activeModel || !sqlDirty || !workspaceOk}
					>
						保存
					</Button>
					<Button icon={<BugOutlined />} onClick={() => void openDiagnosticsDrawer(activeModel)} disabled={!activeModel?.name}>
						断链诊断
					</Button>
					{/* 插入代码 */}
						<Dropdown menu={{ items: insertMenuItems }} trigger={["click"]}>
							<Button icon={<CodeOutlined />} disabled={!activeModel}>
								插入 <DownOutlined className="text-xs" />
							</Button>
						</Dropdown>
					</div>
						<div className="flex items-center gap-2">
							{createPrimaryModelingActions().map((action) => {
								if (action.key === "compile") {
									return (
									<Button
										key={action.key}
										icon={<CodeOutlined />}
										onClick={() => openRun("compile")}
										loading={buildTriggering === "compile"}
										disabled={sqlModels.length === 0 || !configEnabled || !workspaceOk || buildTriggering != null}
										data-testid="platform-sql-modeling-compile"
									>
										{action.label}
									</Button>
								);
							}
								if (action.key === "test") {
									return (
										<Button
										key={action.key}
										icon={<CheckCircleOutlined />}
										onClick={() => openRun("test")}
										loading={buildTriggering === "test"}
										disabled={sqlModels.length === 0 || !configEnabled || !workspaceOk || buildTriggering != null}
										data-testid="platform-sql-modeling-test"
									>
										{action.label}
										</Button>
									);
								}
								if (action.key === "build") {
									return (
										<Button
											key={action.key}
											type="primary"
											icon={<ThunderboltOutlined />}
											onClick={() => openRun("build")}
											loading={buildTriggering === "build"}
											disabled={sqlModels.length === 0 || !configEnabled || !workspaceOk || buildTriggering != null}
											data-testid="platform-sql-modeling-build"
										>
											{action.label}
										</Button>
									);
								}
								return (
									<Button
										key={action.key}
										icon={<RocketOutlined />}
										onClick={() => openRun("release")}
										disabled={!workspaceOk}
										data-testid="platform-sql-modeling-release"
									>
										{action.label}
									</Button>
								);
							})}
						<Dropdown
								menu={{
									items: createSecondaryModelingActions().map((action) => {
										if (action.key === "commit") {
											return {
												key: action.key,
												label: action.label,
												icon: <CloudUploadOutlined />,
												disabled: !workspaceOk,
												onClick: () => void openGitCommitModal(),
											};
										}
										if (action.key === "sync") {
											return {
												key: action.key,
												label: action.label,
												icon: <SyncOutlined />,
												disabled: !workspaceOk || syncingModels,
												onClick: () => void handleSyncModels(),
											};
										}
										if (action.key === "docs") {
											return {
												key: action.key,
												label: action.label,
												icon: <FileTextOutlined />,
												disabled: !configEnabled || !workspaceOk || buildTriggering != null,
												onClick: () => void triggerBuildOperation("docs"),
											};
										}
										return {
											key: action.key,
											label: action.label,
											icon: <UndoOutlined />,
											disabled: !configEnabled || !workspaceOk || !activeModel,
											children: [
												{
													key: "truncate",
													label: "清空产出表 (Level 1)",
													icon: <DeleteOutlined />,
													onClick: () => void openOutputAction("truncate"),
												},
												{
													key: "rebuild",
													label: "重建产出表 (Level 2)",
													icon: <UndoOutlined />,
													danger: true,
													onClick: () => void openOutputAction("rebuild"),
												},
											],
										};
									}),
								}}
							disabled={!workspaceOk}
						>
							<Button>
								更多 <DownOutlined />
							</Button>
						</Dropdown>
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
				<ModelFileBrowser
					keyword={keyword}
					onKeywordChange={setKeyword}
					treeData={treeData}
					selectedKeys={activeModelKey ? [`model:${activeModelKey}`] : activeSpaceKey ? [activeSpaceKey] : []}
					checkedKeys={checkedModelKeys}
					expandedKeys={expandedTreeKeys}
					onSelect={(keys: Key[]) => {
						const key = String(keys[0] || "");
						if (!key) return;
						if (key.startsWith("space-")) {
							setSuppressAutoSelect(false);
							setActiveSpaceKey(key);
							setActiveModelKey(null);
							return;
						}
						if (key.startsWith("layer-")) return;
						if (key.startsWith("model:")) {
							const nextSelection = applyManualModelSelection(key.replace("model:", ""));
							setSuppressAutoSelect(nextSelection.suppressAutoSelect);
							setActiveModelKey(nextSelection.nextActiveModelKey);
							return;
						}
						const nextSelection = applyManualModelSelection(key);
						setSuppressAutoSelect(nextSelection.suppressAutoSelect);
						setActiveModelKey(nextSelection.nextActiveModelKey);
					}}
					onCheck={(keys: string[]) =>
						setBulkSelection((current) =>
							applyBulkSelectionChange(
								current,
								resolveSelectedModelIdsFromTreeKeys(keys, {
									models: sqlModels,
									activeSpaceModels,
									unassignedModels,
									spaceKeyToPlanId: Object.fromEntries(
										Array.from(spaceKeyMap.entries()).map(([key, space]) => [key, String(space.id || "").trim()]),
									),
									unassignedSpaceKey: UNASSIGNED_SPACE_KEY,
									inferLayer,
								}),
								"tree",
							),
						)
					}
					onExpand={setExpandedTreeKeys}
					selectedCount={bulkSelection.selectedIds.length}
					selectionBreakdown={`树 ${selectionSummary.tree} / 治理 ${selectionSummary.governance} / 列表 ${selectionSummary.list}`}
					onSelectAllCurrent={() => setBulkSelection((current) => applyBulkSelectionChange(current, visibleModelIds, "tree"))}
					onClearSelection={() => setBulkSelection((current) => clearBulkSelectionSource(current, "tree"))}
					onBatchDelete={removeSelectedModels}
					batchDeleteDisabled={!bulkSelection.selectedIds.length}
					loading={spacesLoading || modelsLoading}
					showEmptyModelsHint={!modelsLoading && treeData.length > 0 && activeLayerNodes.length === 0}
				/>

				<div className="flex flex-1 flex-col">
					<div className="flex items-center justify-between border-b border-border bg-muted/20 px-4 py-1.5">
						<div className="flex items-center gap-2" data-testid="platform-sql-modeling-active-model">
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
							{activeModel?.name ? (
								<Button size="small" icon={<BugOutlined />} onClick={() => void openDiagnosticsDrawer(activeModel)}>
									诊断
								</Button>
							) : null}
							<Tooltip title="刷新模型列表">
								<Button size="small" icon={<SyncOutlined />} onClick={loadModels} loading={modelsLoading} />
							</Tooltip>
						</Space>
					</div>
					{activeModel && (
						<ModelPipeline
							modelStatus={activeModel.status}
						/>
					)}
						<div className="flex-1 overflow-auto bg-muted/10 px-6 py-4">
							{activeModel ? (
							<Suspense
								fallback={
									<div style={{ padding: 24 }}>
										<Skeleton active title={{ width: '40%' }} paragraph={{ rows: 10, width: ['100%', '95%', '80%', '90%', '70%', '100%', '85%', '60%', '75%', '50%'] }} />
									</div>
								}
							>
								<Editor
									height="100%"
									language={DBT_SQL_LANGUAGE_ID}
									theme="vs"
									value={sqlDraft}
									beforeMount={(monaco) => registerDbtLanguage(monaco)}
									onChange={(value) => setSqlDraft(value || "")}
									options={{
										fontFamily: "'JetBrains Mono', 'Fira Code', 'Source Code Pro', 'Cascadia Code', Consolas, 'Courier New', monospace",
										fontSize: 13,
										fontLigatures: true,
										minimap: { enabled: false },
										automaticLayout: true,
										wordWrap: "on",
										scrollBeyondLastLine: false,
										tabSize: 2,
									}}
								/>
							</Suspense>
						) : (
							<div style={{ padding: 24, textAlign: 'center' }}>
								<Typography.Text type="secondary">请从左侧选择一个模型开始编辑</Typography.Text>
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
													<div className="rounded border border-border bg-muted/20 px-2 py-2">
														<div className="mb-2 flex items-center justify-between">
															<span className="text-muted-foreground">模型诊断</span>
															<Button size="small" type="link" className="px-0" onClick={() => void openDiagnosticsDrawer(activeModel)}>
																打开
															</Button>
														</div>
														<div className="text-[11px] text-muted-foreground">
															查看 ODS → DWD → DWS 断链提示、上游行数和推荐排查 SQL。
														</div>
													</div>
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
												{columnsLoading && modelColumns.length === 0 ? (
													<Skeleton active paragraph={{ rows: 4 }} title={false} />
												) : modelColumns.length ? (
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
											{previewLoading && !previewData ? (
												<div className="p-4">
													<Skeleton active paragraph={{ rows: 4 }} />
												</div>
											) : previewData ? (
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
								key: "operations",
								label: "操作记录",
								children: (
									<div className="p-2">
										<Segmented
											size="small"
											value={opsSubTab}
											onChange={(val) => setOpsSubTab(val as string)}
											options={[
												{ label: "编译", value: "compile" },
												{ label: "测试", value: "test" },
												{ label: "执行日志", value: "execlog" },
												{ label: "运行记录", value: "runs" },
												{ label: "变更状态", value: "gitstatus" },
												{ label: "回退记录", value: "audit" },
											]}
											className="mb-2"
											data-testid="platform-sql-modeling-ops-tabs"
										/>
										{opsSubTab === "compile" && (() => {
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
										})()}
										{opsSubTab === "test" && (() => {
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
										})()}
										{opsSubTab === "execlog" && (() => {
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
													{execLogLoading ? (
														<div className="p-4">
															<Skeleton active paragraph={{ rows: 4 }} />
														</div>
													) : execLog ? (
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
										})()}
										{opsSubTab === "runs" && (
											runs.length === 0 && !runsLoading ? (
												<div className="p-4 text-sm text-muted-foreground">暂无运行记录。</div>
											) : (
												<Table
													rowKey={(row, index) => row.dag_run_id || `row-${index}`}
													size="small"
													pagination={false}
													columns={runColumns}
													dataSource={runs}
													loading={runsLoading}
													scroll={{ y: 170 }}
												/>
											)
										)}
										{opsSubTab === "gitstatus" && (() => {
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
															{allChanges.length > 0 && shouldRenderInlineGitCommit() && (
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
															{allChanges.length > 0 && !shouldRenderInlineGitCommit() && (
																<div className="rounded border border-dashed border-border bg-muted/10 px-3 py-2 text-xs text-muted-foreground">
																	提交入口已统一到顶部工具栏"提交变更"。
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
										})()}
										{opsSubTab === "audit" && (
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
										)}
									</div>
								),
							},
							{
								key: "guide",
								label: "操作说明",
								children: (
									<div className="p-4 text-sm leading-relaxed overflow-auto" style={{ maxHeight: 240 }}>
										<div className="font-semibold text-base mb-3">建模与上线操作流程</div>

										<div className="font-medium mb-1">一、数据准备</div>
										<ol className="list-decimal pl-5 text-xs text-muted-foreground mb-3">
											<li>在「数据集成」中完成 Excel / 数据库的入湖任务，生成 ODS 层表。</li>
											<li>回到本页，点击「ODS 一键生成」选择源表，系统自动生成 DWD/DWS/ADS 模型模板。</li>
										</ol>

										<div className="font-medium mb-1">二、模型编辑</div>
										<ol className="list-decimal pl-5 text-xs text-muted-foreground mb-3">
											<li>在左侧模型树选择模型，右侧 SQL 编辑器中修改逻辑。</li>
											<li>点击工具栏「编译」验证 SQL 语法（dbt compile）。</li>
											<li>点击「测试」运行数据质量测试（dbt test）。</li>
											<li>在「数据预览」Tab 查看模型输出数据。</li>
										</ol>

										<div className="font-medium mb-1">三、上线发布</div>
										<ol className="list-decimal pl-5 text-xs text-muted-foreground mb-3">
											<li>点击工具栏「上线」按钮，填写模型选择器（如 <code>tag:erp</code>）。</li>
											<li>系统依次执行三道检查：
												<ul className="list-disc pl-5 mt-1">
													<li><strong>DAG 就绪检查</strong> — 确认 Airflow 已注册执行计划（约 30 秒）</li>
													<li><strong>质量门禁</strong> — 检查模型是否有编译错误或测试失败</li>
													<li><strong>发布门禁</strong> — 检查 Git 提交状态、依赖完整性</li>
												</ul>
											</li>
											<li>检查通过后，提交 <code>dbt build --select tag:erp</code> 到 Airflow 执行。</li>
											<li>Airflow 启动 Docker 容器运行 dbt，按依赖顺序建表 / 刷数据。</li>
											<li>执行完成后结果自动同步回平台，可在「操作记录 → 运行记录」查看。</li>
										</ol>

										<div className="font-medium mb-1">四、重建表</div>
										<ol className="list-decimal pl-5 text-xs text-muted-foreground mb-3">
											<li>右键模型 →「数据输出」→ 选择「重建」。</li>
											<li>系统使用 <code>dbt build --full-refresh</code> 安全重建，构建失败时保留原表数据。</li>
										</ol>

										<div className="font-medium mb-1">五、常见问题</div>
										<ul className="list-disc pl-5 text-xs text-muted-foreground">
											<li><strong>DAG 未就绪</strong>：新模型首次上线需等待约 30 秒让 Airflow 注册 DAG，稍后重试即可。</li>
											<li><strong>质量门禁阻断</strong>：先执行编译 + 测试修复问题，再尝试上线。</li>
											<li><strong>执行超时</strong>：检查「操作记录 → 执行日志」中的 Airflow 日志定位原因。</li>
											<li><strong>数据预览为空</strong>：确认模型已成功执行（状态为 SUCCESS），再点击加载预览。</li>
										</ul>

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
					open={gitCommitOpen}
					title="提交到 Git"
					onCancel={() => setGitCommitOpen(false)}
					onOk={handleGitCommit}
					okText="提交到 Git"
					cancelText="取消"
					confirmLoading={gitCommitting}
				>
					<div className="space-y-3">
						<Input
							placeholder="请输入提交信息"
							value={gitCommitMsg}
							onChange={(event) => setGitCommitMsg(event.target.value)}
							onPressEnter={handleGitCommit}
						/>
						<div className="rounded border border-border bg-muted/10 px-3 py-2 text-xs text-muted-foreground">
							当前操作会把本地 dbt 工作区改动提交到 Git，本页不负责审批流或远端推送。建议先完成编译和测试。
						</div>
					</div>
				</Modal>

				<Modal
					open={runOpen}
					title={buildRunModalTitle(runMode)}
					onCancel={() => setRunOpen(false)}
					onOk={submitRun}
				okText={buildRunModalOkText(runMode)}
				cancelText="取消"
				confirmLoading={runSubmitting}
				width={760}
			>
				<Form layout="vertical" form={runForm}>
					<Form.Item
						label="模型选择器"
						required
						help={
							runSelectedModelIds.length === 0
								? "请至少勾选一个模型"
								: `已选 ${runSelectedModelIds.length} 个模型，当前筛选结果 ${runFilteredModels.length} / 全部 ${sqlModels.length}`
						}
						validateStatus={runSelectedModelIds.length === 0 ? "error" : undefined}
					>
						<div className="flex flex-col gap-2">
							<div className="flex items-center gap-2">
								<Select<string>
									allowClear
									showSearch
									placeholder="按项目空间筛选"
									value={runSpaceFilter}
									onChange={(value) => setRunSpaceFilter(value)}
									style={{ width: 200 }}
									optionFilterProp="label"
									options={[
										...spaces.map((space) => ({
											value: String(space.id || ""),
											label: space.name || "未命名项目空间",
										})),
										...(unassignedModels.length > 0
											? [{ value: UNASSIGNED_SPACE_KEY, label: `未分配 (${unassignedModels.length})` }]
											: []),
									]}
								/>
								<Input.Search
									allowClear
									placeholder="按名称 / 空间 / 层搜索"
									value={runModelKeyword}
									onChange={(e) => setRunModelKeyword(e.target.value)}
									style={{ flex: 1 }}
								/>
								<Button
									size="small"
									onClick={() => {
										const ids = runFilteredModels.map((m) => String(m.id || "").trim()).filter(Boolean);
										setRunSelectedModelIds((prev) => Array.from(new Set([...prev, ...ids])));
									}}
								>
									全选当前结果
								</Button>
								<Button
									size="small"
									disabled={!runSpaceFilter && !activeSpace}
									onClick={() => {
										let targetModels: SqlModel[];
										if (runSpaceFilter === UNASSIGNED_SPACE_KEY) {
											targetModels = sqlModels.filter((m) => !m.planId);
										} else if (runSpaceFilter) {
											targetModels = sqlModels.filter(
												(m) => String(m.planId || "") === runSpaceFilter,
											);
										} else {
											targetModels = activeSpaceModels;
										}
										const ids = targetModels
											.map((m) => String(m.id || "").trim())
											.filter(Boolean);
										setRunSelectedModelIds(ids);
									}}
								>
									按当前空间全选
								</Button>
								<Button size="small" onClick={() => setRunSelectedModelIds([])}>
									清空
								</Button>
							</div>
							<Table<SqlModel>
								size="small"
								rowKey={(record) => String(record.id || "")}
								dataSource={runFilteredModels}
								pagination={{ pageSize: 8, size: "small", showSizeChanger: false }}
								scroll={{ y: 260 }}
								rowSelection={{
									selectedRowKeys: runSelectedModelIds,
									onChange: (keys) => setRunSelectedModelIds(keys.map((k) => String(k))),
									preserveSelectedRowKeys: true,
								}}
								columns={[
									{
										title: "模型名",
										dataIndex: "name",
										key: "name",
										ellipsis: true,
										render: (value: string) => <span style={{ fontFamily: "monospace" }}>{value}</span>,
									},
									{
										title: "项目空间",
										dataIndex: "planName",
										key: "planName",
										width: 140,
										ellipsis: true,
										render: (value: string) => value || <span className="text-muted-foreground">未分配</span>,
									},
									{
										title: "层",
										dataIndex: "layer",
										key: "layer",
										width: 80,
										render: (value: string) => value || "-",
									},
								]}
							/>
						</div>
					</Form.Item>
					<Form.Item name="target" label="目标">
						<Input placeholder="dev" />
					</Form.Item>
					{runMode === "release" && (
						<>
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
						</>
					)}
					<Form.Item name="vars" label="运行变量">
						<Input.TextArea rows={3} placeholder='JSON 结构，例如 {"run_date":"2026-01-19"}' />
					</Form.Item>
				</Form>
			</Modal>

			</div>

			<ModelEditDrawer
				open={modelDrawerOpen}
				onClose={() => setModelDrawerOpen(false)}
				onSubmit={submitModel}
				submitting={modelSubmitting}
				isEditing={!!editingModel}
				spaces={spaces}
				dataSources={dataSources}
				layers={layers}
				form={modelForm}
			/>

			<ImportModelModal
				open={importOpen}
				onClose={() => setImportOpen(false)}
				onSubmit={submitImport}
				submitting={importSubmitting}
				spaces={spaces}
				dataSources={dataSources}
				sqlFileList={sqlFileList}
				onSqlFileListChange={setSqlFileList}
				csvFileList={csvFileList}
				onCsvFileListChange={setCsvFileList}
				form={importForm}
			/>

			<GovernanceModal
				open={governanceOpen}
				onClose={() => setGovernanceOpen(false)}
				onPreview={handleGovernancePreview}
				onExecute={handleGovernanceExecute}
				previewLoading={governancePreviewLoading}
				executing={governanceExecuting}
				preview={governancePreview}
				selection={governanceSelection}
				totalSelectionCount={bulkSelection.selectedIds.length}
				onSelectionChange={(keys) => setBulkSelection((current) => applyBulkSelectionChange(current, keys, "governance"))}
				onSelectAllPreview={() =>
					setBulkSelection((current) =>
						applyBulkSelectionChange(
							current,
							governancePreview.map((item) => String(item.modelId || "").trim()).filter(Boolean),
							"governance",
						),
					)
				}
				onClearSelection={() => setBulkSelection((current) => clearBulkSelectionSource(current, "governance"))}
				spaces={spaces}
				form={governanceForm}
			/>

			<BatchDeleteResultModal
				open={batchDeleteResultOpen}
				onClose={() => setBatchDeleteResultOpen(false)}
				result={batchDeleteResult}
				title={batchDeleteResultTitle}
			/>

			<BatchImportModal
				open={batchImportOpen}
				onClose={() => setBatchImportOpen(false)}
				onSuccess={handleBatchImportSuccess}
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
				confirmLoading={archiveSubmitting}
				width={720}
				footer={
					<Space>
						<Button onClick={() => setBatchArchiveOpen(false)}>取消</Button>
						<Button danger icon={<DeleteOutlined />} onClick={removeSelectedModels} disabled={!batchArchiveSelection.length}>
							删除所选
						</Button>
						<Button type="primary" onClick={submitBatchArchive} loading={archiveSubmitting} disabled={!batchArchiveSelection.length}>
							批量归档
						</Button>
					</Space>
				}
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
							<div className="flex items-center justify-between gap-3">
								<Checkbox
									checked={batchArchiveSelection.length > 0 && batchArchiveSelection.length === unassignedModels.length}
									indeterminate={
										batchArchiveSelection.length > 0 && batchArchiveSelection.length < unassignedModels.length
									}
									onChange={(event) =>
										setBulkSelection((current) =>
											applyBulkSelectionChange(
												current,
												event.target.checked ? unassignedModelIds : [],
												"list",
											),
										)
									}
								>
									全选未归档模型
								</Checkbox>
								<Button
									type="link"
									size="small"
									className="px-0"
									onClick={() => setBulkSelection((current) => clearBulkSelectionSource(current, "list"))}
									disabled={!batchArchiveSelection.length}
								>
									清空选择
								</Button>
							</div>
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
													setBulkSelection((current) =>
														applyBulkSelectionChange(
															current,
															event.target.checked
																? [...batchArchiveSelection, modelId]
																: batchArchiveSelection.filter((item) => item !== modelId),
															"list",
														),
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

			<OdsGenerateModal
				open={odsGenerateOpen}
				onClose={() => setOdsGenerateOpen(false)}
				onSubmit={submitOdsGenerate}
				submitting={odsGenerateSubmitting}
				spaces={spaces}
				sourcesLoading={sourcesLoading}
				odsSourceOptions={odsSourceOptions}
				odsSourceFilterOptions={odsSourceFilterOptions}
				form={odsGenerateForm}
			/>

			<SnippetDrawer
				open={snippetDrawerOpen}
				onClose={() => setSnippetDrawerOpen(false)}
				snippetTab={snippetTab}
				onSnippetTabChange={(tab) => setSnippetTab(tab)}
				snippetKeyword={snippetKeyword}
				onSnippetKeywordChange={setSnippetKeyword}
				sourcesLoading={sourcesLoading}
				refsLoading={refsLoading}
				filteredSources={filteredDbtSources}
				filteredRefs={filteredDbtRefs}
				onInsertSnippet={insertSnippet}
			/>
			<OutputRelationModal
					open={outputModalOpen}
					onClose={() => {
						setOutputModalOpen(false);
						setOutputAction(null);
						setOutputRelation(null);
						setOutputRelationError(null);
					}}
					onSubmit={() => void submitOutputAction()}
					submitting={outputRelationSubmitting}
					loading={outputRelationLoading}
					outputAction={outputAction}
					outputRelation={outputRelation}
					errorMessage={outputRelationError}
					activeModel={activeModel}
				/>
			<DbtModelDiagnosticsDrawer
				open={diagnosticsOpen}
				onClose={() => setDiagnosticsOpen(false)}
				onReload={() => void loadDiagnostics(diagnosticsModel)}
				onOpenPreview={openDiagnosticsPreview}
				onOpenLogs={openDiagnosticsLogs}
				loading={diagnosticsLoading}
				model={diagnosticsModel}
				diagnostics={diagnosticsResult}
				errorMessage={diagnosticsError}
			/>
			</div>
		);
	}
