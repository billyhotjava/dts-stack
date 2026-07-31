import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const gateUrl = new URL("./gateEvidence.ts", import.meta.url);
const indexUrl = new URL("./index.ts", import.meta.url);
const behaviorTestUrl = new URL("./gateEvidence.test.ts", import.meta.url);

test("gate evidence defines dbt-style structured checks with a verdict", () => {
	assert.equal(existsSync(gateUrl), true, `${gateUrl.pathname} should exist`);
	const source = readFileSync(gateUrl, "utf8");

	for (const key of ["standardsCoverage", "compile", "test", "run"]) {
		assert.match(source, new RegExp(key));
	}
	assert.match(source, /"ready" \| "missing" \| "blocked"/);
	assert.match(source, /"pass" \| "warn" \| "fail"/);
	assert.match(source, /export const resolveGateVerdict/);
	assert.match(source, /export const buildGateEvidence/);
});

test("gate evidence marks api gaps and joins artifact validations", () => {
	const source = readFileSync(gateUrl, "utf8");

	assert.match(source, /GET \/api\/dbt\/test-results/);
	assert.match(source, /GET \/api\/ops\/run-evidence/);
	assert.match(source, /validations/);
	assert.match(source, /buildJourneyUrl/);
	assert.doesNotMatch(source, /console\.(log|error|warn)/);
});

test("gate evidence is exported from the barrel and has behavior tests", () => {
	const indexSource = readFileSync(indexUrl, "utf8");
	assert.match(indexSource, /gateEvidence/);
	for (const name of ["buildGateEvidence", "resolveGateVerdict"]) {
		assert.match(indexSource, new RegExp(name));
	}
	assert.equal(existsSync(behaviorTestUrl), true, "gateEvidence.test.ts (vitest) should exist");
});

test("gate evidence summary remains available on the retained workbench and ops surfaces", () => {
	const summaryUrl = new URL("./GateEvidenceSummary.tsx", import.meta.url);
	assert.equal(existsSync(summaryUrl), true, "GateEvidenceSummary.tsx should exist");
	const summarySource = readFileSync(summaryUrl, "utf8");
	assert.match(summarySource, /gate-evidence-summary/);
	assert.match(summarySource, /gate-evidence-verdict/);
	assert.match(summarySource, /gate-check-/);
	assert.match(summarySource, /JourneyGateEvidenceSummary/);

	const workbenchSource = readFileSync(
		new URL("../../pages/workbench/DataManagementWorkbenchPage.tsx", import.meta.url),
		"utf8",
	);
	assert.match(workbenchSource, /GateEvidenceSummary/);
	assert.match(workbenchSource, /buildGateEvidence/);

	const opsSource = readFileSync(new URL("../../pages/ops/OpsInstancesPage.tsx", import.meta.url), "utf8");
	assert.match(opsSource, /JourneyGateEvidenceSummary/);
});
