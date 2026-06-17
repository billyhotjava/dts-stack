package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.config.AnalyticsOutboundPlatformProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class PlatformInfraClientTest {

    private static final UUID DATA_SOURCE_ID = UUID.fromString("2b0fce68-0c78-41f4-9c63-0f6d1e9292e1");
    private static final String SUCCESS_BODY =
        """
        {
          "status": 200,
          "data": {
            "id": "2b0fce68-0c78-41f4-9c63-0f6d1e9292e1",
            "name": "Project dashboard lake",
            "type": "POSTGRESQL",
            "jdbcUrl": "jdbc:postgresql://dts-pg:5432/biadmin",
            "username": "biadmin",
            "props": {"schema": "public"},
            "secrets": {"password": "plain-secret"},
            "status": "ACTIVE"
          }
        }
        """;

    @Test
    void fetchDataSourceDetailUsesRuntimeEndpointAndReturnsSecrets() {
        PlatformInfraClient client = buildClient(props());
        MockRestServiceServer server = bindServer(client);
        server
            .expect(requestTo("http://platform.test/api/infra/data-sources/" + DATA_SOURCE_ID + "/runtime-detail"))
            .andExpect(method(GET))
            .andExpect(header("X-DTS-Service", "dts-analytics"))
            .andExpect(header("X-DTS-Service-Token", "analytics-secret"))
            .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        PlatformInfraClient.DataSourceDetail detail = client.fetchDataSourceDetail(DATA_SOURCE_ID);

        assertThat(detail.jdbcUrl()).isEqualTo("jdbc:postgresql://dts-pg:5432/biadmin");
        assertThat(detail.username()).isEqualTo("biadmin");
        assertThat(detail.secrets()).containsEntry("password", "plain-secret");
        server.verify();
    }

    @Test
    void listDataSourcesUsesSelectionEndpointForAnalyticsCapability() {
        PlatformInfraClient client = buildClient(props());
        MockRestServiceServer server = bindServer(client);
        server
            .expect(requestTo("http://platform.test/api/infra/data-source-selections?capability=ANALYTICS_REGISTERABLE"))
            .andExpect(method(GET))
            .andExpect(header("X-DTS-Service", "dts-analytics"))
            .andExpect(header("X-DTS-Service-Token", "analytics-secret"))
            .andRespond(withSuccess(
                """
                {
                  "status": 200,
                  "data": {
                    "capability": "ANALYTICS_REGISTERABLE",
                    "defaultDataSourceId": "2b0fce68-0c78-41f4-9c63-0f6d1e9292e1",
                    "items": [
                      {
                        "id": "2b0fce68-0c78-41f4-9c63-0f6d1e9292e1",
                        "name": "Project dashboard lake",
                        "type": "POSTGRESQL",
                        "jdbcUrl": "jdbc:postgresql://dts-pg:5432/biadmin",
                        "description": "warehouse",
                        "ownerDept": "metro",
                        "status": "ACTIVE",
                        "defaultSource": true,
                        "recommended": true
                      }
                    ]
                  }
                }
                """,
                MediaType.APPLICATION_JSON
            ));

        var list = client.listDataSources();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).id()).isEqualTo(DATA_SOURCE_ID.toString());
        assertThat(list.get(0).jdbcUrl()).isEqualTo("jdbc:postgresql://dts-pg:5432/biadmin");
        server.verify();
    }

    @Test
    void listDataSourcesFallsBackToLegacyEndpointWhenSelectionEndpointIsUnavailable() {
        PlatformInfraClient client = buildClient(props());
        MockRestServiceServer server = bindServer(client);
        server
            .expect(once(), requestTo("http://platform.test/api/infra/data-source-selections?capability=ANALYTICS_REGISTERABLE"))
            .andExpect(method(GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server
            .expect(once(), requestTo("http://platform.test/api/infra/data-sources"))
            .andExpect(method(GET))
            .andRespond(withSuccess(
                """
                {
                  "status": 200,
                  "data": [
                    {
                      "id": "2b0fce68-0c78-41f4-9c63-0f6d1e9292e1",
                      "name": "Legacy platform source",
                      "type": "POSTGRESQL",
                      "jdbcUrl": "jdbc:postgresql://dts-pg:5432/biadmin",
                      "status": "ACTIVE"
                    }
                  ]
                }
                """,
                MediaType.APPLICATION_JSON
            ));

        var list = client.listDataSources();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).name()).isEqualTo("Legacy platform source");
        server.verify();
    }

    private PlatformInfraClient buildClient(AnalyticsOutboundPlatformProperties props) {
        return new PlatformInfraClient(new RestTemplateBuilder(), new ObjectMapper(), props);
    }

    private MockRestServiceServer bindServer(PlatformInfraClient client) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        return MockRestServiceServer.bindTo(restTemplate).build();
    }

    private AnalyticsOutboundPlatformProperties props() {
        AnalyticsOutboundPlatformProperties props = new AnalyticsOutboundPlatformProperties();
        props.setBaseUrl("http://platform.test");
        props.setApiPath("/api");
        props.setServiceName("dts-analytics");
        props.setServiceToken("analytics-secret");
        return props;
    }
}
