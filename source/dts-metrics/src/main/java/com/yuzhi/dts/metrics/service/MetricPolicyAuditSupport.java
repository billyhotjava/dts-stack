package com.yuzhi.dts.metrics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

final class MetricPolicyAuditSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MetricPolicyAuditSupport() {}

    static PolicyMetadata metadata(String securityPolicyJson) {
        Map<String, Object> policy = parsePolicy(securityPolicyJson);
        String policySource = stringValue(policy.get("policySource"));
        if (!StringUtils.hasText(policySource)) {
            policySource = "platform-permission";
        }
        return new PolicyMetadata(
            policySource,
            predicateHash(listValue(policy.get("predicates")), listValue(policy.get("maskedColumns")), policySource)
        );
    }

    static String predicateHash(PlatformContractClient.RlsPolicyResult policy) {
        PlatformContractClient.RlsPolicyResult effective = policy != null
            ? policy
            : PlatformContractClient.RlsPolicyResult.empty();
        return predicateHash(effective.predicates(), effective.maskedColumns(), effective.policySource());
    }

    static String predicateHash(List<?> predicates, List<?> maskedColumns, String policySource) {
        Map<String, Object> hashPayload = new LinkedHashMap<>();
        hashPayload.put("policySource", StringUtils.hasText(policySource) ? policySource : "platform-permission");
        hashPayload.put("predicates", predicates != null ? predicates : List.of());
        hashPayload.put("maskedColumns", maskedColumns != null ? maskedColumns : List.of());
        return "sha256:" + sha256(toJson(hashPayload));
    }

    private static Map<String, Object> parsePolicy(String securityPolicyJson) {
        if (!StringUtils.hasText(securityPolicyJson)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(securityPolicyJson, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("securityPolicyJson is not valid JSON", e);
        }
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to canonicalize security policy", e);
        }
    }

    private static List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    record PolicyMetadata(String policySource, String predicateHash) {}
}
