package com.yuzhi.dts.platform.service.catalog;

import java.util.List;

public record CatalogGovernanceGapEvaluation(
    String severity,
    List<String> blockingGaps,
    List<String> warningGaps
) {
    public CatalogGovernanceGapEvaluation {
        severity = severity == null || severity.isBlank() ? "READY" : severity;
        blockingGaps = blockingGaps == null ? List.of() : List.copyOf(blockingGaps);
        warningGaps = warningGaps == null ? List.of() : List.copyOf(warningGaps);
    }
}
