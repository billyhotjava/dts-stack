import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Divider,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Switch,
	Table,
	Tag,
	Tabs,
	Typography,
	Upload,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import type { UploadFile } from "antd/es/upload/interface";
import type { HiveConnectionPersistRequest, HiveConnectionTestRequest, HiveConnectionTestResult } from "#/infra";
import {
	createInfraDataSource,
	deleteInfraDataSource,
	fetchInfraFeatures,
	listConnectionTestLogs,
	listInfraDataSources,
	publishInceptorDataSource,
	testJdbcConnection,
	testHiveConnection,
	updateInfraDataSource,
	type ConnectionTestLog,
	type InfraDataSource,
	type InfraFeatureFlags,
	type JdbcConnectionTestRequest,
	type UpsertInfraDataSourcePayload,
} from "@/api/services/infraService";
import { useAuthCheck } from "@/components/auth/use-auth";
import { useActiveDept } from "@/store/contextStore";

const { Text } = Typography;

const formatDateTime = (value?: string) => {
	if (!value) return "-";
	try {
		return new Date(value).toLocaleString();
	} catch {
		return value;
	}
};

const safeUpper = (value?: string) => String(value || "").trim().toUpperCase();
const ROLE_OPTIONS = [
	{ value: "SOURCE", label: "来源" },
	{ value: "TARGET", label: "目标" },
	{ value: "BOTH", label: "双向" },
];
const LAYER_OPTIONS = [
	{ value: "ODS", label: "ODS" },
	{ value: "DWD", label: "DWD" },
	{ value: "DWS", label: "DWS" },
	{ value: "ADS", label: "ADS" },
];

function tryParseJsonObject(raw: string | undefined): Record<string, any> | undefined {
	const text = String(raw || "").trim();
	if (!text) return undefined;
	try {
		const parsed = JSON.parse(text);
		if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) return parsed as Record<string, any>;
		return undefined;
	} catch {
		return undefined;
	}
}

function toStringMap(value: any): Record<string, string> | undefined {
	if (!value || typeof value !== "object" || Array.isArray(value)) return undefined;
	const out: Record<string, string> = {};
	Object.entries(value).forEach(([k, v]) => {
		if (!k) return;
		if (v == null) return;
		if (typeof v === "string" || typeof v === "number" || typeof v === "boolean") {
			out[k] = String(v);
		}
	});
	return Object.keys(out).length > 0 ? out : undefined;
}

function validateJsonObjectOptional(_: unknown, value: unknown) {
	const text = String(value ?? "").trim();
	if (!text) return Promise.resolve();
	try {
		const parsed = JSON.parse(text);
		if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
			return Promise.resolve();
		}
		return Promise.reject(new Error("必须是 JSON 对象"));
	} catch {
		return Promise.reject(new Error("JSON 格式不正确"));
	}
}

async function readFileAsText(file: File): Promise<string> {
	return new Promise((resolve, reject) => {
		const reader = new FileReader();
		reader.onerror = () => reject(new Error("read_failed"));
		reader.onload = () => resolve(String(reader.result || ""));
		reader.readAsText(file);
	});
}

async function readFileAsBase64(file: File): Promise<string> {
	return new Promise((resolve, reject) => {
		const reader = new FileReader();
		reader.onerror = () => reject(new Error("read_failed"));
		reader.onload = () => {
			const result = String(reader.result || "");
			// result is a data URL: data:...;base64,<content>
			const idx = result.indexOf(",");
			resolve(idx >= 0 ? result.slice(idx + 1) : result);
		};
		reader.readAsDataURL(file);
	});
}

export default function DataSourcesPage() {
	const { checkAny: checkAnyRole } = useAuthCheck("role");
	const activeDept = useActiveDept();
	const canInstituteManage = useMemo(
		() =>
			checkAnyRole(["ROLE_ADMIN", "ROLE_OP_ADMIN", "ROLE_INST_DATA_OWNER", "ROLE_INST_LEADER"]) ||
			checkAnyRole(["ADMIN", "OP_ADMIN", "INST_DATA_OWNER", "INST_LEADER"]),
		[checkAnyRole],
	);
	const normalizedActiveDept = useMemo(() => safeUpper(String(activeDept || "")), [activeDept]);

	const [loading, setLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [features, setFeatures] = useState<InfraFeatureFlags | null>(null);
	const [sources, setSources] = useState<InfraDataSource[]>([]);
	const [logs, setLogs] = useState<ConnectionTestLog[]>([]);

	const [editOpen, setEditOpen] = useState(false);
	const [editMode, setEditMode] = useState<"create" | "edit">("create");
	const [editing, setEditing] = useState<InfraDataSource | null>(null);
	const [form] = Form.useForm();
	const [jdbcTesting, setJdbcTesting] = useState(false);
	const [jdbcTestResult, setJdbcTestResult] = useState<HiveConnectionTestResult | null>(null);

	const [inceptorOpen, setInceptorOpen] = useState(false);
	const [inceptorForm] = Form.useForm();
	const [inceptorTestResult, setInceptorTestResult] = useState<HiveConnectionTestResult | null>(null);
	const [inceptorKrb5File, setInceptorKrb5File] = useState<UploadFile | null>(null);
	const [inceptorKeytabFile, setInceptorKeytabFile] = useState<UploadFile | null>(null);
	const [inceptorSubmitting, setInceptorSubmitting] = useState(false);
	const [inceptorTesting, setInceptorTesting] = useState(false);

	const loadAll = useCallback(async () => {
		setLoading(true);
		try {
			const [f, s, l] = await Promise.all([
				fetchInfraFeatures().catch(() => null as any),
				listInfraDataSources().catch(() => [] as any),
				listConnectionTestLogs().catch(() => [] as any),
			]);
			setFeatures(f || null);
			setSources(Array.isArray(s) ? (s as InfraDataSource[]) : []);
			setLogs(Array.isArray(l) ? (l as ConnectionTestLog[]) : []);
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void loadAll();
	}, [loadAll]);

	const openCreate = () => {
		if (!canInstituteManage && !normalizedActiveDept) {
			toast.error("请先选择部门范围，再新增部门数据源");
			return;
		}
		setEditMode("create");
		setEditing(null);
		setJdbcTestResult(null);
		form.resetFields();
		form.setFieldsValue({
			type: "JDBC",
			propsJson: "{}",
			secretsJson: "",
			testQuery: "SELECT 1",
			usageRole: undefined,
			warehouseLayers: [],
		});
		setEditOpen(true);
	};

	const openEdit = (row: InfraDataSource) => {
		const rowOwner = safeUpper(row.ownerDept);
		const canEditRow = canInstituteManage || (rowOwner && rowOwner === normalizedActiveDept);
		if (!canEditRow) {
			toast.error("无权限编辑该数据源（仅允许维护本部门数据源）");
			return;
		}
		setEditMode("edit");
		setEditing(row);
		setJdbcTestResult(null);
		form.resetFields();
		const rowProps = row.props || {};
		const driverClass = String((rowProps as any).driverClass || (rowProps as any).driver_class || "").trim() || undefined;
		form.setFieldsValue({
			name: row.name,
			type: row.type,
			jdbcUrl: row.jdbcUrl,
			username: row.username,
			description: row.description,
			driverClass,
			usageRole: rowProps?.usageRole || undefined,
			warehouseLayers: Array.isArray(rowProps?.warehouseLayers) ? rowProps.warehouseLayers : [],
			jdbcPropertiesJson: (rowProps as any).jdbcProperties ? JSON.stringify((rowProps as any).jdbcProperties, null, 2) : "",
			propsJson: row.props ? JSON.stringify(row.props, null, 2) : "{}",
			secretsJson: "",
			password: "",
			testQuery: "SELECT 1",
		});
		setEditOpen(true);
	};

	const getDefaultDriverClass = (type: string | undefined) => {
		const t = safeUpper(type);
		if (t === "DAMENG") return "dm.jdbc.driver.DmDriver";
		if (t === "POSTGRES" || t === "POSTGRESQL") return "org.postgresql.Driver";
		if (t === "MYSQL") return "com.mysql.cj.jdbc.Driver";
		return "";
	};

	const doTestJdbc = async () => {
		setJdbcTesting(true);
		try {
			const values = await form.validateFields(["jdbcUrl", "type"]);
			const propsFromJson = tryParseJsonObject(form.getFieldValue("propsJson")) || {};
			const jdbcPropsFromJson = tryParseJsonObject(form.getFieldValue("jdbcPropertiesJson"));
			const jdbcProperties =
				toStringMap(jdbcPropsFromJson) ||
				toStringMap((propsFromJson as any).jdbcProperties) ||
				undefined;

			const secretsFromJson = tryParseJsonObject(form.getFieldValue("secretsJson"));
			const password =
				String(form.getFieldValue("password") || "").trim() ||
				String((secretsFromJson as any)?.password || "").trim() ||
				undefined;

			const rawDriverClass = String(form.getFieldValue("driverClass") || "").trim();
			const driverClass =
				rawDriverClass ||
				String((propsFromJson as any).driverClass || (propsFromJson as any).driver_class || "").trim() ||
				getDefaultDriverClass(String(values.type || "")) ||
				undefined;

			const request: JdbcConnectionTestRequest = {
				jdbcUrl: String(values.jdbcUrl || "").trim(),
				driverClass,
				username: String(form.getFieldValue("username") || "").trim() || undefined,
				password,
				jdbcProperties,
				testQuery: String(form.getFieldValue("testQuery") || "").trim() || undefined,
				dataSourceId: editing?.id,
			};

			const result: any = await testJdbcConnection(request);
			setJdbcTestResult((result as HiveConnectionTestResult) ?? null);
			if (result?.success) {
				toast.success(`连接成功，用时 ${result?.elapsedMillis ?? "-"} ms`);
			} else {
				toast.error(result?.message || "连接失败");
			}
		} catch (error: any) {
			console.error(error);
			setJdbcTestResult(null);
			toast.error(error?.message ?? "连接测试失败");
		} finally {
			setJdbcTesting(false);
		}
	};

	const submitEdit = async () => {
		const values = await form.validateFields();
		if (!canInstituteManage && !normalizedActiveDept) {
			toast.error("缺少部门范围，请先选择部门范围");
			return;
		}

		const propsFromJson = tryParseJsonObject(values.propsJson) || {};
		const jdbcPropsFromJson = tryParseJsonObject(values.jdbcPropertiesJson);
		const mergedProps: Record<string, any> = { ...propsFromJson };
		if (String(values.driverClass || "").trim()) {
			mergedProps.driverClass = String(values.driverClass).trim();
		}
		if (values.usageRole) {
			mergedProps.usageRole = String(values.usageRole);
		} else {
			delete mergedProps.usageRole;
		}
		if (Array.isArray(values.warehouseLayers) && values.warehouseLayers.length) {
			mergedProps.warehouseLayers = values.warehouseLayers;
		} else {
			delete mergedProps.warehouseLayers;
		}
		if (jdbcPropsFromJson !== undefined) {
			mergedProps.jdbcProperties = jdbcPropsFromJson;
		}

		const secretsText = String(values.secretsJson || "").trim();
		const secretsFromJson = secretsText ? tryParseJsonObject(secretsText) : undefined;
		const passwordText = String(values.password || "").trim();
		let mergedSecrets: Record<string, any> | undefined = secretsFromJson;
		if (passwordText) {
			mergedSecrets = { ...(mergedSecrets || {}), password: passwordText };
		}
		// If user explicitly entered "{}", treat it as a clear operation (even if password empty)
		if (secretsText === "{}" && !passwordText) {
			mergedSecrets = {};
		}

		const payload: UpsertInfraDataSourcePayload = {
			name: String(values.name || "").trim(),
			type: String(values.type || "").trim(),
			jdbcUrl: String(values.jdbcUrl || "").trim(),
			username: String(values.username || "").trim() || undefined,
			description: String(values.description || "").trim() || undefined,
			props: mergedProps,
			secrets: mergedSecrets,
		};
		setSaving(true);
		try {
			if (editMode === "create") {
				await createInfraDataSource(payload);
				toast.success("数据源已创建");
			} else if (editing?.id) {
				await updateInfraDataSource(editing.id, payload);
				toast.success("数据源已更新");
			}
			setEditOpen(false);
			await loadAll();
		} catch (error: any) {
			console.error(error);
			toast.error(error?.message ?? "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const handleDelete = async (row: InfraDataSource) => {
		if (!row?.id) return;
		const rowOwner = safeUpper(row.ownerDept);
		const canDeleteRow = canInstituteManage || (rowOwner && rowOwner === normalizedActiveDept);
		if (!canDeleteRow) {
			toast.error("无权限删除该数据源（仅允许维护本部门数据源）");
			return;
		}
		const ok = window.confirm(`确定删除数据源“${row.name}”吗？该操作不可撤销。`);
		if (!ok) return;
		try {
			await deleteInfraDataSource(row.id);
			toast.success("已删除");
			await loadAll();
		} catch (error: any) {
			console.error(error);
			toast.error(error?.message ?? "删除失败");
		}
	};

	const inceptorDefaults = useMemo(() => {
		const defaultJdbcUrl = String(features?.defaultJdbcUrl || "").trim();
		const loginPrincipal = String(features?.loginPrincipal || "").trim();
		const database = String(features?.database || "default").trim() || "default";
		return {
			name: String(features?.dataSourceName || "Inceptor").trim() || "Inceptor",
			description: String(features?.description || "").trim() || undefined,
			servicePrincipal: "hive",
			host: "",
			port: 10000,
			database,
			useHttpTransport: false,
			httpPath: "",
			useSsl: false,
			useCustomJdbc: Boolean(defaultJdbcUrl),
			customJdbcUrl: defaultJdbcUrl || undefined,
			jdbcUrl: defaultJdbcUrl || "",
			loginPrincipal: loginPrincipal || "",
			authMethod: "KEYTAB",
			proxyUser: String(features?.proxyUser || "").trim() || undefined,
			testQuery: "SELECT 1",
		};
	}, [features]);

	const openInceptor = () => {
		if (!canInstituteManage) {
			toast.error("无权限配置主数据源");
			return;
		}
		setInceptorTestResult(null);
		setInceptorKrb5File(null);
		setInceptorKeytabFile(null);
		inceptorForm.resetFields();
		inceptorForm.setFieldsValue(inceptorDefaults);
		setInceptorOpen(true);
	};

	const buildInceptorTestPayload = async (): Promise<HiveConnectionTestRequest> => {
		const values = await inceptorForm.validateFields();
		let krb5Conf = String(values.krb5Conf || "").trim();
		let keytabBase64 = String(values.keytabBase64 || "").trim();
		let keytabFileName = String(values.keytabFileName || "").trim();

		const krbFile = (inceptorKrb5File?.originFileObj as File | undefined) || undefined;
		if (krbFile) {
			krb5Conf = (await readFileAsText(krbFile)).trim();
		}
		const ktFile = (inceptorKeytabFile?.originFileObj as File | undefined) || undefined;
		if (ktFile) {
			keytabBase64 = (await readFileAsBase64(ktFile)).trim();
			keytabFileName = ktFile.name;
		}

		return {
			jdbcUrl: String(values.jdbcUrl || "").trim(),
			loginPrincipal: String(values.loginPrincipal || "").trim(),
			krb5Conf: krb5Conf || undefined,
			authMethod: values.authMethod || "KEYTAB",
			keytabBase64: keytabBase64 || undefined,
			keytabFileName: keytabFileName || undefined,
			password: String(values.password || "").trim() || undefined,
			proxyUser: String(values.proxyUser || "").trim() || undefined,
			testQuery: String(values.testQuery || "").trim() || undefined,
			jdbcProperties: tryParseJsonObject(values.jdbcPropertiesJson) as any,
			remarks: String(values.remarks || "").trim() || undefined,
		};
	};

	const doTestInceptor = async () => {
		setInceptorTesting(true);
		try {
			const payload = await buildInceptorTestPayload();
			const result: any = await testHiveConnection(payload);
			setInceptorTestResult((result as HiveConnectionTestResult) ?? null);
			if (result?.success) {
				toast.success(`连接成功，用时 ${result?.elapsedMillis ?? "-"} ms`);
			} else {
				toast.error(result?.message || "连接失败");
			}
		} catch (error: any) {
			console.error(error);
			setInceptorTestResult(null);
			toast.error(error?.message ?? "连接测试失败");
		} finally {
			setInceptorTesting(false);
		}
	};

	const doPublishInceptor = async () => {
		setInceptorSubmitting(true);
		try {
			const values = await inceptorForm.validateFields();
			const testPayload = await buildInceptorTestPayload();
			const publishPayload: HiveConnectionPersistRequest = {
				...testPayload,
				name: String(values.name || "").trim(),
				description: String(values.description || "").trim() || undefined,
				servicePrincipal: String(values.servicePrincipal || "").trim(),
				host: String(values.host || "").trim(),
				port: Number(values.port || 0),
				database: String(values.database || "").trim(),
				useHttpTransport: Boolean(values.useHttpTransport),
				httpPath: String(values.httpPath || "").trim() || undefined,
				useSsl: Boolean(values.useSsl),
				useCustomJdbc: Boolean(values.useCustomJdbc),
				customJdbcUrl: String(values.customJdbcUrl || "").trim() || undefined,
			};
			await publishInceptorDataSource(publishPayload);
			toast.success("Inceptor 数据源已发布");
			setInceptorOpen(false);
			await loadAll();
		} catch (error: any) {
			console.error(error);
			toast.error(error?.message ?? "发布失败");
		} finally {
			setInceptorSubmitting(false);
		}
	};

	const columns: ColumnsType<InfraDataSource> = useMemo(
		() => [
			{ title: "名称", dataIndex: "name", key: "name", width: 180 },
			{ title: "所属部门", dataIndex: "ownerDept", key: "ownerDept", width: 140, render: (v) => v || "所级" },
			{
				title: "状态",
				key: "status",
				width: 120,
				render: (_: unknown, row) => {
					const s = safeUpper(row.status);
					const color = s === "ACTIVE" ? "green" : s ? "gold" : "default";
					return <Tag color={color}>{s || "-"}</Tag>;
				},
			},
			{
				title: "角色",
				key: "usageRole",
				width: 120,
				render: (_: unknown, row) => {
					const role = (row.props as any)?.usageRole;
					return role ? <Tag>{String(role)}</Tag> : "-";
				},
			},
			{
				title: "分层用途",
				key: "warehouseLayers",
				width: 160,
				render: (_: unknown, row) => {
					const layers = Array.isArray((row.props as any)?.warehouseLayers) ? (row.props as any).warehouseLayers : [];
					return layers.length ? layers.map((l: string) => <Tag key={l}>{l}</Tag>) : "-";
				},
			},
			{ title: "类型", dataIndex: "type", key: "type", width: 140, render: (v) => v || "-" },
			{
				title: "连接串",
				dataIndex: "jdbcUrl",
				key: "jdbcUrl",
				ellipsis: true,
				render: (v) => <Text code>{String(v || "-")}</Text>,
			},
			{
				title: "密钥",
				key: "secrets",
				width: 110,
				render: (_: unknown, row) => <Tag>{row.hasSecrets ? "已加密" : "未配置"}</Tag>,
			},
			{ title: "最近校验", dataIndex: "lastVerifiedAt", key: "lastVerifiedAt", width: 180, render: (v) => formatDateTime(v) },
			{
				title: "操作",
				key: "op",
				width: 220,
				render: (_: unknown, row) => {
					const isInceptor = safeUpper(row.type) === "INCEPTOR";
					const rowOwner = safeUpper(row.ownerDept);
					const canEditRow = canInstituteManage || (rowOwner && rowOwner === normalizedActiveDept);
					return (
						<Space>
							<Button size="small" disabled={!canEditRow} onClick={() => (isInceptor ? openInceptor() : openEdit(row))}>
								{isInceptor ? "配置主数据源" : "编辑"}
							</Button>
							<Button size="small" danger disabled={isInceptor || !canEditRow} onClick={() => handleDelete(row)}>
								删除
							</Button>
						</Space>
					);
				},
			},
		],
		[canInstituteManage, normalizedActiveDept],
	);

	const logColumns: ColumnsType<ConnectionTestLog> = useMemo(
		() => [
			{ title: "结果", key: "result", width: 120, render: (_: unknown, row) => <Tag color={row.result === "SUCCESS" ? "green" : "red"}>{row.result}</Tag> },
			{ title: "耗时(ms)", dataIndex: "elapsedMs", key: "elapsedMs", width: 120, render: (v) => (v == null ? "-" : String(v)) },
			{ title: "信息", dataIndex: "message", key: "message", ellipsis: true, render: (v) => v || "-" },
			{ title: "时间", dataIndex: "createdAt", key: "createdAt", width: 200, render: (v) => formatDateTime(v) },
		],
		[],
	);

	const hasInceptor = Boolean(features?.hasActiveInceptor);
	const inceptorBadge = hasInceptor ? <Tag color="green">已就绪</Tag> : <Tag color="gold">未配置</Tag>;

	return (
		<div className="space-y-4">
			<Card
				title="数据源与数据连接"
				extra={
					<Space>
						<Button onClick={loadAll} loading={loading}>
							刷新
						</Button>
						<Button type="primary" onClick={openCreate}>
							新增数据源
						</Button>
					</Space>
				}
			>
				<div className="space-y-2">
					<div className="flex flex-wrap items-center gap-2">
						<Tag>{features?.multiSourceEnabled ? "多数据源：已启用" : "多数据源：未启用"}</Tag>
						<Tag>{features?.syncInProgress ? "目录同步：进行中" : "目录同步：空闲"}</Tag>
					</div>
					<div className="text-sm text-muted-foreground">
						主数据源（Inceptor）：{inceptorBadge}；默认 JDBC：{features?.defaultJdbcUrl ? <Text code>{features.defaultJdbcUrl}</Text> : "-"}
					</div>
				</div>
			</Card>

			<Card
				title="主数据源（Inceptor）"
				extra={
					<Space>
						<Button onClick={openInceptor} disabled={!canInstituteManage}>
							配置 / 发布
						</Button>
					</Space>
				}
			>
				<div className="space-y-1 text-sm text-muted-foreground">
					<div>登录主体：{features?.loginPrincipal || "-"}</div>
					<div>默认库：{features?.database || "-"}</div>
					<div>Proxy User：{features?.proxyUser || "-"}</div>
					<div>最近连通耗时：{features?.lastTestElapsedMillis != null ? `${features.lastTestElapsedMillis} ms` : "-"}</div>
				</div>
			</Card>

			<Card title="已登记数据源">
				<Table
					rowKey={(row) => row.id}
					columns={columns}
					dataSource={sources}
					loading={loading}
					pagination={{ pageSize: 10, showSizeChanger: true }}
				/>
			</Card>

			<Card title="最近连接测试日志">
				<Table
					rowKey={(row) => row.id}
					columns={logColumns}
					dataSource={logs}
					loading={loading}
					pagination={{ pageSize: 10, showSizeChanger: true }}
				/>
			</Card>

			<Modal
				open={editOpen}
				title={editMode === "create" ? "新增数据源" : "编辑数据源"}
				onCancel={() => setEditOpen(false)}
				footer={
					<Space>
						<Button onClick={() => setEditOpen(false)}>取消</Button>
						<Button onClick={doTestJdbc} loading={jdbcTesting}>
							测试连接
						</Button>
						<Button type="primary" onClick={submitEdit} loading={saving}>
							保存
						</Button>
					</Space>
				}
			>
				<Form form={form} layout="vertical">
					<Form.Item name="name" label="数据源名称" rules={[{ required: true, message: "请输入数据源名称" }]}>
						<Input />
					</Form.Item>
					<Form.Item name="type" label="数据源类型" rules={[{ required: true, message: "请输入数据源类型" }]}>
						<Select
							options={[
								{ label: "JDBC", value: "JDBC" },
								{ label: "POSTGRES", value: "POSTGRES" },
								{ label: "DAMENG", value: "DAMENG" },
								{ label: "MYSQL", value: "MYSQL" },
								{ label: "CUSTOM", value: "CUSTOM" },
							]}
							showSearch
							allowClear
						/>
					</Form.Item>
					<Form.Item name="usageRole" label="数据源角色（可选）">
						<Select options={ROLE_OPTIONS} allowClear placeholder="SOURCE / TARGET / BOTH" />
					</Form.Item>
					<Form.Item name="warehouseLayers" label="数仓分层用途（可选）">
						<Select options={LAYER_OPTIONS} mode="multiple" allowClear placeholder="ODS / DWD / DWS / ADS" />
					</Form.Item>
					<Form.Item name="jdbcUrl" label="JDBC / 连接地址" rules={[{ required: true, message: "请输入连接串" }]}>
						<Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} placeholder="jdbc:..." />
					</Form.Item>
					<Form.Item name="username" label="用户名（可选）">
						<Input />
					</Form.Item>
					<Form.Item name="password" label="密码（可选）">
						<Input.Password autoComplete="new-password" />
					</Form.Item>
					<Form.Item shouldUpdate noStyle>
						{() => {
							const placeholder = getDefaultDriverClass(String(form.getFieldValue("type") || "")) || "com.vendor.Driver";
							return (
								<Form.Item name="driverClass" label="Driver Class（可选）">
									<Input placeholder={placeholder} />
								</Form.Item>
							);
						}}
					</Form.Item>
					<Form.Item name="testQuery" label="测试 SQL（可选，仅用于“测试连接”）">
						<Input placeholder="SELECT 1" />
					</Form.Item>
					<Form.Item name="description" label="描述（可选）">
						<Input />
					</Form.Item>

					{jdbcTestResult && (
						<Alert
							type={jdbcTestResult.success ? "success" : "error"}
							showIcon
							message={jdbcTestResult.success ? "连接成功" : "连接失败"}
							description={
								<div className="space-y-1">
									<div>{jdbcTestResult.message}</div>
									<div>耗时：{jdbcTestResult.elapsedMillis} ms</div>
									{Array.isArray(jdbcTestResult.warnings) && jdbcTestResult.warnings.length > 0 && (
										<div>警告：{jdbcTestResult.warnings.join("；")}</div>
									)}
								</div>
							}
						/>
					)}

					<Divider />
					<Form.Item name="jdbcPropertiesJson" label="连接属性 jdbcProperties（JSON，可选）" rules={[{ validator: validateJsonObjectOptional }]}>
						<Input.TextArea
							autoSize={{ minRows: 3, maxRows: 8 }}
							placeholder={`{\n  "useUnicode": "true",\n  "characterEncoding": "UTF-8"\n}`}
						/>
					</Form.Item>
					<Form.Item name="propsJson" label="连接参数 props（JSON，可选，高级）" rules={[{ validator: validateJsonObjectOptional }]}>
						<Input.TextArea autoSize={{ minRows: 3, maxRows: 8 }} placeholder={`{\n  "catalog": \"...\"\n}`} />
					</Form.Item>
					<Form.Item name="secretsJson" label="密钥 secrets（JSON，可选，高级）" rules={[{ validator: validateJsonObjectOptional }]}>
						<Input.TextArea
							autoSize={{ minRows: 3, maxRows: 8 }}
							placeholder={`{\n  \"password\": \"******\"\n}\n\n# 留空：不修改已有密钥\n# 输入 {}：清空密钥`}
						/>
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={inceptorOpen}
				title="配置并发布 Inceptor 主数据源"
				onCancel={() => setInceptorOpen(false)}
				footer={
					<Space>
						<Button onClick={() => setInceptorOpen(false)}>取消</Button>
						<Button onClick={doTestInceptor} loading={inceptorTesting}>
							测试连接
						</Button>
						<Button type="primary" onClick={doPublishInceptor} loading={inceptorSubmitting}>
							发布 / 覆盖
						</Button>
					</Space>
				}
				width={820}
			>
				<Tabs
					items={[
						{
							key: "base",
							label: "基础配置",
							children: (
								<Form form={inceptorForm} layout="vertical">
									<div className="grid gap-4 md:grid-cols-2">
										<Form.Item name="name" label="数据源名称" rules={[{ required: true, message: "请输入名称" }]}>
											<Input />
										</Form.Item>
										<Form.Item name="description" label="描述（可选）">
											<Input />
										</Form.Item>
										<Form.Item name="host" label="Host" rules={[{ required: true, message: "请输入主机" }]}>
											<Input placeholder="inceptor-host" />
										</Form.Item>
										<Form.Item name="port" label="端口" rules={[{ required: true, message: "请输入端口" }]}>
											<Input type="number" />
										</Form.Item>
										<Form.Item
											name="servicePrincipal"
											label="Service Principal"
											rules={[{ required: true, message: "请输入 service principal" }]}
										>
											<Input placeholder="hive" />
										</Form.Item>
										<Form.Item name="database" label="默认库" rules={[{ required: true, message: "请输入默认库" }]}>
											<Input placeholder="default" />
										</Form.Item>
										<Form.Item name="useHttpTransport" label="HTTP 模式" valuePropName="checked">
											<Switch />
										</Form.Item>
										<Form.Item name="useSsl" label="SSL" valuePropName="checked">
											<Switch />
										</Form.Item>
										<Form.Item name="httpPath" label="HTTP Path（可选）">
											<Input placeholder="/cliservice" />
										</Form.Item>
										<Form.Item name="proxyUser" label="Proxy User（可选）">
											<Input />
										</Form.Item>
									</div>

									<Divider />
									<Form.Item name="useCustomJdbc" label="自定义 JDBC" valuePropName="checked">
										<Switch />
									</Form.Item>
									<Form.Item name="jdbcUrl" label="JDBC URL（用于测试）" rules={[{ required: true, message: "请输入 JDBC URL" }]}>
										<Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} placeholder="jdbc:hive2://..." />
									</Form.Item>
									<Form.Item name="customJdbcUrl" label="自定义 JDBC URL（可选，发布后用于覆盖默认）">
										<Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} placeholder="jdbc:hive2://..." />
									</Form.Item>
								</Form>
							),
						},
						{
							key: "auth",
							label: "认证与测试",
							children: (
								<div className="space-y-3">
									<Form form={inceptorForm} layout="vertical">
										<Form.Item name="loginPrincipal" label="登录用户" rules={[{ required: true, message: "请输入登录用户" }]}>
											<Input placeholder="Kerberos: user@REALM；或 JDBC: username" />
										</Form.Item>
										<div className="grid gap-4 md:grid-cols-2">
											<Form.Item name="authMethod" label="认证方式" initialValue="KEYTAB" rules={[{ required: true }]}>
												<Select
													options={[
														{ label: "KEYTAB（Kerberos）", value: "KEYTAB" },
														{ label: "PASSWORD（Kerberos）", value: "PASSWORD" },
														{ label: "JDBC_PASSWORD（用户名密码）", value: "JDBC_PASSWORD" },
													]}
												/>
											</Form.Item>
											<Form.Item name="testQuery" label="测试 SQL（可选）">
												<Input placeholder="SELECT 1" />
											</Form.Item>
										</div>

										<Form.Item shouldUpdate noStyle>
											{() => {
												const method = String(inceptorForm.getFieldValue("authMethod") || "KEYTAB").toUpperCase();
												if (method === "JDBC_PASSWORD") {
													return null;
												}
												return (
													<Form.Item label="krb5.conf（Kerberos 必填）" required>
														<Space direction="vertical" style={{ width: "100%" }}>
															<Upload
																maxCount={1}
																fileList={inceptorKrb5File ? [inceptorKrb5File] : []}
																beforeUpload={(file) => {
																	setInceptorKrb5File(file as any);
																	return false;
																}}
																onRemove={() => {
																	setInceptorKrb5File(null);
																}}
															>
																<Button>选择 krb5.conf 文件</Button>
															</Upload>
															<Form.Item name="krb5Conf" noStyle>
																<Input.TextArea autoSize={{ minRows: 3, maxRows: 8 }} placeholder="也可直接粘贴 krb5.conf 内容" />
															</Form.Item>
														</Space>
													</Form.Item>
												);
											}}
										</Form.Item>

										<Form.Item shouldUpdate noStyle>
											{() => {
												const method = String(inceptorForm.getFieldValue("authMethod") || "KEYTAB").toUpperCase();
												if (method === "PASSWORD") {
													return (
														<Form.Item name="password" label="Kerberos 密码" rules={[{ required: true, message: "请输入密码" }]}>
															<Input.Password />
														</Form.Item>
													);
												}
												if (method === "JDBC_PASSWORD") {
													return (
														<Form.Item name="password" label="密码" rules={[{ required: true, message: "请输入密码" }]}>
															<Input.Password autoComplete="new-password" />
														</Form.Item>
													);
												}
												return (
													<Form.Item label="Keytab（KEYTAB 模式必填）" required>
														<Space direction="vertical" style={{ width: "100%" }}>
															<Upload
																maxCount={1}
																fileList={inceptorKeytabFile ? [inceptorKeytabFile] : []}
																beforeUpload={(file) => {
																	setInceptorKeytabFile(file as any);
																	inceptorForm.setFieldValue("keytabFileName", file.name);
																	return false;
																}}
																onRemove={() => {
																	setInceptorKeytabFile(null);
																	inceptorForm.setFieldValue("keytabFileName", "");
																}}
															>
																<Button>选择 keytab 文件</Button>
															</Upload>
															<Form.Item name="keytabFileName" label="Keytab 文件名（自动）">
																<Input disabled />
															</Form.Item>
															<Form.Item name="keytabBase64" label="Keytab Base64（可选，优先使用文件）">
																<Input.TextArea autoSize={{ minRows: 2, maxRows: 6 }} placeholder="base64..." />
															</Form.Item>
														</Space>
													</Form.Item>
												);
											}}
											</Form.Item>

											<Form.Item name="jdbcPropertiesJson" label="JDBC Properties（JSON，可选）">
												<Input.TextArea autoSize={{ minRows: 2, maxRows: 6 }} placeholder={`{\n  "hive.exec.dynamic.partition": "true"\n}`} />
											</Form.Item>
										<Form.Item name="remarks" label="备注（可选）">
											<Input />
										</Form.Item>
									</Form>

									{inceptorTestResult && (
										<Alert
											type={inceptorTestResult.success ? "success" : "error"}
											showIcon
											message={inceptorTestResult.success ? "连接成功" : "连接失败"}
											description={
												<div className="space-y-1">
													<div>{inceptorTestResult.message}</div>
													<div>耗时：{inceptorTestResult.elapsedMillis} ms</div>
													{Array.isArray(inceptorTestResult.warnings) && inceptorTestResult.warnings.length > 0 && (
														<div>警告：{inceptorTestResult.warnings.join("；")}</div>
													)}
												</div>
											}
										/>
									)}
								</div>
							),
						},
					]}
				/>
			</Modal>
		</div>
	);
}
