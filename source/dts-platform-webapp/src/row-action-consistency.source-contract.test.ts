import fs from "node:fs";
import path from "node:path";
import ts from "typescript";
import { describe, expect, it } from "vitest";

/**
 * 表格行内操作必须经由 components/table 的 RowActions / actionColumn 统一渲染。
 *
 * 历史问题：各模块自己手写 `title: "操作"` + <Space><Button/></Space>，导致同一个
 * “编辑 / 删除”在数据集成是带边框小号按钮、在数据治理是 type="link" 文字链、在数据建模
 * 是默认尺寸按钮，且 fixed / width 时有时无。基线以数据集成（接入概览）为准。
 */
const srcDir = path.resolve(import.meta.dirname);

const excludedSegments = [
	`${path.sep}analytics${path.sep}pages${path.sep}screens${path.sep}`,
	`${path.sep}components${path.sep}table${path.sep}`,
];

/** 确实不适用统一组件的位置，必须写清理由 */
const ALLOWLIST: Record<string, string> = {
	// 动作数超过一行能容纳的范围，用 Dropdown 折叠“更多”，属于合理的溢出模式。
	"pages/catalog/MetadataManagementPage.tsx": "操作过多，用 Dropdown 收纳次级动作",
};

const collectTsxFiles = (dir: string): string[] =>
	fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
		const fullPath = path.join(dir, entry.name);
		if (excludedSegments.some((segment) => fullPath.includes(segment))) return [];
		if (entry.isDirectory()) return collectTsxFiles(fullPath);
		return entry.isFile() && fullPath.endsWith(".tsx") ? [fullPath] : [];
	});

const tagNameText = (tagName: ts.JsxTagNameExpression, sourceFile: ts.SourceFile) =>
	ts.isIdentifier(tagName) ? tagName.text : tagName.getText(sourceFile);

/** 该节点子树里是否直接渲染了 <Button>（RowActions 内部的不算，已被路径排除） */
const rendersRawButton = (node: ts.Node, sourceFile: ts.SourceFile) => {
	let found = false;
	const visit = (child: ts.Node) => {
		if (found) return;
		if (ts.isJsxOpeningElement(child) || ts.isJsxSelfClosingElement(child)) {
			if (tagNameText(child.tagName, sourceFile) === "Button") {
				found = true;
				return;
			}
		}
		ts.forEachChild(child, visit);
	};
	ts.forEachChild(node, visit);
	return found;
};

/** 找出仍然手写按钮的 `title: "操作"` 列对象 */
const findHandwrittenActionColumns = (source: string, filePath: string): string[] => {
	const sourceFile = ts.createSourceFile(filePath, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
	const offenders: string[] = [];

	const visit = (node: ts.Node) => {
		if (ts.isObjectLiteralExpression(node)) {
			const isActionColumn = node.properties.some(
				(prop) =>
					ts.isPropertyAssignment(prop) &&
					prop.name.getText(sourceFile) === "title" &&
					ts.isStringLiteral(prop.initializer) &&
					prop.initializer.text === "操作",
			);
			if (isActionColumn && rendersRawButton(node, sourceFile)) {
				const line = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile)).line + 1;
				offenders.push(`line ${line}`);
			}
		}
		ts.forEachChild(node, visit);
	};
	visit(sourceFile);
	return offenders;
};

describe("platform table row action consistency", () => {
	it("routes every table action column through RowActions/actionColumn", () => {
		const offenders = collectTsxFiles(srcDir).flatMap((file) => {
			const rel = path.relative(srcDir, file).split(path.sep).join("/");
			if (ALLOWLIST[rel]) return [];
			return findHandwrittenActionColumns(fs.readFileSync(file, "utf8"), file).map((hit) => `${rel}: ${hit}`);
		});

		expect(offenders).toEqual([]);
		// 需要用 TS 编译器解析全量 tsx，默认 5s 在并行压力下会超时
	}, 60_000);

	it("keeps the allowlist honest — every entry must still exist and still need the exemption", () => {
		const stale = Object.keys(ALLOWLIST).filter((rel) => {
			const full = path.join(srcDir, rel);
			if (!fs.existsSync(full)) return true;
			return findHandwrittenActionColumns(fs.readFileSync(full, "utf8"), full).length === 0;
		});

		expect(stale).toEqual([]);
	}, 60_000);
});
