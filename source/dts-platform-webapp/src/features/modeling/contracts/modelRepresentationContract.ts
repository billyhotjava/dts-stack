import type {
	ModelSpecField,
	ModelSpecImplementationMode,
	ModelSpecLayer,
	ModelSpecStatus,
	ModelSpecType,
} from "./modelSpecV2Contract";

export type ModelRepresentationScope = "BUSINESS" | "TECHNICAL";
export type ModelRepresentationProvenance = "DECLARED" | "COMPILED" | "OBSERVED";
export type ModelVisualizationCapability =
	| "BUSINESS_VISUAL_EDIT"
	| "BUSINESS_VISUAL_READ"
	| "ADVANCED_DBT_IMPLEMENTATION"
	| "DESIGNER_DBT_PREVIEW"
	| "BLOCKED";

export type PhysicalPreviewScope = "SERVING" | "CANDIDATE";
export type PhysicalPreviewEvidenceState =
	| "READY"
	| "MATERIALIZING"
	| "FAILED_STALE"
	| "UNAVAILABLE";

/** Complete server-issued evidence pins; clients never infer relation identifiers or checksums. */
export type PhysicalPreviewReference = {
	modelSpecId: string;
	modelRevision: number;
	modelChecksum: string;
	implementationRevision: number;
	implementationChecksum: string;
	scope: PhysicalPreviewScope;
	candidateId: string;
	candidateVersion: number;
	attempt: number;
	pipelineRunId: string;
	observationAttempt: number;
	relationEvidenceId: string;
	evidenceChecksum: string;
};

export type PhysicalPreviewReadModel = {
	serving?: PhysicalPreviewReference | null;
	candidate?: PhysicalPreviewReference | null;
	servingStatus: PhysicalPreviewEvidenceState;
	candidateStatus: PhysicalPreviewEvidenceState;
};

export type ModelRepresentationField = ModelSpecField & {
	provenance: ModelRepresentationProvenanceRef;
};

export type ModelRepresentationProvenanceRef = {
	source: ModelRepresentationProvenance;
	sourceChecksum: string;
	observedAt?: string | null;
};

export type ModelRepresentationLogicalModel = {
	id: string;
	planId: string;
	domainId: string;
	name: string;
	description?: string | null;
	modelType: ModelSpecType;
	layer: ModelSpecLayer;
	status: ModelSpecStatus;
	materialization?: string | null;
	fields: ModelRepresentationField[];
	provenance: ModelRepresentationProvenanceRef;
};

export type ModelRepresentationDependency = {
	modelSpecId: string;
	revision: number;
	provenance: ModelRepresentationProvenanceRef;
};

export type ModelTechnicalImplementation = {
	implementationId: string;
	ownership: ModelSpecImplementationMode;
	projectKey: string;
	dbtUniqueId: string;
	inputMode: string;
	inputs: unknown;
	fieldMappings: unknown;
	settings: unknown;
	materialization: string;
	artifacts: Array<{
		artifactType: string;
		artifactPath: string;
		artifactChecksum: string;
		status: string;
		artifactContent?: string | null;
	}>;
};

export type ModelRepresentationRef = {
	modelRevision: number;
	modelChecksum: string;
	implementationRevision: number;
	implementationChecksum: string;
	assetRef: string;
};

export type ModelRepresentationView = {
	modelSpecId: string;
	modelRevision: number;
	modelChecksum: string;
	implementationRevision?: number | null;
	implementationChecksum?: string | null;
	ownershipMode: ModelSpecImplementationMode;
	representationScope: ModelRepresentationScope;
	visualizationCapability: ModelVisualizationCapability;
	capabilityReasons: string[];
	visibleSections: string[];
	allowedActions: string[];
	logicalModel: ModelRepresentationLogicalModel;
	dependencyProjection: ModelRepresentationDependency[];
	latestPublishedRef?: ModelRepresentationRef | null;
	servingRef?: ModelRepresentationRef | null;
	runtimeObservation: {
		evidenceId?: string | null;
		driftStatus: "CURRENT" | "STALE" | "UNAVAILABLE";
		verified: boolean;
		relationExists: boolean;
		metadataChecksum?: string | null;
		observedAt?: string | null;
	};
	previewCapability: { available: boolean; reasons: string[] };
	physicalPreview?: PhysicalPreviewReadModel | null;
	driftStatus: "CURRENT" | "STALE" | "UNAVAILABLE";
	technicalImplementation?: ModelTechnicalImplementation | null;
	etag: string;
};
