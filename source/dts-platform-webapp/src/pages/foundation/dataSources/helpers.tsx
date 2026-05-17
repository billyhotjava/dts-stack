import { Input, Select } from "antd";
import type { InfraDataSource } from "@/api/services/dataSourcesService";
import type { ApiAuthProviderFieldDTO } from "@/api/ingestion";

export const JDBC_TYPES = new Set([
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

export const API_TYPES = new Set(["api", "http", "https", "http_api", "api_http", "rest", "rest_api", "httpreader"]);

export const TYPE_OPTIONS = [
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

export const normalizeType = (value?: string) => String(value || "").trim().toLowerCase();

export const normalizeConnectorKey = (value?: string) =>
	String(value || "").trim().toLowerCase().replace(/_/g, "-");

export const inferConnectorKey = (type?: string, props?: Record<string, any>) => {
	const explicit = normalizeConnectorKey(props?.connectorKey);
	if (explicit) return explicit;
	const normalized = normalizeType(type);
	if (!normalized) return "";
	if (["postgres", "postgresql", "pg"].includes(normalized)) return "postgresql";
	if (["mssql", "sqlserver", "sql_server"].includes(normalized)) return "sqlserver";
	if (["dameng", "dm8"].includes(normalized)) return "dm";
	if (["api", "http", "https", "http_api", "api_http", "rest", "rest_api", "httpreader"].includes(normalized))
		return "http-api";
	return normalized.replace(/_/g, "-");
};

export const isJdbcType = (type?: string, jdbcUrl?: string) => {
	if (jdbcUrl) return true;
	const normalized = normalizeType(type);
	if (!normalized) return false;
	if (normalized.includes("jdbc")) return true;
	return JDBC_TYPES.has(normalized);
};

export const isFileSource = (type?: string) => {
	const normalized = normalizeType(type);
	return normalized === "excel" || normalized === "csv";
};

export const isApiSourceType = (type?: string) => {
	const normalized = normalizeType(type);
	return Boolean(normalized && API_TYPES.has(normalized));
};

export const isAdminManagedSource = (source?: InfraDataSource | null) => {
	if (!source) return false;
	if (source.props?.source === "admin-data-lake") return true;
	if (source.name === "数仓 (biadmin)") return true;
	return false;
};

export const parseJson = (value?: string) => {
	const text = String(value || "").trim();
	if (!text) return undefined;
	return JSON.parse(text);
};

export const asRecord = (value: any): Record<string, any> | undefined =>
	value && typeof value === "object" && !Array.isArray(value) ? value : undefined;

export const stringifyJson = (value: any) => {
	if (!value || typeof value !== "object" || Array.isArray(value)) return "";
	return JSON.stringify(value, null, 2);
};

export const cleanRecord = (value: any): Record<string, any> | undefined => {
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

export const omitReaderType = (props?: Record<string, any>) => {
	if (!props) return undefined;
	const {
		readerType: _readerType,
		reader: _reader,
		driverClass: _driverClass,
		driverVersion: _driverVersion,
		baseUrl: _baseUrl,
		baseURL: _baseURL,
		authProvider: _authProvider,
		auth: _auth,
		api: _api,
		readerConfig: _readerConfig,
		defaultHeaders: _defaultHeaders,
		requestPolicy: _requestPolicy,
		rateLimit: _rateLimit,
		tls: _tls,
		connectorType: _connectorType,
		sourceCategory: _sourceCategory,
		contractVersion: _contractVersion,
		...rest
	} = props;
	return rest;
};

export const firstRecord = (...values: any[]) => values.map(asRecord).find(Boolean);

export const readApiNode = (props?: Record<string, any>) => firstRecord(props?.api, props?.readerConfig);

export const readApiAuth = (props?: Record<string, any>) => {
	const apiNode = readApiNode(props);
	return firstRecord(props?.auth, apiNode?.auth);
};

export const readApiBaseUrl = (props?: Record<string, any>) => {
	const apiNode = readApiNode(props);
	return String(props?.baseUrl || props?.baseURL || apiNode?.baseUrl || apiNode?.baseURL || "").trim();
};

export const readApiAuthProvider = (props?: Record<string, any>) => {
	const apiNode = readApiNode(props);
	const auth = readApiAuth(props);
	return String(props?.authProvider || apiNode?.authProvider || auth?.provider || "none").trim() || "none";
};

export const readApiAuthConfig = (props?: Record<string, any>) => {
	const auth = readApiAuth(props);
	const config = { ...(asRecord(auth?.config) || {}) };
	Object.entries(auth || {}).forEach(([key, value]) => {
		if (["provider", "config", "secretRefs"].includes(key)) return;
		config[key] = value;
	});
	return cleanRecord(config) || {};
};

export const readApiAuthSecretRefs = (props?: Record<string, any>) =>
	cleanRecord(readApiAuth(props)?.secretRefs) || {};

export const readApiConfigPart = (props: Record<string, any> | undefined, key: string) => {
	const apiNode = readApiNode(props);
	return asRecord(props?.[key]) || asRecord(apiNode?.[key]);
};

export const parseObjectJson = (value: string | undefined, label: string) => {
	const parsed = parseJson(value);
	if (parsed === undefined) return undefined;
	if (!asRecord(parsed)) {
		throw new Error(`${label} 必须是 JSON Object`);
	}
	return parsed as Record<string, any>;
};

export const jsonObjectValidator = (label: string) => (_: any, value: string) => {
	if (!value) return Promise.resolve();
	try {
		parseObjectJson(value, label);
		return Promise.resolve();
	} catch (error: any) {
		return Promise.reject(new Error(error?.message || `${label} JSON 格式错误`));
	}
};

export const buildApiAuthFieldInput = (field: ApiAuthProviderFieldDTO) => {
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
