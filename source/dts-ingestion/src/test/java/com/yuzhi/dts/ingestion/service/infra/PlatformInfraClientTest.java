package com.yuzhi.dts.ingestion.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class PlatformInfraClientTest {

    private static final UUID DATA_SOURCE_ID = UUID.fromString("2b0fce68-0c78-41f4-9c63-0f6d1e9292e1");

    private final IngestionSettingsService settingsService = org.mockito.Mockito.mock(IngestionSettingsService.class);
    private PlatformInfraClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        when(settingsService.getSettings(IngestionSettingsService.SERVICE_PLATFORM))
            .thenReturn(
                new IngestionSettingsService.SettingsSnapshot(
                    Map.of("baseUrl", "http://platform.test", "apiPath", "/api", "serviceToken", "db-runtime-secret")
                )
            );

        client = new PlatformInfraClient(new RestTemplateBuilder(), settingsService, new ObjectMapper(), "env-runtime-secret");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void fetchDataSourceDetailUsesRuntimeEndpointAndServiceToken() {
        server
            .expect(requestTo("http://platform.test/api/infra/data-sources/" + DATA_SOURCE_ID + "/runtime-detail"))
            .andExpect(method(GET))
            .andExpect(header("X-DTS-Service", "dts-ingestion"))
            .andExpect(header("X-DTS-Service-Token", "db-runtime-secret"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status": 200,
                      "data": {
                        "id": "2b0fce68-0c78-41f4-9c63-0f6d1e9292e1",
                        "name": "ERP PostgreSQL",
                        "type": "POSTGRESQL",
                        "jdbcUrl": "jdbc:postgresql://pg.test:5432/erp",
                        "username": "erp_reader",
                        "props": {"schema": "public"},
                        "secrets": {"password": "plain-secret"},
                        "status": "ACTIVE"
                      }
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        PlatformInfraClient.DataSourceDetail detail = client.fetchDataSourceDetail(DATA_SOURCE_ID);

        assertThat(detail.name()).isEqualTo("ERP PostgreSQL");
        assertThat(detail.secrets()).containsEntry("password", "plain-secret");
        server.verify();
    }
}
