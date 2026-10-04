import assert from "node:assert/strict";
import test from "node:test";
import { buildDrillSnapshot, normalizeDrillLevel, resolveNextDrillEntry } from "./drillRuntime.ts";
import type { DataSourceConfig, DrillLevel } from "./types.ts";

const rootDataSource: DataSourceConfig = {
	type: "sql",
	sqlConfig: { databaseId: 1, query: "select 1" },
};

const genericLevel: DrillLevel = {
	label: "Level 1",
	dataSource: { type: "api", apiConfig: { url: "/example", method: "GET" } },
	mappings: [{ sourcePath: "data.key", variableKey: "selectedKey", transform: "string" }],
};

test("normalizes a generic level and maps its click payload", () => {
	const normalized = normalizeDrillLevel(genericLevel);
	assert.ok(normalized);

	assert.deepEqual(resolveNextDrillEntry(normalized, { data: { key: "A-01" } }), {
		label: "Level 1: A-01",
		parameters: { selectedKey: "A-01" },
	});
});

test("does not enter the next level when a required source path is missing", () => {
	const normalized = normalizeDrillLevel(genericLevel);
	assert.ok(normalized);

	assert.equal(resolveNextDrillEntry(normalized, { data: {} }), null);
});

test("inherits parameters across levels by default and restores them on roll-up", () => {
	const first = normalizeDrillLevel(genericLevel);
	const second = normalizeDrillLevel({
		label: "Level 2",
		dataSource: { type: "dataset", datasetConfig: { queryBody: {} } },
		mappings: [{ sourcePath: "data.child", variableKey: "childKey", transform: "uppercase" }],
	});
	assert.ok(first);
	assert.ok(second);
	const firstEntry = resolveNextDrillEntry(first, { data: { key: "A-01" } });
	const secondEntry = resolveNextDrillEntry(second, { data: { child: "b-02" } });
	assert.ok(firstEntry);
	assert.ok(secondEntry);

	const full = buildDrillSnapshot(rootDataSource, [first, second], [firstEntry, secondEntry]);
	assert.equal(full.effectiveDataSource, second.dataSource);
	assert.deepEqual(full.queryParameters, [
		{ name: "selectedKey", value: "A-01" },
		{ name: "childKey", value: "B-02" },
	]);
	assert.deepEqual(full.breadcrumbs, [
		{ label: "全部", depth: 0 },
		{ label: "Level 1: A-01", depth: 1 },
		{ label: "Level 2: B-02", depth: 2 },
	]);

	const rolledUp = buildDrillSnapshot(rootDataSource, [first, second], [firstEntry]);
	assert.equal(rolledUp.effectiveDataSource, first.dataSource);
	assert.deepEqual(rolledUp.queryParameters, [{ name: "selectedKey", value: "A-01" }]);
});

test("clears inherited parameters when a level opts out of context inheritance", () => {
	const first = normalizeDrillLevel(genericLevel);
	const isolated = normalizeDrillLevel({
		label: "Isolated",
		dataSource: { type: "metric", metricConfig: { metricId: 7 } },
		mappings: [{ sourcePath: "value", variableKey: "currentValue", transform: "number" }],
		inheritContext: false,
	});
	assert.ok(first);
	assert.ok(isolated);
	const firstEntry = resolveNextDrillEntry(first, { data: { key: "A-01" } });
	const isolatedEntry = resolveNextDrillEntry(isolated, { value: 12 });
	assert.ok(firstEntry);
	assert.ok(isolatedEntry);

	const snapshot = buildDrillSnapshot(rootDataSource, [first, isolated], [firstEntry, isolatedEntry]);
	assert.deepEqual(snapshot.queryParameters, [{ name: "currentValue", value: "12" }]);
});

test("adapts a legacy card level without domain-specific fallback fields", () => {
	const normalized = normalizeDrillLevel({ cardId: 12, paramName: "selectedKey", label: "Legacy" });
	assert.ok(normalized);
	assert.deepEqual(normalized.dataSource, {
		type: "card",
		sourceType: "card",
		cardConfig: { cardId: 12 },
	});
	assert.deepEqual(resolveNextDrillEntry(normalized, { data: { name: "A-01" } }), {
		label: "Legacy: A-01",
		parameters: { selectedKey: "A-01" },
	});
	assert.deepEqual(resolveNextDrillEntry(normalized, { row: ["B-02", 3] }), {
		label: "Legacy: B-02",
		parameters: { selectedKey: "B-02" },
	});
	assert.equal(resolveNextDrillEntry(normalized, { 项目: "不应读取" }), null);
});

test("uses one mapping path for SQL, API, Card, Dataset, and Metric targets", () => {
	const targets: DataSourceConfig[] = [
		{ type: "sql", sqlConfig: { databaseId: 1, query: "select 1" } },
		{ type: "api", apiConfig: { url: "/example", method: "GET" } },
		{ type: "card", cardConfig: { cardId: 12 } },
		{ type: "dataset", datasetConfig: { queryBody: {} } },
		{ type: "metric", metricConfig: { metricId: 7, cardId: 12 } },
	];

	for (const target of targets) {
		const level = normalizeDrillLevel({
			label: "Neutral target",
			dataSource: target,
			mappings: [{ sourcePath: "data.key", variableKey: "selectedKey", transform: "string" }],
		});
		assert.ok(level);
		const entry = resolveNextDrillEntry(level, { data: { key: "A-01" } });
		assert.ok(entry);
		const snapshot = buildDrillSnapshot(rootDataSource, [level], [entry]);
		assert.equal(snapshot.effectiveDataSource, target);
		assert.deepEqual(snapshot.queryParameters, [{ name: "selectedKey", value: "A-01" }]);
	}
});

test("preserves legacy two-level Card behavior across drill and roll-up", () => {
	const first = normalizeDrillLevel({ cardId: 12, paramName: "parentKey", label: "Legacy 1" });
	const second = normalizeDrillLevel({ cardId: 13, paramName: "selectedKey", label: "Legacy 2" });
	assert.ok(first);
	assert.ok(second);
	const firstEntry = resolveNextDrillEntry(first, { name: "A-01" });
	const secondEntry = resolveNextDrillEntry(second, { name: "B-02" });
	assert.ok(firstEntry);
	assert.ok(secondEntry);

	const firstSnapshot = buildDrillSnapshot(rootDataSource, [first, second], [firstEntry]);
	assert.equal(firstSnapshot.effectiveDataSource.cardConfig?.cardId, 12);
	assert.deepEqual(firstSnapshot.queryParameters, [{ name: "parentKey", value: "A-01" }]);

	const secondSnapshot = buildDrillSnapshot(rootDataSource, [first, second], [firstEntry, secondEntry]);
	assert.equal(secondSnapshot.effectiveDataSource.cardConfig?.cardId, 13);
	assert.deepEqual(secondSnapshot.queryParameters, [{ name: "selectedKey", value: "B-02" }]);

	const rolledUp = buildDrillSnapshot(rootDataSource, [first, second], [firstEntry]);
	assert.equal(rolledUp.effectiveDataSource.cardConfig?.cardId, 12);
	assert.deepEqual(rolledUp.queryParameters, [{ name: "parentKey", value: "A-01" }]);
});
