package com.yuzhi.dts.admin.service.auditv2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.audit.AuditEntry;
import com.yuzhi.dts.common.audit.ChangeSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuditV2Service {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AuditRecorder recorder;
    private final AuditButtonRegistry buttonRegistry;
    private final ChangeSnapshotFormatter changeSnapshotFormatter;
    private final ObjectMapper objectMapper;

    public AuditV2Service(
        AuditRecorder recorder,
        AuditButtonRegistry buttonRegistry,
        ChangeSnapshotFormatter changeSnapshotFormatter,
        ObjectMapper objectMapper
    ) {
        this.recorder = recorder;
        this.buttonRegistry = buttonRegistry;
        this.changeSnapshotFormatter = changeSnapshotFormatter;
        this.objectMapper = objectMapper;
    }

    public AuditEntry record(AuditActionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if ("system".equalsIgnoreCase(request.actorId()) && !request.allowSystemActor()) {
            return null;
        }
        AuditButtonMetadata metadata = buttonRegistry.resolve(request.buttonCode()).orElse(null);

        String moduleKey = firstNonBlank(request.moduleKeyOverride(), metadata != null ? metadata.moduleKey() : null);
        if (!StringUtils.hasText(moduleKey)) {
            throw new IllegalStateException("Missing moduleKey for button " + request.buttonCode());
        }
        AuditOperationKind operationKind = request.operationKindOverride() != null
            ? request.operationKindOverride()
            : metadata != null ? metadata.operationKind() : AuditOperationKind.OTHER;
        boolean allowEmptyTargets = request.allowEmptyTargets() || (metadata != null && metadata.allowEmptyTargets());

        AuditRecorder.AuditBuilder builder = recorder
            .start(request.actorId())
            .sourceSystem("admin")
            .module(moduleKey)
            .actorName(request.actorName())
            .actorRoles(request.actorRoles())
            .result(Optional.ofNullable(request.result()).orElse(AuditResultStatus.SUCCESS))
            .changeRequestRef(request.changeRequestRef())
            .buttonCode(request.buttonCode());

        if (allowEmptyTargets) {
            builder.allowEmptyTargets();
        }

        if (request.occurredAt() != null) {
            builder.occurredAt(request.occurredAt());
        }
        String moduleName = firstNonBlank(request.moduleNameOverride(), metadata != null ? metadata.moduleName() : null);
        if (StringUtils.hasText(moduleName)) {
            builder.moduleName(moduleName);
        }
        String operationCode = firstNonBlank(request.operationCodeOverride(), metadata != null ? metadata.operationCode() : null);
        String operationName = firstNonBlank(request.operationNameOverride(), metadata != null ? metadata.operationName() : null);
        builder.operation(operationCode, operationName, operationKind);

        String summary = firstNonBlank(request.summary(), metadata != null ? metadata.operationName() : null);
        if (StringUtils.hasText(summary)) {
            builder.summary(summary);
        }

        builder.client(request.clientIp(), request.clientAgent()).request(request.requestUri(), request.httpMethod());

        request.metadata().forEach(builder::metadata);
        request.attributes().forEach(builder::extraAttribute);

        for (AuditActionRequest.AuditTarget target : request.targets()) {
            builder.target(target.table(), target.id(), target.label());
        }
        if (!allowEmptyTargets) {
            // builder currently enforces emptiness unless allowEmptyTargets
        }

        List<AuditActionRequest.AuditDetail> normalizedDetails = normalizeDetails(request);
        for (AuditActionRequest.AuditDetail detail : normalizedDetails) {
            builder.detail(detail.key(), detail.value());
        }

        if (!allowEmptyTargets) {
            // Ensure at least one target for non-query operations
            if (operationKind != AuditOperationKind.QUERY && request.targets().isEmpty()) {
                throw new IllegalStateException("Targets required for button " + request.buttonCode());
            }
        }

        return builder.emit();
    }

    private List<AuditActionRequest.AuditDetail> normalizeDetails(AuditActionRequest request) {
        List<AuditActionRequest.AuditDetail> normalized = new ArrayList<>();
        if (request == null) {
            return normalized;
        }
        List<AuditActionRequest.AuditDetail> original = request.details() == null ? List.of() : request.details();
        ChangeSnapshot snapshot = request.changeSnapshot();
        Map<String, Object> before = null;
        Map<String, Object> after = null;
        Object summaryValue = null;

        for (AuditActionRequest.AuditDetail detail : original) {
            if (detail == null || !StringUtils.hasText(detail.key())) {
                continue;
            }
            String key = detail.key();
            Object value = detail.value();
            if ("changeSnapshot".equals(key)) {
                snapshot = preferSnapshot(snapshot, value);
                continue;
            }
            if ("changeSummary".equals(key)) {
                summaryValue = value;
                continue;
            }
            if ("before".equals(key)) {
                Map<String, Object> map = toStringKeyMap(value);
                if (!map.isEmpty()) {
                    before = map;
                    value = map;
                }
            } else if ("after".equals(key)) {
                Map<String, Object> map = toStringKeyMap(value);
                if (!map.isEmpty()) {
                    after = map;
                    value = map;
                }
            }
            normalized.add(new AuditActionRequest.AuditDetail(key, value));
        }

        if (snapshot == null && (before != null || after != null)) {
            snapshot = ChangeSnapshot.of(before != null ? before : Map.of(), after != null ? after : Map.of());
        } else if (snapshot != null && !snapshotHasContent(snapshot) && (before != null || after != null)) {
            ChangeSnapshot candidate = ChangeSnapshot.of(
                before != null ? before : snapshot.getBefore(),
                after != null ? after : snapshot.getAfter()
            );
            if (snapshotHasContent(candidate)) {
                snapshot = candidate;
            }
        }

        String resourceType = resolveResourceType(request);
        if (snapshot != null && snapshotHasContent(snapshot)) {
            Map<String, Object> snapshotMap = snapshot.toMap();
            normalized.add(new AuditActionRequest.AuditDetail("changeSnapshot", snapshotMap));
            List<Map<String, String>> summaryList = changeSnapshotFormatter != null
                ? changeSnapshotFormatter.format(snapshot, resourceType)
                : List.of();
            if (!summaryList.isEmpty() && (summaryValue == null || isSummaryEmpty(summaryValue))) {
                summaryValue = summaryList;
            }
        }

        if (summaryValue != null && !isSummaryEmpty(summaryValue)) {
            normalized.add(new AuditActionRequest.AuditDetail("changeSummary", summaryValue));
        }

        return normalized;
    }

    private ChangeSnapshot preferSnapshot(ChangeSnapshot current, Object candidate) {
        ChangeSnapshot extracted = extractSnapshot(candidate);
        if (extracted == null) {
            return current;
        }
        if (current == null) {
            return extracted;
        }
        if (!snapshotHasContent(current) && snapshotHasContent(extracted)) {
            return extracted;
        }
        return current;
    }

    private ChangeSnapshot extractSnapshot(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ChangeSnapshot snapshot) {
            return snapshot;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = toStringKeyMap(map);
            if (!normalized.isEmpty()) {
                return ChangeSnapshot.fromMap(normalized);
            }
            return null;
        }
        if (value instanceof CharSequence text) {
            String json = text.toString().trim();
            if (json.isEmpty()) {
                return null;
            }
            if (objectMapper != null) {
                try {
                    Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE);
                    return ChangeSnapshot.fromMap(parsed);
                } catch (Exception ignored) {}
            }
            return null;
        }
        return null;
    }

    private boolean snapshotHasContent(ChangeSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        return snapshot.hasChanges() || !snapshot.getBefore().isEmpty() || !snapshot.getAfter().isEmpty();
    }

    private Map<String, Object> toStringKeyMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof ChangeSnapshot snapshot) {
            return snapshot.toMap();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    result.put(String.valueOf(k), v);
                }
            });
            return result;
        }
        if (value instanceof CharSequence text) {
            return readJsonMap(text.toString());
        }
        return Map.of();
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank() || objectMapper == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private boolean isSummaryEmpty(Object summary) {
        if (summary == null) {
            return true;
        }
        if (summary instanceof CharSequence text) {
            return text.toString().trim().isEmpty();
        }
        if (summary instanceof List<?> list) {
            if (list.isEmpty()) {
                return true;
            }
            return list.stream().allMatch(this::isSummaryEmpty);
        }
        if (summary instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private String resolveResourceType(AuditActionRequest request) {
        if (request == null) {
            return null;
        }
        String candidate = firstNonBlank(
            request.changeResourceType(),
            asString(request.metadata().get("resourceType")),
            asString(request.metadata().get("resource_type")),
            asString(request.attributes().get("resourceType")),
            asString(request.attributes().get("resource_type"))
        );
        if (!StringUtils.hasText(candidate)) {
            candidate = deriveResourceTypeFromButtonCode(request.buttonCode());
        }
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        return candidate.trim().toUpperCase(Locale.ROOT);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence text) {
            String trimmed = text.toString().trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        return value.toString();
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private String deriveResourceTypeFromButtonCode(String buttonCode) {
        if (!StringUtils.hasText(buttonCode)) {
            return null;
        }
        String normalized = buttonCode.trim().toUpperCase(Locale.ROOT);
        String[] parts = normalized.split("_");
        if (parts.length < 2) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (part == null || part.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('_');
            }
            sb.append(part);
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
