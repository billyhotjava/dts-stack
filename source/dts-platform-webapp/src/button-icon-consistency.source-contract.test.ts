import fs from "node:fs";
import path from "node:path";
import ts from "typescript";
import { describe, expect, it } from "vitest";

const srcDir = path.resolve(import.meta.dirname);
const excludedSegments = [
	`${path.sep}analytics${path.sep}pages${path.sep}screens${path.sep}`,
	// 登录页的 <Loader2> 是提交中的 loading 指示器而非装饰图标，去掉会丢失加载反馈。
	`${path.sep}pages${path.sep}sys${path.sep}login${path.sep}`,
	// 帮助中心是 size="icon" 的悬浮触发按钮，本就没有文案位，去掉图标会变成空按钮。
	`${path.sep}features${path.sep}help-center${path.sep}`,
];

const collectTsxFiles = (dir: string): string[] =>
	fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
		const fullPath = path.join(dir, entry.name);
		if (excludedSegments.some((segment) => fullPath.includes(segment))) return [];
		if (entry.isDirectory()) return collectTsxFiles(fullPath);
		return entry.isFile() && fullPath.endsWith(".tsx") ? [fullPath] : [];
	});

const tagNameText = (tagName: ts.JsxTagNameExpression, sourceFile: ts.SourceFile) =>
	ts.isIdentifier(tagName) ? tagName.text : tagName.getText(sourceFile);

const attrName = (attr: ts.JsxAttributeLike, sourceFile: ts.SourceFile) =>
	ts.isJsxAttribute(attr) ? attr.name.getText(sourceFile) : "";

/**
 * 图标库导入源。antd 图标靠命名后缀识别，但 lucide-react 的图标名是裸 PascalCase
 * （Pencil / Plus / Save …），无法靠命名识别，必须按文件实际导入的符号来判定，
 * 否则整个数据建模模块的图标按钮都会漏检。
 */
const ICON_MODULES = ["lucide-react", "@ant-design/icons"];

const collectIconImports = (sourceFile: ts.SourceFile): Set<string> => {
	const names = new Set<string>();
	for (const statement of sourceFile.statements) {
		if (!ts.isImportDeclaration(statement)) continue;
		if (!ts.isStringLiteral(statement.moduleSpecifier)) continue;
		if (!ICON_MODULES.includes(statement.moduleSpecifier.text)) continue;
		const bindings = statement.importClause?.namedBindings;
		if (bindings && ts.isNamedImports(bindings)) {
			for (const element of bindings.elements) names.add(element.name.text);
		}
		if (statement.importClause?.name) names.add(statement.importClause.name.text);
	}
	return names;
};

const isIconTag = (tagName: ts.JsxTagNameExpression, sourceFile: ts.SourceFile, iconImports: Set<string>) => {
	const name = tagNameText(tagName, sourceFile);
	return name === "Icon" || /(?:Icon|Outlined|Filled|TwoTone)$/.test(name) || iconImports.has(name);
};

const hasIconDescendant = (node: ts.Node, sourceFile: ts.SourceFile, iconImports: Set<string>) => {
	let found = false;
	const visit = (child: ts.Node) => {
		if (found) return;
		if (
			(ts.isJsxOpeningElement(child) || ts.isJsxSelfClosingElement(child)) &&
			isIconTag(child.tagName, sourceFile, iconImports)
		) {
			found = true;
			return;
		}
		ts.forEachChild(child, visit);
	};
	ts.forEachChild(node, visit);
	return found;
};

const findButtonIconUsages = (source: string, sourceFilePath: string) => {
	const sourceFile = ts.createSourceFile(sourceFilePath, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
	const usages: string[] = [];
	const iconImports = collectIconImports(sourceFile);

	const visit = (node: ts.Node) => {
		if (
			(ts.isJsxOpeningElement(node) || ts.isJsxSelfClosingElement(node)) &&
			tagNameText(node.tagName, sourceFile) === "Button"
		) {
			const attrs = Array.from(node.attributes.properties);
			const hasIconProp = attrs.some((attr) => ["icon", "iconPosition"].includes(attrName(attr, sourceFile)));
			if (hasIconProp) {
				usages.push(
					source
						.slice(node.getStart(sourceFile), Math.min(node.getStart(sourceFile) + 140, node.getEnd()))
						.split("\n")[0]
						.trim(),
				);
			}
		}
		if (
			ts.isJsxElement(node) &&
			tagNameText(node.openingElement.tagName, sourceFile) === "Button" &&
			hasIconDescendant(node, sourceFile, iconImports)
		) {
			usages.push(
				source
					.slice(node.getStart(sourceFile), Math.min(node.getStart(sourceFile) + 140, node.getEnd()))
					.split("\n")[0]
					.trim(),
			);
		}
		ts.forEachChild(node, visit);
	};
	visit(sourceFile);
	return usages;
};

describe("platform business button icon consistency", () => {
	it("keeps business page action buttons text-only", () => {
		const offenders = collectTsxFiles(srcDir).flatMap((file) =>
			findButtonIconUsages(fs.readFileSync(file, "utf8"), file).map(
				(usage) => `${path.relative(srcDir, file)}: ${usage}`,
			),
		);

		expect(offenders).toEqual([]);
		// 需要用 TS 编译器解析全量 tsx，默认 5s 在并行压力下会超时
	}, 60_000);
});
