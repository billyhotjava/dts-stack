package com.yuzhi.dts.ingestion.service.etl.api;

import java.util.List;
import java.util.Map;

public final class ApiSourceContracts {

    public static final String CONTRACT_VERSION = "1.1.0";

    private ApiSourceContracts() {}

    public record ApiSourceConfig(
        String baseUrl,
        Map<String, String> defaultHeaders,
        RequestPolicy requestPolicy,
        RateLimitPolicy rateLimit,
        TlsPolicy tls,
        AuthConfig auth
    ) {}

    public record ApiResourceConfig(
        String resourceId,
        String displayName,
        String path,
        String method,
        Map<String, Object> query,
        Object bodyTemplate,
        String recordPath,
        ApiLandingPolicy landing,
        PaginationPolicy pagination,
        CursorPolicy cursor,
        SchemaSnapshotPolicy schemaSnapshot,
        List<ApiStagingFieldMapping> stagingFields
    ) {}

    public record RequestPolicy(Integer connectTimeoutMillis, Integer readTimeoutMillis, Integer maxResponseBytes, Boolean followRedirects) {}

    public record RateLimitPolicy(Integer requestsPerSecond, Integer burst, Integer maxConcurrency) {}

    public record TlsPolicy(Boolean verifyTls, String mtlsSecretRef) {}

    public record AuthConfig(String provider, Map<String, Object> config, Map<String, String> secretRefs) {}

    public record PaginationPolicy(String type, Integer pageSize, String pageParam, String sizeParam, String nextTokenPath, String nextUrlPath) {}

    public record CursorPolicy(String type, String field, String injectInto, String parameterName, String initialValue, Integer lookbackSeconds) {}

    public record ApiLandingPolicy(String mode, String rawRecordColumn, List<String> technicalColumns) {}

    public record SchemaSnapshotPolicy(Boolean enabled, String snapshotRef, String driftPolicy) {}

    public record ApiStagingFieldMapping(
        String jsonPath,
        String stgColumn,
        String targetType,
        Boolean nullable,
        Boolean primaryKey,
        Boolean sensitive
    ) {}
}
