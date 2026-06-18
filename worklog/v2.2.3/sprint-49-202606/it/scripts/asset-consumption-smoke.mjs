import { createRequire } from "node:module";
import { mkdir } from "node:fs/promises";
import path from "node:path";

const require = createRequire(path.join(process.cwd(), "package.json"));
const { chromium } = require("@playwright/test");

const baseUrl = process.env.DTS_WEBAPP_URL || "http://127.0.0.1:18012";
const evidenceDir =
	process.env.DTS_SMOKE_EVIDENCE_DIR ||
	path.resolve(process.cwd(), "../../worklog/v2.2.3/sprint-49-202606/it/evidence");
const assetTableScreenshotPath = path.join(evidenceDir, "asset-ledger-table-1366x768.png");
const dataProductsScreenshotPath = path.join(evidenceDir, "data-products-consumption-1366x768.png");
const consumptionScreenshotPath = path.join(evidenceDir, "workbench-consumption-product-1366x768.png");
const failureScreenshotPath = path.join(evidenceDir, "asset-consumption-failure.png");

const assetRows = [
	{
		id: "asset-progress-wide",
		assetKey: "ads.project_progress_wide",
		displayName: "项目进度宽表",
		name: "项目进度宽表",
		type: "TABLE",
		warehouseLayer: "ADS",
		classification: "INTERNAL",
		governanceStatus: "GOVERNED",
		matchStatus: "MATCHED",
		owner: "数字化部门",
		ownerDept: "数字化部门",
		domainName: "项目交付",
		table: "ads_project_progress_wide",
		database: "ads",
		lastSyncedAt: "2026-06-18T10:00:00+08:00",
		lifecycleStatus: "SYNCED",
		description: "面向项目交付验收的核心资产",
	},
	{
		id: "asset-order-daily",
		assetKey: "dws.order_daily",
		displayName: "订单日汇总",
		name: "订单日汇总",
		type: "TABLE",
		warehouseLayer: "DWS",
		classification: "INTERNAL",
		governanceStatus: "PENDING_DOMAIN",
		matchStatus: "MATCHED",
		owner: "经营部门",
		ownerDept: "经营部门",
		table: "dws_order_daily",
		database: "dws",
		lastSyncedAt: "2026-06-18T09:30:00+08:00",
		lifecycleStatus: "SYNCED",
	},
];

const productRows = [
	{
		id: "product-001",
		code: "pjm_delivery_product",
		name: "项目交付数据产品",
		ownerDept: "数字化部门",
		description: "把项目进度宽表和交付指标打包给业务使用。",
		status: "DRAFT",
		classification: "INTERNAL",
		freshnessSla: "T+1 09:00 前",
		lifecycleStatus: "ACTIVE",
		visibility: "INTERNAL",
		consumerEntry: "",
		datasetIds: '["asset-progress-wide"]',
		indicatorCodes: '["pjm_progress_rate"]',
	},
];

const goldenChains = [
	{
		chainKey: "chain-product-001",
		displayName: "项目交付数据产品",
		sourceKind: "JDBC",
		currentStage: "CONSUMABLE",
		currentStageLabel: "可消费",
		status: "READY",
		owner: "数字化部门",
	},
];

const goldenChainDetail = {
	...goldenChains[0],
	stages: [
		{ stage: "SOURCE_READY", stageLabel: "数据源", status: "READY", owner: "数字化部门", evidenceRef: "SRC-001" },
		{ stage: "GOVERNANCE_READY", stageLabel: "治理", status: "READY", owner: "数据治理", evidenceRef: "GOV-001" },
		{ stage: "CONSUMABLE", stageLabel: "消费发布", status: "READY", owner: "业务部门", evidenceRef: "PUB-001" },
	],
};

function wrapped(data) {
	return { status: 200, data };
}

async function launchBrowser() {
	const args = ["--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage", "--disable-crashpad", "--disable-crash-reporter"];
	try {
		return await chromium.launch({ headless: true, args });
	} catch {
		return chromium.launch({ headless: true, channel: "chrome", args });
	}
}

function isKnownGlobalConsoleWarning(text) {
	return text.includes("Warning: validateDOMNesting") && text.includes("src/components/nav/vertical/nav-list.tsx");
}

await mkdir(evidenceDir, { recursive: true });

const browser = await launchBrowser();
const context = await browser.newContext({ viewport: { width: 1366, height: 768 } });
const page = await context.newPage();
const consoleErrors = [];
const ignoredConsoleWarnings = [];
const badResponses = [];

page.on("console", (msg) => {
	if (msg.type() === "error") {
		const text = msg.text();
		if (isKnownGlobalConsoleWarning(text)) {
			ignoredConsoleWarnings.push(text);
			return;
		}
		consoleErrors.push(text);
	}
});
page.on("pageerror", (err) => {
	consoleErrors.push(err.message);
});
page.on("response", (response) => {
	if (response.status() >= 400) {
		badResponses.push(`${response.status()} ${response.url()}`);
	}
});

await page.addInitScript(() => {
	const now = Date.now();
	localStorage.setItem(
		"dts.platform.userStore",
		JSON.stringify({
			state: {
				userInfo: {
					id: "sprint-49-f2-smoke",
					username: "sprint49-f2",
					email: "sprint49-f2@example.com",
					roles: ["DEPT_DATA_DEV"],
					permissions: [],
				},
				userToken: {
					authenticated: true,
					tokenExpiresAt: now + 60 * 60 * 1000,
				},
			},
			version: 0,
		}),
	);
	localStorage.setItem("dts.platform.session.loginTs", String(now));
	localStorage.setItem("dts.platform.session.lastActivity", String(now));
});

await page.route("**/api/session/status", (route) => route.fulfill({ json: wrapped({ authenticated: true }) }));
await page.route("**/api/menu/tree", (route) =>
	route.fulfill({
		json: wrapped([
			{
				id: "workbench",
				name: "工作台",
				path: "/workbench",
				children: [{ id: "workbench-home", name: "我的概览", path: "/workbench" }],
			},
			{
				id: "catalog",
				name: "数据资产",
				path: "/catalog",
				children: [
					{ id: "catalog-assets", name: "数据资产目录", path: "/catalog/assets" },
					{ id: "catalog-products", name: "数据产品", path: "/catalog/data-products" },
				],
			},
			{
				id: "services",
				name: "数据服务",
				path: "/services",
				children: [
					{ id: "services-apis", name: "数据 API", path: "/services/apis" },
					{ id: "services-consumption", name: "业务消费", path: "/services/consumption" },
				],
			},
		]),
	}),
);
await page.route("**/bi/api/screens**", (route) =>
	route.fulfill({
		json: wrapped({
			content: [],
			total: 0,
			page: 0,
			size: 10,
		}),
	}),
);
await page.route("**/api/workbench/audit**", (route) => route.fulfill({ json: wrapped({}) }));
await page.route("**/api/directory/orgs**", (route) =>
	route.fulfill({
		json: wrapped([
			{
				code: "digital",
				name: "数字化部门",
			},
		]),
	}),
);
await page.route("**/api/workbench/preferences**", (route) =>
	route.fulfill({
		json: wrapped({
			version: 1,
			availableComponents: [],
			items: [],
		}),
	}),
);

await page.route("**/api/catalog/domains/tree", (route) =>
	route.fulfill({ json: wrapped([{ id: "domain-pjm", name: "项目交付", children: [] }]) }),
);
await page.route("**/api/catalog/domains**", (route) =>
	route.fulfill({ json: wrapped({ content: [{ id: "domain-pjm", name: "项目交付" }], total: 1, page: 0, size: 200 }) }),
);
await page.route("**/api/catalog/ops/reconciliation**", (route) =>
	route.fulfill({
		json: wrapped({
			assertionCount: 1,
			failedCount: 0,
			errorCount: 0,
			warningCount: 0,
			assertions: [],
			regressionChecklist: [],
		}),
	}),
);
await page.route("**/api/catalog/assets-v2/governance-gaps**", (route) =>
	route.fulfill({ json: wrapped({ severityCounts: { BLOCKING: 0 }, content: [], total: 0 }) }),
);
await page.route("**/api/catalog/assets-v2/lineage-failures**", (route) =>
	route.fulfill({ json: wrapped({ content: [], total: 0 }) }),
);
await page.route("**/api/catalog/assets-v2**", (route) =>
	route.fulfill({ json: wrapped({ content: assetRows, total: assetRows.length, page: 0, size: 18 }) }),
);
await page.route("**/api/catalog/data-products**", (route) =>
	route.fulfill({ json: wrapped({ content: productRows, total: productRows.length, page: 0, size: 50 }) }),
);
await page.route("**/api/governance/indicators**", (route) =>
	route.fulfill({
		json: wrapped({
			content: [{ id: "ind-001", code: "pjm_progress_rate", name: "项目进度达成率", domain: "项目交付", status: "ACTIVE" }],
			total: 1,
		}),
	}),
);
await page.route("**/api/golden-chains/chain-product-001", (route) => route.fulfill({ json: wrapped(goldenChainDetail) }));
await page.route("**/api/golden-chains", (route) => route.fulfill({ json: wrapped(goldenChains) }));
await page.route("**/api/workbench/leader-overview**", (route) =>
	route.fulfill({
		json: wrapped({
			role: "DEPT_LEADER",
			kpis: {},
			topReports: [],
			topAssets: [],
			screens: [],
			todos: [],
		}),
	}),
);

const result = {
	ok: false,
	baseUrl,
	assetTableScreenshotPath,
	dataProductsScreenshotPath,
	consumptionScreenshotPath,
	assetMetrics: {},
	consumptionMetrics: {},
	consoleErrors,
	badResponses,
	ignoredConsoleWarnings,
	failures: [],
};

try {
	await page.goto(`${baseUrl}/#/catalog/assets`, { waitUntil: "networkidle" });
	await page.getByText("资产地图").waitFor({ timeout: 15_000 });
	await page.getByRole("button", { name: "进入台账" }).first().click();
	await page.waitForSelector(".catalog-assets-table", { timeout: 15_000 });
	await page.getByText("项目进度宽表").waitFor({ timeout: 10_000 });

	result.assetMetrics = await page.evaluate(() => {
		const uniqueTops = (nodes) => Array.from(new Set(Array.from(nodes).map((node) => Math.round(node.getBoundingClientRect().top))));
		const action = document.querySelector(".catalog-assets-actions");
		const buttons = action ? action.querySelectorAll(".ant-btn") : [];
		const actionCell = action?.closest("td");
		return {
			tablePresent: Boolean(document.querySelector(".catalog-assets-table")),
			actionCellWidth: actionCell ? Math.round(actionCell.getBoundingClientRect().width) : 0,
			actionRows: uniqueTops(buttons).length,
			actionButtonCount: buttons.length,
			tableScrollWidth: Math.round(document.querySelector(".catalog-assets-table .ant-table")?.scrollWidth || 0),
		};
	});
	await page.screenshot({ path: assetTableScreenshotPath, fullPage: true });

	await page.goto(`${baseUrl}/#/catalog/data-products`, { waitUntil: "networkidle" });
	await page.getByText("项目交付数据产品").waitFor({ timeout: 15_000 });
	await page.screenshot({ path: dataProductsScreenshotPath, fullPage: true });
	await page.getByRole("button", { name: "查看消费" }).first().click();
	await page.waitForFunction(() => window.location.hash.includes("/workbench") && window.location.hash.includes("section=consumption"), undefined, {
		timeout: 10_000,
	});
	await page.getByRole("heading", { name: "消费发布" }).waitFor({ timeout: 15_000 });
	await page.getByText(/已带入数据产品 product-001/).waitFor({ timeout: 10_000 });
	result.consumptionMetrics = await page.evaluate(() => ({
		hash: window.location.hash,
		consumptionSectionVisible: Boolean(document.querySelector('[data-testid="data-consumption-workbench-section"]')),
		dataManagementSectionVisible: Boolean(document.querySelector('[data-testid="data-management-workbench-section"]')),
		bodyHasProductContext: document.body.innerText.includes("product-001"),
	}));
	await page.screenshot({ path: consumptionScreenshotPath, fullPage: true });
} catch (error) {
	const bodyText = await page.locator("body").innerText({ timeout: 2000 }).catch(() => "");
	await page.screenshot({ path: failureScreenshotPath, fullPage: true }).catch(() => {});
	result.failureScreenshotPath = failureScreenshotPath;
	result.bodyText = bodyText.slice(0, 2000);
	result.error = error instanceof Error ? error.message : String(error);
}

await browser.close();

if (!result.assetMetrics.tablePresent) result.failures.push("asset ledger table missing");
if (result.assetMetrics.actionCellWidth < 620) result.failures.push(`asset action column too narrow: ${result.assetMetrics.actionCellWidth}`);
if (result.assetMetrics.actionRows > 1) result.failures.push(`asset action buttons wrapped into ${result.assetMetrics.actionRows} rows`);
if (result.assetMetrics.actionButtonCount < 7) result.failures.push(`expected 7 asset action buttons, got ${result.assetMetrics.actionButtonCount}`);
if (!result.consumptionMetrics.hash?.includes("productId=product-001")) result.failures.push(`productId missing from consumption hash: ${result.consumptionMetrics.hash}`);
if (!result.consumptionMetrics.consumptionSectionVisible) result.failures.push("consumption workbench section missing");
if (result.consumptionMetrics.dataManagementSectionVisible) result.failures.push("data-management section id shown for consumption mode");
if (!result.consumptionMetrics.bodyHasProductContext) result.failures.push("product context copy missing");
if (consoleErrors.length) result.failures.push(`console errors: ${consoleErrors.join(" | ")}`);
if (badResponses.length) result.failures.push(`http errors: ${badResponses.join(" | ")}`);

result.ok = result.failures.length === 0;
console.log(JSON.stringify(result, null, 2));

if (result.failures.length) {
	process.exitCode = 1;
}
