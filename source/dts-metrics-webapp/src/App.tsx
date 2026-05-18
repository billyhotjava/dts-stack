import { useCallback, useEffect, useMemo, useState } from "react";
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
			grain: "stat_month + dept_id + project_type",
			materialization: "incremental table",
			refresh: "daily 02:30",
			fields: ["stat_month", "dept_id", "project_type", "project_cnt", "overdue_project_cnt", "direct_cost_execution_rate"],
			sql:
				"select\n  stat_month,\n  dept_id,\n  project_type,\n  count(distinct project_id) as project_cnt\nfrom {{ ref('dwd_project_detail') }}\ngroup by stat_month, dept_id, project_type",
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
		(outputKey: string, url: string) =>
			withBusy(outputKey, async () => {
				setOutput(outputKey, "提交中...");
				try {
					const result = await postYaml<Record<string, unknown>>(url, sampleManifest);
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
			["启用状态", capabilities?.enabled === false ? "未启用" : "已启用"],
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
	submitStaticManifest: (outputKey: string, url: string) => Promise<void>;
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
	return (
		<>
			<div className="section-head">
				<div>
					<h3>运行与平台契约</h3>
					<p>dts-metrics 独立承接语义建模和候选生成物，平台继续负责身份、权限、资产和发布事实。</p>
				</div>
				<a className="button primary" href={routeHref("/metrics/packs", embedded)}>
					提交指标包
				</a>
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
			<div className="toolbar filters">
				<span className="chip active">全部 {assets.length}</span>
				<span className="chip">已发布 {assets.filter((item) => item.status === "PUBLISHED").length}</span>
				<span className="chip">审核中 {assets.filter((item) => item.status === "REVIEW").length}</span>
				<span className="chip">草稿 {assets.filter((item) => item.status === "DRAFT").length}</span>
			</div>
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
						{assets.map((item) => (
							<MetricAssetRow item={item} key={item.code} />
						))}
					</tbody>
				</table>
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
					<p>页面按真实建模顺序组织，避免业务人员直接面对 SQL，也避免绕开 platform 的治理和权限。</p>
				</div>
				<a className="button" href={routeHref("/metrics/semantic/metrics", embedded)}>
					进入公式配置
				</a>
			</div>
			<div className="workflow">
				{[
					["主题域", "选择 platform 中已治理的业务域，绑定数据标准和术语。"],
					["业务对象", "声明主对象、来源明细模型、Join 路径、统计粒度。"],
					["指标公式", "配置聚合、条件聚合、比率、预警和展示格式。"],
					["DWS/ADS", "生成公共汇总模型和应用数据集候选物。"],
					["审核发布", "提交 dbt 门禁，注册资产、血缘和 BI Dataset。"],
				].map(([title, text], index) => (
					<div className="workflow-step" key={title}>
						<span>{String(index + 1).padStart(2, "0")}</span>
						<strong>{title}</strong>
						<p>{text}</p>
					</div>
				))}
			</div>
			<section className="section">
				<h3>语义建模流程</h3>
				<p>输入来自 platform 数据资产、数据标准、业务术语和权限上下文；输出 dbt SQL、schema.yml、BI Dataset 建议和发布门禁材料。</p>
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
	const active = workspace.formulaBlocks[1] ?? workspace.formulaBlocks[0];
	return (
		<>
			<div className="section-head">
				<div>
					<h3>指标公式配置</h3>
					<p>页面展示业务表达、DSL 表达和治理约束，并可调用生成物预览接口；发布仍必须经过 platform 门禁。</p>
				</div>
				<div className="toolbar">
					<button
						className="button primary"
						disabled={busy.formulaPreview}
						type="button"
						onClick={() => void submitStaticManifest("formulaPreview", "/api/metrics/packs/preview-artifacts")}
					>
						预览当前公式生成物
					</button>
					<a className="button" href={routeHref("/metrics/packs", embedded)}>
						从指标包导入
					</a>
				</div>
			</div>
			<div className="split-layout">
				<section className="section">
					<h4>指标清单</h4>
					<KeyTable
						headers={["指标", "指标编码", "单位", "预警"]}
						rows={workspace.formulaBlocks.map((item) => [item.name, item.code, item.unit, item.warning])}
					/>
				</section>
				{active ? <FormulaEditor active={active} /> : null}
			</div>
			<JsonOutput value={outputs.formulaPreview || "等待公式预览"} />
		</>
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
					<input readOnly value="stat_month + dept_id + project_type" />
				</label>
				<label>
					<span>来源模型</span>
					<input readOnly value="dwd_project_detail" />
				</label>
			</div>
			<pre className="code-preview">{active.dsl}</pre>
		</section>
	);
}

function ModelGenerationPage({ workspace, outputs, busy, submitStaticManifest }: RoutePanelProps) {
	return (
		<>
			<div className="section-head">
				<div>
					<h3>DWS/ADS 生成</h3>
					<p>DWS 以复用为目标，ADS 以具体页面消费为目标。生成物只是候选，必须经过平台 dbt 门禁和治理审核。</p>
				</div>
				<button
					className="button primary"
					disabled={busy.modelGeneration}
					type="button"
					onClick={() => void submitStaticManifest("modelGeneration", "/api/metrics/packs/preview-artifacts")}
				>
					生成候选物
				</button>
			</div>
			<div className="page-grid two-columns">
				{workspace.modelCandidates.map((model) => (
					<ModelCandidateCard model={model} key={model.name} />
				))}
			</div>
			<JsonOutput value={outputs.modelGeneration || "等待生成 DWS/ADS 候选物"} />
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
					["今日成功", "2"],
					["等待治理", "1"],
					["平均耗时", "42s"],
					["SLA 风险", "1"],
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
