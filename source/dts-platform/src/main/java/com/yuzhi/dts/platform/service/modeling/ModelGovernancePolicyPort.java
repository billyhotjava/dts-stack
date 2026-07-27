package com.yuzhi.dts.platform.service.modeling;

import java.util.UUID;

/**
 * Read-only projection of the warehouse-plan governance policy used by ModelSpec release gates.
 * The warehouse plan remains the policy owner.
 */
public interface ModelGovernancePolicyPort {

    Policy resolve(String tenantId, UUID planId);

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
