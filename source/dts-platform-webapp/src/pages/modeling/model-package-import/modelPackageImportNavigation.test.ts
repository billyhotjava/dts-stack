import assert from "node:assert/strict";
import test from "node:test";
import {
	buildImportedModelRoute,
	buildImportRepairRoute,
	buildModelPackageImportQuery,
} from "./modelPackageImportNavigation";

test("shared import query preserves page filters and records open, plan and run identity", () => {
	const route = buildModelPackageImportQuery("/modeling/models", new URLSearchParams("modelType=FACT"), {
		open: true,
		planId: "plan 1",
		runId: "run-1",
	});
	assert.match(route, /^\/modeling\/models\?/);
	const query = new URL(route, "http://dts.local").searchParams;
	assert.equal(query.get("modelType"), "FACT");
	assert.equal(query.get("modelImport"), "open");
	assert.equal(query.get("planId"), "plan 1");
	assert.equal(query.get("importRunId"), "run-1");
});

test("result and repair navigation stay on canonical modeling routes", () => {
	assert.equal(
		buildImportedModelRoute("model/1", "plan-1"),
		"/modeling/models/model%2F1?activeStage=logical&planId=plan-1",
	);
	assert.match(buildImportRepairRoute("plan-1", "sources"), /^\/modeling\/plans\/plan-1\/baseline\?/);
});
