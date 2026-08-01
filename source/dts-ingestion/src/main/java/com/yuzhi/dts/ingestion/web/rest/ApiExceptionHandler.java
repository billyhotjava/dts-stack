package com.yuzhi.dts.ingestion.web.rest;

import com.yuzhi.dts.ingestion.service.etl.api.ApiHttpException;
import com.yuzhi.dts.ingestion.service.security.IngestionSensitiveConfigSupport;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiHttpException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiHttp(ApiHttpException ex) {
        LOG.warn(
            "External API call failed code={} status={} attempts={} detail={}",
            ex.getCode(),
            ex.getStatusCode(),
            ex.getAttempts(),
            IngestionSensitiveConfigSupport.sanitizeText(ex.getMessage())
        );
        return stableError(HttpStatus.BAD_GATEWAY.value(), "外部 API 调用失败", ex.getCode());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        LOG.warn(
            "Request rejected status={} detail={}",
            status,
            IngestionSensitiveConfigSupport.sanitizeText(ex.getMessage())
        );
        return stableError(status, stableStatusMessage(status), "HTTP_" + status);
    }

    @ExceptionHandler({ MethodArgumentNotValidException.class, BindException.class })
    public ResponseEntity<ApiResponse<Object>> handleValidation(Exception ex) {
        String message = "参数校验失败";
        if (ex instanceof MethodArgumentNotValidException manve) {
            message = summarizeErrors(manve.getAllErrors());
        } else if (ex instanceof BindException be) {
            message = summarizeErrors(be.getAllErrors());
        }
        return stableError(HttpStatus.BAD_REQUEST.value(), message, "VALIDATION_ERROR");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraint(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations()
            .stream()
            .map(v -> v.getPropertyPath() + ":" + v.getMessage())
            .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "参数校验失败";
        }
        return stableError(HttpStatus.BAD_REQUEST.value(), message, "VALIDATION_ERROR");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleReadable(HttpMessageNotReadableException ex) {
        LOG.warn("Request body parse failed detail={}", IngestionSensitiveConfigSupport.sanitizeText(ex.getMessage()));
        return stableError(HttpStatus.BAD_REQUEST.value(), "请求体解析失败", "INVALID_REQUEST_BODY");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        LOG.warn("Request forbidden type={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "无权访问", "HTTP_403", null)
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Exception ex) {
        LOG.error(
            "Unhandled API error type={} detail={}",
            ex.getClass().getSimpleName(),
            IngestionSensitiveConfigSupport.sanitizeText(ex.getMessage())
        );
        return stableError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务内部错误", "INTERNAL_ERROR");
    }

    private ResponseEntity<ApiResponse<Object>> stableError(int status, String message, String code) {
        return ResponseEntity.status(status).body(new ApiResponse<>(status, message, code, null));
    }

    private String stableStatusMessage(int status) {
        return switch (status) {
            case 400 -> "请求参数错误";
            case 401 -> "未认证";
            case 403 -> "无权访问";
            case 404 -> "资源不存在";
            case 409 -> "请求状态冲突";
            default -> status >= 500 ? "服务内部错误" : "请求失败";
        };
    }

    private String summarizeErrors(java.util.List<ObjectError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "参数校验失败";
        }
        return errors.stream().map(ObjectError::getDefaultMessage).distinct().collect(Collectors.joining("; "));
    }
}
