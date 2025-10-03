package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import com.yuzhi.dts.platform.domain.explore.ExploreSavedQuery;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.domain.explore.ResultSet.StorageFormat;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.explore.ExploreSavedQueryRepository;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.explore.dto.CreateSavedQueryRequest;
import com.yuzhi.dts.platform.service.explore.dto.UpdateSavedQueryRequest;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/explore")
@Transactional
@Validated
public class ExploreResource {

    private static final int HISTORY_LIMIT = 50;
    private static final int PREVIEW_ROW_LIMIT = 50;

    private final ExploreSavedQueryRepository savedRepo;
    private final QueryExecutionRepository executionRepo;
    private final ResultSetRepository resultSetRepo;
    private final CatalogDatasetRepository datasetRepo;
    private final AccessChecker accessChecker;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    public ExploreResource(
        ExploreSavedQueryRepository savedRepo,
        QueryExecutionRepository executionRepo,
        ResultSetRepository resultSetRepo,
        CatalogDatasetRepository datasetRepo,
        AccessChecker accessChecker,
        AuditService audit,
        ObjectMapper objectMapper
    ) {
        this.savedRepo = savedRepo;
        this.executionRepo = executionRepo;
        this.resultSetRepo = resultSetRepo;
        this.datasetRepo = datasetRepo;
        this.accessChecker = accessChecker;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/query/preview")
    public ApiResponse<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
        CatalogDataset dataset = resolveDataset(body.get("datasetId"));
        if (body.get("datasetId") != null && dataset == null) {
            audit.audit("DENY", "explore.preview", Objects.toString(body.get("datasetId"), "unknown"));
            return ApiResponses.error("Access denied for dataset");
        }
        Map<String, Object> payload = generateResult(dataset, extractSql(body), false);
        audit.audit("EXECUTE", "explore.preview", datasetIdentifier(dataset, body.get("datasetId")));
        return ApiResponses.ok(payload);
    }

    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(@RequestBody Map<String, Object> body) {
        CatalogDataset dataset = resolveDataset(body.get("datasetId"));
        if (body.get("datasetId") != null && dataset == null) {
            audit.audit("DENY", "explore.execute", Objects.toString(body.get("datasetId"), "unknown"));
            return ApiResponses.error("Access denied for dataset");
        }
        Map<String, Object> payload = generateResult(dataset, extractSql(body), true);
        audit.audit("EXECUTE", "explore.execute", datasetIdentifier(dataset, body.get("datasetId")));
        return ApiResponses.ok(payload);
    }

    @PostMapping("/explain")
    public ApiResponse<Map<String, Object>> explain(@RequestBody Map<String, Object> body) {
        String sql = extractSql(body);
        List<String> steps = buildExplainSteps(sql);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("effectiveSql", sql.isBlank() ? "SELECT 1" : sql);
        resp.put("steps", steps);
        double cost = ThreadLocalRandom.current().nextDouble(1.5, 15.0);
        resp.put("estimatedCost", String.format(Locale.ROOT, "%.2f", cost));
        audit.audit("READ", "explore.explain", Integer.toString(steps.size()));
        return ApiResponses.ok(resp);
    }

    @PostMapping("/save-result/{executionId}")
    public ApiResponse<Map<String, Object>> saveResult(
        @PathVariable UUID executionId,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        Optional<ResultSet> optional = resultSetRepo.findById(executionId);
        if (optional.isEmpty()) {
            return ApiResponses.error("Result set not found");
        }
        ResultSet record = optional.get();
        int ttlDays = 7;
        if (body != null && body.get("ttlDays") != null) {
            try {
                ttlDays = Math.max(1, Integer.parseInt(String.valueOf(body.get("ttlDays"))));
            } catch (NumberFormatException ignored) {}
        }
        Instant now = Instant.now();
        record.setTtlDays(ttlDays);
        record.setExpiresAt(now.plus(ttlDays, ChronoUnit.DAYS));
        record.setStorageUri("oss://datalake/explore/" + executionId + ".parquet");
        resultSetRepo.save(record);
        audit.audit("UPDATE", "explore.result.save", executionId.toString());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", executionId);
        resp.put("expiresAt", Optional.ofNullable(record.getExpiresAt()).map(Instant::toString).orElse(null));
        resp.put("storageUri", record.getStorageUri());
        return ApiResponses.ok(resp);
    }

    @GetMapping("/query-executions")
    public ApiResponse<List<Map<String, Object>>> listExecutions() {
        List<QueryExecution> executions = executionRepo
            .findAll(PageRequest.of(0, HISTORY_LIMIT, Sort.by(Sort.Direction.DESC, "startedAt", "createdDate")))
            .getContent();

        Set<UUID> datasetIds = executions
            .stream()
            .map(QueryExecution::getDatasetId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, CatalogDataset> datasets = datasetRepo
            .findAllById(datasetIds)
            .stream()
            .collect(Collectors.toMap(CatalogDataset::getId, it -> it));

        List<Map<String, Object>> list = executions
            .stream()
            .map(exec -> toExecutionMap(exec, datasets.get(exec.getDatasetId())))
            .collect(Collectors.toList());
        audit.audit("READ", "explore.execution", Integer.toString(list.size()));
        return ApiResponses.ok(list);
    }

    @GetMapping("/result-sets")
    public ApiResponse<List<Map<String, Object>>> listResultSets() {
        List<ResultSet> all = resultSetRepo.findAll(Sort.by(Sort.Direction.DESC, "createdDate"));
        List<ResultSet> filtered = all
            .stream()
            .filter(rs -> rs.getTtlDays() != null)
            .collect(Collectors.toList());
        Map<UUID, QueryExecution> execByResult = new LinkedHashMap<>();
        Set<UUID> datasetIds = new LinkedHashSet<>();
        for (ResultSet rs : filtered) {
            List<QueryExecution> executions = executionRepo.findByResultSetId(rs.getId());
            QueryExecution exec = executions.isEmpty() ? null : executions.get(0);
            if (exec != null) {
                execByResult.put(rs.getId(), exec);
                if (exec.getDatasetId() != null) {
                    datasetIds.add(exec.getDatasetId());
                }
            }
        }

        Map<UUID, CatalogDataset> datasets = datasetRepo
            .findAllById(datasetIds)
            .stream()
            .collect(Collectors.toMap(CatalogDataset::getId, it -> it));

        List<Map<String, Object>> list = new ArrayList<>();
        for (ResultSet rs : filtered) {
            QueryExecution exec = execByResult.get(rs.getId());
            CatalogDataset dataset = exec != null ? datasets.get(exec.getDatasetId()) : null;
            list.add(toResultSetMap(rs, exec, dataset));
        }
        audit.audit("READ", "explore.resultSet", Integer.toString(list.size()));
        return ApiResponses.ok(list);
    }

    @GetMapping("/result-preview/{id}")
    public ApiResponse<Map<String, Object>> previewResultSet(
        @PathVariable UUID id,
        @RequestParam(name = "rows", defaultValue = "50") int rowsLimit
    ) {
        Optional<ResultSet> optional = resultSetRepo.findById(id);
        if (optional.isEmpty()) {
            return ApiResponses.error("Result set not found");
        }
        ResultSet record = optional.get();
        List<String> headers = parseColumns(record.getColumns());
        PreviewPayload preview = readPreview(record.getPreviewColumns());
        List<Map<String, Object>> rows = preview.hasRows()
            ? cloneRows(preview.getRows(), rowsLimit)
            : generateFallbackRows(headers, rowsLimit);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("headers", headers);
        resp.put("rows", rows);
        resp.put("rowCount", record.getRowCount());
        resp.put("masking", Optional.ofNullable(preview.getMasking()).orElseGet(() -> buildMasking(headers)));
        audit.audit("READ", "explore.result.preview", id.toString());
        return ApiResponses.ok(resp);
    }

    @DeleteMapping("/result-sets/{id}")
    public ApiResponse<Boolean> deleteResultSet(@PathVariable UUID id) {
        if (resultSetRepo.existsById(id)) {
            resultSetRepo.deleteById(id);
            executionRepo.clearResultSetReferences(id);
            audit.audit("DELETE", "explore.result.delete", id.toString());
        }
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PostMapping("/result-sets/cleanup")
    public ApiResponse<Map<String, Object>> cleanupResultSets() {
        Instant now = Instant.now();
        List<ResultSet> expired = resultSetRepo
            .findByExpiresAtBefore(now)
            .stream()
            .collect(Collectors.toList());
        expired.forEach(rs -> {
            resultSetRepo.delete(rs);
            executionRepo.clearResultSetReferences(rs.getId());
        });
        audit.audit("DELETE", "explore.result.cleanup", Integer.toString(expired.size()));
        return ApiResponses.ok(Map.of("deleted", expired.size()));
    }

    @GetMapping("/saved-queries")
    public ApiResponse<List<Map<String, Object>>> listSaved() {
        var list = savedRepo
            .findAll()
            .stream()
            .map(
                it -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", it.getId());
                    m.put("name", it.getName());
                    m.put("datasetId", it.getDatasetId());
                    m.put("sqlText", it.getSqlText());
                    return m;
                }
            )
            .collect(Collectors.toList());
        audit.audit("READ", "explore.savedQuery", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/saved-queries")
    public ApiResponse<ExploreSavedQuery> createSaved(@Valid @RequestBody CreateSavedQueryRequest request) {
        UUID datasetId = null;
        if (StringUtils.hasText(request.getDatasetId())) {
            var datasetCheck = validateDatasetAssociation(request.getDatasetId().trim());
            if (!datasetCheck.success()) {
                audit.audit("DENY", "explore.savedQuery", datasetCheck.auditHint());
                return ApiResponses.error(datasetCheck.message());
            }
            datasetId = datasetCheck.dataset() != null ? datasetCheck.dataset().getId() : null;
        }

        ExploreSavedQuery entity = new ExploreSavedQuery();
        entity.setName(trimToLength(request.getName(), 128));
        entity.setSqlText(trimToLength(request.getSqlText(), 4096));
        entity.setDatasetId(datasetId);

        ExploreSavedQuery saved = savedRepo.save(entity);
        audit.audit("CREATE", "explore.savedQuery", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/saved-queries/{id}")
    public ApiResponse<Boolean> deleteSaved(@PathVariable UUID id) {
        savedRepo.deleteById(id);
        audit.audit("DELETE", "explore.savedQuery", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PutMapping("/saved-queries/{id}")
    public ApiResponse<ExploreSavedQuery> updateSaved(@PathVariable UUID id, @Valid @RequestBody UpdateSavedQueryRequest request) {
        ExploreSavedQuery existing = savedRepo.findById(id).orElse(null);
        if (existing == null) {
            return ApiResponses.error("Saved query not found");
        }

        boolean mutated = false;
        if (request.getName() != null) {
            String name = trimToLength(request.getName(), 128);
            if (!StringUtils.hasText(name)) {
                return ApiResponses.error("name cannot be blank");
            }
            existing.setName(name);
            mutated = true;
        }
        if (request.getSqlText() != null) {
            String sqlText = trimToLength(request.getSqlText(), 4096);
            if (!StringUtils.hasText(sqlText)) {
                return ApiResponses.error("sqlText cannot be blank");
            }
            existing.setSqlText(sqlText);
            mutated = true;
        }
        if (request.isDatasetIdPresent()) {
            String datasetIdRaw = request.getDatasetId();
            if (!StringUtils.hasText(datasetIdRaw)) {
                existing.setDatasetId(null);
                mutated = true;
            } else {
                var datasetCheck = validateDatasetAssociation(datasetIdRaw.trim());
                if (!datasetCheck.success()) {
                    audit.audit("DENY", "explore.savedQuery", datasetCheck.auditHint());
                    return ApiResponses.error(datasetCheck.message());
                }
                existing.setDatasetId(datasetCheck.dataset() != null ? datasetCheck.dataset().getId() : null);
                mutated = true;
            }
        }

        if (!mutated) {
            return ApiResponses.error("No fields to update");
        }

        ExploreSavedQuery saved = savedRepo.save(existing);
        audit.audit("UPDATE", "explore.savedQuery", id.toString());
        return ApiResponses.ok(saved);
    }

    @GetMapping("/saved-queries/{id}")
    public ApiResponse<ExploreSavedQuery> getSaved(@PathVariable UUID id) {
        var q = savedRepo.findById(id).orElse(null);
        if (q != null) {
            audit.audit("READ", "explore.savedQuery", id.toString());
        }
        return ApiResponses.ok(q);
    }

    @PostMapping("/saved-queries/{id}/run")
    public ApiResponse<Map<String, Object>> runSaved(@PathVariable UUID id) {
        var q = savedRepo.findById(id).orElseThrow();
        CatalogDataset dataset = null;
        if (q.getDatasetId() != null) {
            dataset = resolveDataset(q.getDatasetId().toString());
            if (dataset == null) {
                audit.audit("DENY", "explore.savedQuery.run", id.toString());
                return ApiResponses.error("Access denied for dataset");
            }
        }
        Map<String, Object> payload = generateResult(dataset, Optional.ofNullable(q.getSqlText()).orElse(""), true);
        audit.audit("EXECUTE", "explore.savedQuery.run", id.toString());
        return ApiResponses.ok(payload);
    }

    private DatasetAssociationResult validateDatasetAssociation(String datasetIdRaw) {
        if (!StringUtils.hasText(datasetIdRaw)) {
            return DatasetAssociationResult.success(null);
        }
        UUID datasetId;
        try {
            datasetId = UUID.fromString(datasetIdRaw);
        } catch (IllegalArgumentException ex) {
            return DatasetAssociationResult.failure("datasetId must be a valid UUID", "invalid:" + datasetIdRaw);
        }
        CatalogDataset dataset = datasetRepo.findById(datasetId).orElse(null);
        if (dataset == null) {
            return DatasetAssociationResult.failure("指定的数据集不存在", "missing:" + datasetId);
        }
        if (!accessChecker.canRead(dataset)) {
            return DatasetAssociationResult.failure("Access denied for dataset", datasetId.toString());
        }
        return DatasetAssociationResult.success(dataset);
    }

    private CatalogDataset resolveDataset(Object datasetIdRaw) {
        if (datasetIdRaw == null) {
            return null;
        }
        try {
            UUID datasetId = UUID.fromString(String.valueOf(datasetIdRaw));
            CatalogDataset dataset = datasetRepo.findById(datasetId).orElse(null);
            if (dataset == null) {
                return null;
            }
            return accessChecker.canRead(dataset) ? dataset : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> generateResult(CatalogDataset dataset, String sqlText, boolean persist) {
        List<String> headers = buildHeaders(dataset);
        List<Map<String, Object>> rows = buildRows(headers);
        Map<String, Object> masking = buildMasking(headers);
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        long durationMs = tlr.nextLong(180, 950);
        long rowCount = rows.size() * tlr.nextInt(10, 40);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headers", headers);
        payload.put("rows", cloneRows(rows));
        payload.put("durationMs", durationMs);
        payload.put("rowCount", rowCount);

        if (persist) {
            UUID executionId = persistExecution(dataset, sqlText, headers, rows, masking, durationMs, rowCount);
            payload.put("executionId", executionId.toString());
        }
        return payload;
    }

    private UUID persistExecution(
        CatalogDataset dataset,
        String sqlText,
        List<String> headers,
        List<Map<String, Object>> rows,
        Map<String, Object> masking,
        long durationMs,
        long rowCount
    ) {
        ResultSet resultSet = new ResultSet();
        resultSet.setStorageUri("memory://result/" + UUID.randomUUID());
        resultSet.setStorageFormat(StorageFormat.PARQUET);
        resultSet.setColumns(String.join(",", headers));
        resultSet.setRowCount(rowCount);
        resultSet.setChunkCount(1);
        resultSet.setPreviewColumns(writePreview(rows, masking));
        ResultSet savedResult = resultSetRepo.save(resultSet);

        Instant finished = Instant.now();
        Instant started = durationMs > 0 ? finished.minus(durationMs, ChronoUnit.MILLIS) : finished;

        QueryExecution execution = new QueryExecution();
        execution.setEngine(ExecEnums.ExecEngine.TRINO);
        execution.setSqlText(sqlText == null || sqlText.isBlank() ? "SELECT 1" : sqlText);
        execution.setStatus(ExecEnums.ExecStatus.SUCCESS);
        execution.setStartedAt(started);
        execution.setFinishedAt(finished);
        execution.setRowCount(rowCount);
        execution.setElapsedMs(durationMs);
        execution.setResultSetId(savedResult.getId());
        if (dataset != null) {
            execution.setDatasetId(dataset.getId());
            execution.setConnection(Optional.ofNullable(dataset.getTrinoCatalog()).orElse("default"));
            execution.setDatasource(Optional.ofNullable(dataset.getHiveTable()).orElse(dataset.getName()));
        } else {
            execution.setConnection("default");
            execution.setDatasource("adhoc");
        }
        executionRepo.save(execution);
        return savedResult.getId();
    }

    private Map<String, Object> toExecutionMap(QueryExecution execution, CatalogDataset dataset) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", execution.getId() != null ? execution.getId().toString() : null);
        map.put("status", execution.getStatus() != null ? execution.getStatus().name() : ExecEnums.ExecStatus.SUCCESS.name());
        map.put("startedAt", Optional.ofNullable(execution.getStartedAt()).map(Instant::toString).orElse(null));
        map.put("finishedAt", Optional.ofNullable(execution.getFinishedAt()).map(Instant::toString).orElse(null));
        map.put("rowCount", execution.getRowCount());
        map.put("durationMs", execution.getElapsedMs());
        map.put("sqlText", execution.getSqlText());
        if (dataset != null) {
            map.put("datasetName", dataset.getName());
            map.put("classification", translateClassification(dataset.getClassification()));
        } else {
            map.put("datasetName", Optional.ofNullable(execution.getDatasource()).orElse("临时查询"));
            map.put("classification", "内部");
        }
        map.put("executionId", Optional.ofNullable(execution.getResultSetId()).map(UUID::toString).orElse(null));
        return map;
    }

    private Map<String, Object> toResultSetMap(ResultSet record, QueryExecution execution, CatalogDataset dataset) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.getId().toString());
        map.put("columns", parseColumns(record.getColumns()));
        map.put("rowCount", record.getRowCount());
        map.put("createdAt", Optional.ofNullable(record.getCreatedDate()).map(Instant::toString).orElse(null));
        map.put("expiresAt", Optional.ofNullable(record.getExpiresAt()).map(Instant::toString).orElse(null));
        map.put("storageUri", record.getStorageUri());
        if (dataset != null) {
            map.put("datasetName", dataset.getName());
            map.put("classification", translateClassification(dataset.getClassification()));
        } else if (execution != null) {
            map.put("datasetName", Optional.ofNullable(execution.getDatasource()).orElse("临时查询"));
            map.put("classification", "内部");
        } else {
            map.put("datasetName", "临时查询");
            map.put("classification", "内部");
        }
        return map;
    }

    private List<String> buildHeaders(CatalogDataset dataset) {
        List<String> headers = new ArrayList<>();
        if (dataset != null && dataset.getHiveTable() != null) {
            String base = dataset.getHiveTable().replaceAll("[^a-zA-Z0-9]", "_");
            headers.add(base + "_id");
            headers.add(base + "_name");
            headers.add("updated_at");
            headers.add("sensitivity_level");
            headers.add("metric_value");
        } else {
            for (int i = 1; i <= 5; i++) {
                headers.add("col_" + i);
            }
        }
        return headers;
    }

    private List<Map<String, Object>> buildRows(List<String> headers) {
        List<Map<String, Object>> rows = new ArrayList<>();
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String header : headers) {
                String lower = header.toLowerCase(Locale.ROOT);
                if (lower.contains("id")) {
                    row.put(header, "ID-" + tlr.nextInt(1000, 9999));
                } else if (lower.contains("name")) {
                    row.put(header, "样本" + (char) ('A' + tlr.nextInt(0, 26)));
                } else if (lower.contains("time") || lower.contains("date")) {
                    row.put(header, Instant.now().minus(tlr.nextInt(1, 48), ChronoUnit.HOURS).toString());
                } else if (lower.contains("level")) {
                    row.put(header, tlr.nextBoolean() ? "内部" : "秘密");
                } else {
                    row.put(header, tlr.nextInt(1, 10000));
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> cloneRows(List<Map<String, Object>> source) {
        return cloneRows(source, source.size());
    }

    private List<Map<String, Object>> cloneRows(List<Map<String, Object>> source, int limit) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, source.size()); i++) {
            copy.add(new LinkedHashMap<>(source.get(i)));
        }
        return copy;
    }

    private List<Map<String, Object>> generateFallbackRows(List<String> headers, int limit) {
        return cloneRows(buildRows(headers), limit);
    }

    private Map<String, Object> buildMasking(List<String> headers) {
        List<String> masked = headers
            .stream()
            .filter(h -> {
                String lower = h.toLowerCase(Locale.ROOT);
                return lower.contains("name") || lower.contains("id") || lower.contains("phone");
            })
            .collect(Collectors.toList());
        return Map.of("maskedColumns", masked);
    }

    private List<String> parseColumns(String columnsRaw) {
        if (columnsRaw == null || columnsRaw.isBlank()) {
            return List.of();
        }
        String[] parts = columnsRaw.split(",");
        List<String> headers = new ArrayList<>(parts.length);
        for (String part : parts) {
            headers.add(part.trim());
        }
        return headers;
    }

    private String translateClassification(String classification) {
        if (classification == null) {
            return "内部";
        }
        return switch (classification.toUpperCase(Locale.ROOT)) {
            case "PUBLIC" -> "公开";
            case "INTERNAL", "GENERAL" -> "内部";
            case "SECRET" -> "秘密";
            case "TOP_SECRET", "CONFIDENTIAL" -> "机密";
            default -> classification;
        };
    }

    private String datasetIdentifier(CatalogDataset dataset, Object raw) {
        if (dataset != null && dataset.getId() != null) {
            return dataset.getId().toString();
        }
        return Objects.toString(raw, "adhoc");
    }

    private record DatasetAssociationResult(boolean success, CatalogDataset dataset, String message, String auditHint) {

        static DatasetAssociationResult success(CatalogDataset dataset) {
            return new DatasetAssociationResult(true, dataset, null, dataset != null && dataset.getId() != null ? dataset.getId().toString() : "");
        }

        static DatasetAssociationResult failure(String message, String auditHint) {
            return new DatasetAssociationResult(false, null, message, auditHint);
        }
    }

    private String asText(Object value) {
        return value != null ? String.valueOf(value).trim() : null;
    }

    private String trimToLength(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private String extractSql(Map<String, Object> body) {
        Object sql = body.get("sqlText");
        if (sql == null) {
            sql = body.get("sql");
        }
        return sql != null ? String.valueOf(sql) : "";
    }

    private List<String> buildExplainSteps(String sql) {
        String normalized = sql == null ? "" : sql.trim();
        if (normalized.isEmpty()) {
            normalized = "SELECT 1";
        }
        List<String> steps = new ArrayList<>();
        steps.add("Parse SQL");
        if (normalized.toUpperCase(Locale.ROOT).contains("JOIN")) {
            steps.add("Resolve join strategy (Broadcast Hash Join)");
        }
        if (normalized.toUpperCase(Locale.ROOT).contains("GROUP")) {
            steps.add("Aggregate on distributed nodes");
        }
        steps.add("Scan Hive table with predicate pushdown");
        steps.add("Collect results and apply masking");
        return steps;
    }

    private String writePreview(List<Map<String, Object>> rows, Map<String, Object> masking) {
        PreviewPayload payload = new PreviewPayload();
        payload.setRows(cloneRows(rows, Math.min(PREVIEW_ROW_LIMIT, rows.size())));
        payload.setMasking(masking);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private PreviewPayload readPreview(String json) {
        if (json == null || json.isBlank()) {
            return new PreviewPayload();
        }
        try {
            return objectMapper.readValue(json, PreviewPayload.class);
        } catch (Exception e) {
            return new PreviewPayload();
        }
    }

    private static final class PreviewPayload {
        private List<Map<String, Object>> rows;
        private Map<String, Object> masking;

        public List<Map<String, Object>> getRows() {
            return rows;
        }

        public void setRows(List<Map<String, Object>> rows) {
            this.rows = rows;
        }

        public Map<String, Object> getMasking() {
            return masking;
        }

        public void setMasking(Map<String, Object> masking) {
            this.masking = masking;
        }

        public boolean hasRows() {
            return rows != null && !rows.isEmpty();
        }
    }
}
