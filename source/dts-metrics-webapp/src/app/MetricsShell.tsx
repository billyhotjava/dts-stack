import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchJson, postYaml } from "../api";
import SemanticDesignerPage from "../pages/semantic/SemanticDesignerPage";
import type {
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
} from "../types";

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
				description: "提交真实指标包，执行结构校验、候选生成和导入预检。",
			},
			{
				path: "/metrics/migration",
				title: "迁移与回滚",
				stage: "Migration",
				description: "读取 platform 旧语义数据到 dts-metrics 的 dry-run 映射、阻断项和回滚边界。",
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
				description: "从 platform 治理事实源读取主题域、术语和数据标准绑定状态。",
			},
			{
				path: "/metrics/semantic/objects",
				title: "业务对象 Join",
				stage: "Business Object",
				description: "查看真实业务对象、来源资产、Join 条件和 fanout 防护约束。",
			},
			{
				path: "/metrics/semantic/metrics",
				title: "指标公式配置",
				stage: "Metric Designer",
				description: "在 React Flow 画布中配置指标、维度、Join、筛选和模型节点。",
			},
			{
				path: "/metrics/semantic/models",
				title: "DWS/ADS 数据集",
				stage: "DWS / ADS",
				description: "查看由指标图生成并等待 platform/dbt 验证的候选 DWS/ADS 数据集。",
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

type WorkspaceState = Required<
	Pick<
		WorkspaceSnapshot,
		"platformContracts" | "metricAssets" | "subjectMappings" | "objectJoins" | "formulaBlocks" | "modelCandidates" | "publishGates" | "runRecords"
	>
>;

const emptyWorkspace: WorkspaceState = {
	platformContracts: [],
	metricAssets: [],
	subjectMappings: [],
	objectJoins: [],
	formulaBlocks: [],
	modelCandidates: [],
	publishGates: [],
	runRecords: [],
};

const statusOk = new Set(["PASS", "SUCCESS", "ACTIVE", "PUBLISHED", "已接入", "fresh", "UP", "CONNECTED"]);
const statusWarn = new Set(["WARNING", "REVIEW", "PENDING", "PENDING_GOVERNANCE", "待联调", "WARN", "ERROR"]);

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

function mergeWorkspace(snapshot?: WorkspaceSnapshot): WorkspaceState {
	return {
		platformContracts: snapshot?.platformContracts ?? [],
		metricAssets: snapshot?.metricAssets ?? [],
		subjectMappings: snapshot?.subjectMappings ?? [],
		objectJoins: snapshot?.objectJoins ?? [],
		formulaBlocks: snapshot?.formulaBlocks ?? [],
		modelCandidates: snapshot?.modelCandidates ?? [],
		publishGates: snapshot?.publishGates ?? [],
		runRecords: snapshot?.runRecords ?? [],
	};
}

function statusLabel(value?: boolean): string {
	if (value === false) return "未启用";
	if (value === true) return "已启用";
	return "待确认";
}

function countByStatus(items: Array<{ status: string }>, status: string): number {
	return items.filter((item) => item.status === status).length;
}

function arrayCount(value?: unknown[]): number {
	return Array.isArray(value) ? value.length : 0;
}

export default function MetricsShell() {
	const embedded = new URLSearchParams(window.location.search).get("embedded") === "1";
	const activePath = normalizePath(window.location.pathname);
	const activeRoute = routeItems.find((item) => item.path === activePath) ?? routeItems[0];
	const [workspace, setWorkspace] = useState<WorkspaceState>(emptyWorkspace);
	const [health, setHealth] = useState<MetricsHealth | null>(null);
	const [capabilities, setCapabilities] = useState<MetricsCapabilities | null>(null);
	const [serviceError, setServiceError] = useState<string | null>(null);
	const [packageText, setPackageText] = useState("");
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

	const submitPackage = useCallback(
		(actionKey: string, url: string, success: string) =>
			withBusy(actionKey, async () => {
				setOutput("package", "提交中...");
				setMessage("package", "提交中");
				try {
					const result = await postYaml<Record<string, unknown>>(url, packageText);
					setOutput("package", pretty(result));
					const ok = result.valid === true || result.accepted === true || result.status === "OK";
					setMessage("package", ok ? success : "请求已返回，请查看响应内容", ok ? "ok" : "warn");
				} catch (error) {
					setOutput("package", outputError(error));
					setMessage("package", "请求失败", "warn");
				}
			}),
		[packageText, setMessage, setOutput, withBusy],
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
				setWorkspace(emptyWorkspace);
			}
		}
		void load();
		return () => {
			cancelled = true;
		};
	}, []);

	const summaryCards = useMemo(
		() => [
			["服务归属", capabilities?.service || health?.service || "dts-metrics"],
			["启用状态", statusLabel(capabilities?.enabled ?? health?.enabled)],
			["当前版本", capabilities?.edition || health?.edition || "等待服务响应"],
			["权限事实源", "dts-platform"],
		],
		[capabilities, health],
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
								指标资产
							</a>
							<a className="button primary" href={routeHref("/metrics/semantic/metrics", embedded)}>
								指标公式配置
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
							packageText={packageText}
							setPackageText={setPackageText}
							outputs={outputs}
							messages={messages}
							busy={busy}
							loadCapabilitiesInto={loadCapabilitiesInto}
							submitPackage={submitPackage}
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
	packageText: string;
	setPackageText: (value: string) => void;
	outputs: Record<string, string>;
	messages: Record<string, { text: string; tone?: StatusTone }>;
	busy: Record<string, boolean>;
	loadCapabilitiesInto: (outputKey: string) => Promise<void>;
	submitPackage: (actionKey: string, url: string, success: string) => Promise<void>;
	loadMigrationDryRun: () => Promise<void>;
}

function RoutePanel(props: RoutePanelProps) {
	switch (props.activeRoute.path) {
		case "/metrics/center":
			return <CenterPage {...props} />;
		case "/metrics/dictionary":
			return <MetricAssetsPage {...props} />;
		case "/metrics/packs":
			return <PackagePage {...props} />;
		case "/metrics/migration":
			return <MigrationPage {...props} />;
		case "/metrics/operations":
			return <OperationsPage {...props} />;
		case "/metrics/semantic":
			return <SemanticFlowPage embedded={props.embedded} />;
		case "/metrics/semantic/subjects":
			return <SubjectMappingPage {...props} />;
		case "/metrics/semantic/objects":
			return <BusinessObjectJoinPage {...props} />;
		case "/metrics/semantic/metrics":
			return <SemanticDesignerPage embedded={props.embedded} />;
		case "/metrics/semantic/models":
			return <ModelGenerationPage {...props} />;
		case "/metrics/semantic/publish":
			return <PublishPage {...props} />;
		case "/metrics/semantic/runs":
			return <RunMonitorPage {...props} />;
		default:
			return <CenterPage {...props} />;
	}
}

function CenterPage({ capabilities, embedded, workspace, outputs, busy, loadCapabilitiesInto }: RoutePanelProps) {
	const published = countByStatus(workspace.metricAssets, "PUBLISHED");
	const review = countByStatus(workspace.metricAssets, "REVIEW");
	const draft = countByStatus(workspace.metricAssets, "DRAFT");

	return (
		<>
			<div className="section-head">
				<div>
					<h3>指标交付工作台</h3>
					<p>工作台只展示 metrics 服务和 platform 返回的真实状态；无服务响应时显示空态。</p>
				</div>
				<div className="toolbar">
					<button className="button" disabled={busy.centerContract} type="button" onClick={() => void loadCapabilitiesInto("centerContract")}>
						刷新能力
					</button>
					<a className="button primary" href={routeHref("/metrics/semantic/metrics", embedded)}>
						进入画布
					</a>
				</div>
			</div>

			<div className="command-strip">
				<div>
					<span>服务</span>
					<strong>{capabilities?.service || "等待响应"}</strong>
				</div>
				<div>
					<span>平台契约</span>
					<strong>{workspace.platformContracts.length}</strong>
				</div>
				<div>
					<span>指标资产</span>
					<strong>{workspace.metricAssets.length}</strong>
				</div>
				<div>
					<span>DWS/ADS 候选</span>
					<strong>{workspace.modelCandidates.length}</strong>
				</div>
			</div>

			<div className="workbench-grid">
				<section className="section focus-panel">
					<div className="section-title-row">
						<h4>指标资产状态</h4>
						<StatusPill value={review > 0 ? "REVIEW" : workspace.metricAssets.length ? "PASS" : "PENDING"} />
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
				</section>

				<section className="section flow-board">
					<h4>语义建模入口</h4>
					<div className="flow-lanes">
						{routes[1].items.map((item, index) => (
							<a className="flow-lane" href={routeHref(item.path, embedded)} key={item.path}>
								<span>{String(index + 1).padStart(2, "0")}</span>
								<strong>{item.title}</strong>
								<em>{item.description}</em>
							</a>
						))}
					</div>
				</section>
			</div>

			<section className="section">
				<h4>平台契约状态</h4>
				{workspace.platformContracts.length ? (
					<KeyTable headers={["能力", "接口/事实源", "状态"]} rows={workspace.platformContracts} />
				) : (
					<EmptyState title="暂无平台契约数据" description="等待 /api/metrics/workspace/snapshot 返回 platformContracts。" />
				)}
			</section>
			<JsonOutput value={outputs.centerContract || "等待能力响应"} />
		</>
	);
}

function MetricAssetsPage({ embedded, workspace, outputs, busy, loadCapabilitiesInto }: RoutePanelProps) {
	const [query, setQuery] = useState("");
	const [statusFilter, setStatusFilter] = useState("ALL");
	const visibleAssets = useMemo(() => {
		const normalized = query.trim().toLowerCase();
		return workspace.metricAssets.filter((item) => {
			const statusMatched = statusFilter === "ALL" || item.status === statusFilter;
			if (!statusMatched) return false;
			if (!normalized) return true;
			return [item.name, item.code, item.domain, item.owner, item.consumer, item.type].some((field) =>
				String(field || "").toLowerCase().includes(normalized),
			);
		});
	}, [query, statusFilter, workspace.metricAssets]);

	return (
		<>
			<div className="section-head">
				<div>
					<h3>指标资产列表</h3>
					<p>指标资产来自 metrics 服务快照；页面不再填充本地样例。</p>
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
					<input placeholder="指标名、编码、负责人、消费方" value={query} onChange={(event) => setQuery(event.target.value)} />
				</label>
				<div className="toolbar filters" aria-label="指标状态筛选">
					{[
						["ALL", `全部 ${workspace.metricAssets.length}`],
						["PUBLISHED", `已发布 ${countByStatus(workspace.metricAssets, "PUBLISHED")}`],
						["REVIEW", `审核中 ${countByStatus(workspace.metricAssets, "REVIEW")}`],
						["DRAFT", `草稿 ${countByStatus(workspace.metricAssets, "DRAFT")}`],
					].map(([value, label]) => (
						<button className={`chip ${statusFilter === value ? "active" : ""}`} key={value} type="button" onClick={() => setStatusFilter(value)}>
							{label}
						</button>
					))}
				</div>
			</div>

			<div className="table-wrap">
				{visibleAssets.length ? (
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
							{visibleAssets.map((item) => (
								<MetricAssetRow item={item} key={item.code} />
							))}
						</tbody>
					</table>
				) : (
					<EmptyState title="暂无指标资产" description="等待 /api/metrics/workspace/snapshot 返回 metricAssets。" />
				)}
			</div>
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

function PackagePage({ packageText, setPackageText, outputs, messages, busy, submitPackage }: RoutePanelProps) {
	const message = messages.package ?? { text: "等待提交真实指标包" };
	const canSubmit = packageText.trim().length > 0;
	return (
		<section className="section">
			<h3>指标包校验</h3>
			<p>这里不再预填充样例包。请粘贴真实 YAML/JSON 指标包后执行校验、候选生成或导入预检。</p>
			<div className="manifest-grid">
				<div>
					<textarea
						aria-label="指标包内容"
						placeholder="粘贴真实指标包内容"
						spellCheck={false}
						value={packageText}
						onChange={(event) => setPackageText(event.target.value)}
					/>
					<div className="toolbar">
						<button
							className="button primary"
							disabled={!canSubmit || busy.validatePackage}
							type="button"
							onClick={() => void submitPackage("validatePackage", "/api/metrics/packs/validate", "校验通过")}
						>
							校验指标包
						</button>
						<button
							className="button"
							disabled={!canSubmit || busy.previewArtifacts}
							type="button"
							onClick={() => void submitPackage("previewArtifacts", "/api/metrics/packs/preview-artifacts", "候选生成物已生成")}
						>
							预览生成物
						</button>
						<button
							className="button"
							disabled={!canSubmit || busy.dryRunImport}
							type="button"
							onClick={() => void submitPackage("dryRunImport", "/api/metrics/packs/import", "导入预检通过")}
						>
							导入预检
						</button>
					</div>
					<div className={`message ${message.tone || ""}`}>{message.text}</div>
				</div>
				<JsonOutput value={outputs.package || "暂无结果"} />
			</div>
		</section>
	);
}

function SemanticFlowPage({ embedded }: { embedded: boolean }) {
	return (
		<>
			<div className="section-head">
				<div>
					<h3>语义建模流程</h3>
					<p>按菜单顺序进入主题域、业务对象、指标公式、DWS/ADS、发布和运行页面。</p>
				</div>
				<a className="button primary" href={routeHref("/metrics/semantic/metrics", embedded)}>
					进入指标公式配置
				</a>
			</div>
			<div className="workflow">
				{routes[1].items.map((item, index) => (
					<a className="workflow-step" href={routeHref(item.path, embedded)} key={item.path}>
						<span>{String(index + 1).padStart(2, "0")}</span>
						<strong>{item.title}</strong>
						<p>{item.description}</p>
					</a>
				))}
			</div>
		</>
	);
}

function SubjectMappingPage({ embedded, workspace, outputs, busy, loadCapabilitiesInto }: RoutePanelProps) {
	return (
		<>
			<div className="section-head">
				<div>
					<h3>主题域映射</h3>
					<p>主题域、术语和标准绑定必须来自 platform/metrics 服务快照。</p>
				</div>
				<div className="toolbar">
					<button className="button" disabled={busy.subjectContract} type="button" onClick={() => void loadCapabilitiesInto("subjectContract")}>
						读取 platform capability
					</button>
					<a className="button" href={routeHref("/metrics/packs", embedded)}>
						提交指标包
					</a>
				</div>
			</div>
			{workspace.subjectMappings.length ? (
				<div className="page-grid three-columns">
					{workspace.subjectMappings.map((item) => (
						<SubjectCard item={item} key={item.code || item.domain} />
					))}
				</div>
			) : (
				<EmptyState title="暂无主题域映射" description="等待 /api/metrics/workspace/snapshot 返回 subjectMappings。" />
			)}
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

function BusinessObjectJoinPage({ embedded, workspace }: RoutePanelProps) {
	return (
		<>
			<div className="section-head">
				<div>
					<h3>业务对象 Join 设计</h3>
					<p>业务对象、来源资产和 Join 条件必须来自服务端快照。</p>
				</div>
				<a className="button primary" href={routeHref("/metrics/semantic/models", embedded)}>
					查看 DWS/ADS
				</a>
			</div>
			{workspace.objectJoins.length ? (
				<div className="page-grid two-columns">
					{workspace.objectJoins.map((item) => (
						<ObjectJoinCard item={item} key={`${item.object}-${item.source}`} />
					))}
				</div>
			) : (
				<EmptyState title="暂无业务对象 Join" description="等待 /api/metrics/workspace/snapshot 返回 objectJoins。" />
			)}
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
					<div className="join-segment" key={`${table}-${key}-${type}`}>
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

function ModelGenerationPage({ workspace }: RoutePanelProps) {
	return (
		<>
			<div className="section-head">
				<div>
					<h3>DWS/ADS 生成</h3>
					<p>候选数据集来自 metrics 服务生成结果，最终检测必须走 dts-platform/dbt 网关。</p>
				</div>
			</div>
			{workspace.modelCandidates.length ? (
				<div className="generator-layout">
					{workspace.modelCandidates.map((model) => (
						<ModelCandidateCard model={model} key={model.name} />
					))}
				</div>
			) : (
				<EmptyState title="暂无 DWS/ADS 候选" description="等待服务端返回 modelCandidates。" />
			)}
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

function PublishPage({ workspace }: RoutePanelProps) {
	const readyGates = workspace.publishGates.filter(([, status]) => status === "PASS").length;
	const allGates = workspace.publishGates.length;
	return (
		<>
			<div className="section-head">
				<div>
					<h3>审核发布与血缘</h3>
					<p>发布检查来自服务端返回的门禁状态；页面不再内置示例发布链。</p>
				</div>
			</div>
			<div className="release-summary">
				<div>
					<span>门禁通过</span>
					<strong>
						{readyGates}/{allGates}
					</strong>
				</div>
				<div>
					<span>发布事实源</span>
					<strong>platform/dbt gate</strong>
				</div>
			</div>
			{workspace.publishGates.length ? (
				<section className="section">
					<h4>发布检查</h4>
					<KeyTable headers={["检查项", "状态", "说明"]} rows={workspace.publishGates} />
				</section>
			) : (
				<EmptyState title="暂无发布门禁" description="等待 /api/metrics/workspace/snapshot 返回 publishGates。" />
			)}
		</>
	);
}

function OperationsPage({ embedded, workspace, outputs, busy, loadCapabilitiesInto }: RoutePanelProps) {
	const successCount = workspace.runRecords.filter((record) => record[2] === "SUCCESS").length;
	const warningCount = workspace.runRecords.filter((record) => record[2] === "WARNING").length;
	const pendingCount = workspace.runRecords.filter((record) => record[2] === "PENDING").length;

	return (
		<>
			<div className="section-head">
				<div>
					<h3>运行与告警</h3>
					<p>工作台侧只聚合真实运行记录和告警状态，不再提供本地演示数据。</p>
				</div>
				<div className="toolbar">
					<button className="button" disabled={busy.runStatus} type="button" onClick={() => void loadCapabilitiesInto("runStatus")}>
						刷新服务观测
					</button>
					<a className="button" href={routeHref("/metrics/semantic/runs", embedded)}>
						模型运行监控
					</a>
				</div>
			</div>
			<RunRecordSummary successCount={successCount} pendingCount={pendingCount} warningCount={warningCount} total={workspace.runRecords.length} />
			{workspace.runRecords.length ? (
				<div className="table-wrap">
					<KeyTable headers={["模型", "层级", "状态", "最近运行", "耗时", "说明"]} rows={workspace.runRecords} />
				</div>
			) : (
				<EmptyState title="暂无运行告警" description="等待 /api/metrics/workspace/snapshot 返回 runRecords。" />
			)}
			<JsonOutput value={outputs.runStatus || "等待刷新运行观测"} />
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
					<p>运行记录来自 metrics 服务和 platform/dbt 回传。</p>
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
			<RunRecordSummary successCount={successCount} pendingCount={pendingCount} warningCount={warningCount} total={workspace.runRecords.length} />
			{workspace.runRecords.length ? (
				<div className="table-wrap">
					<KeyTable headers={["模型", "层级", "状态", "最近运行", "耗时", "说明"]} rows={workspace.runRecords} />
				</div>
			) : (
				<EmptyState title="暂无运行记录" description="等待 /api/metrics/workspace/snapshot 返回 runRecords。" />
			)}
			<JsonOutput value={outputs.runStatus || "等待刷新运行观测"} />
		</>
	);
}

function RunRecordSummary({
	successCount,
	pendingCount,
	warningCount,
	total,
}: {
	successCount: number;
	pendingCount: number;
	warningCount: number;
	total: number;
}) {
	return (
		<div className="run-board">
			{[
				["成功", String(successCount)],
				["等待", String(pendingCount)],
				["告警", String(warningCount)],
				["总数", String(total)],
			].map(([label, value]) => (
				<div key={label}>
					<span>{label}</span>
					<strong>{value}</strong>
				</div>
			))}
		</div>
	);
}

function MigrationPage({ outputs, busy, loadMigrationDryRun }: RoutePanelProps) {
	return (
		<section className="section">
			<h3>迁移 dry-run</h3>
			<p>迁移页面只展示服务端 dry-run 报告，不在前端内置旧语义样例。</p>
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
	return (
		<pre className="output" aria-label="接口输出">
			{value}
		</pre>
	);
}

function StatusPill({ value }: { value: string }) {
	const tone = statusOk.has(value) ? "ok" : statusWarn.has(value) ? "warn" : "neutral";
	return <span className={`status ${tone}`}>{value}</span>;
}

function looksLikeStatus(value: string): boolean {
	return statusOk.has(value) || statusWarn.has(value) || /^[A-Z_]+$/.test(value);
}
