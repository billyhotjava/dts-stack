package com.yuzhi.dts.platform.domain.catalog;

public enum DatasetJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
