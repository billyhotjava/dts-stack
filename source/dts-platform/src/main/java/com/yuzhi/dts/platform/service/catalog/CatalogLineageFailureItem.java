package com.yuzhi.dts.platform.service.catalog;

import java.util.List;
import java.util.UUID;

public record CatalogLineageFailureItem(
    UUID id,
    String displayName,
    String fqn,
    String assetKey,
    String grantAssetType,
    String grantAssetId,
    String severity,
    boolean blocking,
    List<String> blockingGaps,
    List<String> warningGaps,
    String reason,
    String evidenceSource,
    String nextAction,
    String metadataSource
) {}
