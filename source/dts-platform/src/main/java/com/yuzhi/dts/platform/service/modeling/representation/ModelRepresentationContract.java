package com.yuzhi.dts.platform.service.modeling.representation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only projections of one exactly pinned logical and physical model revision. */
public final class ModelRepresentationContract {

    private ModelRepresentationContract() {}

    public enum RepresentationScope {
        BUSINESS,
        TECHNICAL,
    }

    public enum VisualizationCapability {
        BUSINESS_VISUAL_EDIT,
        BUSINESS_VISUAL_READ,
        DESIGNER_DBT_PREVIEW,
        ADVANCED_DBT_IMPLEMENTATION,
        BLOCKED,
    }

    /** Safe structural coverage of the implementation bundle; never an ownership or permission decision. */
    public enum ProjectionCoverage {
        FULL,
        PARTIAL,
        NONE,
        UNKNOWN,
    }

    /** Lifecycle state of the shared visual/code authoring draft. */
    public enum AuthoringDraftState {
        NONE,
        DRAFT,
        VALIDATED,
        COMMITTED,
    }

    /** Stable command vocabulary consumed by the unified model workbench. */
    public enum AuthoringAction {
        OPEN_VISUAL,
        OPEN_CODE,
        EDIT_MODEL,
        EDIT_IMPLEMENTATION,
        SAVE,
        VALIDATE,
        COMMIT,
        FORK_DRAFT,
    }

    /** Stable, allow-listed reason codes safe for both BUSINESS and TECHNICAL responses. */
    public enum CapabilityReason {
        MODEL_REPRESENTATION_NO_IMPLEMENTATION,
        MODEL_REPRESENTATION_ARTIFACT_PIN_MISMATCH,
        MODEL_REPRESENTATION_FIELDS_UNTRUSTED,
        MODEL_REPRESENTATION_DYNAMIC_DEPENDENCY,
        MODEL_REPRESENTATION_DEPENDENCIES_UNTRUSTED,
        MODEL_REPRESENTATION_RUNTIME_EVIDENCE_STALE,
        MODEL_REPRESENTATION_ADVANCED_REQUIRES_DBT_MANAGED,
        MODEL_REPRESENTATION_SCHEMA_INVALID,
    }

    public enum Provenance {
        DECLARED,
        COMPILED,
        OBSERVED,
    }

    public enum DriftStatus {
        CURRENT,
        STALE,
        UNAVAILABLE,
    }

    public enum PhysicalPreviewScope {
        SERVING,
        CANDIDATE,
    }

    public enum PhysicalPreviewEvidenceState {
        READY,
        MATERIALIZING,
        FAILED_STALE,
        UNAVAILABLE,
    }

    public sealed interface ModelRepresentationView permits BusinessModelRepresentationView, TechnicalModelRepresentationView {
        RepresentationScope representationScope();

        UUID modelSpecId();

        int modelRevision();

        String modelChecksum();

        Integer implementationRevision();

        String implementationChecksum();

        ImplementationMode ownershipMode();

        VisualizationCapability visualizationCapability();

        List<CapabilityReason> capabilityReasons();

        String etag();
    }

    /**
     * Business projection deliberately has no implementation-artifact member. This is a serialization boundary, not a
     * field-filter convention.
     */
    public record BusinessModelRepresentationView(
        RepresentationScope representationScope,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer implementationRevision,
        @JsonInclude(JsonInclude.Include.NON_NULL) String implementationChecksum,
        ImplementationMode ownershipMode,
        VisualizationCapability visualizationCapability,
        List<CapabilityReason> capabilityReasons,
        List<String> visibleSections,
        List<String> allowedActions,
        LogicalModelProjection logicalModel,
        List<DependencyProjection> dependencyProjection,
        @JsonInclude(JsonInclude.Include.NON_NULL) PublicationProjection latestPublishedRef,
        @JsonInclude(JsonInclude.Include.NON_NULL) PublicationProjection servingRef,
        RuntimeObservationProjection runtimeObservation,
        PreviewCapabilityProjection previewCapability,
        PhysicalPreviewProjection physicalPreview,
        DriftStatus driftStatus,
        String etag
    ) implements ModelRepresentationView {
        public BusinessModelRepresentationView {
            capabilityReasons = immutable(capabilityReasons);
            visibleSections = immutable(visibleSections);
            allowedActions = immutable(allowedActions);
            dependencyProjection = immutable(dependencyProjection);
        }
    }

    /** Technical projection is returned only after explicit technical authorization. */
    public record TechnicalModelRepresentationView(
        RepresentationScope representationScope,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        Integer implementationRevision,
        String implementationChecksum,
        ImplementationMode ownershipMode,
        VisualizationCapability visualizationCapability,
        List<CapabilityReason> capabilityReasons,
        List<String> visibleSections,
        List<String> allowedActions,
        LogicalModelProjection logicalModel,
        List<DependencyProjection> dependencyProjection,
        @JsonInclude(JsonInclude.Include.NON_NULL) PublicationProjection latestPublishedRef,
        @JsonInclude(JsonInclude.Include.NON_NULL) PublicationProjection servingRef,
        RuntimeObservationProjection runtimeObservation,
        PreviewCapabilityProjection previewCapability,
        PhysicalPreviewProjection physicalPreview,
        DriftStatus driftStatus,
        TechnicalImplementationProjection technicalImplementation,
        String etag
    ) implements ModelRepresentationView {
        public TechnicalModelRepresentationView {
            capabilityReasons = immutable(capabilityReasons);
            visibleSections = immutable(visibleSections);
            allowedActions = immutable(allowedActions);
            dependencyProjection = immutable(dependencyProjection);
        }
    }

    public record LogicalModelProjection(
        UUID id,
        UUID planId,
        UUID domainId,
        ModelType modelType,
        Layer layer,
        String name,
        String description,
        String materialization,
        ModelStatus status,
        List<FieldProjection> fields,
        ProvenanceRef provenance
    ) {
        public LogicalModelProjection {
            fields = immutable(fields);
        }
    }

    public record FieldProjection(
        String name,
        String displayName,
        String dataType,
        Boolean nullable,
        FieldRole role,
        String securityLevel,
        String dimensionAttributeCode,
        ProvenanceRef provenance
    ) {}

    public record DependencyProjection(UUID modelSpecId, int revision, ProvenanceRef provenance) {}

    public record ProvenanceRef(Provenance source, String sourceChecksum, @JsonInclude(JsonInclude.Include.NON_NULL) Instant observedAt) {}

    public record PublicationProjection(
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        String assetRef
    ) {}

    public record RuntimeObservationProjection(
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID evidenceId,
        DriftStatus driftStatus,
        boolean verified,
        boolean relationExists,
        @JsonInclude(JsonInclude.Include.NON_NULL) String metadataChecksum,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant observedAt
    ) {}

    public record PreviewCapabilityProjection(boolean available, List<CapabilityReason> reasons) {
        public PreviewCapabilityProjection {
            reasons = immutable(reasons);
        }
    }

    /** Complete server-issued pins used by the physical-preview endpoint; relation identifiers remain server-side. */
    public record PhysicalPreviewReferenceProjection(
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        PhysicalPreviewScope scope,
        UUID candidateId,
        int candidateVersion,
        int attempt,
        UUID pipelineRunId,
        int observationAttempt,
        UUID relationEvidenceId,
        String evidenceChecksum
    ) {}

    public record PhysicalPreviewProjection(
        @JsonInclude(JsonInclude.Include.NON_NULL) PhysicalPreviewReferenceProjection serving,
        @JsonInclude(JsonInclude.Include.NON_NULL) PhysicalPreviewReferenceProjection candidate,
        PhysicalPreviewEvidenceState servingStatus,
        PhysicalPreviewEvidenceState candidateStatus
    ) {
        public PhysicalPreviewProjection {
            servingStatus = servingStatus == null ? PhysicalPreviewEvidenceState.UNAVAILABLE : servingStatus;
            candidateStatus = candidateStatus == null ? PhysicalPreviewEvidenceState.UNAVAILABLE : candidateStatus;
        }

        public static PhysicalPreviewProjection unavailable() {
            return new PhysicalPreviewProjection(
                null,
                null,
                PhysicalPreviewEvidenceState.UNAVAILABLE,
                PhysicalPreviewEvidenceState.UNAVAILABLE
            );
        }
    }

    public record TechnicalImplementationProjection(
        UUID implementationId,
        ImplementationMode ownership,
        String projectKey,
        String dbtUniqueId,
        String inputMode,
        JsonNode inputs,
        JsonNode fieldMappings,
        JsonNode settings,
        String materialization,
        List<TechnicalArtifactProjection> artifacts
    ) {
        public TechnicalImplementationProjection {
            artifacts = immutable(artifacts);
        }
    }

    public record TechnicalArtifactProjection(
        String artifactType,
        String artifactPath,
        String artifactChecksum,
        String status,
        @JsonInclude(JsonInclude.Include.NON_NULL) String artifactContent
    ) {}

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
