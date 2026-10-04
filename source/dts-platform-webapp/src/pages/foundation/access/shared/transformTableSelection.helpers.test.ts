import assert from "node:assert/strict";
import test from "node:test";
import {
	buildTableMapping,
	parseTableEntries,
	resolveTableSelection,
	resolveWriterTables,
} from "./transformTableSelection.helpers";

test("parseTableEntries accepts newline and comma separated table names", () => {
	assert.deepEqual(parseTableEntries("cost_center,\nbudget_account,\nbudget_execution_snapshot"), [
		"cost_center",
		"budget_account",
		"budget_execution_snapshot",
	]);
});

test("resolveTableSelection keeps all mode authoritative", () => {
	assert.deepEqual(
		resolveTableSelection({
			mode: "all",
			selectedTables: ["stale_selected"],
			fallbackTables: ["stale_reader"],
			excludeTables: "audit_log,\ntmp_table",
		}),
		{
			selection: "all",
			includeTables: [],
			excludeTables: ["audit_log", "tmp_table"],
		},
	);
});

test("resolveTableSelection uses the canonical manual selection without reviving stale values", () => {
	assert.deepEqual(
		resolveTableSelection({
			mode: "manual",
			selectedTables: ["cost_center", "budget_account"],
			fallbackTables: ["stale_reader"],
			excludeTables: ["ignored_in_manual_mode"],
		}),
		{
			selection: "manual",
			includeTables: ["cost_center", "budget_account"],
			excludeTables: [],
		},
	);
});

test("resolveTableSelection falls back to legacy Reader tables only when no canonical selection exists", () => {
	assert.deepEqual(
		resolveTableSelection({
			mode: "manual",
			selectedTables: [],
			fallbackTables: ["legacy_reader", "legacy_reader"],
		}),
		{
			selection: "manual",
			includeTables: ["legacy_reader"],
			excludeTables: [],
		},
	);
});

test("resolveWriterTables applies the configured prefix to source-aligned tables", () => {
	assert.deepEqual(
		resolveWriterTables({
			sourceTables: ["cost_center", "budget_account"],
			explicitTables: ["cost_center", "budget_account"],
			existingTables: ["cost_center", "budget_account"],
			prefix: "ods_fin_",
		}),
		["ods_fin_cost_center", "ods_fin_budget_account"],
	);
});

test("resolveWriterTables preserves explicit custom target tables", () => {
	assert.deepEqual(
		resolveWriterTables({
			sourceTables: ["cost_center", "budget_account"],
			explicitTables: ["finance_cc", "finance_ba"],
			existingTables: [],
			prefix: "ods_fin_",
		}),
		["finance_cc", "finance_ba"],
	);
});

test("resolveWriterTables keeps the current JSON target when no visual override is supplied", () => {
	assert.deepEqual(
		resolveWriterTables({
			sourceTables: ["cost_center"],
			explicitTables: undefined,
			existingTables: ["custom_cost_center"],
			prefix: "ods_fin_",
		}),
		["custom_cost_center"],
	);
});

test("buildTableMapping reflects the latest manual source and target tables", () => {
	assert.deepEqual(
		buildTableMapping(["cost_center", "budget_account"], ["ods_fin_cost_center", "ods_fin_budget_account"]),
		[
			{ source: "cost_center", target: "ods_fin_cost_center" },
			{ source: "budget_account", target: "ods_fin_budget_account" },
		],
	);
	assert.deepEqual(buildTableMapping([], ["stale_target"]), []);
});

test("buildTableMapping does not invent targets when a custom target list is shorter", () => {
	assert.deepEqual(buildTableMapping(["cost_center", "budget_account"], ["custom_cost_center"]), [
		{ source: "cost_center", target: "custom_cost_center" },
	]);
	assert.deepEqual(buildTableMapping(["cost_center", "budget_account"], []), [
		{ source: "cost_center", target: "cost_center" },
		{ source: "budget_account", target: "budget_account" },
	]);
});
