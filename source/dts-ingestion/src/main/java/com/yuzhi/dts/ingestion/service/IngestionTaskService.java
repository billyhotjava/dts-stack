import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.mapper.IngestionExecutionMapper;
import com.yuzhi.dts.ingestion.service.mapper.IngestionTaskMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final AuditService auditService;

    public IngestionTaskService(
        IngestionTaskRepository taskRepository,
        IngestionExecutionRepository executionRepository,
        IngestionTaskMapper taskMapper,
        IngestionExecutionMapper executionMapper,
        AddaxJobService addaxJobService,
        AirflowAdapter airflowAdapter,
        AuditService auditService
    ) {
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.taskMapper = taskMapper;
        this.executionMapper = executionMapper;
        this.addaxJobService = addaxJobService;
        this.airflowAdapter = airflowAdapter;
        this.auditService = auditService;
    }

    /**
     * 创建新任务
     * 审计信息会自动填充（createdBy, createdDate）
     */
    public IngestionTaskDTO create(IngestionTaskDTO dto) {
        log.info("Creating new ingestion task: {}", dto.getName());

        IngestionTask task = taskMapper.toEntity(dto);

        // 生成Addax Job JSON
        try {
            AddaxJobService.AddaxJobResult jobResult = addaxJobService.createJobFromTask(task);
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

        // 如果启用Airflow，生成DAG ID
        if (Boolean.TRUE.equals(dto.getAirflowEnabled()) && dto.getAirflowDagId() == null) {
            task.setAirflowDagId("ingestion_" + task.getName().toLowerCase().replaceAll("[^a-z0-9_]", "_"));
        }

        task.setStatus("draft");
        IngestionTask savedTask = taskRepository.save(task);

        log.info("Created ingestion task with ID: {} by user: {}", savedTask.getId(), savedTask.getCreatedBy());

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
                taskMapper.partialUpdate(existingTask, dto);

                // 如果配置改变，重新生成Addax Job JSON
                boolean configChanged = !existingTask.getSourceConfig().equals(dto.getSourceConfig())
                    || !existingTask.getSyncMode().equals(dto.getSyncMode())
                    || (dto.getTableMapping() != null && !existingTask.getTableMapping().equals(dto.getTableMapping()));

                if (configChanged) {
                    try {
                        AddaxJobService.AddaxJobResult jobResult = addaxJobService.createJobFromTask(existingTask);
                        existingTask.setAddaxJobPath(jobResult.jobPath());
                    } catch (Exception e) {
                        log.error("Failed to regenerate Addax job for task: {}", id, e);
                        throw new RuntimeException("Failed to regenerate Addax job", e);
                    }
                }

                IngestionTask updatedTask = taskRepository.save(existingTask);
                log.info("Updated ingestion task ID: {} by user: {}", id, updatedTask.getLastModifiedBy());

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

        // 创建执行记录
        IngestionExecution execution = new IngestionExecution();
        execution.setTask(task);
        execution.setStatus("running");
        execution.setStartTime(Instant.now());

        try {
            // 触发Airflow DAG
            if (Boolean.TRUE.equals(task.getAirflowEnabled())) {
                Map<String, Object> conf = Map.of(
                    "job_path", task.getAddaxJobPath(),
                    "task_id", taskId,
                    "task_name", task.getName()
                );
                
                airflowAdapter.triggerIfRequested(
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
                
                execution.setExecutionId("airflow-" + java.util.UUID.randomUUID().toString().substring(0, 8));
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
}
