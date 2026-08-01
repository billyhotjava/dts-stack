package com.yuzhi.dts.platform.service.rollback;

import org.springframework.http.HttpStatus;

public class RollbackInvalidationException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public RollbackInvalidationException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public static RollbackInvalidationException conflict(String message) {
        return new RollbackInvalidationException("ROLLBACK_INVALIDATION_IDEMPOTENCY_CONFLICT", HttpStatus.CONFLICT, message);
    }

    public static RollbackInvalidationException stale(String message) {
        return new RollbackInvalidationException("ROLLBACK_INVALIDATION_STALE_EVENT", HttpStatus.CONFLICT, message);
    }

    public static RollbackInvalidationException unresolvedTarget(String message) {
        return new RollbackInvalidationException("ROLLBACK_INVALIDATION_TARGET_NOT_FOUND", HttpStatus.CONFLICT, message);
    }
}
