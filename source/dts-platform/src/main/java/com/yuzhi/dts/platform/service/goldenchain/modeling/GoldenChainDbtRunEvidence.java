package com.yuzhi.dts.platform.service.goldenchain.modeling;

public record GoldenChainDbtRunEvidence(
    String compileEvidenceRef,
    boolean compilePassed,
    String testEvidenceRef,
    boolean testPassed,
    String buildEvidenceRef,
    boolean buildPassed
) {
    public GoldenChainDbtRunEvidence {
        compileEvidenceRef = normalize(compileEvidenceRef);
        testEvidenceRef = normalize(testEvidenceRef);
        buildEvidenceRef = normalize(buildEvidenceRef);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
