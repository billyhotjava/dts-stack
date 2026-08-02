package com.yuzhi.dts.platform.service.modeling.representation;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Exact-pin read port for implementation, publication and runtime evidence. */
public interface ModelRepresentationEvidencePort {
    Optional<ImplementationPin> findCurrentPin(String tenantId, UUID modelSpecId);

    Optional<RepresentationEvidence> findExact(
        String tenantId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        boolean includeTechnicalContent
    );

    record ImplementationPin(UUID implementationId, int implementationRevision, String implementationChecksum) {}

    record ImplementationSnapshot(
        UUID implementationId,
        UUID modelSpecId,
        UUID planId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        ImplementationMode ownership,
        String projectKey,
        String dbtUniqueId,
        String inputMode,
        JsonNode inputs,
        JsonNode fieldMappings,
        JsonNode settings,
        String materialization
    ) {}

    record ArtifactEvidence(
        String artifactType,
        String artifactPath,
        String artifactChecksum,
        String status,
        String artifactContent,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum
    ) {}

    record PublicationEvidence(
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        String assetRef
    ) {}

    record RuntimeEvidence(
        UUID evidenceId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        boolean verified,
        boolean relationExists,
        String metadataChecksum,
        Instant observedAt,
        List<ObservedField> fields
    ) {
        public RuntimeEvidence {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    record ObservedField(String name, String dataType) {}

    record RepresentationEvidence(
        ImplementationSnapshot implementation,
        List<ArtifactEvidence> artifacts,
        PublicationEvidence latestPublished,
        PublicationEvidence serving,
        RuntimeEvidence runtime,
        List<ModelRepresentationContract.CapabilityReason> projectionIssues
    ) {
        public RepresentationEvidence {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
            projectionIssues = projectionIssues == null ? List.of() : List.copyOf(projectionIssues);
        }
    }
}
