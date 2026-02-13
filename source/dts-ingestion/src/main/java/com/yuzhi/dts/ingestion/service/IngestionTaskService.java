package com.yuzhi.dts.ingestion.service;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalAuditDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalAuditSummaryDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalStateDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.etl.AirflowClient;
import com.yuzhi.dts.ingestion.service.etl.AirflowDagService;
import com.yuzhi.dts.ingestion.service.etl.IncrementalSyncService;
import com.yuzhi.dts.ingestion.service.mapper.IngestionExecutionMapper;
import com.yuzhi.dts.ingestion.service.mapper.IngestionTaskMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 数据入湖任务服务
 * 提供任务的CRUD操作、执行和监控功能
 * 所有操作自动记录审计信息（通过AbstractAuditingEntity）
 */
@Service
@Transactional
public class IngestionTaskService {

    private static final Logger log = LoggerFactory.getLogger(IngestionTaskService.class);

    private final IngestionTaskRepository taskRepository;
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

    public IngestionTaskService(
        IngestionTaskRepository taskRepository,
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
        IngestionTaskChangeLogService changeLogService
    ) {
        this.taskRepository = taskRepository;
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
        log.info("Creating new ingestion task: {} (skipJob={})", dto.getName(), skipJob);

        IngestionTask task = taskMapper.toEntity(dto);

        if (!skipJob) {
            // 生成Addax Job JSON
            try {
                com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource source =
                    resolveSource(task, resolvedSource);
                AddaxJobService.AddaxJobResult jobResult = source == null
                    ? addaxJobService.createJobFromTask(task)
                    : addaxJobService.createJobFromTask(task, source.readerType(), source.readerConfig());
                if (source != null && StringUtils.hasText(source.readerType())) {
                    task.setSourceType(source.readerType());
                }
                task.setAddaxJobPath(jobResult.jobPath());
            } catch (Exception e) {
                log.error("Failed to generate Addax job for task: {}", dto.getName(), e);
                auditService.auditAction(
                    "INGESTION_TASK_CREATE",
                    AuditStage.FAIL,
                    dto.getName(),
                    Map.of("error", e.getMessage())
                );
                throw new RuntimeException("Failed to generate Addax job", e);
            }
        } else {
            try {
                com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource source =
                    resolveSource(task, resolvedSource);
                if (source != null && StringUtils.hasText(source.readerType())) {
                    task.setSourceType(source.readerType());
                }
            } catch (Exception ex) {
                log.warn("Skip Addax job generation; failed to resolve source type: {}", ex.getMessage());
            }
        }

        task.setStatus("draft");
        IngestionTask savedTask = taskRepository.save(task);
        if (!skipJob) {
            savedTask = ensureAirflowDag(savedTask);
        }

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

        return taskMapper.toDto(savedTask);
    }

    /**
     * 更新任务
     * 审计信息会自动更新（lastModifiedBy, lastModifiedDate）
     */
    public IngestionTaskDTO update(Long id, IngestionTaskDTO dto) {
        log.info("Updating ingestion task ID: {}", id);

        return taskRepository.findById(id)
            .map(existingTask -> {
                IngestionTask before = snapshot(existingTask);
                taskMapper.partialUpdate(existingTask, dto);
                if (dto.getSyncConfig() != null || !"incremental".equalsIgnoreCase(dto.getSyncMode())) {
                    existingTask.setSyncConfig(dto.getSyncConfig());
                }
                // 如果配置改变，重新生成Addax Job JSON
                boolean sourceChanged = !java.util.Objects.equals(before.getSourceDataSourceId(), existingTask.getSourceDataSourceId());
                boolean configChanged = sourceChanged
                    || !java.util.Objects.equals(before.getSourceConfig(), existingTask.getSourceConfig())
                    || !java.util.Objects.equals(before.getSyncMode(), existingTask.getSyncMode())
                    || !java.util.Objects.equals(before.getSyncConfig(), existingTask.getSyncConfig())
                    || !java.util.Objects.equals(before.getDestinationType(), existingTask.getDestinationType())
                    || !java.util.Objects.equals(before.getDestinationConfig(), existingTask.getDestinationConfig())
                    || !java.util.Objects.equals(before.getTableMapping(), existingTask.getTableMapping())
                    || !java.util.Objects.equals(before.getAddaxConfig(), existingTask.getAddaxConfig());

                if (configChanged) {
                    try {
                        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource source = resolveSource(existingTask, null);
                        AddaxJobService.AddaxJobResult jobResult = source == null
                            ? addaxJobService.createJobFromTask(existingTask)
                            : addaxJobService.createJobFromTask(existingTask, source.readerType(), source.readerConfig());
                        if (source != null && StringUtils.hasText(source.readerType())) {
                            existingTask.setSourceType(source.readerType());
                        }
                        existingTask.setAddaxJobPath(jobResult.jobPath());
                    } catch (Exception e) {
                        log.error("Failed to regenerate Addax job for task: {}", id, e);
                        throw new RuntimeException("Failed to regenerate Addax job", e);
                    }
                }

                IngestionTask updatedTask = taskRepository.save(existingTask);
                updatedTask = ensureAirflowDag(updatedTask);
                log.info("Updated ingestion task ID: {} by user: {}", id, updatedTask.getLastModifiedBy());

                try {
                    changeLogService.recordTaskUpdate(before, updatedTask);
                } catch (Exception ex) {
                    log.warn("Failed to record change log for task update: {}", id, ex);
                }

                return taskMapper.toDto(updatedTask);
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
            .map(taskMapper::toDto);
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
            return taskRepository.findByStatus(status, pageable)
                .map(taskMapper::toDto);
        }
        return taskRepository.findAll(pageable)
            .map(taskMapper::toDto);
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
        return tasks.stream().map(taskMapper::toDto).toList();
    }

    /**
     * 删除任务（硬删除 + 级联清理）
     * 清理执行记录、变更记录、Addax 作业与 DAG 文件
     */
    public IngestionTaskDTO delete(Long id) {
        log.info("Request to delete IngestionTask : {}", id);
        IngestionTask task = taskRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));
        try {
            executionRepository.deleteByTaskId(id);
        } catch (Exception ex) {
            log.warn("Failed to delete executions for task {}: {}", id, ex.getMessage());
        }
        try {
            incrementalSyncService.clearCheckpointByTaskId(id);
        } catch (Exception ex) {
            log.warn("Failed to delete incremental checkpoints for task {}: {}", id, ex.getMessage());
        }
        try {
            changeLogService.deleteByTaskId(id);
        } catch (Exception ex) {
            log.warn("Failed to delete change logs for task {}: {}", id, ex.getMessage());
        }
        try {
            addaxJobService.deleteJobIfExists(task.getAddaxJobPath());
        } catch (Exception ex) {
            log.warn("Failed to delete Addax job for task {}: {}", id, ex.getMessage());
        }
        try {
            airflowDagService.deleteDagForTask(task);
        } catch (Exception ex) {
            log.warn("Failed to delete Airflow DAG for task {}: {}", id, ex.getMessage());
        }
        taskRepository.delete(task);
        log.info("Deleted ingestion task ID: {} by user: {}", id, task.getLastModifiedBy());
        return taskMapper.toDto(task);
    }

    /**
     * 执行任务
     * 创建执行记录并触发Addax任务
     */
    public IngestionExecutionDTO execute(Long taskId) {
        log.info("Executing ingestion task ID: {}", taskId);

        IngestionTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (!"active".equals(task.getStatus()) && !"draft".equals(task.getStatus())) {
            throw new IllegalStateException("Task is not in executable status: " + task.getStatus());
        }
        Optional<IngestionExecution> latestExecution = executionRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId);
        if (latestExecution.isPresent() && "running".equalsIgnoreCase(latestExecution.get().getStatus())) {
            throw new IllegalStateException("任务仍在运行中，请稍后重试");
        }
        // 创建执行记录（先落库，便于前端异步轮询执行进度）
        IngestionExecution execution = new IngestionExecution();
        execution.setTask(task);
        execution.setStatus("preparing");
        execution.setStartTime(Instant.now());
        execution.setReplaceMode(resolveReplaceMode(task));
        execution = executionRepository.save(execution);

        try {
            boolean airflowEnabled = isAirflowEnabled(task);
            com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource source = resolveSource(task, null);
            Map<String, Object> runtimeReaderOverrides = incrementalSyncService.buildReaderRuntimeOverrides(task, source);
            task = ensureAddaxJobExists(task, source, runtimeReaderOverrides);
            execution.setDroppedTables(resolveDroppedTables(task));
            // File sources cannot use ensureTargetTables because dts-ingestion cannot reach the
            // target database directly. CREATE TABLE DDL is injected into the Addax writer preSql
            // instead (see AddaxJobService.injectFileSourceCreateTablePreSql).
            if (!isFileSourceType(task.getSourceType())) {
                targetTableProvisioner.ensureTargetTables(task, source == null ? null : source.readerConfig());
            }
            // Resolve actual column names for PostgreSQL writers to work around Addax 6.0.8 quoteColumn bug
            addaxJobService.resolveWriterColumnsIfNeeded(task.getAddaxJobPath());
            if (airflowEnabled && task.getAirflowEnabled() == null) {
                task.setAirflowEnabled(true);
                task = taskRepository.save(task);
            }
            if (airflowEnabled) {
                boolean forceDagRefresh = task.getLastExecutedAt() == null;
                task = ensureAirflowDag(task, forceDagRefresh);
            }

            // 触发Airflow DAG
            if (airflowEnabled) {
                String airflowJobPath = addaxJobService.toContainerJobPath(task.getAddaxJobPath());
                Map<String, Object> conf = Map.of(
                    "job_path", airflowJobPath,
                    "task_id", taskId,
                    "task_name", task.getName()
                );
                
                Map<String, Object> airflowResult = airflowAdapter.triggerIfRequested(
                    new AirflowAdapter.AirflowRequest(
                        true, 
                        task.getAirflowDagId(), 
                        null, 
                        null, 
                        null
                    ),
                    conf,
                    true // runNow
                );
                log.info("Airflow trigger result for task {}: {}", taskId, airflowResult);
                String status = airflowResult == null ? null : String.valueOf(airflowResult.get("status"));
                if (!"triggered".equalsIgnoreCase(status)) {
                    String message = airflowResult == null ? null : String.valueOf(airflowResult.get("message"));
                    String code = airflowResult == null ? null : String.valueOf(airflowResult.get("code"));
                    String normalizedCode = (code == null || "null".equalsIgnoreCase(code) || code.isBlank()) ? null : code.trim();
                    String normalizedMessage = (message == null || "null".equalsIgnoreCase(message) || message.isBlank())
                        ? null
                        : message.trim();
                    String detail = normalizedMessage;
                    if (normalizedCode != null) {
                        detail = normalizedMessage == null ? "[" + normalizedCode + "]" : "[" + normalizedCode + "] " + normalizedMessage;
                    }
                    throw new IllegalStateException(
                        "Airflow 触发失败" + (detail == null ? "" : (": " + detail))
                    );
                }
                String dagRunId = extractDagRunId(airflowResult);
                if (StringUtils.hasText(dagRunId)) {
                    execution.setExecutionId(dagRunId);
                } else {
                    execution.setExecutionId("airflow-" + java.util.UUID.randomUUID().toString().substring(0, 8));
                }
            } else {
                // 如果未启用Airflow，记录为手动执行
                execution.setExecutionId("manual-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            }

            execution.setStatus("running");
            execution = executionRepository.save(execution);

            // 更新任务的最后执行信息
            task.setLastExecutedAt(Instant.now());
            task.setLastExecutionStatus("running");
            task.setStatus("active");
            taskRepository.save(task);

            log.info("Started execution {} for task ID: {}", execution.getExecutionId(), taskId);

            // 记录审计
            auditService.auditAction(
                "INGESTION_TASK_EXECUTE",
                AuditStage.SUCCESS,
                task.getName(),
                Map.of(
                    "taskId", taskId, 
                    "executionId", execution.getId(),
                    "operator", execution.getTask().getLastModifiedBy()
                )
            );

            return executionMapper.toDto(execution);
        } catch (Exception e) {
            log.error("Failed to execute task ID: {}", taskId, e);

            execution.setStatus("failed");
            execution.setEndTime(Instant.now());
            execution.setErrorMessage(e.getMessage());
            executionRepository.save(execution);

            task.setLastExecutionStatus("failed");
            taskRepository.save(task);

            // 记录审计失败
            auditService.auditAction(
                "INGESTION_TASK_EXECUTE",
                AuditStage.FAIL,
                task.getName(),
                Map.of("taskId", taskId, "error", e.getMessage())
            );

            throw new RuntimeException("Failed to execute task", e);
        }
    }

    @Async("ingestionTaskExecutor")
    public CompletableFuture<Void> executeAsync(Long taskId) {
        try {
            execute(taskId);
        } catch (Exception ex) {
            log.error("Async execution failed for task {}", taskId, ex);
        }
        return CompletableFuture.completedFuture(null);
    }

    public IngestionExecutionDTO retryExecution(Long taskId, Long executionId, String retryMode) {
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
        String previousStatus = toText(execution.getStatus());
        if ("FAILED_ONLY".equals(mode)) {
            boolean canRetry = "failed".equalsIgnoreCase(previousStatus) || "error".equalsIgnoreCase(previousStatus);
            if (!canRetry) {
                throw new IllegalStateException("仅失败执行可使用 FAILED_ONLY 重试模式");
            }
        }
        auditService.auditAction(
            "INGESTION_TASK_RETRY",
            AuditStage.SUCCESS,
            task.getName(),
            Map.of(
                "taskId", taskId,
                "executionId", executionId,
                "retryMode", mode,
                "previousStatus", previousStatus == null ? "unknown" : previousStatus
            )
        );
        return execute(taskId);
    }


    private String resolveReplaceMode(IngestionTask task) {
        if (task == null) {
            return null;
        }
        return "full_refresh".equalsIgnoreCase(task.getSyncMode()) ? "FULL_REPLACE" : "INCREMENTAL_OR_APPEND";
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
        if (task == null) {
            return task;
        }
        if (resolvedSource != null || task.getSourceDataSourceId() != null) {
            return rebuildAddaxJob(task, resolvedSource, runtimeReaderOverrides);
        }
        // File source tasks always rebuild to ensure preSql CREATE TABLE and
        // explicit writer columns are up-to-date with current file metadata.
        if (isFileSourceType(task.getSourceType())) {
            return rebuildAddaxJob(task, null, runtimeReaderOverrides);
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
        return rebuildAddaxJob(task, null, runtimeReaderOverrides);
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
        String operator = resolveOperator(task);
        try {
            com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource source =
                resolveSource(task, resolvedSource);
            AddaxJobService.AddaxJobResult jobResult = source == null
                ? addaxJobService.createJobFromTask(task, null, null, runtimeReaderOverrides)
                : addaxJobService.createJobFromTask(task, source.readerType(), source.readerConfig(), runtimeReaderOverrides);
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
        IngestionTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || !isAirflowEnabled(task) || !StringUtils.hasText(task.getAirflowDagId())) {
            return;
        }
        Map<String, Object> payload = airflowClient.listDagRuns(task.getAirflowDagId(), Math.max(limit, 20)).orElse(null);
        if (payload == null || !(payload.get("dag_runs") instanceof java.util.List<?> dagRuns)) {
            return;
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
            if (executionRepository.findFirstByTaskIdAndExecutionId(taskId, runId).isPresent()) {
                continue;
            }
            IngestionExecution execution = new IngestionExecution();
            execution.setTask(task);
            execution.setExecutionId(runId);
            execution.setStatus(mapAirflowState(toText(run.get("state"))));
            execution.setStartTime(parseAirflowInstant(firstNonBlank(run.get("start_date"), run.get("execution_date"), run.get("logical_date"))));
            execution.setEndTime(parseAirflowInstant(run.get("end_date")));
            if (execution.getStartTime() == null) {
                execution.setStartTime(Instant.now());
            }
            if ("failed".equalsIgnoreCase(execution.getStatus())) {
                String note = toText(firstNonBlank(run.get("note"), run.get("message")));
                if (StringUtils.hasText(note)) {
                    execution.setErrorMessage(note);
                }
            }
            executionRepository.save(execution);
            inserted++;
        }
        if (inserted > 0) {
            log.info("Backfilled {} execution records for task {} from Airflow dag {}", inserted, taskId, task.getAirflowDagId());
        }
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
        log.debug("Request to get executions for task: {}", taskId);
        Page<IngestionExecutionDTO> page = executionRepository.findByTaskId(taskId, pageable).map(executionMapper::toDto);
        if (page.hasContent()) {
            return page;
        }
        backfillExecutionsFromAirflow(taskId, pageable == null ? 20 : pageable.getPageSize());
        return executionRepository.findByTaskId(taskId, pageable).map(executionMapper::toDto);
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
        String failureCategory = null;
        String failureAdvice = null;
        if (StringUtils.hasText(errorMessage)) {
            failureCategory = com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier.classify(errorMessage);
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
        return taskMapper.toDto(task);
    }

    private IngestionTask ensureAirflowDag(IngestionTask task) {
        return ensureAirflowDag(task, false);
    }

    private IngestionTask ensureAirflowDag(IngestionTask task, boolean forceRebuild) {
        if (task == null || !isAirflowEnabled(task)) {
            return task;
        }
        // Split multi-content-block job into per-table files so each Airflow operator
        // runs Addax with a single content block (Addax only processes the first one).
        java.util.List<AddaxJobService.PerTableJob> perTableJobs =
            addaxJobService.splitJobIntoPerTableFiles(task.getAddaxJobPath());
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

    private IngestionTask snapshot(IngestionTask task) {
        if (task == null) {
            return null;
        }
        IngestionTask snap = new IngestionTask();
        snap.setId(task.getId());
        snap.setName(task.getName());
        snap.setDescription(task.getDescription());
        snap.setSourceType(task.getSourceType());
        snap.setSourceConfig(task.getSourceConfig());
        snap.setSourceDataSourceId(task.getSourceDataSourceId());
        snap.setDestinationType(task.getDestinationType());
        snap.setDestinationConfig(task.getDestinationConfig());
        snap.setSyncMode(task.getSyncMode());
        snap.setSyncSchedule(task.getSyncSchedule());
        snap.setTableMapping(task.getTableMapping());
        snap.setSyncConfig(task.getSyncConfig());
        snap.setAddaxConfig(task.getAddaxConfig());
        snap.setAirflowEnabled(task.getAirflowEnabled());
        snap.setAirflowDagId(task.getAirflowDagId());
        snap.setDbtModelSelector(task.getDbtModelSelector());
        snap.setDbtDagSelector(task.getDbtDagSelector());
        return snap;
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
        return "excel".equals(lower) || "csv".equals(lower) || "excelreader".equals(lower) || "txtfilereader".equals(lower);
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

}
