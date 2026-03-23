package com.yuzhi.dts.ingestion.web.rest;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.ingestion.service.IngestionTaskChangeLogService;
import com.yuzhi.dts.ingestion.service.IngestionExecutionQueryService;
import com.yuzhi.dts.ingestion.service.IngestionTaskQueryService;
import com.yuzhi.dts.ingestion.service.IngestionTaskService;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.etl.ConnectorCapabilityService;
import com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver;
import com.yuzhi.dts.ingestion.service.etl.JdbcMetadataService;
import com.yuzhi.dts.ingestion.service.etl.RealtimeTaskStatusService;
import com.yuzhi.dts.ingestion.service.openmetadata.OpenMetadataAdapter;
import com.yuzhi.dts.ingestion.config.AirflowProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
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
    private IngestionTaskQueryService ingestionTaskQueryService;

    @MockBean
    private IngestionExecutionQueryService ingestionExecutionQueryService;

    @MockBean
    private JdbcMetadataService jdbcMetadataService;

    @MockBean
    private IngestionSourceResolver ingestionSourceResolver;

    @MockBean
    private IngestionTaskChangeLogService changeLogService;

    @MockBean
    private ConnectorCapabilityService connectorCapabilityService;

    @MockBean
    private RealtimeTaskStatusService realtimeTaskStatusService;

    @MockBean
    private AirflowProperties airflowProperties;

    @Test
    void discoverTables_requiresSourceConfig() throws Exception {
        mockMvc.perform(post("/api/ingestion/metadata/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("缺少源端配置"));
    }

    @Test
    void deleteTask_returnsApiResponse() throws Exception {
        IngestionTaskDTO task = new IngestionTaskDTO();
        task.setId(1L);
        task.setName("demo-task");
        when(ingestionTaskService.delete(1L)).thenReturn(task);

        mockMvc.perform(delete("/api/ingestion/tasks/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.taskId").value(1));
    }

    @Test
    void executeTaskAsync_returnsConflictWhenValidationFails() throws Exception {
        when(ingestionTaskService.validateAsyncExecutionRequest(1L))
            .thenThrow(new IllegalStateException("任务仍在运行中，请稍后重试"));

        mockMvc.perform(post("/api/ingestion/tasks/1/execute/async"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value("任务仍在运行中，请稍后重试"));

        verify(ingestionTaskService, never()).executeAsync(1L);
    }

    @Test
    void retryExecutionAsync_returnsNotFoundWhenExecutionIsMissing() throws Exception {
        doThrow(new IllegalArgumentException("Execution not found: 9"))
            .when(ingestionTaskService)
            .validateAsyncRetryRequest(1L, 9L, "FAILED_ONLY");

        mockMvc.perform(post("/api/ingestion/tasks/1/executions/9/retry/async"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Execution not found: 9"));

        verify(ingestionTaskService, never()).retryExecutionAsync(1L, 9L, "FAILED_ONLY");
    }

    @Test
    void executeTaskAsync_returnsServiceUnavailableWhenExecutorRejectsSubmission() throws Exception {
        IngestionTaskDTO task = new IngestionTaskDTO();
        task.setId(1L);
        task.setName("demo-task");
        when(ingestionTaskService.validateAsyncExecutionRequest(1L)).thenReturn(task);
        when(ingestionTaskService.executeAsync(1L))
            .thenThrow(new TaskRejectedException("queue is full"));

        mockMvc.perform(post("/api/ingestion/tasks/1/execute/async"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.message").value("后台执行队列繁忙，请稍后重试"));
    }

    @Test
    void retryExecutionAsync_returnsServiceUnavailableWhenExecutorRejectsSubmission() throws Exception {
        when(ingestionTaskService.retryExecutionAsync(1L, 9L, "FAILED_ONLY"))
            .thenThrow(new TaskRejectedException("queue is full"));

        mockMvc.perform(post("/api/ingestion/tasks/1/executions/9/retry/async"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.message").value("后台执行队列繁忙，请稍后重试"));
    }
}
