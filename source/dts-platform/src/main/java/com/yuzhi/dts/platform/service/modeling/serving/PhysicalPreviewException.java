package com.yuzhi.dts.platform.service.modeling.serving;

import org.springframework.http.HttpStatus;

/** Stable, non-sensitive physical preview failure. */
public class PhysicalPreviewException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;
    private final String correlationId;

    public PhysicalPreviewException(String errorCode, HttpStatus status, String correlationId) {
        super(errorCode);
        this.errorCode = errorCode;
        this.status = status;
        this.correlationId = correlationId;
    }

    public String errorCode() {
        return errorCode;
    }

    public HttpStatus status() {
        return status;
    }

    public String correlationId() {
        return correlationId;
    }
}
