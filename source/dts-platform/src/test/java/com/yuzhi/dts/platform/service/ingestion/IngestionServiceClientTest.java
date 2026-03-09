package com.yuzhi.dts.platform.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.yuzhi.dts.platform.config.DtsIngestionProperties;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
}
