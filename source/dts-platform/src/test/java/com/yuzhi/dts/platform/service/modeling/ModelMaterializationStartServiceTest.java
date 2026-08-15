package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelMaterializationStartServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String ACTOR = "builder-a";
    private static final UUID CANDIDATE_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    @Mock
    private ModelReleaseCandidateService candidateCommands;

    @Mock
    private ModelMaterializationBuildRepository builds;

    @Mock
    private ModelMaterializationSourceAvailabilityGuard sourceAvailability;

    @Mock
    private ModelMaterializationAvailabilityAuditService availabilityAudit;

    private ModelMaterializationStartService service;

    @Test
    void executableCandidateBundleExcludesNonExecutableDbtMetadataArtifacts() throws IOException {
        String source = Files.readString(
            Path.of("src/main/java/com/yuzhi/dts/platform/repository/modeling/ModelMaterializationBuildRepository.java")
        );
        int from = source.indexOf("private List<ArtifactRow> loadArtifacts(");
        int to = source.indexOf("private void validateInputs(", from);

        assertThat(from).isGreaterThanOrEqualTo(0);
        assertThat(to).isGreaterThan(from);
        assertThat(source.substring(from, to))
            .contains("a.status = 'COMPILED'")
            .contains("a.status = 'IMPORTED'")
            .contains("a.node_kind in ('STG', 'EPHEMERAL')")
            .contains("lower(a.path) like 'models/%.sql'")
            .contains("a.artifact_type in ('SQL', 'TEST', 'STG_SQL')")
            .contains("a.artifact_type = 'SCHEMA'")
            .contains("lower(a.path) like '%.yml'")
            .contains("lower(a.path) like '%.yaml'")
            .doesNotContain("a.artifact_type in ('SQL', 'SCHEMA', 'TEST', 'STG_SQL')");

        int dispatchFrom = source.indexOf("private CandidateBuildEntry loadCandidateBuildEntry(");
        int dispatchTo = source.indexOf("private ExecutionTarget executionTarget()", dispatchFrom);
        assertThat(dispatchFrom).isGreaterThanOrEqualTo(0);
        assertThat(dispatchTo).isGreaterThan(dispatchFrom);
        assertThat(source.substring(dispatchFrom, dispatchTo))
            .contains("status = 'COMPILED'")
            .contains("status = 'IMPORTED'")
            .contains("node_kind in ('STG', 'EPHEMERAL')")
            .contains("lower(path) like 'models/%.sql'")
            .contains("artifact_type in ('SQL', 'TEST', 'STG_SQL')")
            .contains("artifact_type = 'SCHEMA'")
            .contains("lower(path) like '%.yml'")
            .contains("lower(path) like '%.yaml'")
            .doesNotContain("artifact_type in ('SQL', 'SCHEMA', 'TEST', 'STG_SQL')");
    }

    @Test
    void retryAcceptsOnlyReconciledFailureOrDeterministicLocalBlock() throws IOException {
        String source = Files.readString(
            Path.of("src/main/java/com/yuzhi/dts/platform/repository/modeling/ModelMaterializationBuildRepository.java")
        );
        int retryFrom = source.indexOf("public QueuedBuildGroup createRetryQueuedBuild(");
        int retryTo = source.indexOf("public QueuedBuildGroup createRematerializationQueuedBuild(", retryFrom);

        assertThat(retryFrom).isGreaterThanOrEqualTo(0);
        assertThat(retryTo).isGreaterThan(retryFrom);
        assertThat(source.substring(retryFrom, retryTo))
            .contains("Set.of(\"FAILED\", \"BLOCKED\")")
            .doesNotContain("UNKNOWN")
            .doesNotContain("SUBMITTED");

        int entriesFrom = source.indexOf("private List<RetryEntryRow> lockRetryEntries(");
        int entriesTo = source.indexOf("private static void requireRetrySnapshotCurrent(", entriesFrom);
        assertThat(entriesFrom).isGreaterThanOrEqualTo(0);
        assertThat(entriesTo).isGreaterThan(entriesFrom);
        assertThat(source.substring(entriesFrom, entriesTo))
            .contains("'BLOCKED'")
            .doesNotContain("'UNKNOWN'")
            .doesNotContain("'SUBMITTED'");
    }

    @Test
    void candidateBuildScopePinsLogicalColumnsFromTheExactModelRevision()
        throws IOException {
        String source = Files.readString(
            Path.of("src/main/java/com/yuzhi/dts/platform/repository/modeling/ModelMaterializationBuildRepository.java")
        );
        int scopeFrom = source.indexOf("public CandidateBuildScope loadCandidateBuildScope(");
        int scopeTo = source.indexOf("private List<BuildEntryRow> lockCurrentBuildEntries(", scopeFrom);

        assertThat(scopeFrom).isGreaterThanOrEqualTo(0);
        assertThat(scopeTo).isGreaterThan(scopeFrom);
        assertThat(source.substring(scopeFrom, scopeTo))
            .contains("join modeling_model_spec_revision sr")
            .contains("sr.snapshot_json -> 'fields' as model_fields");
        assertThat(source)
            .contains("pinnedModelColumns(")
            .contains("row.modelFieldsJson()")
            .contains("row.modelSpecId()");
    }

    @BeforeEach
    void setUp() {
        service = new ModelMaterializationStartService(
            candidateCommands,
            builds,
            sourceAvailability,
            availabilityAudit,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void queuesOneAtomicBuildAfterTheCanonicalBuildingTransition() {
        CandidateView building = candidate(DeliveryStatus.BUILDING);
        when(candidateCommands.transition(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any()))
            .thenReturn(new CommandResult(building, false, List.of()));
        when(builds.createQueuedBuild(building, NOW))
            .thenReturn(mock(ModelMaterializationBuildRepository.QueuedBuildGroup.class));

        CommandResult result = service.start(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            4,
            "build-key",
            "start build"
        );

        assertThat(result.candidate()).isSameAs(building);
        ArgumentCaptor<TransitionCommand> command =
            ArgumentCaptor.forClass(TransitionCommand.class);
        verify(candidateCommands).transition(
            eq(TENANT),
            eq(ACTOR),
            eq(CANDIDATE_ID),
            command.capture()
        );
        assertThat(command.getValue().expectedVersion()).isEqualTo(4);
        assertThat(command.getValue().targetStatus()).isEqualTo(DeliveryStatus.BUILDING);
        verify(builds).createQueuedBuild(building, NOW);
        verify(sourceAvailability).requireCandidateCurrent(TENANT, CANDIDATE_ID, 5);
        verify(builds, never()).requireQueuedBuild(any());
    }

    @Test
    void replayRequiresTheOriginallyCommittedPipelineRowsWithoutCreatingMore() {
        CandidateView building = candidate(DeliveryStatus.BUILDING);
        when(candidateCommands.transition(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any()))
            .thenReturn(new CommandResult(building, true, List.of()));
        when(builds.requireQueuedBuild(building))
            .thenReturn(mock(ModelMaterializationBuildRepository.QueuedBuildGroup.class));

        service.start(TENANT, ACTOR, CANDIDATE_ID, 4, "build-key", "start build");

        verify(builds).requireQueuedBuild(building);
        verify(builds, never()).createQueuedBuild(any(), any());
    }

    @Test
    void retryQueuesTheNextAttemptAfterTheCanonicalBuildingTransition() {
        CandidateView building = candidate(DeliveryStatus.BUILDING);
        when(candidateCommands.transition(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any()))
            .thenReturn(new CommandResult(building, false, List.of()));
        when(builds.createRetryQueuedBuild(building, NOW))
            .thenReturn(mock(ModelMaterializationBuildRepository.QueuedBuildGroup.class));

        CommandResult result = service.retry(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            4,
            "retry-key",
            "retry failed build"
        );

        assertThat(result.candidate()).isSameAs(building);
        ArgumentCaptor<TransitionCommand> command =
            ArgumentCaptor.forClass(TransitionCommand.class);
        verify(candidateCommands).transition(
            eq(TENANT),
            eq(ACTOR),
            eq(CANDIDATE_ID),
            command.capture()
        );
        assertThat(command.getValue().targetStatus()).isEqualTo(DeliveryStatus.BUILDING);
        verify(builds).createRetryQueuedBuild(building, NOW);
        verify(builds, never()).createQueuedBuild(any(), any());
        verify(builds, never()).requireQueuedBuild(any());
    }

    @Test
    void retryReplayRequiresTheCommittedNextAttemptWithoutCreatingAnother() {
        CandidateView building = candidate(DeliveryStatus.BUILDING);
        when(candidateCommands.transition(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any()))
            .thenReturn(new CommandResult(building, true, List.of()));
        when(builds.requireQueuedBuild(building))
            .thenReturn(mock(ModelMaterializationBuildRepository.QueuedBuildGroup.class));

        service.retry(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            4,
            "retry-key",
            "retry failed build"
        );

        verify(builds).requireQueuedBuild(building);
        verify(builds, never()).createRetryQueuedBuild(any(), any());
        verify(builds, never()).createQueuedBuild(any(), any());
    }

    @Test
    void rematerializationQueuesTheNextAttemptWithoutReplacingTheCandidate() {
        CandidateView building = candidate(DeliveryStatus.BUILDING);
        when(candidateCommands.transition(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any()))
            .thenReturn(new CommandResult(building, false, List.of()));
        when(builds.createRematerializationQueuedBuild(building, NOW))
            .thenReturn(mock(ModelMaterializationBuildRepository.QueuedBuildGroup.class));

        CommandResult result = service.rematerialize(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            4,
            "rematerialize-key",
            "rebuild immutable revision"
        );

        assertThat(result.candidate()).isSameAs(building);
        verify(builds).createRematerializationQueuedBuild(building, NOW);
        verify(builds, never()).createRetryQueuedBuild(any(), any());
    }

    @Test
    void canonicalDriftToStaleCreatesNoPipelineRun() {
        CandidateView stale = candidate(DeliveryStatus.STALE);
        when(candidateCommands.transition(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any()))
            .thenReturn(new CommandResult(stale, false, List.of()));

        CommandResult result = service.start(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            4,
            "build-key",
            "start build"
        );

        assertThat(result.candidate().status()).isEqualTo(DeliveryStatus.STALE);
        verify(builds, never()).createQueuedBuild(any(), any());
        verify(builds, never()).requireQueuedBuild(any());
    }

    @Test
    void availabilityFenceRollsTheTransitionBackBeforeTheBuildSnapshot() {
        CandidateView building = candidate(DeliveryStatus.BUILDING);
        when(candidateCommands.transition(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any()))
            .thenReturn(new CommandResult(building, false, List.of()));
        doThrow(
            new ModelReleaseCandidateException(
                ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE,
                "source fenced",
                ModelReleaseCandidateException.Kind.UNPROCESSABLE
            )
        ).when(sourceAvailability).requireCandidateCurrent(TENANT, CANDIDATE_ID, 5);

        assertThatThrownBy(() -> service.start(TENANT, ACTOR, CANDIDATE_ID, 4, "build-key", "start build"))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .extracting(error -> ((ModelReleaseCandidateException) error).code())
            .isEqualTo(ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE);

        verify(candidateCommands).transition(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any());
        verify(availabilityAudit).recordStartDenied(
            ACTOR,
            CANDIDATE_ID,
            5,
            ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE
        );
        verify(builds, never()).createQueuedBuild(any(), any());
    }

    private static CandidateView candidate(DeliveryStatus status) {
        UUID planId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        return new CandidateView(
            CANDIDATE_ID,
            TENANT,
            planId,
            "PROD",
            status,
            5,
            "candidate-key",
            "a".repeat(64),
            new DeliveryAuditView(ACTOR, NOW.minusSeconds(60), null, null, null, null, null, null),
            ACTOR,
            NOW,
            List.of(
                new EntryView(
                    UUID.fromString("40000000-0000-0000-0000-000000000001"),
                    TENANT,
                    CANDIDATE_ID,
                    planId,
                    modelId,
                    1,
                    "b".repeat(64),
                    null,
                    ImplementationMode.DESIGNER_GENERATED,
                    status,
                    0,
                    "primary"
                )
            )
        );
    }
}
