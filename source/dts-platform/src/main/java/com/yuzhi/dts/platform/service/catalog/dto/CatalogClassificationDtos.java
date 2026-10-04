package com.yuzhi.dts.platform.service.catalog.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CatalogClassificationDtos {

    private CatalogClassificationDtos() {}

    public record SealRequest(
        String subjectType,
        String subjectKey,
        String assetType,
        String declaredLevel,
        String detectedLevel,
        String manualFloor,
        List<String> upstreamLevels,
        String originType,
        String originRef,
        String evidenceChecksum,
        String evidenceJson
    ) {}

    public record RaiseRequest(
        String subjectType,
        String subjectKey,
        String candidateLevel,
        String triggerRef,
        String evidenceJson
    ) {}

    public record InheritRequest(
        String subjectType,
        String subjectKey,
        List<String> upstreamLevels,
        String triggerRef,
        String evidenceJson
    ) {}

    public record SealReference(
        UUID sealId,
        String subjectType,
        String subjectKey,
        String assetType,
        String effectiveLevel,
        long snapshotVersion,
        String checksum,
        Instant sealedAt,
        String propagationStatus
    ) {}

    public record EventEvidence(
        UUID id,
        String eventType,
        String previousLevel,
        String candidateLevel,
        String resultingLevel,
        String triggerType,
        String triggerRef,
        String evidenceJson,
        String actor,
        Instant occurredAt,
        long snapshotVersion
    ) {}

    public record Explanation(SealReference seal, List<EventEvidence> events) {}

    public record PropagationJobView(
        UUID id,
        UUID targetDatasetId,
        String targetAssetKey,
        String triggerType,
        String triggerRef,
        String status,
        int attempts,
        int affectedSubjects,
        Instant nextAttemptAt,
        String lastError
    ) {}
}
