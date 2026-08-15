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
	it("keeps the metric form in a bounded vertical scroll chain", () => {
		const editor = ruleBody(".dmx-metric-editor");
		const fieldset = ruleBody(".dmx-editor-fieldset");
		const scrollRegion = ruleBody(".dmx-metric-scroll");

		expect(editor).toMatch(/min-height:\s*0/);
		expect(editor).toMatch(/display:\s*flex/);
		expect(editor).toMatch(/flex-direction:\s*column/);
		expect(fieldset).toMatch(/min-height:\s*0/);
		expect(fieldset).toMatch(/display:\s*flex/);
		expect(fieldset).toMatch(/flex:\s*1(?:\s+1\s+auto)?/);
		expect(fieldset).toMatch(/flex-direction:\s*column/);
		expect(scrollRegion).toMatch(/min-height:\s*0/);
		expect(scrollRegion).toMatch(/overflow:\s*auto/);
	});

	it("prioritizes the metric form over the calculation history", () => {
		const formRegion = ruleBody(".dmx-metric-editor > .dmx-editor-fieldset");
		const history = ruleBody(".dmx-metric-section--history");
		const historyBody = ruleBody(".dmx-metric-section--history > div");

		expect(pageSource).toContain('className="dmx-metric-section dmx-metric-section--history"');
		expect(formRegion).toMatch(/flex-basis:\s*320px/);
		expect(history).toMatch(/flex:\s*0\s+1\s+260px/);
		expect(history).toMatch(/max-height:\s*260px/);
		expect(history).toMatch(/overflow:\s*hidden/);
		expect(historyBody).toMatch(/min-height:\s*0/);
		expect(historyBody).toMatch(/overflow:\s*auto/);
	});
});
