import fs from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";

const require = createRequire(path.resolve(process.cwd(), "package.json"));
const { chromium } = require("@playwright/test");

const baseUrl = process.env.DTS_WEBAPP_URL || "http://127.0.0.1:3001";
const outDir = process.env.DTS_SMOKE_OUT || path.resolve("worklog/v2.2.3/sprint-25-202605/it/evidence/20260501-local/ui-screenshots");

const routes = [
	{ name: "semantic-overview", path: "/metrics/semantic", text: "语义建模流程" },
	{ name: "semantic-subjects", path: "/metrics/semantic/subjects", text: "主题域映射" },
	{ name: "semantic-objects", path: "/metrics/semantic/objects", text: "业务对象 Join" },
	{ name: "semantic-metrics", path: "/metrics/semantic/metrics", text: "指标可视化配置" },
	{ name: "semantic-models", path: "/metrics/semantic/models", text: "DWS/ADS 数据集" },
	{ name: "semantic-publish", path: "/metrics/semantic/publish", text: "审核发布与血缘" },
	{ name: "lineage-impact", path: "/catalog/lineage/impact", text: "影响分析" },
	{ name: "lineage-graph", path: "/catalog/lineage/graph", text: "血缘图谱" },
	{ name: "lineage-columns", path: "/catalog/lineage/columns", text: "字段血缘" },
	{ name: "lineage-import", path: "/catalog/lineage/import", text: "血缘导入" },
	{ name: "lineage-diff", path: "/catalog/lineage/diff", text: "快照对比" },
];

fs.mkdirSync(outDir, { recursive: true });

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 960 }, deviceScaleFactor: 1 });

await page.route(`${baseUrl.replace(/\/$/, "")}/api/**`, async (route) => {
	const url = new URL(route.request().url());
	const pathname = url.pathname;
	let body = {};
	if (pathname.endsWith("/session/status")) {
		body = { authenticated: true, remainingSeconds: 3600 };
	} else if (pathname.includes("/catalog/domains/tree")) {
		body = [];
	} else if (pathname.includes("/catalog/datasets")) {
		body = { content: [], totalElements: 0 };
	} else if (pathname.includes("/semantic-modeling")) {
		body = [];
	} else if (pathname.includes("/catalog/lineage")) {
		body = { nodes: [], edges: [], columnLineages: [], nodeCount: 0, edgeCount: 0 };
	} else if (pathname.includes("/workbench/audit")) {
		body = { ok: true };
	} else {
		body = [];
	}
	await route.fulfill({
		status: 200,
		contentType: "application/json; charset=utf-8",
		body: JSON.stringify(body),
	});
});

await page.addInitScript(() => {
	window.localStorage.setItem(
		"dts.platform.userStore",
		JSON.stringify({
			state: {
				userInfo: {
					id: "sprint25-ui-smoke",
					username: "sprint25-ui-smoke",
					fullName: "Sprint 25 UI Smoke",
					roles: ["OP_ADMIN"],
					permissions: [],
					enabled: true,
				},
				userToken: {
					authenticated: true,
					accessToken: `dev-access-sprint25-${Date.now()}`,
					refreshToken: `dev-refresh-sprint25-${Date.now()}`,
					tokenExpiresAt: Date.now() + 3600_000,
				},
			},
			version: 0,
		}),
	);
});

const summary = [];
for (const item of routes) {
	const url = `${baseUrl.replace(/\/$/, "")}/#${item.path}`;
	await page.goto(url, { waitUntil: "domcontentloaded" });
	await page.waitForTimeout(1200);
	const matched = await page.getByText(item.text, { exact: false }).first().isVisible().catch(() => false);
	const screenshot = path.join(outDir, `${item.name}.png`);
	await page.screenshot({ path: screenshot, fullPage: true });
	summary.push({ ...item, url, matched, screenshot });
	if (!matched) {
		throw new Error(`Expected text not visible on ${item.path}: ${item.text}`);
	}
}

await browser.close();

const summaryPath = path.join(outDir, "summary.json");
fs.writeFileSync(summaryPath, `${JSON.stringify({ baseUrl, routes: summary }, null, 2)}\n`);
console.log(`Sprint-25 UI screenshot smoke passed: ${summaryPath}`);
