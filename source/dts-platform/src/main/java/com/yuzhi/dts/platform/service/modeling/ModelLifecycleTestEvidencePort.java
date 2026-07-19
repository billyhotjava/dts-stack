package com.yuzhi.dts.platform.service.modeling;

/** Resolves a persisted dbt test run instead of trusting a client supplied PASS flag. */
public interface ModelLifecycleTestEvidencePort {
    TestEvidence verify(String externalRunId);

    record TestEvidence(String externalRunId, String status, String message) {}
}
