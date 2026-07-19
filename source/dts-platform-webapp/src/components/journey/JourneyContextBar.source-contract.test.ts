import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const componentUrl = new URL("./JourneyContextBar.tsx", import.meta.url);
const hookUrl = new URL("./useDataProductJourneyContext.ts", import.meta.url);
const contextUrl = new URL("./journeyContext.ts", import.meta.url);
const indexUrl = new URL("./index.ts", import.meta.url);

const pageSources = [
	["integration", new URL("../../pages/foundation/DataSourcesPage.tsx", import.meta.url)],
	["standards", new URL("../../pages/governance/ElementsPage.tsx", import.meta.url)],
	["modeling", new URL("../../pages/modeling/ModelCenterPage.tsx", import.meta.url)],
	["development", new URL("../../pages/modeling/SqlModelingPage.tsx", import.meta.url)],
	["metrics", new URL("../../pages/modeling/MetricWorkbenchPage.tsx", import.meta.url)],
	["service", new URL("../../pages/services/ApiServicesPage.tsx", import.meta.url)],
	["evidence", new URL("../../pages/ops/OpsInstancesPage.tsx", import.meta.url)],
] as const;

test("journey context shared files exist", () => {
	for (const url of [componentUrl, hookUrl, contextUrl, indexUrl]) {
		assert.equal(existsSync(url), true, `${url.pathname} should exist`);
	}
});

test("journey context model keeps stage, return, next, evidence and context parameters together", () => {
	const source = readFileSync(contextUrl, "utf8");

	assert.match(source, /E2E_DATA_PRODUCT_JOURNEY/);
	assert.match(source, /DataProductJourneyStageKey/);
	assert.match(source, /DataProductJourneyContext/);
	assert.match(source, /buildJourneyUrl/);
	assert.match(source, /parseDataProductJourneyContext/);
	for (const key of ["sourceId", "standardDraftId", "modelSpecId", "metricId", "serviceId", "runId", "auditId"]) {
		assert.match(source, new RegExp(key));
	}
	for (const stage of ["integration", "planning", "standards", "modeling", "metrics", "development", "service", "evidence"]) {
		assert.match(source, new RegExp(stage));
	}
	for (const route of [
		"/workbench",
		"/foundation/data-sources",
		"/governance/standards/elements",
		"/modeling/models",
		"/studio/sql-modeling",
		"/modeling/metric-workbench",
		"/services/apis",
		"/ops/instances",
	]) {
		assert.match(source, new RegExp(route.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
	}
	assert.match(source, /params\.set\("journey", E2E_DATA_PRODUCT_JOURNEY\)/);
	assert.match(source, /JOURNEY_CONTEXT_PARAM_KEYS/);
});

test("journey hook only enables the bar for e2e data product context", () => {
	const source = readFileSync(hookUrl, "utf8");

	assert.match(source, /useDataProductJourneyContext/);
	assert.match(source, /useSearchParams/);
	assert.match(source, /parseDataProductJourneyContext/);
	assert.match(source, /enabled/);
	assert.match(source, /journey.*E2E_DATA_PRODUCT_JOURNEY/s);
});

test("journey bar exposes stable actions and user-facing labels", () => {
	const source = readFileSync(componentUrl, "utf8");

	assert.match(source, /export function JourneyContextBar/);
	assert.match(source, /useDataProductJourneyContext/);
	assert.match(source, /data-testid="data-product-journey-context-bar"/);
	assert.match(source, /data-testid="data-product-journey-return"/);
	assert.match(source, /data-testid="data-product-journey-next"/);
	assert.match(source, /data-testid="data-product-journey-evidence"/);
	assert.match(source, /返回工作台/);
	assert.match(source, /继续下一步/);
	assert.match(source, /查看证据/);
	assert.match(source, /当前阶段/);
	assert.match(source, /来源对象/);
	assert.doesNotMatch(source, /分析说明|模块清单|技术债/);
});

test("journey component barrel exports the shared surface", () => {
	const source = readFileSync(indexUrl, "utf8");

	assert.match(source, /JourneyContextBar/);
	assert.match(source, /useDataProductJourneyContext/);
	assert.match(source, /buildJourneyUrl/);
	assert.match(source, /parseDataProductJourneyContext/);
});

test("core pages render the journey context bar with their stage", () => {
	for (const [stage, url] of pageSources) {
		assert.equal(existsSync(url), true, `${url.pathname} should exist`);
		const source = readFileSync(url, "utf8");
		assert.match(source, /JourneyContextBar/, `${url.pathname} should import and render JourneyContextBar`);
		assert.match(source, new RegExp(`stage="${stage}"`), `${url.pathname} should declare stage ${stage}`);
	}
});

test("journey context bar surfaces artifact verification and invalid param recovery", () => {
	const barSource = readFileSync(new URL("./JourneyContextBar.tsx", import.meta.url), "utf8");

	assert.match(barSource, /validations\?:/);
	assert.match(barSource, /journey-context-unverified/);
	assert.match(barSource, /journey-context-invalid-param/);
	assert.match(barSource, /buildJourneyParamClearUrl/);
	assert.match(barSource, /待确认/);
	assert.match(barSource, /（无效）/);
});

test("journey context bar offers a dismissible join hint on menu-direct visits", () => {
	const barSource = readFileSync(new URL("./JourneyContextBar.tsx", import.meta.url), "utf8");

	assert.match(barSource, /resolveJourneyBarMode/);
	assert.match(barSource, /journey-join-hint/);
	assert.match(barSource, /journey-join-enter/);
	assert.match(barSource, /journey-join-dismiss/);
	assert.match(barSource, /sessionStorage/);
	assert.match(barSource, /进入旅程/);
});
