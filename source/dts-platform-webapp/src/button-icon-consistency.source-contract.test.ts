import fs from "node:fs";
import path from "node:path";
import ts from "typescript";
import { describe, expect, it } from "vitest";

const srcDir = path.resolve(import.meta.dirname);
const excludedSegments = [`${path.sep}analytics${path.sep}pages${path.sep}screens${path.sep}`];

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

const isIconTag = (tagName: ts.JsxTagNameExpression, sourceFile: ts.SourceFile) => {
	const name = tagNameText(tagName, sourceFile);
	return name === "Icon" || /(?:Icon|Outlined)$/.test(name);
};

const hasIconDescendant = (node: ts.Node, sourceFile: ts.SourceFile) => {
	let found = false;
	const visit = (child: ts.Node) => {
		if (found) return;
		if ((ts.isJsxOpeningElement(child) || ts.isJsxSelfClosingElement(child)) && isIconTag(child.tagName, sourceFile)) {
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

	const visit = (node: ts.Node) => {
		if ((ts.isJsxOpeningElement(node) || ts.isJsxSelfClosingElement(node)) && tagNameText(node.tagName, sourceFile) === "Button") {
			const attrs = Array.from(node.attributes.properties);
			const hasIconProp = attrs.some((attr) => ["icon", "iconPosition"].includes(attrName(attr, sourceFile)));
			if (hasIconProp) {
				usages.push(source.slice(node.getStart(sourceFile), Math.min(node.getStart(sourceFile) + 140, node.getEnd())).split("\n")[0].trim());
			}
		}
		if (ts.isJsxElement(node) && tagNameText(node.openingElement.tagName, sourceFile) === "Button" && hasIconDescendant(node, sourceFile)) {
			usages.push(source.slice(node.getStart(sourceFile), Math.min(node.getStart(sourceFile) + 140, node.getEnd())).split("\n")[0].trim());
		}
		ts.forEachChild(node, visit);
	};
	visit(sourceFile);
	return usages;
};

describe("platform business button icon consistency", () => {
	it("keeps business page action buttons text-only", () => {
		const offenders = collectTsxFiles(srcDir).flatMap((file) =>
			findButtonIconUsages(fs.readFileSync(file, "utf8"), file).map((usage) => `${path.relative(srcDir, file)}: ${usage}`),
		);

		expect(offenders).toEqual([]);
	});
});
