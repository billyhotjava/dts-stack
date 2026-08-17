package com.yuzhi.dts.analytics.service.analysis;

public class AnalysisConflictException extends RuntimeException {

    private final String errorCode;

    public AnalysisConflictException(String errorCode, String message) {
        super(errorCode + ": " + message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
