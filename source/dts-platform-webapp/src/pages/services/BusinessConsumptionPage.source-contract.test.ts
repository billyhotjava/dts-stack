import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const PAGE_URL = new URL("./BusinessConsumptionPage.tsx", import.meta.url);
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

test("business consumption workbench is reachable from data service center", () => {
	assert.match(MENU_SEED, /"key": "consumption"/);
	assert.match(MENU_SEED, /"externalLink": "\/services\/consumption"/);
	assert.match(ZH_LOCALE, /"servicesConsumption": "业务消费工作台"/);
	assert.match(STATIC_ROUTES, /BusinessConsumptionPage/);
	assert.match(STATIC_ROUTES, /path: "services\/consumption"/);
	assert.match(DYNAMIC_RESOLVER, /"\/services\/consumption": "\/pages\/services\/BusinessConsumptionPage"/);
});

test("business consumption workbench presents F5 as a no-SQL customer workflow", () => {
	assert.equal(existsSync(PAGE_URL), true);
	const source = readFileSync(PAGE_URL, "utf8");

	assert.match(source, /业务消费工作台/);
	assert.match(source, /报表数据集/);
	assert.match(source, /指标入口/);
	assert.match(source, /数据 API/);
	assert.match(source, /权限一致/);
	assert.match(source, /客户验收包/);
	assert.doesNotMatch(source, /SQL|dbt|\.sql/i);
});

test("business consumption workbench is driven by golden chain runtime state", () => {
	assert.equal(existsSync(SERVICE_URL), true);
	const pageSource = readFileSync(PAGE_URL, "utf8");
	const serviceSource = readFileSync(SERVICE_URL, "utf8");

	assert.match(pageSource, /goldenChainService/);
	assert.match(pageSource, /setChains/);
	assert.match(pageSource, /stageSnapshots/);
	assert.match(pageSource, /failureReason/);
	assert.match(pageSource, /nextAction/);
	assert.match(pageSource, /evidenceRef/);
	assert.doesNotMatch(pageSource, /percent=\{83\}|已开放入口.*4|权限一致面.*6/s);

	assert.match(serviceSource, /GoldenChainSummary/);
	assert.match(serviceSource, /GoldenChainDetail/);
	assert.match(serviceSource, /url: "\/golden-chains"/);
	assert.match(serviceSource, /\/golden-chains\/\$\{encodeURIComponent\(chainKey\)\}/);
});
