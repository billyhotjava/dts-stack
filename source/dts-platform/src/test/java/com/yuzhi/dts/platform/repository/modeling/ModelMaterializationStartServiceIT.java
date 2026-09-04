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
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateService;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.RelationLocator;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.TargetContext;
import com.yuzhi.dts.platform.service.modeling.PostgresPhysicalRelationInspector;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CreateCandidateCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.DriftReasonView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ScopeEntryCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
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
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ModelMaterializationStartServiceIT {

    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    private final Queue<Scope> trackedScopes = new ConcurrentLinkedQueue<>();

    @Autowired
    private ModelReleaseCandidateRepository candidates;

    @Autowired
    private ModelMaterializationStartService starts;

    @Autowired
    private ModelReleaseCandidateService candidateCommands;

    @Autowired
    private DbtRuntimeProfileLeaseRepository profileLeases;

    @Autowired
    private ModelMaterializationDispatchRepository dispatches;

    @Autowired
    private ModelMaterializationRunRepository materializationRuns;

    @Autowired
    private ModelPublicationQualityEvidenceRepository qualityEvidence;

    @Autowired
    private PhysicalRelationObservationRepository observations;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanupTrackedScopes() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        RuntimeException cleanupFailure = null;
        Scope scope;
        while ((scope = trackedScopes.poll()) != null) {
            try {
                Scope trackedScope = scope;
                transaction.executeWithoutResult(status -> cleanup(trackedScope));
            } catch (RuntimeException failure) {
                if (cleanupFailure == null) {
                    cleanupFailure = failure;
                } else {
                    cleanupFailure.addSuppressed(failure);
                }
            }
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    @Test
    void commitsBuildingSnapshotClaimAndOneQueuedRunAsOneUnit() {
        Scope scope = scope("atomic-success");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status ->
            jdbcTemplate.execute(
                "create table public." +
                scope.targetIdentifier() +
                " (project_id uuid not null, amount numeric(18,2))"
            )
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
            Instant leaseIssuedAt = Instant.now();
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
                    leaseIssuedAt,
                    leaseIssuedAt.plusSeconds(300),
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
            TransactionAwareDataSourceProxy transactionAwareDataSource =
                new TransactionAwareDataSourceProxy(dataSource);
            when(targetFactory.resolveRuntimeTarget())
                .thenReturn(runtimeTarget);
            try {
                when(targetFactory.open(runtimeTarget))
                    .thenAnswer(invocation ->
                        transactionAwareDataSource.getConnection()
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
                    List.of("project_id", "amount"),
                    Map.of(
                        "project_id",
                        "uuid",
                        "amount",
                        "numeric(18,2)"
                    )
                )
            );
            assertThat(physical.exists()).isTrue();
            assertThat(physical.actualType())
                .isEqualTo(ExpectedRelationType.TABLE);
            assertThat(physical.columns())
                .extracting(column -> column.name())
                .containsExactly("project_id", "amount");
            assertThat(
                inspector(
                    targetFactory,
                    dispatchAt.plusSeconds(2)
                ).dataTypeMatches(
                    "numeric(18,2)",
                    physical.columns().get(1).dataType()
                )
            )
                .isTrue();
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
                profileLeases.consume(leaseId)
            )
                .isTrue();
            assertThat(
                profileLeases.consume(leaseId)
            )
                .isTrue();
            profileLeases.release(
                leaseId,
                leaseIssuedAt.plusSeconds(3)
            );
            assertThat(profileLeases.find(leaseId))
                .get()
                .extracting(LeaseRecord::status)
                .isEqualTo(LeaseStatus.RELEASED);
            CandidateView built = candidateCommands
                .transition(
                    scope.tenant(),
                    "builder-a",
                    scope.candidateId(),
                    new TransitionCommand(
                        2,
                        DeliveryStatus.BUILT,
                        "quality-evidence-built-key",
                        "record completed release build"
                    )
                )
                .candidate();
            candidateCommands.publicationRequested(
                scope.tenant(),
                "builder-a",
                scope.candidateId(),
                new TransitionCommand(
                    built.version(),
                    DeliveryStatus.QUALITY_RUNNING,
                    "quality-evidence-publication-key",
                    "submit completed dbt build evidence"
                )
            );
            assertThat(qualityEvidence.findQualityRunning(20))
                .singleElement()
                .satisfies(work -> {
                    assertThat(work.candidateId())
                        .isEqualTo(scope.candidateId());
                    assertThat(work.evidenceState())
                        .isEqualTo(
                            ModelPublicationQualityEvidenceRepository.EvidenceState.PASSED
                        );
                });
            status.setRollbackOnly();
            });
        } finally {
            transaction.executeWithoutResult(status ->
                jdbcTemplate.execute(
                    "drop table if exists public." +
                    scope.targetIdentifier()
                )
            );
        }
    }

    @Test
    void recoversLegacyNodeNameTargetFromImmutableVisualAuthoringInput() {
        Scope scope = scope("legacy-visual-target");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            seed(scope, true);
            String inputs =
                "[{\"generatorType\":\"RELEASE_IT\",\"config\":{\"visualImplementation\":{\"settings\":{\"targetPhysicalName\":\"" +
                scope.targetIdentifier() +
                "\"}}}}]";
            String settings =
                "{\"targetPhysicalName\":\"" +
                scope.selector() +
                "\",\"loadStrategy\":\"FULL\",\"partitionFields\":[]}";
            assertThat(
                jdbcTemplate.update(
                    """
                    update modeling_model_implementation
                       set inputs_json = cast(? as jsonb), settings_json = cast(? as jsonb)
                     where tenant_id = ? and id = ?
                    """,
                    inputs,
                    settings,
                    scope.tenant(),
                    scope.implementationId()
                )
            )
                .isEqualTo(1);
            assertThat(
                jdbcTemplate.update(
                    """
                    update modeling_model_implementation_revision
                       set inputs_json = cast(? as jsonb), settings_json = cast(? as jsonb)
                     where tenant_id = ? and implementation_id = ? and revision = 1
                    """,
                    inputs,
                    settings,
                    scope.tenant(),
                    scope.implementationId()
                )
            )
                .isEqualTo(1);

            starts.start(
                scope.tenant(),
                "builder-a",
                scope.candidateId(),
                1,
                "legacy-visual-target-start",
                "recover the immutable visual target"
            );

            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select target_identifier
                      from modeling_model_release_candidate_entry
                     where tenant_id = ? and candidate_id = ?
                    """,
                    String.class,
                    scope.tenant(),
                    scope.candidateId()
                )
            )
                .isEqualTo(scope.targetIdentifier());
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select target
                      from modeling_pipeline_run
                     where tenant_id = ? and release_candidate_id = ?
                       and run_purpose = 'RELEASE_BUILD'
                    """,
                    String.class,
                    scope.tenant(),
                    scope.candidateId()
                )
            )
                .isEqualTo(scope.targetIdentifier());
            status.setRollbackOnly();
        });
    }

    @Test
    void postBuildCommandUsesTheLockedMaterializationSnapshotForDriftDetection() {
        Scope scope = scope("post-build-drift");
        TransactionTemplate transaction = new TransactionTemplate(
            transactionManager
        );

        transaction.executeWithoutResult(status -> {
            seed(scope, true);
            CandidateView building = starts
                .start(
                    scope.tenant(),
                    "builder-a",
                    scope.candidateId(),
                    1,
                    "post-build-start-key",
                    "lock materialization snapshot"
                )
                .candidate();
            CandidateView built = candidateCommands
                .transition(
                    scope.tenant(),
                    "builder-a",
                    scope.candidateId(),
                    new TransitionCommand(
                        building.version(),
                        DeliveryStatus.BUILT,
                        "post-build-built-key",
                        "record successful build"
                    )
                )
                .candidate();
            jdbcTemplate.update(
                """
                update modeling_model_implementation
                   set current_implementation_checksum = ?
                 where tenant_id = ? and id = ?
                """,
                "e".repeat(64),
                scope.tenant(),
                scope.implementationId()
            );

            CommandResult result = candidateCommands.transition(
                scope.tenant(),
                "builder-a",
                scope.candidateId(),
                new TransitionCommand(
                    built.version(),
                    DeliveryStatus.QUALITY_RUNNING,
                    "post-build-quality-key",
                    "run quality against current snapshot"
                )
            );

            assertThat(result.candidate().status())
                .isEqualTo(DeliveryStatus.STALE);
            assertThat(result.driftReasons())
                .extracting(DriftReasonView::code)
                .containsExactly(
                    "MODEL_MATERIALIZATION_RETRY_SNAPSHOT_STALE"
                );
            status.setRollbackOnly();
        });
    }

    @Test
    void newSingleModelBuildReclaimsClaimLeftByStaleCandidate() {
        assertNewSingleModelBuildReclaimsClaimLeftByTerminalCandidate(
            "stale-claim-reclaim",
            DeliveryStatus.STALE
        );
    }

    @Test
    void newSingleModelBuildReclaimsClaimLeftByPublishedCandidate() {
        assertNewSingleModelBuildReclaimsClaimLeftByTerminalCandidate(
            "published-claim-reclaim",
            DeliveryStatus.PUBLISHED
        );
    }

    private void assertNewSingleModelBuildReclaimsClaimLeftByTerminalCandidate(
        String scopeName,
        DeliveryStatus terminalStatus
    ) {
        Scope scope = scope(scopeName);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            seed(scope, true);
            CandidateView building = starts
                .start(
                    scope.tenant(),
                    "builder-a",
                    scope.candidateId(),
                    1,
                    "stale-claim-first-start",
                    "lock first candidate snapshot"
                )
                .candidate();
            assertThat(
                jdbcTemplate.update(
                    """
                    update modeling_model_release_candidate
                       set status = ?,
                           version = version + 1,
                           published_by = case when ? = 'PUBLISHED' then 'builder-a' else published_by end,
                           published_date = case when ? = 'PUBLISHED' then current_timestamp else published_date end,
                           last_modified_date = current_timestamp
                     where tenant_id = ?
                       and id = ?
                       and status = 'BUILDING'
                       and version = ?
                    """,
                    terminalStatus.name(),
                    terminalStatus.name(),
                    terminalStatus.name(),
                    scope.tenant(),
                    scope.candidateId(),
                    building.version()
                )
            )
                .isEqualTo(1);
            assertThat(
                jdbcTemplate.update(
                    "update modeling_model_release_candidate_entry set status = ? where tenant_id = ? and candidate_id = ? and status = 'BUILDING'",
                    terminalStatus.name(),
                    scope.tenant(),
                    scope.candidateId()
                )
            )
                .isEqualTo(1);
            assertThat(
                jdbcTemplate.queryForObject(
                    "select active_claim_key is not null from modeling_model_release_candidate_entry where tenant_id = ? and candidate_id = ?",
                    Boolean.class,
                    scope.tenant(),
                    scope.candidateId()
                )
            )
                .isTrue();

            CandidateView replacement = candidateCommands
                .createSingleModelIntent(
                    scope.tenant(),
                    "builder-a",
                    new CreateCandidateCommand(
                        scope.planId(),
                        "PROD",
                        List.of(
                            new ScopeEntryCommand(
                                scope.modelId(),
                                0,
                                "current revision"
                            )
                        ),
                        scopeName + "-single-model-replacement",
                        "create current single-model candidate"
                    )
                )
                .candidate();

            CandidateView replacementBuilding = starts
                .start(
                    scope.tenant(),
                    "builder-a",
                    replacement.id(),
                    replacement.version(),
                    scopeName + "-replacement-start",
                    "build current single-model candidate"
                )
                .candidate();
            assertThat(replacementBuilding.status())
                .isEqualTo(DeliveryStatus.BUILDING);
            assertThat(
                jdbcTemplate.queryForList(
                    "select candidate_id, active_claim_key from modeling_model_release_candidate_entry where tenant_id = ? and candidate_id in (?, ?)",
                    scope.tenant(),
                    scope.candidateId(),
                    replacement.id()
                )
            )
                .satisfies(rows -> {
                    assertThat(rows).hasSize(2);
                    assertThat(
                        rows.stream()
                            .filter(row -> scope.candidateId().equals(row.get("candidate_id")))
                            .findFirst()
                            .orElseThrow()
                            .get("active_claim_key")
                    )
                        .isNull();
                    assertThat(
                        rows.stream()
                            .filter(row -> replacement.id().equals(row.get("candidate_id")))
                            .findFirst()
                            .orElseThrow()
                            .get("active_claim_key")
                    )
                        .asString()
                        .matches("^[0-9a-f]{64}$");
                });
            status.setRollbackOnly();
        });
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

    @Test
    void retryRequiresReconciledFailureAndCreatesAttemptTwo() {
        Scope scope = scope("retry-attempt-two");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Instant attemptAt = Instant.now().plusSeconds(60);
        UUID firstGroupId = transaction.execute(status -> {
                seed(scope, true);
                CandidateView building = starts
                    .start(
                        scope.tenant(),
                        "builder-a",
                        scope.candidateId(),
                        1,
                        "retry-start-key",
                        "start first attempt"
                    )
                    .candidate();
                assertThat(building.version()).isEqualTo(2);

                var claimed = dispatches
                    .claimNext(attemptAt, Duration.ofMinutes(2))
                    .orElseThrow();
                dispatches.markUnknown(
                    claimed.id(),
                    "MODEL_AIRFLOW_TRIGGER_UNKNOWN",
                    attemptAt.plusSeconds(30),
                    attemptAt
                );
                CandidateView failed = candidateCommands
                    .transition(
                        scope.tenant(),
                        "builder-a",
                        scope.candidateId(),
                        new TransitionCommand(
                            2,
                            DeliveryStatus.BUILD_FAILED,
                            "retry-failed-state-key",
                            "simulate failed state before dispatch reconciliation"
                        )
                    )
                    .candidate();
                assertThat(failed.version()).isEqualTo(3);
                return claimed.id();
            });

            assertThat(firstGroupId).isNotNull();
            assertThatThrownBy(() ->
                starts.retry(
                    scope.tenant(),
                    "builder-a",
                    scope.candidateId(),
                    3,
                    "retry-before-reconcile-key",
                    "retry before unknown is reconciled"
                )
            )
                .isInstanceOf(ModelReleaseCandidateException.class)
                .satisfies(error ->
                    assertThat(((ModelReleaseCandidateException) error).code())
                        .isEqualTo("MODEL_MATERIALIZATION_RETRY_RECONCILIATION_REQUIRED")
                );
            assertThat(candidates.find(scope.tenant(), scope.candidateId()))
                .get()
                .extracting(CandidateView::status, CandidateView::version)
                .containsExactly(DeliveryStatus.BUILD_FAILED, 3);

            materializationRuns.markFailed(
                firstGroupId,
                "MODEL_DBT_AIRFLOW_UPSTREAM_FAILED",
                attemptAt.plusSeconds(1)
            );
            CandidateView retrying = starts
                .retry(
                    scope.tenant(),
                    "builder-a",
                    scope.candidateId(),
                    3,
                    "retry-after-reconcile-key",
                    "retry reconciled failure"
                )
                .candidate();

            assertThat(retrying)
                .extracting(CandidateView::status, CandidateView::version)
                .containsExactly(DeliveryStatus.BUILDING, 4);
            assertThat(
                jdbcTemplate.queryForList(
                    """
                    select attempt, status
                      from modeling_pipeline_run
                     where tenant_id = ? and release_candidate_id = ?
                     order by attempt
                    """,
                    scope.tenant(),
                    scope.candidateId()
                )
            )
                .extracting(row -> row.get("attempt"), row -> row.get("status"))
                .containsExactly(
                    org.assertj.core.groups.Tuple.tuple(1, "FAILED"),
                    org.assertj.core.groups.Tuple.tuple(2, "QUEUED")
                );
            assertThat(
                jdbcTemplate.queryForList(
                    """
                    select attempt, status, airflow_run_id
                      from modeling_materialization_dispatch
                     where tenant_id = ? and candidate_id = ?
                     order by attempt
                    """,
                    scope.tenant(),
                    scope.candidateId()
                )
            )
                .satisfies(rows -> {
                    assertThat(rows).hasSize(2);
                    assertThat(rows.get(0))
                        .containsEntry("attempt", 1)
                        .containsEntry("status", "FAILED");
                    assertThat(rows.get(1))
                        .containsEntry("attempt", 2)
                        .containsEntry("status", "PENDING")
                        .containsEntry(
                            "airflow_run_id",
                            "dts_rc_" +
                            scope.candidateId().toString().replace("-", "") +
                            "_a2"
                        );
                });
    }

    @Test
    void retrySnapshotDriftTransitionsCandidateStaleWithoutAttemptTwo()
        throws Exception {
        Scope model = scope("retry-model-drift");
        assertRetryDriftTransitionsStale(
            model,
            "model",
            "MODEL_RELEASE_CANDIDATE_STALE",
            () -> {
                String currentChecksum = "6".repeat(64);
                jdbcTemplate.update(
                    """
                    insert into modeling_model_spec_revision (
                        id, model_spec_id, revision, status,
                        content_checksum, created_date, last_modified_date,
                        tenant_id, contract_version, snapshot_json, created_by
                    ) values (
                        ?, ?, 2, 'DRAFT', ?, current_timestamp,
                        current_timestamp, ?, 2, cast('{}' as jsonb), 'builder-a'
                    )
                    """,
                    UUID.randomUUID(),
                    model.modelId(),
                    currentChecksum,
                    model.tenant()
                );
                assertThat(
                    jdbcTemplate.update(
                        """
                        update modeling_model_spec
                           set revision = 2, current_checksum = ?,
                               last_modified_date = current_timestamp
                         where tenant_id = ? and id = ?
                        """,
                        currentChecksum,
                        model.tenant(),
                        model.modelId()
                    )
                )
                    .isEqualTo(1);
            }
        );

        Scope implementation = scope("retry-implementation-drift");
        assertRetryDriftTransitionsStale(
            implementation,
            "implementation",
            "MODEL_MATERIALIZATION_RETRY_SNAPSHOT_STALE",
            () ->
                assertThat(
                    jdbcTemplate.update(
                        """
                        update modeling_model_implementation
                           set dbt_unique_id = ?,
                               last_modified_date = current_timestamp
                         where tenant_id = ? and id = ? and status = 'ACTIVE'
                        """,
                        "model.dts." + implementation.selector() + "_changed",
                        implementation.tenant(),
                        implementation.implementationId()
                    )
                )
                    .isEqualTo(1)
        );

        Scope artifact = scope("retry-artifact-drift");
        CommandResult artifactStale = assertRetryDriftTransitionsStale(
            artifact,
            "artifact",
            "MODEL_MATERIALIZATION_RETRY_SNAPSHOT_STALE",
            () -> {
                String sql =
                    "{{ config(materialized='table',alias='" +
                    artifact.targetIdentifier() +
                    "') }}\nselect 2 as project_id\n";
                assertThat(
                    jdbcTemplate.update(
                        """
                        insert into modeling_dbt_artifact (
                            id, model_spec_id, plan_id, project_key,
                            dbt_unique_id, artifact_key, artifact_type, path,
                            content_checksum, content, status, revision,
                            model_checksum, ownership, idempotency_key,
                            implementation_revision, node_kind,
                            materialization, physical_asset_ref, created_date,
                            last_modified_date
                        ) values (
                            ?, ?, ?, 'dts', ?, ?, 'SCHEMA', ?, ?, ?,
                            'COMPILED', 1, ?, 'DESIGNER_GENERATED', ?, 1,
                            'MODEL', 'table', null, current_timestamp,
                            current_timestamp
                        )
                        """,
                        UUID.randomUUID(),
                        artifact.modelId(),
                        artifact.planId(),
                        artifact.dbtUniqueId(),
                        "SCHEMA:models/dwd/" + artifact.selector() + ".yml",
                        "models/dwd/" + artifact.selector() + ".yml",
                        sha256(sql),
                        sql,
                        artifact.modelChecksum(),
                        "artifact-drift-" + artifact.modelId()
                    )
                )
                    .isEqualTo(1);
            }
        );
        CommandResult replacement = candidateCommands.createReplacement(
            artifact.tenant(),
            "builder-a",
            artifact.candidateId(),
            artifactStale.candidate().version(),
            new CreateCandidateCommand(
                artifact.planId(),
                "PROD",
                List.of(
                    new ScopeEntryCommand(
                        artifact.modelId(),
                        0,
                        "current revision"
                    )
                ),
                "artifact-replacement-key",
                "replace stale artifact snapshot"
            )
        );
        assertThat(replacement.candidate().status())
            .isEqualTo(DeliveryStatus.DRAFT);
        assertThat(
            jdbcTemplate.queryForList(
                """
                select candidate_id, active_claim_key
                  from modeling_model_release_candidate_entry
                 where tenant_id = ? and candidate_id in (?, ?)
                 order by candidate_id
                """,
                artifact.tenant(),
                artifact.candidateId(),
                replacement.candidate().id()
            )
        )
            .satisfies(rows -> {
                assertThat(rows).hasSize(2);
                Map<String, Object> old = rows
                    .stream()
                    .filter(row ->
                        artifact.candidateId().equals(
                            row.get("candidate_id")
                        )
                    )
                    .findFirst()
                    .orElseThrow();
                Map<String, Object> current = rows
                    .stream()
                    .filter(row ->
                        replacement
                            .candidate()
                            .id()
                            .equals(row.get("candidate_id"))
                    )
                    .findFirst()
                    .orElseThrow();
                assertThat(old.get("active_claim_key")).isNull();
                assertThat(current.get("active_claim_key"))
                    .asString()
                    .matches("^[0-9a-f]{64}$");
            });
        CandidateView replacementBuilding = starts
            .start(
                artifact.tenant(),
                "builder-a",
                replacement.candidate().id(),
                1,
                "artifact-replacement-start-key",
                "build replacement snapshot"
            )
            .candidate();
        assertThat(replacementBuilding.status())
            .isEqualTo(DeliveryStatus.BUILDING);

        Scope dependency = scope("retry-dependency-drift");
        assertRetryDriftTransitionsStale(
            dependency,
            "dependency",
            "MODEL_MATERIALIZATION_RETRY_SNAPSHOT_STALE",
            () ->
                assertThat(
                    jdbcTemplate.update(
                        """
                        update modeling_model_implementation
                           set inputs_json = cast(? as jsonb),
                               last_modified_date = current_timestamp
                         where tenant_id = ? and id = ? and status = 'ACTIVE'
                        """,
                        """
                        [{"generatorType":"RELEASE_IT","config":{"variant":"changed"}}]
                        """,
                        dependency.tenant(),
                        dependency.implementationId()
                    )
                )
                    .isEqualTo(1)
        );

        Scope target = scope("retry-target-drift");
        assertRetryDriftTransitionsStale(
            target,
            "target",
            "MODEL_MATERIALIZATION_RETRY_SNAPSHOT_STALE",
            () ->
                assertThat(
                    jdbcTemplate.update(
                        """
                        update modeling_model_implementation
                           set settings_json = jsonb_set(
                                   settings_json,
                                   '{targetPhysicalName}',
                                   to_jsonb(cast(? as text)),
                                   false
                               ),
                               last_modified_date = current_timestamp
                         where tenant_id = ? and id = ? and status = 'ACTIVE'
                        """,
                        target.targetIdentifier() + "_changed",
                        target.tenant(),
                        target.implementationId()
                    )
                )
                    .isEqualTo(1)
        );

        Scope concurrent = scope("retry-replacement-concurrency");
        CommandResult concurrentStale = assertRetryDriftTransitionsStale(
            concurrent,
            "replacement-concurrency",
            "MODEL_MATERIALIZATION_RETRY_SNAPSHOT_STALE",
            () ->
                assertThat(
                    jdbcTemplate.update(
                        """
                        update modeling_model_implementation
                           set dbt_unique_id = ?,
                               last_modified_date = current_timestamp
                         where tenant_id = ? and id = ? and status = 'ACTIVE'
                        """,
                        "model.dts." + concurrent.selector() + "_changed",
                        concurrent.tenant(),
                        concurrent.implementationId()
                    )
                )
                    .isEqualTo(1)
        );
        assertConcurrentReplacementHasOneClaimOwner(
            concurrent,
            concurrentStale
        );
        assertConcurrentRetryReplaysExactlyOnce(
            scope("concurrent-retry-same-key")
        );
        assertConcurrentRetryWithDifferentKeysKeepsOneAttemptTwo(
            scope("concurrent-retry-different-keys")
        );
        assertConcurrentStartReplaysExactlyOnce(
            scope("concurrent-start-same-key")
        );
        assertConcurrentStartWithDifferentKeysKeepsOneActiveRun(
            scope("concurrent-start-different-keys")
        );
    }

    private void assertConcurrentReplacementHasOneClaimOwner(
        Scope scope,
        CommandResult stale
    ) throws Exception {
        List<Object> outcomes = executeConcurrently(key ->
            candidateCommands.createReplacement(
                scope.tenant(),
                "builder-" + key,
                scope.candidateId(),
                stale.candidate().version(),
                new CreateCandidateCommand(
                    scope.planId(),
                    "PROD",
                    List.of(
                        new ScopeEntryCommand(
                            scope.modelId(),
                            0,
                            "current revision"
                        )
                    ),
                    "concurrent-replacement-" + key,
                    "replace stale snapshot concurrently"
                )
            )
        );

        assertThat(outcomes)
            .filteredOn(CommandResult.class::isInstance)
            .hasSize(1);
        assertThat(outcomes)
            .filteredOn(ModelReleaseCandidateException.class::isInstance)
            .singleElement()
            .satisfies(failure ->
                assertThat(
                    ((ModelReleaseCandidateException) failure).code()
                )
                    .isEqualTo("MODEL_RELEASE_CANDIDATE_WRITE_CONFLICT")
            );

        CommandResult winner = outcomes
            .stream()
            .filter(CommandResult.class::isInstance)
            .map(CommandResult.class::cast)
            .findFirst()
            .orElseThrow();
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                  from modeling_model_release_candidate
                 where tenant_id = ? and plan_id = ?
                """,
                Integer.class,
                scope.tenant(),
                scope.planId()
            )
        )
            .isEqualTo(2);
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                  from modeling_model_release_candidate_entry
                 where tenant_id = ? and candidate_id = ?
                   and active_claim_key is null
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
                select count(*)
                  from modeling_model_release_candidate_entry
                 where tenant_id = ? and candidate_id = ?
                   and active_claim_key ~ '^[0-9a-f]{64}$'
                """,
                Integer.class,
                scope.tenant(),
                winner.candidate().id()
            )
        )
            .isEqualTo(1);
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                  from modeling_model_release_candidate_command
                 where tenant_id = ? and candidate_id = ?
                   and event_type = 'CREATED'
                """,
                Integer.class,
                scope.tenant(),
                winner.candidate().id()
            )
        )
            .isEqualTo(1);
    }

    private void assertConcurrentRetryReplaysExactlyOnce(Scope scope)
        throws Exception {
        prepareFailedAttemptForRetry(scope, "same-key");
        List<Object> outcomes = executeConcurrently(key ->
            starts.retry(
                scope.tenant(),
                "builder-a",
                scope.candidateId(),
                3,
                "concurrent-retry-same-key",
                "retry exactly once"
            )
        );

        assertThat(outcomes)
            .allSatisfy(outcome ->
                assertThat(outcome).isInstanceOf(CommandResult.class)
            );
        assertThat(outcomes)
            .map(CommandResult.class::cast)
            .extracting(CommandResult::replayed)
            .containsExactlyInAnyOrder(false, true);
        assertSingleAttemptTwo(scope, "concurrent-retry-same-key");
    }

    private void assertConcurrentRetryWithDifferentKeysKeepsOneAttemptTwo(
        Scope scope
    ) throws Exception {
        prepareFailedAttemptForRetry(scope, "different-keys");
        List<Object> outcomes = executeConcurrently(key ->
            starts.retry(
                scope.tenant(),
                "builder-" + key,
                scope.candidateId(),
                3,
                "concurrent-retry-" + key,
                "retry with a competing key"
            )
        );

        assertThat(outcomes)
            .filteredOn(CommandResult.class::isInstance)
            .singleElement();
        assertThat(outcomes)
            .filteredOn(ModelReleaseCandidateException.class::isInstance)
            .singleElement()
            .satisfies(failure ->
                assertThat(
                    ((ModelReleaseCandidateException) failure).code()
                )
                    .isEqualTo(
                        "MODEL_RELEASE_CANDIDATE_VERSION_CONFLICT"
                    )
            );
        assertSingleAttemptTwo(scope, null);
    }

    private void prepareFailedAttemptForRetry(
        Scope scope,
        String key
    ) {
        jdbcTemplate.update(
            """
            update modeling_materialization_dispatch
               set status = 'FAILED',
                   last_error_code = 'TEST_RETIRED_PENDING_DISPATCH'
             where status = 'PENDING'
            """
        );
        new TransactionTemplate(transactionManager).executeWithoutResult(
            status -> {
                seed(scope, true);
                starts.start(
                    scope.tenant(),
                    "builder-a",
                    scope.candidateId(),
                    1,
                    "concurrent-retry-start-" + key,
                    "start attempt one"
                );
                UUID groupId = jdbcTemplate.queryForObject(
                    """
                    select pipeline_run_group_id
                      from modeling_pipeline_run
                     where tenant_id = ? and release_candidate_id = ?
                       and attempt = 1
                    """,
                    UUID.class,
                    scope.tenant(),
                    scope.candidateId()
                );
                var claimed = dispatches
                    .claimNext(
                        Instant.now().plusSeconds(60),
                        Duration.ofMinutes(2)
                    )
                    .orElseThrow();
                assertThat(claimed.id()).isEqualTo(groupId);
                materializationRuns.markFailed(
                    groupId,
                    "MODEL_DBT_AIRFLOW_UPSTREAM_FAILED",
                    Instant.now().plusSeconds(61)
                );
                CandidateView failed = candidateCommands
                    .transition(
                        scope.tenant(),
                        "builder-a",
                        scope.candidateId(),
                        new TransitionCommand(
                            2,
                            DeliveryStatus.BUILD_FAILED,
                            "concurrent-retry-failed-" + key,
                            "record failed attempt"
                        )
                    )
                    .candidate();
                assertThat(failed)
                    .extracting(
                        CandidateView::status,
                        CandidateView::version
                    )
                    .containsExactly(DeliveryStatus.BUILD_FAILED, 3);
            }
        );
    }

    private void assertSingleAttemptTwo(
        Scope scope,
        String idempotencyKey
    ) {
        assertThat(candidates.find(scope.tenant(), scope.candidateId()))
            .get()
            .extracting(CandidateView::status, CandidateView::version)
            .containsExactly(DeliveryStatus.BUILDING, 4);
        assertThat(
            jdbcTemplate.queryForList(
                """
                select attempt, status
                  from modeling_pipeline_run
                 where tenant_id = ? and release_candidate_id = ?
                 order by attempt
                """,
                scope.tenant(),
                scope.candidateId()
            )
        )
            .extracting(row -> row.get("attempt"), row -> row.get("status"))
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "FAILED"),
                org.assertj.core.groups.Tuple.tuple(2, "QUEUED")
            );
        assertThat(
            jdbcTemplate.queryForList(
                """
                select attempt, status
                  from modeling_materialization_dispatch
                 where tenant_id = ? and candidate_id = ?
                 order by attempt
                """,
                scope.tenant(),
                scope.candidateId()
            )
        )
            .extracting(row -> row.get("attempt"), row -> row.get("status"))
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "FAILED"),
                org.assertj.core.groups.Tuple.tuple(2, "PENDING")
            );
        if (idempotencyKey != null) {
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*)
                      from modeling_model_release_candidate_command
                     where tenant_id = ? and candidate_id = ?
                       and idempotency_key = ?
                    """,
                    Integer.class,
                    scope.tenant(),
                    scope.candidateId(),
                    idempotencyKey
                )
            )
                .isEqualTo(1);
        }
    }

    private void assertConcurrentStartReplaysExactlyOnce(Scope scope)
        throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(
            status -> seed(scope, true)
        );
        List<Object> outcomes = executeConcurrently(key ->
            starts.start(
                scope.tenant(),
                "builder-a",
                scope.candidateId(),
                1,
                "concurrent-start-same-key",
                "start exactly once"
            )
        );

        assertThat(outcomes)
            .allSatisfy(outcome ->
                assertThat(outcome).isInstanceOf(CommandResult.class)
            );
        assertThat(outcomes)
            .map(CommandResult.class::cast)
            .extracting(CommandResult::replayed)
            .containsExactlyInAnyOrder(false, true);
        assertSingleActiveBuild(scope, "concurrent-start-same-key");
    }

    private void assertConcurrentStartWithDifferentKeysKeepsOneActiveRun(
        Scope scope
    ) throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(
            status -> seed(scope, true)
        );
        List<Object> outcomes = executeConcurrently(key ->
            starts.start(
                scope.tenant(),
                "builder-" + key,
                scope.candidateId(),
                1,
                "concurrent-start-" + key,
                "start with a competing key"
            )
        );

        assertThat(outcomes)
            .filteredOn(CommandResult.class::isInstance)
            .singleElement();
        assertThat(outcomes)
            .filteredOn(ModelReleaseCandidateException.class::isInstance)
            .singleElement()
            .satisfies(failure ->
                assertThat(
                    ((ModelReleaseCandidateException) failure).code()
                )
                    .isEqualTo(
                        "MODEL_RELEASE_CANDIDATE_VERSION_CONFLICT"
                    )
            );
        assertSingleActiveBuild(scope, null);
    }

    private void assertSingleActiveBuild(
        Scope scope,
        String idempotencyKey
    ) {
        assertThat(candidates.find(scope.tenant(), scope.candidateId()))
            .get()
            .extracting(CandidateView::status, CandidateView::version)
            .containsExactly(DeliveryStatus.BUILDING, 2);
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                  from modeling_pipeline_run
                 where tenant_id = ? and release_candidate_id = ?
                   and status = 'QUEUED'
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
                select count(*)
                  from modeling_materialization_dispatch
                 where tenant_id = ? and candidate_id = ?
                   and status = 'PENDING'
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
                select count(*)
                  from modeling_model_release_candidate_entry
                 where tenant_id = ? and candidate_id = ?
                   and active_claim_key ~ '^[0-9a-f]{64}$'
                """,
                Integer.class,
                scope.tenant(),
                scope.candidateId()
            )
        )
            .isEqualTo(1);
        if (idempotencyKey != null) {
            assertThat(
                jdbcTemplate.queryForObject(
                    """
                    select count(*)
                      from modeling_model_release_candidate_command
                     where tenant_id = ? and candidate_id = ?
                       and idempotency_key = ?
                    """,
                    Integer.class,
                    scope.tenant(),
                    scope.candidateId(),
                    idempotencyKey
                )
            )
                .isEqualTo(1);
        }
    }

    private List<Object> executeConcurrently(
        Function<String, Object> operation
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        Function<String, Object> synchronizedOperation = key -> {
            ready.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    return new IllegalStateException(
                        "Concurrent operation start barrier timed out"
                    );
                }
                return operation.apply(key);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return interrupted;
            } catch (RuntimeException failure) {
                return failure;
            }
        };

        try {
            var first = CompletableFuture.supplyAsync(
                () -> synchronizedOperation.apply("a"),
                executor
            );
            var second = CompletableFuture.supplyAsync(
                () -> synchronizedOperation.apply("b"),
                executor
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            return List.of(
                first.get(30, TimeUnit.SECONDS),
                second.get(30, TimeUnit.SECONDS)
            );
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private CommandResult assertRetryDriftTransitionsStale(
        Scope scope,
        String key,
        String expectedDriftCode,
        Runnable mutateCurrentSnapshot
    ) {
        TransactionTemplate transaction = new TransactionTemplate(
            transactionManager
        );
        Instant attemptAt = Instant.now().plusSeconds(60);
        UUID firstGroupId = transaction.execute(status -> {
            seed(scope, true);
            starts.start(
                scope.tenant(),
                "builder-a",
                scope.candidateId(),
                1,
                key + "-drift-start-key",
                "start first attempt"
            );
            var claimed = dispatches
                .claimNext(attemptAt, Duration.ofMinutes(2))
                .orElseThrow();
            materializationRuns.markFailed(
                claimed.id(),
                "MODEL_DBT_AIRFLOW_UPSTREAM_FAILED",
                attemptAt.plusSeconds(1)
            );
            CandidateView failed = candidateCommands
                .transition(
                    scope.tenant(),
                    "builder-a",
                    scope.candidateId(),
                    new TransitionCommand(
                        2,
                        DeliveryStatus.BUILD_FAILED,
                        key + "-drift-failed-state-key",
                        "record failed attempt"
                    )
                )
                .candidate();
            assertThat(failed)
                .extracting(CandidateView::status, CandidateView::version)
                .containsExactly(DeliveryStatus.BUILD_FAILED, 3);
            return claimed.id();
        });

        assertThat(firstGroupId).isNotNull();
        mutateCurrentSnapshot.run();

        CommandResult stale = starts.retry(
            scope.tenant(),
            "builder-a",
            scope.candidateId(),
            3,
            key + "-retry-drift-key",
            "retry after current snapshot changed"
        );

        assertThat(stale.candidate())
            .extracting(CandidateView::status, CandidateView::version)
            .containsExactly(DeliveryStatus.STALE, 4);
        assertThat(stale.driftReasons())
            .extracting(DriftReasonView::code)
            .containsExactly(expectedDriftCode);
        assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                  from modeling_pipeline_run
                 where tenant_id = ? and release_candidate_id = ?
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
                select count(*)
                  from modeling_materialization_dispatch
                 where tenant_id = ? and candidate_id = ?
                """,
                Integer.class,
                scope.tenant(),
                scope.candidateId()
            )
        )
            .isEqualTo(1);
        assertThat(
            jdbcTemplate.queryForMap(
                """
                select event_type, from_status, to_status, candidate_version
                  from modeling_model_release_candidate_command
                 where tenant_id = ? and idempotency_key = ?
                """,
                scope.tenant(),
                key + "-retry-drift-key"
            )
        )
            .containsEntry("event_type", "STALE_DETECTED")
            .containsEntry("from_status", "BUILD_FAILED")
            .containsEntry("to_status", "STALE")
            .containsEntry("candidate_version", 4);
        return stale;
    }

    @Test
    void cancellationReleasesClaimsButRetainsRunObservationSnapshotAndRelation() {
        Scope scope = scope("cancel-preserves-evidence");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status ->
            jdbcTemplate.execute(
                "create table public." +
                scope.targetIdentifier() +
                " (project_id uuid not null, amount numeric(18,2))"
            )
        );
        try {
            transaction.executeWithoutResult(status -> {
                seed(scope, true);
                CandidateView building = starts
                    .start(
                        scope.tenant(),
                        "builder-a",
                        scope.candidateId(),
                        1,
                        "cancel-start-key",
                        "start release build before failure"
                    )
                    .candidate();
                assertThat(building.status()).isEqualTo(DeliveryStatus.BUILDING);

                Map<String, Object> pipeline = jdbcTemplate.queryForMap(
                    """
                    select id, pipeline_run_group_id, dbt_invocation_id
                      from modeling_pipeline_run
                     where tenant_id = ? and release_candidate_id = ?
                    """,
                    scope.tenant(),
                    scope.candidateId()
                );
                UUID pipelineRunId = (UUID) pipeline.get("id");
                UUID pipelineRunGroupId = (UUID) pipeline.get("pipeline_run_group_id");
                UUID dbtInvocationId = (UUID) pipeline.get("dbt_invocation_id");
                String scopedBundleChecksum = "9".repeat(64);
                Instant evidenceAt = Instant.now();
                jdbcTemplate.update(
                    """
                    update modeling_pipeline_run
                       set status = 'DBT_SUCCEEDED',
                           scoped_bundle_checksum = ?,
                           started_date = ?,
                           last_modified_date = ?
                     where id = ?
                    """,
                    scopedBundleChecksum,
                    java.sql.Timestamp.from(evidenceAt),
                    java.sql.Timestamp.from(evidenceAt),
                    pipelineRunId
                );
                observations.appendAll(
                    List.of(
                        new ObservationWrite(
                            scope.tenant(),
                            scope.candidateId(),
                            2,
                            pipelineRunGroupId,
                            pipelineRunId,
                            scope.modelId(),
                            1,
                            scope.modelChecksum(),
                            1,
                            scope.implementationChecksum(),
                            dbtInvocationId,
                            scopedBundleChecksum,
                            "postgres",
                            "sha256:" + "a".repeat(64),
                            "release_it",
                            "public",
                            scope.targetIdentifier(),
                            ExpectedRelationType.TABLE,
                            null,
                            false,
                            false,
                            List.of(),
                            "b".repeat(64),
                            null,
                            null,
                            "RELATION_PROBE_FAILED",
                            evidenceAt.plusSeconds(1),
                            evidenceAt.plusSeconds(1)
                        )
                    )
                );
                var claimed = dispatches
                    .claimNext(evidenceAt.plusSeconds(2), Duration.ofMinutes(2))
                    .orElseThrow();
                assertThat(claimed.id()).isEqualTo(pipelineRunGroupId);
                materializationRuns.markFailed(
                    pipelineRunGroupId,
                    "AIRFLOW_DBT_BUILD_FAILED",
                    evidenceAt.plusSeconds(3)
                );

                CandidateView failed = candidateCommands
                    .transition(
                        scope.tenant(),
                        "builder-a",
                        scope.candidateId(),
                        new TransitionCommand(
                            2,
                            DeliveryStatus.BUILD_FAILED,
                            "cancel-failure-key",
                            "record failed build"
                        )
                    )
                    .candidate();
                assertThat(failed.status()).isEqualTo(DeliveryStatus.BUILD_FAILED);
                assertThat(
                    jdbcTemplate.queryForObject(
                        """
                        select count(*)
                          from modeling_model_release_candidate_entry
                         where tenant_id = ? and candidate_id = ?
                           and active_claim_key ~ '^[0-9a-f]{64}$'
                        """,
                        Integer.class,
                        scope.tenant(),
                        scope.candidateId()
                    )
                )
                    .isEqualTo(1);

                var cancelled = candidateCommands.transition(
                    scope.tenant(),
                    "builder-a",
                    scope.candidateId(),
                    new TransitionCommand(
                        3,
                        DeliveryStatus.CANCELLED,
                        "cancel-command-key",
                        "abandon failed delivery"
                    )
                );

                assertThat(cancelled.candidate())
                    .extracting(CandidateView::status, CandidateView::version)
                    .containsExactly(DeliveryStatus.CANCELLED, 4);
                assertThat(
                    jdbcTemplate.queryForMap(
                        """
                        select active_claim_key, implementation_revision,
                               implementation_checksum, artifact_bundle_checksum,
                               dependency_snapshot_checksum
                          from modeling_model_release_candidate_entry
                         where tenant_id = ? and candidate_id = ?
                        """,
                        scope.tenant(),
                        scope.candidateId()
                    )
                )
                    .satisfies(snapshot -> {
                        assertThat(snapshot.get("active_claim_key")).isNull();
                        assertThat(snapshot.get("implementation_revision")).isEqualTo(1);
                        assertThat(snapshot.get("implementation_checksum")).isEqualTo(scope.implementationChecksum());
                        assertThat(snapshot.get("artifact_bundle_checksum")).isNotNull();
                        assertThat(snapshot.get("dependency_snapshot_checksum")).isNotNull();
                    });
                assertThat(
                    jdbcTemplate.queryForObject(
                        "select count(*) from modeling_pipeline_run where release_candidate_id = ?",
                        Integer.class,
                        scope.candidateId()
                    )
                )
                    .isEqualTo(1);
                assertThat(
                    jdbcTemplate.queryForObject(
                        "select count(*) from modeling_physical_relation_observation where release_candidate_id = ?",
                        Integer.class,
                        scope.candidateId()
                    )
                )
                    .isEqualTo(1);
                assertThat(
                    jdbcTemplate.queryForObject(
                        "select to_regclass(?) is not null",
                        Boolean.class,
                        "public." + scope.targetIdentifier()
                    )
                )
                    .isTrue();

                var replacement = candidateCommands.createReplacement(
                    scope.tenant(),
                    "builder-a",
                    scope.candidateId(),
                    4,
                    new CreateCandidateCommand(
                        scope.planId(),
                        "PROD",
                        List.of(new ScopeEntryCommand(scope.modelId(), 0, "current revision")),
                        "cancel-replacement-key",
                        "restart from current revision"
                    )
                );
                assertThat(replacement.candidate().status()).isEqualTo(DeliveryStatus.DRAFT);
                assertThat(replacement.candidate().id()).isNotEqualTo(scope.candidateId());

                starts.start(
                    scope.tenant(),
                    "builder-a",
                    replacement.candidate().id(),
                    1,
                    "cancel-replacement-start-key",
                    "prove released claim can be acquired"
                );
                assertThat(
                    jdbcTemplate.queryForObject(
                        """
                        select count(*)
                          from modeling_model_release_candidate_entry
                         where tenant_id = ? and candidate_id = ?
                           and active_claim_key ~ '^[0-9a-f]{64}$'
                        """,
                        Integer.class,
                        scope.tenant(),
                        replacement.candidate().id()
                    )
                )
                    .isEqualTo(1);
                assertThat(
                    jdbcTemplate.queryForObject(
                        "select count(*) from modeling_physical_relation_observation where release_candidate_id = ?",
                        Integer.class,
                        scope.candidateId()
                    )
                )
                    .isEqualTo(1);
                status.setRollbackOnly();
            });
        } finally {
            transaction.executeWithoutResult(status ->
                jdbcTemplate.execute(
                    "drop table if exists public." +
                    scope.targetIdentifier()
                )
            );
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
                id, tenant_id, plan_id, layer, warehouse_layer_code, model_type,
                implementation_mode, name, status, revision, version, created_date,
                last_modified_date, contract_version, domain_id, current_checksum,
                idempotency_key, idempotency_request_hash, idempotency_response_snapshot
            ) values (
                ?, ?, ?, 'DWD', 'DWD', 'FACT', 'DESIGNER_GENERATED',
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
                id, model_spec_id, revision, status, content_checksum, created_date,
                last_modified_date, tenant_id, contract_version, snapshot_json, created_by
            ) values (?, ?, 1, 'DRAFT', ?, current_timestamp, current_timestamp, ?, 2,
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
        disableAppendOnlyCleanupTriggers();
        boolean cleanupCompleted = false;
        try {
            jdbcTemplate.update(
                """
                delete from modeling_materialization_source_pin
                 where dispatch_id in (
                     select id from modeling_materialization_dispatch where tenant_id = ?
                 )
                """,
                scope.tenant()
            );
            jdbcTemplate.update(
                "delete from modeling_physical_relation_observation where tenant_id = ?",
                scope.tenant()
            );
            jdbcTemplate.update(
                "delete from modeling_model_release_candidate_command where tenant_id = ?",
                scope.tenant()
            );
            jdbcTemplate.update(
                "delete from modeling_materialization_dispatch where tenant_id = ?",
                scope.tenant()
            );
            jdbcTemplate.update(
                "delete from modeling_dbt_runtime_profile_lease where tenant_id = ?",
                scope.tenant()
            );
            jdbcTemplate.update(
                "delete from modeling_pipeline_run where tenant_id = ?",
                scope.tenant()
            );
            jdbcTemplate.update(
                "delete from modeling_model_release_candidate_entry where tenant_id = ?",
                scope.tenant()
            );
            jdbcTemplate.update(
                "delete from modeling_model_release_candidate where tenant_id = ?",
                scope.tenant()
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
            cleanupCompleted = true;
        } finally {
            if (cleanupCompleted) {
                enableAppendOnlyCleanupTriggers();
            }
        }
    }

    private void disableAppendOnlyCleanupTriggers() {
        jdbcTemplate.execute(
            "alter table modeling_materialization_source_pin disable trigger trg_materialization_source_pin_append_only"
        );
        jdbcTemplate.execute(
            "alter table modeling_physical_relation_observation disable trigger trg_physical_relation_observation_append_only"
        );
        jdbcTemplate.execute(
            "alter table modeling_model_release_candidate_command disable trigger trg_model_release_candidate_command_append_only"
        );
    }

    private void enableAppendOnlyCleanupTriggers() {
        jdbcTemplate.execute(
            "alter table modeling_model_release_candidate_command enable trigger trg_model_release_candidate_command_append_only"
        );
        jdbcTemplate.execute(
            "alter table modeling_physical_relation_observation enable trigger trg_physical_relation_observation_append_only"
        );
        jdbcTemplate.execute(
            "alter table modeling_materialization_source_pin enable trigger trg_materialization_source_pin_append_only"
        );
    }

    private Scope scope(String suffix) {
        UUID modelId = UUID.randomUUID();
        Scope scope = new Scope(
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
        trackedScopes.add(scope);
        return scope;
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
