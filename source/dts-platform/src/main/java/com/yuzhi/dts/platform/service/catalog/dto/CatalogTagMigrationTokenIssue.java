package com.yuzhi.dts.platform.service.catalog.dto;

import java.util.List;
import java.util.UUID;

public record CatalogTagMigrationTokenIssue(
    UUID datasetId,
    String assetKey,
    String token,
    String reason,
    List<UUID> candidateTagIds
) {}
