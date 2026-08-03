package com.yuzhi.dts.platform.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.yuzhi.dts.platform.config.DtsIngestionProperties;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@ExtendWith(OutputCaptureExtension.class)
class IngestionServiceClientTest {

    private IngestionServiceClient client;
    private MockRestServiceServer longServer;

    @BeforeEach
    void setUp() {
        DtsIngestionProperties properties = new DtsIngestionProperties();
        properties.setBaseUrl("http://ingestion.test");
        properties.setServiceName("dts-platform");

        client = new IngestionServiceClient(new RestTemplateBuilder(), properties);
        RestTemplate longRestTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "longRestTemplate");
        longServer = MockRestServiceServer.bindTo(longRestTemplate).ignoreExpectOrder(true).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldTreatAcceptedAsyncExecutionPayloadAsSuccessResponse() {
        longServer
            .expect(requestTo("http://ingestion.test/api/ingestion/tasks/7/execute/async"))
            .andExpect(method(POST))
            .andRespond(
                withStatus(HttpStatus.ACCEPTED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        """
                        {
                          "taskId": 7,
                          "taskName": "dm8test",
                          "status": "submitted",
                          "async": true,
                          "pollIntervalMs": 15000,
                          "message": "任务已提交，正在后台触发执行"
                        }
                        """
                    )
            );

        ApiResponse<Map<String, Object>> response = client.executeTaskAsync(7L);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("任务已提交，正在后台触发执行");
        assertThat(response.getData()).containsEntry("taskId", 7);
        assertThat(response.getData()).containsEntry("status", "submitted");
        assertThat(response.getData()).containsEntry("async", true);
        longServer.verify();
    }

    @Test
    void shouldForwardCurrentUserContextToIngestion() {
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "xiezm",
                    null,
                    java.util.List.of(
                        new SimpleGrantedAuthority("ROLE_INST_DATA_OWNER"),
                        new SimpleGrantedAuthority("ROLE_EMPLOYEE")
                    )
                )
            );
        longServer
            .expect(requestTo("http://ingestion.test/api/ingestion/tasks"))
            .andExpect(method(POST))
            .andExpect(header("X-DTS-Service", "dts-platform"))
            .andExpect(headerDoesNotExist("X-DTS-Service-Token"))
            .andExpect(header("X-DTS-User", "xiezm"))
            .andExpect(header("X-DTS-Roles", "ROLE_INST_DATA_OWNER,ROLE_EMPLOYEE"))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":200,\"message\":\"ok\",\"data\":{\"task\":{\"id\":11}}}")
            );

        ApiResponse<Map<String, Object>> response = client.createIngestionTask(Map.of("name", "demo"));

        assertThat(response.getStatus()).isEqualTo(200);
        longServer.verify();
    }

    @Test
    void shouldSendConfiguredServiceTokenOnEveryIngestionRequest() {
        DtsIngestionProperties properties = new DtsIngestionProperties();
        properties.setBaseUrl("http://ingestion.test");
        properties.setServiceName("dts-platform");
        properties.setServiceToken("platform-to-ingestion-secret");
        IngestionServiceClient tokenClient = new IngestionServiceClient(new RestTemplateBuilder(), properties);
        RestTemplate tokenRestTemplate = (RestTemplate) ReflectionTestUtils.getField(tokenClient, "restTemplate");
        MockRestServiceServer tokenServer = MockRestServiceServer.bindTo(tokenRestTemplate).build();
        tokenServer
            .expect(requestTo("http://ingestion.test/api/ingestion/templates"))
            .andExpect(method(GET))
            .andExpect(header("X-DTS-Service-Token", "platform-to-ingestion-secret"))
            .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("[]"));

        tokenClient.listTemplates();

        tokenServer.verify();
    }

    @Test
    void shouldProxyApiConnectionTestToIngestionApiEndpoint() {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        server
            .expect(requestTo("http://ingestion.test/api/ingestion/api/test-connection"))
            .andExpect(method(POST))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"connected\":true,\"httpStatus\":200,\"authOk\":true,\"sampleCount\":1}")
            );

        ApiResponse<Object> response = client.testApiConnection(Map.of("dataSourceId", "11111111-2222-3333-4444-555555555555"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData()).isInstanceOf(Map.class);
        server.verify();
    }

    @Test
    void shouldPreserveSafeTableDiscoveryFailureEnvelope() {
        longServer
            .expect(requestTo("http://ingestion.test/api/ingestion/metadata/tables"))
            .andExpect(method(POST))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        "{" +
                        "\"status\":502," +
                        "\"message\":\"数据库认证失败，请检查用户名、密码及来源 IP 授权\"," +
                        "\"code\":\"JDBC_METADATA_AUTH_FAILED\"," +
                        "\"data\":null" +
                        "}"
                    )
            );

        ApiResponse<Object> response = client.discoverTables(Map.of("source", Map.of("dataSourceId", UUID.randomUUID())));

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getMessage()).isEqualTo("数据库认证失败，请检查用户名、密码及来源 IP 授权");
        assertThat(response.getCode()).isEqualTo("JDBC_METADATA_AUTH_FAILED");
        assertThat(response.getData()).isNull();
        longServer.verify();
    }

    @Test
    void shouldProxyAccessDefaultsAndTaskRevisionEndpointsWithoutChangingTheirContract() {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        server
            .expect(requestTo("http://ingestion.test/api/ingestion/access/default-policy"))
            .andExpect(method(GET))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"policyKey\":\"INGESTION_DEFAULT\",\"version\":3,\"status\":\"ACTIVE\",\"defaults\":{}}")
            );
        server
            .expect(requestTo("http://ingestion.test/api/ingestion/tasks/7/revisions"))
            .andExpect(method(GET))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("[{\"revisionNumber\":2,\"revisionState\":\"ACTIVE\",\"sourceKind\":\"DATABASE\"}]")
            );
        server
            .expect(requestTo("http://ingestion.test/api/ingestion/tasks/7/effective-config"))
            .andExpect(method(GET))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"taskId\":7,\"revisionNumber\":3,\"revisionState\":\"ACTIVE\",\"sourceKind\":\"DATABASE\",\"effectiveConfig\":{}}")
            );

        ApiResponse<Object> defaults = client.getAccessDefaultPolicy();
        ApiResponse<Object> revisions = client.getTaskRevisions(7L);
        ApiResponse<Object> effective = client.getTaskEffectiveConfig(7L);
        assertThat(defaults.getStatus()).isEqualTo(200);
        assertThat(defaults.getData()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsEntry("policyKey", "INGESTION_DEFAULT")
            .containsEntry("version", 3);
        assertThat(revisions.getStatus()).isEqualTo(200);
        assertThat(revisions.getData()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
            .hasSize(1);
        assertThat(effective.getStatus()).isEqualTo(200);
        assertThat(effective.getData()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsEntry("revisionNumber", 3)
            .containsEntry("sourceKind", "DATABASE");
        server.verify();
    }

    @Test
    void shouldPreserveEffectiveConfigConflictStatusFromIngestion() {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server
            .expect(requestTo("http://ingestion.test/api/ingestion/tasks/7/effective-config"))
            .andExpect(method(GET))
            .andRespond(
                withStatus(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":409,\"message\":\"任务尚未生成可用的配置版本\"}")
            );

        ApiResponse<Object> response = client.getTaskEffectiveConfig(7L);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getData()).isNull();
        server.verify();
    }

    @Test
    void downstreamErrorLogUsesStableMetadataAndNeverIncludesResponseSecrets(CapturedOutput output) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server
            .expect(requestTo("http://ingestion.test/api/ingestion/api/test-connection"))
            .andExpect(method(POST))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .header("X-Request-Id", "req-rollback-42")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        "{\"code\":\"REMOTE_REJECTED\",\"status\":400," +
                        "\"message\":\"password=downstream-secret\",\"token\":\"raw-token\"}"
                    )
            );

        ApiResponse<Object> response = client.testApiConnection(Map.of("dataSourceId", UUID.randomUUID().toString()));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("ingestion service error");
        assertThat(response.getData()).isNull();
        assertThat(output.getAll())
            .contains("code=INGESTION_HTTP_ERROR", "status=400", "requestId=req-rollback-42")
            .doesNotContain("downstream-secret", "raw-token", "REMOTE_REJECTED", "password");
        server.verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    void taskAccessMetadataProjectionNeverMaterializesSensitiveConfiguration() {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server
            .expect(requestTo("http://ingestion.test/api/ingestion/tasks/7"))
            .andExpect(method(GET))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        """
                        {
                          "id":7,
                          "sourceKind":"FILE",
                          "sourceType":"excelreader",
                          "sourceDataSourceId":"11111111-2222-3333-4444-555555555555",
                          "targetDataSourceId":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                          "sourceConfig":{
                            "_fileId":"file-7",
                            "password":"source-password",
                            "classificationSeal":{"effectiveLevel":"INTERNAL","fileFloor":"INTERNAL","checksum":"checksum"}
                          },
                          "destinationConfig":{
                            "targetDataSourceId":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                            "password":"target-password",
                            "secureProps":{"token":"token"}
                          }
                        }
                        """
                    )
            );

        ApiResponse<Map<String, Object>> response = client.getTaskAccessMetadata(7L);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData())
            .containsEntry("sourceKind", "FILE")
            .containsEntry("sourceDataSourceId", "11111111-2222-3333-4444-555555555555")
            .containsEntry("targetDataSourceId", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        Map<String, Object> sourceConfig = (Map<String, Object>) response.getData().get("sourceConfig");
        assertThat(sourceConfig)
            .containsEntry("_fileId", "file-7")
            .doesNotContainKey("password");
        assertThat((Map<String, Object>) sourceConfig.get("classificationSeal"))
            .containsEntry("effectiveLevel", "INTERNAL")
            .containsEntry("fileFloor", "INTERNAL")
            .doesNotContainKey("checksum");
        assertThat((Map<String, Object>) response.getData().get("destinationConfig"))
            .containsEntry("targetDataSourceId", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
            .doesNotContainKeys("password", "secureProps");
        server.verify();
    }

    @Test
    void createIsNotRetriedAfterAmbiguousReadTimeout() {
        assertSingleWriteCall(
            "/api/ingestion/tasks",
            true,
            true,
            candidate -> candidate.createIngestionTask(Map.of("name", "already-created"))
        );
    }

    @Test
    void admitIsNotRetriedAfterDownstreamServerError() {
        assertSingleWriteCall(
            "/api/ingestion/tasks/7/admit",
            false,
            false,
            candidate -> candidate.admitTask(7L, Map.of("classification", "INTERNAL"))
        );
    }

    @Test
    void executeIsNotRetriedAfterAmbiguousReadTimeout() {
        assertSingleWriteCall(
            "/api/ingestion/tasks/7/execute",
            true,
            true,
            candidate -> candidate.executeTask(7L)
        );
    }

    @Test
    void rollbackExecuteIsNotRetriedAfterDownstreamServerError() {
        assertSingleWriteCall(
            "/api/ingestion/rollback/execute",
            true,
            false,
            candidate -> candidate.rollbackExecute(Map.of("taskId", 7L))
        );
    }

    @Test
    void getStillRetriesTransientServerFailuresAccordingToConfiguration() {
        ClientHarness harness = newClientHarness(false);
        harness.server()
            .expect(org.springframework.test.web.client.ExpectedCount.once(), requestTo("http://ingestion.test/api/ingestion/tasks/7"))
            .andExpect(method(GET))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        harness.server()
            .expect(org.springframework.test.web.client.ExpectedCount.once(), requestTo("http://ingestion.test/api/ingestion/tasks/7"))
            .andExpect(method(GET))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":200,\"message\":\"ok\",\"data\":{\"id\":7}}")
            );

        ApiResponse<Map<String, Object>> response = harness.client().getTask(7L);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("id", 7);
        harness.server().verify();
    }

    private void assertSingleWriteCall(
        String path,
        boolean useLongClient,
        boolean simulateReadTimeout,
        java.util.function.Function<IngestionServiceClient, ApiResponse<?>> invocation
    ) {
        ClientHarness harness = newClientHarness(useLongClient);
        var expectation = harness.server()
            .expect(
                org.springframework.test.web.client.ExpectedCount.once(),
                requestTo("http://ingestion.test" + path)
            )
            .andExpect(method(POST));
        if (simulateReadTimeout) {
            expectation.andRespond(request -> {
                throw new org.springframework.web.client.ResourceAccessException("Read timed out after downstream commit");
            });
        } else {
            expectation.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        }

        ApiResponse<?> response = invocation.apply(harness.client());

        assertThat(response.getStatus()).isEqualTo(500);
        harness.server().verify();
    }

    private ClientHarness newClientHarness(boolean useLongClient) {
        DtsIngestionProperties properties = new DtsIngestionProperties();
        properties.setBaseUrl("http://ingestion.test");
        properties.setServiceName("dts-platform");
        properties.getRetry().setWaitDurationMs(1);
        IngestionServiceClient candidate = new IngestionServiceClient(new RestTemplateBuilder(), properties);
        String field = useLongClient ? "longRestTemplate" : "restTemplate";
        RestTemplate template = (RestTemplate) ReflectionTestUtils.getField(candidate, field);
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        return new ClientHarness(candidate, server);
    }

    private record ClientHarness(IngestionServiceClient client, MockRestServiceServer server) {}
}
