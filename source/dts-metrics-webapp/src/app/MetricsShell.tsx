import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchJson, postYaml } from "../api";
import type { ModelVersionHistory, VisualAssetsResponse } from "../features/semantic/semanticTypes";
import SemanticDesignerPage from "../pages/semantic/SemanticDesignerPage";
import type { MetricsCapabilities, MetricsHealth, RouteGroup, RouteItem, StatusTone } from "../types";

type FeatureRoute = RouteItem & {
	feature: "F1" | "F2" | "F3" | "F4" | "F5";
	path: string;
};

type FeatureRouteGroup = Omit<RouteGroup, "items"> & {
	items: FeatureRoute[];
};

type OutputState = Record<string, string>;
type MessageState = Record<string, { text: string; tone?: StatusTone }>;
type BusyState = Record<string, boolean>;
type ModelAction = "artifacts" | "validate" | "submit-review" | "publish-dry-run" | "publish" | "rollback";

const routes: FeatureRouteGroup[] = [
	{
		group: "Sprint-35 Features",
		items: [
			{
				feature: "F1",
				path: "/metrics/f1-architecture",
				title: "整体架构与 PRD 契约",
				stage: "F1 Architecture / PRD",
				description: "固定 DWS/ADS 默认入口、DWD 高级建模边界，以及 platform 控制面事实源。",
			},
			{
				feature: "F2",
				path: "/metrics/f2-api-contracts",
				title: "前后端 API 契约",
				stage: "F2 API Contracts",
				description: "核验可视化资产、graph draft、preflight 和错误码契约。",
			},
			{
				feature: "F3",
				path: "/metrics/f3-visual-workbench",
				title: "前端可视化工作台",
				stage: "F3 Visual Workbench",
				description: "从已治理 DWS/ADS 资产进入 React Flow 建模画布。",
			},
			{
				feature: "F4",
				path: "/metrics/f4-modeling-gateway",
				title: "后端建模与 dbt 网关",
				stage: "F4 Modeling / dbt Gateway",
				description: "提交指标包、生成候选 artifact，并通过 platform/dbt gate 做验证。",
			},
			{
				feature: "F5",
				path: "/metrics/f5-security-it",
				title: "安全、评审机制与 IT 准入",
				stage: "F5 Security / IT",
				description: "核验 service-auth、RLS/masking、迁移 dry-run 与验收证据入口。",
			},
		],
	},
];

const routeItems = routes.flatMap((group) => group.items);
const compatibilityRoutes = new Map<string, string>([
	["/metrics", "/metrics/f3-visual-workbench"],
	["/metrics/center", "/metrics/f1-architecture"],
	["/metrics/dictionary", "/metrics/f3-visual-workbench"],
	["/metrics/packs", "/metrics/f4-modeling-gateway"],
	["/metrics/migration", "/metrics/f5-security-it"],
	["/metrics/operations", "/metrics/f5-security-it"],
	["/metrics/semantic", "/metrics/f3-visual-workbench"],
	["/metrics/semantic/subjects", "/metrics/f1-architecture"],
	["/metrics/semantic/objects", "/metrics/f3-visual-workbench"],
	["/metrics/semantic/metrics", "/metrics/f3-visual-workbench"],
	["/metrics/semantic/models", "/metrics/f4-modeling-gateway"],
	["/metrics/semantic/publish", "/metrics/f4-modeling-gateway"],
	["/metrics/semantic/runs", "/metrics/f5-security-it"],
]);

const layerRules = [
	["源数据库 / ODS / STG", "只作为 lineage、质量缺口和调试证据，不进入普通指标画布。"],
	["DWD", "高级建模上游，用于生成候选 DWS；缺粒度、主键或标准码时阻断。"],
	["DWS", "默认指标建模入口，承载主题域、业务对象、维度和汇总口径。"],
	["ADS", "已有应用层资产可复用或导入，但不能反推成为新指标唯一事实源。"],
	["BI Dataset / 大屏", "发布出口和消费锁定对象，不承载建模事实源。"],
];

const platformBoundaries = [
	["资产目录", "dts-platform catalog assets-v2"],
	["权限/RLS/masking", "platform asset permission policy"],
	["dbt 验证/发布", "platform dbt validation gateway"],
	["审计/审批/血缘", "platform audit, review, lineage"],
	["指标 graph/DSL/artifact", "dts-metrics domain facts"],
];

function normalizePath(pathname: string): string {
	const value = String(pathname || "/metrics/f3-visual-workbench").replace(/\/+$/, "") || "/metrics/f3-visual-workbench";
	return compatibilityRoutes.get(value) ?? value;
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

function statusLabel(value?: boolean): string {
	if (value === false) return "未启用";
	if (value === true) return "已启用";
	return "待确认";
}

function sendJson<T>(url: string, body: unknown): Promise<T> {
	return fetchJson<T>(url, {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify(body ?? {}),
	});
}

export default function MetricsShell() {
	const embedded = new URLSearchParams(window.location.search).get("embedded") === "1";
	const activePath = normalizePath(window.location.pathname);
	const activeRoute = routeItems.find((item) => item.path === activePath) ?? routeItems[2];
	const [health, setHealth] = useState<MetricsHealth | null>(null);
	const [capabilities, setCapabilities] = useState<MetricsCapabilities | null>(null);
	const [serviceError, setServiceError] = useState<string | null>(null);
	const [packageText, setPackageText] = useState("");
	const [graphText, setGraphText] = useState('{\n  "base": "",\n  "measures": [],\n  "dimensions": [],\n  "joins": []\n}');
	const [modelId, setModelId] = useState("order-summary");
	const [modelText, setModelText] = useState(
		'{\n  "modelName": "dws_order_summary",\n  "graph": {\n    "base": "dws_order_day",\n    "measures": ["order_amount"],\n    "dimensions": ["stat_date"],\n    "nodes": [\n      {\n        "id": "dws_order_day",\n        "role": "BASE",\n        "warehouseLayer": "DWS",\n        "grain": ["stat_date"]\n      }\n    ]\n  }\n}',
	);
	const [outputs, setOutputs] = useState<OutputState>({});
	const [messages, setMessages] = useState<MessageState>({});
	const [busy, setBusy] = useState<BusyState>({});

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

	const refreshCapabilities = useCallback(
		(outputKey: string) =>
			withBusy(outputKey, async () => {
				setOutput(outputKey, "读取中...");
				try {
					const [healthResult, capabilityResult] = await Promise.all([
						fetchJson<MetricsHealth>("/api/metrics/health"),
						fetchJson<MetricsCapabilities>("/api/metrics/capabilities"),
					]);
					setHealth(healthResult);
					setCapabilities(capabilityResult);
					setServiceError(null);
					setOutput(outputKey, pretty({ health: healthResult, capabilities: capabilityResult }));
				} catch (error) {
					setServiceError(outputError(error));
					setOutput(outputKey, outputError(error));
				}
			}),
		[setOutput, withBusy],
	);

	const loadVisualAssets = useCallback(
		(outputKey: string, includeDrilldown = false) =>
			withBusy(outputKey, async () => {
				setOutput(outputKey, "读取中...");
				try {
					const suffix = includeDrilldown ? "?layers=DWD&includeDrilldown=true&size=5" : "?layers=DWS,ADS&size=5";
					const result = await fetchJson<VisualAssetsResponse>(`/api/metrics/visual-assets${suffix}`);
					setOutput(outputKey, pretty(result));
				} catch (error) {
					setOutput(outputKey, outputError(error));
				}
			}),
		[setOutput, withBusy],
	);

	const submitGraph = useCallback(
		(outputKey: string, action: "preflight" | "save") =>
			withBusy(outputKey, async () => {
				setOutput(outputKey, "提交中...");
				try {
					const body = JSON.parse(graphText || "{}") as Record<string, unknown>;
					const result =
						action === "preflight"
							? await sendJson<Record<string, unknown>>("/api/metrics/graphs/draft/preflight", body)
							: await sendJson<Record<string, unknown>>("/api/metrics/graphs", body);
					setOutput(outputKey, pretty(result));
				} catch (error) {
					setOutput(outputKey, outputError(error));
				}
			}),
		[graphText, setOutput, withBusy],
	);

	const submitModel = useCallback(
		(outputKey: string, action: ModelAction, success: string) =>
			withBusy(outputKey, async () => {
				const id = modelId.trim();
				if (!id) {
					setOutput("model", "modelId is required");
					setMessage("model", "模型 ID 必填", "warn");
					return;
				}
					setOutput("model", "提交中...");
					setMessage("model", "提交中");
				try {
					const body =
						action === "artifacts" || action === "validate"
							? JSON.parse(modelText || "{}")
							: action === "rollback"
								? { reason: "manual rollback from metrics workbench" }
								: {};
					const result = await sendJson<Record<string, unknown>>(`/api/metrics/models/${encodeURIComponent(id)}/${action}`, body);
					setOutput("model", pretty(result));
					const ok =
						result.status === "ARTIFACT_GENERATED" ||
						result.status === "DBT_VALIDATED" ||
						result.status === "REVIEW_SUBMITTED" ||
						result.status === "PUBLISH_DRY_RUN_READY" ||
						result.status === "PUBLISHED" ||
						result.status === "ROLLED_BACK";
					setMessage("model", ok ? success : "请求已返回，请查看响应内容", ok ? "ok" : "warn");
				} catch (error) {
					setOutput("model", outputError(error));
					setMessage("model", "请求失败", "warn");
				}
			}),
		[modelId, modelText, setMessage, setOutput, withBusy],
	);

	const loadModelVersions = useCallback(
		() =>
			withBusy("modelVersions", async () => {
				const id = modelId.trim();
				if (!id) {
					setOutput("model", "modelId is required");
					setMessage("model", "模型 ID 必填", "warn");
					return;
				}
				setOutput("model", "读取中...");
				setMessage("model", "读取版本历史");
				try {
					const result = await fetchJson<ModelVersionHistory>(`/api/metrics/models/${encodeURIComponent(id)}/versions`);
					setOutput("model", pretty(result));
					setMessage("model", "版本历史已返回", "ok");
				} catch (error) {
					setOutput("model", outputError(error));
					setMessage("model", "请求失败", "warn");
				}
			}),
		[modelId, setMessage, setOutput, withBusy],
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
		void refreshCapabilities("startup");
	}, [refreshCapabilities]);

	const summaryCards = useMemo(
		() => [
			["服务归属", capabilities?.service || health?.service || "dts-metrics"],
			["启用状态", statusLabel(capabilities?.enabled ?? health?.enabled)],
			["当前版本", capabilities?.edition || health?.edition || "等待服务响应"],
			["当前 Feature", `${activeRoute.feature} / ${activeRoute.title}`],
		],
		[activeRoute.feature, activeRoute.title, capabilities, health],
	);

	return (
		<div className="app-shell">
			<header className="topbar">
				<div>
					<p className="eyebrow">Sprint-35 Metrics</p>
					<h1>dts-metrics 数据仓库可视化设计</h1>
				</div>
				<ServicePill health={health} error={serviceError} />
			</header>

			<div className="layout">
				<nav className="side-nav" aria-label="Sprint-35 Feature 导航">
					{routes.map((group) => (
						<div className="nav-group" key={group.group}>
							<p className="nav-group-title">{group.group}</p>
							{group.items.map((item) => (
								<a className={`nav-link ${item.path === activeRoute.path ? "active" : ""}`} href={routeHref(item.path, embedded)} key={item.path}>
									<span>{item.feature}</span>
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
							<a className="button" href={routeHref("/metrics/f2-api-contracts", embedded)}>
								API 契约
							</a>
							<a className="button primary" href={routeHref("/metrics/f3-visual-workbench", embedded)}>
								可视化工作台
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
							embedded={embedded}
							capabilities={capabilities}
							packageText={packageText}
							setPackageText={setPackageText}
							modelId={modelId}
							setModelId={setModelId}
							modelText={modelText}
							setModelText={setModelText}
							graphText={graphText}
							setGraphText={setGraphText}
							outputs={outputs}
							messages={messages}
							busy={busy}
							refreshCapabilities={refreshCapabilities}
							loadVisualAssets={loadVisualAssets}
							submitGraph={submitGraph}
							submitModel={submitModel}
							loadModelVersions={loadModelVersions}
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
	activeRoute: FeatureRoute;
	embedded: boolean;
	capabilities: MetricsCapabilities | null;
	packageText: string;
	setPackageText: (value: string) => void;
	modelId: string;
	setModelId: (value: string) => void;
	modelText: string;
	setModelText: (value: string) => void;
	graphText: string;
	setGraphText: (value: string) => void;
	outputs: OutputState;
	messages: MessageState;
	busy: BusyState;
	refreshCapabilities: (outputKey: string) => Promise<void>;
	loadVisualAssets: (outputKey: string, includeDrilldown?: boolean) => Promise<void>;
	submitGraph: (outputKey: string, action: "preflight" | "save") => Promise<void>;
	submitModel: (outputKey: string, action: ModelAction, success: string) => Promise<void>;
	loadModelVersions: () => Promise<void>;
	submitPackage: (actionKey: string, url: string, success: string) => Promise<void>;
	loadMigrationDryRun: () => Promise<void>;
}

function RoutePanel(props: RoutePanelProps) {
	switch (props.activeRoute.feature) {
		case "F1":
			return <FeatureArchitecturePage {...props} />;
		case "F2":
			return <FeatureApiContractsPage {...props} />;
		case "F3":
			return <SemanticDesignerPage embedded={props.embedded} />;
		case "F4":
			return <FeatureBackendGatewayPage {...props} />;
		case "F5":
			return <FeatureSecurityItPage {...props} />;
		default:
			return <SemanticDesignerPage embedded={props.embedded} />;
	}
}

function FeatureArchitecturePage({ embedded }: RoutePanelProps) {
	return (
		<>
			<div className="section-head">
				<div>
					<h3>分层入口边界</h3>
					<p>DWS/ADS 是普通可视化入口；DWD 只进入高级建模，ODS/STG 留在 lineage 和诊断层。</p>
				</div>
				<a className="button primary" href={routeHref("/metrics/f3-visual-workbench", embedded)}>
					进入 DWS/ADS 画布
				</a>
			</div>
			<div className="page-grid two-columns">
				<section className="section">
					<h4>ELT 分层准入</h4>
					<div className="feature-rule-list">
						{layerRules.map(([label, value]) => (
							<div className="feature-rule" key={label}>
								<strong>{label}</strong>
								<span>{value}</span>
							</div>
						))}
					</div>
				</section>
				<section className="section">
					<h4>事实源边界</h4>
					<KeyTable headers={["能力", "事实源"]} rows={platformBoundaries} />
				</section>
			</div>
		</>
	);
}

function FeatureApiContractsPage({ graphText, setGraphText, outputs, busy, refreshCapabilities, loadVisualAssets, submitGraph }: RoutePanelProps) {
	return (
		<>
			<div className="section-head">
				<div>
					<h3>API 契约核验</h3>
					<p>本页直接调用 dts-metrics API，不读取 platform internal API，也不使用 workspace snapshot。</p>
				</div>
				<div className="toolbar">
					<button className="button" disabled={busy.apiCapability} type="button" onClick={() => void refreshCapabilities("apiCapability")}>
						刷新 capability
					</button>
					<button className="button primary" disabled={busy.visualAssets} type="button" onClick={() => void loadVisualAssets("visualAssets")}>
						读取 DWS/ADS
					</button>
					<button className="button" disabled={busy.dwdAssets} type="button" onClick={() => void loadVisualAssets("dwdAssets", true)}>
						高级 DWD 查询
					</button>
				</div>
			</div>
			<div className="split-layout">
				<section className="section">
					<h4>Graph draft</h4>
					<textarea className="code-editor tall" spellCheck={false} value={graphText} onChange={(event) => setGraphText(event.target.value)} />
					<div className="toolbar">
						<button className="button" disabled={busy.graphPreflight} type="button" onClick={() => void submitGraph("graphPreflight", "preflight")}>
							执行 preflight
						</button>
						<button className="button primary" disabled={busy.graphSave} type="button" onClick={() => void submitGraph("graphSave", "save")}>
							保存 graph draft
						</button>
					</div>
				</section>
				<section className="section">
					<h4>契约响应</h4>
					<JsonOutput value={outputs.graphSave || outputs.graphPreflight || outputs.visualAssets || outputs.apiCapability || "等待 API 响应"} />
				</section>
			</div>
		</>
	);
}

function FeatureBackendGatewayPage({
	packageText,
	setPackageText,
	modelId,
	setModelId,
	modelText,
	setModelText,
	outputs,
	messages,
	busy,
	submitModel,
	loadModelVersions,
	submitPackage,
}: RoutePanelProps) {
	const message = messages.package ?? { text: "等待提交指标包" };
	const modelMessage = messages.model ?? { text: "等待模型 lifecycle 操作" };
	const canSubmit = packageText.trim().length > 0;
	const canSubmitModel = modelId.trim().length > 0 && modelText.trim().length > 0;
	return (
		<div className="page-grid">
			<section className="section">
				<div className="section-head">
					<div>
						<h3>模型 lifecycle 与 platform/dbt 网关</h3>
						<p>候选 artifact、dbt validation、审核和发布只通过 metrics API 编排，再由 platform 控制面执行。</p>
					</div>
					<label className="inline-field">
						<span>模型 ID</span>
						<input value={modelId} onChange={(event) => setModelId(event.target.value)} />
					</label>
				</div>
				<div className="split-layout">
					<div>
						<textarea
							aria-label="模型 lifecycle 请求"
							className="code-editor tall"
							spellCheck={false}
							value={modelText}
							onChange={(event) => setModelText(event.target.value)}
						/>
						<div className="toolbar">
							<button
								className="button primary"
								disabled={!canSubmitModel || busy.modelArtifacts}
								type="button"
								onClick={() => void submitModel("modelArtifacts", "artifacts", "候选 artifact 已生成")}
							>
								生成 artifact
							</button>
							<button
								className="button"
								disabled={!canSubmitModel || busy.modelValidate}
								type="button"
								onClick={() => void submitModel("modelValidate", "validate", "platform/dbt 验证通过")}
							>
								模型验证
							</button>
							<button
								className="button"
								disabled={!modelId.trim() || busy.modelReview}
								type="button"
								onClick={() => void submitModel("modelReview", "submit-review", "审核已提交")}
							>
								提交审核
							</button>
							<button
								className="button"
								disabled={!modelId.trim() || busy.modelDryRun}
								type="button"
								onClick={() => void submitModel("modelDryRun", "publish-dry-run", "发布 dry-run 通过")}
							>
								发布 dry-run
							</button>
							<button
								className="button"
								disabled={!modelId.trim() || busy.modelPublish}
								type="button"
								onClick={() => void submitModel("modelPublish", "publish", "发布已提交")}
							>
								发布
							</button>
							<button
								className="button"
								disabled={!modelId.trim() || busy.modelVersions}
								type="button"
								onClick={() => void loadModelVersions()}
							>
								版本历史
							</button>
							<button
								className="button"
								disabled={!modelId.trim() || busy.modelRollback}
								type="button"
								onClick={() => void submitModel("modelRollback", "rollback", "已回滚上一版")}
							>
								回滚
							</button>
						</div>
						<div className={`message ${modelMessage.tone || ""}`}>{modelMessage.text}</div>
					</div>
					<div>
						<h4>模型网关响应</h4>
						<JsonOutput value={outputs.model || "等待模型 lifecycle 响应"} />
					</div>
				</div>
			</section>
			<div className="manifest-grid">
			<section className="section">
				<h3>指标包与候选 artifact</h3>
				<textarea
					aria-label="指标包内容"
					placeholder="粘贴 YAML/JSON 指标包内容"
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
						预览 artifact
					</button>
					<button
						className="button"
						disabled={!canSubmit || busy.dryRunImport}
						type="button"
						onClick={() => void submitPackage("dryRunImport", "/api/metrics/packs/import", "导入预检通过")}
					>
						导入 dry-run
					</button>
					<button
						className="button"
						disabled={!canSubmit || busy.publishDryRun}
						type="button"
						onClick={() => void submitPackage("publishDryRun", "/api/metrics/packs/publish-dry-run", "发布 dry-run 通过")}
					>
						发布 dry-run
					</button>
				</div>
				<div className={`message ${message.tone || ""}`}>{message.text}</div>
			</section>
			<section className="section">
				<h3>网关响应</h3>
				<JsonOutput value={outputs.package || "等待指标包操作响应"} />
			</section>
			</div>
		</div>
	);
}

function FeatureSecurityItPage({ capabilities, outputs, busy, refreshCapabilities, loadMigrationDryRun }: RoutePanelProps) {
	const contract = capabilities?.platformContract;
	const rows: Array<Array<string | number>> = [
		["service-auth header", contract?.authHeaders?.service || "X-DTS-Service"],
		["token header", contract?.authHeaders?.token || "X-DTS-Service-Token"],
		["service token configured", contract?.serviceTokenConfigured ? "true" : "false"],
		["platform api path", contract?.apiPath || "/api"],
	];
	return (
		<>
			<div className="section-head">
				<div>
					<h3>安全与 IT 准入</h3>
					<p>预览、验证、发布都必须保留 platform 权限、RLS/masking、dbt gate 和审计边界。</p>
				</div>
				<div className="toolbar">
					<button className="button" disabled={busy.securityCapability} type="button" onClick={() => void refreshCapabilities("securityCapability")}>
						刷新安全契约
					</button>
					<button className="button primary" disabled={busy.migration} type="button" onClick={() => void loadMigrationDryRun()}>
						读取迁移 dry-run
					</button>
				</div>
			</div>
			<div className="page-grid two-columns">
				<section className="section">
					<h4>service-auth 边界</h4>
					<KeyTable headers={["项目", "当前值"]} rows={rows} />
				</section>
				<section className="section">
					<h4>IT 响应</h4>
					<JsonOutput value={outputs.migration || outputs.securityCapability || "等待安全契约或迁移 dry-run 响应"} />
				</section>
			</div>
		</>
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
						{row.map((cell, cellIndex) => (
							<td key={`${cell}-${cellIndex}`}>{cell}</td>
						))}
					</tr>
				))}
			</tbody>
		</table>
	);
}

function JsonOutput({ value }: { value: string }) {
	return <pre className="json-output">{value}</pre>;
}
