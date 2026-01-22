import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Col,
	Form,
	Input,
	InputNumber,
	Modal,
	Row,
	Select,
	Space,
	Statistic,
	Table,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
	DatabaseOutlined,
	PlusOutlined,
	ReloadOutlined,
	SearchOutlined,
	ThunderboltOutlined,
	InfoCircleOutlined,
} from "@ant-design/icons";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import mysqlIcon from "@/assets/connector-icons/mysql.svg";
import postgresIcon from "@/assets/connector-icons/postgresql.svg";
import oracleIcon from "@/assets/connector-icons/oracle.svg";
import mssqlIcon from "@/assets/connector-icons/mssql.svg";
import csvIcon from "@/assets/connector-icons/file-csv.svg";
import dmIcon from "@/assets/connector-icons/dameng.ico";
import excelIcon from "@/assets/icons/file-excel.svg";
import {
	checkAirbyteSource,
	createAirbyteSource,
	deleteAirbyteSource,
	discoverAirbyteSource,
	listAirbyteSourceDefinitions,
	listAirbyteSources,
	updateAirbyteSource,
} from "@/api/platformApi";
import { useRouter } from "@/routes/hooks";

const { Text } = Typography;

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

type ConfigFieldType = "text" | "number" | "password" | "select";

type ConfigFieldOption = {
	label: string;
	value: string | number;
};

type ConfigField = {
	key: string;
	label: string;
	type?: ConfigFieldType;
	required?: boolean;
	placeholder?: string;
	options?: ConfigFieldOption[];
	span?: number;
	help?: string;
};

type ConfigProfile = {
	key: string;
	fields: ConfigField[];
	defaults: Record<string, any>;
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
	{ key: "dameng", matchers: ["dameng", "dm8", "达梦"], displayName: "Dameng" },
	{ key: "mssql", matchers: ["microsoft sql server (mssql)", "mssql", "sql server"], displayName: "Microsoft SQL Server" },
];

const CONFIG_FORMAT_OPTIONS: ConfigFieldOption[] = [
	{ label: "CSV", value: "csv" },
	{ label: "Excel", value: "excel" },
	{ label: "JSON", value: "json" },
	{ label: "Parquet", value: "parquet" },
	{ label: "Feather", value: "feather" },
];

const resolveDefinitionKey = (def?: AirbyteDefinition) => {
	const raw = `${def?.name || ""} ${def?.dockerRepository || ""} ${def?.sourceDefinitionId || ""}`.toLowerCase();
	if (raw.includes("postgres")) return "postgres";
	if (raw.includes("file") || raw.includes("csv") || raw.includes("excel")) return "file";
	if (raw.includes("mysql")) return "mysql";
	if (raw.includes("dameng") || raw.includes("dm") || raw.includes("dm8")) return "dameng";
	if (raw.includes("oracle")) return "oracle";
	if (raw.includes("sqlserver") || raw.includes("sql server") || raw.includes("mssql")) return "mssql";
	return "generic";
};

const CONFIG_PROFILES: Record<string, ConfigProfile> = {
	mysql: {
		key: "mysql",
		defaults: { host: "", port: 3306, database: "", username: "", password: "" },
		fields: [
			{ key: "host", label: "主机地址", required: true, placeholder: "db.internal" },
			{ key: "port", label: "端口", required: true, type: "number", placeholder: "3306" },
			{ key: "database", label: "数据库", required: true, placeholder: "erp" },
			{ key: "username", label: "用户名", required: true, placeholder: "readonly" },
			{
				key: "password",
				label: "密码",
				required: true,
				type: "password",
				placeholder: "保持 ****** 表示不修改原值",
				help: "密码等敏感字段将脱敏显示，保持 ****** 表示不修改原值。",
			},
		],
	},
	postgres: {
		key: "postgres",
		defaults: { host: "", port: 5432, database: "", username: "", password: "", schema: "" },
		fields: [
			{ key: "host", label: "主机地址", required: true, placeholder: "db.internal" },
			{ key: "port", label: "端口", required: true, type: "number", placeholder: "5432" },
			{ key: "database", label: "数据库", required: true, placeholder: "analytics" },
			{ key: "schema", label: "Schema (可选)", placeholder: "public" },
			{ key: "username", label: "用户名", required: true, placeholder: "readonly" },
			{
				key: "password",
				label: "密码",
				required: true,
				type: "password",
				placeholder: "保持 ****** 表示不修改原值",
				help: "密码等敏感字段将脱敏显示，保持 ****** 表示不修改原值。",
			},
		],
	},
	dameng: {
		key: "dameng",
		defaults: { host: "", port: 5236, database: "", username: "", password: "" },
		fields: [
			{ key: "host", label: "主机地址", required: true, placeholder: "db.internal" },
			{ key: "port", label: "端口", required: true, type: "number", placeholder: "5236" },
			{ key: "database", label: "数据库", required: true, placeholder: "default" },
			{ key: "username", label: "用户名", required: true, placeholder: "readonly" },
			{
				key: "password",
				label: "密码",
				required: true,
				type: "password",
				placeholder: "保持 ****** 表示不修改原值",
				help: "密码等敏感字段将脱敏显示，保持 ****** 表示不修改原值。",
			},
		],
	},
	mssql: {
		key: "mssql",
		defaults: { host: "", port: 1433, database: "", username: "", password: "" },
		fields: [
			{ key: "host", label: "主机地址", required: true, placeholder: "db.internal" },
			{ key: "port", label: "端口", required: true, type: "number", placeholder: "1433" },
			{ key: "database", label: "数据库", required: true, placeholder: "master" },
			{ key: "username", label: "用户名", required: true, placeholder: "readonly" },
			{
				key: "password",
				label: "密码",
				required: true,
				type: "password",
				placeholder: "保持 ****** 表示不修改原值",
				help: "密码等敏感字段将脱敏显示，保持 ****** 表示不修改原值。",
			},
		],
	},
	oracle: {
		key: "oracle",
		defaults: { host: "", port: 1521, service_name: "", sid: "", username: "", password: "" },
		fields: [
			{ key: "host", label: "主机地址", required: true, placeholder: "db.internal" },
			{ key: "port", label: "端口", required: true, type: "number", placeholder: "1521" },
			{ key: "service_name", label: "Service Name", required: true, placeholder: "ORCL" },
			{ key: "sid", label: "SID (可选)", placeholder: "ORCL" },
			{ key: "username", label: "用户名", required: true, placeholder: "readonly" },
			{
				key: "password",
				label: "密码",
				required: true,
				type: "password",
				placeholder: "保持 ****** 表示不修改原值",
				help: "密码等敏感字段将脱敏显示，保持 ****** 表示不修改原值。",
			},
		],
	},
	file: {
		key: "file",
		defaults: { dataset_name: "", format: "csv", url: "", sheet_name: "" },
		fields: [
			{ key: "dataset_name", label: "数据集名称", required: true, placeholder: "sales_2025" },
			{
				key: "format",
				label: "文件格式",
				required: true,
				type: "select",
				options: CONFIG_FORMAT_OPTIONS,
				placeholder: "选择文件格式",
			},
			{ key: "url", label: "文件地址", required: true, placeholder: "s3://bucket/path.csv", span: 2 },
			{ key: "sheet_name", label: "Sheet 名称 (可选)", placeholder: "Sheet1", span: 2 },
		],
	},
	generic: {
		key: "generic",
		defaults: {},
		fields: [],
	},
};

const resolveConfigProfile = (def?: AirbyteDefinition): ConfigProfile => {
	const key = resolveDefinitionKey(def);
	return CONFIG_PROFILES[key] || CONFIG_PROFILES.generic;
};

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

const normalizeConfigForForm = (config: Record<string, any> | null | undefined, profile: ConfigProfile) => {
	const base = config && typeof config === "object" ? { ...config } : {};
	for (const field of profile.fields) {
		if (field.type !== "number") continue;
		const value = base[field.key];
		if (value === null || value === undefined || value === "") continue;
		const parsed = Number(value);
		if (!Number.isNaN(parsed)) {
			base[field.key] = parsed;
		}
	}
	return base;
};

const hasConfigValue = (config: Record<string, any> | null | undefined) => {
	if (!config || typeof config !== "object") return false;
	return Object.values(config).some((value) => {
		if (value === null || value === undefined) return false;
		if (typeof value === "number") return true;
		return String(value).trim() !== "";
	});
};

const mergeConfigValues = (
	base: Record<string, any> | null | undefined,
	formConfig: Record<string, any> | null | undefined,
	extraConfig: Record<string, any> | null | undefined,
) => {
	const merged = {
		...(base || {}),
		...(formConfig || {}),
		...(extraConfig || {}),
	};
	const cleaned: Record<string, any> = {};
	for (const [key, value] of Object.entries(merged)) {
		if (value === undefined) continue;
		cleaned[key] = typeof value === "string" ? value.trim() : value;
	}
	return cleaned;
};

const renderConfigInput = (field: ConfigField) => {
	if (field.type === "number") {
		return (
			<InputNumber
				placeholder={field.placeholder}
				min={1}
				max={65535}
				style={{ width: "100%" }}
			/>
		);
	}
	if (field.type === "password") {
		return <Input.Password placeholder={field.placeholder} autoComplete="new-password" />;
	}
	if (field.type === "select") {
		return <Select options={field.options} placeholder={field.placeholder} />;
	}
	return <Input placeholder={field.placeholder} />;
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
	const profile = resolveConfigProfile(def);
	return profile.defaults || {};
};

const extractEndpoint = (config?: Record<string, any> | null) => {
	if (!config) return "-";
	const url = config.url || config.path || config.file;
	if (url) return String(url);
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
	const [editMode, setEditMode] = useState<"create" | "edit">("create");
	const [editing, setEditing] = useState<AirbyteSource | null>(null);
	const [editingConfig, setEditingConfig] = useState<Record<string, any> | null>(null);
	const [saving, setSaving] = useState(false);
	const [rowCheckingId, setRowCheckingId] = useState<string | null>(null);
	const [rowDiscoveringId, setRowDiscoveringId] = useState<string | null>(null);
	const [search, setSearch] = useState("");
	const [statusFilter, setStatusFilter] = useState("ALL");
	const [helpOpen, setHelpOpen] = useState(false);
	const [form] = Form.useForm();
	const router = useRouter();
	const selectedDefinitionId = Form.useWatch("sourceDefinitionId", form);
	const selectedDefinition = useMemo(
		() => resolveDefinition(sourceDefs, String(selectedDefinitionId || "")),
		[selectedDefinitionId, sourceDefs],
	);
	const configProfile = useMemo(() => resolveConfigProfile(selectedDefinition), [selectedDefinition]);

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

	const resolveStatusKey = useCallback((row: AirbyteSource) => {
		const status = safeLower(row.status);
		if (status.includes("fail") || status.includes("error")) return "UNHEALTHY";
		if (status.includes("success") || status.includes("succeed")) return "HEALTHY";
		if (row.enabled === false) return "DISABLED";
		return "UNKNOWN";
	}, []);

	const filteredSources = useMemo(() => {
		const keyword = search.trim().toLowerCase();
		return sources.filter((row) => {
			if (statusFilter !== "ALL" && resolveStatusKey(row) !== statusFilter) {
				return false;
			}
			if (!keyword) return true;
			const hay = `${row.name} ${row.owner} ${row.description} ${row.id}`.toLowerCase();
			return hay.includes(keyword);
		});
	}, [sources, search, statusFilter, resolveStatusKey]);

	const isRecent = (value?: string) => {
		if (!value) return false;
		const ts = Date.parse(value);
		if (Number.isNaN(ts)) return false;
		return Date.now() - ts < 7 * 24 * 60 * 60 * 1000;
	};

	const kpis = useMemo(() => {
		const total = sources.length;
		const healthy = sources.filter((row) => resolveStatusKey(row) === "HEALTHY").length;
		const unhealthy = sources.filter((row) => resolveStatusKey(row) === "UNHEALTHY").length;
		const recent = sources.filter((row) => isRecent(row.lastCheckedAt) || isRecent(row.lastDiscoveredAt)).length;
		return { total, healthy, unhealthy, recent };
	}, [sources, resolveStatusKey]);

	const openDetail = (row: AirbyteSource) => {
		if (!row?.id) return;
		router.push(`/foundation/data-sources/${row.id}`);
	};

	const confirmDelete = (row: AirbyteSource) => {
		if (!row?.id) return;
		Modal.confirm({
			title: "删除数据源",
			content: `确认删除「${row.name || "未命名"}」？该操作不可恢复。`,
			okText: "删除",
			cancelText: "取消",
			okButtonProps: { danger: true },
			onOk: async () => {
				try {
					await deleteAirbyteSource(row.id as string);
					toast.success("数据源已删除");
					await loadAll();
				} catch (err: any) {
					toast.error(err?.message || "删除失败");
				}
			},
		});
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

	const resetForm = useCallback(() => {
		setEditMode("create");
		setEditing(null);
		setEditingConfig(null);
		form.resetFields();
		const firstDef = sourceDefList[0];
		if (!firstDef?.sourceDefinitionId) {
			form.setFieldsValue({ enabled: true, configExtraJson: "" });
			return;
		}
		const profile = resolveConfigProfile(firstDef);
		form.setFieldsValue({
			enabled: true,
			sourceDefinitionId: firstDef.sourceDefinitionId,
			config: normalizeConfigForForm(defaultConfigForDefinition(firstDef), profile),
			configExtraJson: "",
		});
	}, [form, sourceDefList]);

	useEffect(() => {
		if (editMode !== "create") return;
		const currentId = String(form.getFieldValue("sourceDefinitionId") || "").trim();
		if (currentId) return;
		if (sourceDefList.length === 0) return;
		resetForm();
	}, [editMode, form, resetForm, sourceDefList.length]);

	const openEdit = (row: AirbyteSource) => {
		setEditMode("edit");
		setEditing(row);
		setEditingConfig(row.config || null);
		form.resetFields();
		const def = resolveDefinition(sourceDefs, row.sourceDefinitionId);
		const profile = resolveConfigProfile(def);
		form.setFieldsValue({
			name: row.name,
			owner: row.owner,
			description: row.description,
			sourceDefinitionId: row.sourceDefinitionId,
			config: normalizeConfigForForm(row.config, profile),
			configExtraJson: "",
			enabled: row.enabled ?? true,
		});
	};

	const applyDefaultConfig = (definitionId?: string) => {
		if (!definitionId) return;
		const current = form.getFieldValue("config") as Record<string, any> | undefined;
		if (hasConfigValue(current)) return;
		const def = sourceDefList.find((item) => item.sourceDefinitionId === definitionId);
		if (!def) return;
		const profile = resolveConfigProfile(def);
		form.setFieldsValue({ config: normalizeConfigForForm(defaultConfigForDefinition(def), profile) });
	};

	const buildPayload = (values: any) => {
		const extraConfig = parseJsonObject(values.configExtraJson, "高级参数");
		const definitionChanged =
			editing?.sourceDefinitionId &&
			values.sourceDefinitionId &&
			String(values.sourceDefinitionId) !== String(editing.sourceDefinitionId);
		const baseConfig = definitionChanged ? null : editingConfig;
		const config = mergeConfigValues(baseConfig, values.config, extraConfig || undefined);
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
			const values = await form.validateFields();
			const payload = buildPayload(values);
			if (editMode === "create") {
				await createAirbyteSource(payload);
				toast.success("数据源已创建");
				resetForm();
			} else if (editing?.id) {
				await updateAirbyteSource(editing.id, payload);
				toast.success("数据源已更新");
			}
			await loadAll();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const statusTag = (row: AirbyteSource) => {
		const key = resolveStatusKey(row);
		if (key === "HEALTHY") return <Tag color="green">健康</Tag>;
		if (key === "UNHEALTHY") return <Tag color="red">异常</Tag>;
		if (key === "DISABLED") return <Tag>停用</Tag>;
		return <Tag color="blue">待检测</Tag>;
	};

	const listColumns: ColumnsType<AirbyteSource> = [
		{
			title: "名称",
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
			title: "状态",
			render: (_, row) => statusTag(row),
		},
		{
			title: "负责人",
			dataIndex: "owner",
			render: (value) => value || "-",
		},
		{
			title: "最近测试",
			dataIndex: "lastCheckedAt",
			render: (value) => formatDateTime(value as string),
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
					<Button type="link" size="small" danger onClick={() => confirmDelete(row)}>
						删除
					</Button>
				</Space>
			),
		},
	];

	const statusOptions = [
		{ label: "全部", value: "ALL" },
		{ label: "健康", value: "HEALTHY" },
		{ label: "异常", value: "UNHEALTHY" },
		{ label: "停用", value: "DISABLED" },
		{ label: "待检测", value: "UNKNOWN" },
	];

	const handleTestForm = async () => {
		if (!editing?.id) {
			toast.error("请先保存数据源再测试连接");
			return;
		}
		await doCheck(editing);
	};

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据源管理"
				description="连接器工厂：动态连接器列表 + 配置表单创建数据源连接。"
				actions={
					<Space>
						<Button icon={<ReloadOutlined />} onClick={loadAll} loading={loading}>
							刷新
						</Button>
						<Button icon={<InfoCircleOutlined />} onClick={() => setHelpOpen(true)}>
							使用说明
						</Button>
						<Button type="primary" icon={<PlusOutlined />} onClick={resetForm}>
							新建数据源
						</Button>
					</Space>
				}
			/>

			<Alert
				type="info"
				showIcon
				message="通过连接器定义与连接参数配置，统一托管业务系统连接；敏感凭证由平台加密保存。"
			/>

			<Row gutter={[16, 16]}>
				<Col xs={12} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="已接入数据源" value={kpis.total} />
					</Card>
				</Col>
				<Col xs={12} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="健康（近7天）" value={kpis.healthy} />
					</Card>
				</Col>
				<Col xs={12} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="需处理（异常）" value={kpis.unhealthy} valueStyle={{ color: "#cf1322" }} />
					</Card>
				</Col>
				<Col xs={12} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="近期变更" value={kpis.recent} />
					</Card>
				</Col>
			</Row>

			<Row gutter={[16, 16]} align="top">
				<Col xs={24} xl={15}>
					<Card
						title="已配置数据源"
						extra={
							<Space>
								<Input
									prefix={<SearchOutlined />}
									placeholder="按名称 / 类型 / 负责人搜索"
									value={search}
									onChange={(event) => setSearch(event.target.value)}
									style={{ width: 220 }}
								/>
								<Select
									value={statusFilter}
									onChange={setStatusFilter}
									style={{ width: 140 }}
									options={statusOptions}
								/>
							</Space>
						}
					>
						{filteredSources.length === 0 && !loading ? (
							<EmptyState title="暂无数据源" description="点击“新建数据源”开始配置连接。" />
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
				</Col>
				<Col xs={24} xl={9}>
					<Card
						title={editMode === "create" ? "创建数据源" : "编辑数据源"}
						extra={
							editMode === "edit" ? (
								<Button size="small" onClick={resetForm}>
									切换为新建
								</Button>
							) : null
						}
					>
						<Form form={form} layout="vertical">
							<div className="grid gap-4 md:grid-cols-2">
								<Form.Item name="name" label="数据源名称" rules={[{ required: true, message: "请输入名称" }]}>
									<Input placeholder="例如：ERP-生产库" />
								</Form.Item>
								<Form.Item name="owner" label="负责人">
									<Input placeholder="例如：张三" />
								</Form.Item>
							</div>
							<Form.Item name="description" label="说明">
								<Input.TextArea rows={2} placeholder="补充说明（可选）" />
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
							<div className="space-y-2">
								<div className="grid gap-4 md:grid-cols-2">
									{configProfile.fields.map((field) => {
										const rules = field.required ? [{ required: true, message: `请填写${field.label}` }] : undefined;
										return (
											<Form.Item
												key={field.key}
												name={["config", field.key]}
												label={field.label}
												rules={rules}
												extra={field.help}
												className={field.span === 2 ? "md:col-span-2" : undefined}
											>
												{renderConfigInput(field)}
											</Form.Item>
										);
									})}
								</div>
								<Form.Item name="configExtraJson" label="高级参数 (JSON，可选)" extra="用于填写未覆盖的连接参数。">
									<Input.TextArea rows={3} placeholder='{"ssl": true}' />
								</Form.Item>
							</div>
							<Form.Item name="enabled" label="启用状态">
								<Select
									options={[
										{ label: "启用", value: true },
										{ label: "停用", value: false },
									]}
								/>
							</Form.Item>
							<div className="flex flex-wrap gap-2">
								<Button icon={<ThunderboltOutlined />} onClick={handleTestForm} disabled={saving}>
									连接测试
								</Button>
								<Button type="primary" onClick={submit} loading={saving}>
									{editMode === "create" ? "创建并保存" : "保存修改"}
								</Button>
							</div>
						</Form>
					</Card>
				</Col>
			</Row>

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
					<div>1. 先选择连接器类型，填写必要的连接参数并保存数据源。</div>
					<div>2. 保存后可进行连接测试与 Schema 探查。</div>
					<div>3. 数据源可被元数据采集与入湖任务复用。</div>
				</div>
			</Modal>
		</div>
	);
}
