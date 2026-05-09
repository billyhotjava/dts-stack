import { expect, test, type Page, type Route } from "@playwright/test";

const SCREEN_ID = "industrial-editor-e2e";

type ScreenComponent = {
	id: string;
	type: string;
	name: string;
	x: number;
	y: number;
	width: number;
	height: number;
	zIndex: number;
	locked: boolean;
	visible: boolean;
	config: Record<string, unknown>;
};

function component(overrides: Partial<ScreenComponent> = {}): ScreenComponent {
	return {
		id: "hero-title",
		type: "title",
		name: "核心指标标题",
		x: 100,
		y: 80,
		width: 200,
		height: 80,
		zIndex: 1,
		locked: false,
		visible: true,
		config: {
			text: "核心指标标题",
			fontSize: 28,
			color: "#ffffff",
		},
		...overrides,
	};
}

function screenDetail(components: ScreenComponent[] = [component()]) {
	return {
		id: SCREEN_ID,
		name: "工业级编辑器回归大屏",
		description: "",
		width: 800,
		height: 450,
		theme: "legacy-dark",
		backgroundColor: "#0f172a",
		backgroundImage: null,
		classification: "INTERNAL",
		components,
		globalVariables: [],
		schemaVersion: 1,
		sourceMode: "draft",
		updatedAt: "2026-05-09T08:00:00.000Z",
		canRead: true,
		canEdit: true,
		canPublish: true,
		canManage: true,
		canDelete: true,
		isOwner: true,
	};
}

async function fulfillJson(route: Route, body: unknown, status = 200) {
	await route.fulfill({
		status,
		contentType: "application/json",
		body: JSON.stringify(body),
	});
}

async function mockScreenEditorApi(page: Page, options: { lockedByOther?: boolean } = {}) {
	const lockedByOther = options.lockedByOther === true;
	let current = screenDetail();
	let lastUpdatePayload: Record<string, unknown> | null = null;
	const otherLock = {
		active: true,
		mine: false,
		ownerId: "u-other",
		ownerName: "并发编辑者",
		expiresAt: "2026-05-09T09:00:00.000Z",
	};
	const ownLock = {
		active: true,
		mine: true,
		ownerId: "u-current",
		ownerName: "当前用户",
		expiresAt: "2026-05-09T09:00:00.000Z",
	};

	await page.route("**/infra/screen-fonts", (route) => fulfillJson(route, []));
	await page.route(`**/bi/api/screens/${SCREEN_ID}/edit-lock`, (route) =>
		fulfillJson(route, lockedByOther ? otherLock : ownLock),
	);
	await page.route(`**/bi/api/screens/${SCREEN_ID}/edit-lock/acquire`, (route) => {
		if (!lockedByOther) {
			return fulfillJson(route, ownLock);
		}
		return fulfillJson(
			route,
			{
				code: "SCREEN_EDIT_LOCKED",
				message: "当前由并发编辑者持有编辑锁",
				lock: otherLock,
			},
			409,
		);
	});
	await page.route(`**/bi/api/screens/${SCREEN_ID}/edit-lock/heartbeat`, (route) => fulfillJson(route, ownLock));
	await page.route(`**/bi/api/screens/${SCREEN_ID}/edit-lock/release`, (route) =>
		fulfillJson(route, { active: false, mine: false }),
	);
	await page.route(`**/bi/api/screens/${SCREEN_ID}/publish`, (route) =>
		fulfillJson(route, {
			screen: current,
			version: { id: 1, versionNo: 1, createdAt: "2026-05-09T08:30:00.000Z" },
		}),
	);
	await page.route(`**/bi/api/screens/${SCREEN_ID}?**`, async (route) => {
		if (route.request().method() === "PUT") {
			lastUpdatePayload = await route.request().postDataJSON();
			current = {
				...current,
				...lastUpdatePayload,
				updatedAt: "2026-05-09T08:31:00.000Z",
			};
			return fulfillJson(route, current);
		}
		return fulfillJson(route, current);
	});
	await page.route(`**/bi/api/screens/${SCREEN_ID}`, async (route) => {
		if (route.request().method() === "PUT") {
			lastUpdatePayload = await route.request().postDataJSON();
			current = {
				...current,
				...lastUpdatePayload,
				updatedAt: "2026-05-09T08:31:00.000Z",
			};
			return fulfillJson(route, current);
		}
		return fulfillJson(route, current);
	});

	return {
		getLastUpdatePayload: () => lastUpdatePayload,
	};
}

test.describe("screen editor · industrial interaction regression", () => {
	test("dragging a component stays in design-space under 50% zoom", async ({ page }) => {
		const api = await mockScreenEditorApi(page);

		await page.goto(`/#/bi/screens/${SCREEN_ID}/edit`);
		const target = page.getByTestId("analytics-screen-component-hero-title");
		await expect(target).toBeVisible({ timeout: 15_000 });

		await page.locator(".zoom-select").selectOption("50");
		const before = await target.boundingBox();
		expect(before).not.toBeNull();
		await page.mouse.move((before?.x ?? 0) + 40, (before?.y ?? 0) + 30);
		await page.mouse.down();
		await page.mouse.move((before?.x ?? 0) + 90, (before?.y ?? 0) + 30);
		await page.mouse.up();

		await page.getByTestId("analytics-screen-primary-action-button").click();
		await expect.poll(() => api.getLastUpdatePayload()).not.toBeNull();
		const payload = api.getLastUpdatePayload();
		const moved = (payload?.components as ScreenComponent[] | undefined)?.find((item) => item.id === "hero-title");
		expect(moved?.x).toBeGreaterThanOrEqual(185);
	});

	test("readonly edit lock disables canvas mutation affordances", async ({ page }) => {
		await mockScreenEditorApi(page, { lockedByOther: true });

		await page.goto(`/#/bi/screens/${SCREEN_ID}/edit`);
		await expect(page.getByTestId("analytics-screen-canvas-readonly-banner")).toBeVisible({ timeout: 15_000 });
		await expect(page.getByText("编辑锁提示：当前由 并发编辑者 编辑中")).toBeVisible();
		await expect(page.getByTestId("analytics-screen-library-readonly-note")).toBeVisible();
		await expect(page.getByTestId("analytics-screen-primary-action-button")).toBeDisabled();
		await expect(page.getByTestId("analytics-screen-canvas")).toHaveAttribute("aria-readonly", "true");
		await expect(page.getByTestId("analytics-screen-component-hero-title")).toHaveAttribute("aria-disabled", "true");
		await expect(page.getByTestId("analytics-screen-property-readonly-note")).toBeVisible();
	});

	test("local recovery draft can be restored before editing continues", async ({ page }) => {
		await mockScreenEditorApi(page);
		await page.addInitScript(({ screenId }) => {
			window.localStorage.setItem(`dts.analytics.screenDesigner.recovery.v1:${screenId}`, JSON.stringify({
				screenId,
				savedAt: Date.now(),
				config: {
					id: screenId,
					name: "恢复后的本地草稿",
					width: 800,
					height: 450,
					theme: "legacy-dark",
					backgroundColor: "#0f172a",
					classification: "INTERNAL",
					components: [{
						id: "hero-title",
						type: "title",
						name: "本地恢复标题",
						x: 160,
						y: 120,
						width: 240,
						height: 80,
						zIndex: 1,
						locked: false,
						visible: true,
						config: { text: "本地恢复标题", color: "#ffffff", fontSize: 30 },
					}],
				},
			}));
		}, { screenId: SCREEN_ID });

		await page.goto(`/#/bi/screens/${SCREEN_ID}/edit`);
		await expect(page.getByText("发现未保存的本地草稿")).toBeVisible({ timeout: 15_000 });
		await page.getByRole("button", { name: "恢复草稿" }).click();
		await expect(page.getByText("本地恢复标题").first()).toBeVisible();
	});

	test("preview renders the same draft component surface as editor data", async ({ page }) => {
		await mockScreenEditorApi(page);

		await page.goto(`/#/bi/screens/${SCREEN_ID}/preview`);
		await expect(page.getByTestId("analytics-screen-preview")).toBeVisible({ timeout: 15_000 });
		await expect(page.getByText("核心指标标题").first()).toBeVisible();
	});
});
