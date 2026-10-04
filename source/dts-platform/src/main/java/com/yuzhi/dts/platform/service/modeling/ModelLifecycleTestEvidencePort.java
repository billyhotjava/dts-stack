package com.yuzhi.dts.platform.service.modeling;

import java.util.UUID;

/** Resolves a persisted dbt test run instead of trusting a client supplied PASS flag. */
public interface ModelLifecycleTestEvidencePort {
    TestEvidence verify(VerificationRequest request);

    record TestEvidence(String externalRunId, String status, String message) {}

    record VerificationRequest(
        String externalRunId,
        String tenantId,
        UUID modelSpecId,
        int implementationRevision,
        String implementationChecksum,
        String projectKey,
        String dbtUniqueId
    ) {}
}
