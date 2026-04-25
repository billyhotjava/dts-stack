package com.yuzhi.dts.ingestion.service.etl.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        SourceConnectorContext context = new SourceConnectorContext(null, "api-task", UUID.randomUUID(), "http_api", Map.of(), "full_refresh", Map.of(), List.of());

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
            Map.of("resourceId", "orders"),
            "incremental",
            Map.of("cursor", Map.of("field", "updatedAt")),
            List.of("orders")
        );

        ExecutionPlan plan = connector.buildExecutionPlan(context);

        assertThat(plan.engine()).isEqualTo("api-http");
        assertThat(plan.connectorType()).isEqualTo("api");
        assertThat(plan.checkpointPolicy().type()).isEqualTo("cursor");
        assertThat(plan.payload()).containsEntry("sourceDataSourceId", sourceId.toString());
    }

    @Test
    void validate_requiresDataSourceId() {
        SourceConnectorContext context = new SourceConnectorContext(null, "api-task", null, "api", Map.of(), "full_refresh", Map.of(), List.of());

        assertThatThrownBy(() -> connector.validate(context))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("API 数据源不能为空");
    }
}

