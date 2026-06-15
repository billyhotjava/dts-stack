package com.yuzhi.dts.platform.service.goldenchain.consumption;

import java.util.Objects;

public record GoldenChainCustomerScenarioStep(
    int sequence,
    String title,
    String businessOutcome,
    GoldenChainAcceptanceEvidenceType evidenceType,
    String evidenceRef
) {
    public GoldenChainCustomerScenarioStep {
        if (sequence <= 0) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        title = requireText(title, "步骤标题不能为空");
        businessOutcome = requireText(businessOutcome, "业务结果不能为空");
        evidenceType = Objects.requireNonNull(evidenceType, "evidenceType must not be null");
        evidenceRef = normalize(evidenceRef);
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
