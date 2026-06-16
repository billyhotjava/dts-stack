import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const SERVICE_URL = new URL("../../api/services/workbenchService.ts", import.meta.url);
const INDEX_URL = new URL("./index.tsx", import.meta.url);
const LEADER_PAGE_URL = new URL("./LeaderOverviewPage.tsx", import.meta.url);
const REGISTRY_URL = new URL("./workbenchComponentRegistry.tsx", import.meta.url);
const DRAWER_URL = new URL("./components/WorkbenchCustomizeDrawer.tsx", import.meta.url);

const expectedComponentKeys = [
	"leader-kpi",
	"top-reports",
	"core-assets",
	"screen-strip",
	"todo",
	"bi-delivery",
	"data-sources",
	"golden-chain",
	"governance-blockers",
	"api-services",
	"ops-health",
];

test("workbench service exposes personal preference endpoints", () => {
	const source = readFileSync(SERVICE_URL, "utf8");

	assert.match(source, /WorkbenchComponentDescriptor/);
	assert.match(source, /WorkbenchPreferenceItem/);
	assert.match(source, /WorkbenchPreferencesResponse/);
	assert.match(source, /preferences:\s*\(\)\s*=>\s*apiClient\.get<WorkbenchPreferencesResponse>\(\{\s*url:\s*"\/workbench\/preferences"/s);
	assert.match(source, /savePreferences:\s*\([^)]*\)\s*=>\s*apiClient\.put<WorkbenchPreferencesResponse>\(\{\s*url:\s*"\/workbench\/preferences"/s);
	assert.match(source, /resetPreferences:\s*\(\)\s*=>\s*apiClient\.post<WorkbenchPreferencesResponse>\(\{\s*url:\s*"\/workbench\/preferences\/reset"/s);
	assert.match(source, /preferences:[\s\S]*_skipErrorToast:\s*true/);
	assert.match(source, /savePreferences:[\s\S]*_skipErrorToast:\s*true/);
	assert.match(source, /resetPreferences:[\s\S]*_skipErrorToast:\s*true/);
});

test("workbench component registry enumerates real product modules, not demo scenarios", () => {
	assert.equal(existsSync(REGISTRY_URL), true);
	const source = readFileSync(REGISTRY_URL, "utf8");

	assert.match(source, /WORKBENCH_COMPONENT_REGISTRY/);
	assert.match(source, /getWorkbenchComponent/);
	assert.match(source, /isLeaderOverviewComponentKey/);
	for (const key of expectedComponentKeys) {
		assert.match(source, new RegExp(`key:\\s*"${key}"`));
	}

	const expectedRoutes = [
		"/workbench/todo",
		"/bi/dashboards",
		"/foundation/data-sources",
		"/workbench?section=data-management",
		"/governance/quality",
		"/services/apis",
		"/ops/overview",
	];
	for (const route of expectedRoutes) {
		assert.match(source, new RegExp(route.replace(/[/?]/g, "\\$&")));
	}

	assert.doesNotMatch(source, /经营分析|质量管理|项目交付|客户服务|demo|Demo/);
	assert.doesNotMatch(source, /react-grid-layout|@dnd-kit|drag/i);
});

test("workbench page is a personalizable container over the leader overview modules", () => {
	const indexSource = readFileSync(INDEX_URL, "utf8");
	const leaderSource = readFileSync(LEADER_PAGE_URL, "utf8");

	assert.match(indexSource, /workbenchService\.preferences/);
	assert.match(indexSource, /workbenchService\.savePreferences/);
	assert.match(indexSource, /workbenchService\.resetPreferences/);
	assert.match(indexSource, /WorkbenchCustomizeDrawer/);
	assert.match(indexSource, /WORKBENCH_COMPONENT_REGISTRY/);
	assert.match(indexSource, /useSearchParams/);
	assert.match(indexSource, /customize=1/);
	assert.match(indexSource, /visibleComponentKeys/);
	assert.match(indexSource, /当前环境暂未启用个人工作台保存/);
	assert.doesNotMatch(indexSource, /<Alert/);
	assert.doesNotMatch(indexSource, /个人工作台配置暂时不可用|No static resource/);

	assert.match(leaderSource, /visibleComponentKeys\?:\s*ReadonlySet<string>/);
	for (const key of ["screen-strip", "leader-kpi", "top-reports", "core-assets"]) {
		assert.match(leaderSource, new RegExp(`isComponentVisible\\("${key}"\\)`));
	}
});

test("legacy data-management query renders the real data management section inside workbench", () => {
	const indexSource = readFileSync(INDEX_URL, "utf8");
	const pageSource = readFileSync(new URL("./DataManagementWorkbenchPage.tsx", import.meta.url), "utf8");

	assert.match(indexSource, /DataManagementWorkbenchPage/);
	assert.match(indexSource, /searchParams\.get\("section"\)/);
	assert.match(indexSource, /activeSection === "data-management"/);
	assert.match(indexSource, /<DataManagementWorkbenchPage embedded/);
	assert.match(pageSource, /embedded\?:\s*boolean/);
	assert.match(pageSource, /data-testid="data-management-workbench-section"/);
});

test("customize drawer uses checkboxes and simple order buttons for Chrome 95 compatibility", () => {
	assert.equal(existsSync(DRAWER_URL), true);
	const source = readFileSync(DRAWER_URL, "utf8");

	assert.match(source, /Checkbox/);
	assert.match(source, /上移/);
	assert.match(source, /下移/);
	assert.match(source, /保存/);
	assert.match(source, /取消/);
	assert.match(source, /恢复默认/);
	assert.match(source, /WorkbenchPreferenceItem/);
	assert.match(source, /availableComponents/);
	assert.doesNotMatch(source, /@dnd-kit|react-grid-layout|structuredClone|ResizeObserver|draggable|drag/i);
});
