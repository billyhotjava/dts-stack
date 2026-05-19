import { useCallback, useEffect, useMemo, useState } from "react";
import type { DragEvent } from "react";
import { fetchJson, postYaml } from "./api";
import type {
	FormulaBlock,
	MetricAsset,
	MetricsCapabilities,
	MetricsHealth,
	ModelCandidate,
	ObjectJoin,
	RouteGroup,
	RouteItem,
	StatusTone,
	SubjectMapping,
	WorkspaceSnapshot,
} from "./types";

const routes: RouteGroup[] = [
	{
		group: "工作台",
		items: [
			{
				path: "/metrics/center",
				title: "指标工作台",
				stage: "Metric Hub",
				description: "集中查看 dts-metrics 服务能力、平台权限契约和指标包交付状态。",
			},
			{
				path: "/metrics/dictionary",
				title: "指标资产",
				stage: "Metric Dictionary",
				description: "沉淀指标名称、口径、公式、单位、负责人、版本和下游消费关系。",
			},
			{
				path: "/metrics/packs",
				title: "指标包",
				stage: "Metric Pack",
				description: "以 YAML/JSON 描述主题域、业务对象、维度、指标和发布物，供合作方独立交付。",
			},
			{
				path: "/metrics/migration",
				title: "迁移与回滚",
				stage: "Migration",
				description: "查看 platform 旧语义数据到 dts-metrics 的 dry-run 映射、阻断项和回滚边界。",
			},
			{
				path: "/metrics/operations",
				title: "运行与告警",
				stage: "Operations",
				description: "查看模型运行、新鲜度、发布预检和平台观测回传状态。",
			},
		],
	},
	{
		group: "语义建模",
		items: [
			{
				path: "/metrics/semantic",
				title: "语义建模流程",
				stage: "Semantic Modeling",
				description: "从业务对象和指标口径出发，逐步生成公共汇总模型、应用数据集和 BI Dataset。",
			},
			{
				path: "/metrics/semantic/subjects",
				title: "主题域映射",
				stage: "Subject Domain",
				description: "管理项目、采购、库存、质量、财务等业务主题，并通过 platform 数据资产目录做权限校验。",
			},
			{
				path: "/metrics/semantic/objects",
				title: "业务对象 Join",
				stage: "Business Object",
				description: "定义项目、合同、供应商、物料等业务对象及其 DWD 明细表关联关系。",
			},
			{
				path: "/metrics/semantic/metrics",
				title: "指标公式配置",
				stage: "Metric Designer",
				description: "用可视化方式配置 count、sum、count_if、sum_if、ratio 等指标公式。",
			},
			{
				path: "/metrics/semantic/models",
				title: "DWS/ADS 数据集",
				stage: "DWS / ADS",
				description: "生成可复用公共汇总模型和面向看板的大屏应用数据集。",
			},
			{
				path: "/metrics/semantic/publish",
				title: "审核发布与血缘",
				stage: "Publish",
				description: "发布前执行工程审核、SQL 预览、数据质量检查和血缘注册。",
			},
			{
				path: "/metrics/semantic/runs",
				title: "模型运行监控",
				stage: "Run Monitor",
				description: "跟踪 dbt/SQLMesh 运行、BI Dataset 注册和失败重试状态。",
			},
		],
	},
];

const routeItems = routes.flatMap((group) => group.items);

const sampleManifest = `pack_id: project-management-core
pack_name: 项目管理核心指标包
version: 0.1.0
industry: project
edition_required: professional
tenant_namespace: demo
security:
  apply_rls: true
source_model: dwd_project_detail
dimensions:
  - field: stat_month
    standard_code: stat_month
  - field: dept_name
    standard_code: dept_name
metrics:
  - metric_code: project_cnt
    metric_name: 项目总数
    term_ids:
      - glossary.project
    formula:
      type: aggregation
      aggregation: count_distinct
      field: project_id
  - metric_code: direct_cost_execution_rate
    metric_name: 直接成本执行率
    term_ids:
      - glossary.direct_cost_execution_rate
    formula:
      type: ratio
      numerator:
        type: aggregation
        aggregation: sum
        field: direct_cost_amount
      denominator:
        type: aggregation
        aggregation: sum
        field: direct_cost_control_amount
      multiply: 100
files:
  domains: domains.yml
  business_objects: business_objects.yml
  dimensions: dimensions.yml
  metrics: metrics.yml
  models: models.yml
  datasets: datasets.yml
dependencies:
  platform_domains:
    - project
  data_standards:
    - stat_month
    - dept_name
  platform_assets:
    - type: DATASET
      id: dwd_project_detail
    - type: GLOSSARY_TERM
      id: glossary.project
    - type: GLOSSARY_TERM
      id: glossary.direct_cost_execution_rate`;

type DesignerFieldRole = "dimension" | "metric";
type MetricKind = "atomic" | "derived" | "composite";

type DesignerField = {
	name: string;
	label: string;
	dataType: string;
	comment: string;
	tableName: string;
	role: DesignerFieldRole;
};

type MetricKindOption = {
	kind: MetricKind;
	title: string;
	description: string;
	rule: string;
};

type TimePeriodOption = {
	key: string;
	label: string;
	window: string;
	expression: string;
	refresh: string;
};

type FilterDraft = {
	key: string;
	label: string;
	field: string;
	operator: string;
	valueLabel: string;
	values?: string[];
	value?: string;
};

const designerFieldPool: DesignerField[] = [
	{
		name: "project_id",
		label: "项目 ID",
		dataType: "varchar",
		comment: "项目唯一标识",
		tableName: "dwd_project_detail",
		role: "metric",
	},
	{
		name: "project_name",
		label: "项目名称",
		dataType: "varchar",
		comment: "项目展示名称",
		tableName: "dwd_project_detail",
		role: "dimension",
	},
	{
		name: "stat_month",
		label: "统计月份",
		dataType: "date",
		comment: "按月汇总粒度",
		tableName: "dwd_project_detail",
		role: "dimension",
	},
	{
		name: "dept_name",
		label: "责任科室",
		dataType: "varchar",
		comment: "平台组织标准字段",
		tableName: "dwd_project_detail",
		role: "dimension",
	},
	{
		name: "project_type",
		label: "项目类型",
		dataType: "varchar",
		comment: "项目分类标准",
		tableName: "dwd_project_detail",
		role: "dimension",
	},
	{
		name: "direct_cost_amount",
		label: "直接成本",
		dataType: "decimal",
		comment: "直接成本执行金额",
		tableName: "dwd_project_detail",
		role: "metric",
	},
	{
		name: "direct_cost_control_amount",
		label: "直接成本控制额",
		dataType: "decimal",
		comment: "直接成本控制基准",
		tableName: "dwd_project_detail",
		role: "metric",
	},
	{
		name: "overdue_flag",
		label: "是否延期",
		dataType: "boolean",
		comment: "延期项目判断条件",
		tableName: "dwd_project_detail",
		role: "metric",
	},
	{
		name: "supplier_id",
		label: "供应商 ID",
		dataType: "varchar",
		comment: "供应商唯一标识",
		tableName: "dwd_purchase_order_detail",
		role: "metric",
	},
	{
		name: "supplier_name",
		label: "供应商名称",
		dataType: "varchar",
		comment: "供应商展示名称",
		tableName: "dwd_purchase_order_detail",
		role: "dimension",
	},
	{
		name: "delivery_month",
		label: "交付月份",
		dataType: "date",
		comment: "采购交付统计月份",
		tableName: "dwd_purchase_order_detail",
		role: "dimension",
	},
	{
		name: "purchase_amount",
		label: "采购金额",
		dataType: "decimal",
		comment: "采购订单金额",
		tableName: "dwd_purchase_order_detail",
		role: "metric",
	},
	{
		name: "on_time_flag",
		label: "准时交付",
		dataType: "boolean",
		comment: "是否按承诺日期完成交付",
		tableName: "dwd_purchase_order_detail",
		role: "metric",
	},
];

const metricKindOptions: MetricKindOption[] = [
	{
		kind: "atomic",
		title: "原子指标",
		description: "字段、聚合函数和最小统计口径",
		rule: "开发完成后才允许被衍生指标引用",
	},
	{
		kind: "derived",
		title: "衍生指标",
		description: "原子指标 + 维度 + 时间周期 + 过滤条件",
		rule: "默认承接原子指标术语、单位和责任人",
	},
	{
		kind: "composite",
		title: "复合指标",
		description: "同粒度衍生指标之间做比率、差值或排名",
		rule: "所有输入指标必须完成治理审核",
	},
];

const defaultTimePeriod: TimePeriodOption = {
	key: "current_month",
	label: "本月",
	window: "自然月",
	expression: "date_trunc('month', stat_date) = date_trunc('month', current_date)",
	refresh: "daily 02:30",
};

const timePeriodOptions: TimePeriodOption[] = [
	{
		key: "yesterday",
		label: "昨日",
		window: "T-1 日",
		expression: "stat_date = current_date - interval '1 day'",
		refresh: "daily 01:30",
	},
	{
		key: "last_week",
		label: "上周",
		window: "自然周",
		expression: "stat_week = date_trunc('week', current_date - interval '1 week')",
		refresh: "weekly Mon 03:00",
	},
	defaultTimePeriod,
	{
		key: "rolling_30d",
		label: "近 30 天",
		window: "滚动窗口",
		expression: "stat_date >= current_date - interval '30 day'",
		refresh: "daily 03:00",
	},
];

const filterDraftTemplates: FilterDraft[] = [
	{
		key: "project_type",
		label: "项目类型",
		field: "project_type",
		operator: "in",
		valueLabel: "重点项目 / 常规项目",
		values: ["key_project", "normal_project"],
	},
	{
		key: "overdue_flag",
		label: "延期状态",
		field: "overdue_flag",
		operator: "=",
		valueLabel: "true",
		value: "true",
	},
	{
		key: "dept_name",
		label: "责任科室",
		field: "dept_name",
		operator: "is_not_null",
		valueLabel: "非空",
	},
];

const metricEngineeringStages = [
	["01", "原始主体", "接入 platform 已授权明细表，确认数据标准和资产身份。", "/metrics/semantic/objects"],
	["02", "指标主体", "在主体上标记维度字段、时间字段、业务主键和 fanout 防护。", "/metrics/semantic/objects"],
	["03", "原子指标", "用字段池拖拽生成 count、sum、count_if、sum_if、ratio 等受控公式。", "/metrics/semantic/metrics"],
	["04", "衍生指标", "选择原子指标、统计维度、时间周期和过滤条件，形成可消费口径。", "/metrics/semantic/metrics"],
	["05", "复合指标", "在同粒度指标之间做比率、占比、差值和排名，保留依赖血缘。", "/metrics/semantic/metrics"],
	["06", "汇总表", "把同维度指标编排为 DWS/ADS，预览 SQL、schema 和列顺序。", "/metrics/semantic/models"],
	["07", "发布消费", "提交 dbt 门禁，注册血缘，进入 BI Dataset、大屏和 API 查询。", "/metrics/semantic/publish"],
];

const benchmarkCapabilityRows = [
	["主体建模", "拖拽表、Join、维度/时间字段标记", "业务对象 Join + 指标主体画布"],
	["指标体系", "原子、衍生、复合指标分层", "指标类型切换 + DSL 预览"],
	["时间周期", "内置周期与自定义统计窗口", "周期选择器写入 manifest"],
	["汇总表", "同维度指标编排、列顺序、物化", "DWS/ADS 汇总表编排区"],
	["发布治理", "发布、调度、血缘、商城共享", "platform/dbt gate + BI Dataset"],
];

const summaryColumns = [
	["stat_month", "维度", "统计月份"],
	["dept_name", "维度", "责任科室"],
	["project_type", "维度", "项目类型"],
	["project_cnt", "指标", "项目总数"],
	["overdue_project_cnt", "指标", "延期项目数"],
	["direct_cost_execution_rate", "指标", "直接成本执行率"],
];

const publishChannels = [
	["dbt 门禁", "PENDING", "提交 /api/etl/dbt/release/submit 后生成发布记录"],
	["血缘注册", "PASS", "source_model -> DWS -> ADS -> BI Dataset"],
	["调度编排", "REVIEW", "汇总表可挂到平台工作流，按刷新周期运行"],
	["资产门户", "REVIEW", "发布后进入数据资产门户，上架前仍需平台审批"],
];

const uniqueDesignerField = (list: DesignerField[], field: DesignerField) =>
	list.some((item) => item.name === field.name && item.tableName === field.tableName) ? list : [...list, field];

const readDesignerField = (event: DragEvent<HTMLElement>) => {
	const raw =
		event.dataTransfer.getData("application/vnd.dts-metrics-field") ||
		event.dataTransfer.getData("application/json");
	if (!raw) {
		return null;
	}
	try {
		const parsed = JSON.parse(raw) as DesignerField;
		if (parsed?.name && parsed?.tableName) {
			return parsed;
		}
	} catch {
		return null;
	}
	return null;
};

const expressionForDesignerField = (field: DesignerField) => {
	const normalized = `${field.name} ${field.label}`.toLowerCase();
	const isNumeric = /int|number|decimal|double|float|numeric/.test(field.dataType.toLowerCase());
	if (normalized.includes("rate") || field.label.includes("率")) {
		return `avg(${field.name})`;
	}
	if (normalized.includes("flag") || normalized.startsWith("is_") || field.label.includes("是否")) {
		return `count_if(${field.name} = true)`;
	}
	if (normalized.endsWith("_id") || normalized.includes(" id")) {
		return `count_distinct(${field.name})`;
	}
	if (isNumeric) {
		return `sum(${field.name})`;
	}
	return `count(${field.name})`;
};

const formulaYamlForDesignerField = (field: DesignerField) => {
	const normalized = `${field.name} ${field.label}`.toLowerCase();
	const isNumeric = /int|number|decimal|double|float|numeric/.test(field.dataType.toLowerCase());
	if (normalized.includes("flag") || normalized.startsWith("is_") || field.label.includes("是否")) {
		return `      type: count_if
      condition:
        field: ${field.name}
        operator: "="
        value: true`;
	}
	if (normalized.endsWith("_id") || normalized.includes(" id")) {
		return `      type: aggregation
      aggregation: count_distinct
      field: ${field.name}`;
	}
	if (isNumeric) {
		return `      type: aggregation
      aggregation: sum
      field: ${field.name}`;
	}
	return `      type: aggregation
      aggregation: count
      field: ${field.name}`;
};

const buildDesignerDsl = (
	{
		sourceName,
		dimensions,
		metrics,
		active,
		metricKind,
		timePeriod,
		filters,
	}: {
		sourceName: string;
		dimensions: DesignerField[];
		metrics: DesignerField[];
		active?: FormulaBlock;
		metricKind: MetricKind;
		timePeriod: TimePeriodOption;
		filters: FilterDraft[];
	},
) => {
	const dimensionLines = dimensions.length
		? dimensions.map((field) => `  - field: ${field.name}\n    standard_code: ${field.name}`).join("\n")
		: "  - field: stat_month\n    standard_code: stat_month";
	const activeCode = safeCode(active?.code ?? "project_cnt");
	const activeTerm = `glossary.${activeCode}`;
	const metricLines = metrics.length
		? metrics
				.map((field) => {
					const code = safeCode(field.name);
					return `  - metric_code: ${code}
    metric_name: ${field.label}
    metric_kind: ${metricKind}
    development_state: in_design
    term_ids:
      - glossary.${code}
    time_period: ${timePeriod.key}
    expression_preview: ${expressionForDesignerField(field)}
    formula:
${formulaYamlForDesignerField(field)}`;
				})
				.join("\n")
		: `  - metric_code: ${activeCode}
    metric_name: ${active?.name ?? "项目总数"}
    metric_kind: ${metricKind}
    development_state: in_design
    term_ids:
      - ${activeTerm}
    time_period: ${timePeriod.key}
    expression_preview: ${active?.display ?? "count_distinct(project_id)"}
    formula:
      type: aggregation
      aggregation: count_distinct
      field: project_id`;
	const metricTerms = metrics.length
		? metrics.map((field) => `    - type: GLOSSARY_TERM\n      id: glossary.${safeCode(field.name)}`).join("\n")
		: `    - type: GLOSSARY_TERM\n      id: ${activeTerm}`;
	const standards = dimensions.length
		? dimensions.map((field) => `    - ${field.name}`).join("\n")
		: "    - stat_month";
	const filterLines = filters.length
		? filters
				.map((filter) => {
					const valueBlock = filter.values?.length
						? `\n    value:\n${filter.values.map((value) => `      - ${value}`).join("\n")}`
						: filter.value
							? `\n    value: ${filter.value}`
							: "";
					return `  - field: ${filter.field}
    operator: ${filter.operator}${valueBlock}`;
				})
				.join("\n")
		: "  - disabled: true";

	return `pack_id: visual-designer-draft
pack_name: 可视化指标配置草稿
version: 0.1.0
industry: project
edition_required: foundation
tenant_namespace: demo
security:
  apply_rls: true
source_model: ${sourceName}
engineering:
  metric_kind: ${metricKind}
  subject_model: ${sourceName}
  development_state: in_design
  time_period:
    key: ${timePeriod.key}
    label: ${timePeriod.label}
    window: ${timePeriod.window}
    expression: ${timePeriod.expression}
    refresh: ${timePeriod.refresh}
  filters:
${filterLines}
dimensions:
${dimensionLines}
metrics:
${metricLines}
files:
  domains: domains.yml
  business_objects: business_objects.yml
  dimensions: dimensions.yml
  metrics: metrics.yml
  models: models.yml
  datasets: datasets.yml
dependencies:
  data_standards:
${standards}
  platform_assets:
    - type: DATASET
      id: ${sourceName}
${metricTerms}`;
};

const buildSummaryTableDsl = (model?: ModelCandidate) => {
	const modelName = safeCode(model?.name ?? "dws_project_month_summary");
	const layer = model?.layer ?? "DWS";
	const grain = model?.grain ?? "stat_month + dept_name + project_type";
	const refresh = model?.refresh ?? "daily 02:30";

	return `pack_id: summary-table-draft
pack_name: 项目管理汇总表草稿
version: 0.1.0
industry: project
edition_required: foundation
tenant_namespace: demo
security:
  apply_rls: true
source_model: dwd_project_detail
dimensions:
  - field: stat_month
    standard_code: stat_month
  - field: dept_name
    standard_code: dept_name
  - field: project_type
    standard_code: project_type
metrics:
  - metric_code: project_cnt
    metric_name: 项目总数
    metric_kind: derived
    term_ids:
      - glossary.project_cnt
    formula:
      type: aggregation
      aggregation: count_distinct
      field: project_id
  - metric_code: overdue_project_cnt
    metric_name: 延期项目数
    metric_kind: derived
    term_ids:
      - glossary.overdue_project_cnt
    formula:
      type: count_if
      condition:
        field: overdue_flag
        operator: "="
        value: true
  - metric_code: direct_cost_execution_rate
    metric_name: 直接成本执行率
    metric_kind: composite
    term_ids:
      - glossary.direct_cost_execution_rate
    formula:
      type: ratio
      numerator:
        type: aggregation
        aggregation: sum
        field: direct_cost_amount
      denominator:
        type: aggregation
        aggregation: sum
        field: direct_cost_control_amount
      multiply: 100
summary_tables:
  - name: ${modelName}
    layer: ${layer}
    grain: ${grain}
    materialization: ${model?.materialization ?? "incremental table"}
    refresh: ${refresh}
    column_order:
${summaryColumns.map(([name]) => `      - ${name}`).join("\n")}
    publish_target:
      bi_dataset: 项目驾驶舱
      marketplace: data_asset_portal
files:
  domains: domains.yml
  business_objects: business_objects.yml
  dimensions: dimensions.yml
  metrics: metrics.yml
  models: models.yml
  datasets: datasets.yml
dependencies:
  data_standards:
    - stat_month
    - dept_name
    - project_type
  platform_assets:
    - type: DATASET
      id: dwd_project_detail
    - type: GLOSSARY_TERM
      id: glossary.project_cnt
    - type: GLOSSARY_TERM
      id: glossary.overdue_project_cnt
    - type: GLOSSARY_TERM
      id: glossary.direct_cost_execution_rate`;
};

const safeCode = (value: string) =>
	value
		.trim()
		.toLowerCase()
		.replace(/[^a-z0-9_]+/g, "_")
		.replace(/^_+|_+$/g, "") || "metric";

const defaultWorkspace = {
	platformContracts: [
		["登录态", "platform-forward-auth", "已接入"],
		["资产权限", "/api/internal/asset-permission/check", "已接入"],
		["主题域", "/api/internal/domains/resolve", "已接入"],
		["数据标准", "/api/internal/data-standards/resolve", "已接入"],
		["业务术语", "/api/internal/glossary/terms/resolve", "已接入"],
		["发布门禁", "/api/etl/dbt/release/submit", "待联调"],
	],
	metricAssets: [
		{
			code: "project_cnt",
			name: "项目总数",
			domain: "项目管理",
			type: "count_distinct",
			grain: "月份 / 科室",
			status: "PUBLISHED",
			version: "1.0.0",
			owner: "项目管理部",
			terms: ["glossary.project"],
			consumer: "项目驾驶舱",
		},
		{
			code: "direct_cost_execution_rate",
			name: "直接成本执行率",
			domain: "项目管理",
			type: "ratio",
			grain: "月份 / 科室 / 项目",
			status: "REVIEW",
			version: "0.2.0",
			owner: "财务管理部",
			terms: ["glossary.direct_cost_execution_rate"],
			consumer: "经营分析看板",
		},
		{
			code: "overdue_project_cnt",
			name: "延期项目数",
			domain: "项目管理",
			type: "count_if",
			grain: "月份 / 科室",
			status: "DRAFT",
			version: "0.1.0",
			owner: "项目管理部",
			terms: ["glossary.project_risk"],
			consumer: "风险预警列表",
		},
	],
	subjectMappings: [
		{
			domain: "项目管理",
			code: "project",
			platformState: "ACTIVE",
			assets: 8,
			metrics: 14,
			standards: "stat_month, dept_name, project_type",
			gap: "缺少项目风险术语 owner",
		},
		{
			domain: "采购管理",
			code: "procurement",
			platformState: "ACTIVE",
			assets: 5,
			metrics: 9,
			standards: "supplier_id, supplier_name, stat_month",
			gap: "准时交付口径待审核",
		},
	],
	objectJoins: [
		{
			object: "项目",
			source: "dwd_project_detail",
			key: "project_id",
			grain: "one row per project per month",
			joins: [
				["dwd_project_budget", "project_id", "left", "1:1"],
				["dwd_project_risk", "project_id", "left", "1:N pre-aggregate"],
				["dim_department", "dept_id", "left", "SCD-1"],
			],
			guardrails: ["join key not null", "risk table pre-aggregated", "dept dimension conforms to platform standard"],
		},
		{
			object: "供应商",
			source: "dwd_purchase_order_detail",
			key: "supplier_id",
			grain: "one row per supplier per month",
			joins: [
				["dim_supplier", "supplier_id", "left", "SCD-2"],
				["dwd_receive_detail", "po_id", "left", "N:1 aggregate"],
			],
			guardrails: ["supplier_id mapped to data standard", "late arrival handled by incremental window"],
		},
	],
	formulaBlocks: [
		{
			code: "project_cnt",
			name: "项目总数",
			display: "count_distinct(project_id)",
			unit: "个",
			format: "integer",
			warning: "none",
			dsl: "formula:\n  type: aggregation\n  aggregation: count_distinct\n  field: project_id",
		},
		{
			code: "direct_cost_execution_rate",
			name: "直接成本执行率",
			display: "sum(direct_cost_amount) / sum(direct_cost_control_amount) * 100",
			unit: "%",
			format: "percent",
			warning: ">= 90 标红",
			dsl:
				"formula:\n  type: ratio\n  numerator:\n    type: aggregation\n    aggregation: sum\n    field: direct_cost_amount\n  denominator:\n    type: aggregation\n    aggregation: sum\n    field: direct_cost_control_amount\n  multiply: 100\n  zero_division: null",
		},
	],
	modelCandidates: [
		{
			layer: "DWS",
			name: "dws_project_month_summary",
			purpose: "项目月度公共汇总模型，可复用于驾驶舱、科室看板和风险分析。",
			grain: "stat_month + dept_name + project_type",
			materialization: "incremental table",
			refresh: "daily 02:30",
			fields: ["stat_month", "dept_name", "project_type", "project_cnt", "overdue_project_cnt", "direct_cost_execution_rate"],
			sql:
				"select\n  stat_month,\n  dept_name,\n  project_type,\n  count(distinct project_id) as project_cnt\nfrom {{ ref('dwd_project_detail') }}\ngroup by stat_month, dept_name, project_type",
		},
		{
			layer: "ADS",
			name: "ads_project_dashboard_overview",
			purpose: "直接服务项目管理综合驾驶舱，减少 BI 工具二次 Join。",
			grain: "stat_month + dashboard_scope",
			materialization: "table",
			refresh: "daily 03:00",
			fields: ["project_cnt", "active_project_cnt", "overdue_project_cnt", "cost_warning_level", "top_dept_name"],
			sql:
				"select\n  stat_month,\n  sum(project_cnt) as project_cnt,\n  sum(overdue_project_cnt) as overdue_project_cnt\nfrom {{ ref('dws_project_month_summary') }}\ngroup by stat_month",
		},
	],
	publishGates: [
		["结构校验", "PASS", "指标包 schema、依赖声明和文件引用通过"],
		["平台权限", "PASS", "当前用户具备来源资产 READ 权限"],
		["术语绑定", "PASS", "指标绑定的 glossary term 已在 platform 激活"],
		["RLS 注入", "PASS", "生成 SQL 强制承接 platform 用户策略"],
		["dbt 门禁", "PENDING", "等待提交 /api/etl/dbt/release/submit"],
	],
	runRecords: [
		["dws_project_month_summary", "DWS", "SUCCESS", "2026-05-17 02:32", "48s", "fresh"],
		["ads_project_dashboard_overview", "ADS", "SUCCESS", "2026-05-17 03:04", "23s", "fresh"],
		["dws_supplier_month_summary", "DWS", "WARNING", "2026-05-17 02:41", "55s", "late source rows"],
		["ads_inventory_risk_board", "ADS", "PENDING", "-", "-", "waiting for governance"],
	],
};

type WorkspaceState = typeof defaultWorkspace;

const statusOk = new Set(["PASS", "SUCCESS", "ACTIVE", "PUBLISHED", "已接入", "fresh", "UP"]);
const statusWarn = new Set([
	"WARNING",
	"REVIEW",
	"PENDING",
	"PENDING_GOVERNANCE",
	"待联调",
	"late source rows",
	"waiting for governance",
]);

function normalizePath(pathname: string): string {
	const value = String(pathname || "/metrics/center").replace(/\/+$/, "");
	if (!value || value === "/metrics") return "/metrics/center";
	return value;
}

function routeHref(path: string, embedded: boolean): string {
	if (!embedded) return path;
	const url = new URL(path, window.location.origin);
	url.searchParams.set("embedded", "1");
	return `${url.pathname}${url.search}${url.hash}`;
}

function pretty(value: unknown): string {
	return JSON.stringify(value, null, 2);
}

function outputError(error: unknown): string {
	return error instanceof Error ? error.message : String(error);
}

function mergeWorkspace(snapshot: WorkspaceSnapshot): WorkspaceState {
	return {
		platformContracts: snapshot.platformContracts ?? defaultWorkspace.platformContracts,
		metricAssets: snapshot.metricAssets ?? defaultWorkspace.metricAssets,
		subjectMappings: snapshot.subjectMappings ?? defaultWorkspace.subjectMappings,
		objectJoins: snapshot.objectJoins ?? defaultWorkspace.objectJoins,
		formulaBlocks: snapshot.formulaBlocks ?? defaultWorkspace.formulaBlocks,
		modelCandidates: snapshot.modelCandidates ?? defaultWorkspace.modelCandidates,
		publishGates: snapshot.publishGates ?? defaultWorkspace.publishGates,
		runRecords: snapshot.runRecords ?? defaultWorkspace.runRecords,
	};
}

function countByStatus(items: Array<{ status: string }>, status: string): number {
	return items.filter((item) => item.status === status).length;
}

function statusLabel(value?: boolean): string {
	if (value === false) return "未启用";
	if (value === true) return "已启用";
	return "待确认";
}

function currentTimeLabel(): string {
	return new Intl.DateTimeFormat("zh-CN", {
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
	}).format(new Date());
}

export default function App() {
	const embedded = new URLSearchParams(window.location.search).get("embedded") === "1";
	const activePath = normalizePath(window.location.pathname);
	const activeRoute = routeItems.find((item) => item.path === activePath) ?? routeItems[0];
	const [workspace, setWorkspace] = useState<WorkspaceState>(defaultWorkspace);
	const [health, setHealth] = useState<MetricsHealth | null>(null);
	const [capabilities, setCapabilities] = useState<MetricsCapabilities | null>(null);
	const [serviceError, setServiceError] = useState<string | null>(null);
	const [manifest, setManifest] = useState(sampleManifest);
	const [outputs, setOutputs] = useState<Record<string, string>>({});
	const [messages, setMessages] = useState<Record<string, { text: string; tone?: StatusTone }>>({});
	const [busy, setBusy] = useState<Record<string, boolean>>({});

	const setOutput = useCallback((key: string, value: string) => {
		setOutputs((current) => ({ ...current, [key]: value }));
	}, []);

	const setMessage = useCallback((key: string, text: string, tone?: StatusTone) => {
		setMessages((current) => ({ ...current, [key]: { text, tone } }));
	}, []);

	const withBusy = useCallback(async (key: string, task: () => Promise<void>) => {
		setBusy((current) => ({ ...current, [key]: true }));
		try {
			await task();
		} finally {
			setBusy((current) => ({ ...current, [key]: false }));
		}
	}, []);

	const loadCapabilitiesInto = useCallback(
		(outputKey: string) =>
			withBusy(outputKey, async () => {
				setOutput(outputKey, "读取中...");
				try {
					const result = await fetchJson<MetricsCapabilities>("/api/metrics/capabilities");
					setCapabilities(result);
					setOutput(outputKey, pretty(result));
				} catch (error) {
					setOutput(outputKey, outputError(error));
				}
			}),
		[setOutput, withBusy],
	);

	const submitManifest = useCallback(
		(actionKey: string, url: string, loading: string, success: string, invalid: string, failure: string) =>
			withBusy(actionKey, async () => {
				setOutput("manifest", "提交中...");
				setMessage("manifest", loading);
				try {
					const result = await postYaml<Record<string, unknown>>(url, manifest);
					setOutput("manifest", pretty(result));
					const ok = result.valid === true || result.accepted === true;
					setMessage("manifest", ok ? success : invalid, ok ? "ok" : "warn");
				} catch (error) {
					setOutput("manifest", outputError(error));
					setMessage("manifest", failure, "warn");
				}
			}),
		[manifest, setMessage, setOutput, withBusy],
	);

	const submitStaticManifest = useCallback(
		(outputKey: string, url: string, content = sampleManifest) =>
			withBusy(outputKey, async () => {
				setOutput(outputKey, "提交中...");
				try {
					const result = await postYaml<Record<string, unknown>>(url, content);
					setOutput(outputKey, pretty(result));
				} catch (error) {
					setOutput(outputKey, outputError(error));
				}
			}),
		[setOutput, withBusy],
	);

	const loadMigrationDryRun = useCallback(
		() =>
			withBusy("migration", async () => {
				setOutput("migration", "读取中...");
				try {
					const result = await fetchJson<Record<string, unknown>>("/api/metrics/migration/semantic-dry-run");
					setOutput("migration", pretty(result));
				} catch (error) {
					setOutput("migration", outputError(error));
				}
			}),
		[setOutput, withBusy],
	);

	useEffect(() => {
		document.documentElement.classList.toggle("embedded", embedded);
		document.body.classList.toggle("embedded", embedded);
	}, [embedded]);

	useEffect(() => {
		let cancelled = false;
		async function load() {
			try {
				const [healthResult, capabilityResult, snapshot] = await Promise.all([
					fetchJson<MetricsHealth>("/api/metrics/health"),
					fetchJson<MetricsCapabilities>("/api/metrics/capabilities"),
					fetchJson<WorkspaceSnapshot>("/api/metrics/workspace/snapshot"),
				]);
				if (cancelled) return;
				setHealth(healthResult);
				setCapabilities(capabilityResult);
				setWorkspace(mergeWorkspace(snapshot));
				setServiceError(null);
			} catch (error) {
				if (cancelled) return;
				setServiceError(outputError(error));
			}
		}
		void load();
		return () => {
			cancelled = true;
		};
	}, []);

	const summaryCards = useMemo(
		() => [
			["服务归属", capabilities?.service || "dts-metrics"],
			["启用状态", statusLabel(capabilities?.enabled)],
			["当前版本", capabilities?.edition || "foundation"],
			["权限事实源", "dts-platform"],
		],
		[capabilities],
	);

	return (
		<div className="app-shell">
			<header className="topbar">
				<div>
					<p className="eyebrow">DTS Metrics Service</p>
					<h1>指标与语义中心</h1>
				</div>
				<ServicePill health={health} error={serviceError} />
			</header>

			<div className="layout">
				<nav className="side-nav" aria-label="指标与语义中心导航">
					{routes.map((group) => (
						<div className="nav-group" key={group.group}>
							<p className="nav-group-title">{group.group}</p>
							{group.items.map((item) => (
								<a
									className={`nav-link ${item.path === activeRoute.path ? "active" : ""}`}
									href={routeHref(item.path, embedded)}
									key={item.path}
								>
									{item.title}
								</a>
							))}
						</div>
					))}
				</nav>

				<main className="content">
					<section className="hero">
						<div>
							<p className="eyebrow">{activeRoute.stage}</p>
							<h2>{activeRoute.title}</h2>
							<p>{activeRoute.description}</p>
						</div>
						<div className="hero-actions">
							<a className="button" href={routeHref("/metrics/dictionary", embedded)}>
								指标字典
							</a>
							<a className="button primary" href={routeHref("/metrics/semantic/metrics", embedded)}>
								配置指标
							</a>
						</div>
					</section>

					<section className="grid cards">
						{summaryCards.map(([label, value]) => (
							<article className="card" key={label}>
								<p className="card-label">{label}</p>
								<p className="card-value">{value}</p>
							</article>
						))}
					</section>

					<section className="panel">
						<RoutePanel
							activeRoute={activeRoute}
							capabilities={capabilities}
							embedded={embedded}
							workspace={workspace}
							manifest={manifest}
							setManifest={setManifest}
							outputs={outputs}
							messages={messages}
							busy={busy}
							loadCapabilitiesInto={loadCapabilitiesInto}
							submitManifest={submitManifest}
							submitStaticManifest={submitStaticManifest}
							loadMigrationDryRun={loadMigrationDryRun}
						/>
					</section>
				</main>
			</div>
		</div>
	);
}

function ServicePill({ health, error }: { health: MetricsHealth | null; error: string | null }) {
	if (error) {
		return (
			<div className="service-pill warn" title={error}>
				服务状态不可用
			</div>
		);
	}
	return <div className="service-pill ok">{`${health?.service || "dts-metrics"} ${health?.status || "UP"}`}</div>;
}

interface RoutePanelProps {
	activeRoute: RouteItem;
	capabilities: MetricsCapabilities | null;
	embedded: boolean;
	workspace: WorkspaceState;
	manifest: string;
	setManifest: (value: string) => void;
	outputs: Record<string, string>;
	messages: Record<string, { text: string; tone?: StatusTone }>;
	busy: Record<string, boolean>;
	loadCapabilitiesInto: (outputKey: string) => Promise<void>;
	submitManifest: (actionKey: string, url: string, loading: string, success: string, invalid: string, failure: string) => Promise<void>;
	submitStaticManifest: (outputKey: string, url: string, content?: string) => Promise<void>;
	loadMigrationDryRun: () => Promise<void>;
}

function RoutePanel(props: RoutePanelProps) {
	const { activeRoute } = props;
	switch (activeRoute.path) {
		case "/metrics/center":
			return <CenterPage {...props} />;
		case "/metrics/dictionary":
			return <MetricAssetsPage {...props} />;
		case "/metrics/packs":
			return <ManifestPage {...props} />;
		case "/metrics/semantic":
			return <SemanticFlowPage embedded={props.embedded} />;
		case "/metrics/semantic/subjects":
			return <SubjectMappingPage {...props} />;
		case "/metrics/semantic/objects":
			return <BusinessObjectJoinPage {...props} />;
		case "/metrics/semantic/metrics":
			return <FormulaConfigPage {...props} />;
		case "/metrics/semantic/models":
			return <ModelGenerationPage {...props} />;
		case "/metrics/semantic/publish":
			return <PublishPage {...props} />;
		case "/metrics/semantic/runs":
		case "/metrics/operations":
			return <RunMonitorPage {...props} />;
		case "/metrics/migration":
			return <MigrationPage {...props} />;
		default:
			return <CenterPage {...props} />;
	}
}

function CenterPage({ capabilities, embedded, workspace }: RoutePanelProps) {
	const published = countByStatus(workspace.metricAssets, "PUBLISHED");
	const review = countByStatus(workspace.metricAssets, "REVIEW");
	const draft = countByStatus(workspace.metricAssets, "DRAFT");
	const connectedContracts = workspace.platformContracts.filter(([, , status]) => status === "connected" || status === "已接入").length;
	const totalContracts = workspace.platformContracts.length || 1;
	const readyModels = workspace.modelCandidates.length;

	return (
		<>
			<div className="section-head">
				<div>
					<h3>指标交付工作台</h3>
					<p>把 Sprint-31A 的资产事实源、Sprint-31 的发布门禁和 Sprint-32 的 metrics 服务收拢成一条可执行路径。</p>
				</div>
				<div className="toolbar">
					<a className="button" href={routeHref("/metrics/dictionary", embedded)}>
						查看指标资产
					</a>
					<a className="button primary" href={routeHref("/metrics/packs", embedded)}>
						提交指标包
					</a>
				</div>
			</div>

			<div className="command-strip">
				<div>
					<span>服务</span>
					<strong>{capabilities?.service || "dts-metrics"}</strong>
				</div>
				<div>
					<span>契约接入</span>
					<strong>
						{connectedContracts}/{totalContracts}
					</strong>
				</div>
				<div>
					<span>指标资产</span>
					<strong>{workspace.metricAssets.length}</strong>
				</div>
				<div>
					<span>DWS/ADS 候选</span>
					<strong>{readyModels}</strong>
				</div>
				<div>
					<span>刷新</span>
					<strong>{currentTimeLabel()}</strong>
				</div>
			</div>

			<div className="workbench-grid">
				<section className="section focus-panel">
					<div className="section-title-row">
						<h4>当前交付状态</h4>
						<StatusPill value={review > 0 ? "REVIEW" : "PASS"} />
					</div>
					<div className="metric-ring" aria-label="指标资产状态概览">
						<div>
							<strong>{workspace.metricAssets.length}</strong>
							<span>指标资产</span>
						</div>
					</div>
					<div className="status-breakdown">
						<div>
							<span className="dot ok-dot" />
							已发布 {published}
						</div>
						<div>
							<span className="dot warn-dot" />
							审核中 {review}
						</div>
						<div>
							<span className="dot neutral-dot" />
							草稿 {draft}
						</div>
					</div>
					<p>发布态指标可进入 BI/大屏消费；审核中和草稿需要先补齐术语、权限和 dbt 门禁材料。</p>
				</section>

				<section className="section flow-board">
					<h4>指标工程闭环</h4>
					<div className="flow-lanes">
						{metricEngineeringStages.map(([step, title, desc, href]) => (
							<a className="flow-lane" href={routeHref(href, embedded)} key={title}>
								<span>{step}</span>
								<strong>{title}</strong>
								<em>{desc}</em>
							</a>
						))}
					</div>
				</section>
			</div>

			<div className="page-grid two-columns">
				<section className="section">
					<h4>平台契约状态</h4>
					<KeyTable headers={["能力", "接口/事实源", "状态"]} rows={workspace.platformContracts} />
					<LiveContract capabilities={capabilities} />
				</section>
				<section className="section">
					<h4>交付漏斗</h4>
					<div className="funnel">
						{[
							["1", "指标包校验", "结构、术语、主题域、数据标准"],
							["2", "候选生成物", "DWS/ADS SQL、schema.yml、BI Dataset"],
							["3", "平台门禁", "权限、dbt 发布、审计、血缘注册"],
							["4", "消费发布", "BI 图表、大屏、API 查询"],
						].map(([step, title, text]) => (
							<div className="funnel-row" key={step}>
								<span>{step}</span>
								<strong>{title}</strong>
								<em>{text}</em>
							</div>
						))}
					</div>
				</section>
			</div>
			<section className="section">
				<div className="section-title-row">
					<h4>TDS 能力对照</h4>
					<span className="chip active">Megaindex review</span>
				</div>
				<KeyTable headers={["能力域", "竞品做法", "本轮落点"]} rows={benchmarkCapabilityRows} />
			</section>
		</>
	);
}

function LiveContract({ capabilities }: { capabilities: MetricsCapabilities | null }) {
	const contract = capabilities?.platformContract ?? {};
	return (
		<div className="live-contract">
			<h5>实时能力响应</h5>
			<KeyTable
				headers={["字段", "值"]}
				rows={[
					["metrics 服务", capabilities?.service || "等待响应"],
					["platform 地址", contract.platformBaseUrl || "等待响应"],
					["platform API 前缀", contract.apiPath || "等待响应"],
					["服务 Token", contract.serviceTokenConfigured === true ? "已配置" : contract.serviceTokenConfigured === false ? "未配置" : "等待响应"],
					["认证头", contract.authHeaders ? `${contract.authHeaders.service} + ${contract.authHeaders.token}` : "等待响应"],
				]}
			/>
			{capabilities?.mvp?.length ? (
				<div className="field-tags">
					{capabilities.mvp.map((item) => (
						<span key={item}>{item}</span>
					))}
				</div>
			) : null}
		</div>
	);
}

function MetricAssetsPage({ embedded, workspace, outputs, busy, loadCapabilitiesInto }: RoutePanelProps) {
	const assets = workspace.metricAssets;
	const [query, setQuery] = useState("");
	const [statusFilter, setStatusFilter] = useState("ALL");
	const visibleAssets = useMemo(() => {
		const normalized = query.trim().toLowerCase();
		return assets.filter((item) => {
			const statusMatched = statusFilter === "ALL" || item.status === statusFilter;
			if (!statusMatched) return false;
			if (!normalized) return true;
			return [item.name, item.code, item.domain, item.owner, item.consumer, item.type].some((field) =>
				String(field || "").toLowerCase().includes(normalized),
			);
		});
	}, [assets, query, statusFilter]);
	const selected = visibleAssets[0] ?? assets[0];

	return (
		<>
			<div className="section-head">
				<div>
					<h3>指标资产列表</h3>
					<p>指标资产以 code 和 version 为主键，下游大屏和 BI 应固定到版本，避免口径变更自动漂移。</p>
				</div>
				<div className="toolbar">
					<button className="button" disabled={busy.dictionaryContract} type="button" onClick={() => void loadCapabilitiesInto("dictionaryContract")}>
						读取平台契约
					</button>
					<a className="button primary" href={routeHref("/metrics/semantic/metrics", embedded)}>
						新建指标
					</a>
				</div>
			</div>

			<div className="list-toolbar">
				<label className="search-field">
					<span>搜索</span>
					<input
						placeholder="指标名、编码、负责人、消费方"
						value={query}
						onChange={(event) => setQuery(event.target.value)}
					/>
				</label>
				<div className="toolbar filters" aria-label="指标状态筛选">
					{[
						["ALL", `全部 ${assets.length}`],
						["PUBLISHED", `已发布 ${countByStatus(assets, "PUBLISHED")}`],
						["REVIEW", `审核中 ${countByStatus(assets, "REVIEW")}`],
						["DRAFT", `草稿 ${countByStatus(assets, "DRAFT")}`],
					].map(([value, label]) => (
						<button
							className={`chip ${statusFilter === value ? "active" : ""}`}
							key={value}
							type="button"
							onClick={() => setStatusFilter(value)}
						>
							{label}
						</button>
					))}
				</div>
			</div>

			<div className="detail-layout">
				<div className="table-wrap">
					<table className="data-table">
						<thead>
							<tr>
								<th>指标</th>
								<th>主题域</th>
								<th>类型</th>
								<th>统计粒度</th>
								<th>版本</th>
								<th>状态</th>
								<th>负责人</th>
								<th>消费方</th>
							</tr>
						</thead>
						<tbody>
							{visibleAssets.length ? (
								visibleAssets.map((item) => <MetricAssetRow item={item} key={item.code} />)
							) : (
								<tr>
									<td colSpan={8}>
										<EmptyState title="没有匹配的指标" description="换一个关键词或状态筛选即可恢复列表。" />
									</td>
								</tr>
							)}
						</tbody>
					</table>
				</div>
				{selected ? (
					<section className="section inspector">
						<div className="section-title-row">
							<h4>{selected.name}</h4>
							<StatusPill value={selected.status} />
						</div>
						<dl className="meta-list">
							<div>
								<dt>指标编码</dt>
								<dd>{selected.code}</dd>
							</div>
							<div>
								<dt>版本</dt>
								<dd>{selected.version}</dd>
							</div>
							<div>
								<dt>负责人</dt>
								<dd>{selected.owner}</dd>
							</div>
							<div>
								<dt>消费方</dt>
								<dd>{selected.consumer}</dd>
							</div>
						</dl>
						<div className="field-tags">
							{selected.terms.map((term) => (
								<span key={term}>{term}</span>
							))}
						</div>
						<div className="action-stack">
							<a className="button primary" href={routeHref("/metrics/semantic/metrics", embedded)}>
								进入公式配置
							</a>
							<a className="button" href={routeHref("/metrics/semantic/publish", embedded)}>
								查看发布门禁
							</a>
						</div>
					</section>
				) : null}
			</div>
			<section className="section muted-section">
				<h4>绑定要求</h4>
				<p>每个指标必须声明 glossary term、source_model、platform asset dependency、统计粒度和默认展示格式。预览和发布阶段都要走 platform 权限与审计链。</p>
			</section>
			<JsonOutput value={outputs.dictionaryContract || "等待读取平台契约"} />
		</>
	);
}

function MetricAssetRow({ item }: { item: MetricAsset }) {
	return (
		<tr>
			<td>
				<strong>{item.name}</strong>
				<span>{item.code}</span>
			</td>
			<td>{item.domain}</td>
			<td>{item.type}</td>
			<td>{item.grain}</td>
			<td>{item.version}</td>
			<td>
				<StatusPill value={item.status} />
			</td>
			<td>{item.owner}</td>
			<td>{item.consumer}</td>
		</tr>
	);
}

function ManifestPage({ embedded, manifest, setManifest, outputs, messages, busy, submitManifest }: RoutePanelProps) {
	const message = messages.manifest ?? { text: "等待校验" };
	return (
		<section className="section">
			<h3>指标包校验</h3>
			<p>合作方按指标包契约提交 YAML/JSON，dts-metrics 做结构校验、候选生成和导入预检；发布事实仍回到 platform。</p>
			<div className="manifest-grid">
				<div>
					<textarea aria-label="指标包内容" spellCheck={false} value={manifest} onChange={(event) => setManifest(event.target.value)} />
					<div className="toolbar">
						<button
							className="button primary"
							disabled={busy.validateManifest}
							type="button"
							onClick={() =>
								void submitManifest(
									"validateManifest",
									"/api/metrics/packs/validate",
									"校验中",
									"校验通过",
									"校验未通过，请查看结果",
									"校验请求失败",
								)
							}
						>
							校验指标包
						</button>
						<button
							className="button"
							disabled={busy.previewArtifacts}
							type="button"
							onClick={() =>
								void submitManifest(
									"previewArtifacts",
									"/api/metrics/packs/preview-artifacts",
									"生成预览中",
									"候选生成物已生成",
									"候选生成物未生成，请查看结果",
									"预览请求失败",
								)
							}
						>
							预览生成物
						</button>
						<button
							className="button"
							disabled={busy.dryRunImport}
							type="button"
							onClick={() =>
								void submitManifest(
									"dryRunImport",
									"/api/metrics/packs/import",
									"导入预检中",
									"导入预检通过",
									"导入预检未通过，请查看结果",
									"导入预检失败",
								)
							}
						>
							导入预检
						</button>
						<a className="button" href={routeHref("/metrics/semantic/metrics", embedded)}>
							进入指标配置
						</a>
					</div>
					<div className={`message ${message.tone || ""}`}>{message.text}</div>
				</div>
				<JsonOutput value={outputs.manifest || "暂无结果"} />
			</div>
		</section>
	);
}

function SemanticFlowPage({ embedded }: { embedded: boolean }) {
	return (
		<>
			<div className="section-head">
				<div>
					<h3>从指标口径到数据应用</h3>
					<p>参考 TDS Megaindex 的主体、原子指标、衍生指标、复合指标和汇总表流程，把语义中心拆成可执行的指标工程链路。</p>
				</div>
				<a className="button" href={routeHref("/metrics/semantic/metrics", embedded)}>
					进入公式配置
				</a>
			</div>
			<div className="workflow">
				{metricEngineeringStages.map(([step, title, text, href]) => (
					<a className="workflow-step" href={routeHref(href, embedded)} key={title}>
						<span>{step}</span>
						<strong>{title}</strong>
						<p>{text}</p>
					</a>
				))}
			</div>
			<div className="page-grid two-columns">
				<section className="section">
					<h3>平台事实源</h3>
					<div className="field-tags">
						<span>asset contract</span>
						<span>asset_grant</span>
						<span>glossary term</span>
						<span>data standard</span>
						<span>dbt publish gateway</span>
					</div>
				</section>
				<section className="section">
					<h3>工程输出</h3>
					<div className="field-tags">
						<span>dbt SQL</span>
						<span>schema.yml</span>
						<span>lineage hint</span>
						<span>BI Dataset candidate</span>
						<span>publish record</span>
					</div>
				</section>
			</div>
			<section className="section">
				<div className="section-title-row">
					<h3>产品对标结果</h3>
					<span className="chip">TDS 4.1</span>
				</div>
				<KeyTable headers={["能力域", "竞品做法", "本轮落点"]} rows={benchmarkCapabilityRows} />
			</section>
		</>
	);
}

function SubjectMappingPage({ embedded, workspace, outputs, busy, loadCapabilitiesInto }: RoutePanelProps) {
	return (
		<>
			<div className="section-head">
				<div>
					<h3>主题域映射</h3>
					<p>主题域不是 metrics 本地事实源，页面只展示引用、缺口和指标包声明状态，真实治理字段仍在 platform 数据资产中维护。</p>
				</div>
				<div className="toolbar">
					<button className="button" disabled={busy.subjectContract} type="button" onClick={() => void loadCapabilitiesInto("subjectContract")}>
						读取 platform capability
					</button>
					<a className="button" href={routeHref("/metrics/packs", embedded)}>
						查看 manifest
					</a>
				</div>
			</div>
			<div className="page-grid three-columns">
				{workspace.subjectMappings.map((item) => (
					<SubjectCard item={item} key={item.code} />
				))}
			</div>
			<JsonOutput value={outputs.subjectContract || "等待读取主题域契约"} />
		</>
	);
}

function SubjectCard({ item }: { item: SubjectMapping }) {
	return (
		<section className="section">
			<div className="section-title-row">
				<h4>{item.domain}</h4>
				<StatusPill value={item.platformState} />
			</div>
			<dl className="meta-list">
				<div>
					<dt>domain code</dt>
					<dd>{item.code}</dd>
				</div>
				<div>
					<dt>平台资产</dt>
					<dd>{item.assets}</dd>
				</div>
				<div>
					<dt>指标数</dt>
					<dd>{item.metrics}</dd>
				</div>
				<div>
					<dt>数据标准</dt>
					<dd>{item.standards}</dd>
				</div>
				<div>
					<dt>治理缺口</dt>
					<dd>{item.gap}</dd>
				</div>
			</dl>
		</section>
	);
}

function BusinessObjectJoinPage({ embedded, workspace, outputs, busy, submitStaticManifest }: RoutePanelProps) {
	return (
		<>
			<div className="section-head">
				<div>
					<h3>业务对象 Join 设计</h3>
					<p>Join 页面强调统计粒度和 fanout 防护。所有来源模型必须先在 manifest dependencies.platform_assets 中声明。</p>
				</div>
				<div className="toolbar">
					<button
						className="button"
						disabled={busy.objectPreview}
						type="button"
						onClick={() => void submitStaticManifest("objectPreview", "/api/metrics/packs/preview-artifacts")}
					>
						预览 Join 生成物
					</button>
					<a className="button primary" href={routeHref("/metrics/semantic/models", embedded)}>
						生成 DWS/ADS
					</a>
				</div>
			</div>
			<div className="page-grid two-columns">
				{workspace.objectJoins.map((item) => (
					<ObjectJoinCard item={item} key={item.object} />
				))}
			</div>
			<JsonOutput value={outputs.objectPreview || "等待预览 Join 候选物"} />
		</>
	);
}

function ObjectJoinCard({ item }: { item: ObjectJoin }) {
	return (
		<section className="section">
			<div className="section-title-row">
				<h4>{item.object}</h4>
				<span className="chip">{item.grain}</span>
			</div>
			<div className="join-chain">
				<div className="join-node primary-node">
					<strong>{item.source}</strong>
					<span>{item.key}</span>
				</div>
				{item.joins.map(([table, key, type, cardinality]) => (
					<div className="join-segment" key={`${table}-${key}`}>
						<div className="join-edge">
							{type} / {cardinality}
						</div>
						<div className="join-node">
							<strong>{table}</strong>
							<span>{key}</span>
						</div>
					</div>
				))}
			</div>
			<ul className="compact-list">
				{item.guardrails.map((guard) => (
					<li key={guard}>{guard}</li>
				))}
			</ul>
		</section>
	);
}

function FormulaConfigPage({ embedded, workspace, outputs, busy, submitStaticManifest }: RoutePanelProps) {
	const [activeCode, setActiveCode] = useState(workspace.formulaBlocks[1]?.code ?? workspace.formulaBlocks[0]?.code ?? "");
	const [sourceName, setSourceName] = useState(workspace.objectJoins[0]?.source ?? "dwd_project_detail");
	const [metricKind, setMetricKind] = useState<MetricKind>("derived");
	const [timePeriodKey, setTimePeriodKey] = useState(defaultTimePeriod.key);
	const [selectedFilterKeys, setSelectedFilterKeys] = useState<string[]>(() => ["project_type"]);
	const [dimensionDrafts, setDimensionDrafts] = useState<DesignerField[]>(() =>
		designerFieldPool.filter((field) => field.tableName === "dwd_project_detail" && ["stat_month", "dept_name"].includes(field.name)),
	);
	const [metricDrafts, setMetricDrafts] = useState<DesignerField[]>(() =>
		designerFieldPool.filter((field) => field.tableName === "dwd_project_detail" && ["project_id", "direct_cost_amount"].includes(field.name)),
	);
	const active = workspace.formulaBlocks.find((item) => item.code === activeCode) ?? workspace.formulaBlocks[0];
	const sourceOptions = useMemo(() => {
		const names = new Set([
			...workspace.objectJoins.map((item) => item.source),
			...designerFieldPool.map((item) => item.tableName),
		]);
		return Array.from(names);
	}, [workspace.objectJoins]);
	const fieldPool = useMemo(
		() => designerFieldPool.filter((field) => field.tableName === sourceName),
		[sourceName],
	);
	const selectedTimePeriod = timePeriodOptions.find((item) => item.key === timePeriodKey) ?? defaultTimePeriod;
	const selectedFilters = useMemo(
		() => filterDraftTemplates.filter((filter) => selectedFilterKeys.includes(filter.key)),
		[selectedFilterKeys],
	);
	const designerDsl = useMemo(
		() =>
			buildDesignerDsl({
				sourceName,
				dimensions: dimensionDrafts,
				metrics: metricDrafts,
				active,
				metricKind,
				timePeriod: selectedTimePeriod,
				filters: selectedFilters,
			}),
		[active, dimensionDrafts, metricDrafts, metricKind, selectedFilters, selectedTimePeriod, sourceName],
	);
	const handleDragStart = (event: DragEvent<HTMLButtonElement>, field: DesignerField) => {
		event.dataTransfer.setData("application/vnd.dts-metrics-field", JSON.stringify(field));
		event.dataTransfer.effectAllowed = "copy";
	};
	const handleDrop = (role: DesignerFieldRole) => (event: DragEvent<HTMLDivElement>) => {
		event.preventDefault();
		const field = readDesignerField(event);
		if (!field) {
			return;
		}
		if (role === "dimension") {
			setDimensionDrafts((current) => uniqueDesignerField(current, field));
			return;
		}
		setMetricDrafts((current) => uniqueDesignerField(current, field));
	};
	const allowDrop = (event: DragEvent<HTMLDivElement>) => {
		event.preventDefault();
		event.dataTransfer.dropEffect = "copy";
	};
	const toggleFilter = (key: string) => {
		setSelectedFilterKeys((current) => (current.includes(key) ? current.filter((item) => item !== key) : [...current, key]));
	};

	return (
		<>
			<div className="section-head">
				<div>
					<h3>指标公式配置</h3>
					<p>按原子、衍生、复合指标分层配置，时间周期、过滤条件和字段拖拽都会进入受控 DSL，预览和发布仍回到 platform 权限链。</p>
				</div>
				<div className="toolbar">
					<button
						className="button primary"
						disabled={busy.formulaPreview}
						type="button"
						onClick={() => void submitStaticManifest("formulaPreview", "/api/metrics/packs/preview-artifacts", designerDsl)}
					>
						预览当前公式生成物
					</button>
					<a className="button" href={routeHref("/metrics/packs", embedded)}>
						从指标包导入
					</a>
				</div>
			</div>
			<div className="metric-kind-tabs" role="tablist" aria-label="指标类型">
				{metricKindOptions.map((option) => (
					<button
						className={`metric-kind-tab ${metricKind === option.kind ? "active" : ""}`}
						key={option.kind}
						type="button"
						onClick={() => setMetricKind(option.kind)}
					>
						<strong>{option.title}</strong>
						<span>{option.description}</span>
						<em>{option.rule}</em>
					</button>
				))}
			</div>
			<div className="designer-layout">
				<section className="section designer-field-pool">
					<div className="section-title-row">
						<h4>字段池</h4>
						<span className="chip">{fieldPool.length} fields</span>
					</div>
					<label className="select-label">
						<span>来源 DWD 明细模型</span>
						<select value={sourceName} onChange={(event) => setSourceName(event.target.value)}>
							{sourceOptions.map((name) => (
								<option key={name} value={name}>
									{name}
								</option>
							))}
						</select>
					</label>
					<div className="designer-field-list">
						{fieldPool.map((field) => (
							<button
								className={`designer-field-card ${field.role}`}
								draggable
								key={`${field.tableName}-${field.name}`}
								type="button"
								onClick={() => {
									if (field.role === "dimension") {
										setDimensionDrafts((current) => uniqueDesignerField(current, field));
									} else {
										setMetricDrafts((current) => uniqueDesignerField(current, field));
									}
								}}
								onDragStart={(event) => handleDragStart(event, field)}
							>
								<strong>{field.label}</strong>
								<span>{field.name}</span>
								<em>
									{field.dataType} / {field.comment}
								</em>
							</button>
						))}
					</div>
				</section>

				<section className="section designer-canvas-panel">
					<div className="section-title-row">
						<h4>可视化配置画布</h4>
						<span className="chip active">{sourceName}</span>
					</div>
					<div className="designer-drop-grid">
						<DesignerDropZone
							emptyText="拖入统计月份、组织、类型等字段"
							fields={dimensionDrafts}
							onDragOver={allowDrop}
							onDrop={handleDrop("dimension")}
							onRemove={(field) =>
								setDimensionDrafts((current) =>
									current.filter((item) => item.name !== field.name || item.tableName !== field.tableName),
								)
							}
							title="维度区"
							tone="dimension"
						/>
						<DesignerDropZone
							emptyText="拖入金额、数量、状态判断等字段"
							fields={metricDrafts}
							onDragOver={allowDrop}
							onDrop={handleDrop("metric")}
							onRemove={(field) =>
								setMetricDrafts((current) =>
									current.filter((item) => item.name !== field.name || item.tableName !== field.tableName),
								)
							}
							title="指标区"
							tone="metric"
						/>
					</div>
					<div className="designer-control-grid">
						<label className="select-label">
							<span>统计时间周期</span>
							<select value={timePeriodKey} onChange={(event) => setTimePeriodKey(event.target.value)}>
								{timePeriodOptions.map((item) => (
									<option key={item.key} value={item.key}>
										{item.label} / {item.window}
									</option>
								))}
							</select>
						</label>
						<div className="filter-builder">
							<div className="drop-zone-head">
								<strong>过滤条件</strong>
								<span>{selectedFilters.length}</span>
							</div>
							<div className="filter-chip-list">
								{filterDraftTemplates.map((filter) => (
									<button
										className={`filter-chip ${selectedFilterKeys.includes(filter.key) ? "active" : ""}`}
										key={filter.key}
										type="button"
										onClick={() => toggleFilter(filter.key)}
									>
										<strong>{filter.label}</strong>
										<span>
											{filter.field} {filter.operator} {filter.valueLabel}
										</span>
									</button>
								))}
							</div>
						</div>
					</div>
					<div className="designer-pipeline">
						<span>{sourceName}</span>
						<span>{metricKindOptions.find((option) => option.kind === metricKind)?.title ?? "指标配置"}</span>
						<span>DWS 公共汇总</span>
						<span>ADS / BI Dataset</span>
					</div>
				</section>

				<section className="section designer-preview">
					<div className="section-title-row">
						<h4>DSL 预览</h4>
						<span className="chip">{dimensionDrafts.length + metricDrafts.length} blocks</span>
					</div>
					<div className="dsl-meta">
						<span>{selectedTimePeriod.label}</span>
						<span>{selectedFilters.length ? `${selectedFilters.length} filters` : "no filters"}</span>
						<span>{metricKind}</span>
					</div>
					<pre className="code-preview">{designerDsl}</pre>
				</section>
			</div>
			<div className="split-layout">
				<section className="section">
					<div className="section-title-row">
						<h4>指标清单</h4>
						<span className="chip">{workspace.formulaBlocks.length} items</span>
					</div>
					<div className="formula-list">
						{workspace.formulaBlocks.map((item) => (
							<button
								className={`formula-item ${active?.code === item.code ? "active" : ""}`}
								key={item.code}
								type="button"
								onClick={() => setActiveCode(item.code)}
							>
								<strong>{item.name}</strong>
								<span>{item.code}</span>
								<em>
									{item.unit} / {item.warning}
								</em>
							</button>
						))}
					</div>
				</section>
				{active ? <FormulaEditor active={active} /> : null}
			</div>
			<JsonOutput value={outputs.formulaPreview || designerDsl} />
		</>
	);
}

function DesignerDropZone({
	emptyText,
	fields,
	onDragOver,
	onDrop,
	onRemove,
	title,
	tone,
}: {
	emptyText: string;
	fields: DesignerField[];
	onDragOver: (event: DragEvent<HTMLDivElement>) => void;
	onDrop: (event: DragEvent<HTMLDivElement>) => void;
	onRemove: (field: DesignerField) => void;
	title: string;
	tone: DesignerFieldRole;
}) {
	return (
		<div className={`designer-drop-zone ${tone}`} onDragOver={onDragOver} onDrop={onDrop}>
			<div className="drop-zone-head">
				<strong>{title}</strong>
				<span>{fields.length}</span>
			</div>
			{fields.length ? (
				<div className="designer-node-grid">
					{fields.map((field) => (
						<div className="designer-node" key={`${field.tableName}-${field.name}`}>
							<div>
								<strong>{field.label}</strong>
								<span>{field.name}</span>
							</div>
							<button aria-label={`移除 ${field.label}`} type="button" onClick={() => onRemove(field)}>
								×
							</button>
						</div>
					))}
				</div>
			) : (
				<div className="designer-drop-empty">{emptyText}</div>
			)}
		</div>
	);
}

function FormulaEditor({ active }: { active: FormulaBlock }) {
	return (
		<section className="section">
			<div className="section-title-row">
				<h4>{active.name}</h4>
				<span className="chip active">{active.format}</span>
			</div>
			<div className="form-grid">
				<label>
					<span>指标编码</span>
					<input readOnly value={active.code} />
				</label>
				<label>
					<span>业务表达</span>
					<input readOnly value={active.display} />
				</label>
				<label>
					<span>默认粒度</span>
					<input readOnly value="stat_month + dept_name + project_type" />
				</label>
				<label>
					<span>来源模型</span>
					<input readOnly value="dwd_project_detail" />
				</label>
			</div>
			<div className="builder-canvas">
				{[
					["来源模型", "dwd_project_detail"],
					["统计粒度", "stat_month + dept_name + project_type"],
					["公式类型", active.format === "percent" ? "ratio" : "aggregation"],
					["治理动作", "term + standard + asset permission"],
				].map(([label, value]) => (
					<div className="builder-node" key={label}>
						<span>{label}</span>
						<strong>{value}</strong>
					</div>
				))}
			</div>
			<pre className="code-preview">{active.dsl}</pre>
		</section>
	);
}

function ModelGenerationPage({ workspace, outputs, busy, submitStaticManifest }: RoutePanelProps) {
	const [activeModelName, setActiveModelName] = useState(workspace.modelCandidates[0]?.name ?? "");
	const activeModel = workspace.modelCandidates.find((model) => model.name === activeModelName) ?? workspace.modelCandidates[0];
	const summaryDsl = buildSummaryTableDsl(activeModel);

	return (
		<>
			<div className="section-head">
				<div>
					<h3>DWS/ADS 生成</h3>
					<p>DWS 以复用为目标，ADS 以具体页面消费为目标。这里补齐 TDS 式汇总表编排，生成物必须经过平台 dbt 门禁和治理审核。</p>
				</div>
				<button
					className="button primary"
					disabled={busy.modelGeneration}
					type="button"
					onClick={() => void submitStaticManifest("modelGeneration", "/api/metrics/packs/preview-artifacts", summaryDsl)}
				>
					预览汇总表
				</button>
			</div>
			<section className="section summary-composer">
				<div className="section-title-row">
					<h4>汇总表编排</h4>
					<span className="chip active">same grain</span>
				</div>
				<div className="summary-layout">
					<div className="summary-column-board">
						{summaryColumns.map(([name, role, label], index) => (
							<div className={`summary-column ${role === "指标" ? "metric" : "dimension"}`} key={name}>
								<span>{String(index + 1).padStart(2, "0")}</span>
								<strong>{name}</strong>
								<em>
									{role} / {label}
								</em>
							</div>
						))}
					</div>
					<div className="summary-rules">
						<div>
							<span>粒度校验</span>
							<strong>{activeModel?.grain ?? "stat_month + dept_name + project_type"}</strong>
							<em>衍生和复合指标必须共享同一统计维度</em>
						</div>
						<div>
							<span>物化策略</span>
							<strong>{activeModel?.materialization ?? "incremental table"}</strong>
							<em>发布后交给 platform/dbt gate 固化</em>
						</div>
						<div>
							<span>刷新周期</span>
							<strong>{activeModel?.refresh ?? defaultTimePeriod.refresh}</strong>
							<em>可挂到平台工作流和运行监控</em>
						</div>
					</div>
				</div>
			</section>
			<div className="generator-layout">
				<section className="section">
					<h4>候选模型</h4>
					<div className="model-selector">
						{workspace.modelCandidates.map((model) => (
							<button
								className={`model-selector-item ${activeModel?.name === model.name ? "active" : ""}`}
								key={model.name}
								type="button"
								onClick={() => setActiveModelName(model.name)}
							>
								<span>{model.layer}</span>
								<strong>{model.name}</strong>
								<em>{model.grain}</em>
							</button>
						))}
					</div>
				</section>
				{activeModel ? <ModelCandidateCard model={activeModel} /> : <EmptyState title="暂无候选模型" description="导入指标包并生成候选物后会显示在这里。" />}
			</div>
			<JsonOutput value={outputs.modelGeneration || summaryDsl} />
		</>
	);
}

function ModelCandidateCard({ model }: { model: ModelCandidate }) {
	return (
		<section className="section model-card">
			<div className="section-title-row">
				<h4>{model.name}</h4>
				<span className="chip active">{model.layer}</span>
			</div>
			<p>{model.purpose}</p>
			<dl className="meta-list">
				<div>
					<dt>粒度</dt>
					<dd>{model.grain}</dd>
				</div>
				<div>
					<dt>物化</dt>
					<dd>{model.materialization}</dd>
				</div>
				<div>
					<dt>刷新</dt>
					<dd>{model.refresh}</dd>
				</div>
			</dl>
			<div className="field-tags">
				{model.fields.map((field) => (
					<span key={field}>{field}</span>
				))}
			</div>
			<pre className="code-preview">{model.sql}</pre>
		</section>
	);
}

function PublishPage({ workspace, outputs, busy, submitStaticManifest }: RoutePanelProps) {
	const readyGates = workspace.publishGates.filter(([, status]) => status === "PASS").length;
	const allGates = workspace.publishGates.length || 1;

	return (
		<>
			<div className="section-head">
				<div>
					<h3>审核发布与血缘</h3>
					<p>发布页把 metrics 生成物推回 platform 的统一发布治理链，不在 metrics 本地绕开审批、审计或资产授权。</p>
				</div>
				<button
					className="button primary"
					disabled={busy.publishDryRun}
					type="button"
					onClick={() => void submitStaticManifest("publishDryRun", "/api/metrics/packs/publish-dry-run")}
				>
					发布预检
				</button>
			</div>
			<div className="release-summary">
				<div>
					<span>门禁通过</span>
					<strong>
						{readyGates}/{allGates}
					</strong>
				</div>
				<div>
					<span>发布方式</span>
					<strong>dry-run</strong>
				</div>
				<div>
					<span>事实源</span>
					<strong>platform/dbt gate</strong>
				</div>
				<div>
					<span>血缘身份</span>
					<strong>asset identity</strong>
				</div>
			</div>
			<section className="section release-path">
				<div className="section-title-row">
					<h4>发布链路</h4>
					<span className="chip">workflow + marketplace</span>
				</div>
				<div className="release-path-grid">
					{publishChannels.map(([title, status, desc]) => (
						<div className="release-path-item" key={title}>
							<StatusPill value={status} />
							<strong>{title}</strong>
							<span>{desc}</span>
						</div>
					))}
				</div>
			</section>
			<div className="page-grid two-columns">
				<section className="section">
					<h4>发布检查</h4>
					<div className="timeline">
						{workspace.publishGates.map(([name, status, desc]) => (
							<div className="timeline-item" key={name}>
								<StatusPill value={status} />
								<strong>{name}</strong>
								<span>{desc}</span>
							</div>
						))}
					</div>
				</section>
				<section className="section">
					<h4>血缘预览</h4>
					<div className="lineage-stack">
						{["dwd_project_detail", "dws_project_month_summary", "ads_project_dashboard_overview", "BI Dataset / 项目驾驶舱"].map((node, index) => (
							<div className="lineage-item" key={node}>
								<div>{node}</div>
								{index < 3 ? <span>↓</span> : null}
							</div>
						))}
					</div>
					<p>血缘注册以 platform asset identity 为准，metric code 只作为可读业务标识。</p>
				</section>
			</div>
			<JsonOutput value={outputs.publishDryRun || "等待发布预检"} />
		</>
	);
}

function RunMonitorPage({ embedded, workspace, outputs, busy, loadCapabilitiesInto }: RoutePanelProps) {
	const successCount = workspace.runRecords.filter((record) => record[2] === "SUCCESS").length;
	const warningCount = workspace.runRecords.filter((record) => record[2] === "WARNING").length;
	const pendingCount = workspace.runRecords.filter((record) => record[2] === "PENDING").length;

	return (
		<>
			<div className="section-head">
				<div>
					<h3>模型运行监控</h3>
					<p>运行页关注新鲜度、耗时、失败原因和平台观测回传，可刷新 dts-metrics 服务观测状态。</p>
				</div>
				<div className="toolbar">
					<button className="button" disabled={busy.runStatus} type="button" onClick={() => void loadCapabilitiesInto("runStatus")}>
						刷新服务观测
					</button>
					<a className="button" href={routeHref("/metrics/semantic/publish", embedded)}>
						查看发布门禁
					</a>
				</div>
			</div>
			<div className="run-board">
				{[
					["今日成功", String(successCount)],
					["等待治理", String(pendingCount)],
					["平均耗时", "42s"],
					["SLA 风险", String(warningCount)],
				].map(([label, value]) => (
					<div key={label}>
						<span>{label}</span>
						<strong>{value}</strong>
					</div>
				))}
			</div>
			<div className="table-wrap">
				<KeyTable headers={["模型", "层级", "状态", "最近运行", "耗时", "说明"]} rows={workspace.runRecords} />
			</div>
			<JsonOutput value={outputs.runStatus || "等待刷新运行观测"} />
		</>
	);
}

function MigrationPage({ outputs, busy, loadMigrationDryRun }: RoutePanelProps) {
	return (
		<section className="section">
			<h3>迁移 dry-run</h3>
			<p>当前版本只提供映射报告和回滚边界，不自动迁移生产数据。旧语义接口保留兼容窗口，最终切换必须经过平台权限和 dbt 发布门禁。</p>
			<div className="toolbar">
				<button className="button primary" disabled={busy.migration} type="button" onClick={() => void loadMigrationDryRun()}>
					读取 dry-run 报告
				</button>
			</div>
			<JsonOutput value={outputs.migration || "等待读取"} />
		</section>
	);
}

function EmptyState({ title, description }: { title: string; description: string }) {
	return (
		<div className="empty-state">
			<strong>{title}</strong>
			<span>{description}</span>
		</div>
	);
}

function KeyTable({ headers, rows }: { headers: string[]; rows: Array<Array<string | number>> }) {
	return (
		<table className="data-table">
			<thead>
				<tr>
					{headers.map((header) => (
						<th key={header}>{header}</th>
					))}
				</tr>
			</thead>
			<tbody>
				{rows.map((row, index) => (
					<tr key={`${row.join("-")}-${index}`}>
						{row.map((cell, cellIndex) => {
							const value = String(cell ?? "");
							return <td key={`${value}-${cellIndex}`}>{looksLikeStatus(value) ? <StatusPill value={value} /> : value}</td>;
						})}
					</tr>
				))}
			</tbody>
		</table>
	);
}

function JsonOutput({ value }: { value: string }) {
	return <pre>{value}</pre>;
}

function StatusPill({ value }: { value: string }) {
	let tone: StatusTone = "neutral";
	if (statusOk.has(value)) tone = "ok";
	if (statusWarn.has(value)) tone = "warn";
	return <span className={`status-pill ${tone}`}>{value}</span>;
}

function looksLikeStatus(value: string): boolean {
	return statusOk.has(value) || statusWarn.has(value) || value === "DRAFT";
}
