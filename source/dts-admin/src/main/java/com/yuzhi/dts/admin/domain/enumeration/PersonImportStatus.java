package com.yuzhi.dts.admin.domain.enumeration;

/**
 * 人员导入批次状态。
 */
public enum PersonImportStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED;

    public boolean isTerminal() {
        return switch (this) {
            case COMPLETED, COMPLETED_WITH_ERRORS, FAILED -> true;
            default -> false;
        };
    }
}
