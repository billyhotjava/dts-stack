package com.yuzhi.dts.platform.service.catalog;

import java.time.Instant;
import java.util.UUID;

public record CatalogAssetColumnContract(
    UUID id,
    String name,
    String dataType,
    Boolean nullable,
    Integer ordinalPosition,
    String description,
    String tags,
    String sensitiveTags,
    UUID standardId,
    String standardRule,
    String status,
    String source,
    Instant updatedAt
) {}
