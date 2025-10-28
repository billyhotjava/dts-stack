package com.yuzhi.dts.admin.service.auditv2;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record AuditSearchCriteria(
    String actor,
    String module,
    String operationKind,
    String action,
    String operationGroup,
    String sourceSystem,
    String result,
    String targetTable,
    String targetId,
    String clientIp,
    String keyword,
    Instant from,
    Instant to,
    Set<String> allowedActors,
    Set<String> excludedActors,
    boolean export
) {
    public AuditSearchCriteria {
        allowedActors = sanitize(allowedActors);
        excludedActors = sanitize(excludedActors);
    }

    private static Set<String> sanitize(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        values
            .stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toLowerCase)
            .forEach(sanitized::add);
        return Collections.unmodifiableSet(sanitized);
    }
}
