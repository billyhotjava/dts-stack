package com.yuzhi.dts.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.etl.AirflowDagService;
import com.yuzhi.dts.ingestion.service.etl.DagPreheatService;
import com.yuzhi.dts.ingestion.service.etl.IncrementalSyncService;
import com.yuzhi.dts.ingestion.service.etl.IngestionRetryService;
import com.yuzhi.dts.ingestion.service.etl.TargetTableProvisioner;
import com.yuzhi.dts.ingestion.service.mapper.IngestionExecutionMapper;
import com.yuzhi.dts.ingestion.service.mapper.IngestionTaskMapper;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionTaskFullRefreshExecutionTest {

    @Mock
    private IngestionTaskRepository taskRepository;

    @Mock
    private IngestionExecutionRepository executionRepository;

    @Mock
    private IngestionTaskMapper taskMapper;

    @Mock
    private IngestionExecutionMapper executionMapper;

    @Mock
    private AddaxJobService addaxJobService;

    @Mock
    private AirflowAdapter airflowAdapter;

    @Mock
    private com.yuzhi.dts.ingestion.service.etl.AirflowClient airflowClient;

    @Mock
    private AirflowDagService airflowDagService;

    @Mock
    private com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver sourceResolver;

    @Mock
    private TargetTableProvisioner targetTableProvisioner;

    @Mock
    private IncrementalSyncService incrementalSyncService;

    @Mock
    private AuditService auditService;

    @Mock
    private IngestionTaskChangeLogService changeLogService;

    @Mock
    private IngestionRetryService retryService;

    @Mock
    private DagPreheatService dagPreheatService;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private IngestionTaskService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new IngestionTaskService(
            taskRepository,
            executionRepository,
            taskMapper,
            executionMapper,
            addaxJobService,
            airflowAdapter,
            airflowClient,
            airflowDagService,
            sourceResolver,
            targetTableProvisioner,
            incrementalSyncService,
            auditService,
            changeLogService,
            retryService,
            dagPreheatService,
            transactionManager
        );

        when(airflowAdapter.isEnabled()).thenReturn(false);
        when(executionRepository.findFirstByTaskIdOrderByCreatedAtDesc(any(Long.class))).thenReturn(Optional.empty());
        when(taskRepository.save(any(IngestionTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepository.save(any(IngestionExecution.class))).thenAnswer(inv -> {
            IngestionExecution execution = inv.getArgument(0);
            if (execution.getId() == null) {
                execution.setId(900L);
            }
            return execution;
        });
    }

    @Test
    void execute_fullRefreshNonFile_shouldProvisionAndRecordDroppedTables() throws Exception {
        IngestionTask task = baseTask(101L, "mysqlreader", "full_refresh");
        task.setAddaxJobPath(Files.createTempFile("addax-job", ".json").toString());

        when(taskRepository.findById(101L)).thenReturn(Optional.of(task));
        when(addaxJobService.needsJobRebuild(any())).thenReturn(false);
        when(addaxJobService.isJobConfigMalformed(any(java.nio.file.Path.class))).thenReturn(false);
        when(addaxJobService.listWriterTablesFromJob(task.getAddaxJobPath())).thenReturn(List.of("public.t1", "public.t2"));
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());

        service.execute(101L);

        verify(targetTableProvisioner).ensureTargetTables(eq(task), eq(null));

        ArgumentCaptor<IngestionExecution> captor = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        IngestionExecution latest = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(latest.getReplaceMode()).isEqualTo("FULL_REPLACE");
        assertThat(latest.getDroppedTables()).isEqualTo("public.t1,public.t2");
    }

    @Test
    void execute_fullRefreshFile_shouldSkipTargetProvisioner() {
        IngestionTask task = baseTask(102L, "excel", "full_refresh");
        task.setAddaxJobPath(null);

        when(taskRepository.findById(102L)).thenReturn(Optional.of(task));
        when(addaxJobService.createJobFromTask(eq(task), eq(null), eq(null), any(Map.class)))
            .thenReturn(new AddaxJobService.AddaxJobResult("job.json", "/tmp/file-job.json", Map.of()));
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());

        service.execute(102L);

        verify(targetTableProvisioner, never()).ensureTargetTables(any(), any());
    }

    @Test
    void execute_airflowEnabledWithInvalidContainerJobPath_shouldFailReadable() throws Exception {
        IngestionTask task = baseTask(103L, "mysqlreader", "full_refresh");
        task.setAirflowEnabled(true);
        task.setAirflowDagId("task_test");
        task.setAddaxJobPath(Files.createTempFile("addax-job", ".json").toString());

        when(taskRepository.findById(103L)).thenReturn(Optional.of(task));
        when(airflowAdapter.isEnabled()).thenReturn(true);
        when(addaxJobService.needsJobRebuild(any())).thenReturn(false);
        when(addaxJobService.isJobConfigMalformed(any(java.nio.file.Path.class))).thenReturn(false);
        when(addaxJobService.splitJobIntoPerTableFiles(any())).thenReturn(List.of());
        when(airflowDagService.rebuildDagForTask(any(), any())).thenReturn("task_test");
        when(addaxJobService.toContainerJobPath(any())).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.execute(103L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Addax 作业路径无效，请先重建作业");
    }

    private IngestionTask baseTask(Long id, String sourceType, String syncMode) {
        IngestionTask task = new IngestionTask();
        task.setId(id);
        task.setName("task-" + id);
        task.setStatus("active");
        task.setSourceType(sourceType);
        task.setDestinationType("postgresqlwriter");
        task.setSyncMode(syncMode);
        task.setAirflowEnabled(false);
        task.setCreatedBy("tester");
        task.setLastModifiedBy("tester");

        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("host", "localhost");
        task.setSourceConfig(sourceConfig);
        return task;
    }
}
