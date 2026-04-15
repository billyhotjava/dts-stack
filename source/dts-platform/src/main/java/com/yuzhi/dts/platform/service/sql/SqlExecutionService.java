package com.yuzhi.dts.platform.service.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.domain.explore.QueryExecutionChunk;
import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionChunkRepository;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.audit.SqlIdeAuditActions;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.sql.dto.SqlResultPageResponse;
import com.yuzhi.dts.platform.service.sql.dto.SqlResultPreview;
import com.yuzhi.dts.platform.service.sql.dto.SqlStatusResponse;
import com.yuzhi.dts.platform.service.sql.dto.SqlSubmitRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlSubmitResponse;
import com.yuzhi.dts.platform.service.sql.dto.SqlValidateRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlValidateResponse;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SqlExecutionService {

    private static final Logger LOG = LoggerFactory.getLogger(SqlExecutionService.class);
    private static final int PREVIEW_LIMIT = 100;
    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_PAGE_SIZE = 1000;
    private static final int EXECUTION_VISIBILITY_RETRIES = 20;
    private static final long EXECUTION_VISIBILITY_RETRY_MILLIS = 50L;

    private static final int CHUNK_SIZE = 2000;

    private final QueryExecutionRepository queryExecutionRepository;
    private final ResultSetRepository resultSetRepository;
    private final QueryExecutionChunkRepository chunkRepository;
    private final AuditService auditService;
    private final QueryGateway queryGateway;
    private final JdbcSqlExecutor jdbcSqlExecutor;
    private final SqlValidationService validationService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate persistTransactionTemplate;
    private final InfraDataSourceRepository infraDataSourceRepository;
    private final ConcurrentMap<UUID, CompletableFuture<Void>> runningTasks = new ConcurrentHashMap<>();
    private final Set<UUID> cancelRequested = ConcurrentHashMap.newKeySet();

    public SqlExecutionService(
        QueryExecutionRepository queryExecutionRepository,
        ResultSetRepository resultSetRepository,
        QueryExecutionChunkRepository chunkRepository,
        AuditService auditService,
        QueryGateway queryGateway,
        JdbcSqlExecutor jdbcSqlExecutor,
        SqlValidationService validationService,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager,
        InfraDataSourceRepository infraDataSourceRepository
    ) {
        this.queryExecutionRepository = queryExecutionRepository;
        this.resultSetRepository = resultSetRepository;
        this.chunkRepository = chunkRepository;
        this.auditService = auditService;
        this.queryGateway = queryGateway;
        this.jdbcSqlExecutor = jdbcSqlExecutor;
        this.validationService = validationService;
        this.objectMapper = objectMapper;
        this.persistTransactionTemplate = new TransactionTemplate(transactionManager);
        this.infraDataSourceRepository = infraDataSourceRepository;
    }

    @Transactional
    public SqlSubmitResponse submit(SqlSubmitRequest request, Principal principal) {
        QueryExecution execution = new QueryExecution();
        // Resolve engine from the actual datasource type; fall back to POSTGRESQL (our only deployed DB).
        String resolvedDatasource = StringUtils.hasText(request.datasource()) ? request.datasource() : null;
        ExecEnums.ExecEngine resolvedEngine = resolveEngine(resolvedDatasource);
        execution.setEngine(resolvedEngine);
        execution.setDatasource(resolvedDatasource);
        execution.setConnection(request.catalog());
        execution.setSqlText(request.sqlText());
        execution.setStatus(ExecEnums.ExecStatus.PENDING);
        execution.setLimitApplied(Boolean.FALSE);
        execution.setQueuePosition(Math.max(0, runningTasks.size()));
        execution.setStartedAt(Instant.now());
        QueryExecution saved = queryExecutionRepository.save(execution);
        recordSubmitAudit(saved, request);

        CompletableFuture<Void> future = CompletableFuture
            .runAsync(() -> executeQueued(saved.getId(), request, principal))
            .whenComplete((unused, throwable) -> {
                runningTasks.remove(saved.getId());
                cancelRequested.remove(saved.getId());
                if (throwable != null) {
                    LOG.warn("sql async execution ended with exception executionId={}", saved.getId(), throwable);
                }
            });
        runningTasks.put(saved.getId(), future);

        return new SqlSubmitResponse(saved.getId(), saved.getTrinoQueryId(), true, null, null);
    }

    @Transactional(readOnly = true)
    public SqlStatusResponse status(UUID executionId) {
        QueryExecution execution = queryExecutionRepository
            .findById(executionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "query execution not found"));

        SqlResultPreview preview = null;
        if (execution.getResultSetId() != null) {
            ResultSet rs = resultSetRepository.findById(execution.getResultSetId()).orElse(null);
            if (rs != null) {
                preview = parsePreview(rs.getPreviewColumns());
            }
        }

        return new SqlStatusResponse(
            execution.getId(),
            execution.getStatus(),
            execution.getElapsedMs(),
            execution.getRowCount(),
            execution.getBytesProcessed(),
            execution.getQueuePosition(),
            execution.getErrorMessage(),
            execution.getResultSetId(),
            null,
            preview
        );
    }

    @Transactional(readOnly = true)
    public SqlResultPageResponse resultPage(UUID executionId, Integer page, Integer pageSize) {
        QueryExecution execution = queryExecutionRepository
            .findById(executionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "query execution not found"));

        if (execution.getResultSetId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "执行记录尚未生成结果集");
        }
        ResultSet resultSet = resultSetRepository
            .findById(execution.getResultSetId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "result set not found"));
        StoredResult stored = parseStoredResult(resultSet.getPreviewColumns());

        int safePageSize = normalizePageSize(pageSize);
        int safePage = Math.max(1, page == null ? 1 : page);
        long totalRows = stored.rowCount();
        int totalPages = totalRows <= 0 ? 0 : (int) Math.ceil((double) totalRows / safePageSize);
        if (totalPages > 0 && safePage > totalPages) {
            safePage = totalPages;
        }

        int from = Math.max(0, (safePage - 1) * safePageSize);
        int to = Math.min(stored.rows().size(), from + safePageSize);
        List<Map<String, Object>> rows = from >= to ? List.of() : new ArrayList<>(stored.rows().subList(from, to));

        return new SqlResultPageResponse(
            execution.getId(),
            execution.getResultSetId(),
            safePage,
            safePageSize,
            totalRows,
            totalPages,
            safePage < totalPages,
            stored.headers(),
            rows
        );
    }

    @Transactional
    public void cancel(UUID executionId, Principal principal) {
        QueryExecution execution = queryExecutionRepository
            .findById(executionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "query execution not found"));
        if (isFinalStatus(execution.getStatus())) {
            return;
        }
        cancelRequested.add(executionId);
        // Cancel the in-flight JDBC Statement (Critical #2: Statement.cancel() on the executing thread)
        jdbcSqlExecutor.cancel(executionId);
        CompletableFuture<Void> runningFuture = runningTasks.get(executionId);
        if (runningFuture != null) {
            runningFuture.cancel(true);
        }
        markCanceled(execution, "查询已被用户取消");
        recordCancelAudit(execution, principal);
    }

    private void executeQueued(UUID executionId, SqlSubmitRequest request, Principal principal) {
        QueryExecution execution = awaitExecutionVisibility(executionId);
        if (execution == null) {
            LOG.warn("sql execution record not visible for async worker executionId={}", executionId);
            return;
        }
        if (cancelRequested.contains(executionId) || execution.getStatus() == ExecEnums.ExecStatus.CANCELED) {
            markCanceled(execution, "查询在执行前已取消");
            return;
        }
        execution.setStatus(ExecEnums.ExecStatus.RUNNING);
        execution.setQueuePosition(0);
        execution.setErrorMessage(null);
        queryExecutionRepository.save(execution);

        try {
            SqlValidateRequest validateRequest = new SqlValidateRequest(
                request.sqlText(),
                request.datasource(),
                request.catalog(),
                request.schema(),
                request.clientRequestId()
            );
            SqlValidateResponse validation = validationService.validate(validateRequest, principal);
            if (!validation.executable()) {
                String reason = validation.violations().stream()
                    .filter(v -> v.blocking())
                    .map(v -> v.message())
                    .findFirst()
                    .orElse("SQL 被策略阻断");
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
            }

            execution.setLimitApplied(validation.limitInfo() != null && validation.limitInfo().enforced());
            queryExecutionRepository.save(execution);

            // T20: emit rewritten-hash audit now that we have validation.rewrittenSql()
            String rawSqlHash = Integer.toHexString(request.sqlText() == null ? 0 : request.sqlText().hashCode());
            String rewrittenSqlHash = Integer.toHexString(
                validation.rewrittenSql() == null ? 0 : validation.rewrittenSql().hashCode()
            );
            java.util.Map<String, Object> submitPayload = new java.util.LinkedHashMap<>();
            submitPayload.put("engine", execution.getEngine() == null ? null : execution.getEngine().name());
            submitPayload.put("sqlHash", rawSqlHash);
            submitPayload.put("rewrittenHash", rewrittenSqlHash);
            submitPayload.put("actionCode", SqlIdeAuditActions.SQL_EXECUTE_SUBMIT);
            auditService.record(
                "EXECUTE",
                "sql.ide.execute",
                "sql.execution",
                execution.getId().toString(),
                "SUCCESS",
                submitPayload
            );

            UUID datasourceId = parseDatasourceId(request.datasource());
            Map<String, Object> payload = queryGateway.execute(validation.rewrittenSql(), datasourceId, executionId);

            if (cancelRequested.contains(executionId)) {
                markCanceled(execution, "查询执行过程中被取消");
                return;
            }

            List<String> headers = normalizeHeaders(payload.get("headers"));
            List<Map<String, Object>> rows = normalizeRows(payload.get("rows"));
            Long rowCountRaw = extractLong(payload.get("rowCount"));
            long totalRows = rowCountRaw != null ? rowCountRaw : rows.size();
            boolean truncated = Boolean.TRUE.equals(payload.get("truncated"));
            Long elapsedMs = extractLong(payload.get("queryMillis"));
            Long connectMs = extractLong(payload.get("connectMillis"));
            long totalElapsedMs = elapsedMs != null
                ? (connectMs != null ? connectMs + elapsedMs : elapsedMs)
                : 0L;

            final List<String> finalHeaders = headers;
            final List<Map<String, Object>> finalRows = rows;
            final long finalTotalRows = totalRows;
            final boolean finalTruncated = truncated;
            final long finalElapsedMs = totalElapsedMs;
            // When JdbcQueryGateway pre-persisted chunks, chunkCount comes from the payload.
            // The explicit chunksPrePersisted flag is used — NOT inferring from finalRows.isEmpty()
            // (which would conflate an empty Hive result set with pre-persisted JDBC chunks).
            final Integer payloadChunkCount = payload.get("chunkCount") instanceof Integer n ? n : null;
            final boolean chunksPrePersisted = Boolean.TRUE.equals(payload.get("chunksPrePersisted"));

            persistTransactionTemplate.executeWithoutResult(status -> {
                if (!finalHeaders.isEmpty()) {
                    // Write chunk records (only when rows were not already persisted by JdbcSqlExecutor)
                    int chunkIndex = 0;
                    for (int offset = 0; offset < finalRows.size(); offset += CHUNK_SIZE) {
                        int end = Math.min(finalRows.size(), offset + CHUNK_SIZE);
                        QueryExecutionChunk chunk = new QueryExecutionChunk();
                        chunk.setExecutionId(execution.getId());
                        chunk.setChunkIndex(chunkIndex++);
                        try {
                            chunk.setRowsJson(objectMapper.writeValueAsString(finalRows.subList(offset, end)));
                        } catch (Exception ex) {
                            chunk.setRowsJson("[]");
                        }
                        chunk.setRowStart((long) offset);
                        chunk.setRowEnd((long) end);
                        chunk.setCreatedDate(Instant.now());
                        chunkRepository.save(chunk);
                    }
                    // If JdbcSqlExecutor pre-persisted the chunks, use the count it reported.
                    // Check the explicit flag — not finalRows.isEmpty() — to avoid conflating
                    // a genuine empty result from the Hive path with pre-persisted JDBC chunks.
                    int effectiveChunkCount = (chunksPrePersisted && payloadChunkCount != null)
                        ? payloadChunkCount
                        : chunkIndex;

                    // Legacy preview blob — first 100 rows kept for /api/sql/result-page v1 compatibility
                    List<Map<String, Object>> previewRows = finalRows.size() <= PREVIEW_LIMIT
                        ? finalRows
                        : finalRows.subList(0, PREVIEW_LIMIT);

                    ResultSet rs = new ResultSet();
                    rs.setStorageUri("inline://result-set/pending");
                    rs.setStorageFormat(ResultSet.StorageFormat.JSON);
                    rs.setColumns(String.join(",", finalHeaders));
                    rs.setRowCount(finalTotalRows);
                    rs.setChunkCount(effectiveChunkCount);
                    rs.setPreviewColumns(buildStoredResultJson(finalHeaders, previewRows, finalTotalRows));
                    rs.setTtlDays(7);
                    rs.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
                    rs = resultSetRepository.save(rs);
                    rs.setStorageUri("inline://result-set/" + rs.getId());
                    rs = resultSetRepository.save(rs);
                    execution.setResultSetId(rs.getId());
                }

                execution.setRowCount(finalTotalRows);
                if (finalElapsedMs > 0L) {
                    execution.setElapsedMs(finalElapsedMs);
                }
                execution.setLimitApplied(finalTruncated);
                execution.setStatus(ExecEnums.ExecStatus.SUCCESS);
                execution.setFinishedAt(Instant.now());
                execution.setErrorMessage(null);
                queryExecutionRepository.save(execution);
            });
            recordCompletionAudit(execution, "SUCCESS", null);
        } catch (Exception ex) {
            QueryExecution latest = queryExecutionRepository.findById(executionId).orElse(execution);
            if (latest.getStatus() == ExecEnums.ExecStatus.CANCELED || cancelRequested.contains(executionId)) {
                markCanceled(latest, "查询执行过程中被取消");
                return;
            }
            latest.setStatus(ExecEnums.ExecStatus.FAILED);
            latest.setErrorMessage(truncate(resolveMessage(ex), 1024));
            latest.setFinishedAt(Instant.now());
            queryExecutionRepository.save(latest);
            recordCompletionAudit(latest, "FAILED", resolveMessage(ex));
        }
    }

    private void markCanceled(QueryExecution execution, String message) {
        execution.setStatus(ExecEnums.ExecStatus.CANCELED);
        execution.setErrorMessage(truncate(message, 1024));
        execution.setFinishedAt(Instant.now());
        queryExecutionRepository.save(execution);
        recordCompletionAudit(execution, "CANCELED", message);
    }

    private boolean isFinalStatus(ExecEnums.ExecStatus status) {
        return status == ExecEnums.ExecStatus.SUCCESS || status == ExecEnums.ExecStatus.FAILED || status == ExecEnums.ExecStatus.CANCELED;
    }

    private void recordSubmitAudit(QueryExecution execution, SqlSubmitRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "提交 SQL 查询");
        if (execution.getDatasource() != null) {
            payload.put("datasource", execution.getDatasource());
        }
        if (execution.getConnection() != null) {
            payload.put("catalog", execution.getConnection());
        }
        if (request.schema() != null) {
            payload.put("schema", request.schema());
        }
        if (request.clientRequestId() != null) {
            payload.put("clientRequestId", request.clientRequestId());
        }
        if (request.fetchSize() != null) {
            payload.put("fetchSize", request.fetchSize());
        }
        if (request.dryRun() != null) {
            payload.put("dryRun", request.dryRun());
        }
        String sql = truncate(request.sqlText(), 2048);
        if (sql != null && !sql.isBlank()) {
            payload.put("sqlText", sql);
        }
        payload.put("status", execution.getStatus() != null ? execution.getStatus().name() : ExecEnums.ExecStatus.PENDING.name());
        auditService.record("EXECUTE", "sql.query", "sql.query", execution.getId().toString(), "SUCCESS", payload);
        // T20: rich SUBMIT audit (with rewrittenHash) is emitted in executeQueued after validation; no duplicate here.
    }

    private void recordCancelAudit(QueryExecution execution, Principal principal) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "停止 SQL 查询");
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            payload.put("requestedBy", principal.getName());
        }
        if (execution.getTrinoQueryId() != null) {
            payload.put("trinoQueryId", execution.getTrinoQueryId());
        }
        if (execution.getElapsedMs() != null) {
            payload.put("elapsedMs", execution.getElapsedMs());
        }
        if (execution.getRowCount() != null) {
            payload.put("rowCount", execution.getRowCount());
        }
        if (execution.getBytesProcessed() != null) {
            payload.put("bytesProcessed", execution.getBytesProcessed());
        }
        payload.put("status", execution.getStatus() != null ? execution.getStatus().name() : ExecEnums.ExecStatus.CANCELED.name());
        auditService.record("CANCEL", "sql.query", "sql.query", execution.getId().toString(), "SUCCESS", payload);
        // T20: structured action constant + reason for forward-compat with T18 timeout path
        java.util.Map<String, Object> cancelPayload = new java.util.LinkedHashMap<>();
        cancelPayload.put("reason", "user");
        cancelPayload.put("actionCode", SqlIdeAuditActions.SQL_EXECUTE_CANCEL);
        auditService.record(
            "CANCEL",
            "sql.ide.execute",
            "sql.execution",
            execution.getId().toString(),
            "SUCCESS",
            cancelPayload
        );
    }

    private void recordCompletionAudit(QueryExecution execution, String phase, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "SQL 查询完成");
        payload.put("phase", phase);
        payload.put("status", execution.getStatus() != null ? execution.getStatus().name() : phase);
        if (execution.getRowCount() != null) {
            payload.put("rowCount", execution.getRowCount());
        }
        if (execution.getElapsedMs() != null) {
            payload.put("elapsedMs", execution.getElapsedMs());
        }
        if (StringUtils.hasText(message)) {
            payload.put("message", truncate(message, 1024));
        }
        auditService.record("READ", "sql.query", "sql.query", execution.getId().toString(), "SUCCESS", payload);
        // T20: structured action constant with typed fields
        java.util.Map<String, Object> completePayload = new java.util.LinkedHashMap<>();
        completePayload.put("status", phase);
        completePayload.put("rows", execution.getRowCount() == null ? -1L : execution.getRowCount());
        completePayload.put("elapsedMs", execution.getElapsedMs() == null ? -1L : execution.getElapsedMs());
        completePayload.put("actionCode", SqlIdeAuditActions.SQL_EXECUTE_COMPLETE);
        auditService.record(
            "EXECUTE",
            "sql.ide.execute",
            "sql.execution",
            execution.getId().toString(),
            phase,
            completePayload
        );
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen);
    }

    private List<String> normalizeHeaders(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> headers = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    String text = String.valueOf(item).trim();
                    if (!text.isEmpty()) {
                        headers.add(text);
                    }
                }
            }
            return headers;
        }
        return List.of();
    }

    private List<Map<String, Object>> normalizeRows(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        row.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private String buildStoredResultJson(List<String> headers, List<Map<String, Object>> rows, long rowCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headers", headers);
        payload.put("rows", rows);
        payload.put("rowCount", rowCount);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private SqlResultPreview parsePreview(String json) {
        StoredResult stored = parseStoredResult(json);
        int upper = Math.min(stored.rows().size(), PREVIEW_LIMIT);
        List<Map<String, Object>> previewRows = upper <= 0 ? List.of() : new ArrayList<>(stored.rows().subList(0, upper));
        return new SqlResultPreview(stored.headers(), previewRows, stored.rowCount(), stored.rowCount() > PREVIEW_LIMIT);
    }

    private StoredResult parseStoredResult(String json) {
        if (!StringUtils.hasText(json)) {
            return new StoredResult(List.of(), List.of(), 0L);
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(json, Map.class);
            List<String> headers = normalizeHeaders(payload.get("headers"));
            List<Map<String, Object>> rows = normalizeRows(payload.get("rows"));
            Long rowCountRaw = extractLong(payload.get("rowCount"));
            long rowCount = rowCountRaw != null ? rowCountRaw : rows.size();
            return new StoredResult(headers, rows, rowCount);
        } catch (Exception ex) {
            return new StoredResult(List.of(), List.of(), 0L);
        }
    }

    private Long extractLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String resolveMessage(Exception ex) {
        String message = ex.getMessage();
        if (message != null) {
            return message;
        }
        return ex.getClass().getSimpleName();
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private UUID parseDatasourceId(String datasource) {
        if (!StringUtils.hasText(datasource)) {
            return null;
        }
        try {
            return UUID.fromString(datasource);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private QueryExecution awaitExecutionVisibility(UUID executionId) {
        for (int attempt = 0; attempt < EXECUTION_VISIBILITY_RETRIES; attempt++) {
            QueryExecution execution = queryExecutionRepository.findById(executionId).orElse(null);
            if (execution != null) {
                return execution;
            }
            if (attempt + 1 >= EXECUTION_VISIBILITY_RETRIES || cancelRequested.contains(executionId)) {
                break;
            }
            try {
                Thread.sleep(EXECUTION_VISIBILITY_RETRY_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    /**
     * Resolve the execution engine from the datasource id string.
     * Falls back to {@link ExecEnums.ExecEngine#POSTGRESQL} when the datasource cannot be
     * resolved (the only engine deployed in this environment).
     */
    private ExecEnums.ExecEngine resolveEngine(String datasourceId) {
        if (datasourceId == null) {
            return ExecEnums.ExecEngine.POSTGRESQL;
        }
        UUID id;
        try {
            id = UUID.fromString(datasourceId);
        } catch (IllegalArgumentException ex) {
            return ExecEnums.ExecEngine.POSTGRESQL;
        }
        return infraDataSourceRepository
            .findById(id)
            .map(ds -> {
                if (ds.getType() == null) return ExecEnums.ExecEngine.POSTGRESQL;
                String type = ds.getType().trim().toUpperCase(java.util.Locale.ROOT);
                return switch (type) {
                    case "HIVE", "INCEPTOR" -> ExecEnums.ExecEngine.HIVE;
                    case "POSTGRESQL", "POSTGRES", "JDBC" -> ExecEnums.ExecEngine.POSTGRESQL;
                    default -> {
                        LOG.warn("Unknown datasource type '{}' for datasource {}, defaulting to POSTGRESQL",
                            type, ds.getId());
                        yield ExecEnums.ExecEngine.POSTGRESQL;
                    }
                };
            })
            .orElse(ExecEnums.ExecEngine.POSTGRESQL);
    }

    private record StoredResult(List<String> headers, List<Map<String, Object>> rows, long rowCount) {}
}
