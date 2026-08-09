import { describe, expect, it } from "vitest";
import type { DbtArchiveInspection } from "@/api/modelSpecImportApi";
import {
	candidateEligibility,
	inspectionSummary,
	isAdvancedDbtImportResult,
	isInspectionCandidateSelectable,
	packageProfileLabel,
} from "./reverseModelingInspection";

const inspection = {
	package: {
		schemaVersion: "dts.model-package/v1",
		packageId: "pm_analytics_v3",
		packageChecksum: "a".repeat(64),
		dbt: { projectName: "pm_analytics_v3", manifestVersion: "source-project/v1" },
		sources: [],
		technicalNodes: [],
		models: [
			{
				dbtUniqueId: "model.pm.blocked",
				name: "blocked",
				conversion: { mode: "BLOCKED", reasonCodes: ["SOURCE_FIELDS_UNVERIFIED"] },
			},
			{
				dbtUniqueId: "model.pm.mapping",
				name: "mapping",
				conversion: { mode: "BLOCKED", reasonCodes: ["SOURCE_SEMANTICS_INCOMPLETE"] },
			},
		],
	},
	compatibility: {
		inspection: "SUPPORTED",
		importProjection: "STRUCTURE_VIEW_ONLY",
		materialization: "UNKNOWN",
		issues: [],
	},
	report: {
		packageProfile: "SOURCE_ONLY",
		summary: { discovered: 2, technicalOnly: 0, eligible: 0, requiresMapping: 1, blocked: 1 },
		candidates: [
			{ dbtUniqueId: "model.pm.blocked", eligibility: "BLOCKED", diagnosticCodes: ["SOURCE_FIELDS_UNVERIFIED"] },
			{
				dbtUniqueId: "model.pm.mapping",
				eligibility: "REQUIRES_MAPPING",
				diagnosticCodes: ["SOURCE_SEMANTICS_INCOMPLETE"],
			},
		],
		diagnostics: [],
	},
	inspectionProof: "proof",
	proofExpiresAt: "2026-08-09T01:00:00Z",
} satisfies DbtArchiveInspection;

describe("reverse modeling inspection projection", () => {
	it("uses server eligibility instead of treating every BLOCKED conversion as unselectable", () => {
		expect(candidateEligibility(inspection, "model.pm.blocked")).toBe("BLOCKED");
		expect(candidateEligibility(inspection, "model.pm.mapping")).toBe("REQUIRES_MAPPING");
		expect(isInspectionCandidateSelectable(inspection, "model.pm.blocked")).toBe(false);
		expect(isInspectionCandidateSelectable(inspection, "model.pm.mapping")).toBe(true);
	});

	it("exposes a stable package profile and summary", () => {
		expect(packageProfileLabel(inspection.report.packageProfile)).toBe("源项目包");
		expect(inspectionSummary(inspection)).toEqual(inspection.report.summary);
	});

	it("opens advanced dbt only for successful DBT-backed import results", () => {
		const preview = {
			runId: "run-1",
			previewHash: "preview-hash",
			summary: { total: 2, ready: 2, blocked: 0, create: 2, update: 0, skip: 0, conflict: 0 },
			items: [
				{ dbtUniqueId: "model.pm.dbt", action: "CREATE", conversionMode: "DBT_BACKED", issues: [] },
				{ dbtUniqueId: "model.pm.visual", action: "CREATE", conversionMode: "DESIGNER_GENERATED", issues: [] },
			],
		} satisfies import("@/api/modelSpecImportApi").ModelSpecImportPreview;
		const result = {
			dbtUniqueId: "model.pm.dbt",
			status: "CREATED",
			modelSpecId: "model-1",
			artifactCount: 2,
			issues: [],
		} satisfies import("@/api/modelSpecImportApi").ModelSpecImportResultItem;

		expect(isAdvancedDbtImportResult(preview, result)).toBe(true);
		expect(isAdvancedDbtImportResult(preview, { ...result, dbtUniqueId: "model.pm.visual" })).toBe(false);
		expect(isAdvancedDbtImportResult(preview, { ...result, status: "FAILED" })).toBe(false);
		expect(isAdvancedDbtImportResult(preview, { ...result, modelSpecId: null })).toBe(false);
		expect(isAdvancedDbtImportResult(null, result)).toBe(false);
	});
});
