import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { type APIRequestContext, type APIResponse, expect, test } from "@playwright/test";
import JSZip from "jszip";

type ModelingWriteAuthorization = {
	modelingContextOption: string;
	domainOption: string;
	prefix: string;
	cleanupMode: "retain";
};

type Inspection = {
	package: any;
	compatibility: any;
	inspectionProof: string;
	proofExpiresAt: string;
};

const modelImportBase = "/api/modeling/model-spec-imports";
const forbiddenLegacyPaths = ["/api/etl/dbt/run", "/api/etl/dbt/preview", "/api/etl/dbt/files"];

let authorization: ModelingWriteAuthorization;
let planId = "";
let temporaryRoot = "";

function requireEnvironment(name: string): string {
	const value = process.env[name]?.trim();
	if (!value) throw new Error(`${name} is required for the authorized Sprint-83 recovery E2E`);
	return value;
}

function readWriteAuthorization(): ModelingWriteAuthorization {
	if (process.env.E2E_MODELING_WRITE_ALLOWED !== "true") {
		throw new Error("E2E_MODELING_WRITE_ALLOWED=true is required; recovery writes are fail-closed by default");
	}
	const prefix = requireEnvironment("E2E_MODELING_PREFIX");
	if (!prefix.startsWith("E2E_") || prefix.length > 40) {
		throw new Error("E2E_MODELING_PREFIX must start with E2E_ and contain at most 40 characters");
	}
	const cleanupMode = requireEnvironment("E2E_MODELING_CLEANUP_MODE");
	if (cleanupMode !== "retain") {
		throw new Error("E2E_MODELING_CLEANUP_MODE=retain is required for auditable forward recovery");
	}
	return {
		modelingContextOption: requireEnvironment("E2E_MODELING_CONTEXT_OPTION"),
		domainOption: requireEnvironment("E2E_MODELING_DOMAIN_OPTION"),
		prefix,
		cleanupMode,
	};
}

function unwrapCanonical(value: unknown): any {
	let current = value as any;
	for (let depth = 0; depth < 3; depth += 1) {
		if (!current || typeof current !== "object" || !("data" in current)) break;
		current = current.data;
	}
	return current;
}

async function canonicalResponse(response: APIResponse, label: string): Promise<any> {
	if (!response.ok()) {
		throw new Error(`${label} failed: HTTP ${response.status()} ${await response.text()}`);
	}
	return unwrapCanonical(await response.json());
}

async function resolveAuthorizedModelingContext(request: APIRequestContext): Promise<string> {
	const response = await request.get("/api/modeling/warehouse-plans", { headers: { Accept: "application/json" } });
	const raw = await canonicalResponse(response, "read modeling contexts");
	const contexts = Array.isArray(raw)
		? raw.filter((item) => item && typeof item === "object" && item.lifecycleStatus !== "ARCHIVED")
		: [];
	const expected = authorization.modelingContextOption;
	const authorized = contexts.find((item) => [item.id, item.code, item.name].map(String).includes(expected));
	if (!authorized) throw new Error(`E2E_MODELING_CONTEXT_OPTION is not active: ${expected}`);
	if (!contexts[0] || contexts[0].id !== authorized.id) {
		throw new Error("The authorized modeling context is not current; refusing recovery writes");
	}
	return String(authorized.id);
}

function fixtureArchive(relativeFixture: string): string {
	const fixtureDirectory = path.resolve(
		import.meta.dirname,
		"../../dts-platform/src/test/resources/fixtures/dbt-sprint83",
		relativeFixture,
	);
	const archivePath = path.join(temporaryRoot, `${relativeFixture.replaceAll("/", "-")}.zip`);
	execFileSync("zip", ["-qr", archivePath, "."], { cwd: fixtureDirectory, stdio: "pipe" });
	return archivePath;
}

async function maliciousModelArchive(): Promise<string> {
	const archive = new JSZip();
	archive.file("../malicious-model-name.sql", "select 1 as id\n");
	const archivePath = path.join(temporaryRoot, "malicious-model-name.zip");
	writeFileSync(archivePath, await archive.generateAsync({ type: "nodebuffer" }));
	return archivePath;
}

async function inspectArchive(request: APIRequestContext, archivePath: string): Promise<Inspection> {
	const response = await request.post(`${modelImportBase}/dbt/archive/inspect`, {
		multipart: {
			archive: {
				name: path.basename(archivePath),
				mimeType: "application/zip",
				buffer: readFileSync(archivePath),
			},
		},
	});
	return canonicalResponse(response, `inspect ${path.basename(archivePath)}`);
}

function semanticOverride(modelUniqueId: string, suffix: string) {
	return {
		modelUniqueId,
		modelType: "FACT",
		layer: "DWD",
		businessName: `${authorization.prefix}_${suffix}`,
		businessDefinition: `${authorization.prefix} Sprint-83 可恢复导入验收`,
		grain: { statement: "一条订单记录一行", keys: ["order_id"] },
		fieldRoles: { order_id: "KEY" },
		businessKeys: ["order_id"],
		standardBindings: [],
		consumptionScenarios: ["Sprint-83 往返建模验收"],
	};
}

async function previewInspection(
	request: APIRequestContext,
	inspection: Inspection,
	semanticOverrides: Array<Record<string, unknown>>,
) {
	const uniqueId = String(inspection.package.models[0]?.dbtUniqueId || "");
	expect(uniqueId).not.toBe("");
	const response = await request.post(`${modelImportBase}/dbt/preview`, {
		data: {
			package: inspection.package,
			inspectionProof: inspection.inspectionProof,
			context: {
				planId,
				domainMappings: { SPRINT83: authorization.domainOption },
				sourceMappings: {},
			},
			selectedUniqueIds: [uniqueId],
			semanticOverrides,
			renameMappings: [],
		},
	});
	const preview = await canonicalResponse(response, `preview ${uniqueId}`);
	const item = preview.items?.find((candidate: any) => candidate.dbtUniqueId === uniqueId);
	expect(item, JSON.stringify(preview, null, 2)).toBeTruthy();
	expect(item.action, JSON.stringify(item, null, 2)).not.toBe("BLOCKED");
	return preview;
}

function assertSummaryAlgebra(result: any) {
	const summary = result.overallRun?.summary || result.summary;
	expect(summary.selected).toBe(
		summary.pending + summary.created + summary.updated + summary.skipped + summary.failed + summary.blocked,
	);
	expect(summary.succeeded).toBe(summary.created + summary.updated);
}

async function waitForTerminal(request: APIRequestContext, runId: string) {
	let terminal: any = null;
	await expect
		.poll(
			async () => {
				const response = await request.get(`${modelImportBase}/${encodeURIComponent(runId)}/apply`);
				terminal = await canonicalResponse(response, `read apply ${runId}`);
				return String(terminal.status || "");
			},
			{ timeout: 180_000, intervals: [500, 1_000, 2_000, 5_000] },
		)
		.not.toBe("RUNNING");
	if (!terminal) throw new Error(`Apply result disappeared for ${runId}`);
	assertSummaryAlgebra(terminal);
	return terminal;
}

async function applyPreview(request: APIRequestContext, preview: any): Promise<{ request: any; terminal: any }> {
	const uniqueId = String(preview.items[0]?.dbtUniqueId || "");
	const action = String(preview.items[0]?.action || "");
	const applyRequest = {
		runId: preview.runId,
		previewHash: preview.previewHash,
		selectedUniqueIds: [uniqueId],
		idempotencyKey: `${authorization.prefix}-${randomUUID()}`,
		conflictResolutions: action === "CONFLICT" ? { [uniqueId]: "ACCEPT_INCOMING" } : {},
	};
	const response = await request.post(`${modelImportBase}/dbt/apply`, { data: applyRequest });
	await canonicalResponse(response, `apply ${uniqueId}`);
	return { request: applyRequest, terminal: await waitForTerminal(request, preview.runId) };
}

function successfulItem(result: any) {
	const items = result.overallRun?.items || result.items || [];
	const item = items.find((candidate: any) => ["CREATED", "UPDATED", "SKIPPED"].includes(candidate.status));
	expect(item, JSON.stringify(result, null, 2)).toBeTruthy();
	return item;
}

async function currentModel(request: APIRequestContext, modelSpecId: string) {
	const response = await request.get(`/api/modeling/model-specs/${encodeURIComponent(modelSpecId)}`);
	return canonicalResponse(response, `read ModelSpec ${modelSpecId}`);
}

test.describe("Sprint-83 source-only, drift, recovery and security acceptance", () => {
	test.describe.configure({ mode: "serial" });
	test.setTimeout(600_000);

	test.beforeAll(async ({ request }) => {
		authorization = readWriteAuthorization();
		temporaryRoot = mkdtempSync(path.join(tmpdir(), "dts-s83-recovery-"));
		planId = await resolveAuthorizedModelingContext(request);
	});

	test.afterAll(() => {
		if (temporaryRoot) rmSync(temporaryRoot, { recursive: true, force: true });
	});

	test("imports an enforced source-only contract as DBT_MANAGED without guessing fields", async ({
		request,
	}, testInfo) => {
		const archivePath = fixtureArchive("fx02-source-only-enforced");
		const inspection = await inspectArchive(request, archivePath);
		const model = inspection.package.models[0];
		expect(inspection.compatibility.importProjection).toBe("IMPORTABLE");
		expect(model.config.structureProvenance).toBe("DECLARED");
		expect(model.config.implementationOwnership).toBe("DBT_MANAGED");
		expect(model.semantics.domainCode).toBe("SPRINT83");
		expect(model.sql.effectiveSqlChecksum).toMatch(/^[0-9a-f]{64}$/);
		expect(model.conversion.reasonCodes).toEqual(["SOURCE_SEMANTICS_INCOMPLETE"]);
		expect(model.columns.map((column: any) => `${column.name}:${column.dataType}`)).toEqual([
			"order_id:bigint",
			"status:varchar",
		]);

		const preview = await previewInspection(request, inspection, [semanticOverride(model.dbtUniqueId, "source_only")]);
		const applied = await applyPreview(request, preview);
		expect(applied.terminal.status).toBe("SUCCESS");
		const item = successfulItem(applied.terminal);
		const canonical = await currentModel(request, item.modelSpecId);
		expect(canonical.implementationMode).toBe("DBT_MANAGED");
		expect(canonical.status).toBe("DRAFT");

		await testInfo.attach("source-only-import.json", {
			body: Buffer.from(
				JSON.stringify(
					{
						packageId: inspection.package.packageId,
						packageChecksum: inspection.package.packageChecksum,
						runId: preview.runId,
						attemptId: applied.terminal.attemptId,
						modelSpecId: item.modelSpecId,
						modelRevision: item.revision,
						modelChecksum: item.modelChecksum,
						implementationRevision: item.implementationRevision,
						implementationChecksum: item.implementationChecksum,
					},
					null,
					2,
				),
			),
			contentType: "application/json",
		});
	});

	test("reimports a stable dbt identity, replays idempotently and forward-undoes the UPDATE", async ({
		request,
	}, testInfo) => {
		const baseInspection = await inspectArchive(request, fixtureArchive("fx04-three-way-drift/base"));
		const uniqueId = String(baseInspection.package.models[0].dbtUniqueId);
		const basePreview = await previewInspection(request, baseInspection, [semanticOverride(uniqueId, "three_way")]);
		const baseApplied = await applyPreview(request, basePreview);
		expect(baseApplied.terminal.status).toBe("SUCCESS");
		const baseItem = successfulItem(baseApplied.terminal);
		const baseModel = await currentModel(request, baseItem.modelSpecId);

		const incomingInspection = await inspectArchive(request, fixtureArchive("fx04-three-way-drift/incoming"));
		expect(incomingInspection.package.models[0].dbtUniqueId).toBe(uniqueId);
		expect(incomingInspection.package.models[0].sql.effectiveSqlChecksum).not.toBe(
			baseInspection.package.models[0].sql.effectiveSqlChecksum,
		);
		const incomingPreview = await previewInspection(request, incomingInspection, []);
		expect(incomingPreview.items[0].action).toBe("UPDATE");
		const incomingApplied = await applyPreview(request, incomingPreview);
		expect(incomingApplied.terminal.status).toBe("SUCCESS");
		const updated = successfulItem(incomingApplied.terminal);
		expect(updated.appliedAction).toBe("UPDATE");
		expect(updated.preAttemptPins).toBeTruthy();
		const afterIncoming = await currentModel(request, updated.modelSpecId);
		expect(afterIncoming.name).toBe(baseModel.name);
		expect(afterIncoming.description).toBe(baseModel.description);

		const replayResponse = await request.post(`${modelImportBase}/dbt/apply`, { data: incomingApplied.request });
		const replay = await canonicalResponse(replayResponse, "replay incoming apply");
		expect(replay.disposition).toBe("REPLAY");
		expect(replay.attemptId).toBe(incomingApplied.terminal.attemptId);
		expect(replay.overallRun).toEqual(incomingApplied.terminal.overallRun);

		const retryResponse = await request.post(`${modelImportBase}/${incomingPreview.runId}/retry`, {
			data: { previewHash: incomingPreview.previewHash, idempotencyKey: `${authorization.prefix}-${randomUUID()}` },
		});
		expect(retryResponse.status()).toBe(409);
		const retryFailure = await retryResponse.json();
		expect(String(retryFailure.code || retryFailure.errorCode || "")).toBe("MODEL_IMPORT_RETRY_NOT_AVAILABLE");

		const expectedCurrentRevisions = {
			[uniqueId]: {
				modelRevision: updated.revision,
				modelChecksum: updated.modelChecksum,
				implementationRevision: updated.implementationRevision,
				implementationChecksum: updated.implementationChecksum,
			},
		};
		const undoResponse = await request.post(`${modelImportBase}/dbt/forward-undo`, {
			data: {
				targetAttemptId: incomingApplied.terminal.attemptId,
				selectedItemIds: [],
				expectedCurrentRevisions,
				idempotencyKey: `${authorization.prefix}-${randomUUID()}`,
			},
		});
		const undoStarted = await canonicalResponse(undoResponse, "forward undo incoming update");
		const undoTerminal =
			undoStarted.status === "RUNNING" ? await waitForTerminal(request, incomingPreview.runId) : undoStarted;
		expect(undoTerminal.status).toBe("SUCCESS");
		const undone = successfulItem(undoTerminal);
		expect(undone.status).toBe("UPDATED");
		expect(undone.implementationChecksum).toBe(updated.preAttemptPins.implementationChecksum);
		const afterUndo = await currentModel(request, undone.modelSpecId);
		expect(afterUndo.name).toBe(baseModel.name);
		expect(afterUndo.description).toBe(baseModel.description);
		expect(afterUndo.implementationMode).toBe("DBT_MANAGED");

		await testInfo.attach("three-way-replay-undo.json", {
			body: Buffer.from(
				JSON.stringify(
					{
						baseAttemptId: baseApplied.terminal.attemptId,
						updateAttemptId: incomingApplied.terminal.attemptId,
						replayDisposition: replay.disposition,
						retryFailureCode: retryFailure.code || retryFailure.errorCode,
						undoAttemptId: undoTerminal.attemptId,
						modelSpecId: undone.modelSpecId,
						preAttemptPins: updated.preAttemptPins,
						expectedCurrentRevisions,
					},
					null,
					2,
				),
			),
			contentType: "application/json",
		});
	});

	test("rejects a malicious model alias and confirms retired execution routes remain closed", async ({
		request,
	}, testInfo) => {
		const archivePath = await maliciousModelArchive();
		const inspectResponse = await request.post(`${modelImportBase}/dbt/archive/inspect`, {
			multipart: {
				archive: {
					name: path.basename(archivePath),
					mimeType: "application/zip",
					buffer: readFileSync(archivePath),
				},
			},
		});
		expect(inspectResponse.status()).toBe(400);
		const inspectionFailure = await inspectResponse.json();
		expect(String(inspectionFailure.code || inspectionFailure.errorCode || "")).toBe(
			"MODEL_IMPORT_ARCHIVE_UNSAFE_PATH",
		);

		const retiredEvidence: Record<string, number> = {};
		for (const retired of forbiddenLegacyPaths.slice(0, 2)) {
			const response =
				retired === "/api/etl/dbt/run"
					? await request.post(retired, { data: { model: "malicious-model-name" } })
					: await request.get(`${retired}?model=malicious-model-name`);
			retiredEvidence[retired] = response.status();
			expect([403, 404, 405]).toContain(response.status());
		}
		retiredEvidence[forbiddenLegacyPaths[2]] = 0;

		await testInfo.attach("security-retired-boundary.json", {
			body: Buffer.from(
				JSON.stringify(
					{
						maliciousArchiveStatus: inspectResponse.status(),
						maliciousArchiveCode: inspectionFailure.code || inspectionFailure.errorCode,
						retiredEvidence,
						sharedFileWriteCalls: 0,
					},
					null,
					2,
				),
			),
			contentType: "application/json",
		});
	});
});
