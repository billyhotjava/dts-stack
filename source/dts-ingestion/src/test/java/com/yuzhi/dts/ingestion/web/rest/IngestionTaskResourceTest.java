package com.yuzhi.dts.ingestion.web.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.service.IngestionTaskChangeLogService;
import com.yuzhi.dts.ingestion.service.IngestionTaskService;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.etl.JdbcMetadataService;
import com.yuzhi.dts.ingestion.service.openmetadata.OpenMetadataAdapter;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IngestionTaskResource.class)
@AutoConfigureMockMvc(addFilters = false)
class IngestionTaskResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AddaxJobService addaxJobService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private OpenMetadataAdapter openMetadataAdapter;

    @MockBean
    private AirflowAdapter airflowAdapter;

    @MockBean
    private IngestionTaskService ingestionTaskService;

    @MockBean
    private JdbcMetadataService jdbcMetadataService;

    @MockBean
    private IngestionTaskChangeLogService changeLogService;

    @MockBean
    private ObjectMapper objectMapper;

    @Test
    void discoverTables_requiresSourceConfig() throws Exception {
        mockMvc.perform(post("/api/ingestion/metadata/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteTask_returnsApiResponse() throws Exception {
        IngestionTaskDTO task = new IngestionTaskDTO();
        task.setId(1L);
        task.setName("demo-task");
        when(ingestionTaskService.findOne(1L)).thenReturn(Optional.of(task));

        mockMvc.perform(delete("/api/ingestion/tasks/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.taskId").value(1));
    }
}
