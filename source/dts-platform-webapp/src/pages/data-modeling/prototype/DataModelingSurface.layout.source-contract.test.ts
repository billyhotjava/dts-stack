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

	it("keeps model release gate evidence readable inside the wide modal", () => {
		const cells = ruleBody(".dmx-stage-gate-table td");
		const blocker = ruleBody(".dmx-stage-gate-blocker");

		expect(cells).toMatch(/white-space:\s*normal/);
		expect(cells).toMatch(/text-overflow:\s*clip/);
		expect(cells).toMatch(/vertical-align:\s*top/);
		expect(blocker).toMatch(/overflow-wrap:\s*anywhere/);
	});

	it("keeps warehouse-planning drawer forms inside the viewport", () => {
		const drawer = ruleBody(".dmx-drawer");
		const drawerBody = ruleBody(".dmx-drawer__body");
		const planningEditor = ruleBody(".dmx-planning-editor");
		const planningGrid = ruleBody(".dmx-planning-editor .dmx-form-grid");
		const planningField = ruleBody(".dmx-planning-editor .dmx-form-grid > label");
		const planningInput = ruleBody(".dmx-planning-editor .dmx-form-grid input");

		expect(drawer).toMatch(/width:\s*clamp\(520px,\s*52vw,\s*760px\)/);
		expect(drawer).toMatch(/max-width:\s*100%/);
		expect(planningEditor).toMatch(/max-width:\s*100%/);
		expect(planningGrid).toMatch(/grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\)/);
		expect(planningField).toMatch(/min-width:\s*0/);
		expect(planningInput).toMatch(/width:\s*100%/);
		expect(planningInput).toMatch(/max-width:\s*100%/);
		expect(drawerBody).toMatch(/min-height:\s*0/);
		expect(drawerBody).toMatch(/overflow-x:\s*hidden/);
		expect(drawerBody).toMatch(/overflow-y:\s*auto/);
		expect(styles).toMatch(
			/@media \(max-width: 820px\) \{[\s\S]*?\.dmx-planning-editor \.dmx-form-grid,[\s\S]*?grid-template-columns:\s*1fr;/,
		);
	});
});
