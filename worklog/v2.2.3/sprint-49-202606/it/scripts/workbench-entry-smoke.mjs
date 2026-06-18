import { createRequire } from "node:module";
import { mkdir } from "node:fs/promises";
import path from "node:path";

const require = createRequire(path.join(process.cwd(), "package.json"));
const { chromium } = require("@playwright/test");

const baseUrl = process.env.DTS_WEBAPP_URL || "http://127.0.0.1:18012";
const evidenceDir =
	process.env.DTS_SMOKE_EVIDENCE_DIR ||
	path.resolve(process.cwd(), "../../worklog/v2.2.3/sprint-49-202606/it/evidence");
const homeScreenshotPath = path.join(evidenceDir, "workbench-home-customize-1366x768.png");
const dataManagementScreenshotPath = path.join(evidenceDir, "workbench-data-management-entry-1366x768.png");
const failureScreenshotPath = path.join(evidenceDir, "workbench-entry-failure.png");

const goldenChains = [
	{
		chainKey: "chain-workbench-entry",
		displayName: "现场配置主题",
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
		{ stage: "SOURCE_READY", stageLabel: "数据源", status: "READY", owner: "数字化部门", evidenceRef: "SRC-ENTRY" },
		{ stage: "GOVERNANCE_READY", stageLabel: "治理", status: "READY", owner: "数据治理", evidenceRef: "GOV-ENTRY" },
		{ stage: "CONSUMABLE", stageLabel: "消费发布", status: "READY", owner: "业务部门", evidenceRef: "PUB-ENTRY" },
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
let preferenceApiRequests = 0;

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
	window.__RUNTIME_CONFIG__ = {
		...(window.__RUNTIME_CONFIG__ || {}),
		enableWorkbenchPreferenceApi: false,
	};
	localStorage.setItem(
		"dts.platform.userStore",
		JSON.stringify({
			state: {
				userInfo: {
					id: "sprint-49-f3-smoke",
					username: "sprint49-f3",
					email: "sprint49-f3@example.com",
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

await page.route("**/api/workbench/preferences**", (route) => {
	preferenceApiRequests += 1;
	return route.fulfill({ status: 500, json: wrapped({ unexpected: true }) });
});
await page.route("**/api/session/status", (route) => route.fulfill({ json: wrapped({ authenticated: true }) }));
await page.route("**/api/menu/tree", (route) =>
	route.fulfill({
		json: wrapped([
			{
				id: "workbench",
				name: "工作台",
				path: "/workbench",
				children: [
					{ id: "workbench-home", name: "我的概览", path: "/workbench" },
					{ id: "workbench-todo", name: "待办事项", path: "/workbench/todo" },
				],
			},
			{
				id: "foundation",
				name: "数据接入",
				path: "/foundation",
				children: [{ id: "foundation-data-sources", name: "数据源", path: "/foundation/data-sources" }],
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
	route.fulfill({ json: wrapped([{ code: "digital", name: "数字化部门" }]) }),
);
await page.route("**/api/catalog/domains/tree", (route) =>
	route.fulfill({ json: wrapped([{ id: "domain-pjm", name: "项目交付", children: [] }]) }),
);
await page.route("**/api/catalog/domains**", (route) =>
	route.fulfill({ json: wrapped({ content: [{ id: "domain-pjm", name: "项目交付" }], total: 1, page: 0, size: 500 }) }),
);
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
await page.route("**/api/golden-chains/chain-workbench-entry", (route) => route.fulfill({ json: wrapped(goldenChainDetail) }));
await page.route("**/api/golden-chains", (route) => route.fulfill({ json: wrapped(goldenChains) }));

const result = {
	ok: false,
	baseUrl,
	homeScreenshotPath,
	dataManagementScreenshotPath,
	preferenceApiRequests,
	homeMetrics: {},
	dataManagementMetrics: {},
	consoleErrors,
	badResponses,
	ignoredConsoleWarnings,
	failures: [],
};

try {
	await page.goto(`${baseUrl}/#/workbench`, { waitUntil: "networkidle" });
	await page.getByTestId("platform-workbench-home").waitFor({ timeout: 15_000 });
	await page.getByTestId("workbench-entry-action-golden-chain").waitFor({ timeout: 15_000 });
	await page.getByRole("button", { name: "自定义工作台" }).click();
	await page.getByText("每个人保存自己的工作台显示项和顺序").waitFor({ timeout: 10_000 });

	result.homeMetrics = await page.evaluate(() => ({
		entryCardCount: document.querySelectorAll('[data-testid^="workbench-entry-card-"]').length,
		entryActionCount: document.querySelectorAll('[data-testid^="workbench-entry-action-"]').length,
		drawerVisible: Boolean(document.querySelector(".ant-drawer")),
		checkboxCount: document.querySelectorAll(".ant-drawer input[type='checkbox']").length,
		bodyMentionsPerUserConfig: document.body.innerText.includes("每个人保存自己的工作台显示项和顺序"),
	}));
	await page.screenshot({ path: homeScreenshotPath, fullPage: true });

	await page.getByRole("button", { name: "取消" }).click();
	await page.getByTestId("workbench-entry-action-golden-chain").click();
	await page.waitForFunction(() => window.location.hash.includes("/workbench") && window.location.hash.includes("section=data-management"), undefined, {
		timeout: 10_000,
	});
	await page.getByTestId("data-management-workbench-section").waitFor({ timeout: 15_000 });
	result.dataManagementMetrics = await page.evaluate(() => ({
		hash: window.location.hash,
		dataManagementSectionVisible: Boolean(document.querySelector('[data-testid="data-management-workbench-section"]')),
		consumptionSectionVisible: Boolean(document.querySelector('[data-testid="data-consumption-workbench-section"]')),
	}));
	await page.screenshot({ path: dataManagementScreenshotPath, fullPage: true });
} catch (error) {
	const bodyText = await page.locator("body").innerText({ timeout: 2000 }).catch(() => "");
	await page.screenshot({ path: failureScreenshotPath, fullPage: true }).catch(() => {});
	result.failureScreenshotPath = failureScreenshotPath;
	result.bodyText = bodyText.slice(0, 2000);
	result.error = error instanceof Error ? error.message : String(error);
}

result.preferenceApiRequests = preferenceApiRequests;

await browser.close();

if (preferenceApiRequests !== 0) result.failures.push(`preference API was requested ${preferenceApiRequests} times`);
if (result.homeMetrics.entryCardCount !== 7) result.failures.push(`expected 7 workbench entry cards, got ${result.homeMetrics.entryCardCount}`);
if (result.homeMetrics.entryActionCount !== 7) result.failures.push(`expected 7 workbench entry actions, got ${result.homeMetrics.entryActionCount}`);
if (!result.homeMetrics.drawerVisible) result.failures.push("customize drawer missing");
if (result.homeMetrics.checkboxCount < 11) result.failures.push(`expected at least 11 customize checkboxes, got ${result.homeMetrics.checkboxCount}`);
if (!result.homeMetrics.bodyMentionsPerUserConfig) result.failures.push("per-user customize copy missing");
if (!result.dataManagementMetrics.hash?.includes("section=data-management")) {
	result.failures.push(`data-management section hash missing: ${result.dataManagementMetrics.hash}`);
}
if (!result.dataManagementMetrics.dataManagementSectionVisible) result.failures.push("data-management section missing");
if (result.dataManagementMetrics.consumptionSectionVisible) result.failures.push("consumption section shown for data-management mode");
if (consoleErrors.length) result.failures.push(`console errors: ${consoleErrors.join(" | ")}`);
if (badResponses.length) result.failures.push(`http errors: ${badResponses.join(" | ")}`);

result.ok = result.failures.length === 0;
console.log(JSON.stringify(result, null, 2));

if (result.failures.length) {
	process.exitCode = 1;
}
