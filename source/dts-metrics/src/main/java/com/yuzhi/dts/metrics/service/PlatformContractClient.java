package com.yuzhi.dts.metrics.service;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
                .uri(internalUrl("/internal/v1/asset-permission/policy"))
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

    @SuppressWarnings("unchecked")
    public Map<String, Object> checkDbtReleaseGate(DbtReleaseGateRequest request) {
        try {
            RestClient.RequestBodySpec spec = restClient
                .post()
                .uri(internalUrl("/etl/dbt/release-gate/check"))
                .header("X-DTS-Service", properties.getServiceName());
            if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
                spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
            }
            Map<String, Object> result = spec.body(request).retrieve().body(Map.class);
            return result != null ? result : Map.of();
        } catch (RestClientException e) {
            throw new PlatformContractException("platform contract call failed: dbt release gate check", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> listCatalogAssets(String warehouseLayer, String keyword, int page, int size) {
        try {
            Map<String, Object> result = withServiceAuth(
                restClient.get(),
                "/catalog/assets-v2?warehouseLayer=" +
                url(warehouseLayer) +
                "&keyword=" +
                url(keyword) +
                "&page=" +
                page +
                "&size=" +
                size
            )
                .retrieve()
                .body(Map.class);
            return result != null ? result : Map.of();
        } catch (RestClientException e) {
            throw new PlatformContractException("platform contract call failed: catalog assets read", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getCatalogAssetSchemaContract(String assetId) {
        try {
            Map<String, Object> result = withServiceAuth(restClient.get(), "/catalog/assets-v2/" + url(assetId) + "/schema-contract")
                .retrieve()
                .body(Map.class);
            return result != null ? result : Map.of();
        } catch (RestClientException e) {
            throw new PlatformContractException("platform contract call failed: catalog asset schema contract read", e);
        }
    }

    public void recordPolicyInjection(PolicyInjectionAuditRequest request) {
        try {
            RestClient.RequestBodySpec spec = restClient
                .post()
                .uri(internalUrl("/internal/v1/asset-permission/audit/policy-injection"))
                .header("X-DTS-Service", properties.getServiceName());
            if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
                spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
            }
            spec.body(request).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new PlatformContractException("platform contract call failed: policy injection audit", e);
        }
    }

    /**
     * Record a high-risk lifecycle action (generate / validate / publish / rollback) as a platform audit
     * event. Mirrors {@link #recordPolicyInjection} exactly: service-auth headers + bodiless POST.
     *
     * <p>F2-T04 / contract note: the receiver {@code /internal/audit-events} is owned by dts-platform and is
     * not yet implemented (F3-T03 联调依赖). Callers gate invocation behind
     * {@code dts.metrics.platform.audit-events-enabled}; this method only defines the metrics-side contract.
     */
    public void recordAuditEvent(AuditEventRequest request) {
        try {
            RestClient.RequestBodySpec spec = restClient
                .post()
                .uri(internalUrl("/internal/audit-events"))
                .header("X-DTS-Service", properties.getServiceName());
            if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
                spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
            }
            spec.body(request).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new PlatformContractException("platform contract call failed: audit event", e);
        }
    }

    /**
     * Register a published model as a platform BI Dataset (F3). The receiver
     * {@code /internal/bi/datasets/register} is owned by dts-platform; callers gate this behind
     * {@code dts.metrics.platform.bi-lineage-register-enabled} until that endpoint is live.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> registerBiDataset(BiDatasetRegisterRequest request) {
        try {
            RestClient.RequestBodySpec spec = restClient
                .post()
                .uri(internalUrl("/internal/bi/datasets/register"))
                .header("X-DTS-Service", properties.getServiceName());
            if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
                spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
            }
            Map<String, Object> result = spec.body(request).retrieve().body(Map.class);
            return result != null ? result : Map.of();
        } catch (RestClientException e) {
            throw new PlatformContractException("platform contract call failed: bi dataset register", e);
        }
    }

    /**
     * Register source -> DWS/ADS -> BI Dataset lineage for a published model (F3). The receiver
     * {@code /internal/lineage/register} is owned by dts-platform; gated like {@link #registerBiDataset}.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> registerLineage(LineageRegisterRequest request) {
        try {
            RestClient.RequestBodySpec spec = restClient
                .post()
                .uri(internalUrl("/internal/lineage/register"))
                .header("X-DTS-Service", properties.getServiceName());
            if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
                spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
            }
            Map<String, Object> result = spec.body(request).retrieve().body(Map.class);
            return result != null ? result : Map.of();
        } catch (RestClientException e) {
            throw new PlatformContractException("platform contract call failed: lineage register", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> registerTaggableAssets(TaggableAssetsRegisterRequest request) {
        try {
            RestClient.RequestBodySpec spec = restClient
                .post()
                .uri(internalUrl("/internal/catalog/taggable-assets/register"))
                .header("X-DTS-Service", properties.getServiceName());
            if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
                spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
            }
            Map<String, Object> result = spec.body(request).retrieve().body(Map.class);
            return result != null ? result : Map.of();
        } catch (RestClientException e) {
            throw new PlatformContractException("platform contract call failed: taggable asset register", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> validateMetricModel(MetricModelValidationRequest request) {
        try {
            RestClient.RequestBodySpec spec = restClient
                .post()
                .uri(internalUrl("/internal/metrics/model-validation"))
                .header("X-DTS-Service", properties.getServiceName());
            if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
                spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
            }
            Map<String, Object> result = spec.body(request).retrieve().body(Map.class);
            return result != null ? result : Map.of();
        } catch (RestClientException e) {
            throw new PlatformContractException("platform contract call failed: metric model validation", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> submitDbtRelease(DbtReleaseSubmitRequest request) {
        try {
            RestClient.RequestBodySpec spec = restClient
                .post()
                .uri(internalUrl("/etl/dbt/release/submit"))
                .header("X-DTS-Service", properties.getServiceName());
            if (StringUtils.hasText(properties.getPlatform().getServiceToken())) {
                spec = spec.header("X-DTS-Service-Token", properties.getPlatform().getServiceToken());
            }
            Map<String, Object> result = spec.body(request).retrieve().body(Map.class);
            return result != null ? result : Map.of();
        } catch (RestClientException e) {
            throw new PlatformContractException("platform contract call failed: dbt release submit", e);
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

    private static String url(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
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

    public record DbtReleaseGateRequest(
        String models,
        String gitRef,
        String commitSha,
        Boolean strictMode,
        String appliedPolicySource,
        String appliedPredicateHash
    ) {}

    public record MetricModelValidationRequest(
        String modelId,
        String modelName,
        String artifactRef,
        Map<String, Object> graph,
        Map<String, Object> artifacts,
        String appliedPolicySource,
        String appliedPredicateHash
    ) {}

    public record DbtReleaseSubmitRequest(
        String modelId,
        String modelName,
        String artifactRef,
        Boolean dryRun,
        String appliedPolicySource,
        String appliedPredicateHash
    ) {}

    public record PolicyInjectionAuditRequest(
        String actor,
        String assetType,
        String assetId,
        String action,
        List<String> predicates,
        List<String> maskedColumns,
        String policySource,
        String predicateHash,
        String direction,
        String packId
    ) {}

    /** A high-risk metric-model lifecycle action recorded as a platform audit event (F2-T04). */
    public record AuditEventRequest(
        String action,
        String modelId,
        String modelName,
        String actor,
        String policySource,
        String predicateHash,
        String platformReference,
        String outcome,
        String occurredAt
    ) {}

    /** Register a published model version as a platform BI Dataset (F3). */
    public record BiDatasetRegisterRequest(
        String modelId,
        String modelName,
        String version,
        String artifactRef,
        String platformPublishReference
    ) {}

    /** Register source -> DWS/ADS -> BI Dataset lineage for a published model version (F3). */
    public record LineageRegisterRequest(
        String modelId,
        String modelName,
        String version,
        String upstreamAsset,
        String platformPublishReference
    ) {}

    public record TaggableAssetRegistration(
        String assetType,
        String canonicalAssetKey,
        String remoteAssetId,
        String ownerDept
    ) {}

    public record TaggableAssetsRegisterRequest(
        List<TaggableAssetRegistration> assets,
        String registrationScope,
        UUID syncRunId,
        int batchIndex,
        int batchCount,
        boolean complete
    ) {
        public TaggableAssetsRegisterRequest(
            List<TaggableAssetRegistration> assets
        ) {
            this(assets, null, null, 0, 1, false);
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
