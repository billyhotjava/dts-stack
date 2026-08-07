package com.yuzhi.dts.ingestion.service.etl.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiSourceConfigNormalizerTest {

    @Test
    void normalize_addsRawOdsLandingAndRemovesLegacyFieldMapping() {
        UUID sourceId = UUID.fromString("11111111-2222-3333-4444-555555555555");

        Map<String, Object> normalized = ApiSourceConfigNormalizer.normalize(
            Map.of(
                "sourceSystem",
                "CRM",
                "resource",
                Map.of(
                    "path",
                    "/v1/orders",
                    "fields",
                    List.of(Map.of("sourceField", "id", "targetColumn", "id")),
                    "cursor",
                    Map.of("field", "updatedAt")
                )
            ),
            sourceId,
            "crm-api-task"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> resource = (Map<String, Object>) normalized.get("resource");
        assertThat(resource)
            .containsEntry("resourceId", "v1_orders")
            .containsEntry("targetTable", "ods_api_crm_v1_orders")
            .doesNotContainKey("fields");

        @SuppressWarnings("unchecked")
        Map<String, Object> landing = (Map<String, Object>) resource.get("landing");
        assertThat(landing)
            .containsEntry("mode", "raw_record")
            .containsEntry("rawRecordColumn", "_dts_raw_record");
        @SuppressWarnings("unchecked")
        List<String> technicalColumns = (List<String>) landing.get("technicalColumns");
        assertThat(technicalColumns).contains("_dts_batch_id", "_dts_cursor_value");

        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) resource.get("schemaSnapshot");
        assertThat(snapshot).containsEntry("enabled", true).containsEntry("driftPolicy", "notify");
    }

    @Test
    void deriveOdsMappings_usesNormalizedTargetTable() {
        List<Map<String, String>> mappings = ApiSourceConfigNormalizer.deriveOdsMappings(
            Map.of(
                "sourceSystem",
                "MES",
                "resources",
                List.of(Map.of("resourceId", "work-orders", "path", "/work-orders"))
            ),
            UUID.randomUUID(),
            "mes-api"
        );

        assertThat(mappings).containsExactly(
            Map.of(
                "source",
                "work-orders",
                "target",
                "ods_api_mes_work_orders",
                "landingMode",
                "raw_record",
                "rawRecordColumn",
                "_dts_raw_record"
            )
        );
    }

    @Test
    void normalize_promotesNestedRuntimePoliciesAndResources() {
        Map<String, Object> normalized = ApiSourceConfigNormalizer.normalize(
            Map.of(
                "api",
                Map.of(
                    "requestPolicy",
                    Map.of("allowHttp", true, "readTimeoutMillis", 9000),
                    "rateLimit",
                    Map.of("requestsPerSecond", 3),
                    "tls",
                    Map.of("verifyTls", false)
                ),
                "readerConfig",
                Map.of(
                    "resources",
                    List.of(Map.of("resourceId", "orders", "path", "/v1/orders", "recordPath", "$.data.items"))
                )
            ),
            UUID.randomUUID(),
            "crm-api"
        );

        assertThat(normalized)
            .containsEntry("requestPolicy", Map.of("allowHttp", true, "readTimeoutMillis", 9000))
            .containsEntry("rateLimit", Map.of("requestsPerSecond", 3))
            .containsEntry("tls", Map.of("verifyTls", false));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) normalized.get("resources");
        assertThat(resources).hasSize(1);
        assertThat(resources.get(0)).containsEntry("path", "/v1/orders").containsEntry("recordPath", "$.data.items");
    }

    @Test
    void promotesRuntimePoliciesFromEmbeddedDatasourcePropsSnapshot() {
        Map<String, Object> normalized = ApiSourceConfigNormalizer.normalize(
            Map.of(
                "baseUrl",
                "http://api.example",
                "auth",
                Map.of("provider", "basic"),
                "props",
                Map.of(
                    "api",
                    Map.of(
                        "requestPolicy",
                        Map.of("allowHttp", true, "readTimeoutMillis", 9000),
                        "rateLimit",
                        Map.of("requestsPerSecond", 3)
                    ),
                    "readerConfig",
                    Map.of(
                        "tls",
                        Map.of("verifyTls", false)
                    ),
                    "baseUrl",
                    "http://api.example"
                )
            ),
            UUID.randomUUID(),
            "legacy-api-task"
        );

        assertThat(normalized)
            .containsEntry("requestPolicy", Map.of("allowHttp", true, "readTimeoutMillis", 9000))
            .containsEntry("rateLimit", Map.of("requestsPerSecond", 3))
            .containsEntry("tls", Map.of("verifyTls", false));
    }
}
