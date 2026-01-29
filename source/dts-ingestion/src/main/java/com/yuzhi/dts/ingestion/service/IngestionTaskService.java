package com.yuzhi.dts.ingestion.service;

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
import com.yuzhi.dts.ingestion.service.mapper.IngestionExecutionMapper;
import com.yuzhi.dts.ingestion.service.mapper.IngestionTaskMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
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
    private final AirflowDagService airflowDagService;
    private final com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver sourceResolver;
    private final com.yuzhi.dts.ingestion.service.etl.TargetTableProvisioner targetTableProvisioner;
    private final AuditService auditService;
    private final IngestionTaskChangeLogService changeLogService;

    public IngestionTaskService(
        IngestionTaskRepository taskRepository,
        IngestionExecutionRepository executionRepository,
        IngestionTaskMapper taskMapper,
        IngestionExecutionMapper executionMapper,
        AddaxJobService addaxJobService,
        AirflowAdapter airflowAdapter,
        AirflowDagService airflowDagService,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver sourceResolver,
        com.yuzhi.dts.ingestion.service.etl.TargetTableProvisioner targetTableProvisioner,
        AuditService auditService,
        IngestionTaskChangeLogService changeLogService
    ) {
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.taskMapper = taskMapper;
        this.executionMapper = executionMapper;
        this.addaxJobService = addaxJobService;
        this.airflowAdapter = airflowAdapter;
        this.airflowDagService = airflowDagService;
        this.sourceResolver = sourceResolver;
        this.targetTableProvisioner = targetTableProvisioner;
        this.auditService = auditService;
        this.changeLogService = changeLogService;
    }

    /**
     * 创建新任务
     * 审计信息会自动填充（createdBy, createdDate）
     */
    public IngestionTaskDTO create(IngestionTaskDTO dto) {
        return create(dto, null);
    }

    public IngestionTaskDTO create(IngestionTaskDTO dto, com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolvedSource) {
        log.info("Creating new ingestion task: {}", dto.getName());

        IngestionTask task = taskMapper.toEntity(dto);

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

        task.setStatus("draft");
        IngestionTask savedTask = taskRepository.save(task);
        savedTask = ensureAirflowDag(savedTask);

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
                // 如果配置改变，重新生成Addax Job JSON
                boolean sourceChanged = !java.util.Objects.equals(before.getSourceDataSourceId(), existingTask.getSourceDataSourceId());
                boolean configChanged = sourceChanged
                    || !java.util.Objects.equals(before.getSourceConfig(), existingTask.getSourceConfig())
                    || !java.util.Objects.equals(before.getSyncMode(), existingTask.getSyncMode())
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
     * 删除任务（软删除）
     * 审计信息会自动更新
     */
    public void delete(Long id) {
        log.info("Request to delete IngestionTask : {}", id);
        taskRepository.findById(id).ifPresent(task -> {
            task.setStatus("deleted");
            taskRepository.save(task);
            log.info("Deleted (soft) ingestion task ID: {} by user: {}", id, task.getLastModifiedBy());
        });
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
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource source = resolveSource(task, null);
        task = ensureAddaxJobExists(task, source);
        targetTableProvisioner.ensureTargetTables(task, source == null ? null : source.readerConfig());
        task = ensureAirflowDag(task);

        // 创建执行记录
        IngestionExecution execution = new IngestionExecution();
        execution.setTask(task);
        execution.setStatus("running");
        execution.setStartTime(Instant.now());

        try {
            // 触发Airflow DAG
            if (Boolean.TRUE.equals(task.getAirflowEnabled())) {
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
                String status = airflowResult == null ? null : String.valueOf(airflowResult.get("status"));
                if (!"triggered".equalsIgnoreCase(status)) {
                    String message = airflowResult == null ? null : String.valueOf(airflowResult.get("message"));
                    throw new IllegalStateException(
                        "Airflow 触发失败" + (message == null || "null".equals(message) ? "" : (": " + message))
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

    private IngestionTask ensureAddaxJobExists(IngestionTask task) {
        return ensureAddaxJobExists(task, null);
    }

    private IngestionTask ensureAddaxJobExists(
        IngestionTask task,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolvedSource
    ) {
        if (task == null) {
            return task;
        }
        if (resolvedSource != null || task.getSourceDataSourceId() != null) {
            return rebuildAddaxJob(task, resolvedSource);
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
        return rebuildAddaxJob(task, null);
    }

    private IngestionTask rebuildAddaxJob(
        IngestionTask task,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolvedSource
    ) {
        String operator = resolveOperator(task);
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
    @Transactional(readOnly = true)
    public Page<IngestionExecutionDTO> getExecutions(Long taskId, Pageable pageable) {
        log.debug("Request to get executions for task: {}", taskId);
        return executionRepository.findByTaskId(taskId, pageable)
            .map(executionMapper::toDto);
    }

    /**
     * 获取最新的执行记录
     */
    @Transactional(readOnly = true)
    public Optional<IngestionExecutionDTO> getLatestExecution(Long taskId) {
        return executionRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId)
            .map(executionMapper::toDto);
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
        if (task == null || !Boolean.TRUE.equals(task.getAirflowEnabled())) {
            return task;
        }
        String dagId = airflowDagService.ensureDagForTask(task);
        if (StringUtils.hasText(dagId) && !dagId.equals(task.getAirflowDagId())) {
            task.setAirflowDagId(dagId);
            return taskRepository.save(task);
        }
        return task;
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
        return sourceResolver.resolve(task.getSourceDataSourceId(), List.of());
    }
}
