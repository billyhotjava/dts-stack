package com.yuzhi.dts.ingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.domain.IngestionTaskRevision;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRevisionRepository;
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
    private IngestionTaskRevisionRepository revisionRepository;

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
    private IngestionAccessContractService accessContractService;

    @Mock
    private IngestionTaskSecretMigrationService secretMigrationService;

    @Mock
    private IngestionRequiresNewExecutor requiresNewExecutor;

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
            revisionRepository,
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
        ingestionTaskService.setSecretMigrationService(secretMigrationService);
        ingestionTaskService.setRequiresNewExecutor(requiresNewExecutor);
        lenient().when(requiresNewExecutor.execute(any())).thenAnswer(invocation ->
            invocation.<java.util.function.Supplier<?>>getArgument(0).get()
        );
        lenient().doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(requiresNewExecutor).executeWithoutResult(any(Runnable.class));

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
        org.mockito.Mockito.lenient().when(executionRepository.saveAndFlush(any(IngestionExecution.class))).thenAnswer(inv -> {
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
        org.mockito.Mockito.lenient().when(entityManager.find(
            eq(IngestionExecution.class),
            any(Long.class),
            eq(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        )).thenAnswer(inv -> savedExecutions.get(inv.<Long>getArgument(1)));
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
        verify(secretMigrationService).markNewTaskClean(1L);
    }

    @Test
    void createAndUpdateShouldRejectRawCredentialsBeforePersistence() {
        IngestionTaskDTO create = createTestTaskDTO();
        create.setSourceConfig(objectMapper.createObjectNode().put("password", "raw-secret"));

        assertThatThrownBy(() -> ingestionTaskService.create(create))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("managed data source or secret reference");
        verify(taskRepository, never()).save(any());

        IngestionTaskDTO update = createTestTaskDTO();
        update.setId(9L);
        update.setDestinationConfig(objectMapper.createObjectNode().put("clientSecret", "raw-secret"));
        assertThatThrownBy(() -> ingestionTaskService.update(9L, update))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("managed data source or secret reference");
        verify(taskRepository, never()).findByIdForUpdate(9L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void managedApiCredentialsShouldOverrideLegacyTaskSecretsAtRuntime() throws Exception {
        IngestionTask task = createTestTaskEntity();
        task.setSourceType("httpreader");
        task.setSourceDataSourceId(TEST_SOURCE_ID);
        task.setSourceConfig(objectMapper.readTree("""
            {"resource":{"path":"/orders"},"secrets":{"token":"legacy-task-token"}}
            """));
        var resolved = new com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource(
            "httpreader",
            Map.of("baseUrl", "https://managed.example", "secrets", Map.of("token", "managed-runtime-token")),
            null
        );
        Method method = IngestionTaskService.class.getDeclaredMethod(
            "mergedApiSourceConfig",
            IngestionTask.class,
            com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource.class
        );
        method.setAccessible(true);

        Map<String, Object> merged = (Map<String, Object>) method.invoke(ingestionTaskService, task, resolved);

        assertThat(merged.get("baseUrl")).isEqualTo("https://managed.example");
        assertThat(merged.toString()).contains("managed-runtime-token").doesNotContain("legacy-task-token");
        assertThat(merged).containsKey("resource");
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
    void updateActiveDraftShouldNotCopyManagedSecretIntoDraftTaskSnapshot() {
        Long taskId = 2L;
        IngestionTask active = createTestTaskEntity();
        active.setId(taskId);
        active.setStatus("active");
        active.setSourceDataSourceId(TEST_SOURCE_ID);
        active.setSourceConfig(objectMapper.createObjectNode()
            .put("endpoint", "https://api.example.test")
            .set("auth", objectMapper.createObjectNode().put("clientSecret", "managed-secret").put("clientId", "old")));
        IngestionTaskDTO incoming = createTestTaskDTO();
        incoming.setId(taskId);
        incoming.setStatus("draft");
        incoming.setSourceConfig(objectMapper.createObjectNode()
            .put("endpoint", "https://api.example.test/v2")
            .set("auth", objectMapper.createObjectNode().put("clientId", "new")));

        ingestionTaskService.setAccessContractService(accessContractService);
        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(active));
        when(accessContractService.findLatestDraftRevision(taskId)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            IngestionTask target = invocation.getArgument(0);
            IngestionTaskDTO patchDto = invocation.getArgument(1);
            target.setSourceConfig(patchDto.getSourceConfig());
            target.setStatus(patchDto.getStatus());
            return null;
        }).when(taskMapper).partialUpdate(any(IngestionTask.class), eq(incoming));
        when(accessContractService.recordDraftRevision(any(IngestionTask.class), isNull(), anyBoolean()))
            .thenAnswer(invocation -> new IngestionTaskRevision());
        when(taskMapper.toDto(any(IngestionTask.class))).thenReturn(incoming);
        when(accessContractService.enrichTaskDto(incoming)).thenReturn(incoming);

        ingestionTaskService.update(taskId, incoming);

        ArgumentCaptor<IngestionTask> draft = ArgumentCaptor.forClass(IngestionTask.class);
        verify(accessContractService).recordDraftRevision(draft.capture(), isNull(), anyBoolean());
        assertThat(draft.getValue().getSourceConfig().path("auth").has("clientSecret")).isFalse();
        assertThat(draft.getValue().getSourceConfig().path("auth").path("clientId").asText()).isEqualTo("new");
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
    void activeEditShouldSaveDraftRevisionWithoutMutatingActiveRuntimeRow() {
        Long taskId = 61L;
        ObjectNode activeSeal = createValidClassificationSeal();
        ObjectNode draftSeal = activeSeal.deepCopy().put("sealId", "draft-seal");
        IngestionTask active = createTestTaskEntity();
        active.setId(taskId);
        active.setStatus("active");
        active.setSourceDataSourceId(TEST_SOURCE_ID);
        active.setSourceConfig(objectMapper.createObjectNode().put("schema", "active_schema"));
        active.setClassificationSeal(activeSeal);
        active.setFieldClassifications(objectMapper.createObjectNode().put("customer_id", "INTERNAL"));

        IngestionTaskDTO dto = createTestTaskDTO();
        dto.setId(taskId);
        dto.setStatus("draft");
        dto.setSourceDataSourceId(TEST_SOURCE_ID);
        dto.setSourceConfig(objectMapper.createObjectNode().put("schema", "draft_schema"));
        dto.setClassificationSeal(draftSeal);
        dto.setFieldClassifications(objectMapper.createObjectNode().put("customer_id", "CONFIDENTIAL"));

        ingestionTaskService.setAccessContractService(accessContractService);
        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(active));
        when(accessContractService.findLatestDraftRevision(taskId)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            IngestionTask target = invocation.getArgument(0);
            target.setSourceConfig(dto.getSourceConfig());
            target.setClassificationSeal(dto.getClassificationSeal());
            target.setFieldClassifications(dto.getFieldClassifications());
            target.setStatus("draft");
            return null;
        }).when(taskMapper).partialUpdate(any(IngestionTask.class), eq(dto));
        when(taskMapper.toDto(any(IngestionTask.class))).thenAnswer(invocation -> {
            IngestionTask task = invocation.getArgument(0);
            IngestionTaskDTO result = new IngestionTaskDTO();
            result.setId(task.getId());
            result.setStatus(task.getStatus());
            result.setSourceConfig(task.getSourceConfig());
            return result;
        });
        when(accessContractService.enrichTaskDto(any(IngestionTaskDTO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IngestionTaskDTO result = ingestionTaskService.update(taskId, dto);

        ArgumentCaptor<IngestionTask> draftCaptor = ArgumentCaptor.forClass(IngestionTask.class);
        verify(accessContractService).recordDraftRevision(draftCaptor.capture(), isNull(), eq(false));
        IngestionTask savedDraft = draftCaptor.getValue();
        assertThat(result.getStatus()).isEqualTo("draft");
        assertThat(savedDraft).isNotSameAs(active);
        assertThat(savedDraft.getSourceConfig().path("schema").asText()).isEqualTo("draft_schema");
        assertThat(active.getStatus()).isEqualTo("active");
        assertThat(active.getSourceConfig().path("schema").asText()).isEqualTo("active_schema");
        verify(taskRepository, never()).save(any(IngestionTask.class));
        verifyNoInteractions(addaxJobService, airflowDagService, dagPreheatService);
    }

    @Test
    void admitActiveTaskShouldApplyLatestDraftThenActivateIt() {
        Long taskId = 62L;
        ObjectNode seal = createValidClassificationSeal();
        ObjectNode fields = objectMapper.createObjectNode().put("customer_id", "INTERNAL");
        IngestionTask active = createTestTaskEntity();
        active.setId(taskId);
        active.setName("active-name");
        active.setStatus("active");
        active.setAddaxJobPath(null);
        active.setClassificationSeal(seal);
        active.setFieldClassifications(fields);
        IngestionTask draft = createTestTaskEntity();
        draft.setId(taskId);
        draft.setName("draft-name");
        draft.setStatus("draft");
        draft.setSourceType("httpreader");
        draft.setSourceDataSourceId(TEST_SOURCE_ID);
        draft.setClassificationSeal(seal);
        draft.setFieldClassifications(fields);
        draft.setAirflowEnabled(false);
        IngestionTaskRevision revision = new IngestionTaskRevision();
        revision.setId(620L);
        revision.setRevisionNumber(2);
        revision.setState("DRAFT");

        ingestionTaskService.setAccessContractService(accessContractService);
        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(active));
        when(accessContractService.findLatestDraftRevision(taskId)).thenReturn(Optional.of(revision));
        when(accessContractService.materializeLatestDraft(active)).thenReturn(draft);
        when(taskRepository.save(active)).thenReturn(active);
        when(taskMapper.toDto(active)).thenAnswer(invocation -> {
            IngestionTaskDTO result = new IngestionTaskDTO();
            result.setId(taskId);
            result.setName(active.getName());
            result.setStatus(active.getStatus());
            return result;
        });
        when(accessContractService.enrichTaskDto(any(IngestionTaskDTO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IngestionTaskDTO result = ingestionTaskService.admit(taskId, seal, fields);

        assertThat(result.getStatus()).isEqualTo("active");
        assertThat(active.getName()).isEqualTo("draft-name");
        assertThat(active.getSourceType()).isEqualTo("httpreader");
        verify(accessContractService).activateDraftRevision(taskId);
        verify(accessContractService, never()).recordDraftRevision(any(), any(), anyBoolean());
        verify(taskRepository).save(active);
        verifyNoInteractions(addaxJobService, airflowDagService, dagPreheatService);
    }

    @Test
    void admitFileDraftShouldRequirePassedPreCheckWhenEnabled() {
        Long taskId = 63L;
        ObjectNode seal = createValidClassificationSeal();
        ObjectNode fields = objectMapper.createObjectNode().put("customer_id", "INTERNAL");
        IngestionTask active = createTestTaskEntity();
        active.setId(taskId);
        active.setStatus("active");
        IngestionTask draft = createTestTaskEntity();
        draft.setId(taskId);
        draft.setStatus("draft");
        draft.setSourceType("csv");
        draft.setSourceDataSourceId(null);
        draft.setClassificationSeal(seal);
        draft.setFieldClassifications(fields);
        draft.setQualityPreCheckEnabled(true);
        draft.setPreCheckStatus("PENDING");
        IngestionTaskRevision revision = new IngestionTaskRevision();
        revision.setId(630L);
        revision.setRevisionNumber(2);
        revision.setState("DRAFT");

        ingestionTaskService.setAccessContractService(accessContractService);
        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(active));
        when(accessContractService.findLatestDraftRevision(taskId)).thenReturn(Optional.of(revision));
        when(accessContractService.materializeLatestDraft(active)).thenReturn(draft);

        assertThatThrownBy(() -> ingestionTaskService.admit(taskId, seal, fields))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("FILE_PRECHECK_REQUIRED");

        verify(accessContractService, never()).activateDraftRevision(anyLong());
        verifyNoInteractions(fileUploadService, addaxJobService, airflowDagService, dagPreheatService);
        verify(taskRepository, never()).save(any(IngestionTask.class));
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
        when(addaxJobService.createJobFromTask(any(IngestionTask.class))).thenReturn(
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
        verify(classificationSealGuard).requireProductionSeal(validationCaptor.capture());
        IngestionTask validationCandidate = validationCaptor.getValue();
        assertThat(validationCandidate).isNotSameAs(task);
        assertThat(validationCandidate.getClassificationSeal()).isEqualTo(seal);
        assertThat(validationCandidate.getFieldClassifications()).isEqualTo(fields);
        verify(taskRepository).save(task);
    }

    @Test
    void admit_shouldStagePausedRevisionDagAndDeferCutoverUntilCommit() {
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
        admitted.setAirflowDagId("ingestion_revision_101_task_1_revision_101");
        IngestionTaskRevision revision = new IngestionTaskRevision();
        revision.setId(101L);
        revision.setRevisionNumber(1);
        revision.setState("DRAFT");
        revision.setEffectiveConfigChecksum("checksum-r1");
        AirflowDagService.StagedDag staged = new AirflowDagService.StagedDag(
            "ingestion_revision_101_task_1_revision_101",
            java.nio.file.Path.of("/tmp/admit-r1.staged"),
            java.nio.file.Path.of("/tmp/admit-r1.py")
        );

        ingestionTaskService.setAccessContractService(accessContractService);
        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));
        when(accessContractService.findLatestDraftRevision(taskId)).thenReturn(Optional.empty());
        when(accessContractService.recordDraftRevision(any(IngestionTask.class), isNull(), eq(true))).thenReturn(revision);
        when(addaxJobService.createJobFromTask(any(IngestionTask.class))).thenReturn(
            new AddaxJobService.AddaxJobResult("job.json", "/tmp/job.json", Map.of())
        );
        when(airflowAdapter.isEnabled()).thenReturn(true);
        when(addaxJobService.splitJobIntoPerTableFiles("/tmp/job.json")).thenReturn(List.of());
        when(airflowDagService.stageDagForTask(any(IngestionTask.class), eq(List.of()), eq(101L), eq("checksum-r1")))
            .thenReturn(staged);
        when(taskMapper.toDto(any(IngestionTask.class))).thenReturn(admitted);
        when(accessContractService.enrichTaskDto(admitted)).thenReturn(admitted);

        IngestionTaskDTO result = ingestionTaskService.admit(taskId, seal, fields);

        assertThat(result.getAirflowDagId()).isEqualTo("ingestion_revision_101_task_1_revision_101");
        assertThat(task.getStatus()).isEqualTo("draft");
        verify(accessContractService).refreshDraftRuntimeSnapshot(eq(taskId), any(IngestionTask.class));
        verify(accessContractService).markDraftDagStaged(
            101L,
            staged.stagedPath().toString(),
            staged.finalPath().toString(),
            task.getAirflowDagId()
        );
        verify(airflowDagService, never()).publishStagedDag(any());
        verify(airflowDagService, never()).setDagPausedStrict(anyString(), anyBoolean());
        verify(accessContractService, never()).activateDraftRevision(anyLong());
        verifyNoInteractions(dagPreheatService);
    }

    @Test
    void admit_outerRollbackMustNotDeleteConcurrentWinnerFinalDag() throws Exception {
        Long taskId = 1L;
        java.nio.file.Path stagedPath = java.nio.file.Files.createTempFile("admit-race-", ".staged");
        java.nio.file.Path finalPath = stagedPath.resolveSibling(stagedPath.getFileName() + ".py");
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("draft");
        task.setAirflowEnabled(true);
        ObjectNode seal = createValidClassificationSeal();
        ObjectNode fields = objectMapper.createObjectNode().put("customer_id", "INTERNAL");
        task.setClassificationSeal(seal);
        task.setFieldClassifications(fields);
        IngestionTaskRevision revision = new IngestionTaskRevision();
        revision.setId(102L);
        revision.setRevisionNumber(1);
        revision.setState("DRAFT");
        revision.setEffectiveConfigChecksum("checksum-race");
        AirflowDagService.StagedDag staged = new AirflowDagService.StagedDag(
            "ingestion_revision_102_task_1_revision_102",
            stagedPath,
            finalPath
        );
        IngestionTaskDTO admitted = createTestTaskDTO();
        admitted.setId(taskId);
        admitted.setStatus("active");
        admitted.setAirflowDagId(staged.dagId());

        ingestionTaskService.setAccessContractService(accessContractService);
        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));
        when(accessContractService.findLatestDraftRevision(taskId)).thenReturn(Optional.empty());
        when(accessContractService.recordDraftRevision(any(IngestionTask.class), isNull(), eq(true))).thenReturn(revision);
        when(addaxJobService.createJobFromTask(any(IngestionTask.class))).thenReturn(
            new AddaxJobService.AddaxJobResult("job.json", "/tmp/job-race.json", Map.of())
        );
        when(airflowAdapter.isEnabled()).thenReturn(true);
        when(addaxJobService.splitJobIntoPerTableFiles("/tmp/job-race.json")).thenReturn(List.of());
        when(airflowDagService.stageDagForTask(any(IngestionTask.class), eq(List.of()), eq(102L), eq("checksum-race")))
            .thenReturn(staged);
        when(taskMapper.toDto(any(IngestionTask.class))).thenReturn(admitted);
        when(accessContractService.enrichTaskDto(admitted)).thenReturn(admitted);
        doAnswer(invocation -> {
            AirflowDagService.StagedDag discarded = invocation.getArgument(0);
            boolean includePublishedDag = invocation.getArgument(1);
            java.nio.file.Files.deleteIfExists(discarded.stagedPath());
            if (includePublishedDag) {
                java.nio.file.Files.deleteIfExists(discarded.finalPath());
            }
            return null;
        }).when(airflowDagService).discardStagedDag(eq(staged), anyBoolean());

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            ingestionTaskService.admit(taskId, seal, fields);
            org.springframework.transaction.support.TransactionSynchronization synchronization =
                org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .getLast();
            java.util.concurrent.CountDownLatch winnerPublished = new java.util.concurrent.CountDownLatch(1);

            java.util.concurrent.Future<?> winner = executor.submit(() -> {
                try {
                    java.nio.file.Files.writeString(finalPath, "winner-final-dag");
                    winnerPublished.countDown();
                } catch (java.io.IOException ex) {
                    throw new java.io.UncheckedIOException(ex);
                }
            });
            java.util.concurrent.Future<?> rollback = executor.submit(() -> {
                try {
                    if (!winnerPublished.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new AssertionError("concurrent winner did not publish DAG");
                    }
                    synchronization.afterCompletion(
                        org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK
                    );
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(ex);
                }
            });

            winner.get(2, java.util.concurrent.TimeUnit.SECONDS);
            rollback.get(2, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(java.nio.file.Files.exists(stagedPath)).isFalse();
            assertThat(java.nio.file.Files.readString(finalPath)).isEqualTo("winner-final-dag");
            verify(airflowDagService).discardStagedDag(staged, false);
            verify(airflowDagService, never()).discardStagedDag(staged, true);
            verify(airflowDagService, never()).discardStagedDagStrict(any(), eq(true));
        } finally {
            executor.shutdownNow();
            java.nio.file.Files.deleteIfExists(stagedPath);
            java.nio.file.Files.deleteIfExists(finalPath);
        }
    }

    @Test
    void reconcileAdmission_shouldKeepOldPlanActiveAndExposePublishFailure() throws Exception {
        Long taskId = 1L;
        java.nio.file.Path stagedPath = java.nio.file.Files.createTempFile("admit-r2-", ".staged");
        java.nio.file.Path finalPath = stagedPath.resolveSibling(stagedPath.getFileName() + ".py");
        IngestionTask canonical = createTestTaskEntity();
        canonical.setId(taskId);
        canonical.setStatus("active");
        canonical.setAirflowDagId("orders_revision_1");
        IngestionTask runtime = createTestTaskEntity();
        runtime.setId(taskId);
        runtime.setStatus("active");
        runtime.setAirflowDagId("orders_revision_2");
        IngestionTaskRevision revision = revision(2L, canonical, "DRAFT", stagedPath, finalPath, "orders_revision_1");

        ingestionTaskService.setAccessContractService(accessContractService);
        when(revisionRepository.findTaskIdById(2L)).thenReturn(Optional.of(taskId));
        when(entityManager.find(IngestionTaskRevision.class, 2L, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
            .thenReturn(revision);
        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(canonical));
        when(accessContractService.materializeExecutionTask(canonical, 2L)).thenReturn(runtime);
        when(airflowDagService.publishStagedDag(any())).thenThrow(new IllegalStateException("publish unavailable"));

        assertThatThrownBy(() -> ingestionTaskService.reconcileAdmissionDagDeployment(2L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("publish unavailable");

        assertThat(canonical.getAirflowDagId()).isEqualTo("orders_revision_1");
        verify(accessContractService).markDagDeployment(
            eq(2L),
            eq(IngestionAccessContractService.DAG_DEPLOYMENT_RECONCILIATION_REQUIRED),
            contains("DAG_PUBLISH_FAILED")
        );
        verify(accessContractService, never()).activateDraftRevision(anyLong());
        verify(airflowDagService, never()).setDagPausedStrict(anyString(), anyBoolean());
    }

    @Test
    void reconcileAdmission_shouldExposeFailedActivationCompensation() throws Exception {
        Long taskId = 1L;
        java.nio.file.Path stagedPath = java.nio.file.Files.createTempFile("admit-r2-", ".staged");
        java.nio.file.Path finalPath = stagedPath.resolveSibling(stagedPath.getFileName() + ".py");
        IngestionTask canonical = createTestTaskEntity();
        canonical.setId(taskId);
        canonical.setStatus("active");
        canonical.setAirflowDagId("orders_revision_1");
        IngestionTask runtime = createTestTaskEntity();
        runtime.setId(taskId);
        runtime.setStatus("active");
        runtime.setAirflowDagId("orders_revision_2");
        IngestionTaskRevision revision = revision(2L, canonical, "DRAFT", stagedPath, finalPath, "orders_revision_1");

        ingestionTaskService.setAccessContractService(accessContractService);
        when(revisionRepository.findTaskIdById(2L)).thenReturn(Optional.of(taskId));
        when(entityManager.find(IngestionTaskRevision.class, 2L, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
            .thenReturn(revision);
        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(canonical));
        when(taskRepository.save(canonical)).thenReturn(canonical);
        when(accessContractService.materializeExecutionTask(canonical, 2L)).thenReturn(runtime);
        when(airflowDagService.publishStagedDag(any())).thenReturn("orders_revision_2");
        when(accessContractService.activateDraftRevision(taskId)).thenThrow(new IllegalStateException("activation failed"));
        doThrow(new IllegalStateException("compensation failed"))
            .when(airflowDagService).discardStagedDagStrict(any(), eq(true));

        assertThatThrownBy(() -> ingestionTaskService.reconcileAdmissionDagDeployment(2L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("activation failed");

        verify(accessContractService).markDagDeployment(
            eq(2L),
            eq(IngestionAccessContractService.DAG_DEPLOYMENT_RECONCILIATION_REQUIRED),
            argThat(error -> error.contains("DAG_ACTIVATION_FAILED") && error.contains("DAG_COMPENSATION_FAILED"))
        );
    }

    @Test
    void reconcileAdmission_shouldNeverDeleteFinalDagWhenAnotherAttemptActivatedRevision() throws Exception {
        Long taskId = 1L;
        java.nio.file.Path stagedPath = java.nio.file.Files.createTempFile("admit-r2-", ".staged");
        java.nio.file.Path finalPath = stagedPath.resolveSibling(stagedPath.getFileName() + ".py");
        IngestionTask canonical = createTestTaskEntity();
        canonical.setId(taskId);
        canonical.setStatus("active");
        IngestionTask runtime = createTestTaskEntity();
        runtime.setId(taskId);
        runtime.setStatus("active");
        runtime.setAirflowDagId("orders_revision_2");
        IngestionTaskRevision draft = revision(2L, canonical, "DRAFT", stagedPath, finalPath, "orders_revision_1");
        IngestionTaskRevision active = revision(2L, canonical, "ACTIVE", stagedPath, finalPath, "orders_revision_1");

        ingestionTaskService.setAccessContractService(accessContractService);
        when(revisionRepository.findTaskIdById(2L)).thenReturn(Optional.of(taskId));
        when(entityManager.find(IngestionTaskRevision.class, 2L, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
            .thenReturn(draft, active);
        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(canonical));
        when(taskRepository.save(canonical)).thenReturn(canonical);
        when(accessContractService.materializeExecutionTask(canonical, 2L)).thenReturn(runtime);
        when(airflowDagService.publishStagedDag(any())).thenReturn("orders_revision_2");
        when(accessContractService.activateDraftRevision(taskId)).thenThrow(new IllegalStateException("lost admission race"));

        assertThatThrownBy(() -> ingestionTaskService.reconcileAdmissionDagDeployment(2L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("lost admission race");

        verify(airflowDagService, never()).discardStagedDagStrict(any(), anyBoolean());
    }

    @Test
    void reconcileAdmission_shouldPauseOldDagBeforeSchedulingOnlyNewDag() {
        Long taskId = 1L;
        IngestionTask canonical = createTestTaskEntity();
        canonical.setId(taskId);
        canonical.setStatus("active");
        IngestionTask runtime = createTestTaskEntity();
        runtime.setId(taskId);
        runtime.setStatus("active");
        runtime.setAirflowDagId("orders_revision_13");
        IngestionTaskRevision revision = revision(
            13L,
            canonical,
            "ACTIVE",
            java.nio.file.Path.of("/tmp/orders-r13.staged"),
            java.nio.file.Path.of("/tmp/orders-r13.py"),
            "orders_revision_12"
        );

        ingestionTaskService.setAccessContractService(accessContractService);
        when(revisionRepository.findTaskIdById(13L)).thenReturn(Optional.of(taskId));
        when(entityManager.find(IngestionTaskRevision.class, 13L, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
            .thenReturn(revision);
        when(taskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(canonical));
        when(accessContractService.materializeExecutionTask(canonical, 13L)).thenReturn(runtime);

        ingestionTaskService.reconcileAdmissionDagDeployment(13L);

        InOrder locks = inOrder(revisionRepository, taskRepository, entityManager);
        locks.verify(revisionRepository).findTaskIdById(13L);
        locks.verify(taskRepository).findByIdForUpdate(taskId);
        locks.verify(entityManager).find(
            IngestionTaskRevision.class,
            13L,
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
        );
        InOrder cutover = inOrder(airflowDagService, accessContractService);
        cutover.verify(airflowDagService).retireDagStrict("orders_revision_12", runtime);
        cutover.verify(airflowDagService).setDagPausedStrict("orders_revision_13", false);
        cutover.verify(accessContractService).markDagDeployment(
            13L,
            IngestionAccessContractService.DAG_DEPLOYMENT_ACTIVE,
            null
        );
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
        when(addaxJobService.createJobFromTask(any(IngestionTask.class))).thenReturn(
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
        order.verify(addaxJobService).createJobFromTask(any(IngestionTask.class));
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

        ingestionTaskService.setAccessContractService(accessContractService);
        when(accessContractService.bindActiveRevision(any(IngestionExecution.class), eq(task))).thenAnswer(invocation -> {
            IngestionExecution execution = invocation.getArgument(0);
            execution.setQualityPolicyRef("dataset:00000000-0000-0000-0000-000000000001");
            return null;
        });
        when(platformInfraClient.triggerQualityRunByPolicyRef(
            "dataset:00000000-0000-0000-0000-000000000001",
            "INGESTION"
        )).thenReturn("quality-run-api-1");

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
        when(apiIngestionExecutor.execute(eq(plan), eq(task), any(IngestionExecution.class))).thenAnswer(invocation -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return ApiIngestionResult.success(12L, 12L, Map.of("stream", "orders"));
        });

        IngestionExecutionDTO result = ingestionTaskService.execute(taskId);
        assertThat(result).isNotNull();

        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        ArgumentCaptor<SourceConnectorContext> contextCaptor = ArgumentCaptor.forClass(SourceConnectorContext.class);
        verify(sourceConnectorRegistry).find(contextCaptor.capture());
        verify(apiConnector).buildExecutionPlan(contextCaptor.getValue());
        verify(apiIngestionExecutor).execute(eq(plan), eq(task), any(IngestionExecution.class));
        verify(platformInfraClient).triggerQualityRunByPolicyRef(
            "dataset:00000000-0000-0000-0000-000000000001",
            "INGESTION"
        );
        verify(airflowAdapter, never()).triggerIfRequested(any(), any(), anyBoolean());
        verify(addaxJobService, never()).resolveWriterColumnsIfNeeded(anyString());

        ArgumentCaptor<IngestionExecution> executionCaptor = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository, atLeast(2)).save(executionCaptor.capture());
        assertThat(executionCaptor.getAllValues()).anySatisfy(saved -> {
            assertThat(saved.getStatus()).isEqualTo("success");
            assertThat(saved.getRowsRead()).isEqualTo(12L);
            assertThat(saved.getRowsWritten()).isEqualTo(12L);
            assertThat(saved.getEndTime()).isNotNull();
            assertThat(saved.getQualityRunId()).isEqualTo("quality-run-api-1");
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
    void concurrentApiClaimsLockExecutionAndAllowOnlyOneExternalDispatch() throws Exception {
        Long taskId = 1L;
        Long executionId = 9001L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        task.setSourceType("httpreader");
        IngestionExecution execution = new IngestionExecution();
        execution.setId(executionId);
        execution.setStatus("preparing");

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch firstClaimSaved = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger lockAttempts = new java.util.concurrent.atomic.AtomicInteger();
        when(entityManager.find(
            IngestionExecution.class,
            executionId,
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
        )).thenAnswer(invocation -> {
            if (lockAttempts.incrementAndGet() > 1
                && !firstClaimSaved.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new AssertionError("second claim was not serialized behind the execution row lock");
            }
            return execution;
        });
        when(executionRepository.save(same(execution))).thenAnswer(invocation -> {
            IngestionExecution saved = invocation.getArgument(0);
            if ("running".equals(saved.getStatus())) {
                firstClaimSaved.countDown();
            }
            return saved;
        });
        ExecutionPlan plan = new ExecutionPlan(
            "api-http",
            "api",
            "1.2.0",
            null,
            Map.of(),
            List.of(),
            new ExecutionPlan.CheckpointPolicy("none", null, "task_success"),
            Map.of("engine", "api-http")
        );

        java.util.concurrent.Callable<Boolean> attempt = () -> {
            start.await(2, java.util.concurrent.TimeUnit.SECONDS);
            try {
                Object claimed = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                    ingestionTaskService,
                    "claimApiExecution",
                    taskId,
                    executionId
                );
                if (claimed != null) {
                    apiIngestionExecutor.execute(plan, task, execution);
                    return true;
                }
            } catch (IllegalStateException rejected) {
                return false;
            }
            return false;
        };

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<Boolean> first = pool.submit(attempt);
            java.util.concurrent.Future<Boolean> second = pool.submit(attempt);
            start.countDown();
            int accepted = Boolean.TRUE.equals(first.get(3, java.util.concurrent.TimeUnit.SECONDS)) ? 1 : 0;
            accepted += Boolean.TRUE.equals(second.get(3, java.util.concurrent.TimeUnit.SECONDS)) ? 1 : 0;

            assertThat(accepted).isEqualTo(1);
            verify(apiIngestionExecutor, times(1)).execute(plan, task, execution);
            verify(entityManager, times(2)).find(
                IngestionExecution.class,
                executionId,
                jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
            );
        } finally {
            pool.shutdownNow();
        }
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
    void scheduledR12RunShouldRemainBoundToR12AfterR13IsActive() {
        Long taskId = 1L;
        IngestionTask canonicalR13 = createTestTaskEntity();
        canonicalR13.setId(taskId);
        canonicalR13.setStatus("active");
        canonicalR13.setAirflowEnabled(true);
        canonicalR13.setAirflowDagId("orders_task_1_revision_13");

        IngestionTask runtimeR12 = createTestTaskEntity();
        runtimeR12.setId(taskId);
        runtimeR12.setStatus("active");
        runtimeR12.setAirflowEnabled(true);
        runtimeR12.setAirflowDagId("orders_task_1_revision_12");

        ingestionTaskService.setAccessContractService(accessContractService);
        when(executionRepository.findFirstByTaskIdAndAirflowDagIdAndExecutionId(
            taskId, "orders_task_1_revision_12", "scheduled__r12"
        )).thenReturn(Optional.empty());
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(canonicalR13));
        when(taskRepository.save(canonicalR13)).thenReturn(canonicalR13);
        when(accessContractService.materializeExecutionTask(canonicalR13, 12L)).thenReturn(runtimeR12);
        when(accessContractService.bindExactRevision(
            any(IngestionExecution.class), eq(canonicalR13), eq(12L), eq("checksum-r12")
        )).thenAnswer(invocation -> {
            IngestionExecution execution = invocation.getArgument(0);
            execution.setTaskRevisionId(12L);
            execution.setRevisionNumber(12);
            execution.setEffectiveConfigChecksum("checksum-r12");
            return null;
        });
        when(executionMapper.toDto(any(IngestionExecution.class))).thenAnswer(invocation -> {
            IngestionExecution execution = invocation.getArgument(0);
            IngestionExecutionDTO dto = new IngestionExecutionDTO();
            dto.setId(execution.getId());
            dto.setTaskId(taskId);
            dto.setExecutionId(execution.getExecutionId());
            dto.setRevisionNumber(execution.getRevisionNumber());
            dto.setEffectiveConfigChecksum(execution.getEffectiveConfigChecksum());
            dto.setAirflowDagId(execution.getAirflowDagId());
            dto.setStatus(execution.getStatus());
            return dto;
        });

        IngestionExecutionDTO execution = ingestionTaskService.registerScheduledExecution(
            taskId, 12L, "checksum-r12", "orders_task_1_revision_12", "scheduled__r12"
        );

        assertThat(execution.getRevisionNumber()).isEqualTo(12);
        assertThat(execution.getEffectiveConfigChecksum()).isEqualTo("checksum-r12");
        assertThat(execution.getAirflowDagId()).isEqualTo("orders_task_1_revision_12");
        assertThat(execution.getStatus()).isEqualTo("running");
        verify(accessContractService, never()).bindActiveRevision(any(), any());
    }

    @Test
    void concurrentScheduledRegistrationShouldReturnUniqueWinnerAfterConstraintConflict() {
        Long taskId = 1L;
        ingestionTaskService.setAccessContractService(accessContractService);
        IngestionExecution winner = new IngestionExecution();
        winner.setId(91L);
        winner.setTask(createTestTaskEntity());
        winner.getTask().setId(taskId);
        winner.getTask().setStatus("active");
        winner.getTask().setAirflowEnabled(true);
        winner.getTask().setAirflowDagId("orders_task_1_revision_12");
        winner.setExecutionId("scheduled__r12");
        winner.setAirflowDagId("orders_task_1_revision_12");
        winner.setTaskRevisionId(12L);
        winner.setRevisionNumber(12);
        winner.setEffectiveConfigChecksum("checksum-r12");
        winner.setStatus("running");

        when(executionRepository.findFirstByTaskIdAndAirflowDagIdAndExecutionId(
            taskId, "orders_task_1_revision_12", "scheduled__r12"
        )).thenReturn(Optional.empty(), Optional.of(winner));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(winner.getTask()));
        // A winner may commit between the initial idempotency lookup and loading
        // the task contract. Registration must not misclassify that winner as a
        // max-concurrency violation before the unique insert arbitrates the race.
        lenient().when(executionRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId)).thenReturn(Optional.of(winner));
        when(accessContractService.materializeExecutionTask(winner.getTask(), 12L)).thenReturn(winner.getTask());
        when(accessContractService.bindExactRevision(any(), eq(winner.getTask()), eq(12L), eq("checksum-r12")))
            .thenAnswer(invocation -> null);
        when(executionRepository.saveAndFlush(any(IngestionExecution.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate scheduled run"));
        when(executionMapper.toDto(winner)).thenAnswer(invocation -> {
            IngestionExecutionDTO dto = new IngestionExecutionDTO();
            dto.setId(91L);
            dto.setTaskId(taskId);
            dto.setExecutionId("scheduled__r12");
            dto.setRevisionNumber(12);
            dto.setEffectiveConfigChecksum("checksum-r12");
            dto.setAirflowDagId("orders_task_1_revision_12");
            dto.setStatus("running");
            return dto;
        });

        IngestionExecutionDTO result = ingestionTaskService.registerScheduledExecution(
            taskId, 12L, "checksum-r12", "orders_task_1_revision_12", "scheduled__r12"
        );

        assertThat(result.getId()).isEqualTo(91L);
        verify(executionRepository).saveAndFlush(any(IngestionExecution.class));
        verify(executionRepository, never()).findFirstByTaskIdOrderByCreatedAtDesc(taskId);
        verify(requiresNewExecutor, atLeast(2)).execute(any());
    }

    @Test
    void concurrentExactApiCallbackShouldReadCommittedWinnerInIndependentTransaction() {
        Long taskId = 92L;
        String exactDagId = "api_task_92_revision_12";
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        task.setSourceType("httpreader");
        task.setAirflowEnabled(true);
        task.setAirflowDagId(exactDagId);
        IngestionExecution winner = new IngestionExecution();
        winner.setId(93L);
        winner.setTask(task);
        winner.setExecutionId("scheduled__api-r12");
        winner.setAirflowDagId(exactDagId);
        winner.setTaskRevisionId(12L);
        winner.setRevisionNumber(12);
        winner.setEffectiveConfigChecksum("checksum-r12");
        winner.setStatus("preparing");
        ingestionTaskService.setAccessContractService(accessContractService);
        when(executionRepository.findFirstByTaskIdAndAirflowDagIdAndExecutionId(
            taskId, exactDagId, "scheduled__api-r12"
        )).thenReturn(Optional.empty(), Optional.of(winner));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(executionRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId)).thenReturn(Optional.empty());
        when(accessContractService.materializeExecutionTask(task, 12L)).thenReturn(task);
        when(executionRepository.saveAndFlush(any(IngestionExecution.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate api callback"));
        when(executionMapper.toDto(winner)).thenAnswer(invocation -> {
            IngestionExecutionDTO dto = new IngestionExecutionDTO();
            dto.setId(93L);
            dto.setTaskId(taskId);
            dto.setExecutionId("scheduled__api-r12");
            dto.setAirflowDagId(exactDagId);
            dto.setRevisionNumber(12);
            dto.setEffectiveConfigChecksum("checksum-r12");
            dto.setStatus("preparing");
            return dto;
        });

        IngestionExecutionDTO result = ingestionTaskService.executeInternalApiForRevision(
            taskId,
            "batch-api-r12",
            "SCHEDULED",
            null,
            null,
            null,
            12L,
            "checksum-r12",
            exactDagId,
            "scheduled__api-r12"
        );

        assertThat(result.getId()).isEqualTo(93L);
        verify(executionRepository).saveAndFlush(any(IngestionExecution.class));
        verify(requiresNewExecutor, atLeast(2)).execute(any());
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
    void pendingSecretMigrationShouldBlockManualAsyncRetryAndAirflowCallbacks() {
        Long taskId = 81L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        IngestionExecution failed = new IngestionExecution();
        failed.setId(82L);
        failed.setTask(task);
        failed.setStatus("failed");
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(executionRepository.findById(82L)).thenReturn(Optional.of(failed));
        when(executionRepository.findFirstByTaskIdAndAirflowDagIdAndExecutionId(any(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        doThrow(new IllegalStateException("INGESTION_TASK_SECRET_MIGRATION_PENDING"))
            .when(secretMigrationService).requireTaskReady(taskId);

        assertThatThrownBy(() -> ingestionTaskService.execute(taskId))
            .hasMessage("INGESTION_TASK_SECRET_MIGRATION_PENDING");
        assertThatThrownBy(() -> ingestionTaskService.validateAsyncExecutionRequest(taskId))
            .hasMessage("INGESTION_TASK_SECRET_MIGRATION_PENDING");
        assertThatThrownBy(() -> ingestionTaskService.validateAsyncRetryRequest(taskId, 82L, "FAILED_ONLY"))
            .hasMessage("INGESTION_TASK_SECRET_MIGRATION_PENDING");
        assertThatThrownBy(() -> ingestionTaskService.executeInternalApiForRevision(
            taskId,
            "batch-81",
            "SCHEDULED",
            null,
            null,
            null,
            12L,
            "checksum-r12",
            "api-task-r12",
            "scheduled__api-r12"
        )).hasMessage("INGESTION_TASK_SECRET_MIGRATION_PENDING");
        assertThatThrownBy(() -> ingestionTaskService.registerScheduledExecution(
            taskId,
            12L,
            "checksum-r12",
            "db-task-r12",
            "scheduled__db-r12"
        )).hasMessage("INGESTION_TASK_SECRET_MIGRATION_PENDING");

        verify(classificationSealGuard, never()).requireProductionSeal(any());
        verify(executionRepository, never()).save(any());
        verify(executionRepository, never()).saveAndFlush(any());
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
    void retryExecution_shouldPersistParentLineageBeforeDispatch() {
        Long taskId = 1L;
        Long executionId = 9L;
        IngestionTask task = createTestTaskEntity();
        task.setId(taskId);
        task.setStatus("active");
        IngestionExecution failed = new IngestionExecution();
        failed.setId(executionId);
        failed.setTask(task);
        failed.setStatus("failed");
        failed.setRetryCount(1);
        failed.setMaxRetries(3);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(executionRepository.findById(executionId)).thenReturn(Optional.of(failed));
        when(executionRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId)).thenReturn(Optional.of(failed));

        ingestionTaskService.retryExecution(taskId, executionId, "FAILED_ONLY");

        ArgumentCaptor<IngestionExecution> child = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository).saveAndFlush(child.capture());
        assertThat(child.getValue().getParentExecutionId()).isEqualTo(executionId);
        assertThat(child.getValue().getRetryCount()).isEqualTo(2);
        assertThat(child.getValue().getMaxRetries()).isEqualTo(3);
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

    private IngestionTaskRevision revision(
        Long revisionId,
        IngestionTask task,
        String state,
        java.nio.file.Path stagedPath,
        java.nio.file.Path publishedPath,
        String previousDagId
    ) {
        IngestionTaskRevision revision = new IngestionTaskRevision();
        revision.setId(revisionId);
        revision.setTask(task);
        revision.setRevisionNumber(revisionId.intValue());
        revision.setState(state);
        revision.setEffectiveConfigChecksum("checksum-r" + revisionId);
        revision.setStagedDagPath(stagedPath.toString());
        revision.setPublishedDagPath(publishedPath.toString());
        revision.setPreviousAirflowDagId(previousDagId);
        return revision;
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
