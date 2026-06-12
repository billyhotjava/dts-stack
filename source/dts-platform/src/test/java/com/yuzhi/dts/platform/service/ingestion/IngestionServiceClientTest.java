package com.yuzhi.dts.platform.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.yuzhi.dts.platform.config.DtsIngestionProperties;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

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
}
