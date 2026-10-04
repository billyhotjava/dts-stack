package com.yuzhi.dts.platform.service.catalog.dto;

import java.util.UUID;

public record CatalogTagMigrationRelation(
    UUID datasetId,
    UUID tagId,
    String tagCode,
    String tagName,
    String assetType,
    String assetKey,
    boolean existing
) {}
