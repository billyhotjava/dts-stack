import { describe, expect, it } from "vitest";
import type { ModelSpecImportSemanticOverride } from "@/api/modelSpecImportApi";
import { applyModelSecurityLevel, modelSecurityLevel } from "./ReverseModelingInspectionSteps";

describe("reverse modeling classification override", () => {
	it("applies an explicit model classification without losing standard metadata", () => {
		const override: ModelSpecImportSemanticOverride = {
			modelUniqueId: "model.pjm.project_follow_up",
			standardBindings: [
				{
					fieldName: "project_no",
					standardElementId: "standard-project-no",
					standardElementVersion: 2,
				},
			],
		};

		const updated = applyModelSecurityLevel(override, ["project_no", "measure_date"], "INTERNAL");

		expect(updated.standardBindings).toEqual([
			{
				fieldName: "project_no",
				standardElementId: "standard-project-no",
				standardElementVersion: 2,
				securityLevel: "INTERNAL",
			},
			{ fieldName: "measure_date", securityLevel: "INTERNAL" },
		]);
		expect(modelSecurityLevel(updated, ["project_no", "measure_date"])).toBe("INTERNAL");
	});

	it("clears only classification evidence and preserves other bindings", () => {
		const override: ModelSpecImportSemanticOverride = {
			modelUniqueId: "model.pjm.project_follow_up",
			standardBindings: [
				{ fieldName: "project_no", standardElementId: "standard-project-no", securityLevel: "SECRET" },
				{ fieldName: "measure_date", securityLevel: "SECRET" },
			],
		};

		const updated = applyModelSecurityLevel(override, ["project_no", "measure_date"], "");

		expect(updated.standardBindings).toEqual([
			{ fieldName: "project_no", standardElementId: "standard-project-no", securityLevel: undefined },
		]);
		expect(modelSecurityLevel(updated, ["project_no", "measure_date"])).toBe("");
	});
});
