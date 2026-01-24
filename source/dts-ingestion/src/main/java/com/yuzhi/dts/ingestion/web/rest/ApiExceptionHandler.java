package com.yuzhi.dts.ingestion.web.rest;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = ex.getMessage();
        }
        return ResponseEntity.ok(ApiResponses.error(status, message == null ? "request failed" : message));
    }

    @ExceptionHandler({ MethodArgumentNotValidException.class, BindException.class })
    public ResponseEntity<ApiResponse<Object>> handleValidation(Exception ex) {
        String message = "参数校验失败";
        if (ex instanceof MethodArgumentNotValidException manve) {
            message = summarizeErrors(manve.getAllErrors());
        } else if (ex instanceof BindException be) {
            message = summarizeErrors(be.getAllErrors());
        }
        return ResponseEntity.ok(ApiResponses.error(HttpStatus.BAD_REQUEST.value(), message));
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
        return ResponseEntity.ok(ApiResponses.error(HttpStatus.BAD_REQUEST.value(), message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleReadable(HttpMessageNotReadableException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = "请求体解析失败";
        }
        return ResponseEntity.ok(ApiResponses.error(HttpStatus.BAD_REQUEST.value(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = "服务内部错误";
        }
        return ResponseEntity.ok(ApiResponses.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), message));
    }

    private String summarizeErrors(java.util.List<ObjectError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "参数校验失败";
        }
        return errors.stream().map(ObjectError::getDefaultMessage).distinct().collect(Collectors.joining("; "));
    }
}
