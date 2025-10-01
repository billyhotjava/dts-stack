package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.service.sql.dto.SqlStatusResponse;
import com.yuzhi.dts.platform.service.sql.dto.SqlSubmitRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlSubmitResponse;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SqlExecutionService {

    private final QueryExecutionRepository queryExecutionRepository;

    public SqlExecutionService(QueryExecutionRepository queryExecutionRepository) {
        this.queryExecutionRepository = queryExecutionRepository;
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
    }
}
