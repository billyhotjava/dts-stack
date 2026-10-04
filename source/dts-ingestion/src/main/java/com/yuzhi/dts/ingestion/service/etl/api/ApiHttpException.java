package com.yuzhi.dts.ingestion.service.etl.api;

public class ApiHttpException extends RuntimeException {

    private final String code;
    private final Integer statusCode;
    private final int attempts;

    public ApiHttpException(String code, String message, Integer statusCode, int attempts) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
        this.attempts = attempts;
    }

    public ApiHttpException(String code, String message, Integer statusCode, int attempts, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.statusCode = statusCode;
        this.attempts = attempts;
    }

    public String getCode() {
        return code;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public int getAttempts() {
        return attempts;
    }
}
