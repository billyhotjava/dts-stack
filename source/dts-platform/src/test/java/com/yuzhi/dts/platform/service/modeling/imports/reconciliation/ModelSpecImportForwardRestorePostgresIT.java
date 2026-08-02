package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationChecksumCodec;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ExpectedImplementationVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecSnapshotCodec;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportForwardRestoreService.ForwardRestoreCommand;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportForwardRestoreService.ForwardRestoreResult;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RevisionPins;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ModelSpecImportForwardRestorePostgresIT {

    private static final String TENANT = "tenant-a";
    private static final String ACTOR = "alice";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000083");
    private static final UUID OTHER_PLAN_ID = UUID.fromString("20000000-0000-0000-0000-000000000083");
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000083");
    private static final UUID IMPLEMENTATION_ID = UUID.fromString("40000000-0000-0000-0000-000000000083");
    private static final UUID DOWNSTREAM_ID = UUID.fromString("50000000-0000-0000-0000-000000000083");
    private static final UUID OTHER_DOWNSTREAM_ID = UUID.fromString("60000000-0000-0000-0000-000000000083");
    private static final UUID OTHER_IMPLEMENTATION_ID = UUID.fromString("70000000-0000-0000-0000-000000000083");
    private static final String POST_MODEL_CHECKSUM = "a".repeat(64);
    private static final String POST_IMPLEMENTATION_CHECKSUM = "b".repeat(64);
    private static final String DOWNSTREAM_CHECKSUM = "e".repeat(64);
    private static final String OTHER_IMPLEMENTATION_CHECKSUM = "f".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");
    private static final Timestamp NOW_SQL = Timestamp.from(NOW);
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s83_forward_undo_[0-9a-f]{32}$");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("modelForwardUndoIT")
        .withUsername("model_undo_test")
        .withPassword("model_undo_test");

    private String schema;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactionManager;
    private TransactionTemplate transactions;
    private ObjectMapper objectMapper;
    private ModelImplementationChecksumCodec implementationChecksums;
    private SaveImplementationCommand historicalCommand;
    private String restoreImplementationChecksum;

    @BeforeEach
    void setUpSchema() throws Exception {
        schema = "s83_forward_undo_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            statement.execute("set search_path to " + schema);
            createTables(statement);
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            schemaJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        jdbc = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        transactions = new TransactionTemplate(transactionManager);
        objectMapper = new ObjectMapper();
        implementationChecksums = new ModelImplementationChecksumCodec(objectMapper);
        historicalCommand = command();
        restoreImplementationChecksum = implementationChecksums.contentChecksum(historicalCommand);
        seedPostImportState();
    }

    @AfterEach
    void dropSchema() throws Exception {
        if (schema == null) return;
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void implementationCasLossRollsBackTheAlreadyAppendedModelSpecRevision() {
        Fixture fixture = fixture(OTHER_PLAN_ID, mock(AuditService.class));

        assertThatThrownBy(() -> restoreInTransaction(fixture))
            .isInstanceOfSatisfying(ModelSpecException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_UNDO_CURRENT_PINS_CHANGED")
            );

        assertPostImportHeadsAndLedgers();
    }

    @Test
    void strictAuditFailureRollsBackBothHeadsAndBothAppendedRevisions() {
        AuditService audit = mock(AuditService.class);
        IllegalStateException failure = new IllegalStateException("audit unavailable");
        doThrow(failure).when(audit).auditActionStrict(any(), any(), any(), any());
        Fixture fixture = fixture(PLAN_ID, audit);

        assertThatThrownBy(() -> restoreInTransaction(fixture)).isSameAs(failure);

        assertPostImportHeadsAndLedgers();
        verify(audit).auditActionStrict(any(), any(), any(), any());
    }

    @Test
    void serializableDownstreamPinAndForwardUndoCannotBothCommit() throws Exception {
        AuditService audit = mock(AuditService.class);
        Fixture fixture = fixture(PLAN_ID, audit);
        CountDownLatch planLockedByPin = new CountDownLatch(1);
        CountDownLatch releasePin = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Void> pinFuture = executor.submit(() -> {
                serializableTransaction().executeWithoutResult(status -> {
                    jdbc.queryForObject(
                        "select id from modeling_warehouse_plan where tenant_id = ? and id = ? for share",
                        UUID.class,
                        TENANT,
                        PLAN_ID
                    );
                    assertThat(jdbc.queryForObject(
                        "select revision from modeling_model_spec where tenant_id = ? and id = ?",
                        Integer.class,
                        TENANT,
                        MODEL_ID
                    )).isEqualTo(11);
                    planLockedByPin.countDown();
                    await(releasePin);
                    jdbc.update(
                        "update modeling_model_spec set depends_on = cast(? as jsonb) where tenant_id = ? and id = ?",
                        "[{\"modelSpecId\":\"" + MODEL_ID + "\",\"revision\":\"11\"}]",
                        TENANT,
                        DOWNSTREAM_ID
                    );
                });
                return null;
            });
            assertThat(planLockedByPin.await(10, TimeUnit.SECONDS)).isTrue();

            Future<UndoOutcome> undoFuture = executor.submit(() -> {
                try {
                    ForwardRestoreResult result = serializableTransaction().execute(status -> {
                        jdbc.queryForObject(
                            "select set_config('application_name', 's83-forward-undo', true)",
                            String.class
                        );
                        return fixture.service().restoreImportedDbtRevision(TENANT, ACTOR, restoreCommand());
                    });
                    return new UndoOutcome(result != null && !result.blocked(), result != null && result.blocked(), null);
                } catch (Throwable failure) {
                    return new UndoOutcome(false, false, failure);
                }
            });

            try {
                assertThat(awaitUndoPlanLock()).isTrue();
            } finally {
                releasePin.countDown();
            }
            pinFuture.get(20, TimeUnit.SECONDS);
            UndoOutcome outcome = undoFuture.get(20, TimeUnit.SECONDS);

            assertThat(outcome.restored()).isFalse();
            assertThat(outcome.blocked() || isSerializationFailure(outcome.failure())).isTrue();
        }

        assertThat(jdbc.queryForObject(
            "select revision from modeling_model_spec where tenant_id = ? and id = ?",
            Integer.class,
            TENANT,
            MODEL_ID
        )).isEqualTo(11);
        assertThat(jdbc.queryForObject(
            "select jsonb_array_length(depends_on) from modeling_model_spec where tenant_id = ? and id = ?",
            Integer.class,
            TENANT,
            DOWNSTREAM_ID
        )).isEqualTo(1);
    }

    @Test
    void serializableReleaseCandidateAndForwardUndoCannotBothCommit() throws Exception {
        UUID candidateId = UUID.randomUUID();

        UndoOutcome outcome = raceTargetPlanGovernanceWrite(() ->
            jdbc.update(
                "insert into modeling_model_release_candidate_entry (tenant_id, candidate_id, model_spec_id) values (?, ?, ?)",
                TENANT,
                candidateId,
                MODEL_ID
            )
        );

        assertUndoDidNotCommit(outcome);
        assertThat(jdbc.queryForObject(
            "select count(*) from modeling_model_release_candidate_entry where tenant_id = ? and candidate_id = ?",
            Integer.class,
            TENANT,
            candidateId
        )).isEqualTo(1);
    }

    @Test
    void serializableMaterializationAndForwardUndoCannotBothCommit() throws Exception {
        UUID candidateId = UUID.randomUUID();

        UndoOutcome outcome = raceTargetPlanGovernanceWrite(() -> {
            jdbc.update(
                "insert into modeling_model_release_candidate_entry (tenant_id, candidate_id, model_spec_id) values (?, ?, ?)",
                TENANT,
                candidateId,
                MODEL_ID
            );
            jdbc.update(
                "insert into modeling_materialization_dispatch (tenant_id, candidate_id) values (?, ?)",
                TENANT,
                candidateId
            );
        });

        assertUndoDidNotCommit(outcome);
        assertThat(jdbc.queryForObject(
            "select count(*) from modeling_materialization_dispatch where tenant_id = ? and candidate_id = ?",
            Integer.class,
            TENANT,
            candidateId
        )).isEqualTo(1);
    }

    @Test
    void serializableDifferentPlanDownstreamPinAndForwardUndoCannotBothCommit() throws Exception {
        UndoOutcome outcome = raceDifferentPlanMutation(() ->
            jdbc.update(
                "update modeling_model_spec set depends_on = cast(? as jsonb) where tenant_id = ? and id = ?",
                "[{\"modelSpecId\":\"" + MODEL_ID + "\",\"revision\":\"11\"}]",
                TENANT,
                OTHER_DOWNSTREAM_ID
            )
        );

        assertThat(outcome.restored()).isFalse();
        assertThat(isSerializationFailure(outcome.failure())).isTrue();
        assertThat(jdbc.queryForObject(
            "select jsonb_array_length(depends_on) from modeling_model_spec where tenant_id = ? and id = ?",
            Integer.class,
            TENANT,
            OTHER_DOWNSTREAM_ID
        )).isEqualTo(1);
    }

    @Test
    void serializableImplementationPinAndForwardUndoCannotBothCommit() throws Exception {
        UndoOutcome outcome = raceDifferentPlanMutation(() ->
            jdbc.update(
                "update modeling_model_implementation set inputs_json = cast(? as jsonb) where tenant_id = ? and id = ?",
                "[{\"modelSpecId\":\"" + MODEL_ID + "\",\"revision\":\"11\",\"checksum\":\"" +
                POST_MODEL_CHECKSUM + "\",\"implementationRevision\":\"6\",\"implementationChecksum\":\"" +
                POST_IMPLEMENTATION_CHECKSUM + "\"}]",
                TENANT,
                OTHER_IMPLEMENTATION_ID
            )
        );

        assertThat(outcome.restored()).isFalse();
        assertThat(isSerializationFailure(outcome.failure())).isTrue();
        assertThat(jdbc.queryForObject(
            "select jsonb_array_length(inputs_json) from modeling_model_implementation where tenant_id = ? and id = ?",
            Integer.class,
            TENANT,
            OTHER_IMPLEMENTATION_ID
        )).isEqualTo(1);
    }

    private UndoOutcome raceTargetPlanGovernanceWrite(Runnable governanceWrite) throws Exception {
        Fixture fixture = fixture(PLAN_ID, mock(AuditService.class));
        CountDownLatch planLockedByWriter = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Void> writerFuture = executor.submit(() -> {
                serializableTransaction().executeWithoutResult(status -> {
                    jdbc.queryForObject(
                        "select id from modeling_warehouse_plan where tenant_id = ? and id = ? for update",
                        UUID.class,
                        TENANT,
                        PLAN_ID
                    );
                    assertThat(jdbc.queryForObject(
                        "select revision from modeling_model_spec where tenant_id = ? and id = ?",
                        Integer.class,
                        TENANT,
                        MODEL_ID
                    )).isEqualTo(11);
                    planLockedByWriter.countDown();
                    await(releaseWriter);
                    governanceWrite.run();
                });
                return null;
            });
            assertThat(planLockedByWriter.await(10, TimeUnit.SECONDS)).isTrue();
            Future<UndoOutcome> undoFuture = executor.submit(() -> serializableUndo(fixture));
            try {
                assertThat(awaitUndoPlanLock()).isTrue();
            } finally {
                releaseWriter.countDown();
            }
            writerFuture.get(20, TimeUnit.SECONDS);
            return undoFuture.get(20, TimeUnit.SECONDS);
        }
    }

    private UndoOutcome raceDifferentPlanMutation(Runnable mutation) throws Exception {
        Fixture fixture = fixture(PLAN_ID, mock(AuditService.class));
        CountDownLatch writerReady = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        CountDownLatch restorePassedEligibility = new CountDownLatch(1);
        CountDownLatch releaseRestore = new CountDownLatch(1);
        doAnswer(invocation -> {
            restorePassedEligibility.countDown();
            await(releaseRestore);
            return fixture.historicalModel();
        })
            .when(fixture.modelSpecs())
            .revision(eq(TENANT), any());

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Void> writerFuture = executor.submit(() -> {
                serializableTransaction().executeWithoutResult(status -> {
                    jdbc.queryForObject(
                        "select id from modeling_warehouse_plan where tenant_id = ? and id = ? for share",
                        UUID.class,
                        TENANT,
                        OTHER_PLAN_ID
                    );
                    assertThat(jdbc.queryForObject(
                        "select revision from modeling_model_spec where tenant_id = ? and id = ?",
                        Integer.class,
                        TENANT,
                        MODEL_ID
                    )).isEqualTo(11);
                    assertThat(jdbc.queryForObject(
                        "select implementation_revision from modeling_model_implementation where tenant_id = ? and id = ?",
                        Integer.class,
                        TENANT,
                        IMPLEMENTATION_ID
                    )).isEqualTo(6);
                    writerReady.countDown();
                    await(releaseWriter);
                    mutation.run();
                });
                return null;
            });
            assertThat(writerReady.await(10, TimeUnit.SECONDS)).isTrue();
            Future<UndoOutcome> undoFuture = executor.submit(() -> serializableUndo(fixture));
            if (!restorePassedEligibility.await(10, TimeUnit.SECONDS)) {
                releaseWriter.countDown();
                releaseRestore.countDown();
                throw new AssertionError("Forward undo did not pass its eligibility snapshot");
            }
            releaseWriter.countDown();
            writerFuture.get(20, TimeUnit.SECONDS);
            releaseRestore.countDown();
            return undoFuture.get(20, TimeUnit.SECONDS);
        } finally {
            releaseWriter.countDown();
            releaseRestore.countDown();
        }
    }

    private UndoOutcome serializableUndo(Fixture fixture) {
        try {
            ForwardRestoreResult result = serializableTransaction().execute(status -> {
                jdbc.queryForObject(
                    "select set_config('application_name', 's83-forward-undo', true)",
                    String.class
                );
                return fixture.service().restoreImportedDbtRevision(TENANT, ACTOR, restoreCommand());
            });
            return new UndoOutcome(result != null && !result.blocked(), result != null && result.blocked(), null);
        } catch (Throwable failure) {
            return new UndoOutcome(false, false, failure);
        }
    }

    private void assertUndoDidNotCommit(UndoOutcome outcome) {
        assertThat(outcome.restored()).isFalse();
        assertThat(outcome.blocked() || isSerializationFailure(outcome.failure())).isTrue();
        assertPostImportHeadsAndLedgers();
    }

    private Fixture fixture(UUID restoredPlanId, AuditService audit) {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecSnapshotCodec snapshots = mock(ModelSpecSnapshotCodec.class);
        ModelSpecRepository modelSpecRepository = new ModelSpecRepository(jdbc, objectMapper);
        ModelLifecycleRepository lifecycle = new ModelLifecycleRepository(jdbc, objectMapper);
        ModelSpecImportReconciliationRepository reconciliation = new ModelSpecImportReconciliationRepository(
            jdbc,
            objectMapper
        );
        ModelSpecView historical = model(8, PLAN_ID);
        ModelSpecView current = model(11, PLAN_ID);
        ModelSpecView appended = model(12, restoredPlanId);
        when(modelSpecs.revision(eq(TENANT), any())).thenReturn(historical);
        when(modelSpecs.update(eq(TENANT), eq(ACTOR), eq(MODEL_ID), any(), any())).thenReturn(current);
        when(snapshots.toUpdatedView(eq(current), any(), eq(12), eq(NOW))).thenReturn(appended);
        when(snapshots.write(appended)).thenReturn("{\"revision\":12}");
        ModelSpecImportForwardRestoreService service = new ModelSpecImportForwardRestoreService(
            modelSpecs,
            modelSpecRepository,
            snapshots,
            lifecycle,
            reconciliation,
            implementationChecksums,
            audit,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(service, modelSpecs, historical);
    }

    private void restoreInTransaction(Fixture fixture) {
        transactions.executeWithoutResult(status ->
            fixture.service().restoreImportedDbtRevision(TENANT, ACTOR, restoreCommand())
        );
    }

    private ForwardRestoreCommand restoreCommand() {
        return new ForwardRestoreCommand(
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 11, POST_MODEL_CHECKSUM),
            new ExpectedImplementationVersion(MODEL_ID, 6, POST_IMPLEMENTATION_CHECKSUM),
            new RevisionPins(8, POST_MODEL_CHECKSUM, 3, restoreImplementationChecksum),
            "finance",
            "model.finance.budget",
            "undo-83"
        );
    }

    private ModelSpecView model(int revision, UUID planId) {
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(MODEL_ID);
        when(model.planId()).thenReturn(planId);
        when(model.revision()).thenReturn(revision);
        when(model.checksum()).thenReturn(POST_MODEL_CHECKSUM);
        when(model.status()).thenReturn(ModelStatus.DRAFT);
        when(model.implementationMode()).thenReturn(ImplementationMode.DBT_MANAGED);
        when(model.updatedAt()).thenReturn(NOW);
        return model;
    }

    private static SaveImplementationCommand command() {
        return new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DBT", Map.of("projectKey", "finance", "dbtUniqueId", "model.finance.budget"))),
            List.of(),
            Map.of(),
            ImplementationMode.DBT_MANAGED,
            "table",
            "history-key"
        );
    }

    private void seedPostImportState() throws Exception {
        String inputs = objectMapper.writeValueAsString(historicalCommand.inputs());
        jdbc.update("insert into modeling_warehouse_plan (id, tenant_id) values (?, ?), (?, ?)", PLAN_ID, TENANT, OTHER_PLAN_ID, TENANT);
        jdbc.update(
            """
            insert into modeling_model_spec (
                id, tenant_id, plan_id, status, revision, current_checksum, contract_version,
                version, last_modified_date, depends_on, dimension_refs
            ) values (?, ?, ?, 'DRAFT', 11, ?, 2, 11, ?, '[]'::jsonb, '[]'::jsonb),
                     (?, ?, ?, 'DRAFT', 1, ?, 2, 1, ?, '[]'::jsonb, '[]'::jsonb),
                     (?, ?, ?, 'DRAFT', 1, ?, 2, 1, ?, '[]'::jsonb, '[]'::jsonb)
            """,
            MODEL_ID,
            TENANT,
            PLAN_ID,
            POST_MODEL_CHECKSUM,
            NOW_SQL,
            DOWNSTREAM_ID,
            TENANT,
            PLAN_ID,
            DOWNSTREAM_CHECKSUM,
            NOW_SQL,
            OTHER_DOWNSTREAM_ID,
            TENANT,
            OTHER_PLAN_ID,
            DOWNSTREAM_CHECKSUM,
            NOW_SQL
        );
        insertModelRevision(8, POST_MODEL_CHECKSUM);
        insertModelRevision(11, POST_MODEL_CHECKSUM);
        jdbc.update(
            """
            insert into modeling_model_implementation (
                id, tenant_id, model_spec_id, plan_id, model_revision, model_checksum,
                ownership, project_key, dbt_unique_id, status, implementation_revision,
                current_implementation_checksum, input_mode, inputs_json, field_mappings_json,
                settings_json, materialization, idempotency_key, last_modified_date
            ) values (?, ?, ?, ?, 11, ?, 'DBT_MANAGED', 'finance', 'model.finance.budget',
                      'ACTIVE', 6, ?, 'GENERATED', cast(? as jsonb), '[]'::jsonb,
                      '{}'::jsonb, 'table', 'post-import', ?)
            """,
            IMPLEMENTATION_ID,
            TENANT,
            MODEL_ID,
            PLAN_ID,
            POST_MODEL_CHECKSUM,
            POST_IMPLEMENTATION_CHECKSUM,
            inputs,
            NOW_SQL
        );
        jdbc.update(
            """
            insert into modeling_model_implementation (
                id, tenant_id, model_spec_id, plan_id, model_revision, model_checksum,
                ownership, project_key, dbt_unique_id, status, implementation_revision,
                current_implementation_checksum, input_mode, inputs_json, field_mappings_json,
                settings_json, materialization, idempotency_key, last_modified_date
            ) values (?, ?, ?, ?, 1, ?, 'DESIGNER_GENERATED', 'other', 'model.other.consumer',
                      'ACTIVE', 1, ?, 'GENERATED', '[]'::jsonb, '[]'::jsonb,
                      '{}'::jsonb, 'table', 'other-current', ?)
            """,
            OTHER_IMPLEMENTATION_ID,
            TENANT,
            OTHER_DOWNSTREAM_ID,
            OTHER_PLAN_ID,
            DOWNSTREAM_CHECKSUM,
            OTHER_IMPLEMENTATION_CHECKSUM,
            NOW_SQL
        );
        insertImplementationRevision(3, restoreImplementationChecksum, inputs);
        insertImplementationRevision(6, POST_IMPLEMENTATION_CHECKSUM, inputs);
    }

    private void insertModelRevision(int revision, String checksum) {
        jdbc.update(
            """
            insert into modeling_model_spec_revision (
                id, model_spec_id, revision, status, content_checksum, created_date,
                last_modified_date, tenant_id, contract_version, snapshot_json, created_by
            ) values (?, ?, ?, 'DRAFT', ?, ?, ?, ?, 2, '{}'::jsonb, 'seed')
            """,
            UUID.randomUUID(),
            MODEL_ID,
            revision,
            checksum,
            NOW_SQL,
            NOW_SQL,
            TENANT
        );
    }

    private void insertImplementationRevision(int revision, String checksum, String inputs) {
        jdbc.update(
            """
            insert into modeling_model_implementation_revision (
                id, tenant_id, implementation_id, revision, content_checksum, input_mode,
                inputs_json, field_mappings_json, settings_json, ownership, materialization,
                created_by, created_date
            ) values (?, ?, ?, ?, ?, 'GENERATED', cast(? as jsonb), '[]'::jsonb,
                      '{}'::jsonb, 'DBT_MANAGED', 'table', 'seed', ?)
            """,
            UUID.randomUUID(),
            TENANT,
            IMPLEMENTATION_ID,
            revision,
            checksum,
            inputs,
            NOW_SQL
        );
    }

    private void assertPostImportHeadsAndLedgers() {
        assertThat(jdbc.queryForObject(
            "select revision from modeling_model_spec where tenant_id = ? and id = ?",
            Integer.class,
            TENANT,
            MODEL_ID
        )).isEqualTo(11);
        assertThat(jdbc.queryForObject(
            "select implementation_revision from modeling_model_implementation where tenant_id = ? and model_spec_id = ?",
            Integer.class,
            TENANT,
            MODEL_ID
        )).isEqualTo(6);
        assertThat(jdbc.queryForObject(
            "select model_revision from modeling_model_implementation where tenant_id = ? and model_spec_id = ?",
            Integer.class,
            TENANT,
            MODEL_ID
        )).isEqualTo(11);
        assertThat(jdbc.queryForObject(
            "select count(*) from modeling_model_spec_revision where tenant_id = ? and model_spec_id = ?",
            Integer.class,
            TENANT,
            MODEL_ID
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            """
            select count(*) from modeling_model_implementation_revision
             where tenant_id = ? and implementation_id = ?
            """,
            Integer.class,
            TENANT,
            IMPLEMENTATION_ID
        )).isEqualTo(2);
    }

    private TransactionTemplate serializableTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        transaction.setTimeout(20);
        return transaction;
    }

    private boolean awaitUndoPlanLock() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            Boolean waiting = jdbc.queryForObject(
                """
                select exists (
                    select 1 from pg_stat_activity
                     where datname = current_database()
                       and application_name = 's83-forward-undo'
                       and wait_event_type = 'Lock'
                )
                """,
                Boolean.class
            );
            if (Boolean.TRUE.equals(waiting)) return true;
            Thread.sleep(25);
        }
        return false;
    }

    private static boolean isSerializationFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof CannotSerializeTransactionException) return true;
            if (current instanceof SQLException sql && "40001".equals(sql.getSQLState())) return true;
            current = current.getCause();
        }
        return false;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for concurrent test step");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void createTables(Statement statement) throws SQLException {
        statement.execute(
            """
            create table modeling_warehouse_plan (
                id uuid primary key, tenant_id varchar(128) not null, unique (tenant_id, id)
            )
            """
        );
        statement.execute(
            """
            create table modeling_model_spec (
                id uuid primary key, tenant_id varchar(128) not null, plan_id uuid not null,
                status varchar(32) not null, revision int not null, current_checksum varchar(64) not null,
                contract_version int not null, version bigint not null default 1,
                last_modified_date timestamp not null, depends_on jsonb not null default '[]'::jsonb,
                dimension_refs jsonb not null default '[]'::jsonb
            )
            """
        );
        statement.execute(
            """
            create table modeling_model_spec_revision (
                id uuid primary key, model_spec_id uuid not null, revision int not null,
                status varchar(32) not null, content_checksum varchar(64) not null,
                created_date timestamp, last_modified_date timestamp, tenant_id varchar(128) not null,
                contract_version int not null, snapshot_json jsonb not null, created_by varchar(128),
                dimension_definition_id uuid, dimension_definition_revision int,
                data_mart_id uuid, variant_code varchar(128),
                unique (tenant_id, model_spec_id, revision)
            )
            """
        );
        statement.execute(
            """
            create table modeling_model_implementation (
                id uuid primary key, tenant_id varchar(128) not null, model_spec_id uuid not null,
                plan_id uuid not null, model_revision int not null, model_checksum varchar(64) not null,
                ownership varchar(32) not null, project_key varchar(256) not null,
                dbt_unique_id varchar(512) not null, status varchar(32) not null,
                implementation_revision int not null, current_implementation_checksum varchar(64) not null,
                input_mode varchar(32) not null, inputs_json jsonb not null,
                field_mappings_json jsonb not null, settings_json jsonb not null,
                materialization varchar(64), idempotency_key varchar(256), last_modified_date timestamp
            )
            """
        );
        statement.execute(
            """
            create table modeling_model_implementation_revision (
                id uuid primary key, tenant_id varchar(128) not null, implementation_id uuid not null,
                revision int not null, content_checksum varchar(64) not null,
                input_mode varchar(32) not null, inputs_json jsonb not null,
                field_mappings_json jsonb not null, settings_json jsonb not null,
                ownership varchar(32) not null, materialization varchar(64),
                created_by varchar(128), created_date timestamp,
                unique (tenant_id, implementation_id, revision)
            )
            """
        );
        statement.execute(
            """
            create table modeling_model_release_candidate_entry (
                tenant_id varchar(128) not null, candidate_id uuid not null, model_spec_id uuid not null
            )
            """
        );
        statement.execute(
            """
            create table modeling_materialization_dispatch (
                tenant_id varchar(128) not null, candidate_id uuid not null
            )
            """
        );
    }

    private Connection openConnection() throws SQLException {
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

    private record Fixture(
        ModelSpecImportForwardRestoreService service,
        ModelSpecApplicationService modelSpecs,
        ModelSpecView historicalModel
    ) {}

    private record UndoOutcome(boolean restored, boolean blocked, Throwable failure) {}
}
