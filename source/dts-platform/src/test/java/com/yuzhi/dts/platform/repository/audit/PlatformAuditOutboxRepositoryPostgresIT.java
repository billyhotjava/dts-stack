package com.yuzhi.dts.platform.repository.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class PlatformAuditOutboxRepositoryPostgresIT {

    private static final String CHANGELOG =
        "config/liquibase/changelog/20260731_02_platform_audit_outbox.xml";
    private static final Instant T0 = Instant.parse("2026-07-31T08:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.4")
            .withDatabaseName("platform_audit_outbox_it")
            .withUsername("platform_audit_outbox_test")
            .withPassword("platform_audit_outbox_test");

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private PlatformAuditOutboxRepository repository;

    @BeforeEach
    void migrateSchema() throws Exception {
        dataSource = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop table if exists platform_audit_outbox cascade");
        jdbcTemplate.execute("drop table if exists databasechangeloglock cascade");
        jdbcTemplate.execute("drop table if exists databasechangelog cascade");
        withLiquibase(liquibase -> liquibase.update(new Contexts(), new LabelExpression()));
        repository = new PlatformAuditOutboxRepository(jdbcTemplate);
    }

    @AfterEach
    void cleanSchema() {
        jdbcTemplate.execute("drop table if exists platform_audit_outbox cascade");
        jdbcTemplate.execute("drop table if exists databasechangeloglock cascade");
        jdbcTemplate.execute("drop table if exists databasechangelog cascade");
    }

    @Test
    void migrationEnforcesLedgerIntegrityAndRollsBackCleanly() throws Exception {
        UUID id = repository.enqueue(command("integrity-event", "a".repeat(64)));
        assertThatThrownBy(() ->
            jdbcTemplate.update(
                "update platform_audit_outbox set payload_hash='not-a-sha256' where id=?",
                id
            )
        ).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() ->
            jdbcTemplate.update(
                "update platform_audit_outbox set status='CLAIMED', claimed_at=null where id=?",
                id
            )
        ).isInstanceOf(DataAccessException.class);

        withLiquibase(liquibase -> liquibase.rollback(1, new Contexts(), new LabelExpression()));
        Integer remaining = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_schema='public' and table_name='platform_audit_outbox'",
            Integer.class
        );
        assertThat(remaining).isZero();
    }

    @Test
    void enqueueIsIdempotentAndParticipatesInCallerTransaction() {
        PlatformAuditOutboxRepository.EnqueueCommand command = command("event-1", "a".repeat(64));
        UUID first = repository.enqueue(command);

        assertThat(repository.enqueue(command)).isEqualTo(first);
        assertThatThrownBy(() -> repository.enqueue(command("event-1", "b".repeat(64))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("different payload");

        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        assertThatThrownBy(() ->
            transaction.executeWithoutResult(status -> {
                repository.enqueue(command("rolled-back-event", "c".repeat(64)));
                throw new RollbackProbe();
            })
        ).isInstanceOf(RollbackProbe.class);

        assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from platform_audit_outbox where event_id='rolled-back-event'",
                Integer.class
            )
        ).isZero();
    }

    @Test
    void staleClaimUsesAttemptAsFence() {
        repository.enqueue(command("fenced-event", "d".repeat(64)));
        Instant firstClaimAt = Instant.now().plusSeconds(5);

        PlatformAuditOutboxRepository.ClaimedAudit first = repository
            .claimNext(firstClaimAt, Duration.ofSeconds(60))
            .orElseThrow();
        PlatformAuditOutboxRepository.ClaimedAudit reclaimed = repository
            .claimNext(firstClaimAt.plusSeconds(61), Duration.ofSeconds(60))
            .orElseThrow();

        assertThat(reclaimed.id()).isEqualTo(first.id());
        assertThat(reclaimed.attempts()).isEqualTo(first.attempts() + 1);
        assertThatThrownBy(() -> repository.markSent(first.id(), first.attempts(), firstClaimAt.plusSeconds(62)))
            .isInstanceOf(IllegalStateException.class);

        repository.markSent(reclaimed.id(), reclaimed.attempts(), firstClaimAt.plusSeconds(62));
        assertThat(status(reclaimed.id())).isEqualTo("SENT");
    }

    @Test
    void concurrentClaimsNeverReturnTheSameRow() throws Exception {
        repository.enqueue(command("concurrent-1", "e".repeat(64)));
        repository.enqueue(command("concurrent-2", "f".repeat(64)));
        Instant claimAt = Instant.now().plusSeconds(5);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<PlatformAuditOutboxRepository.ClaimedAudit> claim = () -> repository
                .claimNext(claimAt, Duration.ofMinutes(2))
                .orElseThrow();
            List<Future<PlatformAuditOutboxRepository.ClaimedAudit>> futures = executor.invokeAll(
                List.of(claim, claim)
            );

            assertThat(List.of(futures.get(0).get().id(), futures.get(1).get().id()))
                .doesNotHaveDuplicates();
        } finally {
            executor.shutdownNow();
        }
    }

    private PlatformAuditOutboxRepository.EnqueueCommand command(String eventId, String hash) {
        return new PlatformAuditOutboxRepository.EnqueueCommand(
            eventId,
            T0,
            hash,
            "{\"eventId\":\"" + eventId + "\",\"producer\":\"dts-platform\"}"
        );
    }

    private String status(UUID id) {
        return jdbcTemplate.queryForObject(
            "select status from platform_audit_outbox where id = ?",
            String.class,
            id
        );
    }

    private void withLiquibase(LiquibaseAction action) throws Exception {
        try (
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
            Connection connection = dataSource.getConnection()
        ) {
            Database database = null;
            try {
                database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                try (Liquibase liquibase = new Liquibase(CHANGELOG, resources, database)) {
                    action.run(liquibase);
                }
            } finally {
                if (database != null && !database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    @FunctionalInterface
    private interface LiquibaseAction {
        void run(Liquibase liquibase) throws Exception;
    }

    private static final class RollbackProbe extends RuntimeException {}
}
