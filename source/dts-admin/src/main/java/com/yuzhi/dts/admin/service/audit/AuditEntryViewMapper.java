package com.yuzhi.dts.admin.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Translates {@link AuditEntryView} domain projections into the wire-format Map
 * shape expected by the audit log UI. Hosting this in a dedicated component
 * keeps {@code AuditEntryResource} focused on routing and authorisation.
 */
@Component
public class AuditEntryViewMapper {

    private static final Set<String> DETAIL_KEYS_TO_HIDE = Set.of("attributes", "actionDisplay", "target");

    private final AuditResourceDictionaryService resourceDictionary;
    private final ObjectMapper objectMapper;

    public AuditEntryViewMapper(AuditResourceDictionaryService resourceDictionary, ObjectMapper objectMapper) {
        this.resourceDictionary = resourceDictionary;
        // Defensive copy: never mutate the upstream ObjectMapper used elsewhere.
        this.objectMapper = objectMapper.copy().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    public Map<String, Object> toResponse(AuditEntryView view, boolean includeDetails) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", view.id());
        map.put("occurredAt", view.occurredAt() != null ? view.occurredAt().toString() : null);
        map.put("sourceSystem", view.sourceSystem());
        map.put("sourceSystemText", mapSourceSystemText(view.sourceSystem()));
        String moduleKey = StringUtils.defaultIfBlank(view.moduleKey(), "general");
        String moduleLabel = StringUtils.isNotBlank(view.moduleName())
            ? view.moduleName()
            : resourceDictionary.resolveLabel(moduleKey).orElse(moduleKey);
        map.put("module", moduleLabel);
        map.put("moduleKey", moduleKey);
        map.put("buttonCode", view.buttonCode());
        map.put("action", StringUtils.defaultIfBlank(view.operationName(), view.operationCode()));
        map.put("operationCode", view.operationCode());
        AuditOperationKind kind = view.operationKind();
        String normalizedCode = normalizeOperationTypeCode(view);
        map.put("operationTypeCode", normalizedCode);
        map.put("operationType", mapOperationTypeLabel(normalizedCode));
        map.put("operationTypeRaw", kind != null ? kind.displayName() : null);
        map.put("operationContent", StringUtils.defaultIfBlank(view.summary(), view.operationName()));
        map.put("summary", view.summary());
        map.put("operationGroup", view.operationGroup());
        map.put("result", view.result());
        map.put("resultText", view.resultLabel());
        map.put("logTypeText", mapLogType(view.sourceSystem()));
        map.put("eventClass", "AUDIT_ENTRY");
        map.put("eventType", view.operationKind() != null ? view.operationKind().code() : "OTHER");

        map.put("actor", view.actorId());
        map.put("actorName", view.actorName());
        map.put("actorRoles", view.actorRoles());
        map.put("actorRole", view.actorRoles().isEmpty() ? null : view.actorRoles().get(0));
        map.put("operatorId", view.actorId());
        map.put("operatorName", view.actorName());
        map.put("operatorRoles", toJson(view.actorRoles()));
        map.put("orgCode", null);
        map.put("orgName", null);
        map.put("departmentName", null);

        map.put("clientIp", view.clientIp());
        map.put("clientAgent", view.clientAgent());
        map.put("requestUri", view.requestUri());
        map.put("httpMethod", view.httpMethod());

        map.put("changeRequestRef", view.changeRequestRef());

        String sourceTable = StringUtils.firstNonBlank(
            extractFromMapLike(view.metadata(), "sourceTable"),
            extractFromMapLike(view.details(), "sourceTable"),
            extractFromMapLike(view.extraAttributes(), "sourceTable")
        );
        String sourcePrimaryKey = StringUtils.firstNonBlank(
            extractFromMapLike(view.metadata(), "sourcePrimaryKey"),
            extractFromMapLike(view.details(), "sourcePrimaryKey"),
            extractFromMapLike(view.extraAttributes(), "sourcePrimaryKey")
        );
        if (StringUtils.isNotBlank(sourceTable)) {
            map.put("sourceTable", sourceTable);
        }
        if (StringUtils.isNotBlank(sourcePrimaryKey)) {
            map.put("sourcePrimaryKey", sourcePrimaryKey);
        }

        List<String> targetIds = new ArrayList<>();
        Map<String, String> targetLabels = new LinkedHashMap<>();
        String targetTable = null;
        for (AuditEntryTargetView target : view.targets()) {
            if (target == null) {
                continue;
            }
            String table = safeTrim(target.table());
            String id = safeTrim(target.id());
            String label = safeTrim(target.label());
            if (targetTable == null && StringUtils.isNotBlank(table)) {
                targetTable = table;
            }
            if (StringUtils.isNotBlank(id)) {
                targetIds.add(id);
                targetLabels.put(id, StringUtils.defaultIfBlank(label, id));
            }
        }
        String canonicalResourceType = StringUtils.defaultIfBlank(view.resourceType(), null);
        map.put("resourceType", StringUtils.defaultIfBlank(canonicalResourceType, targetTable));
        map.put("resourceId", targetIds.isEmpty() ? null : targetIds.get(0));
        map.put("targetTable", targetTable);
        map.put("targetId", targetIds.isEmpty() ? null : targetIds.get(0));
        map.put("targetIds", targetIds);
        map.put("targetLabels", targetLabels.isEmpty() ? Map.of() : targetLabels);
        if (StringUtils.isNotBlank(targetTable)) {
            map.put("targetTableLabel", resourceDictionary.resolveLabel(targetTable).orElse(targetTable));
        }

        map.put("metadata", view.metadata());
        map.put("extraAttributes", view.extraAttributes());

        Map<String, Object> detailPayload;
        if (includeDetails) {
            detailPayload = new LinkedHashMap<>();
            if (view.details() != null) {
                view
                    .details()
                    .forEach((key, value) -> {
                        if (!DETAIL_KEYS_TO_HIDE.contains(key)) {
                            detailPayload.put(key, value);
                        }
                    });
            }
            if (!view.metadata().isEmpty()) {
                detailPayload.putIfAbsent("metadata", view.metadata());
            }
            if (StringUtils.isNotBlank(targetTable)) {
                detailPayload.putIfAbsent("targetTable", targetTable);
                detailPayload.putIfAbsent(
                    "targetTableLabel",
                    resourceDictionary.resolveLabel(targetTable).orElse(targetTable)
                );
            }
            if (!targetIds.isEmpty()) {
                detailPayload.putIfAbsent("targetIds", targetIds);
            }
            if (StringUtils.isNotBlank(canonicalResourceType)) {
                detailPayload.putIfAbsent("resourceType", canonicalResourceType);
            }
        } else {
            detailPayload = Map.of();
        }
        map.put("details", detailPayload);
        map.put("payload", detailPayload);

        Object requestId = includeDetails ? detailPayload.getOrDefault("requestId", detailPayload.get("request_id")) : null;
        if (requestId != null) {
            map.put("requestId", requestId);
        }
        Object approvalSummary = includeDetails ? detailPayload.get("approvalSummary") : null;
        if (approvalSummary != null) {
            map.put("approvalSummary", approvalSummary);
        }

        return map;
    }

    public String mapSourceSystemText(String sourceSystem) {
        if (StringUtils.isBlank(sourceSystem)) {
            return "系统管理";
        }
        return switch (sourceSystem.trim().toLowerCase(Locale.ROOT)) {
            case "platform" -> "业务管理";
            case "analytics" -> "BI分析";
            default -> "系统管理";
        };
    }

    public String mapLogType(String sourceSystem) {
        return switch (StringUtils.trimToEmpty(sourceSystem).toLowerCase(Locale.ROOT)) {
            case "platform" -> "业务端审计";
            case "analytics" -> "分析端审计";
            default -> "管理端审计";
        };
    }

    public String normalizeOperationTypeCode(AuditEntryView view) {
        AuditOperationKind kind = view.operationKind();
        if (kind != null && kind != AuditOperationKind.OTHER) {
            return kind.code();
        }
        String candidate = StringUtils.firstNonBlank(
            extractOperationToken(view),
            view.operationCode(),
            view.operationName(),
            view.summary()
        );
        AuditOperationType type = AuditOperationType.from(candidate);
        if (type == AuditOperationType.UNKNOWN) {
            type = inferOperationType(view, candidate);
        }
        if (type == AuditOperationType.UNKNOWN) {
            type = AuditOperationType.READ;
        }
        return type.getCode();
    }

    public String mapOperationTypeLabel(String normalizedCode) {
        AuditOperationType type = AuditOperationType.from(normalizedCode);
        if (type == AuditOperationType.UNKNOWN) {
            type = AuditOperationType.READ;
        }
        return type.getDisplayName();
    }

    private String extractOperationToken(AuditEntryView view) {
        if (view == null) {
            return null;
        }
        String direct = StringUtils.firstNonBlank(
            extractFromMapLike(view.extraAttributes(), "operationType", "operation_type"),
            extractFromMapLike(view.metadata(), "operationType", "operation_type"),
            extractFromMapLike(view.details(), "operationType", "operation_type")
        );
        if (StringUtils.isNotBlank(direct)) {
            return direct;
        }
        Object payload = view.details() != null ? view.details().get("payload") : null;
        if (payload instanceof Map<?, ?> map) {
            return extractFromMapLike(map, "operationType", "operation_type");
        }
        return null;
    }

    private String extractFromMapLike(Object source, String... keys) {
        if (!(source instanceof Map<?, ?> map) || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            Object value = map.get(key);
            if (value == null) {
                value = map.get(key.toLowerCase(Locale.ROOT));
            }
            if (value == null) {
                value = map.get(key.toUpperCase(Locale.ROOT));
            }
            if (value != null) {
                String text = safeTrim(value.toString());
                if (StringUtils.isNotBlank(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private AuditOperationType inferOperationType(AuditEntryView view, String candidate) {
        List<String> probes = new ArrayList<>();
        if (StringUtils.isNotBlank(candidate)) {
            probes.add(candidate);
        }
        if (StringUtils.isNotBlank(view.operationName())) {
            probes.add(view.operationName());
        }
        if (StringUtils.isNotBlank(view.summary())) {
            probes.add(view.summary());
        }
        if (StringUtils.isNotBlank(view.operationCode())) {
            probes.add(view.operationCode());
        }
        Map<String, Object> details = view.details();
        if (details != null) {
            Object direct = details.get("operationType");
            if (direct instanceof String s && StringUtils.isNotBlank(s)) {
                probes.add(s);
            }
            Object payload = details.get("payload");
            if (payload instanceof Map<?, ?> payloadMap) {
                Object payloadOp = payloadMap.get("operationType");
                if (payloadOp instanceof String s && StringUtils.isNotBlank(s)) {
                    probes.add(s);
                }
                Object payloadSummary = payloadMap.get("summary");
                if (payloadSummary instanceof String s && StringUtils.isNotBlank(s)) {
                    probes.add(s);
                }
            }
        }
        for (String probe : probes) {
            String normalized = probe.toLowerCase(Locale.ROOT);
            if (containsAny(normalized, "下载", "download")) {
                return AuditOperationType.DOWNLOAD;
            }
            if (containsAny(normalized, "上传", "upload")) {
                return AuditOperationType.UPLOAD;
            }
            if (containsAny(normalized, "导出", "export")) {
                return AuditOperationType.EXPORT;
            }
            if (containsAny(normalized, "导入", "import")) {
                return AuditOperationType.IMPORT;
            }
        }
        return AuditOperationType.UNKNOWN;
    }

    private boolean containsAny(String text, String... tokens) {
        if (StringUtils.isBlank(text) || tokens == null || tokens.length == 0) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (token != null && normalized.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return value.toString();
        }
    }
}
