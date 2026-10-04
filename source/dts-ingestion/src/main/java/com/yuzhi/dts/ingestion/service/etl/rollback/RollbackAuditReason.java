package com.yuzhi.dts.ingestion.service.etl.rollback;

public enum RollbackAuditReason {
    ROLLBACK_APPLIED,
    ROLLBACK_PARTIAL,
    ROLLBACK_REJECTED,
    ROLLBACK_FAILED,
    SOURCE_REVALIDATION_PROGRESS,
    SOURCE_AVAILABILITY_RESTORE
}
