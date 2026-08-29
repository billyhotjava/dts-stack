import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const read = (path: string) => readFileSync(new URL(path, import.meta.url), "utf8");

const MENU_SEED = read("../../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json");
const ROLE_DEFAULTS = read("../../../../../dts-admin/src/main/resources/config/data/role-menu-defaults.json");
const ADMIN_MENU_SERVICE = read(
	"../../../../../dts-admin/src/main/java/com/yuzhi/dts/admin/service/PortalMenuService.java",
);
const PLATFORM_CONSOLE_SERVICE = read(
	"../../../../../dts-platform/src/main/java/com/yuzhi/dts/platform/service/sprint27/Sprint27ConsoleService.java",
);
const STATIC_ROUTES = read("../../../routes/sections/dashboard/static-routes.tsx");
const DYNAMIC_RESOLVER = read("../../../routes/sections/dashboard/dynamic-resolver.tsx");
const LEGACY_REDIRECT = read("./LegacyOrchestrationRedirect.tsx");
const ELT_CONSOLE = read("../../explore/etl/EltConsolePage.tsx");
const OPS_INSTANCES = read("../../ops/OpsInstancesPage.tsx");
const OPS_BACKFILL = read("../../ops/OpsBackfillPage.tsx");
const JOURNEY_STAGES = read("../../../components/journey/journeyStageState.ts");
const INGESTION_API = read("../../../api/ingestion.ts");

test("data integration is the only business menu for ingestion tasks", () => {
	assert.match(MENU_SEED, /"title": "数据集成"/);
	assert.match(MENU_SEED, /"externalLink": "\/foundation\/data-sources"/);
	for (const source of [MENU_SEED, ROLE_DEFAULTS]) {
		assert.doesNotMatch(source, /sys\.nav\.portal\.studioOrchestration/);
		assert.doesNotMatch(source, /"route": "\/explore\/etl\/orchestration"/);
		assert.doesNotMatch(source, /"externalLink": "\/explore\/etl\/orchestration"/);
	}
});

test("the retired route keeps only a compatibility redirect into data integration", () => {
	assert.match(STATIC_ROUTES, /path: "explore\/etl\/orchestration"[\s\S]{0,180}<LegacyOrchestrationRedirect \/>/);
	assert.match(
		DYNAMIC_RESOLVER,
		/"\/explore\/etl\/orchestration": "\/pages\/foundation\/access\/LegacyOrchestrationRedirect"/,
	);
	assert.doesNotMatch(DYNAMIC_RESOLVER, /pages\/explore\/etl\/OrchestrationPage/);
	assert.match(ADMIN_MENU_SERVICE, /studio\.orchestration[^\n]*LegacyOrchestrationRedirect/);
	assert.doesNotMatch(ADMIN_MENU_SERVICE, /pages\/explore\/etl\/OrchestrationPage/);
	assert.match(LEGACY_REDIRECT, /foundation\/data-sources\/access\/\$\{taskId\}/);
	assert.match(LEGACY_REDIRECT, /query\.set\("tab", "history"\)/);
	assert.match(LEGACY_REDIRECT, /Navigate to="\/foundation\/data-sources"/);
});

test("business and operations surfaces no longer link to the retired editor", () => {
	for (const source of [ELT_CONSOLE, OPS_INSTANCES, OPS_BACKFILL, JOURNEY_STAGES, PLATFORM_CONSOLE_SERVICE]) {
		assert.doesNotMatch(source, /\/explore\/etl\/orchestration/);
	}
	assert.match(PLATFORM_CONSOLE_SERVICE, /\/ops\/instances\?entryKey=AIRFLOW_DAG/);
	assert.match(JOURNEY_STAGES, /\/ops\/instances\?entryKey=AIRFLOW_DAG/);
});

test("independent orchestration UI and its unused client contract stay physically retired", () => {
	for (const file of [
		"OrchestrationPage.tsx",
		"OrchestrationTaskList.tsx",
		"OrchestrationTaskEditor.tsx",
		"OrchestrationDesignPanel.tsx",
		"OrchestrationRunsTab.tsx",
		"orchestrationDesignModel.ts",
	]) {
		assert.equal(existsSync(new URL(`../../explore/etl/${file}`, import.meta.url)), false, `${file} must stay retired`);
	}
	for (const method of [
		"getTaskDesign",
		"updateTaskDesign",
		"validateTaskDesign",
		"getTaskTopology",
		"setTaskSchedule",
	]) {
		assert.doesNotMatch(INGESTION_API, new RegExp(`async ${method}\\(`));
	}
});
