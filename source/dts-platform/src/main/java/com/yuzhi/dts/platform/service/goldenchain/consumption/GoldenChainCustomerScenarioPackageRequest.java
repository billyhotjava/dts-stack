package com.yuzhi.dts.platform.service.goldenchain.consumption;

import java.util.List;

public record GoldenChainCustomerScenarioPackageRequest(
    String scenarioName,
    String owner,
    List<String> sourceKinds,
    List<GoldenChainCustomerScenarioStep> steps,
    List<GoldenChainBusinessField> businessMetrics,
    GoldenChainReportDatasetPublication reportPublication,
    GoldenChainDataServiceSubscriptionDecision dataServiceDecision,
    GoldenChainConsumptionPermissionView permissionView
) {
    public GoldenChainCustomerScenarioPackageRequest {
        scenarioName = requireText(scenarioName, "客户场景名称不能为空");
        owner = requireText(owner, "负责人不能为空");
        sourceKinds = sourceKinds == null ? List.of() : sourceKinds.stream().map(GoldenChainCustomerScenarioPackageRequest::normalize).toList();
        steps = steps == null ? List.of() : List.copyOf(steps);
        businessMetrics = businessMetrics == null ? List.of() : List.copyOf(businessMetrics);
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
