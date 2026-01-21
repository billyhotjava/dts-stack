import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Badge,
	Button,
	Card,
	Col,
	Form,
	Input,
	Modal,
	Row,
	Select,
	Space,
	Table,
	Tag,
	Tooltip,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
	AppstoreOutlined,
	DatabaseOutlined,
	EditOutlined,
	MoreOutlined,
	PlusOutlined,
	ReloadOutlined,
	SearchOutlined,
	ThunderboltOutlined,
	UnorderedListOutlined,
} from "@ant-design/icons";
import { EmptyState } from "@/components/empty-state";
import mysqlIcon from "@/assets/connector-icons/mysql.svg";
import postgresIcon from "@/assets/connector-icons/postgresql.svg";
import oracleIcon from "@/assets/connector-icons/oracle.svg";
import mssqlIcon from "@/assets/connector-icons/mssql.svg";
import csvIcon from "@/assets/connector-icons/file-csv.svg";
import dmIcon from "@/assets/connector-icons/database.svg";
import excelIcon from "@/assets/icons/file-excel.svg";
import {
	checkAirbyteSource,
	createAirbyteSource,
	discoverAirbyteSource,
	listAirbyteSourceDefinitions,
	listAirbyteSources,
	updateAirbyteSource,
} from "@/api/platformApi";
import { useRouter } from "@/routes/hooks";

const { Text, Title } = Typography;

type AirbyteDefinition = {
	sourceDefinitionId?: string;
	name?: string;
	icon?: string;
	dockerRepository?: string;
};

type AirbyteSource = {
	id?: string;
	name?: string;
	sourceDefinitionId?: string;
	sourceId?: string;
	config?: Record<string, any> | null;
	owner?: string;
	status?: string;
	lastCheckedAt?: string;
	lastDiscoveredAt?: string;
	enabled?: boolean;
	description?: string;
};

type AllowedDefinition = {
	key: string;
	matchers: string[];
	displayName?: string;
};

const formatDateTime = (value?: string) => {
	if (!value) return "-";
	try {
		return new Date(value).toLocaleString();
	} catch {
		return value;
	}
};

const safeLower = (value?: string) => String(value || "").trim().toLowerCase();

const ALLOWED_SOURCE_DEFS: AllowedDefinition[] = [
	{ key: "mysql", matchers: ["mysql"], displayName: "MySQL" },
	{ key: "oracle", matchers: ["oracle db", "oracle"], displayName: "Oracle" },
	{ key: "postgres", matchers: ["postgres"], displayName: "Postgres" },
	{
		key: "file",
		matchers: ["file (csv, json, excel, feather, parquet)", "airbyte/source-file", "file (csv, excel)"],
		displayName: "File (CSV, Excel)",
	},
	{ key: "dameng", matchers: ["dameng", "dm8", "达梦"], displayName: "Dameng (DM)" },
	{ key: "mssql", matchers: ["microsoft sql server (mssql)", "mssql", "sql server"], displayName: "Microsoft SQL Server" },
];

const parseJsonObject = (raw: string | undefined, label: string) => {
	const text = String(raw || "").trim();
	if (!text) return undefined;
	try {
		const parsed = JSON.parse(text);
		if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
			return parsed as Record<string, any>;
		}
	} catch {
		throw new Error(`${label} JSON 格式错误`);
	}
	throw new Error(`${label} 必须是 JSON 对象`);
};

const formatJson = (value?: Record<string, any>) => {
	if (!value) return "";
	try {
		return JSON.stringify(value, null, 2);
	} catch {
		return "";
	}
};

const resolveDefinitionName = (map: Record<string, AirbyteDefinition>, id?: string) => {
	if (!id) return "-";
	return map[id]?.name || id;
};

const resolveDefinition = (map: Record<string, AirbyteDefinition>, id?: string) => {
	if (!id) return undefined;
	return map[id];
};

const resolveIconSrc = (icon?: string) => {
	const value = String(icon || "").trim();
	if (!value) return "";
	if (value.startsWith("/") || value.startsWith("./") || value.startsWith("../")) return value;
	if (value.startsWith("http") || value.startsWith("data:")) return value;
	if (value.startsWith("<svg")) {
		return `data:image/svg+xml;utf8,${encodeURIComponent(value)}`;
	}
	if (value.endsWith(".svg")) return value;
	return `data:image/svg+xml;base64,${value}`;
};

const renderDefinitionIcon = (def?: AirbyteDefinition, size = 18) => {
	const fallback = resolveFallbackIcon(def);
	const src = resolveIconSrc(fallback || def?.icon);
	if (src) {
		return <img src={src} alt={def?.name || "source"} style={{ width: size, height: size }} />;
	}
	return <DatabaseOutlined />;
};

const renderDefinitionTag = (def?: AirbyteDefinition) => (
	<Tag>
		<Space size={6}>
			{renderDefinitionIcon(def, 14)}
			<span>{def?.name || "-"}</span>
		</Space>
	</Tag>
);

const resolveFallbackIcon = (def?: AirbyteDefinition) => {
	const name = safeLower(def?.name);
	const repo = safeLower(def?.dockerRepository);
	const text = `${name} ${repo}`;
	if (text.includes("mysql")) return mysqlIcon;
	if (text.includes("postgres")) return postgresIcon;
	if (text.includes("oracle")) return oracleIcon;
	if (text.includes("mssql") || text.includes("sql server")) return mssqlIcon;
	if (text.includes("dameng") || text.includes("dm8") || text.includes("达梦")) return dmIcon;
	if (text.includes("excel")) return excelIcon;
	if (text.includes("csv") || text.includes("file")) return csvIcon;
	return "";
};

const filterAllowedDefinitions = (defs: AirbyteDefinition[]) => {
	if (!Array.isArray(defs) || defs.length === 0) return [];
	const result: AirbyteDefinition[] = [];
	const used = new Set<string>();
	for (const allow of ALLOWED_SOURCE_DEFS) {
		const match = defs.find((item) => {
			const name = safeLower(item?.name);
			const repo = safeLower(item?.dockerRepository);
			return allow.matchers.some((matcher) => {
				const needle = safeLower(matcher);
				if (!needle) return false;
				return name.includes(needle) || repo.includes(needle);
			});
		});
		if (!match) continue;
		const id = match.sourceDefinitionId || allow.key;
		if (used.has(id)) continue;
		used.add(id);
		result.push({
			...match,
			name: allow.displayName || match.name,
		});
	}
	return result;
};

const defaultConfigForDefinition = (def?: AirbyteDefinition) => {
	if (!def) return {};
	const raw = `${def.name || ""} ${def.dockerRepository || ""} ${def.sourceDefinitionId || ""}`.toLowerCase();
	if (raw.includes("postgres")) {
		return { host: "", port: 5432, database: "", username: "", password: "" };
	}
	if (raw.includes("file") || raw.includes("csv") || raw.includes("excel")) {
		return { dataset_name: "", format: "csv", url: "" };
	}
	if (raw.includes("mysql")) {
		return { host: "", port: 3306, database: "", username: "", password: "" };
	}
	if (raw.includes("dameng") || raw.includes("dm") || raw.includes("dm8")) {
		return { host: "", port: 5236, database: "", username: "", password: "" };
	}
	if (raw.includes("hive")) {
		return { host: "", port: 10000, database: "default", username: "", password: "" };
	}
	if (raw.includes("oracle")) {
		return { host: "", port: 1521, service_name: "", username: "", password: "" };
	}
	if (raw.includes("sqlserver") || raw.includes("sql server") || raw.includes("mssql")) {
		return { host: "", port: 1433, database: "", username: "", password: "" };
	}
	return { host: "", port: "", database: "", username: "", password: "" };
};

const formatDefaultConfig = (def?: AirbyteDefinition) => {
	const config = defaultConfigForDefinition(def);
	return JSON.stringify(config, null, 2);
};

const extractEndpoint = (config?: Record<string, any> | null) => {
	if (!config) return "-";
	const host = config.host || config.hostname || config.server || config.endpoint;
	const port = config.port || config.tcpPort || config.db_port;
	if (host && port) return `${host}:${port}`;
	return host || port || "-";
};

export default function DataSourcesPage() {
	const [loading, setLoading] = useState(false);
	const [defLoading, setDefLoading] = useState(false);
	const [sources, setSources] = useState<AirbyteSource[]>([]);
	const [sourceDefs, setSourceDefs] = useState<Record<string, AirbyteDefinition>>({});
	const [sourceDefList, setSourceDefList] = useState<AirbyteDefinition[]>([]);
	const [editOpen, setEditOpen] = useState(false);
	const [editMode, setEditMode] = useState<"create" | "edit">("create");
	const [editing, setEditing] = useState<AirbyteSource | null>(null);
	const [saving, setSaving] = useState(false);
	const [rowCheckingId, setRowCheckingId] = useState<string | null>(null);
	const [rowDiscoveringId, setRowDiscoveringId] = useState<string | null>(null);
	const [search, setSearch] = useState("");
	const [viewMode, setViewMode] = useState<"card" | "list">("card");
	const [form] = Form.useForm();
	const router = useRouter();

	const loadDefinitions = useCallback(async (notify = false) => {
		setDefLoading(true);
		try {
			const defResp = await listAirbyteSourceDefinitions();
			const rawList = Array.isArray(defResp) ? (defResp as AirbyteDefinition[]) : [];
			const defList = filterAllowedDefinitions(rawList);
			const sourceMap: Record<string, AirbyteDefinition> = {};
			defList.forEach((item) => {
				if (item?.sourceDefinitionId) {
					sourceMap[item.sourceDefinitionId] = item;
				}
			});
			setSourceDefs(sourceMap);
			setSourceDefList(defList);
			if (notify && defList.length === 0) {
				toast.error("未获取到数据源类型");
			}
		} catch (err: any) {
			if (notify) {
				toast.error(err?.message || "获取数据源类型失败");
			}
		} finally {
			setDefLoading(false);
		}
	}, []);

	const loadAll = useCallback(async () => {
		setLoading(true);
		try {
			const sourceResp = await listAirbyteSources();
			setSources(Array.isArray(sourceResp) ? (sourceResp as AirbyteSource[]) : []);
			await loadDefinitions();
		} catch (err: any) {
			toast.error(err?.message || "加载数据源失败");
		} finally {
			setLoading(false);
		}
	}, [loadDefinitions]);

	useEffect(() => {
		void loadAll();
	}, [loadAll]);

	const filteredSources = useMemo(() => {
		const keyword = search.trim().toLowerCase();
		if (!keyword) return sources;
		return sources.filter((row) => {
			const hay = `${row.name} ${row.owner} ${row.description}`.toLowerCase();
			return hay.includes(keyword);
		});
	}, [sources, search]);

	const openDetail = (row: AirbyteSource) => {
		if (!row?.id) return;
		router.push(`/foundation/data-sources/${row.id}`);
	};

	const doCheck = async (row: AirbyteSource) => {
		if (!row?.id) return;
		setRowCheckingId(row.id);
		try {
			const result = await checkAirbyteSource(row.id);
			const status = String(result?.status || "").toLowerCase();
			toast.success(status ? `测试完成：${status}` : "测试完成");
			await loadAll();
		} catch (err: any) {
			toast.error(err?.message || "连接测试失败");
		} finally {
			setRowCheckingId(null);
		}
	};

	const doDiscover = async (row: AirbyteSource) => {
		if (!row?.id) return;
		setRowDiscoveringId(row.id);
		try {
			await discoverAirbyteSource(row.id);
			toast.success("已触发 Schema 探查");
			await loadAll();
		} catch (err: any) {
			toast.error(err?.message || "Schema 探查失败");
		} finally {
			setRowDiscoveringId(null);
		}
	};

	const openCreate = () => {
		setEditMode("create");
		setEditing(null);
		form.resetFields();
		const firstDef = sourceDefList[0];
		form.setFieldsValue({
			enabled: true,
			sourceDefinitionId: firstDef?.sourceDefinitionId,
			configJson: firstDef ? formatDefaultConfig(firstDef) : "",
		});
		setEditOpen(true);
		if (sourceDefList.length === 0) {
			void loadDefinitions(true);
		}
	};

	const openEdit = (row: AirbyteSource) => {
		setEditMode("edit");
		setEditing(row);
		form.resetFields();
		form.setFieldsValue({
			name: row.name,
			owner: row.owner,
			description: row.description,
			sourceDefinitionId: row.sourceDefinitionId,
			configJson: formatJson(row.config),
			enabled: row.enabled ?? true,
		});
		setEditOpen(true);
	};

	const applyDefaultConfig = (definitionId?: string) => {
		if (!definitionId) return;
		const current = String(form.getFieldValue("configJson") || "").trim();
		if (current) return;
		const def = sourceDefList.find((item) => item.sourceDefinitionId === definitionId);
		if (!def) return;
		form.setFieldsValue({ configJson: formatDefaultConfig(def) });
	};

	const buildPayload = (values: any) => {
		const config = parseJsonObject(values.configJson, "源端配置");
		return {
			name: String(values.name || "").trim(),
			owner: String(values.owner || "").trim() || undefined,
			description: String(values.description || "").trim() || undefined,
			sourceDefinitionId: String(values.sourceDefinitionId || "").trim(),
			sourceId: editing?.sourceId || undefined,
			config,
			enabled: values.enabled !== false,
		};
	};

	const submit = async () => {
		setSaving(true);
		try {
			const values = await form.validateFields(["name", "sourceDefinitionId", "configJson"]);
			const payload = buildPayload(values);
			if (editMode === "create") {
				await createAirbyteSource(payload);
				toast.success("数据源已创建");
			} else if (editing?.id) {
				await updateAirbyteSource(editing.id, payload);
				toast.success("数据源已更新");
			}
			setEditOpen(false);
			await loadAll();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	useEffect(() => {
		if (!editOpen || editMode !== "create") return;
		const currentId = String(form.getFieldValue("sourceDefinitionId") || "").trim();
		if (currentId) return;
		const firstDef = sourceDefList[0];
		if (!firstDef?.sourceDefinitionId) return;
		form.setFieldsValue({
			sourceDefinitionId: firstDef.sourceDefinitionId,
			configJson: formatDefaultConfig(firstDef),
		});
	}, [editOpen, editMode, form, sourceDefList]);

	const statusBadge = (row: AirbyteSource) => {
		const status = safeLower(row.status);
		if (status.includes("fail") || status.includes("error")) return <Badge status="error" text="异常" />;
		if (status.includes("success") || status.includes("succeed")) return <Badge status="success" text="正常" />;
		return <Badge status={row.enabled === false ? "default" : "processing"} text={row.enabled === false ? "停用" : "待检测"} />;
	};

	const listColumns: ColumnsType<AirbyteSource> = [
		{
			title: "数据源",
			dataIndex: "name",
			render: (text, row) => {
				const def = resolveDefinition(sourceDefs, row.sourceDefinitionId);
				return (
					<Space>
						<div className="rounded-md bg-slate-100 p-2">
							{renderDefinitionIcon(def, 18)}
						</div>
						<div>
							<Text strong>{text || "未命名"}</Text>
							<div className="text-xs text-slate-500">ID: {row.id || "-"}</div>
						</div>
					</Space>
				);
			},
		},
		{
			title: "类型",
			dataIndex: "sourceDefinitionId",
			render: (value) => renderDefinitionTag(resolveDefinition(sourceDefs, value)),
		},
		{
			title: "连接地址",
			render: (_, row) => extractEndpoint(row.config),
		},
		{
			title: "最近检测",
			dataIndex: "lastCheckedAt",
			render: (value) => formatDateTime(value as string),
		},
		{
			title: "状态",
			render: (_, row) => statusBadge(row),
		},
		{
			title: "操作",
			render: (_, row) => (
				<Space>
					<Button type="link" size="small" onClick={() => doCheck(row)} loading={rowCheckingId === row.id}>
						测试
					</Button>
					<Button type="link" size="small" onClick={() => doDiscover(row)} loading={rowDiscoveringId === row.id}>
						发现
					</Button>
					<Button type="link" size="small" onClick={() => openDetail(row)}>
						详情
					</Button>
					<Button type="link" size="small" onClick={() => openEdit(row)}>
						编辑
					</Button>
				</Space>
			),
		},
	];

	return (
		<div className="space-y-4">
			<div className="flex flex-wrap items-center justify-between gap-4">
				<div>
					<Title level={3} style={{ marginBottom: 4 }}>
						数据源管理
					</Title>
					<Text type="secondary">统一管理业务系统连接，系统自动完成接入与同步准备。</Text>
				</div>
				<Space>
					<Input
						prefix={<SearchOutlined />}
						placeholder="搜索名称、负责人..."
						value={search}
						onChange={(event) => setSearch(event.target.value)}
						style={{ width: 240 }}
					/>
					<Button icon={<ReloadOutlined />} onClick={loadAll}>
						刷新
					</Button>
					<Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
						新增数据源
					</Button>
					<Button
						icon={viewMode === "card" ? <AppstoreOutlined /> : <UnorderedListOutlined />}
						onClick={() => setViewMode(viewMode === "card" ? "list" : "card")}
					>
						{viewMode === "card" ? "卡片" : "列表"}
					</Button>
				</Space>
			</div>

			{viewMode === "card" ? (
				<Row gutter={[16, 16]}>
					{filteredSources.map((row) => (
						<Col key={row.id} xs={24} sm={12} lg={8}>
							<Card
								hoverable
								actions={[
									<Tooltip title="测试连接" key="check">
										<span onClick={() => doCheck(row)}>
											<ThunderboltOutlined />
										</span>
									</Tooltip>,
									<Tooltip title="发现 Schema" key="discover">
										<span onClick={() => doDiscover(row)}>
											<ReloadOutlined />
										</span>
									</Tooltip>,
									<Tooltip title="编辑" key="edit">
										<span onClick={() => openEdit(row)}>
											<EditOutlined />
										</span>
									</Tooltip>,
									<Tooltip title="详情" key="more">
										<span onClick={() => openDetail(row)}>
											<MoreOutlined />
										</span>
									</Tooltip>,
								]}
							>
								<div className="flex items-start justify-between">
									<Space>
										<div className="rounded-md bg-slate-100 p-2">
											{renderDefinitionIcon(resolveDefinition(sourceDefs, row.sourceDefinitionId), 20)}
										</div>
										<div>
											<Text strong>{row.name || "未命名"}</Text>
											<div className="text-xs text-slate-500">负责人：{row.owner || "-"}</div>
										</div>
									</Space>
									{statusBadge(row)}
								</div>
								<div className="mt-4 space-y-2 text-sm text-slate-600">
									<div>类型：{resolveDefinitionName(sourceDefs, row.sourceDefinitionId)}</div>
									<div>地址：{extractEndpoint(row.config)}</div>
									<div>最近检测：{formatDateTime(row.lastCheckedAt)}</div>
									<div>Schema 探查：{formatDateTime(row.lastDiscoveredAt)}</div>
								</div>
							</Card>
						</Col>
					))}
					{filteredSources.length === 0 && !loading ? (
						<Col span={24}>
							<Card>
								<EmptyState title="暂无数据源" description="点击“新增数据源”开始配置连接。" />
							</Card>
						</Col>
					) : null}
				</Row>
			) : (
				<Card>
					{filteredSources.length === 0 && !loading ? (
						<EmptyState title="暂无数据源" description="点击“新增数据源”开始配置连接。" />
					) : (
						<Table
							rowKey={(row) => row.id || row.name || Math.random().toString(36)}
							columns={listColumns}
							dataSource={filteredSources}
							loading={loading}
							pagination={{ pageSize: 8 }}
						/>
					)}
				</Card>
			)}

			<Modal
				open={editOpen}
				title={editMode === "create" ? "新增数据源" : "编辑数据源"}
				onCancel={() => setEditOpen(false)}
				onOk={submit}
				okText="保存"
				cancelText="取消"
				confirmLoading={saving}
				width={760}
			>
				<Form form={form} layout="vertical">
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="name" label="数据源名称" rules={[{ required: true, message: "请输入名称" }]}>
							<Input placeholder="erp_db_prod" />
						</Form.Item>
						<Form.Item name="owner" label="负责人">
							<Input placeholder="负责人姓名" />
						</Form.Item>
					</div>
					<Form.Item name="description" label="说明">
						<Input.TextArea rows={2} placeholder="可选：补充说明" />
					</Form.Item>
					<Form.Item
						name="sourceDefinitionId"
						label="数据源类型"
						rules={[{ required: true, message: "请选择数据源类型" }]}
					>
						<Select
							placeholder="选择数据源类型"
							loading={defLoading}
							notFoundContent={defLoading ? "加载中..." : "未获取到数据源类型"}
							options={sourceDefList.map((item) => ({
								label: (
									<Space size={8}>
										{renderDefinitionIcon(item, 16)}
										<span>{item.name || item.sourceDefinitionId}</span>
									</Space>
								),
								value: item.sourceDefinitionId,
							}))}
							onChange={(value) => applyDefaultConfig(String(value || ""))}
						/>
					</Form.Item>
					<Form.Item
						name="configJson"
						label="连接参数 (JSON)"
						rules={[{ required: true, message: "请填写连接参数" }]}
						extra="密码等敏感字段将脱敏显示，保持 ****** 表示不修改原值。"
					>
						<Input.TextArea rows={6} placeholder='{"host":"...","port":5432}' />
					</Form.Item>
					<Form.Item name="enabled" label="启用状态">
						<Select
							options={[
								{ label: "启用", value: true },
								{ label: "停用", value: false },
							]}
						/>
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
