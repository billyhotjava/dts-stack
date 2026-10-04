package com.yuzhi.dts.platform.service.goldenchain.consumption;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.List;
import java.util.Objects;

public record GoldenChainReportDatasetPublication(
    boolean publishable,
    String datasetRef,
    String metricCatalogRef,
    String consumerSummary,
    List<GoldenChainBusinessField> fields,
    List<String> blockers,
    GoldenChainStageSnapshot stageSnapshot
) {
    public GoldenChainReportDatasetPublication {
        datasetRef = normalize(datasetRef);
        metricCatalogRef = normalize(metricCatalogRef);
        consumerSummary = normalize(consumerSummary);
        fields = fields == null ? List.of() : List.copyOf(fields);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        stageSnapshot = Objects.requireNonNull(stageSnapshot, "stageSnapshot must not be null");
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
