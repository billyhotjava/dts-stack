import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
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
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import {
	createAirbyteConnection,
	discoverAirbyteSource,
	listAirbyteConnections,
	listAirbyteDestinationDefinitions,
	listAirbyteJobs,
	listAirbyteSources,
	listAirbyteSourceDefinitions,
	triggerAirbyteSync,
	updateAirbyteConnection,
} from "@/api/platformApi";
import { useUserInfo } from "@/store/userStore";

const { Text } = Typography;
const MASKED_SECRET = "******";

type AirbyteDefinition = {
	sourceDefinitionId?: string;
	destinationDefinitionId?: string;
	name?: string;
	dockerRepository?: string;
	dockerImageTag?: string;
	documentationUrl?: string;
	icon?: string;
	connectionSpecification?: Record<string, any>;
};

type AirbyteConnection = {
	id?: string;
	name?: string;
	infraSourceId?: string;
	sourceDefinitionId?: string;
	sourceId?: string;
	destinationId?: string;
	connectionId?: string;
	syncMode?: string;
	scheduleType?: string;
	scheduleCron?: string;
	namespace?: string;
	prefix?: string;
	owner?: string;
	status?: string;
	lastJobId?: string;
	lastJobStatus?: string;
	lastSyncAt?: string;
	enabled?: boolean;
	description?: string;
	sourceConfig?: Record<string, any> | null;
	destinationConfig?: Record<string, any> | null;
	selectedStreams?: string[];
	schemaStrategy?: string;
	reconcileRule?: string;
};

type AirbyteJob = {
	id?: string;
	status?: string;
	createdAt?: number | string;
	startedAt?: number | string;
	updatedAt?: number | string;
};

type AirbyteSource = {
	id?: string;
	name?: string;
	sourceDefinitionId?: string;
	sourceId?: string;
	config?: Record<string, any> | null;
};

type ConfigPair = {
	key?: string;
	value?: string;
	secret?: boolean;
};

const formatDateTime = (value?: string | number | null) => {
	if (value == null) return "-";
	const date = typeof value === "number" ? new Date(value) : new Date(String(value));
	if (Number.isNaN(date.getTime())) return String(value);
	return date.toLocaleString();
};

const normalizeText = (value?: string) => String(value || "").trim();
const normalizeUpper = (value?: string) => normalizeText(value).toUpperCase();

const statusColor = (status?: string) => {
	const key = normalizeUpper(status);
	if (["RUNNING", "PENDING", "PROCESSING"].includes(key)) return "blue";
	if (["SUCCEEDED", "SUCCESS", "COMPLETED"].includes(key)) return "green";
	if (["FAILED", "ERROR"].includes(key)) return "red";
	return "default";
};

const syncModeLabel = (mode?: string) => {
	const key = normalizeUpper(mode);
	if (key === "CDC") return "CDC 实时同步";
	if (key === "INCREMENTAL") return "增量追加";
	if (key === "FULL_REFRESH") return "全量重写";
	return mode || "未配置";
};

const scheduleLabel = (row: AirbyteConnection) => {
	const type = normalizeUpper(row.scheduleType);
	if (type === "CRON") return row.scheduleCron || "Cron";
	return "手动";
};

const isSecretKey = (key?: string) => {
	if (!key) return false;
	const token = key.toLowerCase();
	return [
		"password",
		"passwd",
		"secret",
		"token",
		"access_key",
		"accesskey",
		"client_secret",
		"private_key",
		"api_key",
	].some((item) => token.includes(item));
};

const formatPairValue = (value: any) => {
	if (value === null || value === undefined) return "";
	if (typeof value === "string") return value;
	try {
		return JSON.stringify(value);
	} catch {
		return String(value);
	}
};

const pairsFromConfig = (config?: Record<string, any> | null) => {
	if (!config) return [];
	return Object.entries(config).map(([key, value]) => ({
		key,
		value: formatPairValue(value),
		secret: isSecretKey(key),
	}));
};

const parsePairValue = (raw?: string) => {
	if (raw == null) return "";
	const text = String(raw).trim();
	if (!text) return "";
	if (text === MASKED_SECRET) return MASKED_SECRET;
	if (text === "true") return true;
	if (text === "false") return false;
	if (/^-?\\d+(\\.\\d+)?$/.test(text)) return Number(text);
	if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
		try {
			return JSON.parse(text);
		} catch {
			return text;
		}
	}
	return text;
};

const configFromPairs = (pairs?: ConfigPair[]) => {
	if (!pairs || pairs.length === 0) return undefined;
	const payload: Record<string, any> = {};
	for (const item of pairs) {
		const key = normalizeText(item.key);
		if (!key) continue;
		payload[key] = parsePairValue(item.value);
	}
	return payload;
};

const resolveIconSrc = (icon?: string) => {
	if (!icon) return "";
	if (icon.startsWith("data:") || icon.startsWith("http")) return icon;
	return `data:image/svg+xml;base64,${icon}`;
};

const renderDefinitionLabel = (item: AirbyteDefinition | undefined) => {
	if (!item) return <span>未知类型</span>;
	const src = resolveIconSrc(item.icon);
	return (
		<Space>
			{src ? (
				<img src={src} alt={item.name || "icon"} className="h-4 w-4 object-contain" />
			) : (
				<span className="inline-flex h-4 w-4 items-center justify-center rounded-full bg-slate-200 text-[10px] text-slate-500">
					{String(item.name || "D").slice(0, 1).toUpperCase()}
				</span>
			)}
			<span>{item.name || item.sourceDefinitionId || item.destinationDefinitionId}</span>
		</Space>
	);
};

export default function Page() {
	const [loading, setLoading] = useState(false);
	const [connections, setConnections] = useState<AirbyteConnection[]>([]);
	const [sourceDefs, setSourceDefs] = useState<AirbyteDefinition[]>([]);
	const [destinationDefs, setDestinationDefs] = useState<AirbyteDefinition[]>([]);
	const [infraSources, setInfraSources] = useState<AirbyteSource[]>([]);
	const [availableStreams, setAvailableStreams] = useState<string[]>([]);
	const [keyword, setKeyword] = useState("");
	const [editOpen, setEditOpen] = useState(false);
	const [editMode, setEditMode] = useState<"create" | "edit">("create");
	const [editing, setEditing] = useState<AirbyteConnection | null>(null);
	const [saving, setSaving] = useState(false);
	const [destinationMode, setDestinationMode] = useState<"default" | "custom">("default");
	const [jobsOpen, setJobsOpen] = useState(false);
	const [jobs, setJobs] = useState<AirbyteJob[]>([]);
	const [jobsLoading, setJobsLoading] = useState(false);
	const [activeJobConnection, setActiveJobConnection] = useState<AirbyteConnection | null>(null);
	const [form] = Form.useForm();
	const userInfo = useUserInfo() as any;

	const loadDefinitions = useCallback(async () => {
		try {
			const [sources, destinations, infraSourceResp] = await Promise.all([
				listAirbyteSourceDefinitions(),
				listAirbyteDestinationDefinitions(),
				listAirbyteSources(),
			]);
			setSourceDefs(Array.isArray(sources) ? (sources as AirbyteDefinition[]) : []);
			setDestinationDefs(Array.isArray(destinations) ? (destinations as AirbyteDefinition[]) : []);
			setInfraSources(Array.isArray(infraSourceResp) ? (infraSourceResp as AirbyteSource[]) : []);
		} catch (err: any) {
			toast.error(err?.message || "加载连接器列表失败");
		}
	}, []);

	const loadConnections = useCallback(
		async (refresh = false) => {
			setLoading(true);
			try {
				const resp = (await listAirbyteConnections(refresh)) as AirbyteConnection[];
				setConnections(Array.isArray(resp) ? resp : []);
			} catch (err: any) {
				toast.error(err?.message || "加载入湖配置失败");
			} finally {
				setLoading(false);
			}
		},
		[],
	);

	useEffect(() => {
		void loadDefinitions();
		void loadConnections(true);
	}, [loadConnections, loadDefinitions]);

	const stats = useMemo(() => {
		const total = connections.length;
		const enabled = connections.filter((row) => row.enabled !== false).length;
		const running = connections.filter((row) => normalizeUpper(row.lastJobStatus) === "RUNNING").length;
		const failed = connections.filter((row) => normalizeUpper(row.lastJobStatus) === "FAILED").length;
		return { total, enabled, running, failed };
	}, [connections]);

	const sourceMap = useMemo(() => {
		const map = new Map<string, AirbyteDefinition>();
		for (const def of sourceDefs) {
			const id = def.sourceDefinitionId;
			if (id) map.set(id, def);
		}
		return map;
	}, [sourceDefs]);

	const buildDefaultPairs = useCallback(
		(definitionId?: string) => {
			const def = definitionId ? sourceMap.get(definitionId) : undefined;
			const name = (def?.name || def?.dockerRepository || "").toLowerCase();
			const isDatabase =
				["postgres", "mysql", "oracle", "sql server", "mssql", "dameng", "dm8", "db2"].some((key) =>
					name.includes(key),
				);
			const keys = isDatabase
				? ["host", "port", "database", "schema", "username", "password"]
				: ["host", "port", "username", "password"];
			return keys.map((key) => ({ key, value: "", secret: isSecretKey(key) }));
		},
		[sourceMap],
	);

	const filteredConnections = useMemo(() => {
		const key = normalizeText(keyword).toLowerCase();
		if (!key) return connections;
		return connections.filter((row) =>
			[row.name, row.owner, row.syncMode, row.scheduleCron]
				.filter(Boolean)
				.some((value) => String(value).toLowerCase().includes(key)),
		);
	}, [connections, keyword]);

	const openCreate = () => {
		setEditMode("create");
		setEditing(null);
		setDestinationMode("default");
		setAvailableStreams([]);
		form.resetFields();
		form.setFieldsValue({
			name: "",
			syncMode: "INCREMENTAL",
			scheduleType: "manual",
			enabled: true,
			schemaStrategy: "AUTO",
			reconcileRule: "NONE",
			owner: userInfo?.username || userInfo?.name || "",
			sourceConfigPairs: buildDefaultPairs(),
			destinationConfigPairs: [],
		});
		setEditOpen(true);
	};

	const openEdit = (row: AirbyteConnection) => {
		setEditMode("edit");
		setEditing(row);
		setDestinationMode(row.destinationConfig ? "custom" : "default");
		form.resetFields();
		form.setFieldsValue({
			name: row.name,
			infraSourceId: row.infraSourceId,
			sourceDefinitionId: row.sourceDefinitionId,
			sourceConfigPairs: pairsFromConfig(row.sourceConfig),
			syncMode: row.syncMode || "INCREMENTAL",
			scheduleType: row.scheduleType || "manual",
			scheduleCron: row.scheduleCron,
			namespace: row.namespace,
			prefix: row.prefix,
			enabled: row.enabled !== false,
			owner: row.owner,
			description: row.description,
			selectedStreams: row.selectedStreams || [],
			schemaStrategy: row.schemaStrategy || "AUTO",
			reconcileRule: row.reconcileRule || "NONE",
			destinationDefinitionId: "",
			destinationConfigPairs: pairsFromConfig(row.destinationConfig),
		});
		if (row.infraSourceId) {
			void loadStreamsForSource(String(row.infraSourceId));
		} else {
			setAvailableStreams([]);
		}
		setEditOpen(true);
	};

	const handleInfraSourceChange = (value?: string) => {
		if (!value) {
			form.setFieldsValue({ sourceDefinitionId: undefined, sourceConfigPairs: buildDefaultPairs() });
			setAvailableStreams([]);
			return;
		}
		const selected = infraSources.find((item) => String(item.id) === String(value));
		if (!selected) return;
		form.setFieldsValue({
			sourceDefinitionId: selected.sourceDefinitionId,
			sourceConfigPairs: pairsFromConfig(selected.config || undefined),
		});
		void loadStreamsForSource(String(selected.id));
	};

	const loadStreamsForSource = async (sourceId?: string) => {
		if (!sourceId) {
			setAvailableStreams([]);
			return;
		}
		try {
			const payload = await discoverAirbyteSource(sourceId);
			const streams = Array.isArray(payload?.catalog?.streams) ? payload.catalog.streams : [];
			const names = streams
				.map((item: any) => item?.stream?.name)
				.filter((value: any) => typeof value === "string" && value.trim().length > 0);
			setAvailableStreams(Array.from(new Set(names)) as string[]);
		} catch {
			setAvailableStreams([]);
		}
	};

	const loadJobs = async (row: AirbyteConnection) => {
		if (!row.id) return;
		setJobsLoading(true);
		setActiveJobConnection(row);
		try {
			const resp = (await listAirbyteJobs(row.id, 20)) as AirbyteJob[];
			setJobs(Array.isArray(resp) ? resp : []);
			setJobsOpen(true);
		} catch (err: any) {
			toast.error(err?.message || "加载同步记录失败");
		} finally {
			setJobsLoading(false);
		}
	};

	const submitEdit = async () => {
		setSaving(true);
		try {
			const values = await form.validateFields();
			const infraSourceId = normalizeText(values.infraSourceId);
			const selectedSource = infraSourceId
				? infraSources.find((item) => String(item.id) === infraSourceId)
				: null;
			let sourceConfig = configFromPairs(values.sourceConfigPairs as ConfigPair[]);
			if (selectedSource?.config && (!sourceConfig || Object.keys(sourceConfig).length === 0)) {
				sourceConfig = selectedSource.config;
			}
			if (!sourceConfig || Object.keys(sourceConfig).length === 0) {
				toast.error("请完善源端连接配置");
				return;
			}
			const destinationConfig =
				destinationMode === "custom"
					? configFromPairs(values.destinationConfigPairs as ConfigPair[])
					: undefined;
			if (destinationMode === "custom" && destinationConfig && Object.keys(destinationConfig).length === 0) {
				toast.error("请完善目标端配置");
				return;
			}

			const payload: Record<string, any> = {
				name: normalizeText(values.name),
				infraSourceId: infraSourceId || undefined,
				sourceDefinitionId: normalizeText(selectedSource?.sourceDefinitionId || values.sourceDefinitionId),
				sourceConfig,
				syncMode: normalizeText(values.syncMode),
				scheduleType: normalizeText(values.scheduleType),
				scheduleCron: normalizeText(values.scheduleCron) || undefined,
				namespace: normalizeText(values.namespace) || undefined,
				prefix: normalizeText(values.prefix) || undefined,
				enabled: Boolean(values.enabled),
				owner: normalizeText(values.owner) || undefined,
				description: normalizeText(values.description) || undefined,
				selectedStreams: Array.isArray(values.selectedStreams) ? values.selectedStreams : [],
				schemaStrategy: normalizeText(values.schemaStrategy) || undefined,
				reconcileRule: normalizeText(values.reconcileRule) || undefined,
			};
			if (selectedSource?.sourceId) {
				payload.sourceId = selectedSource.sourceId;
			} else if (editing?.sourceId) {
				payload.sourceId = editing.sourceId;
			}
			if (destinationMode === "custom") {
				payload.destinationDefinitionId = normalizeText(values.destinationDefinitionId);
				payload.destinationConfig = destinationConfig;
			}

			if (editMode === "create") {
				await createAirbyteConnection(payload);
				toast.success("入湖接入已创建");
			} else if (editing?.id) {
				await updateAirbyteConnection(editing.id, payload);
				toast.success("入湖接入已更新");
			}
			setEditOpen(false);
			setEditing(null);
			await loadConnections(true);
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const triggerSync = async (row: AirbyteConnection) => {
		if (!row.id) return;
		try {
			await triggerAirbyteSync(row.id);
			toast.success("同步任务已触发");
			await loadConnections(true);
		} catch (err: any) {
			toast.error(err?.message || "触发失败");
		}
	};

	const columns: ColumnsType<AirbyteConnection> = useMemo(
		() => [
			{ title: "接入名称", dataIndex: "name", key: "name", width: 200, render: (v) => <Text strong>{v}</Text> },
			{
				title: "源端类型",
				dataIndex: "sourceDefinitionId",
				key: "sourceDefinitionId",
				width: 180,
				render: (v) => renderDefinitionLabel(sourceMap.get(String(v))),
			},
			{
				title: "同步表数",
				dataIndex: "selectedStreams",
				key: "selectedStreams",
				width: 120,
				render: (v) => (Array.isArray(v) ? v.length : 0),
			},
			{
				title: "同步策略",
				dataIndex: "syncMode",
				key: "syncMode",
				width: 140,
				render: (v) => <Tag>{syncModeLabel(v)}</Tag>,
			},
			{
				title: "调度周期",
				key: "schedule",
				width: 160,
				render: (_, row) => scheduleLabel(row),
			},
			{
				title: "最新状态",
				dataIndex: "lastJobStatus",
				key: "lastJobStatus",
				width: 140,
				render: (v) => <Tag color={statusColor(v)}>{normalizeUpper(v) || "未运行"}</Tag>,
			},
			{
				title: "最近同步",
				dataIndex: "lastSyncAt",
				key: "lastSyncAt",
				width: 180,
				render: (v) => formatDateTime(v),
			},
			{ title: "负责人", dataIndex: "owner", key: "owner", width: 120 },
			{
				title: "操作",
				key: "actions",
				fixed: "right",
				width: 200,
				render: (_, row) => (
					<Space>
						<Button size="small" onClick={() => triggerSync(row)}>
							同步
						</Button>
						<Button size="small" onClick={() => loadJobs(row)}>
							记录
						</Button>
						<Button size="small" onClick={() => openEdit(row)}>
							编辑
						</Button>
					</Space>
				),
			},
		],
		[sourceMap],
	);

	const jobColumns: ColumnsType<AirbyteJob> = [
		{ title: "任务 ID", dataIndex: "id", key: "id", width: 160 },
		{
			title: "状态",
			dataIndex: "status",
			key: "status",
			width: 120,
			render: (v) => <Tag color={statusColor(v)}>{normalizeUpper(v)}</Tag>,
		},
		{ title: "创建时间", dataIndex: "createdAt", key: "createdAt", render: (v) => formatDateTime(v) },
		{ title: "开始时间", dataIndex: "startedAt", key: "startedAt", render: (v) => formatDateTime(v) },
		{ title: "结束时间", dataIndex: "updatedAt", key: "updatedAt", render: (v) => formatDateTime(v) },
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据资源中心 · 数据入湖配置"
				description="配置业务系统到中台的同步规则，统一管理接入任务与状态。"
				actions={
					<Space>
						<Button onClick={() => loadConnections(true)} loading={loading}>
							刷新
						</Button>
						<Button type="primary" onClick={openCreate}>
							新建接入
						</Button>
					</Space>
				}
			/>

			<div className="grid gap-4 lg:grid-cols-4">
				<Card>
					<div className="text-sm text-gray-500">接入总数</div>
					<div className="mt-2 text-2xl font-semibold">{stats.total}</div>
				</Card>
				<Card>
					<div className="text-sm text-gray-500">启用中</div>
					<div className="mt-2 text-2xl font-semibold">{stats.enabled}</div>
				</Card>
				<Card>
					<div className="text-sm text-gray-500">运行中</div>
					<div className="mt-2 text-2xl font-semibold">{stats.running}</div>
				</Card>
				<Card>
					<div className="text-sm text-gray-500">失败</div>
					<div className="mt-2 text-2xl font-semibold text-red-500">{stats.failed}</div>
				</Card>
			</div>

			<Card
				title="入湖任务列表"
				extra={
					<Space>
						<Input
							placeholder="搜索名称/负责人"
							value={keyword}
							onChange={(e) => setKeyword(e.target.value)}
							style={{ width: 220 }}
							allowClear
						/>
					</Space>
				}
			>
				{filteredConnections.length === 0 && !loading ? (
					<EmptyState title="暂无入湖接入" description="新增一个数据接入，将业务库同步到中台。" />
				) : (
					<Table
						rowKey={(row) => row.id || row.connectionId || row.name || "row"}
						columns={columns}
						dataSource={filteredConnections}
						loading={loading}
						scroll={{ x: 1200 }}
						pagination={{ pageSize: 10 }}
					/>
				)}
			</Card>

			<Alert
				type="info"
				showIcon
				message="任务运行完成后将自动刷新元数据，并为后续建模提供最新表结构。"
			/>

			<Modal
				open={editOpen}
				title={editMode === "create" ? "新建入湖接入" : "编辑入湖接入"}
				onCancel={() => setEditOpen(false)}
				onOk={submitEdit}
				confirmLoading={saving}
				okText="保存"
				cancelText="取消"
				width={860}
			>
				<Form layout="vertical" form={form}>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="name" label="接入名称" rules={[{ required: true, message: "请输入接入名称" }]}>
							<Input placeholder="例如：ERP 主数据接入" />
						</Form.Item>
						<Form.Item name="owner" label="负责人">
							<Input placeholder="默认当前用户" />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="infraSourceId" label="已建数据源">
							<Select
								allowClear
								placeholder="选择已建数据源"
								options={infraSources.map((item) => {
									const def = sourceMap.get(String(item.sourceDefinitionId));
									const iconSrc = resolveIconSrc(def?.icon);
									return {
										label: (
											<Space>
												{iconSrc ? (
													<img src={iconSrc} alt={def?.name || "icon"} className="h-4 w-4 object-contain" />
												) : (
													<span className="inline-flex h-4 w-4 items-center justify-center rounded-full bg-slate-200 text-[10px] text-slate-500">
														{String(def?.name || "D").slice(0, 1).toUpperCase()}
													</span>
												)}
												<span>{item.name || item.id}</span>
												<Text type="secondary">{def?.name || "未知类型"}</Text>
											</Space>
										),
										value: item.id,
									};
								})}
								optionLabelProp="label"
								onChange={(value) => handleInfraSourceChange(value as string | undefined)}
							/>
						</Form.Item>
						<Form.Item shouldUpdate={(prev, next) => prev.infraSourceId !== next.infraSourceId} noStyle>
							{({ getFieldValue }) => (
								<Form.Item
									name="sourceDefinitionId"
									label="源端类型"
									rules={[{ required: true, message: "请选择源端类型" }]}
								>
									<Select
										placeholder="选择业务系统类型"
										options={sourceDefs.map((item) => ({
											label: renderDefinitionLabel(item),
											value: item.sourceDefinitionId,
										}))}
										optionLabelProp="label"
										disabled={Boolean(getFieldValue("infraSourceId"))}
										onChange={(value) => {
											if (!getFieldValue("infraSourceId")) {
												form.setFieldsValue({ sourceConfigPairs: buildDefaultPairs(String(value)) });
											}
										}}
									/>
								</Form.Item>
							)}
						</Form.Item>
						<Form.Item name="syncMode" label="同步策略">
							<Select
								options={[
									{ label: "全量重写", value: "FULL_REFRESH" },
									{ label: "增量追加", value: "INCREMENTAL" },
									{ label: "CDC 实时同步", value: "CDC" },
								]}
							/>
						</Form.Item>
					</div>
					<Form.Item shouldUpdate={(prev, next) => prev.infraSourceId !== next.infraSourceId} noStyle>
						{({ getFieldValue }) => (
							<Form.Item
								name="sourceConfigPairs"
								label="源端连接配置"
								rules={[{ required: true, message: "请填写源端连接配置" }]}
								extra="敏感字段支持脱敏显示，保持 ****** 表示不修改原值。"
							>
								<Form.List name="sourceConfigPairs">
									{(fields, { add, remove }) => (
										<div className="space-y-2">
											{fields.map((field) => (
												<Space key={field.key} align="start" className="w-full">
													<Form.Item
														{...field}
														name={[field.name, "key"]}
														rules={[{ required: true, message: "请输入参数名" }]}
														className="mb-0 w-40"
													>
														<Input placeholder="参数名" disabled={Boolean(getFieldValue("infraSourceId"))} />
													</Form.Item>
													<Form.Item shouldUpdate noStyle>
														{() => {
															const keyValue = form.getFieldValue(["sourceConfigPairs", field.name, "key"]);
															const secret = isSecretKey(keyValue);
															return (
																<Form.Item
																	{...field}
																	name={[field.name, "value"]}
																	rules={[{ required: true, message: "请输入参数值" }]}
																	className="mb-0 flex-1"
																>
																	{secret ? (
																		<Input.Password
																			placeholder="请输入"
																			disabled={Boolean(getFieldValue("infraSourceId"))}
																		/>
																	) : (
																		<Input placeholder="请输入" disabled={Boolean(getFieldValue("infraSourceId"))} />
																	)}
																</Form.Item>
															);
														}}
													</Form.Item>
													<Button
														type="text"
														onClick={() => remove(field.name)}
														disabled={Boolean(getFieldValue("infraSourceId"))}
													>
														删除
													</Button>
												</Space>
											))}
											<Button
												type="dashed"
												onClick={() => add({ key: "", value: "" })}
												disabled={Boolean(getFieldValue("infraSourceId"))}
											>
												+ 添加参数
											</Button>
										</div>
									)}
								</Form.List>
							</Form.Item>
						)}
					</Form.Item>
					<Form.Item name="selectedStreams" label="同步表/流选择">
						<Select
							mode="multiple"
							allowClear
							placeholder={availableStreams.length ? "选择需要同步的表" : "请先选择数据源并发现 Schema"}
							options={availableStreams.map((name) => ({ label: name, value: name }))}
						/>
					</Form.Item>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="schemaStrategy" label="Schema 策略">
							<Select
								options={[
									{ label: "自动迁移", value: "AUTO" },
									{ label: "阻断任务", value: "BLOCK" },
									{ label: "待办提醒", value: "MANUAL" },
								]}
							/>
						</Form.Item>
						<Form.Item name="reconcileRule" label="入湖对账">
							<Select
								options={[
									{ label: "不启用", value: "NONE" },
									{ label: "行数强一致", value: "ROW_COUNT_STRICT" },
								]}
							/>
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="scheduleType" label="调度方式">
							<Select
								options={[
									{ label: "手动", value: "manual" },
									{ label: "Cron", value: "cron" },
								]}
							/>
						</Form.Item>
						<Form.Item name="scheduleCron" label="Cron 表达式">
							<Input placeholder="0 2 * * *" />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="namespace" label="入湖命名空间">
							<Input placeholder="ods" />
						</Form.Item>
						<Form.Item name="prefix" label="表前缀">
							<Input placeholder="ods_" />
						</Form.Item>
					</div>

					<Card size="small" title="目标端配置" className="mb-4">
						<Space className="mb-4">
							<Text>目标端：</Text>
							<Select
								value={destinationMode}
								onChange={(value) => setDestinationMode(value)}
								options={[
									{ label: "中台默认数据湖", value: "default" },
									{ label: "自定义目标端", value: "custom" },
								]}
								style={{ width: 200 }}
							/>
						</Space>
						{destinationMode === "custom" ? (
							<>
								<Form.Item name="destinationDefinitionId" label="目标端类型">
									<Select
										placeholder="选择目标端类型"
										options={destinationDefs.map((item) => ({
											label: renderDefinitionLabel(item),
											value: item.destinationDefinitionId,
										}))}
										optionLabelProp="label"
									/>
								</Form.Item>
								<Form.Item name="destinationConfigPairs" label="目标端连接配置" extra="保持 ****** 表示不修改原值。">
									<Form.List name="destinationConfigPairs">
										{(fields, { add, remove }) => (
											<div className="space-y-2">
												{fields.map((field) => (
													<Space key={field.key} align="start" className="w-full">
														<Form.Item
															{...field}
															name={[field.name, "key"]}
															rules={[{ required: true, message: "请输入参数名" }]}
															className="mb-0 w-40"
														>
															<Input placeholder="参数名" />
														</Form.Item>
														<Form.Item shouldUpdate noStyle>
															{() => {
																const keyValue = form.getFieldValue(["destinationConfigPairs", field.name, "key"]);
																const secret = isSecretKey(keyValue);
																return (
																	<Form.Item
																		{...field}
																		name={[field.name, "value"]}
																		rules={[{ required: true, message: "请输入参数值" }]}
																		className="mb-0 flex-1"
																	>
																		{secret ? (
																			<Input.Password placeholder="请输入" />
																		) : (
																			<Input placeholder="请输入" />
																		)}
																	</Form.Item>
																);
															}}
														</Form.Item>
														<Button type="text" onClick={() => remove(field.name)}>
															删除
														</Button>
													</Space>
												))}
												<Button type="dashed" onClick={() => add({ key: "", value: "" })}>
													+ 添加参数
												</Button>
											</div>
										)}
									</Form.List>
								</Form.Item>
							</>
						) : (
							<Text type="secondary">默认写入中台统一数据湖/仓，不需要额外配置。</Text>
						)}
					</Card>

					<Form.Item name="description" label="说明">
						<Input.TextArea rows={2} placeholder="补充说明同步范围或口径" />
					</Form.Item>
					<Form.Item name="enabled" label="启用" valuePropName="checked">
						<Switch />
					</Form.Item>
				</Form>
			</Modal>

			<Drawer
				open={jobsOpen}
				onClose={() => setJobsOpen(false)}
				title={`同步记录${activeJobConnection?.name ? ` - ${activeJobConnection.name}` : ""}`}
				width={680}
			>
				<Table
					rowKey={(row) => String(row.id || row.createdAt || Math.random())}
					columns={jobColumns}
					dataSource={jobs}
					loading={jobsLoading}
					pagination={false}
					size="small"
				/>
			</Drawer>
		</div>
	);
}
