package com.yuzhi.dts.ingestion.service.etl.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.config.ApiProperties;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver;
import com.yuzhi.dts.ingestion.service.etl.JdbcMetadataService;
import com.yuzhi.dts.ingestion.service.etl.connector.ExecutionPlan;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiRawLandingServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void land_shouldCommitEachResourceIndependentlyAndContinueAfterFailure() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        PreparedStatement failedInsert = mock(PreparedStatement.class);
        PreparedStatement successfulInsert = mock(PreparedStatement.class);
        Connection ordersConnection = connectionReturning(failedInsert, new ArrayList<>());
        Connection customersConnection = connectionReturning(successfulInsert, new ArrayList<>());
        when(metadataService.openConnection(any())).thenReturn(ordersConnection, customersConnection);
        doThrow(new SQLException("boom")).when(failedInsert).executeUpdate();
        when(successfulInsert.executeUpdate()).thenReturn(1);
        ApiRawLandingService service = service(metadataService);

        ApiRawLandingService.LandingResult result = service.land(
            plan(),
            task(),
            execution(),
            List.of(
                page("orders", 1, objectMapper.readTree("{\"id\":1,\"updatedAt\":\"2026-01-01T00:00:00Z\"}")),
                page("customers", 1, objectMapper.readTree("{\"id\":2,\"updatedAt\":\"2026-01-01T00:01:00Z\"}"))
            )
        );

        assertThat(result.rowsWritten()).isEqualTo(1L);
        assertThat(result.failedResources()).containsOnly("orders");
        verify(ordersConnection).rollback();
        verify(customersConnection).commit();
    }

    @Test
    void land_shouldNotAdvanceCheckpointForFailedResourceWrite() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        PreparedStatement failedInsert = mock(PreparedStatement.class);
        PreparedStatement checkpoint = mock(PreparedStatement.class);
        List<String> preparedSql = new ArrayList<>();
        Connection connection = connectionReturning(failedInsert, checkpoint, preparedSql);
        when(metadataService.openConnection(any())).thenReturn(connection);
        doThrow(new SQLException("boom")).when(failedInsert).executeUpdate();
        ApiRawLandingService service = service(metadataService);

        ApiRawLandingService.LandingResult result = service.land(
            plan(),
            task(),
            execution(),
            List.of(page("orders", 1, objectMapper.readTree("{\"id\":1,\"updatedAt\":\"2026-01-01T00:00:00Z\"}")))
        );

        assertThat(result.failedResources()).containsOnly("orders");
        assertThat(preparedSql).noneSatisfy(sql -> assertThat(sql).contains("dts_api_ingestion_checkpoint", "ON CONFLICT"));
        verify(checkpoint, never()).executeUpdate();
        verify(connection).rollback();
    }

    @Test
    void land_shouldUseHashConflictInsertAndWriteCheckpointForSuccessfulCursorResource() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        PreparedStatement checkpoint = mock(PreparedStatement.class);
        List<String> preparedSql = new ArrayList<>();
        Connection connection = connectionReturning(insert, checkpoint, preparedSql);
        when(metadataService.openConnection(any())).thenReturn(connection);
        when(insert.executeUpdate()).thenReturn(1);
        when(checkpoint.executeUpdate()).thenReturn(1);
        ApiRawLandingService service = service(metadataService);

        ApiRawLandingService.LandingResult result = service.land(
            plan(),
            task(),
            execution(),
            List.of(page("orders", 1, objectMapper.readTree("{\"id\":1,\"updatedAt\":\"2026-01-01T00:00:00Z\"}")))
        );

        assertThat(result.rowsWritten()).isEqualTo(1L);
        assertThat(preparedSql).anySatisfy(sql -> assertThat(sql).contains("_dts_record_hash", "ON CONFLICT"));
        assertThat(preparedSql).anySatisfy(sql -> assertThat(sql).contains("dts_api_ingestion_checkpoint", "ON CONFLICT"));
        verify(connection).commit();
    }

    @Test
    void land_shouldSkipCheckpointForBackfillExecution() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        PreparedStatement checkpoint = mock(PreparedStatement.class);
        List<String> preparedSql = new ArrayList<>();
        Connection connection = connectionReturning(insert, checkpoint, preparedSql);
        when(metadataService.openConnection(any())).thenReturn(connection);
        when(insert.executeUpdate()).thenReturn(1);
        ApiRawLandingService service = service(metadataService);
        IngestionExecution execution = execution();
        execution.setBackfillWindowStart(Instant.parse("2026-01-10T00:00:00Z"));
        execution.setBackfillWindowEnd(Instant.parse("2026-01-11T00:00:00Z"));

        ApiRawLandingService.LandingResult result = service.land(
            plan(),
            task(),
            execution,
            List.of(page("orders", 1, objectMapper.readTree("{\"id\":1,\"updatedAt\":\"2026-01-10T00:00:00Z\"}")))
        );

        assertThat(result.rowsWritten()).isEqualTo(1L);
        assertThat(preparedSql).noneSatisfy(sql -> assertThat(sql).contains("dts_api_ingestion_checkpoint", "ON CONFLICT"));
        verify(checkpoint, never()).executeUpdate();
        verify(connection).commit();
    }

    @Test
    void loadCheckpoints_shouldReturnCursorValuesByResource() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        PreparedStatement select = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(metadataService.openConnection(any())).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(anyString())).thenReturn(select);
        when(select.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("resource_id")).thenReturn("orders", "customers");
        when(resultSet.getString("cursor_value")).thenReturn("2026-01-05T00:00:00Z", "101");
        ApiRawLandingService service = service(metadataService);

        Map<String, String> checkpoints = service.loadCheckpoints(plan(), task());

        assertThat(checkpoints)
            .containsEntry("orders", "2026-01-05T00:00:00Z")
            .containsEntry("customers", "101");
        verify(select).setLong(1, 10L);
        verify(connection).close();
    }

    @Test
    void land_shouldResolveTargetConnectionFromDestinationDataSourceId() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        IngestionSourceResolver sourceResolver = mock(IngestionSourceResolver.class);
        UUID targetDataSourceId = UUID.fromString("00000000-0000-0000-0000-000000000321");
        when(sourceResolver.resolveJdbcInfo(targetDataSourceId)).thenReturn(
            new JdbcMetadataService.JdbcConnectionInfo(
                "jdbc:postgresql://warehouse/ods",
                "ods_user",
                "resolved-secret",
                "org.postgresql.Driver",
                "42.7.3",
                Map.of("sslmode", "require")
            )
        );
        PreparedStatement insert = mock(PreparedStatement.class);
        Connection connection = connectionReturning(insert, new ArrayList<>());
        when(metadataService.openConnection(any())).thenReturn(connection);
        when(insert.executeUpdate()).thenReturn(1);
        ApiRawLandingService service = service(metadataService, sourceResolver);
        IngestionTask task = task();
        task.setDestinationConfig(
            objectMapper.valueToTree(
                Map.of("targetDataSourceId", targetDataSourceId.toString(), "jdbcUrl", "jdbc:postgresql://stale/legacy")
            )
        );

        ApiRawLandingService.LandingResult result = service.land(
            plan(),
            task,
            execution(),
            List.of(page("orders", 1, objectMapper.readTree("{\"id\":1,\"updatedAt\":\"2026-01-01T00:00:00Z\"}")))
        );

        assertThat(result.rowsWritten()).isEqualTo(1L);
        org.mockito.ArgumentCaptor<JdbcMetadataService.JdbcConnectionInfo> infoCaptor = org.mockito.ArgumentCaptor.forClass(
            JdbcMetadataService.JdbcConnectionInfo.class
        );
        verify(metadataService).openConnection(infoCaptor.capture());
        assertThat(infoCaptor.getValue().jdbcUrl()).isEqualTo("jdbc:postgresql://warehouse/ods");
        assertThat(infoCaptor.getValue().username()).isEqualTo("ods_user");
        assertThat(infoCaptor.getValue().password()).isEqualTo("resolved-secret");
        assertThat(infoCaptor.getValue().jdbcProperties()).containsEntry("sslmode", "require");
    }

    @Test
    void land_shouldUseConfiguredTablePrefixAndBatchSizeForFallbackResource() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        List<String> preparedSql = new ArrayList<>();
        Connection connection = connectionReturning(insert, preparedSql);
        when(metadataService.openConnection(any())).thenReturn(connection);
        when(insert.executeBatch()).thenReturn(new int[] { 1, 1 }, new int[] { 1 });
        ApiProperties properties = new ApiProperties();
        properties.setTablePrefix("ods_ext_");
        properties.getLanding().setBatchSize(2);
        ApiRawLandingService service = new ApiRawLandingService(metadataService, objectMapper, properties);

        ApiRawLandingService.LandingResult result = service.land(
            planWithoutResourceMappings(),
            task(),
            execution(),
            List.of(
                page("orders", 1, objectMapper.readTree("{\"id\":1}")),
                page("orders", 1, objectMapper.readTree("{\"id\":2}")),
                page("orders", 1, objectMapper.readTree("{\"id\":3}"))
            )
        );

        assertThat(result.rowsWritten()).isEqualTo(3L);
        assertThat(preparedSql).anySatisfy(sql -> assertThat(sql).contains("\"ods_ext_orders\""));
        verify(insert, times(3)).addBatch();
        verify(insert, times(2)).executeBatch();
    }

    private Connection connectionReturning(PreparedStatement insert, List<String> preparedSql) throws Exception {
        return connectionReturning(insert, mock(PreparedStatement.class), preparedSql);
    }

    private ApiRawLandingService service(JdbcMetadataService metadataService) {
        return service(metadataService, null);
    }

    private ApiRawLandingService service(JdbcMetadataService metadataService, IngestionSourceResolver sourceResolver) {
        ApiProperties properties = new ApiProperties();
        properties.getLanding().setBatchSize(1);
        return new ApiRawLandingService(metadataService, sourceResolver, objectMapper, properties);
    }

    private Connection connectionReturning(
        PreparedStatement insert,
        PreparedStatement checkpoint,
        List<String> preparedSql
    ) throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            preparedSql.add(sql);
            if (sql.contains("dts_api_ingestion_checkpoint")) {
                return checkpoint;
            }
            return insert;
        });
        return connection;
    }

    private ExecutionPlan plan() {
        return new ExecutionPlan(
            "api-http",
            "api",
            "1.2.0",
            null,
            Map.of(
                "sourceConfig",
                Map.of(
                    "sourceSystem",
                    "CRM",
                    "resources",
                    List.of(
                        Map.of(
                            "resourceId",
                            "orders",
                            "targetTable",
                            "ods_api_crm_orders",
                            "cursor",
                            Map.of("type", "datetime", "field", "updatedAt")
                        ),
                        Map.of("resourceId", "customers", "targetTable", "ods_api_crm_customers")
                    )
                )
            ),
            List.of(),
            new ExecutionPlan.CheckpointPolicy("cursor", "updatedAt", "resource_success"),
            Map.of("engine", "api-http")
        );
    }

    private ExecutionPlan planWithoutResourceMappings() {
        return new ExecutionPlan(
            "api-http",
            "api",
            "1.2.0",
            null,
            Map.of("sourceConfig", Map.of("sourceSystem", "CRM")),
            List.of(),
            new ExecutionPlan.CheckpointPolicy("none", null, "resource_success"),
            Map.of("engine", "api-http")
        );
    }

    private IngestionTask task() {
        IngestionTask task = new IngestionTask();
        task.setId(10L);
        task.setName("api-task");
        task.setDestinationConfig(
            objectMapper.valueToTree(
                Map.of("jdbcUrl", "jdbc:postgresql://target/biadmin", "username", "biadmin", "password", "secret")
            )
        );
        return task;
    }

    private IngestionExecution execution() {
        IngestionExecution execution = new IngestionExecution();
        execution.setId(100L);
        execution.setBatchId("batch-1");
        return execution;
    }

    private ApiHttpEngine.ApiHttpResult page(String resourceId, int pageNo, JsonNode record) {
        byte[] body = ("{\"data\":{\"items\":[" + record + "]}}").getBytes(StandardCharsets.UTF_8);
        return new ApiHttpEngine.ApiHttpResult(
            resourceId,
            URI.create("https://api.example.test/" + resourceId),
            200,
            Map.of(),
            body,
            1,
            pageNo,
            List.of(record),
            record.path("updatedAt").asText(null)
        );
    }
}
