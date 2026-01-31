import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import Editor from "@monaco-editor/react";
import { toast } from "sonner";
import {
	Badge,
	Button,
	Card,
	Drawer,
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
	listModelingPlans,
	syncDbtModels,
	getDbtSyncStatus,
	triggerDbtRun,
	updateDbtConfig,
} from "@/api/platformApi";
import dataSourcesService, { type InfraDataSource } from "@/api/services/dataSourcesService";

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

const formatMillis = (value?: number) => {
	if (!value) return "-";
	const date = new Date(value);
	return Number.isNaN(date.valueOf()) ? String(value) : date.toLocaleString();
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
	const [modelDrawerOpen, setModelDrawerOpen] = useState(false);
	const [modelSubmitting, setModelSubmitting] = useState(false);
	const [editingModel, setEditingModel] = useState<SqlModel | null>(null);
	const [sqlDraft, setSqlDraft] = useState("");
	const [importOpen, setImportOpen] = useState(false);
	const [importSubmitting, setImportSubmitting] = useState(false);
	const [sqlFileList, setSqlFileList] = useState<UploadFile[]>([]);
	const [csvFileList, setCsvFileList] = useState<UploadFile[]>([]);
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
	const [form] = Form.useForm();
	const [runForm] = Form.useForm();
	const [modelForm] = Form.useForm();
	const [importForm] = Form.useForm();

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

	useEffect(() => {
		void loadConfig();
		void loadSyncStatus();
		void loadModels();
		void loadRuns();
		void loadSpaces();
		void loadSources();
	}, [loadConfig, loadModels, loadRuns, loadSpaces, loadSources, loadSyncStatus]);

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

	return (
		<div className="flex min-h-[calc(100vh-120px)] flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
			<div className="flex h-16 items-center justify-between border-b bg-white px-6">
				<Space size="large">
					<div className="text-lg font-bold text-blue-600">Data Studio</div>
					<div className="rounded-md bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600">逻辑建模</div>
					<Badge status={configEnabled ? "success" : "error"} text={configEnabled ? "dbt 已连接" : "dbt 未启用"} />
				</Space>
				<Space>
					<Button onClick={openCreateModel} disabled={!workspaceOk}>
						新建模型
					</Button>
					<Button onClick={openImportModel} disabled={!workspaceOk}>
						导入模型
					</Button>
					<Button onClick={openEditModel} disabled={!activeModel || !workspaceOk}>
						编辑模型
					</Button>
					<Button onClick={saveSqlDraft} disabled={!activeModel || !sqlDirty || !workspaceOk}>
						保存 SQL
					</Button>
					<Button danger onClick={removeModel} disabled={!activeModel}>
						删除模型
					</Button>
					<Tooltip title="语法校验接口暂未接入">
						<Button
							onClick={() => {
								toast.info("语法校验接口暂未接入");
								setBottomTab("compile");
							}}
							disabled={!configEnabled || !activeModelKey}
						>
							语法校验
						</Button>
					</Tooltip>
					<Button onClick={() => setBottomTab("preview")}>运行预览</Button>
					<Button type="primary" onClick={openRun} disabled={!configEnabled || !workspaceOk}>
						提交上线
					</Button>
					<Button onClick={() => setConfigOpen(true)}>工作区配置</Button>
				</Space>
			</div>
			<Card
				className="mx-6 mt-4 border border-slate-200 shadow-sm"
				title={<span className="text-sm font-semibold text-slate-700">dbt 资产同步状态</span>}
				extra={
					<Button size="small" onClick={handleSyncModels} loading={syncingModels} disabled={!configEnabled || !workspaceOk}>
						立即同步
					</Button>
				}
			>
				{dbtSyncStatus ? (
					<div className="grid gap-3 text-xs text-slate-600">
						<div className="grid gap-3 md:grid-cols-2">
							<div className="rounded border border-slate-100 bg-slate-50/50 px-3 py-2">
								<div className="flex items-center gap-2">
									<span className="font-medium text-slate-700">manifest</span>
									{syncTag(dbtSyncStatus.manifest?.synced)}
									<span>同步时间：{formatDateTime(dbtSyncStatus.manifest?.lastSyncAt)}</span>
								</div>
								<div className="mt-1 text-[11px] text-slate-400">
									文件时间：{formatMillis(dbtSyncStatus.manifest?.lastModifiedAt)}
									{dbtSyncStatus.manifest?.message ? ` · ${dbtSyncStatus.manifest.message}` : ""}
								</div>
							</div>
							<div className="rounded border border-slate-100 bg-slate-50/50 px-3 py-2">
								<div className="flex items-center gap-2">
									<span className="font-medium text-slate-700">run_results</span>
									{syncTag(dbtSyncStatus.runResults?.synced)}
									<span>同步时间：{formatDateTime(dbtSyncStatus.runResults?.lastSyncAt)}</span>
								</div>
								<div className="mt-1 text-[11px] text-slate-400">
									文件时间：{formatMillis(dbtSyncStatus.runResults?.lastModifiedAt)}
									{dbtSyncStatus.runResults?.message ? ` · ${dbtSyncStatus.runResults.message}` : ""}
								</div>
							</div>
						</div>
						{dbtSyncStatus.stats ? (
							<div className="rounded border border-slate-100 bg-slate-50/50 px-3 py-2">
								<div className="flex items-center justify-between">
									<span className="font-medium text-slate-700">资产同步统计</span>
									<span className="text-[11px] text-slate-400">
										同步时间：{formatDateTime(dbtSyncStatus.stats.lastSyncAt)}
										{dbtSyncStatus.stats.message ? ` · ${dbtSyncStatus.stats.message}` : ""}
									</span>
								</div>
								<div className="mt-2 grid gap-2 md:grid-cols-3">
									<div className="flex items-center justify-between">
										<span>新增模型</span>
										<span className="font-semibold">{dbtSyncStatus.stats.datasetsCreated ?? 0}</span>
									</div>
									<div className="flex items-center justify-between">
										<span>更新模型</span>
										<span className="font-semibold">{dbtSyncStatus.stats.datasetsUpdated ?? 0}</span>
									</div>
									<div className="flex items-center justify-between">
										<span>ODS 更新</span>
										<span className="font-semibold">{dbtSyncStatus.stats.odsUpdated ?? 0}</span>
									</div>
									<div className="flex items-center justify-between">
										<span>字段同步</span>
										<span className="font-semibold">{dbtSyncStatus.stats.columnsUpdated ?? 0}</span>
									</div>
									<div className="flex items-center justify-between">
										<span>血缘新增</span>
										<span className="font-semibold">{dbtSyncStatus.stats.lineageCreated ?? 0}</span>
									</div>
									<div className="flex items-center justify-between">
										<span>血缘移除</span>
										<span className="font-semibold">{dbtSyncStatus.stats.lineageRemoved ?? 0}</span>
									</div>
								</div>
							</div>
						) : null}
					</div>
				) : (
					<div className="text-xs text-slate-400">未获取同步状态</div>
				)}
			</Card>

			<div className="flex flex-1 overflow-hidden">
				<div className="w-64 border-r border-slate-200 bg-slate-50 p-4">
					<div className="mb-3 text-xs font-bold uppercase text-slate-400">项目目录</div>
					<Input
						size="small"
						placeholder="搜索模型..."
						value={keyword}
						onChange={(event) => setKeyword(event.target.value)}
						className="mb-3"
					/>
					{spacesLoading ? (
						<Card size="small" className="border-dashed text-center text-xs text-slate-400">
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
								<div className="mt-3 text-xs text-slate-400">加载模型中...</div>
							) : activeLayerNodes.length === 0 ? (
								<div className="mt-3 text-xs text-slate-400">当前项目暂无模型。</div>
							) : null}
						</>
					)}
				</div>

				<div className="flex flex-1 flex-col">
					<div className="flex items-center justify-between border-b px-4 py-2">
						<Space>
							<Text strong>{activeModel?.name || "未选择模型"}</Text>
							{layerTag(activeModel?.layer || (activeModel ? inferLayer(activeModel.name) : undefined))}
							<Text type="secondary">{activeModel?.modelPath || "尚未定位模型路径"}</Text>
						</Space>
						<Space>
							<Button size="small" onClick={handleSyncModels} loading={syncingModels} disabled={!workspaceOk}>
								同步模型
							</Button>
							<Button size="small" onClick={loadModels} loading={modelsLoading}>
								刷新模型
							</Button>
							<Button size="small" onClick={loadRuns} loading={runsLoading}>
								刷新运行
							</Button>
						</Space>
					</div>

					<div className="flex-1 overflow-auto bg-slate-950 px-6 py-4">
						{activeModel ? (
							<Suspense
								fallback={
									<div className="flex h-full items-center justify-center text-center text-xs text-slate-500">
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
							<div className="flex h-full items-center justify-center text-center text-xs text-slate-500">
								请选择模型查看 SQL。
							</div>
						)}
					</div>

					<div className="h-64 border-t border-slate-200 bg-white">
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
										<div className="p-4 text-sm text-slate-500">暂无运行记录。</div>
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

				<div className="w-72 border-l border-slate-200 bg-white p-4">
					<div className="mb-4 text-xs font-bold uppercase text-slate-400">项目与元数据</div>
					<Card size="small" title="项目空间" className="mb-4">
						{activeSpace ? (
							<div className="space-y-1 text-xs text-slate-500">
								<div>名称：{activeSpace.name || "-"}</div>
								<div>业务域：{activeSpace.domain || "-"}</div>
								<div>负责人：{activeSpace.owner || "-"}</div>
								<div>状态：{activeSpace.status || "-"}</div>
							</div>
						) : (
							<div className="text-xs text-slate-500">请选择项目空间。</div>
						)}
					</Card>
					<Card size="small" title="模型信息" className="mb-4">
						{activeModel ? (
							<div className="space-y-1 text-xs text-slate-500">
								<div>模型名称：{activeModel.name || "-"}</div>
								<div>数据源：{activeModel.sourceDataSourceName || "-"}</div>
								<div>来源系统：{activeModel.sourceSystem || "-"}</div>
								<div>Schema：{activeModel.schemaName || "-"}</div>
								<div>物化方式：{activeModel.materialized || "-"}</div>
								<div>标签：{activeModel.tags || "-"}</div>
								<div>DAG 选择器：{activeModel.dagSelector || "-"}</div>
								<div>路径：{activeModel.modelPath || "-"}</div>
							</div>
						) : (
							<div className="text-xs text-slate-500">请选择模型查看详情。</div>
						)}
					</Card>
					<Card size="small" title="字段状态" className="mb-4">
						{activeModel ? (
							modelColumns.length ? (
								<Table
									rowKey={(row, idx) => `${row.name || "col"}-${idx}`}
									size="small"
									pagination={false}
									columns={modelColumnColumns}
									dataSource={modelColumns}
									loading={columnsLoading}
									scroll={{ y: 200 }}
								/>
							) : (
								<div className="text-xs text-slate-500">暂无字段配置。</div>
							)
						) : (
							<div className="text-xs text-slate-500">请选择模型查看字段。</div>
						)}
					</Card>
					<Card size="small" title="工作区配置" className="mb-4">
						<div className="text-xs text-slate-500">
							<div>项目目录：{dbtConfig?.config?.projectDir || "未配置"}</div>
							<div>Profiles：{dbtConfig?.config?.profilesDir || "未配置"}</div>
							<div>Target：{dbtConfig?.config?.targetName || "未配置"}</div>
						</div>
						{workspaceStatus && !workspaceStatus.ok ? (
							<div className="mt-2 rounded border border-red-100 bg-red-50 px-2 py-1 text-xs text-red-600">
								{workspaceStatus.message || "dbt 工作区不可用"}
							</div>
						) : null}
						<Button size="small" className="mt-3" onClick={() => setConfigOpen(true)}>
							编辑配置
						</Button>
					</Card>
					<div className="rounded border border-yellow-100 bg-yellow-50 p-3">
						<div className="text-xs font-bold text-yellow-700">元数据校验</div>
						<div className="mt-1 text-xs text-yellow-600">质量与落标检查接口暂未接入。</div>
					</div>
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
									{ label: "启用", value: "READY" },
									{ label: "停用", value: "PAUSED" },
								]}
							/>
						</Form.Item>
					</div>
					<Form.Item name="description" label="描述">
						<Input.TextArea rows={2} placeholder="模型说明" />
					</Form.Item>
					<Form.Item name="sql" label="SQL" rules={[{ required: true, message: "请输入 SQL" }]}>
						<Input.TextArea rows={10} className="font-mono" placeholder="编写模型 SQL" />
					</Form.Item>
					<Form.Item name="enabled" label="是否启用" valuePropName="checked">
						<Switch />
					</Form.Item>
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
		</div>
	);
}
