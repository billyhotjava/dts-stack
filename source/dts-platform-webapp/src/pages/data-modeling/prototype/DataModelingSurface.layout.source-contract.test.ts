import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const styles = readFileSync(new URL("../data-modeling.css", import.meta.url), "utf8");
const editorSource = readFileSync(new URL("./ModelingWorkbenchEditor.tsx", import.meta.url), "utf8");

const ruleBody = (selector: string) => {
	const match = Array.from(styles.matchAll(/([^{}]+)\{([^}]*)\}/g)).find((item) =>
		item[1]
			.split(",")
			.map((value) => value.trim())
			.includes(selector),
	);
	expect(match, `missing CSS rule for ${selector}`).toBeDefined();
	return match?.[2] ?? "";
};

describe("data-modeling natural-height layout contract", () => {
	it("lets every route-level workspace grow with its content", () => {
		for (const selector of [".dmx-model-workbench", ".dmx-metric-workbench", ".dmx-graph-canvas"]) {
			const body = ruleBody(selector);
			expect(body, `${selector} must use natural height`).toMatch(/(?:^|\n)\s*height:\s*auto\s*;/);
			expect(body, `${selector} must not derive its height from the viewport`).not.toMatch(/height:\s*calc\(100vh/);
		}
	});

	it("does not shrink model detail sections inside the shared fieldset", () => {
		const fieldset = ruleBody(".dmx-editor-fieldset");
		const scrollRegion = ruleBody(".dmx-editor-scroll");

		expect(editorSource).toContain('className="dmx-editor-fieldset dmx-editor-scroll"');
		expect(fieldset).toMatch(/min-height:\s*auto/);
		expect(fieldset).toMatch(/display:\s*block/);
		expect(fieldset).toMatch(/flex:\s*none/);
		expect(fieldset).not.toMatch(/flex-direction\s*:/);
		expect(scrollRegion).toMatch(/min-height:\s*auto/);
		expect(scrollRegion).toMatch(/flex:\s*none/);
		expect(scrollRegion).toMatch(/overflow:\s*visible/);
	});
});
