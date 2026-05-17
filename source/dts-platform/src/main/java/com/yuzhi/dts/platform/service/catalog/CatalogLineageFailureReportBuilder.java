package com.yuzhi.dts.platform.service.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CatalogLineageFailureReportBuilder {

    private static final String LINEAGE_GAP = "lineage";

    private CatalogLineageFailureReportBuilder() {}

    public static CatalogLineageFailureReport fromGovernanceGapReport(CatalogAssetPortalService.GovernanceGapReport source) {
        if (source == null) {
            return new CatalogLineageFailureReport(List.of(), Map.of(), Map.of(), 0, 0, 0, 0, 0, "catalog-governance-gaps", null);
        }
        List<CatalogLineageFailureItem> content = new ArrayList<>();
        Map<String, Long> severityCounts = new LinkedHashMap<>();
        Map<String, Long> reasonCounts = new LinkedHashMap<>();
        for (CatalogAssetPortalService.GovernanceGapAsset asset : source.content()) {
            if (asset == null) {
                continue;
            }
            boolean lineageMissing = containsGap(asset.warningGaps(), LINEAGE_GAP);
            boolean blocking = asset.blockingGaps() != null && !asset.blockingGaps().isEmpty();
            if (!lineageMissing && !blocking) {
                continue;
            }
            String severity = blocking ? "BLOCKING" : "WARNING";
            String reason = resolveReason(blocking, lineageMissing);
            increment(severityCounts, severity);
            increment(reasonCounts, reason);
            content.add(
                new CatalogLineageFailureItem(
                    asset.id(),
                    asset.displayName(),
                    asset.fqn(),
                    asset.assetKey(),
                    asset.grantAssetType(),
                    asset.grantAssetId(),
                    severity,
                    blocking,
                    asset.blockingGaps() == null ? List.of() : List.copyOf(asset.blockingGaps()),
                    asset.warningGaps() == null ? List.of() : List.copyOf(asset.warningGaps()),
                    reason,
                    resolveEvidenceSource(asset.metadataSource(), lineageMissing),
                    resolveNextAction(blocking, lineageMissing),
                    asset.metadataSource()
                )
            );
        }
        return new CatalogLineageFailureReport(
            List.copyOf(content),
            Map.copyOf(severityCounts),
            Map.copyOf(reasonCounts),
            source.inspected(),
            source.skipped(),
            source.totalCandidates(),
            source.page(),
            source.size(),
            "catalog-governance-gaps",
            source.metadataSource()
        );
    }

    private static boolean containsGap(List<String> gaps, String expected) {
        if (gaps == null || expected == null) {
            return false;
        }
        return gaps.stream().anyMatch(gap -> expected.equalsIgnoreCase(gap));
    }

    private static String resolveReason(boolean blocking, boolean lineageMissing) {
        if (blocking && lineageMissing) {
            return "asset-governance-blocking+lineage-missing";
        }
        if (blocking) {
            return "asset-governance-blocking";
        }
        return "lineage-missing";
    }

    private static String resolveEvidenceSource(String metadataSource, boolean lineageMissing) {
        if (!lineageMissing) {
            return metadataSource;
        }
        return metadataSource == null ? "catalog-lineage" : metadataSource + "+catalog-lineage";
    }

    private static String resolveNextAction(boolean blocking, boolean lineageMissing) {
        if (blocking) {
            return lineageMissing ? "补齐治理字段并接入血缘来源证明后再发布" : "补齐治理字段后再发布";
        }
        return "补充上游/下游血缘或运行来源证明";
    }

    private static void increment(Map<String, Long> counts, String key) {
        counts.merge(key, 1L, Long::sum);
    }
}
