package com.yuzhi.dts.platform.service.modeling.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.representation.CatalogModelPhysicalPreviewReferenceAdapter;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PhysicalPreviewEvidenceState;
import com.yuzhi.dts.platform.service.modeling.representation.ModelRepresentationContract.PhysicalPreviewScope;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.SuccessfulPublicationCommand;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class Sprint83G3DatabaseRehearsalPostgresIT {

    private static final String APPLY_CHANGELOG =
        "config/liquibase/changelog/20260725_04_model_spec_import_apply.xml";
    private static final String LEASE_CHANGELOG =
        "config/liquibase/changelog/20260725_05_model_spec_import_apply_lease.xml";
    private static final String CANONICAL_CHANGELOG =
        "config/liquibase/changelog/20260802_01_model_spec_import_apply_status_canonical.xml";
    private static final String RECONCILIATION_CHANGELOG =
        "config/liquibase/changelog/20260802_04_model_spec_import_reconciliation_undo.xml";
    private static final String SERVING_CHANGELOG =
        "config/liquibase/changelog/20260802_03_catalog_model_serving_projection.xml";
    private static final String SERVING_CONSTRAINT_CHANGELOG =
        "config/liquibase/changelog/20260802_05_catalog_model_serving_asset_key_constraint.xml";
    private static final String HARDENING_CHANGELOG =
        "config/liquibase/changelog/20260802_06_model_spec_import_json_contract_hardening.xml";
    private static final String MASTER_CHANGELOG = "config/liquibase/master.xml";
    private static final String HARDENING_PREFLIGHT_SQL =
        "worklog/v2.2.3/sprint-83-202608-dbt-visual-roundtrip-modeling/it/sql/" +
        "g3-model-spec-import-json-hardening-preflight.sql";
    private static final String CANONICAL_CHANGESET_ID =
        "20260802_01_model_spec_import_apply_status_canonical";
    private static final String HARDENING_CHANGESET_ID =
        "20260802_06_model_spec_import_json_contract_hardening";
    private static final Pattern OWNED_SCHEMA = Pattern.compile("^s83_g3_[0-9a-f]{32}$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Instant NOW = Instant.parse("2026-08-02T10:00:00Z");

    private static final String TENANT = "s83-g3-tenant";
    private static final UUID MODEL_SPEC_ID = UUID.fromString("30000000-0000-0000-0000-000000000083");
    private static final UUID SOURCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000083");
    private static final UUID PHYSICAL_ASSET_ID = UUID.fromString("40000000-0000-0000-0000-000000000083");
    private static final String R1_MODEL_CHECKSUM = "a".repeat(64);
    private static final String R1_IMPLEMENTATION_CHECKSUM = "b".repeat(64);
    private static final String R2_MODEL_CHECKSUM = "d".repeat(64);
    private static final String R2_IMPLEMENTATION_CHECKSUM = "e".repeat(64);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("sprint83_g3_database_rehearsal_it")
        .withUsername("sprint83_g3_test")
        .withPassword("sprint83_g3_test");

    private String schema;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;

    @BeforeEach
    void createOwnedSchema() throws Exception {
        schema = "s83_g3_" + UUID.randomUUID().toString().replace("-", "");
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            schemaJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterEach
    void dropOwnedSchema() throws Exception {
        if (schema == null) {
            return;
        }
        requireOwnedSchema();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void populatedLegacyLedgerCanonicalizesAttemptItemsSummaryAndForwardOnlyColumns() throws Exception {
        prepareLegacyImportSchema();
        LegacyFixture fixture = seedLegacyLedger(
            """
            {
              "total": 2,
              "created": 1,
              "updated": 0,
              "skipped": 0,
              "replayed": 1,
              "failed": 0,
              "blocked": 0
            }
            """,
            List.of("model.project.fact", "model.project.summary"),
            List.of("model.project.fact", "model.project.summary")
        );

        applyChangelog(CANONICAL_CHANGELOG);
        applyChangelog(RECONCILIATION_CHANGELOG);
        applyChangelog(HARDENING_CHANGELOG);

        Map<String, Object> attempt = jdbc.queryForMap(
            """
            select status, summary_json::text summary_json, operation_type, target_attempt_id
              from modeling_model_spec_import_apply_attempt
             where id = ?
            """,
            fixture.attemptId()
        );
        assertThat(attempt.get("status")).isEqualTo("SUCCESS");
        assertThat(json((String) attempt.get("summary_json"))).isEqualTo(
            json(
                """
                {
                  "selected": 2,
                  "pending": 0,
                  "succeeded": 1,
                  "created": 1,
                  "updated": 0,
                  "skipped": 1,
                  "failed": 0,
                  "blocked": 0
                }
                """
            )
        );
        assertThat(attempt.get("operation_type")).isEqualTo("APPLY");
        assertThat(attempt.get("target_attempt_id")).isNull();

        List<CanonicalItem> items = jdbc.query(
            """
            select dbt_unique_id, status, applied_action, merge_checkpoint_json::text,
                   dependency_json::text
              from modeling_model_spec_import_apply_result
             where attempt_id = ?
             order by seq
            """,
            (row, rowNumber) ->
                new CanonicalItem(
                    row.getString("dbt_unique_id"),
                    row.getString("status"),
                    row.getString("applied_action"),
                    row.getString("merge_checkpoint_json"),
                    row.getString("dependency_json")
                ),
            fixture.attemptId()
        );
        assertThat(items).containsExactly(
            new CanonicalItem("model.project.fact", "CREATED", "CREATE", null, "[]"),
            new CanonicalItem("model.project.summary", "SKIPPED", "SKIP", null, "[]")
        );
        assertThat(appliedChangeSetCount(CANONICAL_CHANGESET_ID)).isEqualTo(1);
        assertThat(validatedConstraints("modeling_model_spec_import_apply_attempt"))
            .contains(
                "ck_model_spec_import_apply_summary_canonical",
                "ck_model_spec_import_apply_summary_complete",
                "ck_model_spec_import_apply_closure_unique",
                "ck_model_spec_import_apply_operation"
            );
        assertThat(validatedConstraints("modeling_model_spec_import_apply_result"))
            .contains(
                "ck_model_spec_import_apply_issue_contract",
                "ck_model_spec_import_result_checkpoint_all_or_none",
                "ck_model_spec_import_result_checkpoint_complete",
                "ck_model_spec_import_result_pre_pins_complete"
            );
        assertThat(appliedChangeSetCount(HARDENING_CHANGESET_ID)).isEqualTo(1);
    }

    @Test
    void hardeningRejectsMissingSummaryKeysEmptyCheckpointPinsAndDuplicateClosureMembers() throws Exception {
        prepareLegacyImportSchema();
        LegacyFixture fixture = seedLegacyLedger(
            completeLegacySummary(),
            List.of("model.project.fact", "model.project.summary"),
            List.of("model.project.fact", "model.project.summary")
        );
        applyChangelog(CANONICAL_CHANGELOG);
        applyChangelog(RECONCILIATION_CHANGELOG);

        assertThat(
            jdbc.update(
                """
                update modeling_model_spec_import_apply_result
                   set project_key = 'project',
                       merge_checkpoint_json = cast(? as jsonb)
                 where attempt_id = ? and seq = 0
                """,
                completeCheckpointJson(),
                fixture.attemptId()
            )
        ).isEqualTo(1);
        assertThat(
            jdbc.update(
                """
                update modeling_model_spec_import_apply_result
                   set pre_attempt_pins_json = '{}'::jsonb
                 where attempt_id = ? and seq = 1
                """,
                fixture.attemptId()
            )
        ).isEqualTo(1);

        List<PreflightDiagnostic> diagnostics = runHardeningPreflight();
        PreflightDiagnostic checkpointDiagnostic = diagnostics
            .stream()
            .filter(diagnostic -> diagnostic.reasonCodesJson().contains("CHECKPOINT_ACCEPTED_EXTERNAL_CHECKSUM_INVALID"))
            .findFirst()
            .orElseThrow();
        PreflightDiagnostic prePinsDiagnostic = diagnostics
            .stream()
            .filter(diagnostic -> diagnostic.reasonCodesJson().contains("PRE_PINS_MISSING_MODEL_REVISION"))
            .findFirst()
            .orElseThrow();
        assertSafeResultDiagnostic(checkpointDiagnostic);
        assertSafeResultDiagnostic(prePinsDiagnostic);
        assertThat(checkpointDiagnostic.reasonCodesJson())
            .contains(
                "CHECKPOINT_ACCEPTED_EXTERNAL_CHECKSUM_INVALID",
                "CHECKPOINT_ACCEPTED_IMPLEMENTATION_REVISION_INVALID",
                "CHECKPOINT_ACCEPTED_IMPLEMENTATION_CHECKSUM_INVALID",
                "CHECKPOINT_MAPPED_MODEL_SPEC_REVISION_INVALID",
                "CHECKPOINT_MAPPED_MODEL_SPEC_ETAG_INVALID"
            );
        assertThat(prePinsDiagnostic.reasonCodesJson())
            .contains(
                "PRE_PINS_MISSING_MODEL_REVISION",
                "PRE_PINS_MISSING_MODEL_CHECKSUM",
                "PRE_PINS_MISSING_IMPLEMENTATION_REVISION",
                "PRE_PINS_MISSING_IMPLEMENTATION_CHECKSUM"
            );

        String beforeHalt = legacyLedgerSnapshot();
        assertThatThrownBy(() -> applyChangelog(HARDENING_CHANGELOG))
            .hasStackTraceContaining(HARDENING_CHANGESET_ID);
        assertThat(appliedChangeSetCount(HARDENING_CHANGESET_ID)).isZero();
        assertThat(legacyLedgerSnapshot()).isEqualTo(beforeHalt);
        assertThat(helperFunctionCount()).isZero();
        assertThat(validatedConstraints("modeling_model_spec_import_apply_attempt"))
            .doesNotContain("ck_model_spec_import_apply_summary_complete", "ck_model_spec_import_apply_closure_unique");

        assertThat(
            jdbc.update(
                """
                update modeling_model_spec_import_apply_result
                   set merge_checkpoint_json = null,
                       accepted_external_checksum = null,
                       accepted_implementation_revision = null,
                       accepted_implementation_checksum = null,
                       mapped_model_spec_revision = null,
                       mapped_model_spec_etag = null,
                       pre_attempt_pins_json = null
                 where attempt_id = ?
                """,
                fixture.attemptId()
            )
        ).isEqualTo(2);
        applyChangelog(HARDENING_CHANGELOG);

        assertThatThrownBy(() ->
            jdbc.update(
                """
                update modeling_model_spec_import_apply_result
                   set merge_checkpoint_json = cast(? as jsonb)
                 where attempt_id = ? and seq = 0
                """,
                completeCheckpointJson(),
                fixture.attemptId()
            )
        )
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_model_spec_import_result_checkpoint_complete");

        assertThat(
            jdbc.update(
                """
                update modeling_model_spec_import_apply_result
                   set project_key = 'project',
                       accepted_external_checksum = ?,
                       accepted_implementation_revision = 1,
                       accepted_implementation_checksum = ?,
                       mapped_model_spec_revision = 1,
                       mapped_model_spec_etag = ?,
                       merge_checkpoint_json = cast(? as jsonb),
                       pre_attempt_pins_json = cast(? as jsonb)
                 where attempt_id = ? and seq = 0
                """,
                "3".repeat(64),
                "4".repeat(64),
                "6".repeat(64),
                completeCheckpointJson(),
                completePrePinsJson(),
                fixture.attemptId()
            )
        ).isEqualTo(1);

        assertThatThrownBy(() ->
            jdbc.update(
                """
                update modeling_model_spec_import_apply_attempt
                   set summary_json = summary_json - 'blocked'
                 where id = ?
                """,
                fixture.attemptId()
            )
        )
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_model_spec_import_apply_summary_complete");
        assertThatThrownBy(() ->
            jdbc.update(
                """
                update modeling_model_spec_import_apply_attempt
                   set selected_closure_json = '["model.project.fact", "model.project.fact"]'::jsonb
                 where id = ?
                """,
                fixture.attemptId()
            )
        )
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_model_spec_import_apply_closure_unique");

        for (String checkpointJson : List.of("{}", incompleteCheckpointJson())) {
            assertThatThrownBy(() ->
                jdbc.update(
                    """
                    update modeling_model_spec_import_apply_result
                       set project_key = 'project',
                           accepted_external_checksum = ?,
                           accepted_implementation_revision = 1,
                           accepted_implementation_checksum = ?,
                           mapped_model_spec_revision = 1,
                           mapped_model_spec_etag = ?,
                           merge_checkpoint_json = cast(? as jsonb)
                     where attempt_id = ? and seq = 0
                    """,
                    "3".repeat(64),
                    "4".repeat(64),
                    "6".repeat(64),
                    checkpointJson,
                    fixture.attemptId()
                )
            )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_model_spec_import_result_checkpoint_complete");
        }
        for (
            String prePinsJson : List.of(
                "{}",
                "{\"modelRevision\":1,\"modelChecksum\":\"" + "7".repeat(64) + "\"}"
            )
        ) {
            assertThatThrownBy(() ->
                jdbc.update(
                    """
                    update modeling_model_spec_import_apply_result
                       set pre_attempt_pins_json = cast(? as jsonb)
                     where attempt_id = ? and seq = 0
                    """,
                    prePinsJson,
                    fixture.attemptId()
                )
            )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_model_spec_import_result_pre_pins_complete");
        }
    }

    @Test
    void duplicateClosureInCanonicalDataHaltsHardeningWithoutCreatingFunctionOrConstraints() throws Exception {
        prepareLegacyImportSchema();
        LegacyFixture fixture = seedLegacyRunningDuplicateClosure();
        applyChangelog(CANONICAL_CHANGELOG);
        applyChangelog(RECONCILIATION_CHANGELOG);
        String before = legacyLedgerSnapshot();

        assertThatThrownBy(() -> applyChangelog(HARDENING_CHANGELOG))
            .hasStackTraceContaining(HARDENING_CHANGESET_ID);

        assertThat(appliedChangeSetCount(HARDENING_CHANGESET_ID)).isZero();
        assertThat(legacyLedgerSnapshot()).isEqualTo(before);
        assertThat(helperFunctionCount()).isZero();
        assertThat(validatedConstraints("modeling_model_spec_import_apply_attempt"))
            .doesNotContain("ck_model_spec_import_apply_summary_complete", "ck_model_spec_import_apply_closure_unique");
        assertThat(duplicateClosureIds(fixture.attemptId())).containsExactly("model.project.fact");
    }

    @Test
    void resultConstraintNameConflictRollsBackFunctionAttemptConstraintsAndChangelog() throws Exception {
        prepareLegacyImportSchema();
        seedLegacyLedger(
            completeLegacySummary(),
            List.of("model.project.fact", "model.project.summary"),
            List.of("model.project.fact", "model.project.summary")
        );
        applyChangelog(CANONICAL_CHANGELOG);
        applyChangelog(RECONCILIATION_CHANGELOG);
        execute(
            """
            alter table modeling_model_spec_import_apply_result
                add constraint ck_model_spec_import_result_checkpoint_complete check (true)
            """
        );
        String before = legacyLedgerSnapshot();

        assertThatThrownBy(() -> applyChangelog(HARDENING_CHANGELOG))
            .hasStackTraceContaining("ck_model_spec_import_result_checkpoint_complete")
            .hasStackTraceContaining("already exists");

        assertThat(appliedChangeSetCount(HARDENING_CHANGESET_ID)).isZero();
        assertThat(legacyLedgerSnapshot()).isEqualTo(before);
        assertThat(helperFunctionCount()).isZero();
        assertThat(validatedConstraints("modeling_model_spec_import_apply_attempt"))
            .doesNotContain("ck_model_spec_import_apply_summary_complete", "ck_model_spec_import_apply_closure_unique");
    }

    @Test
    @Timeout(60)
    void cleanOwnedSchemaAppliesMasterThroughSprint83Hardening() throws Exception {
        applyChangelog(MASTER_CHANGELOG);

        assertThat(appliedChangeSetCount(HARDENING_CHANGESET_ID)).isEqualTo(1);
        assertThat(validatedConstraints("modeling_model_spec_import_apply_attempt"))
            .contains("ck_model_spec_import_apply_summary_complete", "ck_model_spec_import_apply_closure_unique");
    }

    @Test
    void summaryMismatchHaltsBeforeAnyCanonicalMutationAndIdentifiesTheRepairAttempt() throws Exception {
        prepareLegacyImportSchema();
        LegacyFixture fixture = seedLegacyLedger(
            """
            {
              "total": 1,
              "created": 1,
              "updated": 0,
              "skipped": 0,
              "replayed": 1,
              "failed": 0,
              "blocked": 0
            }
            """,
            List.of("model.project.fact", "model.project.summary"),
            List.of("model.project.fact", "model.project.summary")
        );
        String before = legacyLedgerSnapshot();

        assertThatThrownBy(() -> applyChangelog(CANONICAL_CHANGELOG))
            .hasStackTraceContaining(CANONICAL_CHANGESET_ID);

        assertThat(appliedChangeSetCount(CANONICAL_CHANGESET_ID)).isZero();
        assertThat(legacyLedgerSnapshot()).isEqualTo(before);
        assertThat(summaryMismatchAttemptIds()).containsExactly(fixture.attemptId());
        assertThat(legacyStatusResidue()).containsExactly(1, 1);
    }

    @Test
    void closureSetMismatchHaltsBeforeAnyCanonicalMutationAndReportsBothSetDifferences() throws Exception {
        prepareLegacyImportSchema();
        LegacyFixture fixture = seedLegacyLedger(
            """
            {
              "total": 2,
              "created": 1,
              "updated": 0,
              "skipped": 0,
              "replayed": 1,
              "failed": 0,
              "blocked": 0
            }
            """,
            List.of("model.project.fact", "model.project.summary"),
            List.of("model.project.fact", "model.project.unexpected")
        );
        String before = legacyLedgerSnapshot();

        assertThatThrownBy(() -> applyChangelog(CANONICAL_CHANGELOG))
            .hasStackTraceContaining(CANONICAL_CHANGESET_ID);

        assertThat(appliedChangeSetCount(CANONICAL_CHANGESET_ID)).isZero();
        assertThat(legacyLedgerSnapshot()).isEqualTo(before);
        assertThat(missingResultIds(fixture.attemptId())).containsExactly("model.project.summary");
        assertThat(unexpectedResultIds(fixture.attemptId())).containsExactly("model.project.unexpected");
        assertThat(legacyStatusResidue()).containsExactly(1, 1);
    }

    @ParameterizedTest(name = "r2 {0} keeps r1 serving readable")
    @EnumSource(R2EvidenceState.class)
    void failedOrStaleR2KeepsTheExactR1ServingPointerAndReadableEvidence(R2EvidenceState r2State)
        throws Exception {
        prepareServingSchema();
        CatalogModelServingProjectionRepository repository = new CatalogModelServingProjectionRepository(
            jdbc,
            OBJECT_MAPPER
        );
        CatalogModelPhysicalPreviewReferenceAdapter reader = new CatalogModelPhysicalPreviewReferenceAdapter(
            repository
        );

        PublicationFixture r1 = PublicationFixture.r1();
        seedPublicationEvidence(r1, "BUILT", true);
        var r1Mutation = transactions.execute(status -> {
            repository.projectLatestPublished(r1.command());
            return repository.promoteServing(r1.command());
        });
        assertThat(r1Mutation).isNotNull();
        assertThat(r1Mutation.servingChanged()).isTrue();
        String r1ServingJson = servingJson();

        PublicationFixture r2 = PublicationFixture.r2();
        seedPublicationEvidence(r2, r2State.pipelineStatus(), r2State.verified());
        var r2Mutation = transactions.execute(status -> {
            repository.projectLatestPublished(r2.command());
            return repository.promoteServing(r2.command());
        });

        assertThat(r2Mutation).isNotNull();
        assertThat(r2Mutation.servingChanged()).isFalse();
        assertThat(r2Mutation.outcomeCode()).isEqualTo("SERVING_NOT_READY");
        assertThat(servingJson()).isEqualTo(r1ServingJson);

        var persisted = repository.findProjection(TENANT, MODEL_SPEC_ID).orElseThrow();
        assertThat(persisted.latestPublishedRef().modelRevision()).isEqualTo(2);
        assertThat(persisted.latestPublishedRef().candidateId()).isEqualTo(r2.candidateId());
        assertThat(persisted.servingRef().modelRevision()).isEqualTo(1);
        assertThat(persisted.servingRef().candidateId()).isEqualTo(r1.candidateId());
        assertThat(persisted.servingRef().relationEvidenceId()).isEqualTo(r1.evidenceId());

        var preview = reader.resolve(
            TENANT,
            MODEL_SPEC_ID,
            2,
            R2_MODEL_CHECKSUM,
            2,
            R2_IMPLEMENTATION_CHECKSUM
        );
        assertThat(preview.servingStatus()).isEqualTo(PhysicalPreviewEvidenceState.READY);
        assertThat(preview.candidateStatus()).isEqualTo(PhysicalPreviewEvidenceState.UNAVAILABLE);
        assertThat(preview.candidate()).isNull();
        assertThat(preview.serving()).satisfies(serving -> {
            assertThat(serving.scope()).isEqualTo(PhysicalPreviewScope.SERVING);
            assertThat(serving.modelRevision()).isEqualTo(1);
            assertThat(serving.modelChecksum()).isEqualTo(R1_MODEL_CHECKSUM);
            assertThat(serving.implementationRevision()).isEqualTo(1);
            assertThat(serving.implementationChecksum()).isEqualTo(R1_IMPLEMENTATION_CHECKSUM);
            assertThat(serving.candidateId()).isEqualTo(r1.candidateId());
            assertThat(serving.relationEvidenceId()).isEqualTo(r1.evidenceId());
            assertThat(serving.evidenceChecksum()).isEqualTo(r1.evidenceChecksum());
        });
    }

    private void prepareLegacyImportSchema() throws Exception {
        execute("create table modeling_model_spec_import_run (id uuid primary key)");
        execute("create table modeling_model_spec_revision (id uuid primary key)");
        execute("create table modeling_model_implementation_revision (id uuid primary key)");
        execute(
            """
            create table modeling_model_implementation (
                id uuid primary key,
                tenant_id varchar(128) not null,
                model_spec_id uuid not null,
                plan_id uuid not null,
                project_key varchar(128) not null,
                dbt_unique_id varchar(512) not null
            )
            """
        );
        execute(
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
        execute(
            """
            create unique index uk_modeling_dbt_artifact_dbt_slot
                on modeling_dbt_artifact (
                    model_spec_id, revision, implementation_revision, node_kind, artifact_type
                )
             where ownership = 'DBT_MANAGED'
            """
        );
        applyChangelog(APPLY_CHANGELOG);
        applyChangelog(LEASE_CHANGELOG);
    }

    private LegacyFixture seedLegacyLedger(
        String summaryJson,
        List<String> selectedClosure,
        List<String> resultIds
    ) throws Exception {
        UUID runId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        jdbc.update("insert into modeling_model_spec_import_run (id) values (?)", runId);
        String closureJson = OBJECT_MAPPER.writeValueAsString(selectedClosure);
        jdbc.update(
            """
            insert into modeling_model_spec_import_apply_attempt (
                id, run_id, tenant_id, plan_id, attempt_no, preview_hash,
                selected_unique_ids, selected_closure_json, idempotency_key,
                request_hash, status, summary_json, completed_at, created_by
            ) values (
                ?, ?, ?, ?, 1, 'legacy-preview', cast(? as jsonb), cast(? as jsonb),
                'legacy-idempotency', 'legacy-request', 'SUCCEEDED', cast(? as jsonb),
                current_timestamp, 'legacy-operator'
            )
            """,
            attemptId,
            runId,
            TENANT,
            UUID.randomUUID(),
            closureJson,
            closureJson,
            summaryJson
        );
        insertLegacyResult(attemptId, runId, 0, resultIds.get(0), "CREATED");
        insertLegacyResult(attemptId, runId, 1, resultIds.get(1), "REPLAYED");
        return new LegacyFixture(attemptId);
    }

    private LegacyFixture seedLegacyRunningDuplicateClosure() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        jdbc.update("insert into modeling_model_spec_import_run (id) values (?)", runId);
        String closureJson = "[\"model.project.fact\",\"model.project.fact\"]";
        jdbc.update(
            """
            insert into modeling_model_spec_import_apply_attempt (
                id, run_id, tenant_id, plan_id, attempt_no, preview_hash,
                selected_unique_ids, selected_closure_json, idempotency_key,
                request_hash, status, summary_json, created_by, owner_token, lease_expires_at
            ) values (
                ?, ?, ?, ?, 1, 'legacy-preview', '["model.project.fact"]'::jsonb,
                cast(? as jsonb), 'legacy-running-idempotency', 'legacy-running-request',
                'RUNNING', '{}'::jsonb, 'legacy-operator', 'legacy-owner', current_timestamp + interval '5 minutes'
            )
            """,
            attemptId,
            runId,
            TENANT,
            UUID.randomUUID(),
            closureJson
        );
        insertLegacyResult(attemptId, runId, 0, "model.project.fact", "CREATED");
        return new LegacyFixture(attemptId);
    }

    private void insertLegacyResult(UUID attemptId, UUID runId, int seq, String dbtUniqueId, String status) {
        jdbc.update(
            """
            insert into modeling_model_spec_import_apply_result (
                id, attempt_id, run_id, seq, dbt_unique_id,
                candidate_idempotency_key, candidate_request_hash,
                status, artifact_count, issues_json
            ) values (?, ?, ?, ?, ?, ?, ?, ?, 0, '[]'::jsonb)
            """,
            UUID.randomUUID(),
            attemptId,
            runId,
            seq,
            dbtUniqueId,
            "legacy-candidate-" + seq,
            "legacy-candidate-hash-" + seq,
            status
        );
    }

    private void prepareServingSchema() throws Exception {
        execute(
            """
            create table modeling_model_spec (
                tenant_id varchar(128) not null,
                id uuid not null,
                primary key (tenant_id, id)
            )
            """
        );
        execute(
            """
            create table modeling_model_release_candidate (
                tenant_id varchar(128) not null,
                id uuid not null,
                version int not null,
                status varchar(32) not null,
                primary key (tenant_id, id)
            )
            """
        );
        execute(
            """
            create table modeling_model_release_candidate_entry (
                tenant_id varchar(128) not null,
                candidate_id uuid not null,
                model_spec_id uuid not null,
                revision int not null,
                checksum varchar(64) not null,
                implementation_revision int not null,
                implementation_checksum varchar(64) not null,
                status varchar(32) not null
            )
            """
        );
        execute(
            """
            create table modeling_materialization_dispatch (
                id uuid primary key,
                tenant_id varchar(128) not null,
                candidate_id uuid not null,
                candidate_version int not null,
                attempt int not null,
                status varchar(32) not null,
                last_modified_at timestamp not null
            )
            """
        );
        execute(
            """
            create table modeling_pipeline_run (
                id uuid primary key,
                pipeline_run_group_id uuid not null,
                run_purpose varchar(32) not null,
                status varchar(32) not null
            )
            """
        );
        execute(
            """
            create table modeling_physical_relation_observation (
                id uuid primary key,
                tenant_id varchar(128) not null,
                model_spec_id uuid not null,
                model_revision int not null,
                model_checksum varchar(64) not null,
                implementation_revision int not null,
                implementation_checksum varchar(64) not null,
                release_candidate_id uuid not null,
                release_candidate_version int not null,
                pipeline_run_id uuid not null,
                pipeline_run_group_id uuid not null,
                observation_attempt int not null,
                adapter varchar(64) not null,
                credential_version_ref varchar(128) not null,
                database_name varchar(128) not null,
                schema_name varchar(128) not null,
                identifier varchar(128) not null,
                actual_type varchar(32) not null,
                relation_exists boolean not null,
                verified boolean not null,
                actual_columns jsonb not null,
                metadata_checksum varchar(64) not null,
                observed_at timestamp not null
            )
            """
        );
        execute(
            """
            create table catalog_dataset (
                id uuid primary key,
                source_id uuid not null,
                enabled boolean not null,
                hive_database varchar(128) not null,
                hive_table varchar(128) not null
            )
            """
        );
        jdbc.update("insert into modeling_model_spec (tenant_id, id) values (?, ?)", TENANT, MODEL_SPEC_ID);
        jdbc.update(
            """
            insert into catalog_dataset (id, source_id, enabled, hive_database, hive_table)
            values (?, ?, true, 'finance', 'dwd_budget')
            """,
            PHYSICAL_ASSET_ID,
            SOURCE_ID
        );
        applyChangelog(SERVING_CHANGELOG);
        applyChangelog(SERVING_CONSTRAINT_CHANGELOG);
    }

    private void seedPublicationEvidence(PublicationFixture fixture, String pipelineStatus, boolean verified) {
        jdbc.update(
            """
            insert into modeling_model_release_candidate (tenant_id, id, version, status)
            values (?, ?, ?, 'PUBLISHING')
            """,
            TENANT,
            fixture.candidateId(),
            fixture.candidateVersion()
        );
        jdbc.update(
            """
            insert into modeling_model_release_candidate_entry (
                tenant_id, candidate_id, model_spec_id, revision, checksum,
                implementation_revision, implementation_checksum, status
            ) values (?, ?, ?, ?, ?, ?, ?, 'PUBLISHING')
            """,
            TENANT,
            fixture.candidateId(),
            MODEL_SPEC_ID,
            fixture.modelRevision(),
            fixture.modelChecksum(),
            fixture.implementationRevision(),
            fixture.implementationChecksum()
        );
        jdbc.update(
            """
            insert into modeling_materialization_dispatch (
                id, tenant_id, candidate_id, candidate_version, attempt, status, last_modified_at
            ) values (?, ?, ?, ?, 1, 'COMPLETED', ?)
            """,
            fixture.dispatchId(),
            TENANT,
            fixture.candidateId(),
            fixture.candidateVersion(),
            Timestamp.from(fixture.publishedAt())
        );
        jdbc.update(
            """
            insert into modeling_pipeline_run (id, pipeline_run_group_id, run_purpose, status)
            values (?, ?, 'RELEASE_BUILD', ?)
            """,
            fixture.pipelineRunId(),
            fixture.dispatchId(),
            pipelineStatus
        );
        jdbc.update(
            """
            insert into modeling_physical_relation_observation (
                id, tenant_id, model_spec_id, model_revision, model_checksum,
                implementation_revision, implementation_checksum,
                release_candidate_id, release_candidate_version,
                pipeline_run_id, pipeline_run_group_id, observation_attempt,
                adapter, credential_version_ref, database_name, schema_name, identifier,
                actual_type, relation_exists, verified, actual_columns,
                metadata_checksum, observed_at
            ) values (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1,
                'postgres', 'credential:v1', 'warehouse', 'finance', 'dwd_budget',
                'TABLE', true, ?, '[]'::jsonb, ?, ?
            )
            """,
            fixture.evidenceId(),
            TENANT,
            MODEL_SPEC_ID,
            fixture.modelRevision(),
            fixture.modelChecksum(),
            fixture.implementationRevision(),
            fixture.implementationChecksum(),
            fixture.candidateId(),
            fixture.candidateVersion(),
            fixture.pipelineRunId(),
            fixture.dispatchId(),
            verified,
            fixture.evidenceChecksum(),
            Timestamp.from(fixture.publishedAt())
        );
    }

    private String completeLegacySummary() {
        return """
            {
              "total": 2,
              "created": 1,
              "updated": 0,
              "skipped": 0,
              "replayed": 1,
              "failed": 0,
              "blocked": 0
            }
            """;
    }

    private String completeCheckpointJson() {
        return "{" +
            "\"tenantId\":\"" + TENANT + "\"," +
            "\"projectKey\":\"project\"," +
            "\"dbtUniqueId\":\"model.project.fact\"," +
            "\"acceptedExternalChecksum\":\"" + "3".repeat(64) + "\"," +
            "\"acceptedImplementationRevision\":1," +
            "\"acceptedImplementationChecksum\":\"" + "4".repeat(64) + "\"," +
            "\"mappedModelSpecRevision\":1," +
            "\"mappedModelSpecEtag\":\"" + "6".repeat(64) + "\"}";
    }

    private String incompleteCheckpointJson() {
        return "{" +
            "\"tenantId\":\"" + TENANT + "\"," +
            "\"projectKey\":\"project\"," +
            "\"dbtUniqueId\":\"model.project.fact\"," +
            "\"acceptedExternalChecksum\":\"" + "3".repeat(64) + "\"," +
            "\"acceptedImplementationRevision\":1," +
            "\"acceptedImplementationChecksum\":\"" + "4".repeat(64) + "\"," +
            "\"mappedModelSpecRevision\":1}";
    }

    private String completePrePinsJson() {
        return "{" +
            "\"modelRevision\":1," +
            "\"modelChecksum\":\"" + "7".repeat(64) + "\"," +
            "\"implementationRevision\":1," +
            "\"implementationChecksum\":\"" + "8".repeat(64) + "\"}";
    }

    private List<UUID> summaryMismatchAttemptIds() {
        return jdbc.queryForList(
            """
            with facts as (
                select attempt.id,
                       count(result.id) terminal,
                       count(*) filter (where result.status = 'CREATED') created,
                       count(*) filter (where result.status = 'UPDATED') updated,
                       count(*) filter (where result.status = 'SKIPPED') skipped,
                       count(*) filter (where result.status = 'REPLAYED') replayed,
                       count(*) filter (where result.status = 'FAILED') failed,
                       count(*) filter (where result.status = 'BLOCKED') blocked,
                       attempt.summary_json
                  from modeling_model_spec_import_apply_attempt attempt
                  left join modeling_model_spec_import_apply_result result
                    on result.attempt_id = attempt.id
                 group by attempt.id
            )
            select id
              from facts
             where coalesce((summary_json ->> 'total')::int, -1) <> terminal
                or coalesce((summary_json ->> 'created')::int, -1) <> created
                or coalesce((summary_json ->> 'updated')::int, -1) <> updated
                or coalesce((summary_json ->> 'skipped')::int, -1) <> skipped
                or coalesce((summary_json ->> 'replayed')::int, -1) <> replayed
                or coalesce((summary_json ->> 'failed')::int, -1) <> failed
                or coalesce((summary_json ->> 'blocked')::int, -1) <> blocked
             order by id
            """,
            UUID.class
        );
    }

    private List<String> missingResultIds(UUID attemptId) {
        return jdbc.queryForList(
            """
            select expected.dbt_unique_id
              from modeling_model_spec_import_apply_attempt attempt,
                   jsonb_array_elements_text(attempt.selected_closure_json) expected(dbt_unique_id)
             where attempt.id = ?
            except
            select result.dbt_unique_id
              from modeling_model_spec_import_apply_result result
             where result.attempt_id = ?
             order by 1
            """,
            String.class,
            attemptId,
            attemptId
        );
    }

    private List<String> unexpectedResultIds(UUID attemptId) {
        return jdbc.queryForList(
            """
            select result.dbt_unique_id
              from modeling_model_spec_import_apply_result result
             where result.attempt_id = ?
            except
            select expected.dbt_unique_id
              from modeling_model_spec_import_apply_attempt attempt,
                   jsonb_array_elements_text(attempt.selected_closure_json) expected(dbt_unique_id)
             where attempt.id = ?
             order by 1
            """,
            String.class,
            attemptId,
            attemptId
        );
    }

    private List<String> duplicateClosureIds(UUID attemptId) {
        return jdbc.queryForList(
            """
            select member
              from modeling_model_spec_import_apply_attempt attempt,
                   jsonb_array_elements_text(attempt.selected_closure_json) closure(member)
             where attempt.id = ?
             group by member
            having count(*) > 1
             order by member
            """,
            String.class,
            attemptId
        );
    }

    private List<Integer> legacyStatusResidue() {
        return List.of(
            jdbc.queryForObject(
                "select count(*) from modeling_model_spec_import_apply_attempt where status = 'SUCCEEDED'",
                Integer.class
            ),
            jdbc.queryForObject(
                "select count(*) from modeling_model_spec_import_apply_result where status = 'REPLAYED'",
                Integer.class
            )
        );
    }

    private String legacyLedgerSnapshot() {
        return jdbc.queryForObject(
            """
            select jsonb_build_object(
                'attempts', coalesce(
                    (select jsonb_agg(to_jsonb(attempt) order by attempt.id)
                       from modeling_model_spec_import_apply_attempt attempt),
                    '[]'::jsonb
                ),
                'items', coalesce(
                    (select jsonb_agg(to_jsonb(result) order by result.id)
                       from modeling_model_spec_import_apply_result result),
                    '[]'::jsonb
                )
            )::text
            """,
            String.class
        );
    }

    private String servingJson() {
        return jdbc.queryForObject(
            """
            select serving_ref::text
              from modeling_catalog_model_serving_projection
             where tenant_id = ? and model_spec_id = ?
            """,
            String.class,
            TENANT,
            MODEL_SPEC_ID
        );
    }

    private List<String> validatedConstraints(String tableName) {
        return jdbc.queryForList(
            """
            select conname
              from pg_constraint
             where conrelid = cast(? as regclass)
               and convalidated
             order by conname
            """,
            String.class,
            tableName
        );
    }

    private int appliedChangeSetCount(String changeSetId) {
        return jdbc.queryForObject(
            "select count(*) from databasechangelog where id = ?",
            Integer.class,
            changeSetId
        );
    }

    private int helperFunctionCount() {
        return jdbc.queryForObject(
            """
            select count(*)
              from pg_proc function
              join pg_namespace namespace on namespace.oid = function.pronamespace
             where namespace.nspname = current_schema()
               and function.proname = 'modeling_jsonb_text_array_unique_v20260802'
            """,
            Integer.class
        );
    }

    private List<PreflightDiagnostic> runHardeningPreflight() throws Exception {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(HARDENING_PREFLIGHT_SQL);
            if (Files.isRegularFile(candidate)) {
                String sql = Files.readString(candidate, StandardCharsets.UTF_8);
                return jdbc.query(
                    sql,
                    (row, rowNumber) ->
                        new PreflightDiagnostic(
                            row.getString("record_type"),
                            row.getString("attempt_ref"),
                            row.getString("result_ref"),
                            row.getString("dbt_unique_ref"),
                            row.getString("reason_codes")
                        )
                );
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Sprint-83 G3 hardening preflight SQL was not found");
    }

    private void assertSafeResultDiagnostic(PreflightDiagnostic diagnostic) {
        assertThat(diagnostic.recordType()).isEqualTo("RESULT");
        assertThat(diagnostic.attemptRef()).matches("attempt-[0-9a-f]{20}");
        assertThat(diagnostic.resultRef()).matches("result-[0-9a-f]{20}");
        assertThat(diagnostic.dbtUniqueRef()).matches("dbt-ref-[0-9a-f]{20}");
        assertThat(diagnostic.toString())
            .doesNotContain(TENANT, "model.project.fact", "model.project.summary", "3".repeat(64));
    }

    private void applyChangelog(String changelog) throws Exception {
        try (
            Connection connection = connectionInSchema();
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()
        ) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try {
                database.setDefaultSchemaName(schema);
                database.setLiquibaseSchemaName(schema);
                try (Liquibase liquibase = new Liquibase(changelog, resources, database)) {
                    liquibase.setChangeLogParameter("uuidType", "uuid");
                    liquibase.setChangeLogParameter("datetimeType", "timestamp");
                    liquibase.update(new Contexts(), new LabelExpression());
                }
            } finally {
                if (!database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private JsonNode json(String value) throws Exception {
        return OBJECT_MAPPER.readTree(value);
    }

    private void execute(String sql) {
        jdbc.execute(sql);
    }

    private Connection connectionInSchema() throws Exception {
        requireOwnedSchema();
        Connection connection = openConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
        } catch (Exception exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    private Connection openConnection() throws Exception {
        return POSTGRES.createConnection("");
    }

    private String schemaJdbcUrl() {
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        return POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema;
    }

    private void requireOwnedSchema() {
        if (schema == null || !OWNED_SCHEMA.matcher(schema).matches()) {
            throw new IllegalStateException("Refusing to operate on an unowned Sprint-83 G3 schema");
        }
    }

    private record LegacyFixture(UUID attemptId) {}

    private record PreflightDiagnostic(
        String recordType,
        String attemptRef,
        String resultRef,
        String dbtUniqueRef,
        String reasonCodesJson
    ) {}

    private record CanonicalItem(
        String dbtUniqueId,
        String status,
        String appliedAction,
        String mergeCheckpointJson,
        String dependencyJson
    ) {}

    private enum R2EvidenceState {
        FAILED("FAILED", true),
        STALE("BUILT", false);

        private final String pipelineStatus;
        private final boolean verified;

        R2EvidenceState(String pipelineStatus, boolean verified) {
            this.pipelineStatus = pipelineStatus;
            this.verified = verified;
        }

        String pipelineStatus() {
            return pipelineStatus;
        }

        boolean verified() {
            return verified;
        }
    }

    private record PublicationFixture(
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        UUID candidateId,
        int candidateVersion,
        UUID dispatchId,
        UUID pipelineRunId,
        UUID evidenceId,
        String evidenceChecksum,
        Instant publishedAt
    ) {
        private static PublicationFixture r1() {
            return new PublicationFixture(
                1,
                R1_MODEL_CHECKSUM,
                1,
                R1_IMPLEMENTATION_CHECKSUM,
                UUID.fromString("20000000-0000-0000-0000-000000000081"),
                11,
                UUID.fromString("50000000-0000-0000-0000-000000000081"),
                UUID.fromString("51000000-0000-0000-0000-000000000081"),
                UUID.fromString("60000000-0000-0000-0000-000000000081"),
                "c".repeat(64),
                NOW
            );
        }

        private static PublicationFixture r2() {
            return new PublicationFixture(
                2,
                R2_MODEL_CHECKSUM,
                2,
                R2_IMPLEMENTATION_CHECKSUM,
                UUID.fromString("20000000-0000-0000-0000-000000000082"),
                12,
                UUID.fromString("50000000-0000-0000-0000-000000000082"),
                UUID.fromString("51000000-0000-0000-0000-000000000082"),
                UUID.fromString("60000000-0000-0000-0000-000000000082"),
                "f".repeat(64),
                NOW.plusSeconds(60)
            );
        }

        private SuccessfulPublicationCommand command() {
            return new SuccessfulPublicationCommand(
                TENANT,
                MODEL_SPEC_ID,
                modelRevision,
                modelChecksum,
                implementationRevision,
                implementationChecksum,
                candidateId,
                candidateVersion,
                CatalogAssetType.SEMANTIC_MODEL,
                "semantic-model:" + MODEL_SPEC_ID,
                SOURCE_ID,
                "postgres",
                PHYSICAL_ASSET_ID,
                publishedAt
            );
        }
    }
}
