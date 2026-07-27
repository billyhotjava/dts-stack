package com.yuzhi.dts.platform.repository.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseRecord;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseStatus;
import com.yuzhi.dts.platform.repository.modeling.PhysicalRelationObservationRepository.ObservationWrite;
import com.yuzhi.dts.platform.service.etl.DbtTargetConnectionFactory;
import com.yuzhi.dts.platform.service.etl.DbtTargetConnectionFactory.RuntimeTarget;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationStartService;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.RelationLocator;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.TargetContext;
import com.yuzhi.dts.platform.service.modeling.PostgresPhysicalRelationInspector;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ModelMaterializationStartServiceIT {

    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    @Autowired
    private ModelReleaseCandidateRepository candidates;

    @Autowired
    private ModelMaterializationStartService starts;

    @Autowired
    private DbtRuntimeProfileLeaseRepository profileLeases;

    @Autowired
    private ModelMaterializationDispatchRepository dispatches;

    @Autowired
    private ModelMaterializationRunRepository materializationRuns;

    @Autowired
    private PhysicalRelationObservationRepository observations;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void commitsBuildingSnapshotClaimAndOneQueuedRunAsOneUnit() {
        Scope scope = scope("atomic-success");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        jdbcTemplate.execute(
            "create table public." +
            scope.targetIdentifier() +
            " (project_id uuid not null, amount numeric(18,2))"
        );
        try {
            transaction.executeWithoutResult(status -> {
            seed(scope, true);

            var result = starts.start(
                scope.tenant(),
                "builder-a",
                scope.candidateId(),
                1,
                "start-build-success",
                "start release build"
            );

            assertThat(result.candidate().status()).isEqualTo(DeliveryStatus.BUILDING);
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*) from modeling_pipeline_run
                     where tenant_id = ? and release_candidate_id = ?
                       and release_candidate_version = 2 and run_purpose = 'RELEASE_BUILD'
                       and status = 'QUEUED' and implementation_revision = 1
                       and implementation_checksum = ?
                       and artifact_bundle_checksum ~ '^[0-9a-f]{64}$'
                       and airflow_run_id = ?
                    """,
                    Integer.class,
                    scope.tenant(),
                    scope.candidateId(),
                    scope.implementationChecksum(),
                    "dts_rc_" +
                    scope.candidateId().toString().replace("-", "") +
                    "_v2_a1"
                )
            )
                .isEqualTo(1);
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*)
                      from modeling_materialization_dispatch d
                      join modeling_pipeline_run pr
                        on pr.pipeline_run_group_id = d.id
                       and pr.tenant_id = d.tenant_id
                     where d.tenant_id = ?
                       and d.candidate_id = ?
                       and d.candidate_version = 2
                       and d.attempt = 1
                       and d.status = 'PENDING'
                       and d.airflow_dag_id = pr.airflow_dag_id
                       and d.airflow_run_id = pr.airflow_run_id
                       and d.artifact_bundle_checksum =
                           pr.artifact_bundle_checksum
                    """,
                    Integer.class,
                    scope.tenant(),
                    scope.candidateId()
                )
            )
                .isEqualTo(1);
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*) from modeling_model_release_candidate_entry
                     where tenant_id = ? and candidate_id = ?
                       and implementation_id = ? and implementation_revision = 1
                       and implementation_checksum = ?
                       and dbt_unique_id = ? and target_identifier = ?
                       and artifact_bundle_checksum ~ '^[0-9a-f]{64}$'
                       and dependency_snapshot_checksum ~ '^[0-9a-f]{64}$'
                       and active_claim_key ~ '^[0-9a-f]{64}$'
                    """,
                    Integer.class,
                    scope.tenant(),
                    scope.candidateId(),
                    scope.implementationId(),
                    scope.implementationChecksum(),
                    scope.dbtUniqueId(),
                    scope.targetIdentifier()
                )
            )
                .isEqualTo(1);
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*) from modeling_model_release_candidate
                     where tenant_id = ? and id = ? and status = 'BUILDING' and version = 2
                       and execution_target_key = 'postgres-primary'
                       and adapter = 'postgres' and profile_key = 'dts'
                       and target_name = 'dev'
                    """,
                    Integer.class,
                    scope.tenant(),
                    scope.candidateId()
                )
            )
                .isEqualTo(1);
            UUID pipelineRunId = jdbcTemplate.queryForObject(
                """
                select id
                  from modeling_pipeline_run
                 where tenant_id = ? and release_candidate_id = ?
                   and run_purpose = 'RELEASE_BUILD'
                """,
                UUID.class,
                scope.tenant(),
                scope.candidateId()
            );
            Instant dispatchAt = Instant.now().plusSeconds(60);
            var claimed = dispatches
                .claimNext(dispatchAt, Duration.ofMinutes(2))
                .orElseThrow();
            assertThat(claimed.candidateId())
                .isEqualTo(scope.candidateId());
            assertThat(claimed.status()).isEqualTo("CLAIMED");
            String scopedBundleChecksum = "9".repeat(64);
            String runtimeTokenDigest =
                "sha256:" + "8".repeat(64);
            dispatches.markPrepared(
                claimed.id(),
                scopedBundleChecksum,
                runtimeTokenDigest,
                dispatchAt.plusSeconds(300),
                dispatchAt
            );
            var runtime = dispatches
                .lockRuntimeSpec(runtimeTokenDigest)
                .orElseThrow();
            assertThat(runtime.dispatchId())
                .isEqualTo(claimed.id());
            assertThat(runtime.selector())
                .isEqualTo(scope.selector());
            assertThat(runtime.pipelineRunId())
                .isEqualTo(pipelineRunId);
            UUID leaseId = UUID.randomUUID();
            profileLeases.issue(
                new LeaseRecord(
                    leaseId,
                    scope.tenant(),
                    pipelineRunId,
                    "dts_rc_" +
                    scope
                        .candidateId()
                        .toString()
                        .replace("-", "") +
                    "_v2_a1",
                    "DEV",
                    "postgres-primary",
                    "dev",
                    "sha256:" + "a".repeat(64),
                    LeaseStatus.ISSUED,
                    NOW,
                    NOW.plusSeconds(300),
                    null,
                    null
                )
            );
            assertThat(
                dispatches.attachRuntimeLease(
                    claimed.id(),
                    leaseId,
                    dispatchAt
                )
            )
                .isTrue();
            dispatches.markSubmitted(
                claimed.id(),
                false,
                dispatchAt
            );
            UUID dbtInvocationId = UUID.randomUUID();
            materializationRuns.markDbtSucceeded(
                claimed.id(),
                dbtInvocationId,
                1,
                dispatchAt.plusSeconds(1)
            );
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select status
                      from modeling_pipeline_run
                     where id = ?
                    """,
                    String.class,
                    pipelineRunId
                )
            )
                .isEqualTo("DBT_SUCCEEDED");
            String database = jdbcTemplate.queryForObject(
                "select current_database()",
                String.class
            );
            String credentialVersionRef =
                "sha256:" + "a".repeat(64);
            RuntimeTarget runtimeTarget = new RuntimeTarget(
                UUID.randomUUID(),
                database,
                "public",
                "PostgreSQL",
                "jdbc:postgresql://test-only/warehouse",
                "test-only",
                "test-only",
                credentialVersionRef
            );
            DbtTargetConnectionFactory targetFactory = mock(
                DbtTargetConnectionFactory.class
            );
            when(targetFactory.resolveRuntimeTarget())
                .thenReturn(runtimeTarget);
            try {
                when(targetFactory.open(runtimeTarget))
                    .thenAnswer(invocation ->
                        dataSource.getConnection()
                    );
            } catch (java.sql.SQLException impossible) {
                throw new IllegalStateException(impossible);
            }
            var physical = inspector(
                targetFactory,
                dispatchAt.plusSeconds(2)
            ).observe(
                new TargetContext(
                    "postgres-primary",
                    "postgres",
                    database,
                    "public",
                    credentialVersionRef
                ),
                new RelationLocator(
                    database,
                    "public",
                    scope.targetIdentifier(),
                    ExpectedRelationType.TABLE,
                    List.of("project_id", "amount")
                )
            );
            assertThat(physical.exists()).isTrue();
            assertThat(physical.actualType())
                .isEqualTo(ExpectedRelationType.TABLE);
            assertThat(physical.columns())
                .extracting(column -> column.name())
                .containsExactly("project_id", "amount");
            ObservationWrite observation = new ObservationWrite(
                scope.tenant(),
                scope.candidateId(),
                2,
                claimed.id(),
                pipelineRunId,
                scope.modelId(),
                1,
                scope.modelChecksum(),
                1,
                scope.implementationChecksum(),
                dbtInvocationId,
                scopedBundleChecksum,
                "postgres",
                credentialVersionRef,
                database,
                "public",
                scope.targetIdentifier(),
                ExpectedRelationType.TABLE,
                physical.actualType(),
                physical.exists(),
                true,
                physical.columns(),
                sha256("project_id:amount"),
                physical.columnsChecksum(),
                sha256(
                    dbtInvocationId +
                    ":" +
                    physical.columnsChecksum()
                ),
                null,
                physical.observedAt(),
                physical.observedAt()
            );
            assertThat(observations.appendAll(List.of(observation)))
                .singleElement()
                .extracting(
                    persisted ->
                        persisted.observationAttempt()
                )
                .isEqualTo(1);
            assertThat(observations.appendAll(List.of(observation)))
                .singleElement()
                .extracting(
                    persisted ->
                        persisted.observationAttempt()
                )
                .isEqualTo(2);
            assertThat(observations.findCurrent(claimed.id()))
                .singleElement()
                .satisfies(current -> {
                    assertThat(current.verified()).isTrue();
                    assertThat(current.observationAttempt())
                        .isEqualTo(2);
                    assertThat(current.identifier())
                        .isEqualTo(scope.targetIdentifier());
                });
            assertObservationIsAppendOnly(claimed.id());
            materializationRuns.markRelationsVerified(
                claimed.id(),
                1,
                dispatchAt.plusSeconds(2)
            );
            assertThat(
                materializationRuns.finalizeSucceeded(
                    claimed.id(),
                    dispatchAt.plusSeconds(3)
                )
            )
                .isEqualTo(1);
            assertThat(
                materializationRuns
                    .findRunGroup(claimed.id())
                    .orElseThrow()
                    .dispatchStatus()
            )
                .isEqualTo("COMPLETED");
            assertThat(profileLeases.find(leaseId))
                .get()
                .extracting(LeaseRecord::status)
                .isEqualTo(LeaseStatus.ISSUED);
            assertThat(
                profileLeases.consume(
                    leaseId,
                    NOW.plusSeconds(1)
                )
            )
                .isTrue();
            assertThat(
                profileLeases.consume(
                    leaseId,
                    NOW.plusSeconds(2)
                )
            )
                .isFalse();
            profileLeases.release(
                leaseId,
                NOW.plusSeconds(3)
            );
            assertThat(profileLeases.find(leaseId))
                .get()
                .extracting(LeaseRecord::status)
                .isEqualTo(LeaseStatus.RELEASED);
            status.setRollbackOnly();
            });
        } finally {
            jdbcTemplate.execute(
                "drop table if exists public." +
                scope.targetIdentifier()
            );
        }
    }

    @Test
    void missingArtifactRollsCandidateCommandAndRunsBackToDraft() {
        Scope scope = scope("atomic-rollback");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> seed(scope, false));

        try {
            assertThatThrownBy(() ->
                starts.start(
                    scope.tenant(),
                    "builder-a",
                    scope.candidateId(),
                    1,
                    "start-build-failure",
                    "start release build"
                )
            )
                .isInstanceOf(ModelReleaseCandidateException.class)
                .satisfies(error ->
                    assertThat(((ModelReleaseCandidateException) error).code())
                        .isEqualTo("MATERIALIZATION_ARTIFACT_MISSING")
                );

            assertThat(candidates.find(scope.tenant(), scope.candidateId()))
                .get()
                .extracting(CandidateView::status, CandidateView::version)
                .containsExactly(DeliveryStatus.DRAFT, 1);
            assertThat(
                jdbcTemplate.queryForObject(
                    "select count(*) from modeling_pipeline_run where tenant_id = ? and release_candidate_id = ?",
                    Integer.class,
                    scope.tenant(),
                    scope.candidateId()
                )
            )
                .isZero();
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*)
                      from modeling_materialization_dispatch
                     where tenant_id = ? and candidate_id = ?
                    """,
                    Integer.class,
                    scope.tenant(),
                    scope.candidateId()
                )
            )
                .isZero();
            assertThat(
                jdbcTemplate.queryForObject(
                    "select count(*) from modeling_model_release_candidate_command where tenant_id = ? and candidate_id = ?",
                    Integer.class,
                    scope.tenant(),
                    scope.candidateId()
                )
            )
                .isZero();
        } finally {
            transaction.executeWithoutResult(status -> cleanup(scope));
        }
    }

    private void seed(Scope scope, boolean withArtifact) {
        jdbcTemplate.update(
            """
            insert into modeling_warehouse_plan (
                id, tenant_id, owner, code, name, owner_id, onboarding_mode, lifecycle_status,
                status, version, created_date, last_modified_date
            ) values (?, ?, 'builder-a', ?, 'Sprint 76 plan', 'builder-a',
                      'BUSINESS_FIRST', 'DRAFT', 'DRAFT', 1, current_timestamp, current_timestamp)
            """,
            scope.planId(),
            scope.tenant(),
            "s76_" + scope.planId().toString().replace("-", "")
        );
        jdbcTemplate.update(
            """
            insert into catalog_domain (id, name, code, lifecycle_status, access_policy)
            values (?, 'Sprint 76 domain', ?, 'ACTIVE', 'PUBLIC')
            """,
            scope.domainId(),
            "S76_" + scope.domainId().toString().replace("-", "")
        );
        jdbcTemplate.update(
            """
            insert into modeling_model_spec (
                id, tenant_id, object_id, plan_id, process_id, layer, model_type,
                implementation_mode, name, status, revision, version, created_date,
                last_modified_date, contract_version, domain_id, current_checksum,
                idempotency_key, idempotency_request_hash, idempotency_response_snapshot
            ) values (
                ?, ?, null, ?, null, 'DWD', 'FACT', 'DESIGNER_GENERATED',
                'Sprint 76 atomic model', 'DRAFT', 1, 1, current_timestamp,
                current_timestamp, 2, ?, ?, ?, ?, cast('{}' as jsonb)
            )
            """,
            scope.modelId(),
            scope.tenant(),
            scope.planId(),
            scope.domainId(),
            scope.modelChecksum(),
            "model-" + scope.modelId(),
            "f".repeat(64)
        );
        jdbcTemplate.update(
            """
            insert into modeling_model_spec_revision (
                id, model_spec_id, revision, spec_json, status, content_checksum, created_date,
                last_modified_date, tenant_id, contract_version, snapshot_json, created_by
            ) values (?, ?, 1, null, 'DRAFT', ?, current_timestamp, current_timestamp, ?, 2,
                      cast('{}' as jsonb), 'builder-a')
            """,
            UUID.randomUUID(),
            scope.modelId(),
            scope.modelChecksum(),
            scope.tenant()
        );
        Integer inserted = jdbcTemplate.queryForObject(
            """
            with implementation_head as (
                insert into modeling_model_implementation (
                    id, tenant_id, model_spec_id, plan_id, model_revision, model_checksum,
                    ownership, project_key, dbt_unique_id, status, idempotency_key, created_by,
                    created_date, last_modified_date, implementation_revision,
                    current_implementation_checksum, input_mode, inputs_json,
                    field_mappings_json, settings_json, materialization
                ) values (
                    ?, ?, ?, ?, 1, ?, 'DESIGNER_GENERATED', 'dts', ?, 'ACTIVE', ?,
                    'builder-a', current_timestamp, current_timestamp, 1, ?, 'GENERATED',
                    cast('[{"generatorType":"RELEASE_IT","config":{}}]' as jsonb),
                    cast('[]' as jsonb),
                    cast(? as jsonb),
                    'table'
                )
                returning tenant_id, id, implementation_revision,
                          current_implementation_checksum, input_mode, inputs_json,
                          field_mappings_json, settings_json, ownership, materialization
            ), implementation_revision as (
                insert into modeling_model_implementation_revision (
                    id, tenant_id, implementation_id, revision, content_checksum, input_mode,
                    inputs_json, field_mappings_json, settings_json, ownership,
                    materialization, created_by, created_date
                )
                select ?, tenant_id, id, implementation_revision,
                       current_implementation_checksum, input_mode, inputs_json,
                       field_mappings_json, settings_json, ownership, materialization,
                       'builder-a', current_timestamp
                  from implementation_head
                returning 1
            )
            select count(*)::int from implementation_revision
            """,
            Integer.class,
            scope.implementationId(),
            scope.tenant(),
            scope.modelId(),
            scope.planId(),
            scope.modelChecksum(),
            scope.dbtUniqueId(),
            "implementation-" + scope.implementationId(),
            scope.implementationChecksum(),
            "{\"targetPhysicalName\":\"" +
            scope.targetIdentifier() +
            "\",\"loadStrategy\":\"FULL\",\"partitionFields\":[]}",
            UUID.randomUUID()
        );
        assertThat(inserted).isEqualTo(1);
        if (withArtifact) {
            String sql =
                "{{ config(materialized='table',alias='" +
                scope.targetIdentifier() +
                "') }}\nselect 1 as project_id\n";
            jdbcTemplate.update(
                """
                insert into modeling_dbt_artifact (
                    id, model_spec_id, plan_id, project_key, dbt_unique_id, artifact_key,
                    artifact_type, path, content_checksum, content, status, revision,
                    model_checksum, ownership, idempotency_key, implementation_revision,
                    node_kind, materialization, physical_asset_ref, created_date,
                    last_modified_date
                ) values (
                    ?, ?, ?, 'dts', ?, ?, 'SQL', ?, ?, ?, 'COMPILED', 1, ?,
                    'DESIGNER_GENERATED', ?, 1, 'MODEL', 'table', null,
                    current_timestamp, current_timestamp
                )
                """,
                UUID.randomUUID(),
                scope.modelId(),
                scope.planId(),
                scope.dbtUniqueId(),
                "SQL:models/dwd/" + scope.selector() + ".sql",
                "models/dwd/" + scope.selector() + ".sql",
                sha256(sql),
                sql,
                scope.modelChecksum(),
                "artifact-" + scope.modelId()
            );
        }
        CandidateView candidate = new CandidateView(
            scope.candidateId(),
            scope.tenant(),
            scope.planId(),
            "PROD",
            DeliveryStatus.DRAFT,
            1,
            "candidate-" + scope.candidateId(),
            "e".repeat(64),
            new DeliveryAuditView(
                "builder-a",
                NOW,
                null,
                null,
                null,
                null,
                null,
                null
            ),
            "builder-a",
            NOW,
            List.of(
                new EntryView(
                    scope.entryId(),
                    scope.tenant(),
                    scope.candidateId(),
                    scope.planId(),
                    scope.modelId(),
                    1,
                    scope.modelChecksum(),
                    null,
                    ImplementationMode.DESIGNER_GENERATED,
                    DeliveryStatus.DRAFT,
                    0,
                    "primary"
                )
            )
        );
        assertThat(candidates.insert(candidate)).isEqualTo(1);
    }

    private void cleanup(Scope scope) {
        jdbcTemplate.update(
            "delete from modeling_materialization_dispatch where tenant_id = ? and candidate_id = ?",
            scope.tenant(),
            scope.candidateId()
        );
        jdbcTemplate.update(
            "delete from modeling_pipeline_run where tenant_id = ? and release_candidate_id = ?",
            scope.tenant(),
            scope.candidateId()
        );
        jdbcTemplate.update(
            "delete from modeling_model_release_candidate_entry where tenant_id = ? and candidate_id = ?",
            scope.tenant(),
            scope.candidateId()
        );
        jdbcTemplate.update(
            "delete from modeling_model_release_candidate where tenant_id = ? and id = ?",
            scope.tenant(),
            scope.candidateId()
        );
        jdbcTemplate.update(
            "delete from modeling_dbt_artifact where model_spec_id = ?",
            scope.modelId()
        );
        jdbcTemplate.update(
            "delete from modeling_model_implementation_revision where tenant_id = ? and implementation_id = ?",
            scope.tenant(),
            scope.implementationId()
        );
        jdbcTemplate.update(
            "delete from modeling_model_implementation where tenant_id = ? and id = ?",
            scope.tenant(),
            scope.implementationId()
        );
        jdbcTemplate.update(
            "delete from modeling_model_spec_revision where tenant_id = ? and model_spec_id = ?",
            scope.tenant(),
            scope.modelId()
        );
        jdbcTemplate.update(
            "delete from modeling_model_spec where tenant_id = ? and id = ?",
            scope.tenant(),
            scope.modelId()
        );
        jdbcTemplate.update("delete from catalog_domain where id = ?", scope.domainId());
        jdbcTemplate.update(
            "delete from modeling_warehouse_plan where tenant_id = ? and id = ?",
            scope.tenant(),
            scope.planId()
        );
    }

    private static Scope scope(String suffix) {
        UUID modelId = UUID.randomUUID();
        return new Scope(
            "s76-" + suffix + "-" + UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            modelId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            sha256("model:" + modelId),
            sha256("implementation:" + modelId),
            "model.dts.model_" + modelId.toString().replace("-", "_"),
            "dwd_s76_" +
            modelId.toString().replace("-", "").substring(0, 16)
        );
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static PostgresPhysicalRelationInspector inspector(
        DbtTargetConnectionFactory targets,
        Instant now
    ) {
        try {
            var constructor =
                PostgresPhysicalRelationInspector.class
                    .getDeclaredConstructor(
                        DbtTargetConnectionFactory.class,
                        Clock.class
                    );
            constructor.setAccessible(true);
            return constructor.newInstance(
                targets,
                Clock.fixed(now, ZoneOffset.UTC)
            );
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void assertObservationIsAppendOnly(
        UUID pipelineRunGroupId
    ) {
        Connection connection = DataSourceUtils.getConnection(
            dataSource
        );
        Savepoint savepoint = null;
        try {
            savepoint = connection.setSavepoint();
            assertThatThrownBy(() ->
                jdbcTemplate.update(
                    """
                    update modeling_physical_relation_observation
                       set verified = false
                     where pipeline_run_group_id = ?
                    """,
                    pipelineRunGroupId
                )
            )
                .hasMessageContaining(
                    "PHYSICAL_RELATION_OBSERVATION_IS_APPEND_ONLY"
                );
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        } finally {
            if (savepoint != null) {
                try {
                    connection.rollback(savepoint);
                    connection.releaseSavepoint(savepoint);
                } catch (SQLException failure) {
                    throw new IllegalStateException(failure);
                }
            }
        }
    }

    private record Scope(
        String tenant,
        UUID planId,
        UUID domainId,
        UUID modelId,
        UUID implementationId,
        UUID candidateId,
        UUID entryId,
        String modelChecksum,
        String implementationChecksum,
        String dbtUniqueId,
        String targetIdentifier
    ) {
        String selector() {
            return dbtUniqueId.substring(dbtUniqueId.lastIndexOf('.') + 1);
        }
    }
}
