package com.yuzhi.dts.platform.repository.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.yuzhi.dts.platform.config.AuditProperties;
import com.yuzhi.dts.platform.config.AuditProperties.TenancyMode;
import com.yuzhi.dts.platform.service.audit.AuditOutboxReplayReason;
import com.yuzhi.dts.platform.service.audit.AuditOutboxReplayService;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.audit.AuditTenantResolver;
import java.sql.Connection;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.HexFormat;
import java.util.Map;
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
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PlatformAuditOutboxRepositoryPostgresIT {

    private static final String BASE_CHANGELOG =
        "config/liquibase/changelog/20260731_02_platform_audit_outbox.xml";
    private static final String TENANT_CHANGELOG =
        "config/liquibase/changelog/20260801_04_platform_audit_outbox_tenant.xml";
    private static final String GENERATION_ATTEMPTS_CHANGELOG =
        "config/liquibase/changelog/20260801_10_platform_audit_outbox_generation_attempts.xml";
    private static final Instant T0 = Instant.parse("2026-07-31T08:00:00Z");
    private static final String TENANT_A = "tenant-a";

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
        withLiquibase(BASE_CHANGELOG, liquibase -> liquibase.update(new Contexts(), new LabelExpression()));
        withLiquibase(TENANT_CHANGELOG, liquibase -> liquibase.update(new Contexts(), new LabelExpression()));
        withLiquibase(GENERATION_ATTEMPTS_CHANGELOG, liquibase ->
            liquibase.update(new Contexts(), new LabelExpression())
        );
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
        assertThatThrownBy(() ->
            jdbcTemplate.update(
                "update platform_audit_outbox set generation_attempts=dispatch_attempts + 1 where id=?",
                id
            )
        ).isInstanceOf(DataAccessException.class);

        withLiquibase(GENERATION_ATTEMPTS_CHANGELOG, liquibase ->
            liquibase.rollback(1, new Contexts(), new LabelExpression())
        );
        assertThatThrownBy(() ->
            withLiquibase(TENANT_CHANGELOG, liquibase ->
                liquibase.rollback(1, new Contexts(), new LabelExpression())
            )
        ).hasMessageContaining("ROLLBACK_BLOCKED_PLATFORM_AUDIT_TENANT_EVIDENCE_EXISTS");

        jdbcTemplate.update("delete from platform_audit_outbox where id=?", id);
        withLiquibase(TENANT_CHANGELOG, liquibase -> liquibase.rollback(1, new Contexts(), new LabelExpression()));
        withLiquibase(BASE_CHANGELOG, liquibase -> liquibase.rollback(1, new Contexts(), new LabelExpression()));
        Integer remaining = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_schema='public' and table_name='platform_audit_outbox'",
            Integer.class
        );
        assertThat(remaining).isZero();
    }

    @Test
    void tenantMigrationMarksHistoricalRowsUnscopedAndRequiresTenantForNewRows() throws Exception {
        withLiquibase(TENANT_CHANGELOG, liquibase -> liquibase.rollback(1, new Contexts(), new LabelExpression()));
        UUID legacyId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            insert into platform_audit_outbox (
                id, event_id, producer, occurred_at, payload_hash, body_json,
                status, dispatch_attempts, next_attempt_at, created_at, last_modified_at
            ) values (?, 'legacy-event', 'dts-platform', ?, ?, '{}', 'PENDING', 0, ?, ?, ?)
            """,
            legacyId,
            java.sql.Timestamp.from(T0),
            "9".repeat(64),
            java.sql.Timestamp.from(T0),
            java.sql.Timestamp.from(T0),
            java.sql.Timestamp.from(T0)
        );

        withLiquibase(TENANT_CHANGELOG, liquibase -> liquibase.update(new Contexts(), new LabelExpression()));

        assertThat(
            jdbcTemplate.queryForObject(
                "select tenant_id from platform_audit_outbox where id=?",
                String.class,
                legacyId
            )
        ).isEqualTo(PlatformAuditOutboxRepository.LEGACY_UNSCOPED_TENANT);
        assertThat(repository.findReplayTarget(TENANT_A, legacyId)).isEmpty();
        assertThatThrownBy(() ->
            jdbcTemplate.update("update platform_audit_outbox set tenant_id=null where id=?", legacyId)
        ).isInstanceOf(DataAccessException.class);
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

    @Test
    void replayDeadUsesTenantStatusAndHashCasWithoutChangingImmutableEvidence() {
        UUID id = repository.enqueue(command("replay-event", "1".repeat(64)));
        Instant claimAt = Instant.now().plusSeconds(5);
        PlatformAuditOutboxRepository.ClaimedAudit claimed = repository
            .claimNext(claimAt, Duration.ofMinutes(1))
            .orElseThrow();
        repository.markDead(id, claimed.attempts(), "gateway unavailable", claimAt.plusSeconds(1));
        Map<String, Object> before = row(id);
        String expectedBodyJson = String.valueOf(before.get("body_json"));

        assertThat(repository.findReplayTarget("tenant-b", id)).isEmpty();
        assertThat(
            repository.replayDead(
                new PlatformAuditOutboxRepository.ReplayCommand(
                    "tenant-b", id, "1".repeat(64), expectedBodyJson, T0.plusSeconds(7), T0.plusSeconds(7)
                )
            )
        ).isZero();
        assertThat(
            repository.replayDead(
                new PlatformAuditOutboxRepository.ReplayCommand(
                    TENANT_A, id, "2".repeat(64), expectedBodyJson, T0.plusSeconds(7), T0.plusSeconds(7)
                )
            )
        ).isZero();

        assertThat(
            repository.replayDead(
                new PlatformAuditOutboxRepository.ReplayCommand(
                    TENANT_A, id, "1".repeat(64), expectedBodyJson, T0.plusSeconds(7), T0.plusSeconds(7)
                )
            )
        ).isOne();

        Map<String, Object> after = row(id);
        assertThat(after)
            .containsEntry("status", "PENDING")
            .containsEntry("tenant_id", TENANT_A)
            .containsEntry("event_id", before.get("event_id"))
            .containsEntry("producer", before.get("producer"))
            .containsEntry("payload_hash", before.get("payload_hash"))
            .containsEntry("body_json", before.get("body_json"))
            .containsEntry("dispatch_attempts", before.get("dispatch_attempts"))
            .containsEntry("generation_attempts", 0)
            .containsEntry("last_error", before.get("last_error"));
        assertThat(after.get("next_attempt_at")).isNotNull();
        assertThat(
            repository.replayDead(
                new PlatformAuditOutboxRepository.ReplayCommand(
                    TENANT_A, id, "1".repeat(64), expectedBodyJson, T0.plusSeconds(8), T0.plusSeconds(8)
                )
            )
        ).isZero();

        PlatformAuditOutboxRepository.ClaimedAudit replayClaim = repository
            .claimNext(T0.plusSeconds(9), Duration.ofMinutes(1))
            .orElseThrow();
        assertThat(replayClaim.id()).isEqualTo(id);
        assertThat(replayClaim.attempts()).isEqualTo(claimed.attempts() + 1);
        assertThat(replayClaim.generationAttempts()).isOne();
    }

    @Test
    void concurrentReplayCasAllowsExactlyOneTransition() throws Exception {
        UUID id = repository.enqueue(command("concurrent-replay-event", "4".repeat(64)));
        Instant claimAt = Instant.now().plusSeconds(5);
        PlatformAuditOutboxRepository.ClaimedAudit claimed = repository
            .claimNext(claimAt, Duration.ofMinutes(1))
            .orElseThrow();
        repository.markDead(id, claimed.attempts(), "gateway unavailable", claimAt.plusSeconds(1));
        String expectedBodyJson = String.valueOf(row(id).get("body_json"));
        PlatformAuditOutboxRepository.ReplayCommand command = new PlatformAuditOutboxRepository.ReplayCommand(
            TENANT_A,
            id,
            "4".repeat(64),
            expectedBodyJson,
            T0.plusSeconds(7),
            T0.plusSeconds(7)
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = executor.invokeAll(
                List.of(() -> repository.replayDead(command), () -> repository.replayDead(command))
            );

            assertThat(futures.get(0).get() + futures.get(1).get()).isOne();
            assertThat(status(id)).isEqualTo("PENDING");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void replayAndReplayAuditRollbackTogetherWhenAuditWriteFails() {
        String replayBody = body("rollback-replay-event");
        String replayHash = sha256(replayBody);
        UUID id = repository.enqueue(command("rollback-replay-event", replayHash));
        Instant claimAt = Instant.now().plusSeconds(5);
        PlatformAuditOutboxRepository.ClaimedAudit claimed = repository
            .claimNext(claimAt, Duration.ofMinutes(1))
            .orElseThrow();
        repository.markDead(id, claimed.attempts(), "gateway unavailable", claimAt.plusSeconds(1));
        AuditService auditService = mock(AuditService.class);
        doThrow(new IllegalStateException("audit outbox unavailable"))
            .when(auditService)
            .auditActionStrict(anyString(), any(), anyString(), any());
        AuditProperties properties = new AuditProperties();
        properties.setTenancyMode(TenancyMode.SINGLE_TENANT);
        properties.setTenantId(TENANT_A);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ReplayTransactionConfiguration.class);
            context.registerBean(
                PlatformTransactionManager.class,
                () -> new DataSourceTransactionManager(dataSource)
            );
            context.registerBean(PlatformAuditOutboxRepository.class, () -> repository);
            context.registerBean(AuditService.class, () -> auditService);
            context.registerBean(AuditTenantResolver.class, () -> new AuditTenantResolver(properties));
            context.registerBean(AuditOutboxReplayService.class);
            context.refresh();
            AuditOutboxReplayService replay = context.getBean(AuditOutboxReplayService.class);

            assertThatThrownBy(() -> replay.replay(id, replayHash, AuditOutboxReplayReason.OPERATOR_RETRY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit outbox unavailable");
        }

        assertThat(status(id)).isEqualTo("DEAD");
        assertThat(row(id).get("next_attempt_at")).isNull();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class ReplayTransactionConfiguration {}

    private PlatformAuditOutboxRepository.EnqueueCommand command(String eventId, String hash) {
        return new PlatformAuditOutboxRepository.EnqueueCommand(
            TENANT_A,
            eventId,
            T0,
            hash,
            body(eventId)
        );
    }

    private String body(String eventId) {
        return "{\"eventId\":\"" + eventId + "\",\"producer\":\"dts-platform\"}";
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private Map<String, Object> row(UUID id) {
        return jdbcTemplate.queryForMap(
            """
            select tenant_id, event_id, producer, payload_hash, body_json, status,
                   dispatch_attempts, generation_attempts, next_attempt_at, last_error
              from platform_audit_outbox
             where id = ?
            """,
            id
        );
    }

    private String status(UUID id) {
        return jdbcTemplate.queryForObject(
            "select status from platform_audit_outbox where id = ?",
            String.class,
            id
        );
    }

    private void withLiquibase(String changelog, LiquibaseAction action) throws Exception {
        try (
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
            Connection connection = dataSource.getConnection()
        ) {
            Database database = null;
            try {
                database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                try (Liquibase liquibase = new Liquibase(changelog, resources, database)) {
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
