import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import Editor from "@monaco-editor/react";
import { toast } from "sonner";
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
} from "@ant-design/icons";
import type { UploadFile } from "antd/es/upload/interface";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import {
	getDbtConfig,
	listDbtRuns,
	listSqlModels,
	listSqlModelColumns,
	createSqlModel,
	updateSqlModel,
	deleteSqlModel,
	importSqlModel,
	generateSqlModelsFromOds,
	listModelingPlans,
	syncDbtModels,
	getDbtSyncStatus,
	triggerDbtRun,
	updateDbtConfig,
	listDbtSources,
	listDbtRefs,
	listTemplateLayers,
} from "@/api/platformApi";
import dataSourcesService, { type InfraDataSource } from "@/api/services/dataSourcesService";
import { useRouter } from "@/routes/hooks";

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

type DbtSyncStatus = {
	manifest?: DbtSyncArtifactStatus | null;
	runResults?: DbtSyncArtifactStatus | null;
	stats?: DbtSyncStats | null;
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
	dag_run_id?: string;
	state?: string;
	execution_date?: string;
	start_date?: string;
	end_date?: string;
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
	const [importSubmitting, setImportSubmitting] = useState(false);
	const [sqlFileList, setSqlFileList] = useState<UploadFile[]>([]);
	const [csvFileList, setCsvFileList] = useState<UploadFile[]>([]);
	const [odsGenerateOpen, setOdsGenerateOpen] = useState(false);
	const [odsGenerateSubmitting, setOdsGenerateSubmitting] = useState(false);
	const [syncingModels, setSyncingModels] = useState(false);
	const [runsLoading, setRunsLoading] = useState(false);
	const [runs, setRuns] = useState<DagRun[]>([]);
	const [runOpen, setRunOpen] = useState(false);
	const [runSubmitting, setRunSubmitting] = useState(false);
	const [columnsLoading, setColumnsLoading] = useState(false);
	const [modelColumns, setModelColumns] = useState<ModelColumn[]>([]);
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
	const [guideCollapsed, setGuideCollapsed] = useState(false);
	const [form] = Form.useForm();
	const [runForm] = Form.useForm();
	const [modelForm] = Form.useForm();
	const [importForm] = Form.useForm();
	const [odsGenerateForm] = Form.useForm();

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

	const loadSyncStatus = useCallback(async () => {
		try {
			const resp = (await getDbtSyncStatus()) as DbtSyncStatus;
			setDbtSyncStatus(resp || null);
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

	const loadDbtSources = useCallback(async () => {
		setSourcesLoading(true);
		try {
			const resp = (await listDbtSources()) as DbtSourceItem[];
			setDbtSources(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "加载源表列表失败");
			setDbtSources([]);
		} finally {
			setSourcesLoading(false);
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
		if (spaces.length === 0) {
			if (activeSpaceKey) {
				setActiveSpaceKey(null);
			}
			return;
		}
		const hasActive = !!activeSpaceKey && spaces.some((space, idx) => resolveSpaceKey(space, idx) === activeSpaceKey);
		if (!hasActive) {
			setActiveSpaceKey(resolveSpaceKey(spaces[0], 0));
		}
	}, [activeSpaceKey, spaces]);

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
		const selector = activeModel?.dagSelector || (activeModel?.name ? `model:${activeModel.name}` : "");
		runForm.setFieldsValue({
			models: selector,
			target: dbtConfig?.config?.targetName || "",
			vars: "",
		});
		setRunOpen(true);
	};

	const submitRun = async () => {
		setRunSubmitting(true);
		try {
			const values = await runForm.validateFields(["models"]);
			await triggerDbtRun({
				models: normalizeText(values.models),
				target: normalizeText(values.target) || undefined,
				vars: tryParseJsonObject(values.vars),
			});
			toast.success("运行任务已提交");
			setRunOpen(false);
			await loadRuns();
		} catch (err: any) {
			toast.error(err?.message || "触发失败");
		} finally {
			setRunSubmitting(false);
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
		} catch (err: any) {
			toast.error(err?.message || "同步模型失败");
		} finally {
			setSyncingModels(false);
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
			const skipped = result?.skipped || [];
			Modal.info({
				title: "一键生成结果",
				width: 720,
				content: (
					<div>
						<p>{summary}</p>
						<p style={{ marginTop: 8, color: "rgba(0,0,0,0.65)" }}>
							系统已按所选映射生成或更新 DWD / DWS / ADS 模型模板。
						</p>
						{skipped.length > 0 && (
							<>
								<p style={{ marginTop: 8, fontWeight: 600 }}>跳过项：</p>
								<ul style={{ maxHeight: 220, overflow: "auto", fontSize: 12, paddingLeft: 20 }}>
									{skipped.map((item, idx) => (
										<li key={`${item}-${idx}`}>{item}</li>
									))}
								</ul>
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
								<li>完成验证后回到逻辑建模执行“提交上线”。</li>
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

	const odsSourceOptions = useMemo(
		() =>
			dbtSources
				.filter((item) => !!item.id)
				.map((item) => ({
					label: `${item.schema}.${item.table}${item.systemCode ? ` · ${item.systemCode}` : ""}`,
					value: item.id as string,
				})),
		[dbtSources],
	);

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
					title: model.name || model.alias || "未命名模型",
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
			{ title: "计划时间", dataIndex: "execution_date", key: "execution_date", width: 180, render: (v) => formatDateTime(v) },
			{ title: "开始时间", dataIndex: "start_date", key: "start_date", width: 180, render: (v) => formatDateTime(v) },
			{ title: "结束时间", dataIndex: "end_date", key: "end_date", width: 180, render: (v) => formatDateTime(v) },
		],
		[],
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

	const activeSpaceModels = useMemo(() => {
		if (!activeSpace) return filteredModels;
		return filteredModels.filter((model) => model.planId === activeSpace.id);
	}, [activeSpace, filteredModels]);

	const activeLayerNodes = useMemo(() => buildLayerNodes(activeSpaceModels), [buildLayerNodes, activeSpaceModels]);

	const treeData = useMemo(() => {
		if (spaces.length === 0) return [];
		return spaces.map((space, idx) => {
			const key = resolveSpaceKey(space, idx);
			const spaceModels = filteredModels.filter((model) => model.planId === space.id);
			return {
				title: space.name || "未命名项目空间",
				key,
				children: key === activeSpaceKey ? buildLayerNodes(spaceModels) : [],
			};
		});
	}, [spaces, activeSpaceKey, filteredModels, buildLayerNodes]);

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

	return (
		<div className="flex min-h-[calc(100vh-120px)] flex-col overflow-hidden rounded-xl border border-border bg-card shadow-sm">
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
					{/* 提交上线 */}
					<Button
						type="primary"
						icon={<RocketOutlined />}
						onClick={openRun}
						disabled={!configEnabled || !workspaceOk}
					>
						提交上线
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
			<div className="border-b border-border bg-muted/20 px-4 py-2">
				<div className="flex items-center justify-between gap-2">
						<div className="text-sm font-medium text-foreground">
							{"主流程：ODS 接入 -> 选择映射 -> 一键生成 DWD/DWS/ADS -> 校验并上线"}
						</div>
					<Space size={8}>
						<Button size="small" onClick={() => router.push("/foundation/data-sources")}>
							去 ODS 接入
						</Button>
						<Button size="small" type="primary" ghost onClick={openOdsGenerateModel} disabled={!workspaceOk}>
							打开一键生成
						</Button>
						<Button size="small" type="link" onClick={() => setGuideCollapsed((prev) => !prev)}>
							{guideCollapsed ? "展开" : "收起"}
						</Button>
					</Space>
				</div>
				{!guideCollapsed && (
					<ol className="mt-2 list-decimal pl-5 text-xs text-muted-foreground">
						<li>先在数据集成完成 ODS 表接入或源库映射。</li>
						<li>在本页选择项目空间和 ODS 映射后执行一键生成。</li>
						<li>系统自动产出 dwd_ / dws_ / ads_ 模型模板。</li>
						<li>在 dbt 文件浏览器微调并运行，最后提交上线。</li>
					</ol>
				)}
			</div>

			<div className="flex flex-1 overflow-hidden">
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
							{activeModel?.modelPath && (
								<Text type="secondary" className="text-xs">{activeModel.modelPath}</Text>
							)}
							{sqlDirty && <Tag color="orange" className="text-xs">未保存</Tag>}
						</div>
						<Space size="small">
							<Tooltip title="刷新模型列表">
								<Button size="small" icon={<SyncOutlined />} onClick={loadModels} loading={modelsLoading} />
							</Tooltip>
						</Space>
					</div>

					<div className="flex-1 overflow-auto bg-slate-950 px-6 py-4">
						{activeModel ? (
							<Suspense
								fallback={
									<div className="flex h-full items-center justify-center text-center text-xs text-muted-foreground">
										加载编辑器中...
									</div>
								}
							>
								<Editor
									height="100%"
									language="sql"
									theme="vs-dark"
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
								请选择模型查看 SQL。
							</div>
						)}
					</div>

					<div className="h-64 border-t border-border bg-card">
						<Tabs
							activeKey={bottomTab}
							onChange={setBottomTab}
							size="small"
							className="px-4"
							items={[
								{
									key: "preview",
									label: "数据预览 (Top 100)",
									children: (
										<div className="p-4">
											<EmptyState title="暂无预览数据" description="运行预览接口接入后展示结果。" compact />
										</div>
									),
								},
								{
									key: "compile",
									label: "编译日志",
									children: (
										<div className="p-4">
											<EmptyState title="暂无编译日志" description="语法校验接口接入后展示日志。" compact />
										</div>
									),
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
											scroll={{ y: 140 }}
										/>
									),
								},
							]}
						/>
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
				title="提交上线 (dbt run)"
				onCancel={() => setRunOpen(false)}
				onOk={submitRun}
				okText="提交"
				cancelText="取消"
				confirmLoading={runSubmitting}
				width={560}
			>
				<Form layout="vertical" form={runForm}>
					<Form.Item name="models" label="模型选择器" rules={[{ required: true, message: "请输入模型选择器" }]}>
						<Input placeholder="例如：tab:crm 或 model:xxx" />
					</Form.Item>
					<Form.Item name="target" label="Target">
						<Input placeholder="dev" />
					</Form.Item>
					<Form.Item name="vars" label="运行变量">
						<Input.TextArea rows={3} placeholder='JSON 结构，例如 {"run_date":"2026-01-19"}' />
					</Form.Item>
				</Form>
			</Modal>

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
								placeholder="用于补充来源标签和调度选择器"
								options={dataSources.map((ds) => ({
									label: ds?.name || ds?.id,
									value: ds?.id,
								}))}
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
						<div className="mt-2 text-amber-700">
							已移除 ZIP 导入。请使用 ODS 映射作为唯一建模入口。
						</div>
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
		</div>
	);
}
