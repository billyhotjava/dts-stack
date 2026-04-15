package com.yuzhi.dts.platform.service.sql;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.sql.dto.SqlSubmitRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlSummary;
import com.yuzhi.dts.platform.service.sql.dto.SqlValidateResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class SqlExecutionServiceTest {

    @Mock
    private QueryExecutionRepository queryExecutionRepository;

    @Mock
    private ResultSetRepository resultSetRepository;

    @Mock
    private com.yuzhi.dts.platform.repository.explore.QueryExecutionChunkRepository chunkRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private QueryGateway queryGateway;

    @Mock
    private JdbcSqlExecutor jdbcSqlExecutor;

    @Mock
    private SqlValidationService validationService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private InfraDataSourceRepository infraDataSourceRepository;

    private SqlExecutionService service;

    @BeforeEach
    void setUp() {
        // Make TransactionTemplate execute the callback synchronously
        TransactionStatus txStatus = org.mockito.Mockito.mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(txStatus);

        service = new SqlExecutionService(
            queryExecutionRepository,
            resultSetRepository,
            chunkRepository,
            auditService,
            queryGateway,
            jdbcSqlExecutor,
            validationService,
            new ObjectMapper(),
            transactionManager,
            infraDataSourceRepository
        );
    }

    @Test
    void submit_shouldRetryAsyncStartupWhenExecutionIsNotVisibleOnFirstLoad() {
        UUID executionId = UUID.randomUUID();
        QueryExecution saved = new QueryExecution();
        saved.setId(executionId);
        saved.setStatus(ExecEnums.ExecStatus.PENDING);
        saved.setSqlText("select 1");
        saved.setDatasource(UUID.randomUUID().toString());

        AtomicInteger loadCount = new AtomicInteger();
        when(queryExecutionRepository.save(any(QueryExecution.class))).thenAnswer(invocation -> {
            QueryExecution execution = invocation.getArgument(0);
            if (execution.getId() == null) {
                execution.setId(executionId);
            }
            return execution;
        });
        when(queryExecutionRepository.findById(executionId)).thenAnswer(invocation ->
            loadCount.getAndIncrement() == 0 ? Optional.empty() : Optional.of(saved)
        );
        when(validationService.validate(any(), any())).thenReturn(
            new SqlValidateResponse(true, "select 1", new SqlSummary(List.of(), null, List.of()), List.of(), List.of(), null, null)
        );
        when(queryGateway.execute(eq("select 1"), any(), any())).thenReturn(
            Map.of(
                "headers",
                List.of("value"),
                "rows",
                List.of(Map.of("value", 1L)),
                "rowCount",
                1L,
                "queryMillis",
                5L
            )
        );
        when(resultSetRepository.save(any(ResultSet.class))).thenAnswer(invocation -> {
            ResultSet resultSet = invocation.getArgument(0);
            if (resultSet.getId() == null) {
                resultSet.setId(UUID.randomUUID());
            }
            return resultSet;
        });

        service.submit(new SqlSubmitRequest("select 1", saved.getDatasource(), null, null, null, 100, false), null);

        verify(queryGateway, timeout(2000)).execute(eq("select 1"), any(), any());
        assertEventually(() -> saved.getStatus() == ExecEnums.ExecStatus.SUCCESS);
    }

    private void assertEventually(Check condition) {
        AssertionError last = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                assertThat(condition.matches()).isTrue();
                return;
            } catch (AssertionError error) {
                last = error;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (last != null) {
            throw last;
        }
    }

    @FunctionalInterface
    private interface Check {
        boolean matches();
    }
}
