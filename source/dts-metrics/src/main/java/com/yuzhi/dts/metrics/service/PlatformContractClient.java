package com.yuzhi.dts.metrics.service;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class PlatformContractClient {

    private final DtsMetricsProperties properties;
    private final RestClient restClient;

    public PlatformContractClient(DtsMetricsProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public Map<String, Object> describeContract() {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("platformBaseUrl", normalizeBaseUrl());
        contract.put("apiPath", normalizeApiPath());
        contract.put("serviceName", properties.getServiceName());
        contract.put("serviceTokenConfigured", StringUtils.hasText(properties.getPlatform().getServiceToken()));
        contract.put("authHeaders", Map.of("service", "X-DTS-Service", "token", "X-DTS-Service-Token"));
        return contract;
    }

    public String internalUrl(String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        String apiPath = normalizeApiPath();
        if (StringUtils.hasText(apiPath) && normalizedPath.equals(apiPath)) {
            return normalizeBaseUrl() + apiPath;
        }
        if (StringUtils.hasText(apiPath) && normalizedPath.startsWith(apiPath + "/")) {
            return normalizeBaseUrl() + normalizedPath;
        }
        return normalizeBaseUrl() + apiPath + normalizedPath;
    }

    public RestClient.RequestHeadersSpec<?> withServiceAuth(RestClient.RequestHeadersUriSpec<?> request, String path) {
        RestClient.RequestHeadersSpec<?> spec = request
            .uri(internalUrl(path))
            .header("X-DTS-Service", properties.getServiceName());
        if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
            spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
        }
        return spec;
    }

    public PermissionCheckResult checkPermission(PermissionCheckRequest request) {
        RestClient.RequestBodySpec spec = restClient
            .post()
            .uri(internalUrl("/internal/asset-permission/check"))
            .header("X-DTS-Service", properties.getServiceName());
        if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
            spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
        }
        PermissionCheckResult result = spec.body(request).retrieve().body(PermissionCheckResult.class);
        return result != null ? result : PermissionCheckResult.denied("empty_response");
    }

    public GlossaryResolveResult resolveGlossaryTerms(List<String> refs) {
        RestClient.RequestBodySpec spec = restClient
            .post()
            .uri(internalUrl("/internal/glossary/terms/resolve"))
            .header("X-DTS-Service", properties.getServiceName());
        if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
            spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
        }
        GlossaryResolveResult result = spec.body(Map.of("refs", refs != null ? refs : List.of())).retrieve().body(GlossaryResolveResult.class);
        return result != null ? result : GlossaryResolveResult.empty();
    }

    private String normalizeBaseUrl() {
        return stripTrailingSlash(properties.getPlatform().getBaseUrl());
    }

    private String normalizeApiPath() {
        String apiPath = properties.getPlatform().getApiPath();
        if (!StringUtils.hasText(apiPath) || "/".equals(apiPath)) {
            return "";
        }
        return apiPath.startsWith("/") ? stripTrailingSlash(apiPath) : "/" + stripTrailingSlash(apiPath);
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public record PermissionAsset(String type, String id, String key) {}

    public record GlossaryTermContract(String ref, String id, String code, String name, String status, boolean active) {}

    public record GlossaryResolveResult(List<GlossaryTermContract> terms, List<String> missing, List<String> inactive) {
        public static GlossaryResolveResult empty() {
            return new GlossaryResolveResult(List.of(), List.of(), List.of());
        }
    }

    public record PermissionCheckRequest(
        String username,
        java.util.List<String> userRoles,
        String userDeptCode,
        String userClassification,
        String assetClassification,
        String action,
        PermissionAsset asset
    ) {}

    public record PermissionCheckResult(
        boolean allowed,
        String permission,
        String reason,
        String requiredPermission,
        String action,
        String assetType,
        String assetId,
        String assetKey,
        String classificationDecision,
        String grantSource
    ) {
        public static PermissionCheckResult denied(String reason) {
            return new PermissionCheckResult(false, null, reason, null, null, null, null, null, null, null);
        }
    }
}
