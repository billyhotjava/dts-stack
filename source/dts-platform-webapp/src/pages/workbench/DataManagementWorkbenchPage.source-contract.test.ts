import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const PAGE_URL = new URL("./DataManagementWorkbenchPage.tsx", import.meta.url);
const MODEL_URL = new URL("./dataManagementThemeModel.ts", import.meta.url);
const SERVICE_URL = new URL("../../api/services/goldenChainService.ts", import.meta.url);
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

test("workbench is the only homepage and legacy data-management entries redirect back to it", () => {
	assert.doesNotMatch(MENU_SEED, /"key": "data-management"/);
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/workbench\/data-management"/);
	assert.doesNotMatch(ROLE_DEFAULTS, /"code": "sys\.nav\.portal\.workbenchDataManagement"/);
	assert.doesNotMatch(ROLE_DEFAULTS, /"route": "\/workbench\/data-management"/);
	assert.doesNotMatch(ZH_LOCALE, /"workbenchDataManagement": "数据管理工作台"/);
	assert.doesNotMatch(STATIC_ROUTES, /path: "workbench\/data-management"[\s\S]*?<DataManagementWorkbenchPage/);
	assert.doesNotMatch(STATIC_ROUTES, /path: "services\/consumption"[\s\S]*?<DataManagementWorkbenchPage/);
	assert.match(STATIC_ROUTES, /path: "workbench\/data-management"[\s\S]*?section=data-management/);
	assert.match(STATIC_ROUTES, /path: "services\/consumption"[\s\S]*?section=consumption/);
	assert.doesNotMatch(DYNAMIC_RESOLVER, /"\/workbench\/data-management": "\/pages\/workbench\/DataManagementWorkbenchPage"/);
	assert.doesNotMatch(DYNAMIC_RESOLVER, /"\/services\/consumption": "\/pages\/workbench\/DataManagementWorkbenchPage"/);
	assert.match(DYNAMIC_RESOLVER, /"\/workbench\/data-management": "\/workbench\?section=data-management"/);
	assert.match(DYNAMIC_RESOLVER, /"\/services\/consumption": "\/workbench\?section=consumption"/);
});

test("data management workbench waits for onsite business theme definition", () => {
	assert.equal(existsSync(PAGE_URL), true);
	const source = readFileSync(PAGE_URL, "utf8");

	assert.match(source, /数据管理工作台/);
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
	assert.doesNotMatch(source, /SQL|dbt|ODS|DWD|DWS|ADS|\.sql/i);
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
