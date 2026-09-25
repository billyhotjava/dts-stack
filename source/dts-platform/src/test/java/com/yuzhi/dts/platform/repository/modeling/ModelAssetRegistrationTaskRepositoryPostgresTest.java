package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository.State;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ModelAssetRegistrationTaskRepositoryPostgresTest {

    private static final String CHANGELOG = "config/liquibase/changelog/20260925_01_model_asset_registration_task.xml";
    private static final String TENANT = "tenant-a";
    private static final Instant NOW = Instant.parse("2026-09-25T02:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("model_asset_registration_task_test")
        .withUsername("registration_task")
        .withPassword("registration_task");

    private JdbcTemplate jdbc;
    private ModelAssetRegistrationTaskRepository tasks;

    @BeforeEach
    void resetDatabase() throws Exception {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute(
            "drop table if exists modeling_model_asset_registration_task, modeling_model_release_candidate_command, " +
            "databasechangelog, databasechangeloglock"
        );
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
        jdbc.execute(
            "create table modeling_model_release_candidate_command (tenant_id varchar(64), candidate_id uuid, " +
            "candidate_version int, to_status varchar(32))"
        );
        tasks = new ModelAssetRegistrationTaskRepository(jdbc);
    }

    @Test
    void aRebuildRequeuesTheSameCandidateAndClearsThePreviousFailure() {
        UUID candidate = UUID.randomUUID();
        tasks.enqueue(TENANT, candidate, 4, UUID.randomUUID(), "prod", NOW);
        var first = tasks.findByCandidate(TENANT, candidate).orElseThrow();
        assertThat(tasks.claim(first.id(), NOW, NOW.plus(Duration.ofMinutes(5)))).isTrue();
        tasks.markFailed(first.id(), "CATALOG_UNAVAILABLE", "目录不可用", NOW.plus(Duration.ofMinutes(1)), NOW);
        assertThat(tasks.findByCandidate(TENANT, candidate).orElseThrow().state()).isEqualTo(State.FAILED);

        tasks.enqueue(TENANT, candidate, 9, null, "prod", NOW.plusSeconds(60));

        var requeued = tasks.findByCandidate(TENANT, candidate).orElseThrow();
        assertThat(requeued.id()).isEqualTo(first.id());
        assertThat(requeued.state()).isEqualTo(State.PENDING);
        assertThat(requeued.builtCandidateVersion()).isEqualTo(9);
        assertThat(requeued.attempts()).isZero();
        assertThat(requeued.lastErrorCode()).isNull();
    }

    @Test
    void anAttemptIsClaimedOncePerLeaseAndSucceededTasksAreNeverDue() {
        UUID candidate = UUID.randomUUID();
        tasks.enqueue(TENANT, candidate, 4, null, "dev", NOW);
        var task = tasks.findDue(NOW, 5, 10).get(0);

        assertThat(tasks.claim(task.id(), NOW, NOW.plus(Duration.ofMinutes(5)))).isTrue();
        assertThat(tasks.claim(task.id(), NOW, NOW.plus(Duration.ofMinutes(5)))).isFalse();
        assertThat(tasks.findDue(NOW.plus(Duration.ofMinutes(1)), 5, 10)).isEmpty();
        assertThat(tasks.findDue(NOW.plus(Duration.ofMinutes(6)), 5, 10)).hasSize(1);

        tasks.markSucceeded(TENANT, candidate, NOW.plus(Duration.ofMinutes(2)));
        tasks.markFailed(task.id(), "LATE", "late failure", NOW, NOW);

        var done = tasks.findByCandidate(TENANT, candidate).orElseThrow();
        assertThat(done.state()).isEqualTo(State.SUCCEEDED);
        assertThat(done.succeededAt()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        assertThat(tasks.findDue(NOW.plus(Duration.ofHours(1)), 5, 10)).isEmpty();
    }

    @Test
    void exhaustedTasksStopBeingDue() {
        UUID candidate = UUID.randomUUID();
        tasks.enqueue(TENANT, candidate, 4, null, "dev", NOW);
        jdbc.update("update modeling_model_asset_registration_task set attempts = 5, state = 'FAILED'");

        assertThat(tasks.findDue(NOW.plus(Duration.ofDays(1)), 5, 10)).isEmpty();
    }

    @Test
    void theBuildInitiatorIsTheLatestBuildingCommandBeforeBuilt() {
        UUID candidate = UUID.randomUUID();
        jdbc.update(
            "insert into modeling_model_release_candidate_command values (?, ?, 2, 'BUILDING'), (?, ?, 5, 'BUILDING'), " +
            "(?, ?, 6, 'BUILT'), (?, ?, 8, 'BUILDING')",
            TENANT, candidate, TENANT, candidate, TENANT, candidate, TENANT, candidate
        );

        assertThat(tasks.findBuildInitiatorVersion(TENANT, candidate, 6)).contains(5);
        assertThat(tasks.findBuildInitiatorVersion(TENANT, UUID.randomUUID(), 6)).isEmpty();
    }
}
