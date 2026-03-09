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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IngestionTaskProxyResource.class)
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
        DefaultDestinationSnapshot snapshot = new DefaultDestinationSnapshot(
            "rdbmswriter",
            "lake",
            Map.of("connection", java.util.List.of(Map.of("jdbcUrl", java.util.List.of("jdbc:pg"))))
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
    void executeTaskAsyncIsExposedViaPlatformProxy() throws Exception {
        when(ingestionClient.executeTaskAsync(5L))
            .thenReturn(new ApiResponse<>(202, "accepted", Map.of("taskId", 5, "status", "submitted", "async", true)));

        mockMvc.perform(post("/api/ingestion/tasks/5/execute/async"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value(202))
            .andExpect(jsonPath("$.data.taskId").value(5))
            .andExpect(jsonPath("$.data.status").value("submitted"))
            .andExpect(jsonPath("$.data.async").value(true));

        verify(ingestionClient).executeTaskAsync(5L);
    }
}
