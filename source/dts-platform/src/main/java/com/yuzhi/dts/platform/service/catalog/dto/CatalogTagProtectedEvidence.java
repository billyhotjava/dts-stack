package com.yuzhi.dts.platform.service.catalog.dto;

import java.util.UUID;

public record CatalogTagProtectedEvidence(
    UUID datasetId,
    String assetKey,
    String format,
    String originalTags
) {}
