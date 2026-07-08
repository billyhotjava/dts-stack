import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

type MenuNode = {
	key: string;
	title?: string;
	titleKey?: string;
	externalLink?: string;
	children?: MenuNode[];
};

const MENU_SEED = JSON.parse(
	readFileSync(new URL("../../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url), "utf8"),
) as { portalNavSections: MenuNode[] };
const ROLE_DEFAULTS = readFileSync(
	new URL("../../../../../dts-admin/src/main/resources/config/data/role-menu-defaults.json", import.meta.url),
	"utf8",
);
const ZH_LOCALE = readFileSync(new URL("../../../locales/lang/zh_CN/sys.json", import.meta.url), "utf8");
const EN_LOCALE = readFileSync(new URL("../../../locales/lang/en_US/sys.json", import.meta.url), "utf8");
const LIQUIBASE_MASTER = readFileSync(
	new URL("../../../../../dts-admin/src/main/resources/config/liquibase/master.xml", import.meta.url),
	"utf8",
);
const GOLDEN_LINE_REPARENT = readFileSync(
	new URL(
		"../../../../../dts-admin/src/main/resources/config/liquibase/changelog/20260630-02_golden_line_portal_menu_reparent.xml",
		import.meta.url,
	),
	"utf8",
);
const RUNS_MENU_CLEANUP = readFileSync(
	new URL(
		"../../../../../dts-admin/src/main/resources/config/liquibase/changelog/20260630-03_remove_metric_modeling_runs_menu.xml",
		import.meta.url,
	),
	"utf8",
);

const section = (key: string) => {
	const found = MENU_SEED.portalNavSections.find((item) => item.key === key);
	assert.ok(found, `missing section ${key}`);
	return found;
};

const child = (node: MenuNode, key: string) => {
	const found = node.children?.find((item) => item.key === key);
	assert.ok(found, `missing child ${node.key}.${key}`);
	return found;
};

test("portal menu follows the golden line information architecture", () => {
	assert.deepEqual(
		MENU_SEED.portalNavSections.map((item) => item.key),
		["workbench", "data-foundation", "resource", "studio", "metric-modeling", "portal", "consumption", "governance", "ops"],
	);

	assert.equal(section("data-foundation").title, "数据基础");
	assert.deepEqual(section("data-foundation").children?.map((item) => item.key), ["subjects", "standards", "templates"]);
	assert.equal(child(section("data-foundation"), "subjects").externalLink, "/governance/subjects");
	assert.deepEqual(child(section("data-foundation"), "standards").children?.map((item) => item.key), [
		"glossary",
		"elements",
		"reference",
		"standard-package",
	]);

	assert.equal(section("resource").title, "数据集成");
	assert.deepEqual(section("resource").children?.map((item) => item.key), [
		"connectors",
		"sources",
		"jdbcDrivers",
		"metadata",
		"ingestion",
		"changes",
	]);

	assert.equal(section("studio").title, "数据开发");
	assert.doesNotMatch(JSON.stringify(section("studio")), /metric-modeling|studioMetricModeling/);
	assert.deepEqual(section("studio").children?.map((item) => item.key), [
		"low-code-development",
		"projects",
		"sql",
		"scripts",
		"orchestration",
		"adhoc",
		"dbt-files",
	]);

	assert.deepEqual(section("metric-modeling").children?.map((item) => item.key), [
		"metric-workbench",
		"semantic-objects",
		"semantic-metrics",
		"semantic-models",
		"semantic-publish",
	]);
	assert.doesNotMatch(JSON.stringify(section("metric-modeling")), /semantic-subjects|semantic-runs|\/modeling\/semantic\/subjects|\/modeling\/semantic\/runs/);

	assert.equal(section("portal").title, "数据资产");
	assert.deepEqual(section("consumption").children?.map((item) => item.key), ["services", "bi-apps", "screens"]);
	assert.equal(child(child(section("consumption"), "services"), "api").externalLink, "/services/apis");
	assert.equal(child(child(child(section("consumption"), "bi-apps"), "bi"), "dashboards").externalLink, "/bi/dashboards");
	assert.equal(child(section("consumption"), "screens").externalLink, "/bi/screens");

	assert.equal(section("governance").title, "治理运营");
	assert.deepEqual(section("governance").children?.map((item) => item.key), ["qualityRules", "qualityReport", "classification"]);
	assert.doesNotMatch(JSON.stringify(section("governance")), /governanceSubjects|governanceStandards|governanceTemplates/);
	assert.equal(section("ops").title, "运维与监控");
});

test("golden line section titles have locale coverage and role routes stay canonical", () => {
	for (const key of [
		"dataFoundation",
		"dataIntegration",
		"studioCenter",
		"studioLowCodeDevelopment",
		"studioMetricModeling",
		"dataPortal",
		"dataConsumption",
		"governanceOperations",
		"opsCenter",
	]) {
		assert.match(ZH_LOCALE, new RegExp(`"${key}"`));
		assert.match(EN_LOCALE, new RegExp(`"${key}"`));
	}

	for (const route of [
		"/governance/subjects",
		"/governance/standards/elements",
		"/foundation/data-sources",
		"/modeling/metric-workbench",
		"/services/apis",
		"/bi/dashboards",
		"/bi/screens",
		"/ops/instances",
	]) {
		assert.match(ROLE_DEFAULTS, new RegExp(`"route": "${route.replaceAll("/", "\\/")}`));
	}
});

test("admin liquibase reparents persisted menus without deleting bindings", () => {
	assert.match(LIQUIBASE_MASTER, /20260630-02_golden_line_portal_menu_reparent\.xml/);
	assert.match(GOLDEN_LINE_REPARENT, /golden-line-portal-menu-reparent/);
	assert.match(GOLDEN_LINE_REPARENT, /parent_id = data_foundation_id/);
	assert.match(GOLDEN_LINE_REPARENT, /parent_id = consumption_id/);
	assert.match(GOLDEN_LINE_REPARENT, /parent_id = NULL/);
	assert.match(GOLDEN_LINE_REPARENT, /metric-modeling/);
	assert.match(GOLDEN_LINE_REPARENT, /sys\.nav\.portal\.serviceCenter/);
	assert.match(GOLDEN_LINE_REPARENT, /sys\.nav\.portal\.businessIntelligenceApps/);
	assert.doesNotMatch(GOLDEN_LINE_REPARENT, /DELETE FROM portal_menu_visibility|DELETE FROM portal_menu/);
});

test("retired metric run monitor menu is removed while compatibility route stays outside the menu", () => {
	assert.match(LIQUIBASE_MASTER, /20260630-03_remove_metric_modeling_runs_menu\.xml/);
	assert.match(RUNS_MENU_CLEANUP, /studiosemanticruns/);
	assert.match(RUNS_MENU_CLEANUP, /\/modeling\/semantic\/runs/);
	assert.match(RUNS_MENU_CLEANUP, /DELETE FROM portal_menu_visibility/);
	assert.match(RUNS_MENU_CLEANUP, /DELETE FROM portal_menu m/);
	assert.doesNotMatch(JSON.stringify(section("metric-modeling")), /studioSemanticRuns|\/modeling\/semantic\/runs/);
});
