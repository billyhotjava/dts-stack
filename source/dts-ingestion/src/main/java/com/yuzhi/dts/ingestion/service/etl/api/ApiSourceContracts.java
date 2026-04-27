package com.yuzhi.dts.ingestion.service.etl.api;

import java.util.List;
import java.util.Map;

public final class ApiSourceContracts {

    public static final String CONTRACT_VERSION = "1.1.0";
    public static final String ODS_LANDING_MODE = "raw_record";
    public static final String RAW_RECORD_COLUMN = "_dts_raw_record";
    public static final String NORMALIZATION_LAYER = "stg";
    public static final String DEFAULT_SCHEMA_DRIFT_POLICY = "notify";
    public static final List<String> TECHNICAL_COLUMNS = List.of(
        "_dts_source_system",
        "_dts_source_resource",
        "_dts_endpoint",
        "_dts_import_time",
        "_dts_batch_id",
        "_dts_execution_id",
        "_dts_page_no",
        "_dts_record_no",
        "_dts_cursor_value"
    );

    private ApiSourceContracts() {}

    public static ApiLandingPolicy defaultLandingPolicy() {
        return new ApiLandingPolicy(ODS_LANDING_MODE, RAW_RECORD_COLUMN, TECHNICAL_COLUMNS);
    }

    public static SchemaSnapshotPolicy defaultSchemaSnapshotPolicy() {
        return new SchemaSnapshotPolicy(Boolean.TRUE, null, DEFAULT_SCHEMA_DRIFT_POLICY);
    }

    public static Map<String, Object> odsLandingDescriptor() {
        return Map.of(
            "mode",
            ODS_LANDING_MODE,
            "rawRecordColumn",
            RAW_RECORD_COLUMN,
            "technicalColumns",
            TECHNICAL_COLUMNS,
            "normalizationLayer",
            NORMALIZATION_LAYER
        );
    }

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
