import { describe, expect, it } from "vitest";
import type { ModelSpecImportSemanticOverride } from "@/api/modelSpecImportApi";
import { applyModelSecurityLevel, modelSecurityLevel, previewReadinessIssues } from "./ReverseModelingInspectionSteps";

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

describe("reverse modeling preview readiness", () => {
	const inspection = (columns: Array<{ name: string }>, modelType = "DIMENSION") =>
		({
			package: {
				models: [
					{
						dbtUniqueId: "model.pm.dim_risk_level_v2",
						name: "dim_risk_level_v2",
						columns,
						semantics: { modelType, domainCode: "PRJ" },
					},
				],
			},
		}) as unknown as Parameters<typeof previewReadinessIssues>[0]["inspection"];
	const base = {
		planId: "plan-1",
		selected: ["model.pm.dim_risk_level_v2"],
		packageDomains: ["PRJ"],
		domainMappings: { PRJ: "domain-1" },
	};

	it("names every unmet prerequisite instead of only disabling the button", () => {
		expect(
			previewReadinessIssues({
				...base,
				inspection: inspection([{ name: "risk_level_id" }], "FACT"),
				planId: "",
				domainMappings: {},
				semanticOverrides: {},
			}),
		).toEqual([
			"请选择数仓规划",
			"请映射数据域：PRJ",
			"dim_risk_level_v2：请确认发布密级",
			"dim_risk_level_v2：请选择业务过程",
		]);
	});

	it("explains models whose package carries no typed field contract", () => {
		const issues = previewReadinessIssues({ ...base, inspection: inspection([]), semanticOverrides: {} });
		expect(issues).toHaveLength(1);
		expect(issues[0]).toContain("dim_risk_level_v2：dbt 包未提供带类型的字段定义");
	});

	it("is ready once the selected model classification is confirmed", () => {
		const override = applyModelSecurityLevel(
			{ modelUniqueId: "model.pm.dim_risk_level_v2" },
			["risk_level_id"],
			"INTERNAL",
		);
		expect(
			previewReadinessIssues({
				...base,
				inspection: inspection([{ name: "risk_level_id" }]),
				semanticOverrides: { "model.pm.dim_risk_level_v2": override },
			}),
		).toEqual([]);
		expect(
			previewReadinessIssues({
				...base,
				inspection: inspection([{ name: "risk_level_id" }]),
				selected: [],
				semanticOverrides: {},
			}),
		).toEqual(["请至少勾选一个可导入模型"]);
	});
});
