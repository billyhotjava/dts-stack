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
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.DatasetSqlBuilder;
import com.yuzhi.dts.platform.service.security.DatasetSecurityMetadataResolver;
import com.yuzhi.dts.platform.service.security.SecurityGuardException;
import com.yuzhi.dts.platform.service.security.SecuritySqlRewriter;
import com.yuzhi.dts.platform.service.security.SecuritySqlRewriter;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import jakarta.validation.Valid;
import java.lang.reflect.Array;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(ExploreResource.class);

    private final ExploreSavedQueryRepository savedRepo;
    private final QueryExecutionRepository executionRepo;
    private final ResultSetRepository resultSetRepo;
    private final CatalogDatasetRepository datasetRepo;
    private final AccessChecker accessChecker;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final QueryGateway queryGateway;
    private final DatasetSqlBuilder datasetSqlBuilder;
    private final DatasetSecurityMetadataResolver metadataResolver;
    private final SecuritySqlRewriter securitySqlRewriter;

    public ExploreResource(
        ExploreSavedQueryRepository savedRepo,
        QueryExecutionRepository executionRepo,
        ResultSetRepository resultSetRepo,
        CatalogDatasetRepository datasetRepo,
        AccessChecker accessChecker,
        AuditService audit,
        ObjectMapper objectMapper,
        QueryGateway queryGateway,
        DatasetSqlBuilder datasetSqlBuilder,
        DatasetSecurityMetadataResolver metadataResolver,
        SecuritySqlRewriter securitySqlRewriter
    ) {
        this.savedRepo = savedRepo;
        this.executionRepo = executionRepo;
        this.resultSetRepo = resultSetRepo;
        this.datasetRepo = datasetRepo;
        this.accessChecker = accessChecker;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.queryGateway = queryGateway;
        this.datasetSqlBuilder = datasetSqlBuilder;
        this.metadataResolver = metadataResolver;
        this.securitySqlRewriter = securitySqlRewriter;
    }

    @PostMapping("/query/preview")
    public ApiResponse<Map<String, Object>> preview(
        @RequestBody Map<String, Object> body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = resolveDataset(body.get("datasetId"));
        if (body.get("datasetId") != null && dataset == null) {
            audit.audit("DENY", "explore.preview", Objects.toString(body.get("datasetId"), "unknown"));
            return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
        }
        if (dataset != null) {
            String effDept = activeDept != null ? activeDept : claim("dept_code");
            boolean read = accessChecker.canRead(dataset);
            boolean deptOk = accessChecker.departmentAllowed(dataset, effDept);
            if (!read || !deptOk) {
                audit.audit("DENY", "explore.preview", datasetIdentifier(dataset, body.get("datasetId")));
                String code = !deptOk
                    ? com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.INVALID_CONTEXT
                    : com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RBAC_DENY;
                return ApiResponses.error(code, "Access denied for dataset");
            }
        }
        try {
            Map<String, Object> payload = generateResult(dataset, extractSql(body), false);
            audit.audit("EXECUTE", "explore.preview", datasetIdentifier(dataset, body.get("datasetId")));
            return ApiResponses.ok(payload);
        } catch (SecurityGuardException ex) {
            LOG.warn("Explore preview denied: {}", ex.getMessage());
            audit.audit("DENY", "explore.preview", datasetIdentifier(dataset, body.get("datasetId")));
            return ApiResponses.error(ex.getMessage());
        } catch (IllegalStateException ex) {
            LOG.warn("Explore preview failed: {}", ex.getMessage());
            audit.audit("ERROR", "explore.preview", datasetIdentifier(dataset, body.get("datasetId")));
            return ApiResponses.error(ex.getMessage());
        } catch (Exception ex) {
            LOG.error("Explore preview unexpected failure", ex);
            audit.audit("ERROR", "explore.preview", datasetIdentifier(dataset, body.get("datasetId")));
            return ApiResponses.error("查询预览失败: " + ex.getMessage());
        }
    }

    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(
        @RequestBody Map<String, Object> body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = resolveDataset(body.get("datasetId"));
        if (body.get("datasetId") != null && dataset == null) {
            audit.audit("DENY", "explore.execute", Objects.toString(body.get("datasetId"), "unknown"));
            return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
        }
        if (dataset != null) {
            String effDept = activeDept != null ? activeDept : claim("dept_code");
            boolean read = accessChecker.canRead(dataset);
            boolean deptOk = accessChecker.departmentAllowed(dataset, effDept);
            if (!read || !deptOk) {
                audit.audit("DENY", "explore.execute", datasetIdentifier(dataset, body.get("datasetId")));
                String code = !deptOk
                    ? com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.INVALID_CONTEXT
                    : com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RBAC_DENY;
                return ApiResponses.error(code, "Access denied for dataset");
            }
        }
        try {
            Map<String, Object> payload = generateResult(dataset, extractSql(body), true);
            audit.audit("EXECUTE", "explore.execute", datasetIdentifier(dataset, body.get("datasetId")));
            return ApiResponses.ok(payload);
        } catch (SecurityGuardException ex) {
            LOG.warn("Explore execute denied: {}", ex.getMessage());
            audit.audit("DENY", "explore.execute", datasetIdentifier(dataset, body.get("datasetId")));
            return ApiResponses.error(ex.getMessage());
        } catch (IllegalStateException ex) {
            LOG.warn("Explore execute failed: {}", ex.getMessage());
            audit.audit("ERROR", "explore.execute", datasetIdentifier(dataset, body.get("datasetId")));
            return ApiResponses.error(ex.getMessage());
        } catch (Exception ex) {
            LOG.error("Explore execute unexpected failure", ex);
            audit.audit("ERROR", "explore.execute", datasetIdentifier(dataset, body.get("datasetId")));
            return ApiResponses.error("查询执行失败: " + ex.getMessage());
        }
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
        return resultSetRepo
            .findById(executionId)
            .map(record -> {
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
            })
            .orElseGet(() -> ApiResponses.error("Result set not found"));
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
        return resultSetRepo
            .findById(id)
            .map(record -> {
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
            })
            .orElseGet(() -> ApiResponses.error("Result set not found"));
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
    public ApiResponse<Map<String, Object>> runSaved(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        var q = savedRepo.findById(id).orElseThrow();
        CatalogDataset dataset = null;
        if (q.getDatasetId() != null) {
            dataset = resolveDataset(q.getDatasetId().toString());
            if (dataset == null) {
                audit.audit("DENY", "explore.savedQuery.run", id.toString());
                return ApiResponses.error("Access denied for dataset");
            }
            // Enforce scope gate similar to preview/execute
            String effDept = activeDept != null ? activeDept : claim("dept_code");
            boolean read = accessChecker.canRead(dataset);
            boolean deptOk = accessChecker.departmentAllowed(dataset, effDept);
            if (!read || !deptOk) {
                audit.audit("DENY", "explore.savedQuery.run", id.toString());
                String code = !deptOk
                    ? com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.INVALID_CONTEXT
                    : com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RBAC_DENY;
                return ApiResponses.error(code, "Access denied for dataset");
            }
        }
        try {
            Map<String, Object> payload = generateResult(
                dataset,
                Optional.ofNullable(q.getSqlText()).orElse(""),
                true
            );
            audit.audit("EXECUTE", "explore.savedQuery.run", id.toString());
            return ApiResponses.ok(payload);
        } catch (SecurityGuardException ex) {
            LOG.warn("Saved query run denied: {}", ex.getMessage());
            audit.audit("DENY", "explore.savedQuery.run", id.toString());
            return ApiResponses.error(ex.getMessage());
        } catch (IllegalStateException ex) {
            LOG.warn("Saved query run failed: {}", ex.getMessage());
            audit.audit("ERROR", "explore.savedQuery.run", id.toString());
            return ApiResponses.error(ex.getMessage());
        } catch (Exception ex) {
            LOG.error("Saved query run unexpected failure", ex);
            audit.audit("ERROR", "explore.savedQuery.run", id.toString());
            return ApiResponses.error("查询执行失败: " + ex.getMessage());
        }
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
        String effectiveSql = prepareSql(sqlText, dataset);
        effectiveSql = securitySqlRewriter.guard(effectiveSql, dataset);
        Map<String, Object> queryResult = queryGateway.execute(effectiveSql);

        List<String> headers = extractHeaders(queryResult);
        List<Map<String, Object>> rows = extractRows(queryResult.get("rows"));
        if (headers.isEmpty() && !rows.isEmpty()) {
            headers = new ArrayList<>(rows.get(0).keySet());
        }
        if (headers.isEmpty()) {
            headers = buildHeaders(dataset);
        }

        applyDataLevelRowFilter(dataset, headers, rows);

        Map<String, Object> masking = buildMasking(headers);
        long connectMillis = numberOrDefault(queryResult.get("connectMillis"), -1L);
        long queryMillis = numberOrDefault(queryResult.get("queryMillis"), -1L);
        long durationMs = numberOrDefault(queryResult.get("durationMs"), -1L);
        if (durationMs < 0) {
            if (connectMillis >= 0 && queryMillis >= 0) {
                durationMs = connectMillis + queryMillis;
            } else if (queryMillis >= 0) {
                durationMs = queryMillis;
            } else if (connectMillis >= 0) {
                durationMs = connectMillis;
            } else {
                durationMs = rows.isEmpty() ? 0 : 200;
            }
        }

        long rowCount = Math.min(numberOrDefault(queryResult.get("rowCount"), rows.size()), rows.size());

        Map<String, Object> payload = new LinkedHashMap<>(queryResult);
        payload.put("effectiveSql", effectiveSql);
        payload.put("headers", headers);
        payload.put("rows", cloneRows(rows));
        payload.put("masking", masking);
        payload.put("rowCount", rowCount);
        payload.put("durationMs", durationMs);
        if (connectMillis >= 0) {
            payload.put("connectMillis", connectMillis);
        }
        if (queryMillis >= 0) {
            payload.put("queryMillis", queryMillis);
        }

        if (persist) {
            UUID executionId = persistExecution(dataset, effectiveSql, headers, rows, masking, durationMs, rowCount);
            payload.put("executionId", executionId.toString());
        }
        return payload;
    }

    private String prepareSql(String sqlText, CatalogDataset dataset) {
        String candidate = sqlText != null ? sqlText.trim() : "";
        if (!candidate.isEmpty()) {
            return candidate;
        }
        if (dataset != null) {
            return datasetSqlBuilder.buildSampleQuery(dataset, 100);
        }
        return "SELECT 1";
    }

    @SuppressWarnings("unchecked")
    private List<String> extractHeaders(Map<String, Object> queryResult) {
        if (queryResult == null) {
            return new ArrayList<>();
        }
        Object headersObj = queryResult.get("headers");
        if (headersObj instanceof List<?> list) {
            List<String> headers = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) {
                    headers.add(String.valueOf(item));
                }
            }
            headers.removeIf(String::isBlank);
            if (!headers.isEmpty()) {
                return headers;
            }
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(Object rowsObj) {
        if (!(rowsObj instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) map).entrySet()) {
                    if (entry.getKey() != null) {
                        row.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private void applyDataLevelRowFilter(CatalogDataset dataset, List<String> headers, List<Map<String, Object>> rows) {
        if (dataset == null || rows == null || rows.isEmpty()) {
            return;
        }
        Optional<String> columnOpt = metadataResolver.findDataLevelColumn(dataset);
        if (columnOpt.isEmpty()) {
            return;
        }
        List<DataLevel> allowedLevelsList = accessChecker.resolveAllowedDataLevels();
        if (allowedLevelsList == null || allowedLevelsList.isEmpty()) {
            rows.clear();
            return;
        }
        Set<DataLevel> allowedLevels = allowedLevelsList.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (allowedLevels.isEmpty()) {
            rows.clear();
            return;
        }
        Set<String> allowedTokens = allowedLevels
            .stream()
            .flatMap(level -> level.tokens().stream())
            .map(token -> token.toUpperCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        String dataLevelColumn = columnOpt.get();
        int headerIndex = resolveHeaderIndex(headers, dataLevelColumn);

        rows.removeIf(row -> !isRowLevelAllowed(row, headers, headerIndex, dataLevelColumn, allowedLevels, allowedTokens));
    }

    private int resolveHeaderIndex(List<String> headers, String columnName) {
        if (headers == null || columnName == null) {
            return -1;
        }
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header != null && header.trim().equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isRowLevelAllowed(
        Map<String, Object> row,
        List<String> headers,
        int headerIndex,
        String columnName,
        Set<DataLevel> allowedLevels,
        Set<String> allowedTokens
    ) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        Object rawValue = lookupValue(row, columnName);
        if (rawValue == null && headerIndex >= 0 && headers != null && headerIndex < headers.size()) {
            rawValue = lookupValue(row, headers.get(headerIndex));
        }
        if (rawValue == null) {
            return false;
        }
        String text = rawValue.toString().trim();
        if (text.isEmpty()) {
            return false;
        }
        DataLevel level = DataLevel.normalize(text);
        if (level != null) {
            return allowedLevels.contains(level);
        }
        for (String token : expandValueVariants(text)) {
            if (allowedTokens.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private Object lookupValue(Map<String, Object> row, String key) {
        if (key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Set<String> expandValueVariants(String raw) {
        Set<String> variants = new LinkedHashSet<>();
        if (raw == null) {
            return variants;
        }
        String base = raw.trim();
        if (base.isEmpty()) {
            return variants;
        }
        String upper = base.toUpperCase(Locale.ROOT);
        variants.add(upper);
        variants.add(upper.replace('-', '_'));
        variants.add(upper.replace('_', '-'));
        variants.add(upper.replace(' ', '_'));
        variants.add(upper.replace(' ', '-'));
        variants.add(upper.replace('-', ' '));
        variants.add(upper.replace('_', ' '));
        return variants;
    }

    private long numberOrDefault(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
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

    private String claim(String name) {
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get(name);
                return stringifyClaim(v);
            }
            if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
                Object v = principal.getAttribute(name);
                return stringifyClaim(v);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String stringifyClaim(Object raw) {
        Object flattened = flattenClaim(raw);
        if (flattened == null) return null;
        String text = flattened.toString();
        return text == null || text.isBlank() ? null : text;
    }

    private Object flattenClaim(Object raw) {
        if (raw == null) return null;
        if (raw instanceof java.util.Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int len = Array.getLength(raw);
            for (int i = 0; i < len; i++) {
                Object element = Array.get(raw, i);
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        return raw;
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
