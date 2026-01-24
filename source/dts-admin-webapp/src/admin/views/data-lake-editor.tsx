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
	AirbyteDestinationDefinition,
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

const LAKE_TYPE_SET = new Set(TYPE_OPTIONS.map((item) => item.value));

const DRIVER_TYPE_HINTS: Record<string, string[]> = {
	HIVE: ["hive"],
	INCEPTOR: ["inceptor", "hive"],
	ICEBERG: ["iceberg"],
	CLICKHOUSE: ["clickhouse"],
	POSTGRESQL: ["postgres", "postgresql", "pgjdbc", "pg"],
};

const DESTINATION_DEF_HINTS: Record<string, string[]> = {
	HIVE: ["hive"],
	INCEPTOR: ["hive"],
	JDBC: ["jdbc"],
	ICEBERG: ["iceberg"],
	CLICKHOUSE: ["clickhouse"],
	POSTGRESQL: ["postgres", "postgresql"],
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

const resolveDestinationDefinitionId = (
	definitions: AirbyteDestinationDefinition[],
	type?: string,
): string | undefined => {
	if (!definitions.length) {
		return undefined;
	}
	if (definitions.length === 1) {
		return definitions[0]?.destinationDefinitionId;
	}
	const normalizedType = normalizeLakeType(type);
	const keywords = DESTINATION_DEF_HINTS[normalizedType] || [];
	if (!keywords.length) {
		return definitions[0]?.destinationDefinitionId;
	}
	const lowerKeywords = keywords.map((keyword) => keyword.toLowerCase());
	const match = definitions.find((item) => {
		const haystack = `${item.name || ""} ${item.dockerRepository || ""}`.toLowerCase();
		return lowerKeywords.some((keyword) => haystack.includes(keyword));
	});
	return match?.destinationDefinitionId;
};

const extractDatabaseFromJdbcUrl = (jdbcUrl?: string) => {
	if (!jdbcUrl) return undefined;
	const schemeIndex = jdbcUrl.indexOf("://");
	const start = schemeIndex > -1 ? jdbcUrl.indexOf("/", schemeIndex + 3) : jdbcUrl.indexOf("/");
	if (start < 0 || start + 1 >= jdbcUrl.length) {
		return undefined;
	}
	let tail = jdbcUrl.slice(start + 1);
	const queryIndex = tail.indexOf("?");
	const semicolonIndex = tail.indexOf(";");
	const cutIndex =
		queryIndex === -1
			? semicolonIndex
			: semicolonIndex === -1
				? queryIndex
				: Math.min(queryIndex, semicolonIndex);
	if (cutIndex > -1) {
		tail = tail.slice(0, cutIndex);
	}
	const trimmed = tail.trim();
	return trimmed ? trimmed : undefined;
};

const buildDefaultDestinationConfig = (values: FormValues): Record<string, any> => {
	const payload: Record<string, any> = {};
	const jdbcUrl = values.jdbcUrl?.trim();
	if (jdbcUrl) {
		payload.jdbc_url = jdbcUrl;
	}
	const username = values.username?.trim() || values.loginPrincipal?.trim();
	if (username) {
		payload.username = username;
	}
	if (values.password) {
		payload.password = values.password;
	}
	const database = extractDatabaseFromJdbcUrl(jdbcUrl);
	if (database) {
		payload.database = database;
		payload.schema = database;
	}
	const jdbcProps = parseProperties(values.jdbcPropertiesRaw);
	if (Object.keys(jdbcProps).length) {
		payload.jdbc_properties = jdbcProps;
	}
	return payload;
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

	const { data: destinationDefs = [], isFetching: destinationDefsLoading } = useQuery({
		queryKey: ["admin", "data-lake-destination-definitions"],
		queryFn: adminApi.getDataLakeDestinationDefinitions,
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
	const authMethod = Form.useWatch("authMethod", form);
	const useHttpTransport = Form.useWatch("useHttpTransport", form);
	const useCustomJdbc = Form.useWatch("useCustomJdbc", form);
	const keytabName = Form.useWatch("keytabFileName", form) as string | undefined;
	const username = Form.useWatch("username", form) as string | undefined;
	const resolvedType = normalizeLakeType(type || editingType);
	const isInceptor = resolvedType === "INCEPTOR";
	const isJdbcPassword = authMethod === "JDBC_PASSWORD";
	const isKerberos = authMethod !== "JDBC_PASSWORD";
	const isKeytab = authMethod === "KEYTAB";
	const isKerberosPassword = authMethod === "PASSWORD";

	const driverOptions = useMemo(
		() => buildDriverOptions(jdbcDrivers, resolvedType),
		[jdbcDrivers, resolvedType],
	);
	const hasDriverOptions = driverOptions.length > 0;

	const destinationDefOptions = useMemo(
		() =>
			destinationDefs
				.filter((item) => item.destinationDefinitionId)
				.map((item) => ({
					value: String(item.destinationDefinitionId),
					label: item.name || item.destinationDefinitionId || "--",
					repo: item.dockerRepository || "",
				})),
		[destinationDefs],
	);

	const destinationDefOptionsWithFallback = useMemo(() => {
		const list = [...destinationDefOptions];
		const currentId = editingLake?.props?.destinationDefinitionId;
		if (currentId && !list.some((item) => item.value === currentId)) {
			list.push({
				value: currentId,
				label: editingLake?.props?.destinationDefinitionName || currentId,
			});
		}
		return list;
	}, [destinationDefOptions, editingLake?.props?.destinationDefinitionId, editingLake?.props?.destinationDefinitionName]);

	const destinationDefNameMap = useMemo(() => {
		const entries = destinationDefs
			.filter((item) => item.destinationDefinitionId)
			.map((item) => [String(item.destinationDefinitionId), item.name || item.destinationDefinitionId]);
		return new Map(entries);
	}, [destinationDefs]);

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
			destinationConfigRaw: "",
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

	useEffect(() => {
		if (readOnly || isEditing) return;
		if (!destinationDefs.length) return;
		const current = form.getFieldValue("destinationDefinitionId");
		if (current) return;
		const candidate = resolveDestinationDefinitionId(destinationDefs, resolvedType);
		if (candidate) {
			form.setFieldsValue({ destinationDefinitionId: candidate });
		}
	}, [destinationDefs, form, isEditing, readOnly, resolvedType]);

	const buildDataLakePayload = (
		values: FormValues,
		destinationConfigOverride?: Record<string, any>,
	): UpsertInfraDataSourcePayload => {
		const props: Record<string, any> = {};
		if (values.destinationDefinitionId) {
			props.destinationDefinitionId = values.destinationDefinitionId.trim();
		}
		if (values.destinationDefinitionId) {
			const defName = destinationDefNameMap.get(values.destinationDefinitionId);
			if (defName) {
				props.destinationDefinitionName = defName;
			}
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
		const destConfig = destinationConfigOverride ?? parseJsonInput(values.destinationConfigRaw);
		const secrets: Record<string, any> = {};
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
		const destinationConfig = resolveDestinationConfig(values);
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
			destinationConfig,
		};
	};

	const resolveDestinationConfig = (values: FormValues): Record<string, any> | undefined => {
		const parsed = parseJsonInput(values.destinationConfigRaw);
		if (parsed && Object.keys(parsed).length) {
			return parsed;
		}
		const fallback = buildDefaultDestinationConfig(values);
		return Object.keys(fallback).length ? fallback : undefined;
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
			const destinationConfig = resolveDestinationConfig(values);
			if (!destinationConfig || Object.keys(destinationConfig).length === 0) {
				toast.error("请完善目标端配置");
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
			const payload = buildDataLakePayload(values, destinationConfig);
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
			<div className="space-y-6">
				<Card>
					<CardHeader>
						<CardTitle>数据湖不存在</CardTitle>
					</CardHeader>
					<CardContent>
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
		<div className="space-y-6">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<Text variant="subTitle1" className="block">
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
				<CardContent>
					<Form<FormValues> layout="vertical" form={form} disabled={readOnly}>
						<div className="grid gap-4 md:grid-cols-2">
							<Form.Item name="name" label="名称" rules={[{ required: true, message: "请填写名称" }]}>
								<Input placeholder="如：ODS Hive" />
							</Form.Item>
							<Form.Item name="type" label="数据湖类型" rules={[{ required: true, message: "请选择类型" }]}>
								<Select options={TYPE_OPTIONS} disabled={isEditing} />
							</Form.Item>
							<Form.Item name="jdbcUrl" label="JDBC URL" rules={[{ required: true, message: "请填写 JDBC URL" }]}>
								<Input placeholder="jdbc:xxx://host:port/db" />
							</Form.Item>
							{isInceptor ? null : (
								<>
									<Form.Item name="username" label="用户名">
										<Input placeholder="可选" />
									</Form.Item>
									<Form.Item
										name="password"
										label="密码"
										rules={[{ required: Boolean(username), message: "请输入密码" }]}
									>
										<Input.Password placeholder={username ? "请输入密码" : "可选"} />
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
									<Form.Item name="authMethod" label="认证方式" rules={[{ required: true, message: "请选择认证方式" }]}> 
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
									<Form.Item name="host" label="主机" rules={[{ required: true, message: "请填写主机" }]}>
										<Input placeholder="如：inceptor-prod" />
									</Form.Item>
									<Form.Item name="port" label="端口" rules={[{ required: true, message: "请填写端口" }]}>
										<InputNumber min={1} max={65535} className="w-full" />
									</Form.Item>
									<Form.Item name="database" label="默认数据库" rules={[{ required: true, message: "请填写数据库" }]}>
										<Input placeholder="default" />
									</Form.Item>
								</div>

								<div className="grid gap-4 md:grid-cols-2">
									<Form.Item
										name="loginPrincipal"
										label={isJdbcPassword ? "用户名" : "Kerberos 主体"}
										rules={[{ required: true, message: "请填写登录主体" }]}
									>
										<Input placeholder={isJdbcPassword ? "如：hive_user" : "如：hive/_HOST@REALM"} />
									</Form.Item>
									{isKerberos ? (
										<Form.Item name="servicePrincipal" label="服务主体" rules={[{ required: true, message: "请填写服务主体" }]}> 
											<Input placeholder="如：hive/_HOST@REALM" />
										</Form.Item>
									) : (
										<Form.Item name="password" label="密码" rules={[{ required: isJdbcPassword, message: "请输入密码" }]}> 
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
											<Form.Item name="password" label="Kerberos 密码" rules={[{ required: isKerberosPassword, message: "请输入 Kerberos 密码" }]}> 
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
										{
											key: "destination",
											label: "目标连接器配置",
											children: (
												<div className="space-y-4">
													<div className="grid gap-4 md:grid-cols-2">
														<Form.Item
															name="destinationDefinitionId"
															label="目标连接器"
															rules={[{ required: true, message: "请选择目标连接器" }]}
														>
															<Select
																options={destinationDefOptionsWithFallback}
																placeholder="选择目标连接器"
																showSearch
																allowClear
																optionFilterProp="label"
																loading={destinationDefsLoading}
																notFoundContent={
																	destinationDefsLoading ? "加载中..." : "未获取到目标连接器，请确认 Airbyte 已安装目标端插件"
																}
															/>
														</Form.Item>
														<Form.Item name="destinationName" label="目标端名称">
															<Input placeholder="默认使用数据湖名称" />
														</Form.Item>
													</div>
													<Form.Item name="destinationConfigRaw" label="目标端配置 JSON">
														<Input.TextArea rows={6} placeholder='留空则使用当前数据湖连接参数生成，例如：{"jdbc_url":"jdbc:hive2://...","username":"hive"}' />
													</Form.Item>
													<Text variant="body3" className="text-muted-foreground">
														目标连接器配置将同步给平台默认入湖目标，密钥字段需包含在 JSON 中。
													</Text>
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
								<Divider />
								<div className="grid gap-4 md:grid-cols-2">
									<Form.Item
										name="destinationDefinitionId"
										label="目标连接器"
										rules={[{ required: true, message: "请选择目标连接器" }]}
									>
										<Select
											options={destinationDefOptionsWithFallback}
											placeholder="选择目标连接器"
											showSearch
											allowClear
											optionFilterProp="label"
											loading={destinationDefsLoading}
											notFoundContent={
												destinationDefsLoading ? "加载中..." : "未获取到目标连接器，请确认 Airbyte 已安装目标端插件"
											}
										/>
									</Form.Item>
									<Form.Item name="destinationName" label="目标端名称">
										<Input placeholder="默认使用数据湖名称" />
									</Form.Item>
								</div>
								<Form.Item name="destinationConfigRaw" label="目标端配置 JSON">
									<Input.TextArea rows={6} placeholder='留空则使用当前数据湖连接参数生成，例如：{"jdbc_url":"jdbc:hive2://..."}' />
								</Form.Item>
								{editingLake?.hasSecrets ? (
									<Text variant="body3" className="text-muted-foreground">
										已存在目标端密钥，如需更新请重新填写配置 JSON。
									</Text>
								) : null}
							</div>
						)}
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
