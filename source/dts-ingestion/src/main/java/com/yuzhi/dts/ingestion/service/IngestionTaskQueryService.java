package com.yuzhi.dts.ingestion.service;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionObservabilityDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionGovernanceOverviewDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalAuditDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalAuditSummaryDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalStateDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier;
import com.yuzhi.dts.ingestion.service.etl.IncrementalSyncService;
import com.yuzhi.dts.ingestion.service.mapper.IngestionTaskMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class IngestionTaskQueryService {

    private static final Logger log = LoggerFactory.getLogger(IngestionTaskQueryService.class);
    private static final ZoneId OBSERVABILITY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter OBSERVABILITY_DAY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId GOVERNANCE_DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private final IngestionTaskRepository taskRepository;
    private final IngestionExecutionRepository executionRepository;
    private final IngestionTaskMapper taskMapper;
    private final IncrementalSyncService incrementalSyncService;

    public IngestionTaskQueryService(
        IngestionTaskRepository taskRepository,
        IngestionExecutionRepository executionRepository,
        IngestionTaskMapper taskMapper,
        IncrementalSyncService incrementalSyncService
    ) {
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.taskMapper = taskMapper;
        this.incrementalSyncService = incrementalSyncService;
    }

    public Optional<IngestionTaskDTO> findOne(Long id) {
        log.debug("Request to get IngestionTask : {}", id);
        return taskRepository.findById(id).map(taskMapper::toDto);
    }

    public Page<IngestionTaskDTO> findAll(String status, Pageable pageable) {
        log.debug("Request to get all IngestionTasks with status: {}", status);
        if (StringUtils.hasText(status)) {
            return taskRepository.findByStatus(status, pageable).map(taskMapper::toDto);
        }
        return taskRepository.findAll(pageable).map(taskMapper::toDto);
    }

    public List<IngestionTaskDTO> findBySourceDataSourceId(UUID sourceDataSourceId, boolean includeDeleted) {
        if (sourceDataSourceId == null) {
            return List.of();
        }
        List<IngestionTask> tasks = taskRepository.findBySourceDataSourceId(sourceDataSourceId);
        if (!includeDeleted) {
            tasks = tasks.stream().filter(task -> !"deleted".equalsIgnoreCase(task.getStatus())).toList();
        }
        return tasks.stream().map(taskMapper::toDto).toList();
    }

    public List<IngestionIncrementalStateDTO> getIncrementalStates(Long taskId) {
        validateTaskExists(taskId);
        return incrementalSyncService.listCheckpointStates(taskId);
    }

    public List<IngestionIncrementalAuditDTO> getIncrementalAudits(Long taskId, Long executionId) {
        validateTaskExists(taskId);
        return incrementalSyncService.listCheckpointAudits(taskId, executionId);
    }

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
        validateTaskExists(taskId);
        return incrementalSyncService.listCheckpointAuditsPage(taskId, executionId, executionIds, from, to, pageable, tableName, status);
    }

    public IngestionIncrementalAuditSummaryDTO getIncrementalAuditsSummary(
        Long taskId,
        Long executionId,
        Collection<Long> executionIds,
        Instant from,
        Instant to,
        String tableName,
        String status
    ) {
        validateTaskExists(taskId);
        return incrementalSyncService.summarizeCheckpointAudits(taskId, executionId, executionIds, from, to, tableName, status);
    }

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
                String normalizedCategory = StringUtils.hasText(category)
                    ? category.toUpperCase()
                    : ExecutionFailureClassifier.CATEGORY_RUNTIME;
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

        dto.setFailureTop(
            failureMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(entry -> new IngestionExecutionObservabilityDTO.FailureTopItem(entry.getKey(), entry.getValue()))
                .toList()
        );
        dto.setTrend(
            trendMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new IngestionExecutionObservabilityDTO.TrendItem(
                    entry.getKey(),
                    entry.getValue().total,
                    entry.getValue().success,
                    entry.getValue().failed,
                    entry.getValue().timeout
                ))
                .toList()
        );
        return dto;
    }

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
            String sourceKey = (task.getSourceDataSourceId() == null ? "none" : task.getSourceDataSourceId().toString())
                + "|"
                + toText(task.getSourceType());
            SourceLoadAccumulator sourceAcc = sourceLoads.computeIfAbsent(
                sourceKey,
                ignored -> new SourceLoadAccumulator(task.getSourceDataSourceId(), toText(task.getSourceType()))
            );
            String projectKey = resolveProjectKey(task);
            ProjectLoadAccumulator projectAcc = projectLoads.computeIfAbsent(
                StringUtils.hasText(projectKey) ? projectKey : "default",
                ignored -> new ProjectLoadAccumulator(StringUtils.hasText(projectKey) ? projectKey : "default")
            );
            if ("running".equalsIgnoreCase(toText(execution.getStatus()))) {
                sourceAcc.running += 1;
                projectAcc.running += 1;
            } else if ("preparing".equalsIgnoreCase(toText(execution.getStatus()))) {
                sourceAcc.preparing += 1;
                projectAcc.preparing += 1;
            }
        }

        dto.setSourceLoads(
            sourceLoads.values().stream()
                .sorted((a, b) -> Long.compare((b.running + b.preparing), (a.running + a.preparing)))
                .map(acc -> new IngestionGovernanceOverviewDTO.SourceLoadItem(
                    acc.sourceDataSourceId,
                    acc.sourceType,
                    acc.running,
                    acc.preparing
                ))
                .toList()
        );
        dto.setProjectLoads(
            projectLoads.values().stream()
                .sorted((a, b) -> Long.compare((b.running + b.preparing), (a.running + a.preparing)))
                .map(acc -> new IngestionGovernanceOverviewDTO.ProjectLoadItem(acc.projectKey, acc.running, acc.preparing))
                .toList()
        );
        return dto;
    }

    private void validateTaskExists(Long taskId) {
        if (taskId == null || !taskRepository.existsById(taskId)) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
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

    private String resolveProjectKey(IngestionTask task) {
        if (task == null) {
            return "default";
        }
        var syncConfig = task.getSyncConfig();
        var governance = syncConfig == null ? null : syncConfig.path("governance");
        if (governance != null && !governance.isMissingNode() && !governance.isNull()) {
            String configured = toText(governance.path("projectKey").asText(null));
            if (StringUtils.hasText(configured)) {
                return configured;
            }
        }
        String dagSelector = toText(task.getDbtDagSelector());
        if (StringUtils.hasText(dagSelector)) {
            return dagSelector;
        }
        return "default";
    }

    private String toText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static final class TrendAccumulator {
        private long total;
        private long success;
        private long failed;
        private long timeout;
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
}
