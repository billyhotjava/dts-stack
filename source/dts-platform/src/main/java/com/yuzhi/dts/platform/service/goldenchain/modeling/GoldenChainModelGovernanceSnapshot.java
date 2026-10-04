package com.yuzhi.dts.platform.service.goldenchain.modeling;

public record GoldenChainModelGovernanceSnapshot(
    String evidenceRef,
    boolean schemaContractPassed,
    boolean qualityPassed,
    boolean lineageReady,
    boolean classificationReady
) {
    public GoldenChainModelGovernanceSnapshot {
        evidenceRef = normalize(evidenceRef);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
