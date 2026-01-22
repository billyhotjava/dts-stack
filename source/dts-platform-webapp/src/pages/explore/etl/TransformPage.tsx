import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Col,
	Form,
	Input,
	Modal,
	Row,
	Select,
	Space,
	Statistic,
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
	const [statusFilter, setStatusFilter] = useState("ALL");
	const [editMode, setEditMode] = useState<"create" | "edit">("create");
	const [editing, setEditing] = useState<AirbyteConnection | null>(null);
	const [saving, setSaving] = useState(false);
	const [destinationMode, setDestinationMode] = useState<"default" | "custom">("default");
	const [jobs, setJobs] = useState<AirbyteJob[]>([]);
	const [jobsLoading, setJobsLoading] = useState(false);
	const [activeJobConnection, setActiveJobConnection] = useState<AirbyteConnection | null>(null);
	const [helpOpen, setHelpOpen] = useState(false);
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

	const sourceMap = useMemo(() => {
		const map = new Map<string, AirbyteDefinition>();
		for (const def of sourceDefs) {
			const id = def.sourceDefinitionId;
			if (id) map.set(id, def);
		}
		return map;
	}, [sourceDefs]);

	const sourceNameMap = useMemo(() => {
		const map = new Map<string, AirbyteSource>();
		infraSources.forEach((item) => {
			if (item.id) map.set(String(item.id), item);
		});
		return map;
	}, [infraSources]);

	const resolveHealth = useCallback((row: AirbyteConnection) => {
		if (row.enabled === false) return "PAUSED";
		const status = normalizeUpper(row.lastJobStatus);
		if (["FAILED", "ERROR"].includes(status)) return "BAD";
		if (["SUCCEEDED", "SUCCESS", "COMPLETED"].includes(status)) return "OK";
		if (status === "RUNNING") return "RUNNING";
		return "UNKNOWN";
	}, []);

	const resolveStatusLabel = useCallback((row: AirbyteConnection) => {
		const status = normalizeUpper(row.lastJobStatus);
		if (row.enabled === false) return "停用";
		if (status === "RUNNING") return "运行中";
		if (["SUCCEEDED", "SUCCESS", "COMPLETED"].includes(status)) return "健康";
		if (["FAILED", "ERROR"].includes(status)) return "异常";
		return status || "待运行";
	}, []);

	const resolveLag = useCallback((row: AirbyteConnection) => {
		const raw = row.lastSyncAt;
		if (!raw) return "-";
		const ts = new Date(raw as any).getTime();
		if (Number.isNaN(ts)) return "-";
		const diff = Date.now() - ts;
		if (diff <= 0) return "0m";
		const minutes = Math.floor(diff / 60000);
		if (minutes < 60) return `${minutes}m`;
		const hours = Math.floor(minutes / 60);
		if (hours < 24) return `${hours}h`;
		const days = Math.floor(hours / 24);
		return `${days}d`;
	}, []);

	const isLagging = useCallback((row: AirbyteConnection) => {
		const raw = row.lastSyncAt;
		if (!raw) return false;
		const ts = new Date(raw as any).getTime();
		if (Number.isNaN(ts)) return false;
		return Date.now() - ts > 24 * 60 * 60 * 1000;
	}, []);

	const hasSchemaChange = useCallback((row: AirbyteConnection) => {
		const status = normalizeUpper(row.status);
		const jobStatus = normalizeUpper(row.lastJobStatus);
		return status.includes("SCHEMA") || jobStatus.includes("SCHEMA");
	}, []);

	const stats = useMemo(() => {
		const total = connections.length;
		const success24h = connections.filter((row) => {
			const status = normalizeUpper(row.lastJobStatus);
			if (!["SUCCEEDED", "SUCCESS", "COMPLETED"].includes(status)) return false;
			const ts = row.lastSyncAt ? new Date(row.lastSyncAt as any).getTime() : NaN;
			if (Number.isNaN(ts)) return false;
			return Date.now() - ts < 24 * 60 * 60 * 1000;
		}).length;
		const lagging = connections.filter((row) => isLagging(row)).length;
		const schemaChanges = connections.filter((row) => hasSchemaChange(row)).length;
		return { total, success24h, lagging, schemaChanges };
	}, [connections, hasSchemaChange, isLagging]);

	const buildDefaultPairs = useCallback(
		(definitionId?: string) => {
			const def = definitionId ? sourceMap.get(definitionId) : undefined;
			const name = (def?.name || def?.dockerRepository || "").toLowerCase();
			const isDatabase = ["postgres", "mysql", "oracle", "sql server", "mssql", "dameng", "dm8", "db2"].some((key) =>
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
		return connections.filter((row) => {
			const matchesKeyword = !key
				? true
				: [row.name, row.owner, row.syncMode, row.scheduleCron]
						.filter(Boolean)
						.some((value) => String(value).toLowerCase().includes(key));
			if (!matchesKeyword) return false;
			if (statusFilter === "ALL") return true;
			return resolveHealth(row) === statusFilter;
		});
	}, [connections, keyword, resolveHealth, statusFilter]);

	const openCreate = () => {
		setEditMode("create");
		setEditing(null);
		setDestinationMode("default");
		setAvailableStreams([]);
		form.resetFields();
		const initialDefinitionId = sourceDefs[0]?.sourceDefinitionId;
		form.setFieldsValue({
			name: "",
			syncMode: "INCREMENTAL",
			scheduleType: "manual",
			enabled: true,
			schemaStrategy: "AUTO",
			reconcileRule: "NONE",
			owner: userInfo?.username || userInfo?.name || "",
			sourceDefinitionId: initialDefinitionId,
			sourceConfigPairs: buildDefaultPairs(initialDefinitionId),
			destinationConfigPairs: [],
			selectedStreams: [],
		});
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
			setEditing(null);
			setEditMode("create");
			await loadConnections(true);
			openCreate();
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

	const toMillis = (value?: number | string | null) => {
		if (value === null || value === undefined) return null;
		if (typeof value === "number") return value;
		const ts = new Date(String(value)).getTime();
		if (Number.isNaN(ts)) return null;
		return ts;
	};

	const formatDuration = useCallback((start?: number | string | null, end?: number | string | null) => {
		const startTs = toMillis(start);
		const endTs = toMillis(end);
		if (startTs === null || endTs === null) return "-";
		const diff = Math.max(0, endTs - startTs);
		const seconds = Math.floor(diff / 1000);
		if (seconds < 60) return `${seconds}s`;
		const minutes = Math.floor(seconds / 60);
		if (minutes < 60) return `${minutes}m`;
		const hours = Math.floor(minutes / 60);
		if (hours < 24) return `${hours}h`;
		const days = Math.floor(hours / 24);
		return `${days}d`;
	}, []);

	const columns: ColumnsType<AirbyteConnection> = useMemo(
		() => [
			{ title: "任务", dataIndex: "name", key: "name", render: (v) => <Text strong>{v}</Text> },
			{
				title: "源",
				dataIndex: "infraSourceId",
				key: "infraSourceId",
				render: (_, row) => {
					const source = row.infraSourceId ? sourceNameMap.get(String(row.infraSourceId)) : undefined;
					const def = sourceMap.get(String(row.sourceDefinitionId || source?.sourceDefinitionId));
					return (
						<Space direction="vertical" size={0}>
							<Text>{source?.name || row.sourceId || "未关联数据源"}</Text>
							<Text type="secondary" className="text-xs">
								{def?.name || row.sourceDefinitionId || "-"}
							</Text>
						</Space>
					);
				},
			},
			{
				title: "目标",
				dataIndex: "namespace",
				key: "namespace",
				render: (_, row) => <Text>{row.namespace ? `ODS.${row.namespace}` : "默认 ODS"}</Text>,
			},
			{
				title: "模式",
				dataIndex: "syncMode",
				key: "syncMode",
				render: (v) => <Tag>{syncModeLabel(v)}</Tag>,
			},
			{
				title: "延迟",
				dataIndex: "lastSyncAt",
				key: "lastSyncAt",
				render: (_, row) => resolveLag(row),
			},
			{
				title: "状态",
				dataIndex: "lastJobStatus",
				key: "lastJobStatus",
				render: (_, row) => <Tag color={statusColor(row.lastJobStatus)}>{resolveStatusLabel(row)}</Tag>,
			},
			{
				title: "操作",
				key: "actions",
				render: (_, row) => (
					<Space>
						<Button size="small" onClick={() => triggerSync(row)}>
							运行
						</Button>
						<Button size="small" onClick={() => loadJobs(row)}>
							历史
						</Button>
						<Button size="small" onClick={() => openEdit(row)}>
							编辑
						</Button>
					</Space>
				),
			},
		],
		[resolveLag, resolveStatusLabel, sourceMap, sourceNameMap],
	);

	const jobRows = useMemo(
		() =>
			jobs.map((job) => ({
				key: String(job.id || job.createdAt || Math.random()),
				time: formatDateTime(job.createdAt),
				name: activeJobConnection?.name || "-",
				status: normalizeUpper(job.status) || "-",
				sourceRows: "-",
				targetRows: "-",
				delta: "-",
				duration: formatDuration(job.startedAt, job.updatedAt),
			})),
		[activeJobConnection?.name, formatDuration, jobs],
	);

	const jobColumns: ColumnsType<any> = [
		{ title: "时间", dataIndex: "time" },
		{ title: "任务", dataIndex: "name" },
		{
			title: "状态",
			dataIndex: "status",
			render: (v) => <Tag color={statusColor(v)}>{v}</Tag>,
		},
		{ title: "源行数", dataIndex: "sourceRows" },
		{ title: "目标行数", dataIndex: "targetRows" },
		{ title: "差异", dataIndex: "delta" },
		{ title: "延迟", dataIndex: "duration" },
	];

	const statusOptions = [
		{ label: "全部", value: "ALL" },
		{ label: "健康", value: "OK" },
		{ label: "异常", value: "BAD" },
		{ label: "运行中", value: "RUNNING" },
		{ label: "停用", value: "PAUSED" },
		{ label: "待运行", value: "UNKNOWN" },
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据入湖任务"
				description="Airbyte Connection 的业务化包装：统一管理入湖任务、运行历史与治理策略。"
				actions={
					<Space>
						<Button onClick={() => loadConnections(true)} loading={loading}>
							刷新
						</Button>
						<Button onClick={() => setHelpOpen(true)}>使用说明</Button>
						<Button type="primary" onClick={openCreate}>
							新建入湖任务
						</Button>
					</Space>
				}
			/>

			<Alert
				type="info"
				showIcon
				message="入湖任务封装源连接、目标 ODS 预设与同步策略，运行完成后自动刷新元数据。"
			/>

			<Row gutter={[16, 16]}>
				<Col xs={12} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="入湖任务" value={stats.total} />
					</Card>
				</Col>
				<Col xs={12} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="近24h成功" value={stats.success24h} />
					</Card>
				</Col>
				<Col xs={12} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="延迟告警" value={stats.lagging} valueStyle={{ color: "#d48806" }} />
					</Card>
				</Col>
				<Col xs={12} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="Schema 变更待处理" value={stats.schemaChanges} />
					</Card>
				</Col>
			</Row>

			<Row gutter={[16, 16]} align="top">
				<Col xs={24} xl={15}>
					<Card
						title="入湖任务列表"
						extra={
							<Space>
								<Input
									placeholder="按任务名 / 负责人搜索"
									value={keyword}
									onChange={(e) => setKeyword(e.target.value)}
									style={{ width: 220 }}
									allowClear
								/>
								<Select value={statusFilter} onChange={setStatusFilter} style={{ width: 140 }} options={statusOptions} />
							</Space>
						}
					>
						{filteredConnections.length === 0 && !loading ? (
							<EmptyState title="暂无入湖任务" description="新增一个数据接入，将业务库同步到中台。" />
						) : (
							<Table
								rowKey={(row) => row.id || row.connectionId || row.name || "row"}
								columns={columns}
								dataSource={filteredConnections}
								loading={loading}
								pagination={{ pageSize: 8 }}
							/>
						)}
					</Card>
				</Col>
				<Col xs={24} xl={9}>
					<Card
						title={editMode === "create" ? "创建入湖任务" : "编辑入湖任务"}
						extra={
							editMode === "edit" ? (
								<Button size="small" onClick={openCreate}>
									切换为新建
								</Button>
							) : null
						}
					>
						<Form layout="vertical" form={form}>
							<div className="grid gap-4 md:grid-cols-2">
								<Form.Item name="name" label="任务名称" rules={[{ required: true, message: "请输入任务名称" }]}>
									<Input placeholder="例如：ERP-销售订单入湖" />
								</Form.Item>
								<Form.Item name="owner" label="负责人">
									<Input placeholder="默认当前用户" />
								</Form.Item>
							</div>
							<div className="grid gap-4 md:grid-cols-2">
								<Form.Item name="infraSourceId" label="选择源（数据源连接）">
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
							</div>
							<div className="grid gap-4 md:grid-cols-2">
								<Form.Item name="syncMode" label="同步模式">
									<Select
										options={[
											{ label: "全量刷新（覆盖）", value: "FULL_REFRESH" },
											{ label: "增量追加（窗口）", value: "INCREMENTAL" },
											{ label: "CDC（日志捕获）", value: "CDC" },
										]}
									/>
								</Form.Item>
								<Form.Item name="schemaStrategy" label="Schema 漂移策略">
									<Select
										options={[
											{ label: "自动接纳新增字段", value: "AUTO" },
											{ label: "阻断并告警", value: "BLOCK" },
											{ label: "需要审批后接纳", value: "MANUAL" },
										]}
									/>
								</Form.Item>
							</div>
							<div className="grid gap-4 md:grid-cols-2">
								<Form.Item name="reconcileRule" label="入湖对账">
									<Select
										options={[
											{ label: "不启用", value: "NONE" },
											{ label: "行数强一致", value: "ROW_COUNT_STRICT" },
										]}
									/>
								</Form.Item>
								<Form.Item name="scheduleType" label="调度方式">
									<Select
										options={[
											{ label: "手动", value: "manual" },
											{ label: "Cron", value: "cron" },
										]}
									/>
								</Form.Item>
							</div>
							<div className="grid gap-4 md:grid-cols-2">
								<Form.Item name="scheduleCron" label="Cron 表达式">
									<Input placeholder="0 2 * * *" />
								</Form.Item>
								<Form.Item name="namespace" label="入湖命名空间">
									<Input placeholder="ods" />
								</Form.Item>
							</div>
							<div className="grid gap-4 md:grid-cols-2">
								<Form.Item name="prefix" label="表前缀">
									<Input placeholder="ods_" />
								</Form.Item>
								<Form.Item name="enabled" label="启用" valuePropName="checked">
									<Switch />
								</Form.Item>
							</div>
							<Form.Item name="selectedStreams" label="已选择对象">
								<Select
									mode="multiple"
									allowClear
									placeholder={availableStreams.length ? "选择需要同步的表" : "请先选择数据源并发现 Schema"}
									options={availableStreams.map((name) => ({ label: name, value: name }))}
								/>
							</Form.Item>
							<Space>
								<Button
									onClick={async () => {
										const sourceId = form.getFieldValue("infraSourceId");
										if (!sourceId) {
											toast.error("请先选择数据源");
											return;
										}
										await loadStreamsForSource(String(sourceId));
										toast.success("已发现可同步对象");
									}}
								>
									发现可同步对象
								</Button>
								<Button type="primary" onClick={submitEdit} loading={saving}>
									{editMode === "create" ? "创建入湖任务" : "保存修改"}
								</Button>
								<Button onClick={() => toast.success("已保存草稿（示例）")}>保存草稿</Button>
							</Space>

							<Form.Item shouldUpdate={(prev, next) => prev.infraSourceId !== next.infraSourceId} noStyle>
								{({ getFieldValue }) => (
									<Card size="small" title="源端连接配置" className="mt-4">
										<Form.Item
											name="sourceConfigPairs"
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
																					<Input.Password placeholder="请输入" disabled={Boolean(getFieldValue("infraSourceId"))} />
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
									</Card>
								)}
							</Form.Item>

							<Card size="small" title="目标端配置" className="mt-4">
								<Space className="mb-3">
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
																				{secret ? <Input.Password placeholder="请输入" /> : <Input placeholder="请输入" />}
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

							<Form.Item name="description" label="说明" className="mt-3">
								<Input.TextArea rows={2} placeholder="补充说明同步范围或口径" />
							</Form.Item>
						</Form>
					</Card>
				</Col>
			</Row>

			<Card
				title="运行历史与对账"
				extra={
					activeJobConnection ? (
						<Text type="secondary">当前任务：{activeJobConnection.name}</Text>
					) : (
						<Text type="secondary">请选择任务查看历史</Text>
					)
				}
			>
				{jobRows.length === 0 && !jobsLoading ? (
					<EmptyState title="暂无运行记录" description="点击任务的“历史”查看运行记录。" />
				) : (
					<Table columns={jobColumns} dataSource={jobRows} loading={jobsLoading} pagination={{ pageSize: 6 }} />
				)}
			</Card>

			<Modal
				open={helpOpen}
				title="使用说明"
				onCancel={() => setHelpOpen(false)}
				footer={[
					<Button key="close" onClick={() => setHelpOpen(false)}>
						关闭
					</Button>,
				]}
			>
				<div className="space-y-2 text-sm text-slate-600">
					<div>1. 入湖任务复用数据源连接，封装同步模式、表选择与漂移策略。</div>
					<div>2. 点击“发现可同步对象”获取最新 Schema，再选择同步表。</div>
					<div>3. 运行历史与对账指标后续会补充到实际作业输出。</div>
				</div>
			</Modal>
		</div>
	);
}
