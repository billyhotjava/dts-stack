import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { getOrgTree, type OrgNode } from "@/api/services/directoryService";
import {
	Alert,
	Button,
	Card,
	Divider,
	Form,
	Input,
	InputNumber,
	Modal,
	Select,
	Space,
	Switch,
	Tag,
	Typography,
	message,
} from "antd";
import { CompactTable } from "@/components/table";
import {
	AppstoreOutlined,
	PlusOutlined,
	ReloadOutlined,
	EditOutlined,
	DeleteOutlined,
	ExperimentOutlined,
	CheckCircleOutlined,
	CloseCircleOutlined,
} from "@ant-design/icons";
import { Upload } from "@/components/upload";
import RollbackImpactModal, { type RollbackRequest } from "@/components/rollback/RollbackImpactModal";
import dataSourcesService, {
	type ConnectionTestResult,
	type DataSourceUpsertPayload,
	type DataSourceUpdateImpact,
	type ExcelImportParseResponse,
	type ExcelImportPrepareResponse,
	type InfraDataSource,
	type OdsGenerationPreviewResponse,
	type OdsGenerationRequest,
	type OdsPrecheckResponse,
	type OdsSourceColumnRequest,
	type OdsTablePlan,
	type SchemaDiscoverColumn,
	type SchemaDiscoverResponse,
	type SchemaDiscoverTable,
} from "@/api/services/dataSourcesService";
import connectorsService, { type InfraConnector } from "@/api/services/connectorsService";
import {
	ingestionTaskAPI,
	type ApiAuthProviderDescriptorDTO,
	type ApiAuthProviderFieldDTO,
	type ApiConnectorContractDTO,
} from "@/api/ingestion";
import { createIngestionTask } from "@/api/platformApi";
import jdbcDriversService, { type InfraJdbcDriver } from "@/api/services/jdbcDriversService";
import { formatTime } from "@/utils/textUtils";
type UploadRequestOption = Parameters<NonNullable<import("antd").UploadProps["customRequest"]>>[0];

const { Text } = Typography;

const JDBC_TYPES = new Set([
	"dm",
	"dameng",
	"kingbase",
	"gbase",
	"postgresql",
	"postgres",
	"pg",
	"mysql",
	"mariadb",
	"oracle",
	"sqlserver",
	"mssql",
	"clickhouse",
	"hive",
	"inceptor",
	"db2",
	"sqlite",
	"jdbc",
]);

const TYPE_OPTIONS = [
	{ label: "PostgreSQL", value: "postgresql" },
	{ label: "达梦 DM", value: "dm" },
	{ label: "ClickHouse", value: "clickhouse" },
	{ label: "MySQL / MariaDB", value: "mysql" },
	{ label: "Oracle", value: "oracle" },
	{ label: "SQLServer", value: "sqlserver" },
	{ label: "人大金仓", value: "kingbase" },
	{ label: "GBase", value: "gbase" },
	{ label: "Hive", value: "hive" },
	{ label: "Inceptor", value: "inceptor" },
	{ label: "Excel", value: "excel" },
	{ label: "CSV", value: "csv" },
	{ label: "JSON 文件", value: "json" },
	{ label: "API", value: "api" },
	{ label: "通用 JDBC", value: "jdbc" },
];

const normalizeType = (value?: string) => String(value || "").trim().toLowerCase();

const normalizeConnectorKey = (value?: string) => String(value || "").trim().toLowerCase().replace(/_/g, "-");

const inferConnectorKey = (type?: string, props?: Record<string, any>) => {
	const explicit = normalizeConnectorKey(props?.connectorKey);
	if (explicit) return explicit;
	const normalized = normalizeType(type);
	if (!normalized) return "";
	if (["postgres", "postgresql", "pg"].includes(normalized)) return "postgresql";
	if (["mssql", "sqlserver", "sql_server"].includes(normalized)) return "sqlserver";
	if (["dameng", "dm8"].includes(normalized)) return "dm";
	if (["api", "http", "https", "http_api", "api_http", "rest", "rest_api", "httpreader"].includes(normalized)) return "http-api";
	return normalized.replace(/_/g, "-");
};

const isJdbcType = (type?: string, jdbcUrl?: string) => {
	if (jdbcUrl) return true;
	const normalized = normalizeType(type);
	if (!normalized) return false;
	if (normalized.includes("jdbc")) return true;
	return JDBC_TYPES.has(normalized);
};

const isFileSource = (type?: string) => {
	const normalized = normalizeType(type);
	return normalized === "excel" || normalized === "csv";
};

const API_TYPES = new Set(["api", "http", "https", "http_api", "api_http", "rest", "rest_api", "httpreader"]);

const isApiSourceType = (type?: string) => {
	const normalized = normalizeType(type);
	return Boolean(normalized && API_TYPES.has(normalized));
};

const isAdminManagedSource = (source?: InfraDataSource | null) => {
	if (!source) return false;
	// Check props flag (set by BiadminDataSourceInitializer)
	if (source.props?.source === "admin-data-lake") return true;
	// Fallback: match by name for data sources created before the props fix
	if (source.name === "数仓 (biadmin)") return true;
	return false;
};

const parseJson = (value?: string) => {
	const text = String(value || "").trim();
	if (!text) return undefined;
	return JSON.parse(text);
};

const asRecord = (value: any): Record<string, any> | undefined =>
	value && typeof value === "object" && !Array.isArray(value) ? value : undefined;

const stringifyJson = (value: any) => {
	if (!value || typeof value !== "object" || Array.isArray(value)) return "";
	return JSON.stringify(value, null, 2);
};

const cleanRecord = (value: any): Record<string, any> | undefined => {
	const input = asRecord(value);
	if (!input) return undefined;
	const output: Record<string, any> = {};
	Object.entries(input).forEach(([key, raw]) => {
		if (raw === undefined || raw === null) return;
		if (typeof raw === "string" && !raw.trim()) return;
		output[key] = typeof raw === "string" ? raw.trim() : raw;
	});
	return Object.keys(output).length ? output : undefined;
};

const omitReaderType = (props?: Record<string, any>) => {
	if (!props) return undefined;
	const {
		readerType,
		reader,
		driverClass,
		driverVersion,
		baseUrl,
		baseURL,
		authProvider,
		auth,
		api,
		readerConfig,
		defaultHeaders,
		requestPolicy,
		rateLimit,
		tls,
		connectorType,
		sourceCategory,
		contractVersion,
		...rest
	} = props;
	return rest;
};

const firstRecord = (...values: any[]) => values.map(asRecord).find(Boolean);

const readApiNode = (props?: Record<string, any>) => firstRecord(props?.api, props?.readerConfig);

const readApiAuth = (props?: Record<string, any>) => {
	const apiNode = readApiNode(props);
	return firstRecord(props?.auth, apiNode?.auth);
};

const readApiBaseUrl = (props?: Record<string, any>) => {
	const apiNode = readApiNode(props);
	return String(props?.baseUrl || props?.baseURL || apiNode?.baseUrl || apiNode?.baseURL || "").trim();
};

const readApiAuthProvider = (props?: Record<string, any>) => {
	const apiNode = readApiNode(props);
	const auth = readApiAuth(props);
	return String(props?.authProvider || apiNode?.authProvider || auth?.provider || "none").trim() || "none";
};

const readApiAuthConfig = (props?: Record<string, any>) => {
	const auth = readApiAuth(props);
	const config = { ...(asRecord(auth?.config) || {}) };
	Object.entries(auth || {}).forEach(([key, value]) => {
		if (["provider", "config", "secretRefs"].includes(key)) return;
		config[key] = value;
	});
	return cleanRecord(config) || {};
};

const readApiAuthSecretRefs = (props?: Record<string, any>) => cleanRecord(readApiAuth(props)?.secretRefs) || {};

const readApiConfigPart = (props: Record<string, any> | undefined, key: string) => {
	const apiNode = readApiNode(props);
	return asRecord(props?.[key]) || asRecord(apiNode?.[key]);
};

const parseObjectJson = (value: string | undefined, label: string) => {
	const parsed = parseJson(value);
	if (parsed === undefined) return undefined;
	if (!asRecord(parsed)) {
		throw new Error(`${label} 必须是 JSON Object`);
	}
	return parsed as Record<string, any>;
};

const buildApiAuthFieldInput = (field: ApiAuthProviderFieldDTO) => {
	const type = String(field.type || "text").toLowerCase();
	const placeholder = field.description || field.label || field.name;
	if (field.name === "location") {
		return (
			<Select
				placeholder={placeholder}
				options={[
					{ label: "Header", value: "header" },
					{ label: "Query", value: "query" },
				]}
			/>
		);
	}
	if (field.sensitive && type !== "secretref") {
		return <Input.Password placeholder={placeholder} />;
	}
	if (type === "select") {
		return <Input placeholder={placeholder} />;
	}
	if (type === "url") {
		return <Input placeholder="https://example.com/oauth/token" />;
	}
	return <Input placeholder={placeholder} />;
};

const jsonObjectValidator = (label: string) => (_: any, value: string) => {
	if (!value) return Promise.resolve();
	try {
		parseObjectJson(value, label);
		return Promise.resolve();
	} catch (error: any) {
		return Promise.reject(new Error(error?.message || `${label} JSON 格式错误`));
	}
};

const normalizeOdsCode = (value?: string, fallback = "src") => {
	const text = String(value || "").trim().toLowerCase();
	const safe = text.replace(/[^a-z0-9_]+/g, "_").replace(/_+/g, "_").replace(/^_|_$/g, "");
	if (!safe) return fallback;
	return /^\d/.test(safe) ? `c_${safe}` : safe;
};

type OdsColumnOverride = Pick<OdsSourceColumnRequest, "include" | "targetName" | "targetDataType">;

type OdsColumnOverrides = Record<string, Record<string, OdsColumnOverride>>;

const resolveTableKey = (table: Pick<SchemaDiscoverTable, "schema" | "name">) => `${table.schema || ""}.${table.name}`;

const toOdsSourceTable = (
	table: SchemaDiscoverTable,
	overrides?: Record<string, OdsColumnOverride>
) => ({
	schema: table.schema,
	name: table.name,
	comment: table.comment,
	primaryKeys: table.primaryKeys || [],
	incrementalCandidates: table.incrementalCandidates || [],
	columns: (table.columns || []).map((column) => {
		const override = overrides?.[column.name] || {};
		return {
			name: column.name,
			include: override.include === false ? false : undefined,
			targetName: normalizeOdsCode(override.targetName || "", "") || undefined,
			dataType: column.dataType,
			nativeType: column.nativeType,
			targetDataType: String(override.targetDataType || "").trim() || undefined,
			nullable: column.nullable,
			comment: column.comment,
			primaryKey: column.primaryKey,
			indexed: column.indexed,
			incrementalCandidate: column.incrementalCandidate,
		};
	}),
});

type OdsWizardConfig = {
	odsSchema: string;
	systemCode: string;
	bizCode: string;
	syncMode: string;
	entityCode?: string;
};

const ODS_TYPE_OPTIONS = [
	{ label: "自动映射", value: "" },
	{ label: "text", value: "text" },
	{ label: "varchar(255)", value: "varchar(255)" },
	{ label: "integer", value: "integer" },
	{ label: "bigint", value: "bigint" },
	{ label: "numeric(18,2)", value: "numeric(18,2)" },
	{ label: "boolean", value: "boolean" },
	{ label: "date", value: "date" },
	{ label: "timestamp", value: "timestamp" },
	{ label: "jsonb", value: "jsonb" },
];

const defaultOdsWizardConfig = (record?: InfraDataSource | null): OdsWizardConfig => ({
	odsSchema: "ods",
	systemCode: normalizeOdsCode(record?.connectorKey || record?.type || record?.name),
	bizCode: "default",
	syncMode: "full_refresh",
});

export default function DataSourcesPage() {
	const navigate = useNavigate();
	const [list, setList] = useState<InfraDataSource[]>([]);
	const [loading, setLoading] = useState(false);
	const [connectors, setConnectors] = useState<InfraConnector[]>([]);
	const [connectorsLoading, setConnectorsLoading] = useState(false);
	const [drivers, setDrivers] = useState<InfraJdbcDriver[]>([]);
	const [driversLoading, setDriversLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [testingId, setTestingId] = useState<string | null>(null);
	const [schemaDiscoveringId, setSchemaDiscoveringId] = useState<string | null>(null);
	const [schemaModalOpen, setSchemaModalOpen] = useState(false);
	const [schemaDiscoverSource, setSchemaDiscoverSource] = useState<InfraDataSource | null>(null);
	const [schemaDiscoverResult, setSchemaDiscoverResult] = useState<SchemaDiscoverResponse | null>(null);
	const [odsPreviewingKey, setOdsPreviewingKey] = useState<string | null>(null);
	const [odsApplying, setOdsApplying] = useState(false);
	const [syncTaskCreating, setSyncTaskCreating] = useState(false);
	const [odsPrechecking, setOdsPrechecking] = useState(false);
	const [odsPreview, setOdsPreview] = useState<OdsGenerationPreviewResponse | null>(null);
	const [odsPrecheck, setOdsPrecheck] = useState<OdsPrecheckResponse | null>(null);
	const [odsRequest, setOdsRequest] = useState<OdsGenerationRequest | null>(null);
	const [odsConfig, setOdsConfig] = useState<OdsWizardConfig>(defaultOdsWizardConfig());
	const [odsColumnOverrides, setOdsColumnOverrides] = useState<OdsColumnOverrides>({});
	const [editing, setEditing] = useState<InfraDataSource | null>(null);
	const [excelModalOpen, setExcelModalOpen] = useState(false);
	const [excelUploading, setExcelUploading] = useState(false);
	const [excelParsing, setExcelParsing] = useState(false);
	const [excelPrepared, setExcelPrepared] = useState<ExcelImportPrepareResponse | null>(null);
	const [excelSheetName, setExcelSheetName] = useState<string | undefined>();
	const [excelHeaderRow, setExcelHeaderRow] = useState(1);
	const [excelDataStartRow, setExcelDataStartRow] = useState(2);
	const [excelDelimiter, setExcelDelimiter] = useState(",");
	const [excelDateFormat, setExcelDateFormat] = useState("yyyy-MM-dd HH:mm:ss");
	const [excelSkipErrors, setExcelSkipErrors] = useState(true);
	const [excelFillMerged, setExcelFillMerged] = useState(false);
	const [excelParseResult, setExcelParseResult] = useState<ExcelImportParseResponse | null>(null);
	const [rollbackOpen, setRollbackOpen] = useState(false);
	const [rollbackRequest, setRollbackRequest] = useState<RollbackRequest | null>(null);
	const [deptOptions, setDeptOptions] = useState<{ label: string; value: string }[]>([]);
	const [apiContract, setApiContract] = useState<ApiConnectorContractDTO | null>(null);
	const [apiContractLoading, setApiContractLoading] = useState(false);
	const [form] = Form.useForm();

	const loadDepts = useCallback(async () => {
		try {
			const tree = (await getOrgTree()) as OrgNode[];
			const flat: { label: string; value: string }[] = [];
			const walk = (nodes: OrgNode[]) => {
				for (const n of nodes) {
					if (n.deptCode) flat.push({ label: n.name, value: n.deptCode });
					if (n.children) walk(n.children);
				}
			};
			walk(Array.isArray(tree) ? tree : []);
			setDeptOptions(flat);
		} catch { /* ignore */ }
	}, []);

	const showImpact = (impact: DataSourceUpdateImpact | null) => {
		if (!impact || !impact.connectionChanged) {
			return;
		}
		const affected = impact.affectedTasks ?? 0;
		if (affected <= 0) {
			message.info("数据源连接已更新，未发现关联入湖任务。");
			return;
		}
		Modal.info({
			title: "数据源连接已更新",
			content: (
				<div className="space-y-2">
					<div>已影响入湖任务：{affected} 个。</div>
					<div>已登记接入变更：{impact.changeLogCreated ?? 0} 条。</div>
					<div className="text-xs text-slate-500">请前往“接入变更记录”确认任务变更影响。</div>
				</div>
			),
		});
	};

	const loadList = async () => {
		setLoading(true);
		try {
			const data = await dataSourcesService.list();
			setList(Array.isArray(data) ? data : []);
		} catch {
			setList([]);
		} finally {
			setLoading(false);
		}
	};

	const loadConnectors = async () => {
		setConnectorsLoading(true);
		try {
			const data = await connectorsService.list();
			setConnectors(Array.isArray(data) ? data : []);
		} catch {
			setConnectors([]);
		} finally {
			setConnectorsLoading(false);
		}
	};

	const loadDrivers = async () => {
		setDriversLoading(true);
		try {
			const data = await jdbcDriversService.list();
			setDrivers(Array.isArray(data) ? data : []);
		} catch {
			setDrivers([]);
		} finally {
			setDriversLoading(false);
		}
	};

	const loadApiContract = useCallback(async () => {
		if (apiContract || apiContractLoading) return;
		setApiContractLoading(true);
		try {
			const contract = await ingestionTaskAPI.getApiConnectorContract();
			if (contract?.authProviders?.length) {
				setApiContract(contract);
				return;
			}
			const authProviders = await ingestionTaskAPI.getApiAuthProviders();
			setApiContract({ ...(contract || {}), authProviders });
		} catch {
			setApiContract(null);
		} finally {
			setApiContractLoading(false);
		}
	}, [apiContract, apiContractLoading]);

	useEffect(() => {
		loadList();
		loadConnectors();
		loadDrivers();
		loadDepts();
	}, []);

	useEffect(() => {
		if (!editing || drivers.length === 0) return;
		const match = resolveDriverMatch(editing, drivers);
		if (match?.id) {
			form.setFieldsValue({ driverId: match.id });
		}
	}, [editing, drivers, form]);

	const openCreate = () => {
		setEditing(null);
		form.resetFields();
		setExcelParseResult(null);
		setModalOpen(true);
		loadConnectors();
		loadDrivers();
	};

	const applyConnectorDefaults = (connectorKey?: string) => {
		const connector = connectors.find((item) => item.connectorKey === connectorKey);
		const fallback = TYPE_OPTIONS.find((option) => inferConnectorKey(option.value) === connectorKey);
		if (!connector && !fallback) return;
		const nextType = connector?.sourceType || fallback?.value || connectorKey;
		if (!nextType) return;
		form.setFieldsValue({
			type: nextType,
			readerType:
				connectorKey === "http-api"
					? apiContract?.defaultReaderType || "httpreader"
					: form.getFieldValue("readerType"),
		});
		handleTypeChange(nextType);
	};

	const handleTypeChange = (type: string) => {
		if (isApiSourceType(type)) {
			form.setFieldsValue({
				driverId: undefined,
				driverClass: undefined,
				driverVersion: undefined,
				jdbcUrl: undefined,
				username: undefined,
				password: undefined,
				readerType: apiContract?.defaultReaderType || "httpreader",
				apiAuthProvider: form.getFieldValue("apiAuthProvider") || "none",
			});
			void loadApiContract();
			return;
		}
		if (!isJdbcType(type, form.getFieldValue("jdbcUrl"))) {
			form.setFieldsValue({ readerType: form.getFieldValue("readerType") || "" });
		}
	};

	const resolveDriverMatch = (record: InfraDataSource | null, driverList: InfraJdbcDriver[]) => {
		if (!record || !Array.isArray(driverList) || driverList.length === 0) return undefined;
		const props = record.props || {};
		const driverClass = String(props?.driverClass || "").trim().toLowerCase();
		const driverVersion = String(props?.driverVersion || "").trim().toLowerCase();
		if (!driverClass && !driverVersion) return undefined;
		return driverList.find((driver) => {
			const classMatch =
				driverClass &&
				driver.driverClass &&
				driver.driverClass.trim().toLowerCase() === driverClass;
			const versionMatch =
				driverVersion &&
				(driver.fileName?.trim().toLowerCase() === driverVersion ||
					driver.version?.trim().toLowerCase() === driverVersion);
			return classMatch || versionMatch;
		});
	};

	const openEdit = (record: InfraDataSource) => {
		if (isAdminManagedSource(record)) {
			message.info("默认数据湖由系统管理端维护，平台侧不支持编辑");
			return;
		}
		const props = record.props || {};
		const apiSource = isApiSourceType(record.type);
		setEditing(record);
		form.setFieldsValue({
			name: record.name,
			connectorKey: record.connectorKey || inferConnectorKey(record.type, props),
			type: record.type,
			jdbcUrl: record.jdbcUrl,
			username: record.username,
			description: record.description,
			readerType: record.props?.readerType || record.props?.reader,
			driverClass: record.props?.driverClass,
			driverVersion: record.props?.driverVersion,
			propsJson: record.props ? JSON.stringify(omitReaderType(record.props), null, 2) : "",
			apiBaseUrl: apiSource ? readApiBaseUrl(props) : undefined,
			apiAuthProvider: apiSource ? readApiAuthProvider(props) : undefined,
			apiAuthConfig: apiSource ? readApiAuthConfig(props) : undefined,
			apiAuthSecrets: {},
			apiAuthSecretRefs: apiSource ? readApiAuthSecretRefs(props) : undefined,
			apiDefaultHeadersJson: apiSource ? stringifyJson(readApiConfigPart(props, "defaultHeaders")) : "",
			apiRequestPolicyJson: apiSource ? stringifyJson(readApiConfigPart(props, "requestPolicy")) : "",
			apiRateLimitJson: apiSource ? stringifyJson(readApiConfigPart(props, "rateLimit")) : "",
			apiTlsJson: apiSource ? stringifyJson(readApiConfigPart(props, "tls")) : "",
		});
		if (apiSource) {
			void loadApiContract();
		}
		const match = resolveDriverMatch(record, drivers);
		if (match?.id) {
			form.setFieldsValue({ driverId: match.id });
		}
		setExcelParseResult(null);
		setModalOpen(true);
		loadDrivers();
	};

	const handleDelete = (record: InfraDataSource) => {
		if (isAdminManagedSource(record)) {
			message.info("默认数据湖由系统管理端维护，平台侧不支持删除");
			return;
		}
		Modal.confirm({
			title: "确认删除",
			content: `确定删除数据源 "${record.name}" 吗？`,
			okType: "danger",
			onOk: async () => {
				try {
					await dataSourcesService.remove(record.id);
					message.success("已删除数据源");
					loadList();
				} catch {
					// handled by global interceptor
				}
			},
		});
	};

	const showTestResult = (name: string, result: ConnectionTestResult) => {
		const isOk = result.success;
		const details: string[] = [];
		if (result.elapsedMillis != null) details.push(`耗时：${result.elapsedMillis} ms`);
		if (result.engineVersion) details.push(`数据库版本：${result.engineVersion}`);
		if (result.driverVersion) details.push(`驱动版本：${result.driverVersion}`);
		if (result.errorType) details.push(`失败类型：${result.errorType}`);
		if (result.suggestion) details.push(`处理建议：${result.suggestion}`);
		if (result.warnings?.length) details.push(`警告：${result.warnings.join("; ")}`);

		Modal[isOk ? "success" : "error"]({
			title: isOk ? `${name} 连接成功` : `${name} 连接失败`,
			icon: isOk ? <CheckCircleOutlined /> : <CloseCircleOutlined />,
			content: (
				<div style={{ maxHeight: 300, overflow: "auto" }}>
					{result.message && <div style={{ marginBottom: 8, wordBreak: "break-all" }}>{result.message}</div>}
					{details.length > 0 && (
						<div style={{ fontSize: 12, color: "#666" }}>
							{details.map((d, i) => (
								<div key={i}>{d}</div>
							))}
						</div>
					)}
				</div>
			),
			width: 520,
		});
	};

	const handleTest = async (record: InfraDataSource) => {
		if (!record.jdbcUrl && !isApiSourceType(record.type)) {
			message.warning("当前数据源类型暂不支持连接测试");
			return;
		}
		try {
			setTestingId(record.id);
			const result = await dataSourcesService.test(record.id);
			if (result?.success) {
				showTestResult(record.name, result);
			} else {
				showTestResult(record.name, result || { success: false, message: "未获取到测试结果" });
			}
			loadList();
		} catch (error: any) {
			Modal.error({
				title: `${record.name} 连接测试异常`,
				content: error?.message || "连接测试请求失败，请检查网络或服务状态。",
				width: 520,
			});
		} finally {
			setTestingId(null);
		}
	};

	const showSchemaDiscoverResult = (record: InfraDataSource, result: SchemaDiscoverResponse) => {
		setSchemaDiscoverSource(record);
		setSchemaDiscoverResult(result);
		setOdsConfig(defaultOdsWizardConfig(record));
		setOdsPreview(null);
		setOdsPrecheck(null);
		setOdsRequest(null);
		setOdsColumnOverrides({});
		setSchemaModalOpen(true);
	};

	const updateOdsConfig = (patch: Partial<OdsWizardConfig>) => {
		setOdsConfig((prev) => ({ ...prev, ...patch }));
		setOdsPreview(null);
		setOdsPrecheck(null);
		setOdsRequest(null);
	};

	const updateOdsColumnOverride = (
		table: SchemaDiscoverTable,
		column: SchemaDiscoverColumn,
		patch: OdsColumnOverride
	) => {
		const tableKey = resolveTableKey(table);
		setOdsColumnOverrides((prev) => {
			const current = prev[tableKey]?.[column.name] || {};
			return {
				...prev,
				[tableKey]: {
					...(prev[tableKey] || {}),
					[column.name]: { ...current, ...patch },
				},
			};
		});
		setOdsPreview(null);
		setOdsPrecheck(null);
		setOdsRequest(null);
	};

	const buildOdsRequest = (record: InfraDataSource, tables: SchemaDiscoverTable[]): OdsGenerationRequest => ({
		odsSchema: normalizeOdsCode(odsConfig.odsSchema, "ods"),
		systemCode: normalizeOdsCode(odsConfig.systemCode || record.connectorKey || record.type || record.name),
		bizCode: normalizeOdsCode(odsConfig.bizCode || "default", "default"),
		entityCode: tables.length === 1 && odsConfig.entityCode ? normalizeOdsCode(odsConfig.entityCode, tables[0].name) : undefined,
		includeTechnicalColumns: true,
		includeRawJson: true,
		syncMode: odsConfig.syncMode || "full_refresh",
		tables: tables.map((table) => toOdsSourceTable(table, odsColumnOverrides[resolveTableKey(table)])),
	});

	const resolveCommonIncrementalCandidate = (tables?: OdsGenerationRequest["tables"]) => {
		if (!tables?.length) return "";
		const originalNames = new Map<string, string>();
		const normalizeCandidates = (table: OdsGenerationRequest["tables"][number]) =>
			(table.incrementalCandidates || [])
				.filter(Boolean)
				.map((candidate) => {
					const normalized = String(candidate).trim().toLowerCase();
					originalNames.set(normalized, String(candidate).trim());
					return normalized;
				});
		let commonKeys = normalizeCandidates(tables[0]);
		for (const table of tables.slice(1)) {
			const candidates = normalizeCandidates(table);
			commonKeys = commonKeys.filter((candidate) => candidates.includes(candidate));
		}
		if (!commonKeys?.length) return "";
		for (const preferred of ["updated_at", "update_time", "modified_at", "modify_time", "last_updated_at"]) {
			if (commonKeys.includes(preferred)) return originalNames.get(preferred) || preferred;
		}
		const first = commonKeys[0];
		return originalNames.get(first) || first;
	};

	const handleOdsPreview = async (record: InfraDataSource, tables: SchemaDiscoverTable[], loadingKey?: string) => {
		if (!tables.length) {
			message.warning("请选择至少一张表");
			return;
		}
		const request = buildOdsRequest(record, tables);
		const key = loadingKey || tables.map((table) => `${table.schema || ""}.${table.name}`).join("|");
		setOdsPreviewingKey(key);
		try {
			const result = await dataSourcesService.odsPreview(record.id, request);
			setOdsRequest(request);
			setOdsPreview(result);
			setOdsPrecheck(null);
		} catch {
			// handled by global interceptor
		} finally {
			setOdsPreviewingKey(null);
		}
	};

	const renderOdsPrecheckSummary = (result: OdsPrecheckResponse) => {
		const failed = (result.rules || []).filter((rule) => String(rule.status).toUpperCase() === "FAIL");
		const warned = (result.rules || []).filter((rule) => String(rule.status).toUpperCase() === "WARN");
		const rows = [...failed, ...warned].slice(0, 6);
		return (
			<div className="space-y-2">
				<div>
					规则 {result.totalRules || 0} 条，通过 {result.passedRules || 0}，警告 {result.warningRules || 0}，失败 {result.failedRules || 0}。
				</div>
				{rows.length ? (
					<div className="space-y-1 text-xs text-slate-500">
						{rows.map((rule) => (
							<div key={`${rule.code}.${rule.target}`}>
								<Tag color={String(rule.status).toUpperCase() === "FAIL" ? "red" : "gold"}>{rule.status}</Tag>
								{rule.target ? `${rule.target}：` : ""}
								{rule.message}
								{rule.suggestion ? `；建议：${rule.suggestion}` : ""}
							</div>
						))}
					</div>
				) : null}
			</div>
		);
	};

	const runOdsPrecheck = async (silent = false) => {
		if (!schemaDiscoverSource || !odsRequest) {
			message.warning("请先预览 ODS 生成结果");
			return null;
		}
		setOdsPrechecking(true);
		try {
			const result = await dataSourcesService.odsPrecheck(schemaDiscoverSource.id, odsRequest);
			setOdsPrecheck(result);
			if (!silent) {
				const status = String(result?.status || "").toUpperCase();
				if (status === "FAIL") {
					Modal.error({ title: "提交前预检失败", content: renderOdsPrecheckSummary(result), width: 640 });
				} else if (status === "WARN") {
					Modal.warning({ title: "提交前预检存在警告", content: renderOdsPrecheckSummary(result), width: 640 });
				} else {
					Modal.success({ title: "提交前预检通过", content: renderOdsPrecheckSummary(result), width: 640 });
				}
			}
			return result;
		} catch {
			return null;
		} finally {
			setOdsPrechecking(false);
		}
	};

	const confirmWarnPrecheck = (result: OdsPrecheckResponse) =>
		new Promise<boolean>((resolve) => {
			Modal.confirm({
				title: "预检存在警告，是否继续生成同步任务？",
				content: renderOdsPrecheckSummary(result),
				okText: "继续生成",
				cancelText: "返回调整",
				width: 680,
				onOk: () => resolve(true),
				onCancel: () => resolve(false),
			});
		});

	const ensureOdsPrecheckBeforeCreate = async () => {
		const result = await runOdsPrecheck(true);
		if (!result) return false;
		const status = String(result.status || "").toUpperCase();
		if (status === "FAIL") {
			Modal.error({ title: "提交前预检失败", content: renderOdsPrecheckSummary(result), width: 640 });
			return false;
		}
		if (status === "WARN") {
			return confirmWarnPrecheck(result);
		}
		return true;
	};

	const handleOdsApply = async () => {
		if (!schemaDiscoverSource || !odsRequest) {
			message.warning("请先预览 ODS 生成结果");
			return;
		}
		setOdsApplying(true);
		try {
			const result = await dataSourcesService.odsApply(schemaDiscoverSource.id, odsRequest);
			message.success(
				`已生成 ${result?.mappingsUpserted || 0} 个 ODS 映射，写入 ${result?.columnsUpserted || 0} 个字段`
			);
			setOdsPreview((prev) => (prev ? { ...prev, warnings: result?.warnings || prev.warnings } : prev));
		} catch {
			// handled by global interceptor
		} finally {
			setOdsApplying(false);
		}
	};

	const handleCreateSyncTask = async () => {
		if (!schemaDiscoverSource || !odsRequest) {
			message.warning("请先预览 ODS 生成结果");
			return;
		}
		if (
			["incremental", "timestamp_incremental"].includes(String(odsRequest.syncMode || "").toLowerCase()) &&
			!resolveCommonIncrementalCandidate(odsRequest.tables)
		) {
			message.warning("增量同步需要所选表存在相同的增量字段候选");
			return;
		}
		setSyncTaskCreating(true);
		try {
			const precheckOk = await ensureOdsPrecheckBeforeCreate();
			if (!precheckOk) return;
			const applyResult = await dataSourcesService.odsApply(schemaDiscoverSource.id, odsRequest);
			const draft = await dataSourcesService.syncTaskDraft(schemaDiscoverSource.id, odsRequest);
			if (!draft?.payload) {
				throw new Error("未生成同步任务配置");
			}
			const createResult: any = await createIngestionTask(draft.payload);
			const task = createResult?.task || createResult?.data?.task || createResult?.data;
			const taskId = task?.id ? ` #${task.id}` : "";
			message.success(`已生成同步任务${taskId}：${draft.taskName || task?.name || "未命名任务"}`);
			setOdsPreview((prev) => (prev ? { ...prev, warnings: applyResult?.warnings || prev.warnings } : prev));
		} catch (error: any) {
			message.error(error?.message || "生成同步任务失败");
		} finally {
			setSyncTaskCreating(false);
		}
	};

	const renderOdsColumnEditor = (table: SchemaDiscoverTable) => {
		const tableKey = resolveTableKey(table);
		const overrides = odsColumnOverrides[tableKey] || {};
		const columns = table.columns || [];
		if (!columns.length) {
			return null;
		}
		return (
			<details className="mt-2">
				<summary className="cursor-pointer text-xs text-slate-600">字段选择、重命名和类型覆盖</summary>
				<div className="mt-2">
					<CompactTable<SchemaDiscoverColumn>
						size="small"
						rowKey="name"
						pagination={false}
						dataSource={columns}
						scroll={{ x: 840, y: 240 }}
						columns={[
							{
								title: "包含",
								key: "include",
								width: 72,
								render: (_: any, column: SchemaDiscoverColumn) => (
									<Switch
										size="small"
										checked={overrides[column.name]?.include !== false}
										onChange={(checked) => updateOdsColumnOverride(table, column, { include: checked })}
									/>
								),
							},
							{
								title: "源字段",
								dataIndex: "name",
								sorter: (a, b) => (a.name || "").localeCompare(b.name || ""),
								key: "name",
								width: 180,
								render: (value: string, column: SchemaDiscoverColumn) => (
									<Space size={4} wrap>
										<Text code>{value}</Text>
										{column.primaryKey ? <Tag color="blue">PK</Tag> : null}
										{column.incrementalCandidate ? <Tag color="green">增量</Tag> : null}
									</Space>
								),
							},
							{
								title: "目标字段",
								key: "targetName",
								width: 180,
								render: (_: any, column: SchemaDiscoverColumn) => (
									<Input
										size="small"
										allowClear
										disabled={overrides[column.name]?.include === false}
										placeholder={normalizeOdsCode(column.name, column.name)}
										value={overrides[column.name]?.targetName}
										onChange={(event) =>
											updateOdsColumnOverride(table, column, { targetName: event.target.value })
										}
									/>
								),
							},
							{
								title: "目标类型",
								key: "targetDataType",
								width: 170,
								render: (_: any, column: SchemaDiscoverColumn) => (
									<Select
										size="small"
										className="w-full"
										showSearch
										disabled={overrides[column.name]?.include === false}
										value={overrides[column.name]?.targetDataType || ""}
										options={ODS_TYPE_OPTIONS}
										onChange={(value) => updateOdsColumnOverride(table, column, { targetDataType: value })}
									/>
								),
							},
							{
								title: "源类型",
								key: "sourceType",
								width: 160,
								render: (_: any, column: SchemaDiscoverColumn) => column.dataType || column.nativeType || "-",
							},
							{
								title: "说明",
								dataIndex: "comment",
								key: "comment",
								ellipsis: true,
								render: (value?: string) => value || "-",
							},
						]}
					/>
				</div>
			</details>
		);
	};

	const renderOdsPlan = (plan: OdsTablePlan) => (
		<div key={`${plan.odsSchema}.${plan.odsTable}`} className="rounded border border-slate-200 p-3">
			<div className="mb-2 flex flex-wrap items-center gap-2">
				<Text strong>{plan.odsSchema}.{plan.odsTable}</Text>
				<Tag color="blue">{plan.columns?.length || 0} 源字段</Tag>
				<Tag>{plan.technicalColumns?.length || 0} 技术字段</Tag>
			</div>
			<div className="mb-2 text-xs text-slate-500">
				{plan.sourceSchema ? `${plan.sourceSchema}.` : ""}{plan.sourceTable}
				{plan.incrementalCandidates?.length ? ` · 增量候选：${plan.incrementalCandidates.join(", ")}` : ""}
			</div>
			{plan.warnings?.length ? (
				<Alert type="warning" showIcon className="mb-2" message={plan.warnings.join("；")} />
			) : null}
			<div className="mb-2 text-xs text-slate-500">
				{(plan.columns || [])
					.slice(0, 10)
					.map((column) => `${column.targetName}:${column.odsType || "text"}`)
					.join(" · ") || "未生成字段"}
			</div>
			<details>
				<summary className="cursor-pointer text-xs text-slate-600">查看 DDL / dbt source 片段</summary>
				<pre className="mt-2 max-h-64 overflow-auto rounded bg-slate-950 p-3 text-xs text-slate-50">
					{`${plan.createTableSql || ""}\n\n${plan.dbtSourceYaml || ""}`}
				</pre>
			</details>
		</div>
	);

	const handleSchemaDiscover = async (record: InfraDataSource, forceRefresh = false) => {
		if (!record.jdbcUrl) {
			message.info("API / 文件数据源不适用 JDBC Schema 探测，请在入湖任务中配置资源路径后测试执行。");
			return;
		}
		setSchemaDiscoveringId(record.id);
		try {
			const result = await dataSourcesService.schemaDiscover(record.id, {
				maxTables: 100,
				sampleLimit: 0,
				includeColumns: true,
				includeIndexes: true,
				includeSample: false,
				useCache: true,
				forceRefresh,
			});
			if (!result || result.status === "FAILED") {
				Modal.error({
					title: `${record.name} Schema 探测失败`,
					content: result?.error || "未获取到探测结果",
				});
				return;
			}
			showSchemaDiscoverResult(record, result);
		} catch {
			// handled by global interceptor
		} finally {
			setSchemaDiscoveringId(null);
		}
	};

	const handleDriverSelect = (id?: string) => {
		if (!id) return;
		const driver = drivers.find((item) => item.id === id);
		if (!driver) return;
		const next: Record<string, string> = {};
		if (driver.driverClass) {
			next.driverClass = driver.driverClass;
		}
		const versionHint = driver.fileName || driver.version;
		if (versionHint) {
			next.driverVersion = versionHint;
		}
		form.setFieldsValue(next);
	};

	const buildApiSecrets = (values: Record<string, any>, descriptor?: ApiAuthProviderDescriptorDTO) => {
		const secrets: Record<string, any> = {};
		const secretValues = asRecord(values.apiAuthSecrets) || {};
		(descriptor?.fields || []).forEach((field) => {
			if (!field.sensitive || String(field.type || "").toLowerCase() === "secretref") return;
			const value = secretValues[field.name];
			if (typeof value === "string" ? value.trim() : value != null) {
				secrets[field.name] = typeof value === "string" ? value.trim() : value;
			}
		});
		return Object.keys(secrets).length ? secrets : undefined;
	};

	const buildApiProps = (
		values: Record<string, any>,
		baseProps: Record<string, any> | undefined,
		descriptor?: ApiAuthProviderDescriptorDTO,
	) => {
		const baseUrl = String(values.apiBaseUrl || "").trim();
		const authProvider = String(values.apiAuthProvider || "none").trim() || "none";
		const defaultHeaders = parseObjectJson(values.apiDefaultHeadersJson, "默认请求头");
		const requestPolicy = parseObjectJson(values.apiRequestPolicyJson, "请求策略");
		const rateLimit = parseObjectJson(values.apiRateLimitJson, "限流策略");
		const tls = parseObjectJson(values.apiTlsJson, "TLS 策略");
		const rawAuthConfig = cleanRecord(values.apiAuthConfig);
		const configFieldNames = new Set(
			(descriptor?.fields || [])
				.filter((field) => !field.sensitive || String(field.type || "").toLowerCase() === "secretref")
				.map((field) => field.name)
		);
		const authConfig = cleanRecord(
			configFieldNames.size
				? Object.fromEntries(Object.entries(rawAuthConfig || {}).filter(([key]) => configFieldNames.has(key)))
				: rawAuthConfig
		);
		const secretFieldNames = new Set(
			(descriptor?.fields || [])
				.filter((field) => field.sensitive && String(field.type || "").toLowerCase() !== "secretref")
				.map((field) => field.name)
		);
		const existingSecretRefs = cleanRecord(
			Object.fromEntries(
				Object.entries(cleanRecord(values.apiAuthSecretRefs) || {}).filter(([key]) => !secretFieldNames.size || secretFieldNames.has(key))
			)
		) || {};
		const nextSecretRefs: Record<string, string> = {};
		(descriptor?.fields || []).forEach((field) => {
			if (!field.sensitive || String(field.type || "").toLowerCase() === "secretref") return;
			const value = asRecord(values.apiAuthSecrets)?.[field.name];
			if (typeof value === "string" ? value.trim() : value != null) {
				nextSecretRefs[field.name] = field.name;
			}
		});
		const secretRefs = cleanRecord({ ...existingSecretRefs, ...nextSecretRefs });
		const auth =
			authProvider === "none"
				? { provider: "none" }
				: {
						provider: authProvider,
						...(authConfig ? { config: authConfig } : {}),
						...(secretRefs ? { secretRefs } : {}),
					};
		const apiNode = {
			baseUrl,
			...(defaultHeaders ? { defaultHeaders } : {}),
			...(requestPolicy ? { requestPolicy } : {}),
			...(rateLimit ? { rateLimit } : {}),
			...(tls ? { tls } : {}),
			auth,
		};
		const props = {
			...(baseProps || {}),
			baseUrl,
			authProvider,
			auth,
			api: apiNode,
			readerConfig: {
				...(asRecord(baseProps?.readerConfig) || {}),
				...apiNode,
			},
			connectorType: "api",
			readerType: apiContract?.defaultReaderType || "httpreader",
			sourceCategory: "api",
			contractVersion: apiContract?.contractVersion || baseProps?.contractVersion || "1.0.0",
		};
		return props;
	};

	const handleSave = async () => {
		try {
			const values = await form.validateFields();
			setSaving(true);
			const apiSource = isApiSourceType(values.type);
			const jdbc = !apiSource && isJdbcType(values.type, values.jdbcUrl);
			let props = values.propsJson ? parseJson(values.propsJson) : undefined;
			const selectedApiDescriptor = (apiContract?.authProviders || []).find(
				(item) => String(item.id).toLowerCase() === String(values.apiAuthProvider || "none").toLowerCase()
			);
			if (apiSource) {
				props = buildApiProps(values, asRecord(props), selectedApiDescriptor);
			} else if (values.readerType) {
				props = { ...(props || {}), readerType: values.readerType };
			}
			const selectedDriver = drivers.find((item) => item.id === values.driverId);
			const resolvedDriverClass = String(values.driverClass || selectedDriver?.driverClass || "").trim();
			const resolvedDriverVersion = String(
				values.driverVersion || selectedDriver?.fileName || selectedDriver?.version || ""
			).trim();
			if (resolvedDriverClass) {
				props = { ...(props || {}), driverClass: resolvedDriverClass };
			}
			if (resolvedDriverVersion) {
				props = { ...(props || {}), driverVersion: resolvedDriverVersion };
			}
			const apiSecrets = apiSource ? buildApiSecrets(values, selectedApiDescriptor) : undefined;
			const payload: DataSourceUpsertPayload = {
				name: String(values.name).trim(),
				type: String(values.type).trim(),
				connectorKey: normalizeConnectorKey(values.connectorKey) || inferConnectorKey(values.type, props),
				jdbcUrl: jdbc ? String(values.jdbcUrl || "").trim() || undefined : undefined,
				username: jdbc ? String(values.username || "").trim() || undefined : undefined,
				description: String(values.description || "").trim() || undefined,
				props: props && Object.keys(props).length ? props : undefined,
				secrets: apiSource ? apiSecrets : values.password ? { password: values.password } : undefined,
			};
			if (editing) {
				const impact = await dataSourcesService.updateWithImpact(editing.id, payload);
				message.success("数据源已更新");
				showImpact(impact || null);
			} else {
				await dataSourcesService.create(payload);
				message.success("数据源已创建");
			}
			setModalOpen(false);
			loadList();
		} catch (error: any) {
			if (error?.errorFields) return;
		} finally {
			setSaving(false);
		}
	};

	const resetExcelModal = () => {
		setExcelPrepared(null);
		setExcelParseResult(null);
		setExcelSheetName(undefined);
		setExcelHeaderRow(1);
		setExcelDataStartRow(2);
		setExcelDelimiter(",");
		setExcelDateFormat("yyyy-MM-dd HH:mm:ss");
		setExcelSkipErrors(true);
		setExcelFillMerged(true);
	};

	const openExcelModal = () => {
		resetExcelModal();
		setExcelModalOpen(true);
	};

	const handleExcelUpload = async (options: UploadRequestOption) => {
		const file = options.file as File;
		if (!file) return;
		const maxSize = 200 * 1024 * 1024;
		if (file.size > maxSize) {
			message.error("文件超过 200MB 限制");
			options.onError?.(new Error("file_too_large"));
			return;
		}
		setExcelUploading(true);
		try {
			const resp = await dataSourcesService.excelPrepare(file);
			setExcelPrepared(resp);
			setExcelSheetName(resp.sheets?.[0]?.name);
			message.success("文件已上传，请选择 Sheet 并解析");
			options.onSuccess?.(resp as any);
		} catch (error: any) {
			options.onError?.(error);
		} finally {
			setExcelUploading(false);
		}
	};

	const applyExcelResultToForm = (resp: ExcelImportParseResponse) => {
		const columns = resp.columns || [];
		const readerConfig = {
			path: [resp.csvContainerPath || resp.csvPath],
			column: columns.map((col, index) => ({
				index,
				name: col.name,
				type: col.dataType || "string",
			})),
			fieldDelimiter: excelDelimiter || ",",
			encoding: "UTF-8",
			skipHeader: true,
			fileType: "csv",
		};
		form.setFieldsValue({
			readerType: "txtfilereader",
			propsJson: JSON.stringify({ readerConfig }, null, 2),
		});
	};

	const handleExcelParse = async () => {
		if (!excelPrepared?.fileId) {
			message.warning("请先上传 Excel/CSV");
			return;
		}
		setExcelParsing(true);
		try {
			const resp = await dataSourcesService.excelParse({
				fileId: excelPrepared.fileId,
				sheetName: excelSheetName,
				headerRow: excelHeaderRow,
				dataStartRow: excelDataStartRow,
				delimiter: excelDelimiter,
				previewLimit: 20,
				skipErrors: excelSkipErrors,
				fillMerged: excelFillMerged,
				dateFormat: excelDateFormat,
			});
			setExcelParseResult(resp);
			applyExcelResultToForm(resp);
			message.success("解析完成，字段配置已填充");
			setExcelModalOpen(false);
		} catch {
			// handled by global interceptor
		} finally {
			setExcelParsing(false);
		}
	};

	const columns = useMemo(
			() => [
				{ title: "名称", dataIndex: "name", key: "name", width: 180 , sorter: (a: InfraDataSource, b: InfraDataSource) => (a.name || "").localeCompare(b.name || "") },
				{
					title: "连接器",
					dataIndex: "connectorName",
					sorter: (a: InfraDataSource, b: InfraDataSource) => (a.connectorName || "").localeCompare(b.connectorName || ""),
					key: "connectorName",
					width: 180,
					render: (value: string, record: InfraDataSource) => value || record.connectorKey || inferConnectorKey(record.type, record.props) || "-",
				},
				{ title: "类型", dataIndex: "type", key: "type", width: 120 },
			{
				title: "连接地址",
				dataIndex: "jdbcUrl",
				key: "jdbcUrl",
				ellipsis: true,
				render: (value: string, record: InfraDataSource) =>
					isApiSourceType(record.type) ? readApiBaseUrl(record.props) || "-" : value || "-",
			},
			{ title: "用户名", dataIndex: "username", key: "username", width: 140 , sorter: (a: InfraDataSource, b: InfraDataSource) => (a.username || "").localeCompare(b.username || "") },
			{
				title: "状态",
				dataIndex: "status",
				key: "status",
				width: 120,
				render: (value: string) => {
					const text = value || "active";
					return <Tag color={text === "active" ? "success" : "default"}>{text}</Tag>;
				},
			},
			{
				title: "最近验证",
				dataIndex: "lastVerifiedAt",
				sorter: (a: InfraDataSource, b: InfraDataSource) => {
					const ta = a.lastVerifiedAt ? new Date(a.lastVerifiedAt as any).getTime() : 0;
					const tb = b.lastVerifiedAt ? new Date(b.lastVerifiedAt as any).getTime() : 0;
					return ta - tb;
				},
				key: "lastVerifiedAt",
				width: 180,
				render: (value: string) => formatTime(value),
			},
			{
				title: "操作",
				key: "action",
				width: 220,
				render: (_: any, record: InfraDataSource) => {
					const adminManaged = isAdminManagedSource(record);
					const apiSource = isApiSourceType(record.type);
					return (
						<Space>
							{!apiSource && (
								<Button
									size="small"
									loading={schemaDiscoveringId === record.id}
									onClick={() => handleSchemaDiscover(record)}
								>
									探测
								</Button>
							)}
							<Button size="small" icon={<ExperimentOutlined />} loading={testingId === record.id} onClick={() => handleTest(record)}>
								测试
							</Button>
							<Button size="small" icon={<EditOutlined />} disabled={adminManaged} onClick={() => openEdit(record)}>
								编辑
							</Button>
							<Button size="small" danger icon={<DeleteOutlined />} disabled={adminManaged} onClick={() => handleDelete(record)}>
								删除
							</Button>
							{record.id && (
								<Button size="small" danger onClick={() => { setRollbackRequest({ level: 3, scope: "datasource", dataSourceId: record.id }); setRollbackOpen(true); }}>
									全链路回退
								</Button>
							)}
						</Space>
					);
				},
			},
		],
		[schemaDiscoveringId, testingId]
	);

	const driverOptions = useMemo(
		() =>
			drivers.map((driver) => ({
				value: driver.id,
				label: `${driver.fileName}${driver.version ? ` (${driver.version})` : ""}${driver.driverClass ? ` · ${driver.driverClass}` : ""}`,
			})),
		[drivers]
	);

	const connectorOptions = useMemo(() => {
		if (!connectors.length) {
			return TYPE_OPTIONS.map((option) => ({ ...option, value: inferConnectorKey(option.value) || option.value }));
		}
		return connectors.map((connector) => ({
			value: connector.connectorKey,
			label: `${connector.name}${connector.defaultEngine ? ` · ${connector.defaultEngine}` : ""}`,
		}));
	}, [connectors]);

	const connectorValue = Form.useWatch("connectorKey", form);
	const typeValue = Form.useWatch("type", form);
	const jdbcValue = Form.useWatch("jdbcUrl", form);
	const selectedConnector = useMemo(
		() => connectors.find((item) => item.connectorKey === connectorValue),
		[connectorValue, connectors]
	);
	const apiSource = isApiSourceType(typeValue);
	const jdbcRequired = !apiSource && isJdbcType(typeValue, jdbcValue);
	const fileSource = isFileSource(typeValue);
	const apiAuthProviderValue = Form.useWatch("apiAuthProvider", form);
	const apiAuthProviders = useMemo(
		() =>
			apiContract?.authProviders?.length
				? apiContract.authProviders
				: [{ id: "none", label: "无鉴权", description: "不向请求注入任何鉴权信息", fields: [] }],
		[apiContract]
	);
	const selectedApiAuthProvider = useMemo(
		() =>
			apiAuthProviders.find(
				(item) => String(item.id).toLowerCase() === String(apiAuthProviderValue || "none").toLowerCase()
			),
		[apiAuthProviderValue, apiAuthProviders]
	);

	useEffect(() => {
		if (modalOpen && apiSource) {
			void loadApiContract();
		}
	}, [apiSource, loadApiContract, modalOpen]);

	return (
		<Card
			title="数据源连接"
			extra={
				<Space>
					<Button icon={<ReloadOutlined />} onClick={loadList} disabled={loading}>
						刷新
					</Button>
					<Button icon={<AppstoreOutlined />} onClick={() => navigate("/foundation/connectors")}>
						连接器目录
					</Button>
					<Button onClick={() => navigate("/foundation/jdbc-drivers")}>JDBC 驱动管理</Button>
					<Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
						新增数据源
					</Button>
				</Space>
			}
		>
			<CompactTable
				rowKey="id"
				columns={columns as any}
				dataSource={list}
				loading={loading}
				scroll={{ x: 1100 }}
				pagination={{ pageSize: 12 }}
			/>

			<Modal
				title={`${schemaDiscoverSource?.name || "数据源"} Schema 探测`}
				open={schemaModalOpen}
				onCancel={() => setSchemaModalOpen(false)}
				width={920}
				footer={[
					<Button
						key="refresh"
						loading={Boolean(schemaDiscoverSource && schemaDiscoveringId === schemaDiscoverSource.id)}
						onClick={() => schemaDiscoverSource && handleSchemaDiscover(schemaDiscoverSource, true)}
					>
						重新探测
					</Button>,
					<Button key="close" onClick={() => setSchemaModalOpen(false)}>
						关闭
					</Button>,
					<Button
						key="precheck"
						disabled={!odsPreview}
						loading={odsPrechecking}
						onClick={() => void runOdsPrecheck(false)}
					>
						提交前预检
					</Button>,
					<Button
						key="apply"
						type="primary"
						disabled={!odsPreview}
						loading={odsApplying}
						onClick={handleOdsApply}
					>
						生成 ODS 映射与 dbt source
					</Button>,
					<Button
						key="task"
						type="primary"
						disabled={!odsPreview}
						loading={syncTaskCreating}
						onClick={handleCreateSyncTask}
					>
						生成同步任务
					</Button>,
				]}
			>
				<div className="space-y-4">
					<div className="flex flex-wrap items-center gap-2 text-xs text-slate-500">
						<span>
							{schemaDiscoverResult?.databaseProduct || "未知数据库"} · {schemaDiscoverResult?.tables?.length || 0} 张表 · {schemaDiscoverResult?.elapsedMs ?? 0} ms
						</span>
						{schemaDiscoverResult?.cached ? <Tag color="gold">缓存</Tag> : <Tag color="green">实时</Tag>}
					</div>
					{schemaDiscoverResult?.drift ? (
						<Alert
							type="warning"
							showIcon
							message={`Schema drift：新增 ${schemaDiscoverResult.drift.addedTables || 0} 表，删除 ${schemaDiscoverResult.drift.removedTables || 0} 表，变更 ${schemaDiscoverResult.drift.changedTables || 0} 表`}
							description={schemaDiscoverResult.drift.detailsJson ? (
								<details>
									<summary className="cursor-pointer">查看差异详情</summary>
									<pre className="mt-2 max-h-48 overflow-auto text-xs">{schemaDiscoverResult.drift.detailsJson}</pre>
								</details>
							) : undefined}
						/>
					) : null}
					{odsPrecheck ? (
						<Alert
							type={
								String(odsPrecheck.status).toUpperCase() === "FAIL"
									? "error"
									: String(odsPrecheck.status).toUpperCase() === "WARN"
										? "warning"
										: "success"
							}
							showIcon
							message={`提交前预检：${odsPrecheck.status}`}
							description={`规则 ${odsPrecheck.totalRules || 0} 条，通过 ${odsPrecheck.passedRules || 0}，警告 ${odsPrecheck.warningRules || 0}，失败 ${odsPrecheck.failedRules || 0}`}
						/>
					) : null}
					<div className="rounded border border-slate-200 p-3">
						<div className="mb-3 flex flex-wrap items-center gap-2">
							<Text strong>任务生成配置</Text>
							{["incremental", "timestamp_incremental"].includes(String(odsConfig.syncMode || "").toLowerCase()) && odsRequest ? (
								<Tag color={resolveCommonIncrementalCandidate(odsRequest.tables) ? "green" : "red"}>
									增量字段：{resolveCommonIncrementalCandidate(odsRequest.tables) || "未识别"}
								</Tag>
							) : null}
						</div>
						<div className="grid gap-3 md:grid-cols-4">
							<label className="space-y-1">
								<Text type="secondary">ODS Schema</Text>
								<Input
									value={odsConfig.odsSchema}
									onChange={(event) => updateOdsConfig({ odsSchema: event.target.value })}
								/>
							</label>
							<label className="space-y-1">
								<Text type="secondary">来源系统编码</Text>
								<Input
									value={odsConfig.systemCode}
									onChange={(event) => updateOdsConfig({ systemCode: event.target.value })}
								/>
							</label>
							<label className="space-y-1">
								<Text type="secondary">业务编码</Text>
								<Input
									value={odsConfig.bizCode}
									onChange={(event) => updateOdsConfig({ bizCode: event.target.value })}
								/>
							</label>
							<label className="space-y-1">
								<Text type="secondary">同步模式</Text>
								<Select
									className="w-full"
									value={odsConfig.syncMode}
									options={[
										{ label: "全量覆盖", value: "full_refresh" },
										{ label: "全量追加", value: "append" },
										{ label: "时间戳增量", value: "incremental" },
										{ label: "主键增量", value: "primary_key_incremental" },
									]}
									onChange={(value) => updateOdsConfig({ syncMode: value })}
								/>
							</label>
						</div>
					</div>
					<div className="max-h-[360px] overflow-auto">
						{(schemaDiscoverResult?.tables || []).slice(0, 20).map((table) => {
							const key = `${table.schema || ""}.${table.name}`;
							return (
								<div key={key} className="mb-3 rounded border border-slate-200 p-3">
									<div className="mb-2 flex flex-wrap items-center gap-2">
										<Text strong>
											{table.schema ? `${table.schema}.` : ""}
											{table.name}
										</Text>
										<Tag>{table.view ? "VIEW" : table.type || "TABLE"}</Tag>
										<Tag color="blue">{table.columns?.length || 0} 列</Tag>
										<Button
											size="small"
											loading={odsPreviewingKey === key}
											onClick={() => schemaDiscoverSource && handleOdsPreview(schemaDiscoverSource, [table])}
										>
											预览 ODS
										</Button>
									</div>
									{table.primaryKeys?.length ? (
										<div className="mb-1 text-xs">主键：{table.primaryKeys.join(", ")}</div>
									) : null}
									{table.incrementalCandidates?.length ? (
										<div className="mb-1 text-xs">增量候选：{table.incrementalCandidates.join(", ")}</div>
									) : null}
									<div className="text-xs text-slate-500">
										{(table.columns || [])
											.slice(0, 8)
											.map((column) => `${column.name}:${column.dataType || column.nativeType || "unknown"}`)
											.join(" · ") || "未读取字段"}
									</div>
									{renderOdsColumnEditor(table)}
								</div>
							);
						})}
						{(schemaDiscoverResult?.tables || []).length > 20 ? <Text type="secondary">仅展示前 20 张表。</Text> : null}
					</div>
					{schemaDiscoverSource && (schemaDiscoverResult?.tables || []).length > 1 ? (
						<Button
							loading={odsPreviewingKey === "__all__"}
							onClick={() => {
								const tables = (schemaDiscoverResult?.tables || []).filter((table) => !table.view).slice(0, 20);
								void handleOdsPreview(schemaDiscoverSource, tables, "__all__");
							}}
						>
							批量预览前 20 张表
						</Button>
					) : null}
					{odsPreview?.tables?.length ? (
						<div className="space-y-3">
							<Divider />
							<div className="flex flex-wrap items-center gap-2">
								<Text strong>ODS 生成预览</Text>
								<Tag color="green">{odsPreview.tables.length} 张表</Tag>
							</div>
							{odsPreview.warnings?.length ? (
								<Alert type="warning" showIcon message={odsPreview.warnings.join("；")} />
							) : null}
							<div className="space-y-3">{odsPreview.tables.map(renderOdsPlan)}</div>
						</div>
					) : null}
				</div>
			</Modal>

			<Modal
				title={editing ? "编辑数据源" : "新增数据源"}
				open={modalOpen}
				onCancel={() => setModalOpen(false)}
				onOk={handleSave}
				okText="保存"
				confirmLoading={saving}
				destroyOnClose
			>
				<Form layout="vertical" form={form} preserve={false}>
					<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
						<Input placeholder="例如：ERP 数据库" />
					</Form.Item>
					<Form.Item name="connectorKey" label="连接器" rules={[{ required: true, message: "请选择连接器" }]}>
						<Select
							options={connectorOptions}
							placeholder={connectorsLoading ? "连接器加载中..." : "请选择连接器"}
							loading={connectorsLoading}
							showSearch
							optionFilterProp="label"
							onChange={(value) => applyConnectorDefaults(value)}
						/>
					</Form.Item>
					{selectedConnector?.description ? (
						<Text type="secondary" className="block -mt-2 mb-3">
							{selectedConnector.description}
						</Text>
					) : null}
					<Form.Item name="type" label="源类型" rules={[{ required: true, message: "请选择源类型" }]}>
						<Select
							options={TYPE_OPTIONS}
							placeholder="由连接器自动填充"
							disabled={Boolean(connectorValue)}
							onChange={(value) => handleTypeChange(value)}
						/>
					</Form.Item>
					{apiSource && (
						<>
							<Alert
								type="info"
								showIcon
								className="mb-4"
								message="API 数据源配置"
								description="鉴权机制仍可扩展；敏感值只会写入 secrets，props 中仅保存 provider/config/secretRefs。当前 API 入湖运行时未启用，入湖任务会先保存为草稿。"
							/>
							<Form.Item
								name="apiBaseUrl"
								label="API Base URL"
								rules={[
									{ required: true, message: "请输入 API Base URL" },
									{
										validator: async (_: any, value: string) => {
											if (!value) return Promise.resolve();
											try {
												const url = new URL(String(value).trim());
												if (url.protocol !== "http:" && url.protocol !== "https:") {
													return Promise.reject(new Error("仅支持 http/https"));
												}
												return Promise.resolve();
											} catch {
												return Promise.reject(new Error("URL 格式不合法"));
											}
										},
									},
								]}
							>
								<Input placeholder="https://api.example.com" />
							</Form.Item>
							<div className="grid gap-4 md:grid-cols-2">
								<Form.Item
									name="readerType"
									label="Reader 类型"
									initialValue="httpreader"
									rules={[{ required: true, message: "请输入 Reader 类型" }]}
								>
									<Input disabled placeholder="httpreader" />
								</Form.Item>
								<Form.Item name="apiAuthProvider" label="鉴权方式" initialValue="none">
									<Select
										loading={apiContractLoading}
										options={apiAuthProviders.map((item) => ({ label: item.label || item.id, value: item.id }))}
										placeholder="选择鉴权方式"
									/>
								</Form.Item>
							</div>
							{selectedApiAuthProvider?.description ? (
								<Text type="secondary" className="block -mt-2 mb-3">
									{selectedApiAuthProvider.description}
								</Text>
							) : null}
							{selectedApiAuthProvider?.fields?.length ? (
								<div className="grid gap-4 md:grid-cols-2">
									{selectedApiAuthProvider.fields.map((field) => {
										const secretField = Boolean(field.sensitive) && String(field.type || "").toLowerCase() !== "secretref";
										return (
											<Form.Item
												key={field.name}
												name={secretField ? ["apiAuthSecrets", field.name] : ["apiAuthConfig", field.name]}
												label={field.label || field.name}
												extra={
													secretField && editing
														? "留空保持已有密钥不变"
														: field.description || undefined
												}
												rules={[
													{
														required: Boolean(field.required) && (!secretField || !editing),
														message: `请输入${field.label || field.name}`,
													},
												]}
											>
												{buildApiAuthFieldInput(field)}
											</Form.Item>
										);
									})}
								</div>
							) : null}
							<Form.Item
								name="apiDefaultHeadersJson"
								label="默认请求头 JSON（可选）"
								rules={[{ validator: jsonObjectValidator("默认请求头") }]}
							>
								<Input.TextArea rows={3} placeholder='{"Accept":"application/json"}' />
							</Form.Item>
							<div className="grid gap-4 md:grid-cols-3">
								<Form.Item
									name="apiRequestPolicyJson"
									label="请求策略 JSON"
									rules={[{ validator: jsonObjectValidator("请求策略") }]}
								>
									<Input.TextArea rows={3} placeholder='{"connectTimeoutMillis":5000,"readTimeoutMillis":30000}' />
								</Form.Item>
								<Form.Item
									name="apiRateLimitJson"
									label="限流策略 JSON"
									rules={[{ validator: jsonObjectValidator("限流策略") }]}
								>
									<Input.TextArea rows={3} placeholder='{"requestsPerSecond":5,"maxConcurrency":2}' />
								</Form.Item>
								<Form.Item
									name="apiTlsJson"
									label="TLS 策略 JSON"
									rules={[{ validator: jsonObjectValidator("TLS 策略") }]}
								>
									<Input.TextArea rows={3} placeholder='{"verifyTls":true}' />
								</Form.Item>
							</div>
						</>
					)}
					{jdbcRequired && (
						<Form.Item name="driverId" label="JDBC 驱动">
							<Select
								options={driverOptions}
								placeholder={driversLoading ? "驱动加载中..." : "选择驱动以自动填充"}
								loading={driversLoading}
								allowClear
								showSearch
								optionFilterProp="label"
								onChange={(value) => handleDriverSelect(value as string)}
							/>
						</Form.Item>
					)}
					{jdbcRequired && (
						<Form.Item name="driverClass" label="驱动主类">
							<Input placeholder="可自动填充，例如：org.postgresql.Driver" />
						</Form.Item>
					)}
					{jdbcRequired && (
						<Form.Item name="driverVersion" label="驱动文件/版本">
							<Input placeholder="可填 jar 文件名或版本号" />
						</Form.Item>
					)}
					{!apiSource && (
						<>
							<Form.Item
								name="jdbcUrl"
								label="JDBC URL"
								rules={[{ required: jdbcRequired, message: "请输入 JDBC URL" }]}
							>
								<Input placeholder="jdbc:xxx://host:port/db" />
							</Form.Item>
							<Form.Item
								name="username"
								label="用户名"
								rules={[{ required: jdbcRequired, message: "请输入用户名" }]}
							>
								<Input placeholder="数据库账号" />
							</Form.Item>
							<Form.Item
								name="password"
								label={editing ? "密码（留空保持不变）" : "密码"}
								rules={[{ required: jdbcRequired && !editing, message: "请输入密码" }]}
							>
								<Input.Password placeholder="******" />
							</Form.Item>
						</>
					)}
					{!jdbcRequired && !apiSource && (
						<Form.Item
							name="readerType"
							label="Reader 类型"
							rules={[{ required: true, message: "请输入 Reader 类型" }]}
						>
							<Input placeholder="例如：excelreader、httpreader" />
						</Form.Item>
					)}
					{!jdbcRequired && fileSource && (
						<Form.Item label="字段解析">
							<Space>
								<Button onClick={openExcelModal}>上传 Excel/CSV 并解析</Button>
								{excelParseResult?.columns?.length ? (
									<Text type="secondary">已解析 {excelParseResult.columns.length} 列</Text>
								) : null}
							</Space>
						</Form.Item>
					)}
					<Form.Item name="description" label="描述">
						<Input.TextArea rows={2} placeholder="可选" />
					</Form.Item>
					<Form.Item name="ownerDept" label="归属部门" rules={[{ required: true, message: "请选择归属部门" }]}>
						<Select showSearch placeholder="选择归属部门" options={deptOptions}
							filterOption={(input, option) =>
								(option?.label ?? "").toLowerCase().includes(input.toLowerCase())
							}
						/>
					</Form.Item>
					<Form.Item
						name="propsJson"
						label="扩展配置 JSON（可选）"
						rules={[
							{
								validator: async (_: any, value: string) => {
									if (!value) return Promise.resolve();
									try {
										parseJson(value);
										return Promise.resolve();
									} catch {
										return Promise.reject(new Error("JSON 格式错误"));
									}
								},
							},
						]}
					>
						<Input.TextArea rows={4} placeholder='{"readerConfig":{"column":["*"]}}' />
					</Form.Item>
					{editing?.lastError && (
						<Text type="danger">最近错误：{editing.lastError}</Text>
					)}
				</Form>
			</Modal>

			<Modal
				title="Excel/CSV 字段解析"
				open={excelModalOpen}
				onCancel={() => setExcelModalOpen(false)}
				onOk={handleExcelParse}
				okText="解析并应用"
				confirmLoading={excelParsing}
				destroyOnClose
			>
				<Space direction="vertical" style={{ width: "100%" }}>
					<Alert type="warning" showIcon message="非密模块禁止上传涉密数据" />
					<Upload
						name="file"
						multiple={false}
						maxCount={1}
						showUploadList={false}
						accept=".xlsx,.csv"
						customRequest={handleExcelUpload}
						disabled={excelUploading}
					>
						<p className="ant-upload-drag-icon">
							<PlusOutlined />
						</p>
						<p className="ant-upload-text">点击或拖拽上传 Excel/CSV 文件（≤200MB）</p>
						<p className="ant-upload-hint">{excelPrepared?.fileName || "支持 .xlsx / .csv"}</p>
					</Upload>

					{excelPrepared?.sheets?.length ? (
						<Form layout="vertical">
							<Form.Item label="工作表">
								<Select
									value={excelSheetName}
									onChange={(value) => setExcelSheetName(value)}
									options={excelPrepared.sheets.map((sheet) => ({
										label: sheet.name,
										value: sheet.name,
									}))}
								/>
							</Form.Item>
						</Form>
					) : null}

					<Divider />

					<Form layout="vertical">
						<Form.Item label="表头行（1-based）">
							<InputNumber min={1} value={excelHeaderRow} onChange={(v) => setExcelHeaderRow(v || 1)} />
						</Form.Item>
						<Form.Item label="数据起始行（1-based）">
							<InputNumber min={1} value={excelDataStartRow} onChange={(v) => setExcelDataStartRow(v || 2)} />
						</Form.Item>
						<Form.Item label="分隔符">
							<Input value={excelDelimiter} onChange={(e) => setExcelDelimiter(e.target.value || ",")} />
						</Form.Item>
						<Form.Item label="日期格式">
							<Input value={excelDateFormat} onChange={(e) => setExcelDateFormat(e.target.value)} />
						</Form.Item>
						<Form.Item label="合并单元格填充">
							<Switch checked={excelFillMerged} onChange={setExcelFillMerged} />
						</Form.Item>
						<Form.Item label="容错跳过">
							<Switch checked={excelSkipErrors} onChange={setExcelSkipErrors} />
						</Form.Item>
					</Form>
				</Space>
			</Modal>

			<RollbackImpactModal
				open={rollbackOpen}
				request={rollbackRequest}
				onClose={() => setRollbackOpen(false)}
				onSuccess={() => { loadList(); }}
			/>
		</Card>
	);
}
