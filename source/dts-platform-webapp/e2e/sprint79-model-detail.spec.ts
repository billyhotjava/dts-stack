import fs from "node:fs";
import path from "node:path";
import { expect, type Locator, type Page, test } from "@playwright/test";

const evidenceRoot = path.resolve(
	process.cwd(),
	"../../worklog/v2.2.3/sprint-79-202607-modeling-workspace-convergence/it/evidence",
);
const it02EvidenceDir = path.join(evidenceRoot, "IT-02");
const it03EvidenceDir = path.join(evidenceRoot, "IT-03");
const safeMethods = new Set(["GET", "HEAD", "OPTIONS"]);

type ModelSpecCandidate = {
	id: string;
	name: string;
	fieldCount: number;
	designedReady: boolean;
};

type BrowserFailures = {
	pageErrors: string[];
	requestFailures: string[];
	httpFailures: string[];
	modelingWrites: string[];
};

const installReadOnlyBarrier = async (page: Page): Promise<BrowserFailures> => {
	const failures: BrowserFailures = {
		pageErrors: [],
		requestFailures: [],
		httpFailures: [],
		modelingWrites: [],
	};
	page.on("pageerror", (error) => failures.pageErrors.push(error.message));
	page.on("requestfailed", (request) => {
		const errorText = request.failure()?.errorText ?? "unknown";
		if (errorText === "net::ERR_ABORTED" || errorText === "net::ERR_BLOCKED_BY_CLIENT") return;
		const pathname = new URL(request.url()).pathname;
		failures.requestFailures.push(`${request.method()} ${pathname} ${errorText}`);
	});
	page.on("response", (response) => {
		if (response.status() < 400) return;
		const url = new URL(response.url());
		if (
			url.pathname.startsWith("/api/") ||
			url.pathname.startsWith("/admin/api/") ||
			url.pathname.startsWith("/analytics/api/") ||
			url.pathname.startsWith("/bi/api/")
		) {
			failures.httpFailures.push(`${response.status()} ${response.request().method()} ${url.pathname}`);
		}
	});
	await page.route("**/*", async (route) => {
		const request = route.request();
		const pathname = new URL(request.url()).pathname;
		const isApiRequest =
			pathname.startsWith("/api/") ||
			pathname.startsWith("/admin/api/") ||
			pathname.startsWith("/analytics/api/") ||
			pathname.startsWith("/bi/api/");
		if (!isApiRequest || safeMethods.has(request.method())) {
			await route.continue();
			return;
		}
		failures.modelingWrites.push(`${request.method()} ${pathname}`);
		await route.abort("blockedbyclient");
	});
	return failures;
};

const selectReadableModelSpec = async (page: Page, requireDesignedReady: boolean) =>
	page.evaluate(
		async ({
			requireDesignedReady,
		}): Promise<{
			candidate: ModelSpecCandidate | null;
			listCount: number;
			reason: string;
		}> => {
			const unwrap = (payload: unknown): unknown => {
				if (payload && typeof payload === "object" && "data" in payload) {
					return (payload as { data: unknown }).data;
				}
				return payload;
			};
			const asArray = (payload: unknown): unknown[] => {
				const unwrapped = unwrap(payload);
				if (Array.isArray(unwrapped)) return unwrapped;
				if (unwrapped && typeof unwrapped === "object" && "content" in unwrapped) {
					const content = (unwrapped as { content: unknown }).content;
					if (Array.isArray(content)) return content;
				}
				return [];
			};

			const listResponse = await fetch("/api/modeling/model-specs", {
				headers: { Accept: "application/json" },
			});
			if (!listResponse.ok) throw new Error(`ModelSpec list GET failed: ${listResponse.status}`);
			const models = asArray(await listResponse.json());
			for (const value of models) {
				if (!value || typeof value !== "object" || !("id" in value)) continue;
				const id = String((value as { id: unknown }).id || "").trim();
				if (!id) continue;
				const [detailResponse, gatesResponse] = await Promise.all([
					fetch(`/api/modeling/model-specs/${encodeURIComponent(id)}`, {
						headers: { Accept: "application/json" },
					}),
					fetch(`/api/modeling/model-specs/${encodeURIComponent(id)}/stage-gates`, {
						headers: { Accept: "application/json" },
					}),
				]);
				if (!detailResponse.ok || !gatesResponse.ok) continue;
				const detail = unwrap(await detailResponse.json());
				const gates = asArray(await gatesResponse.json());
				if (!detail || typeof detail !== "object") continue;
				const model = detail as {
					id?: unknown;
					name?: unknown;
					compatibilityMode?: unknown;
					status?: unknown;
					fields?: unknown;
				};
				const fields = Array.isArray(model.fields) ? model.fields : [];
				const designedReady = gates.some(
					(gate) =>
						Boolean(gate) &&
						typeof gate === "object" &&
						(gate as { stage?: unknown }).stage === "DESIGNED" &&
						(gate as { status?: unknown }).status === "READY",
				);
				if (
					model.compatibilityMode !== "CANONICAL" ||
					model.status !== "DRAFT" ||
					fields.length === 0 ||
					(requireDesignedReady && !designedReady)
				)
					continue;
				return {
					candidate: {
						id: String(model.id || id),
						name: String(model.name || id),
						fieldCount: fields.length,
						designedReady,
					},
					listCount: models.length,
					reason: "",
				};
			}
			return {
				candidate: null,
				listCount: models.length,
				reason:
					models.length === 0
						? "真实租户没有 ModelSpec"
						: requireDesignedReady
							? "真实租户没有 CANONICAL、有字段且 DESIGNED=READY 的代表 ModelSpec"
							: "真实租户没有 CANONICAL 且字段非空的代表 ModelSpec",
			};
		},
		{ requireDesignedReady },
	);

const expectDrawerActionUnobscured = async (drawer: Locator, allowedLabels: string[]) => {
	const action = drawer.locator(".ant-drawer-extra").getByRole("button").first();
	await expect(action).toHaveCount(1);
	await expect(action).toBeVisible();
	await expect(action).toBeEnabled();
	await expect(action).toBeInViewport();
	const actionState = await action.evaluate((button) => {
		const rect = button.getBoundingClientRect();
		return { label: button.textContent?.trim() || "", hasArea: rect.width > 0 && rect.height > 0 };
	});
	expect(actionState.hasArea).toBe(true);
	expect(allowedLabels).toContain(actionState.label);
	await expect
		.poll(
			() =>
				action.evaluate((button) => {
					const rect = button.getBoundingClientRect();
					const hit = document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2);
					return Boolean(hit && (hit === button || button.contains(hit)));
				}),
			{ message: `Drawer 主操作“${actionState.label}”被遮罩或其他元素覆盖` },
		)
		.toBe(true);
};

const assertCleanReadOnlyJourney = (failures: BrowserFailures) => {
	expect(failures.pageErrors).toEqual([]);
	expect(failures.requestFailures).toEqual([]);
	expect(failures.httpFailures).toEqual([]);
	expect(failures.modelingWrites, "验收写屏障不得捕获任何 API 写请求").toEqual([]);
};

const workspaceParams = (page: Page) => {
	const hash = new URL(page.url()).hash;
	const queryIndex = hash.indexOf("?");
	return new URLSearchParams(queryIndex >= 0 ? hash.slice(queryIndex + 1) : "");
};

test.beforeAll(() => {
	fs.mkdirSync(it02EvidenceDir, { recursive: true });
	fs.mkdirSync(it03EvidenceDir, { recursive: true });
});

test("F2 real ModelSpec renders one logical canvas and compact field table without writes", async ({
	page,
	browser,
}) => {
	const failures = await installReadOnlyBarrier(page);
	await page.setViewportSize({ width: 1440, height: 960 });
	await page.goto("/#/modeling/workbench?module=models&workspaceView=model-specs");
	await expect(page.getByTestId("modeling-workspace-shell")).toBeVisible();

	const selection = await selectReadableModelSpec(page, false);
	expect(selection.candidate, `${selection.reason}；列表返回 ${selection.listCount} 条`).not.toBeNull();
	const candidate = selection.candidate as ModelSpecCandidate;
	test.info().annotations.push(
		{
			type: "model-spec-state",
			description: `fields=${candidate.fieldCount}, designedReady=${candidate.designedReady}`,
		},
		{ type: "browser", description: browser.version() },
	);

	await page.goto(`/#/modeling/models/${encodeURIComponent(candidate.id)}?activeStage=logical`);
	const detailPage = page.getByTestId("model-spec-detail-page");
	await expect(detailPage).toBeVisible();
	await expect(page.getByTestId("model-spec-editor-canvas")).toBeVisible();
	await expect(page.getByTestId("model-spec-logical-canvas")).toBeVisible();
	await expect(page.getByText("单页逻辑设计", { exact: true })).toBeVisible();
	await page.getByRole("tab", { name: "字段设计", exact: true }).click();
	const fieldsTable = page.getByRole("table", { name: "模型字段设计" });
	await expect(fieldsTable).toBeVisible();
	await expect(fieldsTable.locator("tbody tr.ant-table-row")).toHaveCount(candidate.fieldCount);
	for (const header of ["技术编码", "业务名称", "数据类型", "字段作用", "允许为空", "维度属性编码"]) {
		await expect(fieldsTable.getByRole("columnheader", { name: header, exact: true })).toBeVisible();
	}
	await detailPage.screenshot({
		path: path.join(it02EvidenceDir, "model-detail-single-page-and-compact-fields.png"),
		mask: [
			detailPage.getByRole("heading").first(),
			detailPage.locator('[data-testid^="model-spec-gate-"]'),
			fieldsTable.locator("tbody"),
		],
	});
	assertCleanReadOnlyJourney(failures);
	console.log(
		`F2_LOGICAL_READONLY listCount=${selection.listCount} fields=${candidate.fieldCount} browser=${browser.version()} writes=0`,
	);
});

test("F2 workbench restores model context and preserves unsaved input across drawer routing", async ({
	page,
	browser,
}) => {
	const failures = await installReadOnlyBarrier(page);
	await page.setViewportSize({ width: 1440, height: 960 });
	await page.goto("/#/modeling/workbench?module=models&workspaceView=model-specs");
	await expect(page.getByTestId("modeling-workspace-shell")).toBeVisible();

	const selection = await selectReadableModelSpec(page, false);
	expect(selection.candidate, `${selection.reason}；列表返回 ${selection.listCount} 条`).not.toBeNull();
	const candidate = selection.candidate as ModelSpecCandidate;
	await page.goto(
		`/#/modeling/workbench?module=models&workspaceView=model-specs&assetKind=model&assetId=${encodeURIComponent(candidate.id)}&activeStage=logical`,
	);

	const detailPage = page.getByTestId("model-spec-detail-page");
	await expect(detailPage).toBeVisible();
	await expect(page.getByTestId("model-spec-editor-canvas")).toBeVisible();
	await expect.poll(() => workspaceParams(page).get("planId") || "").not.toBe("");
	expect(workspaceParams(page).get("assetId")).toBe(candidate.id);
	expect(workspaceParams(page).get("activeStage")).toBe("logical");

	await page.reload();
	await expect(detailPage).toBeVisible();
	expect(workspaceParams(page).get("assetId")).toBe(candidate.id);

	const description = page.getByPlaceholder("说明模型服务的分析主题、报表或业务问题");
	await expect(description).toBeEnabled();
	const unsavedMarker = "未保存状态仅用于路由回归";
	await description.fill(unsavedMarker);
	await page.getByRole("button", { name: "发布结果", exact: true }).click();
	await expect(page.getByRole("dialog").filter({ has: page.getByText("发布结果", { exact: true }) })).toBeVisible();
	expect(workspaceParams(page).get("activeStage")).toBe("physical");
	await page.getByRole("dialog").getByRole("button", { name: "Close" }).click();
	await expect(page.getByRole("dialog")).toBeHidden();
	await expect(description).toHaveValue(unsavedMarker);
	expect(workspaceParams(page).get("activeStage")).toBe("logical");

	await detailPage.screenshot({
		path: path.join(it02EvidenceDir, "model-workbench-asset-context.png"),
		mask: [
			detailPage.getByRole("heading").first(),
			detailPage.locator(".ant-form"),
			detailPage.locator('[data-testid^="model-spec-gate-"]'),
		],
	});
	await page.getByRole("button", { name: "返回逻辑模型", exact: true }).click();
	await expect(page.getByTestId("model-center-page")).toBeVisible();
	expect(workspaceParams(page).get("module")).toBe("models");
	expect(workspaceParams(page).get("workspaceView")).toBe("model-specs");
	expect(workspaceParams(page).has("assetKind")).toBe(false);
	expect(workspaceParams(page).has("assetId")).toBe(false);
	expect(workspaceParams(page).has("activeStage")).toBe(false);

	assertCleanReadOnlyJourney(failures);
	console.log(`F2_WORKBENCH_CONTEXT listCount=${selection.listCount} browser=${browser.version()} writes=0`);
});

test("F2 implementation and release drawers require a designed model and a usable primary action", async ({
	page,
	browser,
}) => {
	const failures = await installReadOnlyBarrier(page);
	await page.setViewportSize({ width: 1440, height: 960 });
	await page.goto("/#/modeling/workbench?module=models&workspaceView=model-specs");
	await expect(page.getByTestId("modeling-workspace-shell")).toBeVisible();

	const selection = await selectReadableModelSpec(page, true);
	expect(selection.candidate, `${selection.reason}；列表返回 ${selection.listCount} 条`).not.toBeNull();
	const candidate = selection.candidate as ModelSpecCandidate;
	await page.goto(`/#/modeling/models/${encodeURIComponent(candidate.id)}?activeStage=logical`);
	await expect(page.getByRole("button", { name: "数据实现", exact: true })).toBeEnabled();
	await page.getByRole("button", { name: "数据实现", exact: true }).click();

	const implementationDrawer = page.getByRole("dialog").filter({ has: page.getByText("数据实现", { exact: true }) });
	await expect(implementationDrawer).toBeVisible();
	await expect(implementationDrawer.getByTestId("model-spec-implementation-stage")).toBeVisible();
	await expectDrawerActionUnobscured(implementationDrawer, ["配置数据实现", "验证实现"]);
	await implementationDrawer.screenshot({
		path: path.join(it03EvidenceDir, "model-detail-implementation-drawer.png"),
		mask: [implementationDrawer.getByText(candidate.name, { exact: false })],
	});
	await implementationDrawer.getByRole("button", { name: "Close" }).click();
	await expect(implementationDrawer).toBeHidden();

	await page.getByRole("button", { name: "发布结果", exact: true }).click();
	const releaseDrawer = page.getByRole("dialog").filter({ has: page.getByText("发布结果", { exact: true }) });
	await expect(releaseDrawer).toBeVisible();
	await expect(releaseDrawer.getByTestId("model-spec-physical-stage")).toBeVisible();
	await expectDrawerActionUnobscured(releaseDrawer, ["配置数据实现", "验证实现", "构建与提交上线"]);
	await releaseDrawer.screenshot({
		path: path.join(it03EvidenceDir, "model-detail-release-result-drawer.png"),
		mask: [releaseDrawer.getByText(candidate.name, { exact: false })],
	});

	assertCleanReadOnlyJourney(failures);
	console.log(`F2_DRAWER_ACCEPTANCE listCount=${selection.listCount} browser=${browser.version()} writes=0`);
});
