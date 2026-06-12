package com.yuzhi.dts.ingestion.web.rest;

import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.yuzhi.dts.ingestion.service.etl.api.ApiAuthProviderDescriptor;
import com.yuzhi.dts.ingestion.service.etl.api.ApiAuthProviderRegistry;
import com.yuzhi.dts.ingestion.service.openmetadata.OpenMetadataAdapter;
import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.config.ApiProperties;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
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

    @MockBean
    private ApiProperties apiProperties;

    @MockBean
    private ApiAuthProviderRegistry apiAuthProviderRegistry;

    @Test
    void createTask_apiDraftNormalizesRawOdsLandingAndTableMapping() throws Exception {
        UUID sourceId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PlatformInfraClient.DataSourceDetail detail = new PlatformInfraClient.DataSourceDetail(
            sourceId,
            "CRM API",
            "api",
            null,
            null,
            null,
            null,
            Map.of("readerType", "httpreader", "connectorType", "api"),
            Map.of(),
            "ACTIVE"
        );
        when(ingestionSourceResolver.resolve(eq(sourceId), anyList()))
            .thenReturn(new IngestionSourceResolver.ResolvedSource("httpreader", Map.of("readerType", "httpreader"), detail));
        IngestionTaskDTO created = new IngestionTaskDTO();
        created.setId(99L);
        created.setName("crm-orders");
        when(ingestionTaskService.create(any(IngestionTaskDTO.class), any(), eq(true))).thenReturn(created);

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "draft": true,
                      "name": "crm-orders",
                      "source": {
                        "dataSourceId": "11111111-2222-3333-4444-555555555555",
                        "type": "api",
                        "config": {
                          "sourceSystem": "CRM",
                          "resource": {
                            "path": "/v1/orders",
                            "fields": [{"sourceField": "id", "targetColumn": "id"}]
                          }
                        }
                      },
                      "sync": {"mode": "full_refresh"},
                      "streams": {"selection": "manual", "include": ["orders"]},
                      "airflow": {"enabled": false}
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.task.id").value(99));

        ArgumentCaptor<IngestionTaskDTO> captor = ArgumentCaptor.forClass(IngestionTaskDTO.class);
        verify(ingestionTaskService).create(captor.capture(), any(), eq(true));
        IngestionTaskDTO task = captor.getValue();
        assertThat(task.getSourceType()).isEqualTo("httpreader");
        assertThat(task.getAirflowEnabled()).isFalse();
        assertThat(task.getSourceConfig().get("resource").has("fields")).isFalse();
        assertThat(task.getSourceConfig().get("resource").get("targetTable").asText()).isEqualTo("ods_api_crm_v1_orders");
        assertThat(task.getSourceConfig().get("resource").get("landing").get("rawRecordColumn").asText()).isEqualTo("_dts_raw_record");
        assertThat(task.getTableMapping()).hasSize(1);
        assertThat(task.getTableMapping().get(0).get("source").asText()).isEqualTo("v1_orders");
        assertThat(task.getTableMapping().get(0).get("target").asText()).isEqualTo("ods_api_crm_v1_orders");
    }

    @Test
    void createTask_apiDraftRejectsDisabledAuthProvider() throws Exception {
        UUID sourceId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PlatformInfraClient.DataSourceDetail detail = new PlatformInfraClient.DataSourceDetail(
            sourceId,
            "CRM API",
            "api",
            null,
            null,
            null,
            null,
            Map.of("readerType", "httpreader", "connectorType", "api"),
            Map.of(),
            "ACTIVE"
        );
        when(ingestionSourceResolver.resolve(eq(sourceId), anyList()))
            .thenReturn(new IngestionSourceResolver.ResolvedSource("httpreader", Map.of("readerType", "httpreader"), detail));
        when(apiAuthProviderRegistry.findDescriptor("mtls"))
            .thenReturn(Optional.of(new ApiAuthProviderDescriptor("mtls", "mTLS", "preview", List.of(), true, false)));

        mockMvc.perform(post("/api/ingestion/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "draft": true,
                      "name": "crm-orders",
                      "source": {
                        "dataSourceId": "11111111-2222-3333-4444-555555555555",
                        "type": "api",
                        "config": {
                          "sourceSystem": "CRM",
                          "auth": {"provider": "mtls"},
                          "resource": {"path": "/v1/orders"}
                        }
                      },
                      "sync": {"mode": "full_refresh"},
                      "streams": {"selection": "manual", "include": ["orders"]},
                      "airflow": {"enabled": false}
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("API 鉴权方式 mtls 暂未开放，不能用于入湖任务"));

        verify(ingestionTaskService, never()).create(any(IngestionTaskDTO.class), any(), eq(true));
    }

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
    void rebuildApiDags_returnsMigrationSummary() throws Exception {
        when(ingestionTaskService.rebuildApiDags()).thenReturn(
            Map.of("total", 3, "migrated", 1, "skipped", 2, "failed", 0)
        );

        mockMvc.perform(post("/api/ingestion/tasks/dags/rebuild-api"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.migrated").value(1))
            .andExpect(jsonPath("$.skipped").value(2))
            .andExpect(jsonPath("$.failed").value(0));

        verify(ingestionTaskService).rebuildApiDags();
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
