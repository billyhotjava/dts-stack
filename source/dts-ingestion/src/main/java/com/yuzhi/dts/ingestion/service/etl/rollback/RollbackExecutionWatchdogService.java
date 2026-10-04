package com.yuzhi.dts.ingestion.service.etl.rollback;

import com.yuzhi.dts.ingestion.domain.IngestionRollbackOperation;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackOperationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Marks abandoned destructive executions for reconciliation without ever replaying them. */
@Service
public class RollbackExecutionWatchdogService {

    private static final String UNKNOWN_OUTCOME = "ROLLBACK_EXECUTION_OUTCOME_UNKNOWN_AFTER_TIMEOUT";

    private final IngestionRollbackOperationRepository operationRepository;
    private final RollbackAuditService auditService;
    private final TransactionTemplate transactions;
    private final Duration timeout;
    private final Clock clock;

    @Autowired
    public RollbackExecutionWatchdogService(
        IngestionRollbackOperationRepository operationRepository,
        RollbackAuditService auditService,
        PlatformTransactionManager transactionManager,
        @Value("${dts.ingestion.rollback-execution-timeout-ms:1800000}") long timeoutMillis
    ) {
        this(
            operationRepository,
            auditService,
            new TransactionTemplate(transactionManager),
            Duration.ofMillis(timeoutMillis),
            Clock.systemUTC()
        );
    }

    RollbackExecutionWatchdogService(
        IngestionRollbackOperationRepository operationRepository,
        RollbackAuditService auditService,
        TransactionTemplate transactions,
        Duration timeout,
        Clock clock
    ) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("rollback execution timeout must be positive");
        }
        this.operationRepository = operationRepository;
        this.auditService = auditService;
        this.transactions = transactions;
        this.timeout = timeout;
        this.clock = clock;
    }

    @Scheduled(
        initialDelayString = "${dts.ingestion.rollback-watchdog-initial-delay-ms:60000}",
        fixedDelayString = "${dts.ingestion.rollback-watchdog-poll-ms:60000}"
    )
    public void markAbandonedExecutions() {
        Instant cutoff = clock.instant().minus(timeout);
        List<IngestionRollbackOperation> stale = operationRepository
            .findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc("EXECUTING", cutoff);
        stale.forEach(candidate -> transactions.executeWithoutResult(ignored -> markIfStillAbandoned(candidate, cutoff)));
    }

    private void markIfStillAbandoned(IngestionRollbackOperation candidate, Instant cutoff) {
        IngestionRollbackOperation operation = operationRepository.findForUpdate(candidate.getReceiptId()).orElse(null);
        if (
            operation == null ||
            !"EXECUTING".equals(operation.getStatus()) ||
            operation.getUpdatedAt() == null ||
            !operation.getUpdatedAt().isBefore(cutoff)
        ) {
            return;
        }
        Instant now = clock.instant();
        operation.setStatus("RECONCILIATION_REQUIRED");
        operation.setFailureMessage(UNKNOWN_OUTCOME);
        operation.setUpdatedAt(now);
        operationRepository.save(operation);
        auditService.recordCommitted(
            operation.getReceiptId(),
            null,
            RollbackAuditReason.ROLLBACK_FAILED,
            "service:dts-ingestion",
            RollbackLevel.fromCode(operation.getLevel()),
            operation.getScope(),
            operation.getTaskId(),
            operation.getSourceDataSourceId(),
            Map.of("receiptId", operation.getReceiptId(), "state", "EXECUTING"),
            Map.of("timeoutMillis", timeout.toMillis()),
            Map.of("state", "RECONCILIATION_REQUIRED"),
            "FAILED",
            UNKNOWN_OUTCOME
        );
    }
}
