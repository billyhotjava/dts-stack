package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenCompliancePolicy;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenCompliancePolicyRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScreenComplianceService {

    private static final Set<String> DEFAULT_MASK_KEYS = Set.of(
            "phone",
            "mobile",
            "tel",
            "email",
            "mail",
            "idcard",
            "id_no",
            "idnumber",
            "name",
            "realname",
            "bank",
            "account",
            "address",
            "password",
            "secret",
            "token");

    private final ObjectMapper objectMapper;
    private final AnalyticsScreenCompliancePolicyRepository policyRepository;
    private final AtomicReference<ObjectNode> policyRef;
    private final AtomicReference<Integer> policyVersionRef;

    @Autowired
    public ScreenComplianceService(
            ObjectMapper objectMapper,
            AnalyticsScreenCompliancePolicyRepository policyRepository) {
        this.objectMapper = objectMapper;
        this.policyRepository = policyRepository;
        this.policyRef = new AtomicReference<>(defaultPolicy());
        this.policyVersionRef = new AtomicReference<>(1);
        loadPolicyFromStore();
    }

    ScreenComplianceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.policyRepository = null;
        this.policyRef = new AtomicReference<>(defaultPolicy());
        this.policyVersionRef = new AtomicReference<>(1);
    }

    public ObjectNode currentPolicy() {
        return attachVersion(policyRef.get().deepCopy(), policyVersionRef.get());
    }

    @Transactional
    public synchronized ObjectNode updatePolicy(JsonNode body, String updatedBy) {
        ObjectNode policy = policyRef.get().deepCopy();

        if (body != null && body.has("maskingEnabled")) {
            policy.put("maskingEnabled", body.path("maskingEnabled").asBoolean(false));
        }
        if (body != null && body.has("watermarkEnabled")) {
            policy.put("watermarkEnabled", body.path("watermarkEnabled").asBoolean(false));
        }
        if (body != null && body.has("watermarkText")) {
            String text = trimToNull(body.path("watermarkText").asText(null));
            policy.put("watermarkText", text == null ? "DTS INTERNAL" : text);
        }
        if (body != null && body.has("exportApprovalRequired")) {
            policy.put("exportApprovalRequired", body.path("exportApprovalRequired").asBoolean(false));
        }
        if (body != null && body.has("auditRetentionDays")) {
            int days = body.path("auditRetentionDays").asInt(180);
            policy.put("auditRetentionDays", Math.max(7, Math.min(1095, days)));
        }
        if (body != null && body.has("maskRules")) {
            policy.set("maskRules", sanitizeMaskRules(body.path("maskRules")));
        }

        policy.put("updatedBy", updatedBy == null ? "system" : updatedBy);
        policy.putPOJO("updatedAt", Instant.now());
        persistPolicy(policy, updatedBy);
        return attachVersion(policy.deepCopy(), policyVersionRef.get());
    }

    @Transactional(readOnly = true)
    public ArrayNode history(int limit) {
        int safeLimit = Math.max(1, Math.min(200, limit));
        ArrayNode rows = objectMapper.createArrayNode();

        if (policyRepository == null) {
            ObjectNode row = objectMapper.createObjectNode();
            row.put("version", policyVersionRef.get());
            row.put("current", true);
            row.put("updatedBy", policyRef.get().path("updatedBy").asText("system"));
            JsonNode updatedAt = policyRef.get().path("updatedAt");
            if (updatedAt != null && !updatedAt.isMissingNode()) {
                row.set("updatedAt", updatedAt);
            } else {
                row.putPOJO("updatedAt", Instant.now());
            }
            rows.add(row);
            return rows;
        }

        List<AnalyticsScreenCompliancePolicy> policies = policyRepository.findTop200ByOrderByVersionNoDesc();
        for (int i = 0; i < policies.size() && i < safeLimit; i++) {
            AnalyticsScreenCompliancePolicy policy = policies.get(i);
            ObjectNode row = objectMapper.createObjectNode();
            row.put("version", policy.getVersionNo() == null ? 0 : policy.getVersionNo());
            row.put("current", policy.isCurrent());
            row.put("updatedBy", policy.getUpdatedBy());
            row.putPOJO("createdAt", policy.getCreatedAt());
            rows.add(row);
        }
        return rows;
    }

    @Transactional
    public synchronized ObjectNode rollbackToVersion(int version, String updatedBy) {
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (policyRepository == null) {
            throw new IllegalStateException("compliance policy persistence is unavailable");
        }

        AnalyticsScreenCompliancePolicy target = policyRepository.findByVersionNo(version).orElse(null);
        if (target == null) {
            throw new IllegalArgumentException("target policy version not found");
        }

        ObjectNode policy = parsePolicyNode(target.getPolicyJson());
        policy.put("updatedBy", updatedBy == null ? "system" : updatedBy);
        policy.putPOJO("updatedAt", Instant.now());
        policy.put("rolledBackFromVersion", version);

        persistPolicy(policy, updatedBy);
        return attachVersion(policy.deepCopy(), policyVersionRef.get());
    }

    public DatasetQueryService.DatasetResult applyMasking(DatasetQueryService.DatasetResult result) {
        if (result == null || result.rows() == null || result.rows().isEmpty()) {
            return result;
        }

        ObjectNode policy = policyRef.get();
        if (!policy.path("maskingEnabled").asBoolean(false)) {
            return result;
        }

        List<Map<String, Object>> cols = result.cols() == null ? List.of() : result.cols();
        if (cols.isEmpty()) {
            return result;
        }

        Set<String> customMaskRules = new LinkedHashSet<>();
        JsonNode maskRulesNode = policy.path("maskRules");
        if (maskRulesNode.isArray()) {
            for (JsonNode item : maskRulesNode) {
                String normalized = normalizeRule(item == null ? null : item.asText(null));
                if (normalized != null) {
                    customMaskRules.add(normalized);
                }
            }
        }

        List<Integer> maskedColumns = new ArrayList<>();
        List<String> maskKinds = new ArrayList<>();

        for (int i = 0; i < cols.size(); i++) {
            String columnName = extractColumnName(cols.get(i));
            String normalized = normalizeRule(columnName);
            if (normalized == null) {
                continue;
            }
            if (shouldMask(normalized, customMaskRules)) {
                maskedColumns.add(i);
                maskKinds.add(detectMaskKind(normalized));
            }
        }

        if (maskedColumns.isEmpty()) {
            return result;
        }

        List<List<Object>> maskedRows = new ArrayList<>(result.rows().size());
        for (List<Object> row : result.rows()) {
            List<Object> copy = row == null ? new ArrayList<>() : new ArrayList<>(row);
            for (int idx = 0; idx < maskedColumns.size(); idx++) {
                int colIndex = maskedColumns.get(idx);
                if (colIndex < 0 || colIndex >= copy.size()) {
                    continue;
                }
                Object value = copy.get(colIndex);
                copy.set(colIndex, maskValue(value, maskKinds.get(idx)));
            }
            maskedRows.add(copy);
        }

        return new DatasetQueryService.DatasetResult(
                maskedRows, result.cols(), result.resultsMetadataColumns(), result.resultsTimezone());
    }

    private void loadPolicyFromStore() {
        if (policyRepository == null) {
            return;
        }
        AnalyticsScreenCompliancePolicy current = policyRepository.findFirstByCurrentTrueOrderByVersionNoDesc()
                .orElseGet(() -> policyRepository.findFirstByOrderByVersionNoDesc().orElse(null));

        if (current == null) {
            ObjectNode initial = defaultPolicy();
            initial.put("updatedBy", "system");
            initial.putPOJO("updatedAt", Instant.now());
            AnalyticsScreenCompliancePolicy row = new AnalyticsScreenCompliancePolicy();
            row.setVersionNo(1);
            row.setPolicyJson(initial.toString());
            row.setUpdatedBy("system");
            row.setCurrent(true);
            policyRepository.save(row);
            policyRef.set(initial);
            policyVersionRef.set(1);
            return;
        }

        ObjectNode loaded = parsePolicyNode(current.getPolicyJson());
        if (!loaded.has("updatedBy")) {
            loaded.put("updatedBy", current.getUpdatedBy() == null ? "system" : current.getUpdatedBy());
        }
        if (!loaded.has("updatedAt") && current.getCreatedAt() != null) {
            loaded.putPOJO("updatedAt", current.getCreatedAt());
        }
        policyRef.set(loaded);
        policyVersionRef.set(current.getVersionNo() == null ? 1 : current.getVersionNo());
    }

    private void persistPolicy(ObjectNode policy, String updatedBy) {
        if (policyRepository == null) {
            policyRef.set(policy.deepCopy());
            return;
        }

        AnalyticsScreenCompliancePolicy current = policyRepository.findFirstByCurrentTrueOrderByVersionNoDesc().orElse(null);
        if (current != null) {
            current.setCurrent(false);
            policyRepository.save(current);
        }

        int nextVersion = policyRepository.findFirstByOrderByVersionNoDesc()
                .map(AnalyticsScreenCompliancePolicy::getVersionNo)
                .orElse(0) + 1;
        AnalyticsScreenCompliancePolicy row = new AnalyticsScreenCompliancePolicy();
        row.setVersionNo(nextVersion);
        row.setPolicyJson(policy.toString());
        row.setUpdatedBy(trimToNull(updatedBy));
        row.setCurrent(true);
        policyRepository.save(row);

        policyRef.set(policy.deepCopy());
        policyVersionRef.set(nextVersion);
    }

    private ObjectNode parsePolicyNode(String json) {
        if (json != null && !json.isBlank()) {
            try {
                JsonNode parsed = objectMapper.readTree(json);
                if (parsed != null && parsed.isObject()) {
                    ObjectNode node = (ObjectNode) parsed;
                    normalizePolicyDefaults(node);
                    return node;
                }
            } catch (Exception ignored) {
                // Fallback to defaults.
            }
        }
        return defaultPolicy();
    }

    private ObjectNode attachVersion(ObjectNode policy, int version) {
        policy.put("policyVersion", version);
        return policy;
    }

    private void normalizePolicyDefaults(ObjectNode policy) {
        if (!policy.has("maskingEnabled")) {
            policy.put("maskingEnabled", false);
        }
        if (!policy.has("watermarkEnabled")) {
            policy.put("watermarkEnabled", true);
        }
        if (!policy.has("watermarkText")) {
            policy.put("watermarkText", "DTS INTERNAL");
        }
        if (!policy.has("exportApprovalRequired")) {
            policy.put("exportApprovalRequired", false);
        }
        if (!policy.has("auditRetentionDays")) {
            policy.put("auditRetentionDays", 180);
        }
        if (!policy.has("maskRules") || !policy.path("maskRules").isArray()) {
            policy.set("maskRules", defaultPolicy().path("maskRules"));
        } else {
            policy.set("maskRules", sanitizeMaskRules(policy.path("maskRules")));
        }
        if (!policy.has("updatedBy")) {
            policy.put("updatedBy", "system");
        }
        if (!policy.has("updatedAt")) {
            policy.putPOJO("updatedAt", Instant.now());
        }
    }

    private String maskValue(Object value, String kind) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return text;
        }
        return switch (kind) {
            case "email" -> maskEmail(text);
            case "phone" -> maskPhone(text);
            case "id" -> maskId(text);
            case "name" -> maskName(text);
            default -> "******";
        };
    }

    private boolean shouldMask(String normalizedColumnName, Set<String> customMaskRules) {
        if (!customMaskRules.isEmpty()) {
            for (String custom : customMaskRules) {
                if (normalizedColumnName.contains(custom)) {
                    return true;
                }
            }
            return false;
        }
        for (String key : DEFAULT_MASK_KEYS) {
            if (normalizedColumnName.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private String detectMaskKind(String normalizedColumnName) {
        if (normalizedColumnName.contains("mail") || normalizedColumnName.contains("email")) {
            return "email";
        }
        if (normalizedColumnName.contains("phone")
                || normalizedColumnName.contains("mobile")
                || normalizedColumnName.contains("tel")) {
            return "phone";
        }
        if (normalizedColumnName.contains("id") || normalizedColumnName.contains("cert")) {
            return "id";
        }
        if (normalizedColumnName.contains("name")) {
            return "name";
        }
        return "generic";
    }

    private String extractColumnName(Map<String, Object> col) {
        if (col == null) {
            return null;
        }
        Object name = col.get("name");
        if (name instanceof String s && !s.isBlank()) {
            return s;
        }
        Object display = col.get("display_name");
        if (display instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    private ArrayNode sanitizeMaskRules(JsonNode node) {
        ArrayNode rules = objectMapper.createArrayNode();
        if (node == null || !node.isArray()) {
            return rules;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String normalized = normalizeRule(item == null ? null : item.asText(null));
            if (normalized != null) {
                unique.add(normalized);
            }
        }
        for (String rule : unique) {
            rules.add(rule);
        }
        return rules;
    }

    private String normalizeRule(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        normalized = normalized.replaceAll("[^a-z0-9_\\u4e00-\\u9fa5]", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private ObjectNode defaultPolicy() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("maskingEnabled", false);
        node.put("watermarkEnabled", true);
        node.put("watermarkText", "DTS INTERNAL");
        node.put("exportApprovalRequired", false);
        node.put("auditRetentionDays", 180);
        ArrayNode maskRules = objectMapper.createArrayNode();
        maskRules.add("phone");
        maskRules.add("mobile");
        maskRules.add("email");
        maskRules.add("idcard");
        maskRules.add("name");
        node.set("maskRules", maskRules);
        node.put("updatedBy", "system");
        node.putPOJO("updatedAt", Instant.now());
        return node;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String maskName(String value) {
        int length = value.length();
        if (length <= 1) {
            return "*";
        }
        if (length == 2) {
            return value.charAt(0) + "*";
        }
        return value.charAt(0) + "**";
    }

    private String maskEmail(String value) {
        int at = value.indexOf('@');
        if (at <= 0) {
            return "******";
        }
        String local = value.substring(0, at);
        String domain = value.substring(at);
        if (local.length() <= 1) {
            return "*" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }

    private String maskPhone(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return "******";
        }
        String left = digits.substring(0, Math.min(3, digits.length()));
        String right = digits.substring(Math.max(digits.length() - 4, 0));
        return left + "****" + right;
    }

    private String maskId(String value) {
        if (value.length() <= 6) {
            return "******";
        }
        return value.substring(0, 3) + "********" + value.substring(value.length() - 3);
    }
}
