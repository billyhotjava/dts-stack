package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

/** Shared pass-rate semantics for completed quality runs. */
public final class QualityRunOutcomeSemantics {

    public static final String QUALITY_VIOLATION = "QUALITY_VIOLATION";

    private QualityRunOutcomeSemantics() {}

    public static Integer passRate(
        String status,
        String errorCategory,
        Integer rowsTotal,
        Integer failingRowCount
    ) {
        String normalizedStatus = normalize(status);
        if ("SKIPPED".equals(normalizedStatus) || "QUEUED".equals(normalizedStatus) || "RUNNING".equals(normalizedStatus)) {
            return null;
        }
        if (isExecutionFailure(status, errorCategory, failingRowCount) || rowsTotal == null || rowsTotal <= 0 || failingRowCount == null) {
            return null;
        }
        if (rowsTotal != null && rowsTotal > 0) {
            int failing = Math.min(rowsTotal, Math.max(0, failingRowCount != null ? failingRowCount : 0));
            return Math.max(0, Math.min(100, Math.round(((rowsTotal - failing) * 100.0f) / rowsTotal)));
        }
        if (isPassed(status)) {
            return 100;
        }
        if ("FAILED".equals(normalizedStatus)) {
            return 0;
        }
        return null;
    }

    public static boolean isExecutionFailure(String status, String errorCategory, Integer failingRowCount) {
        if (!"FAILED".equals(normalize(status))) {
            return false;
        }
        String normalizedCategory = normalize(errorCategory);
        if (QUALITY_VIOLATION.equals(normalizedCategory)) {
            return false;
        }
        if (StringUtils.hasText(normalizedCategory)) {
            return true;
        }
        return failingRowCount == null || failingRowCount <= 0;
    }

    public static String dominantFailureCategory(List<StatementExecutionResult> results) {
        String qualityViolation = null;
        if (results == null) {
            return null;
        }
        for (StatementExecutionResult result : results) {
            if (result == null || result.status() != StatementExecutionResult.Status.FAILED) {
                continue;
            }
            String category;
            if (StringUtils.hasText(result.errorCode())) {
                category = normalizeErrorCode(result.errorCode());
            } else if (containsIgnoreCase(result.message(), "不符合规则")) {
                category = QUALITY_VIOLATION;
            } else {
                category = classifyExecutionError(result.message());
            }
            if (!QUALITY_VIOLATION.equals(category)) {
                return category;
            }
            qualityViolation = QUALITY_VIOLATION;
        }
        return qualityViolation;
    }

    public static String classifyExecutionError(String rawMessage) {
        String message = rawMessage != null ? rawMessage.trim().toLowerCase(Locale.ROOT) : "";
        if (message.isEmpty()) {
            return "UNKNOWN";
        }
        if (message.contains("permission denied") || message.contains("access denied") || message.contains("not authorized")) {
            return "PERMISSION_DENIED";
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return "TIMEOUT";
        }
        if (message.contains("syntax error") || message.contains("parse exception") || message.contains("parser")) {
            return "SQL_SYNTAX";
        }
        if (
            message.contains("does not exist") ||
            message.contains("not found") ||
            message.contains("unknown table") ||
            message.contains("unknown column")
        ) {
            return "OBJECT_NOT_FOUND";
        }
        if (message.contains("connection refused") || message.contains("connection reset") || message.contains("connection closed")) {
            return "CONNECTION_ERROR";
        }
        return "EXECUTION_ERROR";
    }

    public static String normalizeErrorCode(String errorCode) {
        String normalized = errorCode != null ? errorCode.trim().toUpperCase(Locale.ROOT).replace('-', '_') : "";
        if (normalized.startsWith("08")) return "CONNECTION_ERROR";
        if ("42501".equals(normalized)) return "PERMISSION_DENIED";
        if ("42P01".equals(normalized) || "42703".equals(normalized)) return "OBJECT_NOT_FOUND";
        if ("42601".equals(normalized)) return "SQL_SYNTAX";
        if ("57014".equals(normalized)) return "TIMEOUT";
        return normalized.matches("[A-Z0-9_]{1,64}") ? normalized : "EXECUTION_ERROR";
    }

    private static boolean isPassed(String status) {
        String normalized = normalize(status);
        return "SUCCESS".equals(normalized) ||
            "SUCCEEDED".equals(normalized) ||
            "PASSED".equals(normalized) ||
            "COMPLETED".equals(normalized);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(java.util.Locale.ROOT) : null;
    }

    private static boolean containsIgnoreCase(String value, String expected) {
        return value != null && expected != null && value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }
}
