package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionRealtimeStatus;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionRealtimeStatusRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.dto.IngestionRealtimeStatusDTO;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class RealtimeTaskStatusService {

    private final IngestionRealtimeStatusRepository realtimeStatusRepository;
    private final IngestionTaskRepository taskRepository;
    private final IngestionExecutionRepository executionRepository;

    public RealtimeTaskStatusService(
        IngestionRealtimeStatusRepository realtimeStatusRepository,
        IngestionTaskRepository taskRepository,
        IngestionExecutionRepository executionRepository
    ) {
        this.realtimeStatusRepository = realtimeStatusRepository;
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
    }

    @Transactional(readOnly = true)
    public IngestionRealtimeStatusDTO getTaskStatus(Long taskId) {
        IngestionTask task = taskRepository
            .findById(taskId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));

        Optional<IngestionRealtimeStatus> statusOptional = realtimeStatusRepository.findByTaskId(taskId);
        if (statusOptional.isPresent()) {
            return toDto(statusOptional.get());
        }
        return buildFallback(task);
    }

    private IngestionRealtimeStatusDTO buildFallback(IngestionTask task) {
        Optional<IngestionExecution> latestExecution = executionRepository.findFirstByTaskIdOrderByCreatedAtDesc(task.getId());
        String status = "IDLE";
        Instant updatedAt = task.getLastModifiedDate();
        if (latestExecution.isPresent()) {
            IngestionExecution execution = latestExecution.get();
            String raw = normalize(execution.getStatus());
            if ("running".equals(raw) || "queued".equals(raw) || "preparing".equals(raw)) {
                status = "RUNNING";
            } else if ("failed".equals(raw) || "error".equals(raw)) {
                status = "ERROR";
            }
            updatedAt = execution.getEndTime() != null ? execution.getEndTime() : execution.getCreatedAt();
        }
        return new IngestionRealtimeStatusDTO(
            task.getId(),
            resolveConnectorType(task),
            status,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            updatedAt
        );
    }

    private IngestionRealtimeStatusDTO toDto(IngestionRealtimeStatus entity) {
        return new IngestionRealtimeStatusDTO(
            entity.getTaskId(),
            entity.getConnectorType(),
            entity.getStatus(),
            entity.getTopicName(),
            entity.getConsumerGroup(),
            entity.getCheckpointToken(),
            entity.getLagMs(),
            entity.getThroughputRps(),
            entity.getBacklogCount(),
            entity.getLastHeartbeat(),
            entity.getUpdatedAt()
        );
    }

    private String resolveConnectorType(IngestionTask task) {
        String sourceType = normalize(task.getSourceType());
        if (!StringUtils.hasText(sourceType)) {
            return "addax";
        }
        if (sourceType.contains("file") || sourceType.contains("excel") || sourceType.contains("csv")) {
            return "file";
        }
        return "addax";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }
}
