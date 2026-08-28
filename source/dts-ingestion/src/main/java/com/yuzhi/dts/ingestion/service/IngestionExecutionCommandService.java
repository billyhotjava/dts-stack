package com.yuzhi.dts.ingestion.service;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import com.yuzhi.dts.ingestion.service.etl.AirflowClient;
import com.yuzhi.dts.ingestion.service.mapper.IngestionExecutionMapper;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Task-scoped execution detail and cancellation commands. */
@Service
@Transactional
public class IngestionExecutionCommandService {

    private final IngestionExecutionRepository executionRepository;
    private final IngestionExecutionMapper executionMapper;
    private final AirflowClient airflowClient;

    public IngestionExecutionCommandService(
        IngestionExecutionRepository executionRepository,
        IngestionExecutionMapper executionMapper,
        AirflowClient airflowClient
    ) {
        this.executionRepository = executionRepository;
        this.executionMapper = executionMapper;
        this.airflowClient = airflowClient;
    }

    @Transactional(readOnly = true)
    public IngestionExecutionDTO get(Long taskId, Long executionId) {
        return executionMapper.toDto(requireOwnedExecution(taskId, executionId, false));
    }

    public IngestionExecutionDTO cancel(Long taskId, Long executionId) {
        IngestionExecution execution = requireOwnedExecution(taskId, executionId, true);
        String status = normalize(execution.getStatus());
        if ("cancelled".equals(status)) {
            return executionMapper.toDto(execution);
        }
        if ("preparing".equals(status) || "pending".equals(status)) {
            execution.setStatus("cancelled");
            execution.setEndTime(Instant.now());
            execution.setErrorMessage(null);
            return executionMapper.toDto(executionRepository.save(execution));
        }
        if (!"running".equals(status) && !"queued".equals(status) && !"cancel_requested".equals(status)) {
            throw new IllegalStateException("TASK_EXECUTION_NOT_CANCELLABLE");
        }
        if (!StringUtils.hasText(execution.getAirflowDagId()) || !StringUtils.hasText(execution.getExecutionId())) {
            throw new IllegalStateException("TASK_EXECUTION_RUNTIME_ID_MISSING");
        }

        execution.setStatus("cancel_requested");
        executionRepository.save(execution);
        airflowClient.setDagRunStateStrict(
            execution.getAirflowDagId(),
            execution.getExecutionId(),
            "failed"
        );
        execution.setStatus("cancelled");
        execution.setEndTime(Instant.now());
        execution.setErrorMessage(null);
        execution.setFailureCategory(null);
        execution.setFailureAdvice(null);
        return executionMapper.toDto(executionRepository.save(execution));
    }

    private IngestionExecution requireOwnedExecution(Long taskId, Long executionId, boolean lock) {
        if (taskId == null || executionId == null) {
            throw new IllegalArgumentException("执行实例不存在");
        }
        IngestionExecution execution = (lock
            ? executionRepository.findByIdForUpdate(executionId)
            : executionRepository.findById(executionId))
            .orElseThrow(() -> new IllegalArgumentException("执行实例不存在"));
        if (execution.getTask() == null || !taskId.equals(execution.getTask().getId())) {
            throw new IllegalArgumentException("执行实例不存在");
        }
        return execution;
    }

    private String normalize(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }
}
