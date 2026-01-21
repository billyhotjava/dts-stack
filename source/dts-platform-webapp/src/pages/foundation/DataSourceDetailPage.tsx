import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Badge,
	Breadcrumb,
	Button,
	Card,
	Descriptions,
	Empty,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Tabs,
	Typography,
} from "antd";
import { DatabaseOutlined, EditOutlined, ReloadOutlined, ThunderboltOutlined } from "@ant-design/icons";
import { useParams } from "react-router";
import { useRouter } from "@/routes/hooks";
import {
	checkAirbyteSource,
	discoverAirbyteSource,
	listAirbyteSourceDefinitions,
	listAirbyteSources,
	updateAirbyteSource,
} from "@/api/platformApi";
import mysqlIcon from "@/assets/connector-icons/mysql.svg";
import postgresIcon from "@/assets/connector-icons/postgresql.svg";
import oracleIcon from "@/assets/connector-icons/oracle.svg";
import mssqlIcon from "@/assets/connector-icons/mssql.svg";
import csvIcon from "@/assets/connector-icons/file-csv.svg";
import dmIcon from "@/assets/connector-icons/database.svg";
import excelIcon from "@/assets/icons/file-excel.svg";

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

export default function DataSourceDetailPage() {
	const { id } = useParams();
	const router = useRouter();
	const [loading, setLoading] = useState(false);
	const [defLoading, setDefLoading] = useState(false);
	const [source, setSource] = useState<AirbyteSource | null>(null);
	const [sourceDefs, setSourceDefs] = useState<Record<string, AirbyteDefinition>>({});
	const [sourceDefList, setSourceDefList] = useState<AirbyteDefinition[]>([]);
	const [editOpen, setEditOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [checking, setChecking] = useState(false);
	const [discovering, setDiscovering] = useState(false);
	const [form] = Form.useForm();

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

	const loadData = useCallback(async () => {
		if (!id) return;
		setLoading(true);
		try {
			const all = await listAirbyteSources();
			const list = Array.isArray(all) ? (all as AirbyteSource[]) : [];
			const hit = list.find((item) => item.id === id) || null;
			setSource(hit);
			await loadDefinitions();
		} catch (err: any) {
			toast.error(err?.message || "加载数据源失败");
		} finally {
			setLoading(false);
		}
	}, [id, loadDefinitions]);

	useEffect(() => {
		void loadData();
	}, [loadData]);

	const statusBadge = useMemo(() => {
		const status = safeLower(source?.status);
		if (status.includes("fail") || status.includes("error")) return <Badge status="error" text="异常" />;
		if (status.includes("success") || status.includes("succeed")) return <Badge status="success" text="正常" />;
		return <Badge status={source?.enabled === false ? "default" : "processing"} text={source?.enabled === false ? "停用" : "待检测"} />;
	}, [source?.status, source?.enabled]);

	const openEdit = () => {
		if (!source) return;
		form.resetFields();
		form.setFieldsValue({
			name: source.name,
			owner: source.owner,
			description: source.description,
			sourceDefinitionId: source.sourceDefinitionId,
			configJson: formatJson(source.config),
			enabled: source.enabled ?? true,
		});
		setEditOpen(true);
		if (sourceDefList.length === 0) {
			void loadDefinitions(true);
		}
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
			sourceId: source?.sourceId || undefined,
			config,
			enabled: values.enabled !== false,
		};
	};

	const submit = async () => {
		if (!source?.id) return;
		setSaving(true);
		try {
			const values = await form.validateFields(["name", "sourceDefinitionId", "configJson"]);
			const payload = buildPayload(values);
			await updateAirbyteSource(source.id, payload);
			toast.success("数据源已更新");
			setEditOpen(false);
			await loadData();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const doCheck = async () => {
		if (!source?.id) return;
		setChecking(true);
		try {
			const result = await checkAirbyteSource(source.id);
			const status = String(result?.status || "").toLowerCase();
			toast.success(status ? `测试完成：${status}` : "测试完成");
			await loadData();
		} catch (err: any) {
			toast.error(err?.message || "连接测试失败");
		} finally {
			setChecking(false);
		}
	};

	const doDiscover = async () => {
		if (!source?.id) return;
		setDiscovering(true);
		try {
			await discoverAirbyteSource(source.id);
			toast.success("已触发 Schema 探查");
			await loadData();
		} catch (err: any) {
			toast.error(err?.message || "Schema 探查失败");
		} finally {
			setDiscovering(false);
		}
	};

	if (!id) {
		return <Empty description="缺少数据源 ID" />;
	}

	if (!source && !loading) {
		return <Empty description="未找到数据源" />;
	}

	return (
		<div className="space-y-4">
			<Breadcrumb>
				<Breadcrumb.Item>
					<a onClick={() => router.push("/foundation/data-sources")}>数据源管理</a>
				</Breadcrumb.Item>
				<Breadcrumb.Item>{source?.name || "数据源详情"}</Breadcrumb.Item>
			</Breadcrumb>

			<Card>
				<div className="flex flex-wrap items-start justify-between gap-4">
					<div>
						<Space align="center" size={12}>
							<div className="rounded-md bg-slate-100 p-2">
								{renderDefinitionIcon(resolveDefinition(sourceDefs, source?.sourceDefinitionId), 20) || (
									<div className="h-5 w-5" />
								)}
							</div>
							<div>
								<Title level={4} style={{ marginBottom: 4 }}>
									{source?.name || "数据源详情"}
								</Title>
								<Text type="secondary">{resolveDefinitionName(sourceDefs, source?.sourceDefinitionId)}</Text>
							</div>
						</Space>
						<Space>
							{statusBadge}
							<Text type="secondary">最近检测：{formatDateTime(source?.lastCheckedAt)}</Text>
						</Space>
					</div>
					<Space>
						<Button icon={<ThunderboltOutlined />} loading={checking} onClick={doCheck}>
							测试连接
						</Button>
						<Button icon={<ReloadOutlined />} loading={discovering} onClick={doDiscover}>
							发现 Schema
						</Button>
						<Button icon={<EditOutlined />} onClick={openEdit}>
							编辑
						</Button>
						<Button onClick={() => router.push("/foundation/data-sources")}>返回列表</Button>
					</Space>
				</div>
			</Card>

			<Tabs
				items={[
					{
						key: "settings",
						label: "连接信息",
						children: (
							<div className="space-y-4">
								<Card title="基础信息">
									<Descriptions column={2} bordered size="small">
										<Descriptions.Item label="数据源类型">
											<Space size={8}>
												{renderDefinitionIcon(resolveDefinition(sourceDefs, source?.sourceDefinitionId), 14)}
												<span>{resolveDefinitionName(sourceDefs, source?.sourceDefinitionId)}</span>
											</Space>
										</Descriptions.Item>
										<Descriptions.Item label="连接地址">{extractEndpoint(source?.config)}</Descriptions.Item>
										<Descriptions.Item label="负责人">{source?.owner || "-"}</Descriptions.Item>
										<Descriptions.Item label="启用状态">
											{source?.enabled === false ? "停用" : "启用"}
										</Descriptions.Item>
										<Descriptions.Item label="SourceId" span={2}>
											{source?.sourceId || "-"}
										</Descriptions.Item>
										<Descriptions.Item label="说明" span={2}>
											{source?.description || "-"}
										</Descriptions.Item>
									</Descriptions>
								</Card>
								<Card title="连接参数 (JSON)" size="small">
									<pre className="text-xs text-slate-600 whitespace-pre-wrap">
										{formatJson(source?.config) || "-"}
									</pre>
								</Card>
							</div>
						),
					},
					{
						key: "audit",
						label: "同步记录",
						children: (
							<Card>
								<Descriptions column={2} bordered size="small">
									<Descriptions.Item label="最近检测">
										{formatDateTime(source?.lastCheckedAt)}
									</Descriptions.Item>
									<Descriptions.Item label="Schema 探查">
										{formatDateTime(source?.lastDiscoveredAt)}
									</Descriptions.Item>
								</Descriptions>
							</Card>
						),
					},
				]}
			/>

			<Modal
				open={editOpen}
				title="编辑数据源"
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
