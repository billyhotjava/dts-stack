import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

describe("modifier indicator capability contract", () => {
	it("uses the governance indicator owner without atomic calculation controls", () => {
		const service = read("./services/indicatorProjectionService.ts");
		const page = read("./MetricsPage.tsx");
		const editor = read("./ModifierDefinitionEditor.tsx");

		expect(service).toMatch(/type === "修饰词"/);
		expect(service).toMatch(/type === "修饰词" \? "MODIFIER"/);
		expect(service).toMatch(/supportsIndicatorCalculation/);
		expect(editor).toContain("修饰词用于限定指标统计范围");
		expect(page).toMatch(/category:\s*isModifier\s*\?\s*"MODIFIER"/);
		expect(editor).toContain('category: "MODIFIER"');
	});
});
