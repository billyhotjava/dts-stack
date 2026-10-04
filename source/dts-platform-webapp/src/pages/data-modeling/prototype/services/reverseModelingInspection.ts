import type {
	DbtArchiveInspection,
	DbtImportCandidateEligibility,
	DbtInspectionReport,
	DbtPackageProfile,
	ModelSpecImportPreview,
	ModelSpecImportResultItem,
} from "@/api/modelSpecImportApi";

const mappingReasons = (reasons: string[]) =>
	reasons.length > 0 &&
	reasons.every((reason) => reason.startsWith("MISSING_") || reason === "SOURCE_SEMANTICS_INCOMPLETE");

export const candidateEligibility = (
	inspection: DbtArchiveInspection,
	dbtUniqueId: string,
): DbtImportCandidateEligibility => {
	const serverCandidate = inspection.report?.candidates.find((candidate) => candidate.dbtUniqueId === dbtUniqueId);
	if (serverCandidate) return serverCandidate.eligibility;
	const model = inspection.package.models.find((candidate) => candidate.dbtUniqueId === dbtUniqueId);
	if (!model || model.conversion?.mode === "BLOCKED") {
		return mappingReasons(model?.conversion?.reasonCodes || []) ? "REQUIRES_MAPPING" : "BLOCKED";
	}
	return "ELIGIBLE";
};

export const isInspectionCandidateSelectable = (inspection: DbtArchiveInspection, dbtUniqueId: string) =>
	candidateEligibility(inspection, dbtUniqueId) !== "BLOCKED";

export const isAdvancedDbtImportResult = (
	preview: ModelSpecImportPreview | null,
	result: ModelSpecImportResultItem,
): boolean => {
	if (!result.modelSpecId || !["CREATED", "UPDATED", "SKIPPED"].includes(result.status)) return false;
	return (
		preview?.items.find((candidate) => candidate.dbtUniqueId === result.dbtUniqueId)?.conversionMode === "DBT_BACKED"
	);
};

export const inspectionSummary = (inspection: DbtArchiveInspection): DbtInspectionReport["summary"] => {
	if (inspection.report) return inspection.report.summary;
	const candidates = inspection.package.models.map((model) => candidateEligibility(inspection, model.dbtUniqueId));
	return {
		discovered: candidates.length,
		technicalOnly: inspection.package.technicalNodes?.length || 0,
		eligible: candidates.filter((eligibility) => eligibility === "ELIGIBLE").length,
		requiresMapping: candidates.filter((eligibility) => eligibility === "REQUIRES_MAPPING").length,
		blocked: candidates.filter((eligibility) => eligibility === "BLOCKED").length,
	};
};

export const packageProfileLabel = (profile: DbtPackageProfile) => {
	if (profile === "ARTIFACT_RICH") return "制品完整包";
	if (profile === "SOURCE_ONLY") return "源项目包";
	return "兼容格式包";
};
