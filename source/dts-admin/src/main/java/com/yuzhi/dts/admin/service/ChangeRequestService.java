package com.yuzhi.dts.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.ChangeRequest;
import com.yuzhi.dts.admin.repository.ChangeRequestRepository;
import com.yuzhi.dts.admin.security.SecurityUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChangeRequestService {

    private final ChangeRequestRepository repository;
    private final ObjectMapper objectMapper;

    public ChangeRequestService(ChangeRequestRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public ChangeRequest draft(
        String resourceType,
        String action,
        String resourceId,
        Object afterPayload,
        Object beforePayload,
        String reason
    ) {
        Map<String, Object> after = normalize(afterPayload);
        Map<String, Object> before = normalize(beforePayload);

        ChangeRequest cr = new ChangeRequest();
        cr.setResourceType(Optional.ofNullable(resourceType).map(String::trim).map(String::toUpperCase).orElse("UNKNOWN"));
        cr.setAction(Optional.ofNullable(action).map(String::trim).map(String::toUpperCase).orElse("UNKNOWN"));
        cr.setResourceId(resourceId);
        cr.setPayloadJson(write(after));
        cr.setDiffJson(write(buildDiff(before, after)));
        cr.setStatus("PENDING");
        cr.setRequestedBy(SecurityUtils.getCurrentUserLogin().orElse("sysadmin"));
        cr.setRequestedAt(Instant.now());
        cr.setReason(reason);
        cr.setCategory(resolveCategory(cr.getResourceType()));
        cr.setLastError(null);
        return repository.save(cr);
    }

    private String resolveCategory(String resourceType) {
        if (resourceType == null) {
            return "GENERAL";
        }
        return switch (resourceType.toUpperCase()) {
            case "USER" -> "USER_MANAGEMENT";
            case "ROLE" -> "ROLE_MANAGEMENT";
            case "PORTAL_MENU" -> "MENU_MANAGEMENT";
            case "CONFIG" -> "SYSTEM_CONFIG";
            case "ORG" -> "ORGANIZATION";
            case "CUSTOM_ROLE" -> "CUSTOM_ROLE";
            case "ROLE_ASSIGNMENT" -> "ROLE_ASSIGNMENT";
            default -> "GENERAL";
        };
    }

    private Map<String, Object> buildDiff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("before", before);
        diff.put("after", after);
        diff.put("changes", computeChanges(before, after));
        return diff;
    }

    private List<Map<String, Object>> computeChanges(Map<String, Object> before, Map<String, Object> after) {
        if ((before == null || before.isEmpty()) && (after == null || after.isEmpty())) {
            return List.of();
        }
        Map<String, Object> lhs = before == null ? Map.of() : before;
        Map<String, Object> rhs = after == null ? Map.of() : after;
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(lhs.keySet());
        keys.addAll(rhs.keySet());
        List<Map<String, Object>> changes = new ArrayList<>();
        for (String key : keys) {
            Object b = lhs.get(key);
            Object a = rhs.get(key);
            if (!Objects.equals(normalizeValue(b), normalizeValue(a))) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("field", key);
                item.put("before", b);
                item.put("after", a);
                changes.add(item);
            }
        }
        return changes;
    }

    private Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::normalizeValue).toList();
        }
        return value;
    }

    private Map<String, Object> normalize(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (payload instanceof ChangeRequest cr) {
            return Map.of("id", cr.getId());
        }
        Map<String, Object> converted = objectMapper.convertValue(payload, LinkedHashMap.class);
        return converted == null ? null : normalizeMap(converted);
    }

    private Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry == null) {
                continue;
            }
            Object key = entry.getKey();
            if (key == null) {
                continue;
            }
            String normalizedKey = String.valueOf(key);
            Object value = entry.getValue();
            Object normalizedValue = normalizeValue(value);
            result.put(normalizedKey, normalizedValue);
        }
        return result;
    }

    private String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化变更内容", e);
        }
    }
}
