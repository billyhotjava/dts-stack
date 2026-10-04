import { Input, Select } from "antd";
import type { ApiAuthProviderDescriptorDTO, ApiAuthProviderFieldDTO } from "@/api/ingestion";
import type { InfraDataSource } from "@/api/services/dataSourcesService";

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

export const normalizeType = (value?: string) =>
	String(value || "")
		.trim()
		.toLowerCase();

export const normalizeConnectorKey = (value?: string) =>
	String(value || "")
		.trim()
		.toLowerCase()
		.replace(/_/g, "-");

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

export const readApiAuthSecretRefs = (props?: Record<string, any>) => cleanRecord(readApiAuth(props)?.secretRefs) || {};

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

export interface ApiAuthCatalogField extends ApiAuthProviderFieldDTO {
	options?: Array<{ label: string; value: string }>;
	defaultValue?: string;
}

export interface ApiAuthCatalogProvider extends Omit<ApiAuthProviderDescriptorDTO, "fields"> {
	fields: ApiAuthCatalogField[];
}

/**
 * 经典 API 鉴权方式目录（前端真值）。
 * 字段名与 dts-ingestion 运行时 ApiHttpEngine 读取的配置键一致；
 * 后端 contract 仅用于覆盖启用/禁用状态。
 */
export const API_AUTH_CATALOG: ApiAuthCatalogProvider[] = [
	{
		id: "none",
		label: "无鉴权",
		description: "不向请求注入任何鉴权信息。",
		enabled: true,
		fields: [],
	},
	{
		id: "bearerToken",
		label: "Bearer Token（静态令牌）",
		description: "每次请求携带固定的 Authorization: Bearer <token>；令牌过期后需要手动更新。",
		enabled: true,
		fields: [
			{
				name: "token",
				label: "令牌",
				type: "password",
				required: true,
				sensitive: true,
				description: "填入 API 返回的 access_token",
			},
		],
	},
	{
		id: "basic",
		label: "Basic 认证",
		description: "以用户名/密码生成 Authorization: Basic base64(username:password)。",
		enabled: true,
		fields: [
			{ name: "username", label: "用户名", type: "text", required: true },
			{ name: "password", label: "密码", type: "password", required: true, sensitive: true },
		],
	},
	{
		id: "apiKey",
		label: "API Key",
		description: "以请求头或查询参数注入固定 Key。",
		enabled: true,
		fields: [
			{ name: "name", label: "Key 名称", type: "text", required: true },
			{ name: "value", label: "Key 值", type: "password", required: true, sensitive: true },
			{
				name: "location",
				label: "注入位置",
				type: "select",
				options: [
					{ label: "Header（请求头）", value: "header" },
					{ label: "Query（查询参数）", value: "query" },
				],
			},
		],
	},
	{
		id: "jwtLogin",
		label: "JWT 登录（用户名密码）",
		description: "先用用户名/密码调用登录接口获取 JWT，缓存并按有效期自动刷新后注入请求头。",
		enabled: true,
		fields: [
			{
				name: "loginUrl",
				label: "登录地址",
				type: "url",
				required: true,
				description: "例如 https://example.com/auth/login（支持站内相对路径）",
			},
			{ name: "username", label: "登录用户名", type: "text", required: true },
			{ name: "password", label: "登录密码", type: "password", required: true, sensitive: true },
			{
				name: "tokenPath",
				label: "Token 字段路径",
				type: "text",
				defaultValue: "access_token",
				description: "登录响应中 access_token 所在字段（支持 a.b.c 点路径）",
			},
			{
				name: "loginMethod",
				label: "登录请求方法",
				type: "select",
				options: [
					{ label: "POST", value: "POST" },
					{ label: "GET", value: "GET" },
				],
				defaultValue: "POST",
			},
			{
				name: "tokenPlacement",
				label: "Token 注入位置",
				type: "select",
				options: [
					{ label: "请求头 Authorization: Bearer", value: "" },
					{ label: "自定义请求头", value: "header" },
				],
				defaultValue: "",
			},
			{
				name: "tokenHeaderName",
				label: "自定义 Token 请求头名称",
				type: "text",
				description: "Token 注入位置选择“自定义请求头”时必填，例如 X-Auth-Token",
			},
		],
	},
	{
		id: "oauth2ClientCredentials",
		label: "OAuth2 客户端凭证",
		description: "以 clientId/clientSecret 换取 access_token（client_credentials 模式），缓存并按有效期刷新。",
		enabled: true,
		fields: [
			{ name: "tokenUrl", label: "Token 地址", type: "url", required: true },
			{ name: "clientId", label: "Client ID", type: "text", required: true },
			{ name: "clientSecret", label: "Client Secret", type: "password", required: true, sensitive: true },
			{ name: "scope", label: "Scope", type: "text" },
		],
	},
	{
		id: "customSignature",
		label: "自定义签名",
		description: "按规则对请求签名（即将支持）。",
		enabled: false,
		fields: [{ name: "secret", label: "签名密钥", type: "password", sensitive: true }],
	},
	{
		id: "mtls",
		label: "双向 TLS（mTLS）",
		description: "使用客户端证书双向认证（即将支持）。",
		enabled: false,
		fields: [],
	},
];

/** 合并后端 contract 的启用状态与目录中的展示定义。 */
export const resolveApiAuthCatalog = (contractProviders?: ApiAuthProviderDescriptorDTO[]): ApiAuthCatalogProvider[] => {
	const contractById = new Map((contractProviders || []).map((item) => [String(item.id || "").toLowerCase(), item]));
	return API_AUTH_CATALOG.map((item) => {
		const contract = contractById.get(item.id.toLowerCase());
		return {
			...item,
			enabled: contract ? contract.enabled !== false : item.enabled !== false,
		};
	});
};

export const buildApiAuthFieldInput = (field: ApiAuthCatalogField) => {
	const type = String(field.type || "text").toLowerCase();
	const placeholder = field.description || field.label || field.name;
	if (
		field.options?.length ||
		type === "select" ||
		field.name === "location" ||
		field.name === "loginMethod" ||
		field.name === "tokenPlacement"
	) {
		const options =
			field.options ||
			(field.name === "location"
				? [
						{ label: "Header（请求头）", value: "header" },
						{ label: "Query（查询参数）", value: "query" },
					]
				: field.name === "loginMethod"
					? [
							{ label: "POST", value: "POST" },
							{ label: "GET", value: "GET" },
						]
					: field.name === "tokenPlacement"
						? [
								{ label: "请求头 Authorization: Bearer", value: "" },
								{ label: "自定义请求头", value: "header" },
							]
						: []);
		return <Select placeholder={placeholder} options={options} />;
	}
	if (field.sensitive && type !== "secretref") {
		return <Input.Password placeholder={placeholder} />;
	}
	if (type === "url") {
		return <Input placeholder="https://example.com/oauth/token" />;
	}
	return <Input placeholder={placeholder} />;
};
