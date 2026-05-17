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
				description: "沉淀指标名称、口径、公式、单位、负责人和版本。当前阶段先承接指标包校验与导入入口。",
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
				description: "管理项目、采购、库存、质量、财务等业务主题，后续将与数据资产目录做权限校验。",
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
  - stat_month
  - dept_name
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
  platform_assets:
    - type: DATASET
      id: dwd_project_detail
    - type: GLOSSARY_TERM
      id: glossary.project
    - type: GLOSSARY_TERM
      id: glossary.direct_cost_execution_rate`;

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
	if (activeRoute.path === "/metrics/dictionary" || activeRoute.path === "/metrics/packs") {
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

	if (activeRoute.path === "/metrics/migration") {
		panel.innerHTML = `
			<h3>迁移 dry-run</h3>
			<p>当前版本只提供映射报告和回滚边界，不自动迁移生产数据。旧语义接口保留兼容窗口，最终切换必须经过平台权限和 dbt 发布门禁。</p>
			<div class="toolbar">
				<button class="button primary" type="button" id="load-migration">读取 dry-run 报告</button>
			</div>
			<pre id="migration-output">等待读取</pre>
		`;
		const button = document.getElementById("load-migration");
		if (button) button.addEventListener("click", loadMigrationDryRun);
		return;
	}

	panel.innerHTML = `
		<h3>${escapeHtml(activeRoute.title)}交付边界</h3>
		<p>
			本页面已从 platform-webapp 拆到 dts-metrics 前端入口。platform 继续负责菜单、登录态和资产权限，
			dts-metrics 负责指标包、语义建模、DWS/ADS 数据集生成和发布流程。
		</p>
		<div class="steps">
			${[
				["权限", "只读取 platform 的 asset grant 和菜单授权，不在本地写入新的权限事实。"],
				["建模", "围绕主题域、业务对象、维度、指标公式、统计粒度组织页面。"],
				["发布", "生成 dbt/SQL、schema.yml、BI Dataset 和血缘注册材料，工程审核后发布。"],
			]
				.map(([title, text]) => `<div class="step"><strong>${escapeHtml(title)}</strong><span>${escapeHtml(text)}</span></div>`)
				.join("")}
		</div>
	`;
}

function renderManifestPanel() {
	return `
		<h3>指标包校验</h3>
		<p>合作方可以先按指标包契约提交 YAML/JSON，dts-metrics 做结构校验；后续导入会落到指标语义中心自己的数据表。</p>
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
	`;
}

async function loadServiceStatus() {
	const statusNode = document.getElementById("service-status");
	try {
		const [health, capabilities] = await Promise.all([fetchJson("/api/metrics/health"), fetchJson("/api/metrics/capabilities")]);
		if (statusNode) {
			statusNode.textContent = `${health.service || "dts-metrics"} ${health.status || "UP"}`;
			statusNode.classList.remove("warn");
			statusNode.classList.add("ok");
		}
		renderSummaryCards(capabilities);
	} catch (error) {
		if (statusNode) {
			statusNode.textContent = "服务状态不可用";
			statusNode.classList.remove("ok");
			statusNode.classList.add("warn");
		}
		renderSummaryCards(null);
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
