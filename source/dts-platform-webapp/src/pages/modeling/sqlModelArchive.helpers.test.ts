import assert from "node:assert/strict";
import test from "node:test";
import { buildArchivePayload, collectUnassignedModelIds } from "./sqlModelArchive.helpers";

test("collectUnassignedModelIds returns only unassigned models with ids", () => {
	const result = collectUnassignedModelIds([
		{ id: "m1", name: "ads_a" },
		{ id: "m2", name: "ads_b", planId: "p1" },
		{ name: "ads_c" },
		{ id: "m4", name: "ads_d" },
	]);

	assert.deepEqual(result, ["m1", "m4"]);
});

test("buildArchivePayload preserves existing model fields and only switches planId", () => {
	const payload = buildArchivePayload(
		{
			id: "m1",
			name: "ads_project_cockpit_summary",
			alias: "project_cockpit_summary",
			layer: "ADS",
			sourceDataSourceId: "source-1",
			schemaName: "public",
			materialized: "table",
			tags: "project,ads",
			description: "项目看板汇总模型",
			sql: "select 1 as metric",
			enabled: true,
			status: "DRAFT",
		},
		"plan-9",
	);

	assert.deepEqual(payload, {
		planId: "plan-9",
		name: "ads_project_cockpit_summary",
		alias: "project_cockpit_summary",
		layer: "ADS",
		sourceDataSourceId: "source-1",
		schemaName: "public",
		materialized: "table",
		tags: "project,ads",
		description: "项目看板汇总模型",
		sql: "select 1 as metric",
		enabled: true,
		status: "DRAFT",
	});
});
