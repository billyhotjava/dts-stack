const routes = [
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
				title: "指标可视化配置",
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
const isEmbedded = new URLSearchParams(window.location.search).get("embedded") === "1";
const currentPath = normalizePath(window.location.pathname);
const activeRoute = routeItems.find((item) => item.path === currentPath) || routeItems[0];
const serviceSnapshot = {
	health: null,
	capabilities: null,
};

document.documentElement.classList.toggle("embedded", isEmbedded);
document.body.classList.toggle("embedded", isEmbedded);

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

const routeBlueprints = {
	"/metrics/center": {
		title: "运行与契约概览",
		intro: "指标与语义中心作为独立 dts-metrics 服务运行，平台继续负责登录态、菜单、资产目录、权限、数据标准和审计。",
		items: [
			["平台 API", "读取 /api/internal/capabilities、资产 contract、术语、主题域、数据标准和 asset permission。"],
			["服务边界", "dts-metrics 只生成指标包、语义模型和 DWS/ADS 候选物，不拥有 IAM 和资产授权事实。"],
			["当前交付", "已具备指标包校验、生成预览、迁移 dry-run 和平台契约探活入口。"],
		],
	},
	"/metrics/semantic": {
		title: "语义建模流程",
		intro: "按主题域、业务对象、维度、指标、DWS/ADS、发布审核的顺序组织开发，避免直接从页面拼 SQL。",
		items: [
			["输入", "平台资产目录中的 DWD/DBT 模型、数据标准、业务术语和权限上下文。"],
			["处理", "dts-metrics 校验指标 DSL、统计粒度、来源模型、RLS 约束和 glossary 绑定。"],
			["输出", "dbt SQL、schema.yml、BI Dataset 建议、字段说明和发布门禁材料。"],
		],
	},
	"/metrics/semantic/subjects": {
		title: "主题域映射",
		intro: "主题域来源以 dts-platform 数据资产治理为准，metrics 侧只做引用和映射校验。",
		items: [
			["事实源", "通过 /api/internal/domains/resolve 校验主题域是否存在且可用。"],
			["指标包", "manifest.dependencies.platform_domains 必须显式声明所引用主题域。"],
			["页面闭环", "当前页面展示主题域列表、引用指标数、缺口提示，并可读取 platform capability。"],
		],
	},
	"/metrics/semantic/objects": {
		title: "业务对象 Join",
		intro: "业务对象 Join 只生成候选链路，发布前必须回到平台资产 contract 和权限校验。",
		items: [
			["来源模型", "source_model 必须在 dependencies.platform_assets 中声明为 DATASET、DBT_MODEL 或 SEMANTIC_MODEL。"],
			["Join 约束", "Join 设计必须声明 join_type、grain assertion、SCD 策略和一致性维度引用。"],
			["安全", "预览阶段使用 forward-auth 用户身份检查资产 READ 权限。"],
		],
	},
	"/metrics/semantic/metrics": {
		title: "指标可视化配置",
		intro: "当前以指标包 DSL 承接合作方交付；可视化配置页基于同一 manifest contract 发起候选生成物预览。",
		items: [
			["公式", "支持 aggregation、conditional_count、conditional_sum、ratio、case_when、date_trunc 等基础 DSL。"],
			["术语", "每个指标必须绑定 glossary term，且 term 必须在 dependencies.platform_assets 中显式声明。"],
			["权限", "指标预览必须携带 X-DTS-User，由 platform asset grant 决定可见资产。"],
		],
	},
	"/metrics/semantic/models": {
		title: "DWS/ADS 数据集",
		intro: "DWS 面向公共汇总复用，ADS 面向具体 BI/大屏消费，二者都由发布门禁统一进入 platform 资产体系。",
		items: [
			["DWS", "沉淀可复用统计粒度、维度组合和指标汇总。"],
			["ADS", "服务具体看板、API 或 BI Dataset，尽量减少消费侧二次 Join。"],
			["发布", "生成物仍需经过 dbt release gate、治理缺口检查和权限审计。"],
		],
	},
	"/metrics/semantic/publish": {
		title: "审核发布与血缘",
		intro: "发布链路由 platform 承担审计、dbt 门禁、资产身份、权限和血缘注册，metrics 不绕开平台上线。",
		items: [
			["审核", "指标公式、来源资产、术语绑定、数据标准和 RLS 约束必须在发布前可审查。"],
			["门禁", "通过 /api/etl/dbt/release/submit 提交平台发布流程。"],
			["血缘", "发布后注册 DATASET/DBT_MODEL/METRIC/BI_DATASET 的资产关系。"],
		],
	},
	"/metrics/semantic/runs": {
		title: "模型运行监控",
		intro: "运行状态以 platform 观测与审计为最终事实源，避免 metrics 服务形成新的运行孤岛。",
		items: [
			["运行事件", "metric_run_event、dbt run 结果、preview 失败原因进入平台审计链。"],
			["SLA", "结合 freshness、lastObservedAt 和 maxStalenessMinutes 展示指标新鲜度。"],
			["告警", "异常检测和订阅推送由运行与告警页统一承接。"],
		],
	},
	};

const platformContracts = [
	["登录态", "platform-forward-auth", "已接入"],
	["资产权限", "/api/internal/asset-permission/check", "已接入"],
	["主题域", "/api/internal/domains/resolve", "已接入"],
	["数据标准", "/api/internal/data-standards/resolve", "已接入"],
	["业务术语", "/api/internal/glossary/terms/resolve", "已接入"],
	["发布门禁", "/api/etl/dbt/release/submit", "待联调"],
];

const metricAssets = [
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
	{
		code: "supplier_on_time_rate",
		name: "供应商准时交付率",
		domain: "采购管理",
		type: "ratio",
		grain: "月份 / 供应商",
		status: "PUBLISHED",
		version: "1.1.0",
		owner: "采购管理部",
		terms: ["glossary.supplier_delivery"],
		consumer: "采购绩效看板",
	},
];

const subjectMappings = [
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
	{
		domain: "库存管理",
		code: "inventory",
		platformState: "PENDING_GOVERNANCE",
		assets: 4,
		metrics: 6,
		standards: "material_code, warehouse_code",
		gap: "安全库存标准未绑定",
	},
];

const objectJoins = [
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
];

const formulaBlocks = [
	{
		code: "project_cnt",
		name: "项目总数",
		display: "count_distinct(project_id)",
		unit: "个",
		format: "integer",
		warning: "none",
		dsl: `formula:
  type: aggregation
  aggregation: count_distinct
  field: project_id`,
	},
	{
		code: "direct_cost_execution_rate",
		name: "直接成本执行率",
		display: "sum(direct_cost_amount) / sum(direct_cost_control_amount) * 100",
		unit: "%",
		format: "percent",
		warning: ">= 90 标红",
		dsl: `formula:
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
  zero_division: null`,
	},
	{
		code: "overdue_project_cnt",
		name: "延期项目数",
		display: "count_if(current_date > plan_end_date and status != completed)",
		unit: "个",
		format: "integer",
		warning: "> 0 标黄",
		dsl: `formula:
  type: conditional_count
  field: project_id
  distinct: true
  condition:
    operator: and
    conditions:
      - field: current_date
        operator: ">"
        value_field: plan_end_date
      - field: project_status
        operator: "!="
        value: 已完成`,
	},
];

const modelCandidates = [
	{
		layer: "DWS",
		name: "dws_project_month_summary",
		purpose: "项目月度公共汇总模型，可复用于驾驶舱、科室看板和风险分析。",
		grain: "stat_month + dept_id + project_type",
		materialization: "incremental table",
		refresh: "daily 02:30",
		fields: ["stat_month", "dept_id", "project_type", "project_cnt", "overdue_project_cnt", "direct_cost_execution_rate"],
		sql: `select
  stat_month,
  dept_id,
  project_type,
  count(distinct project_id) as project_cnt,
  count(distinct case when is_overdue = true then project_id end) as overdue_project_cnt,
  case
    when sum(coalesce(direct_cost_control_amount, 0)) = 0 then null
    else sum(coalesce(direct_cost_amount, 0)) / sum(coalesce(direct_cost_control_amount, 0))
  end as direct_cost_execution_rate
from {{ ref('dwd_project_detail') }}
group by stat_month, dept_id, project_type`,
	},
	{
		layer: "ADS",
		name: "ads_project_dashboard_overview",
		purpose: "直接服务项目管理综合驾驶舱，减少 BI 工具二次 Join。",
		grain: "stat_month + dashboard_scope",
		materialization: "table",
		refresh: "daily 03:00",
		fields: ["project_cnt", "active_project_cnt", "overdue_project_cnt", "cost_warning_level", "top_dept_name"],
		sql: `select
  s.stat_month,
  sum(s.project_cnt) as project_cnt,
  sum(s.overdue_project_cnt) as overdue_project_cnt,
  max_by(s.dept_name, s.project_cnt) as top_dept_name,
  case when max(s.direct_cost_execution_rate) >= 0.9 then 'HIGH' else 'NORMAL' end as cost_warning_level
from {{ ref('dws_project_month_summary') }} s
group by s.stat_month`,
	},
];

const publishGates = [
	["结构校验", "PASS", "指标包 schema、依赖声明和文件引用通过"],
	["平台权限", "PASS", "当前用户具备来源资产 READ 权限"],
	["术语绑定", "PASS", "指标绑定的 glossary term 已在 platform 激活"],
	["数据标准", "PASS", "维度字段绑定 stat_month、dept_name 标准"],
	["RLS 注入", "PASS", "生成 SQL 强制承接 platform 用户策略"],
	["dbt 门禁", "PENDING", "等待提交 /api/etl/dbt/release/submit"],
];

const runRecords = [
	["dws_project_month_summary", "DWS", "SUCCESS", "2026-05-17 02:32", "48s", "fresh"],
	["ads_project_dashboard_overview", "ADS", "SUCCESS", "2026-05-17 03:04", "23s", "fresh"],
	["dws_supplier_month_summary", "DWS", "WARNING", "2026-05-17 02:41", "55s", "late source rows"],
	["ads_inventory_risk_board", "ADS", "PENDING", "-", "-", "waiting for governance"],
];

function normalizePath(pathname) {
	const value = String(pathname || "/metrics/center").replace(/\/+$/, "");
	if (!value || value === "/metrics") return "/metrics/center";
	return value;
}

function setText(id, value) {
	const node = document.getElementById(id);
	if (node) node.textContent = value;
}

function setMessage(node, text, tone) {
	if (!node) return;
	node.textContent = text;
	node.classList.remove("ok", "warn");
	if (tone) node.classList.add(tone);
}

function routeHref(path) {
	if (!isEmbedded) return path;
	const url = new URL(path, window.location.origin);
	url.searchParams.set("embedded", "1");
	return `${url.pathname}${url.search}${url.hash}`;
}

function preserveEmbeddedLinks() {
	if (!isEmbedded) return;
	document.querySelectorAll("a[href^='/metrics']").forEach((anchor) => {
		anchor.setAttribute("href", routeHref(anchor.getAttribute("href") || ""));
	});
}

function renderNavigation() {
	const nav = document.getElementById("route-nav");
	if (!nav) return;
	nav.innerHTML = routes
		.map(
			(group) => `
				<div class="nav-group">
					<p class="nav-group-title">${escapeHtml(group.group)}</p>
					${group.items
						.map(
							(item) =>
								`<a class="nav-link ${item.path === activeRoute.path ? "active" : ""}" href="${routeHref(item.path)}">${escapeHtml(
									item.title,
								)}</a>`,
						)
						.join("")}
			</div>
		`,
		)
		.join("");
}

function renderRouteHeader() {
	setText("route-title", activeRoute.title);
	setText("route-stage", activeRoute.stage);
	setText("route-description", activeRoute.description);
}

function renderSummaryCards(capabilities) {
	const cards = [
		["服务归属", capabilities?.service || "dts-metrics"],
		["启用状态", capabilities?.enabled === false ? "未启用" : "已启用"],
		["当前版本", capabilities?.edition || "foundation"],
		["权限事实源", "dts-platform"],
	];
	const container = document.getElementById("summary-cards");
	if (!container) return;
	container.innerHTML = cards
		.map(
			([label, value]) => `
			<article class="card">
				<p class="card-label">${escapeHtml(label)}</p>
				<p class="card-value">${escapeHtml(value)}</p>
			</article>
		`,
		)
		.join("");
}

function renderPanel() {
	const panel = document.getElementById("route-panel");
	if (!panel) return;
	if (activeRoute.path === "/metrics/center") {
		panel.innerHTML = renderCenterPage();
		return;
	}

	if (activeRoute.path === "/metrics/dictionary") {
		panel.innerHTML = renderMetricAssetsPage();
		bindButton("refresh-dictionary-contract", () => loadCapabilitiesInto("dictionary-contract-output"));
		return;
	}

	if (activeRoute.path === "/metrics/packs") {
		panel.innerHTML = renderManifestPanel();
		const input = document.getElementById("manifest-input");
		if (input) input.value = sampleManifest;
		const button = document.getElementById("validate-manifest");
		if (button) button.addEventListener("click", validateManifest);
		const previewButton = document.getElementById("preview-artifacts");
		if (previewButton) previewButton.addEventListener("click", previewArtifacts);
		const importButton = document.getElementById("dry-run-import");
		if (importButton) importButton.addEventListener("click", dryRunImport);
		return;
	}

	if (activeRoute.path === "/metrics/semantic") {
		panel.innerHTML = renderSemanticFlowPage();
		return;
	}

	if (activeRoute.path === "/metrics/semantic/subjects") {
		panel.innerHTML = renderSubjectMappingPage();
		bindButton("refresh-subject-contract", () => loadCapabilitiesInto("subject-contract-output"));
		return;
	}

	if (activeRoute.path === "/metrics/semantic/objects") {
		panel.innerHTML = renderBusinessObjectJoinPage();
		bindButton("preview-object-manifest", () => submitStaticManifest("/api/metrics/packs/preview-artifacts", "object-preview-output"));
		return;
	}

	if (activeRoute.path === "/metrics/semantic/metrics") {
		panel.innerHTML = renderFormulaConfigPage();
		bindButton("preview-formula-artifacts", () => submitStaticManifest("/api/metrics/packs/preview-artifacts", "formula-preview-output"));
		return;
	}

	if (activeRoute.path === "/metrics/semantic/models") {
		panel.innerHTML = renderModelGenerationPage();
		bindButton("generate-model-candidates", () => submitStaticManifest("/api/metrics/packs/preview-artifacts", "model-generation-output"));
		return;
	}

	if (activeRoute.path === "/metrics/semantic/publish") {
		panel.innerHTML = renderPublishPage();
		bindButton("dry-run-publish", () => submitStaticManifest("/api/metrics/packs/import", "publish-output"));
		return;
	}

	if (activeRoute.path === "/metrics/semantic/runs") {
		panel.innerHTML = renderRunMonitorPage();
		bindButton("refresh-run-status", () => loadCapabilitiesInto("run-status-output"));
		return;
	}

	if (activeRoute.path === "/metrics/operations") {
		panel.innerHTML = renderRunMonitorPage();
		bindButton("refresh-run-status", () => loadCapabilitiesInto("run-status-output"));
		return;
	}

	if (activeRoute.path === "/metrics/migration") {
		panel.innerHTML = `
			<section class="section">
				<h3>迁移 dry-run</h3>
				<p>当前版本只提供映射报告和回滚边界，不自动迁移生产数据。旧语义接口保留兼容窗口，最终切换必须经过平台权限和 dbt 发布门禁。</p>
				<div class="toolbar">
					<button class="button primary" type="button" id="load-migration">读取 dry-run 报告</button>
				</div>
				<pre id="migration-output">等待读取</pre>
			</section>
		`;
		const button = document.getElementById("load-migration");
		if (button) button.addEventListener("click", loadMigrationDryRun);
		return;
	}

	panel.innerHTML = renderBlueprintPanel(routeBlueprints[activeRoute.path] || routeBlueprints["/metrics/center"]);
}

function renderCenterPage() {
	return `
		<div class="section-head">
			<div>
				<h3>运行与平台契约</h3>
				<p>这里展示 dts-metrics 和 dts-platform 的当前分工：平台负责身份、权限、资产和发布事实；metrics 负责语义建模和候选生成物。</p>
			</div>
			<a class="button primary" href="${routeHref("/metrics/packs")}">提交指标包</a>
		</div>
		<div class="page-grid two-columns">
			<section class="section">
				<h4>平台契约状态</h4>
				${renderKeyTable(["能力", "接口/事实源", "状态"], platformContracts)}
				<div id="live-contract-panel">${renderLiveContractPanel(serviceSnapshot.capabilities)}</div>
			</section>
			<section class="section">
				<h4>交付漏斗</h4>
				<div class="funnel">
					${[
						["1", "指标包校验", "结构、术语、主题域、数据标准"],
						["2", "候选生成物", "DWS/ADS SQL、schema.yml、BI Dataset"],
						["3", "平台门禁", "权限、dbt 发布、审计、血缘注册"],
						["4", "消费发布", "BI 图表、大屏、API 查询"],
					]
						.map(
							([step, title, text]) => `
								<div class="funnel-row">
									<span>${escapeHtml(step)}</span>
									<strong>${escapeHtml(title)}</strong>
									<em>${escapeHtml(text)}</em>
								</div>
							`,
						)
						.join("")}
				</div>
			</section>
		</div>
	`;
}

function renderLiveContractPanel(capabilities) {
	const contract = capabilities?.platformContract || {};
	const rows = [
		["metrics 服务", capabilities?.service || "等待响应"],
		["platform 地址", contract.platformBaseUrl || "等待响应"],
		["platform API 前缀", contract.apiPath || "等待响应"],
		["服务 Token", contract.serviceTokenConfigured === true ? "已配置" : contract.serviceTokenConfigured === false ? "未配置" : "等待响应"],
		["认证头", contract.authHeaders ? `${contract.authHeaders.service} + ${contract.authHeaders.token}` : "等待响应"],
	];
	const mvp = Array.isArray(capabilities?.mvp) ? capabilities.mvp : [];
	return `
		<div class="live-contract">
			<h5>实时能力响应</h5>
			${renderKeyTable(["字段", "值"], rows)}
			${mvp.length ? `<div class="field-tags">${mvp.map((item) => `<span>${escapeHtml(item)}</span>`).join("")}</div>` : ""}
		</div>
	`;
}

function renderSemanticFlowPage() {
	return `
		<div class="section-head">
			<div>
				<h3>从指标口径到数据应用</h3>
				<p>页面按真实建模顺序组织，避免业务人员直接面对 SQL，也避免绕开 platform 的治理和权限。</p>
			</div>
			<a class="button" href="${routeHref("/metrics/semantic/metrics")}">进入公式配置</a>
		</div>
		<div class="workflow">
			${[
				["主题域", "选择 platform 中已治理的业务域，绑定数据标准和术语。"],
				["业务对象", "声明主对象、来源明细模型、Join 路径、统计粒度。"],
				["指标公式", "配置聚合、条件聚合、比率、预警和展示格式。"],
				["DWS/ADS", "生成公共汇总模型和应用数据集候选物。"],
				["审核发布", "提交 dbt 门禁，注册资产、血缘和 BI Dataset。"],
			]
				.map(
					([title, text], index) => `
						<div class="workflow-step">
							<span>${String(index + 1).padStart(2, "0")}</span>
							<strong>${escapeHtml(title)}</strong>
							<p>${escapeHtml(text)}</p>
						</div>
					`,
				)
				.join("")}
		</div>
		${renderBlueprintPanel(routeBlueprints["/metrics/semantic"])}
	`;
}

function renderMetricAssetsPage() {
	return `
		<div class="section-head">
			<div>
				<h3>指标资产列表</h3>
				<p>指标资产以 code 和 version 为主键，下游大屏/BI 应固定到版本，避免口径变更自动漂移。</p>
			</div>
			<div class="toolbar">
				<button class="button" type="button" id="refresh-dictionary-contract">读取平台契约</button>
				<a class="button primary" href="${routeHref("/metrics/semantic/metrics")}">新建指标</a>
			</div>
		</div>
		<div class="toolbar filters">
			<span class="chip active">全部 ${metricAssets.length}</span>
			<span class="chip">已发布 ${metricAssets.filter((item) => item.status === "PUBLISHED").length}</span>
			<span class="chip">审核中 ${metricAssets.filter((item) => item.status === "REVIEW").length}</span>
			<span class="chip">草稿 ${metricAssets.filter((item) => item.status === "DRAFT").length}</span>
		</div>
		<div class="table-wrap">
			<table class="data-table">
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
					${metricAssets
						.map(
							(item) => `
								<tr>
									<td>
										<strong>${escapeHtml(item.name)}</strong>
										<span>${escapeHtml(item.code)}</span>
									</td>
									<td>${escapeHtml(item.domain)}</td>
									<td>${escapeHtml(item.type)}</td>
									<td>${escapeHtml(item.grain)}</td>
									<td>${escapeHtml(item.version)}</td>
									<td>${renderStatusPill(item.status)}</td>
									<td>${escapeHtml(item.owner)}</td>
									<td>${escapeHtml(item.consumer)}</td>
								</tr>
							`,
						)
						.join("")}
				</tbody>
			</table>
		</div>
		<div class="section muted-section">
			<h4>绑定要求</h4>
			<p>每个指标必须声明 glossary term、source_model、platform asset dependency、统计粒度和默认展示格式。预览和发布阶段都要走 platform 权限与审计链。</p>
		</div>
		<pre id="dictionary-contract-output">等待读取平台契约</pre>
	`;
}

function renderSubjectMappingPage() {
	return `
		<div class="section-head">
			<div>
				<h3>主题域映射</h3>
				<p>主题域不是 metrics 本地事实源，页面只展示引用、缺口和指标包声明状态，真实治理字段仍在 platform 数据资产中维护。</p>
			</div>
			<div class="toolbar">
				<button class="button" type="button" id="refresh-subject-contract">读取 platform capability</button>
				<a class="button" href="${routeHref("/metrics/packs")}">查看 manifest</a>
			</div>
		</div>
		<div class="page-grid three-columns">
			${subjectMappings
				.map(
					(item) => `
						<section class="section">
							<div class="section-title-row">
								<h4>${escapeHtml(item.domain)}</h4>
								${renderStatusPill(item.platformState)}
							</div>
							<dl class="meta-list">
								<div><dt>domain code</dt><dd>${escapeHtml(item.code)}</dd></div>
								<div><dt>平台资产</dt><dd>${escapeHtml(item.assets)}</dd></div>
								<div><dt>指标数</dt><dd>${escapeHtml(item.metrics)}</dd></div>
								<div><dt>数据标准</dt><dd>${escapeHtml(item.standards)}</dd></div>
								<div><dt>治理缺口</dt><dd>${escapeHtml(item.gap)}</dd></div>
							</dl>
						</section>
					`,
				)
				.join("")}
		</div>
		<pre id="subject-contract-output">等待读取主题域契约</pre>
	`;
}

function renderBusinessObjectJoinPage() {
	return `
		<div class="section-head">
			<div>
				<h3>业务对象 Join 设计</h3>
				<p>Join 页面强调统计粒度和 fanout 防护。所有来源模型必须先在 manifest dependencies.platform_assets 中声明。</p>
			</div>
			<div class="toolbar">
				<button class="button" type="button" id="preview-object-manifest">预览 Join 生成物</button>
				<a class="button primary" href="${routeHref("/metrics/semantic/models")}">生成 DWS/ADS</a>
			</div>
		</div>
		<div class="page-grid two-columns">
			${objectJoins.map(renderObjectJoinCard).join("")}
		</div>
		<pre id="object-preview-output">等待预览 Join 候选物</pre>
	`;
}

function renderObjectJoinCard(item) {
	return `
		<section class="section">
			<div class="section-title-row">
				<h4>${escapeHtml(item.object)}</h4>
				<span class="chip">${escapeHtml(item.grain)}</span>
			</div>
			<div class="join-chain">
				<div class="join-node primary-node">
					<strong>${escapeHtml(item.source)}</strong>
					<span>${escapeHtml(item.key)}</span>
				</div>
				${item.joins
					.map(
						([table, key, type, cardinality]) => `
							<div class="join-edge">${escapeHtml(type)} / ${escapeHtml(cardinality)}</div>
							<div class="join-node">
								<strong>${escapeHtml(table)}</strong>
								<span>${escapeHtml(key)}</span>
							</div>
						`,
					)
					.join("")}
			</div>
			<ul class="compact-list">
				${item.guardrails.map((guard) => `<li>${escapeHtml(guard)}</li>`).join("")}
			</ul>
		</section>
	`;
}

function renderFormulaConfigPage() {
	const active = formulaBlocks[1];
	return `
		<div class="section-head">
			<div>
				<h3>指标公式配置</h3>
				<p>页面展示业务表达、DSL 表达和治理约束，并可调用生成物预览接口；发布仍必须经过 platform 门禁。</p>
			</div>
			<div class="toolbar">
				<button class="button primary" type="button" id="preview-formula-artifacts">预览当前公式生成物</button>
				<a class="button" href="${routeHref("/metrics/packs")}">从指标包导入</a>
			</div>
		</div>
		<div class="split-layout">
			<section class="section">
				<h4>指标清单</h4>
				${renderKeyTable(
					["指标", "公式类型", "单位", "预警"],
					formulaBlocks.map((item) => [item.name, item.code, item.unit, item.warning]),
				)}
			</section>
			<section class="section">
				<div class="section-title-row">
					<h4>${escapeHtml(active.name)}</h4>
					<span class="chip active">${escapeHtml(active.format)}</span>
				</div>
				<div class="form-grid">
					<label><span>指标编码</span><input value="${escapeHtml(active.code)}" readonly /></label>
					<label><span>业务表达</span><input value="${escapeHtml(active.display)}" readonly /></label>
					<label><span>默认粒度</span><input value="stat_month + dept_id + project_type" readonly /></label>
					<label><span>来源模型</span><input value="dwd_project_detail" readonly /></label>
				</div>
				<pre class="code-preview">${escapeHtml(active.dsl)}</pre>
			</section>
		</div>
		<pre id="formula-preview-output">等待公式预览</pre>
	`;
}

function renderModelGenerationPage() {
	return `
		<div class="section-head">
			<div>
				<h3>DWS/ADS 生成</h3>
				<p>DWS 以复用为目标，ADS 以具体页面消费为目标。生成物只是候选，必须经过平台 dbt 门禁和治理审核。</p>
			</div>
			<button class="button primary" type="button" id="generate-model-candidates">生成候选物</button>
		</div>
		<div class="page-grid two-columns">
			${modelCandidates
				.map(
					(model) => `
						<section class="section model-card">
							<div class="section-title-row">
								<h4>${escapeHtml(model.name)}</h4>
								<span class="chip active">${escapeHtml(model.layer)}</span>
							</div>
							<p>${escapeHtml(model.purpose)}</p>
							<dl class="meta-list">
								<div><dt>粒度</dt><dd>${escapeHtml(model.grain)}</dd></div>
								<div><dt>物化</dt><dd>${escapeHtml(model.materialization)}</dd></div>
								<div><dt>刷新</dt><dd>${escapeHtml(model.refresh)}</dd></div>
							</dl>
							<div class="field-tags">${model.fields.map((field) => `<span>${escapeHtml(field)}</span>`).join("")}</div>
							<pre class="code-preview">${escapeHtml(model.sql)}</pre>
						</section>
					`,
				)
				.join("")}
		</div>
		<pre id="model-generation-output">等待生成 DWS/ADS 候选物</pre>
	`;
}

function renderPublishPage() {
	return `
		<div class="section-head">
			<div>
				<h3>审核发布与血缘</h3>
				<p>发布页把 metrics 生成物推回 platform 的统一发布治理链，不在 metrics 本地绕开审批、审计或资产授权。</p>
			</div>
			<button class="button primary" type="button" id="dry-run-publish">发布预检</button>
		</div>
		<div class="page-grid two-columns">
			<section class="section">
				<h4>发布检查</h4>
				<div class="timeline">
					${publishGates
						.map(
							([name, status, desc]) => `
								<div class="timeline-item">
									${renderStatusPill(status)}
									<strong>${escapeHtml(name)}</strong>
									<span>${escapeHtml(desc)}</span>
								</div>
							`,
						)
						.join("")}
				</div>
			</section>
			<section class="section">
				<h4>血缘预览</h4>
				<div class="lineage-stack">
					${["dwd_project_detail", "dws_project_month_summary", "ads_project_dashboard_overview", "BI Dataset / 项目驾驶舱"]
						.map((node) => `<div>${escapeHtml(node)}</div>`)
						.join("<span>↓</span>")}
				</div>
				<p>血缘注册以 platform asset identity 为准，metric code 只作为可读业务标识。</p>
			</section>
		</div>
		<pre id="publish-output">等待发布预检</pre>
	`;
}

function renderRunMonitorPage() {
	return `
		<div class="section-head">
			<div>
				<h3>模型运行监控</h3>
				<p>运行页关注新鲜度、耗时、失败原因和平台观测回传，可刷新 dts-metrics 服务观测状态。</p>
			</div>
			<div class="toolbar">
				<button class="button" type="button" id="refresh-run-status">刷新服务观测</button>
				<a class="button" href="${routeHref("/metrics/semantic/publish")}">查看发布门禁</a>
			</div>
		</div>
		<div class="run-board">
			${[
				["今日成功", "2"],
				["等待治理", "1"],
				["平均耗时", "42s"],
				["SLA 风险", "1"],
			]
				.map(([label, value]) => `<div><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`)
				.join("")}
		</div>
		<div class="table-wrap">
			${renderKeyTable(["模型", "层级", "状态", "最近运行", "耗时", "说明"], runRecords)}
		</div>
		<pre id="run-status-output">等待刷新运行观测</pre>
	`;
}

function renderManifestPanel() {
	return `
		<section class="section">
			<h3>指标包校验</h3>
			<p>合作方按指标包契约提交 YAML/JSON，dts-metrics 做结构校验、候选生成和导入预检；发布事实仍回到 platform。</p>
			<div class="manifest-grid">
				<div>
					<textarea id="manifest-input" spellcheck="false" aria-label="指标包内容"></textarea>
					<div class="toolbar">
						<button class="button primary" type="button" id="validate-manifest">校验指标包</button>
						<button class="button" type="button" id="preview-artifacts">预览生成物</button>
						<button class="button" type="button" id="dry-run-import">导入预检</button>
						<a class="button" href="${routeHref("/metrics/semantic/metrics")}">进入指标配置</a>
					</div>
					<div class="message" id="manifest-message">等待校验</div>
				</div>
				<pre id="manifest-output">暂无结果</pre>
			</div>
		</section>
	`;
}

function renderKeyTable(headers, rows) {
	return `
		<table class="data-table">
			<thead>
				<tr>${headers.map((header) => `<th>${escapeHtml(header)}</th>`).join("")}</tr>
			</thead>
			<tbody>
				${rows
					.map(
						(row) => `
							<tr>
								${row
									.map((cell) => {
										const value = String(cell ?? "");
										return `<td>${looksLikeStatus(value) ? renderStatusPill(value) : escapeHtml(value)}</td>`;
									})
									.join("")}
							</tr>
						`,
					)
					.join("")}
			</tbody>
		</table>
	`;
}

function renderStatusPill(status) {
	const value = String(status || "UNKNOWN");
	const tone = ["PASS", "SUCCESS", "ACTIVE", "PUBLISHED", "已接入", "fresh"].includes(value)
		? "ok"
		: ["WARNING", "REVIEW", "PENDING", "PENDING_GOVERNANCE", "待联调", "late source rows", "waiting for governance"].includes(value)
			? "warn"
			: value === "DRAFT"
				? ""
				: "neutral";
	return `<span class="status-pill ${tone}">${escapeHtml(value)}</span>`;
}

function looksLikeStatus(value) {
	return [
		"PASS",
		"SUCCESS",
		"ACTIVE",
		"PUBLISHED",
		"REVIEW",
		"PENDING",
		"PENDING_GOVERNANCE",
		"DRAFT",
		"WARNING",
		"已接入",
		"待联调",
		"fresh",
		"late source rows",
		"waiting for governance",
	].includes(value);
}

function renderBlueprintPanel(blueprint) {
	return `
		<section class="section">
			<h3>${escapeHtml(blueprint.title)}</h3>
			<p>${escapeHtml(blueprint.intro)}</p>
			<div class="steps">
				${blueprint.items
					.map(([title, text]) => `<div class="step"><strong>${escapeHtml(title)}</strong><span>${escapeHtml(text)}</span></div>`)
					.join("")}
			</div>
		</section>
	`;
}

async function loadServiceStatus() {
	const statusNode = document.getElementById("service-status");
	try {
		const [health, capabilities] = await Promise.all([fetchJson("/api/metrics/health"), fetchJson("/api/metrics/capabilities")]);
		serviceSnapshot.health = health;
		serviceSnapshot.capabilities = capabilities;
		if (statusNode) {
			statusNode.textContent = `${health.service || "dts-metrics"} ${health.status || "UP"}`;
			statusNode.classList.remove("warn");
			statusNode.classList.add("ok");
		}
		renderSummaryCards(capabilities);
		refreshLivePanels();
	} catch (error) {
		if (statusNode) {
			statusNode.textContent = "服务状态不可用";
			statusNode.classList.remove("ok");
			statusNode.classList.add("warn");
		}
		serviceSnapshot.health = null;
		serviceSnapshot.capabilities = null;
		renderSummaryCards(null);
		refreshLivePanels();
	}
}

function refreshLivePanels() {
	const liveContractPanel = document.getElementById("live-contract-panel");
	if (liveContractPanel) {
		liveContractPanel.innerHTML = renderLiveContractPanel(serviceSnapshot.capabilities);
	}
}

async function validateManifest() {
	await submitManifest("/api/metrics/packs/validate", "校验中", "校验通过", "校验未通过，请查看结果", "校验请求失败");
}

async function previewArtifacts() {
	await submitManifest("/api/metrics/packs/preview-artifacts", "生成预览中", "候选生成物已生成", "候选生成物未生成，请查看结果", "预览请求失败");
}

async function dryRunImport() {
	await submitManifest("/api/metrics/packs/import", "导入预检中", "导入预检通过", "导入预检未通过，请查看结果", "导入预检失败");
}

async function loadMigrationDryRun() {
	const output = document.getElementById("migration-output");
	const button = document.getElementById("load-migration");
	if (!output) return;
	if (button) button.disabled = true;
	try {
		const result = await fetchJson("/api/metrics/migration/semantic-dry-run");
		output.textContent = JSON.stringify(result, null, 2);
	} catch (error) {
		output.textContent = String(error?.message || error);
	} finally {
		if (button) button.disabled = false;
	}
}

function bindButton(id, handler) {
	const button = document.getElementById(id);
	if (!button) return;
	button.addEventListener("click", () => {
		void handler();
	});
}

async function loadCapabilitiesInto(outputId) {
	const output = document.getElementById(outputId);
	if (!output) return;
	output.textContent = "读取中...";
	try {
		const result = await fetchJson("/api/metrics/capabilities");
		output.textContent = JSON.stringify(result, null, 2);
	} catch (error) {
		output.textContent = String(error?.message || error);
	}
}

async function submitStaticManifest(url, outputId) {
	const output = document.getElementById(outputId);
	if (!output) return;
	output.textContent = "提交中...";
	try {
		const result = await fetchJson(url, {
			method: "POST",
			headers: { "Content-Type": "text/yaml" },
			body: sampleManifest,
		});
		output.textContent = JSON.stringify(result, null, 2);
	} catch (error) {
		output.textContent = String(error?.message || error);
	}
}

async function submitManifest(url, loadingText, successText, invalidText, errorText) {
	const input = document.getElementById("manifest-input");
	const output = document.getElementById("manifest-output");
	const message = document.getElementById("manifest-message");
	if (!input || !output || !message) return;
	const buttons = document.querySelectorAll("#validate-manifest, #preview-artifacts, #dry-run-import");
	setMessage(message, loadingText, "");
	buttons.forEach((button) => {
		button.disabled = true;
	});
	try {
		const result = await fetchJson(url, {
			method: "POST",
			headers: { "Content-Type": "text/yaml" },
			body: input.value,
		});
		output.textContent = JSON.stringify(result, null, 2);
		const ok = result.valid === true || result.accepted === true;
		setMessage(message, ok ? successText : invalidText, ok ? "ok" : "warn");
	} catch (error) {
		output.textContent = String(error?.message || error);
		setMessage(message, errorText, "warn");
	} finally {
		buttons.forEach((button) => {
			button.disabled = false;
		});
	}
}

async function fetchJson(url, options) {
	const response = await fetch(url, { credentials: "same-origin", ...options });
	const text = await response.text();
	if (!response.ok) {
		throw new Error(compactError(text) || `${response.status} ${response.statusText}`);
	}
	if (!text) return {};
	try {
		return JSON.parse(text);
	} catch (error) {
		throw new Error(`响应不是合法 JSON: ${compactError(text) || String(error?.message || error)}`);
	}
}

function compactError(text) {
	return String(text || "")
		.replace(/\s+/g, " ")
		.trim()
		.slice(0, 240);
}

function escapeHtml(value) {
	return String(value ?? "")
		.replace(/&/g, "&amp;")
		.replace(/</g, "&lt;")
		.replace(/>/g, "&gt;")
		.replace(/"/g, "&quot;")
		.replace(/'/g, "&#039;");
}

renderNavigation();
renderRouteHeader();
renderPanel();
preserveEmbeddedLinks();
loadServiceStatus();
