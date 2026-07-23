import assert from "node:assert/strict";
import test from "node:test";
import type { WarehousePlanSourceBindingView } from "../../api/warehousePlanApi.ts";
import {
	appendConfirmedConnectionTableSource,
	catalogSchemaOptions,
	catalogTableChoices,
	mergeCandidateCatalogTableSource,
	mergeConfirmedConnectionTableSource,
	rebaseWarehousePlanSourceDrafts,
	selectableVerifiedConnections,
	sourceInventoryRequiresFurtherConfirmation,
} from "./warehousePlanSourceRegistration.ts";

const binding = (
	bindingId: string,
	confirmationStatus: WarehousePlanSourceBindingView["confirmationStatus"],
	locator: WarehousePlanSourceBindingView["locator"] = { assetId: bindingId },
): WarehousePlanSourceBindingView => ({
	bindingId,
	sourceType: locator?.connectionId ? "CONNECTION_TABLE" : "CATALOG_TABLE",
	locator,
	confirmationStatus,
	exclusionReason: confirmationStatus === "EXCLUDED" ? `exclude-${bindingId}` : null,
	resolutionStatus: "AVAILABLE",
	freshness: "CURRENT",
});

test("only active JDBC connections with a successful verification are selectable", () => {
	const choices = selectableVerifiedConnections([
		{
			id: "warehouse",
			name: "数据仓库",
			type: "POSTGRESQL",
			jdbcUrl: "jdbc:postgresql://db/warehouse",
			status: "ACTIVE",
			lastVerifiedAt: "2026-07-22T03:19:40Z",
		},
		{
			id: "untested",
			name: "未测试连接",
			type: "MYSQL",
			jdbcUrl: "jdbc:mysql://db/app",
			status: "ACTIVE",
		},
		{
			id: "disabled",
			name: "停用连接",
			type: "POSTGRESQL",
			jdbcUrl: "jdbc:postgresql://db/archive",
			status: "DISABLED",
			lastVerifiedAt: "2026-07-22T03:19:40Z",
		},
	]);

	assert.deepEqual(choices, [
		{
			value: "warehouse",
			label: "数据仓库 · POSTGRESQL",
			verifiedAt: "2026-07-22T03:19:40Z",
		},
	]);
});

test("catalog tables are grouped by schema and exclude assets already registered in the plan", () => {
	const tables = [
		{ id: "orders", name: "orders", schema: "sales", columnCount: 12 },
		{ id: "customers", name: "customers", database: "crm", schema: "public", columnCount: 8 },
		{ id: "existing", name: "existing", schema: "sales", columnCount: 3 },
		{ id: "empty", name: "empty", schema: "sales", columnCount: 0 },
		{ id: "empty-only", name: "empty_only", schema: "empty_schema", columnCount: 0 },
		{ id: "", name: "invalid", schema: "sales", columnCount: 1 },
	];

	assert.deepEqual(catalogSchemaOptions(tables), [
		{ value: "public", label: "public" },
		{ value: "sales", label: "sales" },
	]);
	assert.deepEqual(catalogTableChoices(tables, new Set(["existing"]), "sales"), [
		{ value: "orders", label: "sales.orders · 12 个字段" },
	]);
});

test("joining a table explicitly confirms one server-resolvable connection table", () => {
	const existing = [
		{
			bindingId: "binding-1",
			confirmationStatus: "CONFIRMED" as const,
			exclusionReason: null,
		},
	];

	assert.deepEqual(
		appendConfirmedConnectionTableSource(existing, {
			connectionId: " warehouse ",
			namespace: " sales ",
			objectName: " orders ",
		}),
		[
			...existing,
			{
				sourceType: "CONNECTION_TABLE",
				locator: { connectionId: "warehouse", namespace: "sales", objectName: "orders" },
				confirmationStatus: "CONFIRMED",
				exclusionReason: null,
			},
		],
	);
	assert.throws(
		() =>
			appendConfirmedConnectionTableSource(existing, {
				connectionId: "warehouse",
				namespace: "  ",
				objectName: "orders",
			}),
		/connection table locator is required/,
	);
});

test("conflict rebase preserves local decisions by binding id and keeps concurrent additions", () => {
	const original = [binding("a", "CONFIRMED"), binding("b", "CANDIDATE")];
	const latest = [binding("b", "CONFIRMED"), binding("a", "CANDIDATE"), binding("server-added", "CONFIRMED")];

	assert.deepEqual(
		rebaseWarehousePlanSourceDrafts(
			original,
			[
				{ confirmationStatus: "EXCLUDED", exclusionReason: "local-a" },
				{ confirmationStatus: "CONFIRMED", exclusionReason: null },
			],
			latest,
		),
		[
			{ confirmationStatus: "CONFIRMED", exclusionReason: null },
			{ confirmationStatus: "EXCLUDED", exclusionReason: "local-a" },
			{ confirmationStatus: "CONFIRMED", exclusionReason: null },
		],
	);
});

test("conflict rebase keeps concurrent server decisions for fields the local user did not change", () => {
	const original = [binding("a", "CONFIRMED"), binding("b", "CANDIDATE")];
	const latest = [binding("a", "CANDIDATE"), binding("b", "CONFIRMED")];

	assert.deepEqual(
		rebaseWarehousePlanSourceDrafts(
			original,
			[
				{ confirmationStatus: "EXCLUDED", exclusionReason: "local-a" },
				{ confirmationStatus: "CANDIDATE", exclusionReason: null },
			],
			latest,
		),
		[
			{ confirmationStatus: "EXCLUDED", exclusionReason: "local-a" },
			{ confirmationStatus: "CONFIRMED", exclusionReason: null },
		],
	);
});

test("pending sources merge with the latest inventory without duplicating concurrent registrations", () => {
	const latest = [
		binding("connection-binding", "CANDIDATE", {
			connectionId: "warehouse",
			namespace: "sales",
			objectName: "orders",
		}),
		binding("catalog-binding", "CONFIRMED", { assetId: "asset-1" }),
	];
	const latestInputs = latest.map(({ bindingId, confirmationStatus, exclusionReason }) => ({
		bindingId,
		confirmationStatus,
		exclusionReason,
	}));

	assert.deepEqual(
		mergeConfirmedConnectionTableSource(latestInputs, latest, {
			connectionId: "warehouse",
			namespace: "sales",
			objectName: "orders",
		}),
		[
			{ bindingId: "connection-binding", confirmationStatus: "CONFIRMED", exclusionReason: null },
			{ bindingId: "catalog-binding", confirmationStatus: "CONFIRMED", exclusionReason: null },
		],
	);
	assert.equal(mergeCandidateCatalogTableSource(latestInputs, latest, "asset-1").length, 2);
	assert.equal(
		mergeConfirmedConnectionTableSource(latestInputs, latest, {
			connectionId: "another-warehouse",
			namespace: "sales",
			objectName: "orders",
		}).length,
		3,
	);
});

test("candidate catalog assets keep the embedded inventory open until the user confirms a decision", () => {
	assert.equal(sourceInventoryRequiresFurtherConfirmation([binding("candidate", "CANDIDATE")]), true);
	assert.equal(sourceInventoryRequiresFurtherConfirmation([binding("confirmed", "CONFIRMED")]), false);
	assert.equal(sourceInventoryRequiresFurtherConfirmation([binding("excluded", "EXCLUDED")]), false);
});
