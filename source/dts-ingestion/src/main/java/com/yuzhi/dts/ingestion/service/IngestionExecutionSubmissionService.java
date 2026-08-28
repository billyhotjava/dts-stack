package com.yuzhi.dts.ingestion.service;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import com.yuzhi.dts.ingestion.service.mapper.IngestionExecutionMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Persistent idempotency facade for browser-issued execution commands. */
@Service
public class IngestionExecutionSubmissionService {

    private final IngestionExecutionRepository executionRepository;
    private final IngestionExecutionMapper executionMapper;
    private final IngestionTaskService taskService;

    public IngestionExecutionSubmissionService(
        IngestionExecutionRepository executionRepository,
        IngestionExecutionMapper executionMapper,
        IngestionTaskService taskService
    ) {
        this.executionRepository = executionRepository;
        this.executionMapper = executionMapper;
        this.taskService = taskService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public IngestionExecutionDTO submit(Long taskId, String idempotencyKey) {
        return submitCommandInternal(taskId, idempotencyKey).execution();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SubmissionResult submitCommand(Long taskId, String idempotencyKey) {
        return submitCommandInternal(taskId, idempotencyKey);
    }

    private SubmissionResult submitCommandInternal(Long taskId, String idempotencyKey) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task id is required");
        }
        String batchId = commandBatchId(taskId, idempotencyKey);
        Optional<IngestionExecution> existing = executionRepository.findByBatchIdWithTask(batchId);
        if (existing.isPresent()) {
            return new SubmissionResult(owned(taskId, existing.orElseThrow()), true);
        }
        try {
            return new SubmissionResult(taskService.executeWithBatchId(taskId, batchId), false);
        } catch (DataIntegrityViolationException duplicate) {
            return executionRepository.findByBatchIdWithTask(batchId)
                .map(execution -> new SubmissionResult(owned(taskId, execution), true))
                .orElseThrow(() -> duplicate);
        } catch (IllegalStateException serializedConflict) {
            // Task-level concurrency is checked under a row lock. A duplicate
            // request can therefore observe the committed winner as "already
            // running" before reaching the unique index; return that winner.
            return executionRepository.findByBatchIdWithTask(batchId)
                .map(execution -> new SubmissionResult(owned(taskId, execution), true))
                .orElseThrow(() -> serializedConflict);
        }
    }

    public record SubmissionResult(IngestionExecutionDTO execution, boolean replayed) {}

    private IngestionExecutionDTO owned(Long taskId, IngestionExecution execution) {
        if (execution.getTask() == null || !taskId.equals(execution.getTask().getId())) {
            throw new IllegalStateException("IDEMPOTENCY_KEY_CONFLICT");
        }
        return executionMapper.toDto(execution);
    }

    private String commandBatchId(Long taskId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.trim().length() > 256) {
            throw new IllegalArgumentException("IDEMPOTENCY_KEY_INVALID");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(idempotencyKey.trim().getBytes(StandardCharsets.UTF_8));
            return "idem-task-" + taskId + "-" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
