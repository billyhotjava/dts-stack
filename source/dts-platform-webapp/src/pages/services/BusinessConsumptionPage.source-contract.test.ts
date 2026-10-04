import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const PAGE_URL = new URL("../workbench/DataManagementWorkbenchPage.tsx", import.meta.url);
const BUSINESS_CONSUMPTION_URL = new URL("./BusinessConsumptionPage.tsx", import.meta.url);
const MODEL_URL = new URL("../workbench/dataManagementThemeModel.ts", import.meta.url);
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
const ZH_LOCALE = readFileSync(new URL("../../locales/lang/zh_CN/sys.json", import.meta.url), "utf8");

test("legacy business consumption route redirects to the single workbench homepage", () => {
	assert.doesNotMatch(MENU_SEED, /"key": "data-management"/);
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/workbench\/data-management"/);
	assert.doesNotMatch(MENU_SEED, /"key": "consumption"[\s\S]*?"externalLink": "\/services\/consumption"/);
	assert.doesNotMatch(ZH_LOCALE, /"workbenchDataManagement": "数据管理工作台"/);
	assert.doesNotMatch(STATIC_ROUTES, /path: "services\/consumption"[\s\S]*?<DataManagementWorkbenchPage/);
	assert.match(STATIC_ROUTES, /path: "services\/consumption"[\s\S]*?<WorkbenchSectionRedirect section="consumption"/);
	assert.match(STATIC_ROUTES, /next\.set\("section", section\)/);
	assert.match(DYNAMIC_RESOLVER, /"\/services\/consumption": "\/workbench\?section=consumption"/);
	assert.match(DYNAMIC_RESOLVER, /buildDirectRedirectPath/);
});

test("data management workbench does not ship builtin demo business themes", () => {
	assert.equal(existsSync(PAGE_URL), true);
	const source = readFileSync(PAGE_URL, "utf8");

	assert.match(source, /端到端数据产品工作台/);
	assert.match(source, /待现场定义业务主题/);
	assert.match(source, /不内置演示场景/);
	assert.doesNotMatch(source, /经营分析/);
	assert.doesNotMatch(source, /质量管理/);
	assert.doesNotMatch(source, /项目交付/);
	assert.doesNotMatch(source, /客户服务/);
});

test("data management workbench exposes a no-SQL first report journey", () => {
	const source = readFileSync(PAGE_URL, "utf8");

	for (const label of ["接入一张业务表", "选择业务表", "生成同步任务", "生成报表", "查看运行证据"]) {
		assert.match(source, new RegExp(label));
	}
	for (const route of ["/foundation/data-sources", "/bi/explore", "/ops/overview"]) {
		assert.match(source, new RegExp(route.replace(/[/?]/g, "\\$&")));
	}
	assert.doesNotMatch(source, /预览 SQL|模板参数 \(JSON\)|dbt source|sourceId|DDL/);
});

test("data management workbench is driven by golden chain runtime state", () => {
	assert.equal(existsSync(SERVICE_URL), true);
	assert.equal(existsSync(MODEL_URL), true);
	const pageSource = readFileSync(PAGE_URL, "utf8");
	const modelSource = readFileSync(MODEL_URL, "utf8");
	const serviceSource = readFileSync(SERVICE_URL, "utf8");

	assert.match(pageSource, /goldenChainService/);
	assert.match(pageSource, /buildDataManagementThemes/);
	assert.match(pageSource, /failureReason/);
	assert.match(pageSource, /nextAction/);
	assert.match(pageSource, /evidenceRefs/);
	assert.match(modelSource, /DEFAULT_DATA_MANAGEMENT_THEMES/);
	assert.doesNotMatch(modelSource, /keywords: \[/);
	assert.doesNotMatch(pageSource, /percent=\{83\}|已开放入口.*4|权限一致面.*6/s);

	assert.match(serviceSource, /GoldenChainSummary/);
	assert.match(serviceSource, /GoldenChainDetail/);
	assert.match(serviceSource, /url: "\/golden-chains"/);
	assert.match(serviceSource, /\/golden-chains\/\$\{encodeURIComponent\(chainKey\)\}/);
});

test("business consumption metric entry uses the prototype-owned atomic metric surface", () => {
	const source = readFileSync(BUSINESS_CONSUMPTION_URL, "utf8");

	assert.match(source, /route: "\/data-modeling\/metrics\/atomic"/);
	assert.doesNotMatch(source, /\/modeling\/metric-workbench/);
	assert.doesNotMatch(source, /\/bi-apps\/metrics\/center/);
	assert.doesNotMatch(source, /\/metrics\/center/);
});
