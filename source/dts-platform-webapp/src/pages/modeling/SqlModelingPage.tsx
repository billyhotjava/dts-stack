import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Badge,
	Button,
	Card,
	Drawer,
	Form,
	Input,
	Modal,
	Space,
	Table,
	Tabs,
	Tag,
	Tooltip,
	Tree,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import {
	getDbtConfig,
	listDbtModels,
	listDbtRuns,
	listModelingPlans,
	syncDbtModels,
	triggerDbtRun,
	updateDbtConfig,
} from "@/api/platformApi";

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
	target?: { id?: string; name?: string; type?: string };
};

type DbtModelSummary = {
	uniqueId?: string;
	name?: string;
	alias?: string;
	database?: string;
	schema?: string;
	path?: string;
};

type DbtModelResult = {
	enabled?: boolean;
	models?: DbtModelSummary[];
	message?: string | null;
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

const resolveModelKey = (model: DbtModelSummary, fallback: string) => model.uniqueId || model.name || fallback;
const resolveSpaceKey = (space: ProjectSpace, index: number) => `space-${space.id || index}`;

export default function SqlModelingPage() {
	const [configLoading, setConfigLoading] = useState(false);
	const [configSaving, setConfigSaving] = useState(false);
	const [configOpen, setConfigOpen] = useState(false);
	const [dbtConfig, setDbtConfig] = useState<DbtConfigView | null>(null);
	const [spacesLoading, setSpacesLoading] = useState(false);
	const [spaces, setSpaces] = useState<ProjectSpace[]>([]);
	const [activeSpaceKey, setActiveSpaceKey] = useState<string | null>(null);
	const [modelsLoading, setModelsLoading] = useState(false);
	const [modelResult, setModelResult] = useState<DbtModelResult | null>(null);
	const [syncingModels, setSyncingModels] = useState(false);
	const [runsLoading, setRunsLoading] = useState(false);
	const [runs, setRuns] = useState<DagRun[]>([]);
	const [runOpen, setRunOpen] = useState(false);
	const [runSubmitting, setRunSubmitting] = useState(false);
	const [bottomTab, setBottomTab] = useState("preview");
	const [keyword, setKeyword] = useState("");
	const [activeModelKey, setActiveModelKey] = useState<string | null>(null);
	const [form] = Form.useForm();
	const [runForm] = Form.useForm();

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

	const loadModels = useCallback(async () => {
		setModelsLoading(true);
		try {
			const resp = (await listDbtModels()) as DbtModelResult;
			setModelResult(resp || null);
		} catch (err: any) {
			toast.error(err?.message || "加载模型失败");
		} finally {
			setModelsLoading(false);
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

	useEffect(() => {
		void loadConfig();
		void loadModels();
		void loadRuns();
		void loadSpaces();
	}, [loadConfig, loadModels, loadRuns, loadSpaces]);

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
		const modelName = activeModel?.name ? `model:${activeModel.name}` : "";
		runForm.setFieldsValue({
			models: modelName,
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
			toast.success(message);
			await loadModels();
		} catch (err: any) {
			toast.error(err?.message || "同步模型失败");
		} finally {
			setSyncingModels(false);
		}
	};

	const models = modelResult?.models || [];
	const configEnabled = dbtConfig?.enabled !== false;
	const profileStatus = dbtConfig?.profileStatus;

	const modelKeyMap = useMemo(() => {
		const map = new Map<string, DbtModelSummary>();
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

	const activeModel = useMemo(() => {
		if (!activeModelKey) return null;
		return modelKeyMap.get(activeModelKey) || null;
	}, [activeModelKey, modelKeyMap]);

	const filteredModels = useMemo(() => {
		const key = normalizeText(keyword).toLowerCase();
		if (!key) return models;
		return models.filter((model) => (model.name || "").toLowerCase().includes(key));
	}, [keyword, models]);

	const modelLayerNodes = useMemo(() => {
		const layers = new Map<string, DbtModelSummary[]>();
		filteredModels.forEach((model) => {
			const layer = inferLayer(model.name);
			const list = layers.get(layer) || [];
			list.push(model);
			layers.set(layer, list);
		});
		const order = ["ODS", "DWD", "DWS", "ADS", "其他"];
		const layerNodes = Array.from(layers.entries())
			.sort((a, b) => order.indexOf(a[0]) - order.indexOf(b[0]))
			.map(([layer, list]) => ({
				title: `${layer}_层 (${list.length})`,
				key: `layer-${layer}`,
				children: list.map((model, idx) => ({
					title: model.name || model.alias || "未命名模型",
					key: resolveModelKey(model, `${layer}-${idx}`),
					isLeaf: true,
				})),
			}));
		return layerNodes;
	}, [filteredModels]);

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

	const treeData = useMemo(() => {
		if (spaces.length === 0) return [];
		return spaces.map((space, idx) => {
			const key = resolveSpaceKey(space, idx);
			return {
				title: space.name || "未命名项目空间",
				key,
				children: key === activeSpaceKey ? modelLayerNodes : [],
			};
		});
	}, [spaces, activeSpaceKey, modelLayerNodes]);

	return (
		<div className="flex min-h-[calc(100vh-120px)] flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
			<div className="flex h-16 items-center justify-between border-b bg-white px-6">
				<Space size="large">
					<div className="text-lg font-bold text-blue-600">Data Studio</div>
					<div className="rounded-md bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600">逻辑建模</div>
					<Badge status={configEnabled ? "success" : "error"} text={configEnabled ? "dbt 已连接" : "dbt 未启用"} />
				</Space>
				<Space>
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
					<Button type="primary" onClick={openRun} disabled={!configEnabled}>
						提交上线
					</Button>
					<Button onClick={() => setConfigOpen(true)}>工作区配置</Button>
				</Space>
			</div>

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
								selectedKeys={activeModelKey ? [activeModelKey] : activeSpaceKey ? [activeSpaceKey] : []}
								onSelect={(keys) => {
									const key = String(keys[0] || "");
									if (!key) return;
									if (key.startsWith("space-")) {
										setActiveSpaceKey(key);
										setActiveModelKey(null);
										return;
									}
									if (key.startsWith("layer-")) return;
									setActiveModelKey(key);
								}}
							/>
							{modelsLoading ? (
								<div className="mt-3 text-xs text-slate-400">加载模型中...</div>
							) : modelLayerNodes.length === 0 ? (
								<div className="mt-3 text-xs text-slate-400">当前项目暂无模型。</div>
							) : null}
						</>
					)}
				</div>

				<div className="flex flex-1 flex-col">
					<div className="flex items-center justify-between border-b px-4 py-2">
						<Space>
							<Text strong>{activeModel?.name || "未选择模型"}</Text>
							{layerTag(activeModel ? inferLayer(activeModel.name) : undefined)}
							<Text type="secondary">{activeModel?.path || "尚未定位模型路径"}</Text>
						</Space>
						<Space>
							<Button size="small" onClick={handleSyncModels} loading={syncingModels}>
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

					<div className="flex-1 overflow-auto bg-slate-950 px-6 py-4 font-mono text-sm text-slate-200">
						<div className="flex h-full items-center justify-center text-center text-xs text-slate-500">
							{activeModel ? "暂无模型 SQL 内容，等待元数据采集接入。" : "请选择模型查看 SQL。"}
						</div>
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
								<div>数据库：{activeModel.database || "-"}</div>
								<div>Schema：{activeModel.schema || "-"}</div>
								<div>路径：{activeModel.path || "-"}</div>
							</div>
						) : (
							<div className="text-xs text-slate-500">请选择模型查看详情。</div>
						)}
					</Card>
					<Card size="small" title="工作区配置" className="mb-4">
						<div className="text-xs text-slate-500">
							<div>项目目录：{dbtConfig?.config?.projectDir || "未配置"}</div>
							<div>Profiles：{dbtConfig?.config?.profilesDir || "未配置"}</div>
							<div>Target：{dbtConfig?.config?.targetName || "未配置"}</div>
						</div>
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
						<Input placeholder="例如：tag:source_system" />
					</Form.Item>
					<Form.Item name="target" label="Target">
						<Input placeholder="dev" />
					</Form.Item>
					<Form.Item name="vars" label="运行变量">
						<Input.TextArea rows={3} placeholder='JSON 结构，例如 {"run_date":"2026-01-19"}' />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
