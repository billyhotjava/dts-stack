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
import com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier;
import com.yuzhi.dts.ingestion.service.etl.CsvParseService;
import com.yuzhi.dts.ingestion.service.etl.FileUploadService;
import com.yuzhi.dts.ingestion.service.etl.TargetTableProvisioner;
import com.yuzhi.dts.ingestion.service.etl.IncrementalSyncService;
import com.yuzhi.dts.ingestion.service.etl.IngestionRetryService;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import com.yuzhi.dts.ingestion.service.etl.api.ApiIngestionExecutor;
import com.yuzhi.dts.ingestion.service.etl.api.ApiIngestionResult;
import com.yuzhi.dts.ingestion.service.etl.api.ApiHttpException;
import com.yuzhi.dts.ingestion.service.etl.connector.ExecutionPlan;
import com.yuzhi.dts.ingestion.service.etl.connector.SourceConnector;
import com.yuzhi.dts.ingestion.service.etl.connector.SourceConnectorContext;
import com.yuzhi.dts.ingestion.service.etl.connector.SourceConnectorRegistry;
import com.yuzhi.dts.ingestion.service.mapper.IngestionExecutionMapper;
import com.yuzhi.dts.ingestion.service.mapper.IngestionTaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Method;
import java.time.Instant;
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
    private SourceConnectorRegistry sourceConnectorRegistry;

    @Mock
    private ApiIngestionExecutor apiIngestionExecutor;

    @Mock
    private IngestionClassificationSealGuard classificationSealGuard;

    @Mock
    private jakarta.persistence.EntityManager entityManager;

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
            sourceConnectorRegistry,
            apiIngestionExecutor,
            classificationSealGuard,
            entityManager,
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
        org.mockito.Mockito.lenient().when(platformInfraClient.syncIngestionExecutionLineage(any(), any())).thenReturn(true);
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
        assertThat(entity.getStatus()).isEqualTo("draft");
    }

    @Test
    void create_shouldValidateActiveTaskBeforeSaving() {
        IngestionTaskDTO dto = createTestTaskDTO();
        dto.setStatus("active");
        dto.setClassificationSeal(createValidClassificationSeal());
        IngestionTask entity = createTestTaskEntity();

        when(taskMapper.toEntity(dto)).thenReturn(entity);
        doThrow(new IllegalStateException("CLASSIFICATION_SEAL_INVALID"))
            .when(classificationSealGuard)
            .requireProductionSeal(any(IngestionTask.class));

        assertThatThrownBy(() -> ingestionTaskService.create(dto))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CLASSIFICATION_SEAL_INVALID");

        verify(classificationSealGuard).requireProductionSeal(any(IngestionTask.class));
        verify(taskRepository, never()).save(any(IngestionTask.class));
        verifyNoInteractions(addaxJobService);
    }

    @Test
    void create_shouldVerifyManagedFileBeforeAnyActiveTaskSideEffect() {
        String fileHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        ObjectNode seal = createValidClassificationSeal();
        seal.put("fileId", "file-001");
        seal.put("fileSubjectKey", "ingestion-upload:file-001");
        seal.put("fileChecksum", fileHash);
        ObjectNode sourceConfig = objectMapper.createObjectNode().put("_fileId", "file-001");
        IngestionTaskDTO dto = createTestTaskDTO();
        dto.setStatus("active");
        dto.setSourceType("csv");
        dto.setSourceDataSourceId(null);
        dto.setSourceConfig(sourceConfig);
        dto.setClassificationSeal(seal);
        dto.setFieldClassifications(objectMapper.createObjectNode());
        IngestionTask entity = createTestTaskEntity();
        entity.setSourceType("csv");
        entity.setSourceDataSourceId(null);
        entity.setSourceConfig(sourceConfig);
        entity.setClassificationSeal(seal);
        entity.setFieldClassifications(objectMapper.createObjectNode());

        when(taskMapper.toEntity(dto)).thenReturn(entity);
        when(fileUploadService.verifyManagedUpload("file-001", fileHash))
            .thenThrow(new IllegalStateException("FILE_CHECKSUM_MISMATCH"));

        assertThatThrownBy(() -> ingestionTaskService.create(dto))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("FILE_CHECKSUM_MISMATCH");

        verify(fileUploadService).verifyManagedUpload("file-001", fileHash);
        verifyNoInteractions(addaxJobService, airflowDagService, dagPreheatService);
        verify(taskRepository, never()).save(any(IngestionTask.class));
    }

    @Test
    void create_shouldNotPreheatDraftWithProvidedDagId() {
        IngestionTaskDTO dto = createTestTaskDTO();
        dto.setStatus("draft");
        dto.setAirflowDagId("provided-draft-dag");
        IngestionTask entity = createTestTaskEntity();
        entity.setId(1L);
        entity.setCreatedBy("admin");
        entity.setAirflowDagId("provided-draft-dag");

        when(taskMapper.toEntity(dto)).thenReturn(entity);
        when(taskRepository.save(entity)).thenReturn(entity);
        when(taskMapper.toDto(entity)).thenReturn(dto);

        ingestionTaskService.create(dto, null, true);

        verify(dagPreheatService, never()).preheatDag(anyString());
        verify(airflowDagService, never()).ensureDagForTask(any(), anyList());
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

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(existingTask));
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
    void update_shouldRejectDraftToActiveBypass() {
        Long taskId = 1L;
        IngestionTaskDTO dto = createTestTaskDTO();
        dto.setId(taskId);
        dto.setStatus("active");
        IngestionTask existingTask = createTestTaskEntity();
        existingTask.setId(taskId);
        existingTask.setStatus("draft");

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(existingTask));

        assertThatThrownBy(() -> ingestionTaskService.update(taskId, dto))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("admit");

        assertThat(existingTask.getStatus()).isEqualTo("draft");
        verify(taskMapper, never()).partialUpdate(existingTask, dto);
        verify(taskRepository, never()).save(existingTask);
    }

    @Test
    void update_shouldRejectActiveTaskWhenSealBecomesInvalid() {
        Long taskId = 1L;
        IngestionTaskDTO dto = createTestTaskDTO();
        dto.setId(taskId);
        dto.setStatus("active");
        IngestionTask existingTask = createTestTaskEntity();
        existingTask.setId(taskId);
        existingTask.setStatus("active");
        existingTask.setClassificationSeal(createValidClassificationSeal());

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(existingTask));
        doAnswer(invocation -> {
            IngestionTask target = invocation.getArgument(0);
            target.setClassificationSeal(null);
            return null;
        }).when(taskMapper).partialUpdate(existingTask, dto);
        assertThatThrownBy(() -> ingestionTaskService.update(taskId, dto))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("classification evidence");

        verify(classificationSealGuard, never()).requireProductionSeal(existingTask);
        verifyNoInteractions(addaxJobService);
        verify(taskRepository, never()).save(existingTask);
    }

    @Test
    void update_shouldRejectActiveTaskWhenClassificationEvidenceChanges() {
        Long taskId = 1L;
        ObjectNode existingSeal = createValidClassificationSeal();
        ObjectNode existingFields = objectMapper.createObjectNode().put("customer_id", "INTERNAL");
        ObjectNode replacementSeal = existingSeal.deepCopy().put("sealId", "replacement-seal");
        ObjectNode replacementFields = objectMapper.createObjectNode().put("customer_id", "CONFIDENTIAL");
        IngestionTaskDTO dto = createTestTaskDTO();
        dto.setId(taskId);
        dto.setStatus("active");
        dto.setClassificationSeal(replacementSeal);
        dto.setFieldClassifications(replacementFields);
        IngestionTask existingTask = createTestTaskEntity();
        existingTask.setId(taskId);
        existingTask.setStatus("active");
        existingTask.setClassificationSeal(existingSeal);
        existingTask.setFieldClassifications(existingFields);

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(existingTask));
        doAnswer(invocation -> {
            IngestionTask target = invocation.getArgument(0);
            IngestionTaskDTO update = invocation.getArgument(1);
            target.setStatus(update.getStatus());
            target.setClassificationSeal(update.getClassificationSeal());
            target.setFieldClassifications(update.getFieldClassifications());
            return null;
        }).when(taskMapper).partialUpdate(existingTask, dto);

        assertThatThrownBy(() -> ingestionTaskService.update(taskId, dto))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("classification evidence");

        verify(classificationSealGuard, never()).requireProductionSeal(existingTask);
        verifyNoInteractions(addaxJobService);
        verify(taskRepository, never()).save(existingTask);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "sourceConfig",
        "sourceDataSourceId",
        "destinationConfig",
        "destinationType",
        "tableMapping",
        "addaxConfig",
        "syncMode",
        "syncConfig",
        "sourceType",
        "syncSchedule",
        "graphDsl",
        "airflowEnabled",
        "airflowDagId",
        "dbtModelSelector",
        "dbtDagSelector",
        "qualityPreCheckEnabled",
        "stagingTableName",
        "preCheckStatus"
    })
    void update_shouldRejectActiveExecutionConfigChangesUntilTaskReturnsToDraft(String changedField) {
        Long taskId = 1L;
        ObjectNode seal = createValidClassificationSeal();
        ObjectNode fields = objectMapper.createObjectNode().put("customer_id", "INTERNAL");
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        task.setSourceDataSourceId(TEST_SOURCE_ID);
        task.setClassificationSeal(seal);
        task.setFieldClassifications(fields);
        task.setDestinationConfig(objectMapper.createObjectNode().put("host", "old-target"));
        task.setTableMapping(objectMapper.createArrayNode().addObject().put("source", "old").put("target", "old"));
        task.setAddaxConfig(objectMapper.createObjectNode().put("speed", 1));

        IngestionTaskDTO dto = new IngestionTaskDTO();
        dto.setId(taskId);
        dto.setName(task.getName());
        dto.setStatus("active");
        dto.setSourceType(task.getSourceType());
        dto.setSourceDataSourceId(TEST_SOURCE_ID);
        dto.setClassificationSeal(seal);
        dto.setFieldClassifications(fields);

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));
        doAnswer(invocation -> {
            IngestionTask target = invocation.getArgument(0);
            target.setStatus("active");
            target.setClassificationSeal(seal);
            target.setFieldClassifications(fields);
            switch (changedField) {
                case "sourceConfig" -> target.setSourceConfig(objectMapper.createObjectNode().put("host", "new-source"));
                case "sourceDataSourceId" -> target.setSourceDataSourceId(UUID.randomUUID());
                case "destinationConfig" -> target.setDestinationConfig(objectMapper.createObjectNode().put("host", "new-target"));
                case "destinationType" -> target.setDestinationType("mysqlwriter");
                case "tableMapping" -> target.setTableMapping(
                    objectMapper.createArrayNode().addObject().put("source", "new").put("target", "new")
                );
                case "addaxConfig" -> target.setAddaxConfig(objectMapper.createObjectNode().put("speed", 2));
                case "syncMode" -> target.setSyncMode("incremental");
                case "syncConfig" -> target.setSyncConfig(objectMapper.createObjectNode().put("cursor", "updated_at"));
                case "sourceType" -> target.setSourceType("postgresqlreader");
                case "syncSchedule" -> target.setSyncSchedule("0 */5 * * * ?");
                case "graphDsl" -> target.setGraphDsl(objectMapper.createObjectNode().put("node", "changed"));
                case "airflowEnabled" -> target.setAirflowEnabled(true);
                case "airflowDagId" -> target.setAirflowDagId("forged-active-dag");
                case "dbtModelSelector" -> target.setDbtModelSelector("tag:changed");
                case "dbtDagSelector" -> target.setDbtDagSelector("changed_dag");
                case "qualityPreCheckEnabled" -> target.setQualityPreCheckEnabled(true);
                case "stagingTableName" -> target.setStagingTableName("stg_changed");
                case "preCheckStatus" -> target.setPreCheckStatus("PASSED");
                default -> throw new IllegalArgumentException("Unsupported test field: " + changedField);
            }
            return null;
        }).when(taskMapper).partialUpdate(task, dto);

        assertThatThrownBy(() -> ingestionTaskService.update(taskId, dto))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("draft");

        verifyNoInteractions(addaxJobService, airflowDagService, dagPreheatService);
        verify(taskRepository, never()).save(any(IngestionTask.class));
    }

    @Test
    void update_shouldAllowEvidenceChangeOnlyWhenMovingActiveTaskToDraft() {
        Long taskId = 1L;
        ObjectNode existingSeal = createValidClassificationSeal();
        ObjectNode replacementSeal = existingSeal.deepCopy().put("sealId", "replacement-seal");
        ObjectNode replacementFields = objectMapper.createObjectNode().put("customer_id", "CONFIDENTIAL");
        IngestionTaskDTO dto = createTestTaskDTO();
        dto.setId(taskId);
        dto.setStatus("draft");
        dto.setClassificationSeal(replacementSeal);
        dto.setFieldClassifications(replacementFields);
        ObjectNode replacementSourceConfig = objectMapper.createObjectNode().put("host", "draft-host");
        dto.setSourceConfig(replacementSourceConfig);
        IngestionTask existingTask = createTestTaskEntity();
        existingTask.setId(taskId);
        existingTask.setStatus("active");
        existingTask.setClassificationSeal(existingSeal);
        existingTask.setFieldClassifications(objectMapper.createObjectNode().put("customer_id", "INTERNAL"));
        existingTask.setAirflowEnabled(true);
        existingTask.setAirflowDagId("active-task-dag");

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(existingTask));
        doAnswer(invocation -> {
            IngestionTask target = invocation.getArgument(0);
            IngestionTaskDTO update = invocation.getArgument(1);
            target.setStatus(update.getStatus());
            target.setClassificationSeal(update.getClassificationSeal());
            target.setFieldClassifications(update.getFieldClassifications());
            target.setSourceConfig(update.getSourceConfig());
            target.setAirflowDagId("");
            return null;
        }).when(taskMapper).partialUpdate(existingTask, dto);
        when(addaxJobService.createJobFromTask(existingTask)).thenReturn(
            new AddaxJobService.AddaxJobResult("draft-job.json", "/tmp/draft-job.json", Map.of())
        );
        when(taskRepository.save(existingTask)).thenReturn(existingTask);
        when(taskMapper.toDto(existingTask)).thenReturn(dto);

        IngestionTaskDTO result = ingestionTaskService.update(taskId, dto);

        assertThat(result).isSameAs(dto);
        assertThat(existingTask.getStatus()).isEqualTo("draft");
        assertThat(existingTask.getClassificationSeal()).isEqualTo(replacementSeal);
        ArgumentCaptor<IngestionTask> deletedDagTask = ArgumentCaptor.forClass(IngestionTask.class);
        verify(airflowDagService).deleteDagForTask(deletedDagTask.capture());
        assertThat(deletedDagTask.getValue()).isNotSameAs(existingTask);
        assertThat(deletedDagTask.getValue().getAirflowDagId()).isEqualTo("active-task-dag");
        assertThat(existingTask.getAirflowDagId()).isNull();
        verify(addaxJobService).createJobFromTask(existingTask);
        assertThat(existingTask.getAddaxJobPath()).isEqualTo("/tmp/draft-job.json");
        verify(airflowDagService, never()).ensureDagForTask(any(), anyList());
        verify(dagPreheatService, never()).preheatDag(anyString());
    }

    @Test
    void admit_shouldAtomicallyActivateDraftWithValidSeal() {
        Long taskId = 1L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("draft");
        ObjectNode seal = createValidClassificationSeal();
        ObjectNode fields = objectMapper.createObjectNode().put("customer_id", "INTERNAL");
        task.setClassificationSeal(seal);
        task.setFieldClassifications(fields);
        IngestionTaskDTO admitted = createTestTaskDTO();
        admitted.setId(taskId);
        admitted.setStatus("active");
        admitted.setClassificationSeal(seal);
        admitted.setFieldClassifications(fields);

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));
        when(addaxJobService.createJobFromTask(task, null, null, null, null)).thenReturn(
            new AddaxJobService.AddaxJobResult("job.json", "/tmp/job.json", Map.of())
        );
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toDto(task)).thenReturn(admitted);

        IngestionTaskDTO result = ingestionTaskService.admit(taskId, seal, fields);

        assertThat(result.getStatus()).isEqualTo("active");
        assertThat(task.getStatus()).isEqualTo("active");
        assertThat(task.getClassificationSeal()).isEqualTo(seal);
        assertThat(task.getFieldClassifications()).isEqualTo(fields);
        ArgumentCaptor<IngestionTask> validationCaptor = ArgumentCaptor.forClass(IngestionTask.class);
        verify(classificationSealGuard, times(2)).requireProductionSeal(validationCaptor.capture());
        IngestionTask validationCandidate = validationCaptor.getAllValues().get(0);
        assertThat(validationCandidate).isNotSameAs(task);
        assertThat(validationCandidate.getClassificationSeal()).isEqualTo(seal);
        assertThat(validationCandidate.getFieldClassifications()).isEqualTo(fields);
        assertThat(validationCaptor.getAllValues().get(1)).isSameAs(task);
        verify(taskRepository, times(2)).save(task);
    }

    @Test
    void admit_shouldCreateAndPreheatDagOnce() {
        Long taskId = 1L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("draft");
        task.setAirflowEnabled(true);
        ObjectNode seal = createValidClassificationSeal();
        ObjectNode fields = objectMapper.createObjectNode().put("customer_id", "INTERNAL");
        task.setClassificationSeal(seal);
        task.setFieldClassifications(fields);
        IngestionTaskDTO admitted = createTestTaskDTO();
        admitted.setId(taskId);
        admitted.setStatus("active");
        admitted.setAirflowDagId("admitted-dag");

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));
        when(addaxJobService.createJobFromTask(task, null, null, null, null)).thenReturn(
            new AddaxJobService.AddaxJobResult("job.json", "/tmp/job.json", Map.of())
        );
        when(taskRepository.save(task)).thenReturn(task);
        when(airflowAdapter.isEnabled()).thenReturn(true);
        when(addaxJobService.splitJobIntoPerTableFiles("/tmp/job.json")).thenReturn(List.of());
        when(airflowDagService.ensureDagForTask(task, List.of())).thenReturn("admitted-dag");
        when(taskMapper.toDto(task)).thenReturn(admitted);

        IngestionTaskDTO result = ingestionTaskService.admit(taskId, seal, fields);

        assertThat(result.getAirflowDagId()).isEqualTo("admitted-dag");
        assertThat(task.getAirflowDagId()).isEqualTo("admitted-dag");
        verify(airflowDagService, times(1)).ensureDagForTask(task, List.of());
        verify(dagPreheatService, times(1)).preheatDag("admitted-dag");
    }

    @Test
    void admit_shouldResolveManagedFileByIdAndReplaceClientPathsBeforeJobGeneration() {
        Long taskId = 1L;
        String fileHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        ObjectNode seal = createValidClassificationSeal();
        seal.put("fileId", "file-001");
        seal.put("fileSubjectKey", "ingestion-upload:file-001");
        seal.put("fileChecksum", fileHash);
        ObjectNode fields = objectMapper.createObjectNode().put("customer_id", "INTERNAL");
        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("_fileId", "file-001");
        sourceConfig.put("_filePath", "/tmp/client-controlled.csv.enc");
        sourceConfig.put("_containerPath", "/tmp/client-controlled.csv.enc");
        sourceConfig.put("hostPath", "/tmp/client-controlled-host.csv.enc");
        sourceConfig.put("filePath", "/tmp/client-controlled-file.csv.enc");
        sourceConfig.put("path", "/tmp/client-controlled-path.csv.enc");
        sourceConfig.put("containerPath", "/tmp/client-controlled-container.csv.enc");
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("draft");
        task.setSourceType("csv");
        task.setSourceDataSourceId(null);
        task.setSourceConfig(sourceConfig);
        task.setClassificationSeal(seal);
        task.setFieldClassifications(fields);
        IngestionTaskDTO admitted = createTestTaskDTO();
        admitted.setId(taskId);
        admitted.setStatus("active");

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));
        when(fileUploadService.verifyManagedUpload("file-001", fileHash)).thenReturn(
            new FileUploadService.ManagedUpload(
                "file-001",
                "/srv/addax/uploads/file-001.csv.enc",
                "/opt/addax/jobs/uploads/file-001.csv.enc",
                fileHash
            )
        );
        when(addaxJobService.createJobFromTask(task, null, null, null, null)).thenReturn(
            new AddaxJobService.AddaxJobResult("job.json", "/tmp/job.json", Map.of())
        );
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toDto(task)).thenReturn(admitted);

        ingestionTaskService.admit(taskId, seal, fields);

        assertThat(task.getSourceConfig().path("_filePath").asText())
            .isEqualTo("/srv/addax/uploads/file-001.csv.enc");
        assertThat(task.getSourceConfig().path("_containerPath").asText())
            .isEqualTo("/opt/addax/jobs/uploads/file-001.csv.enc");
        assertThat(task.getSourceConfig().path("_fileHash").asText()).isEqualTo(fileHash);
        assertThat(task.getSourceConfig().path("_encrypted").asBoolean()).isTrue();
        assertThat(task.getSourceConfig().has("hostPath")).isFalse();
        assertThat(task.getSourceConfig().has("filePath")).isFalse();
        assertThat(task.getSourceConfig().has("path")).isFalse();
        assertThat(task.getSourceConfig().has("containerPath")).isFalse();
        InOrder order = inOrder(fileUploadService, addaxJobService);
        order.verify(fileUploadService).verifyManagedUpload("file-001", fileHash);
        order.verify(addaxJobService).createJobFromTask(task, null, null, null, null);
    }

    @Test
    void admit_shouldRejectFileTaskWithoutManagedFileIdBeforeSavingOrGeneratingJob() {
        Long taskId = 1L;
        ObjectNode seal = createValidClassificationSeal();
        seal.put("fileId", "file-001");
        seal.put("fileSubjectKey", "ingestion-upload:file-001");
        seal.put("fileChecksum", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        ObjectNode fields = objectMapper.createObjectNode().put("customer_id", "INTERNAL");
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("draft");
        task.setSourceType("csv");
        task.setSourceDataSourceId(null);
        task.setClassificationSeal(seal);
        task.setFieldClassifications(fields);

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> ingestionTaskService.admit(taskId, seal, fields))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("_fileId");

        assertThat(task.getStatus()).isEqualTo("draft");
        verifyNoInteractions(fileUploadService, addaxJobService, airflowDagService, dagPreheatService);
        verify(taskRepository, never()).save(any(IngestionTask.class));
    }

    @Test
    void admit_shouldRejectWhenManagedFileIdDoesNotMatchSealedFileIdentity() {
        Long taskId = 1L;
        ObjectNode seal = createValidClassificationSeal();
        seal.put("fileId", "file-001");
        seal.put("fileSubjectKey", "ingestion-upload:file-001");
        seal.put("fileChecksum", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        ObjectNode fields = objectMapper.createObjectNode();
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("draft");
        task.setSourceType("csv");
        task.setSourceDataSourceId(null);
        task.setSourceConfig(objectMapper.createObjectNode().put("_fileId", "file-002"));
        task.setClassificationSeal(seal);
        task.setFieldClassifications(fields);

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> ingestionTaskService.admit(taskId, seal, fields))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("FILE_UPLOAD_EVIDENCE_MISMATCH");

        verifyNoInteractions(fileUploadService, addaxJobService, airflowDagService, dagPreheatService);
        verify(taskRepository, never()).save(any(IngestionTask.class));
    }

    @Test
    void admit_shouldLeaveDraftUntouchedWhenSealIsInvalid() {
        Long taskId = 1L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("draft");
        ObjectNode invalidSeal = objectMapper.createObjectNode().put("sealId", "invalid");
        ObjectNode fields = objectMapper.createObjectNode().put("customer_id", "INTERNAL");
        task.setClassificationSeal(invalidSeal);
        task.setFieldClassifications(fields);

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));
        doThrow(new IllegalStateException("CLASSIFICATION_SEAL_INVALID"))
            .when(classificationSealGuard)
            .requireProductionSeal(any(IngestionTask.class));

        assertThatThrownBy(() -> ingestionTaskService.admit(taskId, invalidSeal, fields))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CLASSIFICATION_SEAL_INVALID");

        assertThat(task.getStatus()).isEqualTo("draft");
        assertThat(task.getClassificationSeal()).isSameAs(invalidSeal);
        assertThat(task.getFieldClassifications()).isSameAs(fields);
        verify(taskRepository, never()).save(any(IngestionTask.class));
    }

    @Test
    void admit_shouldRejectStaleEvidenceWithoutMutatingDraftOrCreatingRuntimeArtifacts() {
        Long taskId = 1L;
        ObjectNode storedSeal = createValidClassificationSeal();
        ObjectNode storedFields = objectMapper.createObjectNode().put("customer_id", "INTERNAL");
        ObjectNode staleSeal = storedSeal.deepCopy().put("sealId", "stale-seal");
        ObjectNode staleFields = objectMapper.createObjectNode().put("customer_id", "CONFIDENTIAL");
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("draft");
        task.setClassificationSeal(storedSeal);
        task.setFieldClassifications(storedFields);

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> ingestionTaskService.admit(taskId, staleSeal, staleFields))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CLASSIFICATION_SEAL_STALE");

        assertThat(task.getStatus()).isEqualTo("draft");
        assertThat(task.getClassificationSeal()).isSameAs(storedSeal);
        assertThat(task.getFieldClassifications()).isSameAs(storedFields);
        verifyNoInteractions(classificationSealGuard, addaxJobService, airflowDagService, dagPreheatService);
        verify(taskRepository, never()).save(any(IngestionTask.class));
    }

    @Test
    void admit_shouldAllowIdempotentReplayForActiveTask() {
        Long taskId = 1L;
        ObjectNode seal = createValidClassificationSeal();
        ObjectNode fields = objectMapper.createObjectNode().put("customer_id", "INTERNAL");
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        task.setClassificationSeal(seal);
        task.setFieldClassifications(fields);
        IngestionTaskDTO admitted = createTestTaskDTO();
        admitted.setId(taskId);
        admitted.setStatus("active");
        admitted.setClassificationSeal(seal);
        admitted.setFieldClassifications(fields);

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));
        when(taskMapper.toDto(task)).thenReturn(admitted);

        IngestionTaskDTO result = ingestionTaskService.admit(taskId, seal, fields);

        assertThat(result.getStatus()).isEqualTo("active");
        verify(classificationSealGuard).requireProductionSeal(task);
        verify(taskRepository, never()).save(any(IngestionTask.class));
    }

    @Test
    void admit_shouldRejectReplacingSealOnActiveTask() {
        Long taskId = 1L;
        ObjectNode existingSeal = createValidClassificationSeal();
        ObjectNode existingFields = objectMapper.createObjectNode().put("customer_id", "INTERNAL");
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        task.setClassificationSeal(existingSeal);
        task.setFieldClassifications(existingFields);
        ObjectNode replacementSeal = existingSeal.deepCopy();
        replacementSeal.put("sealId", "seal-replacement");
        ObjectNode replacementFields = objectMapper.createObjectNode().put("customer_id", "CONFIDENTIAL");

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> ingestionTaskService.admit(taskId, replacementSeal, replacementFields))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already active");

        assertThat(task.getClassificationSeal()).isSameAs(existingSeal);
        assertThat(task.getFieldClassifications()).isSameAs(existingFields);
        verify(taskRepository, never()).save(any(IngestionTask.class));
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
        task.setStatus("active");
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
    void execute_shouldRevalidateActiveStateBeforeAsyncTriggerPhase() {
        Long taskId = 1L;
        IngestionTask activeTask = createTestTaskEntity();
        activeTask.setId(taskId);
        activeTask.setStatus("active");
        activeTask.setClassificationSeal(createValidClassificationSeal());
        activeTask.setFieldClassifications(objectMapper.createObjectNode());
        IngestionTask draftTask = createTestTaskEntity();
        draftTask.setId(taskId);
        draftTask.setStatus("draft");
        draftTask.setClassificationSeal(activeTask.getClassificationSeal());
        draftTask.setFieldClassifications(activeTask.getFieldClassifications());

        when(taskRepository.findById(taskId))
            .thenReturn(Optional.of(activeTask), Optional.of(draftTask));
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());

        ingestionTaskService.execute(taskId);
        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        ArgumentCaptor<IngestionExecution> executions = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository, atLeast(2)).save(executions.capture());
        assertThat(executions.getAllValues()).anyMatch(execution -> "failed".equals(execution.getStatus()));
        assertThat(executions.getAllValues()).anyMatch(
            execution -> execution.getErrorMessage() != null
                && execution.getErrorMessage().contains("executable status")
        );
        verify(classificationSealGuard).requireProductionSeal(activeTask);
        verifyNoInteractions(addaxJobService, airflowAdapter, airflowDagService, targetTableProvisioner);
    }

    @Test
    void execute_shouldRefreshActiveStateAfterGovernanceQueueWait() {
        Long taskId = 1L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        task.setClassificationSeal(createValidClassificationSeal());
        task.setFieldClassifications(objectMapper.createObjectNode());
        ObjectNode governance = objectMapper.createObjectNode();
        governance.put("maxConcurrentRuns", 1);
        governance.put("rejectPolicy", "QUEUE");
        task.setSyncConfig(objectMapper.createObjectNode().set("governance", governance));

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        when(executionRepository.countByTaskIdAndStatusesIgnoreCase(taskId, List.of("running", "preparing")))
            .thenReturn(1L);
        when(executionRepository.findByStatusesIgnoreCase(List.of("running", "preparing")))
            .thenReturn(List.of());
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());
        doAnswer(invocation -> {
            task.setStatus("draft");
            return null;
        }).when(entityManager).refresh(task, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        ingestionTaskService.execute(taskId);
        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        verify(entityManager).refresh(task, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        ArgumentCaptor<IngestionExecution> executions = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository, atLeast(2)).save(executions.capture());
        assertThat(executions.getAllValues()).anyMatch(execution -> "failed".equals(execution.getStatus()));
        verifyNoInteractions(addaxJobService, airflowAdapter, airflowDagService, targetTableProvisioner);
    }

    @Test
    void execute_shouldReverifyManagedFileBeforeRuntimeJobGeneration() {
        Long taskId = 1L;
        String fileHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        task.setSourceType("csv");
        task.setSourceDataSourceId(null);
        ObjectNode seal = createValidClassificationSeal();
        seal.put("fileId", "file-001");
        seal.put("fileSubjectKey", "ingestion-upload:file-001");
        seal.put("fileChecksum", fileHash);
        task.setClassificationSeal(seal);
        task.setFieldClassifications(objectMapper.createObjectNode());
        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("_fileId", "file-001");
        sourceConfig.put("_filePath", "/tmp/client-controlled.csv.enc");
        task.setSourceConfig(sourceConfig);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());
        when(fileUploadService.verifyManagedUpload("file-001", fileHash)).thenReturn(
            new FileUploadService.ManagedUpload(
                "file-001",
                "/srv/addax/uploads/file-001.csv.enc",
                "/opt/addax/jobs/uploads/file-001.csv.enc",
                fileHash
            )
        );
        when(addaxJobService.createJobFromTask(eq(task), isNull(), isNull(), anyMap(), anyMap())).thenReturn(
            new AddaxJobService.AddaxJobResult("job.json", "/tmp/job.json", Map.of())
        );
        when(taskRepository.save(task)).thenReturn(task);

        ingestionTaskService.execute(taskId);
        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        assertThat(task.getSourceConfig().path("_filePath").asText())
            .isEqualTo("/srv/addax/uploads/file-001.csv.enc");
        InOrder order = inOrder(fileUploadService, addaxJobService);
        order.verify(fileUploadService).verifyManagedUpload("file-001", fileHash);
        order.verify(addaxJobService).createJobFromTask(eq(task), isNull(), isNull(), anyMap(), anyMap());
    }

    @Test
    void execute_shouldRunApiTaskThroughConnectorRegistryAndApiExecutor() {
        Long taskId = 1L;
        UUID sourceId = UUID.randomUUID();
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        task.setAirflowEnabled(false);
        task.setSourceType("httpreader");
        task.setSourceDataSourceId(sourceId);
        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("path", "/orders");
        task.setSourceConfig(sourceConfig);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(sourceResolver.resolve(sourceId, List.of())).thenReturn(
            new com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource(
                "httpreader",
                Map.of(
                    "readerType",
                    "httpreader",
                    "connectorType",
                    "api",
                    "baseUrl",
                    "https://crm.example.test/openapi",
                    "auth",
                    Map.of("provider", "bearerToken", "tokenRef", "accessToken"),
                    "secrets",
                    Map.of("accessToken", "token-123"),
                    "defaultHeaders",
                    Map.of("X-Tenant", "demo")
                ),
                null
            )
        );
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());
        SourceConnector apiConnector = mock(SourceConnector.class);
        ExecutionPlan plan = new ExecutionPlan(
            "api-http",
            "api",
            "1.2.0",
            null,
            Map.of("sourceDataSourceId", sourceId.toString()),
            List.of(),
            new ExecutionPlan.CheckpointPolicy("none", null, "task_success"),
            Map.of("engine", "api-http")
        );
        when(sourceConnectorRegistry.find(any(SourceConnectorContext.class))).thenReturn(Optional.of(apiConnector));
        when(apiConnector.buildExecutionPlan(any(SourceConnectorContext.class))).thenReturn(plan);
        when(apiIngestionExecutor.execute(eq(plan), eq(task), any(IngestionExecution.class))).thenReturn(
            ApiIngestionResult.success(12L, 12L, Map.of("stream", "orders"))
        );

        IngestionExecutionDTO result = ingestionTaskService.execute(taskId);
        assertThat(result).isNotNull();

        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        ArgumentCaptor<SourceConnectorContext> contextCaptor = ArgumentCaptor.forClass(SourceConnectorContext.class);
        verify(sourceConnectorRegistry).find(contextCaptor.capture());
        verify(apiConnector).buildExecutionPlan(contextCaptor.getValue());
        verify(apiIngestionExecutor).execute(eq(plan), eq(task), any(IngestionExecution.class));
        verify(airflowAdapter, never()).triggerIfRequested(any(), any(), anyBoolean());
        verify(addaxJobService, never()).resolveWriterColumnsIfNeeded(anyString());

        ArgumentCaptor<IngestionExecution> executionCaptor = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository, atLeast(2)).save(executionCaptor.capture());
        assertThat(executionCaptor.getAllValues()).anySatisfy(saved -> {
            assertThat(saved.getStatus()).isEqualTo("success");
            assertThat(saved.getRowsRead()).isEqualTo(12L);
            assertThat(saved.getRowsWritten()).isEqualTo(12L);
            assertThat(saved.getEndTime()).isNotNull();
        });

        assertThat(contextCaptor.getValue().taskId()).isEqualTo(taskId);
        assertThat(contextCaptor.getValue().sourceDataSourceId()).isEqualTo(sourceId);
        assertThat(contextCaptor.getValue().sourceConfig())
            .containsEntry("baseUrl", "https://crm.example.test/openapi")
            .containsEntry("path", "/orders");
        assertThat(contextCaptor.getValue().sourceConfig().get("auth")).isEqualTo(
            Map.of("provider", "bearerToken", "tokenRef", "accessToken")
        );
        assertThat(contextCaptor.getValue().sourceConfig().get("secrets")).isEqualTo(Map.of("accessToken", "token-123"));
        assertThat(contextCaptor.getValue().sourceConfig().get("defaultHeaders")).isEqualTo(Map.of("X-Tenant", "demo"));
    }

    @Test
    void execute_shouldMarkApiExecutionFailedWhenExecutorTimesOut() {
        Long taskId = 1L;
        UUID sourceId = UUID.randomUUID();
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        task.setAirflowEnabled(false);
        task.setSourceType("httpreader");
        task.setSourceDataSourceId(sourceId);
        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("path", "/orders");
        task.setSourceConfig(sourceConfig);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(sourceResolver.resolve(sourceId, List.of())).thenReturn(
            new com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource(
                "httpreader",
                Map.of("readerType", "httpreader", "connectorType", "api", "baseUrl", "https://crm.example.test/openapi"),
                null
            )
        );
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());
        SourceConnector apiConnector = mock(SourceConnector.class);
        ExecutionPlan plan = new ExecutionPlan(
            "api-http",
            "api",
            "1.2.0",
            null,
            Map.of("sourceDataSourceId", sourceId.toString()),
            List.of(),
            new ExecutionPlan.CheckpointPolicy("none", null, "task_success"),
            Map.of("engine", "api-http")
        );
        when(sourceConnectorRegistry.find(any(SourceConnectorContext.class))).thenReturn(Optional.of(apiConnector));
        when(apiConnector.buildExecutionPlan(any(SourceConnectorContext.class))).thenReturn(plan);
        when(apiIngestionExecutor.execute(eq(plan), eq(task), any(IngestionExecution.class))).thenThrow(
            new ApiHttpException("API_RUNTIME_TIMEOUT", "API_RUNTIME_TIMEOUT: API 入湖执行超时: 1s", null, 0)
        );

        IngestionExecutionDTO result = ingestionTaskService.execute(taskId);
        assertThat(result).isNotNull();

        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        ArgumentCaptor<IngestionExecution> executionCaptor = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository, atLeast(2)).save(executionCaptor.capture());
        assertThat(executionCaptor.getAllValues()).anySatisfy(saved -> {
            assertThat(saved.getStatus()).isEqualTo("failed");
            assertThat(saved.getErrorMessage()).contains("API_RUNTIME_TIMEOUT");
            assertThat(saved.getFailureCategory()).isEqualTo("RUNTIME_ERROR");
            assertThat(saved.getEndTime()).isNotNull();
        });
        verify(apiIngestionExecutor).execute(eq(plan), eq(task), any(IngestionExecution.class));
    }

    @Test
    void execute_shouldClassifyApiNetworkFailureAndScheduleRetry() {
        Long taskId = 1L;
        UUID sourceId = UUID.randomUUID();
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        task.setAirflowEnabled(false);
        task.setSourceType("httpreader");
        task.setSourceDataSourceId(sourceId);
        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("path", "/orders");
        task.setSourceConfig(sourceConfig);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(sourceResolver.resolve(sourceId, List.of())).thenReturn(
            new com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource(
                "httpreader",
                Map.of("readerType", "httpreader", "connectorType", "api", "baseUrl", "https://crm.example.test/openapi"),
                null
            )
        );
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());
        SourceConnector apiConnector = mock(SourceConnector.class);
        ExecutionPlan plan = new ExecutionPlan(
            "api-http",
            "api",
            "1.2.0",
            null,
            Map.of("sourceDataSourceId", sourceId.toString()),
            List.of(),
            new ExecutionPlan.CheckpointPolicy("none", null, "task_success"),
            Map.of("engine", "api-http")
        );
        when(sourceConnectorRegistry.find(any(SourceConnectorContext.class))).thenReturn(Optional.of(apiConnector));
        when(apiConnector.buildExecutionPlan(any(SourceConnectorContext.class))).thenReturn(plan);
        when(apiIngestionExecutor.execute(eq(plan), eq(task), any(IngestionExecution.class))).thenThrow(
            new ApiHttpException("API_RUNTIME_NETWORK", "API_RUNTIME_NETWORK: connect timed out", null, 1)
        );

        IngestionExecutionDTO result = ingestionTaskService.execute(taskId);
        assertThat(result).isNotNull();

        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        ArgumentCaptor<IngestionExecution> executionCaptor = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository, atLeast(2)).save(executionCaptor.capture());
        assertThat(executionCaptor.getAllValues()).anySatisfy(saved -> {
            assertThat(saved.getStatus()).isEqualTo("failed");
            assertThat(saved.getErrorMessage()).contains("API_RUNTIME_NETWORK");
            assertThat(saved.getFailureCategory()).isEqualTo(ExecutionFailureClassifier.CATEGORY_CONNECTION);
            assertThat(saved.getFailureAdvice()).isEqualTo(
                ExecutionFailureClassifier.advice(ExecutionFailureClassifier.CATEGORY_CONNECTION)
            );
            assertThat(saved.getEndTime()).isNotNull();
        });

        ArgumentCaptor<IngestionExecution> retryExecutionCaptor = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(retryService).scheduleRetryIfEligible(retryExecutionCaptor.capture());
        assertThat(retryExecutionCaptor.getValue().getStatus()).isEqualTo("failed");
        assertThat(retryExecutionCaptor.getValue().getFailureCategory()).isEqualTo(
            ExecutionFailureClassifier.CATEGORY_CONNECTION
        );
        verify(apiIngestionExecutor).execute(eq(plan), eq(task), any(IngestionExecution.class));
    }

    @Test
    void executeInternalApi_shouldUseRequestBatchAndBackfillWindow() {
        Long taskId = 1L;
        UUID sourceId = UUID.randomUUID();
        Instant windowStart = Instant.parse("2026-06-01T00:00:00Z");
        Instant windowEnd = Instant.parse("2026-06-02T00:00:00Z");
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        task.setAirflowEnabled(false);
        task.setSourceType("httpreader");
        task.setSourceDataSourceId(sourceId);
        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("path", "/orders");
        task.setSourceConfig(sourceConfig);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(incrementalSyncService.validateBackfillWindow(task, "updated_at", windowStart, windowEnd)).thenReturn("updated_at");
        when(sourceResolver.resolve(sourceId, List.of())).thenReturn(
            new com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource(
                "httpreader",
                Map.of("connectorType", "api", "baseUrl", "https://crm.example.test/openapi"),
                null
            )
        );
        when(executionMapper.toDto(any(IngestionExecution.class))).thenAnswer(inv -> {
            IngestionExecution execution = inv.getArgument(0);
            IngestionExecutionDTO dto = new IngestionExecutionDTO();
            dto.setId(execution.getId());
            dto.setTaskId(taskId);
            dto.setBatchId(execution.getBatchId());
            dto.setStatus(execution.getStatus());
            dto.setTriggerMode(execution.getTriggerMode());
            dto.setBackfillWindowStart(execution.getBackfillWindowStart());
            dto.setBackfillWindowEnd(execution.getBackfillWindowEnd());
            dto.setBackfillColumn(execution.getBackfillColumn());
            return dto;
        });
        SourceConnector apiConnector = mock(SourceConnector.class);
        ExecutionPlan plan = new ExecutionPlan(
            "api-http",
            "api",
            "1.2.0",
            null,
            Map.of("sourceDataSourceId", sourceId.toString()),
            List.of(),
            new ExecutionPlan.CheckpointPolicy("updated_at", "updated_at", "per_resource"),
            Map.of("engine", "api-http")
        );
        when(sourceConnectorRegistry.find(any(SourceConnectorContext.class))).thenReturn(Optional.of(apiConnector));
        when(apiConnector.buildExecutionPlan(any(SourceConnectorContext.class))).thenReturn(plan);
        when(apiIngestionExecutor.execute(eq(plan), eq(task), any(IngestionExecution.class))).thenReturn(
            ApiIngestionResult.success(3L, 3L, Map.of("stream", "orders"))
        );
        when(platformInfraClient.syncIngestionExecutionLineage(eq(task), any(IngestionExecution.class))).thenReturn(true);

        IngestionExecutionDTO submitted = ingestionTaskService.executeInternalApi(
            taskId,
            "airflow-batch-001",
            "BACKFILL_RANGE",
            windowStart,
            windowEnd,
            "updated_at"
        );

        assertThat(submitted.getBatchId()).isEqualTo("airflow-batch-001");
        assertThat(submitted.getTriggerMode()).isEqualTo("BACKFILL_RANGE");
        assertThat(submitted.getBackfillWindowStart()).isEqualTo(windowStart);
        assertThat(submitted.getBackfillWindowEnd()).isEqualTo(windowEnd);
        assertThat(submitted.getBackfillColumn()).isEqualTo("updated_at");

        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        verify(apiIngestionExecutor).execute(eq(plan), eq(task), any(IngestionExecution.class));
        verify(airflowAdapter, never()).triggerIfRequested(any(), any(), anyBoolean());

        ArgumentCaptor<IngestionExecution> executionCaptor = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository, atLeast(2)).save(executionCaptor.capture());
        assertThat(executionCaptor.getAllValues()).anySatisfy(saved -> {
            assertThat(saved.getBatchId()).isEqualTo("airflow-batch-001");
            assertThat(saved.getTriggerMode()).isEqualTo("BACKFILL_RANGE");
            assertThat(saved.getBackfillWindowStart()).isEqualTo(windowStart);
            assertThat(saved.getBackfillWindowEnd()).isEqualTo(windowEnd);
            assertThat(saved.getBackfillColumn()).isEqualTo("updated_at");
        });
        assertThat(executionCaptor.getAllValues()).anySatisfy(saved -> {
            assertThat(saved.getStatus()).isEqualTo("success");
            assertThat(saved.getRowsRead()).isEqualTo(3L);
            assertThat(saved.getRowsWritten()).isEqualTo(3L);
        });
        org.mockito.InOrder lineageOrder = org.mockito.Mockito.inOrder(platformInfraClient);
        lineageOrder.verify(platformInfraClient).syncIngestionExecutionLineage(eq(task), any(IngestionExecution.class));
        lineageOrder.verify(platformInfraClient).emitIngestionOpenLineageEvent(eq(task), any(IngestionExecution.class));
    }

    @Test
    void syncExecutionLineage_shouldNotEmitOpenLineageWhenAuthoritativeSyncFails() throws Exception {
        IngestionTask task = createTestTaskEntity();
        task.setId(91L);
        task.setName("api-orders");
        task.setSourceType("api");
        IngestionExecution execution = new IngestionExecution();
        execution.setId(92L);
        when(platformInfraClient.syncIngestionExecutionLineage(task, execution)).thenReturn(false);

        Method method = IngestionTaskService.class.getDeclaredMethod(
            "syncExecutionLineageQuietly",
            IngestionTask.class,
            IngestionExecution.class
        );
        method.setAccessible(true);
        method.invoke(ingestionTaskService, task, execution);

        verify(platformInfraClient, never()).emitIngestionOpenLineageEvent(any(), any());
        verify(auditService).auditAction(
            eq("INGESTION_LINEAGE_SYNC"),
            eq(AuditStage.FAIL),
            eq("api-orders"),
            any(Map.class)
        );
    }

    @Test
    void executeInternalApi_shouldEnsureThinDagForAirflowEnabledApiTask() {
        Long taskId = 1L;
        UUID sourceId = UUID.randomUUID();
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setName("api-orders");
        task.setStatus("active");
        task.setSyncMode("full_refresh");
        task.setAirflowEnabled(true);
        task.setSourceType("api");
        task.setSourceDataSourceId(sourceId);
        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("path", "/orders");
        task.setSourceConfig(sourceConfig);

        when(airflowAdapter.isEnabled()).thenReturn(true);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        when(sourceResolver.resolve(sourceId, List.of())).thenReturn(
            new com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource(
                "httpreader",
                Map.of("connectorType", "api", "baseUrl", "https://crm.example.test/openapi"),
                null
            )
        );
        when(airflowDagService.rebuildDagForTask(task, Collections.emptyList())).thenReturn("thin-api-dag");
        when(executionMapper.toDto(any(IngestionExecution.class))).thenAnswer(inv -> {
            IngestionExecution execution = inv.getArgument(0);
            IngestionExecutionDTO dto = new IngestionExecutionDTO();
            dto.setId(execution.getId());
            dto.setTaskId(taskId);
            dto.setBatchId(execution.getBatchId());
            dto.setStatus(execution.getStatus());
            return dto;
        });
        SourceConnector apiConnector = mock(SourceConnector.class);
        ExecutionPlan plan = new ExecutionPlan(
            "api-http",
            "api",
            "1.2.0",
            null,
            Map.of("sourceDataSourceId", sourceId.toString()),
            List.of(),
            new ExecutionPlan.CheckpointPolicy("none", null, "task_success"),
            Map.of("engine", "api-http")
        );
        when(sourceConnectorRegistry.find(any(SourceConnectorContext.class))).thenReturn(Optional.of(apiConnector));
        when(apiConnector.buildExecutionPlan(any(SourceConnectorContext.class))).thenReturn(plan);
        when(apiIngestionExecutor.execute(eq(plan), eq(task), any(IngestionExecution.class))).thenReturn(
            ApiIngestionResult.success(2L, 2L, Map.of("stream", "orders"))
        );

        ingestionTaskService.executeInternalApi(taskId, "airflow-batch-001", "MANUAL", null, null, null);

        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        assertThat(task.getAirflowDagId()).isEqualTo("thin-api-dag");
        verify(airflowDagService).rebuildDagForTask(task, Collections.emptyList());
        verify(airflowAdapter, never()).triggerIfRequested(any(), any(), anyBoolean());
        verify(apiIngestionExecutor).execute(eq(plan), eq(task), any(IngestionExecution.class));
    }

    @Test
    void update_shouldRejectActiveApiRuntimeConfigChangeUntilTaskReturnsToDraft() {
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

        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(existingTask));
        doAnswer(inv -> {
            IngestionTask target = inv.getArgument(0);
            IngestionTaskDTO update = inv.getArgument(1);
            target.setSourceType(update.getSourceType());
            target.setSourceDataSourceId(update.getSourceDataSourceId());
            target.setSourceConfig(update.getSourceConfig());
            target.setStatus(update.getStatus());
            return null;
        }).when(taskMapper).partialUpdate(existingTask, dto);
        assertThatThrownBy(() -> ingestionTaskService.update(taskId, dto))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("draft");
        assertThat(existingTask.getStatus()).isEqualTo("active");
        assertThat(existingTask.getAddaxJobPath()).isEqualTo("/tmp/old-api-job.json");
        verify(addaxJobService, never()).createJobFromTask(any(IngestionTask.class));
        verify(airflowDagService, never()).ensureDagForTask(any(), any());
        verify(taskRepository, never()).save(any(IngestionTask.class));
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
        String fileHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        ObjectNode seal = createValidClassificationSeal();
        seal.put("fileId", "file-legacy");
        seal.put("fileSubjectKey", "ingestion-upload:file-legacy");
        seal.put("fileChecksum", fileHash);
        task.setClassificationSeal(seal);
        task.setFieldClassifications(objectMapper.createObjectNode());
        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("_fileId", "file-legacy");
        sourceConfig.put("_filePath", "/tmp/client-legacy.xlsx.enc");
        task.setSourceConfig(sourceConfig);
        task.setAddaxJobPath("/tmp/legacy-job.json");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());
        when(fileUploadService.verifyManagedUpload("file-legacy", fileHash)).thenReturn(
            new FileUploadService.ManagedUpload(
                "file-legacy",
                "/srv/addax/uploads/file-legacy.xlsx.enc",
                "/opt/addax/jobs/uploads/file-legacy.xlsx.enc",
                fileHash
            )
        );
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

        verify(fileUploadService).verifyManagedUpload("file-legacy", fileHash);
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
    void validateAsyncExecutionRequest_shouldRejectDraftBeforeSealValidation() {
        Long taskId = 1L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("draft");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> ingestionTaskService.validateAsyncExecutionRequest(taskId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("draft");

        verifyNoInteractions(classificationSealGuard);
        verify(executionRepository, never()).findFirstByTaskIdOrderByCreatedAtDesc(taskId);
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
    void validateAsyncRetryRequest_shouldRejectDraftBeforeQueueing() {
        Long taskId = 1L;
        Long executionId = 9L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("draft");
        IngestionExecution execution = new IngestionExecution();
        execution.setId(executionId);
        execution.setTask(task);
        execution.setStatus("failed");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));

        assertThatThrownBy(() -> ingestionTaskService.validateAsyncRetryRequest(taskId, executionId, "FAILED_ONLY"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("draft");

        verifyNoInteractions(classificationSealGuard);
    }

    @Test
    void validateAsyncRetryRequest_shouldRejectInvalidSealBeforeQueueing() {
        Long taskId = 1L;
        Long executionId = 9L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        IngestionExecution execution = new IngestionExecution();
        execution.setId(executionId);
        execution.setTask(task);
        execution.setStatus("failed");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));
        doThrow(new IllegalStateException("CLASSIFICATION_SEAL_REQUIRED"))
            .when(classificationSealGuard)
            .requireProductionSeal(task);

        assertThatThrownBy(() -> ingestionTaskService.validateAsyncRetryRequest(taskId, executionId, "FAILED_ONLY"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CLASSIFICATION_SEAL_REQUIRED");

        verify(classificationSealGuard).requireProductionSeal(task);
    }

    @Test
    void validateAsyncRetryRequest_shouldRejectSuccessfulExecutionForFailedOnly() {
        Long taskId = 1L;
        Long executionId = 9L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        IngestionExecution execution = new IngestionExecution();
        execution.setId(executionId);
        execution.setTask(task);
        execution.setStatus("success");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));

        assertThatThrownBy(() -> ingestionTaskService.validateAsyncRetryRequest(taskId, executionId, "FAILED_ONLY"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("仅失败执行");

        verify(executionRepository, never()).findFirstByTaskIdOrderByCreatedAtDesc(taskId);
    }

    @Test
    void retryExecution_shouldGateBeforeSuccessAuditAndExecution() {
        Long taskId = 1L;
        Long executionId = 9L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("draft");
        IngestionExecution execution = new IngestionExecution();
        execution.setId(executionId);
        execution.setTask(task);
        execution.setStatus("failed");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));

        assertThatThrownBy(() -> ingestionTaskService.retryExecution(taskId, executionId, "FAILED_ONLY"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("draft");

        verify(auditService, never()).auditAction(
            eq("INGESTION_TASK_RETRY"),
            eq(AuditStage.SUCCESS),
            anyString(),
            anyMap()
        );
        verify(executionRepository, never()).save(any(IngestionExecution.class));
        verify(taskRepository, never()).save(any(IngestionTask.class));
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
    void rebuildDag_shouldRejectDraftWithoutRebuildOrPreheat() {
        Long taskId = 1L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("draft");
        task.setAirflowEnabled(true);
        task.setAirflowDagId("draft-dag");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> ingestionTaskService.rebuildDag(taskId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("draft");

        verify(airflowDagService, never()).rebuildDagForTask(any(IngestionTask.class));
        verify(dagPreheatService, never()).preheatDag(anyString());
        verify(taskRepository, never()).save(any(IngestionTask.class));
    }

    @Test
    void rebuildApiDags_shouldForceRebuildOnlyActiveApiTasks() {
        IngestionTask apiTask = createTestTaskEntity();
        apiTask.setId(10L);
        apiTask.setName("api-orders");
        apiTask.setSourceType("api");
        apiTask.setStatus("active");
        apiTask.setAirflowEnabled(true);
        apiTask.setAirflowDagId("old-api-dag");

        IngestionTask deletedApiTask = createTestTaskEntity();
        deletedApiTask.setId(11L);
        deletedApiTask.setName("deleted-api");
        deletedApiTask.setSourceType("api");
        deletedApiTask.setStatus("deleted");
        deletedApiTask.setAirflowEnabled(true);

        IngestionTask draftApiTask = createTestTaskEntity();
        draftApiTask.setId(13L);
        draftApiTask.setName("draft-api");
        draftApiTask.setSourceType("api");
        draftApiTask.setStatus("draft");
        draftApiTask.setAirflowEnabled(true);

        IngestionTask jdbcTask = createTestTaskEntity();
        jdbcTask.setId(12L);
        jdbcTask.setName("jdbc-task");
        jdbcTask.setSourceType("mysqlreader");
        jdbcTask.setStatus("active");
        jdbcTask.setAirflowEnabled(true);

        when(taskRepository.findAll()).thenReturn(List.of(apiTask, deletedApiTask, draftApiTask, jdbcTask));
        when(airflowDagService.rebuildDagForTask(apiTask)).thenReturn("thin-api-dag");
        when(taskRepository.save(apiTask)).thenReturn(apiTask);

        Map<String, Object> result = ingestionTaskService.rebuildApiDags();

        assertThat(result)
            .containsEntry("total", 4)
            .containsEntry("migrated", 1)
            .containsEntry("skipped", 3)
            .containsEntry("failed", 0);
        assertThat(apiTask.getAirflowDagId()).isEqualTo("thin-api-dag");
        verify(airflowDagService).rebuildDagForTask(apiTask);
        verify(airflowDagService, never()).rebuildDagForTask(deletedApiTask);
        verify(airflowDagService, never()).rebuildDagForTask(draftApiTask);
        verify(airflowDagService, never()).rebuildDagForTask(jdbcTask);
        verify(taskRepository).save(apiTask);
        verify(dagPreheatService).preheatDag("thin-api-dag");
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

    private ObjectNode createValidClassificationSeal() {
        ObjectNode seal = objectMapper.createObjectNode();
        seal.put("sealId", "seal-test-001");
        seal.put("subjectType", "ASSET");
        seal.put("subjectKey", "data-source:" + TEST_SOURCE_ID);
        seal.put("effectiveLevel", "INTERNAL");
        seal.put("snapshotVersion", 1L);
        seal.put("checksum", "0123456789abcdef0123456789abcdef");
        seal.put("sealedAt", "2026-07-28T00:00:00Z");
        return seal;
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
