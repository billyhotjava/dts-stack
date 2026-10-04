package com.yuzhi.dts.platform.service.goldenchain.ops;

public enum GoldenChainOpsRetryAction {
    RETRY_INGESTION,
    ROTATE_CREDENTIAL,
    OPEN_BACKFILL,
    REBUILD_MODEL,
    REFRESH_LINEAGE,
    REQUEST_PERMISSION,
    MANUAL_REVIEW
}
