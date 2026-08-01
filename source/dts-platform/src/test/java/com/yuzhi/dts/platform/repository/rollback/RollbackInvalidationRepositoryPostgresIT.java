package com.yuzhi.dts.platform.repository.rollback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.ingestion.RollbackCommand;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationException;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.CompletionCommand;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.CompletionOutcome;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.PrepareCommand;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.PreparedInvalidation;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RollbackInvalidationRepositoryPostgresIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260801_08_rollback_invalidation_availability.xml";
    private static final String GENERATION_ATTEMPTS_CHANGELOG =
        "config/liquibase/changelog/20260801_11_rollback_dispatch_generation_attempts.xml";
    private static final UUID SOURCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DATASET_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MAPPING_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("rollbackInvalidationIT")
        .withUsername("rollback_invalidation_test")
        .withPassword("rollback_invalidation_test");

    private String schema;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private RollbackInvalidationRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        schema = "rollback_invalidation_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = rootConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
        }
        dataSource = schemaDataSource();
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        createPrerequisiteTables();
        applyChangelog();
        repository = new RollbackInvalidationRepository(jdbc);
        insertMapping();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (schema == null) {
            return;
        }
        try (Connection connection = rootConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void prepareApplyAndRestoreAreCasGuardedIdempotentAndAppendOnly() {
        AuditService audit = mock(AuditService.class);
        RollbackInvalidationService service = new RollbackInvalidationService(
            repository,
            audit,
            new ObjectMapper()
        );

        PreparedInvalidation prepared = inTransaction(() ->
            service.prepare(new PrepareCommand(plan(), SOURCE_ID, "alice", "confirmation-token"))
        );

        assertThat(mappingEnabled()).isFalse();
        assertThat(mappingFenceId()).isEqualTo(prepared.receiptId());
        assertAvailability("FENCED", 0L, prepared.sourceSequence());
        var preparedDispatch = repository.findDispatch(prepared.receiptId()).orElseThrow();
        assertThat(preparedDispatch.status()).isEqualTo("PENDING");
        assertThat(preparedDispatch.attempts()).isZero();
        assertThat(preparedDispatch.generationAttempts()).isZero();
        assertThatThrownBy(() ->
            jdbc.update(
                "update integration_rollback_dispatch_outbox set generation_attempts=dispatch_attempts + 1 where receipt_id=?",
                prepared.receiptId()
            )
        ).isInstanceOf(DataAccessException.class);
        assertThat(preparedDispatch.commandHash()).hasSize(64);
        assertThat(preparedDispatch.commandJson())
            .contains("\"rollbackId\":\"" + prepared.receiptId() + "\"")
            .contains("\"idempotencyKey\":\"platform:" + prepared.receiptId() + "\"")
            .contains("\"level\":1")
            .contains("\"sourceSequence\":" + prepared.sourceSequence());
        PreparedInvalidation replayedPrepare = inTransaction(() ->
            service.prepare(new PrepareCommand(plan(), SOURCE_ID, "alice", "confirmation-token"))
        );
        assertThat(replayedPrepare.receiptId()).isEqualTo(prepared.receiptId());
        assertThat(replayedPrepare.idempotentReplay()).isTrue();
        assertThat(rowCount("integration_rollback_invalidation_receipt")).isOne();
        assertThat(rowCount("integration_rollback_dispatch_outbox")).isOne();
        assertThat(repository.findDispatch(prepared.receiptId()).orElseThrow().commandJson())
            .isEqualTo(preparedDispatch.commandJson());

        assertThatThrownBy(() ->
                repository.updateReceiptState(
                    prepared.receiptId(),
                    RollbackInvalidationService.RESTORED,
                    "ingestion.rollback.42.illegal-restore",
                    "f".repeat(64),
                    prepared.sourceSequence() + 1L,
                    "{}",
                    Instant.parse("2026-08-01T01:30:00Z")
                )
            )
            .isInstanceOfSatisfying(RollbackInvalidationException.class, error ->
                assertThat(error.code()).isEqualTo("ROLLBACK_INVALIDATION_STALE_EVENT")
            );
        assertThat(receiptState(prepared.receiptId())).isEqualTo(RollbackInvalidationService.PREPARED);

        CompletionCommand stale = new CompletionCommand(
            prepared.receiptId(),
            "ingestion.rollback.42.stale",
            prepared.sourceSequence(),
            CompletionOutcome.APPLY,
            "stale completion",
            false,
            "operation-42"
        );
        assertThatThrownBy(() -> inTransaction(() -> service.complete(stale)))
            .isInstanceOfSatisfying(RollbackInvalidationException.class, error ->
                assertThat(error.code()).isEqualTo("ROLLBACK_INVALIDATION_STALE_EVENT")
            );
        assertAvailability("FENCED", 0L, prepared.sourceSequence());

        long sequenceBeforeHugeJump = sourceSequenceLastValue();
        CompletionCommand hugeJump = new CompletionCommand(
            prepared.receiptId(),
            "ingestion.rollback.42.huge-jump",
            Long.MAX_VALUE - 1L,
            CompletionOutcome.APPLY,
            "invalid future completion",
            false,
            "operation-42"
        );
        assertThatThrownBy(() -> inTransaction(() -> service.complete(hugeJump)))
            .isInstanceOfSatisfying(RollbackInvalidationException.class, error ->
                assertThat(error.code()).isEqualTo("ROLLBACK_INVALIDATION_STALE_EVENT")
            );
        assertThat(sourceSequenceLastValue()).isEqualTo(sequenceBeforeHugeJump);

        CompletionCommand apply = new CompletionCommand(
            prepared.receiptId(),
            "ingestion.rollback.42.applied",
            prepared.sourceSequence() + 1L,
            CompletionOutcome.APPLY,
            "ingestion committed rollback",
            false,
            "operation-42"
        );
        assertThat(inTransaction(() -> service.complete(apply)).state())
            .isEqualTo(RollbackInvalidationService.APPLIED);
        assertThat(inTransaction(() -> service.complete(apply)).idempotentReplay()).isTrue();
        assertAvailability("UNAVAILABLE", 1L, apply.sourceSequence());
        assertThat(dispatchStatus(prepared.receiptId())).isEqualTo("COMPLETED");
        assertThat(mappingEnabled()).isFalse();
        assertThat(mappingFenceId()).isEqualTo(prepared.receiptId());

        CompletionCommand changedReplay = new CompletionCommand(
            apply.receiptId(),
            apply.eventId(),
            apply.sourceSequence(),
            apply.outcome(),
            "changed payload",
            false,
            apply.downstreamReference()
        );
        assertThatThrownBy(() -> inTransaction(() -> service.complete(changedReplay)))
            .isInstanceOfSatisfying(RollbackInvalidationException.class, error ->
                assertThat(error.code()).isEqualTo("ROLLBACK_INVALIDATION_IDEMPOTENCY_CONFLICT")
            );

        CompletionCommand restore = new CompletionCommand(
            prepared.receiptId(),
            "ingestion.rollback.42.restored",
            prepared.sourceSequence() + 2L,
            CompletionOutcome.RESTORE,
            "source rebuilt and verified",
            false,
            "restore-42"
        );
        assertThat(inTransaction(() -> service.complete(restore)).state())
            .isEqualTo(RollbackInvalidationService.RESTORED);
        assertAvailability("AVAILABLE", 2L, restore.sourceSequence());
        assertThat(mappingEnabled()).isTrue();
        assertThat(mappingFenceId()).isNull();
        assertThat(rowCount("catalog_asset_availability_event")).isEqualTo(3);
        assertThat(rowCount("integration_rollback_invalidation_completion_event")).isEqualTo(2);
        assertThat(dispatchStatus(prepared.receiptId())).isEqualTo("COMPLETED");
        assertThat(rowCount("platform_event_outbox")).isEqualTo(3);

        assertThatThrownBy(() ->
                jdbc.update(
                    """
                    insert into integration_rollback_invalidation_completion_event (
                        id, receipt_id, event_id, payload_hash, outcome, resulting_state,
                        source_sequence, zero_side_effects_confirmed, completed_at
                    ) values (?, ?, 'invalid-outcome-state', ?, 'APPLY', 'RESTORED', ?, true, current_timestamp)
                    """,
                    UUID.randomUUID(),
                    prepared.receiptId(),
                    "c".repeat(64),
                    restore.sourceSequence() + 1L
                )
            )
            .isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("chk_rollback_invalidation_completion_event");

        assertThat(inTransaction(() -> service.complete(apply)).idempotentReplay()).isTrue();
        assertThat(inTransaction(() -> service.complete(apply)).state())
            .isEqualTo(RollbackInvalidationService.RESTORED);
        assertThatThrownBy(() -> inTransaction(() -> service.complete(changedReplay)))
            .isInstanceOfSatisfying(RollbackInvalidationException.class, error ->
                assertThat(error.code()).isEqualTo("ROLLBACK_INVALIDATION_IDEMPOTENCY_CONFLICT")
            );

        assertEvidenceMutationBlocked("delete from catalog_asset_availability_event");
        assertEvidenceMutationBlocked("truncate catalog_asset_availability_event");
        assertEvidenceMutationBlocked("delete from integration_rollback_invalidation_completion_event");
        assertEvidenceMutationBlocked("truncate integration_rollback_invalidation_completion_event");
        assertThat(
            jdbc.update(
                "update integration_rollback_invalidation_target set applied_at = applied_at where receipt_id = ?",
                prepared.receiptId()
            )
        ).isOne();
        assertEvidenceMutationBlocked(
            "update integration_rollback_invalidation_target set previous_mapping_enabled = not previous_mapping_enabled"
        );
        assertEvidenceMutationBlocked(
            "update integration_rollback_invalidation_target set applied_at = applied_at + interval '1 second'"
        );
        assertEvidenceMutationBlocked("delete from integration_rollback_invalidation_target");
        assertEvidenceMutationBlocked("truncate integration_rollback_invalidation_target");
        assertEvidenceMutationBlocked(
            "update integration_rollback_dispatch_outbox set command_json = '{}'"
        );
        assertEvidenceMutationBlocked("delete from integration_rollback_dispatch_outbox");
        assertEvidenceMutationBlocked("truncate integration_rollback_dispatch_outbox");

        PreparedInvalidation secondPrepared = inTransaction(() ->
            service.prepare(new PrepareCommand(plan(), SOURCE_ID, "alice", "second-confirmation-token"))
        );
        assertThat(secondPrepared.sourceSequence()).isEqualTo(restore.sourceSequence() + 1L);
        assertAvailability("FENCED", 2L, secondPrepared.sourceSequence());
        assertThat(mappingFenceId()).isEqualTo(secondPrepared.receiptId());
        assertThat(dispatchStatus(secondPrepared.receiptId())).isEqualTo("PENDING");

        Instant now = Instant.parse("2026-08-01T02:00:00Z");
        repository.insertPlatformEvent(
            "rollback-it-strict-event",
            "a".repeat(64),
            "RollbackInvalidationIT",
            prepared.receiptId().toString(),
            "VERIFY",
            "SUCCESS",
            "alice",
            "ROLLBACK_INVALIDATION_APPLY",
            "{}",
            now
        );
        repository.insertPlatformEvent(
            "rollback-it-strict-event",
            "a".repeat(64),
            "RollbackInvalidationIT",
            prepared.receiptId().toString(),
            "VERIFY",
            "SUCCESS",
            "alice",
            "ROLLBACK_INVALIDATION_APPLY",
            "{}",
            now
        );
        assertThatThrownBy(() ->
                repository.insertPlatformEvent(
                    "rollback-it-strict-event",
                    "b".repeat(64),
                    "RollbackInvalidationIT",
                    prepared.receiptId().toString(),
                    "VERIFY",
                    "SUCCESS",
                    "alice",
                    "ROLLBACK_INVALIDATION_APPLY",
                    "{\"changed\":true}",
                    now
                )
            )
            .isInstanceOfSatisfying(RollbackInvalidationException.class, error ->
                assertThat(error.code()).isEqualTo("ROLLBACK_INVALIDATION_IDEMPOTENCY_CONFLICT")
            );
    }

    @Test
    void terminalReceiptConstraintRejectsMissingCompletionEvidence() {
        String insertPreparedReceipt = """
            insert into integration_rollback_invalidation_receipt (
                id, idempotency_key, payload_hash, state, rollback_level, rollback_scope,
                source_data_source_id, source_sequence, actor, prepared_at, created_at, updated_at
            ) values (?, ?, repeat('a', 64), 'PREPARED', 1, 'TASK', ?, ?, 'tester',
                      current_timestamp, current_timestamp, current_timestamp)
            """;
        UUID missingHashReceipt = UUID.randomUUID();
        UUID missingSequenceReceipt = UUID.randomUUID();
        jdbc.update(insertPreparedReceipt, missingHashReceipt, "missing-hash", SOURCE_ID, 10L);
        jdbc.update(insertPreparedReceipt, missingSequenceReceipt, "missing-sequence", SOURCE_ID, 20L);

        assertThatThrownBy(() ->
                jdbc.update(
                    """
                    update integration_rollback_invalidation_receipt
                    set state = 'APPLIED', completion_event_id = 'completion-missing-hash',
                        completion_payload_hash = null, completion_source_sequence = source_sequence + 1
                    where id = ?
                    """,
                    missingHashReceipt
                )
            )
            .isInstanceOf(DataAccessException.class)
            .hasStackTraceContaining("chk_rollback_invalidation_state");
        assertThatThrownBy(() ->
                jdbc.update(
                    """
                    update integration_rollback_invalidation_receipt
                    set state = 'APPLIED', completion_event_id = 'completion-missing-sequence',
                        completion_payload_hash = repeat('b', 64), completion_source_sequence = null
                    where id = ?
                    """,
                    missingSequenceReceipt
                )
            )
            .isInstanceOf(DataAccessException.class);
    }

    @Test
    void strictAuditFailureRollsBackReceiptTargetsFenceAndOutbox() {
        AuditService audit = mock(AuditService.class);
        doThrow(new IllegalStateException("audit unavailable"))
            .when(audit)
            .auditActionStrict(anyString(), any(), anyString(), any());
        RollbackInvalidationService service = new RollbackInvalidationService(
            repository,
            audit,
            new ObjectMapper()
        );

        assertThatThrownBy(() ->
                inTransaction(() ->
                    service.prepare(new PrepareCommand(plan(), SOURCE_ID, "alice", "failing-confirmation"))
                )
            )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("audit unavailable");

        assertThat(rowCount("integration_rollback_invalidation_receipt")).isZero();
        assertThat(rowCount("integration_rollback_dispatch_outbox")).isZero();
        assertThat(rowCount("integration_rollback_invalidation_target")).isZero();
        assertThat(rowCount("catalog_asset_availability")).isZero();
        assertThat(rowCount("platform_event_outbox")).isZero();
        assertThat(mappingEnabled()).isTrue();
        assertThat(mappingFenceId()).isNull();
        assertThat(
            jdbc.queryForObject(
                "select availability_source_sequence from infra_ods_table_mapping where id = ?",
                Long.class,
                MAPPING_ID
            )
        ).isZero();
    }

    @Test
    void committedPrepareSurvivesDispatcherCrashLeaseExpiryAndManualReplayWithoutCommandDrift() {
        RollbackInvalidationService service = new RollbackInvalidationService(
            repository,
            mock(AuditService.class),
            new ObjectMapper()
        );
        PreparedInvalidation prepared = inTransaction(() ->
            service.prepare(new PrepareCommand(plan(), SOURCE_ID, "alice", "dispatch-crash"))
        );
        var persisted = repository.findDispatch(prepared.receiptId()).orElseThrow();
        Instant firstClaimAt = persisted.nextAttemptAt().plusSeconds(1L);

        var firstClaim = inTransaction(() ->
            repository.claimDispatch(prepared.receiptId(), firstClaimAt, Duration.ofMinutes(5))
        ).orElseThrow();
        assertThat(firstClaim.status()).isEqualTo("CLAIMED");
        assertThat(firstClaim.attempts()).isEqualTo(1);
        assertThat(firstClaim.generationAttempts()).isEqualTo(1);
        assertThat(firstClaim.commandHash()).isEqualTo(persisted.commandHash());
        assertThat(firstClaim.commandJson()).isEqualTo(persisted.commandJson());

        assertThat(
            inTransaction(() ->
                repository.claimDispatch(
                    prepared.receiptId(),
                    firstClaimAt.plus(Duration.ofMinutes(4)),
                    Duration.ofMinutes(5)
                )
            )
        ).isEmpty();

        var recoveredClaim = inTransaction(() ->
            repository.claimDispatch(
                prepared.receiptId(),
                firstClaimAt.plus(Duration.ofMinutes(6)),
                Duration.ofMinutes(5)
            )
        ).orElseThrow();
        assertThat(recoveredClaim.attempts()).isEqualTo(2);
        assertThat(recoveredClaim.generationAttempts()).isEqualTo(2);
        assertThat(recoveredClaim.commandHash()).isEqualTo(firstClaim.commandHash());
        assertThat(recoveredClaim.commandJson()).isEqualTo(firstClaim.commandJson());

        inTransaction(() -> {
            repository.markDispatchRetry(
                prepared.receiptId(),
                recoveredClaim.attempts(),
                "simulated worker restart",
                firstClaimAt.plus(Duration.ofMinutes(7)),
                firstClaimAt.plus(Duration.ofMinutes(6))
            );
            return null;
        });
        assertThat(service.receipt(prepared.receiptId()).dispatch().status()).isEqualTo("RETRY");

        var replayed = inTransaction(() -> service.replayDispatch(prepared.receiptId()));
        assertThat(replayed.state()).isEqualTo(RollbackInvalidationService.PREPARED);
        assertThat(replayed.dispatch().status()).isEqualTo("PENDING");
        var replayedRecord = repository.findDispatch(prepared.receiptId()).orElseThrow();
        assertThat(replayedRecord.attempts()).isEqualTo(2);
        assertThat(replayedRecord.generationAttempts()).isZero();
        assertThat(repository.findDispatch(prepared.receiptId()).orElseThrow().commandHash())
            .isEqualTo(persisted.commandHash());
        assertThat(repository.findDispatch(prepared.receiptId()).orElseThrow().commandJson())
            .isEqualTo(persisted.commandJson());
        assertThat(mappingFenceId()).isEqualTo(prepared.receiptId());
        assertAvailability("FENCED", 0L, prepared.sourceSequence());

        var replayClaim = inTransaction(() ->
            repository.claimDispatch(
                prepared.receiptId(),
                firstClaimAt.plus(Duration.ofMinutes(8)),
                Duration.ofMinutes(5)
            )
        ).orElseThrow();
        assertThat(replayClaim.attempts()).isEqualTo(3);
        assertThat(replayClaim.generationAttempts()).isOne();
    }

    @Test
    void preparedSnapshotCannotStealAFenceWrittenByANewerOwner() {
        var targets = repository.discoverTargets(SOURCE_ID, List.of("orders"));
        UUID newerFence = UUID.fromString("90000000-0000-0000-0000-000000000001");
        jdbc.update(
            """
            update infra_ods_table_mapping
               set enabled = false, availability_fence_id = ?, availability_event_id = 'newer-fence',
                   availability_source_sequence = 1
             where id = ?
            """,
            newerFence,
            MAPPING_ID
        );

        assertThatThrownBy(() ->
                inTransaction(() -> {
                    repository.fenceTargets(
                        UUID.fromString("80000000-0000-0000-0000-000000000001"),
                        2L,
                        "rollback-invalidation:stale:prepare",
                        "a".repeat(64),
                        targets,
                        Instant.parse("2026-08-01T02:00:00Z")
                    );
                    return null;
                })
            )
            .isInstanceOfSatisfying(RollbackInvalidationException.class, error ->
                assertThat(error.code()).isEqualTo("ROLLBACK_INVALIDATION_STALE_EVENT")
            );

        assertThat(mappingFenceId()).isEqualTo(newerFence);
        assertThat(rowCount("catalog_asset_availability")).isZero();
        assertThat(rowCount("catalog_asset_availability_event")).isZero();
    }

    @Test
    void prepareFailsClosedWhenRequestedTargetsAreMissingOrOnlyPartiallyResolved() {
        RollbackInvalidationService service = new RollbackInvalidationService(
            repository,
            mock(AuditService.class),
            new ObjectMapper()
        );
        RollbackCommand partialPlan = new RollbackCommand(
            1,
            "task",
            42L,
            null,
            List.of("orders", "missing_orders"),
            false
        );

        assertThatThrownBy(() ->
                inTransaction(() ->
                    service.prepare(new PrepareCommand(partialPlan, SOURCE_ID, "alice", "partial-targets"))
                )
            )
            .isInstanceOfSatisfying(RollbackInvalidationException.class, error ->
                assertThat(error.code()).isEqualTo("ROLLBACK_INVALIDATION_TARGET_NOT_FOUND")
            );
        assertThat(rowCount("integration_rollback_invalidation_receipt")).isZero();
        assertThat(mappingEnabled()).isTrue();

        jdbc.update(
            "update catalog_dataset set source_id = ? where id = ?",
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            DATASET_ID
        );
        assertThatThrownBy(() ->
                inTransaction(() ->
                    service.prepare(new PrepareCommand(plan(), SOURCE_ID, "alice", "mismatched-dataset-source"))
                )
            )
            .isInstanceOfSatisfying(RollbackInvalidationException.class, error ->
                assertThat(error.code()).isEqualTo("ROLLBACK_INVALIDATION_TARGET_NOT_FOUND")
            );
        assertThat(rowCount("integration_rollback_invalidation_receipt")).isZero();
        assertThat(mappingEnabled()).isTrue();

        jdbc.update("delete from infra_ods_table_mapping where id = ?", MAPPING_ID);
        assertThatThrownBy(() ->
                inTransaction(() ->
                    service.prepare(new PrepareCommand(plan(), SOURCE_ID, "alice", "no-targets"))
                )
            )
            .isInstanceOfSatisfying(RollbackInvalidationException.class, error ->
                assertThat(error.code()).isEqualTo("ROLLBACK_INVALIDATION_TARGET_NOT_FOUND")
            );
        assertThat(rowCount("integration_rollback_invalidation_receipt")).isZero();
    }

    @Test
    void reconciliationAuditFailureRollsBackMarkerAndLeavesPreparedFenceTruthful() {
        RollbackInvalidationService prepareService = new RollbackInvalidationService(
            repository,
            mock(AuditService.class),
            new ObjectMapper()
        );
        PreparedInvalidation prepared = inTransaction(() ->
            prepareService.prepare(new PrepareCommand(plan(), SOURCE_ID, "alice", "reconcile-audit"))
        );
        AuditService failingAudit = mock(AuditService.class);
        doThrow(new IllegalStateException("audit unavailable"))
            .when(failingAudit)
            .auditActionStrict(anyString(), any(), anyString(), any());
        RollbackInvalidationService reconcileService = new RollbackInvalidationService(
            repository,
            failingAudit,
            new ObjectMapper()
        );

        assertThatThrownBy(() ->
                inTransaction(() -> reconcileService.markReconciliationRequired(prepared.receiptId(), Map.of()))
            )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("audit unavailable");

        assertThat(receiptState(prepared.receiptId())).isEqualTo(RollbackInvalidationService.PREPARED);
        assertThat(mappingFenceId()).isEqualTo(prepared.receiptId());
        assertAvailability("FENCED", 0L, prepared.sourceSequence());
        assertThat(rowCount("platform_event_outbox")).isOne();
    }

    @Test
    void emptyMigrationRollbackRemovesOnlyRollbackInvalidationInfrastructure() throws Exception {
        rollbackGenerationAttemptsChangelog();
        rollbackChangelog();

        assertThat(tableExists("integration_rollback_invalidation_receipt")).isFalse();
        assertThat(tableExists("integration_rollback_dispatch_outbox")).isFalse();
        assertThat(tableExists("integration_rollback_invalidation_completion_event")).isFalse();
        assertThat(tableExists("catalog_asset_availability_event")).isFalse();
        assertThat(columnExists("infra_ods_table_mapping", "availability_fence_id")).isFalse();
        assertThat(columnExists("platform_event_outbox", "payload_hash")).isFalse();
        assertThat(functionExists("reject_rollback_invalidation_evidence_mutation")).isFalse();
        assertThat(tableExists("infra_ods_table_mapping")).isTrue();
        assertThat(tableExists("platform_event_outbox")).isTrue();
    }

    @Test
    void migrationRollbackIsBlockedOnceFenceOrEvidenceExists() {
        RollbackInvalidationService service = new RollbackInvalidationService(
            repository,
            mock(AuditService.class),
            new ObjectMapper()
        );
        PreparedInvalidation prepared = inTransaction(() ->
            service.prepare(new PrepareCommand(plan(), SOURCE_ID, "alice", "rollback-block"))
        );

        assertThatThrownBy(this::rollbackChangelog)
            .hasStackTraceContaining("ROLLBACK_BLOCKED_ROLLBACK_INVALIDATION_EVIDENCE_EXISTS");

        assertThat(tableExists("integration_rollback_invalidation_receipt")).isTrue();
        assertThat(rowCount("integration_rollback_invalidation_receipt")).isOne();
        assertThat(rowCount("integration_rollback_dispatch_outbox")).isOne();
        assertThat(mappingFenceId()).isEqualTo(prepared.receiptId());
        assertThat(rowCount("catalog_asset_availability_event")).isOne();
    }

    private RollbackCommand plan() {
        return new RollbackCommand(1, "task", 42L, null, List.of("orders"), false);
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    private void assertAvailability(String status, long epoch, long sourceSequence) {
        assertThat(
            jdbc.queryForMap(
                "select status, availability_epoch, source_sequence from catalog_asset_availability"
            )
        )
            .containsEntry("status", status)
            .containsEntry("availability_epoch", epoch)
            .containsEntry("source_sequence", sourceSequence);
    }

    private boolean mappingEnabled() {
        return Boolean.TRUE.equals(
            jdbc.queryForObject(
                "select enabled from infra_ods_table_mapping where id = ?",
                Boolean.class,
                MAPPING_ID
            )
        );
    }

    private UUID mappingFenceId() {
        return jdbc.queryForObject(
            "select availability_fence_id from infra_ods_table_mapping where id = ?",
            UUID.class,
            MAPPING_ID
        );
    }

    private int rowCount(String table) {
        Integer count = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private String receiptState(UUID receiptId) {
        return jdbc.queryForObject(
            "select state from integration_rollback_invalidation_receipt where id = ?",
            String.class,
            receiptId
        );
    }

    private String dispatchStatus(UUID receiptId) {
        return jdbc.queryForObject(
            "select status from integration_rollback_dispatch_outbox where receipt_id = ?",
            String.class,
            receiptId
        );
    }

    private long sourceSequenceLastValue() {
        Long value = jdbc.queryForObject(
            "select last_value from integration_rollback_invalidation_source_seq",
            Long.class
        );
        return value == null ? 0L : value;
    }

    private void assertEvidenceMutationBlocked(String sql) {
        assertThatThrownBy(() -> jdbc.execute(sql))
            .isInstanceOf(DataAccessException.class)
            .hasRootCauseInstanceOf(SQLException.class)
            .rootCause()
            .extracting(error -> ((SQLException) error).getSQLState())
            .isEqualTo("55000");
    }

    private boolean tableExists(String table) {
        return Boolean.TRUE.equals(
            jdbc.queryForObject(
                """
                select exists (
                    select 1 from information_schema.tables
                     where table_schema = current_schema() and table_name = ?
                )
                """,
                Boolean.class,
                table
            )
        );
    }

    private boolean columnExists(String table, String column) {
        return Boolean.TRUE.equals(
            jdbc.queryForObject(
                """
                select exists (
                    select 1 from information_schema.columns
                     where table_schema = current_schema() and table_name = ? and column_name = ?
                )
                """,
                Boolean.class,
                table,
                column
            )
        );
    }

    private boolean functionExists(String function) {
        return Boolean.TRUE.equals(
            jdbc.queryForObject(
                """
                select exists (
                    select 1
                      from pg_proc p
                      join pg_namespace n on n.oid = p.pronamespace
                     where n.nspname = current_schema() and p.proname = ?
                )
                """,
                Boolean.class,
                function
            )
        );
    }

    private void insertMapping() {
        jdbc.update(
            """
            insert into catalog_dataset (id, name, source_id, hive_database, hive_table)
            values (?, 'orders', ?, 'ods', 'orders')
            """,
            DATASET_ID,
            SOURCE_ID
        );
        jdbc.update(
            """
            insert into infra_ods_table_mapping (
                id, connection_id, stream_name, stream_namespace, system_code, biz_code,
                entity_code, ods_schema, ods_table, dataset_id, enabled, created_date,
                last_modified_date
            ) values (?, ?, 'orders', 'sales', 'erp', 'sales', 'order', 'ods', 'orders',
                      ?, true, current_timestamp, current_timestamp)
            """,
            MAPPING_ID,
            SOURCE_ID,
            DATASET_ID
        );
    }

    private void createPrerequisiteTables() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(
                """
                create table catalog_dataset (
                    id uuid primary key,
                    name varchar(128),
                    source_id uuid,
                    hive_database varchar(256),
                    hive_table varchar(256)
                )
                """
            );
            statement.execute(
                """
                create table infra_ods_table_mapping (
                    id uuid primary key,
                    connection_id uuid not null,
                    stream_name varchar(256) not null,
                    stream_namespace varchar(256),
                    system_code varchar(64) not null,
                    biz_code varchar(64) not null,
                    entity_code varchar(128) not null,
                    ods_schema varchar(128) not null,
                    ods_table varchar(128) not null,
                    dataset_id uuid,
                    enabled boolean not null default true,
                    created_date timestamp,
                    last_modified_date timestamp
                )
                """
            );
            statement.execute(
                """
                create table platform_event_outbox (
                    id uuid primary key,
                    event_id varchar(80) not null unique,
                    event_type varchar(128) not null,
                    domain varchar(64) not null,
                    source_app varchar(64) not null,
                    aggregate_type varchar(96),
                    aggregate_id varchar(128),
                    action varchar(64) not null,
                    severity varchar(32) not null,
                    status varchar(32) not null,
                    occurred_at timestamp not null,
                    actor varchar(128),
                    audit_action_code varchar(128),
                    payload_json text,
                    dispatch_status varchar(32) not null,
                    dispatch_attempts integer not null,
                    created_by varchar(50),
                    created_date timestamp,
                    last_modified_by varchar(50),
                    last_modified_date timestamp
                )
                """
            );
        }
    }

    private void applyChangelog() throws Exception {
        updateChangelog(CHANGELOG);
        updateChangelog(GENERATION_ATTEMPTS_CHANGELOG);
    }

    private void updateChangelog(String changelog) throws Exception {
        try (
            Connection connection = dataSource.getConnection();
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()
        ) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try {
                database.setDefaultSchemaName(schema);
                database.setLiquibaseSchemaName(schema);
                try (Liquibase liquibase = new Liquibase(changelog, resources, database)) {
                    liquibase.update(new Contexts(), new LabelExpression());
                }
            } finally {
                if (!database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private void rollbackGenerationAttemptsChangelog() throws Exception {
        rollbackChangelog(GENERATION_ATTEMPTS_CHANGELOG);
    }

    private void rollbackChangelog() throws Exception {
        rollbackChangelog(CHANGELOG);
    }

    private void rollbackChangelog(String changelog) throws Exception {
        try (
            Connection connection = dataSource.getConnection();
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()
        ) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try {
                database.setDefaultSchemaName(schema);
                database.setLiquibaseSchemaName(schema);
                try (Liquibase liquibase = new Liquibase(changelog, resources, database)) {
                    liquibase.rollback(1, new Contexts(), new LabelExpression());
                }
            } finally {
                if (!database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private DataSource schemaDataSource() {
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        return new DriverManagerDataSource(
            POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
    }

    private Connection rootConnection() throws SQLException {
        return java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
    }
}
