package com.yuzhi.dts.metrics.service.dto;

public enum MetricLifecycleStatus {
    ARTIFACT_GENERATED,
    DBT_VALIDATED,
    REVIEW_SUBMITTED,
    PUBLISH_DRY_RUN_READY,
    PUBLISHED,
    ROLLED_BACK;

    public String code() {
        return name();
    }
}
