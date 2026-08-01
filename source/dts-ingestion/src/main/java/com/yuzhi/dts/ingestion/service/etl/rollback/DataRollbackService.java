package com.yuzhi.dts.ingestion.service.etl.rollback;

import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowDagService;
import com.yuzhi.dts.ingestion.service.etl.FileUploadService;
import com.yuzhi.dts.ingestion.service.etl.TableOperationService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class DataRollbackService {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-fA-F]{64}");

    private final IngestionTaskRepository taskRepository;
    private final IngestionExecutionRepository executionRepository;
    private final TableOperationService tableOpService;
    private final ConfirmationPolicy confirmationPolicy;
    private final FileUploadService fileUploadService;
    private final AddaxJobService addaxJobService;
    private final AirflowDagService airflowDagService;
    private final RollbackSagaService sagaService;

    public DataRollbackService(
        IngestionTaskRepository taskRepository,
        IngestionExecutionRepository executionRepository,
        TableOperationService tableOpService,
        ConfirmationPolicy confirmationPolicy,
        FileUploadService fileUploadService,
        AddaxJobService addaxJobService,
        AirflowDagService airflowDagService,
        RollbackSagaService sagaService
    ) {
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.tableOpService = tableOpService;
        this.confirmationPolicy = confirmationPolicy;
        this.fileUploadService = fileUploadService;
        this.addaxJobService = addaxJobService;
        this.airflowDagService = airflowDagService;
        this.sagaService = sagaService;
    }

    public RollbackImpact analyze(RollbackRequest request) {
        validateRequestShape(request, true);
        RollbackLevel level = RollbackLevel.fromCode(request.level());
        return analyzeResolved(request, level, resolveTasks(request));
    }

    public RollbackResult execute(RollbackRequest request, String operator) {
        validateRequestShape(request, false);
        RollbackLevel level = RollbackLevel.fromCode(request.level());
        validatePreparedFence(request, List.of());
        List<IngestionTask> tasks;
        String resolutionFailure = null;
        try {
            tasks = resolveTasks(request);
            validatePreparedFence(request, tasks);
        } catch (IllegalArgumentException ex) {
            tasks = List.of();
            resolutionFailure = ex.getMessage();
        }
        RollbackImpact impact = analyzeResolved(request, level, tasks);
        List<RollbackAffectedObjectEvidence> planned = plannedObjects(request, level, tasks);
        RollbackSagaService.BeginDecision decision = sagaService.begin(request, impact, operator, planned);
        if (!decision.execute()) {
            return decision.replay();
        }

        try {
            PhysicalOutcome physical;
            if (resolutionFailure != null || tasks.isEmpty()) {
                String error = resolutionFailure == null ? "ROLLBACK_TARGETS_NOT_FOUND" : resolutionFailure;
                physical = PhysicalOutcome.failure(List.of(error), List.of());
            } else if (level == RollbackLevel.TRUNCATE_DATA) {
                physical = executeLevelOne(request, tasks);
            } else if (level == RollbackLevel.REBUILD_SCHEMA) {
                physical = executeLevelTwo(tasks);
            } else {
                physical = executeLevelThree(tasks);
            }
            return sagaService.complete(request, impact, operator, physical.result(), physical.evidence());
        } catch (RuntimeException failure) {
            sagaService.markUnexpectedFailure(request, impact, operator, failure);
            throw failure;
        }
    }

    private RollbackImpact analyzeResolved(
        RollbackRequest request,
        RollbackLevel level,
        List<IngestionTask> tasks
    ) {
        List<String> affectedTables = new ArrayList<>();
        List<String> uploadFiles = new ArrayList<>();
        int totalExecutions = 0;
        List<Long> affectedTaskIds = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (IngestionTask task : tasks) {
            affectedTaskIds.add(task.getId());
            List<String> taskTables = tableOpService.resolveTargetTables(task);
            if (!request.tables().isEmpty() && level == RollbackLevel.TRUNCATE_DATA) {
                taskTables = selectedTables(request, taskTables);
            }
            affectedTables.addAll(taskTables);
            totalExecutions += Math.toIntExact(
                executionRepository.countByTaskIdAndStatusesIgnoreCase(
                    task.getId(),
                    List.of("success", "failed", "running")
                )
            );
            if (isFileSourceType(task.getSourceType())) {
                String path = extractUploadPath(task);
                if (path != null) {
                    uploadFiles.add(path);
                }
                if (level == RollbackLevel.REBUILD_SCHEMA) {
                    warnings.add("任务 " + task.getId() + " 需要重新落地文件并完成源结构校验后才能恢复可用");
                }
                if (level == RollbackLevel.FULL_CASCADE) {
                    warnings.add("任务 " + task.getId() + " 的托管上传文件和运行制品将被清理，历史执行证据保留");
                }
            }
        }

        return new RollbackImpact(
            request.level(),
            request.scope(),
            request.taskId(),
            request.dataSourceId(),
            List.copyOf(affectedTables),
            List.copyOf(uploadFiles),
            totalExecutions,
            confirmationPolicy.confirmationType(level),
            List.copyOf(affectedTaskIds),
            List.copyOf(warnings)
        );
    }

    private PhysicalOutcome executeLevelOne(RollbackRequest request, List<IngestionTask> tasks) {
        List<String> actions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<Long> taskIds = new ArrayList<>();
        List<RollbackAffectedObjectEvidence> evidence = new ArrayList<>();
        for (IngestionTask task : tasks) {
            taskIds.add(task.getId());
            var connection = tableOpService.resolveTargetConnectionInfo(task);
            List<String> tables = selectedTables(request, tableOpService.resolveTargetTables(task));
            for (String table : tables) {
                try {
                    String[] parts = parseSchemaTable(table);
                    tableOpService.truncateTable(connection, parts[0], parts[1]);
                    actions.add("TRUNCATED: " + table);
                    evidence.add(applied("TABLE", table, "TRUNCATE"));
                } catch (Exception ex) {
                    String error = "TRUNCATE_FAILED: " + table + " - " + ex.getMessage();
                    errors.add(error);
                    evidence.add(failed("TABLE", table, "TRUNCATE", error));
                    return new PhysicalOutcome(result(false, actions, errors, taskIds), evidence);
                }
            }
        }
        return new PhysicalOutcome(result(!actions.isEmpty() && errors.isEmpty(), actions, errors, taskIds), evidence);
    }

    private PhysicalOutcome executeLevelTwo(List<IngestionTask> tasks) {
        List<String> actions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<RollbackAffectedObjectEvidence> evidence = new ArrayList<>();
        List<Long> taskIds = tasks.stream().map(IngestionTask::getId).toList();
        for (IngestionTask task : tasks) {
            var connection = tableOpService.resolveTargetConnectionInfo(task);
            for (String table : tableOpService.resolveTargetTables(task)) {
                try {
                    String[] parts = parseSchemaTable(table);
                    tableOpService.dropTable(connection, parts[0], parts[1]);
                    actions.add("DROPPED: " + table);
                    evidence.add(applied("TABLE", table, "DROP"));
                } catch (Exception ex) {
                    String error = "DROP_FAILED: " + table + " - " + ex.getMessage();
                    errors.add(error);
                    evidence.add(failed("TABLE", table, "DROP", error));
                    return new PhysicalOutcome(result(false, actions, errors, taskIds), evidence);
                }
            }
        }
        return new PhysicalOutcome(result(!actions.isEmpty(), actions, errors, taskIds), evidence);
    }

    private PhysicalOutcome executeLevelThree(List<IngestionTask> tasks) {
        RollbackResult validation = validateManagedUploadReferences(tasks);
        if (validation != null) {
            return new PhysicalOutcome(validation, List.of());
        }
        List<String> actions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<RollbackAffectedObjectEvidence> evidence = new ArrayList<>();
        List<Long> taskIds = tasks.stream().map(IngestionTask::getId).toList();

        for (IngestionTask task : tasks) {
            var connection = tableOpService.resolveTargetConnectionInfo(task);
            for (String table : tableOpService.resolveTargetTables(task)) {
                try {
                    String[] parts = parseSchemaTable(table);
                    tableOpService.dropTable(connection, parts[0], parts[1]);
                    actions.add("DROPPED: " + table);
                    evidence.add(applied("TABLE", table, "DROP"));
                } catch (Exception ex) {
                    String error = "DROP_FAILED: " + table + " - " + ex.getMessage();
                    errors.add(error);
                    evidence.add(failed("TABLE", table, "DROP", error));
                    return new PhysicalOutcome(result(false, actions, errors, taskIds), evidence);
                }
            }

            if (isFileSourceType(task.getSourceType())) {
                try {
                    List<String> deleted = fileUploadService.cleanupForTask(task);
                    deleted.forEach(path -> {
                        actions.add("FILE_DELETED: " + path);
                        evidence.add(applied("MANAGED_FILE", path, "DELETE"));
                    });
                } catch (Exception ex) {
                    String ref = managedFileId(task);
                    String error = "FILE_CLEANUP_FAILED: " + ex.getMessage();
                    errors.add(error);
                    evidence.add(failed("MANAGED_FILE", ref, "DELETE", error));
                    return new PhysicalOutcome(result(false, actions, errors, taskIds), evidence);
                }
            }

            try {
                boolean deleted = addaxJobService.deleteJobIfExists(task.getAddaxJobPath());
                String ref = task.getAddaxJobPath() == null ? "task:" + task.getId() : task.getAddaxJobPath();
                actions.add((deleted ? "ADDAX_JOB_DELETED: " : "ADDAX_JOB_NOT_FOUND: ") + ref);
                evidence.add(new RollbackAffectedObjectEvidence(
                    "ADDAX_JOB",
                    ref,
                    "DELETE",
                    deleted ? "APPLIED" : "NOT_FOUND",
                    Map.of("deleted", deleted)
                ));
            } catch (Exception ex) {
                String ref = task.getAddaxJobPath() == null ? "task:" + task.getId() : task.getAddaxJobPath();
                String error = "ADDAX_JOB_DELETE_FAILED: " + ex.getMessage();
                errors.add(error);
                evidence.add(failed("ADDAX_JOB", ref, "DELETE", error));
                return new PhysicalOutcome(result(false, actions, errors, taskIds), evidence);
            }

            try {
                boolean deleted = airflowDagService.deleteDagForTask(task);
                String ref = task.getAirflowDagId() == null ? "task:" + task.getId() : task.getAirflowDagId();
                actions.add((deleted ? "DAG_DELETED: " : "DAG_NOT_FOUND: ") + ref);
                evidence.add(new RollbackAffectedObjectEvidence(
                    "AIRFLOW_DAG",
                    ref,
                    "DELETE",
                    deleted ? "APPLIED" : "NOT_FOUND",
                    Map.of("deleted", deleted)
                ));
            } catch (Exception ex) {
                String ref = task.getAirflowDagId() == null ? "task:" + task.getId() : task.getAirflowDagId();
                String error = "DAG_DELETE_FAILED: " + ex.getMessage();
                errors.add(error);
                evidence.add(failed("AIRFLOW_DAG", ref, "DELETE", error));
                return new PhysicalOutcome(result(false, actions, errors, taskIds), evidence);
            }
        }
        return new PhysicalOutcome(result(errors.isEmpty(), actions, errors, taskIds), evidence);
    }

    private List<RollbackAffectedObjectEvidence> plannedObjects(
        RollbackRequest request,
        RollbackLevel level,
        List<IngestionTask> tasks
    ) {
        List<RollbackAffectedObjectEvidence> planned = new ArrayList<>();
        for (IngestionTask task : tasks) {
            List<String> tables = level == RollbackLevel.TRUNCATE_DATA
                ? selectedTables(request, tableOpService.resolveTargetTables(task))
                : tableOpService.resolveTargetTables(task);
            for (String table : tables) {
                planned.add(
                    new RollbackAffectedObjectEvidence(
                        "TABLE",
                        table,
                        level == RollbackLevel.TRUNCATE_DATA ? "TRUNCATE" : "DROP",
                        "PLANNED",
                        Map.of("taskId", task.getId())
                    )
                );
            }
            planned.add(
                RollbackAffectedObjectEvidence.planned(
                    "INGESTION_TASK",
                    String.valueOf(task.getId()),
                    level == RollbackLevel.TRUNCATE_DATA ? "REVALIDATE" : "STATE_CHANGE"
                )
            );
            if (level == RollbackLevel.FULL_CASCADE) {
                if (isFileSourceType(task.getSourceType())) {
                    planned.add(RollbackAffectedObjectEvidence.planned("MANAGED_FILE", managedFileId(task), "DELETE"));
                }
                String addaxRef = task.getAddaxJobPath() == null ? "task:" + task.getId() : task.getAddaxJobPath();
                String dagRef = task.getAirflowDagId() == null ? "task:" + task.getId() : task.getAirflowDagId();
                planned.add(RollbackAffectedObjectEvidence.planned("ADDAX_JOB", addaxRef, "DELETE"));
                planned.add(RollbackAffectedObjectEvidence.planned("AIRFLOW_DAG", dagRef, "DELETE"));
            }
        }
        return List.copyOf(planned);
    }

    private List<String> selectedTables(RollbackRequest request, List<String> availableTables) {
        List<String> available = availableTables == null ? List.of() : List.copyOf(availableTables);
        if (request.tables().isEmpty()) {
            return available;
        }
        java.util.Set<String> owned = new java.util.LinkedHashSet<>(available);
        List<String> unknown = request.tables().stream().filter(table -> !owned.contains(table)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("ROLLBACK_TABLE_NOT_OWNED: " + unknown);
        }
        return available.stream().filter(request.tables()::contains).toList();
    }

    private void validateRequestShape(RollbackRequest request, boolean analysis) {
        if (request == null) {
            throw new IllegalArgumentException("rollback request is required");
        }
        if (analysis != request.dryRun()) {
            throw new IllegalArgumentException(
                analysis
                    ? "ROLLBACK_ANALYZE_DRY_RUN_REQUIRED"
                    : "ROLLBACK_EXECUTE_DRY_RUN_FORBIDDEN: dryRun 请求只能用于影响分析"
            );
        }
        if (!request.isScopeTargetValid()) {
            throw new IllegalArgumentException("ROLLBACK_SCOPE_TARGET_INVALID");
        }
        if (request.tables().size() > 500) {
            throw new IllegalArgumentException("ROLLBACK_TABLE_LIMIT_EXCEEDED");
        }
        if (
            request.tables().stream().anyMatch(
                table -> table == null || table.isBlank() || table.length() > 512
            )
        ) {
            throw new IllegalArgumentException("ROLLBACK_TABLE_NAME_INVALID");
        }
        if (request.level() != RollbackLevel.TRUNCATE_DATA.code() && !request.tables().isEmpty()) {
            throw new IllegalArgumentException("ROLLBACK_TABLE_SELECTION_LEVEL_INVALID");
        }
    }

    private RollbackResult validateManagedUploadReferences(List<IngestionTask> tasks) {
        List<Long> taskIds = tasks.stream().map(IngestionTask::getId).toList();
        for (IngestionTask task : tasks) {
            if (isFileSourceType(task.getSourceType()) && !managedFileId(task).matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")) {
                return result(
                    false,
                    List.of(),
                    List.of("MANAGED_FILE_ID_REQUIRED_FOR_DELETE: task " + task.getId()),
                    taskIds
                );
            }
        }
        return null;
    }

    private void validatePreparedFence(RollbackRequest request, List<IngestionTask> tasks) {
        RollbackRequest.AvailabilityFence fence = request.availabilityFence();
        if (request.rollbackId() == null || fence == null || !request.rollbackId().equals(fence.receiptId())) {
            throw new IllegalArgumentException("ROLLBACK_PREPARED_FENCE_REQUIRED");
        }
        String expectedKey = "platform:" + request.rollbackId();
        if (!expectedKey.equals(request.idempotencyKey()) || expectedKey.length() > 128) {
            throw new IllegalArgumentException("ROLLBACK_IDEMPOTENCY_KEY_INVALID");
        }
        if (request.requestHash() == null || !SHA_256.matcher(request.requestHash()).matches()) {
            throw new IllegalArgumentException("ROLLBACK_REQUEST_HASH_INVALID");
        }
        if (!"PREPARED".equals(fence.state()) || fence.sourceDataSourceId() == null || fence.sourceSequence() <= 0 || fence.targetCount() <= 0) {
            throw new IllegalArgumentException("ROLLBACK_PREPARED_FENCE_INVALID");
        }
        if ("datasource".equals(request.scope()) && !fence.sourceDataSourceId().equals(request.dataSourceId())) {
            throw new IllegalArgumentException("ROLLBACK_FENCE_SOURCE_MISMATCH");
        }
        for (IngestionTask task : tasks) {
            if (!fence.sourceDataSourceId().equals(task.getSourceDataSourceId())) {
                throw new IllegalArgumentException("ROLLBACK_FENCE_TASK_SOURCE_MISMATCH: " + task.getId());
            }
        }
    }

    private RollbackResult result(
        boolean success,
        List<String> actions,
        List<String> errors,
        List<Long> taskIds
    ) {
        boolean applied = actions.stream().anyMatch(this::isAppliedAction);
        return new RollbackResult(
            success,
            actions,
            errors,
            taskIds,
            applied,
            applied ? (errors.isEmpty() ? "APPLIED" : "PARTIAL") : "NONE",
            null,
            null,
            null,
            false,
            false
        );
    }

    private boolean isAppliedAction(String action) {
        return action.startsWith("TRUNCATED:")
            || action.startsWith("DROPPED:")
            || action.startsWith("FILE_DELETED:")
            || action.startsWith("ADDAX_JOB_DELETED:")
            || action.startsWith("DAG_DELETED:");
    }

    private RollbackAffectedObjectEvidence applied(String type, String ref, String action) {
        return new RollbackAffectedObjectEvidence(type, ref, action, "APPLIED", Map.of("applied", true));
    }

    private RollbackAffectedObjectEvidence failed(String type, String ref, String action, String error) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("applied", false);
        details.put("error", error);
        return new RollbackAffectedObjectEvidence(type, ref, action, "FAILED", details);
    }

    private String[] parseSchemaTable(String qualifiedName) {
        if (qualifiedName.contains(".")) {
            return qualifiedName.split("\\.", 2);
        }
        return new String[] { "public", qualifiedName };
    }

    private List<IngestionTask> resolveTasks(RollbackRequest request) {
        if ("task".equals(request.scope())) {
            if (request.taskId() == null) {
                throw new IllegalArgumentException("taskId is required for scope=task");
            }
            IngestionTask task = taskRepository.findById(request.taskId())
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + request.taskId()));
            if ("deleted".equalsIgnoreCase(task.getStatus())) {
                throw new IllegalArgumentException("任务已删除: " + request.taskId());
            }
            return List.of(task);
        }
        if ("datasource".equals(request.scope())) {
            if (request.dataSourceId() == null) {
                throw new IllegalArgumentException("dataSourceId is required for scope=datasource");
            }
            return taskRepository.findBySourceDataSourceId(request.dataSourceId()).stream()
                .filter(task -> !"deleted".equalsIgnoreCase(task.getStatus()))
                .toList();
        }
        throw new IllegalArgumentException("Unknown scope: " + request.scope());
    }

    private boolean isFileSourceType(String sourceType) {
        if (sourceType == null) {
            return false;
        }
        String normalized = sourceType.toLowerCase(Locale.ROOT);
        return List.of("excel", "csv", "txt", "text", "tsv", "excelreader", "txtfilereader").contains(normalized);
    }

    private String managedFileId(IngestionTask task) {
        var config = task.getSourceConfig();
        return config == null ? "" : config.path("_fileId").asText("");
    }

    private String extractUploadPath(IngestionTask task) {
        var config = task.getSourceConfig();
        if (config == null) {
            return null;
        }
        for (String key : List.of("hostPath", "_filePath", "filePath", "path")) {
            String path = config.path(key).asText(null);
            if (path != null && !path.isBlank()) {
                return path;
            }
        }
        return null;
    }

    private record PhysicalOutcome(
        RollbackResult result,
        List<RollbackAffectedObjectEvidence> evidence
    ) {
        private PhysicalOutcome {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }

        private static PhysicalOutcome failure(List<String> errors, List<Long> taskIds) {
            return new PhysicalOutcome(
                new RollbackResult(false, List.of(), errors, taskIds),
                List.of()
            );
        }
    }
}
