package com.yuzhi.dts.ingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.etl.AirflowDagService;
import com.yuzhi.dts.ingestion.service.etl.DagPreheatService;
import com.yuzhi.dts.ingestion.service.etl.ExcelParseService;
import com.yuzhi.dts.ingestion.service.etl.CsvParseService;
import com.yuzhi.dts.ingestion.service.etl.FileUploadService;
import com.yuzhi.dts.ingestion.service.etl.TargetTableProvisioner;
import com.yuzhi.dts.ingestion.service.etl.IncrementalSyncService;
import com.yuzhi.dts.ingestion.service.etl.IngestionRetryService;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import com.yuzhi.dts.ingestion.service.mapper.IngestionExecutionMapper;
import com.yuzhi.dts.ingestion.service.mapper.IngestionTaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 单元测试：IngestionTaskService
 * 测试任务CRUD、执行和监控功能
 */
@ExtendWith(MockitoExtension.class)
class IngestionTaskServiceTest {

    private static final UUID TEST_SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

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
    private ExcelParseService excelParseService;

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private CsvParseService csvParseService;

    @Mock
    private IngestionRetryService retryService;

    @Mock
    private DagPreheatService dagPreheatService;

    @Mock
    private PlatformInfraClient platformInfraClient;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private IngestionTaskService ingestionTaskService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        ingestionTaskService = new IngestionTaskService(
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
            excelParseService,
            fileUploadService,
            csvParseService,
            retryService,
            dagPreheatService,
            platformInfraClient,
            transactionManager,
            Runnable::run
        );

        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        }

        // Make TransactionTemplate actually execute the callback (needed for async trigger phase)
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any()))
            .thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());

        // Track saved executions so findById returns them in the async trigger phase
        java.util.Map<Long, IngestionExecution> savedExecutions = new java.util.concurrent.ConcurrentHashMap<>();
        org.mockito.Mockito.lenient().when(executionRepository.save(any(IngestionExecution.class))).thenAnswer(inv -> {
            IngestionExecution execution = inv.getArgument(0);
            if (execution.getId() == null) {
                execution.setId(System.nanoTime());
            }
            savedExecutions.put(execution.getId(), execution);
            return execution;
        });
        org.mockito.Mockito.lenient().when(executionRepository.findById(any(Long.class))).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.ofNullable(savedExecutions.get(id));
        });
    }

    @AfterEach
    void tearDown() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldCreateTaskSuccessfully() {
        // Given
        IngestionTaskDTO dto = createTestTaskDTO();
        IngestionTask entity = createTestTaskEntity();
        IngestionTask savedEntity = createTestTaskEntity();
        savedEntity.setId(1L);
        savedEntity.setCreatedBy("admin");

        when(taskMapper.toEntity(dto)).thenReturn(entity);
        when(addaxJobService.createJobFromTask(entity)).thenReturn(
            new AddaxJobService.AddaxJobResult("job.json", "/path/to/job.json", Map.of())
        );
        when(taskRepository.save(entity)).thenReturn(savedEntity);
        when(taskMapper.toDto(savedEntity)).thenReturn(dto);

        // When
        IngestionTaskDTO result = ingestionTaskService.create(dto);

        // Then
        assertThat(result).isNotNull();
        verify(addaxJobService).createJobFromTask(entity);
        verify(taskRepository).save(entity);

        // Verify audit
        verify(auditService).auditAction(
            eq("INGESTION_TASK_CREATE"),
            eq(AuditStage.SUCCESS),
            anyString(),
            any(Map.class)
        );
    }

    @Test
    void shouldHandleCreateTaskFailure() {
        // Given
        IngestionTaskDTO dto = createTestTaskDTO();
        IngestionTask entity = createTestTaskEntity();

        when(taskMapper.toEntity(dto)).thenReturn(entity);
        when(addaxJobService.createJobFromTask(entity)).thenThrow(new RuntimeException("Job generation failed"));

        // When & Then
        assertThatThrownBy(() -> ingestionTaskService.create(dto))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to generate Addax job");

        // Verify audit for failure
        verify(auditService).auditAction(
            eq("INGESTION_TASK_CREATE"),
            eq(AuditStage.FAIL),
            anyString(),
            any(Map.class)
        );
    }

    @Test
    void shouldFindTaskById() {
        // Given
        Long taskId = 1L;
        IngestionTask entity = createTestTaskEntity();
        entity.setId(taskId);
        IngestionTaskDTO dto = createTestTaskDTO();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(entity));
        when(taskMapper.toDto(entity)).thenReturn(dto);

        // When
        Optional<IngestionTaskDTO> result = ingestionTaskService.findOne(taskId);

        // Then
        assertThat(result).isPresent();
        verify(taskRepository).findById(taskId);
    }

    @Test
    void shouldFindAllTasks() {
        // Given
        String status = "active";
        Pageable pageable = PageRequest.of(0, 20);
        List<IngestionTask> tasks = List.of(createTestTaskEntity());
        Page<IngestionTask> page = new PageImpl<>(tasks, pageable, 1);

        when(taskRepository.findByStatus(status, pageable)).thenReturn(page);
        when(taskMapper.toDto(any(IngestionTask.class))).thenReturn(createTestTaskDTO());

        // When
        Page<IngestionTaskDTO> result = ingestionTaskService.findAll(status, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(taskRepository).findByStatus(status, pageable);
    }

    @Test
    void shouldUpdateTask() {
        // Given
        Long taskId = 1L;
        IngestionTaskDTO dto = createTestTaskDTO();
        dto.setId(taskId);
        IngestionTask existingTask = createTestTaskEntity();
        existingTask.setId(taskId);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(existingTask));
        when(taskRepository.save(existingTask)).thenReturn(existingTask);
        when(taskMapper.toDto(existingTask)).thenReturn(dto);

        // When
        IngestionTaskDTO result = ingestionTaskService.update(taskId, dto);

        // Then
        assertThat(result).isNotNull();
        verify(taskMapper).partialUpdate(existingTask, dto);
        verify(taskRepository).save(existingTask);
    }

    @Test
    void shouldDeleteTaskAndCascadeCleanup() {
        // Given
        Long taskId = 1L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        // When
        ingestionTaskService.delete(taskId);

        // Then
        verify(executionRepository).deleteByTaskId(taskId);
        verify(incrementalSyncService).clearCheckpointByTaskId(taskId);
        verify(changeLogService).deleteByTaskId(taskId);
        verify(addaxJobService).deleteJobIfExists(task.getAddaxJobPath());
        verify(airflowDagService).deleteDagForTask(task);
        verify(taskRepository).delete(task);
    }

    @Test
    void shouldExecuteTaskWithAirflow() throws Exception {
        // Given
        Long taskId = 1L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("draft");
        task.setAirflowEnabled(true);
        task.setAirflowDagId("test-dag");
        task.setAddaxJobPath(java.nio.file.Files.createTempFile("addax-job", ".json").toString());

        IngestionExecution execution = new IngestionExecution();
        execution.setId(1L);
        execution.setStatus("running");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(airflowAdapter.isEnabled()).thenReturn(true);
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());
        when(addaxJobService.needsJobRebuild(any())).thenReturn(false);
        when(addaxJobService.isJobConfigMalformed(any(java.nio.file.Path.class))).thenReturn(false);
        when(addaxJobService.splitJobIntoPerTableFiles(task.getAddaxJobPath())).thenReturn(Collections.emptyList());
        when(addaxJobService.toContainerJobPath(task.getAddaxJobPath())).thenReturn("/opt/addax/job.json");
        when(airflowDagService.rebuildDagForTask(task, Collections.emptyList())).thenReturn(task.getAirflowDagId());
        when(airflowAdapter.triggerIfRequested(any(), any(), eq(true))).thenReturn(Map.of("status", "triggered", "dagRunId", "dag-run-1"));
        when(taskRepository.save(task)).thenReturn(task);

        // When — execute() is now two-phase: synchronous "preparing" + async trigger after commit
        IngestionExecutionDTO result = ingestionTaskService.execute(taskId);

        // Then — Phase 1 returns immediately with "preparing" status
        assertThat(result).isNotNull();
        // Phase 2 runs in afterCommit — simulate by invoking registered synchronizations
        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        verify(executionRepository, atLeast(1)).save(any(IngestionExecution.class));
        verify(taskRepository, atLeastOnce()).save(task);
    }

    @Test
    void shouldHandleExecuteTaskFailure() throws Exception {
        // Given
        Long taskId = 1L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        task.setAirflowEnabled(true);
        task.setAirflowDagId("test-dag");
        task.setAddaxJobPath(java.nio.file.Files.createTempFile("addax-job-fail", ".json").toString());

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(airflowAdapter.isEnabled()).thenReturn(true);
        when(addaxJobService.needsJobRebuild(any())).thenReturn(false);
        when(addaxJobService.isJobConfigMalformed(any(java.nio.file.Path.class))).thenReturn(false);
        when(addaxJobService.splitJobIntoPerTableFiles(task.getAddaxJobPath())).thenReturn(Collections.emptyList());
        when(addaxJobService.toContainerJobPath(task.getAddaxJobPath())).thenReturn("/opt/addax/job.json");
        when(airflowDagService.rebuildDagForTask(task, Collections.emptyList())).thenReturn(task.getAirflowDagId());
        when(taskRepository.save(task)).thenReturn(task);
        when(airflowAdapter.triggerIfRequested(any(), any(), anyBoolean()))
            .thenThrow(new RuntimeException("Airflow trigger failed"));
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());

        // When — execute() is now two-phase; Phase 1 succeeds even when Phase 2 will fail
        IngestionExecutionDTO result = ingestionTaskService.execute(taskId);
        assertThat(result).isNotNull();

        // Phase 2 runs in afterCommit — the trigger failure is handled async via markExecutionFailed
        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        // Verify that the execution was marked as failed in the async phase
        ArgumentCaptor<IngestionExecution> captor = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository, atLeast(2)).save(captor.capture());
        boolean hasFailed = captor.getAllValues().stream()
            .anyMatch(e -> "failed".equals(e.getStatus()));
        assertThat(hasFailed).isTrue();
        verify(platformInfraClient).syncIngestionExecutionLineage(eq(task), any(IngestionExecution.class));
    }

    @Test
    void execute_shouldRejectApiSourceTaskUntilRuntimeIsEnabled() {
        Long taskId = 1L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("draft");
        task.setSourceType("httpreader");
        task.setSourceConfig(objectMapper.createObjectNode().put("apiType", "ingress"));

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());

        IngestionExecutionDTO result = ingestionTaskService.execute(taskId);
        assertThat(result).isNotNull();
    }

    @Test
    void update_shouldKeepApiTaskDraftAndSkipRuntimeAssetGeneration() {
        Long taskId = 1L;
        UUID sourceId = UUID.randomUUID();
        IngestionTaskDTO dto = createTestTaskDTO();
        dto.setId(taskId);
        dto.setSourceType("httpreader");
        dto.setSourceDataSourceId(sourceId);
        dto.setStatus("active");
        ObjectNode nextSourceConfig = objectMapper.createObjectNode();
        nextSourceConfig.put("path", "/orders");
        dto.setSourceConfig(nextSourceConfig);

        IngestionTask existingTask = createTestTaskEntity();
        existingTask.setId(taskId);
        existingTask.setSourceType("httpreader");
        existingTask.setSourceDataSourceId(sourceId);
        existingTask.setStatus("active");
        existingTask.setAirflowEnabled(true);
        existingTask.setAddaxJobPath("/tmp/old-api-job.json");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(existingTask));
        doAnswer(inv -> {
            IngestionTask target = inv.getArgument(0);
            IngestionTaskDTO update = inv.getArgument(1);
            target.setSourceType(update.getSourceType());
            target.setSourceDataSourceId(update.getSourceDataSourceId());
            target.setSourceConfig(update.getSourceConfig());
            target.setStatus(update.getStatus());
            return null;
        }).when(taskMapper).partialUpdate(existingTask, dto);
        when(taskRepository.save(existingTask)).thenReturn(existingTask);
        when(taskMapper.toDto(existingTask)).thenReturn(dto);

        IngestionTaskDTO result = ingestionTaskService.update(taskId, dto);

        assertThat(result).isNotNull();
        assertThat(existingTask.getStatus()).isEqualTo("active");
        assertThat(existingTask.getAddaxJobPath()).isNull();
        verify(addaxJobService, never()).createJobFromTask(any(IngestionTask.class));
        verify(airflowDagService, never()).ensureDagForTask(any(), any());
    }

    @Test
    void shouldGetExecutionHistory() {
        // Given
        Long taskId = 1L;
        Pageable pageable = PageRequest.of(0, 20);
        List<IngestionExecution> executions = List.of(new IngestionExecution());
        Page<IngestionExecution> page = new PageImpl<>(executions, pageable, 1);

        when(executionRepository.findByTaskId(taskId, pageable)).thenReturn(page);
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());

        // When
        Page<IngestionExecutionDTO> result = ingestionTaskService.getExecutions(taskId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(executionRepository).findByTaskId(taskId, pageable);
    }

    @Test
    void shouldGetLatestExecution() {
        // Given
        Long taskId = 1L;
        IngestionExecution execution = new IngestionExecution();
        execution.setId(1L);

        when(executionRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId))
            .thenReturn(Optional.of(execution));
        when(executionMapper.toDto(execution)).thenReturn(new IngestionExecutionDTO());

        // When
        Optional<IngestionExecutionDTO> result = ingestionTaskService.getLatestExecution(taskId);

        // Then
        assertThat(result).isPresent();
        verify(executionRepository).findFirstByTaskIdOrderByCreatedAtDesc(taskId);
    }

    @Test
    void execute_shouldRebuildAddaxJobForLegacyTxtFileSource() throws Exception {
        Long taskId = 1L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        task.setSourceType("txt");
        task.setSourceDataSourceId(null);
        task.setAirflowEnabled(true);
        task.setAirflowDagId("legacy-txt-dag");
        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("_filePath", java.nio.file.Files.createTempFile("legacy-txt", ".xlsx.enc").toString());
        task.setSourceConfig(sourceConfig);
        task.setAddaxJobPath("/tmp/legacy-job.json");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());
        when(airflowAdapter.isEnabled()).thenReturn(true);
        when(addaxJobService.createJobFromTask(any(IngestionTask.class), any(), any(), any(), any())).thenReturn(
            new AddaxJobService.AddaxJobResult("legacy-txt-job.json", "/tmp/legacy-txt-job.json", Map.of())
        );
        when(addaxJobService.splitJobIntoPerTableFiles(anyString())).thenReturn(List.of());
        when(addaxJobService.toContainerJobPath(anyString())).thenReturn("/opt/addax/job.json");
        when(airflowDagService.rebuildDagForTask(any(IngestionTask.class), anyList())).thenReturn("legacy-txt-dag");
        when(airflowAdapter.triggerIfRequested(any(), anyMap(), eq(true))).thenReturn(
            Map.of("status", "triggered", "dagRunId", "run-001")
        );

        IngestionExecutionDTO result = ingestionTaskService.execute(taskId);
        assertThat(result).isNotNull();

        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        verify(addaxJobService).createJobFromTask(any(IngestionTask.class), any(), any(), any(), any());
        verify(targetTableProvisioner, never()).ensureTargetTables(any(), any(), any());
    }

    @Test
    void validateAsyncExecutionRequest_shouldRejectWhenLatestExecutionStillRunning() {
        Long taskId = 1L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");

        IngestionExecution latestExecution = new IngestionExecution();
        latestExecution.setStatus("running");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(executionRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId)).thenReturn(Optional.of(latestExecution));

        assertThatThrownBy(() -> ingestionTaskService.validateAsyncExecutionRequest(taskId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("任务仍在运行中");
    }

    @Test
    void validateAsyncRetryRequest_shouldRejectUnsupportedRetryMode() {
        Long taskId = 1L;
        Long executionId = 9L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);

        IngestionExecution execution = new IngestionExecution();
        execution.setId(executionId);
        execution.setTask(task);
        execution.setStatus("failed");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));

        assertThatThrownBy(() -> ingestionTaskService.validateAsyncRetryRequest(taskId, executionId, "BAD_MODE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported retry mode");
    }

    @Test
    void rebuildDag_shouldPreheatRebuiltDag() {
        Long taskId = 1L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        task.setAirflowEnabled(true);
        task.setAirflowDagId("old-dag");

        IngestionTaskDTO dto = createTestTaskDTO();
        dto.setId(taskId);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(airflowDagService.rebuildDagForTask(task)).thenReturn("rebuilt-dag");
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toDto(task)).thenReturn(dto);

        IngestionTaskDTO result = ingestionTaskService.rebuildDag(taskId);

        assertThat(result).isNotNull();
        assertThat(task.getAirflowDagId()).isEqualTo("rebuilt-dag");
        verify(taskRepository).save(task);
        verify(dagPreheatService).preheatDag("rebuilt-dag");
    }

    @Test
    void validateExcelFormulaOrFail_shouldUseSourceSheetSelector() throws Exception {
        IngestionTask task = createTestTaskEntity();
        task.setId(1L);
        task.setSourceType("excelreader");
        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("_filePath", "/tmp/test.xlsx");
        sourceConfig.put("_sourceSheet", "Sheet1");
        task.setSourceConfig(sourceConfig);

        byte[] plain = new byte[] { 1, 2, 3 };
        when(fileUploadService.readPlainBytes(any(java.nio.file.Path.class))).thenReturn(plain);

        Method method = IngestionTaskService.class.getDeclaredMethod("validateExcelFormulaOrFail", IngestionTask.class);
        method.setAccessible(true);
        method.invoke(ingestionTaskService, task);

        verify(fileUploadService).readPlainBytes(any(java.nio.file.Path.class));
        verify(excelParseService).validateFormulaCells(eq(plain), eq("Sheet1"));
        verify(excelParseService, never()).validateFormulaCells(eq(plain));
    }

    @Test
    void validateExcelFormulaOrFail_shouldValidateAllSheetsWhenSelectorMissing() throws Exception {
        IngestionTask task = createTestTaskEntity();
        task.setId(2L);
        task.setSourceType("excelreader");
        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("_filePath", "/tmp/test.xlsx");
        task.setSourceConfig(sourceConfig);

        byte[] plain = new byte[] { 4, 5, 6 };
        when(fileUploadService.readPlainBytes(any(java.nio.file.Path.class))).thenReturn(plain);

        Method method = IngestionTaskService.class.getDeclaredMethod("validateExcelFormulaOrFail", IngestionTask.class);
        method.setAccessible(true);
        method.invoke(ingestionTaskService, task);

        verify(fileUploadService).readPlainBytes(any(java.nio.file.Path.class));
        verify(excelParseService).validateFormulaCells(eq(plain));
        verify(excelParseService, never()).validateFormulaCells(eq(plain), anyString());
    }

    // Helper methods
    private IngestionTaskDTO createTestTaskDTO() {
        IngestionTaskDTO dto = new IngestionTaskDTO();
        dto.setName("test-task");
        dto.setSourceType("mysqlreader");
        dto.setSourceDataSourceId(TEST_SOURCE_ID);
        dto.setDestinationType("postgresqlwriter");
        dto.setSyncMode("full_refresh");

        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("host", "localhost");
        dto.setSourceConfig(sourceConfig);

        return dto;
    }

    private IngestionTask createTestTaskEntity() {
        IngestionTask task = new IngestionTask();
        task.setName("test-task");
        task.setSourceType("mysqlreader");
        task.setDestinationType("postgresqlwriter");
        task.setSyncMode("full_refresh");
        task.setStatus("draft");

        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("host", "localhost");
        task.setSourceConfig(sourceConfig);

        return task;
    }
}
