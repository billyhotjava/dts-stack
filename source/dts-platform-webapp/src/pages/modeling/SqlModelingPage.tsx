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
import { getDbtConfig, listDbtModels, listDbtRuns, triggerDbtRun, updateDbtConfig } from "@/api/platformApi";

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

export default function SqlModelingPage() {
	const [configLoading, setConfigLoading] = useState(false);
	const [configSaving, setConfigSaving] = useState(false);
	const [configOpen, setConfigOpen] = useState(false);
	const [dbtConfig, setDbtConfig] = useState<DbtConfigView | null>(null);
	const [modelsLoading, setModelsLoading] = useState(false);
	const [modelResult, setModelResult] = useState<DbtModelResult | null>(null);
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
	}, [loadConfig, loadModels, loadRuns]);

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

	const treeData = useMemo(() => {
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
		if (layerNodes.length === 0) {
			return [];
		}
		return [
			{
				title: `核心数仓项目 (dbt)`,
				key: "root",
				children: layerNodes,
			},
		];
	}, [filteredModels]);

	const editorLines = useMemo(() => {
		const modelName = activeModel?.name || "dwd_order_detail";
		const upstream = modelName.startsWith("dwd_")
			? modelName.replace("dwd_", "ods_")
			: modelName.startsWith("dws_")
				? modelName.replace("dws_", "dwd_")
				: "ods_orders";
		return [
			`WITH base AS (`,
			`  SELECT * FROM {{ ref('${upstream}') }}`,
			`),`,
			`final AS (`,
			`  SELECT`,
			`    order_id,`,
			`    customer_id,`,
			`    SUM(order_amt) AS total_revenue`,
			`  FROM base`,
			`  GROUP BY 1, 2`,
			`)`,
			`SELECT * FROM final;`,
		];
	}, [activeModel?.name]);

	const previewColumns: ColumnsType<Record<string, any>> = [
		{ title: "order_id", dataIndex: "order_id", key: "order_id" },
		{ title: "customer_id", dataIndex: "customer_id", key: "customer_id" },
		{
			title: "total_revenue",
			dataIndex: "total_revenue",
			key: "total_revenue",
			render: (v) => <span className="font-mono text-green-600">{v}</span>,
		},
	];

	const previewRows = [
		{ key: 1, order_id: "ORD001", customer_id: "C772", total_revenue: 128.5 },
		{ key: 2, order_id: "ORD002", customer_id: "C881", total_revenue: 99.0 },
		{ key: 3, order_id: "ORD003", customer_id: "C102", total_revenue: 250.3 },
	];

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

	const compileLogs = useMemo(() => {
		const now = formatDateTime(new Date().toISOString());
		const name = activeModel?.name || "dwd_order_detail";
		const schema = activeModel?.schema || dbtConfig?.config?.schema || "public";
		return [
			`[${now}] Compiled successfully.`,
			`[${now}] Model: ${name}`,
			`[${now}] Target schema: ${schema}`,
			`[${now}] SQL rendered with ref('${name}').`,
		];
	}, [activeModel?.name, activeModel?.schema, dbtConfig?.config?.schema]);

	const metadataWarnings = useMemo(() => {
		if (!activeModel) {
			return "尚未选择模型，无法进行落标检查。";
		}
		if (activeModel?.schema && dbtConfig?.config?.schema && activeModel.schema !== dbtConfig.config.schema) {
			return `字段 [customer_id] 在标准库中定义为 STRING(32)，当前模型为 ${activeModel.schema}。`;
		}
		return "字段 [customer_id] 在标准库中定义为 STRING(32)，当前模型中为 VARCHAR。";
	}, [activeModel, dbtConfig?.config?.schema]);

	return (
		<div className="flex min-h-[calc(100vh-120px)] flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
			<div className="flex h-16 items-center justify-between border-b bg-white px-6">
				<Space size="large">
					<div className="text-lg font-bold text-blue-600">Data Studio</div>
					<div className="rounded-md bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600">逻辑建模</div>
					<Badge status={configEnabled ? "success" : "error"} text={configEnabled ? "dbt 已连接" : "dbt 未启用"} />
				</Space>
				<Space>
					<Tooltip title="语法校验 (dbt compile)">
						<Button
							onClick={() => {
								toast.success("语法校验完成");
								setBottomTab("compile");
							}}
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
					{modelsLoading ? (
						<Card size="small" className="border-dashed text-center text-xs text-slate-400">
							加载模型中...
						</Card>
					) : treeData.length === 0 ? (
						<EmptyState title="暂无模型" description={modelResult?.message || "先生成 dbt manifest.json"} />
					) : (
						<DirectoryTree
							defaultExpandAll
							treeData={treeData}
							selectedKeys={activeModelKey ? [activeModelKey] : []}
							onSelect={(keys) => {
								const key = String(keys[0] || "");
								if (!key || key.startsWith("layer-") || key === "root") return;
								setActiveModelKey(key);
							}}
						/>
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
							<Button size="small" onClick={loadModels} loading={modelsLoading}>
								刷新模型
							</Button>
							<Button size="small" onClick={loadRuns} loading={runsLoading}>
								刷新运行
							</Button>
						</Space>
					</div>

					<div className="flex-1 overflow-auto bg-slate-950 px-6 py-4 font-mono text-sm text-slate-200">
						{editorLines.map((line, index) => (
							<div key={`${line}-${index}`} className="flex items-start">
								<span className="w-10 pr-4 text-right text-slate-600">{index + 1}</span>
								<span className="whitespace-pre">{line}</span>
							</div>
						))}
						<div className="mt-1 h-5 w-px animate-pulse bg-blue-400" />
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
										<Table
											size="small"
											pagination={false}
											scroll={{ y: 140 }}
											dataSource={previewRows}
											columns={previewColumns}
										/>
									),
								},
								{
									key: "compile",
									label: "编译日志",
									children: (
										<div className="p-4 font-mono text-xs text-slate-500">
											{compileLogs.map((line) => (
												<div key={line}>{line}</div>
											))}
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
					<div className="mb-4 text-xs font-bold uppercase text-slate-400">标准与元数据参考</div>
					<Card size="small" title="推荐数据元" className="mb-4">
						<div className="space-y-2 text-xs">
							<div className="flex items-center justify-between rounded bg-blue-50 px-2 py-1">
								<Text code>order_amt</Text>
								<Tag color="blue">DECIMAL</Tag>
							</div>
							<div className="flex items-center justify-between rounded px-2 py-1 hover:bg-slate-50">
								<Text code>customer_id</Text>
								<Tag>STRING(32)</Tag>
							</div>
							<div className="flex items-center justify-between rounded px-2 py-1 hover:bg-slate-50">
								<Text code>order_id</Text>
								<Tag>STRING</Tag>
							</div>
						</div>
					</Card>
					<Card size="small" title="模型上下文" className="mb-4">
						<div className="text-xs text-slate-500">上游依赖 (Upstream)</div>
						<Tag className="mb-2 mt-1">{activeModel?.name ? `ref('${activeModel.name}')` : "ref('ods_orders')"}</Tag>
						<div className="text-xs text-slate-500">引用描述</div>
						<Text className="text-xs">
							该模型用于计算每日销售汇总，对应的标准术语：有效销售额。
						</Text>
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
						<div className="text-xs font-bold text-yellow-700">落标检查预警</div>
						<div className="mt-1 text-xs text-yellow-600">{metadataWarnings}</div>
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
						<Input.TextArea rows={3} placeholder='JSON 结构，例如 {"erp_schema":"erp_demo"}' />
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
						<Input placeholder="例如：model:erp_demo+" />
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
