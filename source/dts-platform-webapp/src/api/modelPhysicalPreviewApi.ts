import api from "@/api/apiClient";
import type {
	PhysicalPreviewEvidenceState,
	PhysicalPreviewReference,
	PhysicalPreviewScope,
} from "@/features/modeling/contracts/modelRepresentationContract";

export type { PhysicalPreviewEvidenceState, PhysicalPreviewReference, PhysicalPreviewScope };
export type PhysicalPreviewPageSize = 20 | 50 | 100 | 500;
export type PhysicalPreviewMode = "STRUCTURE" | "SAMPLE";

export type PhysicalPreviewColumn = {
	ordinalPosition: number;
	name: string;
	dataType: string;
	nullable: boolean;
	policy: "ALLOW" | "MASK";
};

export type ModelPhysicalPreview = {
	modelSpecId: string;
	modelRevision: number;
	implementationRevision: number;
	previewMode: PhysicalPreviewMode;
	previewScope: PhysicalPreviewScope;
	candidateId: string;
	candidateVersion: number;
	attempt: number;
	pipelineRunId: string;
	observationAttempt: number;
	relationEvidenceId: string;
	evidenceChecksum: string;
	catalogAssetType: "SEMANTIC_MODEL";
	catalogAssetKey: string;
	relationRef: string;
	observedAt: string;
	queriedAt: string;
	columns: PhysicalPreviewColumn[];
	maskedRows: Array<Record<string, unknown>>;
	maskingSummary: {
		allowedColumnCount: number;
		maskedColumnCount: number;
		deniedColumnCount: number;
	};
	returnedRows: number;
	truncated: boolean;
	driftStatus: "CURRENT" | "STALE";
	correlationId: string;
	candidateLabel?: string | null;
};

const PAGE_SIZES: PhysicalPreviewPageSize[] = [20, 50, 100, 500];

const requestPhysicalPreview = (
	reference: PhysicalPreviewReference,
	mode: PhysicalPreviewMode,
	limit?: PhysicalPreviewPageSize,
) => {
	return api.get<ModelPhysicalPreview>({
		url: `/modeling/model-specs/${encodeURIComponent(reference.modelSpecId)}/implementations/${encodeURIComponent(
			String(reference.implementationRevision),
		)}/physical-preview`,
		params: {
			modelRevision: reference.modelRevision,
			modelChecksum: reference.modelChecksum,
			implementationChecksum: reference.implementationChecksum,
			mode,
			scope: reference.scope,
			candidateId: reference.candidateId,
			candidateVersion: reference.candidateVersion,
			attempt: reference.attempt,
			pipelineRunId: reference.pipelineRunId,
			observationAttempt: reference.observationAttempt,
			relationEvidenceId: reference.relationEvidenceId,
			evidenceChecksum: reference.evidenceChecksum,
			...(mode === "SAMPLE" ? { limit } : {}),
		},
		headers: { "Cache-Control": "no-store", Pragma: "no-cache" },
		_skipErrorToast: true,
	} as any);
};

export const getModelPhysicalStructure = (reference: PhysicalPreviewReference) =>
	requestPhysicalPreview(reference, "STRUCTURE");

export const getModelPhysicalPreview = (
	reference: PhysicalPreviewReference,
	limit: PhysicalPreviewPageSize = 100,
) => {
	if (!PAGE_SIZES.includes(limit)) throw new Error("PHYSICAL_PREVIEW_LIMIT_EXCEEDED");
	return requestPhysicalPreview(reference, "SAMPLE", limit);
};
