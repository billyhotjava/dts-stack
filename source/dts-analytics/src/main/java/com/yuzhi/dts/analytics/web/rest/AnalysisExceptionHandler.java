package com.yuzhi.dts.analytics.web.rest;

import com.yuzhi.dts.analytics.service.analysis.AnalysisConflictException;
import com.yuzhi.dts.analytics.service.analysis.AnalysisDependencyException;
import com.yuzhi.dts.analytics.service.analysis.AnalysisForbiddenException;
import com.yuzhi.dts.analytics.service.analysis.AnalysisNotFoundException;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQueryTimeoutException;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQueryCancelledException;
import com.yuzhi.dts.analytics.service.analysis.AnalysisRateLimitException;
import com.yuzhi.dts.analytics.service.analysis.AnalysisSpecValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = { AnalysisResource.class, DashboardResource.class })
public class AnalysisExceptionHandler {

    @ExceptionHandler(AnalysisSpecValidationException.class)
    public ResponseEntity<Map<String, Object>> validation(
        AnalysisSpecValidationException failure,
        HttpServletRequest request
    ) {
        HttpStatus status = failure.getErrorCode().contains("MALFORMED") || failure.getErrorCode().contains("UNKNOWN_FIELD")
            ? HttpStatus.BAD_REQUEST
            : HttpStatus.UNPROCESSABLE_ENTITY;
        return response(status, failure.getErrorCode(), failure.getMessage(), failure.getField(), request);
    }

    @ExceptionHandler(AnalysisConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(
        AnalysisConflictException failure,
        HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, failure.getErrorCode(), failure.getMessage(), null, request);
    }

    @ExceptionHandler(AnalysisNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(
        AnalysisNotFoundException failure,
        HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", failure.getMessage(), null, request);
    }

    @ExceptionHandler(AnalysisForbiddenException.class)
    public ResponseEntity<Map<String, Object>> forbidden(
        AnalysisForbiddenException failure,
        HttpServletRequest request
    ) {
        return response(HttpStatus.FORBIDDEN, "ANALYSIS_FORBIDDEN", failure.getMessage(), null, request);
    }

    @ExceptionHandler(AnalysisDependencyException.class)
    public ResponseEntity<Map<String, Object>> dependency(
        AnalysisDependencyException failure,
        HttpServletRequest request
    ) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, failure.getErrorCode(), failure.getMessage(), null, request);
    }

    @ExceptionHandler(AnalysisRateLimitException.class)
    public ResponseEntity<Map<String, Object>> rateLimit(
        AnalysisRateLimitException failure,
        HttpServletRequest request
    ) {
        Map<String, Object> body = body(
            "ANALYSIS_QUERY_LIMIT_EXCEEDED",
            failure.getMessage(),
            null,
            request
        );
        body.put("scope", failure.getScope());
        body.put("retryAfter", failure.getRetryAfterSeconds());
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(failure.getRetryAfterSeconds()))
            .body(body);
    }

    @ExceptionHandler(AnalysisQueryTimeoutException.class)
    public ResponseEntity<Map<String, Object>> timeout(
        AnalysisQueryTimeoutException failure,
        HttpServletRequest request
    ) {
        return response(HttpStatus.GATEWAY_TIMEOUT, "ANALYSIS_QUERY_TIMEOUT", failure.getMessage(), null, request);
    }

    @ExceptionHandler(AnalysisQueryCancelledException.class)
    public ResponseEntity<Map<String, Object>> cancelled(
        AnalysisQueryCancelledException failure,
        HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "ANALYSIS_QUERY_CANCELLED", failure.getMessage(), null, request);
    }

    private ResponseEntity<Map<String, Object>> response(
        HttpStatus status,
        String errorCode,
        String message,
        String field,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(body(errorCode, message, field, request));
    }

    private Map<String, Object> body(
        String errorCode,
        String message,
        String field,
        HttpServletRequest request
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("errorCode", errorCode);
        body.put("message", message);
        if (field != null) body.put("field", field);
        String correlationId = correlationId(request);
        if (correlationId != null) body.put("correlationId", correlationId);
        return body;
    }

    private String correlationId(HttpServletRequest request) {
        if (request == null) return null;
        for (String header : new String[] { "X-Correlation-Id", "X-Request-Id" }) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }
}
