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
            "registration_asset_probe, modeling_plan_execution_binding, databasechangelog, databasechangeloglock"
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
        assertThat(tasks.claim(first, NOW, NOW.plus(Duration.ofMinutes(5)))).isTrue();
        tasks.markFailed(first, 1, "CATALOG_UNAVAILABLE", "目录不可用", NOW.plus(Duration.ofMinutes(1)), NOW);
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

        assertThat(tasks.claim(task, NOW, NOW.plus(Duration.ofMinutes(5)))).isTrue();
        assertThat(tasks.claim(task, NOW, NOW.plus(Duration.ofMinutes(5)))).isFalse();
        assertThat(tasks.findDue(NOW.plus(Duration.ofMinutes(1)), 5, 10)).isEmpty();
        assertThat(tasks.findDue(NOW.plus(Duration.ofMinutes(6)), 5, 10)).hasSize(1);

        tasks.markSucceeded(task, 1, NOW.plus(Duration.ofMinutes(2)));
        tasks.markFailed(task, 1, "LATE", "late failure", NOW, NOW);

        var done = tasks.findByCandidate(TENANT, candidate).orElseThrow();
        assertThat(done.state()).isEqualTo(State.SUCCEEDED);
        assertThat(done.succeededAt()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        assertThat(tasks.findDue(NOW.plus(Duration.ofHours(1)), 5, 10)).isEmpty();
    }

    @Test
    void newerBuildFencesLateCompletionFailureAndStaleEnqueue() {
        UUID candidate = UUID.randomUUID();
        tasks.enqueue(TENANT, candidate, 6, null, "prod", NOW);
        var old = tasks.findByCandidate(TENANT, candidate).orElseThrow();
        assertThat(tasks.claim(old, NOW, NOW.plusSeconds(10))).isTrue();
        tasks.enqueue(TENANT, candidate, 9, null, "prod", NOW);
        assertThat(tasks.markSucceeded(old, 1, NOW)).isFalse();
        tasks.markFailed(old, 1, "LATE", "late", NOW, NOW);
        tasks.enqueue(TENANT, candidate, 6, null, "prod", NOW);
        var current = tasks.findByCandidate(TENANT, candidate).orElseThrow();
        assertThat(current.builtCandidateVersion()).isEqualTo(9);
        assertThat(current.state()).isEqualTo(State.PENDING);
        assertThat(current.attempts()).isZero();
        assertThat(tasks.claim(old, NOW, NOW.plusSeconds(10))).isFalse();
    }

    @Test
    void expiredAttemptAndManualRetryFenceLateWorkers() {
        UUID candidate = UUID.randomUUID();
        tasks.enqueue(TENANT, candidate, 6, null, "prod", NOW);
        var first = tasks.findByCandidate(TENANT, candidate).orElseThrow();
        tasks.claim(first, NOW, NOW.plusSeconds(10));
        var second = tasks.findByCandidate(TENANT, candidate).orElseThrow();
        assertThat(tasks.claim(second, NOW.plusSeconds(11), NOW.plusSeconds(30))).isTrue();
        assertThat(tasks.markSucceeded(first, 1, NOW)).isFalse();
        assertThat(tasks.lockAttempt(first, 1)).isFalse();
        assertThat(tasks.retry(second, NOW)).isTrue();
        assertThat(tasks.markSucceeded(second, 2, NOW)).isFalse();
        var retried = tasks.findByCandidate(TENANT, candidate).orElseThrow();
        assertThat(retried.id()).isNotEqualTo(first.id());
        assertThat(retried.attempts()).isZero();
    }

    @Test
    void assetWritesAndTaskCompletionRollbackTogether() {
        UUID candidateId = UUID.randomUUID();
        tasks.enqueue(TENANT, candidateId, 6, UUID.randomUUID(), "prod", NOW);
        var task = tasks.findByCandidate(TENANT, candidateId).orElseThrow();
        tasks.claim(task, NOW, NOW.plusSeconds(60));
        jdbc.update("insert into modeling_model_release_candidate_command values (?, ?, 5, 'BUILDING')", TENANT, candidateId);
        jdbc.execute("create table registration_asset_probe (id uuid primary key)");
        var candidates = org.mockito.Mockito.mock(ModelReleaseCandidateRepository.class);
        var registration = org.mockito.Mockito.mock(com.yuzhi.dts.platform.service.modeling.CandidateQualityAssetRegistrationService.class);
        var authorization = org.mockito.Mockito.mock(com.yuzhi.dts.platform.service.modeling.ModelingExecutionAuthorization.class);
        var candidate = org.mockito.Mockito.mock(com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView.class);
        org.mockito.Mockito.when(candidates.find(TENANT, candidateId)).thenReturn(java.util.Optional.of(candidate));
        org.mockito.Mockito.when(candidate.status()).thenReturn(com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus.BUILT);
        org.mockito.Mockito.doAnswer(call -> {
            jdbc.update("insert into registration_asset_probe values (?)", candidateId);
            throw new IllegalStateException("second asset write failed");
        }).when(registration).ensureRegistered(candidate);
        var service = new com.yuzhi.dts.platform.service.modeling.ModelAssetRegistrationAttemptService(tasks, candidates, registration, authorization);
        var factory = new org.springframework.aop.framework.ProxyFactory(service);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()),
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var proxied = (com.yuzhi.dts.platform.service.modeling.ModelAssetRegistrationAttemptService) factory.getProxy();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> proxied.register(task, 1)).hasMessageContaining("second asset write failed");
        assertThat(jdbc.queryForObject("select count(*) from registration_asset_probe", Integer.class)).isZero();
        assertThat(tasks.findByCandidate(TENANT, candidateId).orElseThrow().state()).isEqualTo(State.PENDING);
    }

    @Test
    void activationMigrationPreservesExistingBindingsAndRollsBack() throws Exception {
        jdbc.execute("create table modeling_plan_execution_binding (id uuid primary key)");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into modeling_plan_execution_binding values (?)", id);
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("config/liquibase/changelog/20260925_02_model_execution_activation.xml", new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
                assertThat(jdbc.queryForObject("select activation_required from modeling_plan_execution_binding where id=?", Boolean.class, id)).isFalse();
                liquibase.rollback(1, new Contexts(), new LabelExpression());
                assertThat(jdbc.queryForObject("select count(*) from modeling_plan_execution_binding", Integer.class)).isEqualTo(1);
            }
        }
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
