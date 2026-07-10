package com.yuzhi.dts.ingestion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.yuzhi.dts.ingestion.config.ApiProperties;
import com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver;
import com.yuzhi.dts.ingestion.service.etl.api.ApiHttpEngine;
import com.yuzhi.dts.ingestion.service.etl.api.ApiHttpException;
import com.yuzhi.dts.ingestion.service.etl.api.ApiAuthProviderRegistry;
import com.yuzhi.dts.ingestion.service.etl.connector.ExecutionPlan;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApiConnectorContractResourceTest {

    private IngestionSourceResolver sourceResolver;
    private ApiHttpEngine apiHttpEngine;
    private ApiConnectorContractResource resource;
    private HttpServer server;
    private ExecutorService serverExecutor;

    @BeforeEach
    void setUp() {
        sourceResolver = mock(IngestionSourceResolver.class);
        apiHttpEngine = mock(ApiHttpEngine.class);
        resource = new ApiConnectorContractResource(new ApiAuthProviderRegistry(), sourceResolver, apiHttpEngine);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
            serverExecutor = null;
        }
    }

    @Test
    void getContract_exposesApiSourceContractAndAuthProviders() {
        Map<String, Object> contract = resource.getContract().getBody();

        assertThat(contract)
            .containsEntry("contractVersion", "1.2.0")
            .containsEntry("connectorType", "api")
            .containsEntry("defaultReaderType", "httpreader");
        @SuppressWarnings("unchecked")
        List<String> syncModes = (List<String>) contract.get("syncModes");
        assertThat(syncModes).contains("full_refresh", "incremental");
        @SuppressWarnings("unchecked")
        List<String> sourceTypes = (List<String>) contract.get("sourceTypes");
        assertThat(sourceTypes).contains("api_http", "https", "httpreader");
        @SuppressWarnings("unchecked")
        Map<String, Object> odsLanding = (Map<String, Object>) contract.get("odsLanding");
        assertThat(odsLanding)
            .containsEntry("mode", "raw_record")
            .containsEntry("rawRecordColumn", "_dts_raw_record")
            .containsEntry("normalizationLayer", "stg");
        @SuppressWarnings("unchecked")
        List<String> technicalColumns = (List<String>) odsLanding.get("technicalColumns");
        assertThat(technicalColumns).contains("_dts_batch_id", "_dts_execution_id", "_dts_cursor_value");
        assertThat((List<?>) contract.get("authProviders")).hasSizeGreaterThanOrEqualTo(5);
        @SuppressWarnings("unchecked")
        List<Object> authProviders = (List<Object>) contract.get("authProviders");
        assertThat(authProviders)
            .anySatisfy(provider -> assertThat(provider).hasFieldOrPropertyWithValue("id", "jwtLogin").hasFieldOrPropertyWithValue("enabled", true))
            .anySatisfy(provider -> assertThat(provider).hasFieldOrPropertyWithValue("id", "customSignature").hasFieldOrPropertyWithValue("enabled", false))
            .anySatisfy(provider -> assertThat(provider).hasFieldOrPropertyWithValue("id", "mtls").hasFieldOrPropertyWithValue("enabled", false));
    }

    @Test
    void testConnection_shouldReturnConnectedSampleSummary() {
        UUID dataSourceId = UUID.randomUUID();
        when(sourceResolver.resolveApiInfo(dataSourceId)).thenReturn((IngestionSourceResolver.ApiConnectionInfo) apiInfo(dataSourceId));
        when(apiHttpEngine.execute(any(ExecutionPlan.class))).thenReturn(
            List.of(
                new ApiHttpEngine.ApiHttpResult(
                    "orders",
                    URI.create("https://crm.example.test/v1/orders"),
                    200,
                    Map.of(),
                    "{}".getBytes(StandardCharsets.UTF_8),
                    1,
                    1,
                    List.of(JsonNodeFactory.instance.objectNode().put("id", 1)),
                    null
                )
            )
        );

        Map<String, Object> body = resource
            .testConnection(
                new ApiConnectorContractResource.ApiConnectionTestRequest(
                    dataSourceId,
                    Map.<String, Object>of("resourceId", "orders", "path", "/v1/orders", "recordPath", "$.data.items"),
                    Map.<String, Object>of("maxResponseBytes", 1024)
                )
            )
            .getBody();

        assertThat(body)
            .containsEntry("connected", true)
            .containsEntry("httpStatus", 200)
            .containsEntry("authOk", true)
            .containsEntry("sampleCount", 1)
            .containsEntry("recordPathResolved", true);
        assertThat((List<?>) body.get("sampleRecords")).hasSize(1);
        ArgumentCaptor<ExecutionPlan> planCaptor = ArgumentCaptor.forClass(ExecutionPlan.class);
        verify(apiHttpEngine).execute(planCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceConfig = (Map<String, Object>) planCaptor.getValue().payload().get("sourceConfig");
        assertThat(sourceConfig)
            .containsEntry("baseUrl", "https://crm.example.test")
            .containsKey("secrets");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) sourceConfig.get("resources");
        assertThat(resources.get(0)).containsEntry("path", "/v1/orders");
        @SuppressWarnings("unchecked")
        Map<String, Object> pagination = (Map<String, Object>) resources.get(0).get("pagination");
        assertThat(pagination).containsEntry("maxPages", 1);
    }

    @Test
    void testConnection_shouldReturnAuthFailureInsteadOfThrowing() {
        UUID dataSourceId = UUID.randomUUID();
        when(sourceResolver.resolveApiInfo(dataSourceId)).thenReturn((IngestionSourceResolver.ApiConnectionInfo) apiInfo(dataSourceId));
        when(apiHttpEngine.execute(any(ExecutionPlan.class))).thenThrow(
            new ApiHttpException("API_RUNTIME_AUTH", "API 鉴权失败: HTTP 401", 401, 1)
        );

        Map<String, Object> body = resource
            .testConnection(
                new ApiConnectorContractResource.ApiConnectionTestRequest(
                    dataSourceId,
                    Map.<String, Object>of("resourceId", "orders", "path", "/v1/orders"),
                    Map.of()
                )
            )
            .getBody();

        assertThat(body)
            .containsEntry("connected", false)
            .containsEntry("httpStatus", 401)
            .containsEntry("authOk", false)
            .containsEntry("failureCategory", "PERMISSION_ERROR");
        assertThat(body.get("advice")).isInstanceOf(String.class);
    }

    @Test
    void testConnection_shouldProbeRawSourceConfigAndSecretsBeforeSave() {
        when(apiHttpEngine.execute(any(ExecutionPlan.class))).thenReturn(
            List.of(
                new ApiHttpEngine.ApiHttpResult(
                    "orders",
                    URI.create("https://crm.example.test/v1/orders"),
                    200,
                    Map.of(),
                    "{}".getBytes(StandardCharsets.UTF_8),
                    1,
                    1,
                    List.of(JsonNodeFactory.instance.objectNode().put("id", 1)),
                    null
                )
            )
        );

        Map<String, Object> body = resource
            .testConnection(
                new ApiConnectorContractResource.ApiConnectionTestRequest(
                    null,
                    Map.<String, Object>of("resourceId", "orders", "path", "/v1/orders", "recordPath", "$.data.items"),
                    Map.of(),
                    Map.<String, Object>of(
                        "baseUrl",
                        "https://crm.example.test",
                        "auth",
                        Map.of("provider", "bearerToken", "tokenRef", "accessToken"),
                        "defaultHeaders",
                        Map.of("X-Tenant", "demo")
                    ),
                    Map.<String, Object>of("accessToken", "token-123")
                )
            )
            .getBody();

        assertThat(body)
            .containsEntry("connected", true)
            .containsEntry("sampleCount", 1)
            .containsEntry("recordPathResolved", true);
        verify(sourceResolver, org.mockito.Mockito.never()).resolveApiInfo(any(UUID.class));
        ArgumentCaptor<ExecutionPlan> planCaptor = ArgumentCaptor.forClass(ExecutionPlan.class);
        verify(apiHttpEngine).execute(planCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceConfig = (Map<String, Object>) planCaptor.getValue().payload().get("sourceConfig");
        assertThat(sourceConfig)
            .containsEntry("baseUrl", "https://crm.example.test")
            .containsEntry("defaultHeaders", Map.of("X-Tenant", "demo"))
            .containsEntry("secrets", Map.of("accessToken", "token-123"));
        assertThat(sourceConfig.get("auth")).isEqualTo(Map.of("provider", "bearerToken", "tokenRef", "accessToken"));
    }

    @Test
    void testConnection_shouldReturnNetworkFailureInsteadOfThrowing() {
        UUID dataSourceId = UUID.randomUUID();
        when(sourceResolver.resolveApiInfo(dataSourceId)).thenReturn((IngestionSourceResolver.ApiConnectionInfo) apiInfo(dataSourceId));
        when(apiHttpEngine.execute(any(ExecutionPlan.class))).thenThrow(
            new ApiHttpException("API_RUNTIME_NETWORK", "API_RUNTIME_NETWORK: connect timed out", null, 1)
        );

        Map<String, Object> body = resource
            .testConnection(
                new ApiConnectorContractResource.ApiConnectionTestRequest(
                    dataSourceId,
                    Map.<String, Object>of("resourceId", "orders", "path", "/v1/orders"),
                    Map.of()
                )
            )
            .getBody();

        assertThat(body)
            .containsEntry("connected", false)
            .containsEntry("authOk", true)
            .containsEntry("sampleCount", 0)
            .containsEntry("recordPathResolved", false)
            .containsEntry("failureCategory", "CONNECTION_ERROR")
            .containsEntry("errorCode", "API_RUNTIME_NETWORK");
        assertThat(body.get("advice")).isInstanceOf(String.class);
    }

    @Test
    void testConnection_shouldNormalizeSavedNestedRuntimeConfigBeforeExecution() {
        UUID dataSourceId = UUID.randomUUID();
        when(sourceResolver.resolveApiInfo(dataSourceId)).thenReturn(
            new IngestionSourceResolver.ApiConnectionInfo(
                dataSourceId,
                "CRM API",
                "https://crm.example.test",
                "none",
                Map.of("provider", "none"),
                Map.of(),
                Map.of(
                    "connectorType",
                    "api",
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
                Map.of()
            )
        );
        when(apiHttpEngine.execute(any(ExecutionPlan.class))).thenReturn(List.of());

        resource.testConnection(
            new ApiConnectorContractResource.ApiConnectionTestRequest(dataSourceId, Map.of(), Map.of())
        );

        ArgumentCaptor<ExecutionPlan> planCaptor = ArgumentCaptor.forClass(ExecutionPlan.class);
        verify(apiHttpEngine).execute(planCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceConfig = (Map<String, Object>) planCaptor.getValue().payload().get("sourceConfig");
        assertThat(sourceConfig)
            .containsEntry("rateLimit", Map.of("requestsPerSecond", 3))
            .containsEntry("tls", Map.of("verifyTls", false));
        @SuppressWarnings("unchecked")
        Map<String, Object> requestPolicy = (Map<String, Object>) sourceConfig.get("requestPolicy");
        assertThat(requestPolicy)
            .containsEntry("allowHttp", true)
            .containsEntry("readTimeoutMillis", 9000)
            .containsKey("connectTimeoutMillis");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) sourceConfig.get("resources");
        assertThat(resources).hasSize(1);
        assertThat(resources.get(0)).containsEntry("path", "/v1/orders");
    }

    @Test
    void testConnection_shouldCallMockApiWithRawConfigAndReturnSample() throws Exception {
        startMockApi(exchange -> {
            if (!"Bearer token-123".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                write(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1,\"name\":\"order-1\"}]}}");
        });
        resource = new ApiConnectorContractResource(new ApiAuthProviderRegistry(), sourceResolver, realHttpEngine());

        Map<String, Object> body = resource
            .testConnection(
                new ApiConnectorContractResource.ApiConnectionTestRequest(
                    null,
                    Map.<String, Object>of("resourceId", "orders", "path", "/v1/orders", "recordPath", "$.data.items"),
                    Map.of(),
                    Map.<String, Object>of(
                        "baseUrl",
                        mockApiBaseUrl(),
                        "auth",
                        Map.of("provider", "bearerToken", "tokenRef", "accessToken")
                    ),
                    Map.<String, Object>of("accessToken", "token-123")
                )
            )
            .getBody();

        assertThat(body)
            .containsEntry("connected", true)
            .containsEntry("httpStatus", 200)
            .containsEntry("authOk", true)
            .containsEntry("sampleCount", 1)
            .containsEntry("recordPathResolved", true);
        assertThat((List<?>) body.get("sampleRecords")).hasSize(1);
    }

    @Test
    void testConnection_shouldAcceptFrontendNestedApiKeyAuthConfig() throws Exception {
        startMockApi(exchange -> {
            if (!"key-123".equals(exchange.getRequestHeaders().getFirst("X-API-Key"))) {
                write(exchange, 401, "{\"error\":\"missing api key\"}");
                return;
            }
            write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1}]}}");
        });
        resource = new ApiConnectorContractResource(new ApiAuthProviderRegistry(), sourceResolver, realHttpEngine());

        Map<String, Object> body = resource
            .testConnection(
                new ApiConnectorContractResource.ApiConnectionTestRequest(
                    null,
                    Map.<String, Object>of("resourceId", "orders", "path", "/v1/orders", "recordPath", "$.data.items"),
                    Map.of(),
                    Map.<String, Object>of(
                        "baseUrl",
                        mockApiBaseUrl(),
                        "auth",
                        Map.of(
                            "provider",
                            "apiKey",
                            "config",
                            Map.of("name", "X-API-Key", "location", "header"),
                            "secretRefs",
                            Map.of("value", "value")
                        )
                    ),
                    Map.<String, Object>of("value", "key-123")
                )
            )
            .getBody();

        assertThat(body)
            .containsEntry("connected", true)
            .containsEntry("httpStatus", 200)
            .containsEntry("authOk", true)
            .containsEntry("sampleCount", 1);
    }

    @Test
    void testConnection_shouldAcceptFrontendNestedBasicAuthConfig() throws Exception {
        startMockApi(exchange -> {
            if (!"Basic YXBpLXVzZXI6YXBpLXBhc3M=".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                write(exchange, 401, "{\"error\":\"missing basic auth\"}");
                return;
            }
            write(exchange, 200, "{\"data\":{\"items\":[{\"id\":1}]}}");
        });
        resource = new ApiConnectorContractResource(new ApiAuthProviderRegistry(), sourceResolver, realHttpEngine());

        Map<String, Object> body = resource
            .testConnection(
                new ApiConnectorContractResource.ApiConnectionTestRequest(
                    null,
                    Map.<String, Object>of("resourceId", "orders", "path", "/v1/orders", "recordPath", "$.data.items"),
                    Map.of(),
                    Map.<String, Object>of(
                        "baseUrl",
                        mockApiBaseUrl(),
                        "auth",
                        Map.of(
                            "provider",
                            "basic",
                            "config",
                            Map.of("username", "api-user"),
                            "secretRefs",
                            Map.of("password", "password")
                        )
                    ),
                    Map.<String, Object>of("password", "api-pass")
                )
            )
            .getBody();

        assertThat(body)
            .containsEntry("connected", true)
            .containsEntry("httpStatus", 200)
            .containsEntry("authOk", true)
            .containsEntry("sampleCount", 1);
    }

    @Test
    void testConnection_shouldClassifyMockApiAuthFailureWithRealEngine() throws Exception {
        startMockApi(exchange -> write(exchange, 401, "{\"error\":\"unauthorized\"}"));
        resource = new ApiConnectorContractResource(new ApiAuthProviderRegistry(), sourceResolver, realHttpEngine());

        Map<String, Object> body = resource
            .testConnection(
                new ApiConnectorContractResource.ApiConnectionTestRequest(
                    null,
                    Map.<String, Object>of("resourceId", "orders", "path", "/v1/orders", "recordPath", "$.data.items"),
                    Map.of(),
                    Map.<String, Object>of(
                        "baseUrl",
                        mockApiBaseUrl(),
                        "auth",
                        Map.of("provider", "bearerToken", "tokenRef", "accessToken")
                    ),
                    Map.<String, Object>of("accessToken", "wrong-token")
                )
            )
            .getBody();

        assertThat(body)
            .containsEntry("connected", false)
            .containsEntry("httpStatus", 401)
            .containsEntry("authOk", false)
            .containsEntry("sampleCount", 0)
            .containsEntry("recordPathResolved", false)
            .containsEntry("failureCategory", "PERMISSION_ERROR")
            .containsEntry("errorCode", "API_RUNTIME_AUTH");
    }

    private Object apiInfo(UUID dataSourceId) {
        return new IngestionSourceResolver.ApiConnectionInfo(
            dataSourceId,
            "CRM API",
            "https://crm.example.test",
            "bearerToken",
            Map.of("provider", "bearerToken", "tokenRef", "accessToken"),
            Map.of("Accept", "application/json"),
            Map.of("connectorType", "api"),
            Map.of("accessToken", "token-123")
        );
    }

    private ApiHttpEngine realHttpEngine() {
        ApiProperties properties = new ApiProperties();
        properties.setAllowHttp(true);
        properties.setAllowedHosts(List.of("127.0.0.1"));
        return new ApiHttpEngine(properties);
    }

    private String mockApiBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void startMockApi(MockApiHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler::handle);
        serverExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(serverExecutor);
        server.start();
    }

    private void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface MockApiHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
