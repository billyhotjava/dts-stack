package com.yuzhi.dts.analytics.service.analysis;

public class AnalysisForbiddenException extends RuntimeException {
    public AnalysisForbiddenException(String message) {
        super("ANALYSIS_FORBIDDEN: " + message);
    }
}
