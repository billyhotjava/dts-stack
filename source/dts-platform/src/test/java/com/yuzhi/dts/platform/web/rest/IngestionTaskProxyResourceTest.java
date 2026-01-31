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
}
