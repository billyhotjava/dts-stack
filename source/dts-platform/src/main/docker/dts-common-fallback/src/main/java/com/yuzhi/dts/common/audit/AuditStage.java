package com.yuzhi.dts.common.audit;

public enum AuditStage {
    BEGIN,
    SUCCESS,
    FAIL;

    public static AuditStage fromString(String value) {
        if (value == null) {
            return SUCCESS;
        }
        return switch (value.trim().toUpperCase()) {
            case "BEGIN", "START" -> BEGIN;
            case "FAIL", "FAILED", "FAILURE", "ERROR" -> FAIL;
            default -> SUCCESS;
        };
    }
}
