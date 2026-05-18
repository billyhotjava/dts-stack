package com.yuzhi.dts.opmanager.job;

public enum UpgradeJobState {
    REGISTERED,
    VALIDATED,
    PLANNED,
    READY,
    BACKING_UP,
    LOADING_IMAGES,
    APPLYING_CONFIG,
    RECREATING_SERVICES,
    HEALTH_CHECKING,
    COMPLETED,
    FAILED,
    ROLLBACK_REQUIRED,
    ROLLING_BACK,
    ROLLED_BACK
}
