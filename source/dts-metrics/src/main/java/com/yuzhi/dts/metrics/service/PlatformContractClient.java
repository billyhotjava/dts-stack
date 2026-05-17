package com.yuzhi.dts.metrics.service;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PlatformContractClient {

    private static final int RESOLVE_BATCH_SIZE = 200;

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
        try {
            RestClient.RequestBodySpec spec = restClient
                .post()
                .uri(internalUrl("/internal/asset-permission/check"))
                .header("X-DTS-Service", properties.getServiceName());
            if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
                spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
            }
            PermissionCheckResult result = spec.body(request).retrieve().body(PermissionCheckResult.class);
            return result != null ? result : PermissionCheckResult.denied("empty_response");
        } catch (RestClientException e) {
            throw new PlatformContractException("platform contract call failed: asset permission check", e);
        }
    }

    public RlsPolicyResult resolveRlsPolicy(RlsPolicyRequest request) {
        try {
            RestClient.RequestBodySpec spec = restClient
                .post()
                .uri(internalUrl("/internal/asset-permission/policy"))
                .header("X-DTS-Service", properties.getServiceName());
            if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
                spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
            }
            RlsPolicyResult result = spec.body(request).retrieve().body(RlsPolicyResult.class);
            return result != null ? result : RlsPolicyResult.empty();
        } catch (RestClientException e) {
            throw new PlatformContractException("platform contract call failed: security policy resolve", e);
        }
    }

    public GlossaryResolveResult resolveGlossaryTerms(List<String> refs) {
        List<String> requestedRefs = refs != null ? refs : List.of();
        if (requestedRefs.isEmpty()) {
            return GlossaryResolveResult.empty();
        }
        List<GlossaryTermContract> terms = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> inactive = new ArrayList<>();
        List<String> ambiguous = new ArrayList<>();
        for (int start = 0; start < requestedRefs.size(); start += RESOLVE_BATCH_SIZE) {
            int end = Math.min(start + RESOLVE_BATCH_SIZE, requestedRefs.size());
            GlossaryResolveResult batch = resolveGlossaryTermsBatch(requestedRefs.subList(start, end));
            if (batch.terms() != null) {
                terms.addAll(batch.terms());
            }
            if (batch.missing() != null) {
                missing.addAll(batch.missing());
            }
            if (batch.inactive() != null) {
                inactive.addAll(batch.inactive());
            }
            if (batch.ambiguous() != null) {
                ambiguous.addAll(batch.ambiguous());
            }
        }
        return new GlossaryResolveResult(List.copyOf(terms), List.copyOf(missing), List.copyOf(inactive), List.copyOf(ambiguous));
    }

    public DomainResolveResult resolveDomains(List<String> refs) {
        List<String> requestedRefs = refs != null ? refs : List.of();
        if (requestedRefs.isEmpty()) {
            return DomainResolveResult.empty();
        }
        List<DomainContract> domains = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> ambiguous = new ArrayList<>();
        for (int start = 0; start < requestedRefs.size(); start += RESOLVE_BATCH_SIZE) {
            int end = Math.min(start + RESOLVE_BATCH_SIZE, requestedRefs.size());
            DomainResolveResult batch = resolveDomainsBatch(requestedRefs.subList(start, end));
            if (batch.domains() != null) {
                domains.addAll(batch.domains());
            }
            if (batch.missing() != null) {
                missing.addAll(batch.missing());
            }
            if (batch.ambiguous() != null) {
                ambiguous.addAll(batch.ambiguous());
            }
        }
        return new DomainResolveResult(List.copyOf(domains), List.copyOf(missing), List.copyOf(ambiguous));
    }

    public DataStandardResolveResult resolveDataStandards(List<String> refs) {
        List<String> requestedRefs = refs != null ? refs : List.of();
        if (requestedRefs.isEmpty()) {
            return DataStandardResolveResult.empty();
        }
        List<DataStandardContract> standards = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> inactive = new ArrayList<>();
        List<String> ambiguous = new ArrayList<>();
        for (int start = 0; start < requestedRefs.size(); start += RESOLVE_BATCH_SIZE) {
            int end = Math.min(start + RESOLVE_BATCH_SIZE, requestedRefs.size());
            DataStandardResolveResult batch = resolveDataStandardsBatch(requestedRefs.subList(start, end));
            if (batch.standards() != null) {
                standards.addAll(batch.standards());
            }
            if (batch.missing() != null) {
                missing.addAll(batch.missing());
            }
            if (batch.inactive() != null) {
                inactive.addAll(batch.inactive());
            }
            if (batch.ambiguous() != null) {
                ambiguous.addAll(batch.ambiguous());
            }
        }
        return new DataStandardResolveResult(List.copyOf(standards), List.copyOf(missing), List.copyOf(inactive), List.copyOf(ambiguous));
    }

    private GlossaryResolveResult resolveGlossaryTermsBatch(List<String> refs) {
        try {
            RestClient.RequestBodySpec spec = restClient
                .post()
                .uri(internalUrl("/internal/glossary/terms/resolve"))
                .header("X-DTS-Service", properties.getServiceName());
            if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
                spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
            }
            GlossaryResolveResult result = spec.body(Map.of("refs", refs)).retrieve().body(GlossaryResolveResult.class);
            return result != null ? result : GlossaryResolveResult.empty();
        } catch (RestClientException e) {
            throw new PlatformContractException("platform contract call failed: glossary terms resolve", e);
        }
    }

    private DomainResolveResult resolveDomainsBatch(List<String> refs) {
        try {
            RestClient.RequestBodySpec spec = restClient
                .post()
                .uri(internalUrl("/internal/domains/resolve"))
                .header("X-DTS-Service", properties.getServiceName());
            if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
                spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
            }
            DomainResolveResult result = spec.body(Map.of("refs", refs)).retrieve().body(DomainResolveResult.class);
            return result != null ? result : DomainResolveResult.empty();
        } catch (RestClientException e) {
            throw new PlatformContractException("platform contract call failed: domains resolve", e);
        }
    }

    private DataStandardResolveResult resolveDataStandardsBatch(List<String> refs) {
        try {
            RestClient.RequestBodySpec spec = restClient
                .post()
                .uri(internalUrl("/internal/data-standards/resolve"))
                .header("X-DTS-Service", properties.getServiceName());
            if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
                spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
            }
            DataStandardResolveResult result = spec.body(Map.of("refs", refs)).retrieve().body(DataStandardResolveResult.class);
            return result != null ? result : DataStandardResolveResult.empty();
        } catch (RestClientException e) {
            throw new PlatformContractException("platform contract call failed: data standards resolve", e);
        }
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

    public record DomainContract(String ref, String id, String code, String name, String owner) {}

    public record DataStandardContract(
        String ref,
        String id,
        String code,
        String name,
        String domain,
        String dataType,
        Boolean nullable,
        String status,
        boolean active
    ) {}

    public record GlossaryResolveResult(
        List<GlossaryTermContract> terms,
        List<String> missing,
        List<String> inactive,
        List<String> ambiguous
    ) {
        public static GlossaryResolveResult empty() {
            return new GlossaryResolveResult(List.of(), List.of(), List.of(), List.of());
        }
    }

    public record DomainResolveResult(List<DomainContract> domains, List<String> missing, List<String> ambiguous) {
        public static DomainResolveResult empty() {
            return new DomainResolveResult(List.of(), List.of(), List.of());
        }
    }

    public record DataStandardResolveResult(
        List<DataStandardContract> standards,
        List<String> missing,
        List<String> inactive,
        List<String> ambiguous
    ) {
        public static DataStandardResolveResult empty() {
            return new DataStandardResolveResult(List.of(), List.of(), List.of(), List.of());
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

    public record RlsPolicyRequest(
        String username,
        java.util.List<String> userRoles,
        String userDeptCode,
        String userClassification,
        String assetClassification,
        String action,
        PermissionAsset asset
    ) {}

    public record RlsPolicyResult(boolean applyRls, List<String> predicates, List<String> maskedColumns, String policySource) {
        public static RlsPolicyResult empty() {
            return new RlsPolicyResult(true, List.of(), List.of(), "platform-permission");
        }
    }

    public static class PlatformContractException extends RuntimeException {
        public PlatformContractException(String message, Throwable cause) {
            super(message, cause);
        }

        public PlatformContractException(String message) {
            super(message);
        }
    }
}
