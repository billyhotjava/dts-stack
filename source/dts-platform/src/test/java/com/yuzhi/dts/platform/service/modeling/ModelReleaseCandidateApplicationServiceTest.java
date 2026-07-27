package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventType;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CreateCandidateCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ScopeEntryCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkbenchState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkspaceAction;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    private ModelReleaseCandidateApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ModelReleaseCandidateApplicationService(
            repository,
            commands,
            materializationStarts,
            planAccess
        );
        when(planAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
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
        assertThat(view.allowedActions()).containsExactly(WorkspaceAction.UPDATE_SCOPE);
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
            WorkspaceAction.START_BUILD
        );
        assertThat(view.primaryBlocker()).isNull();
        assertThat(view.etag()).isEqualTo("\"release-candidate:" + CANDIDATE_ID + ":4\"");
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

        assertThat(service.create(TENANT, ACTOR, PLAN_ID, command)).isSameAs(replay);

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
        when(commands.transition(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new CommandResult(
                candidate(DeliveryStatus.BUILDING, List.of(entry(DeliveryStatus.BUILDING))),
                false,
                List.of()
            ));

        service.lock(TENANT, ACTOR, PLAN_ID, CANDIDATE_ID, 4, "lock-key", "scope confirmed");
        service.retry(TENANT, ACTOR, PLAN_ID, CANDIDATE_ID, 4, "retry-key", "repair complete");

        ArgumentCaptor<TransitionCommand> commandsCaptor = ArgumentCaptor.forClass(TransitionCommand.class);
        verify(materializationStarts).start(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            4,
            "lock-key",
            "scope confirmed"
        );
        verify(commands).transition(
            org.mockito.ArgumentMatchers.eq(TENANT),
            org.mockito.ArgumentMatchers.eq(ACTOR),
            org.mockito.ArgumentMatchers.eq(CANDIDATE_ID),
            commandsCaptor.capture()
        );
        assertThat(commandsCaptor.getValue().targetStatus()).isEqualTo(DeliveryStatus.BUILDING);
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
        when(commands.transition(
            org.mockito.ArgumentMatchers.eq(TENANT),
            org.mockito.ArgumentMatchers.eq(ACTOR),
            org.mockito.ArgumentMatchers.eq(CANDIDATE_ID),
            org.mockito.ArgumentMatchers.any()
        ))
            .thenReturn(new CommandResult(alreadyBuilding, true, List.of()));

        service.retry(TENANT, ACTOR, PLAN_ID, CANDIDATE_ID, 4, "retry-key", "repair complete");

        ArgumentCaptor<TransitionCommand> command = ArgumentCaptor.forClass(TransitionCommand.class);
        verify(commands).transition(
            org.mockito.ArgumentMatchers.eq(TENANT),
            org.mockito.ArgumentMatchers.eq(ACTOR),
            org.mockito.ArgumentMatchers.eq(CANDIDATE_ID),
            command.capture()
        );
        assertThat(command.getValue().targetStatus()).isEqualTo(DeliveryStatus.BUILDING);
        assertThat(command.getValue().expectedVersion()).isEqualTo(4);
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

    private static CandidateView candidate(DeliveryStatus status, List<EntryView> entries) {
        return new CandidateView(
            CANDIDATE_ID,
            TENANT,
            PLAN_ID,
            "prod",
            status,
            4,
            "create-key",
            "a".repeat(64),
            audit(),
            ACTOR,
            NOW,
            entries
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
