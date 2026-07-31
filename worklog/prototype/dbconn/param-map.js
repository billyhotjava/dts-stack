/**
 * 现网参数全量归属表。
 *
 * 来源：pages/foundation/DataSourceFormModal.tsx（22 个字段）
 *      pages/explore/etl/steps/*.tsx（64 个字段）
 * 两侧重复：name / description / ownerDept / readerType
 *
 * where 取值：
 *   connector  连接器 schema（建连接时一次性，之后继承）
 *   discover   向导②选表（自动推导为主，可逐表覆盖）
 *   policy     向导③策略（调度、增量、容错）
 *   platform   平台/管理员配置，最终用户看不到
 *   quota      项目或连接上的配额，任务只在配额内申请
 *   drop       不应存在，需要删除或合并
 */

(function () {

const PARAMS = [
	/* ---- 身份与归属 ---- */
	{ f: "name", zh: "任务名称", where: "policy", note: "" },
	{ f: "description", zh: "描述", where: "policy", note: "" },
	{ f: "ownerDept", zh: "归属部门", where: "policy", note: "默认继承连接，覆盖需审批", dup: true },
	{ f: "projectKey", zh: "所属项目", where: "policy", note: "决定配额与调度队列" },
	{ f: "sourceSystem", zh: "来源系统", where: "connector", note: "属于连接的元信息，不是任务的" },
	{ f: "classification", zh: "数据密级", where: "connector", note: "默认继承连接，覆盖需审批", ds: true },

	/* ---- 连接（数据源表单） ---- */
	{ f: "connectorKey", zh: "连接器", where: "connector", note: "", ds: true },
	{ f: "type", zh: "源类型", where: "drop", note: "与 connectorKey 重复，二者取一", ds: true },
	{ f: "jdbcUrl", zh: "JDBC URL", where: "connector", note: "由 host/port/库名结构化拼装，不再手写", ds: true },
	{ f: "username", zh: "用户名", where: "connector", note: "", ds: true },
	{ f: "password", zh: "密码", where: "connector", note: "密钥服务保管，不回显", ds: true },
	{ f: "driverId", zh: "JDBC 驱动", where: "connector", note: "随连接器带出，缺失时就地上传", ds: true },
	{ f: "driverClass", zh: "驱动主类", where: "platform", note: "驱动包自带，无需用户填", ds: true },
	{ f: "driverVersion", zh: "驱动版本", where: "platform", note: "同上", ds: true },
	{ f: "propsJson", zh: "扩展配置 JSON", where: "connector", note: "改为键值对编辑器", ds: true, json: true },
	{ f: "readerType", zh: "Reader 类型", where: "platform", note: "由连接器决定，用户不该选", dup: true, ds: true },

	/* ---- API 连接（数据源表单） ---- */
	{ f: "apiBaseUrl", zh: "API Base URL", where: "connector", note: "", ds: true },
	{ f: "apiAllowHttp", zh: "允许明文 HTTP", where: "connector", note: "开关 + 安全审批", ds: true },
	{ f: "apiAuthProvider", zh: "鉴权方式", where: "connector", note: "选择后联动展开对应字段", ds: true },
	{ f: "apiDefaultHeadersJson", zh: "默认请求头", where: "connector", note: "改为键值对编辑器", ds: true, json: true },
	{ f: "apiRequestPolicyJson", zh: "请求策略", where: "connector", note: "拆为超时/重试/退避三个数字字段", ds: true, json: true },
	{ f: "apiRateLimitJson", zh: "限流策略", where: "connector", note: "拆为 QPS/并发两个数字字段", ds: true, json: true },
	{ f: "apiTlsJson", zh: "TLS 策略", where: "connector", note: "拆为证书校验开关等", ds: true, json: true },

	/* ---- API 资源（任务侧）---- */
	{ f: "apiResourceId", zh: "资源标识", where: "discover", note: "一个 API 连接下可有多个资源，等价于「表」" },
	{ f: "apiResourcePath", zh: "接口路径", where: "discover", note: "同上" },
	{ f: "apiResourceDisplayName", zh: "资源显示名", where: "discover", note: "同上" },
	{ f: "apiMethod", zh: "请求方法", where: "discover", note: "同上" },
	{ f: "apiRecordPath", zh: "记录路径", where: "discover", note: "JSONPath，决定从响应里取哪个数组" },
	{ f: "apiQueryJson", zh: "Query 参数", where: "discover", note: "改为键值对编辑器", json: true },
	{ f: "apiBodyTemplateJson", zh: "Body 模板", where: "discover", note: "POST 才需要，保留代码编辑器但带校验", json: true },
	{ f: "apiPaginationJson", zh: "分页配置", where: "connector", note: "同一 API 的分页方式通常一致，提到连接层", json: true },
	{ f: "apiCursorJson", zh: "增量游标", where: "policy", note: "属于增量策略，不是连接", json: true },

	/* ---- 文件 ---- */
	{ f: "file", zh: "上传文件", where: "discover", note: "", ds: true },
	{ f: "fileAutoId", zh: "自动主键", where: "discover", note: "" },
	{ f: "fileTableName", zh: "目标表名", where: "discover", note: "由文件名推导，可覆盖" },

	/* ---- Reader 读取 ---- */
	{ f: "sourceCategory", zh: "数据来源类别", where: "connector", note: "由连接器带出" },
	{ f: "sourceDataSourceId", zh: "数据源连接", where: "connector", note: "在合一模型里就是这条接入自己" },
	{ f: "tableSelectionMode", zh: "表选择方式", where: "discover", note: "全部 / 手动 / 规则" },
	{ f: "tableExclude", zh: "排除表", where: "discover", note: "" },
	{ f: "readerSchema", zh: "源 Schema", where: "discover", note: "" },
	{ f: "readerTables", zh: "源表清单", where: "discover", note: "由勾选生成，不再手打" },
	{ f: "readerTablePattern", zh: "表名匹配规则", where: "discover", note: "" },
	{ f: "readerColumns", zh: "列裁剪", where: "discover", note: "逐表设置，默认全列" },
	{ f: "readerWhere", zh: "过滤条件", where: "discover", note: "逐表设置" },
	{ f: "readerQuerySql", zh: "自定义查询 SQL", where: "discover", note: "高级逃生口，与勾选表互斥" },
	{ f: "readerConfig", zh: "Reader 配置 JSON", where: "drop", note: "必填的裸 JSON，应由上面各项生成", json: true },
	{ f: "readerExtraConfig", zh: "Reader 扩展 JSON", where: "connector", note: "⚠ 现用 {...base,...extra} 静默覆盖同屏的字段/过滤条件。降级为受控逃生口：白名单校验 + 拒改平台管辖键 + 留痕", json: true, risk: true },
	{ f: "(splitPk)", zh: "分片键", where: "discover", note: "⚠ 全仓仅存在于两处前端 placeholder，后端零引用；且 channel 硬编码为 1，填了也不生效", risk: true },
	{ f: "(channel)", zh: "并发通道数", where: "connector", note: "⚠ AddaxJobService:339 硬编码 channel=1，无任何配置入口", risk: true },
	{ f: "(fetchSize)", zh: "批量拉取行数", where: "connector", note: "Addax 支持但平台未暴露" },
	{ f: "(queryTimeOut)", zh: "单条查询超时", where: "connector", note: "同上" },
	{ f: "(session)", zh: "会话初始化语句", where: "connector", note: "同上，如 Oracle NLS_DATE_FORMAT" },

	/* ---- 增量 ---- */
	{ f: "syncMode", zh: "同步方式", where: "policy", note: "全量 / 增量 / CDC" },
	{ f: "incrementalType", zh: "增量类型", where: "policy", note: "时间戳 / 自增主键" },
	{ f: "incrementalColumn", zh: "增量列", where: "discover", note: "系统按类型自动识别，可覆盖" },
	{ f: "initialWatermark", zh: "初始水位", where: "policy", note: "首轮从哪里开始拉" },
	{ f: "syncPrefix", zh: "目标表前缀", where: "platform", note: "命名规范应由平台统一，不是每个任务自定" },

	/* ---- Writer 写入：这一整组都不该出现在任务表单 ---- */
	{ f: "targetDataSourceId", zh: "目标数据源", where: "platform", note: "湖是唯一目标，平台级配置" },
	{ f: "writerJdbcUrls", zh: "目标 JDBC URL", where: "platform", note: "⚠ 用户当前要手填湖地址", risk: true },
	{ f: "writerUsername", zh: "目标用户名", where: "platform", note: "⚠ 用户当前要手填湖账号", risk: true },
	{ f: "writerPassword", zh: "目标密码", where: "platform", note: "⚠ 用户当前要手填湖密码，且每任务存一份", risk: true },
	{ f: "writerSchema", zh: "目标 Schema", where: "platform", note: "由分层规范决定" },
	{ f: "writerTables", zh: "目标表名", where: "discover", note: "由源表名 + 命名规范推导，可覆盖" },
	{ f: "writerColumns", zh: "目标列", where: "discover", note: "由字段映射生成" },
	{ f: "writerWriteMode", zh: "写入模式", where: "policy", note: "覆盖 / 追加 / upsert，由同步方式推导" },
	{ f: "writerPreSql", zh: "写入前 SQL", where: "policy", note: "高级项，需审批" },
	{ f: "writerPostSql", zh: "写入后 SQL", where: "policy", note: "高级项，需审批" },
	{ f: "writerConfig", zh: "Writer 配置 JSON", where: "drop", note: "同 readerConfig", json: true },
	{ f: "writerExtraConfig", zh: "Writer 扩展 JSON", where: "drop", note: "同 readerExtraConfig", json: true },

	/* ---- 调度 ---- */
	{ f: "scheduleType", zh: "调度方式", where: "policy", note: "手动 / 间隔 / Cron" },
	{ f: "scheduleCron", zh: "Cron 表达式", where: "policy", note: "" },
	{ f: "scheduleIntervalMinutes", zh: "间隔分钟", where: "policy", note: "" },
	{ f: "windowStart", zh: "运行窗口起", where: "policy", note: "避开源库业务高峰" },
	{ f: "windowEnd", zh: "运行窗口止", where: "policy", note: "" },
	{ f: "windowTimezone", zh: "窗口时区", where: "platform", note: "全局统一即可，不必每任务填" },

	/* ---- 资源与容错 ---- */
	{ f: "priority", zh: "优先级", where: "policy", note: "" },
	{ f: "taskConcurrency", zh: "任务并发", where: "policy", note: "在配额内申请" },
	{ f: "sourceConcurrency", zh: "来源并发上限", where: "quota", note: "⚠ 默认 0 = 不限，护源库的闸门默认没生效；应设在连接上由管理员统一维护", risk: true },
	{ f: "projectConcurrency", zh: "项目并发上限", where: "quota", note: "⚠ 默认 0 = 不限，同上", risk: true },
	{ f: "rejectPolicy", zh: "限流策略", where: "policy", note: "只有 REJECT / QUEUE 两个有效值：撞并发上限时直接失败，或排队等待（硬编码最多 30 秒）" },
	{ f: "maxConcurrentRuns", zh: "任务并发上限", where: "policy", note: "同一任务在途执行数，默认 1" },
	{ f: "priority", zh: "队列优先级", where: "policy", note: "⚠ 仅在 rejectPolicy=QUEUE 时决定抢槽次序；默认 REJECT 模式下只透传给 Airflow conf，不影响行为", risk: true },
	{ f: "windowStart/windowEnd", zh: "执行窗口", where: "policy", note: "⚠ 窗口外触发直接抛异常拒绝，不是顺延——调度批次会直接失败且不自动补", risk: true },
	{ f: "(readerSchema 覆盖)", zh: "Schema", where: "discover", note: "默认继承连接，仅在需要扫其它 Schema 时覆盖" },
	{ f: "(表名筛选)", zh: "表名筛选", where: "discover", note: "SQL LIKE，在源端下推以减少元数据扫描量" },

	/* ---- 编排与下游 ---- */
	{ f: "airflowEnabled", zh: "启用 Airflow", where: "platform", note: "调度实现细节，不该暴露给用户" },
	{ f: "dbtModels", zh: "dbt 模型", where: "platform", note: "入湖完成后的下游触发，属编排" },
	{ f: "dbtModelSelector", zh: "dbt 模型选择器", where: "platform", note: "同上" },
	{ f: "dbtDagSelector", zh: "dbt DAG 选择器", where: "platform", note: "同上" },

	/* ---- UI 与动作 ---- */
	{ f: "editorMode", zh: "编辑器模式", where: "drop", note: "「表单模式 / JSON 模式」切换本身是妥协产物" },
	{ f: "runNow", zh: "立即运行", where: "policy", note: "创建后的动作，不是配置项" },
	{ f: "jobConfig", zh: "作业配置", where: "drop", note: "又一个兜底 JSON", json: true },
];


/* ------------------------------------------------------------------ */
/* 屏幕对照：现网某一屏的字段逐个落到原型哪里                            */
/* behaviour 一栏均已在后端代码中核实，标注了出处                        */
/* ------------------------------------------------------------------ */

const SCREEN_MAPS = [
	{
		screen: "数据入湖配置 · 数据源",
		path: "/explore/etl/transform → 新建 → 数据源",
		rows: [
			{ label: "数据源连接", now: "下拉选已建好的数据源", behaviour: "任务与连接是两个对象，必须先去别的菜单建好", to: "向导① 选连接器并就地填参数", tone: "ok" },
			{ label: "Reader 类型", now: "禁用输入框「将根据数据源自动生成」", behaviour: "完全由连接器决定，用户无法也不需要干预", to: "不出现在界面；连接器 schema 内部决定", tone: "muted", note: "占了一个格子却不承载任何决策" },
			{ label: "Schema（可选）", now: "文本框，例如 public", behaviour: "决定去源端哪个 schema 找表", to: "向导② 发现范围 · 继承自连接，可覆盖", tone: "ok" },
			{ label: "表名筛选（可选）", now: "SQL LIKE，例如 ods_%", behaviour: "在源端下推，减少元数据扫描量", to: "向导② 发现范围 · 表名筛选", tone: "ok" },
			{ label: "入湖表选择", now: "全部表（默认）/ 手动选择", behaviour: "决定 readerTables 怎么生成", to: "向导② 手动勾选 / 按规则匹配", tone: "ok" },
			{ label: "排除表（可选）", now: "换行或逗号分隔", behaviour: "从发现结果里剔除", to: "向导② 发现范围 · 排除表", tone: "ok" },
			{ label: "Reader 字段", now: "逗号分隔，默认 *", behaviour: "写入 Addax reader 的 column", to: "向导② 逐表高级 · 列裁剪（默认全列）", tone: "ok" },
			{ label: "Reader 过滤条件", now: "例如 status = 1", behaviour: "拼进 WHERE", to: "向导② 逐表高级 · 过滤条件", tone: "ok" },
			{ label: "Reader 查询 SQL", now: "每行一条", behaviour: "有值时整表读取被替换", to: "向导② 逐表高级 · 自定义查询 SQL（标为逃生口）", tone: "warn" },
			{ label: "Reader 扩展配置 JSON", now: "占位符 {\"splitPk\":\"id\"}", behaviour: "⚠ 原样成为 Addax reader parameter（AddaxJobService:362），且 {...base,...extra} 会静默覆盖上方的字段与过滤条件（ingestionFormHelpers:571）", to: "连接器 schema「读取性能」分组 + 受控逃生口", tone: "bad" },
			{ label: "目标数据源", now: "下拉，数仓 (biadmin)（推荐）", behaviour: "湖是唯一目标，却做成了每任务可选", to: "向导③ 目标端 · 平台托管只读", tone: "bad" },
			{ label: "目标表前缀", now: "从数据源自动推算，可手动修改", behaviour: "生成 ODS 表名", to: "向导③ 目标端 · 表名规范（平台配置）", tone: "muted" },
		],
	},
	{
		screen: "数据入湖配置 · 运行治理策略（可选）",
		path: "/explore/etl/transform → 新建 → 运行治理策略",
		rows: [
			{ label: "任务并发上限", now: "默认 1", behaviour: "同一任务「运行中/排队中」执行数上限（IngestionTaskService:1876）", to: "向导③ 运行时 · 本任务并发", tone: "ok" },
			{ label: "来源并发上限", now: "0 表示不限", behaviour: "⚠ 默认 0 = 闸门不生效。同一数据源上所有任务的在途数（:1882）——这是护源库的唯一防线", to: "配额：设在连接上，由管理员维护", tone: "bad" },
			{ label: "项目并发上限", now: "0 表示不限", behaviour: "⚠ 同上，默认不生效（:1892）", to: "配额：设在项目上", tone: "bad" },
			{ label: "项目标识", now: "例如 project:patent", behaviour: "上面配额的分组键；留空回退 dbtDagSelector，再回退 \"default\"（:1855）", to: "向导③ 基本信息 · 所属项目（下拉，不手打）", tone: "ok" },
			{ label: "队列优先级", now: "MEDIUM", behaviour: "⚠ 权重 HIGH/P0/CRITICAL/URGENT=3、MEDIUM=2、LOW/P2=1，但仅在 QUEUE 模式抢槽位时生效（:2101）；REJECT 模式下只透传给 Airflow conf", to: "向导③ 运行时 · 非排队模式下置灰并说明无效", tone: "warn" },
			{ label: "限流策略", now: "REJECT", behaviour: "⚠ 只有 REJECT / QUEUE 两个有效值。QUEUE 最多等 30 秒（GOVERNANCE_QUEUE_MAX_WAIT 硬编码，2 秒轮询），超时仍失败（:90、:623）", to: "向导③ 运行时 · 撞上并发上限时", tone: "warn" },
			{ label: "执行窗口开始 / 结束", now: "HH:mm，例如 01:00 / 06:00", behaviour: "⚠ 窗口外触发直接抛异常拒绝，不是顺延（:1031）；支持跨零点", to: "向导③ 运行时 · 允许运行窗口，hint 明确写「不会自动顺延」", tone: "bad" },
			{ label: "执行窗口时区", now: "Asia/Shanghai", behaviour: "非法值静默回退默认（:1834）", to: "向导③ 运行时 · 时区（平台配置）", tone: "muted" },
		],
	},
];

const WHERE_META = {
	connector: { label: "连接器 schema", tone: "info", desc: "建连接时填一次，任务侧继承" },
	discover: { label: "向导②选表", tone: "ok", desc: "自动推导为主，可逐表覆盖" },
	policy: { label: "向导③策略", tone: "ok", desc: "调度、增量、容错" },
	platform: { label: "平台配置", tone: "muted", desc: "管理员维护，最终用户看不到" },
	quota: { label: "配额", tone: "warn", desc: "设在项目或连接上，任务只申请" },
	drop: { label: "应删除", tone: "bad", desc: "裸 JSON 兜底或概念重复" },
};

window.PARAM_MAP = { PARAMS, WHERE_META, SCREEN_MAPS };

})();
