import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import test from "node:test";
import type { WarehousePlanSourceBindingView } from "../../api/warehousePlanApi.ts";

const helperUrl = new URL("./modelSpecSourceSelection.ts", import.meta.url);

const binding = (overrides: Partial<WarehousePlanSourceBindingView> = {}): WarehousePlanSourceBindingView => ({
	bindingId: "binding-1",
	sourceType: "CATALOG_TABLE",
	locator: { assetId: "asset-1" },
	sourceId: "ods.customer_event",
	confirmationStatus: "CONFIRMED",
	displayName: "客户事件表",
	confirmedVersion: "schema-v1",
	resolvedVersion: "schema-v1",
	resolutionStatus: "AVAILABLE",
	freshness: "CURRENT",
	...overrides,
});

test("only complete confirmed current and available plan sources are selectable", async () => {
	assert.equal(existsSync(helperUrl), true, "model source selection helper is missing");
	const { selectableModelSpecSources } = await import(helperUrl.href);
	const selectable = selectableModelSpecSources([
		binding(),
		binding({ bindingId: "candidate", confirmationStatus: "CANDIDATE" }),
		binding({ bindingId: "stale", freshness: "STALE" }),
		binding({ bindingId: "missing", resolutionStatus: "MISSING" }),
		binding({ bindingId: "no-ref", sourceId: "" }),
		binding({ bindingId: "no-version", resolvedVersion: "" }),
	]);

	assert.deepEqual(
		selectable.map((choice) => choice.value),
		["binding-1"],
	);
	assert.equal(selectable[0].label.includes("客户事件表"), true);
	assert.equal(selectable[0].label.includes("ods.customer_event"), true);
});

test("warehouse source types map to the canonical ModelSpec source kinds", async () => {
	assert.equal(existsSync(helperUrl), true, "model source selection helper is missing");
	const { selectableModelSpecSources } = await import(helperUrl.href);
	const choices = selectableModelSpecSources([
		binding({ bindingId: "connection", sourceType: "CONNECTION_TABLE" }),
		binding({ bindingId: "catalog", sourceType: "CATALOG_TABLE" }),
		binding({ bindingId: "excel", sourceType: "EXCEL_FILE" }),
		binding({ bindingId: "dbt", sourceType: "DBT_NODE" }),
	]);

	assert.deepEqual(
		choices.map((choice) => choice.kind),
		["TABLE", "TABLE", "DATASET", "DBT_MODEL"],
	);
});

test("selecting a plan source replaces server-owned identity while preserving modeling semantics", async () => {
	assert.equal(existsSync(helperUrl), true, "model source selection helper is missing");
	const { modelSpecSourceDraftFromChoice, selectableModelSpecSources } = await import(helperUrl.href);
	const [choice] = selectableModelSpecSources([
		binding({
			bindingId: "binding-new",
			sourceType: "DBT_NODE",
			sourceId: "model.project.customer_event",
			resolvedVersion: "manifest-v2",
		}),
	]);
	const result = modelSpecSourceDraftFromChoice(choice, {
		kind: "TABLE",
		ref: "forged.ref",
		layer: "DWD",
		role: "JOINED",
		sourceBindingId: "forged-binding",
		resolvedVersion: "forged-version",
		alias: "customer_event",
		joinType: "LEFT",
		joinExpression: "fact.customer_id = customer_event.id",
	});

	assert.deepEqual(result, {
		kind: "DBT_MODEL",
		ref: "model.project.customer_event",
		layer: "DWD",
		role: "JOINED",
		sourceBindingId: "binding-new",
		resolvedVersion: "manifest-v2",
		alias: "customer_event",
		joinType: "LEFT",
		joinExpression: "fact.customer_id = customer_event.id",
	});
});

test("saved sources that are no longer current stay visible only as disabled diagnostics", async () => {
	assert.equal(existsSync(helperUrl), true, "model source selection helper is missing");
	const { withPinnedExistingSources } = await import(helperUrl.href);
	const choices = withPinnedExistingSources(
		[],
		[
			{
				kind: "TABLE",
				ref: "ods.old_customer",
				layer: "ODS",
				role: "PRIMARY",
				sourceBindingId: "stale-binding",
				resolvedVersion: "old-v1",
			},
		],
	);

	assert.equal(choices.length, 1);
	assert.equal(choices[0].value, "stale-binding");
	assert.equal(choices[0].disabled, true);
	assert.match(choices[0].label, /当前不可用/);
});

test("version drift stays blocked until the user explicitly adopts the current source", async () => {
	assert.equal(existsSync(helperUrl), true, "model source selection helper is missing");
	const {
		modelSpecSourceDraftFromChoice,
		modelSpecSourceMatchesChoice,
		modelSpecSourcesAreCurrent,
		selectableModelSpecSources,
	} = await import(helperUrl.href);
	const choices = selectableModelSpecSources([
		binding({
			bindingId: "binding-1",
			sourceType: "DBT_NODE",
			sourceId: "model.project.customer_event_v2",
			resolvedVersion: "manifest-v2",
		}),
	]);
	const saved = {
		kind: "TABLE" as const,
		ref: "ods.customer_event",
		layer: "DWD" as const,
		role: "JOINED" as const,
		sourceBindingId: "binding-1",
		resolvedVersion: "schema-v1",
		alias: "customer_event",
		joinType: "LEFT" as const,
		joinExpression: "fact.customer_id = customer_event.id",
	};

	assert.equal(modelSpecSourceMatchesChoice(saved, choices[0]), false);
	assert.equal(modelSpecSourcesAreCurrent(choices, [saved]), false);

	const result = modelSpecSourceDraftFromChoice(choices[0], saved);

	assert.deepEqual(result, {
		kind: "DBT_MODEL",
		ref: "model.project.customer_event_v2",
		layer: "DWD",
		role: "JOINED",
		sourceBindingId: "binding-1",
		resolvedVersion: "manifest-v2",
		alias: "customer_event",
		joinType: "LEFT",
		joinExpression: "fact.customer_id = customer_event.id",
	});
	assert.equal(modelSpecSourcesAreCurrent(choices, [result]), true);
});

test("source inventory distinguishes empty, forbidden and unavailable recovery states", async () => {
	assert.equal(existsSync(helperUrl), true, "model source selection helper is missing");
	const { modelSpecSourceInventoryState } = await import(helperUrl.href);

	assert.equal(modelSpecSourceInventoryState([]), "EMPTY");
	assert.equal(modelSpecSourceInventoryState([binding()]), "READY");
	assert.equal(
		modelSpecSourceInventoryState([binding({ resolutionStatus: "FORBIDDEN", freshness: "UNKNOWN" })]),
		"FORBIDDEN",
	);
	assert.equal(modelSpecSourceInventoryState([binding({ freshness: "STALE" })]), "UNAVAILABLE");
});

test("source load errors distinguish missing permission from provider failures", async () => {
	assert.equal(existsSync(helperUrl), true, "model source selection helper is missing");
	const { modelSpecSourcePermissionDenied } = await import(helperUrl.href);

	assert.equal(modelSpecSourcePermissionDenied({ response: { status: 401 } }), true);
	assert.equal(modelSpecSourcePermissionDenied({ response: { status: 403 } }), true);
	assert.equal(modelSpecSourcePermissionDenied({ response: { status: 503 } }), false);
	assert.equal(modelSpecSourcePermissionDenied(new Error("offline")), false);
});
