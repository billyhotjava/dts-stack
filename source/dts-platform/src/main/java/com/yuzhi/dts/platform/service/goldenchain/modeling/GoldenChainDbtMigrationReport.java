package com.yuzhi.dts.platform.service.goldenchain.modeling;

import java.util.List;

public record GoldenChainDbtMigrationReport(
    int totalAssets,
    int highRiskAssets,
    int mediumRiskAssets,
    int lowRiskAssets,
    List<GoldenChainDbtMigrationItem> items
) {
    public GoldenChainDbtMigrationReport {
        items = items == null ? List.of() : List.copyOf(items);
        if (totalAssets != items.size()) {
            throw new IllegalArgumentException("资产总数必须等于清单条目数");
        }
    }
}
