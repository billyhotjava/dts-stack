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
				title: "指标字典",
				stage: "Metric Dictionary",
				description: "沉淀指标名称、口径、公式、单位、负责人和版本。当前阶段先承接指标包校验与导入入口。",
			},
			{
				path: "/metrics/packs",
				title: "指标包",
				stage: "Metric Pack",
				description: "以 YAML/JSON 描述主题域、业务对象、维度、指标和发布物，供合作方独立交付。",
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
const currentPath = normalizePath(window.location.pathname);
const activeRoute = routeItems.find((item) => item.path === currentPath) || routeItems[0];

const sampleManifest = `pack_id: project-management-core
pack_name: 项目管理核心指标包
version: 0.1.0
industry: project
edition_required: professional
files:
  domains: domains.yml
  business_objects: business_objects.yml
  dimensions: dimensions.yml
  metrics: metrics.yml
  models: models.yml
  datasets: datasets.yml
dependencies: {}`;

function normalizePath(pathname) {
	const value = String(pathname || "/metrics/center").replace(/\/+$/, "");
	if (!value || value === "/metrics") return "/metrics/center";
	return value;
}

function setText(id, value) {
	const node = document.getElementById(id);
	if (node) node.textContent = value;
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
							`<a class="nav-link ${item.path === activeRoute.path ? "active" : ""}" href="${item.path}">${escapeHtml(
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
					<a class="button" href="/metrics/semantic/metrics">进入指标配置</a>
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
			statusNode.classList.add("ok");
		}
		renderSummaryCards(capabilities);
	} catch (error) {
		if (statusNode) {
			statusNode.textContent = "服务状态不可用";
			statusNode.classList.add("warn");
		}
		renderSummaryCards(null);
	}
}

async function validateManifest() {
	const input = document.getElementById("manifest-input");
	const output = document.getElementById("manifest-output");
	const message = document.getElementById("manifest-message");
	if (!input || !output || !message) return;
	message.textContent = "校验中";
	try {
		const result = await fetchJson("/api/metrics/packs/validate", {
			method: "POST",
			headers: { "Content-Type": "text/yaml" },
			body: input.value,
		});
		output.textContent = JSON.stringify(result, null, 2);
		message.textContent = result.valid ? "校验通过" : "校验未通过，请查看结果";
	} catch (error) {
		output.textContent = String(error?.message || error);
		message.textContent = "校验请求失败";
	}
}

async function fetchJson(url, options) {
	const response = await fetch(url, { credentials: "same-origin", ...options });
	const text = await response.text();
	if (!response.ok) {
		throw new Error(text || `${response.status} ${response.statusText}`);
	}
	if (!text) return {};
	return JSON.parse(text);
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
loadServiceStatus();
