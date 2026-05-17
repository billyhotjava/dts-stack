package com.yuzhi.dts.platform.service.catalog;

import java.util.List;

public record CatalogAssetGovernanceProfile(
    String lifecycleStatus,
    String governanceStatus,
    List<String> missingFields,
    boolean consumable
) {}
