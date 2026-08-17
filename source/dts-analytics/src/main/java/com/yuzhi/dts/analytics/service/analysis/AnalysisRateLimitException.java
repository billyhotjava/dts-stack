package com.yuzhi.dts.analytics.service.analysis;

public class AnalysisRateLimitException extends RuntimeException {

    private final String scope;
    private final int retryAfterSeconds;

    public AnalysisRateLimitException(String scope, int retryAfterSeconds) {
        super("ANALYSIS_QUERY_LIMIT_EXCEEDED: " + scope + " concurrency budget is exhausted");
        this.scope = scope;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getScope() {
        return scope;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
