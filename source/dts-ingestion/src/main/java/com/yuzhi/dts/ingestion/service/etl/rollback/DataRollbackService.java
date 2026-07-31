package com.yuzhi.dts.ingestion.service.etl.rollback;

import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.IngestionTaskChangeLogService;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowDagService;
import com.yuzhi.dts.ingestion.service.etl.FileUploadService;
import com.yuzhi.dts.ingestion.service.etl.IncrementalSyncService;
import com.yuzhi.dts.ingestion.service.etl.TableOperationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DataRollbackService {

	private static final Logger LOG = LoggerFactory.getLogger(DataRollbackService.class);

	private final IngestionTaskRepository taskRepository;
	private final IngestionExecutionRepository executionRepository;
	private final TableOperationService tableOpService;
	private final ConfirmationPolicy confirmationPolicy;
	private final RollbackAuditService auditService;
	private final ObjectMapper objectMapper;
	private final FileUploadService fileUploadService;
	private final IncrementalSyncService incrementalSyncService;
	private final IngestionTaskChangeLogService changeLogService;
	private final AddaxJobService addaxJobService;
	private final AirflowDagService airflowDagService;
	private TransactionTemplate rollbackTxTemplate;

	public DataRollbackService(IngestionTaskRepository taskRepository,
							   IngestionExecutionRepository executionRepository,
							   TableOperationService tableOpService,
							   ConfirmationPolicy confirmationPolicy,
							   RollbackAuditService auditService,
							   ObjectMapper objectMapper,
							   FileUploadService fileUploadService,
							   IncrementalSyncService incrementalSyncService,
							   IngestionTaskChangeLogService changeLogService,
							   AddaxJobService addaxJobService,
							   AirflowDagService airflowDagService) {
		this.taskRepository = taskRepository;
		this.executionRepository = executionRepository;
		this.tableOpService = tableOpService;
		this.confirmationPolicy = confirmationPolicy;
		this.auditService = auditService;
		this.objectMapper = objectMapper;
		this.fileUploadService = fileUploadService;
		this.incrementalSyncService = incrementalSyncService;
		this.changeLogService = changeLogService;
		this.addaxJobService = addaxJobService;
		this.airflowDagService = airflowDagService;
	}

	@Autowired
	void setTransactionManager(PlatformTransactionManager transactionManager) {
		this.rollbackTxTemplate = new TransactionTemplate(transactionManager);
	}

	/**
	 * Impact analysis — no destructive operations, returns what WOULD be affected.
	 */
	public RollbackImpact analyze(RollbackRequest request) {
		// 1. Validate request
		RollbackLevel level = RollbackLevel.fromCode(request.level());

		// 2. Resolve tasks based on scope
		List<IngestionTask> tasks = resolveTasks(request);

		// 3. For each task, collect affected resources
		List<String> affectedTables = new ArrayList<>();
		List<String> uploadFiles = new ArrayList<>();
		int totalExecutions = 0;
		List<Long> cascadeTaskIds = new ArrayList<>();
		List<String> warnings = new ArrayList<>();

		for (IngestionTask task : tasks) {
			cascadeTaskIds.add(task.getId());

			// Resolve target tables
			List<String> taskTables = tableOpService.resolveTargetTables(task);
			if (request.tables() != null && !request.tables().isEmpty()
					&& level == RollbackLevel.TRUNCATE_DATA) {
				// Level 1 with specific tables: filter
				taskTables = taskTables.stream()
					.filter(t -> request.tables().contains(t))
					.toList();
			}
			affectedTables.addAll(taskTables);

			// Count executions
			totalExecutions += (int) executionRepository.countByTaskIdAndStatusesIgnoreCase(
				task.getId(), List.of("success", "failed", "running"));

			// Check for Excel/CSV upload files
			if (isFileSourceType(task.getSourceType())) {
				String hostPath = extractUploadPath(task);
				if (hostPath != null) {
					uploadFiles.add(hostPath);
				}
				if (level == RollbackLevel.REBUILD_SCHEMA) {
					warnings.add("任务 " + task.getId() + " 为文件导入类型，DROP 表后需重新上传文件并修正列映射");
				}
				if (level == RollbackLevel.FULL_CASCADE) {
					warnings.add("任务 " + task.getId() + " 的上传文件将被清理");
				}
			}
		}

		String confirmType = confirmationPolicy.confirmationType(level);

		return new RollbackImpact(
			request.level(), request.scope(),
			request.taskId(), request.dataSourceId(),
			affectedTables,
			List.of(),  // ODS mappings - populated by dts-platform
			List.of(),  // models - populated by dts-platform
			List.of(),  // dbt files - populated by dts-platform
			0,          // datasets - populated by dts-platform
			uploadFiles, totalExecutions, confirmType,
			cascadeTaskIds, warnings
		);
	}

	/**
	 * Execute rollback — dispatches to level-specific handler.
	 */
	public RollbackResult execute(RollbackRequest request, String operator) {
		if (request == null || request.dryRun()) {
			throw new IllegalArgumentException(
				"ROLLBACK_EXECUTE_DRY_RUN_FORBIDDEN: dryRun 请求只能用于影响分析"
			);
		}
		RollbackLevel level = RollbackLevel.fromCode(request.level());
		List<IngestionTask> tasks = resolveTasks(request);
		if (tasks.isEmpty()) {
			return RollbackResult.empty("未找到可回退的任务");
		}
		RollbackImpact impact = analyze(request);  // for audit
		RollbackResult result;
		try {
			result = switch (level) {
				case TRUNCATE_DATA -> executeLevel1(request, tasks);
				case REBUILD_SCHEMA -> executeLevel2(request, tasks);
				case FULL_CASCADE -> executeLevel3(request, tasks, operator);
			};
			auditService.record(operator, level, request.scope(), request.taskId(), request.dataSourceId(),
				toJson(request), toJson(impact), toJson(result), result.status(),
				result.errors().isEmpty() ? null : String.join("; ", result.errors()));
		} catch (Exception ex) {
			auditService.record(operator, level, request.scope(), request.taskId(), request.dataSourceId(),
				toJson(request), toJson(impact), null, "FAILED", ex.getMessage());
			throw ex;
		}
		return result;
	}

	/**
	 * Level 1: TRUNCATE target tables (data only, schema preserved).
	 */
	private RollbackResult executeLevel1(RollbackRequest request, List<IngestionTask> tasks) {
		List<String> actions = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		List<Long> taskIds = new ArrayList<>();
		for (IngestionTask task : tasks) {
			taskIds.add(task.getId());
			var connInfo = tableOpService.resolveTargetConnectionInfo(task);
			List<String> tables = tableOpService.resolveTargetTables(task);
			if (request.tables() != null && !request.tables().isEmpty()) {
				tables = tables.stream().filter(t -> request.tables().contains(t)).toList();
			}
			for (String table : tables) {
				try {
					String[] parts = parseSchemaTable(table);
					tableOpService.truncateTable(connInfo, parts[0], parts[1]);
					actions.add("TRUNCATED: " + table);
				} catch (Exception ex) {
					errors.add("TRUNCATE_FAILED: " + table + " - " + ex.getMessage());
				}
			}
		}
		return new RollbackResult(errors.isEmpty(), actions, errors, false, taskIds);
	}

	/**
	 * Level 2: DROP target tables and switch syncMode to full_refresh.
	 */
	private RollbackResult executeLevel2(RollbackRequest request, List<IngestionTask> tasks) {
		List<String> actions = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		List<Long> taskIds = new ArrayList<>();
		boolean dbtNeeded = request.rebuildDbt();
		for (IngestionTask task : tasks) {
			taskIds.add(task.getId());
			var connInfo = tableOpService.resolveTargetConnectionInfo(task);
			List<String> tables = tableOpService.resolveTargetTables(task);
			for (String table : tables) {
				try {
					String[] parts = parseSchemaTable(table);
					tableOpService.dropTable(connInfo, parts[0], parts[1]);
					actions.add("DROPPED: " + table);
				} catch (Exception ex) {
					errors.add("DROP_FAILED: " + table + " - " + ex.getMessage());
				}
			}
			// Switch syncMode to full_refresh so next run recreates tables
			String prevMode = task.getSyncMode();
			if (!"full_refresh".equalsIgnoreCase(prevMode)) {
				task.setSyncMode("full_refresh");
				taskRepository.save(task);
				actions.add("SYNC_MODE_SWITCHED: task " + task.getId() + " " + prevMode + " -> full_refresh");
			}
		}
		return new RollbackResult(errors.isEmpty(), actions, errors, dbtNeeded, taskIds);
	}

	/**
	 * Level 3: Full cascade rollback — DROP tables, clean files, delete all
	 * related records, and soft-delete tasks.
	 */
	private RollbackResult executeLevel3(RollbackRequest request, List<IngestionTask> tasks, String operator) {
		if ("datasource".equals(request.scope())) {
			return executeLevel3DataSource(tasks, operator);
		}
		return executeLevel3Task(tasks.get(0));
	}

	private RollbackResult executeLevel3DataSource(List<IngestionTask> tasks, String operator) {
		List<RollbackResult> subResults = new ArrayList<>();
		for (IngestionTask task : tasks) {
			RollbackResult result = executeLevel3Task(task);
			subResults.add(result);
			if (!result.success()) {
				break;
			}
		}
		return RollbackResult.merge(subResults);
	}

	private RollbackResult executeLevel3Task(IngestionTask task) {
		Long taskId = task.getId();
		List<String> actions = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		// 1. DROP all ODS physical tables
		try {
			var connInfo = tableOpService.resolveTargetConnectionInfo(task);
			for (String table : tableOpService.resolveTargetTables(task)) {
				try {
					String[] parts = parseSchemaTable(table);
					tableOpService.dropTable(connInfo, parts[0], parts[1]);
					actions.add("DROPPED: " + table);
				} catch (Exception ex) {
					errors.add("DROP_FAILED: " + table + " - " + ex.getMessage());
				}
			}
		} catch (Exception ex) {
			errors.add("CONN_FAILED: task " + taskId + " - " + ex.getMessage());
		}
		if (!errors.isEmpty()) {
			return new RollbackResult(false, actions, errors, false, List.of(taskId));
		}

		// 2. Critical local database cleanup. Any failure rolls back these DB steps
		// and stops before files, runtime artifacts, or the task are removed.
		if (!executeCriticalDatabaseCleanup(taskId, actions, errors)) {
			return new RollbackResult(false, actions, errors, false, List.of(taskId));
		}

		// 3. External resources are attempted only after all critical DB steps pass.
		if (isFileSourceType(task.getSourceType())) {
			try {
				List<String> deleted = fileUploadService.cleanupForTask(task);
				for (String f : deleted) actions.add("FILE_DELETED: " + f);
			} catch (Exception ex) {
				errors.add("FILE_CLEANUP_FAILED: " + ex.getMessage());
			}
		}

		// 4. Delete Addax job file
		try {
			boolean deleted = addaxJobService.deleteJobIfExists(task.getAddaxJobPath());
			actions.add((deleted ? "ADDAX_JOB_DELETED: task " : "ADDAX_JOB_NOT_FOUND: task ") + taskId);
		} catch (Exception ex) {
			errors.add("ADDAX_JOB_DELETE_FAILED: " + ex.getMessage());
		}

		// 5. Delete Airflow DAG
		try {
			boolean deleted = airflowDagService.deleteDagForTask(task);
			actions.add((deleted ? "DAG_DELETED: task " : "DAG_NOT_FOUND: task ") + taskId);
		} catch (Exception ex) {
			errors.add("DAG_DELETE_FAILED: " + ex.getMessage());
		}

		// 6. Soft-delete task
		try {
			task.setStatus("deleted");
			taskRepository.save(task);
			actions.add("TASK_DELETED: " + taskId);
		} catch (Exception ex) {
			errors.add("TASK_DELETE_FAILED: " + ex.getMessage());
			return new RollbackResult(false, actions, errors, false, List.of(taskId));
		}

		return new RollbackResult(errors.isEmpty(), actions, errors, false, List.of(taskId));
	}

	private boolean executeCriticalDatabaseCleanup(Long taskId, List<String> actions, List<String> errors) {
			List<String> committedActions = new ArrayList<>();
			try {
				if (rollbackTxTemplate == null) {
					doCriticalDatabaseCleanup(taskId, committedActions);
				} else {
					rollbackTxTemplate.executeWithoutResult(status -> doCriticalDatabaseCleanup(taskId, committedActions));
				}
				actions.addAll(committedActions);
				return true;
			} catch (CriticalDatabaseCleanupException ex) {
				errors.add(ex.getMessage());
				actions.add("LOCAL_DB_ROLLED_BACK: task " + taskId);
				return false;
			}
	}

	private void doCriticalDatabaseCleanup(Long taskId, List<String> actions) {
			try {
				executionRepository.deleteByTaskId(taskId);
				actions.add("EXECUTIONS_DELETED: task " + taskId);
			} catch (Exception ex) {
				throw new CriticalDatabaseCleanupException("EXEC_DELETE_FAILED: " + ex.getMessage(), ex);
			}
			try {
				incrementalSyncService.clearCheckpointByTaskId(taskId);
				actions.add("CHECKPOINTS_DELETED: task " + taskId);
			} catch (Exception ex) {
				throw new CriticalDatabaseCleanupException("CHECKPOINT_DELETE_FAILED: " + ex.getMessage(), ex);
			}
			try {
				changeLogService.deleteByTaskId(taskId);
				actions.add("CHANGELOGS_DELETED: task " + taskId);
			} catch (Exception ex) {
				throw new CriticalDatabaseCleanupException("CHANGELOG_DELETE_FAILED: " + ex.getMessage(), ex);
			}
	}

	private static final class CriticalDatabaseCleanupException extends RuntimeException {
		private CriticalDatabaseCleanupException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private String[] parseSchemaTable(String qualifiedName) {
		if (qualifiedName.contains(".")) {
			String[] parts = qualifiedName.split("\\.", 2);
			return new String[]{parts[0], parts[1]};
		}
		return new String[]{"public", qualifiedName};
	}

	private String toJson(Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (Exception ex) {
			return String.valueOf(obj);
		}
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
		} else if ("datasource".equals(request.scope())) {
			if (request.dataSourceId() == null) {
				throw new IllegalArgumentException("dataSourceId is required for scope=datasource");
			}
			return taskRepository.findBySourceDataSourceId(request.dataSourceId()).stream()
				.filter(t -> !"deleted".equalsIgnoreCase(t.getStatus()))
				.toList();
		}
		throw new IllegalArgumentException("Unknown scope: " + request.scope());
	}

	private boolean isFileSourceType(String sourceType) {
		if (sourceType == null) return false;
		String lower = sourceType.toLowerCase(Locale.ROOT);
		return "excel".equals(lower) || "csv".equals(lower)
			|| "excelreader".equals(lower) || "txtfilereader".equals(lower);
	}

	private String extractUploadPath(IngestionTask task) {
		// 支持新老键名：优先 hostPath，其次 _filePath/filePath/path
		var config = task.getSourceConfig();
		if (config == null) {
			return null;
		}
		String path = config.path("hostPath").asText(null);
		if (path != null && !path.isBlank()) {
			return path;
		}
		path = config.path("_filePath").asText(null);
		if (path != null && !path.isBlank()) {
			return path;
		}
		path = config.path("filePath").asText(null);
		if (path != null && !path.isBlank()) {
			return path;
		}
		path = config.path("path").asText(null);
		if (path != null && !path.isBlank()) {
			return path;
		}
		return null;
	}
}
