package com.yuzhi.dts.copilot.analytics.web.rest.errors;

import com.yuzhi.dts.copilot.analytics.web.filter.RequestIdFilter;
import com.yuzhi.dts.copilot.analytics.web.support.RequestContextUtils;
import com.zaxxer.hikari.pool.HikariPool;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.ConnectException;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_CODE_HEADER = "X-Error-Code";
    private static final String ERROR_RETRYABLE_HEADER = "X-Error-Retryable";

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        return buildError(HttpStatus.BAD_REQUEST, "REQ_MISSING_PARAM", ex.getMessage(), request, response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        String message = "Invalid value for parameter '%s'".formatted(ex.getName());
        return buildError(HttpStatus.BAD_REQUEST, "REQ_INVALID_PARAM", message, request, response);
    }

    @ExceptionHandler(ScreenSpecValidationException.class)
    public ResponseEntity<ApiError> handleScreenSpecValidation(
            ScreenSpecValidationException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), request, response, false);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        IllegalArgumentMapping mapping = mapIllegalArgument(ex);
        return buildError(HttpStatus.BAD_REQUEST, mapping.code(), mapping.message(), request, response, mapping.retryable());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.warn("[analytics] Data integrity violation on {}: {}", request.getRequestURI(), ex.getMessage());
        if ("DELETE".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().startsWith("/api/database/")) {
            return buildError(
                    HttpStatus.CONFLICT,
                    "DB_DELETE_CONFLICT",
                    "Database still has related metadata and cannot be deleted directly",
                    request,
                    response,
                    false);
        }
        return buildError(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", ex.getMessage(), request, response, false);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccess(
            DataAccessException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.error("[analytics] Database error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildError(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DB_UNAVAILABLE",
                "Analytics database unavailable or not initialized",
                request,
                response,
                true);
    }

    @ExceptionHandler(UnexpectedRollbackException.class)
    public ResponseEntity<ApiError> handleUnexpectedRollback(
            UnexpectedRollbackException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.error("[analytics] Transaction rollback on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "TX_ROLLBACK",
                "Transaction rolled back unexpectedly",
                request,
                response,
                true);
    }

    @ExceptionHandler(HikariPool.PoolInitializationException.class)
    public ResponseEntity<ApiError> handlePoolInit(
            HikariPool.PoolInitializationException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.error("[analytics] External DB connection init failed on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        String message = "External database connection failed";
        if (hasCause(ex, ConnectException.class)) {
            message = "External database connection refused";
        }
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, "EXT_DB_CONNECT_FAILED", message, request, response, true);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleDefault(
            Exception ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.error("[analytics] Unhandled error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.getMessage(), request, response);
    }

    private ResponseEntity<ApiError> buildError(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            HttpServletResponse response) {
        boolean retryable = status.value() == 408
                || status.value() == 429
                || status.value() == 502
                || status.value() == 503
                || status.value() == 504;
        return buildError(status, code, message, request, response, retryable);
    }

    private ResponseEntity<ApiError> buildError(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            HttpServletResponse response,
            boolean retryable) {
        String resolvedMessage = (message == null || message.isBlank()) ? status.getReasonPhrase() : message;
        String resolvedCode = (code == null || code.isBlank()) ? "UNKNOWN" : code;
        String requestId = resolveRequestId();
        if (response != null) {
            response.setHeader(ERROR_CODE_HEADER, resolvedCode);
            response.setHeader(ERROR_RETRYABLE_HEADER, String.valueOf(retryable));
        }
        ApiError payload = new ApiError(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                resolvedCode,
                retryable,
                resolvedMessage,
                request.getRequestURI(),
                requestId);
        return ResponseEntity.status(status).body(payload);
    }

    private String resolveRequestId() {
        String requestId = RequestContextUtils.resolveRequestId();
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return MDC.get(RequestIdFilter.MDC_KEY_REQUEST_ID);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private IllegalArgumentMapping mapIllegalArgument(IllegalArgumentException ex) {
        String message = ex == null ? null : ex.getMessage();
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (normalized.contains("missing required sql template parameters")
                || normalized.contains("missing required parameter:")) {
            return new IllegalArgumentMapping("SQL_TEMPLATE_PARAM_MISSING", false, message);
        }
        if (normalized.contains("unsupported parameter(s):")) {
            return new IllegalArgumentMapping("SQL_TEMPLATE_PARAM_UNSUPPORTED", false, message);
        }
        if (normalized.contains("invalid parameter name:")) {
            return new IllegalArgumentMapping("SQL_TEMPLATE_PARAM_INVALID", false, message);
        }
        if (normalized.contains("only select/with read-only sql is allowed")) {
            return new IllegalArgumentMapping("SQL_READ_ONLY_REQUIRED", false, message);
        }
        if (normalized.contains("multiple sql statements are not allowed")) {
            return new IllegalArgumentMapping("SQL_MULTI_STATEMENT_BLOCKED", false, message);
        }
        if (normalized.contains("dangerous sql statement is blocked")) {
            return new IllegalArgumentMapping("SQL_DANGEROUS_STATEMENT_BLOCKED", false, message);
        }
        if (normalized.contains("sql is too long")) {
            return new IllegalArgumentMapping("SQL_TOO_LONG", false, message);
        }
        if (normalized.contains("sql is empty after normalization")) {
            return new IllegalArgumentMapping("SQL_EMPTY", false, message);
        }
        return new IllegalArgumentMapping("REQ_INVALID_ARGUMENT", false, message);
    }

    private record IllegalArgumentMapping(String code, boolean retryable, String message) {}
}
