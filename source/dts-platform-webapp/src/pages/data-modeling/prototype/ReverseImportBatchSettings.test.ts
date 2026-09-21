import { describe, expect, it } from "vitest";
import type { DbtArchiveInspection } from "@/api/modelSpecImportApi";
import { EMPTY_IMPORT_DEFAULTS, initialImportOverrides, resolveImportDefaults } from "./ReverseImportBatchSettings";
import { previewReadinessIssues } from "./ReverseModelingInspectionSteps";
import { suggestImportSourceMappings } from "./services/importSourceMapping";
import type { WarehousePlanSourceBindingView } from "@/api/warehousePlanApi";
const inspection = {
	package: {
		models: Array.from({ length: 100 }, (_, i) => ({
			dbtUniqueId: `model.pjm.${i}`,
			name: `model_${i}`,
			columns: [{ name: "id" }],
			semantics: { modelType: i % 2 ? "FACT" : "APPLICATION", domainCode: "PRJ" },
		})),
	},
} as unknown as DbtArchiveInspection;
const defaults = {
	domainId: "domain",
	businessProcessId: "process",
	dataMartId: "mart",
	subjectDomainId: "subject",
	securityLevel: "SECRET",
};
describe("whole package inheritance", () => {
	it("configures 100 models once and removes all model readiness blockers", () => {
		const result = resolveImportDefaults(inspection, initialImportOverrides(inspection), defaults, []);
		expect(Object.keys(result)).toHaveLength(100);
		expect(result["model.pjm.1"].businessProcessId).toBe("process");
		expect(result["model.pjm.0"].subjectDomainId).toBe("subject");
		expect(result["model.pjm.0"].businessProcessId).toBeUndefined();
		expect(
			previewReadinessIssues({
				inspection,
				planId: "plan",
				selected: Object.keys(result),
				packageDomains: ["PRJ"],
				domainMappings: { PRJ: "domain" },
				semanticOverrides: result,
			}),
		).toEqual([]);
	});
	it("preserves individual settings across default changes, and restores inheritance explicitly", () => {
		const overrides = resolveImportDefaults(inspection, initialImportOverrides(inspection), defaults, []);
		overrides["model.pjm.1"].businessProcessId = "custom";
		const next = { ...defaults, businessProcessId: "new-process", securityLevel: "INTERNAL" };
		const result = resolveImportDefaults(inspection, overrides, next, ["model.pjm.1"]);
		expect(result["model.pjm.1"].businessProcessId).toBe("custom");
		expect(result["model.pjm.1"].standardBindings?.[0].securityLevel).toBe("SECRET");
		expect(result["model.pjm.3"].businessProcessId).toBe("new-process");
		expect(resolveImportDefaults(inspection, overrides, next, [])["model.pjm.1"].businessProcessId).toBe("new-process");
		const cleared = resolveImportDefaults(inspection, overrides, EMPTY_IMPORT_DEFAULTS, []);
		expect(cleared["model.pjm.1"].businessProcessId).toBeUndefined();
		expect(cleared["model.pjm.1"].standardBindings?.some((b) => b.securityLevel)).toBe(false);
	});
});
it("matches only unique current sources and retains user mappings", () => {
	const source = (id: string, name: string) =>
		({
			bindingId: id,
			displayName: name,
			confirmationStatus: "CONFIRMED",
			resolutionStatus: "AVAILABLE",
			freshness: "CURRENT",
		}) as WarehousePlanSourceBindingView;
	const relations = [
		["source.pjm.budget_v2", "budget_v2"],
		["source.pjm.progress_v2", "progress_v2"],
	] as const;
	expect(suggestImportSourceMappings([...relations], [source("b", "ods_budget_v2")], {})).toEqual({
		"source.pjm.budget_v2": "b",
	});
	expect(
		suggestImportSourceMappings([...relations], [source("b", "ods_budget_v2"), source("b2", "ods_budget_v2")], {}),
	).toEqual({});
	expect(
		suggestImportSourceMappings([...relations], [source("b", "ods_budget_v2")], { "source.pjm.budget_v2": "manual" }),
	).toEqual({ "source.pjm.budget_v2": "manual" });
});
