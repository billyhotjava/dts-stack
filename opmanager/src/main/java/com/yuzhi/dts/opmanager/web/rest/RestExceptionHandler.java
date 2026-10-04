package com.yuzhi.dts.opmanager.web.rest;

import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().isEmpty()
            ? "request validation failed"
            : exception.getBindingResult().getFieldErrors().getFirst().getField() + " " + exception.getBindingResult().getFieldErrors().getFirst().getDefaultMessage();
        return ResponseEntity.badRequest().body(new ApiError(message));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(exception.getMessage()));
    }
}
