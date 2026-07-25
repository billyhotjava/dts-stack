import assert from "node:assert/strict";
import test from "node:test";
import type { ModelSpecImportPreviewItem } from "@/api/modelSpecImportApi";
import {
	canRetryFailedImport,
	createModelPackageImportState,
	defaultSelectedUniqueIds,
	hasCompleteImportContext,
	isPreviewApplicable,
	MODEL_IMPORT_GENERIC_DIAGNOSTIC_CODE,
	parseModelPackage,
	reduceModelPackageImportState,
	resolveModelPackageImportIdempotencySlot,
} from "./modelPackageImportState";

const packageJson = {
	schemaVersion: "dts.model-package/v1",
	packageId: "demo",
	packageChecksum: "a".repeat(64),
	dbt: { projectName: "demo_project", projectVersion: "1" },
	sources: [{ dbtUniqueId: "source.demo.orders", name: "orders" }],
	models: [
		{
			dbtUniqueId: "model.demo.fact_orders",
			name: "fact_orders",
			semantics: { domainCode: "ORDER", sourceRefs: [] },
		},
	],
};

test("parses only the versioned model package and exposes business metadata", () => {
	const parsed = parseModelPackage(JSON.stringify(packageJson), "demo.json", 512);
	assert.equal(parsed.metadata.projectName, "demo_project");
	assert.equal(parsed.metadata.modelCount, 1);
	assert.equal(parsed.modelPackage.sources[0].dbtUniqueId, "source.demo.orders");
});

test("context is complete only after plan, domain and confirmed source mappings exist", () => {
	const parsed = parseModelPackage(JSON.stringify(packageJson), "demo.json", 512);
	let state = reduceModelPackageImportState(createModelPackageImportState(), {
		type: "PACKAGE_LOADED",
		...parsed,
	});
	state = reduceModelPackageImportState(state, { type: "PLAN_SELECTED", planId: "plan-1" });
	assert.equal(state.modelPackage?.packageId, "demo");
	assert.equal(state.metadata?.projectName, "demo_project");
	assert.equal(state.step, 1);
	assert.equal(hasCompleteImportContext(state), false);
	state = reduceModelPackageImportState(state, { type: "DOMAIN_MAPPED", code: "ORDER", domainId: "domain-1" });
	state = reduceModelPackageImportState(state, {
		type: "SOURCE_MAPPED",
		uniqueId: "source.demo.orders",
		bindingId: "binding-1",
	});
	assert.equal(hasCompleteImportContext(state), true);
	state = reduceModelPackageImportState(state, {
		type: "PREVIEW_SUCCEEDED",
		preview: {
			runId: "run-1",
			planId: "plan-1",
			previewHash: "hash",
			summary: { total: 0, ready: 0, blocked: 0, create: 0, update: 0, skip: 0, conflict: 0 },
			items: [],
		},
	});
	assert.equal(state.step, 2);
	assert.equal(state.modelPackage, null);
});

test("preview success drops the raw package, metadata and client mappings", () => {
	const parsed = parseModelPackage(JSON.stringify(packageJson), "demo.json", 512);
	let state = reduceModelPackageImportState(createModelPackageImportState("plan-1"), {
		type: "PACKAGE_LOADED",
		...parsed,
	});
	state = reduceModelPackageImportState(state, { type: "DOMAIN_MAPPED", code: "ORDER", domainId: "domain-1" });
	state = reduceModelPackageImportState(state, {
		type: "SOURCE_MAPPED",
		uniqueId: "source.demo.orders",
		bindingId: "binding-1",
	});
	state = reduceModelPackageImportState(state, {
		type: "PREVIEW_SUCCEEDED",
		preview: {
			runId: "run-1",
			planId: "plan-1",
			previewHash: "hash",
			summary: { total: 0, ready: 0, blocked: 0, create: 0, update: 0, skip: 0, conflict: 0 },
			items: [],
		},
	});
	assert.equal(state.modelPackage, null);
	assert.equal(state.metadata, null);
	assert.deepEqual(state.domainMappings, {});
	assert.deepEqual(state.sourceMappings, {});
	assert.equal(state.runId, "run-1");
});

test("switching plan, request failure and explicit close cleanup remove sensitive package payload", () => {
	const parsed = parseModelPackage(JSON.stringify(packageJson), "demo.json", 512);
	const loaded = reduceModelPackageImportState(createModelPackageImportState("plan-1"), {
		type: "PACKAGE_LOADED",
		...parsed,
	});
	for (const cleaned of [
		reduceModelPackageImportState(loaded, { type: "PLAN_SELECTED", planId: "plan-2" }),
		reduceModelPackageImportState(loaded, { type: "REQUEST_FAILED", message: "读取失败" }),
		reduceModelPackageImportState(loaded, { type: "SENSITIVE_CLEARED" }),
	]) {
		assert.equal(cleaned.modelPackage, null);
		assert.equal(cleaned.metadata, null);
		assert.deepEqual(cleaned.domainMappings, {});
		assert.deepEqual(cleaned.sourceMappings, {});
	}
});

test("same plan selection is idempotent while a real plan switch clears the loaded package", () => {
	const parsed = parseModelPackage(JSON.stringify(packageJson), "demo.json", 512);
	const loaded = reduceModelPackageImportState(createModelPackageImportState("plan-1"), {
		type: "PACKAGE_LOADED",
		...parsed,
	});
	const same = reduceModelPackageImportState(loaded, { type: "PLAN_SELECTED", planId: "plan-1" });
	assert.equal(same.modelPackage?.packageId, "demo");
	assert.equal(same.step, 1);
	const switched = reduceModelPackageImportState(same, { type: "PLAN_SELECTED", planId: "plan-2" });
	assert.equal(switched.modelPackage, null);
	assert.equal(switched.metadata, null);
	assert.equal(switched.step, 0);
});

test("request failure ignores raw detail and retains only a sanitized diagnostic code", () => {
	const failure = reduceModelPackageImportState(createModelPackageImportState(), {
		type: "REQUEST_FAILED",
		message: "导入请求失败，请稍后重试。",
		diagnosticCode: "UPSTREAM_IMPORT_FAILURE",
		rawMessage: "select secret_value from tenant_private",
	});
	assert.equal(failure.error, "导入请求失败，请稍后重试。");
	assert.doesNotMatch(failure.error, /secret_value|tenant_private/);
	assert.equal(failure.diagnosticCode, "UPSTREAM_IMPORT_FAILURE");

	const invalid = reduceModelPackageImportState(createModelPackageImportState(), {
		type: "REQUEST_FAILED",
		message: "导入请求失败，请稍后重试。",
		diagnosticCode: "invalid-code-with-details",
		rawMessage: "raw stack",
	});
	assert.equal(invalid.diagnosticCode, MODEL_IMPORT_GENERIC_DIAGNOSTIC_CODE);
});

test("READY candidates are selected while blocked, conflict and error candidates stay disabled", () => {
	const item = (dbtUniqueId: string, action: ModelSpecImportPreviewItem["action"], severity?: string) => ({
		dbtUniqueId,
		action,
		conversionMode: action === "BLOCKED" ? "BLOCKED" : "DESIGNER_GENERATED",
		issues: severity ? [{ code: "ISSUE", severity, message: "issue" }] : [],
	}) as ModelSpecImportPreviewItem;
	assert.deepEqual(
		defaultSelectedUniqueIds([
			item("ready", "CREATE"),
			item("blocked", "BLOCKED"),
			item("conflict", "CONFLICT"),
			item("errored", "UPDATE", "ERROR"),
		]),
		["ready"],
	);
});

test("changing plan fails closed by clearing preview, result and run identity", () => {
	const state = {
		...createModelPackageImportState("plan-1", "run-1"),
		step: 3 as const,
		preview: {
			runId: "run-1",
			planId: "plan-1",
			previewHash: "hash",
			summary: { total: 1, ready: 1, blocked: 0, create: 1, update: 0, skip: 0, conflict: 0 },
			items: [],
		},
		result: {
			attemptId: "attempt-1",
			runId: "run-1",
			status: "SUCCEEDED" as const,
			summary: { total: 1, created: 1, updated: 0, skipped: 0, replayed: 0, failed: 0, blocked: 0 },
			items: [],
		},
	};
	const changed = reduceModelPackageImportState(state, { type: "PLAN_SELECTED", planId: "plan-2" });
	assert.equal(changed.planId, "plan-2");
	assert.equal(changed.runId, "");
	assert.equal(changed.preview, null);
	assert.equal(changed.result, null);
	assert.equal(changed.step, 0);
});

test("only a current PREVIEWED run or a fresh first response is applicable", () => {
	const preview = {
		runId: "run-1",
		previewHash: "hash",
		summary: { total: 0, ready: 0, blocked: 0, create: 0, update: 0, skip: 0, conflict: 0 },
		items: [],
	};
	assert.equal(isPreviewApplicable(preview, 100), true);
	assert.equal(isPreviewApplicable({ ...preview, status: "BLOCKED" }, 100), false);
	assert.equal(isPreviewApplicable({ ...preview, status: "EXPIRED" }, 100), false);
	assert.equal(isPreviewApplicable({ ...preview, status: "PREVIEWED", expiresAt: new Date(99).toISOString() }, 100), false);
	assert.equal(isPreviewApplicable({ ...preview, status: "PREVIEWED", expiresAt: new Date(101).toISOString() }, 100), true);
});

test("idempotency key stays stable for one intent and rotates when the intent changes", () => {
	let sequence = 0;
	const createKey = () => `key-${++sequence}`;
	const first = resolveModelPackageImportIdempotencySlot(null, "run:selection-a", createKey);
	const replay = resolveModelPackageImportIdempotencySlot(first, "run:selection-a", createKey);
	const changed = resolveModelPackageImportIdempotencySlot(replay, "run:selection-b", createKey);
	assert.equal(replay.key, first.key);
	assert.notEqual(changed.key, first.key);
});

test("failed retry requires permission, an editable plan, a current preview and FAILED items", () => {
	const preview = {
		runId: "run-1",
		planId: "plan-1",
		previewHash: "hash",
		status: "PREVIEWED" as const,
		expiresAt: new Date(200).toISOString(),
		summary: { total: 2, ready: 1, blocked: 1, create: 1, update: 0, skip: 0, conflict: 0 },
		items: [],
	};
	assert.equal(canRetryFailedImport(true, true, preview, 1, 100), true);
	assert.equal(canRetryFailedImport(false, true, preview, 1, 100), false);
	assert.equal(canRetryFailedImport(true, false, preview, 1, 100), false);
	assert.equal(canRetryFailedImport(true, true, { ...preview, status: "EXPIRED" }, 1, 100), false);
	assert.equal(canRetryFailedImport(true, true, preview, 0, 100), false);
});
