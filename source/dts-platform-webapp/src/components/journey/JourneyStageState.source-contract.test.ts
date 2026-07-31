import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const stateUrl = new URL("./journeyStageState.ts", import.meta.url);
const indexUrl = new URL("./index.ts", import.meta.url);
const barUrl = new URL("./JourneyContextBar.tsx", import.meta.url);
const workbenchUrl = new URL("../../pages/workbench/DataManagementWorkbenchPage.tsx", import.meta.url);

test("journey stage state model defines status, gaps, blockers and next actions", () => {
	assert.equal(existsSync(stateUrl), true, `${stateUrl.pathname} should exist`);
	const source = readFileSync(stateUrl, "utf8");

	assert.match(source, /DataProductJourneyStageStatus/);
	for (const status of ["not_started", "blocked", "ready", "in_progress", "done"]) {
		assert.match(source, new RegExp(status));
	}
	assert.match(source, /DataProductJourneyStageState/);
	for (const field of ["stageKey", "status", "owner", "gap", "nextAction", "evidenceRefs", "blocker"]) {
		assert.match(source, new RegExp(field));
	}
	assert.match(source, /resolveJourneyGap/);
	assert.match(source, /resolveJourneyNextAction/);
	assert.match(source, /resolveDataProductJourneyStageState/);
	assert.match(source, /resolveDataProductJourneyStageStates/);
	assert.match(source, /JourneyArtifactVerification/);
	for (const verification of ["verified", "invalid", "unverified"]) {
		assert.match(source, new RegExp(verification));
	}
	assert.match(source, /上下文对象无效/);
	assert.match(source, /requiredParams/);
	assert.match(source, /artifactParam/);
	assert.match(source, /apiName/);
	assert.match(source, /API 缺口/);
});

test("journey stage model covers the eight product journey stages", () => {
	const source = readFileSync(stateUrl, "utf8");

	for (const stage of [
		"integration",
		"planning",
		"standards",
		"modeling",
		"metrics",
		"development",
		"service",
		"evidence",
	]) {
		assert.match(source, new RegExp(stage));
	}
	for (const route of [
		"/foundation/data-sources",
		"/data-modeling/planning/spaces",
		"/governance/standards/elements",
		"/data-modeling/dimensions/workbench",
		"/data-modeling/metrics/atomic",
		"/data-modeling/home/workspace",
		"/explore/etl/scripts",
		"/services/apis",
		"/ops/instances",
	]) {
		assert.match(source, new RegExp(route.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
	}
	for (const gap of ["缺少数据源", "缺少标准草稿", "缺少模型", "缺少指标", "缺少服务"]) {
		assert.match(source, new RegExp(gap));
	}
});

test("workbench and context bar consume the shared journey stage state model", () => {
	const workbenchSource = readFileSync(workbenchUrl, "utf8");
	const barSource = readFileSync(barUrl, "utf8");
	const indexSource = readFileSync(indexUrl, "utf8");

	assert.match(workbenchSource, /resolveDataProductJourneyStageStates/);
	assert.match(workbenchSource, /useSearchParams/);
	assert.match(workbenchSource, /productJourneyStages/);
	assert.match(workbenchSource, /stage\.status/);
	assert.match(workbenchSource, /stage\.blocker/);
	assert.match(workbenchSource, /stage\.nextAction/);
	assert.doesNotMatch(workbenchSource, /gap:\s*"未选择数据源或同步任务未预检"/);
	assert.match(barSource, /resolveDataProductJourneyStageState/);
	assert.match(barSource, /context\.params/);
	assert.match(barSource, /stageState\.status/);
	assert.match(barSource, /stageState\.gap/);
	assert.match(barSource, /stageState\.blocker/);
	assert.match(indexSource, /journeyStageState/);
});

test("blocker states are not presented as success", () => {
	const source = readFileSync(stateUrl, "utf8");

	assert.match(source, /status:\s*"blocked"/);
	assert.match(source, /blocker:/);
	assert.match(source, /tone:\s*"warning"/);
	assert.doesNotMatch(source, /status:\s*"done"[\s\S]{0,160}blocker:/);
});
