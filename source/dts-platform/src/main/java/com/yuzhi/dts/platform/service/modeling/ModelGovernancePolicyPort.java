package com.yuzhi.dts.platform.service.modeling;

/**
 * Read-only projection of the platform-global governance policy used by ModelSpec release gates.
 * Model release governance is intentionally independent from the retired warehouse-plan workflow.
 */
public interface ModelGovernancePolicyPort {

    long DEFAULT_QUALITY_EVIDENCE_MAX_AGE_SECONDS = 86_400L;

    Policy resolve();

    enum StandardCoverage {
        NONE,
        KEY_AND_MEASURE,
        ALL_FIELDS,
    }

    enum QualityGate {
        ADVISORY,
        BLOCKING,
    }

    record Policy(
        boolean available,
        StandardCoverage standardCoverage,
        QualityGate qualityGate,
        long qualityEvidenceMaxAgeSeconds,
        String reasonCode
    ) {
        public static Policy available(StandardCoverage standardCoverage, QualityGate qualityGate) {
            return available(standardCoverage, qualityGate, DEFAULT_QUALITY_EVIDENCE_MAX_AGE_SECONDS);
        }

        public static Policy available(
            StandardCoverage standardCoverage,
            QualityGate qualityGate,
            long qualityEvidenceMaxAgeSeconds
        ) {
            if (standardCoverage == null || qualityGate == null) {
                throw new IllegalArgumentException("Resolved governance policy values are required");
            }
            if (qualityEvidenceMaxAgeSeconds < 1) {
                throw new IllegalArgumentException("qualityEvidenceMaxAgeSeconds must be positive");
            }
            return new Policy(true, standardCoverage, qualityGate, qualityEvidenceMaxAgeSeconds, null);
        }

        public static Policy unavailable(String reasonCode) {
            return new Policy(false, null, null, DEFAULT_QUALITY_EVIDENCE_MAX_AGE_SECONDS, reasonCode);
        }
    }
}
