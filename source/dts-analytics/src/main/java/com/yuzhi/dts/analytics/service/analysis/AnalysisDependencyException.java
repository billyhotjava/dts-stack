package com.yuzhi.dts.analytics.service.analysis;

public class AnalysisDependencyException extends RuntimeException {

    private final String errorCode;

    public AnalysisDependencyException(String errorCode, String message, Throwable cause) {
        super(errorCode + ": " + message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
