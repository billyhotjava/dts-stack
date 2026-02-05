package com.yuzhi.dts.platform.service.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.query.QueryGateway;
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
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SqlExecutionService {

    private final QueryExecutionRepository queryExecutionRepository;
    private final ResultSetRepository resultSetRepository;
    private final AuditService auditService;
    private final QueryGateway queryGateway;
    private final SqlValidationService validationService;
    private final ObjectMapper objectMapper;

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
        execution.setDatasource(request.datasource() != null ? request.datasource() : "trino");
        execution.setConnection(request.catalog());
        execution.setSqlText(request.sqlText());
        execution.setStatus(ExecEnums.ExecStatus.RUNNING);
        execution.setLimitApplied(Boolean.FALSE);
        execution.setQueuePosition(0);
        execution.setStartedAt(Instant.now());
        QueryExecution saved = queryExecutionRepository.save(execution);

        SqlResultPreview preview = null;
        UUID resultSetId = null;
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

            String effectiveSql = validation.rewrittenSql();
            if (validation.limitInfo() != null) {
                saved.setLimitApplied(validation.limitInfo().enforced());
            }
            Map<String, Object> payload = queryGateway.execute(effectiveSql);

            List<String> headers = normalizeHeaders(payload.get("headers"));
            List<Map<String, Object>> rows = normalizeRows(payload.get("rows"));
            int totalRows = rows.size();
            int previewLimit = Math.min(totalRows, 100);
            List<Map<String, Object>> previewRows = rows.subList(0, previewLimit);

            preview = new SqlResultPreview(headers, previewRows, (long) totalRows, totalRows > previewLimit);

            if (!headers.isEmpty()) {
                ResultSet rs = new ResultSet();
                rs.setStorageUri("inline://result-set/pending");
                rs.setStorageFormat(ResultSet.StorageFormat.JSON);
                rs.setColumns(String.join(",", headers));
                rs.setRowCount((long) totalRows);
                rs.setPreviewColumns(buildPreviewJson(headers, previewRows));
                rs.setTtlDays(7);
                rs.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
                rs = resultSetRepository.save(rs);
                rs.setStorageUri("inline://result-set/" + rs.getId());
                rs = resultSetRepository.save(rs);
                resultSetId = rs.getId();
                saved.setResultSetId(resultSetId);
            }

            saved.setRowCount((long) totalRows);
            Long elapsedMs = extractLong(payload.get("queryMillis"));
            if (elapsedMs != null) {
                Long connectMs = extractLong(payload.get("connectMillis"));
                saved.setElapsedMs(connectMs != null ? connectMs + elapsedMs : elapsedMs);
            }
            saved.setStatus(ExecEnums.ExecStatus.SUCCESS);
            saved.setFinishedAt(Instant.now());
            queryExecutionRepository.save(saved);
            recordSubmitAudit(saved, request);
            return new SqlSubmitResponse(saved.getId(), saved.getTrinoQueryId(), false, resultSetId, preview);
        } catch (Exception ex) {
            saved.setStatus(ExecEnums.ExecStatus.FAILED);
            saved.setErrorMessage(truncate(resolveMessage(ex), 1024));
            saved.setFinishedAt(Instant.now());
            queryExecutionRepository.save(saved);
            recordSubmitAudit(saved, request);
            if (ex instanceof ResponseStatusException) {
                throw ex;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "执行 SQL 失败: " + resolveMessage(ex));
        }
    }

    @Transactional(readOnly = true)
    public SqlStatusResponse status(UUID executionId) {
        QueryExecution execution = queryExecutionRepository.findById(executionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "query execution not found"));

        SqlResultPreview preview = null;
        if (execution.getResultSetId() != null) {
            ResultSet rs = resultSetRepository.findById(execution.getResultSetId()).orElse(null);
            if (rs != null && StringUtils.hasText(rs.getPreviewColumns())) {
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

    @Transactional
    public void cancel(UUID executionId, Principal principal) {
        QueryExecution execution = queryExecutionRepository.findById(executionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "query execution not found"));
        execution.setStatus(ExecEnums.ExecStatus.CANCELED);
        execution.setFinishedAt(Instant.now());
        queryExecutionRepository.save(execution);
        recordCancelAudit(execution, principal);
    }

    private void recordSubmitAudit(QueryExecution execution, SqlSubmitRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "执行 SQL 查询");
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
        payload.put("status", execution.getStatus() != null ? execution.getStatus().name() : "PENDING");
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

    private String buildPreviewJson(List<String> headers, List<Map<String, Object>> rows) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headers", headers);
        payload.put("rows", rows);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private SqlResultPreview parsePreview(String json) {
        try {
            Map<?, ?> payload = objectMapper.readValue(json, Map.class);
            List<String> headers = normalizeHeaders(payload.get("headers"));
            List<Map<String, Object>> rows = normalizeRows(payload.get("rows"));
            long rowCount = rows.size();
            return new SqlResultPreview(headers, rows, rowCount, false);
        } catch (Exception ex) {
            return null;
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
}
