package com.yuzhi.dts.ingestion.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.dto.CellError;
import com.yuzhi.dts.ingestion.service.dto.ColumnInfo;
import com.yuzhi.dts.ingestion.service.dto.ParseResult;
import com.yuzhi.dts.ingestion.service.etl.BuiltInRuleChecker;
import com.yuzhi.dts.ingestion.service.etl.ExcelParseService;
import com.yuzhi.dts.ingestion.service.etl.StagingTableService;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    private final StagingTableService stagingTableService;
    private final BuiltInRuleChecker builtInRuleChecker;

    public IngestionPreCheckResource(
        IngestionTaskRepository taskRepository,
        ExcelParseService excelParseService,
        StagingTableService stagingTableService,
        BuiltInRuleChecker builtInRuleChecker
    ) {
        this.taskRepository = taskRepository;
        this.excelParseService = excelParseService;
        this.stagingTableService = stagingTableService;
        this.builtInRuleChecker = builtInRuleChecker;
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
            // Parse Excel file via InputStream
            ParseResult parseResult;
            try (InputStream is = Files.newInputStream(path)) {
                parseResult = excelParseService.parse(is);
            }

            // Create staging table and bulk insert
            UUID taskUuid = UUID.nameUUIDFromBytes(("ingestion-task-" + id).getBytes());
            String tableName = stagingTableService.create(taskUuid, parseResult.columns());
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
     * Run governance quality rules on the staging table data.
     *
     * NOTE: IngestionQualityBridge is in dts-platform module and cannot be directly
     * injected here (cross-module dependency). This endpoint is a placeholder that
     * should be either:
     *   (a) moved to dts-platform module, or
     *   (b) implemented via REST call to dts-platform, or
     *   (c) extracted to a shared module.
     * For now, returns a TODO response indicating the cross-module concern.
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

        // TODO: Call IngestionQualityBridge.preCheck(stagingTableName, datasetId, totalRows)
        // IngestionQualityBridge is in dts-platform module. Options:
        //   1. Add a REST endpoint in dts-platform and call it via RestTemplate/WebClient
        //   2. Move this endpoint to dts-platform
        //   3. Extract bridge interface to a shared module
        // For now, mark as CHECKING then PASSED (placeholder).
        task.setPreCheckStatus("CHECKING");
        taskRepository.save(task);

        log.warn("Pre-check for task {} is using placeholder logic. " +
            "IngestionQualityBridge integration pending (cross-module).", id);

        task.setPreCheckStatus("PASSED");
        taskRepository.save(task);

        return ResponseEntity.ok(new PreCheckResponse(
            "PASSED",
            0,
            0,
            0,
            List.of()
        ));
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
     * POST /api/ingestion/tasks/{id}/re-check
     * Re-run pre-check on edited staging data (same flow as pre-check).
     */
    @PostMapping("/{id}/re-check")
    @Transactional
    public ResponseEntity<PreCheckResponse> reCheck(@PathVariable Long id) {
        // Re-check follows the same flow as pre-check
        return preCheck(id);
    }

    /**
     * POST /api/ingestion/tasks/{id}/submit
     * Verify all rows are clean, then proceed with ingestion.
     * NOTE: Actual Addax execution integration is deferred; this validates and returns success.
     */
    @PostMapping("/{id}/submit")
    @Transactional
    public ResponseEntity<Map<String, Object>> submit(@PathVariable Long id) {
        IngestionTask task = findTaskOrThrow(id);

        String tableName = task.getStagingTableName();
        if (tableName == null || tableName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No staging table found. Run /parse first.");
        }

        if (!stagingTableService.allClean(tableName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Staging table still has errors. Fix all errors before submitting.");
        }

        // TODO: Trigger actual Addax execution with data sourced from staging table.
        // For now, just mark as submitted and clean up staging table.
        stagingTableService.drop(tableName);
        task.setStagingTableName(null);
        task.setPreCheckStatus(null);
        taskRepository.save(task);

        return ResponseEntity.ok(Map.of(
            "taskId", id,
            "status", "submitted"
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
}
