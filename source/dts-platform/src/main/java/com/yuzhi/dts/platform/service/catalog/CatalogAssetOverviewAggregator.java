package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetPortalService.AssetSummary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * 资产概览聚合（地图页数据源）：对 listAssets 产出的行做全量统计。
 * attention = 未定密 ∨ 未归域 ∨ 生命周期已弃用/已归档/已阻断 ∨ 治理状态 PENDING_xx 或 DISABLED。
 * 纯函数，可见性/过滤完全由调用方（listAssets）保证。
 */
public final class CatalogAssetOverviewAggregator {

    /** 与前端 LAYER_ORDER 对齐；未识别的归 OTHER。 */
    private static final Set<String> KNOWN_LAYERS = Set.of("SOURCE", "ODS", "STG", "DWD", "DIM", "DWS", "ADS");

    /**
     * 失效资产的生命周期取值。历史代码比较的 "STALE" 不在
     * CatalogAssetLifecycleStatus 中且从未被写入，导致该指标恒为 0。
     */
    private static final Set<String> STALE_LIFECYCLE_STATUSES = Set.of("DEPRECATED", "ARCHIVED", "BLOCKED");

    private CatalogAssetOverviewAggregator() {}

    public record MatrixCell(String layer, String domainId, long total, long attention) {}

    public record AssetOverview(
        long total,
        long unclassified,
        long missingDomain,
        long stale,
        long attention,
        Map<String, Long> byLayer,
        Map<String, Long> governanceStatusCounts,
        List<MatrixCell> matrix,
        int scanned,
        boolean truncated
    ) {}

    public static AssetOverview aggregate(List<AssetSummary> rows, int scanned, boolean truncated) {
        long unclassified = 0;
        long missingDomain = 0;
        long stale = 0;
        long attention = 0;
        Map<String, Long> byLayer = new LinkedHashMap<>();
        Map<String, Long> governanceStatusCounts = new LinkedHashMap<>();
        Map<String, long[]> matrixCells = new LinkedHashMap<>();

        for (AssetSummary row : rows) {
            boolean rowUnclassified = !StringUtils.hasText(row.classification());
            boolean rowMissingDomain = row.domainId() == null;
            boolean rowStale = STALE_LIFECYCLE_STATUSES.contains(normalizeUpper(row.lifecycleStatus()));
            String governanceStatus = normalizeUpper(row.governanceStatus());
            boolean rowGovernancePending =
                governanceStatus.startsWith("PENDING_") || "DISABLED".equals(governanceStatus);
            boolean rowAttention = rowUnclassified || rowMissingDomain || rowStale || rowGovernancePending;

            if (rowUnclassified) unclassified++;
            if (rowMissingDomain) missingDomain++;
            if (rowStale) stale++;
            if (rowAttention) attention++;
            if (StringUtils.hasText(governanceStatus)) {
                governanceStatusCounts.merge(governanceStatus, 1L, Long::sum);
            }

            String layer = normalizeLayer(row.warehouseLayer());
            byLayer.merge(layer, 1L, Long::sum);

            String domainKey = row.domainId() == null ? null : row.domainId().toString();
            String cellKey = layer + "|" + domainKey;
            long[] cell = matrixCells.computeIfAbsent(cellKey, key -> new long[] { 0, 0 });
            cell[0]++;
            if (rowAttention) {
                cell[1]++;
            }
        }

        List<MatrixCell> matrix = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : matrixCells.entrySet()) {
            int sep = entry.getKey().indexOf('|');
            String layer = entry.getKey().substring(0, sep);
            String domainRaw = entry.getKey().substring(sep + 1);
            String domainId = "null".equals(domainRaw) ? null : domainRaw;
            matrix.add(new MatrixCell(layer, domainId, entry.getValue()[0], entry.getValue()[1]));
        }

        return new AssetOverview(
            rows.size(),
            unclassified,
            missingDomain,
            stale,
            attention,
            byLayer,
            governanceStatusCounts,
            matrix,
            scanned,
            truncated
        );
    }

    private static String normalizeLayer(String value) {
        String normalized = normalizeUpper(value);
        return KNOWN_LAYERS.contains(normalized) ? normalized : "OTHER";
    }

    private static String normalizeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
