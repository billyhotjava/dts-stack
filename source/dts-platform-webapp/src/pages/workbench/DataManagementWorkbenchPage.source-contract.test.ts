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

test("data management workbench is a workbench-level route and keeps the old consumption link compatible", () => {
	assert.match(MENU_SEED, /"key": "data-management"/);
	assert.match(MENU_SEED, /"externalLink": "\/workbench\/data-management"/);
	assert.match(ROLE_DEFAULTS, /"code": "sys\.nav\.portal\.workbenchDataManagement"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/workbench\/data-management"/);
	assert.match(ZH_LOCALE, /"workbenchDataManagement": "数据管理工作台"/);
	assert.match(STATIC_ROUTES, /DataManagementWorkbenchPage/);
	assert.match(STATIC_ROUTES, /path: "workbench\/data-management"/);
	assert.match(STATIC_ROUTES, /path: "services\/consumption"/);
	assert.match(DYNAMIC_RESOLVER, /"\/workbench\/data-management": "\/pages\/workbench\/DataManagementWorkbenchPage"/);
	assert.match(DYNAMIC_RESOLVER, /"\/services\/consumption": "\/pages\/workbench\/DataManagementWorkbenchPage"/);
});

test("data management workbench presents business themes before assets or technical centers", () => {
	assert.equal(existsSync(PAGE_URL), true);
	const source = readFileSync(PAGE_URL, "utf8");

	assert.match(source, /数据管理工作台/);
	assert.match(source, /主题看板/);
	assert.match(source, /经营分析/);
	assert.match(source, /质量管理/);
	assert.match(source, /项目交付/);
	assert.match(source, /客户服务/);
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
	assert.doesNotMatch(pageSource, /percent=\{83\}|已开放入口.*4|权限一致面.*6/s);

	assert.match(serviceSource, /GoldenChainSummary/);
	assert.match(serviceSource, /GoldenChainDetail/);
	assert.match(serviceSource, /url: "\/golden-chains"/);
});
