package com.yuzhi.dts.platform.service.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
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
import org.springframework.transaction.annotation.Transactional;
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

    private final QueryExecutionRepository queryExecutionRepository;
    private final ResultSetRepository resultSetRepository;
    private final AuditService auditService;
    private final QueryGateway queryGateway;
    private final SqlValidationService validationService;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<UUID, CompletableFuture<Void>> runningTasks = new ConcurrentHashMap<>();
    private final Set<UUID> cancelRequested = ConcurrentHashMap.newKeySet();

    public SqlExecutionService(
        QueryExecutionRepository queryExecutionRepository,
        ResultSetRepository resultSetRepository,
        AuditService auditService,
        QueryGateway queryGateway,
        SqlValidationService validationService,
        ObjectMapper objectMapper
    ) {
        this.queryExecutionRepository = queryExecutionRepository;
        this.resultSetRepository = resultSetRepository;
        this.auditService = auditService;
        this.queryGateway = queryGateway;
        this.validationService = validationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SqlSubmitResponse submit(SqlSubmitRequest request, Principal principal) {
        QueryExecution execution = new QueryExecution();
        execution.setEngine(ExecEnums.ExecEngine.TRINO);
        execution.setDatasource(StringUtils.hasText(request.datasource()) ? request.datasource() : "trino");
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

            UUID datasourceId = parseDatasourceId(request.datasource());
            Map<String, Object> payload = queryGateway.execute(validation.rewrittenSql(), datasourceId);

            if (cancelRequested.contains(executionId)) {
                markCanceled(execution, "查询执行过程中被取消");
                return;
            }

            List<String> headers = normalizeHeaders(payload.get("headers"));
            List<Map<String, Object>> rows = normalizeRows(payload.get("rows"));
            Long rowCountRaw = extractLong(payload.get("rowCount"));
            long totalRows = rowCountRaw != null ? rowCountRaw : rows.size();

            if (!headers.isEmpty()) {
                ResultSet rs = new ResultSet();
                rs.setStorageUri("inline://result-set/pending");
                rs.setStorageFormat(ResultSet.StorageFormat.JSON);
                rs.setColumns(String.join(",", headers));
                rs.setRowCount(totalRows);
                rs.setPreviewColumns(buildStoredResultJson(headers, rows, totalRows));
                rs.setTtlDays(7);
                rs.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
                rs = resultSetRepository.save(rs);
                rs.setStorageUri("inline://result-set/" + rs.getId());
                rs = resultSetRepository.save(rs);
                execution.setResultSetId(rs.getId());
            }

            execution.setRowCount(totalRows);
            Long elapsedMs = extractLong(payload.get("queryMillis"));
            if (elapsedMs != null) {
                Long connectMs = extractLong(payload.get("connectMillis"));
                execution.setElapsedMs(connectMs != null ? connectMs + elapsedMs : elapsedMs);
            }
            execution.setStatus(ExecEnums.ExecStatus.SUCCESS);
            execution.setFinishedAt(Instant.now());
            execution.setErrorMessage(null);
            queryExecutionRepository.save(execution);
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

    private record StoredResult(List<String> headers, List<Map<String, Object>> rows, long rowCount) {}
}
