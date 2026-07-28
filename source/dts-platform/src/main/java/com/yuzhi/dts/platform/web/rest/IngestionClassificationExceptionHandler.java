package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.catalog.CatalogClassificationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = IngestionTaskProxyResource.class)
public class IngestionClassificationExceptionHandler {

    @ExceptionHandler(CatalogClassificationException.class)
    public ResponseEntity<ApiResponse<Object>> handleClassificationError(
        CatalogClassificationException exception
    ) {
        HttpStatus status = switch (exception.getCode()) {
            case "CLASSIFICATION_DOWNGRADE_FORBIDDEN",
                "CLASSIFICATION_SEAL_STALE",
                "FILE_CLASSIFICATION_SEAL_STALE" -> HttpStatus.CONFLICT;
            case "CLASSIFICATION_NOT_FOUND",
                "CLASSIFICATION_TARGET_NOT_FOUND",
                "FILE_CLASSIFICATION_SEAL_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity
            .status(status)
            .body(
                new ApiResponse<>(
                    status.value(),
                    exception.getMessage(),
                    exception.getCode(),
                    null
                )
            );
    }
}
