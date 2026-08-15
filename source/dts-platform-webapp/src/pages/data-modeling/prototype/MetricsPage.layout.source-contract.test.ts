import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const styles = readFileSync(new URL("../data-modeling.css", import.meta.url), "utf8");

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
});
