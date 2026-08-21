import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const root = path.resolve(__dirname);
const read = (file: string) => fs.readFileSync(path.join(root, file), "utf8");

describe("model quality entry contract", () => {
	it("uses one quality-constraint entry and delegates governed rules to the quality center", () => {
		const editor = read("ModelingWorkbenchEditor.tsx");
		const dialog = read("ModelWorkbenchDialog.tsx");

		expect(editor).toContain("质量约束");
		expect(editor).not.toMatch(/>\s*质量规则\s*</);
		expect(dialog).toContain('quality: "质量约束"');
		expect(dialog).toContain("<ModelQualityConstraintPanel");
	});
});
