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
import java.util.Set;
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
    Map<String, Object> details,
    String resourceType
) {
    private static final Set<String> CANONICAL_RESOURCE_TYPES = Set.of(
        "USER",
        "ROLE",
        "CUSTOM_ROLE",
        "ROLE_ASSIGNMENT",
        "PORTAL_MENU",
        "MENU",
        "GROUP",
        "ORG",
        "INFRA_DATA_SOURCE",
        "SYSTEM_CONFIG",
        "CATALOG_DATASET",
        "CHANGE_REQUEST",
        "APPROVAL",
        "PERMISSION"
    );

    private static final Map<String, String> RESOURCE_TYPE_ALIASES = Map.ofEntries(
        Map.entry("ADMIN_USER", "USER"),
        Map.entry("ADMIN_USER_MANAGEMENT", "USER"),
        Map.entry("AUTH_USER", "USER"),
        Map.entry("ADMIN_ROLE", "ROLE"),
        Map.entry("ADMIN_ROLE_MANAGEMENT", "ROLE"),
        Map.entry("PLATFORM_ROLE", "ROLE"),
        Map.entry("ADMIN_CUSTOM_ROLE", "CUSTOM_ROLE"),
        Map.entry("ADMIN_ROLE_ASSIGNMENT", "ROLE_ASSIGNMENT"),
        Map.entry("ADMIN_PORTAL_MENU", "PORTAL_MENU"),
        Map.entry("ADMIN_MENU", "PORTAL_MENU"),
        Map.entry("MENU_MANAGEMENT", "PORTAL_MENU"),
        Map.entry("ADMIN_GROUP", "GROUP"),
        Map.entry("ADMIN_ORG", "ORG"),
        Map.entry("ADMIN_ORGANIZATION", "ORG"),
        Map.entry("ADMIN_DATA_SOURCE", "INFRA_DATA_SOURCE"),
        Map.entry("ADMIN_DATASET", "CATALOG_DATASET"),
        Map.entry("ADMIN_SYSTEM_CONFIG", "SYSTEM_CONFIG"),
        Map.entry("ADMIN_CHANGE_REQUEST", "CHANGE_REQUEST"),
        Map.entry("ADMIN_APPROVAL", "APPROVAL")
    );

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
        String primaryTargetTable = targets.isEmpty() ? null : targets.get(0).table();
        String resourceType = canonicalizeResourceType(
            firstNonBlank(
                extractResourceType(metadata),
                extractResourceType(extraAttributes),
                primaryTargetTable
            )
        );
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
            details,
            resourceType
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

    private static String extractResourceType(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (String key : List.of("resourceType", "resource_type", "resource-type", "resource")) {
            Object value = source.get(key);
            if (value instanceof CharSequence text) {
                String trimmed = safeTrim(text.toString());
                if (trimmed != null) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    private static String canonicalizeResourceType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (CANONICAL_RESOURCE_TYPES.contains(normalized)) {
            return normalized;
        }
        String alias = RESOURCE_TYPE_ALIASES.get(normalized);
        if (alias != null) {
            return alias;
        }
        if (normalized.contains("ROLE_ASSIGN")) {
            return "ROLE_ASSIGNMENT";
        }
        if (normalized.contains("CUSTOM_ROLE")) {
            return "CUSTOM_ROLE";
        }
        if (normalized.contains("PORTAL_MENU") || normalized.endsWith("_MENU") || normalized.contains("MENU_")) {
            return "PORTAL_MENU";
        }
        if (normalized.contains("MENU") && !normalized.contains("PERMISSION")) {
            return "PORTAL_MENU";
        }
        if (normalized.contains("ROLE")) {
            return "ROLE";
        }
        if (normalized.contains("USER")) {
            return "USER";
        }
        if (normalized.contains("GROUP")) {
            return "GROUP";
        }
        if (normalized.contains("ORGANIZATION") || normalized.contains("ORG")) {
            return "ORG";
        }
        if (normalized.contains("DATA_SOURCE") || normalized.contains("DATASOURCE")) {
            return "INFRA_DATA_SOURCE";
        }
        if (normalized.contains("SYSTEM_CONFIG") || normalized.contains("SYS_CONFIG")) {
            return "SYSTEM_CONFIG";
        }
        if (normalized.contains("DATASET")) {
            return "CATALOG_DATASET";
        }
        if (normalized.contains("CHANGE_REQUEST")) {
            return "CHANGE_REQUEST";
        }
        if (normalized.contains("APPROVAL")) {
            return "APPROVAL";
        }
        if (normalized.contains("PERMISSION")) {
            return "PERMISSION";
        }
        return normalized;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
