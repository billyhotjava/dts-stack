package com.yuzhi.dts.platform.service.security.dto;

public record StatementExecutionResult(String key, String sql, Status status, String message) {
    public enum Status {
        SUCCEEDED,
        FAILED,
        SKIPPED
    }
}
