package com.yuzhi.dts.platform.service.catalog.dto;

import java.util.List;

public record CatalogTagMigrationReport(
    String batchId,
    String checksum,
    long datasetCount,
    long nonEmptyDatasetCount,
    long tokenCount,
    long matchedTokenCount,
    List<CatalogTagMigrationTokenIssue> unmatchedTokens,
    List<CatalogTagMigrationTokenIssue> ambiguousTokens,
    List<CatalogTagProtectedEvidence> protectedEvidence,
    long plannedRelationCount,
    long existingRelationCount,
    List<CatalogTagMigrationRelation> relations
) {}
