import { expect, type Page, type Route, test } from "@playwright/test";

type Scenario = "success" | "empty" | "error";

const screens = [
	{
		id: 101,
		name: "质量运营大屏",
		description: "质量主题域发布大屏",
		domainId: "quality",
		classification: "INTERNAL",
		publishedVersionNo: 3,
		publishedAt: "2026-08-20T08:00:00Z",
		canRead: true,
	},
	{
		id: 102,
		name: "经营态势大屏",
		description: "未归类发布大屏",
		domainId: null,
		classification: "SECRET",
		publishedVersionNo: 2,
		publishedAt: "2026-08-20T08:10:00Z",
		canRead: true,
	},
];

function platformEnvelope(data: unknown) {
	return JSON.stringify({ status: 200, data, message: "OK" });
}

async function json(route: Route, data: unknown, envelope = false, status = 200) {
	await route.fulfill({
		status,
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
						username: "sprint96-viewer",
						fullName: "Sprint 96 Viewer",
						roles: ["ROLE_ANALYST"],
						permissions: ["read"],
						enabled: true,
					},
					userToken: { accessToken: "sprint96-mock-token" },
				},
				version: 0,
			}),
		);
		localStorage.setItem("dts.platform.session.loginTs", now);
		localStorage.setItem("dts.platform.session.lastActivity", now);
	});
}

async function installApis(page: Page, scenario: Scenario) {
	await page.route(/^https?:\/\/[^/]+\/(?:bi\/api\/|api\/)/, async (route) => {
		const url = new URL(route.request().url());
		if (url.pathname === "/api/session/status") {
			return json(route, { authenticated: true, remainingSeconds: 3600 }, true);
		}
		if (url.pathname === "/api/menu/tree") return json(route, [], true);
		if (url.pathname === "/api/catalog/domains/tree") {
			return json(route, [{ id: "quality", name: "质量管理", children: [] }], true);
		}
		if (url.pathname === "/api/reports/visit") return json(route, { recorded: true }, true);
		if (url.pathname === "/bi/api/screens") {
			expect(url.searchParams.get("publishedOnly")).toBe("true");
			if (scenario === "error") return json(route, { message: "catalog unavailable" }, false, 503);
			return json(route, scenario === "empty" ? [] : screens);
		}
		const detailMatch = url.pathname.match(/^\/bi\/api\/screens\/(\d+)$/);
		if (detailMatch) {
			expect(url.searchParams.get("mode")).toBe("published");
			expect(url.searchParams.get("fallbackDraft")).toBe("false");
			const screen = screens.find((item) => String(item.id) === detailMatch[1]) ?? screens[0];
			return json(route, {
				...screen,
				sourceMode: "published",
				width: 1920,
				height: 1080,
				backgroundColor: "#08121f",
				theme: "dark-command",
				components: [],
				globalVariables: [],
			});
		}
		return json(route, url.pathname.startsWith("/bi/api/") ? {} : {}, !url.pathname.startsWith("/bi/api/"));
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

test("browses published screens by domain, deep-links a leaf, and renders published runtime", async ({ page }, testInfo) => {
	await installIdentity(page);
	await installApis(page, "success");
	const failures = collectFailures(page);

	await page.goto("/bi/portal");
	await expect(page.getByTestId("data-portal-page")).toBeVisible();
	await expect(page.getByText("质量管理", { exact: true })).toBeVisible();
	await expect(page.getByText("质量运营大屏", { exact: true })).toBeVisible();
	await expect(page).toHaveURL(/\/bi\/portal\/101$/);

	const runtime = page.getByTestId("data-portal-runtime");
	await expect(runtime).toHaveAttribute("src", /mode=published/);
	await expect(runtime).toHaveAttribute("src", /embed=1/);
	await expect(runtime.contentFrame().getByTestId("analytics-screen-preview")).toBeVisible();

	await page.getByText("经营态势大屏", { exact: true }).click();
	await expect(page).toHaveURL(/\/bi\/portal\/102$/);
	await expect(runtime).toHaveAttribute("src", /\/bi\/screens\/102\/preview/);

	await page.screenshot({ path: testInfo.outputPath("data-portal-desktop.png"), fullPage: true });
	expect(failures).toEqual({ consoleErrors: [], pageErrors: [], failedResponses: [] });
});

test("shows an actionable empty state at a narrow viewport", async ({ page }, testInfo) => {
	await page.setViewportSize({ width: 820, height: 720 });
	await installIdentity(page);
	await installApis(page, "empty");
	const failures = collectFailures(page);

	await page.goto("/bi/portal");
	await expect(page.getByTestId("data-portal-empty")).toBeVisible();
	await expect(page.getByRole("link", { name: "大屏管理" })).toBeVisible();
	await page.screenshot({ path: testInfo.outputPath("data-portal-empty-narrow.png"), fullPage: true });
	expect(failures).toEqual({ consoleErrors: [], pageErrors: [], failedResponses: [] });
});

test("shows a retryable directory error without rendering a runtime", async ({ page }) => {
	await installIdentity(page);
	await installApis(page, "error");

	await page.goto("/bi/portal");
	await expect(page.getByTestId("data-portal-error")).toBeVisible();
	await expect(page.getByRole("button", { name: "重新加载" })).toBeVisible();
	await expect(page.getByTestId("data-portal-runtime")).toHaveCount(0);
});
