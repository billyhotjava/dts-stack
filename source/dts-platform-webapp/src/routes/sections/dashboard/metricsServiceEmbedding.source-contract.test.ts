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
const SUBJECTS_MENU_CLEANUP = readFileSync(
	new URL(
		"../../../../../dts-admin/src/main/resources/config/liquibase/changelog/20260630-01_remove_metric_modeling_subjects_menu.xml",
		import.meta.url,
	),
	"utf8",
);
const ZH_LOCALE = readFileSync(new URL("../../../locales/lang/zh_CN/sys.json", import.meta.url), "utf8");
const EN_LOCALE = readFileSync(new URL("../../../locales/lang/en_US/sys.json", import.meta.url), "utf8");

const PLATFORM_METRIC_KEYS = [
	"dataMetrics",
	"metricComposite",
	"metricDerived",
	"metricAtomic",
	"metricModifiers",
	"metricPeriods",
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

test("metrics menu entries converge on prototype-owned modeling routes", () => {
	for (const route of [
		"/data-modeling/metrics/composite",
		"/data-modeling/metrics/derived",
		"/data-modeling/metrics/atomic",
		"/data-modeling/metrics/modifiers",
		"/data-modeling/metrics/periods",
	]) {
		assert.match(MENU_SEED, new RegExp(`"externalLink": "${route.replaceAll("/", "\\/")}"`));
		assert.match(ROLE_DEFAULTS, new RegExp(`"route": "${route.replaceAll("/", "\\/")}"`));
	}
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/modeling\/(?:workbench|metric-workbench|dimensions|models)"/);
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/modeling\/semantic\//);
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/modeling\/semantic\/subjects"/);
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/modeling\/semantic\/runs"/);
	assert.match(MENU_SEED, /"titleKey": "sys\.nav\.portal\.opsInstances"/);
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/bi-apps\/metrics/);
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/metrics\//);

	assert.doesNotMatch(ROLE_DEFAULTS, /"route": "\/modeling\/(?:workbench|metric-workbench|dimensions|models)"/);
	assert.doesNotMatch(ROLE_DEFAULTS, /"route": "\/modeling\/semantic\//);
	assert.doesNotMatch(ROLE_DEFAULTS, /"route": "\/modeling\/semantic\/subjects"/);
	assert.doesNotMatch(ROLE_DEFAULTS, /"route": "\/modeling\/semantic\/runs"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/ops\/instances"/);
	assert.doesNotMatch(ROLE_DEFAULTS, /"route": "\/bi-apps\/metrics/);
});

test("platform metric menu title keys have locale coverage", () => {
	for (const key of PLATFORM_METRIC_KEYS) {
		assert.match(ZH_LOCALE, new RegExp(`"${key}"`));
		assert.match(EN_LOCALE, new RegExp(`"${key}"`));
	}
	assert.match(MENU_SEED, /"titleKey": "sys\.nav\.portal\.metricAtomic"/);
});

test("legacy metric modeling subject menu is cleaned from persisted admin menus", () => {
	assert.match(SUBJECTS_MENU_CLEANUP, /studiosemanticsubjects/);
	assert.match(SUBJECTS_MENU_CLEANUP, /\/modeling\/semantic\/subjects/);
	assert.match(SUBJECTS_MENU_CLEANUP, /studio\/metric-modeling\/subjects/);
	assert.match(SUBJECTS_MENU_CLEANUP, /DELETE FROM portal_menu_visibility/);
	assert.match(SUBJECTS_MENU_CLEANUP, /DELETE FROM portal_menu m/);
});
