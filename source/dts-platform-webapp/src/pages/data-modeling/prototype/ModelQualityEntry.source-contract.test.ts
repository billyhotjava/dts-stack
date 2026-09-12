import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const root = path.resolve(__dirname);
const read = (file: string) => fs.readFileSync(path.join(root, file), "utf8");

describe("model quality entry contract", () => {
	it("uses one quality-gate entry and delegates governed rules to the quality center", () => {
		const editor = read("ModelingWorkbenchEditor.tsx");
		const workflow = read("ModelWorkflowToolbar.tsx");
		const dialog = read("ModelWorkbenchDialog.tsx");

		expect(workflow).toContain("质量检查");
		expect(editor).not.toMatch(/>\s*质量规则\s*</);
		expect(dialog).toContain('quality: "质量检查"');
		expect(dialog).toContain("<ModelQualityConstraintPanel");
	});
	it("keeps governed quality editing outside the modeling steps", () => {
		const wizard = read("ModelWizardFrame.tsx");
		const actions = read("ModelMaterializationActions.tsx");
		const panel = read("ModelTargetQualityPanel.tsx");
		expect(wizard).toContain("canConfigureQuality={false}");
		expect(wizard).not.toContain("<ModelTargetQualityPanel");
		expect(actions).toContain("configureQuality ? onConfigureQuality : onPrimaryAction");
		expect(read("ModelPublishDialog.tsx")).not.toContain('document.getElementById("model-target-quality")');
		expect(panel).toContain("open={expanded}");
		expect(panel).toContain("dmx-target-quality__form");
		expect(panel).toContain("QUALITY_DATASET_NOT_DEFAULT_LAKE");
	});
});
