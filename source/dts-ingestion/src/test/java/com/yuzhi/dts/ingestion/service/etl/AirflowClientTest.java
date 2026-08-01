package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class AirflowClientTest {

    @Mock
    private IngestionSettingsService settingsService;

    private AirflowClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        AirflowProperties properties = new AirflowProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://airflow.example");
        properties.setApiPath("/api/v1");
        when(settingsService.getSettings(IngestionSettingsService.SERVICE_AIRFLOW))
            .thenReturn(new IngestionSettingsService.SettingsSnapshot(Map.of()));
        client = new AirflowClient(new RestTemplateBuilder(), properties, settingsService);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        server = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void strictDagLifecycleExceptionsMustNotExposeExternalResponseBody() {
        server.expect(requestTo("http://airflow.example/api/v1/dags/orders"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"token\":\"secret-token\"}"));

        assertThatThrownBy(() -> client.deleteDagStrict("orders"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AIRFLOW_DAG_DELETE_HTTP_FAILED: status=500")
            .message()
            .doesNotContain("secret-token", "token");
        server.verify();
    }

    @Test
    void triggerFailureMustReturnStableMessageInsteadOfExternalResponseBody() {
        server.expect(requestTo("http://airflow.example/api/v1/dags/orders/dagRuns"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("client_secret=line-secret"));

        AirflowClient.TriggerResult result = client.triggerDag("orders", Map.of("conf", Map.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Airflow DAG 触发失败");
        assertThat(result.message()).doesNotContain("line-secret", "client_secret");
        server.verify();
    }
}
