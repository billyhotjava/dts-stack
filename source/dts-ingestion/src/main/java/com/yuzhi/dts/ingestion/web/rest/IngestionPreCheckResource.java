package com.yuzhi.dts.ingestion.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.dto.CellError;
import com.yuzhi.dts.ingestion.service.dto.ColumnInfo;
import com.yuzhi.dts.ingestion.service.dto.ParseResult;
import com.yuzhi.dts.ingestion.service.etl.BuiltInRuleChecker;
import com.yuzhi.dts.ingestion.service.etl.CsvParseService;
import com.yuzhi.dts.ingestion.service.etl.ExcelParseService;
import com.yuzhi.dts.ingestion.service.etl.StagingTableService;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for Excel pre-check flow on ingestion tasks.
 * Handles parsing, staging, built-in rule checking, and submission.
 */
@RestController
@RequestMapping("/api/ingestion/tasks")
public class IngestionPreCheckResource {

    private static final Logger log = LoggerFactory.getLogger(IngestionPreCheckResource.class);

    private final IngestionTaskRepository taskRepository;
    private final ExcelParseService excelParseService;
    private final CsvParseService csvParseService;
    private final StagingTableService stagingTableService;
    private final BuiltInRuleChecker builtInRuleChecker;
    private final PlatformInfraClient platformInfraClient;

    public IngestionPreCheckResource(
        IngestionTaskRepository taskRepository,
        ExcelParseService excelParseService,
        CsvParseService csvParseService,
        StagingTableService stagingTableService,
        BuiltInRuleChecker builtInRuleChecker,
        PlatformInfraClient platformInfraClient
    ) {
        this.taskRepository = taskRepository;
        this.excelParseService = excelParseService;
        this.csvParseService = csvParseService;
        this.stagingTableService = stagingTableService;
        this.builtInRuleChecker = builtInRuleChecker;
        this.platformInfraClient = platformInfraClient;
    }

    // ----- DTOs for request / response -----

    public record ParseResponse(
        int totalRows,
        List<ColumnInfo> columns,
        String stagingTableName,
        int builtInErrorCount
    ) {}

    public record UpdateCellRequest(String column, String value) {}

    public record PreCheckResponse(
        String status,
        int totalRules,
        int passedRules,
        int failedRules,
        List<String> failedRuleNames
    ) {}

    // ----- Endpoints -----

    /**
     * POST /api/ingestion/tasks/{id}/parse
     * Parse the uploaded Excel file, create a staging table, run built-in checks.
     */
    @PostMapping("/{id}/parse")
    @Transactional
    public ResponseEntity<ParseResponse> parse(@PathVariable Long id) {
        IngestionTask task = findTaskOrThrow(id);

        // Get uploaded file path from sourceConfig._filePath
        String filePath = extractFilePath(task);
        if (filePath == null || filePath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No uploaded file found for task " + id);
        }

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file not found: " + filePath);
        }

        try {
            ParseResult parseResult;
            try (InputStream is = Files.newInputStream(path)) {
                parseResult = isCsvTask(task, path) ? csvParseService.parse(is) : excelParseService.parse(is);
            }

            if (task.getStagingTableName() != null && !task.getStagingTableName().isBlank()) {
                stagingTableService.drop(task.getStagingTableName());
            }
            UUID taskUuid = UUID.nameUUIDFromBytes(("ingestion-task-" + id).getBytes());
            String tableName = stagingTableService.create(taskUuid, id, parseResult.columns());
            stagingTableService.bulkInsert(tableName, parseResult.columns(), parseResult.rows());

            // Run built-in rule checks and write errors to staging table
            Map<Integer, List<CellError>> errors = builtInRuleChecker.check(tableName, parseResult.columns());
            int errorCount = 0;
            for (Map.Entry<Integer, List<CellError>> entry : errors.entrySet()) {
                stagingTableService.updateErrors(tableName, entry.getKey(), entry.getValue());
                errorCount += entry.getValue().size();
            }

            // Update task
            task.setStagingTableName(tableName);
            task.setPreCheckStatus("PENDING");
            taskRepository.save(task);

            return ResponseEntity.ok(new ParseResponse(
                parseResult.totalRows(),
                parseResult.columns(),
                tableName,
                errorCount
            ));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse file for task {}", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse file: " + e.getMessage());
        }
    }

    /**
     * POST /api/ingestion/tasks/{id}/pre-check
     * Run governance quality rules on the staging table data by calling
     * dts-platform's governance pre-check API via internal REST.
     */
    @PostMapping("/{id}/pre-check")
    @Transactional
    public ResponseEntity<PreCheckResponse> preCheck(@PathVariable Long id) {
        IngestionTask task = findTaskOrThrow(id);

        String tableName = task.getStagingTableName();
        if (tableName == null || tableName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No staging table found. Run /parse first.");
        }

        task.setPreCheckStatus("CHECKING");
        taskRepository.save(task);

        try {
            UUID datasetId = extractPreCheckDatasetId(task);
            if (datasetId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No target dataset configured for pre-check");
            }
            // Count rows via staging table query
            int totalRows = countStagingRows(tableName);

            Map<String, Object> result = platformInfraClient.preCheckStagingData(
                tableName, datasetId, totalRows);

            int passedRows = toInt(result.get("passedRows"));
            int failedRows = toInt(result.get("failedRows"));
            int total = toInt(result.get("totalRows"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errorsByRule =
                result.get("errorsByRule") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();

            List<String> failedRuleNames = errorsByRule.stream()
                .map(r -> r.get("ruleName"))
                .filter(n -> n != null)
                .map(String::valueOf)
                .toList();

            String status = failedRows > 0 ? "FAILED" : "PASSED";
            task.setPreCheckStatus(status);
            taskRepository.save(task);

            return ResponseEntity.ok(new PreCheckResponse(
                status,
                errorsByRule.size(),
                errorsByRule.size() - failedRuleNames.size() + passedRows, // approximate passed rules
                failedRuleNames.size(),
                failedRuleNames
            ));
        } catch (ResponseStatusException e) {
            task.setPreCheckStatus("FAILED");
            taskRepository.save(task);
            throw e;
        } catch (Exception e) {
            log.error("Pre-check failed for task {} via platform API: {}", id, e.getMessage());
            task.setPreCheckStatus("FAILED");
            taskRepository.save(task);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Quality pre-check service unavailable: " + e.getMessage());
        }
    }

    /**
     * PUT /api/ingestion/tasks/{id}/staging/{rowNum}
     * Update a cell in the staging table.
     */
    @PutMapping("/{id}/staging/{rowNum}")
    public ResponseEntity<Map<String, Object>> updateCell(
        @PathVariable Long id,
        @PathVariable int rowNum,
        @RequestBody UpdateCellRequest request
    ) {
        IngestionTask task = findTaskOrThrow(id);

        String tableName = task.getStagingTableName();
        if (tableName == null || tableName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No staging table found. Run /parse first.");
        }

        stagingTableService.updateCell(tableName, rowNum, request.column(), request.value());

        return ResponseEntity.ok(Map.of(
            "rowNum", rowNum,
            "column", request.column(),
            "value", request.value()
        ));
    }

    /**
     * GET /api/ingestion/tasks/{id}/staging
     * Query parsed staging rows. Use errorsOnly=true to list bad rows.
     */
    @GetMapping("/{id}/staging")
    public ResponseEntity<Page<Map<String, Object>>> getStagingRows(
        @PathVariable Long id,
        @RequestParam(value = "errorsOnly", defaultValue = "false") boolean errorsOnly,
        Pageable pageable
    ) {
        IngestionTask task = findTaskOrThrow(id);
        String tableName = requireStagingTable(task);
        return ResponseEntity.ok(stagingTableService.query(tableName, errorsOnly, pageable));
    }

    /**
     * GET /api/ingestion/tasks/{id}/staging/errors/summary
     * Return bad-row counts and samples for the execution/detail page.
     */
    @GetMapping("/{id}/staging/errors/summary")
    public ResponseEntity<StagingTableService.StagingErrorSummary> getBadRowSummary(
        @PathVariable Long id,
        @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        IngestionTask task = findTaskOrThrow(id);
        String tableName = requireStagingTable(task);
        return ResponseEntity.ok(stagingTableService.summarizeErrors(tableName, limit));
    }

    /**
     * GET /api/ingestion/tasks/{id}/staging/errors/download
     * Download current bad rows as CSV for offline fix and re-import.
     */
    @GetMapping(value = "/{id}/staging/errors/download", produces = "text/csv")
    public ResponseEntity<byte[]> downloadBadRows(@PathVariable Long id) {
        IngestionTask task = findTaskOrThrow(id);
        String tableName = requireStagingTable(task);
        byte[] csv = stagingTableService.exportErrorRowsCsv(tableName);
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bad_rows_task_" + id + ".csv\"")
            .body(csv);
    }

    /**
     * POST /api/ingestion/tasks/{id}/re-check
     * Re-run pre-check on edited staging data (same flow as pre-check).
     * After user fixes cells via PUT /staging/{rowNum}, this re-validates
     * the staging table against governance quality rules.
     */
    @PostMapping("/{id}/re-check")
    @Transactional
    public ResponseEntity<PreCheckResponse> reCheck(@PathVariable Long id) {
        return preCheck(id);
    }

    /**
     * POST /api/ingestion/tasks/{id}/submit
     * Verify all rows are clean, transfer data from staging to target ODS table,
     * then clean up the staging table.
     */
    @PostMapping("/{id}/submit")
    @Transactional
    public ResponseEntity<Map<String, Object>> submit(@PathVariable Long id) {
        IngestionTask task = findTaskOrThrow(id);

        String tableName = task.getStagingTableName();
        if (tableName == null || tableName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂存表不存在");
        }

        if (!stagingTableService.allClean(tableName)) {
            long errorCount = stagingTableService.countErrors(tableName);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "还有 " + errorCount + " 个错误待修复");
        }

        // Transfer data from staging table to target ODS table
        try {
            int rows = stagingTableService.transferToTarget(tableName, task);
            log.info("Task {}: transferred {} rows from staging to target", id, rows);
        } catch (Exception e) {
            log.error("Task {}: failed to transfer staging data to target table", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "数据提交入湖失败: " + e.getMessage());
        }

        // Clean up staging table after successful transfer
        stagingTableService.drop(tableName);
        task.setStagingTableName(null);
        task.setPreCheckStatus("SUBMITTED");
        taskRepository.save(task);

        return ResponseEntity.ok(Map.of(
            "taskId", id,
            "status", "submitted",
            "message", "数据已成功提交入湖"
        ));
    }

    /**
     * DELETE /api/ingestion/tasks/{id}/staging
     * Drop the staging table and clear pre-check state.
     */
    @DeleteMapping("/{id}/staging")
    @Transactional
    public ResponseEntity<Void> dropStaging(@PathVariable Long id) {
        IngestionTask task = findTaskOrThrow(id);

        String tableName = task.getStagingTableName();
        if (tableName != null && !tableName.isBlank()) {
            stagingTableService.drop(tableName);
        }

        task.setStagingTableName(null);
        task.setPreCheckStatus(null);
        taskRepository.save(task);

        return ResponseEntity.noContent().build();
    }

    // ----- Helpers -----

    private IngestionTask findTaskOrThrow(Long id) {
        return taskRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: " + id));
    }

    private boolean isCsvTask(IngestionTask task, Path path) {
        if (task.getSourceType() != null) {
            String lower = task.getSourceType().toLowerCase(java.util.Locale.ROOT);
            if ("csv".equals(lower) || "txtfilereader".equals(lower)) {
                return true;
            }
        }
        if (task.getSourceConfig() != null) {
            String fileType = task.getSourceConfig().path("_fileType").asText(null);
            if ("csv".equalsIgnoreCase(fileType)) {
                return true;
            }
        }
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return fileName.endsWith(".csv");
    }

    private String requireStagingTable(IngestionTask task) {
        String tableName = task.getStagingTableName();
        if (tableName == null || tableName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂存表不存在，请先执行文件解析");
        }
        return tableName;
    }

    private int countStagingRows(String tableName) {
        return stagingTableService.countRows(tableName);
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number num) return num.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractFilePath(IngestionTask task) {
        JsonNode sourceConfig = task.getSourceConfig();
        if (sourceConfig == null) {
            return null;
        }
        // Try _filePath first (standard key for file uploads), then filePath
        JsonNode filePathNode = sourceConfig.get("_filePath");
        if (filePathNode == null) {
            filePathNode = sourceConfig.get("filePath");
        }
        if (filePathNode == null) {
            filePathNode = sourceConfig.get("path");
        }
        return filePathNode != null ? filePathNode.asText() : null;
    }

    private UUID extractPreCheckDatasetId(IngestionTask task) {
        JsonNode sourceConfig = task.getSourceConfig();
        if (sourceConfig != null) {
            JsonNode datasetNode = sourceConfig.get("_datasetId");
            if (datasetNode == null) {
                datasetNode = sourceConfig.get("datasetId");
            }
            if (datasetNode != null && !datasetNode.asText("").isBlank()) {
                try {
                    return UUID.fromString(datasetNode.asText());
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid target dataset id for pre-check");
                }
            }
        }
        return task.getSourceDataSourceId();
    }
}
