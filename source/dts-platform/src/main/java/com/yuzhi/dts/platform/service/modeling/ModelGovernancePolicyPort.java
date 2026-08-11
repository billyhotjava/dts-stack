package com.yuzhi.dts.platform.service.modeling;

/**
 * Read-only projection of the platform-global governance policy used by ModelSpec release gates.
 * Model release governance is intentionally independent from the retired warehouse-plan workflow.
 */
public interface ModelGovernancePolicyPort {

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
        String reasonCode
    ) {
        public static Policy available(StandardCoverage standardCoverage, QualityGate qualityGate) {
            if (standardCoverage == null || qualityGate == null) {
                throw new IllegalArgumentException("Resolved governance policy values are required");
            }
            return new Policy(true, standardCoverage, qualityGate, null);
        }

        public static Policy unavailable(String reasonCode) {
            return new Policy(false, null, null, reasonCode);
        }
    }
}
