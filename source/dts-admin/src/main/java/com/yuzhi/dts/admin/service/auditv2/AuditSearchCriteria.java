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
    boolean export,
    /**
     * Filter by exact change_request_ref. Null/blank means "do not filter by ref value".
     * Used by auditors to follow a specific change-request chain (sysadmin submit → authadmin approve → execute).
     */
    String changeRequestRef,
    /**
     * Tri-state filter: {@code null} = any; {@code true} = only entries with a change-request ref
     * (i.e. ChangeRequest-mediated actions); {@code false} = only entries without one
     * (i.e. direct/un-approved actions — useful for spotting un-gated high-risk operations).
     */
    Boolean hasChangeRequest
) {
    public AuditSearchCriteria {
        allowedActors = sanitize(allowedActors);
        excludedActors = sanitize(excludedActors);
    }

    /**
     * Backward-compatible constructor: callers that don't filter by change-request fields
     * keep their existing 16-arg shape.
     */
    public AuditSearchCriteria(
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
        this(actor, module, operationKind, action, operationGroup, sourceSystem, result,
            targetTable, targetId, clientIp, keyword, from, to, allowedActors, excludedActors, export,
            null, null);
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
