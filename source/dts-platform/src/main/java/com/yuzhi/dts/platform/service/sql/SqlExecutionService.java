package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.sql.dto.SqlStatusResponse;
import com.yuzhi.dts.platform.service.sql.dto.SqlSubmitRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlSubmitResponse;
import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SqlExecutionService {

    private final QueryExecutionRepository queryExecutionRepository;
    private final AuditService auditService;

    public SqlExecutionService(QueryExecutionRepository queryExecutionRepository, AuditService auditService) {
        this.queryExecutionRepository = queryExecutionRepository;
        this.auditService = auditService;
    }

    @Transactional
    public SqlSubmitResponse submit(SqlSubmitRequest request, Principal principal) {
        QueryExecution execution = new QueryExecution();
        execution.setEngine(ExecEnums.ExecEngine.TRINO);
        execution.setDatasource(request.datasource() != null ? request.datasource() : "trino");
        execution.setConnection(request.catalog());
        execution.setSqlText(request.sqlText());
        execution.setStatus(ExecEnums.ExecStatus.PENDING);
        execution.setLimitApplied(Boolean.FALSE);
        execution.setQueuePosition(0);
        QueryExecution saved = queryExecutionRepository.save(execution);
        recordSubmitAudit(saved, request);
        return new SqlSubmitResponse(saved.getId(), saved.getTrinoQueryId(), true);
    }

    @Transactional(readOnly = true)
    public SqlStatusResponse status(UUID executionId) {
        QueryExecution execution = queryExecutionRepository.findById(executionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "query execution not found"));

        return new SqlStatusResponse(
            execution.getId(),
            execution.getStatus(),
            execution.getElapsedMs(),
            execution.getRowCount(),
            execution.getBytesProcessed(),
            execution.getQueuePosition(),
            execution.getErrorMessage(),
            execution.getResultSetId(),
            null
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
}
