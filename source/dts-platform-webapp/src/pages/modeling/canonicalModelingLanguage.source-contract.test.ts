import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { extname, relative, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import ts from "typescript";

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
const WEBAPP_ROOT = fileURLToPath(new URL("../../../", import.meta.url));
const EXPECTED_OBJECT_LABELS = ["业务分类", "数仓分层", "数据标准", "维度", "四类表", "指标"] as const;

type CustomerSurfaceManifest = {
	sourceRoot: string;
	recursive: boolean;
	sourceExtensions: readonly string[];
	excludedSourceSuffixes: readonly string[];
	definitionFiles: readonly string[];
	additionalCustomerSources: readonly string[];
	objectLabels: readonly string[];
};

// Each legacy occurrence is frozen by path, line, column, term and a short hash
// of its normalized source line. This is intentionally narrower than a whole-file
// hash while still making moves and contextual rewrites explicit baseline changes.
const RETIRED_TERM_MIGRATION_FINGERPRINTS = Object.freeze([
	"src/pages/modeling/LowCodeDevelopmentPage.tsx:80:11:业务对象:2dca396e33ae0898",
	"src/pages/modeling/LowCodeDevelopmentPage.tsx:82:20:业务对象:f85d72f2fa6d2c84",
	"src/pages/modeling/LowCodeDevelopmentPage.tsx:95:30:业务对象:3ccb568b3501ba6b",
	"src/pages/modeling/MetricWorkbenchPage.tsx:106:33:业务对象:75a7bcd18b186267",
	"src/pages/modeling/MetricWorkbenchPage.tsx:113:32:业务对象:a623bcaecd420e77",
	"src/pages/modeling/MetricWorkbenchPage.tsx:165:32:业务对象:f15254abeac5fb32",
	"src/pages/modeling/MetricWorkbenchPage.tsx:265:31:业务对象:5f845d46812946c9",
	"src/pages/modeling/MetricWorkbenchPage.tsx:268:27:业务对象:b38a0959f723f2c1",
	"src/pages/modeling/MetricWorkbenchPage.tsx:313:41:业务对象:f018c0e3a5012547",
	"src/pages/modeling/MetricWorkbenchPage.tsx:346:55:业务对象:069bb0cf64afd0a8",
	"src/pages/modeling/ModelTemplatesPage.tsx:433:75:业务对象:f6a239e836779307",
	"src/pages/modeling/SemanticMetricsPage.tsx:95:21:业务对象:3cf3be78263ffea2",
	"src/pages/modeling/SemanticMetricsPage.tsx:123:39:业务对象:bb8f9859a3075c16",
	"src/pages/modeling/SemanticMetricsPage.tsx:180:57:业务对象:04a40506c5faf173",
	"src/pages/modeling/SemanticMetricsPage.tsx:183:44:业务对象:fe12f450033fa42e",
	"src/pages/modeling/SemanticObjectsPage.tsx:142:28:业务对象:fd823455bfd2e014",
	"src/pages/modeling/SemanticObjectsPage.tsx:157:19:业务对象:0041d995848e9875",
	"src/pages/modeling/SemanticObjectsPage.tsx:206:10:业务对象:66e065e2f715a93b",
	"src/pages/modeling/SemanticObjectsPage.tsx:210:12:业务对象:3cf3be78263ffea2",
	"src/pages/modeling/SemanticObjectsPage.tsx:270:11:业务对象:93e20a7a6106d7cd",
	"src/pages/modeling/SemanticObjectsPage.tsx:271:17:业务对象:1e63d6f3dadfd385",
	"src/pages/modeling/SemanticObjectsPage.tsx:274:15:业务对象:469d1d18e2f8f73b",
	"src/pages/modeling/SemanticObjectsPage.tsx:284:22:业务对象:76bc237c8ac44720",
	"src/pages/modeling/SemanticObjectsPage.tsx:337:14:业务对象:99feecea38f37e5e",
	"src/pages/modeling/businessObjectCode.ts:1:4:业务对象:37eb91d7e7338171",
	"src/pages/modeling/metric-workbench/MetricCanvas.tsx:350:25:业务对象:080a5cdf9227b609",
	"src/pages/modeling/metric-workbench/MetricCanvas.tsx:350:40:业务对象:080a5cdf9227b609",
	"src/pages/modeling/metric-workbench/MetricCanvas.tsx:372:31:业务对象:5416b8e64d528c3e",
	"src/pages/modeling/metric-workbench/MetricDetailPanel.tsx:354:30:业务对象:97c5432cf73ad220",
	"src/pages/modeling/metric-workbench/MetricDetailPanel.tsx:574:28:业务对象:9a4e919bd2c94b91",
	"src/pages/modeling/metric-workbench/SubjectBrowserPanel.tsx:104:30:业务对象:e88badccf932da4b",
	"src/pages/modeling/metric-workbench/SubjectBrowserPanel.tsx:136:63:业务对象:4c244ae573a3a978",
	"src/pages/modeling/metric-workbench/SubjectBrowserPanel.tsx:166:23:业务对象:72e3c8782a50f3b5",
	"src/pages/modeling/metric-workbench/metricCanvas.helpers.ts:415:48:业务对象:0b4d15a882556229",
	"src/pages/modeling/semantic-workspace/BusinessModelingContextBar.tsx:48:19:业务对象:8bea1846601df66a",
	"src/pages/modeling/semantic-workspace/ModelingConceptCards.tsx:28:11:业务对象:3cf3be78263ffea2",
	"src/pages/modeling/semantic-workspace/ModelingConceptCards.tsx:56:37:业务对象:d0c4cf16bb229935",
] as const);

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
	const sourceRoot = resolve(WEBAPP_ROOT, manifest.sourceRoot);
	const visit = (directory: string) => {
		for (const entry of readdirSync(directory, { withFileTypes: true })) {
			const absolutePath = resolve(directory, entry.name);
			if (entry.isDirectory()) {
				if (manifest.recursive) visit(absolutePath);
				continue;
			}
			const sourceRootRelativePath = relative(sourceRoot, absolutePath).replaceAll("\\", "/");
			const webappRelativePath = relative(WEBAPP_ROOT, absolutePath).replaceAll("\\", "/");
			if (!manifest.sourceExtensions.includes(extname(entry.name))) continue;
			if (manifest.excludedSourceSuffixes.some((suffix) => entry.name.endsWith(suffix))) continue;
			if (manifest.definitionFiles.includes(sourceRootRelativePath)) continue;
			discovered.push(webappRelativePath);
		}
	};

	visit(sourceRoot);
	for (const additionalSource of manifest.additionalCustomerSources ?? []) {
		assert.equal(
			existsSync(resolve(WEBAPP_ROOT, additionalSource)),
			true,
			`missing additional customer source: ${additionalSource}`,
		);
		discovered.push(additionalSource);
	}
	return [...new Set(discovered)].sort();
};

const countOccurrences = (source: string, term: string) => source.split(term).length - 1;

const normalizedSourceLineHash = (line: string): string =>
	createHash("sha256").update(line.trim().replace(/\s+/gu, " ")).digest("hex").slice(0, 16);

const collectRetiredTermFingerprints = (relativePath: string, source: string): string[] => {
	const fingerprints: string[] = [];
	for (const [lineIndex, line] of source.split(/\r?\n/u).entries()) {
		for (const term of FORBIDDEN_CUSTOMER_TERMS) {
			let searchFrom = 0;
			while (searchFrom < line.length) {
				const termIndex = line.indexOf(term, searchFrom);
				if (termIndex < 0) break;
				fingerprints.push(
					`${relativePath}:${lineIndex + 1}:${termIndex + 1}:${term}:${normalizedSourceLineHash(line)}`,
				);
				searchFrom = termIndex + term.length;
			}
		}
	}
	return fingerprints;
};

type ObjectLabelDeclaration = {
	label: string;
	line: number;
	column: number;
};

const staticStringValue = (node: ts.Node | undefined): string | undefined => {
	if (node && (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node))) return node.text;
	return undefined;
};

const isObjectLabelPropertyName = (name: ts.PropertyName | undefined): boolean => {
	if (!name) return false;
	if (
		ts.isIdentifier(name) ||
		ts.isStringLiteral(name) ||
		ts.isNumericLiteral(name) ||
		ts.isNoSubstitutionTemplateLiteral(name)
	) {
		return name.text === "objectLabel";
	}
	return ts.isComputedPropertyName(name) && staticStringValue(name.expression) === "objectLabel";
};

const assignmentObjectLabelNode = (left: ts.Expression): ts.Node | undefined => {
	if (ts.isIdentifier(left) && left.text === "objectLabel") return left;
	if (ts.isPropertyAccessExpression(left) && left.name.text === "objectLabel") return left.name;
	if (ts.isElementAccessExpression(left) && staticStringValue(left.argumentExpression) === "objectLabel") {
		return left.argumentExpression;
	}
	return undefined;
};

const extractObjectLabelDeclarations = (
	source: string,
	fileName = "customer-surface.tsx",
): ObjectLabelDeclaration[] => {
	const declarations: ObjectLabelDeclaration[] = [];
	const sourceFile = ts.createSourceFile(
		fileName,
		source,
		ts.ScriptTarget.Latest,
		true,
		fileName.endsWith(".tsx") ? ts.ScriptKind.TSX : ts.ScriptKind.TS,
	);
	const addDeclaration = (labelNode: ts.Node | undefined, locationNode: ts.Node) => {
		const label = staticStringValue(labelNode);
		if (label === undefined) return;
		const location = sourceFile.getLineAndCharacterOfPosition(locationNode.getStart(sourceFile));
		declarations.push({
			label,
			line: location.line + 1,
			column: location.character + 1,
		});
	};
	const visit = (node: ts.Node) => {
		if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.name.text === "objectLabel") {
			addDeclaration(node.initializer, node.name);
		} else if (ts.isPropertyAssignment(node) && isObjectLabelPropertyName(node.name)) {
			addDeclaration(node.initializer, node.name);
		} else if (ts.isJsxAttribute(node) && ts.isIdentifier(node.name) && node.name.text === "objectLabel") {
			if (node.initializer && ts.isJsxExpression(node.initializer)) {
				addDeclaration(node.initializer.expression, node.name);
			} else {
				addDeclaration(node.initializer, node.name);
			}
		} else if (ts.isBinaryExpression(node) && node.operatorToken.kind === ts.SyntaxKind.EqualsToken) {
			const locationNode = assignmentObjectLabelNode(node.left);
			if (locationNode) addDeclaration(node.right, locationNode);
		}
		ts.forEachChild(node, visit);
	};
	visit(sourceFile);
	return declarations;
};

const assertCanonicalObjectLabelDeclarations = (
	relativePath: string,
	source: string,
	isCanonicalObjectLabel: (value: unknown) => boolean,
) => {
	for (const declaration of extractObjectLabelDeclarations(source, relativePath)) {
		assert.equal(
			isCanonicalObjectLabel(declaration.label),
			true,
			`${relativePath}:${declaration.line}:${declaration.column} declares non-canonical objectLabel: ${declaration.label}`,
		);
	}
};

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
			additionalCustomerSources: ["src/pages/governance/SubjectAreasPage.tsx"],
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

test("objectLabel declarations in top-level and nested customer source accept only canonical labels", async () => {
	const contract = await import(CONTRACT_URL.href);
	const acceptedFixture = `
		const category = { objectLabel: "业务分类" };
		objectLabel = '数仓分层';
		const standard = { nested: { objectLabel: \`数据标准\` } };
		const dimension = { objectLabel: "维度" };
		const tables = { objectLabel: '四类表' };
		const metric = { objectLabel: \`指标\` };
		const domainHelp = { objectLabel: "数据域" };
		const subjectHelp = { nested: { objectLabel: '主题域' } };
	`;
	assert.deepEqual(
		extractObjectLabelDeclarations(acceptedFixture).map(({ label }) => label),
		[...EXPECTED_OBJECT_LABELS, "数据域", "主题域"],
	);
	assert.doesNotThrow(() =>
		assertCanonicalObjectLabelDeclarations(
			"accepted-object-labels.tsx",
			acceptedFixture,
			contract.isCanonicalModelingObjectLabel,
		),
	);

	const acceptedSurfaceFixture = `
		{ const objectLabel: string = "业务分类"; }
		{ const objectLabel = '数仓分层'; }
		const quoted = { "objectLabel": "数据标准" };
		const computed = { ["objectLabel"]: \`维度\` };
		const jsx = <><Card objectLabel="四类表" /><Card objectLabel={'指标'} /><Card objectLabel={\`业务分类\`} /></>;
		objectLabel = "数仓分层";
		card.objectLabel = '数据标准';
		card["objectLabel"] = \`维度\`;
	`;
	assert.deepEqual(
		extractObjectLabelDeclarations(acceptedSurfaceFixture).map(({ label }) => label),
		["业务分类", "数仓分层", "数据标准", "维度", "四类表", "指标", "业务分类", "数仓分层", "数据标准", "维度"],
	);

	const dynamicSurfaceFixture = `
		const objectLabel = resolveLabel();
		const card = { objectLabel: currentLabel };
		const jsx = <Card objectLabel={currentLabel} />;
		card.objectLabel = resolveLabel();
		card["objectLabel"] = currentLabel;
	`;
	assert.deepEqual(extractObjectLabelDeclarations(dynamicSurfaceFixture), []);

	for (const [fixturePath, forbiddenFixture] of [
		["top-level-business-activity.tsx", 'const activity = { objectLabel: "业务活动" };'],
		["nested-detail-table.tsx", 'const card = { nested: { objectLabel: "明细表" } };'],
		["typed-declaration.tsx", 'const objectLabel: string = "业务活动";'],
		["quoted-property.tsx", 'const card = { "objectLabel": "明细表" };'],
		["jsx-expression.tsx", 'const card = <Card objectLabel={"业务活动"} />;'],
		["element-access-assignment.tsx", 'card["objectLabel"] = "明细表";'],
	] as const) {
		assert.throws(
			() =>
				assertCanonicalObjectLabelDeclarations(fixturePath, forbiddenFixture, contract.isCanonicalModelingObjectLabel),
			new RegExp(`${fixturePath.replaceAll(".", "\\.")}:1:\\d+ declares non-canonical objectLabel`, "u"),
		);
	}
});

test("recursively discovered and explicit customer sources match exact retired-term fingerprints", async () => {
	const contract = await import(CONTRACT_URL.href);
	const manifest = contract.CANONICAL_MODELING_CUSTOMER_SURFACE_MANIFEST as CustomerSurfaceManifest;
	const customerSources = discoverCustomerSources(manifest);

	assert.ok(
		customerSources.includes("src/pages/modeling/ModelingWorkbenchPage.tsx"),
		"top-level customer pages must be discovered",
	);
	assert.ok(
		customerSources.includes("src/pages/modeling/metric-workbench/MetricCanvas.tsx"),
		"nested customer components must be discovered recursively",
	);
	assert.ok(
		customerSources.includes("src/pages/governance/SubjectAreasPage.tsx"),
		"brief-mandated governance customer page must be included explicitly",
	);

	const actualFingerprints: string[] = [];
	for (const relativePath of customerSources) {
		const source = readFileSync(resolve(WEBAPP_ROOT, relativePath), "utf8");
		assertCanonicalObjectLabelDeclarations(relativePath, source, contract.isCanonicalModelingObjectLabel);
		actualFingerprints.push(...collectRetiredTermFingerprints(relativePath, source));
	}
	assert.deepEqual(
		actualFingerprints,
		[...RETIRED_TERM_MIGRATION_FINGERPRINTS],
		"retired-term occurrences changed path, position, context or value; update the reviewed migration baseline explicitly",
	);
});

test("exact retired-term fingerprints reject equal-count moves and context rewrites", () => {
	const fixturePath = "synthetic/legacy-copy.tsx";
	const baselineSource = 'const item = "业务对象";';
	const baseline = collectRetiredTermFingerprints(fixturePath, baselineSource);
	const mutations = [`\n${baselineSource}`, 'const card = "业务对象";'] as const;

	for (const mutation of mutations) {
		assert.equal(countOccurrences(mutation, "业务对象"), countOccurrences(baselineSource, "业务对象"));
		assert.throws(() => assert.deepEqual(collectRetiredTermFingerprints(fixturePath, mutation), baseline), {
			name: "AssertionError",
		});
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
