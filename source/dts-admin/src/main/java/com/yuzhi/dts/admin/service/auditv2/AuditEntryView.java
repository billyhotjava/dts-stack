package com.yuzhi.dts.admin.service.auditv2;

import com.yuzhi.dts.admin.domain.audit.AuditEntry;
import com.yuzhi.dts.admin.domain.audit.AuditEntryDetail;
import com.yuzhi.dts.admin.domain.audit.AuditEntryTarget;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public record AuditEntryView(
    Long id,
    Instant occurredAt,
    String sourceSystem,
    String moduleKey,
    String moduleName,
    String buttonCode,
    String operationCode,
    String operationName,
    AuditOperationKind operationKind,
    String result,
    String summary,
    String actorId,
    String actorName,
    List<String> actorRoles,
    String changeRequestRef,
    String clientIp,
    String clientAgent,
    String requestUri,
    String httpMethod,
    Map<String, Object> metadata,
    Map<String, Object> extraAttributes,
    List<AuditEntryTargetView> targets,
    Map<String, Object> details
) {
    public static AuditEntryView from(AuditEntry entry, boolean includeDetails) {
        if (entry == null) {
            return null;
        }
        Map<String, Object> metadata = entry.getMetadata() == null ? Map.of() : Map.copyOf(entry.getMetadata());
        Map<String, Object> extraAttributes = entry.getExtraAttributes() == null
            ? Map.of()
            : Map.copyOf(entry.getExtraAttributes());
        List<String> roles = entry.getActorRoles() == null ? List.of() : List.copyOf(entry.getActorRoles());
        List<AuditEntryTargetView> targets = entry
            .getTargets()
            .stream()
            .sorted(java.util.Comparator.comparingInt(AuditEntryTarget::getPosition))
            .map(t ->
                new AuditEntryTargetView(
                    safeTrim(t.getTargetTable()),
                    safeTrim(t.getTargetId()),
                    safeTrim(t.getTargetLabel())
                )
            )
            .toList();
        Map<String, Object> details = includeDetails ? collectDetails(entry) : Map.of();
        return new AuditEntryView(
            entry.getId(),
            entry.getOccurredAt(),
            safeTrim(entry.getSourceSystem()),
            safeTrim(entry.getModuleKey()),
            safeTrim(entry.getModuleName()),
            safeTrim(entry.getButtonCode()),
            safeTrim(entry.getOperationCode()),
            safeTrim(entry.getOperationName()),
            resolveKind(entry.getOperationKind()),
            safeTrim(entry.getResult()),
            safeTrim(entry.getSummary()),
            safeTrim(entry.getActorId()),
            safeTrim(entry.getActorName()),
            roles,
            safeTrim(entry.getChangeRequestRef()),
            formatIp(entry.getClientIp()),
            safeTrim(entry.getClientAgent()),
            safeTrim(entry.getRequestUri()),
            safeTrim(entry.getHttpMethod()),
            metadata,
            extraAttributes,
            targets,
            details
        );
    }

    private static Map<String, Object> collectDetails(AuditEntry entry) {
        List<AuditEntryDetail> detailList = entry.getDetails();
        if (detailList == null || detailList.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        detailList
            .stream()
            .sorted(java.util.Comparator.comparingInt(AuditEntryDetail::getPosition))
            .forEach(detail -> {
                if (detail == null || detail.getDetailKey() == null) {
                    return;
                }
                out.put(detail.getDetailKey(), detail.getDetailValue());
            });
        return out;
    }

    private static String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String formatIp(InetAddress addr) {
        if (addr == null) {
            return null;
        }
        return addr.getHostAddress();
    }

    private static AuditOperationKind resolveKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return AuditOperationKind.OTHER;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (AuditOperationKind kind : AuditOperationKind.values()) {
            if (kind.code().equals(normalized)) {
                return kind;
            }
        }
        return AuditOperationKind.OTHER;
    }

    public Optional<AuditEntryTargetView> primaryTarget() {
        if (targets == null || targets.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(targets.get(0));
    }

    public String operationKindLabel() {
        return operationKind != null ? operationKind.displayName() : AuditOperationKind.OTHER.displayName();
    }

    public String resultLabel() {
        String normalized = result == null ? "" : result.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SUCCESS" -> AuditResultStatus.SUCCESS.displayName();
            case "FAILED", "FAIL", "FAILURE" -> AuditResultStatus.FAILED.displayName();
            case "PENDING" -> AuditResultStatus.PENDING.displayName();
            default -> result;
        };
    }

    public String operationGroup() {
        // 目前将模块 key 作为默认的分组标识，后续可根据 metadata 扩展
        if (moduleKey != null && !moduleKey.isBlank()) {
            return moduleKey.trim().toLowerCase(Locale.ROOT);
        }
        if (moduleName != null && !moduleName.isBlank()) {
            return moduleName.trim();
        }
        return "general";
    }
}
