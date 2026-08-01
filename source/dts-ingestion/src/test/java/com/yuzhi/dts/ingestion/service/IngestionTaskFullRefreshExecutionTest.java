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
import com.yuzhi.dts.ingestion.repository.IngestionTaskRevisionRepository;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.etl.AirflowDagService;
import com.yuzhi.dts.ingestion.service.etl.DagPreheatService;
import com.yuzhi.dts.ingestion.service.etl.CsvParseService;
import com.yuzhi.dts.ingestion.service.etl.ExcelParseService;
import com.yuzhi.dts.ingestion.service.etl.FileUploadService;
import com.yuzhi.dts.ingestion.service.etl.IncrementalSyncService;
import com.yuzhi.dts.ingestion.service.etl.IngestionRetryService;
import com.yuzhi.dts.ingestion.service.etl.TargetTableProvisioner;
import com.yuzhi.dts.ingestion.service.etl.api.ApiIngestionExecutor;
import com.yuzhi.dts.ingestion.service.etl.connector.SourceConnectorRegistry;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import com.yuzhi.dts.ingestion.service.mapper.IngestionExecutionMapper;
import com.yuzhi.dts.ingestion.service.mapper.IngestionTaskMapper;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
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
    private jakarta.persistence.EntityManager entityManager;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private IngestionTaskService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new IngestionTaskService(
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

        // Initialize transaction synchronization for unit tests (execute() registers a post-commit hook)
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        }

        // Make TransactionTemplate actually execute the callback (needed for async trigger phase)
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any()))
            .thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());

        when(airflowAdapter.isEnabled()).thenReturn(false);
        when(executionRepository.findFirstByTaskIdOrderByCreatedAtDesc(any(Long.class))).thenReturn(Optional.empty());
        when(taskRepository.save(any(IngestionTask.class))).thenAnswer(inv -> inv.getArgument(0));

        // Track saved executions so findById can return them in the async trigger phase
        java.util.Map<Long, IngestionExecution> savedExecutions = new java.util.concurrent.ConcurrentHashMap<>();
        when(executionRepository.save(any(IngestionExecution.class))).thenAnswer(inv -> {
            IngestionExecution execution = inv.getArgument(0);
            if (execution.getId() == null) {
                execution.setId(900L);
            }
            savedExecutions.put(execution.getId(), execution);
            return execution;
        });
        org.mockito.Mockito.lenient().when(executionRepository.findById(any(Long.class))).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return java.util.Optional.ofNullable(savedExecutions.get(id));
        });
    }

    @AfterEach
    void tearDown() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
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

        // Phase 2 runs in afterCommit — simulate by invoking registered synchronizations
        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        verify(targetTableProvisioner).ensureTargetTables(eq(task), eq(null), any(IngestionExecution.class));

        ArgumentCaptor<IngestionExecution> captor = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        IngestionExecution latest = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(latest.getReplaceMode()).isEqualTo("FULL_REPLACE");
        assertThat(latest.getDroppedTables()).isEqualTo("public.t1,public.t2");
    }

    @Test
    void execute_fullRefreshFile_shouldSkipTargetProvisioner() {
        IngestionTask task = baseTask(102L, "csv", "full_refresh");
        task.setAddaxJobPath(null);

        when(taskRepository.findById(102L)).thenReturn(Optional.of(task));
        when(fileUploadService.verifyManagedUpload("file-102", fileChecksum()))
            .thenReturn(new FileUploadService.ManagedUpload("file-102", "/tmp/file-102.csv", "/opt/addax/jobs/uploads/file-102.csv", fileChecksum()));
        when(addaxJobService.createJobFromTask(eq(task), eq(null), eq(null), any(Map.class), any(Map.class)))
            .thenReturn(new AddaxJobService.AddaxJobResult("job.json", "/tmp/file-job.json", Map.of()));
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());

        service.execute(102L);

        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        verify(targetTableProvisioner, never()).ensureTargetTables(any(), any(), any());
    }

    @Test
    void execute_fileTaskWithInvalidFormula_shouldFailInAsyncPhase() throws Exception {
        IngestionTask task = baseTask(104L, "excel", "full_refresh");
        java.nio.file.Path file = Files.createTempFile("formula-invalid", ".xlsx");
        ObjectNode sourceConfig = (ObjectNode) task.getSourceConfig().deepCopy();
        sourceConfig.put("_filePath", file.toString());
        task.setSourceConfig(sourceConfig);

        when(taskRepository.findById(104L)).thenReturn(Optional.of(task));
        when(fileUploadService.verifyManagedUpload("file-104", fileChecksum()))
            .thenReturn(new FileUploadService.ManagedUpload("file-104", file.toString(), "/opt/addax/jobs/uploads/file-104.xlsx", fileChecksum()));
        when(fileUploadService.readPlainBytes(file)).thenReturn("x".getBytes(StandardCharsets.UTF_8));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Excel 公式预检失败：A2 公式无法解析 -> 1/0"))
            .when(excelParseService)
            .validateFormulaCells(any(byte[].class));
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());

        service.execute(104L);

        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        verify(addaxJobService, never()).createJobFromTask(any(), any(), any(), any(), any());
        verify(targetTableProvisioner, never()).ensureTargetTables(any(), any(), any());
        ArgumentCaptor<IngestionExecution> captor = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        boolean hasFailed = captor.getAllValues().stream()
            .anyMatch(e -> "failed".equals(e.getStatus())
                && e.getErrorMessage() != null
                && e.getErrorMessage().contains("公式预检失败"));
        assertThat(hasFailed).isTrue();
    }

    @Test
    void execute_fileTaskWithHostPathFormula_shouldUseVerifiedManagedPath() throws Exception {
        IngestionTask task = baseTask(105L, "excel", "full_refresh");
        java.nio.file.Path clientFile = Files.createTempFile("formula-client-host", ".xlsx");
        java.nio.file.Path managedFile = Files.createTempFile("formula-managed-host", ".xlsx");
        ObjectNode sourceConfig = (ObjectNode) task.getSourceConfig().deepCopy();
        sourceConfig.put("hostPath", clientFile.toString());
        task.setSourceConfig(sourceConfig);

        when(taskRepository.findById(105L)).thenReturn(Optional.of(task));
        when(fileUploadService.verifyManagedUpload("file-105", fileChecksum()))
            .thenReturn(new FileUploadService.ManagedUpload("file-105", managedFile.toString(), "/opt/addax/jobs/uploads/file-105.xlsx", fileChecksum()));
        when(fileUploadService.readPlainBytes(managedFile)).thenReturn("y".getBytes(StandardCharsets.UTF_8));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Excel 公式预检失败：A2 公式无法解析 -> not_existing_named_range"))
            .when(excelParseService)
            .validateFormulaCells(any(byte[].class));
        when(executionMapper.toDto(any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());

        service.execute(105L);

        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        verify(fileUploadService).readPlainBytes(managedFile);
        verify(fileUploadService, never()).readPlainBytes(clientFile);
        verify(addaxJobService, never()).createJobFromTask(any(), any(), any(), any(), any());
        verify(targetTableProvisioner, never()).ensureTargetTables(any(), any(), any());
        ArgumentCaptor<IngestionExecution> captor = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        boolean hasFailed = captor.getAllValues().stream()
            .anyMatch(e -> "failed".equals(e.getStatus())
                && e.getErrorMessage() != null
                && e.getErrorMessage().contains("公式预检失败"));
        assertThat(hasFailed).isTrue();
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

        // Phase 1 succeeds (returns "preparing" execution)
        service.execute(103L);

        // Phase 2 runs in afterCommit — the error is handled async via markExecutionFailed
        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

        ArgumentCaptor<IngestionExecution> captor = ArgumentCaptor.forClass(IngestionExecution.class);
        verify(executionRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        boolean hasFailed = captor.getAllValues().stream()
            .anyMatch(e -> "failed".equals(e.getStatus())
                && e.getErrorMessage() != null
                && e.getErrorMessage().contains("Addax 作业路径无效"));
        assertThat(hasFailed).isTrue();
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
        if (sourceType != null && List.of("excel", "csv", "txt", "excelreader", "txtfilereader").contains(sourceType.toLowerCase())) {
            String fileId = "file-" + id;
            sourceConfig.put("_fileId", fileId);
            ObjectNode seal = objectMapper.createObjectNode();
            seal.put("fileId", fileId);
            seal.put("fileSubjectKey", "ingestion-upload:" + fileId);
            seal.put("fileChecksum", fileChecksum());
            task.setClassificationSeal(seal);
        }
        task.setSourceConfig(sourceConfig);
        return task;
    }

    private String fileChecksum() {
        return "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    }
}
