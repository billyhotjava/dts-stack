package com.yuzhi.dts.platform.service.security.dto;

public record StatementExecutionResult(String key, String sql, Status status, String message, String errorCode) {

    public StatementExecutionResult(String key, String sql, Status status, String message) {
        this(key, sql, status, message, null);
    }

    public enum Status {
        SUCCEEDED,
        FAILED,
        SKIPPED
    }
}
