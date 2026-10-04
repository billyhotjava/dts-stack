package com.yuzhi.dts.ingestion.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class IngestionRollbackSagaPostgresMigrationIT {

    private static final String CHANGELOG = "config/liquibase/ingestion-rollback-saga-postgres-it.xml";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("ingestion_rollback_saga_it")
        .withUsername("ingestion_rollback_saga_test")
        .withPassword("ingestion_rollback_saga_test");

    @BeforeEach
    void resetSchema() throws SQLException {
        execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
    }

    @Test
    void migrationCreatesConstrainedOutboxAndAppendOnlyEvidence() throws Exception {
        withLiquibase(liquibase -> liquibase.update(new Contexts(), new LabelExpression()));
        UUID receipt = UUID.randomUUID();
        execute("""
            INSERT INTO ingestion_rollback_operation(
                receipt_id,idempotency_key,request_hash,payload_hash,level,scope,
                task_id,source_data_source_id,fence_sequence,operator,status,side_effects_applied,
                request_json,impact_json,created_at,updated_at,lock_version
            ) VALUES (
                '%s','platform:%s','%s','%s',2,'task',42,'%s',7,'operator','EXECUTING',false,
                '{}','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
            )
            """.formatted(receipt, receipt, "a".repeat(64), "b".repeat(64), UUID.randomUUID()));
        UUID levelOneReceipt = UUID.randomUUID();
        execute("""
            INSERT INTO ingestion_rollback_operation(
                receipt_id,idempotency_key,request_hash,payload_hash,level,scope,
                task_id,source_data_source_id,fence_sequence,operator,status,side_effects_applied,
                request_json,impact_json,created_at,updated_at,lock_version
            ) VALUES (
                '%s','platform:%s','%s','%s',1,'task',43,'%s',9,'operator','EXECUTING',false,
                '{}','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
            )
            """.formatted(
                levelOneReceipt,
                levelOneReceipt,
                "f".repeat(64),
                "0".repeat(64),
                UUID.randomUUID()
            ));
        execute("""
            INSERT INTO ingestion_rollback_affected_object(
                operation_receipt_id,object_type,object_ref,action,phase,status,evidence_json,evidence_hash,recorded_at
            ) VALUES ('%s','TABLE','public.orders','DROP','PLANNED','PLANNED','{}','%s',CURRENT_TIMESTAMP)
            """.formatted(receipt, "c".repeat(64)));
        execute("""
            INSERT INTO rollback_audit_log(
                operator,level,scope,operation_receipt_id,event_hash,reason_code,status,created_at
            ) VALUES ('operator',2,'task','%s','%s','ROLLBACK_APPLIED','SUCCESS',CURRENT_TIMESTAMP)
            """.formatted(receipt, "d".repeat(64)));
        execute("""
            INSERT INTO ingestion_rollback_outbox(
                id,operation_receipt_id,event_id,event_hash,source_sequence,outcome,payload_json,status,
                attempt_count,next_attempt_at,created_at,updated_at,lock_version
            ) VALUES ('%s','%s','rollback:%s:8','%s',8,'APPLY','{}','PENDING',0,
                CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
            """.formatted(UUID.randomUUID(), receipt, receipt, "e".repeat(64)));

        assertThatThrownBy(() -> execute("UPDATE rollback_audit_log SET status='FAILED'"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ROLLBACK_EVIDENCE_APPEND_ONLY");
        assertThatThrownBy(() -> execute("TRUNCATE rollback_audit_log"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ROLLBACK_EVIDENCE_APPEND_ONLY");
        assertThatThrownBy(() -> execute("DELETE FROM ingestion_rollback_affected_object"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ROLLBACK_EVIDENCE_APPEND_ONLY");
        assertThatThrownBy(() -> execute("TRUNCATE ingestion_rollback_affected_object"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ROLLBACK_EVIDENCE_APPEND_ONLY");
        assertThat(queryLong("SELECT count(*) FROM ingestion_rollback_outbox WHERE status='PENDING'"))
            .isEqualTo(1L);
        assertThatThrownBy(() -> execute("UPDATE ingestion_rollback_operation SET completion_sequence=7 WHERE receipt_id='" + receipt + "'"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ck_ingestion_rollback_operation_fence");
        assertThatThrownBy(() -> execute("UPDATE ingestion_rollback_operation SET task_id=NULL WHERE receipt_id='" + receipt + "'"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ck_ingestion_rollback_operation_scope_task");
        assertThat(queryLong("SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() AND indexname='idx_ingestion_rollback_outbox_operation_sequence'"))
            .isEqualTo(1L);
    }

    @Test
    void forwardMigrationAddsTruncateGuardsToAnExistingSagaSchema() throws Exception {
        withLiquibase(liquibase -> liquibase.update(2, new Contexts(), new LabelExpression()));
        assertThat(queryString("""
            SELECT md5sum
              FROM databasechangelog
             WHERE id = '20260801-04-ingestion-rollback-saga-outbox'
               AND author = 'codex'
            """))
            .isEqualTo("9:0cd3b900585c20a83a28fb84cdbcf79f");
        assertThat(queryLong("""
            SELECT count(*)
              FROM pg_trigger
             WHERE tgname IN (
                 'trg_rollback_audit_no_truncate',
                 'trg_ingestion_rollback_evidence_no_truncate'
             )
               AND NOT tgisinternal
            """))
            .isZero();

        withLiquibase(liquibase -> liquibase.update(new Contexts(), new LabelExpression()));

        assertThat(queryLong("""
            SELECT count(*)
              FROM pg_trigger
             WHERE tgname IN (
                 'trg_rollback_audit_no_truncate',
                 'trg_ingestion_rollback_evidence_no_truncate'
             )
               AND NOT tgisinternal
            """))
            .isEqualTo(2L);
    }

    @Test
    void acceptsPreviouslyDeployedTruncateGuardChecksum() throws Exception {
        withLiquibase(liquibase -> liquibase.update(2, new Contexts(), new LabelExpression()));
        execute("""
            UPDATE databasechangelog
               SET md5sum = '9:74731bf50dea3b48c98c65fcfbb7a033'
             WHERE id = '20260801-04-ingestion-rollback-saga-outbox'
               AND author = 'codex'
            """);
        createTruncateGuards();

        withLiquibase(liquibase -> liquibase.update(new Contexts(), new LabelExpression()));

        assertThat(queryLong("""
            SELECT count(*)
              FROM databasechangelog
             WHERE id IN (
                 '20260801-04-ingestion-rollback-saga-outbox',
                 '20260801-06-ingestion-rollback-truncate-guards'
             )
               AND author = 'codex'
            """))
            .isEqualTo(2L);
        assertThat(queryLong("""
            SELECT count(*)
              FROM pg_trigger
             WHERE tgname IN (
                 'trg_rollback_audit_no_truncate',
                 'trg_ingestion_rollback_evidence_no_truncate'
             )
               AND NOT tgisinternal
            """))
            .isEqualTo(2L);
        assertThatThrownBy(() -> execute("TRUNCATE rollback_audit_log"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ROLLBACK_EVIDENCE_APPEND_ONLY");
        assertThatThrownBy(() -> execute("TRUNCATE ingestion_rollback_affected_object"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ROLLBACK_EVIDENCE_APPEND_ONLY");
    }

    @Test
    void migrationFailsClosedOnADisabledTruncateGuard() throws Exception {
        withLiquibase(liquibase -> liquibase.update(2, new Contexts(), new LabelExpression()));
        execute("""
            CREATE TRIGGER trg_rollback_audit_no_truncate
            BEFORE TRUNCATE ON rollback_audit_log
            FOR EACH STATEMENT EXECUTE FUNCTION dts_reject_rollback_evidence_mutation()
            """);
        execute("ALTER TABLE rollback_audit_log DISABLE TRIGGER trg_rollback_audit_no_truncate");

        assertThatThrownBy(() -> withLiquibase(liquibase -> liquibase.update(new Contexts(), new LabelExpression())))
            .isInstanceOf(LiquibaseException.class)
            .hasMessageContaining("INGESTION_ROLLBACK_TRUNCATE_GUARD_CONFLICT");
        assertThat(queryLong("""
            SELECT count(*)
              FROM databasechangelog
             WHERE id = '20260801-06-ingestion-rollback-truncate-guards'
               AND author = 'codex'
            """))
            .isZero();
    }

    @Test
    void migrationFailsClosedOnPartiallyAppliedAuditColumns() throws Exception {
        withLiquibase(liquibase -> liquibase.update(1, new Contexts(), new LabelExpression()));
        execute("ALTER TABLE rollback_audit_log ADD COLUMN operation_receipt_id uuid");
        assertThatThrownBy(() -> withLiquibase(liquibase -> liquibase.update(new Contexts(), new LabelExpression())))
            .isInstanceOf(LiquibaseException.class)
            .hasMessageContaining("INGESTION_ROLLBACK_SAGA_SCHEMA_BLOCKED");
    }

    @Test
    void migrationFailsClosedOnAConflictingAppendOnlyFunction() throws Exception {
        withLiquibase(liquibase -> liquibase.update(1, new Contexts(), new LabelExpression()));
        execute("""
            CREATE FUNCTION dts_reject_rollback_evidence_mutation()
            RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NEW; END; $$
            """);

        assertThatThrownBy(() -> withLiquibase(liquibase -> liquibase.update(new Contexts(), new LabelExpression())))
            .isInstanceOf(LiquibaseException.class)
            .hasMessageContaining("INGESTION_ROLLBACK_SAGA_SCHEMA_BLOCKED");
    }

    @Test
    void migrationIsForwardOnly() throws Exception {
        withLiquibase(liquibase -> liquibase.update(new Contexts(), new LabelExpression()));
        assertThatThrownBy(() -> withLiquibase(liquibase -> liquibase.rollback(1, new Contexts(), new LabelExpression())))
            .isInstanceOf(LiquibaseException.class)
            .hasMessageContaining("ROLLBACK_BLOCKED_INGESTION_ROLLBACK_TRUNCATE_GUARDS_FORWARD_ONLY");
    }

    private void withLiquibase(LiquibaseWork work) throws Exception {
        try (ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor(); Connection connection = connection()) {
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

    private Connection connection() throws SQLException {
        return java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long queryLong(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private String queryString(String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private void createTruncateGuards() throws SQLException {
        execute("""
            CREATE TRIGGER trg_rollback_audit_no_truncate
            BEFORE TRUNCATE ON rollback_audit_log
            FOR EACH STATEMENT EXECUTE FUNCTION dts_reject_rollback_evidence_mutation()
            """);
        execute("""
            CREATE TRIGGER trg_ingestion_rollback_evidence_no_truncate
            BEFORE TRUNCATE ON ingestion_rollback_affected_object
            FOR EACH STATEMENT EXECUTE FUNCTION dts_reject_rollback_evidence_mutation()
            """);
    }

    @FunctionalInterface
    private interface LiquibaseWork {
        void run(Liquibase liquibase) throws Exception;
    }
}
