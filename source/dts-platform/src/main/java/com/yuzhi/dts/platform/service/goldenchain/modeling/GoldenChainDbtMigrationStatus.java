package com.yuzhi.dts.platform.service.goldenchain.modeling;

public enum GoldenChainDbtMigrationStatus {
    MANAGED,
    NEED_SOURCE,
    NEED_ASSET,
    NEED_LINEAGE,
    NEED_RUNTIME_GRAPH,
    NEED_CONFIRMATION
}
