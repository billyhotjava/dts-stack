import { readFileSync } from "node:fs";
import { expect, type Page, test } from "@playwright/test";
import { installSprint79ProductionReadOnlyBarrier } from "./support/sprint79ProductionReadOnly";

type SeedNode = {
	key: string;
	path: string;
	titleKey: string;
	title: string;
	icon?: string;
	externalLink?: string;
	children?: SeedNode[];
};

type StoredAuthState = {
	cookies?: Array<{
		name: string;
		value: string;
		httpOnly?: boolean;
		expires?: number;
	}>;
	origins?: Array<{
		localStorage?: Array<{ name: string; value: string }>;
	}>;
};

type StoredUserStore = {
	state?: Record<string, unknown> & { userToken?: Record<string, unknown> };
};

const seed = JSON.parse(
	readFileSync(
		new URL("../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
		"utf8",
	),
) as { portalNavSections: SeedNode[] };

const storedAuth = JSON.parse(readFileSync(new URL("./.auth/user.json", import.meta.url), "utf8")) as StoredAuthState;
const CONTROLLED_DATE_MODEL_ID = "10000000-0000-0000-0000-000000000001";
const CONTROLLED_PLAN_ID = "20000000-0000-0000-0000-000000000001";
const CONTROLLED_DOMAIN_ID = "30000000-0000-0000-0000-000000000001";
const CONTROLLED_DEFINITION_ID = "40000000-0000-0000-0000-000000000001";
const controlledDateModel = {
	contractVersion: 2,
	id: CONTROLLED_DATE_MODEL_ID,
	planId: CONTROLLED_PLAN_ID,
	domainId: CONTROLLED_DOMAIN_ID,
	modelType: "DIMENSION",
	layer: "DWD",
	warehouseLayerCode: "DWD",
	name: "日期维度表",
	description: "统一日期、月份、周和工作日口径。",
	implementationMode: "DESIGNER_GENERATED",
	materialization: "table",
	businessActivityRef: null,
	consumptionScenario: null,
	grain: { statement: "一个自然日一行", keys: ["date_key"] },
	factShape: null,
	timeSemantics: null,
	generationStrategy: { type: "DATE_DIMENSION", reference: null },
	dimensionProfile: { dimensionCode: "DATE", hierarchies: [], scdPolicy: { type: "TYPE1" }, reuseScope: "TENANT" },
	dimensionDefinitionRef: { dimensionDefinitionId: CONTROLLED_DEFINITION_ID, revision: 1 },
	status: "DRAFT",
	revision: 2,
	checksum: "a".repeat(64),
	createdAt: "2026-08-09T00:00:00Z",
	updatedAt: "2026-08-09T00:00:00Z",
	compatibilityMode: "CANONICAL",
	legacyRefs: null,
	dataMartId: null,
	variantCode: null,
	implementationPolicy: null,
	fields: [
		{ name: "date_key", displayName: "日期ID", dataType: "varchar", nullable: false, role: "KEY" },
		{ name: "full_date", displayName: "日期", dataType: "date", nullable: false, role: "TIME" },
	],
	sourceRefs: [],
	dependsOn: [],
	dimensionRefs: [],
	metricRefs: [],
	standardBindings: [],
};

function menuTree() {
	let menuId = 1;
	const mapNode = (node: SeedNode, sectionKey: string, parentPath = ""): Record<string, unknown> => {
		const path = node.externalLink || `${parentPath}/${node.path}`.replace(/\/{2,}/g, "/");
		return {
			id: menuId++,
			name: node.titleKey,
			displayName: node.title,
			path,
			icon: node.icon,
			deleted: false,
			metadata: JSON.stringify({
				key: node.key,
				sectionKey,
				entryKey: node.key,
				titleKey: node.titleKey,
				title: node.title,
				icon: node.icon,
			}),
			children: (node.children || []).map((child) => mapNode(child, sectionKey, path)),
		};
	};
	return seed.portalNavSections.map((node) => mapNode(node, node.key));
}

async function installLocalAuthenticatedState(page: Page) {
	const baseURL = new URL(process.env.E2E_BASE_URL || "http://127.0.0.1:4182");
	const entries = storedAuth.origins?.flatMap((origin) => origin.localStorage || []) || [];
	const userStoreEntry = entries.find((entry) => entry.name === "dts.platform.userStore");
	if (!userStoreEntry || !storedAuth.cookies?.length) {
		throw new Error("e2e/.auth/user.json must contain an authenticated user store and portal session cookies");
	}
	const now = Date.now();
	const userStore = JSON.parse(userStoreEntry.value) as StoredUserStore;
	userStore.state = {
		...(userStore.state || {}),
		userToken: {
			...(userStore.state?.userToken || {}),
			authenticated: true,
			tokenExpiresAt: now + 60 * 60 * 1000,
		},
	};
	const localEntries = entries.map((entry) =>
		entry.name === "dts.platform.userStore"
			? { ...entry, value: JSON.stringify(userStore) }
			: entry.name.startsWith("dts.platform.session.")
				? { ...entry, value: String(now) }
				: entry,
	);
	await page.context().addCookies(
		storedAuth.cookies.map((cookie) => ({
			name: cookie.name,
			value: cookie.value,
			url: baseURL.origin,
			httpOnly: cookie.httpOnly === true,
			secure: false,
			sameSite: "Lax" as const,
			expires: cookie.expires && cookie.expires * 1000 > now ? cookie.expires : -1,
		})),
	);
	await page.addInitScript((storageEntries) => {
		for (const entry of storageEntries) window.localStorage.setItem(entry.name, entry.value);
	}, localEntries);
}

async function installPlatformReadProxy(page: Page) {
	const platformHost = process.env.E2E_PLATFORM_HOST?.trim();
	if (!platformHost) throw new Error("E2E_PLATFORM_HOST is required for the local read-only regression");
	await page.route(
		(url) => url.pathname.startsWith("/api/"),
		async (route) => {
			const request = route.request();
			const pathname = new URL(request.url()).pathname;
			if (!new Set(["GET", "HEAD", "OPTIONS"]).has(request.method()) && pathname !== "/api/workbench/audit") {
				await route.fallback();
				return;
			}
			const upstream = new URL(request.url());
			upstream.protocol = "https:";
			upstream.hostname = "127.0.0.1";
			upstream.port = "";
			const response = await route.fetch({
				url: upstream.toString(),
				headers: { ...request.headers(), host: platformHost },
			});
			await route.fulfill({ response });
		},
	);
}

async function installControlledMaterializationProxy(page: Page) {
	const writeSequence: string[] = [];
	const unexpectedReads: string[] = [];
	const unexpectedWrites: string[] = [];
	const compileHeaders: Array<{ model: string; implementation: string }> = [];
	const now = "2026-08-09T00:00:00Z";
	const candidateId = "50000000-0000-0000-0000-000000000001";
	await page.route(
		(url) => url.pathname.startsWith("/api/"),
		async (route) => {
			const request = route.request();
			const url = new URL(request.url());
			const { pathname } = url;
			const method = request.method();
			const fulfill = (data: unknown) =>
				route.fulfill({
					status: 200,
					contentType: "application/json",
					body: JSON.stringify({ status: 200, data, message: "OK" }),
				});

			if (pathname === "/api/menu/tree" && method === "GET") {
				await fulfill(menuTree());
				return;
			}
			if (pathname === "/api/session/status" && method === "GET") {
				await fulfill({ authenticated: true, remainingSeconds: 3600 });
				return;
			}
			if (pathname === "/api/keycloak/auth/refresh" && method === "POST") {
				await fulfill({ authenticated: true, expiresIn: 3600, portalExpiresIn: 3600 });
				return;
			}
			if (pathname === "/api/modeling/model-specs" && method === "GET") {
				await fulfill([controlledDateModel]);
				return;
			}
			if (pathname === `/api/modeling/model-specs/${CONTROLLED_DATE_MODEL_ID}` && method === "GET") {
				await fulfill(controlledDateModel);
				return;
			}
			if (pathname === "/api/catalog/domains/tree" && method === "GET") {
				await fulfill([
					{ id: CONTROLLED_DOMAIN_ID, key: "RND_PROJECT", code: "RND_PROJECT", name: "研发项目治理域", children: [] },
				]);
				return;
			}
			if (pathname === "/api/catalog/domains" && method === "GET") {
				await fulfill({
					content: [{ id: CONTROLLED_DOMAIN_ID, code: "RND_PROJECT", name: "研发项目治理域" }],
					totalElements: 1,
				});
				return;
			}
			if (pathname === "/api/modeling/dimension-definitions" && method === "GET") {
				await fulfill([
					{
						id: CONTROLLED_DEFINITION_ID,
						systemCode: "dim_date",
						domainId: CONTROLLED_DOMAIN_ID,
						name: "日期",
						definition: "统一自然日期定义。",
						ownerId: "controlled-owner",
						reuseScope: "TENANT",
						hierarchies: [],
						scopeType: "DOMAIN",
						dataMartId: null,
						attributes: [],
						status: "CURRENT",
						revision: 1,
						checksum: "b".repeat(64),
						usageCount: 1,
						createdAt: now,
						updatedAt: now,
					},
				]);
				return;
			}
			if (pathname === "/api/modeling/metadata-standards" && method === "GET") {
				await fulfill({ content: [] });
				return;
			}
			if (pathname === "/api/modeling/warehouse-layers" && method === "GET") {
				await fulfill([
					{
						code: "DWD",
						name: "明细数据层",
						systemLayerCode: "DWD",
						layerGroup: "COMMON",
						modelTypes: ["DIMENSION", "FACT"],
						kind: "DETAIL",
						responsibility: "公共明细与维度",
						namingPrefixes: ["dim_", "dwd_"],
						optional: false,
						builtin: true,
						deletable: false,
						disabledReason: null,
					},
				]);
				return;
			}
			if (pathname === `/api/modeling/model-specs/${CONTROLLED_DATE_MODEL_ID}/representations` && method === "GET") {
				await fulfill({
					modelSpecId: CONTROLLED_DATE_MODEL_ID,
					modelRevision: 2,
					modelChecksum: controlledDateModel.checksum,
					implementationRevision: 1,
					implementationChecksum: "c".repeat(64),
					ownershipMode: "DESIGNER_GENERATED",
					representationScope: "BUSINESS",
					visualizationCapability: "BUSINESS_VISUAL_EDIT",
					capabilityReasons: [],
					visibleSections: [],
					allowedActions: [],
					logicalModel: {},
					dependencyProjection: [],
					runtimeObservation: { driftStatus: "UNAVAILABLE", verified: false, relationExists: false },
					previewCapability: { available: false, reasons: [] },
					physicalPreview: null,
					driftStatus: "UNAVAILABLE",
					technicalImplementation: null,
					etag: '"controlled-representation"',
				});
				return;
			}
			const lifecycleMatch = pathname.match(/^\/api\/modeling\/model-specs\/([^/]+)\/lifecycle$/);
			if (lifecycleMatch && method === "GET") {
				const modelSpecId = decodeURIComponent(lifecycleMatch[1]);
				await fulfill({
					implementation: {
						id: "40000000-0000-0000-0000-000000000001",
						modelSpecId,
						planId: "controlled-plan",
						revision: 2,
						modelChecksum: "controlled-model-checksum",
						ownership: "DESIGNER_GENERATED",
						projectKey: "system-managed",
						dbtUniqueId: `model.${modelSpecId}`,
						status: "ACTIVE",
						implementationRevision: 1,
						implementationChecksum: "controlled-implementation-checksum",
						inputMode: "GENERATED",
						inputs: [{ generatorType: "DATE_DIMENSION", config: {} }],
						fieldMappings: [],
						settings: { targetPhysicalName: "dim_date", loadStrategy: "FULL", partitionFields: [] },
						materialization: "table",
					},
					artifacts: [],
					events: [],
				});
				return;
			}
			const workspaceMatch = pathname.match(/^\/api\/modeling\/plans\/([^/]+)\/release-candidates\/workspace$/);
			if (workspaceMatch && method === "GET") {
				await fulfill({
					planId: decodeURIComponent(workspaceMatch[1]),
					state: "EMPTY",
					candidate: null,
					evidence: [],
					entryEvidence: [],
					primaryBlocker: null,
					allowedActions: ["CREATE_CANDIDATE"],
					etag: null,
				});
				return;
			}
			const compileMatch = pathname.match(/^\/api\/modeling\/model-specs\/([^/]+)\/lifecycle\/compile$/);
			if (compileMatch && method === "POST") {
				writeSequence.push("compile");
				compileHeaders.push({
					model: request.headers()["if-match"] || "",
					implementation: request.headers()["if-match-implementation"] || "",
				});
				await fulfill({ implementation: {}, event: {}, artifacts: [{ status: "COMPILED", artifactType: "SQL" }] });
				return;
			}
			const createMatch = pathname.match(/^\/api\/modeling\/plans\/([^/]+)\/release-candidates$/);
			if (createMatch && method === "POST") {
				writeSequence.push("create-candidate");
				const planId = decodeURIComponent(createMatch[1]);
				const body = request.postDataJSON() as { environment?: string; entries?: Array<{ modelSpecId: string }> };
				await fulfill({
					candidate: {
						id: candidateId,
						tenantId: "controlled-tenant",
						planId,
						environment: body.environment || "dev",
						status: "DRAFT",
						version: 1,
						audit: { createdBy: "controlled-e2e", createdAt: now },
						lastModifiedBy: "controlled-e2e",
						lastModifiedAt: now,
						entries: (body.entries || []).map((entry, index) => ({
							id: `entry-${index + 1}`,
							tenantId: "controlled-tenant",
							candidateId,
							planId,
							modelSpecId: entry.modelSpecId,
							revision: 2,
							checksum: "controlled-model-checksum",
							implementationMode: "DESIGNER_GENERATED",
							status: "DRAFT",
							sortOrder: index,
						})),
						origin: "BATCH_WORKBENCH",
					},
					replayed: false,
					driftReasons: [],
					allowedActions: ["START_BUILD"],
				});
				return;
			}
			if (pathname.endsWith(`/release-candidates/${candidateId}/lock`) && method === "POST") {
				writeSequence.push("lock-candidate");
				await fulfill({
					candidate: { id: candidateId, version: 2 },
					replayed: false,
					driftReasons: [],
					allowedActions: [],
				});
				return;
			}
			if (pathname === "/api/workbench/audit" && method === "POST") {
				await fulfill(null);
				return;
			}
			if (new Set(["GET", "HEAD", "OPTIONS"]).has(method)) {
				unexpectedReads.push(`${method} ${pathname}`);
				await fulfill([]);
				return;
			}
			unexpectedWrites.push(`${method} ${pathname}`);
			await route.fulfill({ status: 405, contentType: "application/json", body: '{"status":405,"message":"blocked"}' });
		},
	);
	return { compileHeaders, unexpectedReads, unexpectedWrites, writeSequence };
}

test("opens a draft from list management without reloading the workbench catalog", async ({ page }) => {
	test.setTimeout(90_000);
	await page.setViewportSize({ width: 1366, height: 768 });
	await installLocalAuthenticatedState(page);
	const failures = await installSprint79ProductionReadOnlyBarrier(page);
	await installPlatformReadProxy(page);
	await page.route(/\/api\/menu\/tree(?:\?.*)?$/, (route) =>
		route.fulfill({
			status: 200,
			contentType: "application/json",
			body: JSON.stringify({ status: 200, data: menuTree(), message: "OK" }),
		}),
	);
	const consoleErrors: string[] = [];
	const requests: Array<{ method: string; pathname: string; resourceType: string }> = [];
	page.on("console", (message) => {
		if (message.type() === "error") consoleErrors.push(message.text());
	});
	page.on("request", (request) => {
		requests.push({
			method: request.method(),
			pathname: new URL(request.url()).pathname,
			resourceType: request.resourceType(),
		});
	});

	await page.goto("/#/data-modeling/dimensions/workbench");
	await expect(page.locator("main.dmx-workbench-page h1")).toHaveText("维度建模", { timeout: 20_000 });
	await expect(page.getByRole("button", { name: "列表管理" })).toBeVisible();
	await page.waitForLoadState("networkidle", { timeout: 20_000 });
	const modelListReadsBefore = requests.filter(
		(request) => request.method === "GET" && request.pathname === "/api/modeling/model-specs",
	).length;
	const lifecycleReadsBefore = requests.filter(
		(request) => request.method === "GET" && /\/api\/modeling\/model-specs\/[^/]+\/lifecycle$/.test(request.pathname),
	).length;
	const documentReadsBefore = requests.filter((request) => request.resourceType === "document").length;

	await page.getByRole("button", { name: "列表管理" }).click();
	await expect(page.getByRole("heading", { name: "模型列表" })).toBeVisible();
	await expect(page.getByRole("button", { name: "查看" }).first()).toBeVisible();
	await page.getByLabel("搜索模型列表").fill("日期维度表");
	const targetRow = page
		.locator(".dmx-model-list-table tbody tr")
		.filter({ hasText: "日期维度表" })
		.filter({ has: page.getByRole("button", { name: "编辑" }) });
	await expect(targetRow).toHaveCount(1);
	await targetRow.getByRole("button", { name: "编辑" }).click();

	const editor = page.locator(".dmx-model-editor");
	await expect(editor.getByRole("heading", { name: "基本信息" })).toBeVisible({ timeout: 20_000 });
	await expect(page).toHaveURL(/#\/data-modeling\/dimensions\/workbench\?modelSpecId=/);
	const tableName = editor.getByLabel("表名", { exact: true });
	await expect(tableName).toBeEnabled();
	await expect(editor.getByLabel("实现来源")).toBeVisible();
	if ((await tableName.inputValue()).trim()) {
		await expect(tableName).toHaveValue(/^[a-z][a-z0-9_]*$/);
	} else {
		await expect(editor).toContainText("历史草稿尚未保存物理表名，请补录后保存");
	}
	await expect
		.poll(
			() =>
				requests.filter(
					(request) =>
						request.method === "GET" && /\/api\/modeling\/model-specs\/[^/]+\/lifecycle$/.test(request.pathname),
				).length,
			{ message: "selecting one model must load exactly one model lifecycle" },
		)
		.toBe(lifecycleReadsBefore + 1);
	expect(
		requests.filter((request) => request.method === "GET" && request.pathname === "/api/modeling/model-specs"),
		"model selection must not reload the full model catalog",
	).toHaveLength(modelListReadsBefore);
	expect(
		requests.filter((request) => request.resourceType === "document"),
		"model selection must stay in the current document",
	).toHaveLength(documentReadsBefore);
	await expect(page.getByText("正在加载模型工作台")).toHaveCount(0);

	await page.setViewportSize({ width: 768, height: 900 });
	await expect(tableName).toBeVisible();
	const widths = await page.evaluate(() => ({
		viewport: document.documentElement.clientWidth,
		document: document.documentElement.scrollWidth,
	}));
	expect(widths.document).toBeLessThanOrEqual(widths.viewport + 1);
	expect(failures.modelingWrites, "unexpected API writes").toEqual([]);
	expect(failures.pageErrors, "page errors").toEqual([]);
	expect(failures.requestFailures, "request failures").toEqual([]);
	expect(failures.httpFailures, "HTTP API failures").toEqual([]);
	expect(consoleErrors, "console errors").toEqual([]);
});

test("compiles the saved date implementation before creating and locking its release candidate", async ({
	page,
}, testInfo) => {
	await page.setViewportSize({ width: 1366, height: 768 });
	await installLocalAuthenticatedState(page);
	const controlled = await installControlledMaterializationProxy(page);
	const consoleErrors: string[] = [];
	const requestFailures: string[] = [];
	page.on("console", (message) => {
		if (message.type() === "error") consoleErrors.push(message.text());
	});
	page.on("requestfailed", (request) =>
		requestFailures.push(
			`${request.method()} ${new URL(request.url()).pathname} ${request.failure()?.errorText || ""}`,
		),
	);

	await page.goto(`/#/data-modeling/dimensions/workbench?modelSpecId=${CONTROLLED_DATE_MODEL_ID}`);
	await expect(page.locator("main.dmx-workbench-page h1")).toHaveText("维度建模", { timeout: 20_000 });
	await expect(page.locator(".dmx-model-editor").getByRole("heading", { name: "基本信息" })).toBeVisible();

	await page.getByRole("button", { name: "发布", exact: true }).click();
	const dialog = page.getByRole("dialog").filter({ hasText: "发布与物化" });
	await expect(dialog).toBeVisible();
	await expect(dialog).toContainText("服务端先生成或校验当前实现制品");
	const build = dialog.getByRole("button", { name: "创建并运行" });
	await expect(build).toBeEnabled();
	await build.click();

	await expect.poll(() => controlled.writeSequence).toEqual(["compile", "create-candidate", "lock-candidate"]);
	expect(controlled.compileHeaders).toHaveLength(1);
	expect(controlled.compileHeaders[0].model).toMatch(/^"model-spec:/);
	expect(controlled.compileHeaders[0].implementation).toMatch(/^"model-implementation:/);
	expect(controlled.unexpectedReads).toEqual([]);
	expect(controlled.unexpectedWrites).toEqual([]);
	await expect(dialog).not.toContainText("MATERIALIZATION_ARTIFACT_MISSING");
	await page.screenshot({ path: testInfo.outputPath("materialization-build-desktop.png"), fullPage: true });

	await page.setViewportSize({ width: 768, height: 900 });
	await expect(dialog).toBeVisible();
	const box = await dialog.boundingBox();
	expect(box).not.toBeNull();
	expect(box?.x || 0).toBeGreaterThanOrEqual(0);
	expect((box?.x || 0) + (box?.width || 0)).toBeLessThanOrEqual(769);
	await page.screenshot({ path: testInfo.outputPath("materialization-build-narrow.png"), fullPage: true });
	expect(requestFailures).toEqual([]);
	expect(consoleErrors).toEqual([]);
});
