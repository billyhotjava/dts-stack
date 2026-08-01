package com.yuzhi.dts.ingestion.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.ingestion.config.InfraSecurityProperties;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.domain.IngestionTaskRevision;
import com.yuzhi.dts.ingestion.service.IngestionAccessContractService;
import com.yuzhi.dts.ingestion.service.IngestionRequiresNewExecutor;
import com.yuzhi.dts.ingestion.service.IngestionTaskSecretMigrationService;
import com.yuzhi.dts.ingestion.service.audit.IngestionSecretRestoreAuditOutboxRepository;
import com.yuzhi.dts.ingestion.service.audit.IngestionSecretRestoreAuditService;
import com.yuzhi.dts.ingestion.service.infra.InfraSettingsCryptoService;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class IngestionAccessContractPostgresMigrationIT {

    private static final String CHANGELOG = "config/liquibase/ingestion-access-contract-postgres-it.xml";
    private static final int CHANGESET_COUNT = 10;
    private static final int PRE_SCHEDULED_INDEX_CHANGESET_COUNT = 6;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("ingestion_access_contract_it")
        .withUsername("ingestion_access_contract_test")
        .withPassword("ingestion_access_contract_test");

    @BeforeEach
    void createProductionLegacySchema() throws SQLException {
        execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        execute("""
            CREATE TABLE ingestion_task (
                id bigint PRIMARY KEY,
                name varchar(255),
                description text,
                source_type varchar(100),
                source_data_source_id bigint,
                destination_type varchar(100),
                sync_mode varchar(50),
                sync_schedule varchar(255),
                table_mapping jsonb,
                sync_config jsonb,
                classification_seal jsonb,
                field_classifications jsonb,
                airflow_enabled boolean,
                created_by varchar(100),
                created_date timestamp with time zone
            )
            """);
        execute("""
            CREATE TABLE ingestion_execution (
                id bigint PRIMARY KEY,
                task_id bigint NOT NULL REFERENCES ingestion_task(id),
                execution_id varchar(200),
                status varchar(50)
            )
            """);
        execute("""
            INSERT INTO ingestion_task(
                id, name, source_type, source_data_source_id, destination_type,
                sync_mode, sync_schedule, table_mapping, sync_config,
                classification_seal, field_classifications, airflow_enabled,
                created_by, created_date
            ) VALUES (
                7, 'legacy-db-task', 'mysqlreader', 31, 'postgresqlwriter',
                'full_refresh', '0 0 * * *', '[]'::jsonb, '{}'::jsonb,
                '{"effectiveLevel":"INTERNAL"}'::jsonb, '{}'::jsonb, true,
                'legacy-user', CURRENT_TIMESTAMP
            )
            """);
        execute("INSERT INTO ingestion_execution(id, task_id, execution_id, status) VALUES (71, 7, 'legacy-run', 'success')");
    }

    @Test
    void migrationShouldApplyRollbackAndReapplyWithoutRebindingLegacyExecutions() throws Exception {
        withLiquibase(liquibase -> {
            liquibase.update(new Contexts(), new LabelExpression());
            assertAppliedContract();

            liquibase.rollback(CHANGESET_COUNT, new Contexts(), new LabelExpression());
            assertThat(queryLong("SELECT count(*) FROM ingestion_task WHERE id = 7")).isEqualTo(1L);
            assertThat(queryLong("SELECT count(*) FROM ingestion_execution WHERE id = 71")).isEqualTo(1L);
            assertThat(queryLong("SELECT count(*) FROM information_schema.tables WHERE table_name = 'ingestion_task_revision'"))
                .isZero();
            assertThat(queryLong("SELECT count(*) FROM information_schema.tables WHERE table_name = 'ingestion_task_secret_migration'"))
                .isZero();
            assertThat(queryLong("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'ingestion_execution' AND column_name = 'task_revision_id'
                """))
                .isZero();

            liquibase.update(new Contexts(), new LabelExpression());
            assertAppliedContract();
        });
    }

    @Test
    void migrationShouldReplaceSameNameInvalidIndexWithValidUniqueIndex() throws Exception {
        withLiquibase(liquibase -> {
            liquibase.update(PRE_SCHEDULED_INDEX_CHANGESET_COUNT, new Contexts(), new LabelExpression());
            seedDuplicateScheduledRun();

            assertThatThrownBy(() -> execute(scheduledIndexCreateSql()))
                .isInstanceOf(SQLException.class);
            assertThat(queryLong("""
                SELECT count(*)
                FROM pg_index i
                JOIN pg_class c ON c.oid = i.indexrelid
                WHERE c.relname = 'uk_ingestion_execution_scheduled_run'
                  AND NOT i.indisvalid
                """))
                .isEqualTo(1L);

            execute("DELETE FROM ingestion_execution WHERE id = 72");
            liquibase.update(new Contexts(), new LabelExpression());

            assertScheduledIndexValidAndRecorded();
        });
    }

    @Test
    void migrationShouldFailClosedOnDuplicatesAndSucceedOnCleanRetry() throws Exception {
        withLiquibase(liquibase -> {
            liquibase.update(PRE_SCHEDULED_INDEX_CHANGESET_COUNT, new Contexts(), new LabelExpression());
            seedDuplicateScheduledRun();

            assertThatThrownBy(() -> liquibase.update(new Contexts(), new LabelExpression()))
                .isInstanceOf(LiquibaseException.class)
                .hasMessageContaining("INGESTION_SCHEDULED_EXECUTION_DUPLICATES_EXIST");
            assertThat(queryLong("""
                SELECT count(*) FROM databasechangelog
                WHERE id = '20260801-04-ingestion-scheduled-execution-idempotency'
                """))
                .isZero();
            assertThat(queryLong("""
                SELECT count(*) FROM pg_class
                WHERE relkind = 'i' AND relname = 'uk_ingestion_execution_scheduled_run'
                """))
                .isZero();

            execute("DELETE FROM ingestion_execution WHERE id = 72");
            liquibase.update(new Contexts(), new LabelExpression());

            assertScheduledIndexValidAndRecorded();
        });
    }

    @Test
    void taskThenRevisionLockOrderShouldNotDeadlockAndShouldPreserveWinner() throws Exception {
        withLiquibase(liquibase -> liquibase.update(new Contexts(), new LabelExpression()));
        seedSealedDraftRevision();
        long revisionId = queryLong("SELECT id FROM ingestion_task_revision WHERE task_id=7 AND revision_number=2");
        CountDownLatch winnerHasTaskLock = new CountDownLatch(1);
        CountDownLatch contenderReadTaskId = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> winner = executor.submit(() -> {
                try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                    connection.setAutoCommit(false);
                    statement.execute("SET LOCAL lock_timeout='5s'");
                    statement.executeQuery("SELECT id FROM ingestion_task WHERE id=7 FOR UPDATE").close();
                    winnerHasTaskLock.countDown();
                    assertThat(contenderReadTaskId.await(5, TimeUnit.SECONDS)).isTrue();
                    statement.executeQuery(
                        "SELECT id FROM ingestion_task_revision WHERE id=" + revisionId + " FOR UPDATE"
                    ).close();
                    statement.executeUpdate(
                        "UPDATE ingestion_task_revision SET state='ACTIVE' WHERE id=" + revisionId
                    );
                    connection.commit();
                    return "ACTIVE";
                }
            });
            Future<String> contender = executor.submit(() -> {
                assertThat(winnerHasTaskLock.await(5, TimeUnit.SECONDS)).isTrue();
                try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                    connection.setAutoCommit(false);
                    statement.execute("SET LOCAL lock_timeout='5s'");
                    try (ResultSet task = statement.executeQuery(
                        "SELECT task_id FROM ingestion_task_revision WHERE id=" + revisionId
                    )) {
                        task.next();
                        assertThat(task.getLong(1)).isEqualTo(7L);
                    }
                    contenderReadTaskId.countDown();
                    statement.executeQuery("SELECT id FROM ingestion_task WHERE id=7 FOR UPDATE").close();
                    String state;
                    try (ResultSet revision = statement.executeQuery(
                        "SELECT state FROM ingestion_task_revision WHERE id=" + revisionId + " FOR UPDATE"
                    )) {
                        revision.next();
                        state = revision.getString(1);
                    }
                    connection.commit();
                    return state;
                }
            });

            assertThat(winner.get(10, TimeUnit.SECONDS)).isEqualTo("ACTIVE");
            assertThat(contender.get(10, TimeUnit.SECONDS)).isEqualTo("ACTIVE");
            assertThat(queryText("SELECT state FROM ingestion_task_revision WHERE id=" + revisionId))
                .isEqualTo("ACTIVE");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void compatibilityRestoreShouldSurviveMigrationAndRemainReadableForOldVersion() throws Exception {
        withLiquibase(liquibase -> liquibase.update(new Contexts(), new LabelExpression()));
        execute("UPDATE ingestion_task_secret_migration SET status='CLEAN' WHERE task_id=7");
        ObjectMapper objectMapper = new ObjectMapper();
        InfraSecurityProperties properties = new InfraSecurityProperties();
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
        ));
        properties.setKeyVersion("restore-it-v1");
        InfraSettingsCryptoService crypto = new InfraSettingsCryptoService(properties);
        crypto.init();
        UUID sourceId = UUID.randomUUID();
        IngestionTask task = new IngestionTask();
        task.setId(7L);
        task.setSourceType("mysqlreader");
        task.setSourceDataSourceId(sourceId);
        task.setDestinationType("postgresqlwriter");
        task.setSourceConfig(objectMapper.createObjectNode().put("schema", "finance"));
        task.setDestinationConfig(objectMapper.createObjectNode().put("table", "ods_finance"));
        task.setAddaxConfig(objectMapper.createObjectNode().put("channel", 1));
        ObjectNode runtime = objectMapper.createObjectNode();
        runtime.put("sourceType", "mysqlreader");
        runtime.put("sourceDataSourceId", sourceId.toString());
        runtime.put("destinationType", "postgresqlwriter");
        runtime.set("sourceConfig", objectMapper.createObjectNode()
            .put("schema", "finance")
            .put("password", "old-version-secret"));
        runtime.set("destinationConfig", task.getDestinationConfig());
        runtime.set("addaxConfig", task.getAddaxConfig());
        byte[] iv = crypto.randomIv();
        IngestionTaskRevision revision = new IngestionTaskRevision();
        revision.setId(queryLong(
            "SELECT id FROM ingestion_task_revision WHERE task_id=7 AND revision_number=1"
        ));
        revision.setRevisionNumber(2);
        revision.setRuntimeSnapshot(crypto.encryptStrict(objectMapper.writeValueAsBytes(runtime), iv));
        revision.setRuntimeSnapshotIv(iv);
        revision.setRuntimeSnapshotKeyVersion(crypto.currentKeyVersion());
        revision.setEffectiveConfigChecksum("restore-checksum");
        ObjectNode evidence = runtime.deepCopy();
        ((ObjectNode) evidence.get("sourceConfig")).remove("password");
        revision.setTaskSnapshot(evidence);
        IngestionTaskRepository taskRepository = mock(IngestionTaskRepository.class);
        IngestionTaskRevisionRepository revisionRepository = mock(IngestionTaskRevisionRepository.class);
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));
        when(taskRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(task));
        when(revisionRepository.findAllByTaskIdOrderByRevisionNumberDesc(7L)).thenReturn(List.of(revision));
        when(revisionRepository.findAllByTaskIdForUpdate(7L)).thenReturn(List.of(revision));
        IngestionAccessContractService accessContractService = mock(IngestionAccessContractService.class);
        IngestionTaskSecretMigrationService migration = new IngestionTaskSecretMigrationService(
            postgresJdbcTemplate(),
            taskRepository,
            revisionRepository,
            accessContractService,
            crypto,
            new IngestionRequiresNewExecutor(),
            new IngestionSecretRestoreAuditService(
                new IngestionSecretRestoreAuditOutboxRepository(postgresJdbcTemplate()),
                objectMapper,
                "default"
            ),
            objectMapper
        );

        assertThat(migration.dryRunCompatibilityRestore(10, "xiezm").ready()).isEqualTo(1);
        assertThat(migration.restoreCompatibilityBatch("downgrade-it", 10, "xiezm").restored()).isEqualTo(1);
        assertThat(migration.restoreCompatibilityBatch("downgrade-it", 10, "xiezm").examined()).isZero();
        assertThat(task.getSourceConfig().path("password").asText()).isEqualTo("old-version-secret");
        assertThat(queryText("SELECT status FROM ingestion_task_secret_migration WHERE task_id=7"))
            .isEqualTo("RESTORED_COMPAT");
        assertThatThrownBy(() -> migration.requireTaskReady(7L))
            .hasMessage("INGESTION_TASK_SECRET_MIGRATION_RESTORED_COMPAT");
        assertThat(migration.migratePendingTasks()).isZero();
        assertThat(task.getSourceConfig().path("password").asText()).isEqualTo("old-version-secret");
        assertThat(queryLong(
            "SELECT count(*) FROM ingestion_secret_restore_audit_outbox WHERE actor='xiezm' " +
                "AND classification='SENSITIVE_CONFIGURATION_RECOVERY' AND batch_id='downgrade-it'"
        )).isEqualTo(4L);
        verify(taskRepository).saveAndFlush(task);
        verify(accessContractService, never()).materializeExecutionTask(any(), any());
    }

    @Test
    void secretRestoreAuditOutboxShouldBeAnImmutableDurableLedger() throws Exception {
        withLiquibase(liquibase -> {
            liquibase.update(new Contexts(), new LabelExpression());
            JdbcTemplate jdbcTemplate = postgresJdbcTemplate();
            IngestionSecretRestoreAuditOutboxRepository repository =
                new IngestionSecretRestoreAuditOutboxRepository(jdbcTemplate);
            UUID auditId = new IngestionSecretRestoreAuditService(repository, new ObjectMapper(), "tenant-ledger")
                .recordAttempt("ledger-admin", "ledger-batch", false, 7L, null, "ledger-checksum");
            String evidenceBefore = auditEvidenceDigest(auditId);

            assertThatThrownBy(() -> execute("""
                UPDATE ingestion_secret_restore_audit_outbox
                SET event_id='tampered-event', tenant_id='tampered-tenant', actor='tampered-actor',
                    task_id=999, revision_id=999, config_checksum='tampered-checksum',
                    payload_json='{}', payload_hash=repeat('f', 64),
                    created_at=created_at + interval '1 second'
                WHERE id='%s'
                """.formatted(auditId)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("INGESTION_SECRET_RESTORE_AUDIT_EVIDENCE_IMMUTABLE");
            assertThatThrownBy(() -> execute("""
                UPDATE ingestion_secret_restore_audit_outbox
                SET delivery_status='DELIVERED', delivered_at=CURRENT_TIMESTAMP
                WHERE id='%s'
                """.formatted(auditId)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("INGESTION_SECRET_RESTORE_AUDIT_DELIVERY_TRANSITION_INVALID");

            var claimed = repository.claimNext(Instant.now().minusSeconds(300)).orElseThrow();
            assertThat(claimed.id()).isEqualTo(auditId);
            repository.markDelivered(auditId, claimed.deliveryAttempts());
            assertThat(queryText(
                "SELECT delivery_status FROM ingestion_secret_restore_audit_outbox WHERE id='" + auditId + "'"
            )).isEqualTo("DELIVERED");
            assertThat(auditEvidenceDigest(auditId)).isEqualTo(evidenceBefore);

            assertThatThrownBy(() -> execute(
                "DELETE FROM ingestion_secret_restore_audit_outbox WHERE id='" + auditId + "'"
            ))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("INGESTION_SECRET_RESTORE_AUDIT_DELETE_FORBIDDEN");
            assertThatThrownBy(() -> execute("TRUNCATE TABLE ingestion_secret_restore_audit_outbox"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("INGESTION_SECRET_RESTORE_AUDIT_TRUNCATE_FORBIDDEN");

            assertThatThrownBy(() -> liquibase.rollback(1, new Contexts(), new LabelExpression()))
                .isInstanceOf(LiquibaseException.class)
                .hasMessageContaining("ROLLBACK_BLOCKED_INGESTION_SECRET_RESTORE_AUDIT_EVIDENCE_EXISTS");
            assertThat(queryLong(
                "SELECT count(*) FROM ingestion_secret_restore_audit_outbox WHERE id='" + auditId + "'"
            )).isEqualTo(1L);
            assertThat(queryLong("""
                SELECT count(*) FROM databasechangelog
                WHERE id='20260801-07-ingestion-secret-restore-audit-outbox' AND exectype='EXECUTED'
                """)).isEqualTo(1L);
        });
    }

    @Test
    void staleAuditClaimGenerationShouldNotOverwriteReclaimedWorker() throws Exception {
        withLiquibase(liquibase -> {
            liquibase.update(new Contexts(), new LabelExpression());
            JdbcTemplate jdbcTemplate = postgresJdbcTemplate();
            IngestionSecretRestoreAuditOutboxRepository repository =
                new IngestionSecretRestoreAuditOutboxRepository(jdbcTemplate);
            IngestionSecretRestoreAuditService auditService =
                new IngestionSecretRestoreAuditService(repository, new ObjectMapper(), "tenant-fence");

            assertStaleTerminalRejected(repository, auditService, "deliver-fence", "DELIVERED");
            assertStaleTerminalRejected(repository, auditService, "retry-fence", "RETRY");
            assertStaleTerminalRejected(repository, auditService, "dead-fence", "DEAD");
        });
    }

    private void assertStaleTerminalRejected(
        IngestionSecretRestoreAuditOutboxRepository repository,
        IngestionSecretRestoreAuditService auditService,
        String batchId,
        String terminal
    ) throws SQLException {
        UUID auditId = auditService.recordAttempt("fence-admin", batchId, false, 7L, null, "fence-checksum");
        var oldClaim = repository.claimNext(Instant.now().minusSeconds(300)).orElseThrow();
        assertThat(oldClaim.id()).isEqualTo(auditId);
        var reclaimed = repository.claimNext(Instant.now().plusSeconds(60)).orElseThrow();
        assertThat(reclaimed.id()).isEqualTo(auditId);
        assertThat(reclaimed.deliveryAttempts()).isEqualTo(oldClaim.deliveryAttempts() + 1);

        assertThatThrownBy(() -> {
            switch (terminal) {
                case "DELIVERED" -> repository.markDelivered(auditId, oldClaim.deliveryAttempts());
                case "RETRY" -> repository.markRetry(
                    auditId,
                    oldClaim.deliveryAttempts(),
                    Instant.now().plusSeconds(30),
                    "STALE_RETRY"
                );
                case "DEAD" -> repository.markDead(auditId, oldClaim.deliveryAttempts(), "STALE_DEAD");
                default -> throw new IllegalArgumentException("unsupported terminal");
            }
        })
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("INGESTION_SECRET_RESTORE_AUDIT_STALE_CLAIM");
        assertThat(queryText(
            "SELECT delivery_status FROM ingestion_secret_restore_audit_outbox WHERE id='" + auditId + "'"
        )).isEqualTo("CLAIMED");
        assertThat(queryLong(
            "SELECT delivery_attempts FROM ingestion_secret_restore_audit_outbox WHERE id='" + auditId + "'"
        )).isEqualTo(reclaimed.deliveryAttempts());

        repository.markDelivered(auditId, reclaimed.deliveryAttempts());
    }

    private String auditEvidenceDigest(UUID auditId) throws SQLException {
        return queryText("""
            SELECT md5(concat_ws('|',
                id::text, event_id, tenant_id, actor, event_type, classification, batch_id,
                coalesce(task_id::text, ''), coalesce(revision_id::text, ''),
                coalesce(config_checksum, ''), outcome, coalesce(error_code, ''),
                payload_hash, payload_json, created_at::text
            ))
            FROM ingestion_secret_restore_audit_outbox
            WHERE id='%s'
            """.formatted(auditId));
    }

    private void withLiquibase(LiquibaseWork work) throws Exception {
        try (
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
            Connection connection = connection()
        ) {
            Database database = null;
            try {
                database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                try (Liquibase liquibase = new Liquibase(CHANGELOG, resources, database)) {
                    work.run(liquibase);
                }
            } finally {
                if (database != null && !database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    @FunctionalInterface
    private interface LiquibaseWork {
        void run(Liquibase liquibase) throws Exception;
    }

    private void seedDuplicateScheduledRun() throws SQLException {
        execute("UPDATE ingestion_execution SET airflow_dag_id = 'dag-race', execution_id = 'run-race' WHERE id = 71");
        execute("""
            INSERT INTO ingestion_execution(id, task_id, execution_id, airflow_dag_id, status)
            VALUES (72, 7, 'run-race', 'dag-race', 'running')
            """);
    }

    private void seedSealedDraftRevision() throws SQLException {
        execute("""
            INSERT INTO ingestion_task_revision(
                task_id, revision_number, state, source_kind, task_snapshot,
                effective_config, effective_config_checksum, default_policy_version,
                default_policy_checksum, runtime_snapshot, runtime_snapshot_iv,
                runtime_snapshot_key_version, created_by, created_at
            ) VALUES (
                7, 2, 'DRAFT', 'database', '{}'::jsonb,
                '{}'::jsonb, repeat('a', 64), 1,
                repeat('b', 64), decode('01', 'hex'), decode('02', 'hex'),
                'v1', 'lock-order-it', CURRENT_TIMESTAMP
            )
            """);
    }

    private String scheduledIndexCreateSql() {
        return """
            CREATE UNIQUE INDEX CONCURRENTLY uk_ingestion_execution_scheduled_run
            ON ingestion_execution(task_id, airflow_dag_id, execution_id)
            WHERE task_id IS NOT NULL AND airflow_dag_id IS NOT NULL AND execution_id IS NOT NULL
            """;
    }

    private void assertScheduledIndexValidAndRecorded() throws SQLException {
        assertTaskRevisionIndexHardened();
        assertThat(queryLong("""
            SELECT count(*) FROM ingestion_task_secret_migration
            WHERE task_id = 7 AND status = 'PENDING' AND error_code IS NULL
            """))
            .isEqualTo(1L);
        assertThat(queryLong("""
            SELECT count(*) FROM pg_constraint
            WHERE conname = 'fk_ingestion_task_secret_migration_task'
            """))
            .isEqualTo(1L);
        assertThat(queryLong("""
            SELECT count(*)
            FROM pg_index i
            JOIN pg_class c ON c.oid = i.indexrelid
            WHERE c.relname = 'uk_ingestion_execution_scheduled_run'
              AND i.indisvalid
              AND i.indisunique
            """))
            .isEqualTo(1L);
        assertThat(queryLong("""
            SELECT count(*) FROM databasechangelog
            WHERE id = '20260801-04-ingestion-scheduled-execution-idempotency'
              AND author = 'codex'
              AND exectype = 'EXECUTED'
            """))
            .isEqualTo(1L);
        assertThat(queryLong("SELECT count(*) FROM databasechangelog")).isEqualTo(CHANGESET_COUNT);
    }

    private void assertTaskRevisionIndexHardened() throws SQLException {
        assertThat(queryText("""
            SELECT pg_get_expr(indpred, indrelid)
            FROM pg_index
            WHERE indexrelid = 'idx_ingestion_execution_task_revision'::regclass
              AND indisvalid
            """))
            .contains("task_revision_id IS NOT NULL");
    }

    private void assertAppliedContract() throws SQLException {
        assertThat(queryLong("SELECT count(*) FROM ingestion_task_revision WHERE task_id = 7 AND state = 'LEGACY_UNSEALED'"))
            .isEqualTo(1L);
        assertThat(queryText("""
            SELECT pg_get_constraintdef(oid) FROM pg_constraint
            WHERE conname='ck_ingestion_task_secret_migration_status'
            """))
            .contains("RESTORED_COMPAT");
        assertThat(queryLong("""
            SELECT count(*) FROM ingestion_task_revision
            WHERE task_id = 7
              AND runtime_snapshot IS NULL
              AND runtime_snapshot_iv IS NULL
              AND runtime_snapshot_key_version IS NULL
            """))
            .isEqualTo(1L);
        assertThat(queryLong("""
            SELECT count(*) FROM ingestion_execution
            WHERE id = 71
              AND task_revision_id IS NULL
              AND revision_number IS NULL
              AND effective_config_checksum IS NULL
            """))
            .isEqualTo(1L);
        assertThat(queryLong("""
            SELECT count(*)
            FROM pg_constraint
            WHERE conname = 'fk_ingestion_execution_task_revision' AND convalidated
            """))
            .isEqualTo(1L);
        assertThat(queryLong("""
            SELECT count(*)
            FROM pg_index
            WHERE indexrelid = 'idx_ingestion_execution_task_revision'::regclass
              AND indisvalid
            """))
            .isEqualTo(1L);
        assertTaskRevisionIndexHardened();
        assertThat(queryText("""
            SELECT pg_get_expr(indpred, indrelid)
            FROM pg_index
            WHERE indexrelid = 'uk_ingestion_execution_scheduled_run'::regclass
            """))
            .contains("task_id IS NOT NULL", "airflow_dag_id IS NOT NULL", "execution_id IS NOT NULL");

        execute("""
            INSERT INTO ingestion_task_revision(
                task_id, revision_number, state, source_kind, task_snapshot,
                effective_config, effective_config_checksum, default_policy_version,
                default_policy_checksum, runtime_snapshot, runtime_snapshot_iv,
                runtime_snapshot_key_version, created_by, created_at,
                dag_deployment_status, staged_dag_path, published_dag_path,
                dag_deployment_updated_at
            ) VALUES (
                7, 2, 'DRAFT', 'database', '{}'::jsonb,
                '{}'::jsonb, repeat('a', 64), 1,
                repeat('b', 64), decode('01', 'hex'), decode('02', 'hex'),
                'v1', 'migration-it', CURRENT_TIMESTAMP,
                'STAGED', '/tmp/task-r2.staged', '/airflow/task-r2.py',
                CURRENT_TIMESTAMP
            )
            """);

        assertThatThrownBy(() -> execute("UPDATE ingestion_access_default_policy SET status = 'UNKNOWN'"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE ingestion_access_default_policy SET version = 0"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE ingestion_task_revision SET state = 'UNKNOWN' WHERE revision_number = 2"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE ingestion_task_revision SET source_kind = 'stream' WHERE revision_number = 2"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE ingestion_task_revision SET revision_number = 0 WHERE revision_number = 2"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE ingestion_task_revision SET default_policy_version = 0 WHERE revision_number = 2"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE ingestion_task_revision SET runtime_snapshot_iv = NULL WHERE revision_number = 2"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE ingestion_task_revision SET dag_deployment_status = 'UNKNOWN' WHERE revision_number = 2"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE ingestion_task_revision SET published_dag_path = NULL WHERE revision_number = 2"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE ingestion_execution SET revision_number = -1 WHERE id = 71"))
            .isInstanceOf(SQLException.class);
        execute("UPDATE ingestion_execution SET airflow_dag_id = 'dag-r2', execution_id = 'run-r2' WHERE id = 71");
        assertThatThrownBy(() -> execute("""
            INSERT INTO ingestion_execution(id, task_id, execution_id, airflow_dag_id, status)
            VALUES (72, 7, 'run-r2', 'dag-r2', 'running')
            """))
            .isInstanceOf(SQLException.class);
    }

    private static Connection connection() throws SQLException {
        Connection connection = POSTGRES.createConnection("");
        connection.setAutoCommit(true);
        return connection;
    }

    private static JdbcTemplate postgresJdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long queryLong(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static String queryText(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }
}
