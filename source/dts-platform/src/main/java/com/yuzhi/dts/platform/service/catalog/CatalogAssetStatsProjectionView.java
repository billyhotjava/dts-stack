package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.StatsBucket;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.StatsSnapshot;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.Freshness;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.GovernanceReadiness;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Compatibility view for domain navigation backed only by the bounded statistics projection. */
public record CatalogAssetStatsProjectionView(
    DomainStats all,
    DomainStats unassigned,
    Map<String, DomainStats> byDomain,
    long scanned,
    boolean truncated,
    Instant asOf,
    Freshness freshness,
    boolean approximate,
    String projectionState
) {
    public CatalogAssetStatsProjectionView {
        byDomain = byDomain == null ? Map.of() : Map.copyOf(byDomain);
    }

    public static CatalogAssetStatsProjectionView from(StatsSnapshot snapshot) {
        Map<String, long[]> domainCounts = new LinkedHashMap<>();
        long attention = 0;
        long unassignedTotal = 0;
        long unassignedAttention = 0;
        for (StatsBucket bucket : snapshot.buckets()) {
            boolean needsAttention = bucket.governance() != GovernanceReadiness.GOVERNED;
            if (needsAttention) {
                attention += bucket.count();
            }
            if (bucket.domainId() == null) {
                unassignedTotal += bucket.count();
                if (needsAttention) {
                    unassignedAttention += bucket.count();
                }
                continue;
            }
            long[] counts = domainCounts.computeIfAbsent(bucket.domainId().toString(), ignored -> new long[2]);
            counts[0] += bucket.count();
            if (needsAttention) {
                counts[1] += bucket.count();
            }
        }

        Map<String, DomainStats> byDomain = new LinkedHashMap<>();
        domainCounts.forEach((domainId, counts) -> byDomain.put(domainId, new DomainStats(counts[0], counts[1])));
        return new CatalogAssetStatsProjectionView(
            new DomainStats(snapshot.total(), attention),
            new DomainStats(unassignedTotal, unassignedAttention),
            byDomain,
            0,
            false,
            snapshot.asOf(),
            snapshot.freshness(),
            snapshot.approximate(),
            snapshot.projectionState()
        );
    }

    public record DomainStats(long total, long attention) {}
}
