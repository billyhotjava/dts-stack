package com.yuzhi.dts.platform.service.policy;

import java.util.Map;

public record PolicyDecision(
    String decision,
    String policyRef,
    String reason,
    Map<String, Object> context
) {
    public static PolicyDecision allow(String reason) {
        return new PolicyDecision("ALLOW", null, reason, Map.of());
    }
}
