import assert from "node:assert/strict";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { extname, relative, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const CONTRACT_URL = new URL("./canonicalModelingLanguage.ts", import.meta.url);
const LEGACY_MENU_KEY = "semantic-objects";
const LEGACY_ROUTE = "/modeling/semantic/objects";
const RETIRED_OBJECT_TERMS = ["业务对象", "语义对象"] as const;
const RETIRED_METRIC_TERMS = ["原始指标", "二次指标"] as const;
const REPLACEMENT_OBJECT_TERMS = ["业务实体", "模型对象", "数据对象", "语义实体"] as const;
const FORBIDDEN_CUSTOMER_TERMS = [
	...RETIRED_OBJECT_TERMS,
	...RETIRED_METRIC_TERMS,
	...REPLACEMENT_OBJECT_TERMS,
] as const;
const MODELING_SOURCE_ROOT = fileURLToPath(new URL("./", import.meta.url));
const EXPECTED_OBJECT_LABELS = ["业务分类", "数仓分层", "数据标准", "维度", "四类表", "指标"] as const;

type ForbiddenCustomerTerm = (typeof FORBIDDEN_CUSTOMER_TERMS)[number];

type CustomerSurfaceManifest = {
	sourceRoot: string;
	recursive: boolean;
	sourceExtensions: readonly string[];
	excludedSourceSuffixes: readonly string[];
	definitionFiles: readonly string[];
	objectLabels: readonly string[];
};

// Sprint-67 only freezes the existing migration debt. A new customer source gets
// a zero allowance automatically, and no file receives an allowance for a
// replacement alias or retired metric term.
const RETIRED_TERM_MIGRATION_BASELINE = Object.freeze({
	"LowCodeDevelopmentPage.tsx": { 业务对象: 3 },
	"MetricWorkbenchPage.tsx": { 业务对象: 7 },
	"ModelTemplatesPage.tsx": { 业务对象: 1 },
	"SemanticMetricsPage.tsx": { 业务对象: 4 },
	"SemanticObjectsPage.tsx": { 业务对象: 9 },
	"businessObjectCode.ts": { 业务对象: 1 },
	"metric-workbench/MetricCanvas.tsx": { 业务对象: 3 },
	"metric-workbench/MetricDetailPanel.tsx": { 业务对象: 2 },
	"metric-workbench/SubjectBrowserPanel.tsx": { 业务对象: 3 },
	"metric-workbench/metricCanvas.helpers.ts": { 业务对象: 1 },
	"semantic-workspace/BusinessModelingContextBar.tsx": { 业务对象: 1 },
	"semantic-workspace/ModelingConceptCards.tsx": { 业务对象: 2 },
} satisfies Record<string, Partial<Record<ForbiddenCustomerTerm, number>>>);

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

const discoverCustomerSources = (manifest: CustomerSurfaceManifest): string[] => {
	const discovered: string[] = [];
	const visit = (directory: string) => {
		for (const entry of readdirSync(directory, { withFileTypes: true })) {
			const absolutePath = resolve(directory, entry.name);
			if (entry.isDirectory()) {
				if (manifest.recursive) visit(absolutePath);
				continue;
			}
			const relativePath = relative(MODELING_SOURCE_ROOT, absolutePath).replaceAll("\\", "/");
			if (!manifest.sourceExtensions.includes(extname(entry.name))) continue;
			if (manifest.excludedSourceSuffixes.some((suffix) => entry.name.endsWith(suffix))) continue;
			if (manifest.definitionFiles.includes(relativePath)) continue;
			discovered.push(relativePath);
		}
	};

	visit(MODELING_SOURCE_ROOT);
	return discovered.sort();
};

const countOccurrences = (source: string, term: string) => source.split(term).length - 1;

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

test("canonical customer object labels allow only six groups and business-category help aliases", async () => {
	const contract = await import(CONTRACT_URL.href);
	assert.equal(
		typeof contract.isCanonicalModelingObjectLabel,
		"function",
		"missing canonical customer object-label validator",
	);
	assert.deepEqual([...contract.CANONICAL_MODELING_OBJECT_LABELS], EXPECTED_OBJECT_LABELS);
	assert.deepEqual([...contract.REJECTED_MODELING_OBJECT_REPLACEMENT_TERMS], REPLACEMENT_OBJECT_TERMS);
	assert.deepEqual(
		{ ...contract.CANONICAL_MODELING_CUSTOMER_SURFACE_MANIFEST },
		{
			sourceRoot: "src/pages/modeling",
			recursive: true,
			sourceExtensions: [".ts", ".tsx"],
			excludedSourceSuffixes: [".test.ts", ".test.tsx", ".test-support.ts", ".test-support.tsx"],
			definitionFiles: ["canonicalModelingLanguage.ts"],
			objectLabels: EXPECTED_OBJECT_LABELS,
		},
	);

	for (const label of [...EXPECTED_OBJECT_LABELS, "数据域", "主题域"]) {
		assert.equal(contract.isCanonicalModelingObjectLabel(label), true, `expected canonical object label: ${label}`);
	}
	for (const label of [...REPLACEMENT_OBJECT_TERMS, "业务活动", "明细表", "未知对象"]) {
		assert.equal(contract.isCanonicalModelingObjectLabel(label), false, `unexpected seventh object label: ${label}`);
	}
	for (const objectLabel of contract.CANONICAL_MODELING_CUSTOMER_SURFACE_MANIFEST.objectLabels) {
		assert.equal(
			contract.isCanonicalModelingObjectLabel(objectLabel),
			true,
			`invalid declared objectLabel: ${objectLabel}`,
		);
	}
});

test("recursively discovered modeling customer sources cannot grow retired terms or introduce replacement aliases", async () => {
	const contract = await import(CONTRACT_URL.href);
	const manifest = contract.CANONICAL_MODELING_CUSTOMER_SURFACE_MANIFEST as CustomerSurfaceManifest;
	const customerSources = discoverCustomerSources(manifest);

	assert.ok(customerSources.includes("ModelingWorkbenchPage.tsx"), "top-level customer pages must be discovered");
	assert.ok(
		customerSources.includes("metric-workbench/MetricCanvas.tsx"),
		"nested customer components must be discovered recursively",
	);
	for (const baselinePath of Object.keys(RETIRED_TERM_MIGRATION_BASELINE)) {
		assert.ok(customerSources.includes(baselinePath), `stale retired-term baseline path: ${baselinePath}`);
	}

	for (const relativePath of customerSources) {
		const source = readFileSync(resolve(MODELING_SOURCE_ROOT, relativePath), "utf8");
		const baseline = RETIRED_TERM_MIGRATION_BASELINE[relativePath as keyof typeof RETIRED_TERM_MIGRATION_BASELINE] as
			| Partial<Record<ForbiddenCustomerTerm, number>>
			| undefined;
		for (const term of FORBIDDEN_CUSTOMER_TERMS) {
			const actual = countOccurrences(source, term);
			const maximum = baseline?.[term] ?? 0;
			assert.ok(
				actual <= maximum,
				`${relativePath} contains ${actual} occurrence(s) of forbidden customer term ${term}; maximum is ${maximum}`,
			);
		}
	}
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
			REPLACEMENT_OBJECT_TERMS.some((term) => node.title?.includes(term)),
			false,
			`replacement object copy leaked to menu ${node.key}`,
		);
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
			REPLACEMENT_OBJECT_TERMS.some((term) => item.title.includes(term)),
			false,
			`replacement object copy leaked to role menu ${item.code}`,
		);
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
