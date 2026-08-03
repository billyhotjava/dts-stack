import { execFileSync } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { type APIResponse, expect, type Locator, type Page, test } from "@playwright/test";

type ModelingWriteAuthorization = {
	planOption: string;
	domainOption: string;
	sourceOption: string;
	prefix: string;
	cleanupMode: "retain";
};

type ApiObservation = {
	method: string;
	pathname: string;
	status: number;
	correlationId: string;
};

type CommittedImplementationPins = {
	modelRevision: number;
	modelChecksum: string;
	implementationId: string;
	implementationRevision: number;
	implementationChecksum: string;
};

type AuditEvidence = {
	id: string;
	occurredAt: string;
	operationCode: string;
	resourceId: string;
	result: string;
};

const forbiddenLegacyPaths = ["/api/etl/dbt/run", "/api/etl/dbt/preview", "/api/etl/dbt/files"];
const requiredAuditActions = [
	"MODELING_DBT_IMPORT_INSPECT",
	"MODEL_SPEC_IMPORT_PREVIEW",
	"MODELING_DBT_IMPORT_APPLY",
	"MODELING_DBT_DRAFT_COMMIT",
	"MODEL_RELEASE_CANDIDATE_CREATE",
	"MODEL_RELEASE_CANDIDATE_PUBLISH",
] as const;

let archiveDirectory = "";
let archivePath = "";
let authorization: ModelingWriteAuthorization;
let selectedPlanId = "";
let importedModelId = "";
let importedModelIds: string[] = [];
let importPackageId = "";
let importRunId = "";
let importAttemptId = "";
let dbtDraftId = "";
let releaseCandidateId = "";
let committedPins: CommittedImplementationPins | null = null;
let startedAt = "";

function requireEnvironment(name: string): string {
	const value = process.env[name]?.trim();
	if (!value) throw new Error(`${name} is required for the authorized Sprint-83/84 modeling E2E`);
	return value;
}

function readWriteAuthorization(): ModelingWriteAuthorization {
	if (process.env.E2E_MODELING_WRITE_ALLOWED !== "true") {
		throw new Error("E2E_MODELING_WRITE_ALLOWED=true is required; modeling writes are fail-closed by default");
	}
	const prefix = requireEnvironment("E2E_MODELING_PREFIX");
	if (!prefix.startsWith("E2E_") || prefix.length > 40) {
		throw new Error("E2E_MODELING_PREFIX must start with E2E_ and contain at most 40 characters");
	}
	const cleanupMode = requireEnvironment("E2E_MODELING_CLEANUP_MODE");
	if (cleanupMode !== "retain") {
		throw new Error(
			"E2E_MODELING_CLEANUP_MODE=retain is required because published/materialized evidence must not be hard-deleted",
		);
	}
	return {
		planOption: requireEnvironment("E2E_MODELING_PLAN_OPTION"),
		domainOption: process.env.E2E_MODELING_DOMAIN_OPTION?.trim() || "",
		sourceOption: process.env.E2E_MODELING_SOURCE_OPTION?.trim() || "",
		prefix,
		cleanupMode,
	};
}

function createFixtureArchive(): string {
	archiveDirectory = mkdtempSync(path.join(tmpdir(), "dts-s83-s84-e2e-"));
	archivePath = path.join(archiveDirectory, "fx01-artifact-rich.zip");
	const fixtureDirectory = path.resolve(
		import.meta.dirname,
		"../../dts-platform/src/test/resources/fixtures/dbt-sprint83/fx01-artifact-rich",
	);
	execFileSync("zip", ["-qr", archivePath, "."], { cwd: fixtureDirectory, stdio: "pipe" });
	return archivePath;
}

function unwrapCanonical(value: unknown): any {
	let current = value as any;
	for (let depth = 0; depth < 3; depth += 1) {
		if (!current || typeof current !== "object" || !("data" in current)) break;
		current = current.data;
	}
	return current;
}

async function canonicalJson(page: Page, url: string): Promise<any> {
	const response = await page.request.get(url, { headers: { Accept: "application/json" } });
	if (!response.ok())
		throw new Error(`Canonical read failed: GET ${new URL(response.url()).pathname} -> ${response.status()}`);
	return unwrapCanonical(await response.json());
}

async function canonicalResponseJson(response: APIResponse): Promise<any> {
	return unwrapCanonical(await response.json());
}

function requireCommittedPins(): CommittedImplementationPins {
	if (!committedPins) throw new Error("The advanced dbt commit must return implementation pins before release");
	return committedPins;
}

function observeApi(page: Page) {
	const observations: ApiObservation[] = [];
	const legacyRequests: string[] = [];
	page.on("request", (request) => {
		const pathname = new URL(request.url()).pathname;
		if (forbiddenLegacyPaths.some((legacy) => pathname === legacy || pathname.startsWith(`${legacy}/`))) {
			legacyRequests.push(`${request.method()} ${pathname}`);
		}
	});
	page.on("response", (response) => {
		const pathname = new URL(response.url()).pathname;
		if (!pathname.startsWith("/api/")) return;
		observations.push({
			method: response.request().method(),
			pathname,
			status: response.status(),
			correlationId:
				response.headers()["x-correlation-id"] ||
				response.headers()["x-request-id"] ||
				response.headers()["trace-id"] ||
				"",
		});
	});
	return { observations, legacyRequests };
}

async function clickRealMenu(page: Page, route: string, section = "维度建模") {
	const navigation = page.getByRole("navigation").first();
	const anchor = navigation.locator(`a[href*="${route}"]`).last();
	if (!(await anchor.isVisible())) {
		for (const label of ["数据建模", section]) {
			const control = navigation.getByText(label, { exact: true }).last();
			if (await control.isVisible()) await control.click();
			if (await anchor.isVisible()) break;
		}
	}
	await expect(anchor, `${route} must be reachable through the deployed menu`).toBeVisible();
	await anchor.click();
}

async function clickAndRequireSuccess(
	page: Page,
	control: Locator,
	predicate: (pathname: string, method: string) => boolean,
) {
	const responsePromise = page.waitForResponse((response) => {
		const request = response.request();
		return predicate(new URL(response.url()).pathname, request.method());
	});
	await control.click();
	const response = await responsePromise;
	expect(
		response.status(),
		`${response.request().method()} ${new URL(response.url()).pathname}`,
	).toBeGreaterThanOrEqual(200);
	expect(response.status(), `${response.request().method()} ${new URL(response.url()).pathname}`).toBeLessThan(300);
	return response;
}

async function selectRequiredMappings(page: Page) {
	const domainLabels = page.locator(".dmx-mapping-grid label").filter({ hasText: /^数据域 / });
	for (let index = 0; index < (await domainLabels.count()); index += 1) {
		if (!authorization.domainOption) {
			throw new Error("E2E_MODELING_DOMAIN_OPTION is required because the imported package needs a domain mapping");
		}
		await domainLabels.nth(index).locator("select").selectOption({ label: authorization.domainOption });
	}
	const sourceLabels = page.locator(".dmx-mapping-grid label").filter({ hasText: /^来源 / });
	for (let index = 0; index < (await sourceLabels.count()); index += 1) {
		if (!authorization.sourceOption) {
			throw new Error("E2E_MODELING_SOURCE_OPTION is required because the imported package needs a source mapping");
		}
		await sourceLabels.nth(index).locator("select").selectOption({ label: authorization.sourceOption });
	}
}

async function completeSelectedModelSemantics(page: Page) {
	const rows = page.locator(".dmx-import-semantics tbody tr");
	for (let index = 0; index < (await rows.count()); index += 1) {
		const row = rows.nth(index);
		const checkbox = row.locator('input[type="checkbox"]');
		if (!(await checkbox.isChecked()) || (await checkbox.isDisabled())) continue;
		const uniqueId = (await row.locator("td").nth(1).innerText()).split("\n", 1)[0].trim();
		const name = uniqueId.split(".").at(-1) || `model_${index + 1}`;
		const inputs = row.locator('input:not([type="checkbox"])');
		await inputs.nth(0).fill(`${authorization.prefix}_${name}`);
		await row.locator("select").nth(0).selectOption("FACT");
		await row.locator("select").nth(1).selectOption("DWD");
		await inputs.nth(1).fill(`一条 ${authorization.prefix} 订单记录一行`);
		await inputs.nth(2).fill("order_id");
	}
}

async function waitForImportTerminal(page: Page) {
	await expect
		.poll(
			async () => {
				const refresh = page.getByRole("button", { name: /刷新结果/ });
				if (await refresh.isVisible()) {
					await clickAndRequireSuccess(
						page,
						refresh,
						(pathname, method) => pathname.includes("/model-spec-imports/") && method === "GET",
					);
				}
				return (await page.locator(".dmx-complete-result h3").textContent()) || "";
			},
			{ message: "dbt import must reach a terminal result", timeout: 180_000, intervals: [1_000, 2_000, 5_000] },
		)
		.not.toContain("正在执行");
}

async function collectImportedModels(page: Page) {
	const rows = page.locator(".dmx-complete-result tbody tr");
	const ids: string[] = [];
	for (let index = 0; index < (await rows.count()); index += 1) {
		const row = rows.nth(index);
		const status = (await row.locator("td").nth(1).innerText()).trim();
		const id = (await row.locator("td").nth(2).innerText()).trim();
		if (["CREATED", "UPDATED", "SUCCEEDED"].some((value) => status.includes(value)) && /^[0-9a-f-]{36}$/i.test(id)) {
			ids.push(id);
		}
	}
	expect(ids.length, "the import must return at least one canonical ModelSpec id").toBeGreaterThan(0);
	return Array.from(new Set(ids));
}

async function releaseWorkspace(page: Page) {
	return canonicalJson(page, `/api/modeling/plans/${encodeURIComponent(selectedPlanId)}/release-candidates/workspace`);
}

async function waitForPublishAdmission(page: Page) {
	const pins = requireCommittedPins();
	await expect
		.poll(
			async () => {
				const workspace = await releaseWorkspace(page);
				const candidate = workspace.candidate;
				const entry = candidate?.entries?.find((item: any) => item.modelSpecId === importedModelId);
				return {
					candidateId: candidate?.id || "",
					modelSpecId: entry?.modelSpecId || "",
					modelRevision: Number(entry?.revision || 0),
					modelChecksum: entry?.checksum || "",
					implementationId: entry?.implementationId || "",
					publishAllowed: Boolean(workspace.allowedActions?.includes("PUBLISH")),
				};
			},
			{
				message: "release build must produce a candidate admitted for PUBLISH",
				timeout: 600_000,
				intervals: [2_000, 5_000, 10_000],
			},
		)
		.toEqual({
			candidateId: releaseCandidateId,
			modelSpecId: importedModelId,
			modelRevision: pins.modelRevision,
			modelChecksum: pins.modelChecksum,
			implementationId: pins.implementationId,
			publishAllowed: true,
		});
}

async function waitForPublishedCandidate(page: Page) {
	await expect
		.poll(
			async () => {
				const workspace = await releaseWorkspace(page);
				return `${workspace.candidate?.id || "NONE"}:${workspace.candidate?.status || "NONE"}`;
			},
			{ message: "release candidate must reach PUBLISHED", timeout: 300_000, intervals: [2_000, 5_000, 10_000] },
		)
		.toMatch(new RegExp(`^${releaseCandidateId}:(PUBLISHED|MATERIALIZED)$`));
}

async function currentModel(page: Page) {
	return canonicalJson(page, `/api/modeling/model-specs/${encodeURIComponent(importedModelId)}`);
}

async function waitForServingEvidence(page: Page) {
	await expect
		.poll(
			async () => {
				const model = await currentModel(page);
				const query = new URLSearchParams({ modelRevision: String(model.revision), representationScope: "BUSINESS" });
				const representation = await canonicalJson(
					page,
					`/api/modeling/model-specs/${encodeURIComponent(importedModelId)}/representations?${query}`,
				);
				return Boolean(representation.physicalPreview?.serving);
			},
			{
				message: "published model must expose serving relation evidence",
				timeout: 600_000,
				intervals: [2_000, 5_000, 10_000],
			},
		)
		.toBe(true);
}

async function requireAuditAction(page: Page, action: string, resourceId: string): Promise<AuditEvidence> {
	let matched: AuditEvidence | null = null;
	await expect
		.poll(
			async () => {
				const query = new URLSearchParams({
					action,
					resource: resourceId,
					from: startedAt,
					page: "0",
					size: "200",
					sort: "occurredAt,desc",
				});
				const response = await page.request.get(`/api/security/audit-logs?${query}`, {
					headers: { Accept: "application/json" },
				});
				if (response.status() === 401 || response.status() === 403) {
					throw new Error(
						`The E2E identity cannot read public audit evidence for ${action}: HTTP ${response.status()}`,
					);
				}
				if (!response.ok()) return false;
				const payload = unwrapCanonical(await response.json());
				const rows = Array.isArray(payload?.content) ? payload.content : [];
				const row = rows.find(
					(item: any) =>
						item?.operationCode === action &&
						(item?.resourceId === resourceId || item?.targetId === resourceId || item?.targetIds?.includes(resourceId)),
				);
				if (!row) return false;
				matched = {
					id: String(row.id || ""),
					occurredAt: String(row.occurredAt || ""),
					operationCode: String(row.operationCode || ""),
					resourceId,
					result: String(row.result || ""),
				};
				return true;
			},
			{
				message: `public audit must contain ${action} for ${resourceId}`,
				timeout: 120_000,
				intervals: [1_000, 2_000, 5_000],
			},
		)
		.toBe(true);
	if (!matched) throw new Error(`Public audit evidence disappeared for ${action} / ${resourceId}`);
	return matched;
}

test.describe("Sprint-83/84 authorized dbt visual roundtrip", () => {
	test.describe.configure({ mode: "serial" });
	test.setTimeout(1_200_000);

	test.beforeAll(() => {
		authorization = readWriteAuthorization();
		startedAt = new Date(Date.now() - 5_000).toISOString();
		createFixtureArchive();
	});

	test.afterAll(() => {
		if (archiveDirectory) rmSync(archiveDirectory, { recursive: true, force: true });
	});

	test("imports artifact-rich ZIP through the real menu and records canonical ModelSpecs", async ({
		page,
	}, testInfo) => {
		await page.setViewportSize({ width: 1366, height: 768 });
		const evidence = observeApi(page);
		await page.goto("/#/workbench");
		await clickRealMenu(page, "/data-modeling/dimensions/reverse");
		await expect(page.locator('main[class*="dmx-"] h1')).toHaveText("逆向建模");
		await page.getByRole("button", { name: /快速开始/ }).click();

		const planSelect = page.locator("label.dmx-reverse-plan select");
		await planSelect.selectOption({ label: authorization.planOption });
		selectedPlanId = await planSelect.inputValue();
		expect(selectedPlanId, "the authorized plan must resolve to a canonical id").toMatch(/^[0-9a-f-]{36}$/i);
		await page.getByLabel("选择 dbt ZIP").setInputFiles(archivePath);
		const inspectResponse = await clickAndRequireSuccess(
			page,
			page.getByRole("button", { name: "开始识别" }),
			(pathname, method) => pathname.endsWith("/model-spec-imports/dbt/archive/inspect") && method === "POST",
		);
		const inspection = await canonicalResponseJson(inspectResponse);
		importPackageId = String(inspection?.package?.packageId || "");
		expect(importPackageId, "inspect must return the audited package resource id").not.toBe("");
		await expect(page.getByRole("heading", { name: "确认模型信息" })).toBeVisible();
		await selectRequiredMappings(page);
		await completeSelectedModelSemantics(page);
		const previewResponse = await clickAndRequireSuccess(
			page,
			page.getByRole("button", { name: "生成预览" }),
			(pathname, method) => pathname.endsWith("/model-spec-imports/dbt/preview") && method === "POST",
		);
		const preview = await canonicalResponseJson(previewResponse);
		importRunId = String(preview?.runId || "");
		expect(importRunId, "preview must return the audited import run id").toMatch(/^[0-9a-f-]{36}$/i);
		await expect(page.getByRole("heading", { name: "导入预览" })).toBeVisible();
		const applyResponse = await clickAndRequireSuccess(
			page,
			page.getByRole("button", { name: "生成模型" }),
			(pathname, method) => pathname.endsWith("/model-spec-imports/dbt/apply") && method === "POST",
		);
		const apply = await canonicalResponseJson(applyResponse);
		expect(String(apply?.runId || ""), "apply must remain bound to the preview run").toBe(importRunId);
		importAttemptId = String(apply?.attemptId || "");
		expect(importAttemptId, "apply must return the audited attempt id").toMatch(/^[0-9a-f-]{36}$/i);
		await waitForImportTerminal(page);
		await expect(page.locator(".dmx-complete-result h3")).not.toContainText(/FAILED|BLOCKED/);
		importedModelIds = await collectImportedModels(page);
		importedModelId = importedModelIds[0];
		expect(evidence.legacyRequests, "new modeling UI must never call legacy dbt control planes").toEqual([]);

		await page.screenshot({ path: testInfo.outputPath("dbt-import-terminal.png"), fullPage: true });
		await testInfo.attach("dbt-import-evidence.json", {
			body: Buffer.from(
				JSON.stringify(
					{
						planId: selectedPlanId,
						packageId: importPackageId,
						runId: importRunId,
						attemptId: importAttemptId,
						modelSpecIds: importedModelIds,
						prefix: authorization.prefix,
						cleanupMode: authorization.cleanupMode,
						api: evidence.observations,
					},
					null,
					2,
				),
			),
			contentType: "application/json",
		});
	});

	test("commits advanced dbt, builds, publishes and reads serving evidence", async ({ page }, testInfo) => {
		expect(importedModelId, "the ZIP import test must produce a ModelSpec id").not.toBe("");
		const evidence = observeApi(page);
		await page.goto(`/#/data-modeling/dimensions/workbench?modelSpecId=${encodeURIComponent(importedModelId)}`);
		await expect(page.locator('main[class*="dmx-"] h1')).toHaveText("维度建模");
		await expect(page.locator(".dmx-model-context")).toBeVisible({ timeout: 30_000 });

		const description = page.locator("label").filter({ hasText: "业务定义" }).locator("textarea");
		await description.fill(`${authorization.prefix} dbt 可视化往返验收模型`);
		await clickAndRequireSuccess(
			page,
			page.getByRole("button", { name: /^保存$/ }),
			(pathname, method) => pathname.endsWith(`/model-specs/${importedModelId}`) && method === "PUT",
		);
		await expect(page.locator(".dmx-toast")).toContainText("模型草稿已保存");

		await page.getByRole("button", { name: "高级 dbt" }).click();
		const advanced = page.getByRole("dialog", { name: "高级 dbt 实现" });
		await expect(advanced).toBeVisible();
		const createDraft = advanced.getByRole("button", { name: "创建高级草稿" });
		await expect(createDraft, "a newly imported ModelSpec must start a fresh audited dbt draft").toBeVisible();
		const draftResponse = await clickAndRequireSuccess(
			page,
			createDraft,
			(pathname, method) => pathname.endsWith("/dbt-implementation-drafts") && method === "POST",
		);
		const draft = await canonicalResponseJson(draftResponse);
		dbtDraftId = String(draft?.draftId || "");
		expect(dbtDraftId, "advanced dbt create must return the audited draft id").toMatch(/^[0-9a-f-]{36}$/i);
		const sqlFile = advanced
			.locator("aside")
			.getByRole("button", { name: /models\/.*\.sql$/i })
			.first();
		await expect(sqlFile, "the committed implementation must edit an explicit models/*.sql file").toBeVisible();
		const sqlPath = (await sqlFile.innerText()).trim();
		expect(sqlPath).toMatch(/^models\/.*\.sql$/i);
		await sqlFile.click();
		const editor = advanced.getByLabel(`编辑 ${sqlPath}`);
		await expect(editor).toBeVisible({ timeout: 30_000 });
		await editor.fill(`${await editor.inputValue()}\n-- ${authorization.prefix} roundtrip evidence`);
		await clickAndRequireSuccess(
			page,
			advanced.getByRole("button", { name: "保存文件" }),
			(pathname, method) => pathname.includes("/dbt-implementation-drafts/") && method === "PUT",
		);
		await clickAndRequireSuccess(
			page,
			advanced.getByRole("button", { name: "校验" }),
			(pathname, method) => pathname.endsWith("/validate") && method === "POST",
		);
		await expect(advanced.getByText("校验通过", { exact: true })).toBeVisible({ timeout: 60_000 });
		const commitResponse = await clickAndRequireSuccess(
			page,
			advanced.getByRole("button", { name: "提交实现" }),
			(pathname, method) => pathname.endsWith("/commit") && method === "POST",
		);
		const commit = await canonicalResponseJson(commitResponse);
		expect(String(commit?.draftId || ""), "commit receipt must remain bound to the created draft").toBe(dbtDraftId);
		committedPins = {
			modelRevision: Number(commit?.modelRevision || 0),
			modelChecksum: String(commit?.modelChecksum || ""),
			implementationId: String(commit?.implementationId || ""),
			implementationRevision: Number(commit?.implementationRevision || 0),
			implementationChecksum: String(commit?.implementationChecksum || ""),
		};
		expect(committedPins.modelRevision).toBeGreaterThan(0);
		expect(committedPins.modelChecksum).not.toBe("");
		expect(committedPins.implementationId).toMatch(/^[0-9a-f-]{36}$/i);
		expect(committedPins.implementationRevision).toBeGreaterThan(0);
		expect(committedPins.implementationChecksum).not.toBe("");
		await expect(advanced).toContainText("实现已提交", { timeout: 60_000 });
		await advanced.getByRole("button", { name: "关闭" }).click();

		await clickAndRequireSuccess(
			page,
			page.getByRole("button", { name: "刷新" }).last(),
			(pathname, method) => pathname.endsWith(`/model-specs/${importedModelId}`) && method === "GET",
		);
		await page.getByRole("button", { name: "发布与物化" }).click();
		let publishDialog = page.getByRole("dialog", { name: "发布与物化" });
		await expect(publishDialog).toBeVisible();
		await publishDialog.locator("label").filter({ hasText: "执行环境" }).locator("select").selectOption("test");
		const build = publishDialog.getByRole("button", { name: /创建并运行|开始构建|重试构建/ });
		await expect(build, await publishDialog.innerText()).toBeEnabled();
		const buildResponse = await clickAndRequireSuccess(
			page,
			build,
			(pathname, method) => method === "POST" && pathname.endsWith(`/plans/${selectedPlanId}/release-candidates`),
		);
		const buildCommand = await canonicalResponseJson(buildResponse);
		const candidate = buildCommand?.candidate;
		releaseCandidateId = String(candidate?.id || "");
		expect(releaseCandidateId, "build must create a new auditable candidate for this run").toMatch(/^[0-9a-f-]{36}$/i);
		const pins = requireCommittedPins();
		const candidateEntry = candidate?.entries?.find((entry: any) => entry.modelSpecId === importedModelId);
		expect(candidateEntry, "the new candidate must contain the imported ModelSpec").toBeTruthy();
		expect(Number(candidateEntry?.revision || 0)).toBe(pins.modelRevision);
		expect(String(candidateEntry?.checksum || "")).toBe(pins.modelChecksum);
		expect(String(candidateEntry?.implementationId || "")).toBe(pins.implementationId);
		await waitForPublishAdmission(page);

		await publishDialog.getByRole("button", { name: "关闭" }).click();
		await page.getByRole("button", { name: "发布与物化" }).click();
		publishDialog = page.getByRole("dialog", { name: "发布与物化" });
		await publishDialog.getByRole("button", { name: "发布模型" }).click();
		const publish = publishDialog.getByRole("button", { name: "发布", exact: true });
		await expect(publish, await publishDialog.innerText()).toBeEnabled();
		const publishResponse = await clickAndRequireSuccess(
			page,
			publish,
			(pathname, method) => method === "POST" && pathname.endsWith(`/release-candidates/${releaseCandidateId}/publish`),
		);
		const publication = await canonicalResponseJson(publishResponse);
		expect(
			String(publication?.candidate?.id || publication?.candidateId || ""),
			"publish must target this candidate",
		).toBe(releaseCandidateId);
		await waitForPublishedCandidate(page);
		await waitForServingEvidence(page);
		await publishDialog.getByRole("button", { name: "关闭" }).click();

		await clickAndRequireSuccess(
			page,
			page.getByRole("button", { name: "刷新" }).last(),
			(pathname, method) => pathname.endsWith(`/model-specs/${importedModelId}`) && method === "GET",
		);
		await page.getByRole("button", { name: "物理预览" }).click();
		const previewDialog = page.getByRole("dialog", { name: "物理结构与数据预览" });
		await expect(previewDialog.getByRole("button", { name: "读取结构" })).toBeEnabled({ timeout: 30_000 });
		await clickAndRequireSuccess(
			page,
			previewDialog.getByRole("button", { name: "读取结构" }),
			(pathname, method) => pathname.includes("/physical-preview") && method === "GET",
		);
		await expect(previewDialog).toContainText("证据状态");
		await clickAndRequireSuccess(
			page,
			previewDialog.getByRole("button", { name: "读取脱敏样本" }),
			(pathname, method) => pathname.includes("/physical-preview") && method === "GET",
		);
		await expect(previewDialog).toContainText("脱敏摘要");
		expect(evidence.legacyRequests, "roundtrip must stay on the canonical ModelSpec/dbt gateway path").toEqual([]);

		await page.screenshot({ path: testInfo.outputPath("serving-physical-preview.png"), fullPage: true });
		await testInfo.attach("roundtrip-api-evidence.json", {
			body: Buffer.from(JSON.stringify(evidence.observations, null, 2)),
			contentType: "application/json",
		});
	});

	test("finds the roundtrip actions in the public audit ledger", async ({ page }, testInfo) => {
		const expectations = [
			{ action: requiredAuditActions[0], resourceId: importPackageId },
			{ action: requiredAuditActions[1], resourceId: importRunId },
			{ action: requiredAuditActions[2], resourceId: importAttemptId },
			{ action: requiredAuditActions[3], resourceId: dbtDraftId },
			{ action: requiredAuditActions[4], resourceId: releaseCandidateId },
			{ action: requiredAuditActions[5], resourceId: releaseCandidateId },
		];
		for (const expectation of expectations) {
			expect(expectation.action, "every audit contract action must be defined").not.toBeUndefined();
			expect(expectation.resourceId, `resource id is required for ${expectation.action}`).not.toBe("");
		}
		const evidence: AuditEvidence[] = [];
		for (const expectation of expectations) {
			evidence.push(await requireAuditAction(page, expectation.action, expectation.resourceId));
		}
		await testInfo.attach("audit-actions.json", {
			body: Buffer.from(
				JSON.stringify(
					{
						from: startedAt,
						evidence,
						modelSpecIds: importedModelIds,
						candidateId: releaseCandidateId,
						cleanup: "Retained under the explicitly authorized E2E_ prefix for audit traceability",
					},
					null,
					2,
				),
			),
			contentType: "application/json",
		});
	});
});
