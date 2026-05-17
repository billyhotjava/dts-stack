package com.yuzhi.dts.metrics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.metrics.service.dto.MetricArtifactPreviewResult;
import com.yuzhi.dts.metrics.service.dto.MetricArtifactPublishResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MetricArtifactPublishService {

    private final MetricArtifactGenerationService generationService;
    private final PlatformContractClient platformContractClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MetricArtifactPublishService(
        MetricArtifactGenerationService generationService,
        PlatformContractClient platformContractClient
    ) {
        this.generationService = generationService;
        this.platformContractClient = platformContractClient;
    }

    public MetricArtifactPublishResult publishDryRun(
        String manifestContent,
        MetricArtifactGenerationService.PreviewActor actor
    ) {
        MetricArtifactPreviewResult preview = generationService.preview(manifestContent, actor);
        if (preview == null || !preview.valid()) {
            return MetricArtifactPublishResult.invalid(
                preview == null ? List.of("artifact preview returned empty result") : preview.errors(),
                preview == null ? Map.of() : preview.summary()
            );
        }

        PolicyMetadata policy = policyMetadata(preview.artifacts().get("securityPolicyJson"));
        String modelName = String.valueOf(preview.summary().getOrDefault("modelName", "all"));
        Map<String, Object> releaseGate = platformContractClient.checkDbtReleaseGate(
            new PlatformContractClient.DbtReleaseGateRequest(
                modelName,
                null,
                null,
                true,
                policy.policySource(),
                policy.predicateHash()
            )
        );
        List<String> warnings = new ArrayList<>(preview.warnings());
        warnings.add("Publish dry-run re-resolved dts-platform security policy before dbt release gate.");
        return MetricArtifactPublishResult.valid(
            preview.summary(),
            preview.artifacts(),
            warnings,
            releaseGate,
            policy.policySource(),
            policy.predicateHash()
        );
    }

    private PolicyMetadata policyMetadata(String securityPolicyJson) {
        Map<String, Object> policy = parsePolicy(securityPolicyJson);
        String policySource = stringValue(policy.get("policySource"));
        if (!StringUtils.hasText(policySource)) {
            policySource = "platform-permission";
        }
        Map<String, Object> hashPayload = new LinkedHashMap<>();
        hashPayload.put("policySource", policySource);
        hashPayload.put("predicates", listValue(policy.get("predicates")));
        hashPayload.put("maskedColumns", listValue(policy.get("maskedColumns")));
        return new PolicyMetadata(policySource, "sha256:" + sha256(toJson(hashPayload)));
    }

    private Map<String, Object> parsePolicy(String securityPolicyJson) {
        if (!StringUtils.hasText(securityPolicyJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(securityPolicyJson, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("securityPolicyJson is not valid JSON", e);
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
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

    private record PolicyMetadata(String policySource, String predicateHash) {}
}
