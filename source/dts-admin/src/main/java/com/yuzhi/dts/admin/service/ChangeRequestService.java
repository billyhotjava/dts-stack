package com.yuzhi.dts.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.ChangeRequest;
import com.yuzhi.dts.admin.repository.AdminApprovalItemRepository;
import com.yuzhi.dts.admin.repository.ChangeRequestRepository;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.common.audit.ChangeSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ChangeRequestService {

    private static final Set<String> DEDUP_TYPES = Set.of("USER", "ROLE", "PORTAL_MENU");
    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING");
    private static final Logger LOG = LoggerFactory.getLogger(ChangeRequestService.class);

    private final ChangeRequestRepository repository;
    private final ObjectMapper objectMapper;
    private final AdminApprovalItemRepository approvalItemRepository;

    public ChangeRequestService(
        ChangeRequestRepository repository,
        ObjectMapper objectMapper,
        AdminApprovalItemRepository approvalItemRepository
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.approvalItemRepository = approvalItemRepository;
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
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

        String normalizedType = Optional.ofNullable(resourceType).map(String::trim).map(String::toUpperCase).orElse("UNKNOWN");
        String normalizedAction = Optional.ofNullable(action).map(String::trim).map(String::toUpperCase).orElse("UNKNOWN");
        String normalizedResourceId = normalizeResourceId(resourceId);

        String payloadJson = write(after);
        String diffJson = write(buildDiff(normalizedType, before, after));

        enforceNoDuplicate(normalizedType, normalizedAction, normalizedResourceId, after, payloadJson);

        ChangeRequest cr = new ChangeRequest();
        cr.setResourceType(normalizedType);
        cr.setAction(normalizedAction);
        cr.setResourceId(normalizedResourceId);
        cr.setPayloadJson(payloadJson);
        cr.setDiffJson(diffJson);
        cr.setStatus("PENDING");
        cr.setRequestedBy(SecurityUtils.getCurrentUserLogin().orElse("sysadmin"));
        cr.setRequestedAt(Instant.now());
        cr.setReason(reason);
        cr.setCategory(resolveCategory(cr.getResourceType()));
        cr.setLastError(null);
        return repository.save(cr);
    }

    public String ensureNoDuplicate(String resourceType, String action, String resourceId, String payloadJson) {
        String normalizedType = Optional.ofNullable(resourceType).map(String::trim).map(String::toUpperCase).orElse("UNKNOWN");
        String normalizedAction = Optional.ofNullable(action).map(String::trim).map(String::toUpperCase).orElse("UNKNOWN");
        String normalizedId = normalizeResourceId(resourceId);
        Map<String, Object> afterPayload = normalize(read(payloadJson));
        String canonicalPayload = write(afterPayload);
        enforceNoDuplicate(normalizedType, normalizedAction, normalizedId, afterPayload, canonicalPayload);
        return canonicalPayload != null ? canonicalPayload : payloadJson;
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

    private Map<String, Object> buildDiff(String resourceType, Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> effectiveBefore = snapshotClone(before);
        Map<String, Object> effectiveAfter = snapshotClone(after);
        if (isPortalMenuResource(resourceType)) {
            effectiveBefore = ensureMenuContext(effectiveBefore, Map.of());
            effectiveAfter = ensureMenuContext(effectiveAfter, effectiveBefore);
            if (hasMenuRolePayload(effectiveBefore) && !hasMenuRolePayload(effectiveAfter)) {
                copyMenuRoles(effectiveAfter, effectiveBefore);
            }
        }
        ChangeSnapshot snapshot = ChangeSnapshot.of(effectiveBefore, effectiveAfter);
        return snapshot.toMap();
    }

    private Map<String, Object> snapshotClone(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(source);
    }

    private Map<String, Object> ensureMenuContext(Map<String, Object> candidate, Map<String, Object> fallback) {
        Map<String, Object> normalized = snapshotClone(candidate);
        copyMenuContext(normalized, fallback);
        return normalized;
    }

    private void copyMenuRoles(Map<String, Object> target, Map<String, Object> fallback) {
        if (fallback == null || fallback.isEmpty()) {
            return;
        }
        if (!target.containsKey("allowedRoles") && fallback.containsKey("allowedRoles")) {
            target.put("allowedRoles", fallback.get("allowedRoles"));
        }
        if (!target.containsKey("visibilityRules") && fallback.containsKey("visibilityRules")) {
            target.put("visibilityRules", fallback.get("visibilityRules"));
        }
    }

    private boolean hasMenuRolePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        return payload.containsKey("allowedRoles") || payload.containsKey("visibilityRules");
    }

    private boolean isPortalMenuResource(String resourceType) {
        if (resourceType == null) {
            return false;
        }
        String normalized = resourceType.trim().toUpperCase(Locale.ROOT);
        return "PORTAL_MENU".equals(normalized) || "MENU".equals(normalized) || "MENU_MANAGEMENT".equals(normalized);
    }

    private void copyMenuContext(Map<String, Object> target, Map<String, Object> fallback) {
        if (fallback == null) {
            fallback = Map.of();
        }
        copyIfAbsent(target, fallback, "id");
        copyIfAbsent(target, fallback, "name");
        copyIfAbsent(target, fallback, "path");
        copyIfAbsent(target, fallback, "displayName");
        copyIfAbsent(target, fallback, "title");
        copyIfAbsent(target, fallback, "menuTitle");
        copyIfAbsent(target, fallback, "menuName");
        copyIfAbsent(target, fallback, "menuLabel");
        copyIfAbsent(target, fallback, "metadata");
    }

    private void copyIfAbsent(Map<String, Object> target, Map<String, Object> fallback, String key) {
        if (target.containsKey(key)) {
            Object current = target.get(key);
            if (current instanceof String s && StringUtils.hasText(s)) {
                return;
            }
            if (current != null) {
                return;
            }
        }
        if (fallback.containsKey(key)) {
            target.put(key, fallback.get(key));
        }
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

    private void enforceNoDuplicate(
        String resourceType,
        String action,
        String resourceId,
        Map<String, Object> afterPayload,
        String payloadJson
    ) {
        if (!DEDUP_TYPES.contains(resourceType)) {
            return;
        }
        Collection<ChangeRequest> actives = repository.findByResourceTypeAndStatusIn(resourceType, ACTIVE_STATUSES);
        if (actives == null || actives.isEmpty()) {
            return;
        }
        String normalizedId = normalizeResourceId(resourceId);
        String fingerprint = buildFingerprint(resourceType, normalizedId, afterPayload);
        for (ChangeRequest existing : actives) {
            String existingId = normalizeResourceId(existing.getResourceId());
            boolean conflict =
                matchesById(normalizedId, existingId) ||
                matchesByFingerprint(fingerprint, resourceType, existingId, existing) ||
                matchesByPayload(normalizedId, existingId, payloadJson, existing);
            if (conflict) {
                if (handleOrphaned(existing)) {
                    continue;
                }
                throw new IllegalStateException(buildDuplicateMessage(resourceType, normalizedId, afterPayload, existing));
            }
        }
    }

    private boolean handleOrphaned(ChangeRequest existing) {
        if (existing == null || existing.getId() == null) {
            return false;
        }
        if (approvalItemRepository == null) {
            return false;
        }
        boolean referenced = approvalItemRepository.existsByChangeRequestId(String.valueOf(existing.getId()));
        if (referenced) {
            return false;
        }
        existing.setStatus("FAILED");
        existing.setDecidedBy(SecurityUtils.getCurrentUserLogin().orElse("system"));
        existing.setDecidedAt(Instant.now());
        existing.setLastError("缺少对应审批请求，已自动作废");
        repository.save(existing);
        LOG.warn("Change request {} marked as FAILED due to missing approval reference", existing.getId());
        return true;
    }

    private boolean matchesById(String requestedId, String existingId) {
        if (!StringUtils.hasText(requestedId) || !StringUtils.hasText(existingId)) {
            return false;
        }
        return requestedId.trim().equalsIgnoreCase(existingId.trim());
    }

    private boolean matchesByFingerprint(
        String fingerprint,
        String resourceType,
        String existingId,
        ChangeRequest existing
    ) {
        if (!StringUtils.hasText(fingerprint)) {
            return false;
        }
        String existingFingerprint = buildFingerprint(resourceType, existingId, read(existing.getPayloadJson()));
        return StringUtils.hasText(existingFingerprint) && fingerprint.equalsIgnoreCase(existingFingerprint);
    }

    private boolean matchesByPayload(
        String requestedId,
        String existingId,
        String payloadJson,
        ChangeRequest existing
    ) {
        if (StringUtils.hasText(requestedId) || StringUtils.hasText(existingId)) {
            return false;
        }
        return payloadJson != null && payloadJson.equals(existing.getPayloadJson());
    }

    private String buildFingerprint(String resourceType, String resourceId, Map<String, Object> payload) {
        String normalizedId = normalizeResourceId(resourceId);
        if (StringUtils.hasText(normalizedId)) {
            return resourceType + "::" + normalizedId.trim().toLowerCase(Locale.ROOT);
        }
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        if ("PORTAL_MENU".equals(resourceType)) {
            String path = extractString(payload.get("path"));
            if (StringUtils.hasText(path)) {
                return resourceType + "::path::" + path.toLowerCase(Locale.ROOT);
            }
            String name = extractString(payload.get("name"));
            if (StringUtils.hasText(name)) {
                return resourceType + "::name::" + name.toLowerCase(Locale.ROOT);
            }
        } else if ("ROLE".equals(resourceType)) {
            String roleName = extractString(payload.get("name"));
            if (!StringUtils.hasText(roleName)) {
                roleName = extractString(payload.get("role"));
            }
            if (StringUtils.hasText(roleName)) {
                return resourceType + "::" + roleName.toUpperCase(Locale.ROOT);
            }
        } else if ("USER".equals(resourceType)) {
            String username = extractString(payload.get("username"));
            if (StringUtils.hasText(username)) {
                return resourceType + "::" + username.toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private String buildDuplicateMessage(
        String resourceType,
        String resourceId,
        Map<String, Object> payload,
        ChangeRequest existing
    ) {
        Map<String, Object> effectivePayload = payload != null && !payload.isEmpty() ? payload : read(existing.getPayloadJson());
        String base = "存在未处理的相同变更，请等待审批完成后再提交";
        switch (resourceType) {
            case "USER": {
                String username = firstNonBlank(resourceId, extractString(effectivePayload == null ? null : effectivePayload.get("username")));
                if (StringUtils.hasText(username)) {
                    return "用户【" + username.trim() + "】已有待审批的变更，请等待审批完成后再提交";
                }
                return base;
            }
            case "ROLE": {
                String roleName = firstNonBlank(
                    resourceId,
                    extractString(effectivePayload == null ? null : effectivePayload.get("name")),
                    extractString(effectivePayload == null ? null : effectivePayload.get("role"))
                );
                if (StringUtils.hasText(roleName)) {
                    return "角色【" + roleName.trim().toUpperCase(Locale.ROOT) + "】已有待审批的变更，请等待审批完成后再提交";
                }
                return base;
            }
            case "PORTAL_MENU": {
                String identifier = firstNonBlank(
                    resourceId,
                    extractString(effectivePayload == null ? null : effectivePayload.get("path")),
                    extractString(effectivePayload == null ? null : effectivePayload.get("name"))
                );
                if (StringUtils.hasText(identifier)) {
                    return "菜单【" + identifier.trim() + "】已有待审批的变更，请等待审批完成后再提交";
                }
                return base;
            }
            default:
                return base;
        }
    }

    private Map<String, Object> read(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeResourceId(String resourceId) {
        if (resourceId == null) {
            return null;
        }
        String trimmed = resourceId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String extractString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
