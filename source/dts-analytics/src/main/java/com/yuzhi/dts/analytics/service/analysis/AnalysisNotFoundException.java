package com.yuzhi.dts.analytics.service.analysis;

public class AnalysisNotFoundException extends RuntimeException {
    public AnalysisNotFoundException(String message) {
        super("ANALYSIS_NOT_FOUND: " + message);
    }
}
