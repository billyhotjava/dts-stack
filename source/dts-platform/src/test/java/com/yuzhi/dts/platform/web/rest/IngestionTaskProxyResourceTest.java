package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService;
import com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService.DefaultDestinationSnapshot;
import com.yuzhi.dts.platform.service.etl.OdsTableMappingSyncService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = IngestionTaskProxyResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration"
    }
)
@AutoConfigureMockMvc(addFilters = false)
class IngestionTaskProxyResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestionServiceClient ingestionClient;

    @MockBean
    private DefaultDestinationSyncService destinationSyncService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private OdsTableMappingSyncService odsTableMappingSyncService;

    @MockBean
    private ExternalRunLogService externalRunLogService;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

    @Test
    void createTaskForwardsPayload() throws Exception {
        String platformDataSourceId = "11111111-2222-3333-4444-555555555555";
        DefaultDestinationSnapshot snapshot = new DefaultDestinationSnapshot(
            "rdbmswriter",
            "lake",
            Map.of("connection", java.util.List.of(Map.of("jdbcUrl", java.util.List.of("jdbc:pg")))),
            platformDataSourceId
        );
        when(destinationSyncService.ensureDefaultDestination()).thenReturn(snapshot);
        when(ingestionClient.createIngestionTask(anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("task", "demo")));

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"task\",\"destination\":{\"config\":{\"table\":[\"t1\"]}}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).createIngestionTask(captor.capture());
        Map<String, Object> payload = captor.getValue();
        Object destinationObj = payload.get("destination");
        assertThat(destinationObj).isInstanceOf(Map.class);
        Map<String, Object> destination = (Map<String, Object>) destinationObj;
        assertThat(destination.get("definitionId")).isEqualTo("rdbmswriter");
        Map<String, Object> config = (Map<String, Object>) destination.get("config");
        assertThat(((java.util.List<?>) config.get("table")).get(0)).isEqualTo("t1");
        assertThat(config.get("targetDataSourceId")).isEqualTo(platformDataSourceId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createTaskUsesSelectedTargetDataSourceWhenProvided() throws Exception {
        String targetDataSourceId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        DefaultDestinationSnapshot snapshot = new DefaultDestinationSnapshot(
            "postgresqlwriter",
            "经营分析湖仓",
            Map.of("jdbcUrl", "jdbc:postgresql://analytics-pg:5432/ads", "username", "biadmin"),
            targetDataSourceId
        );
        when(destinationSyncService.ensureDestination(targetDataSourceId)).thenReturn(snapshot);
        when(ingestionClient.createIngestionTask(anyMap()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("task", "demo")));

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"task",
                      "destination":{
                        "usePlatformDefault":true,
                        "config":{
                          "targetDataSourceId":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                          "connection":[{"table":["ods_orders"]}]
                        }
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ingestionClient).createIngestionTask(captor.capture());
        Map<String, Object> destination = (Map<String, Object>) captor.getValue().get("destination");
        Map<String, Object> config = (Map<String, Object>) destination.get("config");
        assertThat(destination.get("definitionId")).isEqualTo("postgresqlwriter");
        assertThat(config.get("targetDataSourceId")).isEqualTo(targetDataSourceId);
        assertThat(config.get("jdbcUrl")).isEqualTo("jdbc:postgresql://analytics-pg:5432/ads");
        assertThat(config.get("username")).isEqualTo("biadmin");
    }

    @Test
    void retryExecutionIsExposedViaPlatformProxy() throws Exception {
        when(ingestionClient.retryExecution(1L, 2L, Map.of("mode", "FAILED_ONLY")))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("executionId", 2, "status", "QUEUED")));

        mockMvc.perform(post("/api/ingestion/tasks/1/executions/2/retry").param("mode", "FAILED_ONLY"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.executionId").value(2))
            .andExpect(jsonPath("$.data.status").value("QUEUED"));

        verify(ingestionClient).retryExecution(1L, 2L, Map.of("mode", "FAILED_ONLY"));
    }

    @Test
    void apiConnectionTestIsExposedViaPlatformProxy() throws Exception {
        when(ingestionClient.testApiConnection(Map.of("dataSourceId", "11111111-2222-3333-4444-555555555555")))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("connected", true, "httpStatus", 200, "sampleCount", 1)));

        mockMvc.perform(post("/api/ingestion/api/test-connection")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataSourceId\":\"11111111-2222-3333-4444-555555555555\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.connected").value(true))
            .andExpect(jsonPath("$.data.httpStatus").value(200))
            .andExpect(jsonPath("$.data.sampleCount").value(1));

        verify(ingestionClient).testApiConnection(Map.of("dataSourceId", "11111111-2222-3333-4444-555555555555"));
    }

    @Test
    void executeTaskAsyncIsExposedViaPlatformProxy() throws Exception {
        when(ingestionClient.executeTaskAsync(5L))
            .thenReturn(new ApiResponse<>(202, "accepted", Map.of("taskId", 5, "status", "submitted", "async", true)));

        mockMvc.perform(post("/api/ingestion/tasks/5/execute/async"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.taskId").value(5))
            .andExpect(jsonPath("$.data.status").value("submitted"))
            .andExpect(jsonPath("$.data.async").value(true));

        verify(ingestionClient).executeTaskAsync(5L);
    }

    @Test
    void backfillTaskIsExposedViaPlatformProxy() throws Exception {
        when(ingestionClient.backfillTask(
                5L,
                Map.of(
                    "windowStart", "2026-04-29T00:00:00Z",
                    "windowEnd", "2026-04-30T00:00:00Z",
                    "column", "update_time"
                )
            ))
            .thenReturn(
                new ApiResponse<>(
                    202,
                    "补数已提交，正在后台执行",
                    Map.of("taskId", 5, "executionId", 9, "status", "submitted", "async", true)
                )
            );

        mockMvc.perform(post("/api/ingestion/tasks/5/backfill")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"windowStart\":\"2026-04-29T00:00:00Z\",\"windowEnd\":\"2026-04-30T00:00:00Z\",\"column\":\"update_time\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.taskId").value(5))
            .andExpect(jsonPath("$.data.executionId").value(9))
            .andExpect(jsonPath("$.data.status").value("submitted"))
            .andExpect(jsonPath("$.data.async").value(true));

        verify(ingestionClient).backfillTask(
            5L,
            Map.of(
                "windowStart", "2026-04-29T00:00:00Z",
                "windowEnd", "2026-04-30T00:00:00Z",
                "column", "update_time"
            )
        );
    }

    @Test
    void retryExecutionAsyncIsExposedViaPlatformProxy() throws Exception {
        when(ingestionClient.retryExecutionAsync(1L, 2L, Map.of("mode", "FAILED_ONLY")))
            .thenReturn(new ApiResponse<>(202, "accepted", Map.of("taskId", 1, "executionId", 2, "status", "submitted", "async", true)));

        mockMvc.perform(post("/api/ingestion/tasks/1/executions/2/retry/async").param("mode", "FAILED_ONLY"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.taskId").value(1))
            .andExpect(jsonPath("$.data.executionId").value(2))
            .andExpect(jsonPath("$.data.status").value("submitted"))
            .andExpect(jsonPath("$.data.async").value(true));

        verify(ingestionClient).retryExecutionAsync(1L, 2L, Map.of("mode", "FAILED_ONLY"));
    }
}
