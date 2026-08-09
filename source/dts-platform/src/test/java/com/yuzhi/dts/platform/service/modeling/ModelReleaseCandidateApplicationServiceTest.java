package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventType;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CreateCandidateCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryEvidenceView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.RelationEvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ScopeEntryCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkbenchState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkspaceAction;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelReleaseCandidateApplicationServiceTest {

    private static final String TENANT = "server-tenant";
    private static final String ACTOR = "alice";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");

    @Mock
    private ModelReleaseCandidateRepository repository;

    @Mock
    private ModelReleaseCandidateService commands;

    @Mock
    private ModelMaterializationStartService materializationStarts;

    @Mock
    private ModelSpecPlanWriteAccessPort planAccess;

    @Mock
    private ReleaseDutyResolver dutyResolver;

    @Mock
    private CandidatePublicationAdmissionService publicationAdmission;

    @Mock
    private CandidatePublicationCoordinator publicationCoordinator;

    @Mock
    private CandidateRollbackCommitService rollbackCommits;

    @Mock
    private ReleaseCandidateWorkbenchEvidencePort workbenchEvidence;

    private ModelReleaseCandidateApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ModelReleaseCandidateApplicationService(
            repository,
            commands,
            materializationStarts,
            planAccess,
            dutyResolver,
            publicationAdmission,
            publicationCoordinator,
            rollbackCommits,
            workbenchEvidence
        );
        lenient().when(planAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        lenient()
            .when(dutyResolver.currentDuties())
            .thenReturn(Set.of(DeliveryActorRole.MODEL_MAINTAINER));
    }

    @Test
    void emptyPlanReturnsOneServerOwnedFirstUseState() {
        when(repository.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of());

        var view = service.workspace(TENANT, ACTOR, PLAN_ID);

        assertThat(view.state()).isEqualTo(WorkbenchState.EMPTY);
        assertThat(view.candidate()).isNull();
        assertThat(view.etag()).isNull();
        assertThat(view.allowedActions()).containsExactly(WorkspaceAction.CREATE_CANDIDATE);
        assertThat(view.primaryBlocker().code()).isEqualTo("MODEL_RELEASE_CANDIDATE_REQUIRED");
        assertThat(view.evidence()).hasSize(7);
    }

    @Test
    void emptyDraftExposesScopeEditingButNeverStartBuild() {
        CandidateView candidate = candidate(DeliveryStatus.DRAFT, List.of());
        when(repository.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(candidate));

        var view = service.workspace(TENANT, ACTOR, PLAN_ID);

        assertThat(view.state()).isEqualTo(WorkbenchState.EMPTY);
        assertThat(view.allowedActions()).containsExactly(
            WorkspaceAction.UPDATE_SCOPE,
            WorkspaceAction.CANCEL_CANDIDATE
        );
        assertThat(view.primaryBlocker().code()).isEqualTo(ModelReleaseCandidateContract.SCOPE_EMPTY_ERROR_CODE);
        assertThat(view.etag()).isEqualTo("\"release-candidate:" + CANDIDATE_ID + ":4\"");
    }

    @Test
    void loadedDraftReturnsStableScopeActionsAndStrongEtag() {
        CandidateView candidate = candidate(
            DeliveryStatus.DRAFT,
            List.of(entry(DeliveryStatus.DRAFT))
        );
        when(repository.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(candidate));

        var view = service.workspace(TENANT, ACTOR, PLAN_ID);

        assertThat(view.state()).isEqualTo(WorkbenchState.READY);
        assertThat(view.allowedActions()).containsExactly(
            WorkspaceAction.UPDATE_SCOPE,
            WorkspaceAction.START_BUILD,
            WorkspaceAction.CANCEL_CANDIDATE
        );
        assertThat(view.primaryBlocker()).isNull();
        assertThat(view.etag()).isEqualTo("\"release-candidate:" + CANDIDATE_ID + ":4\"");
    }

    @Test
    void builtCandidateProjectsPersistedRunAndVerifiedRelationEvidence() {
        CandidateView candidate = candidate(
            DeliveryStatus.BUILT,
            List.of(entry(DeliveryStatus.BUILT))
        );
        EntryEvidenceView entryEvidence = new EntryEvidenceView(
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            MODEL_ID,
            "财务项目模型",
            2,
            3,
            "finance.dwd.finance_project",
            "BUILT",
            RelationEvidenceState.VERIFIED,
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            "dts_release_build_postgres_primary",
            "manual__candidate_1",
            1,
            NOW.minusSeconds(30),
            NOW,
            NOW.minusSeconds(1),
            null
        );
        when(repository.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(candidate));
        when(workbenchEvidence.findCurrent(candidate)).thenReturn(List.of(entryEvidence));

        var view = service.workspace(TENANT, ACTOR, PLAN_ID);

        assertThat(view.entryEvidence()).containsExactly(entryEvidence);
		assertThat(view.allowedActions()).containsExactly(WorkspaceAction.RUN_QUALITY, WorkspaceAction.REMATERIALIZE);
        assertThat(view.evidence())
            .filteredOn(summary ->
                summary.type() == com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryEvidenceType.ARTIFACT ||
                summary.type() == com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryEvidenceType.BUILD_RUN
            )
            .allSatisfy(summary -> assertThat(summary.state()).isEqualTo(EvidenceState.PASSED));
    }

    @Test
    void rematerializationSupersedesCreatesAndStartsOneReplacementInOrder() {
        CandidateView built = candidate(DeliveryStatus.BUILT, List.of(entry(DeliveryStatus.BUILT)));
        CandidateView stale = candidateAtVersion(DeliveryStatus.STALE, 5, CANDIDATE_ID);
        UUID replacementId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        CandidateView replacement = candidateAtVersion(DeliveryStatus.DRAFT, 1, replacementId);
        CandidateView building = candidateAtVersion(DeliveryStatus.BUILDING, 2, replacementId);
        CreateCandidateCommand request = new CreateCandidateCommand(
            PLAN_ID,
            "dev",
            List.of(new ScopeEntryCommand(MODEL_ID, 0, "selected in workbench")),
            "rematerialize-root-key",
            "rebuild selected relations"
        );
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(built));
        when(commands.supersedeForRematerialization(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any()))
            .thenReturn(new CommandResult(stale, false, List.of()));
        when(commands.createReplacement(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), eq(5), any()))
            .thenReturn(new CommandResult(replacement, false, List.of()));
        when(materializationStarts.start(eq(TENANT), eq(ACTOR), eq(replacementId), eq(1), any(), any()))
            .thenReturn(new CommandResult(building, false, List.of()));

        CommandResult result = service.rematerialize(
            TENANT,
            ACTOR,
            PLAN_ID,
            CANDIDATE_ID,
            4,
            request
        );

        assertThat(result.candidate()).isEqualTo(building);
        InOrder order = org.mockito.Mockito.inOrder(commands, materializationStarts);
        order.verify(commands).supersedeForRematerialization(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any());
        order.verify(commands).createReplacement(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), eq(5), any());
        order.verify(materializationStarts).start(eq(TENANT), eq(ACTOR), eq(replacementId), eq(1), any(), any());
    }

    @Test
    void materializationStatusReadIsPlanAuthorizedAndBoundedToRequestedModels() {
        List<ModelReleaseCandidateContract.ModelMaterializationStatusView> statuses = List.of();
        when(workbenchEvidence.findLatest(TENANT, PLAN_ID, List.of(MODEL_ID))).thenReturn(statuses);

        assertThat(service.materializationStatuses(TENANT, ACTOR, PLAN_ID, List.of(MODEL_ID))).isSameAs(statuses);

        verify(planAccess).canMaintain(TENANT, PLAN_ID, ACTOR);
        verify(workbenchEvidence).findLatest(TENANT, PLAN_ID, List.of(MODEL_ID));
    }

    @Test
    void oneFailedRelationKeepsAMultiEntryCandidateFromReportingBuildSuccess() {
        UUID secondModelId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        UUID secondEntryId = UUID.fromString("40000000-0000-0000-0000-000000000002");
        CandidateView candidate = candidate(
            DeliveryStatus.BUILD_FAILED,
            List.of(
                entry(DeliveryStatus.BUILD_FAILED),
                new EntryView(
                    secondEntryId,
                    TENANT,
                    CANDIDATE_ID,
                    PLAN_ID,
                    secondModelId,
                    1,
                    "c".repeat(64),
                    null,
                    ImplementationMode.DESIGNER_GENERATED,
                    DeliveryStatus.BUILD_FAILED,
                    1,
                    "dependent model"
                )
            )
        );
        UUID groupId = UUID.fromString("50000000-0000-0000-0000-000000000001");
        List<EntryEvidenceView> evidence = List.of(
            new EntryEvidenceView(
                entry(DeliveryStatus.BUILD_FAILED).id(),
                MODEL_ID,
                "财务项目模型",
                2,
                3,
                "finance.dwd.finance_project",
                "BUILT",
                RelationEvidenceState.VERIFIED,
                groupId,
                UUID.fromString("60000000-0000-0000-0000-000000000001"),
                "dts_release_build_postgres_primary",
                "manual__candidate_1",
                1,
                NOW.minusSeconds(30),
                NOW,
                NOW.minusSeconds(1),
                null
            ),
            new EntryEvidenceView(
                secondEntryId,
                secondModelId,
                "财务项目明细",
                1,
                1,
                "finance.dwd.finance_project_detail",
                "FAILED",
                RelationEvidenceState.FAILED,
                groupId,
                UUID.fromString("60000000-0000-0000-0000-000000000001"),
                "dts_release_build_postgres_primary",
                "manual__candidate_1",
                1,
                NOW.minusSeconds(30),
                NOW,
                NOW.minusSeconds(1),
                "MODEL_PHYSICAL_RELATION_NOT_FOUND"
            )
        );
        when(repository.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(candidate));
        when(commands.detectDrift(TENANT, candidate)).thenReturn(List.of());
        when(workbenchEvidence.findCurrent(candidate)).thenReturn(evidence);

        var view = service.workspace(TENANT, ACTOR, PLAN_ID);

        assertThat(view.evidence())
            .filteredOn(summary ->
                summary.type() ==
                com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryEvidenceType.BUILD_RUN
            )
            .singleElement()
            .satisfies(summary -> {
                assertThat(summary.state()).isEqualTo(EvidenceState.FAILED);
                assertThat(summary.code()).isEqualTo("MODEL_PHYSICAL_RELATION_NOT_FOUND");
            });
    }

    @Test
    void planAuthorizationFailsBeforeCandidateStateIsRead() {
        when(planAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(false);

        assertThatThrownBy(() -> service.workspace(TENANT, ACTOR, PLAN_ID))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).kind())
                    .isEqualTo(ModelReleaseCandidateException.Kind.FORBIDDEN)
            );

        verify(repository, never()).listForWorkbench(TENANT, PLAN_ID);
    }

    @Test
    void ordinaryCreateCannotOpenASecondActiveCandidateForThePlan() {
        CandidateView active = candidate(DeliveryStatus.DRAFT, List.of());
        when(repository.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(active));

        assertThatThrownBy(() ->
            service.create(
                TENANT,
                ACTOR,
                PLAN_ID,
                new CreateCandidateCommand(PLAN_ID, "prod", List.of(), "second-key", "second active candidate")
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo("MODEL_RELEASE_CANDIDATE_ACTIVE_EXISTS")
            );

        verify(commands, never()).create(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void ordinaryCreateReplaysTheOriginalCommandBeforeRejectingAnActiveCandidate() {
        CandidateView active = candidate(DeliveryStatus.DRAFT, List.of());
        CreateCandidateCommand command = new CreateCandidateCommand(
            PLAN_ID,
            "prod",
            List.of(),
            active.idempotencyKey(),
            "prepare candidate"
        );
        CommandResult replay = new CommandResult(active, true, List.of());
        when(repository.findByIdempotencyKey(TENANT, active.idempotencyKey())).thenReturn(Optional.of(active));
        when(commands.create(TENANT, ACTOR, command)).thenReturn(replay);

        assertThat(service.create(TENANT, ACTOR, PLAN_ID, command)).isEqualTo(replay);

        verify(commands).create(TENANT, ACTOR, command);
        verify(repository, never()).listForWorkbench(TENANT, PLAN_ID);
    }

    @Test
    void duplicateActiveCandidatesFailClosedInsteadOfChoosingTheNewest() {
        CandidateView first = candidate(DeliveryStatus.DRAFT, List.of(entry(DeliveryStatus.DRAFT)));
        CandidateView second = new CandidateView(
            UUID.fromString("20000000-0000-0000-0000-000000000002"),
            TENANT,
            PLAN_ID,
            "prod",
            DeliveryStatus.BUILDING,
            2,
            "second-key",
            "c".repeat(64),
            audit(),
            ACTOR,
            NOW.minusSeconds(1),
            List.of(
                new EntryView(
                    UUID.fromString("40000000-0000-0000-0000-000000000002"),
                    TENANT,
                    UUID.fromString("20000000-0000-0000-0000-000000000002"),
                    PLAN_ID,
                    MODEL_ID,
                    2,
                    "b".repeat(64),
                    null,
                    ImplementationMode.DBT_MANAGED,
                    DeliveryStatus.BUILDING,
                    0,
                    "primary"
                )
            )
        );
        when(repository.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.workspace(TENANT, ACTOR, PLAN_ID))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo("MODEL_RELEASE_CANDIDATE_CURRENT_AMBIGUOUS")
            );
    }

    @Test
    void canonicalDriftBlocksTheReadModelBeforeAnotherWriteOccurs() {
        CandidateView candidate = candidate(DeliveryStatus.DRAFT, List.of(entry(DeliveryStatus.DRAFT)));
        when(repository.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(candidate));
        when(commands.detectDrift(TENANT, candidate)).thenReturn(
            List.of(
                new ModelReleaseCandidateContract.DriftReasonView(
                    MODEL_ID,
                    2,
                    "b".repeat(64),
                    3,
                    "c".repeat(64),
                    "MODEL_RELEASE_CANDIDATE_REVISION_DRIFT",
                    "Current revision changed"
                )
            )
        );

        var view = service.workspace(TENANT, ACTOR, PLAN_ID);

        assertThat(view.state()).isEqualTo(WorkbenchState.BLOCKED);
        assertThat(view.primaryBlocker().code()).isEqualTo(ModelReleaseCandidateContract.STALE_ERROR_CODE);
        assertThat(view.allowedActions()).containsExactly(WorkspaceAction.REFRESH_CANDIDATE);
    }

    @Test
    void driftRefreshUsesTheServerOwnedStaleTransition() {
        CandidateView draft = candidate(DeliveryStatus.DRAFT, List.of(entry(DeliveryStatus.DRAFT)));
        CandidateView stale = candidate(DeliveryStatus.STALE, List.of(entry(DeliveryStatus.STALE)));
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(draft));
        when(commands.transition(
            org.mockito.ArgumentMatchers.eq(TENANT),
            org.mockito.ArgumentMatchers.eq(ACTOR),
            org.mockito.ArgumentMatchers.eq(CANDIDATE_ID),
            org.mockito.ArgumentMatchers.any()
        ))
            .thenReturn(new CommandResult(stale, false, List.of()));

        service.refreshDrift(TENANT, ACTOR, PLAN_ID, CANDIDATE_ID, 4, "refresh-key", "confirm drift");

        ArgumentCaptor<TransitionCommand> command = ArgumentCaptor.forClass(TransitionCommand.class);
        verify(commands).transition(
            org.mockito.ArgumentMatchers.eq(TENANT),
            org.mockito.ArgumentMatchers.eq(ACTOR),
            org.mockito.ArgumentMatchers.eq(CANDIDATE_ID),
            command.capture()
        );
        assertThat(command.getValue().targetStatus()).isEqualTo(DeliveryStatus.STALE);
        assertThat(command.getValue().expectedVersion()).isEqualTo(4);
    }

    @Test
    void lockAndRetryChooseServerOwnedTransitions() {
        CandidateView draft = candidate(DeliveryStatus.DRAFT, List.of(entry(DeliveryStatus.DRAFT)));
        CandidateView failed = candidate(DeliveryStatus.BUILD_FAILED, List.of(entry(DeliveryStatus.BUILD_FAILED)));
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(draft), Optional.of(failed));
        CommandResult building = new CommandResult(
            candidate(DeliveryStatus.BUILDING, List.of(entry(DeliveryStatus.BUILDING))),
            false,
            List.of()
        );
        when(materializationStarts.start(TENANT, ACTOR, CANDIDATE_ID, 4, "lock-key", "scope confirmed"))
            .thenReturn(building);
        when(materializationStarts.retry(TENANT, ACTOR, CANDIDATE_ID, 4, "retry-key", "repair complete"))
            .thenReturn(building);

        service.lock(TENANT, ACTOR, PLAN_ID, CANDIDATE_ID, 4, "lock-key", "scope confirmed");
        service.retry(TENANT, ACTOR, PLAN_ID, CANDIDATE_ID, 4, "retry-key", "repair complete");

        verify(materializationStarts).start(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            4,
            "lock-key",
            "scope confirmed"
        );
        verify(materializationStarts).retry(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            4,
            "retry-key",
            "repair complete"
        );
        verify(commands, never()).transition(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void cancelUsesTheServerOwnedTerminalTransition() {
        CandidateView failed = candidate(
            DeliveryStatus.BUILD_FAILED,
            List.of(entry(DeliveryStatus.BUILD_FAILED))
        );
        CandidateView cancelled = candidate(
            DeliveryStatus.CANCELLED,
            List.of(entry(DeliveryStatus.CANCELLED))
        );
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(failed));
        when(commands.transition(
            org.mockito.ArgumentMatchers.eq(TENANT),
            org.mockito.ArgumentMatchers.eq(ACTOR),
            org.mockito.ArgumentMatchers.eq(CANDIDATE_ID),
            org.mockito.ArgumentMatchers.any()
        ))
            .thenReturn(new CommandResult(cancelled, false, List.of()));

        service.cancel(
            TENANT,
            ACTOR,
            PLAN_ID,
            CANDIDATE_ID,
            4,
            "cancel-key",
            "abandon failed build"
        );

        ArgumentCaptor<TransitionCommand> command = ArgumentCaptor.forClass(TransitionCommand.class);
        verify(commands).transition(
            org.mockito.ArgumentMatchers.eq(TENANT),
            org.mockito.ArgumentMatchers.eq(ACTOR),
            org.mockito.ArgumentMatchers.eq(CANDIDATE_ID),
            command.capture()
        );
        assertThat(command.getValue())
            .extracting(
                TransitionCommand::expectedVersion,
                TransitionCommand::targetStatus,
                TransitionCommand::idempotencyKey,
                TransitionCommand::reason
            )
            .containsExactly(4, DeliveryStatus.CANCELLED, "cancel-key", "abandon failed build");
    }

    @Test
    void cancelledEmptyCandidateOffersReplacementWithoutReopeningIt() {
        CandidateView cancelled = candidate(DeliveryStatus.CANCELLED, List.of());
        when(repository.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(cancelled));

        var view = service.workspace(TENANT, ACTOR, PLAN_ID);

        assertThat(view.state()).isEqualTo(WorkbenchState.BLOCKED);
        assertThat(view.primaryBlocker().code()).isEqualTo("MODEL_RELEASE_CANDIDATE_REPLACEMENT_REQUIRED");
        assertThat(view.allowedActions()).containsExactly(WorkspaceAction.CREATE_REPLACEMENT_CANDIDATE);
    }

    @Test
    void retryWithTheSameKeyReachesCommandReplayAfterTheFirstResponseWasLost() {
        CandidateView alreadyBuilding = candidate(
            DeliveryStatus.BUILDING,
            List.of(entry(DeliveryStatus.BUILDING))
        );
        CommandEventView receipt = new CommandEventView(
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            TENANT,
            CANDIDATE_ID,
            PLAN_ID,
            5,
            CommandEventType.STATUS_CHANGED,
            DeliveryStatus.BUILD_FAILED,
            DeliveryStatus.BUILDING,
            ACTOR,
            NOW,
            "repair complete",
            "retry-key",
            "d".repeat(64),
            "{}"
        );
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(alreadyBuilding));
        when(repository.findCommandByIdempotencyKey(TENANT, "retry-key")).thenReturn(Optional.of(receipt));
        when(materializationStarts.retry(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            4,
            "retry-key",
            "repair complete"
        ))
            .thenReturn(new CommandResult(alreadyBuilding, true, List.of()));

        service.retry(TENANT, ACTOR, PLAN_ID, CANDIDATE_ID, 4, "retry-key", "repair complete");

        verify(materializationStarts).retry(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            4,
            "retry-key",
            "repair complete"
        );
        verify(commands, never()).transition(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void retryReplayOfSnapshotDriftReentersTheOriginalBuildRetryCommand() {
        CandidateView stale = candidate(
            DeliveryStatus.STALE,
            List.of(entry(DeliveryStatus.STALE))
        );
        CommandEventView receipt = new CommandEventView(
            UUID.randomUUID(),
            TENANT,
            CANDIDATE_ID,
            PLAN_ID,
            5,
            CommandEventType.STALE_DETECTED,
            DeliveryStatus.BUILD_FAILED,
            DeliveryStatus.STALE,
            ACTOR,
            NOW,
            "implementation drift",
            "retry-drift-key",
            "a".repeat(64),
            "{}"
        );
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(stale));
        when(repository.findCommandByIdempotencyKey(TENANT, "retry-drift-key"))
            .thenReturn(Optional.of(receipt));
        when(materializationStarts.retry(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            4,
            "retry-drift-key",
            "retry failed build"
        ))
            .thenReturn(new CommandResult(stale, true, List.of()));

        service.retry(
            TENANT,
            ACTOR,
            PLAN_ID,
            CANDIDATE_ID,
            4,
            "retry-drift-key",
            "retry failed build"
        );

        verify(materializationStarts).retry(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            4,
            "retry-drift-key",
            "retry failed build"
        );
        verify(commands, never()).transition(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void candidateFromAnotherPlanIsNotDisclosed() {
        UUID anotherPlan = UUID.fromString("10000000-0000-0000-0000-000000000099");
        CandidateView foreignPlanCandidate = new CandidateView(
            CANDIDATE_ID,
            TENANT,
            anotherPlan,
            "prod",
            DeliveryStatus.DRAFT,
            4,
            "create-key",
            "a".repeat(64),
            audit(),
            ACTOR,
            NOW,
            List.of()
        );
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(foreignPlanCandidate));

        assertThatThrownBy(() ->
            service.lock(TENANT, ACTOR, PLAN_ID, CANDIDATE_ID, 4, "lock-key", "scope confirmed")
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).kind())
                    .isEqualTo(ModelReleaseCandidateException.Kind.NOT_FOUND)
            );

        verify(commands, never()).transition(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
        verify(materializationStarts, never()).start(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void reviewerProjectionAndApproveCommandUseTheSameResolvedDuty() {
        DeliveryAuditView submitted = new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(120),
            "submitter",
            NOW.minusSeconds(60),
            null,
            null,
            null,
            null
        );
        CandidateView pending = candidate(
            DeliveryStatus.REVIEW_PENDING,
            List.of(entry(DeliveryStatus.REVIEW_PENDING)),
            submitted
        );
        DeliveryAuditView approvedAudit = new DeliveryAuditView(
            submitted.createdBy(),
            submitted.createdAt(),
            submitted.submittedBy(),
            submitted.submittedAt(),
            ACTOR,
            NOW,
            null,
            null
        );
        CandidateView approved = candidate(
            DeliveryStatus.APPROVED,
            List.of(entry(DeliveryStatus.APPROVED)),
            approvedAudit
        );
        when(dutyResolver.currentDuties()).thenReturn(Set.of(DeliveryActorRole.RELEASE_REVIEWER));
        when(repository.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(pending));
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(pending));
        when(commands.transition(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any()))
            .thenReturn(new CommandResult(approved, false, List.of()));

        var workspace = service.workspace(TENANT, ACTOR, PLAN_ID);
        var result = service.approve(
            TENANT,
            ACTOR,
            PLAN_ID,
            CANDIDATE_ID,
            4,
            "approve-key",
            "quality evidence accepted"
        );

        assertThat(workspace.allowedActions()).containsExactly(WorkspaceAction.APPROVE, WorkspaceAction.REJECT);
        assertThat(result.allowedActions()).isEmpty();
        ArgumentCaptor<TransitionCommand> transition = ArgumentCaptor.forClass(TransitionCommand.class);
        verify(commands).transition(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), transition.capture());
        assertThat(transition.getValue().targetStatus()).isEqualTo(DeliveryStatus.APPROVED);
    }

    @Test
    void maintainerCannotExecuteReviewerCommandEvenWithPlanMaintenanceAccess() {
        DeliveryAuditView submitted = new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(120),
            "submitter",
            NOW.minusSeconds(60),
            null,
            null,
            null,
            null
        );
        CandidateView pending = candidate(
            DeliveryStatus.REVIEW_PENDING,
            List.of(entry(DeliveryStatus.REVIEW_PENDING)),
            submitted
        );
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() ->
            service.approve(
                TENANT,
                ACTOR,
                PLAN_ID,
                CANDIDATE_ID,
                4,
                "approve-key",
                "attempt privilege escalation"
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).kind())
                    .isEqualTo(ModelReleaseCandidateException.Kind.FORBIDDEN)
            );

        verify(commands, never()).transition(any(), any(), any(), any());
    }

    @Test
    void noResolvedReleaseDutyCannotReachAnyPublicationMutation() {
        when(dutyResolver.currentDuties()).thenReturn(Set.of());

        assertThatThrownBy(() ->
            service.publish(
                TENANT,
                ACTOR,
                PLAN_ID,
                CANDIDATE_ID,
                4,
                "publish-key",
                "publish approved release"
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).kind())
                    .isEqualTo(ModelReleaseCandidateException.Kind.FORBIDDEN)
            );
        assertThatThrownBy(() ->
            service.retryPublication(
                TENANT,
                ACTOR,
                PLAN_ID,
                CANDIDATE_ID,
                4,
                "registration-retry-key",
                "retry local publication"
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).kind())
                    .isEqualTo(ModelReleaseCandidateException.Kind.FORBIDDEN)
            );
        assertThatThrownBy(() ->
            service.rollback(
                TENANT,
                ACTOR,
                PLAN_ID,
                CANDIDATE_ID,
                4,
                "rollback-key",
                "withdraw release"
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).kind())
                    .isEqualTo(ModelReleaseCandidateException.Kind.FORBIDDEN)
            );

        verify(repository, never()).find(any(), any());
        verifyNoInteractions(publicationAdmission, publicationCoordinator, rollbackCommits);
    }

    @Test
    void publishAppliesAssetGateBeforePublishingTransitionAndLocalCommit() {
        DeliveryAuditView approvedAudit = new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(180),
            "submitter",
            NOW.minusSeconds(120),
            "reviewer",
            NOW.minusSeconds(60),
            null,
            null
        );
        CandidateView approved = candidate(
            DeliveryStatus.APPROVED,
            List.of(entry(DeliveryStatus.APPROVED)),
            approvedAudit
        );
        CandidateView publishing = candidate(
            DeliveryStatus.PUBLISHING,
            List.of(entry(DeliveryStatus.PUBLISHING)),
            approvedAudit
        );
        DeliveryAuditView publishedAudit = new DeliveryAuditView(
            approvedAudit.createdBy(),
            approvedAudit.createdAt(),
            approvedAudit.submittedBy(),
            approvedAudit.submittedAt(),
            approvedAudit.approvedBy(),
            approvedAudit.approvedAt(),
            ACTOR,
            NOW
        );
        CandidateView published = candidate(
            DeliveryStatus.PUBLISHED,
            List.of(entry(DeliveryStatus.PUBLISHED)),
            publishedAudit
        );
        when(dutyResolver.currentDuties()).thenReturn(Set.of(DeliveryActorRole.RELEASE_OPERATOR));
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(approved));
        when(commands.transition(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any()))
            .thenReturn(new CommandResult(publishing, false, List.of()));
        when(
            publicationCoordinator.publish(
                TENANT,
                ACTOR,
                publishing,
                "publish-key",
                "publish approved release"
            )
        )
            .thenReturn(new CommandResult(published, false, List.of()));

        CommandResult result = service.publish(
            TENANT,
            ACTOR,
            PLAN_ID,
            CANDIDATE_ID,
            4,
            "publish-key",
            "publish approved release"
        );

        assertThat(result.candidate().status()).isEqualTo(DeliveryStatus.PUBLISHED);
        InOrder order = org.mockito.Mockito.inOrder(publicationAdmission, commands, publicationCoordinator);
        order.verify(publicationAdmission).requireAllowed(approved);
        order.verify(commands).transition(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any());
        order
            .verify(publicationCoordinator)
            .publish(TENANT, ACTOR, publishing, "publish-key", "publish approved release");
    }

    @Test
    void deniedAssetGateLeavesApprovedCandidateUntouched() {
        DeliveryAuditView approvedAudit = new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(180),
            "submitter",
            NOW.minusSeconds(120),
            "reviewer",
            NOW.minusSeconds(60),
            null,
            null
        );
        CandidateView approved = candidate(
            DeliveryStatus.APPROVED,
            List.of(entry(DeliveryStatus.APPROVED)),
            approvedAudit
        );
        when(dutyResolver.currentDuties()).thenReturn(Set.of(DeliveryActorRole.RELEASE_OPERATOR));
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(approved));
        when(publicationAdmission.requireAllowed(approved))
            .thenThrow(
                new ModelReleaseCandidateException(
                    "MODEL_RELEASE_ASSET_ACTION_FORBIDDEN",
                    "denied",
                    ModelReleaseCandidateException.Kind.FORBIDDEN
                )
            );

        assertThatThrownBy(() ->
            service.publish(
                TENANT,
                ACTOR,
                PLAN_ID,
                CANDIDATE_ID,
                4,
                "publish-key",
                "publish approved release"
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo("MODEL_RELEASE_ASSET_ACTION_FORBIDDEN")
            );

        verify(commands, never()).transition(any(), any(), any(), any());
        verify(publicationCoordinator, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void publishReplayReturnsCurrentPublishedProjectionWithoutRepeatingLocalWrites() {
        DeliveryAuditView publishedAudit = new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(180),
            "submitter",
            NOW.minusSeconds(120),
            "reviewer",
            NOW.minusSeconds(60),
            ACTOR,
            NOW
        );
        CandidateView published = candidate(
            DeliveryStatus.PUBLISHED,
            List.of(entry(DeliveryStatus.PUBLISHED)),
            publishedAudit
        );
        CommandEventView receipt = new CommandEventView(
            UUID.fromString("70000000-0000-0000-0000-000000000001"),
            TENANT,
            CANDIDATE_ID,
            PLAN_ID,
            5,
            CommandEventType.STATUS_CHANGED,
            DeliveryStatus.APPROVED,
            DeliveryStatus.PUBLISHING,
            ACTOR,
            NOW.minusSeconds(30),
            "publish approved release",
            "publish-key",
            "d".repeat(64),
            "{}"
        );
        when(dutyResolver.currentDuties()).thenReturn(Set.of(DeliveryActorRole.RELEASE_OPERATOR));
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(published));
        when(repository.findCommandByIdempotencyKey(TENANT, "publish-key")).thenReturn(Optional.of(receipt));
        when(commands.transition(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any()))
            .thenReturn(new CommandResult(published, true, List.of()));

        CommandResult replay = service.publish(
            TENANT,
            ACTOR,
            PLAN_ID,
            CANDIDATE_ID,
            4,
            "publish-key",
            "publish approved release"
        );

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.candidate()).isEqualTo(published);
        verify(publicationAdmission, never()).requireAllowed(any());
        verify(publicationCoordinator, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void registrationRetryConsumesTheSamePhysicalEvidenceWithoutCallingBuildStart() {
        DeliveryAuditView partialAudit = new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(180),
            "submitter",
            NOW.minusSeconds(120),
            "reviewer",
            NOW.minusSeconds(60),
            ACTOR,
            NOW.minusSeconds(30)
        );
        CandidateView partial = candidate(
            DeliveryStatus.PARTIAL,
            List.of(entry(DeliveryStatus.PARTIAL)),
            partialAudit
        );
        CandidateView published = candidate(
            DeliveryStatus.PUBLISHED,
            List.of(entry(DeliveryStatus.PUBLISHED)),
            new DeliveryAuditView(
                partialAudit.createdBy(),
                partialAudit.createdAt(),
                partialAudit.submittedBy(),
                partialAudit.submittedAt(),
                partialAudit.approvedBy(),
                partialAudit.approvedAt(),
                partialAudit.publishedBy(),
                partialAudit.publishedAt()
            )
        );
        when(dutyResolver.currentDuties()).thenReturn(Set.of(DeliveryActorRole.RELEASE_OPERATOR));
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(partial));
        when(
            publicationCoordinator.publish(
                TENANT,
                ACTOR,
                partial,
                "registration-retry-key",
                "retry local publication"
            )
        )
            .thenReturn(new CommandResult(published, false, List.of()));

        CommandResult result = service.retryPublication(
            TENANT,
            ACTOR,
            PLAN_ID,
            CANDIDATE_ID,
            4,
            "registration-retry-key",
            "retry local publication"
        );

        assertThat(result.candidate().status()).isEqualTo(DeliveryStatus.PUBLISHED);
        InOrder order = org.mockito.Mockito.inOrder(publicationAdmission, publicationCoordinator);
        order.verify(publicationAdmission).requireAllowed(partial);
        order
            .verify(publicationCoordinator)
            .publish(TENANT, ACTOR, partial, "registration-retry-key", "retry local publication");
        verify(materializationStarts, never()).start(any(), any(), any(), anyInt(), any(), any());
        verify(materializationStarts, never()).retry(any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void rollbackUsesTheAssetGateAndCandidateAtomicRollbackInsteadOfLegacyLifecycleMutation() {
        DeliveryAuditView publishedAudit = new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(180),
            "submitter",
            NOW.minusSeconds(120),
            "reviewer",
            NOW.minusSeconds(60),
            ACTOR,
            NOW.minusSeconds(30)
        );
        CandidateView published = candidate(
            DeliveryStatus.PUBLISHED,
            List.of(entry(DeliveryStatus.PUBLISHED)),
            publishedAudit
        );
        CandidateView rolledBack = candidate(
            DeliveryStatus.ROLLED_BACK,
            List.of(entry(DeliveryStatus.ROLLED_BACK)),
            publishedAudit
        );
        when(dutyResolver.currentDuties()).thenReturn(Set.of(DeliveryActorRole.RELEASE_OPERATOR));
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(published));
        when(
            rollbackCommits.rollback(
                TENANT,
                ACTOR,
                published,
                "rollback-key",
                "withdraw release"
            )
        )
            .thenReturn(new CommandResult(rolledBack, false, List.of()));

        CommandResult result = service.rollback(
            TENANT,
            ACTOR,
            PLAN_ID,
            CANDIDATE_ID,
            4,
            "rollback-key",
            "withdraw release"
        );

        assertThat(result.candidate().status()).isEqualTo(DeliveryStatus.ROLLED_BACK);
        InOrder order = org.mockito.Mockito.inOrder(publicationAdmission, rollbackCommits);
        order.verify(publicationAdmission).requireArchiveAllowed(published);
        order.verify(rollbackCommits).rollback(TENANT, ACTOR, published, "rollback-key", "withdraw release");
    }

    private static CandidateView candidate(DeliveryStatus status, List<EntryView> entries) {
        return candidate(status, entries, audit());
    }

    private static CandidateView candidate(
        DeliveryStatus status,
        List<EntryView> entries,
        DeliveryAuditView audit
    ) {
        return new CandidateView(
            CANDIDATE_ID,
            TENANT,
            PLAN_ID,
            "prod",
            status,
            4,
            "create-key",
            "a".repeat(64),
            audit,
            ACTOR,
            NOW,
            entries
        );
    }

    private static CandidateView candidateAtVersion(DeliveryStatus status, int version, UUID candidateId) {
        EntryView item = new EntryView(
            UUID.randomUUID(),
            TENANT,
            candidateId,
            PLAN_ID,
            MODEL_ID,
            2,
            "b".repeat(64),
            null,
            ImplementationMode.DBT_MANAGED,
            status,
            0,
            "primary"
        );
        return new CandidateView(
            candidateId,
            TENANT,
            PLAN_ID,
            "dev",
            status,
            version,
            "candidate-key-" + candidateId,
            "c".repeat(64),
            audit(),
            ACTOR,
            NOW.plusSeconds(version),
            List.of(item)
        );
    }

    private static EntryView entry(DeliveryStatus status) {
        return new EntryView(
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            TENANT,
            CANDIDATE_ID,
            PLAN_ID,
            MODEL_ID,
            2,
            "b".repeat(64),
            null,
            ImplementationMode.DBT_MANAGED,
            status,
            0,
            "primary"
        );
    }

    private static DeliveryAuditView audit() {
        return new DeliveryAuditView(ACTOR, NOW.minusSeconds(60), null, null, null, null, null, null);
    }
}
