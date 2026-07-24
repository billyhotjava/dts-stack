package com.yuzhi.dts.ingestion.service.etl.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.yuzhi.dts.ingestion.config.ApiProperties;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.etl.connector.ExecutionPlan;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ApiIngestionExecutorTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void execute_shouldRejectConcurrentExecutionForSameTask() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ApiIngestionExecutor executor = new ApiIngestionExecutor((plan, context) -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return ApiIngestionResult.success(1L, 1L, Map.of("stream", "orders"));
        });
        IngestionTask task = task(10L);
        ExecutionPlan plan = plan();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ApiIngestionResult> first = pool.submit(() -> executor.execute(plan, task, execution(100L)));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> executor.execute(plan, task, execution(101L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("正在执行");

            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).rowsWritten()).isEqualTo(1L);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void execute_shouldTimeoutAndInterruptLongRunningPlan() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicBoolean returnedAfterInterrupt = new AtomicBoolean(false);
        ApiProperties properties = new ApiProperties();
        properties.setExecutionTimeout(Duration.ofMillis(50));
        ApiIngestionExecutor executor = new ApiIngestionExecutor(
            (plan, context) -> {
                entered.countDown();
                try {
                    Thread.sleep(5_000);
                    return ApiIngestionResult.success(1L, 1L, Map.of());
                } catch (InterruptedException ex) {
                    interrupted.countDown();
                    returnedAfterInterrupt.set(true);
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            },
            null,
            properties
        );

        assertThatThrownBy(() -> executor.execute(plan(), task(10L), execution(100L)))
            .isInstanceOf(ApiHttpException.class)
            .satisfies(error -> assertThat(((ApiHttpException) error).getCode()).isEqualTo("API_RUNTIME_TIMEOUT"))
            .hasMessageContaining("API 入湖执行超时");

        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(returnedAfterInterrupt).isTrue();
    }

    @Test
    void execute_shouldReportRowsReadFromHttpRecordsBeforeRawLandingIsImplemented() throws Exception {
        startServer(exchange -> write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1},{\"id\":2}]}}"));
        ApiIngestionExecutor executor = new ApiIngestionExecutor(engine());

        ApiIngestionResult result = executor.execute(httpPlan(), task(10L), execution(100L));

        assertThat(result.rowsRead()).isEqualTo(2L);
        assertThat(result.rowsWritten()).isZero();
        assertThat(result.metrics())
            .containsEntry("rowsRead", 2L)
            .containsEntry("resources", 1)
            .containsEntry("resourceCount", 1)
            .containsEntry("pages", 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resourceStats = (List<Map<String, Object>>) result.metrics().get("resourceStats");
        assertThat(resourceStats).hasSize(1);
        assertThat(resourceStats.get(0))
            .containsEntry("resourceId", "orders")
            .containsEntry("rowsRead", 2L)
            .containsEntry("pageCount", 1L)
            .containsEntry("httpStatus", 200);
    }

    @Test
    void execute_shouldRunRawLandingAfterHttpFetchAndReportRowsWritten() throws Exception {
        startServer(exchange -> write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1},{\"id\":2}]}}"));
        ApiRawLandingService rawLandingService = mock(ApiRawLandingService.class);
        when(rawLandingService.land(any(), any(), any(), any()))
            .thenReturn(new ApiRawLandingService.LandingResult(2L, List.of(), Map.of("orders.rowsWritten", 2L)));
        ApiIngestionExecutor executor = new ApiIngestionExecutor(engine(), rawLandingService);

        ApiIngestionResult result = executor.execute(httpPlan(), task(10L), execution(100L));

        assertThat(result.rowsRead()).isEqualTo(2L);
        assertThat(result.rowsWritten()).isEqualTo(2L);
        assertThat(result.metrics()).containsEntry("rowsWritten", 2L).containsEntry("orders.rowsWritten", 2L);
        verify(rawLandingService).land(any(), any(), any(), any());
    }

    @Test
    void execute_shouldMarkFailedResourceWhenRawLandingReportsPartialFailure() throws Exception {
        startServer(exchange -> write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1}]}}"));
        ApiRawLandingService rawLandingService = mock(ApiRawLandingService.class);
        when(rawLandingService.land(any(), any(), any(), any()))
            .thenReturn(new ApiRawLandingService.LandingResult(0L, List.of("orders"), Map.of("orders.error", "ddl failed")));
        ApiIngestionExecutor executor = new ApiIngestionExecutor(engine(), rawLandingService);

        ApiIngestionResult result = executor.execute(httpPlan(), task(10L), execution(100L));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("orders");
        assertThat(result.metrics()).containsEntry("failedResources", List.of("orders"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resourceStats = (List<Map<String, Object>>) result.metrics().get("resourceStats");
        assertThat(resourceStats).hasSize(1);
        assertThat(resourceStats.get(0))
            .containsEntry("resourceId", "orders")
            .containsEntry("status", "FAILED")
            .containsEntry("rowsRead", 1L);
    }

    @Test
    void execute_shouldUseBackfillWindowInsteadOfCursorLookbackForHttpQuery() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        startServer(exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            write(exchange, 200, "{\"data\":{\"items\":[]}}");
        });
        ApiRawLandingService rawLandingService = mock(ApiRawLandingService.class);
        when(rawLandingService.land(any(), any(), any(), any()))
            .thenReturn(new ApiRawLandingService.LandingResult(0L, List.of(), Map.of()));
        ApiIngestionExecutor executor = new ApiIngestionExecutor(engine(), rawLandingService);
        IngestionExecution execution = execution(100L);
        execution.setBackfillWindowStart(Instant.parse("2026-01-10T00:00:00Z"));
        execution.setBackfillWindowEnd(Instant.parse("2026-01-11T00:00:00Z"));

        ApiIngestionResult result = executor.execute(backfillHttpPlan(), task(10L), execution);

        assertThat(result.success()).isTrue();
        assertThat(query.get())
            .contains("updatedAfter=2026-01-10T00%3A00%3A00Z", "updatedBefore=2026-01-11T00%3A00%3A00Z")
            .doesNotContain("2025-12-31");
        verify(rawLandingService, never()).loadCheckpoints(any(), any());
    }

    @Test
    void execute_shouldUsePersistedCheckpointWithLookbackForIncrementalHttpQuery() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        startServer(exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            write(exchange, 200, "{\"data\":{\"items\":[]}}");
        });
        ApiRawLandingService rawLandingService = mock(ApiRawLandingService.class);
        when(rawLandingService.loadCheckpoints(any(), any())).thenReturn(Map.of("orders", "2026-01-05T00:00:00Z"));
        when(rawLandingService.land(any(), any(), any(), any()))
            .thenReturn(new ApiRawLandingService.LandingResult(0L, List.of(), Map.of()));
        ApiIngestionExecutor executor = new ApiIngestionExecutor(engine(), rawLandingService);

        ApiIngestionResult result = executor.execute(backfillHttpPlan(), task(10L), execution(100L));

        assertThat(result.success()).isTrue();
        assertThat(query.get())
            .contains("updatedAfter=2026-01-04T23%3A55%3A00Z")
            .doesNotContain("2025-12-31");
        verify(rawLandingService).loadCheckpoints(any(), any());
    }

    @Test
    void execute_shouldSnapshotApiLineageAndPublishSuccessMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ApiIngestionExecutor executor = new ApiIngestionExecutor(
            (plan, context) -> ApiIngestionResult.success(
                3L,
                2L,
                Map.of(
                    "pages",
                    2,
                    "resourceStats",
                    List.of(
                        Map.of(
                            "resourceId",
                            "orders",
                            "rowsRead",
                            3L,
                            "pageCount",
                            2L,
                            "cursorValue",
                            "2026-01-10T00:00:00Z",
                            "status",
                            "SUCCESS",
                            "httpStatus",
                            200
                        )
                    )
                )
            ),
            meterRegistry
        );
        IngestionTask task = task(10L);
        task.setSourceDataSourceId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        IngestionExecution execution = execution(100L);

        ApiIngestionResult result = executor.execute(apiLineagePlan(), task, execution);

        assertThat(result.success()).isTrue();
        JsonNode source = execution.getSourceTables().get(0);
        assertThat(source.get("namespace").asText()).isEqualTo("api:00000000-0000-0000-0000-000000000001");
        assertThat(source.get("name").asText()).isEqualTo("orders");
        assertThat(source.get("sourceType").asText()).isEqualTo("api");
        assertThat(source.get("rowsRead").asLong()).isEqualTo(3L);
        assertThat(source.get("pageCount").asLong()).isEqualTo(2L);
        assertThat(source.get("cursorValue").asText()).isEqualTo("2026-01-10T00:00:00Z");
        assertThat(source.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(source.get("httpStatus").asLong()).isEqualTo(200L);
        JsonNode target = execution.getTargetTables().get(0);
        assertThat(target.get("namespace").asText()).isEqualTo("ods");
        assertThat(target.get("name").asText()).isEqualTo("ods_api_crm_orders");
        assertThat(meterRegistry.find("dts_api_ingestion_records").tag("taskId", "10").counter().count()).isEqualTo(3.0);
        assertThat(meterRegistry.find("dts_api_ingestion_pages").tag("taskId", "10").counter().count()).isEqualTo(2.0);
        assertThat(meterRegistry.find("dts_api_ingestion_duration").tag("taskId", "10").timer().count()).isEqualTo(1L);
    }

    @Test
    void execute_shouldEmitModelableLandingEvidenceOnlyAfterSuccessfulLanding() {
        ApiIngestionExecutor executor = new ApiIngestionExecutor(
            (plan, context) -> ApiIngestionResult.success(
                5L,
                4L,
                Map.of(
                    "orders.rowsWritten",
                    4L,
                    "resourceStats",
                    List.of(
                        Map.of(
                            "resourceId",
                            "orders",
                            "rowsRead",
                            5L,
                            "cursorValue",
                            "2026-01-10T00:00:00Z",
                            "status",
                            "SUCCESS"
                        )
                    )
                )
            )
        );
        IngestionExecution execution = execution(100L);
        execution.setExecutionId("api-run-100");

        executor.execute(apiLineagePlan(), task(10L), execution);

        JsonNode target = execution.getTargetTables().get(0);
        assertThat(target.get("qualifiedName").asText()).isEqualTo("ods.ods_api_crm_orders");
        assertThat(target.get("resourceId").asText()).isEqualTo("orders");
        assertThat(target.get("sourceType").asText()).isEqualTo("api");
        assertThat(target.get("landingMode").asText()).isEqualTo("raw_ods");
        assertThat(target.get("rawRecordColumn").asText()).isEqualTo(ApiSourceContracts.defaultLandingPolicy().rawRecordColumn());
        assertThat(target.get("technicalColumns")).hasSize(ApiSourceContracts.defaultLandingPolicy().technicalColumns().size());
        assertThat(target.get("cursorValue").asText()).isEqualTo("2026-01-10T00:00:00Z");
        assertThat(target.get("rowsWritten").asLong()).isEqualTo(4L);
        assertThat(target.get("executionId").asText()).isEqualTo("api-run-100");
        assertThat(target.get("configChecksum").asText()).startsWith("sha256:");
        assertThat(target.get("landingStatus").asText()).isEqualTo("SUCCESS");
        assertThat(target.get("fieldSnapshot")).isNotEmpty();
        assertThat(target.get("fieldSnapshotChecksum").asText()).matches("^sha256:[0-9a-f]{64}$");
    }

    @Test
    void execute_shouldNotEmitModelableEvidenceWhenLandingFails() {
        ApiIngestionExecutor executor = new ApiIngestionExecutor(
            (plan, context) -> ApiIngestionResult.failed("orders landing failed", Map.of("orders.error", "ddl failed"))
        );
        IngestionExecution execution = execution(100L);

        ApiIngestionResult result = executor.execute(apiLineagePlan(), task(10L), execution);

        assertThat(result.success()).isFalse();
        assertThat(execution.getSourceTables()).isNull();
        assertThat(execution.getTargetTables()).isNull();
    }

    @Test
    void execute_shouldPublishFailureMetricWithFailureCategory() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ApiIngestionExecutor executor = new ApiIngestionExecutor(
            (plan, context) -> {
                throw new ApiHttpException("API_RUNTIME_AUTH", "HTTP 401", 401, 1);
            },
            meterRegistry
        );

        assertThatThrownBy(() -> executor.execute(apiLineagePlan(), task(10L), execution(100L)))
            .isInstanceOf(ApiHttpException.class);

        assertThat(
            meterRegistry.find("dts_api_ingestion_failures")
                .tag("taskId", "10")
                .tag("category", "PERMISSION_ERROR")
                .counter()
                .count()
        ).isEqualTo(1.0);
    }

    private IngestionTask task(Long id) {
        IngestionTask task = new IngestionTask();
        task.setId(id);
        task.setName("api-task");
        task.setSourceType("httpreader");
        return task;
    }

    private IngestionExecution execution(Long id) {
        IngestionExecution execution = new IngestionExecution();
        execution.setId(id);
        return execution;
    }

    private ExecutionPlan plan() {
        return new ExecutionPlan(
            "api-http",
            "api",
            "1.2.0",
            null,
            Map.of(),
            List.of(),
            new ExecutionPlan.CheckpointPolicy("none", null, "task_success"),
            Map.of("engine", "api-http")
        );
    }

    private ExecutionPlan httpPlan() {
        return new ExecutionPlan(
            "api-http",
            "api",
            "1.2.0",
            null,
            Map.of(
                "sourceConfig",
                Map.of(
                    "baseUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "resources",
                    List.of(Map.of("resourceId", "orders", "path", "/orders", "recordPath", "$.data.items"))
                )
            ),
            List.of(),
            new ExecutionPlan.CheckpointPolicy("none", null, "task_success"),
            Map.of("engine", "api-http")
        );
    }

    private ExecutionPlan backfillHttpPlan() {
        return new ExecutionPlan(
            "api-http",
            "api",
            "1.2.0",
            null,
            Map.of(
                "sourceConfig",
                Map.of(
                    "baseUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "resources",
                    List.of(
                        Map.of(
                            "resourceId",
                            "orders",
                            "path",
                            "/orders",
                            "recordPath",
                            "$.data.items",
                            "cursor",
                            Map.of(
                                "type",
                                "datetime",
                                "field",
                                "updatedAt",
                                "injectInto",
                                "query",
                                "parameterName",
                                "updatedAfter",
                                "endParameterName",
                                "updatedBefore",
                                "initialValue",
                                "2026-01-01T00:00:00Z",
                                "lookbackSeconds",
                                300
                            )
                        )
                    )
                )
            ),
            List.of(),
            new ExecutionPlan.CheckpointPolicy("cursor", "updatedAt", "task_success"),
            Map.of("engine", "api-http")
        );
    }

    private ExecutionPlan apiLineagePlan() {
        return new ExecutionPlan(
            "api-http",
            "api",
            "1.2.0",
            null,
            Map.of(
                "sourceDataSourceId",
                "00000000-0000-0000-0000-000000000001",
                "odsMappings",
                List.of(
                    Map.of(
                        "source",
                        "orders",
                        "target",
                        "ods.ods_api_crm_orders",
                        "landingMode",
                        "raw_record"
                    )
                )
            ),
            List.of(),
            new ExecutionPlan.CheckpointPolicy("cursor", "updatedAt", "task_success"),
            Map.of("connectorType", "api", "engine", "api-http")
        );
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
    }

    private ApiHttpEngine engine() {
        ApiProperties properties = new ApiProperties();
        properties.setAllowHttp(true);
        properties.setAllowedHosts(List.of("127.0.0.1"));
        return new ApiHttpEngine(duration -> {}, properties);
    }

    private void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
