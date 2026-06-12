package com.yuzhi.dts.ingestion.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.config.IngestionOutboundPlatformProperties;
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

    private static final String SUCCESS_BODY =
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
        """;

    private PlatformInfraClient buildClient(IngestionOutboundPlatformProperties props) {
        return new PlatformInfraClient(new RestTemplateBuilder(), settingsService, new ObjectMapper(), props);
    }

    private MockRestServiceServer bindServer(PlatformInfraClient client) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        return MockRestServiceServer.bindTo(restTemplate).build();
    }

    private IngestionOutboundPlatformProperties props(String token) {
        IngestionOutboundPlatformProperties p = new IngestionOutboundPlatformProperties();
        p.setBaseUrl("http://platform.test");
        p.setApiPath("/api");
        p.setServiceToken(token);
        p.setServiceName("dts-ingestion");
        return p;
    }

    @BeforeEach
    void setUp() {
        // 默认:settings 中含 baseUrl/apiPath/serviceToken,settings 优先于 properties
        when(settingsService.getSettings(IngestionSettingsService.SERVICE_PLATFORM))
            .thenReturn(
                new IngestionSettingsService.SettingsSnapshot(
                    Map.of("baseUrl", "http://platform.test", "apiPath", "/api", "serviceToken", "db-runtime-secret")
                )
            );
    }

    @Test
    void fetchDataSourceDetailUsesRuntimeEndpointWithSettingsToken() {
        // settings.serviceToken 优先于 properties.serviceToken
        PlatformInfraClient client = buildClient(props("env-runtime-secret"));
        MockRestServiceServer server = bindServer(client);
        server
            .expect(requestTo("http://platform.test/api/infra/data-sources/" + DATA_SOURCE_ID + "/runtime-detail"))
            .andExpect(method(GET))
            .andExpect(header("X-DTS-Service", "dts-ingestion"))
            .andExpect(header("X-DTS-Service-Token", "db-runtime-secret"))
            .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        PlatformInfraClient.DataSourceDetail detail = client.fetchDataSourceDetail(DATA_SOURCE_ID);

        assertThat(detail.name()).isEqualTo("ERP PostgreSQL");
        server.verify();
    }

    @Test
    void fetchDataSourceDetailFallsBackToPropertiesTokenWhenSettingsBlank() {
        // settings 没有 serviceToken → 走 properties fallback
        when(settingsService.getSettings(IngestionSettingsService.SERVICE_PLATFORM))
            .thenReturn(
                new IngestionSettingsService.SettingsSnapshot(
                    Map.of("baseUrl", "http://platform.test", "apiPath", "/api")
                )
            );
        PlatformInfraClient client = buildClient(props("env-runtime-secret"));
        MockRestServiceServer server = bindServer(client);
        server
            .expect(requestTo("http://platform.test/api/infra/data-sources/" + DATA_SOURCE_ID + "/runtime-detail"))
            .andExpect(header("X-DTS-Service-Token", "env-runtime-secret"))
            .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        client.fetchDataSourceDetail(DATA_SOURCE_ID);
        server.verify();
    }

    @Test
    void fetchDataSourceDetailOmitsTokenHeaderWhenAllSourcesEmpty() {
        // 既没 settings.serviceToken,也没 properties.serviceToken
        when(settingsService.getSettings(IngestionSettingsService.SERVICE_PLATFORM))
            .thenReturn(
                new IngestionSettingsService.SettingsSnapshot(
                    Map.of("baseUrl", "http://platform.test", "apiPath", "/api")
                )
            );
        PlatformInfraClient client = buildClient(props(null));
        MockRestServiceServer server = bindServer(client);
        server
            .expect(requestTo("http://platform.test/api/infra/data-sources/" + DATA_SOURCE_ID + "/runtime-detail"))
            .andExpect(headerDoesNotExist("X-DTS-Service-Token"))
            .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        client.fetchDataSourceDetail(DATA_SOURCE_ID);
        server.verify();
    }

    @Test
    void fetchDataSourceDetailUsesPropertiesBaseUrlWhenSettingsLackIt() {
        // settings 不含 baseUrl → 走 properties.baseUrl
        when(settingsService.getSettings(IngestionSettingsService.SERVICE_PLATFORM))
            .thenReturn(new IngestionSettingsService.SettingsSnapshot(Map.of("serviceToken", "tok")));
        IngestionOutboundPlatformProperties p = props("ignored");
        p.setBaseUrl("http://from-properties.test");
        PlatformInfraClient client = buildClient(p);
        MockRestServiceServer server = bindServer(client);
        server
            .expect(requestTo("http://from-properties.test/api/infra/data-sources/" + DATA_SOURCE_ID + "/runtime-detail"))
            .andExpect(header("X-DTS-Service-Token", "tok"))
            .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        client.fetchDataSourceDetail(DATA_SOURCE_ID);
        server.verify();
    }

    @Test
    void syncIngestionExecutionLineageIncludesRowCountFacets() {
        PlatformInfraClient client = buildClient(props("env-runtime-secret"));
        MockRestServiceServer server = bindServer(client);
        server
            .expect(requestTo("http://platform.test/api/catalog/lineage/ingestion-executions"))
            .andExpect(method(POST))
            .andExpect(content().string(allOf(
                containsString("\"rowsRead\":10"),
                containsString("\"rowsWritten\":9"),
                containsString("\"sourceTables\""),
                containsString("\"targetTables\"")
            )))
            .andRespond(withSuccess("{\"status\":200}", MediaType.APPLICATION_JSON));

        boolean synced = client.syncIngestionExecutionLineage(apiTask(), apiExecution());

        assertThat(synced).isTrue();
        server.verify();
    }

    @Test
    void emitIngestionOpenLineageEventPostsRowCountFacets() {
        PlatformInfraClient client = buildClient(props("env-runtime-secret"));
        MockRestServiceServer server = bindServer(client);
        server
            .expect(requestTo("http://platform.test/api/internal/lineage/openlineage"))
            .andExpect(method(POST))
            .andExpect(content().string(allOf(
                containsString("\"eventType\":\"COMPLETE\""),
                containsString("\"namespace\":\"dts-ingestion\""),
                containsString("\"rowCount\""),
                containsString("\"rowsRead\":10"),
                containsString("\"rowsWritten\":9"),
                containsString("\"inputs\""),
                containsString("\"outputs\"")
            )))
            .andRespond(withSuccess("{\"status\":200}", MediaType.APPLICATION_JSON));

        boolean emitted = client.emitIngestionOpenLineageEvent(apiTask(), apiExecution());

        assertThat(emitted).isTrue();
        server.verify();
    }

    private IngestionTask apiTask() {
        IngestionTask task = new IngestionTask();
        task.setId(10L);
        task.setName("orders-api");
        task.setSourceType("httpreader");
        task.setSourceDataSourceId(DATA_SOURCE_ID);
        task.setDestinationType("postgreswriter");
        return task;
    }

    private IngestionExecution apiExecution() {
        IngestionExecution execution = new IngestionExecution();
        execution.setId(100L);
        execution.setExecutionId("api-100");
        execution.setStatus("success");
        execution.setRowsRead(10L);
        execution.setRowsWritten(9L);
        execution.setSourceTables(JsonNodeFactory.instance.arrayNode().add(JsonNodeFactory.instance.objectNode().put("name", "orders")));
        execution.setTargetTables(
            JsonNodeFactory.instance.arrayNode().add(JsonNodeFactory.instance.objectNode().put("name", "ods_orders"))
        );
        return execution;
    }
}
