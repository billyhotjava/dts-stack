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
		"../../../../../dts-admin/src/main/resources/config/liquibase/changelog/20260709-01_dataworks_portal_menu_reparent.xml",
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

test("portal menu follows the DataWorks-style modeling information architecture", () => {
	assert.deepEqual(
		MENU_SEED.portalNavSections.map((item) => item.key),
		["workbench", "resource", "studio", "governance", "consumption"],
	);

	assert.equal(section("resource").title, "数据集成");
	assert.deepEqual(section("resource").children?.map((item) => item.key), [
		"connectors",
		"sources",
		"jdbcDrivers",
		"metadata",
		"ingestion",
		"changes",
	]);

	assert.equal(section("studio").title, "数据开发与运维");
	assert.deepEqual(section("studio").children?.map((item) => item.key), [
		"modeling",
		"data-studio",
		"ops-center",
	]);

	const modeling = child(section("studio"), "modeling");
	assert.equal(modeling.title, "数据建模");
	assert.deepEqual(modeling.children?.map((item) => item.key), [
		"warehouse-planning",
		"standards",
		"dimensional-modeling",
		"data-metrics",
	]);
	assert.deepEqual(child(modeling, "warehouse-planning").children?.map((item) => item.key), ["subjects", "projects"]);
	assert.equal(child(child(modeling, "warehouse-planning"), "subjects").externalLink, "/governance/subjects");
	assert.deepEqual(child(modeling, "standards").children?.map((item) => item.key), [
		"standard-package",
		"glossary",
		"elements",
		"reference",
		"templates",
	]);
	assert.deepEqual(child(modeling, "dimensional-modeling").children?.map((item) => item.key), [
		"low-code-development",
		"sql",
		"semantic-objects",
		"semantic-models",
		"dbt-files",
	]);
	assert.deepEqual(child(modeling, "data-metrics").children?.map((item) => item.key), [
		"metric-workbench",
		"semantic-metrics",
		"semantic-publish",
	]);
	assert.doesNotMatch(JSON.stringify(modeling), /semantic-subjects|semantic-runs|\/modeling\/semantic\/subjects|\/modeling\/semantic\/runs/);

	assert.deepEqual(child(section("studio"), "data-studio").children?.map((item) => item.key), ["scripts", "orchestration", "adhoc"]);
	assert.deepEqual(child(section("studio"), "ops-center").children?.map((item) => item.key), [
		"overview",
		"instances",
		"alerts",
		"backfill",
	]);

	assert.equal(section("governance").title, "数据治理");
	assert.deepEqual(section("governance").children?.map((item) => item.key), ["assets", "qualityRules", "qualityReport", "classification"]);
	assert.deepEqual(child(section("governance"), "assets").children?.map((item) => item.key), [
		"map",
		"search",
		"metadata-management",
		"detail",
		"lineage",
		"permission",
	]);

	assert.equal(section("consumption").title, "数据分析与服务");
	assert.deepEqual(section("consumption").children?.map((item) => item.key), ["bi-apps", "screens", "services"]);
	assert.equal(child(child(section("consumption"), "services"), "api").externalLink, "/services/apis");
	assert.equal(child(child(child(section("consumption"), "bi-apps"), "bi"), "dashboards").externalLink, "/bi/dashboards");
	assert.equal(child(section("consumption"), "screens").externalLink, "/bi/screens");
	assert.equal(child(child(section("consumption"), "services"), "products").externalLink, "/catalog/data-products");
});

test("golden line section titles have locale coverage and role routes stay canonical", () => {
	for (const key of [
		"dataIntegration",
		"studioCenter",
		"studioDataModeling",
		"studioDataStudio",
		"studioOpsCenter",
		"warehousePlanning",
		"dataStandards",
		"dimensionalModeling",
		"dataMetrics",
		"studioLowCodeDevelopment",
		"governanceAssets",
		"dataConsumption",
		"governanceOperations",
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
		"/catalog/data-products",
		"/bi/dashboards",
		"/bi/screens",
		"/ops/instances",
	]) {
		assert.match(ROLE_DEFAULTS, new RegExp(`"route": "${route.replaceAll("/", "\\/")}`));
	}
});

test("admin liquibase reparents persisted menus without deleting bindings", () => {
	assert.match(LIQUIBASE_MASTER, /20260709-01_dataworks_portal_menu_reparent\.xml/);
	assert.match(GOLDEN_LINE_REPARENT, /dataworks-portal-menu-reparent/);
	assert.match(GOLDEN_LINE_REPARENT, /studio_id/);
	assert.match(GOLDEN_LINE_REPARENT, /modeling_id/);
	assert.match(GOLDEN_LINE_REPARENT, /warehouse_planning_id/);
	assert.match(GOLDEN_LINE_REPARENT, /standards_id/);
	assert.match(GOLDEN_LINE_REPARENT, /dimensional_modeling_id/);
	assert.match(GOLDEN_LINE_REPARENT, /data_metrics_id/);
	assert.match(GOLDEN_LINE_REPARENT, /ops_center_id/);
	assert.match(GOLDEN_LINE_REPARENT, /parent_id = consumption_id/);
	assert.match(GOLDEN_LINE_REPARENT, /parent_id = governance_id/);
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
	assert.doesNotMatch(JSON.stringify(section("studio")), /studioSemanticRuns|\/modeling\/semantic\/runs/);
});
