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
import com.yuzhi.dts.platform.service.ops.WarehousePlanOperationsReadPort;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Action;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.DependencyRole;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.OrderedEntry;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Preview;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventType;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CreateCandidateCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryEvidenceView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.GovernanceQualitySummaryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.RelationEvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.MaterializationAttemptView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ScopeEntryCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkbenchState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkspaceAction;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.QualityEvidencePort.QualityEvidence;
import java.time.Instant;
import java.util.Arrays;
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
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class ModelReleaseCandidateApplicationServiceTest {

    private static final String TENANT = "server-tenant";
    private static final String ACTOR = "alice";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");

    @Test
    void planChecksumBoundCandidateWritesShareOneTransactionBoundary() {
        assertThat(
            Arrays
                .stream(ModelReleaseCandidateApplicationService.class.getDeclaredMethods())
                .filter(method ->
                    Set.of("create", "createReplacement", "rematerialize").contains(method.getName())
                )
        )
            .isNotEmpty()
            .allSatisfy(method -> assertThat(method.getAnnotation(Transactional.class)).isNotNull());
    }

    @Mock
    private ModelReleaseCandidateRepository repository;

    @Mock
    private ModelReleaseCandidateService commands;

    @Mock
    private ModelMaterializationStartService materializationStarts;

    @Mock
    private ModelSpecPlanWriteAccessPort planAccess;

    @Mock
    private WarehousePlanOperationsReadPort planReadAccess;

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

    @Mock
    private ModelMaterializationPlanService materializationPlans;

    @Mock
    private ModelReleaseCandidatePreflightService preflight;

    @Mock
    private CandidateGovernanceQualityEvidenceService governanceQuality;

    private ModelReleaseCandidateApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ModelReleaseCandidateApplicationService(
            repository,
            commands,
            materializationStarts,
            planAccess,
            planReadAccess,
            dutyResolver,
            publicationAdmission,
            publicationCoordinator,
            rollbackCommits,
            workbenchEvidence,
            materializationPlans,
            preflight,
            governanceQuality
        );
        lenient().when(planAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(true);
        lenient().when(planReadAccess.canReadPlan(PLAN_ID)).thenReturn(true);
        lenient()
            .when(dutyResolver.currentDuties())
            .thenReturn(Set.of(DeliveryActorRole.MODEL_MAINTAINER));
        lenient()
            .when(preflight.requireEligible(eq(TENANT), any(CreateCandidateCommand.class)))
            .thenAnswer(invocation -> {
                CreateCandidateCommand command = invocation.getArgument(1);
                return new ModelReleaseCandidatePreflightService.PreflightResult(null, command);
            });
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
        assertThat(view.allowedActions()).containsExactly(
            WorkspaceAction.RUN_QUALITY,
            WorkspaceAction.CANCEL_CANDIDATE,
            WorkspaceAction.REMATERIALIZE
        );
        assertThat(view.evidence())
            .filteredOn(summary ->
                summary.type() == com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryEvidenceType.ARTIFACT ||
                summary.type() == com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryEvidenceType.BUILD_RUN
            )
            .allSatisfy(summary -> assertThat(summary.state()).isEqualTo(EvidenceState.PASSED));
    }

    @Test
    void rematerializationCreatesANewAttemptOnTheSameImmutableCandidate() {
        CandidateView built = candidate(DeliveryStatus.BUILT, List.of(entry(DeliveryStatus.BUILT)));
        CandidateView building = candidateAtVersion(DeliveryStatus.BUILDING, 5, CANDIDATE_ID);
        CreateCandidateCommand request = new CreateCandidateCommand(
            PLAN_ID,
            "prod",
            List.of(new ScopeEntryCommand(MODEL_ID, 0, "selected in workbench")),
            "rematerialize-root-key",
            "rebuild selected relations"
        );
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(built));
        when(
            materializationStarts.rematerialize(
                eq(TENANT),
                eq(ACTOR),
                eq(CANDIDATE_ID),
                eq(4),
                any(),
                any(),
                eq(List.of(MODEL_ID))
            )
        )
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
        verify(materializationStarts).rematerialize(
            eq(TENANT),
            eq(ACTOR),
            eq(CANDIDATE_ID),
            eq(4),
            eq("rematerialize-root-key"),
            eq("rebuild selected relations"),
            eq(List.of(MODEL_ID))
        );
        verify(commands, never()).createReplacement(any(), any(), any(), anyInt(), any());
    }

    @Test
    void rematerializationRejectsScopeChangesBecauseCandidateRevisionsAreImmutable() {
        UUID otherModelId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        CandidateView built = candidate(DeliveryStatus.BUILT, List.of(entry(DeliveryStatus.BUILT)));
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(built));
        CreateCandidateCommand request = new CreateCandidateCommand(
            PLAN_ID,
            "dev",
            List.of(new ScopeEntryCommand(otherModelId, 0, "selected")),
            "rematerialize-root-key",
            "rebuild selected relations"
        );

        assertThatThrownBy(() -> service.rematerialize(TENANT, ACTOR, PLAN_ID, CANDIDATE_ID, 4, request))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo("MODEL_RELEASE_REMATERIALIZATION_SCOPE_MISMATCH")
            );

        verifyNoInteractions(materializationStarts);
    }

    @Test
    void checksumBoundRematerializationKeepsCandidateIdentityButQueuesOnlyBuildNodes() {
        UUID upstreamId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        EntryView upstream = new EntryView(
            UUID.fromString("40000000-0000-0000-0000-000000000002"),
            TENANT,
            CANDIDATE_ID,
            PLAN_ID,
            upstreamId,
            2,
            "c".repeat(64),
            null,
            ImplementationMode.DBT_MANAGED,
            DeliveryStatus.BUILT,
            0,
            "AUTO_DEPENDENCY"
        );
        EntryView root = new EntryView(
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            TENANT,
            CANDIDATE_ID,
            PLAN_ID,
            MODEL_ID,
            2,
            "b".repeat(64),
            null,
            ImplementationMode.DBT_MANAGED,
            DeliveryStatus.BUILT,
            1,
            "MATERIALIZATION_ROOT"
        );
        CandidateView built = candidate(DeliveryStatus.BUILT, List.of(upstream, root));
        CandidateView building = candidateAtVersion(DeliveryStatus.BUILDING, 5, CANDIDATE_ID);
        CreateCandidateCommand request = new CreateCandidateCommand(
            PLAN_ID,
            "prod",
            List.of(new ScopeEntryCommand(MODEL_ID, 0, "selected root")),
            "planned-rematerialization-key",
            "rebuild only the requested relation"
        );
        String checksum = "f".repeat(64);
        Preview preview = new Preview(
            PLAN_ID,
            "prod",
            ModelMaterializationPlanContract.Strategy.WITH_MISSING_UPSTREAMS,
            checksum,
            true,
            List.of(MODEL_ID),
            List.of(
                new OrderedEntry(
                    upstreamId,
                    "published upstream",
                    2,
                    "c".repeat(64),
                    1,
                    "d".repeat(64),
                    "e".repeat(64),
                    Layer.DWS,
                    DependencyRole.UPSTREAM,
                    0,
                    Action.REUSE,
                    "EXACT_VERIFIED_RELATION",
                    UUID.fromString("50000000-0000-0000-0000-000000000001"),
                    "warehouse.public.dws_upstream"
                ),
                new OrderedEntry(
                    MODEL_ID,
                    "requested root",
                    2,
                    "b".repeat(64),
                    1,
                    "d".repeat(64),
                    "e".repeat(64),
                    Layer.ADS,
                    DependencyRole.ROOT,
                    1,
                    Action.BUILD,
                    "REQUESTED_MODEL",
                    null,
                    null
                )
            ),
            List.of()
        );
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(built));
        when(
            materializationPlans.requireCurrent(
                eq(TENANT),
                any(ModelMaterializationPlanContract.PreviewCommand.class),
                eq(checksum)
            )
        )
            .thenReturn(
                new ModelMaterializationPlanService.ValidatedPlan(
                    preview,
                    List.of(new ScopeEntryCommand(MODEL_ID, 0, "MATERIALIZATION_ROOT"))
                )
            );
        when(
            materializationStarts.rematerialize(
                TENANT,
                ACTOR,
                CANDIDATE_ID,
                4,
                request.idempotencyKey(),
                request.reason(),
                List.of(MODEL_ID)
            )
        )
            .thenReturn(new CommandResult(building, false, List.of()));

        CommandResult result = service.rematerialize(
            TENANT,
            ACTOR,
            PLAN_ID,
            CANDIDATE_ID,
            4,
            request,
            checksum,
            ModelMaterializationPlanContract.Strategy.WITH_MISSING_UPSTREAMS
        );

        assertThat(result.candidate()).isSameAs(building);
        InOrder order = org.mockito.Mockito.inOrder(repository, materializationPlans, materializationStarts);
        order.verify(repository).lockPlanForCandidate(TENANT, PLAN_ID);
        order.verify(materializationPlans).requireCurrent(
            eq(TENANT),
            any(ModelMaterializationPlanContract.PreviewCommand.class),
            eq(checksum)
        );
        order.verify(materializationStarts).rematerialize(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            4,
            request.idempotencyKey(),
            request.reason(),
            List.of(MODEL_ID)
        );
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
    void materializationHistoryIsCandidateScopedAndPreservesEveryAttempt() {
        CandidateView built = candidate(DeliveryStatus.BUILT, List.of(entry(DeliveryStatus.BUILT)));
        MaterializationAttemptView attempt = new MaterializationAttemptView(
            CANDIDATE_ID,
            4,
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            2,
            "COMPLETED",
            null,
            NOW.minusSeconds(60),
            NOW,
            List.of()
        );
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(built));
        when(workbenchEvidence.findHistory(built)).thenReturn(List.of(attempt));

        assertThat(service.materializationHistory(TENANT, ACTOR, PLAN_ID, CANDIDATE_ID))
            .containsExactly(attempt);

        verify(workbenchEvidence).findHistory(built);
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
    void planReadAuthorizationFailsBeforeCandidateStateIsRead() {
        when(planReadAccess.canReadPlan(PLAN_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.workspace(TENANT, ACTOR, PLAN_ID))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).kind())
                    .isEqualTo(ModelReleaseCandidateException.Kind.FORBIDDEN)
            );

        verify(repository, never()).listForWorkbench(TENANT, PLAN_ID);
    }

    @Test
    void readOnlyPlanCanReadEmptyWorkspaceWithoutWriteActions() {
        when(planAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(false);
        when(dutyResolver.currentDuties()).thenReturn(Set.of());
        when(repository.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of());

        var workspace = service.workspace(TENANT, ACTOR, PLAN_ID);

        assertThat(workspace.candidate()).isNull();
        assertThat(workspace.allowedActions()).isEmpty();
        assertThat(service.canMaintainForRead(TENANT, ACTOR, PLAN_ID)).isFalse();
        verify(repository).listForWorkbench(TENANT, PLAN_ID);
    }

    @Test
    void readOnlyPlanCanReadPublishedCandidateWithoutWriteActions() {
        when(planAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(false);
        when(dutyResolver.currentDuties()).thenReturn(Set.of(DeliveryActorRole.MODEL_MAINTAINER));
        CandidateView published = candidate(
            DeliveryStatus.PUBLISHED,
            List.of(entry(DeliveryStatus.PUBLISHED)),
            new DeliveryAuditView(ACTOR, NOW.minusSeconds(60), ACTOR, NOW.minusSeconds(30), null, null, ACTOR, NOW)
        );
        when(repository.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(published));
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(java.util.Optional.of(published));

        var workspace = service.workspace(TENANT, ACTOR, PLAN_ID);

        assertThat(workspace.candidate().id()).isEqualTo(CANDIDATE_ID);
        assertThat(workspace.allowedActions()).isEmpty();
        assertThat(service.allowedActionsForRead(TENANT, ACTOR, PLAN_ID, CANDIDATE_ID)).isEmpty();
    }

    @Test
    void ordinaryCreateCannotOpenASecondActiveCandidateForThePlan() {
        CandidateView active = candidate(DeliveryStatus.DRAFT, List.of(entry(DeliveryStatus.DRAFT)));
        when(repository.listActiveForPlan(TENANT, PLAN_ID)).thenReturn(List.of(active));

        assertThatThrownBy(() ->
            service.create(
                TENANT,
                ACTOR,
                PLAN_ID,
                new CreateCandidateCommand(PLAN_ID, active.environment(), List.of(new ScopeEntryCommand(MODEL_ID, 0, "overlap")), "second-key", "second active candidate")
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo("MODEL_RELEASE_CANDIDATE_ACTIVE_EXISTS")
            );

        verify(commands, never()).createBatchWithExpandedScope(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyList()
        );
    }

    @Test
    void createsAnIndependentModelWithoutCancellingAnExistingOrdinaryCandidate() {
        CandidateView active = candidate(DeliveryStatus.BUILT, List.of(entry(DeliveryStatus.BUILT)));
        UUID otherModel = UUID.randomUUID();
        CreateCandidateCommand command = new CreateCandidateCommand(PLAN_ID, active.environment(),
            List.of(new ScopeEntryCommand(otherModel, 0, "independent model using the same source")), "independent", "build independently");
        CandidateView created = candidate(DeliveryStatus.DRAFT, List.of());
        when(repository.listActiveForPlan(TENANT, PLAN_ID)).thenReturn(List.of(active));
        when(commands.createBatchWithExpandedScope(TENANT, ACTOR, command, command.entries()))
            .thenReturn(new CommandResult(created, false, List.of()));

        assertThat(service.create(TENANT, ACTOR, PLAN_ID, command).candidate()).isSameAs(created);
        verify(repository).lockPlanForCandidate(TENANT, PLAN_ID);
        verify(commands, never()).transition(any(), any(), any(), any());
    }

    @Test
    void scopedWorkspaceDoesNotFallBackToThePlanWideCandidate() {
        when(repository.listForModelScope(TENANT, PLAN_ID, "prod", List.of(MODEL_ID))).thenReturn(List.of());
        assertThat(service.workspaceForScope(TENANT, ACTOR, PLAN_ID, "prod", List.of(MODEL_ID)).candidate()).isNull();
        verify(repository, never()).listForWorkbench(any(), any());
        CandidateView own = candidate(DeliveryStatus.DRAFT, List.of(entry(DeliveryStatus.DRAFT)));
        when(repository.listForModelScope(TENANT, PLAN_ID, "prod", List.of(MODEL_ID))).thenReturn(List.of(own));
        assertThat(service.workspaceForScope(TENANT, ACTOR, PLAN_ID, "prod", List.of(MODEL_ID)).candidate()).isSameAs(own);
    }

    @Test
    void scopedWorkspaceRequiresSeparateHandlingForMultipleExistingCandidates() {
        CandidateView first = candidate(DeliveryStatus.DRAFT, List.of(entry(DeliveryStatus.DRAFT)));
        CandidateView second = candidate(DeliveryStatus.BUILT, List.of(entry(DeliveryStatus.BUILT)));
        when(repository.listForModelScope(TENANT, PLAN_ID, "prod", List.of(MODEL_ID))).thenReturn(List.of(first, second));
        assertThatThrownBy(() -> service.workspaceForScope(TENANT, ACTOR, PLAN_ID, "prod", List.of(MODEL_ID)))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .hasMessageContaining("多个活动候选");
    }

    @Test
    void publishedCandidateRemainsHistoryButAllowsTheNextDeliveryCandidate() {
        CandidateView published = candidate(
            DeliveryStatus.PUBLISHED,
            List.of(entry(DeliveryStatus.PUBLISHED)),
            new DeliveryAuditView(
                ACTOR,
                NOW.minusSeconds(60),
                ACTOR,
                NOW.minusSeconds(30),
                null,
                null,
                ACTOR,
                NOW
            )
        );
        CandidateView draft = candidate(
            DeliveryStatus.DRAFT,
            List.of(entry(DeliveryStatus.DRAFT))
        );
        CreateCandidateCommand command = new CreateCandidateCommand(
            PLAN_ID,
            "dev",
            List.of(new ScopeEntryCommand(MODEL_ID, 0, "next dependency layer")),
            "next-candidate-key",
            "deliver the next model scope"
        );
        when(repository.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(published));
        when(commands.createBatchWithExpandedScope(TENANT, ACTOR, command, command.entries()))
            .thenReturn(new CommandResult(draft, false, List.of()));

        var workspace = service.workspace(TENANT, ACTOR, PLAN_ID);
        var created = service.create(TENANT, ACTOR, PLAN_ID, command);

        assertThat(workspace.candidate()).isSameAs(published);
        assertThat(workspace.allowedActions()).contains(WorkspaceAction.CREATE_CANDIDATE);
        assertThat(created.candidate()).isSameAs(draft);
        verify(preflight).requireEligible(TENANT, command);
        verify(commands).createBatchWithExpandedScope(TENANT, ACTOR, command, command.entries());
    }

    @Test
    void checksumBoundCreateUsesOnlyTheServerOwnedBuildScope() {
        UUID upstreamId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        CreateCandidateCommand command = new CreateCandidateCommand(
            PLAN_ID,
            "dev",
            List.of(new ScopeEntryCommand(MODEL_ID, 0, "selected root")),
            "planned-candidate-key",
            "materialize dependency plan"
        );
        List<ScopeEntryCommand> buildScope = List.of(
            new ScopeEntryCommand(upstreamId, 0, "AUTO_DEPENDENCY"),
            new ScopeEntryCommand(MODEL_ID, 1, "MATERIALIZATION_ROOT")
        );
        String checksum = "a".repeat(64);
        when(
            materializationPlans.requireCurrent(
                eq(TENANT),
                any(ModelMaterializationPlanContract.PreviewCommand.class),
                eq(checksum)
            )
        )
            .thenReturn(new ModelMaterializationPlanService.ValidatedPlan(null, buildScope));
        CandidateView draft = candidate(DeliveryStatus.DRAFT, List.of(entry(DeliveryStatus.DRAFT)));
        when(commands.createBatchWithExpandedScope(TENANT, ACTOR, command, buildScope))
            .thenReturn(new CommandResult(draft, false, List.of()));

        CommandResult result = service.create(
            TENANT,
            ACTOR,
            PLAN_ID,
            command,
            checksum,
            ModelMaterializationPlanContract.Strategy.WITH_MISSING_UPSTREAMS
        );

        assertThat(result.candidate()).isSameAs(draft);
        ArgumentCaptor<ModelMaterializationPlanContract.PreviewCommand> preview = ArgumentCaptor.forClass(
            ModelMaterializationPlanContract.PreviewCommand.class
        );
        verify(materializationPlans).requireCurrent(eq(TENANT), preview.capture(), eq(checksum));
        assertThat(preview.getValue().requestedModelSpecIds()).containsExactly(MODEL_ID);
        verify(commands).createBatchWithExpandedScope(TENANT, ACTOR, command, buildScope);
        verify(preflight, never()).requireEligible(eq(TENANT), any(CreateCandidateCommand.class));
        InOrder order = org.mockito.Mockito.inOrder(repository, materializationPlans, commands);
        order.verify(repository).lockPlanForCandidate(TENANT, PLAN_ID);
        order.verify(materializationPlans).requireCurrent(eq(TENANT), any(), eq(checksum));
        order.verify(commands).createBatchWithExpandedScope(TENANT, ACTOR, command, buildScope);
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
        when(commands.createBatchWithExpandedScope(TENANT, ACTOR, command, List.of())).thenReturn(replay);

        assertThat(service.create(TENANT, ACTOR, PLAN_ID, command)).isEqualTo(replay);

        verify(commands).createBatchWithExpandedScope(TENANT, ACTOR, command, List.of());
        verify(repository, never()).listForWorkbench(TENANT, PLAN_ID);
        verify(preflight, never()).requireEligible(eq(TENANT), any(CreateCandidateCommand.class));
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
    void dataOwnerWorkspacePrefersDirectPublishAfterQualityWithoutReviewActions() {
        CandidateView qualityPassed = candidate(
            DeliveryStatus.QUALITY_PASSED,
            List.of(entry(DeliveryStatus.QUALITY_PASSED))
        );
        when(dutyResolver.currentDuties())
            .thenReturn(
                Set.of(
                    DeliveryActorRole.MODEL_MAINTAINER,
                    DeliveryActorRole.RELEASE_OPERATOR
                )
            );
        when(repository.listForWorkbench(TENANT, PLAN_ID))
            .thenReturn(List.of(qualityPassed));

        var workspace = service.workspace(TENANT, ACTOR, PLAN_ID);

        assertThat(workspace.allowedActions())
            .containsExactly(WorkspaceAction.PUBLISH);
    }

    @Test
    void missingBlockingGovernanceQualityRemovesPublishAndExplainsTheBlocker() {
        CandidateView qualityPassed = candidate(
            DeliveryStatus.QUALITY_PASSED,
            List.of(entry(DeliveryStatus.QUALITY_PASSED))
        );
        when(dutyResolver.currentDuties())
            .thenReturn(Set.of(DeliveryActorRole.MODEL_MAINTAINER, DeliveryActorRole.RELEASE_OPERATOR));
        when(repository.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(qualityPassed));
        when(governanceQuality.evaluateForRead(qualityPassed))
            .thenReturn(
                new GovernanceQualitySummaryView(
                    true,
                    EvidenceState.FAILED,
                    "MODEL_SPEC_GOVERNANCE_QUALITY_MISSING",
                    "当前物理资产缺少治理质量运行证据",
                    300,
                    List.of()
                )
            );

        var workspace = service.workspace(TENANT, ACTOR, PLAN_ID);

        assertThat(workspace.state()).isEqualTo(WorkbenchState.BLOCKED);
        assertThat(workspace.allowedActions()).doesNotContain(WorkspaceAction.PUBLISH);
        assertThat(workspace.primaryBlocker().code()).isEqualTo("MODEL_SPEC_GOVERNANCE_QUALITY_MISSING");
        assertThat(workspace.governanceQuality().required()).isTrue();
    }

    @Test
    void publishCommandCannotBypassTheBlockingGovernanceQualityGate() {
        CandidateView approved = candidate(
            DeliveryStatus.APPROVED,
            List.of(entry(DeliveryStatus.APPROVED)),
            new DeliveryAuditView(
                "creator",
                NOW.minusSeconds(180),
                "submitter",
                NOW.minusSeconds(120),
                "reviewer",
                NOW.minusSeconds(60),
                null,
                null
            )
        );
        when(dutyResolver.currentDuties()).thenReturn(Set.of(DeliveryActorRole.RELEASE_OPERATOR));
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(approved));
        when(governanceQuality.requirePublishableSnapshot(approved))
            .thenThrow(
                new ModelReleaseCandidateException(
                    "MODEL_SPEC_GOVERNANCE_QUALITY_MISSING",
                    "治理质量证据缺失",
                    ModelReleaseCandidateException.Kind.UNPROCESSABLE
                )
            );

        assertThatThrownBy(() ->
            service.publish(
                TENANT,
                ACTOR,
                PLAN_ID,
                CANDIDATE_ID,
                4,
                "quality-gated-publish",
                "publish only after governance quality passes"
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo("MODEL_SPEC_GOVERNANCE_QUALITY_MISSING")
            );
        verifyNoInteractions(publicationAdmission, publicationCoordinator);
        verify(commands, never()).transition(any(), any(), any(), any());
        verify(commands, never()).transitionWithQualityEvidence(any(), any(), any(), any(), any());
    }

    @Test
    void dataOwnerCanDirectlyPublishOwnPendingCandidateWithoutApproval() {
        DeliveryAuditView submitted = new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(120),
            ACTOR,
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
        CandidateView publishing = candidate(
            DeliveryStatus.PUBLISHING,
            List.of(entry(DeliveryStatus.PUBLISHING)),
            submitted
        );
        DeliveryAuditView publishedAudit = new DeliveryAuditView(
            submitted.createdBy(),
            submitted.createdAt(),
            submitted.submittedBy(),
            submitted.submittedAt(),
            null,
            null,
            ACTOR,
            NOW
        );
        CandidateView published = candidate(
            DeliveryStatus.PUBLISHED,
            List.of(entry(DeliveryStatus.PUBLISHED)),
            publishedAudit
        );
        when(dutyResolver.currentDuties())
            .thenReturn(
                Set.of(
                    DeliveryActorRole.MODEL_MAINTAINER,
                    DeliveryActorRole.RELEASE_OPERATOR
                )
            );
        when(repository.find(TENANT, CANDIDATE_ID))
            .thenReturn(Optional.of(pending));
        CandidateQualityEvidenceSnapshot qualitySnapshot = qualitySnapshot(pending);
        when(governanceQuality.requirePublishableSnapshot(pending)).thenReturn(qualitySnapshot);
        when(commands.transitionWithQualityEvidence(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any(), eq(qualitySnapshot)))
            .thenReturn(new CommandResult(publishing, false, List.of()));
        when(
            publicationCoordinator.publish(
                TENANT,
                ACTOR,
                publishing,
                "direct-publish-key",
                "data owner self-service release"
            )
        )
            .thenReturn(new CommandResult(published, false, List.of()));

        CommandResult result = service.publish(
            TENANT,
            ACTOR,
            PLAN_ID,
            CANDIDATE_ID,
            4,
            "direct-publish-key",
            "data owner self-service release"
        );

        assertThat(result.candidate().status())
            .isEqualTo(DeliveryStatus.PUBLISHED);
        assertThat(result.candidate().audit().approvedBy()).isNull();
        assertThat(result.candidate().audit().publishedBy()).isEqualTo(ACTOR);
    }

    @Test
    void releaseOperatorCannotBypassWarehousePlanScope() {
        when(planAccess.canMaintain(TENANT, PLAN_ID, ACTOR)).thenReturn(false);
        when(dutyResolver.currentDuties())
            .thenReturn(Set.of(DeliveryActorRole.RELEASE_OPERATOR));

        assertThatThrownBy(() ->
            service.publish(
                TENANT,
                ACTOR,
                PLAN_ID,
                CANDIDATE_ID,
                4,
                "publish-key",
                "attempt cross-department release"
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).kind())
                    .isEqualTo(ModelReleaseCandidateException.Kind.FORBIDDEN)
            );

        verify(repository, never()).find(any(), any());
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
        CandidateQualityEvidenceSnapshot qualitySnapshot = qualitySnapshot(approved);
        when(governanceQuality.requirePublishableSnapshot(approved)).thenReturn(qualitySnapshot);
        when(commands.transitionWithQualityEvidence(eq(TENANT), eq(ACTOR), eq(CANDIDATE_ID), any(), eq(qualitySnapshot)))
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
        order.verify(commands).transitionWithQualityEvidence(
            eq(TENANT),
            eq(ACTOR),
            eq(CANDIDATE_ID),
            any(),
            eq(qualitySnapshot)
        );
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

    private static CandidateQualityEvidenceSnapshot qualitySnapshot(CandidateView candidate) {
        GovernanceQualitySummaryView summary = new GovernanceQualitySummaryView(
            true,
            EvidenceState.PASSED,
            null,
            null,
            300,
            List.of(
                new QualityEvidence(
                    "70000000-0000-0000-0000-000000000001",
                    UUID.fromString("70000000-0000-0000-0000-000000000002"),
                    UUID.fromString("70000000-0000-0000-0000-000000000003"),
                    UUID.fromString("70000000-0000-0000-0000-000000000004"),
                    UUID.fromString("70000000-0000-0000-0000-000000000005"),
                    "SUCCEEDED",
                    NOW.minusSeconds(5),
                    "d".repeat(64),
                    List.of()
                )
            )
        );
        return CandidateQualityEvidenceSnapshot.captureLegacy(candidate, summary);
    }

    private static DeliveryAuditView audit() {
        return new DeliveryAuditView(ACTOR, NOW.minusSeconds(60), null, null, null, null, null, null);
    }
}
