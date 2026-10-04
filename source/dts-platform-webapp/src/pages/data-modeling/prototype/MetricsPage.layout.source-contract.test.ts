import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const styles = readFileSync(new URL("../data-modeling.css", import.meta.url), "utf8");
const pageSource = readFileSync(new URL("./MetricsPage.tsx", import.meta.url), "utf8");

const ruleBody = (selector: string) => {
	const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
	const match = styles.match(new RegExp(`${escapedSelector}\\s*\\{([^}]*)\\}`));
	expect(match, `missing CSS rule for ${selector}`).not.toBeNull();
	return match?.[1] ?? "";
};

describe("MetricsPage editor layout contract", () => {
	it("keeps the metric form in the natural page flow", () => {
		const editor = ruleBody(".dmx-metric-editor");
		const fieldset = ruleBody(".dmx-editor-fieldset");
		const scrollRegion = ruleBody(".dmx-metric-scroll");

		expect(editor).toMatch(/min-height:\s*0/);
		expect(editor).toMatch(/display:\s*flex/);
		expect(editor).toMatch(/flex-direction:\s*column/);
		expect(fieldset).toMatch(/min-height:\s*auto/);
		expect(fieldset).toMatch(/display:\s*block/);
		expect(fieldset).toMatch(/flex:\s*none/);
		expect(fieldset).not.toMatch(/flex-direction\s*:/);
		expect(scrollRegion).toMatch(/min-height:\s*auto/);
		expect(scrollRegion).toMatch(/flex:\s*none/);
		expect(scrollRegion).toMatch(/overflow:\s*visible/);
	});

	it("lets calculation history expand without a fixed vertical cap", () => {
		const history = ruleBody(".dmx-metric-section--history");
		const historyBody = ruleBody(".dmx-metric-section--history > div");

		expect(pageSource).toContain('className="dmx-metric-section dmx-metric-section--history"');
		expect(history).not.toMatch(/max-height\s*:/);
		expect(history).not.toMatch(/flex\s*:/);
		expect(history).toMatch(/overflow:\s*visible/);
		expect(historyBody).toMatch(/min-height:\s*auto/);
		expect(historyBody).toMatch(/overflow:\s*visible/);
	});
});
