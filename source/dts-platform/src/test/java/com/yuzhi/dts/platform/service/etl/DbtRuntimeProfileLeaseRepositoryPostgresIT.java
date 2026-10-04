package com.yuzhi.dts.platform.service.etl;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseCompensationOutcome;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseMutationOutcome;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseMutationResult;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseRecord;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseStatus;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DbtRuntimeProfileLeaseRepositoryPostgresIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.6")
            .withDatabaseName("dbt_profile_lease_it")
            .withUsername("dbt_profile_lease_test")
            .withPassword("dbt_profile_lease_test");

    private JdbcTemplate jdbcTemplate;
    private DbtRuntimeProfileLeaseRepository repository;
    private TransactionTemplate transactions;

    @BeforeEach
    void createSchema() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new DbtRuntimeProfileLeaseRepository(
            jdbcTemplate
        );
        transactions = new TransactionTemplate(
            new DataSourceTransactionManager(dataSource)
        );
        jdbcTemplate.execute(
            "drop table if exists modeling_dbt_runtime_profile_lease"
        );
        jdbcTemplate.execute(
            """
            create table modeling_dbt_runtime_profile_lease (
                id uuid primary key,
                tenant_id varchar(128) not null,
                pipeline_run_id uuid not null,
                dag_run_id varchar(256) not null,
                environment varchar(64) not null,
                execution_target_key varchar(256) not null,
                target_name varchar(128) not null,
                credential_version_ref varchar(128) not null,
                status varchar(16) not null,
                issued_at timestamp not null,
                expires_at timestamp not null,
                consumed_at timestamp,
                released_at timestamp
            )
            """
        );
    }

    @Test
    void queuedConsumeAndRenewCannotReviveLeasesAfterDatabaseExpiry()
        throws Exception {
        UUID issuedLeaseId = UUID.randomUUID();
        UUID consumedLeaseId = UUID.randomUUID();
        Instant issuedAt = databaseNow().minusSeconds(1);
        Instant initialExpiry = databaseNow().plusSeconds(10);
        repository.issue(
            lease(issuedLeaseId, issuedAt, initialExpiry)
        );
        repository.issue(
            lease(consumedLeaseId, issuedAt, initialExpiry)
        );
        assertThat(
            repository.consumeState(consumedLeaseId).outcome()
        )
            .isEqualTo(LeaseMutationOutcome.SUCCESS);
        jdbcTemplate.update(
            """
            update modeling_dbt_runtime_profile_lease
               set expires_at = clock_timestamp() + interval '1 second'
             where id in (?, ?)
            """,
            issuedLeaseId,
            consumedLeaseId
        );
        Instant observedExpiry = jdbcTemplate
            .queryForObject(
                """
                select max(expires_at)
                  from modeling_dbt_runtime_profile_lease
                 where id in (?, ?)
                """,
                Timestamp.class,
                issuedLeaseId,
                consumedLeaseId
            )
            .toInstant();

        CountDownLatch rowsLocked = new CountDownLatch(1);
        CountDownLatch requestsStarted = new CountDownLatch(2);
        CountDownLatch releaseLocks = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(3);
        Future<?> blocker = null;
        try {
            blocker = executor.submit(() ->
                transactions.executeWithoutResult(status -> {
                    jdbcTemplate.queryForList(
                        """
                        select id
                          from modeling_dbt_runtime_profile_lease
                         where id in (?, ?)
                         order by id
                           for update
                        """,
                        issuedLeaseId,
                        consumedLeaseId
                    );
                    rowsLocked.countDown();
                    await(releaseLocks);
                })
            );
            assertThat(rowsLocked.await(5, SECONDS)).isTrue();
            Future<LeaseMutationResult> consume = executor.submit(() -> {
                requestsStarted.countDown();
                return repository.consumeState(issuedLeaseId);
            });
            Future<LeaseMutationResult> renew = executor.submit(() -> {
                requestsStarted.countDown();
                return repository.renewState(
                    consumedLeaseId,
                    Duration.ofMinutes(5)
                );
            });
            assertThat(requestsStarted.await(5, SECONDS)).isTrue();

            org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                    assertThat(waitingLeaseMutations())
                        .isGreaterThanOrEqualTo(2)
                );
            org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                    assertThat(databaseNow())
                        .isAfterOrEqualTo(observedExpiry)
                );
            releaseLocks.countDown();

            assertThat(consume.get(5, SECONDS).outcome())
                .isEqualTo(LeaseMutationOutcome.EXPIRED);
            assertThat(renew.get(5, SECONDS).outcome())
                .isEqualTo(LeaseMutationOutcome.EXPIRED);
            blocker.get(5, SECONDS);
        } finally {
            releaseLocks.countDown();
            executor.shutdownNow();
            if (blocker != null && !blocker.isDone()) {
                blocker.cancel(true);
            }
        }

        assertThat(
            jdbcTemplate.queryForObject(
                """
                select max(expires_at)
                  from modeling_dbt_runtime_profile_lease
                 where id in (?, ?)
                """,
                Timestamp.class,
                issuedLeaseId,
                consumedLeaseId
            )
        )
            .isEqualTo(Timestamp.from(observedExpiry));
    }

    @Test
    void queuedExpirationUsesPostLockClockAndRejectsRenewedVersion()
        throws Exception {
        UUID expiringLeaseId = UUID.randomUUID();
        UUID renewedLeaseId = UUID.randomUUID();
        Instant issuedAt = databaseNow().minusSeconds(1);
        Instant initialExpiry = databaseNow().plusSeconds(1);
        repository.issue(
            lease(expiringLeaseId, issuedAt, initialExpiry)
        );
        repository.issue(
            lease(renewedLeaseId, issuedAt, initialExpiry)
        );

        CountDownLatch rowsLocked = new CountDownLatch(1);
        CountDownLatch requestsStarted = new CountDownLatch(2);
        CountDownLatch releaseLocks = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(3);
        Future<?> blocker = null;
        try {
            blocker = executor.submit(() ->
                transactions.executeWithoutResult(status -> {
                    jdbcTemplate.queryForList(
                        """
                        select id
                          from modeling_dbt_runtime_profile_lease
                         where id in (?, ?)
                         order by id
                           for update
                        """,
                        expiringLeaseId,
                        renewedLeaseId
                    );
                    rowsLocked.countDown();
                    await(releaseLocks);
                    jdbcTemplate.update(
                        """
                        update modeling_dbt_runtime_profile_lease
                           set expires_at =
                               clock_timestamp() + interval '5 minutes'
                         where id = ?
                        """,
                        renewedLeaseId
                    );
                })
            );
            assertThat(rowsLocked.await(5, SECONDS)).isTrue();
            Future<Boolean> expiring = executor.submit(() -> {
                requestsStarted.countDown();
                return repository.expire(
                    expiringLeaseId,
                    initialExpiry
                );
            });
            Future<Boolean> renewed = executor.submit(() -> {
                requestsStarted.countDown();
                return repository.expire(
                    renewedLeaseId,
                    initialExpiry
                );
            });
            assertThat(requestsStarted.await(5, SECONDS)).isTrue();
            org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                    assertThat(waitingLeaseMutations())
                        .isGreaterThanOrEqualTo(2)
                );
            org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                    assertThat(databaseNow())
                        .isAfterOrEqualTo(initialExpiry)
                );
            releaseLocks.countDown();

            assertThat(expiring.get(5, SECONDS)).isTrue();
            assertThat(renewed.get(5, SECONDS)).isFalse();
            blocker.get(5, SECONDS);
        } finally {
            releaseLocks.countDown();
            executor.shutdownNow();
            if (blocker != null && !blocker.isDone()) {
                blocker.cancel(true);
            }
        }

        LeaseRecord expired = repository
            .find(expiringLeaseId)
            .orElseThrow();
        LeaseRecord renewed = repository
            .find(renewedLeaseId)
            .orElseThrow();
        assertThat(expired.status()).isEqualTo(LeaseStatus.EXPIRED);
        assertThat(expired.releasedAt())
            .isAfterOrEqualTo(initialExpiry);
        assertThat(renewed.status()).isEqualTo(LeaseStatus.ISSUED);
        assertThat(renewed.expiresAt()).isAfter(initialExpiry);
    }

    @Test
    void inspectionAndMissingProfileCompensationUseLockedDatabaseState() {
        UUID expiredLeaseId = UUID.randomUUID();
        UUID compensationLeaseId = UUID.randomUUID();
        Instant issuedAt = databaseNow().minusSeconds(1);
        Instant initialExpiry = databaseNow().plusSeconds(60);
        repository.issue(
            lease(expiredLeaseId, issuedAt, initialExpiry)
        );
        repository.issue(
            lease(compensationLeaseId, issuedAt, initialExpiry)
        );

        assertThat(
            repository.viewActiveState(expiredLeaseId).outcome()
        )
            .isEqualTo(LeaseMutationOutcome.SUCCESS);
        jdbcTemplate.update(
            """
            update modeling_dbt_runtime_profile_lease
               set expires_at = clock_timestamp() - interval '1 second'
             where id = ?
            """,
            expiredLeaseId
        );
        assertThat(
            repository.viewActiveState(expiredLeaseId).outcome()
        )
            .isEqualTo(LeaseMutationOutcome.EXPIRED);
        assertThat(
            repository.viewActiveState(UUID.randomUUID()).outcome()
        )
            .isEqualTo(LeaseMutationOutcome.NOT_FOUND);

        assertThat(
            repository.consumeState(compensationLeaseId).outcome()
        )
            .isEqualTo(LeaseMutationOutcome.SUCCESS);
        LeaseRecord stale = repository
            .find(compensationLeaseId)
            .orElseThrow();
        assertThat(
            repository
                .renewState(
                    compensationLeaseId,
                    Duration.ofMinutes(5)
                )
                .outcome()
        )
            .isEqualTo(LeaseMutationOutcome.SUCCESS);

        LeaseCompensationOutcome compensated =
            transactions.execute(status ->
                repository.compensateMissingProfile(
                    compensationLeaseId,
                    stale.status(),
                    stale.expiresAt()
                )
            );

        assertThat(compensated)
            .isEqualTo(LeaseCompensationOutcome.COMPENSATED);
        assertThat(
            repository
                .find(compensationLeaseId)
                .orElseThrow()
                .status()
        )
            .isEqualTo(LeaseStatus.EXPIRED);
    }

    private int waitingLeaseMutations() {
        return jdbcTemplate.queryForObject(
            """
            select count(*)
              from pg_stat_activity
             where datname = current_database()
               and pid <> pg_backend_pid()
               and state = 'active'
               and wait_event_type = 'Lock'
               and query like '%modeling_dbt_runtime_profile_lease%'
            """,
            Integer.class
        );
    }

    private Instant databaseNow() {
        return jdbcTemplate
            .queryForObject(
                "select clock_timestamp()",
                Timestamp.class
            )
            .toInstant();
    }

    private static LeaseRecord lease(
        UUID leaseId,
        Instant issuedAt,
        Instant expiresAt
    ) {
        return new LeaseRecord(
            leaseId,
            "tenant-a",
            UUID.randomUUID(),
            "manual__lease-it",
            "DEV",
            "postgres-primary",
            "dev",
            "sha256:" + "0".repeat(64),
            LeaseStatus.ISSUED,
            issuedAt,
            expiresAt,
            null,
            null
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, SECONDS)) {
                throw new IllegalStateException(
                    "Timed out waiting for lease test latch"
                );
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Lease test latch was interrupted",
                interrupted
            );
        }
    }
}
