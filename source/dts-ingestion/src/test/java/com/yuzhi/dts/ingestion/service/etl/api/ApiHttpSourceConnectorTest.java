package com.yuzhi.dts.ingestion.service.etl.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.ingestion.config.ApiProperties;
import com.yuzhi.dts.ingestion.service.etl.connector.ExecutionPlan;
import com.yuzhi.dts.ingestion.service.etl.connector.SourceConnectorContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiHttpSourceConnectorTest {

    private final ApiHttpSourceConnector connector = new ApiHttpSourceConnector();

    @Test
    void supportsApiAliases() {
        SourceConnectorContext context = new SourceConnectorContext(
            null,
            "api-task",
            UUID.randomUUID(),
            "http_api",
            Map.of(),
            "full_refresh",
            Map.of(),
            List.of()
        );

        assertThat(connector.supports(context)).isTrue();
    }

    @Test
    void buildExecutionPlan_usesApiHttpEngineAndCursorPolicyForIncremental() {
        UUID sourceId = UUID.randomUUID();
        SourceConnectorContext context = new SourceConnectorContext(
            1L,
            "api-task",
            sourceId,
            "api",
            Map.of(
                "sourceSystem",
                "CRM",
                "resource",
                Map.of(
                    "resourceId",
                    "orders",
                    "path",
                    "/orders",
                    "fields",
                    List.of(Map.of("sourceField", "id", "targetColumn", "id")),
                    "cursor",
                    Map.of("field", "updatedAt")
                )
            ),
            "incremental",
            Map.of("cursor", Map.of("field", "updatedAt")),
            List.of()
        );

        ExecutionPlan plan = connector.buildExecutionPlan(context);

        assertThat(plan.engine()).isEqualTo("api-http");
        assertThat(plan.connectorType()).isEqualTo("api");
        assertThat(plan.checkpointPolicy().type()).isEqualTo("cursor");
        assertThat(plan.checkpointPolicy().cursorField()).isEqualTo("updatedAt");
        assertThat(plan.payload()).containsEntry("sourceDataSourceId", sourceId.toString());
        assertThat(plan.payload()).containsEntry("streams", List.of("orders"));
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceConfig = (Map<String, Object>) plan.payload().get("sourceConfig");
        @SuppressWarnings("unchecked")
        Map<String, Object> resource = (Map<String, Object>) sourceConfig.get("resource");
        assertThat(resource)
            .containsEntry("targetTable", "ods_api_crm_orders")
            .doesNotContainKey("fields");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> mappings = (List<Map<String, String>>) plan.payload().get("odsMappings");
        assertThat(mappings).hasSize(1);
        assertThat(mappings.get(0)).containsEntry("source", "orders").containsEntry("target", "ods_api_crm_orders");
    }

    @Test
    void buildExecutionPlan_usesConfiguredApiTablePrefix() {
        ApiProperties properties = new ApiProperties();
        properties.setTablePrefix("ods_ext_");
        ApiHttpSourceConnector prefixedConnector = new ApiHttpSourceConnector(properties);
        UUID sourceId = UUID.randomUUID();
        SourceConnectorContext context = new SourceConnectorContext(
            1L,
            "api-task",
            sourceId,
            "api",
            Map.of(
                "sourceSystem",
                "CRM",
                "resource",
                Map.of("resourceId", "orders", "path", "/orders")
            ),
            "full_refresh",
            Map.of(),
            List.of()
        );

        ExecutionPlan plan = prefixedConnector.buildExecutionPlan(context);

        @SuppressWarnings("unchecked")
        Map<String, Object> sourceConfig = (Map<String, Object>) plan.payload().get("sourceConfig");
        @SuppressWarnings("unchecked")
        Map<String, Object> resource = (Map<String, Object>) sourceConfig.get("resource");
        assertThat(resource).containsEntry("targetTable", "ods_ext_crm_orders");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> mappings = (List<Map<String, String>>) plan.payload().get("odsMappings");
        assertThat(mappings.get(0)).containsEntry("target", "ods_ext_crm_orders");
    }

    @Test
    void buildExecutionPlan_promotesNestedRuntimePoliciesAndResources() {
        UUID sourceId = UUID.randomUUID();
        SourceConnectorContext context = new SourceConnectorContext(
            1L,
            "api-task",
            sourceId,
            "api",
            Map.of(
                "api",
                Map.of(
                    "requestPolicy",
                    Map.of("allowHttp", true),
                    "rateLimit",
                    Map.of("requestsPerSecond", 2),
                    "tls",
                    Map.of("verifyTls", false)
                ),
                "readerConfig",
                Map.of("resources", List.of(Map.of("resourceId", "orders", "path", "/v1/orders")))
            ),
            "full_refresh",
            Map.of(),
            List.of()
        );

        ExecutionPlan plan = connector.buildExecutionPlan(context);

        @SuppressWarnings("unchecked")
        Map<String, Object> sourceConfig = (Map<String, Object>) plan.payload().get("sourceConfig");
        assertThat(sourceConfig)
            .containsEntry("requestPolicy", Map.of("allowHttp", true))
            .containsEntry("rateLimit", Map.of("requestsPerSecond", 2))
            .containsEntry("tls", Map.of("verifyTls", false));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) sourceConfig.get("resources");
        assertThat(resources).hasSize(1);
        assertThat(resources.get(0)).containsEntry("path", "/v1/orders");
    }

    @Test
    void validate_requiresDataSourceId() {
        SourceConnectorContext context = new SourceConnectorContext(
            null,
            "api-task",
            null,
            "api",
            Map.of(),
            "full_refresh",
            Map.of(),
            List.of()
        );

        assertThatThrownBy(() -> connector.validate(context))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("API 数据源不能为空");
    }
}
