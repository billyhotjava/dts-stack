import { createRequire } from "node:module";
import { mkdir } from "node:fs/promises";
import path from "node:path";

const require = createRequire(path.join(process.cwd(), "package.json"));
const { chromium } = require("@playwright/test");

const baseUrl = process.env.DTS_WEBAPP_URL || "http://127.0.0.1:18012";
const evidenceDir =
	process.env.DTS_SMOKE_EVIDENCE_DIR ||
	path.resolve(process.cwd(), "../../worklog/v2.2.3/sprint-49-202606/it/evidence");
const screenshotPath = path.join(evidenceDir, "connector-registry-1366x768.png");
const drawerScreenshotPath = path.join(evidenceDir, "connector-registry-drawer-1366x768.png");
const createScreenshotPath = path.join(evidenceDir, "data-source-create-from-connector-1366x768.png");
const failureScreenshotPath = path.join(evidenceDir, "connector-registry-failure.png");

const connectorRows = [
	{
		id: "postgresql",
		connectorKey: "postgresql",
		name: "PostgreSQL",
		category: "DATABASE",
		sourceType: "postgres",
		defaultEngine: "ADDAX",
		status: "ACTIVE",
		displayOrder: 10,
		description: "PostgreSQL 数据库连接器",
		lastUpdatedAt: "2026-06-17T04:24:26+08:00",
		capabilities: {
			connectionTest: true,
			schemaDiscover: true,
			samplePreview: true,
			fullRefresh: true,
			append: true,
			timestampIncremental: true,
			primaryKeyIncremental: true,
			cdc: true,
			odsGeneration: true,
			dbtSourceGeneration: true,
		},
		configSchema: { required: ["host", "port", "database"], optional: ["schema"], defaults: { port: 5432 } },
		sensitiveFields: ["password"],
		compatibility: { chrome: "95+" },
	},
	{
		id: "mysql",
		connectorKey: "mysql",
		name: "MySQL",
		category: "DATABASE",
		sourceType: "mysql",
		defaultEngine: "ADDAX",
		status: "ACTIVE",
		displayOrder: 20,
		description: "MySQL 数据库连接器",
		lastUpdatedAt: "2026-06-17T04:24:26+08:00",
		capabilities: {
			connectionTest: true,
			schemaDiscover: true,
			samplePreview: true,
			fullRefresh: true,
			append: true,
			timestampIncremental: true,
			primaryKeyIncremental: true,
		},
		configSchema: { required: ["host", "port", "database"], optional: ["charset"], defaults: { port: 3306 } },
		sensitiveFields: ["password"],
		compatibility: { chrome: "95+" },
	},
];

function wrapped(data) {
	return { status: 200, data };
}

async function launchBrowser() {
	const args = ["--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage", "--disable-crashpad", "--disable-crash-reporter"];
	try {
		return await chromium.launch({ headless: true, args });
	} catch {
		return chromium.launch({
			headless: true,
			channel: "chrome",
			args,
		});
	}
}

await mkdir(evidenceDir, { recursive: true });

const browser = await launchBrowser();
const context = await browser.newContext({ viewport: { width: 1366, height: 768 } });
const page = await context.newPage();
const consoleErrors = [];
const ignoredConsoleWarnings = [];

function isKnownGlobalConsoleWarning(text) {
	return text.includes("Warning: validateDOMNesting") && text.includes("src/components/nav/vertical/nav-list.tsx");
}

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

await page.addInitScript(() => {
	const now = Date.now();
	localStorage.setItem(
		"dts.platform.userStore",
		JSON.stringify({
			state: {
				userInfo: {
					id: "sprint-49-smoke",
					username: "sprint49",
					email: "sprint49@example.com",
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
					id: "foundation",
					name: "数据接入基础",
					path: "/foundation",
					children: [
						{ id: "foundation-connectors", name: "连接器目录", path: "/foundation/connectors" },
						{ id: "foundation-data-sources", name: "数据源连接", path: "/foundation/data-sources" },
					],
				},
			]),
		}),
	);
await page.route("**/api/infra/connectors**", (route) => route.fulfill({ json: wrapped(connectorRows) }));
await page.route("**/api/infra/data-sources**", (route) => route.fulfill({ json: wrapped([]) }));
await page.route("**/api/infra/jdbc-drivers**", (route) => route.fulfill({ json: wrapped([]) }));
await page.route("**/api/directory/orgs", (route) => route.fulfill({ json: wrapped([]) }));

try {
	await page.goto(`${baseUrl}/#/foundation/connectors`, { waitUntil: "networkidle" });
	await page.waitForSelector(".connector-registry-table", { timeout: 15_000 });
	await page.waitForSelector("text=PostgreSQL", { timeout: 15_000 });
} catch (error) {
	const bodyText = await page.locator("body").innerText({ timeout: 2000 }).catch(() => "");
	await page.screenshot({ path: failureScreenshotPath, fullPage: true }).catch(() => {});
	await browser.close();
	console.log(
		JSON.stringify(
			{
				ok: false,
				baseUrl,
				currentUrl: page.url(),
				failureScreenshotPath,
				consoleErrors,
				ignoredConsoleWarnings,
				bodyText: bodyText.slice(0, 2000),
				error: error instanceof Error ? error.message : String(error),
			},
			null,
			2,
		),
	);
	process.exitCode = 1;
	process.exit();
}

const metrics = await page.evaluate(() => {
	const uniqueTops = (nodes) => {
		const tops = Array.from(nodes).map((node) => Math.round(node.getBoundingClientRect().top));
		return Array.from(new Set(tops));
	};
	const capability = document.querySelector(".connector-registry-capability-tags");
	const action = document.querySelector(".connector-registry-actions");
	const actionButtons = action ? action.querySelectorAll(".ant-btn") : [];
	const capabilityTags = capability ? capability.querySelectorAll(".ant-tag") : [];
	const capabilityCell = capability?.closest("td");
	const actionCell = action?.closest("td");

	return {
		path: window.location.pathname,
		tablePresent: Boolean(document.querySelector(".connector-registry-table")),
		capabilityCellWidth: capabilityCell ? Math.round(capabilityCell.getBoundingClientRect().width) : 0,
		actionCellWidth: actionCell ? Math.round(actionCell.getBoundingClientRect().width) : 0,
		capabilityRows: uniqueTops(capabilityTags).length,
		actionRows: uniqueTops(actionButtons).length,
		actionButtonCount: actionButtons.length,
	};
});

await page.screenshot({ path: screenshotPath, fullPage: true });
const actionWorkflow = {
	configDrawerOpened: false,
	templateDrawerOpened: false,
	footerCreateButtonVisible: false,
	dataSourceCreateModalOpened: false,
	initialConnectorApplied: false,
	drawerLeft: 0,
	drawerWidth: 0,
	createModalLeft: 0,
	createModalWidth: 0,
};

try {
	await page.getByRole("button", { name: /配\s*置/ }).first().click();
	await page.getByText("PostgreSQL / 配置要求").waitFor({ timeout: 5000 });
	actionWorkflow.configDrawerOpened = true;
	actionWorkflow.footerCreateButtonVisible =
		(await page.locator(".ant-drawer-footer").getByRole("button", { name: "创建数据源" }).count()) > 0;
	await page.locator(".ant-drawer-close").click();
	await page.locator(".ant-drawer").waitFor({ state: "hidden", timeout: 5000 });

	await page.getByRole("button", { name: /查看模板/ }).first().click();
	await page.getByText("PostgreSQL / 配置模板").waitFor({ timeout: 5000 });
	await page.getByText("默认值模板").waitFor({ timeout: 5000 });
	await page.waitForFunction(
		() => {
			const wrapper = document.querySelector(".ant-drawer-content-wrapper");
			const rect = wrapper?.getBoundingClientRect();
			return Boolean(rect && rect.width >= 680 && rect.left <= window.innerWidth - 680 && rect.right <= window.innerWidth + 2);
		},
		undefined,
		{ timeout: 5000 },
	);
	const drawerMetrics = await page.evaluate(() => {
		const wrapper = document.querySelector(".ant-drawer-content-wrapper");
		const rect = wrapper?.getBoundingClientRect();
		return {
			left: rect ? Math.round(rect.left) : 0,
			width: rect ? Math.round(rect.width) : 0,
		};
	});
	actionWorkflow.drawerLeft = drawerMetrics.left;
	actionWorkflow.drawerWidth = drawerMetrics.width;
	actionWorkflow.templateDrawerOpened = true;
	await page.screenshot({ path: drawerScreenshotPath, fullPage: true });

	await page.locator(".ant-drawer-footer").getByRole("button", { name: "创建数据源" }).click();
	await page.waitForFunction(() => window.location.hash.includes("/foundation/data-sources"), undefined, { timeout: 5000 });
	await page.getByText("新增数据源").waitFor({ timeout: 10_000 });
	actionWorkflow.dataSourceCreateModalOpened = true;
	await page.getByText("PostgreSQL · ADDAX").waitFor({ timeout: 10_000 });
	actionWorkflow.initialConnectorApplied = true;
	await page.waitForFunction(
		() => {
			const modal = document.querySelector(".ant-modal-content");
			const rect = modal?.getBoundingClientRect();
			return Boolean(rect && rect.width >= 480 && rect.left >= 0 && rect.right <= window.innerWidth);
		},
		undefined,
		{ timeout: 5000 },
	);
	const createModalMetrics = await page.evaluate(() => {
		const modal = document.querySelector(".ant-modal-content");
		const rect = modal?.getBoundingClientRect();
		return {
			left: rect ? Math.round(rect.left) : 0,
			width: rect ? Math.round(rect.width) : 0,
		};
	});
	actionWorkflow.createModalLeft = createModalMetrics.left;
	actionWorkflow.createModalWidth = createModalMetrics.width;
	await page.screenshot({ path: createScreenshotPath, fullPage: true });
} catch (error) {
	const bodyText = await page.locator("body").innerText({ timeout: 2000 }).catch(() => "");
	await page.screenshot({ path: failureScreenshotPath, fullPage: true }).catch(() => {});
	await browser.close();
	console.log(
		JSON.stringify(
			{
				ok: false,
				baseUrl,
				currentUrl: page.url(),
				failureScreenshotPath,
				screenshotPath,
				drawerScreenshotPath,
				createScreenshotPath,
				metrics,
				actionWorkflow,
				consoleErrors,
				ignoredConsoleWarnings,
				bodyText: bodyText.slice(0, 2000),
				error: error instanceof Error ? error.message : String(error),
			},
			null,
			2,
		),
	);
	process.exitCode = 1;
	process.exit();
}
await browser.close();

const failures = [];
if (!metrics.tablePresent) failures.push("table missing");
if (metrics.capabilityCellWidth < 150) failures.push(`capability column too narrow: ${metrics.capabilityCellWidth}`);
if (metrics.actionCellWidth < 320) failures.push(`action column too narrow: ${metrics.actionCellWidth}`);
if (metrics.capabilityRows > 1) failures.push(`capability tags wrapped into ${metrics.capabilityRows} rows`);
if (metrics.actionRows > 1) failures.push(`action buttons wrapped into ${metrics.actionRows} rows`);
if (metrics.actionButtonCount < 5) failures.push(`expected 5 action buttons, got ${metrics.actionButtonCount}`);
if (!actionWorkflow.configDrawerOpened) failures.push("config drawer did not open");
if (!actionWorkflow.templateDrawerOpened) failures.push("template drawer did not open");
if (!actionWorkflow.footerCreateButtonVisible) failures.push("drawer footer create button missing");
if (!actionWorkflow.dataSourceCreateModalOpened) failures.push("data source create modal did not open");
if (!actionWorkflow.initialConnectorApplied) failures.push("initial connector was not applied to create modal");
if (actionWorkflow.drawerWidth < 680) failures.push(`drawer width too narrow: ${actionWorkflow.drawerWidth}`);
if (actionWorkflow.drawerLeft > 700) failures.push(`drawer is not fully visible: left=${actionWorkflow.drawerLeft}`);
if (actionWorkflow.createModalWidth < 480) failures.push(`create modal width too narrow: ${actionWorkflow.createModalWidth}`);
if (actionWorkflow.createModalLeft < 0 || actionWorkflow.createModalLeft > 900) {
	failures.push(`create modal is not fully visible: left=${actionWorkflow.createModalLeft}`);
}
if (consoleErrors.length) failures.push(`console errors: ${consoleErrors.join(" | ")}`);

const result = {
	ok: failures.length === 0,
		baseUrl,
		screenshotPath,
		drawerScreenshotPath,
		createScreenshotPath,
		metrics,
		actionWorkflow,
		consoleErrors,
	ignoredConsoleWarnings,
	failures,
};

console.log(JSON.stringify(result, null, 2));

if (failures.length) {
	process.exitCode = 1;
}
