package com.yuzhi.dts.ingestion.service;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionSchemaSnapshot;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionSchemaSnapshotRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.etl.AirflowClient;
import com.yuzhi.dts.ingestion.service.etl.AirflowDagService;
import com.yuzhi.dts.ingestion.service.etl.StagingTableService;
import com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class IngestionExecutionQueryService {

    private final IngestionTaskRepository taskRepository;
    private final IngestionExecutionRepository executionRepository;
    private final IngestionSchemaSnapshotRepository schemaSnapshotRepository;
    private final AirflowAdapter airflowAdapter;
    private final AirflowClient airflowClient;
    private final AirflowDagService airflowDagService;
    private final StagingTableService stagingTableService;

    public IngestionExecutionQueryService(
        IngestionTaskRepository taskRepository,
        IngestionExecutionRepository executionRepository,
        IngestionSchemaSnapshotRepository schemaSnapshotRepository,
        AirflowAdapter airflowAdapter,
        AirflowClient airflowClient,
        AirflowDagService airflowDagService,
        StagingTableService stagingTableService
    ) {
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.schemaSnapshotRepository = schemaSnapshotRepository;
        this.airflowAdapter = airflowAdapter;
        this.airflowClient = airflowClient;
        this.airflowDagService = airflowDagService;
        this.stagingTableService = stagingTableService;
    }

    public Map<String, Object> traceExecution(String batchId, String dtsExecutionId) {
        String normalizedBatchId = toText(batchId);
        String normalizedExecutionId = toText(dtsExecutionId);
        if (!StringUtils.hasText(normalizedBatchId) && !StringUtils.hasText(normalizedExecutionId)) {
            throw new IllegalArgumentException("batchId or executionId is required");
        }
        IngestionExecution execution = resolveTraceExecution(normalizedBatchId, normalizedExecutionId);
        IngestionTask task = execution.getTask();
        List<IngestionSchemaSnapshot> snapshots = schemaSnapshotRepository.findByExecution_IdOrderByCreatedDateDesc(execution.getId());
        if (snapshots.isEmpty() && task != null) {
            snapshots = schemaSnapshotRepository.findByTask_IdOrderByCreatedDateDesc(task.getId());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", execution.getBatchId());
        result.put("executionId", execution.getId());
        result.put("airflowRunId", execution.getExecutionId());
        result.put("status", execution.getStatus());
        result.put("startTime", execution.getStartTime());
        result.put("endTime", execution.getEndTime());
        result.put("rowsRead", execution.getRowsRead());
        result.put("rowsWritten", execution.getRowsWritten());
        result.put("logPath", execution.getLogPath());
        if (StringUtils.hasText(execution.getErrorMessage())) {
            result.put("errorMessage", execution.getErrorMessage());
        }
        if (task != null) {
            Map<String, Object> taskPayload = new LinkedHashMap<>();
            taskPayload.put("id", task.getId());
            taskPayload.put("name", task.getName());
            taskPayload.put("sourceType", task.getSourceType());
            taskPayload.put("syncMode", task.getSyncMode());
            taskPayload.put("status", task.getStatus());
            result.put("task", taskPayload);
            addBadRowSummary(result, task);
        }
        result.put("sources", snapshots.stream().map(this::toSnapshotTrace).toList());
        return result;
    }

    private void addBadRowSummary(Map<String, Object> result, IngestionTask task) {
        if (task == null || !StringUtils.hasText(task.getStagingTableName())) {
            return;
        }
        try {
            result.put("badRows", stagingTableService.summarizeErrors(task.getStagingTableName(), 20));
        } catch (Exception ex) {
            result.put("badRows", Map.of(
                "stagingTable", task.getStagingTableName(),
                "message", "坏行摘要暂不可用: " + ex.getMessage()
            ));
        }
    }

    private IngestionExecution resolveTraceExecution(String batchId, String dtsExecutionId) {
        if (StringUtils.hasText(batchId)) {
            return executionRepository.findByBatchId(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found by batchId: " + batchId));
        }
        Long numericId = parseLong(dtsExecutionId);
        if (numericId != null) {
            return executionRepository.findById(numericId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + dtsExecutionId));
        }
        return executionRepository.findByExecutionId(dtsExecutionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + dtsExecutionId));
    }

    private Map<String, Object> toSnapshotTrace(IngestionSchemaSnapshot snapshot) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("snapshotId", snapshot.getId());
        item.put("sourceType", snapshot.getSourceType());
        item.put("sourceSystem", snapshot.getSourceSystem());
        item.put("sourceSchema", snapshot.getSourceSchema());
        item.put("sourceTable", snapshot.getSourceTable());
        item.put("sourceResource", snapshot.getSourceResource());
        item.put("odsSchema", snapshot.getOdsSchema());
        item.put("odsTable", snapshot.getOdsTable());
        item.put("schemaFingerprint", snapshot.getSchemaFingerprint());
        int columnCount = snapshot.getColumnsJson() != null && snapshot.getColumnsJson().isArray()
            ? snapshot.getColumnsJson().size()
            : 0;
        long technicalColumnCount = 0;
        if (snapshot.getColumnsJson() != null && snapshot.getColumnsJson().isArray()) {
            for (var column : snapshot.getColumnsJson()) {
                if (column.has("technical") && column.get("technical").asBoolean(false)) {
                    technicalColumnCount++;
                }
            }
        }
        item.put("columnCount", columnCount);
        item.put("technicalColumnCount", technicalColumnCount);
        item.put("primaryKeyColumns", snapshot.getPrimaryKeyColumns());
        item.put("warnings", snapshot.getWarningsJson());
        return item;
    }

    public Map<String, Object> fetchExecutionLog(Long taskId, Long executionId, Integer tryNumber, String keyword, String scope) {
        IngestionTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        IngestionExecution execution = executionRepository.findById(executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        if (execution.getTask() == null || !taskId.equals(execution.getTask().getId())) {
            throw new IllegalArgumentException("Execution does not belong to task: " + taskId);
        }
        if (!isAirflowEnabled(task)) {
            return Map.of("taskId", taskId, "executionId", executionId, "message", "Airflow 未启用，暂无日志");
        }
        String dagId = StringUtils.hasText(execution.getAirflowDagId())
            ? execution.getAirflowDagId()
            : airflowDagService.resolveDagIdForTask(task);
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
                for (Integer candidateTry : resolveTryCandidates(tryNumber, tryHints.get(candidate))) {
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
                for (Integer candidateTry : resolveTryCandidates(tryNumber, tryHints.get(candidate))) {
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
            failureCategory = ExecutionFailureClassifier.classify(errorMessage);
        }
        if (!StringUtils.hasText(failureAdvice) && StringUtils.hasText(failureCategory)) {
            failureAdvice = ExecutionFailureClassifier.advice(failureCategory);
        }

        Map<String, Object> result = new LinkedHashMap<>();
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

    private boolean isAirflowEnabled(IngestionTask task) {
        if (task == null || !airflowAdapter.isEnabled()) {
            return false;
        }
        Boolean enabled = task.getAirflowEnabled();
        return enabled == null || Boolean.TRUE.equals(enabled);
    }

    private Map<String, Integer> resolveTaskTryHints(String dagId, String dagRunId) {
        Map<String, Integer> hints = new LinkedHashMap<>();
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
            hints.merge(taskId.trim(), tryNumber, Integer::max);
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
        Map<String, String> states = new LinkedHashMap<>();
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
            states.put(taskId.trim(), toText(instance.get("state")));
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
            if (line != null && line.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                filtered.append(line).append("\n");
            }
        }
        return filtered.toString();
    }

    private String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
