package com.yuzhi.dts.analytics.service.analysis;

public class AnalysisQueryTimeoutException extends RuntimeException {

    public AnalysisQueryTimeoutException(String message, Throwable cause) {
        super("ANALYSIS_QUERY_TIMEOUT: " + message, cause);
    }
}
