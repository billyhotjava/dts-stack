import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const styles = readFileSync(new URL("../data-modeling.css", import.meta.url), "utf8");
const inspectionSource = readFileSync(new URL("./ReverseModelingInspectionSteps.tsx", import.meta.url), "utf8");

const ruleBody = (selector: string) => {
	const match = Array.from(styles.matchAll(/([^{}]+)\{([^}]*)\}/g))
		.filter((item) =>
			item[1]
				.split(",")
				.map((value) => value.trim())
				.includes(selector),
		)
		.at(-1);
	expect(match, `missing CSS rule for ${selector}`).toBeDefined();
	return match?.[2] ?? "";
};

describe("reverse-modeling responsive layout contract", () => {
	it("keeps package settings responsive and exposes source registration in the wizard", () => {
		expect(ruleBody(".dmx-import-batch-settings .dmx-mapping-grid")).toContain("minmax(220px, 1fr)");
		expect(inspectionSource).toContain("<ReverseImportSourceRegistration");
		expect(inspectionSource).toContain("全选可导入模型");
	});
	it("keeps the wizard and body within their route container", () => {
		const wizard = ruleBody(".dmx-reverse-wizard");
		const body = ruleBody(".dmx-wizard-body");

		expect(wizard).toMatch(/width:\s*100%/);
		expect(wizard).toMatch(/min-width:\s*0/);
		expect(body).toMatch(/min-width:\s*0/);
	});

	it("assigns horizontal scrolling to the wide model mapping table", () => {
		const table = ruleBody(".dmx-import-semantics");

		expect(table).toMatch(/width:\s*100%/);
		expect(table).toMatch(/min-width:\s*0/);
		expect(table).toMatch(/max-width:\s*100%/);
		expect(table).not.toMatch(/min-width:\s*1250px/);
		expect(inspectionSource).toContain("scroll={{ x: 1250 }}");
	});
});

// S10DC-106: a conflict-only preview is BLOCKED even when a row remains selected.
describe("reverse-modeling apply state contract", () => {
	it("guards both the apply handler and button with the server PREVIEWED state", () => {
		const page = readFileSync(new URL("./ReverseModelingPage.tsx", import.meta.url), "utf8");
		expect(page).toContain('if (!canMaintain || busy || !selected.length || preview?.status !== "PREVIEWED") return;');
		expect(page).toContain(
			'disabled={!canMaintain || preview?.status !== "PREVIEWED" || !selected.length || Boolean(busy)}',
		);
		expect(page).toContain("返回上一步重新生成预览");
	});
});
