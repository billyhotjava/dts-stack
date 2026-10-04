import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const viewsDir = path.resolve(import.meta.dirname);

const collectTsxFiles = (dir: string): string[] =>
	fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
		const fullPath = path.join(dir, entry.name);
		if (entry.isDirectory()) return collectTsxFiles(fullPath);
		return entry.isFile() && fullPath.endsWith(".tsx") ? [fullPath] : [];
	});

const stripTsxComments = (source: string) =>
	source
		.replace(/\{\/\*[\s\S]*?\*\/\}/g, "")
		.replace(/\/\*[\s\S]*?\*\//g, "")
		.replace(/^\s*\/\/.*$/gm, "");

const findButtonIconUsages = (source: string) => {
	const cleanSource = stripTsxComments(source);
	const usages: string[] = [];
	const buttonPattern = /<Button\b[\s\S]*?(?:<\/Button>|\/>)/g;
	for (const match of cleanSource.matchAll(buttonPattern)) {
		const block = match[0];
		const openingTag = block.match(/<Button\b[\s\S]*?(?:>|\/>)/)?.[0] ?? block;
		if (/\sicon\s*=/.test(openingTag) || /<Icon\b/.test(block)) {
			usages.push(block.split("\n")[0].trim());
		}
	}
	return usages;
};

describe("admin view button icon consistency", () => {
	it("keeps admin business action buttons text-only", () => {
		const offenders = collectTsxFiles(viewsDir).flatMap((file) =>
			findButtonIconUsages(fs.readFileSync(file, "utf8")).map((usage) => `${path.relative(viewsDir, file)}: ${usage}`),
		);

		expect(offenders).toEqual([]);
	});
});
