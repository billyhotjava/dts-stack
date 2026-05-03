package com.yuzhi.dts.platform.service.policy;

import java.util.Map;

public record PolicyDecisionContext(
    String actor,
    String domain,
    String action,
    String resourceType,
    String resourceId,
    String dataClassification,
    Map<String, Object> attributes
) {}
