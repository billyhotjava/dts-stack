import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const STATIC_ROUTES = readFileSync(new URL("./static-routes.tsx", import.meta.url), "utf8");
const MENU_SEED = readFileSync(
	new URL("../../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
	"utf8",
);
const ROLE_DEFAULTS = readFileSync(
	new URL("../../../../../dts-admin/src/main/resources/config/data/role-menu-defaults.json", import.meta.url),
	"utf8",
);
const ZH_LOCALE = readFileSync(new URL("../../../locales/lang/zh_CN/sys.json", import.meta.url), "utf8");
const EN_LOCALE = readFileSync(new URL("../../../locales/lang/en_US/sys.json", import.meta.url), "utf8");

const PLATFORM_METRIC_KEYS = [
	"studioMetricModeling",
	"studioMetricWorkbench",
	"studioSemanticSubjects",
	"studioSemanticObjects",
	"studioSemanticMetrics",
	"studioSemanticModels",
	"studioSemanticPublish",
	"studioSemanticRuns",
];

test("legacy metrics bridge redirects to platform modeling instead of embedding dts-metrics", () => {
	assert.match(STATIC_ROUTES, /const MetricsServiceRedirect/);
	assert.match(STATIC_ROUTES, /metricsServiceHrefFromPlatformLocation/);
	assert.match(STATIC_ROUTES, /path: "metrics"/);
	assert.match(STATIC_ROUTES, /path: "metrics\/\*"/);
	assert.doesNotMatch(STATIC_ROUTES, /const MetricsServiceFrame/);
	assert.doesNotMatch(STATIC_ROUTES, /<iframe/);
	assert.doesNotMatch(STATIC_ROUTES, /metricsServiceEmbeddedHrefFromPlatformLocation/);
	assert.doesNotMatch(STATIC_ROUTES, /window\.location\.replace/);
});

test("metrics menu entries target platform modeling routes", () => {
	assert.match(MENU_SEED, /"externalLink": "\/modeling\/metric-workbench"/);
	assert.match(MENU_SEED, /"externalLink": "\/modeling\/semantic\/subjects"/);
	assert.match(MENU_SEED, /"externalLink": "\/modeling\/semantic\/objects"/);
	assert.match(MENU_SEED, /"externalLink": "\/modeling\/semantic\/metrics"/);
	assert.match(MENU_SEED, /"externalLink": "\/modeling\/semantic\/models"/);
	assert.match(MENU_SEED, /"externalLink": "\/modeling\/semantic\/publish"/);
	assert.match(MENU_SEED, /"externalLink": "\/modeling\/semantic\/runs"/);
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/bi-apps\/metrics/);
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/metrics\//);

	assert.match(ROLE_DEFAULTS, /"route": "\/modeling\/metric-workbench"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/modeling\/semantic\/subjects"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/modeling\/semantic\/objects"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/modeling\/semantic\/metrics"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/modeling\/semantic\/models"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/modeling\/semantic\/publish"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/modeling\/semantic\/runs"/);
	assert.doesNotMatch(ROLE_DEFAULTS, /"route": "\/bi-apps\/metrics/);
});

test("platform metric menu title keys have locale coverage", () => {
	for (const key of PLATFORM_METRIC_KEYS) {
		assert.match(MENU_SEED, new RegExp(`"titleKey": "sys\\.nav\\.portal\\.${key}"`));
		assert.match(ZH_LOCALE, new RegExp(`"${key}"`));
		assert.match(EN_LOCALE, new RegExp(`"${key}"`));
	}
});
