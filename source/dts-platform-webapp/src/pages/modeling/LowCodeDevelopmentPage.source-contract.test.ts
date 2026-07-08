import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE = readFileSync(new URL("./LowCodeDevelopmentPage.tsx", import.meta.url), "utf8");
const METRIC_WORKBENCH = readFileSync(new URL("./MetricWorkbenchPage.tsx", import.meta.url), "utf8");
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

test("low-code development entry is wired into data development without removing advanced entries", () => {
	assert.match(MENU_SEED, /"key": "low-code-development"/);
	assert.match(MENU_SEED, /"title": "低代码开发向导"/);
	assert.match(MENU_SEED, /"externalLink": "\/studio\/low-code-development"/);
	assert.match(MENU_SEED, /"key": "sql"[\s\S]*?"externalLink": "\/studio\/sql-modeling"/);
	assert.match(MENU_SEED, /"key": "scripts"[\s\S]*?"externalLink": "\/explore\/etl\/scripts"/);
	assert.match(MENU_SEED, /"key": "orchestration"[\s\S]*?"externalLink": "\/explore\/etl\/orchestration"/);
	assert.match(MENU_SEED, /"key": "dbt-files"[\s\S]*?"externalLink": "\/modeling\/dbt-files"/);
	assert.match(ROLE_DEFAULTS, /"code": "sys\.nav\.portal\.studioLowCodeDevelopment"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/studio\/low-code-development"/);
});

test("low-code development route resolves to a real platform page", () => {
	assert.match(STATIC_ROUTES, /const LowCodeDevelopmentPage = lazy\(\(\) => import\("@\/pages\/modeling\/LowCodeDevelopmentPage"\)\)/);
	assert.match(STATIC_ROUTES, /path: "studio\/low-code-development"[\s\S]*<LowCodeDevelopmentPage/);
	assert.match(DYNAMIC_RESOLVER, /"\/studio\/low-code-development": "\/pages\/modeling\/LowCodeDevelopmentPage"/);
});

test("low-code development page keeps ELT, metrics, publishing, and ops in one journey", () => {
	for (const key of [
		"data_ready",
		"object_confirmed",
		"metric_designed",
		"model_candidate",
		"publish_ready",
		"run_evidence",
	]) {
		assert.match(PAGE, new RegExp(`key: "${key}"`));
	}
	assert.match(PAGE, /data-testid="low-code-development-page"/);
	assert.match(PAGE, /data-testid=\{`low-code-development-step-\$\{step\.key\}`\}/);
	assert.match(PAGE, /\/workbench\?section=data-management&journey=first-report/);
	assert.match(PAGE, /\/foundation\/data-sources/);
	assert.match(PAGE, /\/explore\/etl\/transform\/new/);
	assert.match(PAGE, /\/catalog\/metadata-management/);
	assert.match(PAGE, /\/modeling\/metric-workbench\?journey=low-code-development/);
	assert.match(PAGE, /\/modeling\/semantic\/models\?journey=low-code-development/);
	assert.match(PAGE, /\/modeling\/semantic\/publish\?journey=low-code-development/);
	assert.match(PAGE, /\/ops\/instances\?entryKey=DBT_RUN&journey=low-code-development/);
	assert.match(PAGE, /data-testid="advanced-development-links"/);
	assert.doesNotMatch(PAGE, /生成 SQL/);
});

test("metric workbench accepts low-code journey context and returns to the guide", () => {
	assert.match(METRIC_WORKBENCH, /useLocation/);
	assert.match(METRIC_WORKBENCH, /journey === "low-code-development"/);
	assert.match(METRIC_WORKBENCH, /data-testid="metric-workbench-low-code-context"/);
	assert.match(METRIC_WORKBENCH, /返回低代码向导/);
	assert.match(METRIC_WORKBENCH, /businessObjectId/);
	assert.match(METRIC_WORKBENCH, /target=report/);
});
