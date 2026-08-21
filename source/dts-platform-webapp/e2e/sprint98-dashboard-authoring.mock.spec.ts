import { expect, type Page, type Route, test } from "@playwright/test";

type MockCard = {
	id: number;
	name: string;
	type: "question" | "analysis";
	display: string;
	lifecycle_status: "DRAFT" | "PUBLISHED";
	published_revision_id: number | null;
};

const cards: MockCard[] = [
	{
		id: 2,
		name: "历史问题卡",
		type: "question",
		display: "table",
		lifecycle_status: "DRAFT",
		published_revision_id: null,
	},
	{
		id: 11,
		name: "项目综合分析",
		type: "analysis",
		display: "bar",
		lifecycle_status: "PUBLISHED",
		published_revision_id: 91,
	},
	{
		id: 12,
		name: "项目趋势分析",
		type: "analysis",
		display: "line",
		lifecycle_status: "PUBLISHED",
		published_revision_id: 92,
	},
];

let orderedCards: Array<Record<string, unknown>> = [];
const requestSequence: string[] = [];
let orgRequestCount = 0;
let roleRequestCount = 0;

function cardById(id: number) {
	return cards.find((card) => card.id === id) ?? null;
}

function initialOrderedCards() {
	return [
		{
			id: 1,
			card_id: 2,
			row: 0,
			col: 0,
			size_x: 6,
			size_y: 4,
			parameter_mappings: [],
			visualization_settings: {},
			card: cardById(2),
		},
	];
}

function dashboardDetail() {
	return {
		id: 22,
		name: "项目经营驾驶舱",
		description: "Sprint 98 看板编排验收",
		collection_id: null,
		lifecycle_status: "DRAFT",
		published_revision_id: null,
		registration_status: "NOT_REGISTERED",
		version_no: 0,
		parameters: [],
		ordered_cards: orderedCards,
	};
}

function platformEnvelope(data: unknown) {
	return JSON.stringify({ status: 200, data, message: "OK" });
}

async function json(route: Route, data: unknown, envelope = false) {
	await route.fulfill({
		status: 200,
		contentType: "application/json",
		body: envelope ? platformEnvelope(data) : JSON.stringify(data),
	});
}

async function installIdentity(page: Page) {
	await page.addInitScript(() => {
		const now = String(Date.now());
		localStorage.setItem(
			"dts.platform.userStore",
			JSON.stringify({
				state: {
					userInfo: {
						username: "sprint98-maintainer",
						fullName: "Sprint 98 Maintainer",
						roles: ["ROLE_OP_ADMIN", "ROLE_ANALYST"],
						permissions: ["read", "write", "export"],
						enabled: true,
					},
					userToken: { accessToken: "sprint98-mock-token" },
				},
				version: 0,
			}),
		);
		localStorage.setItem("dts.platform.session.loginTs", now);
		localStorage.setItem("dts.platform.session.lastActivity", now);
	});
}

function queryResult() {
	return {
		status: "completed",
		row_count: 2,
		running_time: 12,
		data: {
			rows: [
				["研发中心", 8],
				["生产中心", 5],
			],
			cols: [
				{ name: "department", display_name: "责任部门", base_type: "type/Text" },
				{ name: "project_count", display_name: "项目数", base_type: "type/Integer" },
			],
		},
	};
}

async function installApis(page: Page) {
	await page.route("**/api/**", async (route) => {
		const request = route.request();
		const path = new URL(request.url()).pathname;

		if (path === "/api/session/status") return json(route, { authenticated: true, remainingSeconds: 3600 }, true);
		if (path === "/api/menu/tree") return json(route, [], true);
		if (path === "/api/directory/orgs") {
			orgRequestCount += 1;
			return json(
				route,
				[
					{
						id: 1,
						name: "集团总部",
						deptCode: "HQ",
						children: [{ id: 2, name: "项目管理部", deptCode: "PMO", children: [] }],
					},
				],
				true,
			);
		}
		if (path === "/api/directory/roles") {
			roleRequestCount += 1;
			return json(route, [{ id: "analyst", name: "ROLE_ANALYST", description: "分析人员", source: "builtin" }], true);
		}
		if (path === "/bi/api/collection") return json(route, []);
		if (path === "/bi/api/card" && request.method() === "GET") return json(route, cards);
		if (path === "/bi/api/dashboard/22" && request.method() === "GET") return json(route, dashboardDetail());
		if (path === "/bi/api/dashboard/save") {
			requestSequence.push("save");
			const body = JSON.parse(request.postData() ?? "{}") as { dashcards?: Array<Record<string, unknown>> };
			orderedCards = (body.dashcards ?? []).map((dashcard, index) => {
				const cardId = Number(dashcard.card_id);
				return { ...dashcard, id: index + 1, card_id: cardId, card: cardById(cardId) };
			});
			return json(route, dashboardDetail());
		}
		if (path === "/bi/api/dashboard/22/validate") {
			requestSequence.push("validate");
			const audience = JSON.parse(request.postData() ?? "{}") as { deptCodes?: string[]; roleCodes?: string[] };
			const hasAudience = Boolean(audience.deptCodes?.length || audience.roleCodes?.length);
			const invalidIndex = orderedCards.findIndex(
				(dashcard) => cardById(Number(dashcard.card_id))?.type !== "analysis",
			);
			const blockers = !hasAudience
				? [{ code: "DASHBOARD_AUDIENCE_REQUIRED", path: "audience", message: "audience required" }]
				: invalidIndex >= 0
					? [
							{
								code: "DASHBOARD_ANALYSIS_REQUIRED",
								path: `components[${invalidIndex}]`,
								message: "component must reference a governed analysis",
							},
						]
					: [];
			return json(route, {
				valid: blockers.length === 0,
				blockers,
				warnings: [],
				dependencySnapshot: { dashboardId: 22, componentCount: orderedCards.length },
			});
		}
		if (path === "/bi/api/dashboard/22/publish") {
			requestSequence.push("publish");
			return json(route, {
				dashboardId: 22,
				revisionId: 101,
				versionNo: 1,
				lifecycleStatus: "PUBLISHED",
				registrationStatus: "PENDING_REGISTRATION",
				contractChecksum: "dashboard-sprint98",
				dependencySnapshot: { componentCount: orderedCards.length },
				publishedAt: "2026-08-21T08:00:00Z",
			});
		}
		if (/^\/bi\/api\/dashboard\/22\/dashcard\/\d+\/card\/\d+\/query$/.test(path)) return json(route, queryResult());
		if (/^\/bi\/api\/card\/\d+\/query$/.test(path)) return json(route, queryResult());
		return json(route, path.startsWith("/bi/api/") ? {} : {}, !path.startsWith("/bi/api/"));
	});
}

function collectFailures(page: Page) {
	const consoleErrors: string[] = [];
	const pageErrors: string[] = [];
	const failedResponses: string[] = [];
	page.on("console", (message) => {
		if (message.type() === "error") consoleErrors.push(message.text());
	});
	page.on("pageerror", (error) => pageErrors.push(error.message));
	page.on("response", (response) => {
		if (response.status() >= 400) failedResponses.push(`${response.status()} ${new URL(response.url()).pathname}`);
	});
	return { consoleErrors, pageErrors, failedResponses };
}

test("authors, repairs and publishes a governed dashboard from the visual composer", async ({ page }, testInfo) => {
	orderedCards = initialOrderedCards();
	requestSequence.length = 0;
	orgRequestCount = 0;
	roleRequestCount = 0;
	await installIdentity(page);
	await installApis(page);
	const failures = collectFailures(page);

	await page.goto("/#/bi/dashboards/22/edit");
	await expect(page.getByTestId("analytics-dashboard-editor")).toBeVisible();
	await expect(page.getByText("已发布分析", { exact: true }).first()).toBeVisible();
	await expect(page.getByText("12 列编排画布", { exact: true })).toBeVisible();
	await expect(page.getByText("组件属性", { exact: true })).toBeVisible();
	await expect(page.getByText("需替换", { exact: true }).first()).toBeVisible();

	const source = page.locator(".mb-dashboard-analysis-library__item").filter({ hasText: "项目综合分析" });
	await source.dragTo(page.locator(".react-grid-layout"), { targetPosition: { x: 420, y: 160 } });
	const draggedCard = page.locator('[data-testid^="dashboard-card-"]').filter({ hasText: "项目综合分析" });
	await expect(draggedCard).toBeVisible();
	await expect(page.getByRole("spinbutton", { name: "组件宽" })).toHaveValue("6");
	await page.getByRole("spinbutton", { name: "组件宽" }).fill("5");
	await page.getByRole("button", { name: "保存草稿" }).click();
	await expect.poll(() => orderedCards.find((card) => card.card_id === 11)?.size_x).toBe(5);
	await page.reload();
	await expect(page.getByTestId("analytics-dashboard-editor")).toBeVisible();
	await page.locator('[data-testid^="dashboard-card-"]').filter({ hasText: "项目综合分析" }).getByRole("button").first().click();
	await expect(page.getByRole("spinbutton", { name: "组件宽" })).toHaveValue("5");
	expect(orgRequestCount).toBe(2);
	expect(roleRequestCount).toBe(2);
	requestSequence.length = 0;

	await page.getByRole("button", { name: /^发\s*布$/ }).click();
	const drawer = page.locator(".ant-drawer").filter({ hasText: "发布仪表板" });
	await expect(drawer.getByText("请选择至少一个可见部门或可见角色。", { exact: true })).toBeVisible();
	expect(requestSequence).toEqual(["save"]);
	await expect(drawer.getByRole("button", { name: "重新校验" })).toBeDisabled();

	await drawer.locator(".ant-select").first().click();
	await page.getByText("集团总部 / 项目管理部", { exact: true }).last().click();
	await drawer.getByRole("button", { name: "重新校验" }).click();
	await expect(
		drawer.getByText("组件“历史问题卡”不是已发布的治理分析，请替换后重新校验。", { exact: true }),
	).toBeVisible();
	expect(requestSequence.slice(0, 2)).toEqual(["save", "validate"]);
	await drawer.getByRole("button", { name: "Close" }).click();

	const legacyCard = page.locator('[data-testid^="dashboard-card-"]').filter({ hasText: "历史问题卡" });
	await legacyCard.getByRole("button", { name: "替换分析" }).click();
	const picker = page.locator(".ant-modal").filter({ hasText: "替换为已发布分析" });
	await picker.getByRole("radio").check();
	await picker.getByRole("button", { name: /添加/ }).click();
	await expect(picker).toBeHidden();
	await expect(page.locator('[data-testid^="dashboard-card-"]').filter({ hasText: "项目趋势分析" })).toBeVisible();
	await expect(page.getByText("已发布分析", { exact: true }).last()).toBeVisible();
	const canonicalWidth = page.getByRole("spinbutton", { name: "组件宽" });
	await expect(canonicalWidth).toHaveValue("6");

	await page.screenshot({ path: testInfo.outputPath("dashboard-composer-1366x768.png"), fullPage: true });
	await page.setViewportSize({ width: 768, height: 900 });
	await expect(page.getByText("12 列编排画布", { exact: true })).toBeVisible();
	const noNarrowOverflow = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
	expect(noNarrowOverflow).toBe(true);
	await expect(canonicalWidth).toHaveValue("6");
	await expect.poll(async () => {
		const narrowCardBoxes = await page.locator('[data-testid^="dashboard-card-"]').evaluateAll((elements) =>
			elements.map((element) => {
				const box = element.getBoundingClientRect();
				return { top: box.top, bottom: box.bottom };
			}),
		);
		return narrowCardBoxes[1].top >= narrowCardBoxes[0].bottom - 1;
	}).toBe(true);
	await page.screenshot({ path: testInfo.outputPath("dashboard-composer-768x900.png"), fullPage: true });
	await page.setViewportSize({ width: 1366, height: 768 });

	const beforeSecondPublish = requestSequence.length;
	await page.getByRole("button", { name: /^发\s*布$/ }).click();
	await expect(drawer.getByText("校验通过，可以发布", { exact: true })).toBeVisible();
	expect(requestSequence.slice(beforeSecondPublish, beforeSecondPublish + 2)).toEqual(["save", "validate"]);
	await drawer.getByRole("button", { name: "确认发布" }).click();
	await expect(page.getByText("业务入口正在注册", { exact: true })).toBeVisible();
	expect(requestSequence.at(-1)).toBe("publish");
	expect(orgRequestCount).toBe(2);
	expect(roleRequestCount).toBe(2);

	expect(failures.pageErrors).toEqual([]);
	expect(failures.consoleErrors).toEqual([]);
	expect(failures.failedResponses).toEqual([]);
});
