package com.yuzhi.dts.analytics.service.analysis;

public class AnalysisSpecValidationException extends RuntimeException {

    private final String errorCode;
    private final String field;

    public AnalysisSpecValidationException(String errorCode, String field, String message) {
        super(errorCode + ": " + message);
        this.errorCode = errorCode;
        this.field = field;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getField() {
        return field;
    }
}
