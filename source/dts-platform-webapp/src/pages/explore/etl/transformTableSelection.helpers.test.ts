import assert from "node:assert/strict";
import test from "node:test";
import {
	parseTableEntries,
	resolveTableSelection,
} from "./transformTableSelection.helpers";

test("parseTableEntries accepts newline and comma separated table names", () => {
	assert.deepEqual(
		parseTableEntries(
			"cost_center,\nbudget_account,\nbudget_execution_snapshot",
		),
		[
			"cost_center",
			"budget_account",
			"budget_execution_snapshot",
		],
	);
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
