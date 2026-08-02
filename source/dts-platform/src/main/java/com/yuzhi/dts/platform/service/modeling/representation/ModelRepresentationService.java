package com.yuzhi.dts.platform.service.modeling.representation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.BusinessModelRepresentationView;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.CapabilityReason;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.DependencyProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.DriftStatus;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.LogicalModelProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.ModelRepresentationView;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PhysicalPreviewProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PhysicalPreviewEvidenceState;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PreviewCapabilityProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PublicationProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.RepresentationScope;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.RuntimeObservationProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.TechnicalArtifactProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.TechnicalImplementationProjection;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.TechnicalModelRepresentationView;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.VisualizationCapability;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ArtifactEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.ImplementationSnapshot;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.PublicationEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationEvidencePort.RepresentationEvidence;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationException.Kind;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationProvenanceProjector.ProjectionResult;
import com.yuzhi.dts.platform.service.modeling.representation.ModelVisualizationCapabilityEvaluator.CapabilityDecision;
import com.yuzhi.dts.platform.service.modeling.representation.ModelVisualizationCapabilityEvaluator.ProjectionTrust;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds one immutable representation from authorized, exactly pinned owner records. */
@Service
@Transactional(readOnly = true)
public class ModelRepresentationService {

    private final ModelSpecApplicationService modelSpecs;
    private final ModelRepresentationEvidencePort evidencePort;
    private final ModelPhysicalPreviewReferencePort physicalPreviewReferences;
    private final ModelRepresentationProvenanceProjector provenanceProjector;
    private final ModelVisualizationCapabilityEvaluator capabilityEvaluator;
    private final ObjectMapper objectMapper;

    public ModelRepresentationService(
        ModelSpecApplicationService modelSpecs,
        ModelRepresentationEvidencePort evidencePort,
        ModelRepresentationProvenanceProjector provenanceProjector,
        ModelVisualizationCapabilityEvaluator capabilityEvaluator,
        ObjectMapper objectMapper
    ) {
        this(
            modelSpecs,
            evidencePort,
            (tenantId, modelSpecId, modelRevision, modelChecksum, implementationRevision, implementationChecksum) ->
                PhysicalPreviewProjection.unavailable(),
            provenanceProjector,
            capabilityEvaluator,
            objectMapper
        );
    }

    @Autowired
    public ModelRepresentationService(
        ModelSpecApplicationService modelSpecs,
        ModelRepresentationEvidencePort evidencePort,
        ModelPhysicalPreviewReferencePort physicalPreviewReferences,
        ModelRepresentationProvenanceProjector provenanceProjector,
        ModelVisualizationCapabilityEvaluator capabilityEvaluator,
        ObjectMapper objectMapper
    ) {
        this.modelSpecs = modelSpecs;
        this.evidencePort = evidencePort;
        this.physicalPreviewReferences = physicalPreviewReferences;
        this.provenanceProjector = provenanceProjector;
        this.capabilityEvaluator = capabilityEvaluator;
        this.objectMapper = objectMapper;
    }

    public ModelRepresentationView get(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        int modelRevision,
        Integer implementationRevision,
        RepresentationScope scope,
        boolean technicalAuthorized
    ) {
        validateRequest(tenantId, actorId, modelSpecId, modelRevision, implementationRevision, scope);

        // The owner read path intentionally runs first so an invisible model remains indistinguishable from missing.
        ModelSpecView model = modelSpecs.revision(tenantId, new ModelRevisionRef(modelSpecId, modelRevision));
        if (scope == RepresentationScope.TECHNICAL && !technicalAuthorized) {
            throw new ModelRepresentationException(
                "MODEL_REPRESENTATION_TECHNICAL_ACCESS_DENIED",
                "Technical model representation requires technical-model authority",
                Kind.FORBIDDEN
            );
        }
        if (implementationRevision == null) return withoutImplementation(tenantId, model, scope);

        RepresentationEvidence evidence = evidencePort
            .findExact(
                tenantId,
                modelSpecId,
                modelRevision,
                model.checksum(),
                implementationRevision,
                scope == RepresentationScope.TECHNICAL
            )
            .orElseThrow(() ->
                new ModelRepresentationException(
                    "MODEL_REPRESENTATION_IMPLEMENTATION_NOT_FOUND",
                    "The requested implementation revision was not found for the exact logical revision pin",
                    Kind.NOT_FOUND,
                    Map.of("modelSpecId", modelSpecId, "modelRevision", modelRevision, "implementationRevision", implementationRevision)
                )
            );
        validateExactPins(model, implementationRevision, evidence);

        ProjectionResult projection = provenanceProjector.project(model, evidence);
        ProjectionTrust trust = new ProjectionTrust(
            true,
            projection.fieldsTrusted(),
            projection.dependenciesTrusted(),
            true,
            projection.dynamicDependencies(),
            projection.driftStatus() == DriftStatus.STALE
        );
        CapabilityDecision decision = capabilityEvaluator.evaluate(scope, evidence.implementation().ownership(), trust);
        List<CapabilityReason> reasons = mergeReasons(decision.reasons(), projection.reasons());
        PhysicalPreviewProjection physicalPreview = physicalPreviewReferences.resolve(
            tenantId,
            model.id(),
            model.revision(),
            model.checksum(),
            implementationRevision,
            evidence.implementation().implementationChecksum()
        );
        if (physicalPreview == null) physicalPreview = PhysicalPreviewProjection.unavailable();

        return scope == RepresentationScope.TECHNICAL
            ? technical(model, evidence, projection, decision.capability(), reasons, physicalPreview)
            : business(model, evidence, projection, decision.capability(), reasons, physicalPreview);
    }

    private ModelRepresentationView withoutImplementation(String tenantId, ModelSpecView model, RepresentationScope scope) {
        if (evidencePort.findCurrentPin(tenantId, model.id()).isPresent()) {
            throw new ModelRepresentationException(
                "MODEL_REPRESENTATION_IMPLEMENTATION_PIN_REQUIRED",
                "An implementation exists; its exact revision pin is required",
                Kind.CONFLICT,
                Map.of("modelSpecId", model.id(), "modelRevision", model.revision())
            );
        }
        ProjectionResult projection = provenanceProjector.declared(model);
        CapabilityDecision decision = capabilityEvaluator.evaluate(
            scope,
            model.implementationMode(),
            new ProjectionTrust(false, false, true, false, false, false)
        );
        PreviewCapabilityProjection preview = new PreviewCapabilityProjection(false, decision.reasons());
        RepresentationSemanticPayload payload = payload(
            scope,
            model,
            null,
            decision.capability(),
            decision.reasons(),
            List.of("LOGICAL_MODEL"),
            List.of(),
            projection,
            null,
            null,
            preview,
            PhysicalPreviewProjection.unavailable(),
            null
        );
        return toView(payload);
    }

    private BusinessModelRepresentationView business(
        ModelSpecView model,
        RepresentationEvidence evidence,
        ProjectionResult projection,
        VisualizationCapability capability,
        List<CapabilityReason> reasons,
        PhysicalPreviewProjection physicalPreview
    ) {
        RepresentationSemanticPayload payload = payload(
            RepresentationScope.BUSINESS,
            model,
            evidence.implementation(),
            capability,
            reasons,
            List.of("LOGICAL_MODEL", "DEPENDENCIES", "PUBLICATION", "RUNTIME", "PHYSICAL_PREVIEW"),
            businessActions(capability),
            projection,
            publication(evidence.latestPublished()),
            publication(evidence.serving()),
            preview(capability, reasons),
            businessPhysicalPreview(physicalPreview),
            null
        );
        return (BusinessModelRepresentationView) toView(payload);
    }

    private static PhysicalPreviewProjection businessPhysicalPreview(PhysicalPreviewProjection physicalPreview) {
        if (physicalPreview == null) return PhysicalPreviewProjection.unavailable();
        return new PhysicalPreviewProjection(
            physicalPreview.serving(),
            null,
            physicalPreview.servingStatus(),
            PhysicalPreviewEvidenceState.UNAVAILABLE
        );
    }

    private TechnicalModelRepresentationView technical(
        ModelSpecView model,
        RepresentationEvidence evidence,
        ProjectionResult projection,
        VisualizationCapability capability,
        List<CapabilityReason> reasons,
        PhysicalPreviewProjection physicalPreview
    ) {
        ImplementationSnapshot implementation = evidence.implementation();
        TechnicalImplementationProjection technical = new TechnicalImplementationProjection(
            implementation.implementationId(),
            implementation.ownership(),
            implementation.projectKey(),
            implementation.dbtUniqueId(),
            implementation.inputMode(),
            implementation.inputs(),
            implementation.fieldMappings(),
            implementation.settings(),
            implementation.materialization(),
            evidence.artifacts().stream()
                .map(artifact ->
                    new TechnicalArtifactProjection(
                        artifact.artifactType(),
                        artifact.artifactPath(),
                        artifact.artifactChecksum(),
                        artifact.status(),
                        artifact.artifactContent()
                    )
                )
                .toList()
        );
        RepresentationSemanticPayload payload = payload(
            RepresentationScope.TECHNICAL,
            model,
            implementation,
            capability,
            reasons,
            List.of("LOGICAL_MODEL", "DEPENDENCIES", "PUBLICATION", "RUNTIME", "PHYSICAL_PREVIEW", "TECHNICAL_IMPLEMENTATION"),
            technicalActions(capability),
            projection,
            publication(evidence.latestPublished()),
            publication(evidence.serving()),
            preview(capability, reasons),
            physicalPreview,
            technical
        );
        return (TechnicalModelRepresentationView) toView(payload);
    }

    private RepresentationSemanticPayload payload(
        RepresentationScope scope,
        ModelSpecView model,
        ImplementationSnapshot implementation,
        VisualizationCapability capability,
        List<CapabilityReason> reasons,
        List<String> visibleSections,
        List<String> allowedActions,
        ProjectionResult projection,
        PublicationProjection latestPublished,
        PublicationProjection serving,
        PreviewCapabilityProjection preview,
        PhysicalPreviewProjection physicalPreview,
        TechnicalImplementationProjection technical
    ) {
        return new RepresentationSemanticPayload(
            scope,
            model.id(),
            model.revision(),
            model.checksum(),
            implementation == null ? null : implementation.implementationRevision(),
            implementation == null ? null : implementation.implementationChecksum(),
            implementation == null ? model.implementationMode() : implementation.ownership(),
            capability,
            reasons,
            visibleSections,
            allowedActions,
            projection.logicalModel(),
            projection.dependencies(),
            latestPublished,
            serving,
            projection.runtimeObservation(),
            preview,
            physicalPreview,
            projection.driftStatus(),
            technical
        );
    }

    private ModelRepresentationView toView(RepresentationSemanticPayload payload) {
        String etag = etag(payload);
        if (payload.scope() == RepresentationScope.TECHNICAL) {
            return new TechnicalModelRepresentationView(
                payload.scope(),
                payload.modelSpecId(),
                payload.modelRevision(),
                payload.modelChecksum(),
                payload.implementationRevision(),
                payload.implementationChecksum(),
                payload.ownershipMode(),
                payload.visualizationCapability(),
                payload.capabilityReasons(),
                payload.visibleSections(),
                payload.allowedActions(),
                payload.logicalModel(),
                payload.dependencyProjection(),
                payload.latestPublishedRef(),
                payload.servingRef(),
                payload.runtimeObservation(),
                payload.previewCapability(),
                payload.physicalPreview(),
                payload.driftStatus(),
                payload.technicalImplementation(),
                etag
            );
        }
        return new BusinessModelRepresentationView(
            payload.scope(),
            payload.modelSpecId(),
            payload.modelRevision(),
            payload.modelChecksum(),
            payload.implementationRevision(),
            payload.implementationChecksum(),
            payload.ownershipMode(),
            payload.visualizationCapability(),
            payload.capabilityReasons(),
            payload.visibleSections(),
            payload.allowedActions(),
            payload.logicalModel(),
            payload.dependencyProjection(),
            payload.latestPublishedRef(),
            payload.servingRef(),
            payload.runtimeObservation(),
            payload.previewCapability(),
            payload.physicalPreview(),
            payload.driftStatus(),
            etag
        );
    }

    private static void validateRequest(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        int modelRevision,
        Integer implementationRevision,
        RepresentationScope scope
    ) {
        if (actorId == null || actorId.isBlank()) {
            throw new ModelRepresentationException(
                "MODEL_REPRESENTATION_ACTOR_REQUIRED",
                "An authenticated model reader is required",
                Kind.FORBIDDEN
            );
        }
        if (tenantId == null || tenantId.isBlank() || modelSpecId == null || modelRevision < 1 || scope == null) {
            throw new ModelRepresentationException(
                "MODEL_REPRESENTATION_REQUEST_INVALID",
                "Tenant, model, model revision and representation scope are required",
                Kind.BAD_REQUEST
            );
        }
        if (implementationRevision != null && implementationRevision < 1) {
            throw new ModelRepresentationException(
                "MODEL_REPRESENTATION_IMPLEMENTATION_REVISION_INVALID",
                "Implementation revision must be positive",
                Kind.BAD_REQUEST
            );
        }
    }

    private static void validateExactPins(ModelSpecView model, int requestedImplementationRevision, RepresentationEvidence evidence) {
        ImplementationSnapshot implementation = evidence.implementation();
        if (
            implementation == null ||
            !Objects.equals(implementation.modelSpecId(), model.id()) ||
            !Objects.equals(implementation.planId(), model.planId()) ||
            implementation.modelRevision() != model.revision() ||
            !Objects.equals(implementation.modelChecksum(), model.checksum()) ||
            implementation.implementationRevision() != requestedImplementationRevision ||
            implementation.implementationChecksum() == null ||
            implementation.implementationChecksum().isBlank() ||
            implementation.ownership() == null ||
            implementation.ownership() != model.implementationMode()
        ) {
            throw new ModelRepresentationException(
                "MODEL_REPRESENTATION_PIN_MISMATCH",
                "Implementation evidence does not match the exact logical and implementation pins",
                Kind.CONFLICT
            );
        }
        for (ArtifactEvidence artifact : evidence.artifacts()) {
            if (
                artifact == null ||
                !Objects.equals(artifact.modelSpecId(), model.id()) ||
                artifact.modelRevision() != model.revision() ||
                !Objects.equals(artifact.modelChecksum(), model.checksum()) ||
                artifact.implementationRevision() != implementation.implementationRevision() ||
                !Objects.equals(artifact.implementationChecksum(), implementation.implementationChecksum()) ||
                artifact.artifactChecksum() == null ||
                artifact.artifactChecksum().isBlank()
            ) {
                throw new ModelRepresentationException(
                    "MODEL_REPRESENTATION_ARTIFACT_PIN_MISMATCH",
                    "An implementation artifact does not match the exact model and implementation pins",
                    Kind.CONFLICT
                );
            }
        }
    }

    private static PublicationProjection publication(PublicationEvidence evidence) {
        if (evidence == null) return null;
        return new PublicationProjection(
            evidence.modelRevision(),
            evidence.modelChecksum(),
            evidence.implementationRevision(),
            evidence.implementationChecksum(),
            evidence.assetRef()
        );
    }

    private static PreviewCapabilityProjection preview(
        VisualizationCapability capability,
        List<CapabilityReason> reasons
    ) {
        boolean available = capability != VisualizationCapability.BLOCKED;
        return new PreviewCapabilityProjection(available, available ? List.of() : reasons);
    }

    private static List<String> businessActions(VisualizationCapability capability) {
        return switch (capability) {
            case BUSINESS_VISUAL_EDIT -> List.of("OPEN_VISUAL", "EDIT_VISUAL");
            case BUSINESS_VISUAL_READ -> List.of("OPEN_VISUAL");
            default -> List.of();
        };
    }

    private static List<String> technicalActions(VisualizationCapability capability) {
        return capability == VisualizationCapability.ADVANCED_DBT_IMPLEMENTATION
            ? List.of("OPEN_ADVANCED_DBT")
            : List.of();
    }

    private static List<CapabilityReason> mergeReasons(
        List<CapabilityReason> decisionReasons,
        List<CapabilityReason> projectionReasons
    ) {
        Set<CapabilityReason> merged = new LinkedHashSet<>();
        if (decisionReasons != null) merged.addAll(decisionReasons);
        if (projectionReasons != null) merged.addAll(projectionReasons);
        return List.copyOf(merged);
    }

    private String etag(RepresentationSemanticPayload payload) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(payload);
            return '"' + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical)) + '"';
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate model representation ETag", exception);
        }
    }

    /** Every serialized response semantic except the ETag itself participates in this canonical hash payload. */
    private record RepresentationSemanticPayload(
        RepresentationScope scope,
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
        PublicationProjection latestPublishedRef,
        PublicationProjection servingRef,
        RuntimeObservationProjection runtimeObservation,
        PreviewCapabilityProjection previewCapability,
        PhysicalPreviewProjection physicalPreview,
        DriftStatus driftStatus,
        TechnicalImplementationProjection technicalImplementation
    ) {}
}
