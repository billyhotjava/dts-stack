/**
 * DTS 数据接入原型 —— 静态数据。
 *
 * 核心主张：连接器不只声明"能力标签"，还声明"配置契约（schema）"。
 * 前端表单由 schema 渲染，后端用同一份 schema 校验。
 * 现网 UI 里的 11 个裸 JSON 文本框，在这里全部还原成结构化字段。
 */

/* ------------------------------------------------------------------ */
/* 字段类型：text | password | number | select | switch | kv | tags     */
/* group 用于折叠分区；when 用于条件显示                                */
/* ------------------------------------------------------------------ */

(function () {

const JDBC_COMMON_ADVANCED = [
	{ name: "connectTimeoutMs", label: "连接超时", type: "number", default: 10000, suffix: "ms", min: 1000 },
	{ name: "queryTimeoutSec", label: "查询超时", type: "number", default: 300, suffix: "秒", min: 10 },
	{ name: "extraParams", label: "额外连接参数", type: "kv", hint: "键值对形式，替代过去手写 JDBC URL 尾巴" },
];

const CONNECTORS = [
	{
		key: "mysql",
		name: "MySQL",
		category: "DATABASE",
		engine: "ADDAX",
		icon: "🐬",
		driver: { status: "READY", label: "mysql-connector-j 8.4.0" },
		capabilities: ["connectionTest", "schemaDiscover", "samplePreview", "fullRefresh", "timestampIncremental", "primaryKeyIncremental", "odsGeneration", "dbtSourceGeneration"],
		executionBindings: [
			{ mode: "FULL_REFRESH", adapter: "ADDAX_BATCH", status: "READY" },
			{ mode: "TIMESTAMP_INCREMENTAL", adapter: "ADDAX_BATCH", status: "READY" },
			{ mode: "PRIMARY_KEY_INCREMENTAL", adapter: "ADDAX_BATCH", status: "READY" },
			{ mode: "CDC", adapter: "DEBEZIUM_CDC", status: "POC" },
		],
		schema: [
			{ group: "连接", fields: [
				{ name: "host", label: "主机", type: "text", required: true, placeholder: "10.20.30.40", span: 2 },
				{ name: "port", label: "端口", type: "number", required: true, default: 3306 },
				{ name: "database", label: "默认库", type: "text", required: true, placeholder: "ods_source" },
				{ name: "username", label: "用户名", type: "text", required: true },
				{ name: "password", label: "密码", type: "password", required: true, secret: true },
			]},
				{ group: "高级", collapsed: true, fields: [
					{ name: "useSsl", label: "启用 SSL", type: "switch", default: false },
					...JDBC_COMMON_ADVANCED,
				]},
			],
	},
	{
		key: "postgresql",
		name: "PostgreSQL",
		category: "DATABASE",
		engine: "ADDAX",
		icon: "🐘",
		driver: { status: "READY", label: "postgresql 42.7.3" },
		capabilities: ["connectionTest", "schemaDiscover", "samplePreview", "fullRefresh", "timestampIncremental", "primaryKeyIncremental", "odsGeneration", "dbtSourceGeneration"],
		executionBindings: [
			{ mode: "FULL_REFRESH", adapter: "ADDAX_BATCH", status: "READY" },
			{ mode: "TIMESTAMP_INCREMENTAL", adapter: "ADDAX_BATCH", status: "READY" },
			{ mode: "PRIMARY_KEY_INCREMENTAL", adapter: "ADDAX_BATCH", status: "READY" },
			{ mode: "CDC", adapter: "DEBEZIUM_CDC", status: "PLANNED" },
		],
		schema: [
			{ group: "连接", fields: [
				{ name: "host", label: "主机", type: "text", required: true, span: 2 },
				{ name: "port", label: "端口", type: "number", required: true, default: 5432 },
				{ name: "database", label: "数据库", type: "text", required: true },
				{ name: "schema", label: "Schema", type: "text", default: "public" },
				{ name: "username", label: "用户名", type: "text", required: true },
				{ name: "password", label: "密码", type: "password", required: true, secret: true },
				]},
				{ group: "高级", collapsed: true, fields: JDBC_COMMON_ADVANCED },
			],
	},
	{
		key: "oracle",
		name: "Oracle",
		category: "DATABASE",
		engine: "ADDAX",
		icon: "🅾️",
			driver: { status: "READY", label: "ojdbc11 23.4.0" },
			capabilities: ["connectionTest", "schemaDiscover", "samplePreview", "fullRefresh", "timestampIncremental", "primaryKeyIncremental", "odsGeneration"],
			executionBindings: [
				{ mode: "FULL_REFRESH", adapter: "ADDAX_BATCH", status: "READY" },
				{ mode: "TIMESTAMP_INCREMENTAL", adapter: "ADDAX_BATCH", status: "READY" },
				{ mode: "PRIMARY_KEY_INCREMENTAL", adapter: "ADDAX_BATCH", status: "READY" },
			],
		schema: [
			{ group: "连接", fields: [
				{ name: "host", label: "主机", type: "text", required: true, span: 2 },
				{ name: "port", label: "端口", type: "number", required: true, default: 1521 },
				{ name: "serviceMode", label: "连接方式", type: "select", default: "service", options: [
					{ value: "service", label: "Service Name" },
					{ value: "sid", label: "SID" },
				]},
				{ name: "serviceName", label: "Service Name", type: "text", required: true, when: { serviceMode: "service" } },
				{ name: "sid", label: "SID", type: "text", required: true, when: { serviceMode: "sid" } },
				{ name: "username", label: "用户名", type: "text", required: true },
				{ name: "password", label: "密码", type: "password", required: true, secret: true },
				]},
				{ group: "高级", collapsed: true, fields: JDBC_COMMON_ADVANCED },
			],
	},
	{
		key: "dm8",
		name: "达梦 DM8",
		category: "DATABASE",
		engine: "ADDAX",
		icon: "🇨🇳",
			driver: { status: "MISSING", label: "未上传驱动包" },
			capabilities: ["connectionTest", "schemaDiscover", "fullRefresh", "timestampIncremental", "odsGeneration"],
			executionBindings: [
				{ mode: "FULL_REFRESH", adapter: "ADDAX_BATCH", status: "BLOCKED_DRIVER" },
				{ mode: "TIMESTAMP_INCREMENTAL", adapter: "ADDAX_BATCH", status: "BLOCKED_DRIVER" },
			],
		schema: [
			{ group: "连接", fields: [
				{ name: "host", label: "主机", type: "text", required: true, span: 2 },
				{ name: "port", label: "端口", type: "number", required: true, default: 5236 },
				{ name: "schema", label: "模式", type: "text", required: true },
				{ name: "username", label: "用户名", type: "text", required: true },
				{ name: "password", label: "密码", type: "password", required: true, secret: true },
				]},
				{ group: "高级", collapsed: true, fields: JDBC_COMMON_ADVANCED },
			],
	},
	{
		key: "inceptor",
		name: "Inceptor / Hive",
		category: "DATABASE",
		engine: "ADDAX",
		icon: "🐝",
			driver: { status: "READY", label: "inceptor-jdbc 8.32" },
			capabilities: ["connectionTest", "schemaDiscover", "fullRefresh", "append", "odsGeneration", "dbtSourceGeneration"],
			executionBindings: [
				{ mode: "FULL_REFRESH", adapter: "ADDAX_BATCH", status: "READY" },
				{ mode: "APPEND_BATCH", adapter: "ADDAX_BATCH", status: "READY" },
			],
		schema: [
			{ group: "连接", fields: [
				{ name: "jdbcUrl", label: "JDBC URL", type: "text", required: true, span: 3, placeholder: "jdbc:inceptor2://host:10000/default" },
				{ name: "authType", label: "认证方式", type: "select", default: "ldap", options: [
					{ value: "ldap", label: "LDAP" },
					{ value: "kerberos", label: "Kerberos" },
				]},
				{ name: "username", label: "用户名", type: "text", required: true, when: { authType: "ldap" } },
				{ name: "password", label: "密码", type: "password", required: true, secret: true, when: { authType: "ldap" } },
				{ name: "principal", label: "Principal", type: "text", required: true, when: { authType: "kerberos" } },
				{ name: "keytab", label: "Keytab", type: "file", required: true, secret: true, when: { authType: "kerberos" } },
				]},
				{ group: "高级", collapsed: true, fields: JDBC_COMMON_ADVANCED },
			],
	},
	{
		key: "http_api",
		name: "HTTP / REST API",
		category: "API",
		engine: "API_RUNTIME",
		icon: "🌐",
			driver: { status: "READY", label: "内置运行时" },
			capabilities: ["connectionTest", "samplePreview", "fullRefresh", "timestampIncremental", "odsGeneration"],
			executionBindings: [
				{ mode: "FULL_REFRESH", adapter: "DTS_API_HTTP", status: "READY" },
				{ mode: "CURSOR_INCREMENTAL", adapter: "DTS_API_HTTP", status: "READY" },
			],
		/* 这一段是全篇重点：现网这里是 8 个裸 JSON 文本框 */
		replaces: ["apiDefaultHeadersJson", "apiRequestPolicyJson", "apiRateLimitJson", "apiTlsJson"],
		resourceReplaces: ["apiQueryJson", "apiBodyTemplateJson", "apiPaginationJson", "apiCursorJson"],
		schema: [
			{ group: "连接", fields: [
				{ name: "baseUrl", label: "Base URL", type: "text", required: true, span: 3, placeholder: "https://api.example.gov.cn/v1" },
				{ name: "allowPlainHttp", label: "允许明文 HTTP", type: "switch", default: false, danger: true, hint: "开启后连接不加密，需安全审批" },
				{ name: "verifyCert", label: "校验服务端证书", type: "switch", default: true },
			]},
			{ group: "鉴权", fields: [
				{ name: "authType", label: "鉴权方式", type: "select", default: "bearer", options: [
					{ value: "none", label: "无" },
					{ value: "bearer", label: "Bearer Token" },
					{ value: "basic", label: "Basic" },
					{ value: "apiKey", label: "API Key" },
					{ value: "jwtLogin", label: "登录换 Token" },
				]},
				{ name: "token", label: "Token", type: "password", required: true, secret: true, span: 2, when: { authType: "bearer" } },
				{ name: "username", label: "用户名", type: "text", required: true, when: { authType: "basic" } },
				{ name: "password", label: "密码", type: "password", required: true, secret: true, when: { authType: "basic" } },
				{ name: "apiKeyName", label: "参数名", type: "text", required: true, when: { authType: "apiKey" } },
				{ name: "apiKeyIn", label: "位置", type: "select", default: "header", when: { authType: "apiKey" }, options: [
					{ value: "header", label: "请求头" },
					{ value: "query", label: "Query" },
				]},
				{ name: "apiKeyValue", label: "密钥", type: "password", required: true, secret: true, when: { authType: "apiKey" } },
				{ name: "loginPath", label: "登录接口路径", type: "text", required: true, when: { authType: "jwtLogin" } },
				{ name: "loginUsername", label: "登录账号", type: "text", required: true, when: { authType: "jwtLogin" } },
				{ name: "loginPassword", label: "登录口令", type: "password", required: true, secret: true, when: { authType: "jwtLogin" } },
				{ name: "tokenPath", label: "Token 提取路径", type: "text", default: "$.data.token", when: { authType: "jwtLogin" }, hint: "JSONPath" },
				{ name: "reloginOn401", label: "401 自动重登", type: "switch", default: true, when: { authType: "jwtLogin" } },
			]},
			{ group: "连接默认请求策略", collapsed: true, fields: [
				{ name: "headers", label: "默认请求头", type: "kv" },
				{ name: "rateLimitQps", label: "限流", type: "number", default: 5, suffix: "req/s" },
				{ name: "maxConcurrency", label: "并发", type: "number", default: 2, min: 1, max: 16 },
				{ name: "retryMax", label: "最大重试", type: "number", default: 3 },
				{ name: "retryBackoffMs", label: "重试退避", type: "number", default: 2000, suffix: "ms" },
				{ name: "timeoutMs", label: "读超时", type: "number", default: 30000, suffix: "ms", hint: "现网 PKI 登录曾因 10s 零余量超时" },
			]},
		],
	},
	{
		key: "file_batch",
		name: "文件（Excel / CSV）",
		category: "FILE",
		engine: "FILE",
		icon: "📄",
			driver: { status: "READY", label: "内置解析器" },
			capabilities: ["samplePreview", "fullRefresh", "odsGeneration"],
			executionBindings: [
				{ mode: "REPLACE_BATCH", adapter: "DTS_NATIVE_FILE", status: "READY" },
				{ mode: "APPEND_BATCH", adapter: "DTS_NATIVE_FILE", status: "READY" },
			],
		schema: [
			{ group: "文件", fields: [
				{
					name: "file", label: "上传文件", type: "file", required: true, span: 3,
					hint: "上传只形成不可变 Artifact；工作表、分隔符等解析设置在下一步随 Revision 冻结。",
				},
			]},
		],
	},
	{
		key: "kafka",
		name: "Kafka",
		category: "STREAM",
		engine: "FUTURE",
		icon: "📡",
		driver: { status: "PLANNED", label: "规划中" },
		disabled: true,
		capabilities: [],
		executionBindings: [
			{ mode: "APPEND", adapter: "KAFKA_STREAM", status: "PLANNED" },
			{ mode: "CDC", adapter: "KAFKA_STREAM", status: "PLANNED" },
		],
		schema: [],
	},
];

/* ------------------------------------------------------------------ */
/* AccessWorkspace 展示数据（底层连接、资源、计划、Revision 保持独立）    */
/* ------------------------------------------------------------------ */

const CONNECTIONS = [
	{
		id: "ing-1042",
		name: "人事主数据库",
		connector: "mysql",
		endpoint: "10.20.30.41:3306 / hr_prod",
		owner: "人事处",
		classification: "内部",
		state: "RUNNING",
		syncMode: "incremental",
		schedule: "每日 02:00",
		tables: 24,
		lastRun: "2026-07-30 02:04",
		lastRunState: "SUCCESS",
		lastRows: 184_320,
		nextRun: "2026-07-31 02:00",
		health: { drift: 1, failed7d: 0, pendingChange: 0 },
	},
	{
		id: "ing-1039",
		name: "财务共享 Oracle",
		connector: "oracle",
		endpoint: "10.20.31.7:1521 / FINSVC",
		owner: "财务处",
		classification: "秘密",
		state: "ATTENTION",
		syncMode: "incremental",
		schedule: "每日 01:30",
		tables: 61,
		lastRun: "2026-07-30 01:38",
		lastRunState: "PARTIAL",
		lastRows: 92_144,
		nextRun: "2026-07-31 01:30",
		health: { drift: 3, failed7d: 2, pendingChange: 1 },
	},
	{
		id: "ing-1035",
		name: "省厅人口库 API",
		connector: "http_api",
		endpoint: "https://api.example.gov.cn/v1",
		owner: "信息中心",
		classification: "秘密",
		state: "RUNNING",
		syncMode: "incremental",
		schedule: "每 4 小时",
		tables: 3,
		lastRun: "2026-07-30 16:00",
		lastRunState: "SUCCESS",
		lastRows: 12_806,
		nextRun: "2026-07-30 20:00",
		health: { drift: 0, failed7d: 0, pendingChange: 0 },
	},
	{
		id: "ing-1028",
		name: "资产盘点表（月报）",
		connector: "file_batch",
		endpoint: "手工上传 / xlsx",
		owner: "资产处",
		classification: "内部",
		state: "STAGING",
		syncMode: "full_refresh",
		schedule: "手动",
		tables: 1,
		lastRun: "2026-07-28 09:12",
		lastRunState: "STAGING",
		lastRows: 1_204,
		nextRun: "—",
		health: { drift: 0, failed7d: 0, pendingChange: 0, stagingErrors: 17 },
	},
	{
		id: "ing-1011",
		name: "教务系统 PostgreSQL",
		connector: "postgresql",
		endpoint: "10.20.30.88:5432 / edu",
		owner: "教务处",
		classification: "内部",
		state: "PAUSED",
		syncMode: "full_refresh",
		schedule: "每周一 03:00",
		tables: 12,
		lastRun: "2026-07-22 03:05",
		lastRunState: "SUCCESS",
		lastRows: 48_002,
		nextRun: "已暂停",
		health: { drift: 0, failed7d: 0, pendingChange: 0 },
	},
	{
		id: "ing-1007",
		name: "档案库 达梦",
		connector: "dm8",
		endpoint: "10.20.32.15:5236 / ARCHIVE",
		owner: "档案处",
		classification: "机密",
		state: "DRAFT",
		syncMode: "full_refresh",
		schedule: "未设置",
		tables: 0,
		lastRun: "—",
		lastRunState: "—",
		lastRows: 0,
		nextRun: "—",
		health: { drift: 0, failed7d: 0, pendingChange: 0 },
	},
];

/* ------------------------------------------------------------------ */
/* 表发现结果（向导第二步）                                             */
/* ------------------------------------------------------------------ */

const DISCOVERED_TABLES = [
	{ name: "hr_employee", comment: "员工主表", rows: 18_402, size: "42 MB", pk: "emp_id", incrementalCol: "gmt_modified", cols: 38, classification: "秘密", selected: false },
	{ name: "hr_department", comment: "部门", rows: 312, size: "1 MB", pk: "dept_id", incrementalCol: "gmt_modified", cols: 14, classification: "内部", selected: false },
	{ name: "hr_position", comment: "岗位字典", rows: 96, size: "< 1 MB", pk: "pos_id", incrementalCol: null, cols: 9, classification: "内部", selected: false },
	{ name: "hr_contract", comment: "劳动合同", rows: 21_558, size: "58 MB", pk: "contract_id", incrementalCol: "update_time", cols: 27, classification: "秘密", selected: false },
	{ name: "hr_salary_detail", comment: "薪酬明细", rows: 1_204_889, size: "3.1 GB", pk: "id", incrementalCol: "pay_month", cols: 41, classification: "机密", selected: false, warn: "含疑似敏感字段，建议单独审批" },
	{ name: "hr_attendance", comment: "考勤流水", rows: 8_902_114, size: "12 GB", pk: "id", incrementalCol: "punch_time", cols: 11, classification: "内部", selected: false, warn: "数据量大，建议独立任务与调度窗口" },
	{ name: "hr_train_record", comment: "培训记录", rows: 44_120, size: "88 MB", pk: "id", incrementalCol: "gmt_modified", cols: 19, classification: "内部", selected: false },
	{ name: "tmp_hr_sync_bak", comment: "（临时备份表）", rows: 18_400, size: "40 MB", pk: null, incrementalCol: null, cols: 38, classification: "内部", selected: false, warn: "无主键，无法增量" },
];

const SAMPLE_COLUMNS = [
	{ src: "emp_id", srcType: "bigint(20)", target: "emp_id", targetType: "BIGINT", role: "主键", std: "员工标识 / DE-0031" },
	{ src: "emp_name", srcType: "varchar(64)", target: "emp_name", targetType: "STRING", role: "", std: "姓名 / DE-0004", classification: "内部" },
	{ src: "id_card_no", srcType: "varchar(32)", target: "id_card_no", targetType: "STRING", role: "", std: "公民身份号码 / DE-0001", classification: "秘密", masked: true },
	{ src: "dept_id", srcType: "bigint(20)", target: "dept_id", targetType: "BIGINT", role: "外键", std: "部门标识 / DE-0032" },
	{ src: "hire_date", srcType: "date", target: "hire_date", targetType: "DATE", role: "", std: "" },
	{ src: "gmt_modified", srcType: "datetime", target: "gmt_modified", targetType: "TIMESTAMP", role: "增量列", std: "" },
];

/* API Endpoint 是连接下的资源；请求、分页与响应结构随 Revision 冻结 */
const API_RESOURCE_DEMO = {
	resourceKey: "population-change-search",
	method: "POST",
	path: "/population/changes/search",
	query: [
		{ k: "regionCode", v: "330000" },
		{ k: "includeHistory", v: "false" },
	],
	bodyTemplate: [
		"{",
		'  "changedFrom": "${window_start}",',
		'  "changedTo": "${window_end}",',
		'  "status": ["ACTIVE"]',
		"}",
	].join("\n"),
	sampleStatus: "succeeded",
	sampleResult: {
		status: 200,
		durationMs: 286,
		bytes: "1.8 KB",
		requestUrl: "/population/changes/search?regionCode=330000&includeHistory=false",
		body: {
			data: {
				records: [
					{ personId: "P-100238", name: "张*", idCardNo: "3301**********0832", changedAt: "2026-07-30T10:22:18+08:00" },
					{ personId: "P-100241", name: "李*", idCardNo: "3301**********1244", changedAt: "2026-07-30T10:24:06+08:00" },
				],
				nextCursor: "eyJvZmZzZXQiOjIwMH0",
			},
			requestId: "req-demo-20260731-001",
		},
	},
	recordPath: "$.data.records",
	pagination: {
		type: "cursor",
		cursorParam: "cursor",
		cursorPath: "$.data.nextCursor",
		pageSize: 200,
		maxPages: 500,
	},
	checkpointPreview: "eyJvZmZzZXQiOjIwMH0",
	inferredSchema: [
		{ name: "personId", jsonType: "string", targetType: "STRING", nullable: false, classification: "内部" },
		{ name: "name", jsonType: "string", targetType: "STRING", nullable: false, classification: "内部" },
		{ name: "idCardNo", jsonType: "string", targetType: "STRING", nullable: false, classification: "秘密" },
		{ name: "changedAt", jsonType: "string/date-time", targetType: "TIMESTAMP", nullable: false },
	],
	classification: {
		effectiveClassification: "秘密",
		evidence: [
			"样例响应字段 idCardNo 命中公民身份号码规则 DE-0001",
			"样例值保存前已脱敏，原值不进入浏览器与 Revision",
		],
	},
	targetHint: "ods.ods_population_change",
	estimatedVolume: "约 12.8 万条/日",
	requiresApproval: true,
};

/* 文件不是长连接：每次上传先形成不可变 Artifact，再定义解析 Revision */
const FILE_RESOURCE_DEMOS = {
	xlsx: {
		artifact: {
			id: "artifact-assets-202607-v3",
			fileName: "资产盘点_202607.xlsx",
			format: "xlsx",
			size: "286 KB",
			version: "Artifact v3",
			sha256: "5d41402abc4b2a76b9719d911017c5925d41402abc4b2a76b9719d911017c592",
			classificationSeal: {
				state: "SEALED",
				effectiveClassification: "内部",
				sealId: "seal-cls-7128",
				evidence: ["上传人声明为内部", "字段抽样未命中秘密或机密识别规则"],
			},
			securityScan: {
				status: "PASSED",
				engine: "ClamAV 1.4 + Office Guard",
				signatures: "病毒库 2026-07-31",
				scannedAt: "2026-07-31 09:42:18",
			},
		},
		parse: {
			availableSheets: ["资产明细", "填报说明"],
			sheet: "资产明细",
			headerRow: 2,
			dataStartRow: 3,
			dateFormat: "yyyy-MM-dd",
				fillMerged: false,
		},
		previewColumns: [
			{ name: "asset_code", label: "资产编号", type: "STRING" },
			{ name: "asset_name", label: "资产名称", type: "STRING" },
			{ name: "department", label: "使用部门", type: "STRING" },
			{ name: "purchase_date", label: "购置日期", type: "DATE" },
			{ name: "original_value", label: "原值(元)", type: "DECIMAL(18,2)" },
		],
		previewRows: [
			{ asset_code: "ZC-2026-0031", asset_name: "图形工作站", department: "信息中心", purchase_date: "2026-03-12", original_value: "28,600.00" },
			{ asset_code: "ZC-2026-0032", asset_name: "存储阵列", department: "数据处", purchase_date: "2026-04-08", original_value: "186,000.00" },
			{ asset_code: "ZC-2026-0033", asset_name: "扫描仪", department: "档案处", purchase_date: "2026-04-19", original_value: "4,980.00" },
		],
		schemaDiff: {
			againstRevision: "R2（当前生效）",
			added: [{ name: "department", detail: "新增 STRING，可空" }],
			changed: [{ name: "original_value", detail: "DECIMAL(14,2) → DECIMAL(18,2)" }],
			removed: [],
		},
		rowCount: 1_204,
		targetHint: "ods.ods_asset_inventory",
		requiresApproval: false,
	},
	csv: {
		artifact: {
			id: "artifact-assets-202607-csv-v1",
			fileName: "资产盘点_202607.csv",
			format: "csv",
			size: "198 KB",
			version: "Artifact v1",
			sha256: "9e107d9d372bb6826bd81d3542a419d69e107d9d372bb6826bd81d3542a419d6",
			classificationSeal: {
				state: "SEALED",
				effectiveClassification: "内部",
				sealId: "seal-cls-7131",
				evidence: ["上传人声明为内部", "CSV 抽样字段未命中秘密或机密识别规则"],
			},
			securityScan: {
				status: "PASSED",
				engine: "ClamAV 1.4 + CSV Guard",
				signatures: "病毒库 2026-07-31",
				scannedAt: "2026-07-31 09:44:03",
			},
		},
		parse: {
			encoding: "UTF-8",
			delimiter: ",",
			headerRow: 1,
			quote: '"',
			escape: '"',
			dateFormat: "yyyy-MM-dd",
		},
		previewColumns: [
			{ name: "asset_code", label: "资产编号", type: "STRING" },
			{ name: "asset_name", label: "资产名称", type: "STRING" },
			{ name: "department", label: "使用部门", type: "STRING" },
			{ name: "purchase_date", label: "购置日期", type: "DATE" },
			{ name: "original_value", label: "原值(元)", type: "DECIMAL(18,2)" },
		],
		previewRows: [
			{ asset_code: "ZC-2026-0031", asset_name: "图形工作站", department: "信息中心", purchase_date: "2026-03-12", original_value: "28600.00" },
			{ asset_code: "ZC-2026-0032", asset_name: "存储阵列", department: "数据处", purchase_date: "2026-04-08", original_value: "186000.00" },
			{ asset_code: "ZC-2026-0033", asset_name: "扫描仪", department: "档案处", purchase_date: "2026-04-19", original_value: "4980.00" },
		],
		schemaDiff: {
			againstRevision: "R2（当前生效）",
			added: [{ name: "department", detail: "新增 STRING，可空" }],
			changed: [],
			removed: [],
		},
		rowCount: 1_204,
		targetHint: "ods.ods_asset_inventory",
		requiresApproval: false,
	},
};

/* ------------------------------------------------------------------ */
/* 运维数据                                                             */
/* ------------------------------------------------------------------ */

const RUNS = [
	{ id: "run-88213", started: "2026-07-30 01:38:02", duration: "6m 41s", state: "PARTIAL", rows: 92_144, tables: "58 / 61", trigger: "调度", note: "3 张表因结构漂移跳过" },
	{ id: "run-88109", started: "2026-07-29 01:30:04", duration: "9m 02s", state: "FAILED", rows: 0, tables: "0 / 61", trigger: "调度", note: "ORA-12170 连接超时" },
	{ id: "run-88110", started: "2026-07-29 02:12:44", duration: "8m 55s", state: "SUCCESS", rows: 141_882, tables: "61 / 61", trigger: "人工重跑", note: "自动重试成功" },
	{ id: "run-87996", started: "2026-07-28 01:30:03", duration: "8m 12s", state: "SUCCESS", rows: 138_401, tables: "61 / 61", trigger: "调度", note: "" },
	{ id: "run-87880", started: "2026-07-27 01:30:02", duration: "8m 30s", state: "SUCCESS", rows: 139_552, tables: "61 / 61", trigger: "调度", note: "" },
];

const DRIFTS = [
	{ table: "fin_voucher", kind: "新增字段", detail: "audit_remark VARCHAR(512)", detected: "2026-07-30 01:38", policy: "自动接受", state: "已应用" },
	{ table: "fin_account", kind: "类型变更", detail: "balance DECIMAL(18,2) → DECIMAL(20,4)", detected: "2026-07-30 01:38", policy: "人工确认", state: "待处置" },
	{ table: "fin_subject", kind: "字段删除", detail: "legacy_code 已从源端移除", detected: "2026-07-29 01:30", policy: "人工确认", state: "待处置" },
];

const STAGING_ROWS = [
	{ row: 3, col: "资产编号", value: "ZC-2026-0031", error: "" },
	{ row: 4, col: "资产编号", value: "ZC2026 0032", error: "不符合编码规则 ZC-YYYY-NNNN" },
	{ row: 7, col: "购置日期", value: "2026/13/02", error: "非法日期" },
	{ row: 9, col: "使用部门", value: "信息处", error: "部门字典中不存在" },
	{ row: 12, col: "原值(元)", value: "—", error: "必填项为空" },
];

const CHANGES = [
	{ id: "chg-2211", type: "新增入湖表", summary: "新增 fin_budget_exec 等 3 张表", risk: "中", state: "待审批", assignee: "财务处 / 王主管", at: "2026-07-30 09:12" },
	{ id: "chg-2190", type: "同步模式变更", summary: "全量 → 时间戳增量（update_time）", risk: "高", state: "已通过", assignee: "信息中心 / 李工", at: "2026-07-26 14:30" },
	{ id: "chg-2154", type: "密级调整", summary: "内部 → 秘密（含身份证号）", risk: "高", state: "已通过", assignee: "保密办 / 张", at: "2026-07-20 10:05" },
];

/* ------------------------------------------------------------------ */

const CAPABILITY_LABELS = {
	connectionTest: "连接测试",
	schemaDiscover: "结构发现",
	samplePreview: "数据采样",
	fullRefresh: "全量",
	append: "追加",
	timestampIncremental: "时间戳增量",
	primaryKeyIncremental: "主键增量",
	cdc: "CDC",
	odsGeneration: "ODS 建表",
	dbtSourceGeneration: "dbt source",
};


/* 兼容旧示意数据与运行记录的标签映射；新 AccessWorkspace 不再用单一 state。 */
const STATE_META = {
	RUNNING: { label: "正常运行", tone: "ok" },
	ATTENTION: { label: "需要关注", tone: "warn" },
	STAGING: { label: "落地待确认", tone: "info" },
	PAUSED: { label: "已暂停", tone: "muted" },
	DRAFT: { label: "草稿", tone: "muted" },
	SUCCESS: { label: "成功", tone: "ok" },
	PARTIAL: { label: "部分成功", tone: "warn" },
	FAILED: { label: "失败", tone: "bad" },
};


/* 兼容旧截图的迁移表示意；新生命周期由 views.js 的独立维度表达。 */
const STATE_MACHINE = {
	DRAFT: ["READY"],
	READY: ["RUNNING", "ARCHIVED"],
	RUNNING: ["PAUSED", "ATTENTION"],
	ATTENTION: ["RUNNING", "PAUSED"],
	PAUSED: ["RUNNING", "ARCHIVED"],
	ARCHIVED: [],
};


window.PROTO = {
	CONNECTORS, CONNECTIONS, DISCOVERED_TABLES, SAMPLE_COLUMNS,
	API_RESOURCE_DEMO, FILE_RESOURCE_DEMOS,
	RUNS, DRIFTS, STAGING_ROWS, CHANGES,
	CAPABILITY_LABELS, STATE_META, STATE_MACHINE,
	connectorByKey: (key) => CONNECTORS.find((c) => c.key === key),
};

})();
