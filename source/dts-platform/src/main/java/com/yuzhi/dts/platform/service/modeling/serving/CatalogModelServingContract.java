package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import java.time.Instant;
import java.util.UUID;

/** Immutable Catalog projection references for one stable logical ModelSpec asset. */
public final class CatalogModelServingContract {

    private CatalogModelServingContract() {}

    /**
     * Governance publication pointer. candidateVersion is the optimistic Candidate row version;
     * evidenceCandidateVersion is the immutable build/observation version and remains null until promotion.
     */
    public record PublishedRef(
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        UUID candidateId,
        int candidateVersion,
        Integer evidenceCandidateVersion,
        UUID physicalAssetId,
        Instant publishedAt
    ) {}

    /** Serving pointer whose candidateVersion is always the immutable build/observation version. */
    public record ServingRef(
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        UUID candidateId,
        int candidateVersion,
        int attempt,
        UUID pipelineRunId,
        int observationAttempt,
        UUID relationEvidenceId,
        String evidenceChecksum,
        UUID physicalAssetId,
        UUID sourceId,
        String adapter,
        String databaseName,
        String schemaName,
        String identifier,
        Instant observedAt
    ) {}

    public record ModelServingProjection(
        String tenantId,
        UUID modelSpecId,
        CatalogAssetType catalogAssetType,
        String catalogAssetKey,
        PublishedRef latestPublishedRef,
        ServingRef servingRef,
        long version,
        String syncStatus,
        Instant updatedAt
    ) {}

    public record SuccessfulPublicationCommand(
        String tenantId,
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        UUID candidateId,
        int candidateVersion,
        CatalogAssetType catalogAssetType,
        String catalogAssetKey,
        UUID sourceId,
        String adapter,
        UUID physicalAssetId,
        Instant publishedAt
    ) {}
}
