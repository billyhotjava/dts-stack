package com.yuzhi.dts.analytics.service.analysis;

public class AnalysisQueryCancelledException extends RuntimeException {

    public AnalysisQueryCancelledException() {
        super("ANALYSIS_QUERY_CANCELLED: query was cancelled by its owner");
    }
}
