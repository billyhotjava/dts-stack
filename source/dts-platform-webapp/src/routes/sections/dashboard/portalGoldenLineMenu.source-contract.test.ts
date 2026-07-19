import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

type MenuNode = {
	key: string;
	title?: string;
	titleKey?: string;
	externalLink?: string;
	children?: MenuNode[];
};

type RoleMenuDefault = {
	code: string;
	title: string;
	route: string;
	requiredRoles: string[];
};

type PortalLocale = {
	sys: {
		nav: {
			portal: Record<string, string>;
		};
	};
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
const ROLE_DEFAULT_ENTRIES = JSON.parse(ROLE_DEFAULTS) as RoleMenuDefault[];
const ZH_PORTAL_LOCALE = (JSON.parse(ZH_LOCALE) as PortalLocale).sys.nav.portal;
const EN_PORTAL_LOCALE = (JSON.parse(EN_LOCALE) as PortalLocale).sys.nav.portal;
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
const MODELING_MENU_CONVERGENCE = readFileSync(
	new URL(
		"../../../../../dts-admin/src/main/resources/config/liquibase/changelog/20260712-01_modeling_menu_convergence.xml",
		import.meta.url,
	),
	"utf8",
);
const GENERIC_MODELING_WORKBENCH_MENU_URL = new URL(
	"../../../../../dts-admin/src/main/resources/config/liquibase/changelog/20260717-01_generic_modeling_workbench_menu.xml",
	import.meta.url,
);
const SPRINT67_MODELING_MENU_URL = new URL(
	"../../../../../dts-admin/src/main/resources/config/liquibase/changelog/20260719-01_sprint67_modeling_menu_convergence.xml",
	import.meta.url,
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

test("portal menu starts modeling with the generic workbench", () => {
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
		"modeling-workbench",
		"warehouse-planning",
		"standards",
		"dimensional-modeling",
		"data-metrics",
	]);
	assert.equal(child(modeling, "modeling-workbench").title, "建模工作台");
	assert.equal(child(modeling, "modeling-workbench").externalLink, "/modeling/workbench");
	assert.deepEqual(child(modeling, "warehouse-planning").children?.map((item) => item.key), ["subjects"]);
	assert.equal(child(child(modeling, "warehouse-planning"), "subjects").title, "业务分类");
	assert.equal(child(child(modeling, "warehouse-planning"), "subjects").externalLink, "/governance/subjects");
	assert.deepEqual(child(modeling, "standards").children?.map((item) => item.key), [
		"glossary",
		"elements",
		"reference",
	]);
	assert.deepEqual(child(modeling, "dimensional-modeling").children?.map((item) => item.key), [
		"semantic-objects",
		"semantic-models",
		"sql",
	]);
	assert.equal(child(child(modeling, "dimensional-modeling"), "sql").title, "高级建模（SQL/dbt）");
	assert.equal(child(child(modeling, "dimensional-modeling"), "sql").externalLink, "/studio/sql-modeling");
	assert.deepEqual(child(modeling, "data-metrics").children?.map((item) => item.key), ["metric-workbench"]);
	assert.doesNotMatch(
		JSON.stringify(modeling),
		/low-code-development|dbt-files|semantic-metrics|semantic-publish|standard-package|governanceTemplates/,
	);
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

test("dimension modeling menu converges on the canonical dimension catalog and model center", () => {
	const dimensionalModeling = child(child(section("studio"), "modeling"), "dimensional-modeling");
	const dimensionCatalog = child(dimensionalModeling, "semantic-objects");
	const modelCenter = child(dimensionalModeling, "semantic-models");

	assert.equal(dimensionCatalog.title, "维度目录");
	assert.equal(dimensionCatalog.externalLink, "/modeling/dimensions");
	assert.equal(modelCenter.title, "模型中心");
	assert.equal(modelCenter.externalLink, "/modeling/models");

	const roleDefaultByCode = new Map(ROLE_DEFAULT_ENTRIES.map((entry) => [entry.code, entry]));
	assert.deepEqual(roleDefaultByCode.get("sys.nav.portal.studioSemanticObjects"), {
		code: "sys.nav.portal.studioSemanticObjects",
		title: "维度目录",
		route: "/modeling/dimensions",
		requiredRoles: [],
	});
	assert.deepEqual(roleDefaultByCode.get("sys.nav.portal.studioSemanticModels"), {
		code: "sys.nav.portal.studioSemanticModels",
		title: "模型中心",
		route: "/modeling/models",
		requiredRoles: [],
	});

	assert.equal(ZH_PORTAL_LOCALE.studioSemanticObjects, "维度目录");
	assert.equal(ZH_PORTAL_LOCALE.studioSemanticModels, "模型中心");
	assert.equal(EN_PORTAL_LOCALE.studioSemanticObjects, "Dimension catalog");
	assert.equal(EN_PORTAL_LOCALE.studioSemanticModels, "Model center");
});

test("canonical role defaults contain only visible modeling leaves", () => {
	const roleDefaultByCode = new Map(ROLE_DEFAULT_ENTRIES.map((entry) => [entry.code, entry]));
	assert.deepEqual(roleDefaultByCode.get("sys.nav.portal.governanceSubjects"), {
		code: "sys.nav.portal.governanceSubjects",
		title: "业务分类",
		route: "/governance/subjects",
		requiredRoles: [],
	});
	assert.deepEqual(roleDefaultByCode.get("sys.nav.portal.studioSqlModeling"), {
		code: "sys.nav.portal.studioSqlModeling",
		title: "高级建模（SQL/dbt）",
		route: "/studio/sql-modeling",
		requiredRoles: [],
	});
	for (const retired of [
		"sys.nav.portal.governanceStandardPackage",
		"sys.nav.portal.governanceTemplates",
		"sys.nav.portal.studioLowCodeDevelopment",
		"sys.nav.portal.studioDbtFiles",
		"sys.nav.portal.studioSemanticMetrics",
		"sys.nav.portal.studioSemanticPublish",
	]) {
		assert.equal(roleDefaultByCode.has(retired), false, `${retired} must not be rebound for a new installation`);
	}
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
		"studioBusinessProcesses",
		"governanceAssets",
		"dataConsumption",
		"governanceOperations",
	]) {
		assert.match(ZH_LOCALE, new RegExp(`"${key}"`));
		assert.match(EN_LOCALE, new RegExp(`"${key}"`));
	}

	for (const route of [
		"/modeling/workbench",
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
	assert.match(ZH_LOCALE, /"studioBusinessProcesses": "建模工作台"/);
	assert.match(EN_LOCALE, /"studioBusinessProcesses": "Modeling workbench"/);
});

test("generic modeling workbench migration keeps the role-bound menu row", () => {
	assert.match(LIQUIBASE_MASTER, /20260717-01_generic_modeling_workbench_menu\.xml/);
	assert.equal(existsSync(GENERIC_MODELING_WORKBENCH_MENU_URL), true);
	const migration = readFileSync(GENERIC_MODELING_WORKBENCH_MENU_URL, "utf8");
	assert.match(migration, /studioBusinessProcesses/);
	assert.match(migration, /\/modeling\/workbench/);
	assert.doesNotMatch(migration, /DELETE FROM portal_menu_visibility|DELETE FROM portal_menu/);
});

test("Sprint-67 menu migration soft-deletes duplicates and preserves role visibility", () => {
	assert.match(LIQUIBASE_MASTER, /20260719-01_sprint67_modeling_menu_convergence\.xml/);
	assert.equal(existsSync(SPRINT67_MODELING_MENU_URL), true);
	const migration = readFileSync(SPRINT67_MODELING_MENU_URL, "utf8");
	for (const code of [
		"studioDbtFiles",
		"studioLowCodeDevelopment",
		"studioSemanticMetrics",
		"studioSemanticPublish",
	]) {
		assert.match(migration, new RegExp(code));
	}
	assert.match(migration, /deleted = TRUE/);
	assert.match(migration, /高级建模（SQL\/dbt）/);
	assert.doesNotMatch(migration, /DELETE FROM portal_menu_visibility|DELETE FROM portal_menu/);
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

test("modeling menu convergence reparents low-code and renames project spaces without deleting bindings", () => {
	assert.match(LIQUIBASE_MASTER, /20260712-01_modeling_menu_convergence\.xml/);
	assert.match(MODELING_MENU_CONVERGENCE, /studioLowCodeDevelopment/);
	assert.match(MODELING_MENU_CONVERGENCE, /studioDataModeling/);
	assert.match(MODELING_MENU_CONVERGENCE, /studioProjects/);
	assert.match(MODELING_MENU_CONVERGENCE, /studioBusinessProcesses/);
	assert.match(MODELING_MENU_CONVERGENCE, /parent_id = modeling_id/);
	assert.match(MODELING_MENU_CONVERGENCE, /parent_id = warehouse_planning_id/);
	assert.match(MODELING_MENU_CONVERGENCE, /role_menu|portal_menu/);
});

test("retired metric run monitor menu is removed while compatibility route stays outside the menu", () => {
	assert.match(LIQUIBASE_MASTER, /20260630-03_remove_metric_modeling_runs_menu\.xml/);
	assert.match(RUNS_MENU_CLEANUP, /studiosemanticruns/);
	assert.match(RUNS_MENU_CLEANUP, /\/modeling\/semantic\/runs/);
	assert.match(RUNS_MENU_CLEANUP, /DELETE FROM portal_menu_visibility/);
	assert.match(RUNS_MENU_CLEANUP, /DELETE FROM portal_menu m/);
	assert.doesNotMatch(JSON.stringify(section("studio")), /studioSemanticRuns|\/modeling\/semantic\/runs/);
});
