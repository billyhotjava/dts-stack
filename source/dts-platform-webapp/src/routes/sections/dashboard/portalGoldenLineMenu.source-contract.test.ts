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
	readFileSync(
		new URL("../../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
		"utf8",
	),
) as { portalNavSections: MenuNode[] };
const ROLE_DEFAULT_ENTRIES = JSON.parse(
	readFileSync(
		new URL("../../../../../dts-admin/src/main/resources/config/data/role-menu-defaults.json", import.meta.url),
		"utf8",
	),
) as RoleMenuDefault[];
const ZH_PORTAL_LOCALE = (
	JSON.parse(readFileSync(new URL("../../../locales/lang/zh_CN/sys.json", import.meta.url), "utf8")) as PortalLocale
).sys.nav.portal;
const EN_PORTAL_LOCALE = (
	JSON.parse(readFileSync(new URL("../../../locales/lang/en_US/sys.json", import.meta.url), "utf8")) as PortalLocale
).sys.nav.portal;
const STATIC_ROUTES = readFileSync(new URL("./static-routes.tsx", import.meta.url), "utf8");
const DYNAMIC_RESOLVER = readFileSync(new URL("./dynamic-resolver.tsx", import.meta.url), "utf8");
const LIQUIBASE_MASTER = readFileSync(
	new URL("../../../../../dts-admin/src/main/resources/config/liquibase/master.xml", import.meta.url),
	"utf8",
);
const SPRINT80_MENU_MIGRATION_URL = new URL(
	"../../../../../dts-admin/src/main/resources/config/liquibase/changelog/20260731-02_sprint80_prototype_modeling_menu.xml",
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

const flatten = (node: MenuNode): MenuNode[] => [node, ...(node.children || []).flatMap(flatten)];
const leaves = (node: MenuNode): MenuNode[] =>
	node.children?.length ? node.children.flatMap(leaves) : node.externalLink ? [node] : [];
const escapeRegExp = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

test("primary modules follow the warehouse lifecycle order and modeling stays outside studio", () => {
	assert.deepEqual(
		MENU_SEED.portalNavSections.map((item) => item.key),
		["workbench", "resource", "data-architecture", "modeling", "studio", "governance", "consumption"],
	);
	assert.deepEqual(
		section("studio").children?.map((item) => item.key),
		["data-studio", "ops-center"],
	);
	assert.doesNotMatch(JSON.stringify(section("studio")), /data-modeling|studioDataModeling|"key":"modeling"/);

	const modeling = section("modeling");
	assert.equal(modeling.title, "数据建模");
	assert.deepEqual(
		modeling.children?.map((item) => item.key),
		[
			"modeling-home-workspace",
			"planning-system",
			"standards",
			"dimensional-modeling",
			"data-metrics",
			"modeling-tools",
			"modeling-graphs",
		],
	);
	const overview = child(modeling, "modeling-home-workspace");
	assert.equal(overview.titleKey, "sys.nav.portal.modelingHomeWorkspace");
	assert.equal(overview.title, "建模概览");
	assert.equal(overview.externalLink, "/data-modeling/home/workspace");
	assert.equal(
		flatten(modeling).some((item) => item.key === "modeling-home"),
		false,
	);
	assert.equal(
		flatten(modeling).some((item) => item.key === "modeling-home-recent"),
		false,
	);
	assert.equal(
		flatten(modeling).some((item) => item.key === "modeling-home-tasks"),
		false,
	);
});

test("modeling exposes its strategy directly after warehouse planning moves to the canonical owner", () => {
	const modeling = section("modeling");
	assert.equal(
		flatten(modeling).some((item) => item.key === "warehouse-planning"),
		false,
	);
	assert.equal(
		flatten(modeling).some((item) => item.key === "planning-spaces"),
		false,
	);
	assert.equal(child(modeling, "planning-system").title, "建模策略");
	assert.equal(child(modeling, "planning-system").externalLink, "/data-modeling/planning/system");
	assert.deepEqual(
		child(modeling, "standards").children?.map((item) => item.key),
		["standards-fields", "standards-codes", "standards-roots", "standards-dictionary", "standards-mappings"],
	);
	assert.deepEqual(
		child(modeling, "dimensional-modeling").children?.map((item) => item.key),
		["dimensions-workbench", "dimensions-reverse"],
	);
	assert.deepEqual(
		child(modeling, "data-metrics").children?.map((item) => item.key),
		["metrics-composite", "metrics-derived", "metrics-atomic", "metrics-modifiers", "metrics-periods"],
	);
	assert.deepEqual(
		child(modeling, "modeling-tools").children?.map((item) => item.key),
		["tools-toolbox", "tools-imports", "tools-exports"],
	);
	assert.deepEqual(
		child(modeling, "modeling-graphs").children?.map((item) => item.key),
		["graphs-models", "graphs-standards", "graphs-metrics"],
	);

	const modelingLeaves = leaves(modeling);
	assert.equal(modelingLeaves.length, 20);
	assert.equal(new Set(modelingLeaves.map((item) => item.externalLink)).size, 20);
	for (const leaf of modelingLeaves) {
		assert.match(leaf.externalLink || "", /^\/data-modeling\//);
	}
	assert.equal(
		modelingLeaves.some((item) => item.externalLink === "/data-modeling/home/recent"),
		false,
	);
	assert.equal(
		modelingLeaves.some((item) => item.externalLink === "/data-modeling/home/tasks"),
		false,
	);
});

test("warehouse planning is the only visible owner for five global dictionary views", () => {
	const architecture = section("data-architecture");
	assert.equal(architecture.title, "数仓规划");
	assert.deepEqual(
		leaves(architecture).map((item) => item.externalLink),
		[
			"/data-architecture?view=business-domains",
			"/data-architecture?view=processes",
			"/data-architecture?view=layers",
			"/data-architecture?view=marts",
			"/data-architecture?view=subjects",
		],
	);
	assert.doesNotMatch(
		JSON.stringify(section("modeling")),
		/planning-(?:business-categories|domains|processes|layers|marts|subjects)/,
	);
	for (const leaf of leaves(architecture)) {
		assert.ok(leaf.titleKey);
		const localeKey = leaf.titleKey?.replace("sys.nav.portal.", "") || "";
		assert.ok(ZH_PORTAL_LOCALE[localeKey]);
		assert.ok(EN_PORTAL_LOCALE[localeKey]);
		assert.deepEqual(
			ROLE_DEFAULT_ENTRIES.find((entry) => entry.code === leaf.titleKey),
			{ code: leaf.titleKey, title: leaf.title, route: leaf.externalLink, requiredRoles: [] },
		);
	}
});

test("role defaults mirror visible modeling leaves and retain old planning routes only as compatibility grants", () => {
	const modelingLeaves = leaves(section("modeling"));
	const modelingRoleDefaults = ROLE_DEFAULT_ENTRIES.filter((entry) => entry.route.startsWith("/data-modeling/"));
	assert.equal(modelingRoleDefaults.length, 26);

	const defaultsByCode = new Map(modelingRoleDefaults.map((entry) => [entry.code, entry]));
	for (const leaf of modelingLeaves) {
		assert.ok(leaf.titleKey, `${leaf.key} must define titleKey`);
		assert.deepEqual(defaultsByCode.get(leaf.titleKey || ""), {
			code: leaf.titleKey,
			title: leaf.title,
			route: leaf.externalLink,
			requiredRoles: [],
		});
	}
	assert.equal(defaultsByCode.size, 26);
	assert.deepEqual(
		modelingRoleDefaults
			.filter(
				(entry) => entry.code.startsWith("sys.nav.portal.planning") && entry.code !== "sys.nav.portal.planningSystem",
			)
			.map((entry) => entry.route)
			.sort(),
		[
			"/data-modeling/planning/business-categories",
			"/data-modeling/planning/domains",
			"/data-modeling/planning/layers",
			"/data-modeling/planning/marts",
			"/data-modeling/planning/processes",
			"/data-modeling/planning/subjects",
		],
	);
	assert.equal(
		ROLE_DEFAULT_ENTRIES.some((entry) =>
			/^\/(?:modeling|studio\/(?:sql-modeling|projects|low-code-development))/.test(entry.route),
		),
		false,
	);
});

test("every prototype modeling menu title has Chinese and English locale coverage", () => {
	for (const node of flatten(section("modeling"))) {
		assert.ok(node.titleKey, `${node.key} must define titleKey`);
		const localeKey = node.titleKey?.replace("sys.nav.portal.", "") || "";
		assert.ok(ZH_PORTAL_LOCALE[localeKey], `missing zh_CN locale for ${localeKey}`);
		assert.ok(EN_PORTAL_LOCALE[localeKey], `missing en_US locale for ${localeKey}`);
	}
});

test("new modeling routes have one page owner and old URLs are compatibility redirects only", () => {
	assert.match(STATIC_ROUTES, /path: "data-architecture"[\s\S]*?<DataArchitecturePage \/>/);
	assert.match(DYNAMIC_RESOLVER, /"\/data-architecture": "\/pages\/data-architecture\/DataArchitecturePage"/);
	assert.match(DYNAMIC_RESOLVER, /"\/governance\/subjects": "\/pages\/data-architecture\/LegacySubjectAreasRedirect"/);
	assert.match(STATIC_ROUTES, /path: "data-modeling\/\*"[\s\S]*?<DataModelingPage \/>/);
	assert.match(DYNAMIC_RESOLVER, /normalized === "\/data-modeling"/);
	assert.match(DYNAMIC_RESOLVER, /normalized\.startsWith\("\/data-modeling\/"\)/);
	assert.match(DYNAMIC_RESOLVER, /return "\/pages\/data-modeling\/DataModelingPage"/);
	assert.doesNotMatch(
		STATIC_ROUTES,
		/ModelingCompatibilityPage|ModelTemplatesPage|SqlModelingPage|ModelingWorkbenchPage/,
	);

	for (const legacyPath of [
		"studio/low-code-development",
		"studio/projects",
		"studio/sql-modeling",
		"modeling/workbench",
		"modeling/plans",
		"modeling/dimensions",
		"modeling/models",
		"modeling/metric-workbench",
		"modeling/semantic/metrics",
		"modeling/semantic/publish",
	]) {
		assert.match(
			STATIC_ROUTES,
			new RegExp(`path: "${escapeRegExp(legacyPath)}"[\\s\\S]*?<LegacyDataModelingRedirect \\/>`),
			`${legacyPath} must render only the compatibility redirect`,
		);
	}
});

test("Sprint-80 migration promotes the existing root and installs the prototype hierarchy", () => {
	assert.match(LIQUIBASE_MASTER, /20260731-02_sprint80_prototype_modeling_menu\.xml/);
	assert.equal(existsSync(SPRINT80_MENU_MIGRATION_URL), true);
	const migration = readFileSync(SPRINT80_MENU_MIGRATION_URL, "utf8");

	assert.match(migration, /SET parent_id = NULL/);
	assert.match(migration, /"sectionKey":"modeling"/);
	assert.match(migration, /jsonb_array_elements/);
	assert.match(migration, /sys\.nav\.portal\.modelingHomeWorkspace/);
	assert.match(migration, /sys\.nav\.portal\.modelingTools/);
	assert.match(migration, /sys\.nav\.portal\.modelingGraphs/);
	assert.match(migration, /\/data-modeling\/home\/workspace/);
	assert.match(migration, /\/data-modeling\/graphs\/metrics/);
	assert.match(migration, /<rollback>[\s\S]*parent_id = studio_id/);
	assert.doesNotMatch(migration, /DELETE FROM role_menu/);
});
