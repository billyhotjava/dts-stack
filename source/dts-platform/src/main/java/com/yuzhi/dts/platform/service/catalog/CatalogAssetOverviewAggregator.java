package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetPortalService.AssetSummary;
import java.time.Instant;
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

    /** 单个主题域下的资产体量与待处置数，供左侧范围导航展示。 */
    public record DomainStats(long total, long attention) {}

    public record AssetOverview(
        long total,
        long unclassified,
        long missingDomain,
        long stale,
        long attention,
        long tagged,
        long untagged,
        int tagCoveragePercent,
        Map<String, Long> byLayer,
        Map<String, Long> governanceStatusCounts,
        Map<String, Long> eligibilityCounts,
        List<MatrixCell> matrix,
        Map<String, DomainStats> byDomain,
        Map<String, Long> domainCountByLayer,
        int scanned,
        boolean truncated,
        Instant asOf
    ) {
        public AssetOverview(
            long total,
            long unclassified,
            long missingDomain,
            long stale,
            long attention,
            long tagged,
            long untagged,
            int tagCoveragePercent,
            Map<String, Long> byLayer,
            Map<String, Long> governanceStatusCounts,
            List<MatrixCell> matrix,
            Map<String, DomainStats> byDomain,
            Map<String, Long> domainCountByLayer,
            int scanned,
            boolean truncated
        ) {
            this(
                total,
                unclassified,
                missingDomain,
                stale,
                attention,
                tagged,
                untagged,
                tagCoveragePercent,
                byLayer,
                governanceStatusCounts,
                Map.of(),
                matrix,
                byDomain,
                domainCountByLayer,
                scanned,
                truncated,
                null
            );
        }
    }

    public static AssetOverview aggregate(List<AssetSummary> rows, int scanned, boolean truncated) {
        long unclassified = 0;
        long missingDomain = 0;
        long stale = 0;
        long attention = 0;
        long tagged = 0;
        Map<String, Long> byLayer = new LinkedHashMap<>();
        Map<String, Long> governanceStatusCounts = new LinkedHashMap<>();
        Map<String, Long> eligibilityCounts = new LinkedHashMap<>();
        Map<String, long[]> matrixCells = new LinkedHashMap<>();
        Instant asOf = null;

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
            if (!row.assetTags().isEmpty()) tagged++;
            if (StringUtils.hasText(governanceStatus)) {
                governanceStatusCounts.merge(governanceStatus, 1L, Long::sum);
            }
            String eligibility = normalizeUpper(row.consumptionEligibility());
            if (StringUtils.hasText(eligibility)) {
                eligibilityCounts.merge(eligibility, 1L, Long::sum);
            }
            if (row.projectionUpdatedAt() != null && (asOf == null || row.projectionUpdatedAt().isAfter(asOf))) {
                asOf = row.projectionUpdatedAt();
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

        // 域级 rollup 由 matrix 的 domainId 维度归并而来，不引入第二套统计口径。
        // domainId 为 null 的行属于"未归域"，由 missingDomain 表达，不进入本表。
        Map<String, DomainStats> byDomain = new LinkedHashMap<>();
        for (MatrixCell cell : matrix) {
            if (cell.domainId() == null) {
                continue;
            }
            DomainStats current = byDomain.getOrDefault(cell.domainId(), new DomainStats(0, 0));
            byDomain.put(cell.domainId(), new DomainStats(current.total() + cell.total(), current.attention() + cell.attention()));
        }

        long untagged = rows.size() - tagged;
        int tagCoveragePercent = rows.isEmpty() ? 0 : (int) Math.round((tagged * 100.0d) / rows.size());
        Map<String, Long> domainCountByLayer = new LinkedHashMap<>();
        for (MatrixCell cell : matrix) {
            if (cell.domainId() == null) {
                continue;
            }
            domainCountByLayer.merge(cell.layer(), 1L, Long::sum);
        }
        return new AssetOverview(
            rows.size(),
            unclassified,
            missingDomain,
            stale,
            attention,
            tagged,
            untagged,
            tagCoveragePercent,
            byLayer,
            governanceStatusCounts,
            eligibilityCounts,
            matrix,
            byDomain,
            domainCountByLayer,
            scanned,
            truncated,
            asOf
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
