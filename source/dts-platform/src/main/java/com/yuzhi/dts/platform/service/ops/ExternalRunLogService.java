package com.yuzhi.dts.platform.service.ops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog;
import com.yuzhi.dts.platform.repository.infra.InfraExternalRunLogRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ExternalRunLogService {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalRunLogService.class);

    public static final String ENTRY_INGESTION = "INGESTION_TASK";
    public static final String ENTRY_DBT = "DBT_RUN";
    public static final String ENTRY_AIRFLOW = "AIRFLOW_DAG";

    private final InfraExternalRunLogRepository repository;
    private final ObjectMapper objectMapper;

    public ExternalRunLogService(InfraExternalRunLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Optional<InfraExternalRunLog> recordIngestionExecution(Object payload, String ownerDept) {
        if (!(payload instanceof Map<?, ?> raw)) {
            return Optional.empty();
        }
        String externalRunId = text(raw.get("executionId"));
        if (!StringUtils.hasText(externalRunId)) {
            return Optional.empty();
        }
        String status = normalizeStatus(text(raw.get("status")));
        Instant startTime = parseInstant(raw.get("startTime"));
        Instant endTime = parseInstant(raw.get("endTime"));
        String taskName = text(raw.get("taskName"));
        String message = text(raw.get("errorMessage"));
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("taskId", raw.get("taskId"));
        metrics.put("taskName", taskName);
        metrics.put("logPath", raw.get("logPath"));
        UUID artifactId = resolveIngestionArtifactId(raw.get("taskId"));
        return Optional.of(
            upsertExternalRun(
                ENTRY_INGESTION,
                "INGESTION_TASK",
                taskName,
                externalRunId,
                status,
                startTime,
                endTime,
                message,
                toJson(metrics),
                ownerDept,
                artifactId
            )
        );
    }

    public int syncIngestionExecutions(Object payload, String ownerDept) {
        if (payload == null) {
            return 0;
        }
        Object target = payload;
        if (payload instanceof Map<?, ?> map && map.containsKey("content")) {
            target = map.get("content");
        }
        if (target instanceof java.util.List<?> list) {
            int count = 0;
            for (Object item : list) {
                if (recordIngestionExecution(item, ownerDept).isPresent()) {
                    count++;
                }
            }
            return count;
        }
        return recordIngestionExecution(target, ownerDept).isPresent() ? 1 : 0;
    }

    public Optional<InfraExternalRunLog> recordAirflowRun(
        String entryKey,
        String dagId,
        Map<String, Object> triggerResult,
        Map<String, Object> conf,
        String ownerDept
    ) {
        if (!StringUtils.hasText(dagId)) {
            return Optional.empty();
        }
        String externalRunId = text(triggerResult == null ? null : triggerResult.get("dag_run_id"));
        if (!StringUtils.hasText(externalRunId)) {
            externalRunId = text(triggerResult == null ? null : triggerResult.get("run_id"));
        }
        if (!StringUtils.hasText(externalRunId)) {
            externalRunId = dagId + ":" + Instant.now().toString();
        }
        String status = normalizeStatus(text(triggerResult == null ? null : triggerResult.get("state")));
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("dagId", dagId);
        if (conf != null && !conf.isEmpty()) {
            metrics.put("conf", conf);
        }
        if (triggerResult != null && !triggerResult.isEmpty()) {
            metrics.put("result", triggerResult);
        }
        return Optional.of(
            upsertExternalRun(
                entryKey,
                "AIRFLOW_DAG",
                dagId,
                externalRunId,
                status,
                Instant.now(),
                null,
                null,
                toJson(metrics),
                ownerDept,
                null
            )
        );
    }

    public int syncAirflowRuns(String entryKey, String dagId, Object payload, String ownerDept) {
        if (!(payload instanceof Map<?, ?> map)) {
            return 0;
        }
        Object runs = map.get("dag_runs");
        if (!(runs instanceof java.util.List<?> list)) {
            return 0;
        }
        int updated = 0;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> run)) {
                continue;
            }
            String externalRunId = text(run.get("dag_run_id"));
            if (!StringUtils.hasText(externalRunId)) {
                externalRunId = text(run.get("run_id"));
            }
            if (!StringUtils.hasText(externalRunId)) {
                continue;
            }
            String status = normalizeStatus(text(run.get("state")));
            Instant startedAt = parseInstant(run.get("start_date"));
            Instant endAt = parseInstant(run.get("end_date"));
            if (startedAt == null) {
                startedAt = parseInstant(run.get("execution_date"));
            }
            if (startedAt == null) {
                startedAt = parseInstant(run.get("logical_date"));
            }
            Map<String, Object> existingMetrics = repository
                .findFirstByEntryKeyIgnoreCaseAndExternalRunId(entryKey, externalRunId)
                .map(log -> parseJsonMap(log.getMetricsJson()))
                .orElse(Map.of());
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("dagId", dagId);
            Object existingConf = existingMetrics.get("conf");
            if (existingConf != null) {
                metrics.put("conf", existingConf);
            }
            metrics.put("run", run);
            upsertExternalRun(
                entryKey,
                "AIRFLOW_DAG",
                dagId,
                externalRunId,
                status,
                startedAt,
                endAt,
                null,
                toJson(metrics),
                ownerDept,
                null
            );
            updated++;
        }
        return updated;
    }

    public InfraExternalRunLog upsertExternalRun(
        String entryKey,
        String artifactType,
        String artifactName,
        String externalRunId,
        String status,
        Instant startedAt,
        Instant finishedAt,
        String message,
        String metricsJson,
        String ownerDept
    ) {
        return upsertExternalRun(
            entryKey,
            artifactType,
            artifactName,
            externalRunId,
            status,
            startedAt,
            finishedAt,
            message,
            metricsJson,
            ownerDept,
            null
        );
    }

    public InfraExternalRunLog upsertExternalRun(
        String entryKey,
        String artifactType,
        String artifactName,
        String externalRunId,
        String status,
        Instant startedAt,
        Instant finishedAt,
        String message,
        String metricsJson,
        String ownerDept,
        UUID artifactId
    ) {
        InfraExternalRunLog log = repository
            .findFirstByEntryKeyIgnoreCaseAndExternalRunId(entryKey, externalRunId)
            .orElseGet(InfraExternalRunLog::new);
        log.setEntryKey(entryKey);
        log.setArtifactType(artifactType);
        log.setArtifactName(artifactName);
        if (artifactId != null) {
            log.setArtifactId(artifactId);
        }
        log.setExternalRunId(externalRunId);
        if (StringUtils.hasText(status)) {
            log.setStatus(status.toUpperCase(Locale.ROOT));
        } else if (!StringUtils.hasText(log.getStatus())) {
            log.setStatus("SUBMITTED");
        }
        if (startedAt != null) {
            log.setStartedAt(startedAt);
        } else if (log.getStartedAt() == null) {
            log.setStartedAt(Instant.now());
        }
        if (finishedAt != null) {
            log.setFinishedAt(finishedAt);
        }
        if (StringUtils.hasText(message)) {
            log.setMessage(message.length() > 1024 ? message.substring(0, 1024) : message);
        }
        if (StringUtils.hasText(metricsJson)) {
            log.setMetricsJson(metricsJson);
        }
        if (StringUtils.hasText(ownerDept)) {
            log.setOwnerDept(ownerDept);
        }
        log.setClassification(SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code());
        log.setDurationMs(computeDuration(log.getStartedAt(), log.getFinishedAt(), log.getDurationMs()));
        return repository.save(log);
    }

    public int deleteIngestionRuns(String taskName, Long taskId) {
        int removed = 0;
        UUID artifactId = resolveIngestionArtifactId(taskId);
        if (artifactId != null) {
            removed += repository.deleteByEntryKeyIgnoreCaseAndArtifactId(ENTRY_INGESTION, artifactId);
        }
        if (StringUtils.hasText(taskName)) {
            removed += repository.deleteByEntryKeyIgnoreCaseAndArtifactNameIgnoreCase(ENTRY_INGESTION, taskName);
        }
        if (removed > 0) {
            LOG.info("Deleted {} ingestion run logs for task {}", removed, taskName);
        }
        return removed;
    }

    public Optional<DbtBuildEvidenceSnapshot> findLatestDbtBuildEvidence() {
        return findRecentDbtBuildEvidence(1).stream().findFirst();
    }

    public List<DbtBuildEvidenceSnapshot> findRecentDbtBuildEvidence(int limit) {
        int safeLimit = Math.max(1, limit);
        List<DbtBuildEvidenceSnapshot> evidence = new java.util.ArrayList<>();
        List<InfraExternalRunLog> runs = repository.search(ENTRY_DBT, null, null, null, true);
        for (InfraExternalRunLog run : runs) {
            if (run == null) {
                continue;
            }
            Map<String, Object> metrics = parseJsonMap(run.getMetricsJson());
            Map<String, Object> conf = asMap(metrics.get("conf"));
            String operation = normalizeOperation(text(conf.get("operation")));
            if (!isBuildOperation(operation)) {
                continue;
            }
            String models = text(conf.get("models"));
            String target = text(conf.get("target"));
            String command = buildDbtCommand(operation, models, target);
            Instant generatedAt = run.getFinishedAt() != null ? run.getFinishedAt() : run.getStartedAt();
            evidence.add(
                new DbtBuildEvidenceSnapshot(
                    run.getExternalRunId(),
                    normalizeStatus(run.getStatus()),
                    run.getStartedAt(),
                    generatedAt,
                    command,
                    estimateSelectorCount(models),
                    "external-run-log"
                )
            );
            if (evidence.size() >= safeLimit) {
                break;
            }
        }
        return List.copyOf(evidence);
    }

    private UUID resolveIngestionArtifactId(Object taskId) {
        if (taskId == null) {
            return null;
        }
        String value = String.valueOf(taskId).trim();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return UUID.nameUUIDFromBytes(("ingestion-task:" + value).getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("SUCCESS".equals(normalized) || "SUCCEEDED".equals(normalized) || "FINISHED".equals(normalized)) {
            return "SUCCESS";
        }
        if ("FAILED".equals(normalized) || "FAIL".equals(normalized) || "ERROR".equals(normalized)) {
            return "FAILED";
        }
        if ("RUNNING".equals(normalized) || "QUEUED".equals(normalized) || "SUBMITTED".equals(normalized)) {
            return "RUNNING";
        }
        return normalized;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private String normalizeOperation(String operation) {
        if (!StringUtils.hasText(operation)) {
            return null;
        }
        return operation.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBuildOperation(String operation) {
        return "compile".equals(operation) || "test".equals(operation) || "build".equals(operation);
    }

    private String buildDbtCommand(String operation, String models, String target) {
        if (!StringUtils.hasText(operation)) {
            return null;
        }
        StringBuilder command = new StringBuilder("dbt ").append(operation.trim().toLowerCase(Locale.ROOT));
        if (StringUtils.hasText(models) && !"all".equalsIgnoreCase(models.trim())) {
            command.append(" --select ").append(models.trim());
        }
        if (StringUtils.hasText(target)) {
            command.append(" --target ").append(target.trim());
        }
        return command.toString();
    }

    private int estimateSelectorCount(String models) {
        if (!StringUtils.hasText(models)) {
            return 1;
        }
        String[] parts = models.trim().split("[,\\s]+");
        int count = 0;
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private Instant parseInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Instant.parse(text.trim());
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    private Long computeDuration(Instant start, Instant end, Long fallback) {
        if (start != null && end != null) {
            return Math.max(0L, end.toEpochMilli() - start.toEpochMilli());
        }
        return fallback;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            LOG.warn("Failed to serialize metrics json: {}", ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return Map.of();
        } catch (Exception ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    public record DbtBuildEvidenceSnapshot(
        String externalRunId,
        String status,
        Instant startedAt,
        Instant generatedAt,
        String command,
        int selectedCount,
        String source
    ) {}
}
