package com.yuzhi.dts.platform.service.modeling.imports.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplySummary;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginCommand;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginDisposition;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.CandidateResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ModelSpecImportApplyException;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ResultStatus;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ModelSpecImportApplyRepositoryIT {

    private static final List<String> CHANGELOGS = List.of(
        "config/liquibase/changelog/20260725_04_model_spec_import_apply.xml",
        "config/liquibase/changelog/20260725_05_model_spec_import_apply_lease.xml"
    );
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s70_model_apply_[0-9a-f]{32}$");
    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("modelSpecApplyIT")
        .withUsername("model_apply_test")
        .withPassword("model_apply_test");

    private String schema;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private ModelSpecImportApplyRepository repository;

    @BeforeEach
    void setUpSchema() throws Exception {
        schema = "s70_model_apply_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
            statement.execute("create table modeling_model_spec_import_run (id uuid primary key)");
            statement.execute(
                """
                create table modeling_dbt_artifact (
                    id uuid primary key,
                    model_spec_id uuid not null,
                    revision int not null,
                    implementation_revision int not null,
                    project_key varchar(128) not null,
                    dbt_unique_id varchar(512) not null,
                    node_kind varchar(64) not null,
                    artifact_type varchar(64) not null,
                    ownership varchar(32) not null
                )
                """
            );
            statement.execute(
                """
                create unique index uk_modeling_dbt_artifact_dbt_slot
                    on modeling_dbt_artifact (
                        model_spec_id, revision, implementation_revision, node_kind, artifact_type
                    )
                 where ownership = 'DBT_MANAGED'
                """
            );
        }
        applyChangelog();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            schemaJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        repository = new ModelSpecImportApplyRepository(jdbc, new ObjectMapper());
    }

    @AfterEach
    void dropSchema() throws Exception {
        if (schema == null) {
            return;
        }
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void beginReplaysSameKeyAndHashButRejectsDifferentHashAndCompetingRunningKey() {
        UUID replayRun = insertPreviewRun();
        BeginCommand original = command(replayRun, "apply-key", "request-hash");

        BeginResult started = inTransaction(() -> repository.begin(original));
        assertThat(started.disposition()).isEqualTo(BeginDisposition.STARTED);
        assertThat(inTransaction(() -> repository.begin(original)).disposition())
            .isEqualTo(BeginDisposition.RUNNING);
        assertThatThrownBy(() ->
            inTransaction(() -> repository.begin(command(replayRun, "apply-key", "different-hash")))
        )
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_IDEMPOTENCY_CONFLICT")
            );
        assertThatThrownBy(() ->
            inTransaction(() -> repository.begin(command(replayRun, "another-key", "another-request")))
        )
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_APPLY_ALREADY_RUNNING")
            );

        CandidateResult created = result("model.project.fact", ResultStatus.CREATED);
        inTransaction(() ->
            repository.recordSuccess("default", replayRun, original.attemptId(), started.ownerToken(), created)
        );
        var finalized = inTransaction(() ->
            repository.finalizeAttempt(
                "default",
                replayRun,
                original.attemptId(),
                started.ownerToken(),
                "actor",
                NOW.plusSeconds(1)
            )
        );
        assertThat(finalized.summary()).isEqualTo(new ApplySummary(1, 1, 0, 0, 0, 0, 0));
        assertThat(inTransaction(() -> repository.begin(original)).disposition())
            .isEqualTo(BeginDisposition.REPLAY);
    }

    @Test
    void fullDbtSlotAllowsDistinctProjectNodesButRejectsTheExactSameRevisionSlot() {
        UUID modelSpecId = UUID.randomUUID();
        insertArtifact(modelSpecId, "project-a", "model.project_a.fact");
        insertArtifact(modelSpecId, "project-b", "model.project_b.fact");

        assertThatThrownBy(() -> insertArtifact(modelSpecId, "project-a", "model.project_a.fact"))
            .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void concurrentSameRequestClaimsOnceAndReplaysTheRunningOwner() throws Exception {
        UUID runId = insertPreviewRun();
        UUID planId = UUID.randomUUID();
        BeginCommand first = command(UUID.randomUUID(), runId, planId, null, "shared-key", "shared-hash");
        BeginCommand second = command(UUID.randomUUID(), runId, planId, null, "shared-key", "shared-hash");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<BeginDisposition> dispositions = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        try (var executor = Executors.newFixedThreadPool(2)) {
            for (BeginCommand command : List.of(first, second)) {
                executor.submit(() -> {
                    try {
                        ready.countDown();
                        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                        dispositions.add(inTransaction(() -> repository.begin(command)).disposition());
                    } catch (Exception exception) {
                        throw new AssertionError(exception);
                    }
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(dispositions).containsExactlyInAnyOrder(BeginDisposition.STARTED, BeginDisposition.RUNNING);
        assertThat(jdbc.queryForObject(
            "select count(*) from modeling_model_spec_import_apply_attempt where run_id = ?",
            Integer.class,
            runId
        ))
            .isEqualTo(1);
    }

    @Test
    void expiredRunningAttemptIsRecoveredReplayedAndReleasesRetrySlot() {
        UUID runId = insertPreviewRun();
        UUID planId = UUID.randomUUID();
        BeginCommand original = command(
            UUID.randomUUID(),
            runId,
            planId,
            null,
            "lease-key",
            "lease-hash",
            List.of("model.project.fact", "model.project.summary"),
            List.of("model.project.fact", "model.project.summary")
        );
        BeginResult started = inTransaction(() -> repository.begin(original));
        inTransaction(() ->
            repository.recordSuccess(
                "default",
                runId,
                original.attemptId(),
                started.ownerToken(),
                result("model.project.fact", ResultStatus.CREATED)
            )
        );
        jdbc.update(
            """
            update modeling_model_spec_import_apply_attempt
               set lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'
             where tenant_id = 'default' and run_id = ? and id = ?
            """,
            runId,
            original.attemptId()
        );

        var recovered = inTransaction(() ->
            repository.recoverExpiredRunning("default", runId, "recovery-actor").orElseThrow()
        );

        assertThat(recovered.status()).isEqualTo(ModelSpecImportApplyContract.AttemptStatus.PARTIAL);
        assertThat(recovered.summary()).isEqualTo(new ApplySummary(2, 1, 0, 0, 0, 1, 0));
        assertThat(recovered.results())
            .filteredOn(item -> item.dbtUniqueId().equals("model.project.summary"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.status()).isEqualTo(ResultStatus.FAILED);
                assertThat(item.issues()).extracting("code").containsExactly("MODEL_IMPORT_APPLY_INTERRUPTED");
            });
        assertThat(inTransaction(() -> repository.begin(original)).disposition())
            .isEqualTo(BeginDisposition.REPLAY);
        assertThatThrownBy(() ->
            inTransaction(() ->
                repository.recordFailure(
                    "default",
                    runId,
                    original.attemptId(),
                    started.ownerToken(),
                    result("model.project.summary", ResultStatus.FAILED)
                )
            )
        )
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_APPLY_LEASE_LOST")
            );
        assertThatThrownBy(() ->
            inTransaction(() -> {
                repository.renewLease("default", runId, original.attemptId(), started.ownerToken());
                return null;
            })
        )
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_APPLY_LEASE_LOST")
            );

        BeginCommand retry = command(
            UUID.randomUUID(),
            runId,
            planId,
            original.attemptId(),
            "lease-retry-key",
            "lease-retry-hash",
            List.of("model.project.summary"),
            List.of("model.project.summary")
        );
        assertThat(inTransaction(() -> repository.begin(retry)).disposition())
            .isEqualTo(BeginDisposition.STARTED);
    }

    @Test
    void activeRunningLeaseIsNotRecovered() {
        UUID runId = insertPreviewRun();
        BeginCommand command = command(runId, "active-lease-key", "active-lease-hash");
        BeginResult started = inTransaction(() -> repository.begin(command));

        assertThat(
            inTransaction(() -> repository.recoverExpiredRunning("default", runId, "recovery-actor"))
        ).isEmpty();
        inTransaction(() -> {
            repository.renewLease("default", runId, command.attemptId(), started.ownerToken());
            return null;
        });
        assertThat(repository.findRunning("default", runId)).isPresent();
    }

    @Test
    void recordAndFinalizeRequireTheExactFrozenClosure() {
        UUID runId = insertPreviewRun();
        BeginCommand command = command(runId, "closure-key", "closure-hash");
        BeginResult started = inTransaction(() -> repository.begin(command));

        assertThatThrownBy(() ->
            inTransaction(() ->
                repository.recordSuccess(
                    "default",
                    runId,
                    command.attemptId(),
                    started.ownerToken(),
                    result("model.project.unselected", ResultStatus.CREATED)
                )
            )
        )
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_APPLY_CANDIDATE_NOT_SELECTED")
            );
        assertThatThrownBy(() ->
            inTransaction(() ->
                repository.finalizeAttempt(
                    "default",
                    runId,
                    command.attemptId(),
                    started.ownerToken(),
                    "actor",
                    NOW.plusSeconds(1)
                )
            )
        )
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_APPLY_RESULTS_INCOMPLETE")
            );
    }

    @Test
    void retryMustReferenceLatestUnresolvedAttemptAndCannotReuseStaleSource() {
        UUID runId = insertPreviewRun();
        UUID planId = UUID.randomUUID();
        BeginCommand original = command(UUID.randomUUID(), runId, planId, null, "initial-key", "initial-hash");
        BeginResult originalStarted = inTransaction(() -> repository.begin(original));
        inTransaction(() ->
            repository.recordFailure(
                "default",
                runId,
                original.attemptId(),
                originalStarted.ownerToken(),
                result("model.project.fact", ResultStatus.FAILED)
            )
        );
        inTransaction(() ->
            repository.finalizeAttempt(
                "default",
                runId,
                original.attemptId(),
                originalStarted.ownerToken(),
                "actor",
                NOW.plusSeconds(1)
            )
        );

        BeginCommand retry = command(
            UUID.randomUUID(),
            runId,
            planId,
            original.attemptId(),
            "retry-key",
            "retry-hash"
        );
        BeginResult retryStarted = inTransaction(() -> repository.begin(retry));
        assertThat(retryStarted.disposition()).isEqualTo(BeginDisposition.STARTED);
        inTransaction(() ->
            repository.recordSuccess(
                "default",
                runId,
                retry.attemptId(),
                retryStarted.ownerToken(),
                result("model.project.fact", ResultStatus.CREATED)
            )
        );
        inTransaction(() ->
            repository.finalizeAttempt(
                "default",
                runId,
                retry.attemptId(),
                retryStarted.ownerToken(),
                "actor",
                NOW.plusSeconds(2)
            )
        );

        BeginCommand staleRetry = command(
            UUID.randomUUID(),
            runId,
            planId,
            original.attemptId(),
            "stale-retry-key",
            "stale-retry-hash"
        );
        assertThatThrownBy(() -> inTransaction(() -> repository.begin(staleRetry)))
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_RETRY_SOURCE_STALE")
            );
    }

    private BeginCommand command(UUID runId, String idempotencyKey, String requestHash) {
        return command(UUID.randomUUID(), runId, UUID.randomUUID(), null, idempotencyKey, requestHash);
    }

    private BeginCommand command(
        UUID attemptId,
        UUID runId,
        UUID planId,
        UUID retrySourceAttemptId,
        String idempotencyKey,
        String requestHash
    ) {
        return command(
            attemptId,
            runId,
            planId,
            retrySourceAttemptId,
            idempotencyKey,
            requestHash,
            List.of("model.project.fact"),
            List.of("model.project.fact")
        );
    }

    private BeginCommand command(
        UUID attemptId,
        UUID runId,
        UUID planId,
        UUID retrySourceAttemptId,
        String idempotencyKey,
        String requestHash,
        List<String> selectedUniqueIds,
        List<String> selectedClosure
    ) {
        return new BeginCommand(
            attemptId,
            runId,
            planId,
            retrySourceAttemptId,
            "default",
            "preview-hash",
            selectedUniqueIds,
            selectedClosure,
            idempotencyKey,
            requestHash,
            "actor",
            NOW
        );
    }

    private static CandidateResult result(String dbtUniqueId, ResultStatus status) {
        boolean successful = status != ResultStatus.FAILED && status != ResultStatus.BLOCKED;
        return new CandidateResult(
            UUID.randomUUID(),
            0,
            dbtUniqueId,
            "candidate:" + dbtUniqueId,
            "candidate-hash:" + dbtUniqueId,
            status,
            successful ? UUID.randomUUID() : null,
            successful ? 1 : null,
            successful ? "model-checksum" : null,
            successful ? 1 : null,
            successful ? "implementation-checksum" : null,
            successful ? 2 : 0,
            List.of(),
            NOW
        );
    }

    private UUID insertPreviewRun() {
        UUID runId = UUID.randomUUID();
        jdbc.update("insert into modeling_model_spec_import_run (id) values (?)", runId);
        return runId;
    }

    private void insertArtifact(UUID modelSpecId, String projectKey, String dbtUniqueId) {
        jdbc.update(
            """
            insert into modeling_dbt_artifact (
                id, model_spec_id, revision, implementation_revision,
                project_key, dbt_unique_id, node_kind, artifact_type, ownership
            ) values (?, ?, 1, 1, ?, ?, 'MODEL', 'SQL', 'DBT_MANAGED')
            """,
            UUID.randomUUID(),
            modelSpecId,
            projectKey,
            dbtUniqueId
        );
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    private void applyChangelog() throws Exception {
        try (
            Connection connection = connectionInSchema();
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()
        ) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try {
                database.setDefaultSchemaName(schema);
                database.setLiquibaseSchemaName(schema);
                for (String changelog : CHANGELOGS) {
                    Liquibase liquibase = new Liquibase(changelog, resources, database);
                    liquibase.setChangeLogParameter("uuidType", "uuid");
                    liquibase.update(new Contexts(), new LabelExpression());
                }
            } finally {
                if (!database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private Connection connectionInSchema() throws Exception {
        Connection connection = openConnection();
        connection.setSchema(schema);
        return connection;
    }

    private Connection openConnection() throws Exception {
        return java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
    }

    private String schemaJdbcUrl() {
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        return POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema;
    }

    private void requireOwnedSchema() {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalStateException("Refusing to operate on an unowned integration-test schema");
        }
    }
}
