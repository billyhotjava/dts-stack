package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public record IndicatorDerivationValidationResult(
    boolean valid,
    String compiledExpression,
    List<Issue> issues,
    List<String> dependencyCodes
) {
    public IndicatorDerivationValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        dependencyCodes = dependencyCodes == null ? List.of() : List.copyOf(dependencyCodes);
    }

    @JsonIgnore
    public List<String> issueCodes() {
        return issues.stream().map(Issue::code).toList();
    }

    public record Issue(String code, String message) {}
}
