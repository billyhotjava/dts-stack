import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const CONTRACT_URL = new URL("./canonicalModelingLanguage.ts", import.meta.url);
const LEGACY_MENU_KEY = "semantic-objects";
const LEGACY_ROUTE = "/modeling/semantic/objects";
const RETIRED_OBJECT_TERMS = ["业务对象", "语义对象"] as const;
const RETIRED_METRIC_TERMS = ["原始指标", "二次指标"] as const;
const FORBIDDEN_CUSTOMER_TERMS = [...RETIRED_OBJECT_TERMS, ...RETIRED_METRIC_TERMS];

type MenuNode = {
	key: string;
	title?: string;
	externalLink?: string;
	children?: MenuNode[];
};

type RoleMenuDefault = {
	code: string;
	title: string;
	route: string;
};

const readSource = (path: string) => readFileSync(new URL(path, import.meta.url), "utf8");

const flattenMenu = (nodes: MenuNode[]): MenuNode[] =>
	nodes.flatMap((node) => [node, ...flattenMenu(node.children ?? [])]);

test("canonical modeling language has one exact mapping for six objects, four table types and the mainline", async () => {
	assert.equal(existsSync(CONTRACT_URL), true, "missing centralized canonical modeling language contract");
	const contract = await import(CONTRACT_URL.href);

	assert.deepEqual(
		contract.CANONICAL_MODELING_OBJECT_GROUPS.map((item: { key: string; label: string; meaning: string }) => [
			item.key,
			item.label,
			item.meaning,
		]),
		[
			["BUSINESS_CATEGORY", "业务分类", "管什么业务"],
			["WAREHOUSE_LAYER", "数仓分层", "数据放在哪一层"],
			["DATA_STANDARD", "数据标准", "字段长什么样"],
			["DIMENSION", "维度", "从什么角度分析"],
			["MODEL_TABLE", "四类表", "形成什么数据表"],
			["METRIC", "指标", "数字怎么算"],
		],
	);
	assert.deepEqual([...contract.CANONICAL_MODELING_OBJECT_GROUPS[0].helpAliases], ["数据域", "主题域"]);
	assert.equal(
		contract.CANONICAL_MODELING_OBJECT_GROUPS.slice(1).some((item: { helpAliases?: string[] }) => item.helpAliases),
		false,
	);
	assert.deepEqual(
		{ ...contract.MODEL_TYPE_CUSTOMER_LABELS },
		{
			FACT: "明细表",
			DIMENSION: "维度表",
			SUMMARY: "汇总表",
			APPLICATION: "应用表",
		},
	);
	assert.deepEqual(
		{ ...contract.METRIC_TYPE_CUSTOMER_LABELS },
		{
			ATOMIC: "原子指标",
			DERIVED: "派生指标",
		},
	);
	assert.deepEqual(
		contract.CANONICAL_MODELING_MAINLINE.map((item: { label: string }) => item.label),
		["业务分类", "维度/四类表", "标准/实现", "指标"],
	);
	assert.deepEqual(
		{ ...contract.LEGACY_MODELING_OBJECT_COMPATIBILITY },
		{
			menuKey: LEGACY_MENU_KEY,
			route: LEGACY_ROUTE,
		},
	);
	assert.deepEqual([...contract.RETIRED_MODELING_OBJECT_TERMS], RETIRED_OBJECT_TERMS);
	assert.deepEqual([...contract.RETIRED_METRIC_CUSTOMER_TERMS], RETIRED_METRIC_TERMS);
	assert.doesNotMatch(
		JSON.stringify(contract.CANONICAL_MODELING_LANGUAGE),
		/status|progress|complete|stageProjection/i,
	);
});

test("menu copy permits retired object language only on the single legacy key and route pair", () => {
	const menuSeed = JSON.parse(
		readSource("../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json"),
	) as { portalNavSections: MenuNode[] };
	const roleDefaults = JSON.parse(
		readSource("../../../../dts-admin/src/main/resources/config/data/role-menu-defaults.json"),
	) as RoleMenuDefault[];
	const menuNodes = flattenMenu(menuSeed.portalNavSections);
	const compatibilityNodes = menuNodes.filter(
		(node) => node.key === LEGACY_MENU_KEY || node.externalLink === LEGACY_ROUTE,
	);

	assert.ok(compatibilityNodes.length <= 1, "legacy modeling object menu exception must remain unique");
	for (const node of menuNodes) {
		const hasRetiredTitle = RETIRED_OBJECT_TERMS.some((term) => node.title?.includes(term));
		assert.equal(
			RETIRED_METRIC_TERMS.some((term) => node.title?.includes(term)),
			false,
			`retired metric copy leaked to menu ${node.key}`,
		);
		const hasRetiredKeyOrRoute =
			/(?:business|semantic)[-/]?objects?/i.test(node.key) ||
			/(?:business|semantic)[-/]?objects?/i.test(node.externalLink ?? "");
		if (hasRetiredTitle || hasRetiredKeyOrRoute) {
			assert.equal(node.key, LEGACY_MENU_KEY, `unexpected retired modeling menu key: ${node.key}`);
			assert.equal(node.externalLink, LEGACY_ROUTE, `unexpected retired modeling menu route: ${node.externalLink}`);
		}
	}

	const compatibilityRoleDefaults = roleDefaults.filter((item) => item.route === LEGACY_ROUTE);
	assert.ok(compatibilityRoleDefaults.length <= 1, "legacy modeling object role default must remain unique");
	for (const item of roleDefaults) {
		assert.equal(
			RETIRED_METRIC_TERMS.some((term) => item.title.includes(term)),
			false,
			`retired metric copy leaked to role menu ${item.code}`,
		);
		if (RETIRED_OBJECT_TERMS.some((term) => item.title.includes(term))) {
			assert.equal(item.route, LEGACY_ROUTE, `retired role-menu copy leaked to ${item.route}`);
		}
	}
});

test("canonical route, page and API copy do not reintroduce retired customer terms", () => {
	const customerCopySources = [
		"../../routes/sections/dashboard/static-routes.tsx",
		"../../routes/sections/dashboard/dynamic-resolver.tsx",
		"./ModelingWorkbenchPage.tsx",
		"./WarehousePlanDetailPage.tsx",
		"./semantic-workspace/SemanticWorkspaceFrame.tsx",
		"../../api/modelingApi.ts",
		"./modelingVnextContract.ts",
	] as const;

	for (const path of customerCopySources) {
		const source = readSource(path);
		for (const term of FORBIDDEN_CUSTOMER_TERMS) {
			assert.equal(source.includes(term), false, `${path} reintroduced retired customer term: ${term}`);
		}
	}

	for (const path of [
		"../../routes/sections/dashboard/static-routes.tsx",
		"../../routes/sections/dashboard/dynamic-resolver.tsx",
	]) {
		const source = readSource(path);
		for (const match of source.matchAll(/["'](\/?modeling\/[^"']+)["']/g)) {
			if (/(?:business|semantic)[-/]?objects?/i.test(match[1])) {
				assert.equal(`/${match[1].replace(/^\//, "")}`, LEGACY_ROUTE, `unexpected retired modeling route: ${match[1]}`);
			}
		}
	}
});
