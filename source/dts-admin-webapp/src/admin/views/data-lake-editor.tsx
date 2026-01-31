import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
	Alert,
	Button as AntButton,
	Collapse,
	Divider,
	Form,
	Input,
	InputNumber,
	Select,
	Space,
	Switch,
	Upload,
} from "antd";
import { adminApi } from "@/admin/api/adminApi";
import type {
	HiveAuthMethod,
	HiveConnectionPersistRequest,
	HiveConnectionTestRequest,
	HiveConnectionTestResult,
	JdbcDriverInfo,
	UpsertInfraDataSourcePayload,
} from "@/types/infra";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text } from "@/ui/typography";
import { toast } from "sonner";

type FormValues = {
	name?: string;
	type?: string;
	description?: string;
	defaulted?: boolean;
	jdbcUrl?: string;
	username?: string;
	password?: string;
	driverVersion?: string;
	driverClass?: string;
	testQuery?: string;
	jdbcPropertiesRaw?: string;
	destinationDefinitionId?: string;
	destinationName?: string;
	destinationConfigRaw?: string;
	loginPrincipal?: string;
	authMethod?: HiveAuthMethod | string;
	krb5Conf?: string;
	keytabBase64?: string;
	keytabFileName?: string;
	proxyUser?: string;
	servicePrincipal?: string;
	host?: string;
	port?: number;
	database?: string;
	useHttpTransport?: boolean;
	httpPath?: string;
	useSsl?: boolean;
	useCustomJdbc?: boolean;
	customJdbcUrl?: string;
};

const TYPE_OPTIONS = [
	{ value: "HIVE", label: "Hive" },
	{ value: "INCEPTOR", label: "Inceptor" },
	{ value: "JDBC", label: "JDBC" },
	{ value: "ICEBERG", label: "Iceberg" },
	{ value: "CLICKHOUSE", label: "ClickHouse" },
	{ value: "POSTGRESQL", label: "PostgreSQL" },
];

const AUTH_METHOD_OPTIONS = [
	{ value: "KEYTAB", label: "Kerberos Keytab" },
	{ value: "PASSWORD", label: "Kerberos 密码" },
	{ value: "JDBC_PASSWORD", label: "JDBC 用户名密码" },
];

const ADDAX_WRITER_OPTIONS = [
	{ value: "rdbmswriter", label: "rdbmswriter（通用 JDBC）" },
	{ value: "mysqlwriter", label: "mysqlwriter" },
	{ value: "postgresqlwriter", label: "postgresqlwriter" },
	{ value: "oraclewriter", label: "oraclewriter" },
	{ value: "sqlserverwriter", label: "sqlserverwriter" },
	{ value: "dmwriter", label: "dmwriter（达梦）" },
	{ value: "clickhousewriter", label: "clickhousewriter" },
	{ value: "hivewriter", label: "hivewriter" },
	{ value: "hdfswriter", label: "hdfswriter" },
	{ value: "hbase11xwriter", label: "hbase11xwriter" },
	{ value: "hbase20xwriter", label: "hbase20xwriter" },
	{ value: "eswriter", label: "eswriter" },
	{ value: "mongodbwriter", label: "mongodbwriter" },
	{ value: "rediswriter", label: "rediswriter" },
	{ value: "cassandrawriter", label: "cassandrawriter" },
	{ value: "ftpwriter", label: "ftpwriter" },
	{ value: "txtfilewriter", label: "txtfilewriter" },
	{ value: "streamwriter", label: "streamwriter" },
	{ value: "odpswriter", label: "odpswriter" },
	{ value: "adxwriter", label: "adxwriter" },
	{ value: "customwriter", label: "customwriter" },
];

const LAKE_TYPE_SET = new Set(TYPE_OPTIONS.map((item) => item.value));
const TABLE_PLACEHOLDER = "${table}";

const DRIVER_TYPE_HINTS: Record<string, string[]> = {
	HIVE: ["hive"],
	INCEPTOR: ["inceptor", "hive"],
	ICEBERG: ["iceberg"],
	CLICKHOUSE: ["clickhouse"],
	POSTGRESQL: ["postgres", "postgresql", "pgjdbc", "pg"],
};


const normalizeLakeType = (value?: string) => {
	const upper = String(value || "").trim().toUpperCase();
	if (LAKE_TYPE_SET.has(upper)) {
		return upper;
	}
	return "JDBC";
};

const buildDriverOptions = (drivers: JdbcDriverInfo[], type?: string) => {
	const options = drivers.map((driver: JdbcDriverInfo) => ({
		value: driver.version && driver.version.trim() ? driver.version : driver.fileName,
		label: driver.label || driver.fileName,
		meta: `${driver.fileName} ${driver.label || ""}`.toLowerCase(),
	}));
	const normalizedType = normalizeLakeType(type);
	const keywords = DRIVER_TYPE_HINTS[normalizedType] || [];
	if (!keywords.length) {
		return options;
	}
	const filtered = options.filter((option) =>
		keywords.some((keyword) => option.meta.includes(keyword.toLowerCase())),
	);
	return filtered.length > 0 ? filtered : options;
};


const readFileAsText = (file: File) =>
	new Promise<string>((resolve, reject) => {
		const reader = new FileReader();
		reader.onload = () => resolve(String(reader.result || ""));
		reader.onerror = () => reject(reader.error || new Error("读取文件失败"));
		reader.readAsText(file);
	});

const arrayBufferToBase64 = (buffer: ArrayBuffer) => {
	let binary = "";
	const bytes = new Uint8Array(buffer);
	const chunkSize = 0x8000;
	for (let i = 0; i < bytes.length; i += chunkSize) {
		binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize));
	}
	return btoa(binary);
};

const readFileAsBase64 = (file: File) =>
	new Promise<string>((resolve, reject) => {
		const reader = new FileReader();
		reader.onload = () => {
			try {
				const result = reader.result;
				if (result instanceof ArrayBuffer) {
					resolve(arrayBufferToBase64(result));
				} else {
					resolve("");
				}
			} catch (error) {
				reject(error);
			}
		};
		reader.onerror = () => reject(reader.error || new Error("读取文件失败"));
		reader.readAsArrayBuffer(file);
	});

const buildPropertiesRaw = (props?: Record<string, string>): string => {
	if (!props) return "";
	return Object.entries(props)
		.map(([key, value]) => `${key}=${value ?? ""}`)
		.join("\n");
};

const parseProperties = (raw?: string): Record<string, string> => {
	const output: Record<string, string> = {};
	if (!raw) return output;
	raw
		.split("\n")
		.map((line) => line.trim())
		.filter(Boolean)
		.forEach((line) => {
			const [key, ...rest] = line.split("=");
			const trimmedKey = key.trim();
			if (!trimmedKey) return;
			output[trimmedKey] = rest.join("=").trim();
		});
	return output;
};

const formatJson = (value?: Record<string, any> | null): string => {
	if (!value || typeof value !== "object") return "";
	if (!Object.keys(value).length) return "";
	try {
		return JSON.stringify(value, null, 2);
	} catch {
		return "";
	}
};

const parseJsonInput = (raw?: string): Record<string, any> | undefined => {
	if (!raw || !raw.trim()) return undefined;
	return JSON.parse(raw);
};

const buildWriterTemplate = (
	writerType: string | undefined,
	values: Pick<FormValues, "jdbcUrl" | "username" | "password">,
): Record<string, any> => {
	const jdbcUrl = values.jdbcUrl?.trim() || "jdbc:your_database_url";
	const username = values.username?.trim() || "your_username";
	const password = values.password || "your_password";
	const normalizedType = String(writerType || "").toLowerCase();
	const base = {
		username,
		password,
		connection: [
			{
				jdbcUrl: [jdbcUrl],
				table: [TABLE_PLACEHOLDER],
			},
		],
		column: ["*"],
	};
	if (normalizedType.includes("hive") || normalizedType.includes("iceberg")) {
		return {
			...base,
			fileType: "text",
			writeMode: "append",
			fieldDelimiter: "\u0001",
			nullFormat: "\\N",
		};
	}
	return {
		...base,
		writeMode: "insert",
	};
};


export default function DataLakeEditorView() {
	const queryClient = useQueryClient();
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();
	const { id } = useParams();
	const readOnly = searchParams.get("view") === "detail";
	const [form] = Form.useForm<FormValues>();
	const [initialized, setInitialized] = useState(false);
	const [testResult, setTestResult] = useState<HiveConnectionTestResult | null>(null);
	const [saving, setSaving] = useState(false);
	const [testing, setTesting] = useState(false);
	const [krb5FileName, setKrb5FileName] = useState<string>("");

	const { data: dataLakes = [], isFetching: dataLakesLoading } = useQuery({
		queryKey: ["admin", "data-lakes"],
		queryFn: adminApi.listDataLakes,
	});

	const { data: jdbcDrivers = [], isFetching: jdbcDriversLoading } = useQuery({
		queryKey: ["admin", "data-lake-jdbc-drivers"],
		queryFn: adminApi.getDataLakeJdbcDrivers,
	});

	const editingLake = useMemo(
		() => dataLakes.find((lake) => lake.id && lake.id === id),
		[dataLakes, id],
	);
	const isEditing = Boolean(editingLake);
	const editingType = normalizeLakeType(editingLake?.type);

	const { data: inceptorConfig } = useQuery({
		queryKey: ["admin", "inceptor-config"],
		queryFn: adminApi.getInceptorConfig,
		enabled: editingType === "INCEPTOR",
	});

	const type = Form.useWatch("type", form);
	const defaulted = Form.useWatch("defaulted", form);
	const authMethod = Form.useWatch("authMethod", form);
	const useHttpTransport = Form.useWatch("useHttpTransport", form);
	const useCustomJdbc = Form.useWatch("useCustomJdbc", form);
	const keytabName = Form.useWatch("keytabFileName", form) as string | undefined;
	const destinationConfigRaw = Form.useWatch("destinationConfigRaw", form) as string | undefined;
	const resolvedType = normalizeLakeType(type || editingType);
	const isInceptor = resolvedType === "INCEPTOR";
	const isJdbcPassword = authMethod === "JDBC_PASSWORD";
	const isKerberos = authMethod !== "JDBC_PASSWORD";
	const isKeytab = authMethod === "KEYTAB";
	const isKerberosPassword = authMethod === "PASSWORD";

	const writerConfigRequired = Boolean(defaulted);
	const hasRawWriterConfig = useMemo(() => {
		if (!destinationConfigRaw || !destinationConfigRaw.trim()) return false;
		try {
			const parsed = parseJsonInput(destinationConfigRaw);
			return Boolean(parsed && Object.keys(parsed).length);
		} catch {
			return false;
		}
	}, [destinationConfigRaw]);
	const showWriterConfigAlert = !readOnly && writerConfigRequired && !hasRawWriterConfig;

	const driverOptions = useMemo(
		() => buildDriverOptions(jdbcDrivers, resolvedType),
		[jdbcDrivers, resolvedType],
	);
	const hasDriverOptions = driverOptions.length > 0;

	useEffect(() => {
		if (initialized) return;
		if (!isEditing) {
			form.setFieldsValue({
				type: "JDBC",
				defaulted: false,
				useHttpTransport: false,
				useSsl: false,
				useCustomJdbc: false,
				authMethod: "KEYTAB",
				port: 10000,
				testQuery: "select 1",
			});
			setInitialized(true);
			return;
		}
		if (!editingLake) return;
		if (editingType === "INCEPTOR") {
			if (!inceptorConfig) return;
			form.setFieldsValue({
				name: inceptorConfig.name || editingLake.name,
				type: "INCEPTOR",
				description: inceptorConfig.description || editingLake.description || "",
				jdbcUrl: inceptorConfig.jdbcUrl || editingLake.jdbcUrl || "",
				loginPrincipal: inceptorConfig.loginPrincipal || "",
				authMethod: (inceptorConfig.authMethod as FormValues["authMethod"]) || "KEYTAB",
				krb5Conf: inceptorConfig.krb5Conf || "",
				keytabBase64: inceptorConfig.keytabBase64 || "",
				keytabFileName: inceptorConfig.keytabFileName || "",
				password: inceptorConfig.password || "",
				jdbcPropertiesRaw: buildPropertiesRaw(inceptorConfig.jdbcProperties),
				proxyUser: inceptorConfig.proxyUser || "",
				servicePrincipal: inceptorConfig.servicePrincipal || "",
				host: inceptorConfig.host || "",
				port: inceptorConfig.port ?? 10000,
				database: inceptorConfig.database || "",
				useHttpTransport: Boolean(inceptorConfig.useHttpTransport),
				httpPath: inceptorConfig.httpPath || "",
				useSsl: Boolean(inceptorConfig.useSsl),
				useCustomJdbc: Boolean(inceptorConfig.useCustomJdbc),
				customJdbcUrl: inceptorConfig.customJdbcUrl || "",
				driverVersion: inceptorConfig.driverVersion || "",
				testQuery: "select 1",
				defaulted: Boolean(inceptorConfig.defaulted),
				destinationDefinitionId: inceptorConfig.destinationDefinitionId || "",
				destinationName: inceptorConfig.destinationName || "",
				destinationConfigRaw: formatJson(inceptorConfig.destinationConfig),
			});
			setKrb5FileName(inceptorConfig.krb5Conf ? "已配置" : "");
			setInitialized(true);
			return;
		}
		form.setFieldsValue({
			name: editingLake.name,
			type: normalizeLakeType(editingLake.type),
			description: editingLake.description,
			jdbcUrl: editingLake.jdbcUrl,
			username: editingLake.username,
			password: "",
			defaulted: editingLake.defaulted,
			driverClass: editingLake.props?.driverClass,
			driverVersion: editingLake.props?.driverVersion,
			testQuery: editingLake.props?.testQuery,
			jdbcPropertiesRaw: buildPropertiesRaw(editingLake.props?.jdbcProperties),
			destinationDefinitionId: editingLake.props?.destinationDefinitionId,
			destinationName: editingLake.props?.destinationName,
			destinationConfigRaw: formatJson(editingLake.destinationConfig ?? (editingLake.props as any)?.destinationConfig),
		});
		setInitialized(true);
	}, [
		initialized,
		isEditing,
		editingLake,
		editingType,
		inceptorConfig,
		form,
	]);

	const buildDataLakePayload = (values: FormValues): UpsertInfraDataSourcePayload => {
		const props: Record<string, any> = {};
		if (values.destinationDefinitionId) {
			props.destinationDefinitionId = values.destinationDefinitionId.trim();
		}
		const destinationName = values.destinationName?.trim() || values.name?.trim();
		if (destinationName) {
			props.destinationName = destinationName;
		}
		if (values.driverClass) {
			props.driverClass = values.driverClass.trim();
		}
		if (values.driverVersion) {
			props.driverVersion = values.driverVersion.trim();
		}
		if (values.testQuery) {
			props.testQuery = values.testQuery.trim();
		}
		const jdbcProps = parseProperties(values.jdbcPropertiesRaw);
		if (Object.keys(jdbcProps).length) {
			props.jdbcProperties = jdbcProps;
		}
		const payload: UpsertInfraDataSourcePayload = {
			name: String(values.name || "").trim(),
			type: normalizeLakeType(values.type),
			jdbcUrl: String(values.jdbcUrl || "").trim(),
			username: values.username?.trim(),
			description: values.description?.trim(),
			props,
			defaulted: Boolean(values.defaulted),
		};
		const secrets: Record<string, any> = {};
		const destConfig = parseJsonInput(values.destinationConfigRaw);
		if (destConfig) {
			secrets.destinationConfig = destConfig;
		}
		if (values.password) {
			secrets.password = values.password;
		}
		if (Object.keys(secrets).length) {
			payload.secrets = secrets;
		}
		return payload;
	};

	const buildJdbcTestPayload = (values: FormValues) => ({
		jdbcUrl: values.jdbcUrl?.trim(),
		username: values.username?.trim(),
		password: values.password,
		driverClass: values.driverClass?.trim(),
		driverVersion: values.driverVersion?.trim(),
		testQuery: values.testQuery?.trim(),
		jdbcProperties: parseProperties(values.jdbcPropertiesRaw),
	});

	const buildInceptorTestPayload = (values: FormValues): HiveConnectionTestRequest => {
		const method = values.authMethod as HiveAuthMethod;
		const isKeytab = method === "KEYTAB";
		const isPassword = method === "PASSWORD";
		const isJdbcPassword = method === "JDBC_PASSWORD";
		return {
			jdbcUrl: String(values.jdbcUrl || ""),
			loginPrincipal: String(values.loginPrincipal || ""),
			authMethod: method,
			krb5Conf: isKeytab || isPassword ? values.krb5Conf || undefined : undefined,
			keytabBase64: isKeytab ? values.keytabBase64 || undefined : undefined,
			keytabFileName: isKeytab ? values.keytabFileName || undefined : undefined,
			password: isPassword || isJdbcPassword ? values.password || undefined : undefined,
			jdbcProperties: parseProperties(values.jdbcPropertiesRaw),
			proxyUser: values.proxyUser || undefined,
			testQuery: values.testQuery || undefined,
			driverVersion: values.driverVersion || undefined,
		};
	};

	const buildInceptorPublishPayload = (
		values: FormValues,
		result: HiveConnectionTestResult | null,
	): HiveConnectionPersistRequest => {
		const method = values.authMethod as HiveAuthMethod;
		const isKeytab = method === "KEYTAB";
		const isPassword = method === "PASSWORD";
		const isJdbcPassword = method === "JDBC_PASSWORD";
		return {
			name: String(values.name || "").trim(),
			description: values.description || undefined,
			jdbcUrl: String(values.jdbcUrl || "").trim(),
			loginPrincipal: String(values.loginPrincipal || "").trim(),
			authMethod: method,
			krb5Conf: isKeytab || isPassword ? values.krb5Conf || undefined : undefined,
			keytabBase64: isKeytab ? values.keytabBase64 || undefined : undefined,
			keytabFileName: isKeytab ? values.keytabFileName || undefined : undefined,
			password: isPassword || isJdbcPassword ? values.password || undefined : undefined,
			jdbcProperties: parseProperties(values.jdbcPropertiesRaw),
			proxyUser: values.proxyUser || undefined,
			servicePrincipal: isJdbcPassword ? undefined : values.servicePrincipal || undefined,
			host: String(values.host || "").trim(),
			port: Number(values.port || 10000),
			database: String(values.database || "").trim(),
			useHttpTransport: Boolean(values.useHttpTransport),
			httpPath: values.httpPath || undefined,
			useSsl: Boolean(values.useSsl),
			useCustomJdbc: Boolean(values.useCustomJdbc),
			customJdbcUrl: values.customJdbcUrl || undefined,
			lastTestElapsedMillis: result?.elapsedMillis ?? undefined,
			engineVersion: result?.engineVersion ?? null,
			driverVersion: result?.driverVersion ?? values.driverVersion ?? null,
			defaulted: values.defaulted ?? false,
			destinationDefinitionId: values.destinationDefinitionId || undefined,
			destinationName: values.destinationName?.trim() || values.name?.trim() || undefined,
			destinationConfig: parseJsonInput(values.destinationConfigRaw),
		};
	};

	const validateWriterConfig = async (_: unknown, value: string | undefined) => {
		if (readOnly) return Promise.resolve();
		const trimmed = value?.trim();
		if (!trimmed) {
			if (writerConfigRequired) {
				return Promise.reject(new Error("请填写写入器配置 JSON"));
			}
			return Promise.resolve();
		}
		try {
			JSON.parse(trimmed);
			return Promise.resolve();
		} catch {
			return Promise.reject(new Error("写入器配置 JSON 格式错误"));
		}
	};

	const handleFillWriterConfig = () => {
		const values = form.getFieldsValue();
		const template = buildWriterTemplate(values.destinationDefinitionId, values);
		form.setFieldsValue({ destinationConfigRaw: formatJson(template) });
	};

	const handleClearWriterConfig = () => {
		form.setFieldsValue({ destinationConfigRaw: "" });
	};

	const handleTest = async () => {
		if (!isEditing && !isInceptor) {
			toast.error("请先保存后再测试");
			return;
		}
		try {
			setTesting(true);
			if (isInceptor) {
				const requiredFields = ["name", "jdbcUrl", "loginPrincipal", "authMethod", "host", "port", "database"];
				if (isKerberos) {
					requiredFields.push("krb5Conf");
				}
				if (isKeytab) {
					requiredFields.push("keytabBase64");
				}
				if (isKerberosPassword || isJdbcPassword) {
					requiredFields.push("password");
				}
				if (isKerberos) {
					requiredFields.push("servicePrincipal");
				}
				await form.validateFields(requiredFields);
				const values = form.getFieldsValue();
				const payload = buildInceptorTestPayload(values);
				const result = await adminApi.testInceptorConnection(payload, editingLake?.id);
				setTestResult(result);
				if (result.success) {
					toast.success("连接成功");
				} else {
					toast.error(result.message || "连接失败");
				}
				queryClient.invalidateQueries({ queryKey: ["admin", "data-lake-test-logs"] });
				queryClient.invalidateQueries({ queryKey: ["admin", "data-lakes"] });
				return;
			}
			await form.validateFields(["name", "type", "jdbcUrl"]);
			const values = form.getFieldsValue();
			const payload = buildJdbcTestPayload(values);
			const result = await adminApi.testDataLakeConnection(editingLake?.id as string, payload);
			setTestResult(result);
			if (result.success) {
				toast.success("连接成功");
			} else {
				toast.error(result.message || "连接失败");
			}
			queryClient.invalidateQueries({ queryKey: ["admin", "data-lake-test-logs"] });
			queryClient.invalidateQueries({ queryKey: ["admin", "data-lakes"] });
		} catch (error: any) {
			if (error?.errorFields?.length) {
				return;
			}
			toast.error(error?.message || "测试失败");
		} finally {
			setTesting(false);
		}
	};

	const handleSave = async () => {
		try {
			setSaving(true);
			const values = await form.validateFields();
			const destinationConfig = parseJsonInput(values.destinationConfigRaw);
			if (writerConfigRequired && (!destinationConfig || Object.keys(destinationConfig).length === 0)) {
				form.scrollToField("destinationConfigRaw");
				toast.error("请完善写入器配置");
				return;
			}
			if (isInceptor) {
				const payload = buildInceptorPublishPayload(values, testResult);
				await adminApi.publishInceptorConfig(payload);
				toast.success(isEditing ? "已更新 Inceptor 数据湖" : "已新增 Inceptor 数据湖");
				queryClient.invalidateQueries({ queryKey: ["admin", "data-lakes"] });
				queryClient.invalidateQueries({ queryKey: ["admin", "inceptor-config"] });
				navigate("/admin/data-lake");
				return;
			}
			const payload = buildDataLakePayload(values);
			if (isEditing && editingLake?.id) {
				await adminApi.updateDataLake(editingLake.id, payload);
				toast.success("已更新数据湖配置");
			} else {
				await adminApi.createDataLake(payload);
				toast.success("已新增数据湖配置");
			}
			queryClient.invalidateQueries({ queryKey: ["admin", "data-lakes"] });
			navigate("/admin/data-lake");
		} catch (error: any) {
			if (error?.errorFields?.length) {
				return;
			}
			if (error instanceof SyntaxError) {
				toast.error("目标端配置 JSON 格式错误");
				return;
			}
			toast.error(error?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	if (id && !dataLakesLoading && !editingLake) {
		return (
			<div className="mx-auto w-full max-w-[1400px] px-6 py-6 space-y-6">
				<Card>
					<CardHeader>
						<CardTitle>数据湖不存在</CardTitle>
					</CardHeader>
					<CardContent className="text-sm">
						<Text variant="body3" className="text-muted-foreground">
							未找到对应的数据湖记录，请返回列表重新选择。
						</Text>
						<div className="mt-4">
							<AntButton type="primary" onClick={() => navigate("/admin/data-lake")}>返回列表</AntButton>
						</div>
					</CardContent>
				</Card>
			</div>
		);
	}

	return (
		<div className="mx-auto w-full max-w-[1400px] px-6 py-6 space-y-6">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<Text variant="body1" className="block text-lg font-semibold">
						{readOnly ? "数据湖详情" : isEditing ? "编辑数据湖" : "新增数据湖"}
					</Text>
					<Text variant="body3" className="text-muted-foreground">
						{readOnly
							? "查看数据湖连接与认证配置详情。"
							: "根据类型填写连接信息，保存后可用于平台入湖配置与测试。"}
					</Text>
				</div>
				<Space>
					<AntButton onClick={() => navigate("/admin/data-lake")}>返回列表</AntButton>
					{readOnly ? (
						isEditing ? (
							<AntButton type="primary" onClick={() => navigate(`/admin/data-lake/${id}`)}>
								编辑
							</AntButton>
						) : null
					) : (
						<>
							<AntButton onClick={handleTest} loading={testing}>
								测试连接
							</AntButton>
							<AntButton type="primary" onClick={handleSave} loading={saving}>
								保存
							</AntButton>
						</>
					)}
				</Space>
			</div>

			<Card>
				<CardHeader>
				<CardTitle>连接配置</CardTitle>
			</CardHeader>
			<CardContent className="text-sm">
				<Form<FormValues>
					layout="vertical"
					form={form}
						requiredMark
						disabled={readOnly}
						size="small"
						className="text-sm [&_.ant-form-item-label>label]:text-sm [&_.ant-input]:text-sm [&_.ant-input-number-input]:text-sm [&_.ant-select-selector]:text-sm [&_.ant-select-selection-item]:text-sm [&_.ant-select-selection-placeholder]:text-sm"
					>
						<div className="grid gap-4 md:grid-cols-2">
							<Form.Item name="name" label="名称" rules={[{ required: true, message: "请填写名称" }]}>
								<Input placeholder="如：ODS Hive" />
							</Form.Item>
							<Form.Item
								name="type"
								label="数据湖类型"
								required
								rules={[{ required: true, message: "请选择类型" }]}
							>
								<Select options={TYPE_OPTIONS} disabled={isEditing} />
							</Form.Item>
							<Form.Item
								name="jdbcUrl"
								label="JDBC URL"
								required
								rules={[{ required: true, message: "请填写 JDBC URL" }]}
							>
								<Input placeholder="jdbc:xxx://host:port/db" />
							</Form.Item>
							{isInceptor ? null : (
								<>
									<Form.Item
										name="username"
										label="用户名"
										required
										rules={[{ required: true, message: "请输入用户名" }]}
									>
										<Input placeholder="数据库用户名" />
									</Form.Item>
									<Form.Item
										name="password"
										label="密码"
										required
										rules={[{ required: true, message: "请输入密码" }]}
									>
										<Input.Password placeholder="数据库密码" />
									</Form.Item>
								</>
							)}
						</div>
						<Form.Item name="description" label="描述">
							<Input placeholder="可选描述" />
						</Form.Item>
						<Form.Item name="defaulted" label="设为默认" valuePropName="checked">
							<Switch />
						</Form.Item>

						<Divider />

						{isInceptor ? (
							<div className="space-y-4">
								<div className="grid gap-4 md:grid-cols-2">
									<Form.Item
										name="authMethod"
										label="认证方式"
										required
										rules={[{ required: true, message: "请选择认证方式" }]}
									>
										<Select options={AUTH_METHOD_OPTIONS} />
									</Form.Item>
									<Form.Item name="driverVersion" label="JDBC 驱动">
										{hasDriverOptions ? (
											<Select
												options={driverOptions}
												placeholder="选择驱动版本"
												showSearch
												allowClear
												optionFilterProp="label"
												loading={jdbcDriversLoading}
												notFoundContent={jdbcDriversLoading ? "加载中..." : "未检测到驱动"}
											/>
										) : (
											<Input placeholder="可选，填写驱动版本" />
										)}
									</Form.Item>
								</div>

								<div className="grid gap-4 md:grid-cols-3">
									<Form.Item name="host" label="主机" required rules={[{ required: true, message: "请填写主机" }]}>
										<Input placeholder="如：inceptor-prod" />
									</Form.Item>
									<Form.Item name="port" label="端口" required rules={[{ required: true, message: "请填写端口" }]}>
										<InputNumber min={1} max={65535} className="w-full" />
									</Form.Item>
									<Form.Item name="database" label="默认数据库" required rules={[{ required: true, message: "请填写数据库" }]}>
										<Input placeholder="default" />
									</Form.Item>
								</div>

								<div className="grid gap-4 md:grid-cols-2">
									<Form.Item
										name="loginPrincipal"
										label={isJdbcPassword ? "用户名" : "Kerberos 主体"}
										required
										rules={[{ required: true, message: "请填写登录主体" }]}
									>
										<Input placeholder={isJdbcPassword ? "如：hive_user" : "如：hive/_HOST@REALM"} />
									</Form.Item>
									{isKerberos ? (
										<Form.Item
											name="servicePrincipal"
											label="服务主体"
											required
											rules={[{ required: true, message: "请填写服务主体" }]}
										>
											<Input placeholder="如：hive/_HOST@REALM" />
										</Form.Item>
									) : (
										<Form.Item
											name="password"
											label="密码"
											required={isJdbcPassword}
											rules={[{ required: isJdbcPassword, message: "请输入密码" }]}
										>
											<Input.Password placeholder="请输入密码" />
										</Form.Item>
									)}
								</div>

								{isKerberos ? (
									<div className="grid gap-4 md:grid-cols-2">
										<Form.Item noStyle shouldUpdate>
											{() => {
												const error = form.getFieldError("krb5Conf")[0];
												return (
													<Form.Item label="krb5.conf 文件" required={isKerberos} validateStatus={error ? "error" : ""} help={error}>
														<Space direction="vertical" size={4} className="w-full">
															<Upload
																disabled={readOnly}
																maxCount={1}
																showUploadList={false}
																accept=".conf"
																beforeUpload={async (file) => {
																	try {
																		const text = await readFileAsText(file as File);
																		form.setFieldsValue({ krb5Conf: text });
																		form.setFields([{ name: "krb5Conf", errors: [] }]);
																		setKrb5FileName(file.name);
																	} catch (error: any) {
																		toast.error(error?.message || "读取 krb5.conf 失败");
																	}
																	return false;
																}}
																onRemove={() => {
																	form.setFieldsValue({ krb5Conf: "" });
																	setKrb5FileName("");
																}}
															>
																<AntButton>上传 krb5.conf</AntButton>
															</Upload>
															<Text variant="body3" className="text-muted-foreground">
																{krb5FileName || "未选择文件"}
															</Text>
														</Space>
													</Form.Item>
												);
											}}
										</Form.Item>

										{isKeytab ? (
											<Form.Item noStyle shouldUpdate>
												{() => {
													const error = form.getFieldError("keytabBase64")[0];
													return (
														<Form.Item label="Keytab 文件" required={isKeytab} validateStatus={error ? "error" : ""} help={error}>
															<Space direction="vertical" size={4} className="w-full">
																<Upload
																	disabled={readOnly}
																	maxCount={1}
																	showUploadList={false}
																	accept=".keytab"
																	beforeUpload={async (file) => {
																		try {
																			const base64 = await readFileAsBase64(file as File);
																			form.setFieldsValue({ keytabBase64: base64, keytabFileName: file.name });
																			form.setFields([{ name: "keytabBase64", errors: [] }]);
																		} catch (error: any) {
																			toast.error(error?.message || "读取 Keytab 失败");
																		}
																		return false;
																	}}
																	onRemove={() => {
																		form.setFieldsValue({ keytabBase64: "", keytabFileName: "" });
																	}}
																>
																	<AntButton>上传 Keytab</AntButton>
																</Upload>
																<Text variant="body3" className="text-muted-foreground">
																	{keytabName || (form.getFieldValue("keytabBase64") ? "已配置" : "未选择文件")}
																</Text>
															</Space>
														</Form.Item>
													);
												}}
											</Form.Item>
										) : (
											<Form.Item
												name="password"
												label="Kerberos 密码"
												required={isKerberosPassword}
												rules={[{ required: isKerberosPassword, message: "请输入 Kerberos 密码" }]}
											>
												<Input.Password placeholder="请输入 Kerberos 密码" />
											</Form.Item>
										)}

										<Form.Item name="krb5Conf" rules={[{ required: isKerberos, message: "请上传 krb5.conf 文件" }]} hidden>
											<Input />
										</Form.Item>
										<Form.Item name="keytabBase64" rules={[{ required: isKeytab, message: "请上传 Keytab 文件" }]} hidden>
											<Input />
										</Form.Item>
										<Form.Item name="keytabFileName" hidden>
											<Input />
										</Form.Item>
									</div>
								) : null}

								<Form.Item name="testQuery" label="测试 SQL">
									<Input.TextArea rows={2} placeholder="例如：select * from db.table limit 1" />
								</Form.Item>

								<Collapse
									ghost
									defaultActiveKey={["advanced"]}
									items={[
										{
											key: "advanced",
											label: "高级配置",
											children: (
												<div className="space-y-4">
													<div className="grid gap-4 md:grid-cols-3">
														<Form.Item name="useSsl" label="启用 SSL" valuePropName="checked">
															<Switch />
														</Form.Item>
														<Form.Item name="useHttpTransport" label="HTTP 传输" valuePropName="checked">
															<Switch />
														</Form.Item>
														<Form.Item name="useCustomJdbc" label="自定义 JDBC" valuePropName="checked">
															<Switch />
														</Form.Item>
													</div>
													{useHttpTransport ? (
														<Form.Item name="httpPath" label="HTTP Path">
															<Input placeholder="如：/gateway/default/hive" />
														</Form.Item>
													) : null}
													{useCustomJdbc ? (
														<Form.Item name="customJdbcUrl" label="自定义 JDBC URL">
															<Input placeholder="jdbc:inceptor2://host:port/default" />
														</Form.Item>
													) : null}
													<div className="grid gap-4 md:grid-cols-2">
														<Form.Item name="proxyUser" label="代理用户">
															<Input placeholder="可选" />
														</Form.Item>
														<Form.Item name="jdbcPropertiesRaw" label="JDBC 扩展参数（每行 key=value）">
															<Input.TextArea rows={4} placeholder="transportMode=http" />
														</Form.Item>
													</div>
												</div>
											),
										},
									]}
								/>
							</div>
						) : (
							<div className="space-y-4">
								<div className="grid gap-4 md:grid-cols-3">
									<Form.Item name="driverVersion" label="JDBC 驱动">
										{hasDriverOptions ? (
											<Select
												options={driverOptions}
												placeholder="选择驱动版本"
												showSearch
												allowClear
												optionFilterProp="label"
												loading={jdbcDriversLoading}
												notFoundContent={jdbcDriversLoading ? "加载中..." : "未检测到驱动"}
											/>
										) : (
											<Input placeholder="可选，填写驱动版本" />
										)}
									</Form.Item>
									<Form.Item name="driverClass" label="驱动类">
										<Input placeholder="可选，例如：org.postgresql.Driver" />
									</Form.Item>
									<Form.Item name="testQuery" label="测试 SQL">
										<Input placeholder="默认 SELECT 1" />
									</Form.Item>
								</div>
								<Form.Item name="jdbcPropertiesRaw" label="JDBC 扩展参数（每行 key=value）">
									<Input.TextArea rows={4} placeholder="connectTimeout=5" />
								</Form.Item>
							</div>
						)}

						<Divider />

						{showWriterConfigAlert ? (
							<Alert
								type="warning"
								showIcon
								message="写入器配置必填"
								description={`默认数据湖需要配置通用写入器模板（可使用 ${TABLE_PLACEHOLDER} 占位符）。`}
								className="mb-4"
							/>
						) : null}

						<div className="space-y-4">
							<div className="grid gap-4 md:grid-cols-2">
								<Form.Item
									name="destinationDefinitionId"
									label="写入器类型"
									required={writerConfigRequired}
									rules={[{ required: writerConfigRequired, message: "请选择写入器类型" }]}
								>
									<Select
										showSearch
										allowClear
										placeholder="请选择 Addax 写入器"
										options={ADDAX_WRITER_OPTIONS}
										optionFilterProp="label"
									/>
								</Form.Item>
								<Form.Item name="destinationName" label="写入器名称">
									<Input placeholder="默认使用数据湖名称" />
								</Form.Item>
							</div>
							<Form.Item
								name="destinationConfigRaw"
								label="写入器配置 JSON（通用模板）"
								required={writerConfigRequired}
								rules={[{ validator: validateWriterConfig }]}
							>
								<Input.TextArea
									rows={6}
									placeholder={`{"connection":[{"jdbcUrl":["jdbc:..."],"table":["${TABLE_PLACEHOLDER}"]}],"username":"user","password":"***","column":["*"]}`}
								/>
							</Form.Item>
							<Space>
								<AntButton onClick={handleFillWriterConfig} disabled={readOnly}>
									生成模板
								</AntButton>
								<AntButton onClick={handleClearWriterConfig} disabled={readOnly}>
									清空配置
								</AntButton>
							</Space>
							<Text variant="body3" className="text-muted-foreground">
								通用模板不需要填写具体表名，可使用 {TABLE_PLACEHOLDER} 占位符，入湖任务会补齐目标表。
							</Text>
						</div>
					</Form>
				</CardContent>
			</Card>

			{testResult ? (
				<Card>
					<CardHeader>
						<CardTitle>最新测试结果</CardTitle>
					</CardHeader>
					<CardContent>
						{(() => {
							const parts = [`耗时 ${testResult.elapsedMillis ?? "--"} ms`];
							if (testResult.engineVersion) {
								parts.push(`引擎 ${testResult.engineVersion}`);
							}
							if (testResult.driverVersion) {
								parts.push(`驱动 ${testResult.driverVersion}`);
							}
							return (
								<Alert
									type={testResult.success ? "success" : "error"}
									message={testResult.message || (testResult.success ? "连接成功" : "连接失败")}
									description={parts.join(" · ")}
									showIcon
								/>
							);
						})()}
					</CardContent>
				</Card>
			) : null}
		</div>
	);
}
