package com.yuzhi.dts.ingestion.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.domain.IngestionTaskRevision;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRevisionRepository;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalAuditDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalAuditSummaryDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionGovernanceOverviewDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionObservabilityDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalStateDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.etl.AirflowClient;
import com.yuzhi.dts.ingestion.service.etl.AirflowDagService;
import com.yuzhi.dts.ingestion.service.etl.DagPreheatService;
import com.yuzhi.dts.ingestion.service.etl.ExcelParseService;
import com.yuzhi.dts.ingestion.service.etl.CsvParseService;
import com.yuzhi.dts.ingestion.service.etl.FileUploadService;
import com.yuzhi.dts.ingestion.service.etl.IncrementalSyncService;
import com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier;
import com.yuzhi.dts.ingestion.service.etl.IngestionExecutionLineageSnapshot;
import com.yuzhi.dts.ingestion.service.etl.api.ApiConnectorTypes;
import com.yuzhi.dts.ingestion.service.etl.api.ApiIngestionExecutor;
import com.yuzhi.dts.ingestion.service.etl.api.ApiIngestionResult;
import com.yuzhi.dts.ingestion.service.etl.rollback.RollbackRecoveryService;
import com.yuzhi.dts.ingestion.service.etl.connector.ExecutionPlan;
import com.yuzhi.dts.ingestion.service.etl.connector.SourceConnector;
import com.yuzhi.dts.ingestion.service.etl.connector.SourceConnectorContext;
import com.yuzhi.dts.ingestion.service.etl.connector.SourceConnectorRegistry;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import com.yuzhi.dts.ingestion.service.mapper.IngestionExecutionMapper;
import com.yuzhi.dts.ingestion.service.mapper.IngestionTaskMapper;
import com.yuzhi.dts.ingestion.service.security.IngestionSensitiveConfigSupport;
import com.yuzhi.dts.common.audit.AuditStage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executor;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 数据入湖任务服务
 * 提供任务的CRUD操作、执行和监控功能
 * 所有操作自动记录审计信息（通过AbstractAuditingEntity）
 */
@Service
@Transactional
public class IngestionTaskService {

    private static final Logger log = LoggerFactory.getLogger(IngestionTaskService.class);
    private static final ZoneId OBSERVABILITY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter OBSERVABILITY_DAY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId GOVERNANCE_DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration GOVERNANCE_QUEUE_MAX_WAIT = Duration.ofSeconds(30);
    private static final Duration GOVERNANCE_QUEUE_POLL_INTERVAL = Duration.ofSeconds(2);
    private static final List<String> IN_PROGRESS_STATUSES = List.of("running", "preparing", "queued");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final IngestionTaskRepository taskRepository;
    private final IngestionTaskRevisionRepository revisionRepository;
    private final IngestionExecutionRepository executionRepository;
    private final IngestionTaskMapper taskMapper;
    private final IngestionExecutionMapper executionMapper;
    private final AddaxJobService addaxJobService;
    private final AirflowAdapter airflowAdapter;
    private final AirflowClient airflowClient;
    private final AirflowDagService airflowDagService;
    private final com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver sourceResolver;
    private final com.yuzhi.dts.ingestion.service.etl.TargetTableProvisioner targetTableProvisioner;
    private final IncrementalSyncService incrementalSyncService;
    private final AuditService auditService;
    private final IngestionTaskChangeLogService changeLogService;
    private final ExcelParseService excelParseService;
    private final FileUploadService fileUploadService;
    private final CsvParseService csvParseService;
    private final com.yuzhi.dts.ingestion.service.etl.IngestionRetryService retryService;
    private final DagPreheatService dagPreheatService;
    private final PlatformInfraClient platformInfraClient;
    private final SourceConnectorRegistry sourceConnectorRegistry;
    private final ApiIngestionExecutor apiIngestionExecutor;
    private final IngestionClassificationSealGuard classificationSealGuard;
    private final EntityManager entityManager;
    private final TransactionTemplate txTemplate;
    private final Executor ingestionTaskExecutor;
    private IngestionAccessContractService accessContractService;
    private IngestionTaskSecretMigrationService secretMigrationService;
    private IngestionRequiresNewExecutor requiresNewExecutor;
    private RollbackRecoveryService rollbackRecoveryService;

    public IngestionTaskService(
        IngestionTaskRepository taskRepository,
        IngestionTaskRevisionRepository revisionRepository,
        IngestionExecutionRepository executionRepository,
        IngestionTaskMapper taskMapper,
        IngestionExecutionMapper executionMapper,
        AddaxJobService addaxJobService,
        AirflowAdapter airflowAdapter,
        AirflowClient airflowClient,
        AirflowDagService airflowDagService,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver sourceResolver,
        com.yuzhi.dts.ingestion.service.etl.TargetTableProvisioner targetTableProvisioner,
        IncrementalSyncService incrementalSyncService,
        AuditService auditService,
        IngestionTaskChangeLogService changeLogService,
        ExcelParseService excelParseService,
        FileUploadService fileUploadService,
        CsvParseService csvParseService,
        @org.springframework.context.annotation.Lazy com.yuzhi.dts.ingestion.service.etl.IngestionRetryService retryService,
        DagPreheatService dagPreheatService,
        PlatformInfraClient platformInfraClient,
        SourceConnectorRegistry sourceConnectorRegistry,
        ApiIngestionExecutor apiIngestionExecutor,
        IngestionClassificationSealGuard classificationSealGuard,
        EntityManager entityManager,
        PlatformTransactionManager transactionManager,
        @org.springframework.beans.factory.annotation.Qualifier("ingestionTaskExecutor") Executor ingestionTaskExecutor
    ) {
        this.taskRepository = taskRepository;
        this.revisionRepository = revisionRepository;
        this.executionRepository = executionRepository;
        this.taskMapper = taskMapper;
        this.executionMapper = executionMapper;
        this.addaxJobService = addaxJobService;
        this.airflowAdapter = airflowAdapter;
        this.airflowClient = airflowClient;
        this.airflowDagService = airflowDagService;
        this.sourceResolver = sourceResolver;
        this.targetTableProvisioner = targetTableProvisioner;
        this.incrementalSyncService = incrementalSyncService;
        this.auditService = auditService;
        this.changeLogService = changeLogService;
        this.excelParseService = excelParseService;
        this.fileUploadService = fileUploadService;
        this.csvParseService = csvParseService;
        this.retryService = retryService;
        this.dagPreheatService = dagPreheatService;
        this.platformInfraClient = platformInfraClient;
        this.sourceConnectorRegistry = sourceConnectorRegistry;
        this.apiIngestionExecutor = apiIngestionExecutor;
        this.classificationSealGuard = classificationSealGuard;
        this.entityManager = entityManager;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.ingestionTaskExecutor = ingestionTaskExecutor;
    }

    @Autowired
    void setAccessContractService(IngestionAccessContractService accessContractService) {
        this.accessContractService = accessContractService;
    }

    @Autowired
    void setSecretMigrationService(IngestionTaskSecretMigrationService secretMigrationService) {
        this.secretMigrationService = secretMigrationService;
    }

    @Autowired
    void setRequiresNewExecutor(IngestionRequiresNewExecutor requiresNewExecutor) {
        this.requiresNewExecutor = requiresNewExecutor;
    }

    @Autowired
    void setRollbackRecoveryService(RollbackRecoveryService rollbackRecoveryService) {
        this.rollbackRecoveryService = rollbackRecoveryService;
    }

    /**
     * 创建新任务
     * 审计信息会自动填充（createdBy, createdDate）
     */
    public IngestionTaskDTO create(IngestionTaskDTO dto) {
        return create(dto, null, false);
    }

    public IngestionTaskDTO create(IngestionTaskDTO dto, com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolvedSource) {
        return create(dto, resolvedSource, false);
    }

    public IngestionTaskDTO create(
        IngestionTaskDTO dto,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolvedSource,
        boolean skipJob
    ) {
        assertNoRawTaskSecrets(dto);
        log.info("Creating new ingestion task: {} (skipJob={})", dto.getName(), skipJob);

        if (!isFileSourceType(dto.getSourceType()) && dto.getSourceDataSourceId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "来源数据源不能为空，请先选择数据源");
        }

        IngestionTask task = taskMapper.toEntity(dto);
        if (isFileSourceType(task.getSourceType()) && task.getQualityPreCheckEnabled() == null) {
            task.setQualityPreCheckEnabled(true);
        }
        task.setStatus(normalizeInitialStatus(dto.getStatus()));
        task.setAddaxJobPath(null);
        task.setAirflowDagId(null);
        try {
            com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource source =
                resolveSource(task, resolvedSource);
            if (source != null && StringUtils.hasText(source.readerType())) {
                task.setSourceType(source.readerType());
            }
        } catch (Exception ex) {
            log.warn("Draft saved without runtime artifact generation; source resolution failed: {}", ex.getMessage());
        }

        IngestionTask savedTask = taskRepository.save(task);
        if (secretMigrationService != null) {
            taskRepository.flush();
            secretMigrationService.markNewTaskClean(savedTask.getId());
        }
        recordTaskRevision(savedTask, dto.getQualityPolicyRef(), false, false);

        log.info("Created ingestion task with ID: {} by user: {}", savedTask.getId(), savedTask.getCreatedBy());

        try {
            changeLogService.recordTaskCreate(savedTask);
        } catch (Exception ex) {
            log.warn("Failed to record change log for task create: {}", savedTask.getId(), ex);
        }

        // 记录审计
        auditService.auditAction(
            "INGESTION_TASK_CREATE",
            AuditStage.SUCCESS,
            savedTask.getName(),
            Map.of("taskId", savedTask.getId(), "operator", savedTask.getCreatedBy())
        );

        return enrichTaskDto(taskMapper.toDto(savedTask));
    }

    /**
     * Atomically promotes a draft task into the executable state after validating
     * the immutable classification evidence. Active tasks accept idempotent replay.
     */
    public IngestionTaskDTO admit(Long id, JsonNode classificationSeal, JsonNode fieldClassifications) {
        IngestionTask task = taskRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));
        requireSecretMigrationReady(task.getId());
        if (!statusEquals(task.getStatus(), "draft") && !statusEquals(task.getStatus(), "active")) {
            throw new IllegalStateException("Task is not in admissible status: " + task.getStatus());
        }
        boolean wasActive = statusEquals(task.getStatus(), "active");
        boolean hasDraftRevision = accessContractService != null
            && accessContractService.findLatestDraftRevision(task.getId()).isPresent();
        if (wasActive && !hasDraftRevision) {
            if (!java.util.Objects.equals(task.getClassificationSeal(), classificationSeal)
                || !java.util.Objects.equals(task.getFieldClassifications(), fieldClassifications)) {
                throw new IllegalStateException("Task is already active with different classification evidence");
            }
            classificationSealGuard.requireProductionSeal(task);
            return enrichTaskDto(taskMapper.toDto(task));
        }

        IngestionTask draft = hasDraftRevision
            ? accessContractService.materializeLatestDraft(task)
            : task;
        if (draft.getClassificationSeal() == null
            || draft.getClassificationSeal().isNull()
            || classificationSeal == null
            || classificationSeal.isNull()
            || draft.getFieldClassifications() == null
            || draft.getFieldClassifications().isNull()
            || fieldClassifications == null
            || fieldClassifications.isNull()
            || !java.util.Objects.equals(draft.getClassificationSeal(), classificationSeal)
            || !java.util.Objects.equals(draft.getFieldClassifications(), fieldClassifications)) {
            throw new IllegalStateException(
                "CLASSIFICATION_SEAL_STALE: draft classification evidence changed; refresh before admission"
            );
        }

        IngestionTask validationCandidate = classificationValidationCandidate(
            draft,
            classificationSeal,
            fieldClassifications
        );
        classificationSealGuard.requireProductionSeal(validationCandidate);
        requirePassedFilePreCheck(draft);
        verifyManagedFileTask(draft);

        IngestionTaskRevision draftRevision = null;
        if (accessContractService != null) {
            // File verification canonicalizes managed paths. Freeze that canonical
            // candidate before activation so execution metadata and checksum describe
            // exactly the plan copied into the ACTIVE compatibility row.
            if (isFileSourceType(draft.getSourceType()) || !hasDraftRevision) {
                draftRevision = accessContractService.recordDraftRevision(draft, null, true);
            } else {
                Long admissionTaskId = task.getId();
                draftRevision = accessContractService.findLatestDraftRevision(admissionTaskId)
                    .orElseThrow(() -> new IllegalStateException("Task has no draft revision: " + admissionTaskId));
            }
        }

        AdmissionArtifacts artifacts = stageAdmissionArtifacts(draft, draftRevision);
        boolean lifecycleRegistered = false;
        try {
            if (accessContractService != null) {
                accessContractService.refreshDraftRuntimeSnapshot(task.getId(), artifacts.task());
            }
            if (artifacts.stagedDag() != null) {
                if (draftRevision == null || accessContractService == null) {
                    throw new IllegalStateException("Airflow admission requires a persisted draft revision");
                }
                accessContractService.markDraftDagStaged(
                    draftRevision.getId(),
                    artifacts.stagedDag().stagedPath().toString(),
                    artifacts.stagedDag().finalPath().toString(),
                    artifacts.previousAirflowDagId()
                );
                lifecycleRegistered = registerAdmissionArtifactLifecycle(artifacts);
                if (!lifecycleRegistered) {
                    reconcileAdmissionDagDeployment(draftRevision.getId());
                }
                return enrichTaskDto(taskMapper.toDto(artifacts.task()));
            }

            applyPlanSnapshot(task, artifacts.task());
            task.setClassificationSeal(classificationSeal);
            task.setFieldClassifications(fieldClassifications);
            task.setStatus("active");
            task = taskRepository.save(task);
            if (accessContractService != null) {
                accessContractService.activateDraftRevision(task.getId());
            }
        } catch (RuntimeException ex) {
            if (!lifecycleRegistered) {
                discardAdmissionAttemptArtifacts(artifacts);
            }
            throw ex;
        }
        return enrichTaskDto(taskMapper.toDto(task));
    }

    /**
     * 更新任务
     * 审计信息会自动更新（lastModifiedBy, lastModifiedDate）
     */
    public IngestionTaskDTO update(Long id, IngestionTaskDTO dto) {
        assertNoRawTaskSecrets(dto);
        log.info("Updating ingestion task ID: {}", id);

        return taskRepository.findByIdForUpdate(id)
            .map(existingTask -> {
                requireSecretMigrationReady(existingTask.getId());
                boolean activeOrPaused = statusEquals(existingTask.getStatus(), "active")
                    || statusEquals(existingTask.getStatus(), "paused");
                IngestionTask basePlan = activeOrPaused
                    && accessContractService != null
                    && accessContractService.findLatestDraftRevision(existingTask.getId()).isPresent()
                        ? accessContractService.materializeLatestDraft(existingTask)
                        : snapshot(existingTask);
                IngestionTask candidate = snapshot(basePlan);
                taskMapper.partialUpdate(candidate, dto);
                preserveAbsentManagedSecrets(basePlan, candidate, dto);
                if (dto.getSyncConfig() != null) {
                    candidate.setSyncConfig(dto.getSyncConfig());
                }
                candidate.setId(existingTask.getId());

                if (!isFileSourceType(candidate.getSourceType()) && candidate.getSourceDataSourceId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "来源数据源不能为空，请先选择数据源");
                }

                boolean planChanged = planConfigChanged(basePlan, candidate)
                    || StringUtils.hasText(dto.getQualityPolicyRef());
                String requestedStatus = StringUtils.hasText(dto.getStatus()) ? dto.getStatus().trim().toLowerCase(java.util.Locale.ROOT) : null;

                if (activeOrPaused && planChanged) {
                    if (requestedStatus != null
                        && !statusEquals(requestedStatus, existingTask.getStatus())
                        && !statusEquals(requestedStatus, "draft")) {
                        throw new IllegalStateException("State transition cannot be combined with ingestion plan changes");
                    }
                    if (accessContractService == null) {
                        throw new IllegalStateException("Versioned ingestion access contract is required for active task edits");
                    }
                    return saveDraftRevisionForActiveTask(existingTask, dto);
                }

                if (activeOrPaused && statusEquals(requestedStatus, "draft")) {
                    if (accessContractService == null) {
                        throw new IllegalStateException("Versioned ingestion access contract is required for active task edits");
                    }
                    return saveDraftRevisionForActiveTask(existingTask, dto);
                }

                if (activeOrPaused && requestedStatus != null && !statusEquals(requestedStatus, existingTask.getStatus())) {
                    return transitionOperationalState(existingTask, requestedStatus);
                }

                if (activeOrPaused) {
                    return enrichTaskDto(taskMapper.toDto(existingTask));
                }

                if (statusEquals(requestedStatus, "active") || statusEquals(requestedStatus, "paused")) {
                    throw new IllegalStateException("Task must enter active status through /admit");
                }
                IngestionTask before = snapshot(existingTask);
                boolean sourceChanged = sourceIdentityChanged(before, candidate);
                if (sourceChanged && java.util.Objects.equals(before.getClassificationSeal(), candidate.getClassificationSeal())) {
                    candidate.setClassificationSeal(null);
                    candidate.setFieldClassifications(null);
                }
                if (sourceChanged && isFileSourceType(candidate.getSourceType())) {
                    candidate.setStagingTableName(null);
                    candidate.setPreCheckStatus(null);
                }
                candidate.setStatus("draft");
                candidate.setAddaxJobPath(null);
                candidate.setAirflowDagId(null);
                applyPlanSnapshot(existingTask, candidate);
                existingTask.setStatus("draft");
                IngestionTask updatedTask = taskRepository.save(existingTask);
                recordTaskRevision(updatedTask, dto.getQualityPolicyRef(), false, !sourceChanged);
                log.info("Updated ingestion task ID: {} by user: {}", id, updatedTask.getLastModifiedBy());

                try {
                    changeLogService.recordTaskUpdate(before, updatedTask);
                } catch (Exception ex) {
                    log.warn("Failed to record change log for task update: {}", id, ex);
                }

                return enrichTaskDto(taskMapper.toDto(updatedTask));
            })
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));
    }

    /**
     * 获取任务详情
     */
    @Transactional(readOnly = true)
    public Optional<IngestionTaskDTO> findOne(Long id) {
        log.debug("Request to get IngestionTask : {}", id);
        return taskRepository.findById(id)
            .map(taskMapper::toDto)
            .map(this::enrichTaskDto);
    }

    @Transactional(readOnly = true)
    public List<IngestionIncrementalStateDTO> getIncrementalStates(Long taskId) {
        if (taskId == null || !taskRepository.existsById(taskId)) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        return incrementalSyncService.listCheckpointStates(taskId);
    }

    @Transactional(readOnly = true)
    public List<IngestionIncrementalAuditDTO> getIncrementalAudits(Long taskId, Long executionId) {
        if (taskId == null || !taskRepository.existsById(taskId)) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        return incrementalSyncService.listCheckpointAudits(taskId, executionId);
    }

    @Transactional(readOnly = true)
    public Page<IngestionIncrementalAuditDTO> getIncrementalAuditsPage(
        Long taskId,
        Long executionId,
        Collection<Long> executionIds,
        Instant from,
        Instant to,
        Pageable pageable,
        String tableName,
        String status
    ) {
        if (taskId == null || !taskRepository.existsById(taskId)) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        return incrementalSyncService.listCheckpointAuditsPage(taskId, executionId, executionIds, from, to, pageable, tableName, status);
    }

    @Transactional(readOnly = true)
    public IngestionIncrementalAuditSummaryDTO getIncrementalAuditsSummary(
        Long taskId,
        Long executionId,
        Collection<Long> executionIds,
        Instant from,
        Instant to,
        String tableName,
        String status
    ) {
        if (taskId == null || !taskRepository.existsById(taskId)) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        return incrementalSyncService.summarizeCheckpointAudits(taskId, executionId, executionIds, from, to, tableName, status);
    }

    /**
     * 获取任务列表
     */
    @Transactional(readOnly = true)
    public Page<IngestionTaskDTO> findAll(String status, Pageable pageable) {
        log.debug("Request to get all IngestionTasks with status: {}", status);
        if (status != null && !status.isEmpty()) {
            Page<IngestionTaskDTO> page = taskRepository.findByStatus(status, pageable).map(taskMapper::toDto);
            enrichTaskDtos(page.getContent());
            return page;
        }
        Page<IngestionTaskDTO> page = taskRepository.findAll(pageable).map(taskMapper::toDto);
        enrichTaskDtos(page.getContent());
        return page;
    }

    /**
     * 根据源数据源ID查询任务列表
     */
    @Transactional(readOnly = true)
    public List<IngestionTaskDTO> findBySourceDataSourceId(java.util.UUID sourceDataSourceId, boolean includeDeleted) {
        if (sourceDataSourceId == null) {
            return List.of();
        }
        List<IngestionTask> tasks = taskRepository.findBySourceDataSourceId(sourceDataSourceId);
        if (!includeDeleted) {
            tasks = tasks.stream()
                .filter(task -> !"deleted".equalsIgnoreCase(task.getStatus()))
                .toList();
        }
        List<IngestionTaskDTO> result = new ArrayList<>(tasks.stream().map(taskMapper::toDto).toList());
        enrichTaskDtos(result);
        return result;
    }

    /**
     * 逻辑删除任务。
     * 保留任务、执行历史、变更记录和增量检查点，仅停止调度并清理可再生运行制品。
     */
    public IngestionTaskDTO delete(Long id) {
        log.info("Request to delete IngestionTask : {}", id);
        IngestionTask task = taskRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));

        if (!"deleted".equalsIgnoreCase(task.getStatus())) {
            if (executionRepository.countByTaskIdAndStatusesIgnoreCase(id, IN_PROGRESS_STATUSES) > 0) {
                throw new IllegalStateException("任务正在执行，无法删除；请等待执行结束后重试");
            }

            task.setStatus("deleted");
            task.setAirflowEnabled(false);
            taskRepository.save(task);
            log.info("Soft-deleted ingestion task ID: {} by user: {}", id, task.getLastModifiedBy());
        }

        return taskMapper.toDto(task);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RuntimeArtifactCleanupResult cleanupRetiredTaskArtifacts(Long id) {
        IngestionTask task;
        try {
            task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Task not found after retirement: " + id));
            if (!"deleted".equalsIgnoreCase(task.getStatus())) {
                throw new IllegalStateException("Task is not retired: " + id);
            }
        } catch (RuntimeException ex) {
            RuntimeArtifactCleanupResult result = new RuntimeArtifactCleanupResult(
                false,
                false,
                true,
                ex.getClass().getSimpleName(),
                ex.getClass().getSimpleName()
            );
            auditRuntimeArtifactCleanup(id, result);
            return result;
        }
        return cleanupRetiredTaskArtifacts(task);
    }

    private RuntimeArtifactCleanupResult cleanupRetiredTaskArtifacts(IngestionTask task) {
        boolean addaxCleaned = true;
        boolean airflowCleaned = true;
        String addaxErrorType = null;
        String airflowErrorType = null;

        try {
            addaxJobService.deleteJobIfExists(task.getAddaxJobPath());
        } catch (Exception ex) {
            addaxCleaned = false;
            addaxErrorType = ex.getClass().getSimpleName();
            log.warn("Failed to delete Addax job for task {}: {}", task.getId(), ex.getMessage());
        }
        try {
            airflowDagService.deleteDagForTask(task);
        } catch (Exception ex) {
            airflowCleaned = false;
            airflowErrorType = ex.getClass().getSimpleName();
            log.warn("Failed to delete Airflow DAG for task {}: {}", task.getId(), ex.getMessage());
        }
        RuntimeArtifactCleanupResult result = new RuntimeArtifactCleanupResult(
            addaxCleaned,
            airflowCleaned,
            !addaxCleaned || !airflowCleaned,
            addaxErrorType,
            airflowErrorType
        );
        auditRuntimeArtifactCleanup(task.getId(), result);
        return result;
    }

    private void auditRuntimeArtifactCleanup(Long taskId, RuntimeArtifactCleanupResult result) {
        Map<String, Object> auditMeta = new LinkedHashMap<>();
        auditMeta.put("taskId", taskId);
        auditMeta.put("addaxCleaned", result.addaxCleaned());
        auditMeta.put("airflowCleaned", result.airflowCleaned());
        auditMeta.put("retryable", result.retryable());
        if (result.addaxErrorType() != null) {
            auditMeta.put("addaxErrorType", result.addaxErrorType());
        }
        if (result.airflowErrorType() != null) {
            auditMeta.put("airflowErrorType", result.airflowErrorType());
        }
        auditMeta.put("summary", result.retryable() ? "接入任务运行制品清理未完成，可再次删除进行补偿" : "接入任务运行制品清理完成");
        auditService.auditAction(
            "INGESTION_TASK_RUNTIME_ARTIFACT_CLEANUP",
            result.retryable() ? AuditStage.FAIL : AuditStage.SUCCESS,
            String.valueOf(taskId),
            Map.copyOf(auditMeta)
        );
    }

    public record RuntimeArtifactCleanupResult(
        boolean addaxCleaned,
        boolean airflowCleaned,
        boolean retryable,
        String addaxErrorType,
        String airflowErrorType
    ) {}

    /**
     * 执行任务
     * 创建执行记录并触发Addax任务
     */
    public IngestionExecutionDTO execute(Long taskId) {
        return execute(taskId, "MANUAL", null);
    }

    public IngestionExecutionDTO backfill(Long taskId, Instant windowStart, Instant windowEnd, String column) {
        return execute(taskId, "BACKFILL_RANGE", new BackfillWindow(column, windowStart, windowEnd));
    }

    public IngestionExecutionDTO executeInternalApi(
        Long taskId,
        String batchId,
        String triggerMode,
        Instant windowStart,
        Instant windowEnd,
        String column
    ) {
        String mode = normalizeTriggerMode(triggerMode);
        BackfillWindow backfillWindow = null;
        if ("BACKFILL_RANGE".equalsIgnoreCase(mode)
            || windowStart != null
            || windowEnd != null
            || StringUtils.hasText(column)) {
            if (windowStart == null || windowEnd == null) {
                throw new IllegalArgumentException("backfillWindowStart and backfillWindowEnd are required for API backfill");
            }
            mode = "BACKFILL_RANGE";
            backfillWindow = new BackfillWindow(column, windowStart, windowEnd);
        }
        return execute(taskId, mode, backfillWindow, batchId, true, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public IngestionExecutionDTO executeInternalApiForRevision(
        Long taskId,
        String batchId,
        String triggerMode,
        Instant windowStart,
        Instant windowEnd,
        String column,
        Long revisionId,
        String configChecksum,
        String airflowDagId,
        String airflowRunId
    ) {
        String mode = normalizeTriggerMode(triggerMode);
        BackfillWindow backfillWindow = null;
        if ("BACKFILL_RANGE".equalsIgnoreCase(mode)
            || windowStart != null
            || windowEnd != null
            || StringUtils.hasText(column)) {
            if (windowStart == null || windowEnd == null) {
                throw new IllegalArgumentException("backfillWindowStart and backfillWindowEnd are required for API backfill");
            }
            mode = "BACKFILL_RANGE";
            backfillWindow = new BackfillWindow(column, windowStart, windowEnd);
        }
        ExactRevisionContext exactRevision = new ExactRevisionContext(revisionId, configChecksum, airflowDagId, airflowRunId);
        validateExactRevisionContext(exactRevision);
        String resolvedMode = mode;
        BackfillWindow resolvedBackfillWindow = backfillWindow;
        try {
            return inRequiresNewTransaction(() -> {
                Optional<IngestionExecution> existing = executionRepository.findFirstByTaskIdAndAirflowDagIdAndExecutionId(
                    taskId,
                    airflowDagId,
                    airflowRunId
                );
                if (existing.isPresent()) {
                    return toExactExistingExecution(existing.orElseThrow(), exactRevision);
                }
                return execute(taskId, resolvedMode, resolvedBackfillWindow, batchId, true, exactRevision);
            });
        } catch (DataIntegrityViolationException duplicate) {
            return inRequiresNewTransaction(() -> executionRepository
                .findFirstByTaskIdAndAirflowDagIdAndExecutionId(taskId, airflowDagId, airflowRunId)
                .map(existing -> toExactExistingExecution(existing, exactRevision))
                .orElseThrow(() -> duplicate));
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public IngestionExecutionDTO registerScheduledExecution(
        Long taskId,
        Long revisionId,
        String configChecksum,
        String airflowDagId,
        String airflowRunId
    ) {
        ExactRevisionContext exactRevision = new ExactRevisionContext(revisionId, configChecksum, airflowDagId, airflowRunId);
        validateExactRevisionContext(exactRevision);
        try {
            IngestionExecutionDTO registered = inRequiresNewTransaction(() -> {
                Optional<IngestionExecution> existing = executionRepository.findFirstByTaskIdAndAirflowDagIdAndExecutionId(
                    taskId,
                    airflowDagId,
                    airflowRunId
                );
                if (existing.isPresent()) {
                    return toExactExistingExecution(existing.orElseThrow(), exactRevision);
                }

                IngestionTask canonicalTask = loadScheduledRegistrationTask(taskId);
                IngestionTask runtimeTask = materializeExactRevisionTask(canonicalTask, exactRevision);
                if (isApiSourceTask(runtimeTask)) {
                    throw new IllegalArgumentException("API source DAGs must use the internal execution endpoint");
                }

                IngestionExecution execution = new IngestionExecution();
                execution.setTask(canonicalTask);
                execution.setExecutionId(airflowRunId);
                execution.setAirflowDagId(airflowDagId);
                execution.setBatchId(resolveBatchId(taskId, airflowRunId));
                execution.setStatus("running");
                execution.setStartTime(Instant.now());
                execution.setCreatedAt(Instant.now());
                execution.setTriggerMode("SCHEDULED");
                execution.setReplaceMode(resolveReplaceMode(runtimeTask, "SCHEDULED"));
                IngestionExecutionLineageSnapshot.apply(execution, runtimeTask);
                accessContractService.bindExactRevision(execution, canonicalTask, revisionId, configChecksum);
                execution = executionRepository.saveAndFlush(execution);

                canonicalTask.setLastExecutionStatus("running");
                canonicalTask.setLastExecutedAt(execution.getStartTime());
                taskRepository.save(canonicalTask);
                return executionMapper.toDto(execution);
            });
            if (registered == null) {
                throw new IllegalStateException("Scheduled execution registration transaction returned no result");
            }
            return registered;
        } catch (DataIntegrityViolationException duplicate) {
            return inRequiresNewTransaction(() -> executionRepository
                .findFirstByTaskIdAndAirflowDagIdAndExecutionId(taskId, airflowDagId, airflowRunId)
                .map(existing -> toExactExistingExecution(existing, exactRevision))
                .orElseThrow(() -> duplicate));
        }
    }

    public IngestionTaskDTO validateAsyncExecutionRequest(Long taskId) {
        return taskMapper.toDto(loadExecutableTask(taskId));
    }

    public void validateAsyncRetryRequest(Long taskId, Long executionId, String retryMode) {
        ValidatedRetryRequest validated = validateRetryRequest(taskId, executionId, retryMode);
        validateRetryEligibility(validated);
        loadExecutableTask(taskId);
    }

    private IngestionExecutionDTO execute(Long taskId, String triggerMode) {
        return execute(taskId, triggerMode, null);
    }

    private IngestionExecutionDTO execute(Long taskId, String triggerMode, BackfillWindow backfillWindow) {
        return execute(taskId, triggerMode, backfillWindow, null, false, null);
    }

    private IngestionExecutionDTO execute(
        Long taskId,
        String triggerMode,
        BackfillWindow backfillWindow,
        String requestedBatchId,
        boolean apiOnly,
        ExactRevisionContext exactRevision
    ) {
        return execute(taskId, triggerMode, backfillWindow, requestedBatchId, apiOnly, exactRevision, null);
    }

    private IngestionExecutionDTO execute(
        Long taskId,
        String triggerMode,
        BackfillWindow backfillWindow,
        String requestedBatchId,
        boolean apiOnly,
        ExactRevisionContext exactRevision,
        RetryLineage retryLineage
    ) {
        log.info("Executing ingestion task ID: {}", taskId);

        IngestionTask canonicalTask = loadExecutableTask(taskId);
        IngestionTask task = exactRevision == null ? canonicalTask : materializeExactRevisionTask(canonicalTask, exactRevision);
        if (apiOnly && !isApiSourceTask(task)) {
            throw new IllegalArgumentException("Internal API ingestion endpoint only supports API source tasks: " + taskId);
        }
        GovernancePolicy policy = resolveGovernancePolicy(task);
        BackfillWindow resolvedBackfill = null;
        if (backfillWindow != null) {
            String resolvedColumn = incrementalSyncService.validateBackfillWindow(
                task,
                backfillWindow.column(),
                backfillWindow.windowStart(),
                backfillWindow.windowEnd()
            );
            resolvedBackfill = new BackfillWindow(resolvedColumn, backfillWindow.windowStart(), backfillWindow.windowEnd());
        }

        // Phase 1 (synchronous): validate, governance check, create execution record
        String governanceBlockedReason = evaluateGovernanceBlock(task, policy, null);
        if (StringUtils.hasText(governanceBlockedReason)) {
            if (!"QUEUE".equalsIgnoreCase(policy.rejectPolicy())) {
                throw new RuntimeException("Failed to execute task: " + governanceBlockedReason);
            }
            // Queue mode: governance wait happens in the async phase
        }

        IngestionExecution execution = new IngestionExecution();
        execution.setTask(canonicalTask);
        execution.setStatus("preparing");
        execution.setCreatedAt(Instant.now());
        execution.setReplaceMode(resolveReplaceMode(task, triggerMode));
        execution.setTriggerMode(normalizeTriggerMode(triggerMode));
        if (retryLineage != null) {
            execution.setParentExecutionId(retryLineage.parentExecutionId());
            execution.setRetryCount(retryLineage.retryCount());
            execution.setMaxRetries(retryLineage.maxRetries());
        }
        if (resolvedBackfill != null) {
            execution.setBackfillColumn(resolvedBackfill.column());
            execution.setBackfillWindowStart(resolvedBackfill.windowStart());
            execution.setBackfillWindowEnd(resolvedBackfill.windowEnd());
        }
        execution.setBatchId(resolveBatchId(taskId, requestedBatchId));
        execution.setExecutionId(
            exactRevision == null ? "preparing-" + UUID.randomUUID().toString().substring(0, 8) : exactRevision.airflowRunId()
        );
        if (exactRevision != null) {
            execution.setAirflowDagId(exactRevision.airflowDagId());
        }
        IngestionExecutionLineageSnapshot.apply(execution, task);
        if (accessContractService != null) {
            if (exactRevision == null) {
                accessContractService.bindActiveRevision(execution, canonicalTask);
            } else {
                accessContractService.bindExactRevision(
                    execution,
                    canonicalTask,
                    exactRevision.revisionId(),
                    exactRevision.configChecksum()
                );
            }
        }
        execution = exactRevision == null && retryLineage == null
            ? executionRepository.save(execution)
            : executionRepository.saveAndFlush(execution);

        canonicalTask.setLastExecutionStatus("preparing");
        taskRepository.save(canonicalTask);

        // Phase 2 (asynchronous): job generation, DAG wait, Airflow trigger
        // Must run AFTER transaction commits so the execution record is visible to the new thread.
        final Long executionId = execution.getId();
        final Long finalTaskId = taskId;
        final GovernancePolicy finalPolicy = policy;
        final String finalGovernanceBlockedReason = governanceBlockedReason;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    try {
                        executeAirflowTriggerPhase(finalTaskId, executionId, finalPolicy, finalGovernanceBlockedReason);
                    } catch (Exception ex) {
                        log.error("Async trigger phase failed for task {} execution {}: {}", finalTaskId, executionId, ex.getMessage(), ex);
                    }
                }, ingestionTaskExecutor);
            }
        });

        log.info("Execution {} for task {} submitted to background trigger phase", execution.getId(), taskId);
        return executionMapper.toDto(execution);
    }

    /**
     * Phase 2 runs in a background virtual thread AFTER the calling transaction has
     * committed.  Because there is no inherited Hibernate Session / transaction, we
     * use a programmatic TransactionTemplate to open a fresh one.  The @Transactional
     * annotation on this private method has no effect (Spring AOP cannot proxy private
     * methods), so it is intentionally removed.
     */
    private void executeAirflowTriggerPhase(
        Long taskId,
        Long executionId,
        GovernancePolicy policy,
        String governanceBlockedReason
    ) {
        ApiExecutionWork apiWork = txTemplate.execute(status -> loadApiExecutionWork(taskId, executionId));
        if (apiWork != null) {
            executeApiTriggerPhase(apiWork, policy, governanceBlockedReason);
            return;
        }
        txTemplate.executeWithoutResult(txStatus -> {
            doExecuteAirflowTriggerPhase(taskId, executionId, policy, governanceBlockedReason);
        });
    }

    private ApiExecutionWork loadApiExecutionWork(Long taskId, Long executionId) {
        IngestionExecution execution = executionRepository.findById(executionId).orElse(null);
        IngestionTask canonicalTask = taskRepository.findById(taskId).orElse(null);
        if (execution == null || canonicalTask == null) {
            return null;
        }
        IngestionTask runtime = materializeApiRuntimeTask(canonicalTask, execution);
        return isApiSourceTask(runtime)
            ? new ApiExecutionWork(runtime, copyApiExecution(execution))
            : null;
    }

    private void executeApiTriggerPhase(
        ApiExecutionWork initialWork,
        GovernancePolicy policy,
        String governanceBlockedReason
    ) {
        Long taskId = initialWork.task().getId();
        Long executionId = initialWork.execution().getId();
        IngestionTask runtimeTask = initialWork.task();
        boolean executionSucceeded = false;
        try {
            if (StringUtils.hasText(governanceBlockedReason) && "QUEUE".equalsIgnoreCase(policy.rejectPolicy())) {
                boolean ready = waitForGovernanceSlot(
                    runtimeTask,
                    policy,
                    executionId,
                    GOVERNANCE_QUEUE_MAX_WAIT,
                    GOVERNANCE_QUEUE_POLL_INTERVAL
                );
                if (!ready) {
                    throw new IllegalStateException(
                        "触发治理队列等待超时(" + GOVERNANCE_QUEUE_MAX_WAIT.toSeconds() + "s)：" + governanceBlockedReason
                    );
                }
            }

            ApiExecutionWork claimed = txTemplate.execute(status -> claimApiExecution(taskId, executionId));
            if (claimed == null) {
                throw new IllegalStateException("API execution claim returned no result");
            }
            runtimeTask = claimed.task();
            IngestionExecution runtimeExecution = claimed.execution();
            com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource source = resolveSource(runtimeTask, null);
            ApiIngestionResult result = runApiIngestionPlan(runtimeTask, runtimeExecution, source);

            IngestionTask completedTask = runtimeTask;
            IngestionExecution completedExecution = txTemplate.execute(status ->
                finalizeApiExecutionSuccess(taskId, executionId, runtimeExecution, result)
            );
            if (completedExecution == null) {
                throw new IllegalStateException("API execution finalizer returned no result");
            }
            executionSucceeded = true;
            if (rollbackRecoveryService != null) {
                rollbackRecoveryService.recordSuccessfulRevalidation(completedTask, completedExecution);
            }
            triggerPostIngestionQualityCheck(completedTask, completedExecution);
            syncExecutionLineageQuietly(completedTask, completedExecution);
            auditApiExecution(completedTask, completedExecution, true, null);
            log.info("Completed API execution {} for task ID: {}", completedExecution.getExecutionId(), taskId);
        } catch (Exception ex) {
            String failureMessage = extractFailureMessage(ex);
            if (executionSucceeded) {
                log.warn("[api-execution] post-success hook failed for task {}: {}", taskId, failureMessage, ex);
                return;
            }
            IngestionTask failedTask = runtimeTask;
            IngestionExecution failedExecution = txTemplate.execute(status ->
                finalizeApiExecutionFailure(taskId, executionId, failedTask, failureMessage)
            );
            if (failedExecution != null) {
                syncExecutionLineageQuietly(failedTask, failedExecution);
                auditApiExecution(failedTask, failedExecution, false, failureMessage);
            }
            log.error("[api-execution] failed for task {}: {}", taskId, failureMessage, ex);
        }
    }

    private ApiExecutionWork claimApiExecution(Long taskId, Long executionId) {
        IngestionExecution execution = entityManager.find(
            IngestionExecution.class,
            executionId,
            LockModeType.PESSIMISTIC_WRITE
        );
        if (execution == null) {
            throw new IllegalStateException("API execution not found: " + executionId);
        }
        IngestionTask canonicalTask = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalStateException("API ingestion task not found: " + taskId));
        entityManager.refresh(canonicalTask, LockModeType.PESSIMISTIC_WRITE);
        requireActiveProductionTask(canonicalTask);
        if (!statusEquals(execution.getStatus(), "preparing")) {
            throw new IllegalStateException("API execution is not claimable: " + execution.getStatus());
        }
        IngestionTask runtime = materializeApiRuntimeTask(canonicalTask, execution);
        if (!isApiSourceTask(runtime)) {
            throw new IllegalStateException("Execution revision is not an API ingestion task");
        }
        execution.setExecutionId("api-" + UUID.randomUUID().toString().substring(0, 8));
        execution.setStatus("running");
        execution.setStartTime(Instant.now());
        executionRepository.save(execution);
        canonicalTask.setLastExecutedAt(execution.getStartTime());
        canonicalTask.setLastExecutionStatus("running");
        taskRepository.save(canonicalTask);
        return new ApiExecutionWork(runtime, copyApiExecution(execution));
    }

    private IngestionTask materializeApiRuntimeTask(IngestionTask canonicalTask, IngestionExecution execution) {
        if (accessContractService == null) {
            return canonicalTask;
        }
        IngestionTask runtime = accessContractService.materializeExecutionTask(
            canonicalTask,
            execution.getTaskRevisionId()
        );
        return runtime == null ? canonicalTask : runtime;
    }

    private void doExecuteAirflowTriggerPhase(
        Long taskId,
        Long executionId,
        GovernancePolicy policy,
        String governanceBlockedReason
    ) {
        IngestionExecution execution = executionRepository.findById(executionId).orElse(null);
        if (execution == null) {
            log.error("[async-trigger] execution {} not found, aborting", executionId);
            return;
        }
        // Load task directly from repository — not via execution.getTask() lazy proxy,
        // because this runs in a new session outside the original request scope.
        IngestionTask canonicalTask = taskRepository.findById(taskId).orElse(null);
        if (canonicalTask == null) {
            markExecutionFailed(execution, null, "任务不存在");
            return;
        }
        IngestionTask task = accessContractService == null
            ? snapshot(canonicalTask)
            : accessContractService.materializeExecutionTask(canonicalTask, execution.getTaskRevisionId());

        boolean airflowTriggered = false;
        try {
            requireActiveProductionTask(task);
            // Governance queue wait (if applicable)
            if (StringUtils.hasText(governanceBlockedReason) && "QUEUE".equalsIgnoreCase(policy.rejectPolicy())) {
                boolean ready = waitForGovernanceSlot(task, policy, execution.getId(), GOVERNANCE_QUEUE_MAX_WAIT, GOVERNANCE_QUEUE_POLL_INTERVAL);
                if (!ready) {
                    throw new IllegalStateException(
                        "触发治理队列等待超时(" + GOVERNANCE_QUEUE_MAX_WAIT.toSeconds() + "s)：" + governanceBlockedReason
                    );
                }
                entityManager.refresh(canonicalTask, LockModeType.PESSIMISTIC_WRITE);
                if (!statusEquals(canonicalTask.getStatus(), "active")) {
                    throw new IllegalStateException("Task is not in executable status: " + canonicalTask.getStatus());
                }
            }
            verifyManagedFileTask(task);

            boolean airflowEnabled = isAirflowEnabled(task);
            com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource source = resolveSource(task, null);
            Map<String, Object> runtimeReaderOverrides = isBackfillExecution(execution)
                ? incrementalSyncService.buildBackfillReaderRuntimeOverrides(
                    task,
                    source,
                    execution.getBackfillColumn(),
                    execution.getBackfillWindowStart(),
                    execution.getBackfillWindowEnd()
                )
                : incrementalSyncService.buildReaderRuntimeOverrides(task, source);
            validateExcelFormulaOrFail(task);
            validateUploadedFileColumnsOrFail(task);
            Map<String, Object> runtimeContext = buildExecutionRuntimeContext(task, execution);
            task = prepareExecutionAddaxJob(task, source, runtimeReaderOverrides, runtimeContext, execution);
            execution.setDroppedTables(resolveDroppedTables(task));
            boolean apiTask = isApiSourceTask(task);
            // API tasks provision their ODS landing table inside the DAG (raw_record + technical columns),
            // and they don't have an Addax job to resolve writer columns from.
            if (!isFileSourceType(task.getSourceType()) && !apiTask) {
                targetTableProvisioner.ensureTargetTables(task, source == null ? null : source.readerConfig(), execution);
            }
            if (!apiTask) {
                addaxJobService.resolveWriterColumnsIfNeeded(task.getAddaxJobPath());
            }
            if (airflowEnabled && !apiTask) {
                task = prepareExecutionDag(task, execution);
                execution.setAirflowDagId(task.getAirflowDagId());
                executionRepository.save(execution);
            }
            execution.setStartTime(Instant.now());

            if (apiTask) {
                executeApiIngestionPlan(task, execution, source);
                triggerPostIngestionQualityCheck(task, execution);
                canonicalTask.setLastExecutedAt(Instant.now());
                canonicalTask.setLastExecutionStatus(execution.getStatus());
                taskRepository.save(canonicalTask);
                syncExecutionLineageQuietly(task, execution);
                log.info("Completed API execution {} for task ID: {}", execution.getExecutionId(), taskId);

                Map<String, Object> auditMeta = new LinkedHashMap<>();
                auditMeta.put("taskId", taskId);
                auditMeta.put("executionId", execution.getId());
                auditMeta.put("batchId", execution.getBatchId());
                auditMeta.put("operator", resolveOperator(task));
                auditMeta.put("engine", "api-http");
                auditService.auditAction("INGESTION_TASK_EXECUTE", AuditStage.SUCCESS, task.getName(), auditMeta);
                return;
            }

            if (airflowEnabled) {
                Map<String, Object> conf = new java.util.LinkedHashMap<>();
                if (!apiTask) {
                    String airflowJobPath = addaxJobService.toContainerJobPath(task.getAddaxJobPath());
                    if (!StringUtils.hasText(airflowJobPath)) {
                        throw new IllegalStateException("Addax 作业路径无效，请先重建作业");
                    }
                    conf.put("job_path", airflowJobPath);
                }
                conf.put("task_id", taskId);
                conf.put("task_name", StringUtils.hasText(task.getName()) ? task.getName() : ("task-" + taskId));
                conf.put("batch_id", execution.getBatchId());
                conf.put("ingestion_execution_id", execution.getId());
                if (StringUtils.hasText(policy.priority())) conf.put("priority", policy.priority());
                if (StringUtils.hasText(policy.rejectPolicy())) conf.put("reject_policy", policy.rejectPolicy());
                if (StringUtils.hasText(policy.projectKey())) conf.put("project_key", policy.projectKey());
                if (isBackfillExecution(execution)) {
                    conf.put("backfill_column", execution.getBackfillColumn());
                    conf.put("backfill_window_start", execution.getBackfillWindowStart().toString());
                    conf.put("backfill_window_end", execution.getBackfillWindowEnd().toString());
                }

                Map<String, Object> airflowResult = airflowAdapter.triggerIfRequested(
                    new AirflowAdapter.AirflowRequest(true, task.getAirflowDagId(), null, null, null),
                    conf, true
                );
                log.info("Airflow trigger result for task {}: {}", taskId, airflowResult);
                String status = airflowResult == null ? null : String.valueOf(airflowResult.get("status"));
                if (!"triggered".equalsIgnoreCase(status)) {
                    String message = airflowResult == null ? null : String.valueOf(airflowResult.get("message"));
                    String code = airflowResult == null ? null : String.valueOf(airflowResult.get("code"));
                    String normalizedCode = (code == null || "null".equalsIgnoreCase(code) || code.isBlank()) ? null : code.trim();
                    String normalizedMessage = (message == null || "null".equalsIgnoreCase(message) || message.isBlank()) ? null : message.trim();
                    String detail = normalizedMessage;
                    if (normalizedCode != null) {
                        detail = normalizedMessage == null ? "[" + normalizedCode + "]" : "[" + normalizedCode + "] " + normalizedMessage;
                    }
                    throw new IllegalStateException("Airflow 触发失败" + (detail == null ? "" : (": " + detail)));
                }
                airflowTriggered = true;
                String dagRunId = extractDagRunId(airflowResult);
                execution.setExecutionId(StringUtils.hasText(dagRunId) ? dagRunId : ("airflow-" + UUID.randomUUID().toString().substring(0, 8)));
                execution.setStatus("running");
                executionRepository.save(execution);
            } else {
                execution.setExecutionId("manual-" + UUID.randomUUID().toString().substring(0, 8));
                execution.setStatus("running");
                executionRepository.save(execution);
            }

            canonicalTask.setLastExecutedAt(Instant.now());
            canonicalTask.setLastExecutionStatus("running");
            taskRepository.save(canonicalTask);
            if (!airflowEnabled) {
                syncExecutionLineageQuietly(task, execution);
            }
            log.info("Started execution {} for task ID: {}", execution.getExecutionId(), taskId);

            auditService.auditAction("INGESTION_TASK_EXECUTE", AuditStage.SUCCESS, task.getName(),
                Map.of("taskId", taskId, "executionId", execution.getId(), "batchId", execution.getBatchId(), "operator", resolveOperator(task)));

        } catch (Exception e) {
            if (airflowTriggered) {
                log.warn("Post-trigger error for task {} — DAG already running, keeping status=running", taskId, e);
                return;
            }
            log.error("[async-trigger] failed for task {}: {}", taskId, e.getMessage(), e);
            markExecutionFailed(execution, task, extractFailureMessage(e));
        }
    }

    private void markExecutionFailed(IngestionExecution execution, IngestionTask task, String failureMessage) {
        IngestionTask canonicalTask = execution != null && execution.getTask() != null
            ? execution.getTask()
            : task;
        String failureCategory = ExecutionFailureClassifier.classify(failureMessage);
        String failureAdvice = ExecutionFailureClassifier.advice(failureCategory);

        if (execution.getStartTime() == null) {
            execution.setStartTime(execution.getCreatedAt() != null ? execution.getCreatedAt() : Instant.now());
        }
        execution.setStatus("failed");
        execution.setEndTime(Instant.now());
        execution.setErrorMessage(failureMessage);
        execution.setFailureCategory(failureCategory);
        execution.setFailureAdvice(failureAdvice);
        if (task != null) {
            IngestionExecutionLineageSnapshot.applyIfMissing(execution, task);
        }
        retryService.scheduleRetryIfEligible(execution);
        executionRepository.save(execution);

        if (canonicalTask != null) {
            canonicalTask.setLastExecutionStatus("failed");
            taskRepository.save(canonicalTask);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("taskId", canonicalTask.getId());
            meta.put("failureCategory", failureCategory);
            meta.put("failureAdvice", failureAdvice);
            if (StringUtils.hasText(failureMessage)) meta.put("error", failureMessage);
            auditService.auditAction("INGESTION_TASK_EXECUTE", AuditStage.FAIL, canonicalTask.getName(), meta);
            syncExecutionLineageQuietly(task != null ? task : canonicalTask, execution);
        }
    }

    private void executeApiIngestionPlan(
        IngestionTask task,
        IngestionExecution execution,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource source
    ) {
        execution.setExecutionId("api-" + UUID.randomUUID().toString().substring(0, 8));
        execution.setStatus("running");
        executionRepository.save(execution);

        ApiIngestionResult result = runApiIngestionPlan(task, execution, source);

        execution.setStatus("success");
        execution.setEndTime(Instant.now());
        if (result.rowsRead() != null) {
            execution.setRowsRead(result.rowsRead());
        }
        if (result.rowsWritten() != null) {
            execution.setRowsWritten(result.rowsWritten());
        }
        executionRepository.save(execution);
        if (rollbackRecoveryService != null) {
            rollbackRecoveryService.recordSuccessfulRevalidation(task, execution);
        }
    }

    private ApiIngestionResult runApiIngestionPlan(
        IngestionTask task,
        IngestionExecution execution,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource source
    ) {
        SourceConnectorContext context = buildSourceConnectorContext(task, source);
        SourceConnector connector = sourceConnectorRegistry.find(context)
            .orElseThrow(() -> new IllegalStateException("未找到 API 数据源连接器: " + task.getSourceType()));
        ExecutionPlan plan = connector.buildExecutionPlan(context);
        ApiIngestionResult result = apiIngestionExecutor.execute(plan, task, execution);
        if (result == null) {
            throw new IllegalStateException("API 入湖执行未返回结果");
        }
        if (!result.success()) {
            String message = StringUtils.hasText(result.errorMessage()) ? result.errorMessage() : "API 入湖执行失败";
            throw new IllegalStateException(message);
        }
        return result;
    }

    private IngestionExecution finalizeApiExecutionSuccess(
        Long taskId,
        Long executionId,
        IngestionExecution runtimeExecution,
        ApiIngestionResult result
    ) {
        IngestionExecution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new IllegalStateException("API execution not found during finalization: " + executionId));
        IngestionTask canonicalTask = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalStateException("API task not found during finalization: " + taskId));
        entityManager.refresh(canonicalTask, LockModeType.PESSIMISTIC_WRITE);
        execution.setStatus("success");
        execution.setEndTime(Instant.now());
        execution.setRowsRead(result.rowsRead());
        execution.setRowsWritten(result.rowsWritten());
        execution.setSourceTables(runtimeExecution.getSourceTables());
        execution.setTargetTables(runtimeExecution.getTargetTables());
        executionRepository.save(execution);
        canonicalTask.setLastExecutedAt(execution.getEndTime());
        canonicalTask.setLastExecutionStatus("success");
        taskRepository.save(canonicalTask);
        return copyApiExecution(execution);
    }

    private IngestionExecution finalizeApiExecutionFailure(
        Long taskId,
        Long executionId,
        IngestionTask runtimeTask,
        String failureMessage
    ) {
        IngestionExecution execution = executionRepository.findById(executionId).orElse(null);
        if (execution == null) {
            return null;
        }
        IngestionTask canonicalTask = taskRepository.findById(taskId).orElse(null);
        if (canonicalTask != null) {
            entityManager.refresh(canonicalTask, LockModeType.PESSIMISTIC_WRITE);
        }
        String failureCategory = ExecutionFailureClassifier.classify(failureMessage);
        execution.setStatus("failed");
        execution.setEndTime(Instant.now());
        execution.setErrorMessage(failureMessage);
        execution.setFailureCategory(failureCategory);
        execution.setFailureAdvice(ExecutionFailureClassifier.advice(failureCategory));
        if (execution.getStartTime() == null) {
            execution.setStartTime(execution.getCreatedAt() == null ? Instant.now() : execution.getCreatedAt());
        }
        if (runtimeTask != null) {
            IngestionExecutionLineageSnapshot.applyIfMissing(execution, runtimeTask);
        }
        retryService.scheduleRetryIfEligible(execution);
        executionRepository.save(execution);
        if (canonicalTask != null) {
            canonicalTask.setLastExecutionStatus("failed");
            taskRepository.save(canonicalTask);
        }
        return copyApiExecution(execution);
    }

    private IngestionExecution copyApiExecution(IngestionExecution source) {
        IngestionExecution copy = new IngestionExecution();
        copy.setId(source.getId());
        copy.setVersion(source.getVersion());
        copy.setExecutionId(source.getExecutionId());
        copy.setTaskRevisionId(source.getTaskRevisionId());
        copy.setRevisionNumber(source.getRevisionNumber());
        copy.setEffectiveConfigChecksum(source.getEffectiveConfigChecksum());
        copy.setQualityPolicyRef(source.getQualityPolicyRef());
        copy.setQualityRunId(source.getQualityRunId());
        copy.setAirflowDagId(source.getAirflowDagId());
        copy.setBatchId(source.getBatchId());
        copy.setStatus(source.getStatus());
        copy.setStartTime(source.getStartTime());
        copy.setEndTime(source.getEndTime());
        copy.setRowsRead(source.getRowsRead());
        copy.setRowsWritten(source.getRowsWritten());
        copy.setErrorMessage(source.getErrorMessage());
        copy.setFailureCategory(source.getFailureCategory());
        copy.setFailureAdvice(source.getFailureAdvice());
        copy.setTriggerMode(source.getTriggerMode());
        copy.setReplaceMode(source.getReplaceMode());
        copy.setBackfillColumn(source.getBackfillColumn());
        copy.setBackfillWindowStart(source.getBackfillWindowStart());
        copy.setBackfillWindowEnd(source.getBackfillWindowEnd());
        copy.setSourceTables(source.getSourceTables() == null ? null : source.getSourceTables().deepCopy());
        copy.setTargetTables(source.getTargetTables() == null ? null : source.getTargetTables().deepCopy());
        copy.setRetryCount(source.getRetryCount());
        copy.setMaxRetries(source.getMaxRetries());
        copy.setNextRetryAt(source.getNextRetryAt());
        copy.setRetryExhausted(source.isRetryExhausted());
        copy.setParentExecutionId(source.getParentExecutionId());
        copy.setCreatedAt(source.getCreatedAt());
        return copy;
    }

    private void auditApiExecution(
        IngestionTask task,
        IngestionExecution execution,
        boolean success,
        String failureMessage
    ) {
        if (task == null || execution == null) {
            return;
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("taskId", task.getId());
        meta.put("executionId", execution.getId());
        meta.put("batchId", execution.getBatchId());
        meta.put("operator", resolveOperator(task));
        meta.put("engine", "api-http");
        if (StringUtils.hasText(failureMessage)) {
            meta.put("error", failureMessage);
        }
        auditService.auditAction(
            "INGESTION_TASK_EXECUTE",
            success ? AuditStage.SUCCESS : AuditStage.FAIL,
            task.getName(),
            meta
        );
    }

    private record ApiExecutionWork(IngestionTask task, IngestionExecution execution) {}

    /**
     * API ingestion does not pass through the Airflow status synchronizer, so it
     * must trigger the same centrally managed post-run quality policy here. The
     * quality trigger is best-effort and never rewrites the ingestion outcome.
     */
    private void triggerPostIngestionQualityCheck(IngestionTask task, IngestionExecution execution) {
        if (task == null || execution == null || Boolean.TRUE.equals(task.getQualityPreCheckEnabled())) {
            return;
        }
        String qualityPolicyRef = execution.getQualityPolicyRef();
        if (!StringUtils.hasText(qualityPolicyRef)) {
            log.debug("[quality] no official qualityPolicyRef bound for API task={}, skipping", task.getId());
            return;
        }
        try {
            String qualityRunId = platformInfraClient.triggerQualityRunByPolicyRef(qualityPolicyRef, "INGESTION");
            execution.setQualityRunId(qualityRunId);
            if (execution.getId() != null) {
                txTemplate.executeWithoutResult(status -> executionRepository.findById(execution.getId()).ifPresent(managed -> {
                    managed.setQualityRunId(qualityRunId);
                    executionRepository.save(managed);
                }));
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("taskId", task.getId());
            meta.put("executionId", execution.getId());
            meta.put("qualityPolicyRef", qualityPolicyRef);
            if (StringUtils.hasText(qualityRunId)) {
                meta.put("qualityRunId", qualityRunId);
            }
            meta.put("triggerType", "INGESTION");
            auditService.auditAction("INGESTION_TASK_QUALITY_TRIGGER", AuditStage.SUCCESS, task.getName(), meta);
        } catch (Exception ex) {
            log.warn("[quality] API post-ingestion quality trigger failed for task={}: {}", task.getId(), ex.getMessage());
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("taskId", task.getId());
            meta.put("executionId", execution.getId());
            meta.put("qualityPolicyRef", qualityPolicyRef);
            meta.put("error", ex.getMessage());
            auditService.auditAction("INGESTION_TASK_QUALITY_TRIGGER", AuditStage.FAIL, task.getName(), meta);
        }
    }

    private SourceConnectorContext buildSourceConnectorContext(
        IngestionTask task,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource source
    ) {
        return new SourceConnectorContext(
            task.getId(),
            task.getName(),
            task.getSourceDataSourceId(),
            task.getSourceType(),
            mergedApiSourceConfig(task, source),
            task.getSyncMode(),
            jsonObjectMap(task.getSyncConfig(), "syncConfig"),
            List.of()
        );
    }

    private Map<String, Object> mergedApiSourceConfig(
        IngestionTask task,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource source
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        Map<String, Object> managedConfig = source == null || source.readerConfig() == null
            ? Map.of()
            : source.readerConfig();
        merged.putAll(managedConfig);
        JsonNode taskConfig = task.getSourceConfig();
        if (task.getSourceDataSourceId() != null) {
            taskConfig = stripRawSecrets(taskConfig);
        }
        merged.putAll(jsonObjectMap(taskConfig, "sourceConfig"));
        overlayManagedSecrets(merged, managedConfig);
        return merged;
    }

    @SuppressWarnings("unchecked")
    private void overlayManagedSecrets(Map<String, Object> target, Map<String, Object> managed) {
        for (Map.Entry<String, Object> entry : managed.entrySet()) {
            Object managedValue = entry.getValue();
            JsonNode managedNode = OBJECT_MAPPER.valueToTree(managedValue);
            if (IngestionSensitiveConfigSupport.isRawSecretKeyName(entry.getKey())
                || IngestionSensitiveConfigSupport.containsRawSecrets(managedNode)) {
                target.put(entry.getKey(), managedValue);
                continue;
            }
            if (managedValue instanceof Map<?, ?> managedNested) {
                Object targetValue = target.get(entry.getKey());
                Map<String, Object> targetNested = targetValue instanceof Map<?, ?> existing
                    ? new LinkedHashMap<>((Map<String, Object>) existing)
                    : new LinkedHashMap<>();
                overlayManagedSecrets(targetNested, (Map<String, Object>) managedNested);
                target.put(entry.getKey(), targetNested);
            }
        }
    }

    private Map<String, Object> jsonObjectMap(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException(fieldName + " 必须是 JSON 对象");
        }
        Map<String, Object> value = OBJECT_MAPPER.convertValue(node, MAP_TYPE);
        return value == null ? Map.of() : value;
    }

    private void syncExecutionLineageQuietly(IngestionTask task, IngestionExecution execution) {
        if (task == null || execution == null) {
            return;
        }
        try {
            boolean synced = platformInfraClient.syncIngestionExecutionLineage(task, execution);
            if (!synced) {
                auditService.auditAction(
                    "INGESTION_LINEAGE_SYNC",
                    AuditStage.FAIL,
                    task.getName(),
                    lineageFailureMeta(task, execution, "platform-sync-returned-false")
                );
                return;
            }
            if (isApiSourceTask(task)) {
                boolean emitted = platformInfraClient.emitIngestionOpenLineageEvent(task, execution);
                if (!emitted) {
                    log.debug("API OpenLineage event skipped or not emitted for task {} execution {}", task.getId(), execution.getId());
                }
            }
        } catch (Exception ex) {
            log.warn(
                "Failed to sync ingestion execution lineage for task {} execution {}: {}",
                task.getId(),
                execution.getId(),
                ex.getMessage()
            );
            auditService.auditAction(
                "INGESTION_LINEAGE_SYNC",
                AuditStage.FAIL,
                task.getName(),
                lineageFailureMeta(task, execution, ex.getMessage())
            );
        }
    }

    private Map<String, Object> lineageFailureMeta(IngestionTask task, IngestionExecution execution, String reason) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("taskId", task != null ? task.getId() : null);
        meta.put("taskName", task != null ? task.getName() : null);
        meta.put("executionId", execution != null ? execution.getId() : null);
        meta.put("batchId", execution != null ? execution.getBatchId() : null);
        meta.put("status", execution != null ? execution.getStatus() : null);
        if (StringUtils.hasText(reason)) meta.put("reason", reason);
        return meta;
    }

    private String generateBatchId(Long taskId) {
        String taskPart = taskId == null ? "task" : "task-" + taskId;
        return "batch-" + taskPart + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveBatchId(Long taskId, String requestedBatchId) {
        String normalized = toText(requestedBatchId);
        if (StringUtils.hasText(normalized)) {
            return truncateText(normalized, 128);
        }
        return generateBatchId(taskId);
    }

    private Map<String, Object> buildExecutionRuntimeContext(IngestionTask task, IngestionExecution execution) {
        Map<String, Object> context = new java.util.LinkedHashMap<>();
        if (execution != null && StringUtils.hasText(execution.getBatchId())) {
            context.put("batchId", execution.getBatchId());
        }
        if (execution != null && execution.getId() != null) {
            context.put("executionId", execution.getId().toString());
        }
        if (task != null && task.getId() != null) {
            context.put("taskId", task.getId().toString());
        }
        if (isBackfillExecution(execution)) {
            context.put("backfillColumn", execution.getBackfillColumn());
            context.put("backfillWindowStart", execution.getBackfillWindowStart().toString());
            context.put("backfillWindowEnd", execution.getBackfillWindowEnd().toString());
        }
        return context;
    }


    @Async("ingestionTaskExecutor")
    public CompletableFuture<Void> executeAsync(Long taskId) {
        try {
            execute(taskId);
            return CompletableFuture.completedFuture(null);
        } catch (Exception ex) {
            log.error("Async execution failed for task {}", taskId, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    @Async("ingestionTaskExecutor")
    public CompletableFuture<Void> retryExecutionAsync(Long taskId, Long executionId, String retryMode) {
        try {
            retryExecution(taskId, executionId, retryMode);
            return CompletableFuture.completedFuture(null);
        } catch (Exception ex) {
            log.error("Async retry failed for task {} execution {}: {}", taskId, executionId, ex.getMessage(), ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    @Async("ingestionTaskExecutor")
    public CompletableFuture<Void> backfillAsync(Long taskId, Instant windowStart, Instant windowEnd, String column) {
        try {
            backfill(taskId, windowStart, windowEnd, column);
            return CompletableFuture.completedFuture(null);
        } catch (Exception ex) {
            log.error("Async backfill failed for task {}: {}", taskId, ex.getMessage(), ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    public IngestionExecutionDTO retryExecution(Long taskId, Long executionId, String retryMode) {
        ValidatedRetryRequest validated = validateRetryRequest(taskId, executionId, retryMode);
        validateRetryEligibility(validated);
        IngestionTask task = loadExecutableTask(taskId);
        IngestionExecution execution = validated.execution();
        String mode = validated.mode();
        String previousStatus = toText(execution.getStatus());
        String previousError = toText(execution.getErrorMessage());
        String previousCategory = StringUtils.hasText(execution.getFailureCategory())
            ? execution.getFailureCategory()
            : (StringUtils.hasText(previousError)
                ? com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier.classify(previousError)
                : null);
        String previousAdvice = StringUtils.hasText(execution.getFailureAdvice())
            ? execution.getFailureAdvice()
            : (StringUtils.hasText(previousCategory)
                ? com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier.advice(previousCategory)
                : null);
        Map<String, Object> retryMeta = new java.util.LinkedHashMap<>();
        retryMeta.put("taskId", taskId);
        retryMeta.put("executionId", executionId);
        retryMeta.put("retryMode", mode);
        retryMeta.put("previousStatus", previousStatus == null ? "unknown" : previousStatus);
        if (StringUtils.hasText(previousCategory)) {
            retryMeta.put("previousFailureCategory", previousCategory);
            retryMeta.put("previousFailureAdvice", previousAdvice);
        }
        auditService.auditAction(
            "INGESTION_TASK_RETRY",
            AuditStage.SUCCESS,
            task.getName(),
            retryMeta
        );
        RetryLineage retryLineage = new RetryLineage(
            executionId,
            Math.max(0, execution.getRetryCount()) + 1,
            Math.max(execution.getMaxRetries(), Math.max(0, execution.getRetryCount()) + 1)
        );
        return execute(taskId, mode, null, null, false, null, retryLineage);
    }

    private record RetryLineage(Long parentExecutionId, int retryCount, int maxRetries) {}

    private IngestionTask loadExecutableTask(Long taskId) {
        IngestionTask task = taskRepository.findByIdForUpdate(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        requireSecretMigrationReady(taskId);
        requireActiveProductionTask(task);
        GovernancePolicy policy = resolveGovernancePolicy(task);
        if (!isWithinExecutionWindow(policy)) {
            throw new IllegalStateException("不在允许执行窗口内，当前策略窗口: " + policy.windowDisplay());
        }
        Optional<IngestionExecution> latestExecution = executionRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId);
        if (latestExecution.isPresent()
            && ("running".equalsIgnoreCase(latestExecution.get().getStatus())
                || "preparing".equalsIgnoreCase(latestExecution.get().getStatus()))
            && policy.maxConcurrentRuns() <= 1) {
            throw new IllegalStateException("任务仍在运行中，请稍后重试");
        }
        return task;
    }

    /**
     * Validates the immutable task contract for an Airflow run that has already
     * started. Registration must not apply launch-time window/concurrency gates:
     * the database uniqueness constraint arbitrates duplicate callbacks, and the
     * committed winner is returned after a conflict.
     */
    private IngestionTask loadScheduledRegistrationTask(Long taskId) {
        IngestionTask task = taskRepository.findByIdForUpdate(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        requireSecretMigrationReady(taskId);
        requireActiveProductionTask(task);
        return task;
    }

    private String normalizeInitialStatus(String status) {
        if (!StringUtils.hasText(status) || statusEquals(status, "draft") || statusEquals(status, "active")) {
            return "draft";
        }
        throw new IllegalArgumentException("Unsupported initial task status: " + status);
    }

    private boolean statusEquals(String status, String expected) {
        return StringUtils.hasText(status) && expected.equalsIgnoreCase(status.trim());
    }

    private void requireActiveProductionTask(IngestionTask task) {
        if (!statusEquals(task.getStatus(), "active")) {
            throw new IllegalStateException("Task is not in executable status: " + task.getStatus());
        }
        classificationSealGuard.requireProductionSeal(task);
    }

    private void verifyManagedFileTask(IngestionTask task) {
        if (task == null || !isFileSourceType(task.getSourceType())) {
            return;
        }
        JsonNode sourceConfig = task.getSourceConfig();
        String fileId = sourceConfig == null ? null : sourceConfig.path("_fileId").asText(null);
        if (!StringUtils.hasText(fileId)) {
            throw new IllegalStateException("MANAGED_FILE_ID_REQUIRED: 文件任务缺少 _fileId");
        }
        JsonNode seal = task.getClassificationSeal();
        String sealedFileId = seal == null ? null : seal.path("fileId").asText(null);
        String sealedFileSubjectKey = seal == null ? null : seal.path("fileSubjectKey").asText(null);
        if (
            !fileId.equals(sealedFileId)
                || !("ingestion-upload:" + fileId).equals(sealedFileSubjectKey)
        ) {
            throw new IllegalStateException(
                "FILE_UPLOAD_EVIDENCE_MISMATCH: 文件任务引用与密级封存不一致"
            );
        }
        String fileChecksum = seal == null ? null : seal.path("fileChecksum").asText(null);
        if (!StringUtils.hasText(fileChecksum)) {
            throw new IllegalStateException("FILE_CHECKSUM_REQUIRED: 文件封存缺少 fileChecksum");
        }
        FileUploadService.ManagedUpload verified = fileUploadService.verifyManagedUpload(
            fileId,
            fileChecksum
        );
        com.fasterxml.jackson.databind.node.ObjectNode canonicalConfig =
            sourceConfig != null && sourceConfig.isObject()
                ? ((com.fasterxml.jackson.databind.node.ObjectNode) sourceConfig).deepCopy()
                : OBJECT_MAPPER.createObjectNode();
        canonicalConfig.remove(List.of("hostPath", "filePath", "path", "containerPath"));
        canonicalConfig.put("_fileId", verified.fileId());
        canonicalConfig.put("_filePath", verified.hostPath());
        canonicalConfig.put("_containerPath", verified.containerPath());
        canonicalConfig.put("_fileHash", verified.fileHash());
        canonicalConfig.put("_encrypted", true);
        task.setSourceConfig(canonicalConfig);
    }

    private JsonNode copyJsonNode(JsonNode value) {
        return value == null ? null : value.deepCopy();
    }

    private IngestionTask classificationValidationCandidate(
        IngestionTask source,
        JsonNode classificationSeal,
        JsonNode fieldClassifications
    ) {
        IngestionTask candidate = new IngestionTask();
        candidate.setId(source.getId());
        candidate.setName(source.getName());
        candidate.setSourceType(source.getSourceType());
        candidate.setSourceDataSourceId(source.getSourceDataSourceId());
        candidate.setStatus(source.getStatus());
        candidate.setClassificationSeal(classificationSeal);
        candidate.setFieldClassifications(fieldClassifications);
        return candidate;
    }

    private ValidatedRetryRequest validateRetryRequest(Long taskId, Long executionId, String retryMode) {
        if (taskId == null || executionId == null) {
            throw new IllegalArgumentException("taskId and executionId are required");
        }
        IngestionTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        IngestionExecution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        if (execution.getTask() == null || !taskId.equals(execution.getTask().getId())) {
            throw new IllegalArgumentException("Execution does not belong to task: " + taskId);
        }
        String mode = StringUtils.hasText(retryMode) ? retryMode.trim().toUpperCase(java.util.Locale.ROOT) : "FAILED_ONLY";
        if (!"FAILED_ONLY".equals(mode) && !"FULL_RERUN".equals(mode)) {
            throw new IllegalArgumentException("Unsupported retry mode: " + retryMode);
        }
        return new ValidatedRetryRequest(task, execution, mode);
    }

    private void validateRetryEligibility(ValidatedRetryRequest validated) {
        if (!"FAILED_ONLY".equals(validated.mode())) {
            return;
        }
        String previousStatus = toText(validated.execution().getStatus());
        boolean canRetry = "failed".equalsIgnoreCase(previousStatus) || "error".equalsIgnoreCase(previousStatus);
        if (!canRetry) {
            throw new IllegalStateException("仅失败执行可使用 FAILED_ONLY 重试模式");
        }
    }

    private record ValidatedRetryRequest(IngestionTask task, IngestionExecution execution, String mode) {}

    private String normalizeTriggerMode(String triggerMode) {
        String mode = toText(triggerMode);
        if (!StringUtils.hasText(mode)) {
            return "MANUAL";
        }
        String upper = mode.toUpperCase(java.util.Locale.ROOT);
        if ("FAILED_ONLY".equals(upper) || "FULL_RERUN".equals(upper) || "MANUAL".equals(upper) || "BACKFILL_RANGE".equals(upper)) {
            return upper;
        }
        return "MANUAL";
    }


    private String resolveReplaceMode(IngestionTask task, String triggerMode) {
        if (task == null) {
            return null;
        }
        if ("BACKFILL_RANGE".equalsIgnoreCase(triggerMode)) {
            return "BACKFILL_RANGE";
        }
        return "full_refresh".equalsIgnoreCase(task.getSyncMode()) ? "FULL_REPLACE" : "INCREMENTAL_OR_APPEND";
    }

    private boolean isBackfillExecution(IngestionExecution execution) {
        return execution != null
            && "BACKFILL_RANGE".equalsIgnoreCase(execution.getTriggerMode())
            && execution.getBackfillWindowStart() != null
            && execution.getBackfillWindowEnd() != null;
    }

    private record BackfillWindow(String column, Instant windowStart, Instant windowEnd) {}

    private record ExactRevisionContext(Long revisionId, String configChecksum, String airflowDagId, String airflowRunId) {}

    private void validateExactRevisionContext(ExactRevisionContext context) {
        if (context == null
            || context.revisionId() == null
            || !StringUtils.hasText(context.configChecksum())
            || !StringUtils.hasText(context.airflowDagId())
            || !StringUtils.hasText(context.airflowRunId())) {
            throw new IllegalArgumentException("revisionId, configChecksum, airflowDagId and airflowRunId are required together");
        }
    }

    private IngestionTask materializeExactRevisionTask(IngestionTask canonicalTask, ExactRevisionContext context) {
        validateExactRevisionContext(context);
        if (accessContractService == null) {
            throw new IllegalStateException("Ingestion access contract service is required for scheduled execution");
        }
        IngestionTask runtimeTask = accessContractService.materializeExecutionTask(canonicalTask, context.revisionId());
        if (!context.airflowDagId().equals(runtimeTask.getAirflowDagId())) {
            throw new IllegalStateException(
                "Scheduled execution DAG does not match revision " + context.revisionId() + ": " + context.airflowDagId()
            );
        }
        return runtimeTask;
    }

    private IngestionExecutionDTO toExactExistingExecution(IngestionExecution execution, ExactRevisionContext context) {
        if (!context.revisionId().equals(execution.getTaskRevisionId())
            || !context.configChecksum().equals(execution.getEffectiveConfigChecksum())) {
            throw new IllegalStateException("Airflow run is already bound to a different ingestion revision");
        }
        return executionMapper.toDto(execution);
    }

    private String resolveDroppedTables(IngestionTask task) {
        if (task == null) {
            return null;
        }
        if (!"full_refresh".equalsIgnoreCase(task.getSyncMode())) {
            return null;
        }
        if (!StringUtils.hasText(task.getAddaxJobPath())) {
            return null;
        }
        List<String> tables = addaxJobService.listWriterTablesFromJob(task.getAddaxJobPath());
        if (tables == null || tables.isEmpty()) {
            return null;
        }
        return String.join(",", tables);
    }

    private IngestionTask prepareExecutionAddaxJob(
        IngestionTask task,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolvedSource,
        Map<String, Object> runtimeReaderOverrides,
        Map<String, Object> runtimeContext,
        IngestionExecution execution
    ) {
        if (task == null || isApiSourceTask(task)) {
            if (task != null) {
                task.setAddaxJobPath(null);
            }
            return task;
        }
        String originalName = task.getName();
        String executionKey = execution == null || execution.getId() == null
            ? UUID.randomUUID().toString().substring(0, 8)
            : execution.getId().toString();
        task.setName((StringUtils.hasText(originalName) ? originalName : "ingestion") + "-execution-" + executionKey);
        try {
            AddaxJobService.AddaxJobResult jobResult = resolvedSource == null
                ? addaxJobService.createJobFromTask(task, null, null, runtimeReaderOverrides, runtimeContext)
                : addaxJobService.createJobFromTask(
                    task,
                    resolvedSource.readerType(),
                    resolvedSource.readerConfig(),
                    runtimeReaderOverrides,
                    runtimeContext
                );
            if (resolvedSource != null && StringUtils.hasText(resolvedSource.readerType())) {
                task.setSourceType(resolvedSource.readerType());
            }
            task.setAddaxJobPath(jobResult.jobPath());
            return task;
        } finally {
            task.setName(originalName);
        }
    }

    private IngestionTask prepareExecutionDag(IngestionTask task, IngestionExecution execution) {
        List<AddaxJobService.PerTableJob> perTableJobs = addaxJobService.splitJobIntoPerTableFiles(task.getAddaxJobPath());
        String revisionPart = execution.getRevisionNumber() == null ? "unsealed" : execution.getRevisionNumber().toString();
        task.setAirflowDagId(
            "ingestion_revision_" + revisionPart + "_execution_" + execution.getId()
        );
        String originalSchedule = task.getSyncSchedule();
        task.setSyncSchedule(null);
        try {
            String dagId = airflowDagService.rebuildDagForTask(task, perTableJobs);
            if (!StringUtils.hasText(dagId)) {
                throw new IllegalStateException("Execution DAG generation failed");
            }
            task.setAirflowDagId(dagId);
            return task;
        } finally {
            task.setSyncSchedule(originalSchedule);
        }
    }

    private IngestionTask ensureAddaxJobExists(IngestionTask task) {
        return ensureAddaxJobExists(task, null, null);
    }

    private IngestionTask ensureAddaxJobExists(
        IngestionTask task,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolvedSource
    ) {
        return ensureAddaxJobExists(task, resolvedSource, null);
    }

    private IngestionTask ensureAddaxJobExists(
        IngestionTask task,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolvedSource,
        Map<String, Object> runtimeReaderOverrides
    ) {
        return ensureAddaxJobExists(task, resolvedSource, runtimeReaderOverrides, null);
    }

    private IngestionTask ensureAddaxJobExists(
        IngestionTask task,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolvedSource,
        Map<String, Object> runtimeReaderOverrides,
        Map<String, Object> runtimeContext
    ) {
        if (task == null) {
            return task;
        }
        // API tasks don't use Addax; their execution is encoded directly in a PythonOperator-based DAG.
        // Skip Addax job generation entirely so we don't write malformed JSON for httpreader.
        if (isApiSourceTask(task)) {
            if (StringUtils.hasText(task.getAddaxJobPath())) {
                task.setAddaxJobPath(null);
                task = taskRepository.save(task);
            }
            return task;
        }
        if (resolvedSource != null || task.getSourceDataSourceId() != null) {
            return rebuildAddaxJob(task, resolvedSource, runtimeReaderOverrides, runtimeContext);
        }
        // File source tasks always rebuild to ensure preSql CREATE TABLE and
        // explicit writer columns are up-to-date with current file metadata.
        if (isFileSourceType(task.getSourceType())) {
            return rebuildAddaxJob(task, null, runtimeReaderOverrides, runtimeContext);
        }
        String jobPath = task.getAddaxJobPath();
        if (StringUtils.hasText(jobPath)) {
            Path path = Paths.get(jobPath);
            if (Files.exists(path)
                && !addaxJobService.needsJobRebuild(task)
                && !addaxJobService.isJobConfigMalformed(path)) {
                return task;
            }
        }
        return rebuildAddaxJob(task, null, runtimeReaderOverrides, runtimeContext);
    }

    private IngestionTask rebuildAddaxJob(
        IngestionTask task,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolvedSource
    ) {
        return rebuildAddaxJob(task, resolvedSource, null);
    }

    private IngestionTask rebuildAddaxJob(
        IngestionTask task,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolvedSource,
        Map<String, Object> runtimeReaderOverrides
    ) {
        return rebuildAddaxJob(task, resolvedSource, runtimeReaderOverrides, null);
    }

    private IngestionTask rebuildAddaxJob(
        IngestionTask task,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolvedSource,
        Map<String, Object> runtimeReaderOverrides,
        Map<String, Object> runtimeContext
    ) {
        String operator = resolveOperator(task);
        try {
            com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource source =
                resolveSource(task, resolvedSource);
            AddaxJobService.AddaxJobResult jobResult = source == null
                ? addaxJobService.createJobFromTask(task, null, null, runtimeReaderOverrides, runtimeContext)
                : addaxJobService.createJobFromTask(task, source.readerType(), source.readerConfig(), runtimeReaderOverrides, runtimeContext);
            if (source != null && StringUtils.hasText(source.readerType())) {
                task.setSourceType(source.readerType());
            }
            task.setAddaxJobPath(jobResult.jobPath());
            IngestionTask saved = taskRepository.save(task);
            auditService.auditAction(
                "INGESTION_TASK_JOB_REBUILD",
                AuditStage.SUCCESS,
                task.getName(),
                Map.of(
                    "summary",
                    "重建 Addax 作业",
                    "taskId",
                    task.getId(),
                    "jobPath",
                    jobResult.jobPath(),
                    "operator",
                    operator
                )
            );
            return saved;
        } catch (Exception ex) {
            auditService.auditAction(
                "INGESTION_TASK_JOB_REBUILD",
                AuditStage.FAIL,
                task.getName(),
                Map.of(
                    "summary",
                    "重建 Addax 作业失败",
                    "taskId",
                    task.getId(),
                    "operator",
                    operator,
                    "error",
                    ex.getMessage()
                )
            );
            throw new IllegalStateException("Addax 作业文件缺失，重建失败: " + ex.getMessage(), ex);
        }
    }

    private String resolveOperator(IngestionTask task) {
        if (task == null) {
            return "system";
        }
        if (StringUtils.hasText(task.getLastModifiedBy())) {
            return task.getLastModifiedBy();
        }
        if (StringUtils.hasText(task.getCreatedBy())) {
            return task.getCreatedBy();
        }
        return "system";
    }

    private void backfillExecutionsFromAirflow(Long taskId, int limit) {
        if (taskId == null || limit <= 0) {
            return;
        }
        IngestionTask canonicalTask = taskRepository.findById(taskId).orElse(null);
        if (canonicalTask == null || !isAirflowEnabled(canonicalTask)) {
            return;
        }
        int inserted = 0;
        boolean foundRevisionDag = false;
        if (accessContractService != null) {
            for (IngestionTaskRevision revision : accessContractService.findTaskRevisionEntities(taskId)) {
                if (IngestionAccessContractService.REVISION_LEGACY_UNSEALED.equals(revision.getState())
                    || revision.getRuntimeSnapshot() == null
                    || revision.getRuntimeSnapshotIv() == null) {
                    continue;
                }
                IngestionTask revisionTask = accessContractService.materializeExecutionTask(canonicalTask, revision.getId());
                if (!StringUtils.hasText(revisionTask.getAirflowDagId())) {
                    continue;
                }
                foundRevisionDag = true;
                inserted += backfillExecutionsForDag(canonicalTask, revisionTask, revision, limit);
            }
        }
        // Legacy tasks have no seal that can prove an exact historical plan.
        // Preserve observability, but intentionally leave revision fields null.
        if (!foundRevisionDag && StringUtils.hasText(canonicalTask.getAirflowDagId())) {
            inserted += backfillExecutionsForDag(canonicalTask, canonicalTask, null, limit);
        }
        if (inserted > 0) {
            log.info("Backfilled {} exact execution records for task {} from Airflow", inserted, taskId);
        }
    }

    private int backfillExecutionsForDag(
        IngestionTask canonicalTask,
        IngestionTask runtimeTask,
        IngestionTaskRevision revision,
        int limit
    ) {
        String dagId = runtimeTask.getAirflowDagId();
        Map<String, Object> payload = airflowClient.listDagRuns(dagId, Math.max(limit, 20)).orElse(null);
        if (payload == null || !(payload.get("dag_runs") instanceof java.util.List<?> dagRuns)) {
            return 0;
        }
        int inserted = 0;
        for (Object runObj : dagRuns) {
            if (!(runObj instanceof Map<?, ?> run)) {
                continue;
            }
            Object runIdObj = firstNonBlank(run.get("dag_run_id"), run.get("dagRunId"), run.get("run_id"), run.get("runId"));
            if (runIdObj == null) {
                continue;
            }
            String runId = runIdObj.toString().trim();
            if (!StringUtils.hasText(runId)) {
                continue;
            }
            if (executionRepository.findFirstByTaskIdAndAirflowDagIdAndExecutionId(canonicalTask.getId(), dagId, runId).isPresent()) {
                continue;
            }
            IngestionExecution execution = new IngestionExecution();
            execution.setTask(canonicalTask);
            execution.setExecutionId(runId);
            execution.setAirflowDagId(dagId);
            execution.setTriggerMode("SCHEDULED");
            execution.setStatus(mapAirflowState(toText(run.get("state"))));
            IngestionExecutionLineageSnapshot.apply(execution, runtimeTask);
            if (revision != null) {
                accessContractService.bindExactRevision(
                    execution,
                    canonicalTask,
                    revision.getId(),
                    revision.getEffectiveConfigChecksum()
                );
            }
            execution.setStartTime(parseAirflowInstant(firstNonBlank(run.get("start_date"), run.get("execution_date"), run.get("logical_date"))));
            execution.setEndTime(parseAirflowInstant(run.get("end_date")));
            if (execution.getStartTime() == null) {
                execution.setStartTime(Instant.now());
            }
            if ("failed".equalsIgnoreCase(execution.getStatus())) {
                String note = toText(firstNonBlank(run.get("note"), run.get("message")));
                if (StringUtils.hasText(note)) {
                    execution.setErrorMessage(note);
                    String failureCategory = com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier.classify(note);
                    execution.setFailureCategory(failureCategory);
                    execution.setFailureAdvice(com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier.advice(failureCategory));
                }
            }
            executionRepository.save(execution);
            inserted++;
        }
        return inserted;
    }

    private String mapAirflowState(String state) {
        if (!StringUtils.hasText(state)) {
            return "running";
        }
        String normalized = state.trim().toLowerCase(java.util.Locale.ROOT);
        if ("success".equals(normalized)) {
            return "success";
        }
        if ("failed".equals(normalized) || "error".equals(normalized)) {
            return "failed";
        }
        return "running";
    }

    private String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private String extractFailureMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        java.util.LinkedHashSet<String> parts = new java.util.LinkedHashSet<>();
        Throwable cursor = throwable;
        int hops = 0;
        while (cursor != null && hops < 8) {
            String message = sanitizeErrorText(cursor.getMessage());
            if (StringUtils.hasText(message)) {
                parts.add(message);
            }
            cursor = cursor.getCause();
            hops++;
        }
        if (parts.isEmpty()) {
            return throwable.getClass().getSimpleName();
        }
        return truncateText(String.join(" | ", parts), 3000);
    }

    private String sanitizeErrorText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replaceAll("\\s+", " ")
            .trim();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String truncateText(String value, int maxLen) {
        if (!StringUtils.hasText(value) || maxLen <= 0 || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen - 3) + "...";
    }

    private Instant parseAirflowInstant(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractDagRunId(Map<String, Object> airflowResult) {
        if (airflowResult == null) {
            return null;
        }
        Object payload = airflowResult.get("payload");
        if (!(payload instanceof Map<?, ?> payloadMap)) {
            return null;
        }
        Object dagRunId =
            firstNonBlank(payloadMap.get("dag_run_id"), payloadMap.get("dagRunId"), payloadMap.get("run_id"), payloadMap.get("runId"));
        return dagRunId == null ? null : dagRunId.toString().trim();
    }

    private Object firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (StringUtils.hasText(text)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 获取任务的执行历史
     */
    @Transactional
    public Page<IngestionExecutionDTO> getExecutions(Long taskId, Pageable pageable) {
        return getExecutions(taskId, pageable, null, null);
    }

    @Transactional
    public Page<IngestionExecutionDTO> getExecutions(Long taskId, Pageable pageable, String status, String failureCategory) {
        log.debug("Request to get executions for task: {} status={} failureCategory={}", taskId, status, failureCategory);
        Page<IngestionExecutionDTO> page = queryExecutions(taskId, pageable, status, failureCategory).map(executionMapper::toDto);
        if (page.hasContent()) {
            return page;
        }
        backfillExecutionsFromAirflow(taskId, pageable == null ? 20 : pageable.getPageSize());
        return queryExecutions(taskId, pageable, status, failureCategory).map(executionMapper::toDto);
    }

    private Page<IngestionExecution> queryExecutions(Long taskId, Pageable pageable, String status, String failureCategory) {
        String normalizedStatus = toText(status);
        List<String> normalizedFailureCategories = parseFailureCategories(failureCategory);
        if (StringUtils.hasText(normalizedStatus) && normalizedFailureCategories.size() > 1) {
            return executionRepository.findByTaskIdAndStatusIgnoreCaseAndFailureCategoriesIgnoreCase(
                taskId,
                normalizedStatus,
                normalizedFailureCategories,
                pageable
            );
        }
        if (StringUtils.hasText(normalizedStatus) && normalizedFailureCategories.size() == 1) {
            return executionRepository.findByTaskIdAndStatusIgnoreCaseAndFailureCategoryIgnoreCase(
                taskId,
                normalizedStatus,
                normalizedFailureCategories.get(0),
                pageable
            );
        }
        if (StringUtils.hasText(normalizedStatus)) {
            return executionRepository.findByTaskIdAndStatusIgnoreCase(taskId, normalizedStatus, pageable);
        }
        if (normalizedFailureCategories.size() > 1) {
            return executionRepository.findByTaskIdAndFailureCategoriesIgnoreCase(taskId, normalizedFailureCategories, pageable);
        }
        if (normalizedFailureCategories.size() == 1) {
            return executionRepository.findByTaskIdAndFailureCategoryIgnoreCase(taskId, normalizedFailureCategories.get(0), pageable);
        }
        return executionRepository.findByTaskId(taskId, pageable);
    }

    private List<String> parseFailureCategories(String value) {
        String normalized = toText(value);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        List<String> categories = new ArrayList<>();
        for (String part : normalized.split("[,;|\\s]+")) {
            String item = toText(part);
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String upper = item.toUpperCase(java.util.Locale.ROOT);
            if (!categories.contains(upper)) {
                categories.add(upper);
            }
        }
        return categories;
    }

    @Transactional(readOnly = true)
    public IngestionExecutionObservabilityDTO getExecutionObservability(
        Long taskId,
        String sourceType,
        UUID sourceDataSourceId,
        Instant from,
        Instant to,
        Integer days,
        Integer timeoutMinutes
    ) {
        int safeDays = days == null ? 7 : Math.max(1, Math.min(days, 90));
        int safeTimeoutMinutes = timeoutMinutes == null ? 10 : Math.max(1, Math.min(timeoutMinutes, 24 * 60));
        Instant windowEnd = to == null ? Instant.now() : to;
        Instant windowStart = from == null ? windowEnd.minus(Duration.ofDays(safeDays)) : from;
        if (windowStart.isAfter(windowEnd)) {
            throw new IllegalArgumentException("windowStart cannot be after windowEnd");
        }

        String normalizedSourceType = toText(sourceType);
        List<IngestionExecution> executions = taskId == null
            ? executionRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(windowStart, windowEnd)
            : executionRepository.findByTaskIdAndCreatedAtBetweenOrderByCreatedAtAsc(taskId, windowStart, windowEnd);

        if (StringUtils.hasText(normalizedSourceType) || sourceDataSourceId != null) {
            List<IngestionExecution> filtered = new ArrayList<>();
            for (IngestionExecution execution : executions) {
                IngestionTask task = execution == null ? null : execution.getTask();
                if (task == null) {
                    continue;
                }
                if (StringUtils.hasText(normalizedSourceType)) {
                    String current = toText(task.getSourceType());
                    if (!normalizedSourceType.equalsIgnoreCase(current)) {
                        continue;
                    }
                }
                if (sourceDataSourceId != null && !sourceDataSourceId.equals(task.getSourceDataSourceId())) {
                    continue;
                }
                filtered.add(execution);
            }
            executions = filtered;
        }

        IngestionExecutionObservabilityDTO dto = new IngestionExecutionObservabilityDTO();
        dto.setTaskId(taskId);
        dto.setSourceType(normalizedSourceType);
        dto.setSourceDataSourceId(sourceDataSourceId);
        dto.setWindowStart(windowStart);
        dto.setWindowEnd(windowEnd);
        dto.setWindowDays(safeDays);
        dto.setTimeoutMinutes(safeTimeoutMinutes);

        Map<String, TrendAccumulator> trendMap = new LinkedHashMap<>();
        Map<String, Long> failureMap = new HashMap<>();
        Map<Long, Instant> pendingFailureMap = new HashMap<>();
        long total = 0L;
        long success = 0L;
        long failed = 0L;
        long running = 0L;
        long terminal = 0L;
        long timeout = 0L;
        long durationCount = 0L;
        long durationSecondsSum = 0L;
        long mttrCount = 0L;
        long mttrSecondsSum = 0L;
        long timeoutThresholdSeconds = safeTimeoutMinutes * 60L;

        for (IngestionExecution execution : executions) {
            if (execution == null) {
                continue;
            }
            total += 1;
            String status = normalizeStatus(execution.getStatus());
            boolean isSuccess = "success".equals(status);
            boolean isFailed = "failed".equals(status) || "error".equals(status);
            boolean isTerminal = isSuccess || isFailed;

            if (isSuccess) {
                success += 1;
            } else if (isFailed) {
                failed += 1;
            } else {
                running += 1;
            }
            if (isTerminal) {
                terminal += 1;
            }

            Long durationSeconds = durationSeconds(execution.getStartTime(), execution.getEndTime());
            if (isTerminal && durationSeconds != null) {
                durationCount += 1;
                durationSecondsSum += durationSeconds;
                if (durationSeconds > timeoutThresholdSeconds) {
                    timeout += 1;
                }
            }

            Instant point = executionPoint(execution);
            TrendAccumulator trend = trendMap.computeIfAbsent(toDay(point), key -> new TrendAccumulator());
            trend.total += 1;
            if (isSuccess) {
                trend.success += 1;
            } else if (isFailed) {
                trend.failed += 1;
            }
            if (isTerminal && durationSeconds != null && durationSeconds > timeoutThresholdSeconds) {
                trend.timeout += 1;
            }

            Long key = execution.getTask() != null ? execution.getTask().getId() : null;
            if (key == null) {
                key = -1L;
            }
            if (isFailed) {
                pendingFailureMap.putIfAbsent(key, point);
                String category = toText(execution.getFailureCategory());
                if (!StringUtils.hasText(category)) {
                    category = ExecutionFailureClassifier.classify(execution.getErrorMessage());
                }
                String normalizedCategory = StringUtils.hasText(category) ? category.toUpperCase() : ExecutionFailureClassifier.CATEGORY_RUNTIME;
                failureMap.put(normalizedCategory, failureMap.getOrDefault(normalizedCategory, 0L) + 1L);
            } else if (isSuccess) {
                Instant failureAt = pendingFailureMap.get(key);
                if (failureAt != null) {
                    long recover = Duration.between(failureAt, point).getSeconds();
                    if (recover >= 0) {
                        mttrCount += 1;
                        mttrSecondsSum += recover;
                    }
                    pendingFailureMap.remove(key);
                }
            }
        }

        dto.setTotal(total);
        dto.setSuccess(success);
        dto.setFailed(failed);
        dto.setRunning(running);
        dto.setTerminal(terminal);
        dto.setTimeout(timeout);
        dto.setSuccessRate(rate(success, terminal));
        dto.setTimeoutRate(rate(timeout, terminal));
        dto.setAvgDurationSeconds(durationCount == 0 ? null : round2((double) durationSecondsSum / durationCount));
        dto.setMttrSeconds(mttrCount == 0 ? null : round2((double) mttrSecondsSum / mttrCount));

        List<IngestionExecutionObservabilityDTO.FailureTopItem> topItems = failureMap.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(5)
            .map(entry -> new IngestionExecutionObservabilityDTO.FailureTopItem(entry.getKey(), entry.getValue()))
            .toList();
        dto.setFailureTop(topItems);

        List<IngestionExecutionObservabilityDTO.TrendItem> trendItems = trendMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new IngestionExecutionObservabilityDTO.TrendItem(
                entry.getKey(),
                entry.getValue().total,
                entry.getValue().success,
                entry.getValue().failed,
                entry.getValue().timeout
            ))
            .toList();
        dto.setTrend(trendItems);

        return dto;
    }

    @Transactional(readOnly = true)
    public IngestionGovernanceOverviewDTO getGovernanceOverview(Integer hours) {
        int safeHours = hours == null ? 24 : Math.max(1, Math.min(hours, 7 * 24));
        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofHours(safeHours));
        List<IngestionExecution> inProgress = executionRepository.findByStatusesIgnoreCase(List.of("running", "preparing"));
        List<IngestionExecution> recent = executionRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(from, now);

        IngestionGovernanceOverviewDTO dto = new IngestionGovernanceOverviewDTO();
        dto.setGeneratedAt(now);
        long running = inProgress.stream().filter(e -> "running".equalsIgnoreCase(toText(e.getStatus()))).count();
        long preparing = inProgress.stream().filter(e -> "preparing".equalsIgnoreCase(toText(e.getStatus()))).count();
        dto.setRunning(running);
        dto.setPreparing(preparing);
        dto.setQueueLength(preparing);
        long queueWaitCount = 0L;
        long queueWaitSumSeconds = 0L;
        long queueWaitMaxSeconds = 0L;
        for (IngestionExecution execution : inProgress) {
            if (execution == null || !"preparing".equalsIgnoreCase(toText(execution.getStatus()))) {
                continue;
            }
            Instant queuedAt = execution.getStartTime() != null ? execution.getStartTime() : execution.getCreatedAt();
            if (queuedAt == null) {
                continue;
            }
            long waitSeconds = Math.max(0L, Duration.between(queuedAt, now).getSeconds());
            queueWaitCount += 1;
            queueWaitSumSeconds += waitSeconds;
            queueWaitMaxSeconds = Math.max(queueWaitMaxSeconds, waitSeconds);
        }
        dto.setAvgQueueWaitSeconds(queueWaitCount == 0 ? null : round2((double) queueWaitSumSeconds / queueWaitCount));
        dto.setMaxQueueWaitSeconds(queueWaitCount == 0 ? null : round2((double) queueWaitMaxSeconds));

        long blocked = 0L;
        long durationCount = 0L;
        long durationTotal = 0L;
        Map<String, SourceLoadAccumulator> sourceLoads = new LinkedHashMap<>();
        Map<String, ProjectLoadAccumulator> projectLoads = new LinkedHashMap<>();
        for (IngestionExecution execution : recent) {
            if (execution == null) {
                continue;
            }
            String error = toText(execution.getErrorMessage());
            if (StringUtils.hasText(error) && (error.contains("并发已达上限") || error.contains("执行窗口"))) {
                blocked += 1;
            }
            Long seconds = durationSeconds(execution.getStartTime(), execution.getEndTime());
            if (seconds != null && ("success".equalsIgnoreCase(toText(execution.getStatus())) || "failed".equalsIgnoreCase(toText(execution.getStatus())))) {
                durationCount += 1;
                durationTotal += seconds;
            }
        }
        dto.setBlockedByPolicy(blocked);
        dto.setAvgExecutionSeconds(durationCount == 0 ? null : round2((double) durationTotal / durationCount));

        for (IngestionExecution execution : inProgress) {
            if (execution == null || execution.getTask() == null) {
                continue;
            }
            IngestionTask task = execution.getTask();
            String key = (task.getSourceDataSourceId() == null ? "none" : task.getSourceDataSourceId().toString())
                + "|"
                + toText(task.getSourceType());
            SourceLoadAccumulator acc = sourceLoads.computeIfAbsent(
                key,
                k -> new SourceLoadAccumulator(task.getSourceDataSourceId(), toText(task.getSourceType()))
            );
            String projectKey = resolveProjectKey(task);
            ProjectLoadAccumulator projectAcc = projectLoads.computeIfAbsent(
                StringUtils.hasText(projectKey) ? projectKey : "default",
                k -> new ProjectLoadAccumulator(StringUtils.hasText(projectKey) ? projectKey : "default")
            );
            if ("running".equalsIgnoreCase(toText(execution.getStatus()))) {
                acc.running += 1;
                projectAcc.running += 1;
            } else if ("preparing".equalsIgnoreCase(toText(execution.getStatus()))) {
                acc.preparing += 1;
                projectAcc.preparing += 1;
            }
        }
        List<IngestionGovernanceOverviewDTO.SourceLoadItem> loads = sourceLoads.values().stream()
            .sorted((a, b) -> Long.compare((b.running + b.preparing), (a.running + a.preparing)))
            .map(acc -> new IngestionGovernanceOverviewDTO.SourceLoadItem(acc.sourceDataSourceId, acc.sourceType, acc.running, acc.preparing))
            .toList();
        dto.setSourceLoads(loads);
        List<IngestionGovernanceOverviewDTO.ProjectLoadItem> projectItems = projectLoads.values().stream()
            .sorted((a, b) -> Long.compare((b.running + b.preparing), (a.running + a.preparing)))
            .map(acc -> new IngestionGovernanceOverviewDTO.ProjectLoadItem(acc.projectKey, acc.running, acc.preparing))
            .toList();
        dto.setProjectLoads(projectItems);
        return dto;
    }

    private String normalizeStatus(String value) {
        String normalized = toText(value);
        return StringUtils.hasText(normalized) ? normalized.toLowerCase() : "";
    }

    private Long durationSeconds(Instant start, Instant end) {
        if (start == null || end == null) {
            return null;
        }
        long seconds = Duration.between(start, end).getSeconds();
        return seconds < 0 ? null : seconds;
    }

    private Instant executionPoint(IngestionExecution execution) {
        if (execution == null) {
            return Instant.now();
        }
        if (execution.getEndTime() != null) {
            return execution.getEndTime();
        }
        if (execution.getStartTime() != null) {
            return execution.getStartTime();
        }
        if (execution.getCreatedAt() != null) {
            return execution.getCreatedAt();
        }
        return Instant.now();
    }

    private String toDay(Instant point) {
        Instant safePoint = point == null ? Instant.now() : point;
        LocalDate day = safePoint.atZone(OBSERVABILITY_ZONE).toLocalDate();
        return OBSERVABILITY_DAY_FORMATTER.format(day);
    }

    private Double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return round2((numerator * 100.0D) / denominator);
    }

    private Double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static final class TrendAccumulator {
        private long total;
        private long success;
        private long failed;
        private long timeout;
    }

    private GovernancePolicy resolveGovernancePolicy(IngestionTask task) {
        int maxConcurrentRuns = 1;
        int sourceConcurrencyLimit = 0;
        int projectConcurrencyLimit = 0;
        String projectKey = resolveProjectKey(task);
        String priority = "MEDIUM";
        String rejectPolicy = "REJECT";
        LocalTime windowStart = null;
        LocalTime windowEnd = null;
        ZoneId zone = GOVERNANCE_DEFAULT_ZONE;

        JsonNode syncConfig = task == null ? null : task.getSyncConfig();
        JsonNode governance = syncConfig == null ? null : syncConfig.path("governance");
        if (governance != null && !governance.isMissingNode() && !governance.isNull()) {
            maxConcurrentRuns = positiveOrDefault(governance.path("maxConcurrentRuns"), 1);
            sourceConcurrencyLimit = nonNegativeOrDefault(governance.path("sourceConcurrencyLimit"), 0);
            projectConcurrencyLimit = nonNegativeOrDefault(governance.path("projectConcurrencyLimit"), 0);
            String rawProjectKey = toText(governance.path("projectKey").asText(null));
            if (StringUtils.hasText(rawProjectKey)) {
                projectKey = rawProjectKey;
            }
            String rawPriority = toText(governance.path("priority").asText(null));
            if (StringUtils.hasText(rawPriority)) {
                priority = rawPriority.toUpperCase();
            }
            String rawReject = toText(governance.path("rejectPolicy").asText(null));
            if (StringUtils.hasText(rawReject)) {
                rejectPolicy = rawReject.toUpperCase();
            }
            String rawWindowStart = toText(governance.path("windowStart").asText(null));
            String rawWindowEnd = toText(governance.path("windowEnd").asText(null));
            if (StringUtils.hasText(rawWindowStart) && StringUtils.hasText(rawWindowEnd)) {
                windowStart = parseLocalTime(rawWindowStart);
                windowEnd = parseLocalTime(rawWindowEnd);
            }
            String rawZone = toText(governance.path("windowTimezone").asText(null));
            if (StringUtils.hasText(rawZone)) {
                try {
                    zone = ZoneId.of(rawZone);
                } catch (Exception ex) {
                    zone = GOVERNANCE_DEFAULT_ZONE;
                }
            }
        }
        return new GovernancePolicy(
            maxConcurrentRuns,
            sourceConcurrencyLimit,
            projectConcurrencyLimit,
            projectKey,
            priority,
            rejectPolicy,
            windowStart,
            windowEnd,
            zone
        );
    }

    private String resolveProjectKey(IngestionTask task) {
        if (task == null) {
            return "default";
        }
        JsonNode syncConfig = task.getSyncConfig();
        JsonNode governance = syncConfig == null ? null : syncConfig.path("governance");
        if (governance != null && !governance.isMissingNode() && !governance.isNull()) {
            String configured = toText(governance.path("projectKey").asText(null));
            if (StringUtils.hasText(configured)) {
                return configured;
            }
        }
        return "default";
    }

    private String evaluateGovernanceBlock(IngestionTask task, GovernancePolicy policy, Long excludeExecutionId) {
        // Use database count queries for atomic concurrency checks to prevent race conditions.
        // The excludeExecutionId offset accounts for the current execution's own "preparing" record.
        long excludeOffset = excludeExecutionId != null ? 1 : 0;
        if (policy.maxConcurrentRuns() > 0) {
            long taskInProgress = executionRepository.countByTaskIdAndStatusesIgnoreCase(task.getId(), IN_PROGRESS_STATUSES) - excludeOffset;
            if (taskInProgress >= policy.maxConcurrentRuns()) {
                return "任务并发已达上限(" + policy.maxConcurrentRuns() + ")，当前运行中/排队中执行: " + taskInProgress;
            }
        }
        if (task.getSourceDataSourceId() != null && policy.sourceConcurrencyLimit() > 0) {
            long sourceInProgress = executionRepository.countBySourceDataSourceIdAndStatusesIgnoreCase(
                task.getSourceDataSourceId(), IN_PROGRESS_STATUSES) - excludeOffset;
            if (sourceInProgress >= policy.sourceConcurrencyLimit()) {
                return "来源并发已达上限(" + policy.sourceConcurrencyLimit() + ")，source="
                    + task.getSourceDataSourceId()
                    + " 当前运行中/排队中执行: "
                    + sourceInProgress;
            }
        }
        if (StringUtils.hasText(policy.projectKey()) && policy.projectConcurrencyLimit() > 0) {
            long projectInProgress = countInProgressByProject(policy.projectKey(), excludeExecutionId, null);
            if (projectInProgress >= policy.projectConcurrencyLimit()) {
                return "项目并发已达上限(" + policy.projectConcurrencyLimit() + ")，project="
                    + policy.projectKey()
                    + " 当前运行中/排队中执行: "
                    + projectInProgress;
            }
        }
        return null;
    }

    private boolean waitForGovernanceSlot(
        IngestionTask task,
        GovernancePolicy policy,
        Long currentExecutionId,
        Duration maxWait,
        Duration pollInterval
    ) {
        if (maxWait == null || maxWait.isNegative() || maxWait.isZero()) {
            return !StringUtils.hasText(evaluateGovernanceBlock(task, policy, currentExecutionId))
                && isQueueTurn(task, policy, currentExecutionId);
        }
        Duration interval = (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero())
            ? Duration.ofSeconds(1)
            : pollInterval;
        Instant deadline = Instant.now().plus(maxWait);
        String lastReason = evaluateGovernanceBlock(task, policy, currentExecutionId);
        boolean queueTurn = isQueueTurn(task, policy, currentExecutionId);
        while ((StringUtils.hasText(lastReason) || !queueTurn) && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
            lastReason = evaluateGovernanceBlock(task, policy, currentExecutionId);
            queueTurn = isQueueTurn(task, policy, currentExecutionId);
        }
        if (StringUtils.hasText(lastReason)) {
            log.warn("Governance queue wait timeout for task {}: {}", task.getId(), lastReason);
            return false;
        }
        if (!queueTurn) {
            log.warn("Governance queue wait timeout for task {}: waiting for higher-priority queue turn", task.getId());
            return false;
        }
        // Final atomic check: re-verify concurrency limits using database count
        // to prevent race conditions where multiple threads pass the polling loop simultaneously.
        String finalCheck = evaluateGovernanceBlock(task, policy, currentExecutionId);
        if (StringUtils.hasText(finalCheck)) {
            log.warn("Governance final check failed for task {} after queue wait: {}", task.getId(), finalCheck);
            return false;
        }
        return true;
    }

    private boolean isWithinExecutionWindow(GovernancePolicy policy) {
        if (policy == null || policy.windowStart() == null || policy.windowEnd() == null) {
            return true;
        }
        LocalTime now = LocalTime.now(policy.zone());
        LocalTime start = policy.windowStart();
        LocalTime end = policy.windowEnd();
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private long countInProgressByTask(Long taskId) {
        return countInProgressByTask(taskId, null, null);
    }

    private long countInProgressByTask(Long taskId, Long excludeExecutionId, List<IngestionExecution> inProgressExecutions) {
        if (taskId == null) {
            return 0L;
        }
        List<IngestionExecution> rows = inProgressExecutions == null ? listInProgressExecutions() : inProgressExecutions;
        return rows.stream()
            .filter(execution -> execution != null && execution.getTask() != null)
            .filter(execution -> taskId.equals(execution.getTask().getId()))
            .filter(execution -> excludeExecutionId == null || !excludeExecutionId.equals(execution.getId()))
            .count();
    }

    private long countInProgressBySource(UUID sourceDataSourceId) {
        return countInProgressBySource(sourceDataSourceId, null, null);
    }

    private long countInProgressBySource(
        UUID sourceDataSourceId,
        Long excludeExecutionId,
        List<IngestionExecution> inProgressExecutions
    ) {
        if (sourceDataSourceId == null) {
            return 0L;
        }
        List<IngestionExecution> rows = inProgressExecutions == null ? listInProgressExecutions() : inProgressExecutions;
        return rows.stream()
            .filter(execution -> execution != null && execution.getTask() != null)
            .filter(execution -> sourceDataSourceId.equals(execution.getTask().getSourceDataSourceId()))
            .filter(execution -> excludeExecutionId == null || !excludeExecutionId.equals(execution.getId()))
            .count();
    }

    private long countInProgressByProject(String projectKey) {
        return countInProgressByProject(projectKey, null, null);
    }

    private long countInProgressByProject(
        String projectKey,
        Long excludeExecutionId,
        List<IngestionExecution> inProgressExecutions
    ) {
        if (!StringUtils.hasText(projectKey)) {
            return 0L;
        }
        List<IngestionExecution> rows = inProgressExecutions == null ? listInProgressExecutions() : inProgressExecutions;
        return rows.stream()
            .filter(execution -> execution != null && execution.getTask() != null)
            .filter(execution -> projectKey.equalsIgnoreCase(resolveProjectKey(execution.getTask())))
            .filter(execution -> excludeExecutionId == null || !excludeExecutionId.equals(execution.getId()))
            .count();
    }

    private List<IngestionExecution> listInProgressExecutions() {
        return executionRepository.findByStatusesIgnoreCase(IN_PROGRESS_STATUSES);
    }

    private boolean isQueueTurn(IngestionTask task, GovernancePolicy policy, Long currentExecutionId) {
        if (task == null || currentExecutionId == null) {
            return true;
        }
        List<IngestionExecution> inProgressExecutions = listInProgressExecutions();
        IngestionExecution current = inProgressExecutions.stream()
            .filter(execution -> execution != null && currentExecutionId.equals(execution.getId()))
            .findFirst()
            .orElse(null);
        if (current == null) {
            return true;
        }
        int selfPriority = priorityWeight(policy == null ? null : policy.priority());
        Instant selfCreatedAt = current.getCreatedAt();
        Long selfId = current.getId();
        for (IngestionExecution candidate : inProgressExecutions) {
            if (candidate == null || candidate.getTask() == null || candidate.getId() == null) {
                continue;
            }
            if (candidate.getId().equals(currentExecutionId)) {
                continue;
            }
            if (!"preparing".equalsIgnoreCase(toText(candidate.getStatus()))) {
                continue;
            }
            GovernancePolicy candidatePolicy = resolveGovernancePolicy(candidate.getTask());
            if (!isGovernanceConflict(task, policy, candidate.getTask(), candidatePolicy)) {
                continue;
            }
            int candidatePriority = priorityWeight(candidatePolicy.priority());
            if (candidatePriority > selfPriority) {
                return false;
            }
            if (candidatePriority == selfPriority) {
                Instant candidateCreatedAt = candidate.getCreatedAt();
                if (candidateCreatedAt != null && selfCreatedAt != null && candidateCreatedAt.isBefore(selfCreatedAt)) {
                    return false;
                }
                if (candidateCreatedAt != null
                    && selfCreatedAt != null
                    && candidateCreatedAt.equals(selfCreatedAt)
                    && candidate.getId() < selfId) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isGovernanceConflict(
        IngestionTask currentTask,
        GovernancePolicy currentPolicy,
        IngestionTask queuedTask,
        GovernancePolicy queuedPolicy
    ) {
        if (currentTask == null || queuedTask == null || currentPolicy == null || queuedPolicy == null) {
            return false;
        }
        if (currentPolicy.maxConcurrentRuns() > 0
            && currentTask.getId() != null
            && currentTask.getId().equals(queuedTask.getId())) {
            return true;
        }
        if (currentPolicy.sourceConcurrencyLimit() > 0
            && currentTask.getSourceDataSourceId() != null
            && currentTask.getSourceDataSourceId().equals(queuedTask.getSourceDataSourceId())) {
            return true;
        }
        if (currentPolicy.projectConcurrencyLimit() > 0
            && StringUtils.hasText(currentPolicy.projectKey())
            && currentPolicy.projectKey().equalsIgnoreCase(queuedPolicy.projectKey())) {
            return true;
        }
        return false;
    }

    private int priorityWeight(String priority) {
        String normalized = toText(priority);
        if (!StringUtils.hasText(normalized)) {
            return 2;
        }
        return switch (normalized.toUpperCase(java.util.Locale.ROOT)) {
            case "P0", "HIGH", "CRITICAL", "URGENT" -> 3;
            case "P2", "LOW" -> 1;
            default -> 2;
        };
    }

    private int positiveOrDefault(JsonNode node, int defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        int value = node.asInt(defaultValue);
        return value > 0 ? value : defaultValue;
    }

    private int nonNegativeOrDefault(JsonNode node, int defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        int value = node.asInt(defaultValue);
        return value >= 0 ? value : defaultValue;
    }

    private LocalTime parseLocalTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private record GovernancePolicy(
        int maxConcurrentRuns,
        int sourceConcurrencyLimit,
        int projectConcurrencyLimit,
        String projectKey,
        String priority,
        String rejectPolicy,
        LocalTime windowStart,
        LocalTime windowEnd,
        ZoneId zone
    ) {
        String windowDisplay() {
            if (windowStart == null || windowEnd == null) {
                return "N/A";
            }
            return windowStart + " - " + windowEnd + " (" + zone + ")";
        }
    }

    private static final class SourceLoadAccumulator {
        private final UUID sourceDataSourceId;
        private final String sourceType;
        private long running;
        private long preparing;

        private SourceLoadAccumulator(UUID sourceDataSourceId, String sourceType) {
            this.sourceDataSourceId = sourceDataSourceId;
            this.sourceType = sourceType;
        }
    }

    private static final class ProjectLoadAccumulator {
        private final String projectKey;
        private long running;
        private long preparing;

        private ProjectLoadAccumulator(String projectKey) {
            this.projectKey = projectKey;
        }
    }

    /**
     * 获取最新的执行记录
     */
    @Transactional
    public Optional<IngestionExecutionDTO> getLatestExecution(Long taskId) {
        Optional<IngestionExecutionDTO> latest = executionRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId).map(executionMapper::toDto);
        if (latest.isPresent()) {
            return latest;
        }
        backfillExecutionsFromAirflow(taskId, 20);
        return executionRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId).map(executionMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fetchExecutionLog(Long taskId, Long executionId, Integer tryNumber, String keyword, String scope) {
        IngestionTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        IngestionExecution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        if (execution.getTask() == null || !taskId.equals(execution.getTask().getId())) {
            throw new IllegalArgumentException("Execution does not belong to task: " + taskId);
        }
        if (!isAirflowEnabled(task)) {
            return Map.of(
                "taskId", taskId,
                "executionId", executionId,
                "message", "Airflow 未启用，暂无日志"
            );
        }
        String dagId = airflowDagService.resolveDagIdForTask(task);
        String preferredTaskId = airflowDagService.resolveTaskIdForTask(task);
        String dagRunId = execution.getExecutionId();
        int resolvedTry = tryNumber == null ? 1 : Math.max(1, tryNumber);
        if (!StringUtils.hasText(dagRunId) || !StringUtils.hasText(dagId)) {
            return Map.of(
                "taskId", taskId,
                "executionId", executionId,
                "dagId", dagId,
                "dagRunId", dagRunId,
                "taskInstanceId", preferredTaskId,
                "tryNumber", resolvedTry,
                "message", "缺少 Airflow 执行信息，无法获取日志"
            );
        }
        List<String> candidates = resolveTaskLogCandidates(dagId, dagRunId, preferredTaskId);
        Map<String, Integer> tryHints = resolveTaskTryHints(dagId, dagRunId);
        Map<String, String> taskStates = resolveTaskStates(dagId, dagRunId);
        boolean aggregateAll = "all".equalsIgnoreCase(toText(scope));
        String selectedTaskId = StringUtils.hasText(preferredTaskId) ? preferredTaskId : null;
        int selectedTry = resolvedTry;
        String log = "";
        if (aggregateAll) {
            StringBuilder merged = new StringBuilder();
            for (String candidate : candidates) {
                if (!StringUtils.hasText(candidate)) {
                    continue;
                }
                List<Integer> tryCandidates = resolveTryCandidates(tryNumber, tryHints.get(candidate));
                for (Integer candidateTry : tryCandidates) {
                    int safeTry = candidateTry == null ? resolvedTry : Math.max(1, candidateTry);
                    String fetched = airflowClient.getTaskLog(dagId, dagRunId, candidate, safeTry).orElse("");
                    if (!StringUtils.hasText(fetched)) {
                        continue;
                    }
                    if (!StringUtils.hasText(selectedTaskId)) {
                        selectedTaskId = candidate;
                        selectedTry = safeTry;
                    }
                    merged
                        .append("===== TASK ")
                        .append(candidate)
                        .append(" (try ")
                        .append(safeTry)
                        .append(")")
                        .append(taskStates.containsKey(candidate) ? " state=" + taskStates.get(candidate) : "")
                        .append(" =====\n")
                        .append(fetched)
                        .append("\n\n");
                    break;
                }
            }
            log = merged.toString();
        } else {
            outer:
            for (String candidate : candidates) {
                if (!StringUtils.hasText(candidate)) {
                    continue;
                }
                List<Integer> tryCandidates = resolveTryCandidates(tryNumber, tryHints.get(candidate));
                for (Integer candidateTry : tryCandidates) {
                    int safeTry = candidateTry == null ? resolvedTry : Math.max(1, candidateTry);
                    String fetched = airflowClient.getTaskLog(dagId, dagRunId, candidate, safeTry).orElse("");
                    if (StringUtils.hasText(fetched)) {
                        selectedTaskId = candidate;
                        selectedTry = safeTry;
                        log = fetched;
                        break outer;
                    }
                }
            }
        }

        String filteredLog = filterLogByKeyword(log, keyword);
        String errorMessage = execution.getErrorMessage();
        String failureCategory = StringUtils.hasText(execution.getFailureCategory()) ? execution.getFailureCategory() : null;
        String failureAdvice = StringUtils.hasText(execution.getFailureAdvice()) ? execution.getFailureAdvice() : null;
        if (!StringUtils.hasText(failureCategory) && StringUtils.hasText(errorMessage)) {
            failureCategory = com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier.classify(errorMessage);
        }
        if (!StringUtils.hasText(failureAdvice) && StringUtils.hasText(failureCategory)) {
            failureAdvice = com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier.advice(failureCategory);
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("executionId", executionId);
        result.put("dagId", dagId);
        result.put("dagRunId", dagRunId);
        result.put("taskInstanceId", selectedTaskId);
        result.put("tryNumber", selectedTry);
        result.put("taskTryHints", tryHints);
        result.put("taskStates", taskStates);
        result.put("taskInstanceCandidates", candidates);
        result.put("scope", aggregateAll ? "all" : "single");
        result.put("keyword", toText(keyword));
        result.put("log", filteredLog);
        if (StringUtils.hasText(errorMessage)) {
            result.put("errorMessage", errorMessage);
        }
        if (StringUtils.hasText(failureCategory)) {
            result.put("failureCategory", failureCategory);
            result.put("failureAdvice", failureAdvice);
        }
        if (!StringUtils.hasText(filteredLog)) {
            result.put("message", StringUtils.hasText(log) ? "关键字过滤后无匹配日志" : "日志为空或未就绪");
        }
        return result;
    }

    public IngestionTaskDTO rebuildDag(Long taskId) {
        IngestionTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        requireActiveProductionTask(task);
        if (!Boolean.TRUE.equals(task.getAirflowEnabled())) {
            throw new IllegalStateException("未启用 Airflow 编排");
        }
        String dagId = airflowDagService.rebuildDagForTask(task);
        if (!StringUtils.hasText(dagId)) {
            throw new IllegalStateException("DAG 生成失败");
        }
        if (!dagId.equals(task.getAirflowDagId())) {
            task.setAirflowDagId(dagId);
        }
        task = taskRepository.save(task);
        dagPreheatService.preheatDag(dagId);
        return taskMapper.toDto(task);
    }

    public Map<String, Object> rebuildApiDags() {
        List<IngestionTask> tasks = taskRepository.findAll();
        List<Map<String, Object>> items = new ArrayList<>();
        int migrated = 0;
        int skipped = 0;
        int failed = 0;

        for (IngestionTask task : tasks) {
            if (task == null) {
                skipped++;
                items.add(apiDagMigrationItem(null, null, "skipped", "task is null", null));
                continue;
            }
            Long taskId = task.getId();
            String taskName = task.getName();
            if (!ApiConnectorTypes.isApiSourceType(task.getSourceType())) {
                skipped++;
                items.add(apiDagMigrationItem(taskId, taskName, "skipped", "not api source", null));
                continue;
            }
            if (!statusEquals(task.getStatus(), "active")) {
                skipped++;
                items.add(apiDagMigrationItem(taskId, taskName, "skipped", "task not active", null));
                continue;
            }
            if (Boolean.FALSE.equals(task.getAirflowEnabled())) {
                skipped++;
                items.add(apiDagMigrationItem(taskId, taskName, "skipped", "airflow disabled", null));
                continue;
            }
            try {
                classificationSealGuard.requireProductionSeal(task);
                String dagId = airflowDagService.rebuildDagForTask(task);
                if (!StringUtils.hasText(dagId)) {
                    throw new IllegalStateException("DAG 生成失败");
                }
                if (!dagId.equals(task.getAirflowDagId())) {
                    task.setAirflowDagId(dagId);
                }
                taskRepository.save(task);
                dagPreheatService.preheatDag(dagId);
                migrated++;
                items.add(apiDagMigrationItem(taskId, taskName, "migrated", null, dagId));
            } catch (Exception ex) {
                failed++;
                items.add(apiDagMigrationItem(taskId, taskName, "failed", trimMessage(ex.getMessage()), task.getAirflowDagId()));
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", tasks.size());
        summary.put("migrated", migrated);
        summary.put("skipped", skipped);
        summary.put("failed", failed);
        summary.put("items", List.copyOf(items));
        return summary;
    }

    private Map<String, Object> apiDagMigrationItem(Long taskId, String taskName, String action, String reason, String dagId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("taskId", taskId);
        item.put("taskName", taskName);
        item.put("action", action);
        if (StringUtils.hasText(reason)) {
            item.put("reason", reason);
        }
        if (StringUtils.hasText(dagId)) {
            item.put("dagId", dagId);
        }
        return item;
    }

    private String trimMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
    }

    private IngestionTask ensureAirflowDag(IngestionTask task) {
        return ensureAirflowDag(task, false);
    }

    private IngestionTask ensureAirflowDag(IngestionTask task, boolean forceRebuild) {
        if (task == null) {
            return task;
        }
        if (!statusEquals(task.getStatus(), "active")) {
            airflowDagService.deleteDagForTask(task);
            if (StringUtils.hasText(task.getAirflowDagId())) {
                task.setAirflowDagId(null);
                return taskRepository.save(task);
            }
            return task;
        }
        classificationSealGuard.requireProductionSeal(task);
        if (!isAirflowEnabled(task)) {
            return task;
        }
        // Split multi-content-block job into per-table files so each Airflow operator
        // runs Addax with a single content block (Addax only processes the first one).
        java.util.List<AddaxJobService.PerTableJob> perTableJobs = isApiSourceTask(task)
            ? List.of()
            : addaxJobService.splitJobIntoPerTableFiles(task.getAddaxJobPath());
        String dagId = forceRebuild
            ? airflowDagService.rebuildDagForTask(task, perTableJobs)
            : airflowDagService.ensureDagForTask(task, perTableJobs);
        if (!StringUtils.hasText(dagId)) {
            throw new IllegalStateException("DAG 生成失败，请检查 Airflow DAG 目录配置");
        }
        if (!dagId.equals(task.getAirflowDagId())) {
            task.setAirflowDagId(dagId);
            return taskRepository.save(task);
        }
        return task;
    }

    private boolean isAirflowEnabled(IngestionTask task) {
        if (task == null) {
            return false;
        }
        if (!airflowAdapter.isEnabled()) {
            return false;
        }
        Boolean enabled = task.getAirflowEnabled();
        return enabled == null || Boolean.TRUE.equals(enabled);
    }

    private AdmissionArtifacts stageAdmissionArtifacts(IngestionTask draft, IngestionTaskRevision revision) {
        IngestionTask admittedPlan = snapshot(draft);
        admittedPlan.setStatus("active");
        admittedPlan.setAddaxJobPath(null);
        admittedPlan.setAirflowDagId(null);

        String revisionKey = revision == null || revision.getId() == null
            ? "task_" + admittedPlan.getId() + "_" + UUID.randomUUID().toString().substring(0, 8)
            : "revision_" + revision.getId();
        List<String> generatedJobPaths = new ArrayList<>();
        AirflowDagService.StagedDag stagedDag = null;
        try {
            if (!isApiSourceTask(admittedPlan)) {
                com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource source =
                    resolveSource(admittedPlan, null);
                String originalName = admittedPlan.getName();
                admittedPlan.setName(
                    (StringUtils.hasText(originalName) ? originalName : "ingestion") + "-" + revisionKey
                );
                AddaxJobService.AddaxJobResult jobResult;
                try {
                    jobResult = source == null
                        ? addaxJobService.createJobFromTask(admittedPlan)
                        : addaxJobService.createJobFromTask(admittedPlan, source.readerType(), source.readerConfig());
                } finally {
                    admittedPlan.setName(originalName);
                }
                if (source != null && StringUtils.hasText(source.readerType())) {
                    admittedPlan.setSourceType(source.readerType());
                }
                admittedPlan.setAddaxJobPath(jobResult.jobPath());
                generatedJobPaths.add(jobResult.jobPath());
            }

            if (isAirflowEnabled(admittedPlan)) {
                admittedPlan.setAirflowDagId("ingestion_" + revisionKey);
                List<AddaxJobService.PerTableJob> perTableJobs = isApiSourceTask(admittedPlan)
                    ? List.of()
                    : addaxJobService.splitJobIntoPerTableFiles(admittedPlan.getAddaxJobPath());
                perTableJobs.stream()
                    .map(AddaxJobService.PerTableJob::hostJobPath)
                    .filter(StringUtils::hasText)
                    .forEach(generatedJobPaths::add);
                stagedDag = airflowDagService.stageDagForTask(
                    admittedPlan,
                    perTableJobs,
                    revision == null ? null : revision.getId(),
                    revision == null ? null : revision.getEffectiveConfigChecksum()
                );
                admittedPlan.setAirflowDagId(stagedDag.dagId());
            }
            return new AdmissionArtifacts(
                admittedPlan,
                List.copyOf(new java.util.LinkedHashSet<>(generatedJobPaths)),
                stagedDag,
                draft.getAirflowDagId(),
                revision == null ? null : revision.getId()
            );
        } catch (RuntimeException ex) {
            AdmissionArtifacts partial = new AdmissionArtifacts(
                admittedPlan,
                List.copyOf(new java.util.LinkedHashSet<>(generatedJobPaths)),
                stagedDag,
                draft.getAirflowDagId(),
                revision == null ? null : revision.getId()
            );
            discardAdmissionAttemptArtifacts(partial);
            throw ex;
        }
    }

    private boolean registerAdmissionArtifactLifecycle(AdmissionArtifacts artifacts) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (artifacts.revisionId() != null) {
                    reconcileAdmissionDagDeployment(artifacts.revisionId());
                }
            }

            @Override
            public void afterCompletion(int status) {
                boolean rolledBack = status != TransactionSynchronization.STATUS_COMMITTED;
                if (rolledBack) {
                    discardAdmissionAttemptArtifacts(artifacts);
                }
            }
        });
        return true;
    }

    private void discardAdmissionAttemptArtifacts(AdmissionArtifacts artifacts) {
        if (artifacts == null) {
            return;
        }
        for (String path : artifacts.generatedJobPaths()) {
            addaxJobService.deleteJobIfExists(path);
        }
        // The final path is revision-scoped and may already belong to a concurrent
        // admission winner. Outer transaction rollback owns only this attempt's
        // staged file. A published DAG may be removed only by the separately
        // transacted, revision-locked activation compensation below.
        airflowDagService.discardStagedDag(artifacts.stagedDag(), false);
    }

    private record AdmissionArtifacts(
        IngestionTask task,
        List<String> generatedJobPaths,
        AirflowDagService.StagedDag stagedDag,
        String previousAirflowDagId,
        Long revisionId
    ) {}

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void reconcileAdmissionDagDeployments() {
        if (accessContractService == null) {
            return;
        }
        for (IngestionTaskRevision revision : accessContractService.findDagDeploymentsNeedingReconciliation()) {
            try {
                reconcileAdmissionDagDeployment(revision.getId());
            } catch (RuntimeException ex) {
                log.error("Admission DAG reconciliation failed for revision {}: {}", revision.getId(), ex.getMessage(), ex);
            }
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void reconcileAdmissionDagDeployment(Long revisionId) {
        if (accessContractService == null) {
            throw new IllegalStateException("Access contract service is unavailable");
        }
        AtomicReference<AirflowDagService.StagedDag> stagedDagRef = new AtomicReference<>();
        AdmissionDagDeployment deployment;
        try {
            deployment = inRequiresNewTransaction(() -> {
                Long taskId = revisionRepository.findTaskIdById(revisionId)
                    .orElseThrow(() -> new IllegalStateException("Ingestion task revision not found: " + revisionId));
                IngestionTask canonical = taskRepository.findByIdForUpdate(taskId)
                    .orElseThrow(() -> new IllegalStateException("Admission task not found: " + taskId));
                IngestionTaskRevision revision = lockAdmissionRevision(revisionId);
                if (revision.getTask() == null || !taskId.equals(revision.getTask().getId())) {
                    throw new IllegalStateException("Admission revision task changed while acquiring locks: " + revisionId);
                }
                IngestionTask runtime = accessContractService.materializeExecutionTask(canonical, revisionId);
                AirflowDagService.StagedDag stagedDag = stagedDagFromRevision(revision, runtime);
                stagedDagRef.set(stagedDag);

                if (IngestionAccessContractService.REVISION_DRAFT.equals(revision.getState())) {
                    try {
                        if (!Files.exists(stagedDag.finalPath())) {
                            if (!Files.exists(stagedDag.stagedPath())) {
                                stagedDag = restageAdmissionDag(runtime, revision);
                                stagedDagRef.set(stagedDag);
                            }
                            String publishedDagId = airflowDagService.publishStagedDag(stagedDag);
                            if (!java.util.Objects.equals(runtime.getAirflowDagId(), publishedDagId)) {
                                throw new IllegalStateException("Published DAG identity does not match admitted revision");
                            }
                        }
                    } catch (RuntimeException publishError) {
                        throw new AdmissionDagPhaseException("PUBLISH", publishError);
                    }

                    try {
                        applyPlanSnapshot(canonical, runtime);
                        canonical.setStatus("active");
                        taskRepository.save(canonical);
                        accessContractService.activateDraftRevision(taskId);
                        accessContractService.markDagDeployment(
                            revisionId,
                            IngestionAccessContractService.DAG_DEPLOYMENT_RECONCILIATION_REQUIRED,
                            null
                        );
                    } catch (RuntimeException activationError) {
                        throw new AdmissionDagPhaseException("ACTIVATION", activationError);
                    }
                } else if (!IngestionAccessContractService.REVISION_ACTIVE.equals(revision.getState())) {
                    throw new IllegalStateException(
                        "Admission revision is not deployable: " + revisionId + " state=" + revision.getState()
                    );
                }
                return new AdmissionDagDeployment(revision, runtime);
            });
        } catch (AdmissionDagPhaseException phaseError) {
            if ("PUBLISH".equals(phaseError.phase())) {
                markDagReconciliationFailure(revisionId, "DAG_PUBLISH_FAILED: " + phaseError.getCause().getMessage());
            } else {
                compensateFailedAdmissionActivation(revisionId, stagedDagRef.get(), phaseError);
            }
            throw phaseError.unwrap();
        }
        if (deployment == null) {
            throw new IllegalStateException("Admission DAG reconciliation transaction returned no result");
        }
        IngestionTaskRevision revision = deployment.revision();
        IngestionTask runtime = deployment.runtime();

        try {
            if (StringUtils.hasText(revision.getPreviousAirflowDagId())
                && !revision.getPreviousAirflowDagId().equals(runtime.getAirflowDagId())) {
                airflowDagService.retireDagStrict(revision.getPreviousAirflowDagId(), runtime);
            }
            airflowDagService.setDagPausedStrict(runtime.getAirflowDagId(), false);
            inRequiresNewTransactionWithoutResult(() -> accessContractService.markDagDeployment(
                    revisionId,
                    IngestionAccessContractService.DAG_DEPLOYMENT_ACTIVE,
                    null
                ));
        } catch (RuntimeException ex) {
            markDagReconciliationFailure(revisionId, "DAG_CUTOVER_FAILED: " + ex.getMessage());
            throw ex;
        }
    }

    private void compensateFailedAdmissionActivation(
        Long revisionId,
        AirflowDagService.StagedDag stagedDag,
        AdmissionDagPhaseException activationError
    ) {
        try {
            inRequiresNewTransactionWithoutResult(() -> {
                IngestionTaskRevision current = lockAdmissionRevision(revisionId);
                if (IngestionAccessContractService.REVISION_ACTIVE.equals(current.getState())) {
                    return;
                }
                if (IngestionAccessContractService.REVISION_DRAFT.equals(current.getState()) && stagedDag != null) {
                    airflowDagService.discardStagedDagStrict(stagedDag, true);
                }
                accessContractService.markDagDeployment(
                    revisionId,
                    IngestionAccessContractService.DAG_DEPLOYMENT_RESTAGE_REQUIRED,
                    "DAG_ACTIVATION_FAILED: " + activationError.getCause().getMessage()
                );
            });
        } catch (RuntimeException compensationError) {
            markDagReconciliationFailure(
                revisionId,
                "DAG_ACTIVATION_FAILED: " + activationError.getCause().getMessage()
                    + "; DAG_COMPENSATION_FAILED: " + compensationError.getMessage()
            );
            activationError.addSuppressed(compensationError);
        }
    }

    private record AdmissionDagDeployment(IngestionTaskRevision revision, IngestionTask runtime) {}

    private IngestionTaskRevision lockAdmissionRevision(Long revisionId) {
        IngestionTaskRevision revision = entityManager.find(
            IngestionTaskRevision.class,
            revisionId,
            LockModeType.PESSIMISTIC_WRITE
        );
        if (revision == null) {
            throw new IllegalStateException("Ingestion task revision not found: " + revisionId);
        }
        return revision;
    }

    private static final class AdmissionDagPhaseException extends RuntimeException {
        private final String phase;

        private AdmissionDagPhaseException(String phase, RuntimeException cause) {
            super(cause.getMessage(), cause);
            this.phase = phase;
        }

        private String phase() {
            return phase;
        }

        private RuntimeException unwrap() {
            RuntimeException cause = (RuntimeException) getCause();
            for (Throwable suppressed : getSuppressed()) {
                cause.addSuppressed(suppressed);
            }
            return cause;
        }
    }

    private AirflowDagService.StagedDag restageAdmissionDag(IngestionTask runtime, IngestionTaskRevision revision) {
        List<AddaxJobService.PerTableJob> perTableJobs = isApiSourceTask(runtime)
            ? List.of()
            : addaxJobService.splitJobIntoPerTableFiles(runtime.getAddaxJobPath());
        AirflowDagService.StagedDag staged = airflowDagService.stageDagForTask(
            runtime,
            perTableJobs,
            revision.getId(),
            revision.getEffectiveConfigChecksum()
        );
        accessContractService.markDraftDagStaged(
            revision.getId(),
            staged.stagedPath().toString(),
            staged.finalPath().toString(),
            revision.getPreviousAirflowDagId()
        );
        return staged;
    }

    private AirflowDagService.StagedDag stagedDagFromRevision(IngestionTaskRevision revision, IngestionTask runtime) {
        if (!StringUtils.hasText(revision.getStagedDagPath()) || !StringUtils.hasText(revision.getPublishedDagPath())) {
            throw new IllegalStateException("Revision has no staged DAG paths: " + revision.getId());
        }
        return new AirflowDagService.StagedDag(
            runtime.getAirflowDagId(),
            Path.of(revision.getStagedDagPath()),
            Path.of(revision.getPublishedDagPath())
        );
    }

    private void markDagReconciliationFailure(Long revisionId, String error) {
        inRequiresNewTransactionWithoutResult(() -> accessContractService.markDagDeployment(
                revisionId,
                IngestionAccessContractService.DAG_DEPLOYMENT_RECONCILIATION_REQUIRED,
                truncateText(error, 4000)
            ));
    }

    private boolean planConfigChanged(IngestionTask before, IngestionTask current) {
        return executionConfigChanged(before, current)
            || !java.util.Objects.equals(before.getName(), current.getName())
            || !java.util.Objects.equals(before.getDescription(), current.getDescription())
            || !java.util.Objects.equals(before.getClassificationSeal(), current.getClassificationSeal())
            || !java.util.Objects.equals(before.getFieldClassifications(), current.getFieldClassifications());
    }

    private IngestionTaskDTO transitionOperationalState(IngestionTask task, String requestedStatus) {
        if (statusEquals(task.getStatus(), "active") && statusEquals(requestedStatus, "paused")) {
            if (StringUtils.hasText(task.getAirflowDagId())) {
                airflowDagService.deleteDagForTask(task);
            }
            task.setStatus("paused");
            IngestionTask saved = taskRepository.save(task);
            return enrichTaskDto(taskMapper.toDto(saved));
        }
        if (statusEquals(task.getStatus(), "paused") && statusEquals(requestedStatus, "active")) {
            classificationSealGuard.requireProductionSeal(task);
            task.setStatus("active");
            IngestionTask saved = taskRepository.save(task);
            saved = ensureAirflowDag(saved, true);
            if (StringUtils.hasText(saved.getAirflowDagId())) {
                dagPreheatService.preheatDag(saved.getAirflowDagId());
            }
            return enrichTaskDto(taskMapper.toDto(saved));
        }
        throw new IllegalStateException(
            "Unsupported operational state transition: " + task.getStatus() + " -> " + requestedStatus
        );
    }

    private IngestionTaskDTO saveDraftRevisionForActiveTask(IngestionTask activeTask, IngestionTaskDTO dto) {
        assertNoRawTaskSecrets(dto);
        IngestionTask draft = accessContractService.findLatestDraftRevision(activeTask.getId()).isPresent()
            ? accessContractService.materializeLatestDraft(activeTask)
            : snapshot(activeTask);
        draft.setSourceConfig(stripRawSecrets(draft.getSourceConfig()));
        draft.setDestinationConfig(stripRawSecrets(draft.getDestinationConfig()));
        draft.setAddaxConfig(stripRawSecrets(draft.getAddaxConfig()));
        IngestionTask beforeDraft = snapshot(draft);
        JsonNode previousClassificationSeal = copyJsonNode(draft.getClassificationSeal());

        taskMapper.partialUpdate(draft, dto);
        preserveAbsentManagedSecrets(beforeDraft, draft, dto);
        if (dto.getSyncConfig() != null) {
            draft.setSyncConfig(dto.getSyncConfig());
        }
        // Runtime artifact ids are owned by admission, never by the edit payload.
        draft.setAirflowDagId(activeTask.getAirflowDagId());
        draft.setStatus("draft");

        boolean sourceChanged = sourceIdentityChanged(beforeDraft, draft);
        if (sourceChanged && java.util.Objects.equals(previousClassificationSeal, draft.getClassificationSeal())) {
            draft.setClassificationSeal(null);
            draft.setFieldClassifications(null);
        }
        if (sourceChanged && isFileSourceType(draft.getSourceType())) {
            // A newly uploaded file invalidates staging and every previous result.
            draft.setStagingTableName(null);
            draft.setPreCheckStatus(null);
        }
        if (isFileSourceType(draft.getSourceType()) && draft.getQualityPreCheckEnabled() == null) {
            draft.setQualityPreCheckEnabled(true);
        }

        accessContractService.recordDraftRevision(
            draft,
            dto.getQualityPolicyRef(),
            !sourceChanged
        );

        try {
            changeLogService.recordTaskUpdate(beforeDraft, draft);
        } catch (Exception ex) {
            log.warn("Failed to record draft change log for active task: {}", activeTask.getId(), ex);
        }
        log.info("Saved draft revision for active ingestion task ID: {}", activeTask.getId());
        return enrichTaskDto(taskMapper.toDto(draft));
    }

    private void preserveAbsentManagedSecrets(IngestionTask managed, IngestionTask target, IngestionTaskDTO incoming) {
        if (target == null) {
            return;
        }
        // Managed credentials are resolved at runtime from platform secure_props.
        // Never inherit an omitted credential from the compatibility task row or
        // from an encrypted historical revision.
        target.setSourceConfig(stripRawSecrets(target.getSourceConfig()));
        target.setDestinationConfig(stripRawSecrets(target.getDestinationConfig()));
        target.setAddaxConfig(stripRawSecrets(target.getAddaxConfig()));
    }

    private void assertNoRawTaskSecrets(IngestionTaskDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("ingestion task is required");
        }
        IngestionSensitiveConfigSupport.assertNoRawSecrets(dto.getSourceConfig(), "sourceConfig");
        IngestionSensitiveConfigSupport.assertNoRawSecrets(dto.getDestinationConfig(), "destinationConfig");
        IngestionSensitiveConfigSupport.assertNoRawSecrets(dto.getAddaxConfig(), "addaxConfig");
    }

    private JsonNode stripRawSecrets(JsonNode config) {
        if (config == null || config.isNull()) {
            return null;
        }
        return IngestionSensitiveConfigSupport.stripRawSecrets(config);
    }

    private void requireSecretMigrationReady(Long taskId) {
        if (secretMigrationService != null) {
            secretMigrationService.requireTaskReady(taskId);
        }
    }

    private <T> T inRequiresNewTransaction(Supplier<T> work) {
        return requiresNewExecutor == null ? work.get() : requiresNewExecutor.execute(work);
    }

    private void inRequiresNewTransactionWithoutResult(Runnable work) {
        if (requiresNewExecutor == null) {
            work.run();
            return;
        }
        requiresNewExecutor.executeWithoutResult(work);
    }

    private void requirePassedFilePreCheck(IngestionTask task) {
        if (task == null
            || !isFileSourceType(task.getSourceType())
            || !Boolean.TRUE.equals(task.getQualityPreCheckEnabled())) {
            return;
        }
        if (!statusEquals(task.getPreCheckStatus(), "PASSED")) {
            throw new IllegalStateException(
                "FILE_PRECHECK_REQUIRED: 文件草稿必须完成质量预检并通过后才能准入"
            );
        }
    }

    private void applyPlanSnapshot(IngestionTask target, IngestionTask source) {
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setSourceType(source.getSourceType());
        target.setSourceDataSourceId(source.getSourceDataSourceId());
        target.setSourceConfig(copyJsonNode(source.getSourceConfig()));
        target.setDestinationType(source.getDestinationType());
        target.setDestinationConfig(copyJsonNode(source.getDestinationConfig()));
        target.setSyncMode(source.getSyncMode());
        target.setSyncSchedule(source.getSyncSchedule());
        target.setTableMapping(copyJsonNode(source.getTableMapping()));
        target.setSyncConfig(copyJsonNode(source.getSyncConfig()));
        target.setGraphDsl(copyJsonNode(source.getGraphDsl()));
        target.setClassificationSeal(copyJsonNode(source.getClassificationSeal()));
        target.setFieldClassifications(copyJsonNode(source.getFieldClassifications()));
        target.setAddaxConfig(copyJsonNode(source.getAddaxConfig()));
        target.setAddaxJobPath(source.getAddaxJobPath());
        target.setAirflowEnabled(source.getAirflowEnabled());
        target.setAirflowDagId(source.getAirflowDagId());
        target.setQualityPreCheckEnabled(source.getQualityPreCheckEnabled());
        target.setStagingTableName(source.getStagingTableName());
        target.setPreCheckStatus(source.getPreCheckStatus());
    }

    private IngestionTask snapshot(IngestionTask task) {
        if (task == null) {
            return null;
        }
        IngestionTask snap = new IngestionTask();
        snap.setId(task.getId());
        snap.setName(task.getName());
        snap.setDescription(task.getDescription());
        snap.setSourceType(task.getSourceType());
        snap.setSourceConfig(copyJsonNode(task.getSourceConfig()));
        snap.setSourceDataSourceId(task.getSourceDataSourceId());
        snap.setDestinationType(task.getDestinationType());
        snap.setDestinationConfig(copyJsonNode(task.getDestinationConfig()));
        snap.setSyncMode(task.getSyncMode());
        snap.setSyncSchedule(task.getSyncSchedule());
        snap.setTableMapping(copyJsonNode(task.getTableMapping()));
        snap.setSyncConfig(copyJsonNode(task.getSyncConfig()));
        snap.setGraphDsl(copyJsonNode(task.getGraphDsl()));
        snap.setClassificationSeal(copyJsonNode(task.getClassificationSeal()));
        snap.setFieldClassifications(copyJsonNode(task.getFieldClassifications()));
        snap.setAddaxConfig(copyJsonNode(task.getAddaxConfig()));
        snap.setAirflowEnabled(task.getAirflowEnabled());
        snap.setAirflowDagId(task.getAirflowDagId());
        snap.setQualityPreCheckEnabled(task.getQualityPreCheckEnabled());
        snap.setStagingTableName(task.getStagingTableName());
        snap.setPreCheckStatus(task.getPreCheckStatus());
        snap.setStatus(task.getStatus());
        return snap;
    }

    private boolean executionConfigChanged(IngestionTask before, IngestionTask current) {
        return !java.util.Objects.equals(before.getSourceType(), current.getSourceType())
            || !java.util.Objects.equals(before.getSourceDataSourceId(), current.getSourceDataSourceId())
            || !java.util.Objects.equals(before.getSourceConfig(), current.getSourceConfig())
            || !java.util.Objects.equals(before.getDestinationType(), current.getDestinationType())
            || !java.util.Objects.equals(before.getDestinationConfig(), current.getDestinationConfig())
            || !java.util.Objects.equals(before.getSyncMode(), current.getSyncMode())
            || !java.util.Objects.equals(before.getSyncSchedule(), current.getSyncSchedule())
            || !java.util.Objects.equals(before.getTableMapping(), current.getTableMapping())
            || !java.util.Objects.equals(before.getSyncConfig(), current.getSyncConfig())
            || !java.util.Objects.equals(before.getGraphDsl(), current.getGraphDsl())
            || !java.util.Objects.equals(before.getAddaxConfig(), current.getAddaxConfig())
            || !java.util.Objects.equals(before.getAirflowEnabled(), current.getAirflowEnabled())
            || !java.util.Objects.equals(before.getAirflowDagId(), current.getAirflowDagId())
            || !java.util.Objects.equals(before.getQualityPreCheckEnabled(), current.getQualityPreCheckEnabled())
            || !java.util.Objects.equals(before.getStagingTableName(), current.getStagingTableName())
            || !java.util.Objects.equals(before.getPreCheckStatus(), current.getPreCheckStatus());
    }

    private void validateExcelFormulaOrFail(IngestionTask task) {
        if (task == null) {
            return;
        }
        if (!isFileSourceType(task.getSourceType())) {
            return;
        }

        String sourceType = StringUtils.hasText(task.getSourceType()) ? task.getSourceType().toLowerCase(java.util.Locale.ROOT) : null;
        if ("csv".equals(sourceType) || "txtfilereader".equals(sourceType)) {
            return;
        }

        String fileType = resolveUploadedFileType(task);
        if (StringUtils.hasText(fileType) && ("csv".equalsIgnoreCase(fileType) || "txt".equalsIgnoreCase(fileType))) {
            return;
        }

        String filePath = resolveUploadedFilePath(task.getSourceConfig());
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        byte[] plain = fileUploadService.readPlainBytes(Path.of(filePath));
        String sheetSelector = resolveSourceSheetSelector(task.getSourceConfig());
        if (StringUtils.hasText(sheetSelector)) {
            excelParseService.validateFormulaCells(plain, sheetSelector);
        } else {
            excelParseService.validateFormulaCells(plain);
        }
    }

    private void validateUploadedFileColumnsOrFail(IngestionTask task) {
        if (task == null || !isFileSourceType(task.getSourceType())) {
            return;
        }

        int expectedColumns = resolveExpectedFileColumnCount(task.getSourceConfig());
        if (expectedColumns < 1) {
            return;
        }

        String filePath = resolveUploadedFilePath(task.getSourceConfig());
        if (!StringUtils.hasText(filePath)) {
            return;
        }

        String fileType = resolveUploadedFileType(task);
        byte[] plain = fileUploadService.readPlainBytes(Path.of(filePath));
        int actualColumns = resolveUploadedFileColumnCount(fileType, plain);
        if (actualColumns < 1) {
            return;
        }

        if (actualColumns != expectedColumns) {
            throw new IllegalArgumentException(
                String.format(
                    "入湖文件列数与配置不一致：配置 %d 列，实际 %d 列，请检查文件头/分隔符/是否使用了错误文件",
                    expectedColumns,
                    actualColumns
                )
            );
        }
    }

    private int resolveUploadedFileColumnCount(String fileType, byte[] plain) {
        String normalized = normalizeUploadedFileType(fileType);
        if (isCsvLikeExtension(normalized)) {
            try {
                return csvParseService.parseHeaders(new ByteArrayInputStream(plain)).size();
            } catch (Exception ex) {
                throw new IllegalArgumentException("CSV 入湖文件解析失败：" + ex.getMessage(), ex);
            }
        }
        if (isExcelLikeType(normalized)) {
            try {
                return fileUploadService.parseExcelHeaders(plain).size();
            } catch (Exception ex) {
                throw new IllegalArgumentException("Excel 入湖文件解析失败：" + ex.getMessage(), ex);
            }
        }
        return -1;
    }

    private int resolveExpectedFileColumnCount(JsonNode sourceConfig) {
        if (sourceConfig == null) {
            return -1;
        }
        JsonNode fileColumnsNode = sourceConfig.path("_fileColumns");
        if (!fileColumnsNode.isArray()) {
            return -1;
        }
        return fileColumnsNode.size();
    }

    private String resolveUploadedFilePath(JsonNode sourceConfig) {
        if (sourceConfig == null) {
            return null;
        }
        for (String key : List.of("hostPath", "_filePath", "filePath", "path", "_containerPath", "containerPath")) {
            String value = sourceConfig.path(key).asText(null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String resolveUploadedFileType(IngestionTask task) {
        String sourceType = StringUtils.hasText(task.getSourceType()) ? task.getSourceType().toLowerCase(java.util.Locale.ROOT) : null;
        JsonNode sourceConfig = task.getSourceConfig();
        if (sourceConfig != null) {
            String configured = sourceConfig.path("_fileType").asText(null);
            if (StringUtils.hasText(configured)) {
                return normalizeUploadedFileType(configured);
            }
            configured = sourceConfig.path("fileType").asText(null);
            if (StringUtils.hasText(configured)) {
                return normalizeUploadedFileType(configured);
            }
        }
        if ("txtfilereader".equals(sourceType) || "csv".equals(sourceType) || "txt".equals(sourceType)) {
            return "csv";
        }
        String filePath = resolveUploadedFilePath(sourceConfig);
        if (!StringUtils.hasText(filePath)) {
            return "csv".equals(sourceType) || "txt".equals(sourceType) ? "csv" : sourceType;
        }
        String extension = extractFileTypeFromPath(filePath);
        if (!StringUtils.hasText(extension)) {
            return "csv".equals(sourceType) || "txt".equals(sourceType) ? "csv" : sourceType;
        }
        if ("txt".equals(sourceType) || "txtfilereader".equals(sourceType)) {
            if (isCsvLikeExtension(extension)) {
                return "csv";
            }
        }
        return normalizeUploadedFileType(extension);
    }

    private String resolveSourceSheetSelector(JsonNode sourceConfig) {
        if (sourceConfig == null) {
            return null;
        }
        for (String key : List.of("_sheetName", "sheetName", "_sourceSheet", "sourceSheet", "sheet")) {
            String sheet = sourceConfig.path(key).asText(null);
            if (StringUtils.hasText(sheet)) {
                return sheet.trim();
            }
        }

        JsonNode sheetIndex = sourceConfig.path("sheetIndex");
        if (sheetIndex.isInt()) {
            return String.valueOf(sheetIndex.intValue());
        }
        if (sheetIndex.isTextual() && StringUtils.hasText(sheetIndex.asText())) {
            return sheetIndex.asText().trim();
        }
        return null;
    }

    private boolean isCsvLikeExtension(String extension) {
        return "csv".equalsIgnoreCase(extension)
            || "tsv".equalsIgnoreCase(extension)
            || "txt".equalsIgnoreCase(extension)
            || "text".equalsIgnoreCase(extension);
    }

    private boolean isExcelLikeType(String type) {
        return "excel".equalsIgnoreCase(type)
            || "excelreader".equalsIgnoreCase(type)
            || "xls".equalsIgnoreCase(type)
            || "xlsx".equalsIgnoreCase(type);
    }

    private String normalizeUploadedFileType(String fileType) {
        if (!StringUtils.hasText(fileType)) {
            return "";
        }
        String normalized = stripEncryptionSuffix(fileType.trim().toLowerCase(java.util.Locale.ROOT));
        if ("txtfilereader".equals(normalized) || "txt".equals(normalized)) {
            return "csv";
        }
        if ("excelreader".equals(normalized)) {
            return "excel";
        }
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String extractFileTypeFromPath(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return "";
        }
        String safeName = stripEncryptionSuffix(filePath.trim());
        int dot = safeName.lastIndexOf('.');
        if (dot < 0 || dot >= safeName.length() - 1) {
            return "";
        }
        return safeName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private String stripEncryptionSuffix(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".enc") ? lower.substring(0, lower.length() - 4) : value;
    }

    private com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolveSource(
        IngestionTask task,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource provided
    ) {
        if (provided != null) {
            return provided;
        }
        if (task == null || task.getSourceDataSourceId() == null) {
            return null;
        }
        // File sources have no dataSourceId-based connection
        if (isFileSourceType(task.getSourceType())) {
            return null;
        }
        return sourceResolver.resolve(task.getSourceDataSourceId(), List.of());
    }

    private boolean isFileSourceType(String sourceType) {
        if (!org.springframework.util.StringUtils.hasText(sourceType)) return false;
        String lower = sourceType.toLowerCase(java.util.Locale.ROOT);
        return "excel".equals(lower)
            || "csv".equals(lower)
            || "txt".equals(lower)
            || "excelreader".equals(lower)
            || "txtfilereader".equals(lower);
    }

    private boolean isApiSourceTask(IngestionTask task) {
        return task != null && ApiConnectorTypes.isApiSourceType(task.getSourceType());
    }

    private Map<String, Integer> resolveTaskTryHints(String dagId, String dagRunId) {
        Map<String, Integer> hints = new java.util.LinkedHashMap<>();
        List<Map<String, Object>> instances = airflowClient.listTaskInstances(dagId, dagRunId).orElse(List.of());
        for (Map<String, Object> instance : instances) {
            if (instance == null || instance.isEmpty()) {
                continue;
            }
            String taskId = toText(instance.get("task_id"));
            if (!StringUtils.hasText(taskId)) {
                taskId = toText(instance.get("taskId"));
            }
            if (!StringUtils.hasText(taskId)) {
                continue;
            }
            Integer tryNumber = resolveInteger(instance.get("try_number"));
            if (tryNumber == null) {
                tryNumber = resolveInteger(instance.get("tryNumber"));
            }
            if (tryNumber == null) {
                tryNumber = resolveInteger(instance.get("try"));
            }
            if (tryNumber == null || tryNumber <= 0) {
                continue;
            }
            String normalizedTaskId = taskId.trim();
            hints.merge(normalizedTaskId, tryNumber, Integer::max);
        }
        return hints;
    }

    private List<Integer> resolveTryCandidates(Integer requestedTry, Integer hintTry) {
        java.util.LinkedHashSet<Integer> ordered = new java.util.LinkedHashSet<>();
        Integer safeHintTry = hintTry == null ? null : Math.max(1, hintTry);
        if (requestedTry != null) {
            ordered.add(Math.max(1, requestedTry));
            if (safeHintTry != null) {
                ordered.add(safeHintTry);
            }
        } else {
            if (safeHintTry != null) {
                ordered.add(safeHintTry);
                int fallbackCount = 0;
                for (int cursor = safeHintTry - 1; cursor >= 1 && fallbackCount < 2; cursor--) {
                    ordered.add(cursor);
                    fallbackCount++;
                }
            }
            ordered.add(1);
        }
        return new java.util.ArrayList<>(ordered);
    }

    private Integer resolveInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number number) {
                return number.intValue();
            }
            String text = value.toString().trim();
            if (!StringUtils.hasText(text)) {
                return null;
            }
            return Integer.parseInt(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> resolveTaskLogCandidates(String dagId, String dagRunId, String preferredTaskId) {
        java.util.LinkedHashSet<String> ordered = new java.util.LinkedHashSet<>();
        List<Map<String, Object>> instances = airflowClient.listTaskInstances(dagId, dagRunId).orElse(List.of());
        java.util.LinkedHashSet<String> addaxTasks = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> otherTasks = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> discovered = new java.util.LinkedHashSet<>();
        for (Map<String, Object> instance : instances) {
            if (instance == null || instance.isEmpty()) {
                continue;
            }
            String taskId = toText(instance.get("task_id"));
            if (!StringUtils.hasText(taskId)) {
                taskId = toText(instance.get("taskId"));
            }
            if (!StringUtils.hasText(taskId)) {
                continue;
            }
            String normalized = taskId.trim();
            discovered.add(normalized);
            if (normalized.startsWith("addax_")) {
                addaxTasks.add(normalized);
            } else {
                otherTasks.add(normalized);
            }
        }
        if (StringUtils.hasText(preferredTaskId)) {
            String preferred = preferredTaskId.trim();
            if (discovered.isEmpty() || discovered.contains(preferred)) {
                ordered.add(preferred);
            }
        }
        ordered.addAll(addaxTasks);
        ordered.addAll(otherTasks);
        return new java.util.ArrayList<>(ordered);
    }

    private Map<String, String> resolveTaskStates(String dagId, String dagRunId) {
        Map<String, String> states = new java.util.LinkedHashMap<>();
        List<Map<String, Object>> instances = airflowClient.listTaskInstances(dagId, dagRunId).orElse(List.of());
        for (Map<String, Object> instance : instances) {
            if (instance == null || instance.isEmpty()) {
                continue;
            }
            String taskId = toText(instance.get("task_id"));
            if (!StringUtils.hasText(taskId)) {
                taskId = toText(instance.get("taskId"));
            }
            if (!StringUtils.hasText(taskId)) {
                continue;
            }
            String state = toText(instance.get("state"));
            states.put(taskId.trim(), state);
        }
        return states;
    }

    private String filterLogByKeyword(String log, String keyword) {
        if (!StringUtils.hasText(log)) {
            return "";
        }
        String normalizedKeyword = toText(keyword);
        if (!StringUtils.hasText(normalizedKeyword)) {
            return log;
        }
        String needle = normalizedKeyword.toLowerCase(java.util.Locale.ROOT);
        String[] lines = log.split("\r?\n");
        StringBuilder filtered = new StringBuilder();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            if (line.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                filtered.append(line).append("\n");
            }
        }
        return filtered.toString();
    }

    private boolean sourceIdentityChanged(IngestionTask before, IngestionTask after) {
        if (before == null || after == null) {
            return false;
        }
        return !java.util.Objects.equals(before.getSourceDataSourceId(), after.getSourceDataSourceId())
            || !java.util.Objects.equals(before.getSourceType(), after.getSourceType())
            || !java.util.Objects.equals(before.getSourceConfig(), after.getSourceConfig());
    }

    private void recordTaskRevision(
        IngestionTask task,
        String qualityPolicyRef,
        boolean activateImmediately,
        boolean inheritPreviousQualityPolicyRef
    ) {
        if (accessContractService == null) {
            return;
        }
        accessContractService.recordDraftRevision(task, qualityPolicyRef, inheritPreviousQualityPolicyRef);
        if (activateImmediately) {
            accessContractService.activateDraftRevision(task.getId());
        }
    }

    private IngestionTaskDTO enrichTaskDto(IngestionTaskDTO dto) {
        return accessContractService == null ? dto : accessContractService.enrichTaskDto(dto);
    }

    private void enrichTaskDtos(List<IngestionTaskDTO> dtos) {
        if (accessContractService != null) {
            accessContractService.enrichTaskDtos(dtos);
        }
    }

}
