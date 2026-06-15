package com.yuzhi.dts.platform.service.goldenchain.consumption;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.List;
import java.util.Objects;

public record GoldenChainCustomerAcceptancePackage(
    boolean ready,
    String scenarioName,
    String customerNarrative,
    List<GoldenChainCustomerScenarioStep> steps,
    List<GoldenChainBusinessField> businessMetrics,
    List<String> evidenceChecklist,
    List<String> blockers,
    GoldenChainStageSnapshot stageSnapshot
) {
    public GoldenChainCustomerAcceptancePackage {
        scenarioName = normalize(scenarioName);
        customerNarrative = normalize(customerNarrative);
        steps = steps == null ? List.of() : List.copyOf(steps);
        businessMetrics = businessMetrics == null ? List.of() : List.copyOf(businessMetrics);
        evidenceChecklist = evidenceChecklist == null ? List.of() : List.copyOf(evidenceChecklist);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        stageSnapshot = Objects.requireNonNull(stageSnapshot, "stageSnapshot must not be null");
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
