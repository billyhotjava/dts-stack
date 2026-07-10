import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const PAGE_URL = new URL("./DataManagementWorkbenchPage.tsx", import.meta.url);
const MODEL_URL = new URL("./dataManagementThemeModel.ts", import.meta.url);
const SERVICE_URL = new URL("../../api/services/goldenChainService.ts", import.meta.url);
const JOURNEY_STAGE_STATE = readFileSync(
	new URL("../../components/journey/journeyStageState.ts", import.meta.url),
	"utf8",
);
const STATIC_ROUTES = readFileSync(
	new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url),
	"utf8",
);
const DYNAMIC_RESOLVER = readFileSync(
	new URL("../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url),
	"utf8",
);
const MENU_SEED = readFileSync(
	new URL("../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
	"utf8",
);
const ROLE_DEFAULTS = readFileSync(
	new URL("../../../../dts-admin/src/main/resources/config/data/role-menu-defaults.json", import.meta.url),
	"utf8",
);
const ZH_LOCALE = readFileSync(new URL("../../locales/lang/zh_CN/sys.json", import.meta.url), "utf8");
const ADMIN_LIQUIBASE_MASTER = readFileSync(
	new URL("../../../../dts-admin/src/main/resources/config/liquibase/master.xml", import.meta.url),
	"utf8",
);
const ADMIN_MENU_CLEANUP = readFileSync(
	new URL(
		"../../../../dts-admin/src/main/resources/config/liquibase/changelog/20260617-01_remove_workbench_data_management_menu.xml",
		import.meta.url,
	),
	"utf8",
);

test("workbench is the only homepage and legacy data-management entries redirect back to it", () => {
	assert.doesNotMatch(MENU_SEED, /"key": "data-management"/);
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/workbench\/data-management"/);
	assert.doesNotMatch(ROLE_DEFAULTS, /"code": "sys\.nav\.portal\.workbenchDataManagement"/);
	assert.doesNotMatch(ROLE_DEFAULTS, /"route": "\/workbench\/data-management"/);
	assert.doesNotMatch(ZH_LOCALE, /"workbenchDataManagement": "数据管理工作台"/);
	assert.doesNotMatch(STATIC_ROUTES, /path: "workbench\/data-management"[\s\S]*?<DataManagementWorkbenchPage/);
	assert.doesNotMatch(STATIC_ROUTES, /path: "services\/consumption"[\s\S]*?<DataManagementWorkbenchPage/);
	assert.match(STATIC_ROUTES, /path: "workbench\/data-management"[\s\S]*?<WorkbenchSectionRedirect section="data-management"/);
	assert.match(STATIC_ROUTES, /path: "services\/consumption"[\s\S]*?<WorkbenchSectionRedirect section="consumption"/);
	assert.match(STATIC_ROUTES, /next\.set\("section", section\)/);
	assert.doesNotMatch(DYNAMIC_RESOLVER, /"\/workbench\/data-management": "\/pages\/workbench\/DataManagementWorkbenchPage"/);
	assert.doesNotMatch(DYNAMIC_RESOLVER, /"\/services\/consumption": "\/pages\/workbench\/DataManagementWorkbenchPage"/);
	assert.match(DYNAMIC_RESOLVER, /"\/workbench\/data-management": "\/workbench\?section=data-management"/);
	assert.match(DYNAMIC_RESOLVER, /"\/services\/consumption": "\/workbench\?section=consumption"/);
	assert.match(DYNAMIC_RESOLVER, /buildDirectRedirectPath/);
});

test("data management workbench waits for onsite business theme definition", () => {
	assert.equal(existsSync(PAGE_URL), true);
	const source = readFileSync(PAGE_URL, "utf8");

	assert.match(source, /端到端数据产品工作台/);
	assert.match(source, /待现场定义业务主题/);
	assert.match(source, /不内置演示场景/);
	assert.doesNotMatch(source, /经营分析/);
	assert.doesNotMatch(source, /质量管理/);
	assert.doesNotMatch(source, /项目交付/);
	assert.doesNotMatch(source, /客户服务/);
	assert.match(source, /数据可用/);
	assert.match(source, /治理状态/);
	assert.match(source, /消费状态/);
	assert.match(source, /运行健康/);
	assert.doesNotMatch(source, /业务消费工作台/);
});

test("workbench exposes the end-to-end data product journey from integration to service evidence", () => {
	assert.equal(existsSync(PAGE_URL), true);
	const source = readFileSync(PAGE_URL, "utf8");
	const journeySource = `${source}\n${JOURNEY_STAGE_STATE}`;

	assert.match(source, /data-testid="end-to-end-data-product-journey"/);
	assert.match(source, /productJourneyStages/);
	assert.match(source, /E2E_DATA_PRODUCT_JOURNEY/);
	assert.match(source, /withE2EJourney/);
	assert.match(source, /journey", E2E_DATA_PRODUCT_JOURNEY/);
	assert.match(source, /data-testid="end-to-end-journey-continue"/);
	assert.match(source, /resolveDataProductJourneyStageStates/);
	assert.match(journeySource, /配置数据源/);
	assert.match(journeySource, /确认数仓规划/);
	assert.match(journeySource, /套用标准包/);
	assert.match(journeySource, /进入低代码建模/);
	assert.match(journeySource, /设计指标/);
	assert.match(journeySource, /编排数据开发/);
	assert.match(journeySource, /发布数据 API/);
	assert.match(journeySource, /查看运行证据/);
	for (const label of ["负责角色", "当前缺口", "下一步"]) {
		assert.match(source, new RegExp(label));
	}
	for (const label of ["数据集成", "数仓规划", "数据标准", "维度建模", "数据指标", "数据开发", "数据服务", "运行证据"]) {
		assert.match(journeySource, new RegExp(label));
	}
	for (const route of [
		"/foundation/data-sources",
		"/explore/etl/transform",
		"/governance/subjects",
		"/catalog/metadata-management",
		"/foundation/standard-package",
		"/governance/standards/elements",
		"/studio/low-code-development",
		"/studio/sql-modeling",
		"/modeling/metric-workbench",
		"/modeling/semantic/metrics",
		"/explore/etl/scripts",
		"/explore/etl/orchestration",
		"/services/apis",
		"/bi/dashboards",
		"/ops/instances",
		"/ops/overview",
	]) {
		assert.match(journeySource, new RegExp(route.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
		assert.ok(
			DYNAMIC_RESOLVER.includes(`"${route}"`) || STATIC_ROUTES.includes(`path: "${route.slice(1)}"`),
			`${route} should resolve to a registered page`,
		);
	}
	for (const key of ["integration", "planning", "standards", "modeling", "metrics", "development", "service", "evidence"]) {
		assert.match(journeySource, new RegExp(key));
	}
	assert.match(source, /data-testid=\{`end-to-end-stage-\$\{stage\.key\}-primary`\}/);
	assert.match(source, /data-testid=\{`end-to-end-stage-\$\{stage\.key\}-secondary`\}/);
});

test("workbench exposes an explicit warehouse layer planning surface", () => {
	const source = readFileSync(PAGE_URL, "utf8");
	assert.match(source, /warehouse-layer-planning/);
	for (const layer of ["ODS_RAW", "ODS_STANDARDIZED", "DWD", "DWS", "ADS"]) {
		assert.match(source, new RegExp(layer));
	}
	assert.match(source, /分层规划/);
	assert.match(source, /进入低代码建模|进入 SQL 建模/);
});

test("first report journey can be focused from the workbench entry route", () => {
	assert.equal(existsSync(PAGE_URL), true);
	const source = readFileSync(PAGE_URL, "utf8");

	assert.match(source, /firstReportActive\?:\s*boolean/);
	assert.match(source, /data-testid="first-report-journey-guide"/);
	assert.match(source, /data-journey-active=\{firstReportActive/);
	assert.match(source, /当前首单：接入业务表生成报表/);
	assert.match(source, /首单目标/);
	for (const label of ["连接成功", "任务可运行", "报表可查看", "证据可追溯"]) {
		assert.match(source, new RegExp(label));
	}
	assert.doesNotMatch(source, /预览 SQL|模板参数 \(JSON\)|dbt source|sourceId|DDL/);
});

test("admin database migration removes legacy data-management workbench menu", () => {
	assert.match(ADMIN_LIQUIBASE_MASTER, /20260617-01_remove_workbench_data_management_menu\.xml/);
	assert.match(ADMIN_MENU_CLEANUP, /portal_menu_visibility/);
	assert.match(ADMIN_MENU_CLEANUP, /portal_menu/);
	assert.match(ADMIN_MENU_CLEANUP, /workbenchdatamanagement/i);
	assert.match(ADMIN_MENU_CLEANUP, /\/workbench\/data-management/);
	assert.match(ADMIN_MENU_CLEANUP, /data-management/);
});

test("data management workbench is driven by golden chain state through a theme model", () => {
	assert.equal(existsSync(PAGE_URL), true);
	assert.equal(existsSync(MODEL_URL), true);
	assert.equal(existsSync(SERVICE_URL), true);
	const pageSource = readFileSync(PAGE_URL, "utf8");
	const modelSource = readFileSync(MODEL_URL, "utf8");
	const serviceSource = readFileSync(SERVICE_URL, "utf8");

	assert.match(pageSource, /goldenChainService/);
	assert.match(pageSource, /buildDataManagementThemes/);
	assert.match(pageSource, /selectedTheme/);
	assert.match(pageSource, /failureReason/);
	assert.match(pageSource, /nextAction/);
	assert.match(pageSource, /evidenceRefs/);
	assert.match(modelSource, /DEFAULT_DATA_MANAGEMENT_THEMES/);
	assert.match(modelSource, /buildDataManagementThemes/);
	assert.doesNotMatch(modelSource, /keywords: \[/);
	assert.doesNotMatch(pageSource, /percent=\{83\}|已开放入口.*4|权限一致面.*6/s);

	assert.match(serviceSource, /GoldenChainSummary/);
	assert.match(serviceSource, /GoldenChainDetail/);
	assert.match(serviceSource, /url: "\/golden-chains"/);
});
