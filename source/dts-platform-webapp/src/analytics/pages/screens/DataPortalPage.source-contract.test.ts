import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const pagePath = new URL("./DataPortalPage.tsx", import.meta.url);
const previewPath = new URL("./ScreenPreviewPage.tsx", import.meta.url);
const apiPath = new URL("../../api/analyticsApi.ts", import.meta.url);
const routesPath = new URL("../../../routes/sections/dashboard/static-routes.tsx", import.meta.url);
const menuSeedPath = new URL(
	"../../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json",
	import.meta.url,
);
const roleDefaultsPath = new URL(
	"../../../../../dts-admin/src/main/resources/config/data/role-menu-defaults.json",
	import.meta.url,
);
const zhLocalePath = new URL("../../../locales/lang/zh_CN/sys.json", import.meta.url);
const enLocalePath = new URL("../../../locales/lang/en_US/sys.json", import.meta.url);

type MenuNode = {
	key?: string;
	title?: string;
	titleKey?: string;
	externalLink?: string;
	children?: MenuNode[];
};

function child(nodes: MenuNode[] | undefined, key: string): MenuNode | undefined {
	return nodes?.find((node) => node.key === key);
}

test("data portal binds persisted menus, published screens and dashboards, deep links, and four states", async () => {
	const [page, api, routes, dashboard] = await Promise.all([
		readFile(pagePath, "utf8"),
		readFile(apiPath, "utf8"),
		readFile(routesPath, "utf8"),
		readFile(new URL("../DashboardDetailPage.tsx", import.meta.url), "utf8"),
	]);

	assert.match(api, /publishedOnly\?:\s*boolean/);
	assert.match(api, /qs\.set\(["']publishedOnly["'],\s*["']true["']\)/);
	assert.match(api, /getDataPortal/);
	assert.match(api, /createDataPortalDirectory/);
	assert.match(api, /createDataPortalBinding/);
	assert.match(page, /listScreens\(\{\s*publishedOnly:\s*true\s*\}\)/);
	assert.match(page, /listDashboards\(\)/);
	assert.match(page, /getDataPortal\(\)/);
	assert.match(page, /buildDataPortalTree/);
	assert.match(page, /门户编排/);
	assert.match(page, /data-testid=["']data-portal-page["']/);
	assert.match(page, /data-testid=["']data-portal-loading["']/);
	assert.match(page, /data-testid=["']data-portal-empty["']/);
	assert.match(page, /data-testid=["']data-portal-error["']/);
	assert.match(page, /data-testid=["']data-portal-runtime["']/);
	assert.match(page, /\/bi\/screens\/\$\{encodeURIComponent\(String\(selectedScreen\.id\)\)\}\/preview/);
	assert.match(page, /mode=published/);
	assert.match(page, /embed=1/);
	assert.match(page, /DashboardDetailPage/);
	assert.match(page, /大屏管理/);
	assert.match(routes, /path:\s*["']bi\/portal["']/);
	assert.match(routes, /path:\s*["']bi\/portal\/:screenId["']/);
	assert.match(routes, /path:\s*["']bi\/portal\/item\/:bindingId["']/);
	assert.match(routes, /path:\s*["']bi\/portal\/:contentType\/:contentId["']/);
	assert.match(dashboard, /embedded\?:\s*boolean/);
});

test("screen preview preserves draft default and supports fail-closed published embed mode", async () => {
	const preview = await readFile(previewPath, "utf8");

	assert.match(preview, /previewMode/);
	assert.match(preview, /mode:\s*previewMode/);
	assert.match(preview, /fallbackDraft:\s*previewMode\s*!==\s*["']published["']/);
	assert.match(preview, /isEmbedded/);
	assert.match(preview, /!isEmbedded\s*&&/);
});

test("screen management and data portal keep separate audiences and canonical menu locations", async () => {
	const [seedSource, defaultsSource, zhLocale, enLocale] = await Promise.all([
		readFile(menuSeedPath, "utf8"),
		readFile(roleDefaultsPath, "utf8"),
		readFile(zhLocalePath, "utf8"),
		readFile(enLocalePath, "utf8"),
	]);
	const seed = JSON.parse(seedSource) as { portalNavSections: MenuNode[] };
	const defaults = JSON.parse(defaultsSource) as Array<{ code: string; title: string; route: string }>;
	const consumption = child(seed.portalNavSections, "consumption");
	const screens = child(seed.portalNavSections, "screens");
	const biAnalysis = child(child(consumption?.children, "bi-apps")?.children, "bi");
	const portal = child(biAnalysis?.children, "portal");

	assert.ok(consumption);
	assert.ok(screens);
	assert.equal(child(consumption.children, "screens"), undefined);
	assert.equal(seed.portalNavSections.indexOf(screens), seed.portalNavSections.indexOf(consumption) + 1);
	assert.deepEqual(
		{ title: screens?.title, titleKey: screens?.titleKey, route: screens?.externalLink },
		{ title: "大屏管理", titleKey: "sys.nav.portal.biScreens", route: "/bi/screens" },
	);
	assert.deepEqual(
		{ title: portal?.title, titleKey: portal?.titleKey, route: portal?.externalLink },
		{ title: "数据门户", titleKey: "sys.nav.portal.biPortal", route: "/bi/portal" },
	);
	assert.deepEqual(
		defaults.find((item) => item.code === "sys.nav.portal.biScreens"),
		{ code: "sys.nav.portal.biScreens", title: "大屏管理", route: "/bi/screens", requiredRoles: [] },
	);
	assert.deepEqual(
		defaults.find((item) => item.code === "sys.nav.portal.biPortal"),
		{ code: "sys.nav.portal.biPortal", title: "数据门户", route: "/bi/portal", requiredRoles: [] },
	);
	assert.match(zhLocale, /"biScreens":\s*"大屏管理"/);
	assert.match(zhLocale, /"biPortal":\s*"数据门户"/);
	assert.match(enLocale, /"biScreens":\s*"Screen management"/);
	assert.match(enLocale, /"biPortal":\s*"Data portal"/);
	const dynamicRoutes = await readFile(
		new URL("../../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url),
		"utf8",
	);
	assert.match(dynamicRoutes, /"\/consumption\/screens":\s*"\/bi\/screens"/);
});
