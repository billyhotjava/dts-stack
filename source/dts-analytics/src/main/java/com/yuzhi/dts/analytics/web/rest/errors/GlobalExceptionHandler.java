package com.yuzhi.dts.analytics.web.rest.errors;

import com.yuzhi.dts.analytics.web.filter.RequestIdFilter;
import com.yuzhi.dts.analytics.web.support.RequestContextUtils;
import com.zaxxer.hikari.pool.HikariPool;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.ConnectException;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
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
                response);
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
                response);
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
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, "EXT_DB_CONNECT_FAILED", message, request, response);
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
        String resolvedMessage = (message == null || message.isBlank()) ? status.getReasonPhrase() : message;
        String resolvedCode = (code == null || code.isBlank()) ? "UNKNOWN" : code;
        String requestId = resolveRequestId();
        if (response != null) {
            response.setHeader(ERROR_CODE_HEADER, resolvedCode);
        }
        ApiError payload = new ApiError(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                resolvedCode,
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
}
