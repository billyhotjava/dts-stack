package com.yuzhi.dts.analytics.web.rest.errors;

import com.yuzhi.dts.analytics.web.filter.RequestIdFilter;
import com.yuzhi.dts.analytics.web.support.RequestContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value for parameter '%s'".formatted(ex.getName());
        return buildError(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        log.error("[analytics] Database error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildError(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Analytics database unavailable or not initialized",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleDefault(Exception ex, HttpServletRequest request) {
        log.error("[analytics] Unhandled error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request);
    }

    private ResponseEntity<ApiError> buildError(HttpStatus status, String message, HttpServletRequest request) {
        String resolvedMessage = (message == null || message.isBlank()) ? status.getReasonPhrase() : message;
        ApiError payload = new ApiError(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                resolvedMessage,
                request.getRequestURI(),
                resolveRequestId());
        return ResponseEntity.status(status).body(payload);
    }

    private String resolveRequestId() {
        String requestId = RequestContextUtils.resolveRequestId();
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return MDC.get(RequestIdFilter.MDC_KEY_REQUEST_ID);
    }
}
