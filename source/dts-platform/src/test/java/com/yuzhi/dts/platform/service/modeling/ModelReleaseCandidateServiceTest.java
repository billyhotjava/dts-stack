package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAction;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventType;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandEventView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CreateCandidateCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CurrentModelReference;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.DriftReasonView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ReplaceScopeCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ScopeEntryCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.VersionConflictView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelReleaseCandidateServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String ACTOR = "model-owner";
    private static final Instant NOW = Instant.parse("2026-07-24T08:00:00Z");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String CHECKSUM = "a".repeat(64);

    @Mock
    private ModelReleaseCandidateRepository repository;

    @Mock
    private ModelReleaseCandidateRetryDriftGate retryDriftGate;

    private ModelReleaseCandidateService service;

    @BeforeEach
    void setUp() {
        service = new ModelReleaseCandidateService(
            repository,
            new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            UUID::randomUUID,
            null,
            retryDriftGate
        );
    }

    @Test
    void replaysTheOriginalCreateResponseForTheSameTenantKeyAndPayload() {
        CreateCandidateCommand command = createCommand("create-key", "first release");
        when(repository.findCommandByIdempotencyKey(TENANT, "create-key")).thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(TENANT, "create-key")).thenReturn(Optional.empty());
        when(repository.findCurrentModelReferences(TENANT, PLAN_ID, List.of(MODEL_ID))).thenReturn(
            Map.of(MODEL_ID, currentReference(1, CHECKSUM))
        );
        when(repository.insert(any())).thenReturn(1);
        when(repository.appendCommand(any())).thenReturn(1);

        var first = service.create(TENANT, ACTOR, command);
        ArgumentCaptor<CommandEventView> event = ArgumentCaptor.forClass(CommandEventView.class);
        verify(repository).appendCommand(event.capture());
        when(repository.findCommandByIdempotencyKey(TENANT, "create-key")).thenReturn(Optional.of(event.getValue()));

        var replay = service.create(TENANT, ACTOR, command);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.candidate()).isEqualTo(first.candidate());
        assertThat(event.getValue().eventType()).isEqualTo(CommandEventType.CREATED);
        verify(repository).lockPlanForCandidate(TENANT, PLAN_ID);
        verify(repository).insert(any());
    }

    @Test
    void singleModelOriginIsCreatedAndReplayedWithoutChangingBatchRequestHashes() {
        CreateCandidateCommand command = createCommand(
            "single-create-key",
            "single model intent"
        );
        when(
            repository.findCommandByIdempotencyKey(
                TENANT,
                "single-create-key"
            )
        )
            .thenReturn(Optional.empty());
        when(
            repository.findByIdempotencyKey(TENANT, "single-create-key")
        )
            .thenReturn(Optional.empty());
        when(
            repository.findCurrentModelReferences(
                TENANT,
                PLAN_ID,
                List.of(MODEL_ID)
            )
        )
            .thenReturn(Map.of(MODEL_ID, currentReference(1, CHECKSUM)));
        when(repository.insert(any())).thenReturn(1);
        when(repository.appendCommand(any())).thenReturn(1);

        var first = service.createSingleModelIntent(TENANT, ACTOR, command);
        ArgumentCaptor<CandidateView> candidate =
            ArgumentCaptor.forClass(CandidateView.class);
        verify(repository).insert(candidate.capture());
        assertThat(candidate.getValue().origin())
            .isEqualTo(CandidateOrigin.SINGLE_MODEL_INTENT);

        ArgumentCaptor<CommandEventView> event =
            ArgumentCaptor.forClass(CommandEventView.class);
        verify(repository).appendCommand(event.capture());
        when(
            repository.findCommandByIdempotencyKey(
                TENANT,
                "single-create-key"
            )
        )
            .thenReturn(Optional.of(event.getValue()));

        var replay = service.createSingleModelIntent(TENANT, ACTOR, command);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.candidate().origin())
            .isEqualTo(CandidateOrigin.SINGLE_MODEL_INTENT);
        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo(
                        ModelReleaseCandidateContract.IDEMPOTENCY_CONFLICT_ERROR_CODE
                    )
            );
    }

    @Test
    void rejectsTheSameTenantKeyWhenThePayloadChanges() {
        CreateCandidateCommand original = createCommand("shared-key", "first release");
        when(repository.findCommandByIdempotencyKey(TENANT, "shared-key")).thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(TENANT, "shared-key")).thenReturn(Optional.empty());
        when(repository.findCurrentModelReferences(TENANT, PLAN_ID, List.of(MODEL_ID))).thenReturn(
            Map.of(MODEL_ID, currentReference(1, CHECKSUM))
        );
        when(repository.insert(any())).thenReturn(1);
        when(repository.appendCommand(any())).thenReturn(1);
        service.create(TENANT, ACTOR, original);
        ArgumentCaptor<CommandEventView> event = ArgumentCaptor.forClass(CommandEventView.class);
        verify(repository).appendCommand(event.capture());
        when(repository.findCommandByIdempotencyKey(TENANT, "shared-key")).thenReturn(Optional.of(event.getValue()));

        CreateCandidateCommand changed = new CreateCandidateCommand(
            PLAN_ID,
            "prod",
            original.entries(),
            "shared-key",
            "different reason"
        );
        assertThatThrownBy(() -> service.create(TENANT, ACTOR, changed))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo(ModelReleaseCandidateContract.IDEMPOTENCY_CONFLICT_ERROR_CODE)
            );
    }

    @Test
    void cannotReuseACreateKeyForAStateCommandInTheSameTenant() {
        CandidateView creator = candidate(DeliveryStatus.DRAFT, 1, createdAudit(), List.of());
        when(repository.findCommandByIdempotencyKey(TENANT, "original-create-key")).thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(TENANT, "original-create-key")).thenReturn(Optional.of(creator));

        assertThatThrownBy(() ->
            service.transition(
                TENANT,
                ACTOR,
                CANDIDATE_ID,
                new TransitionCommand(1, DeliveryStatus.BUILDING, "original-create-key", "reuse create key")
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo(ModelReleaseCandidateContract.IDEMPOTENCY_CONFLICT_ERROR_CODE)
            );
        verify(repository, never()).transitionAndAppend(any(), anyInt(), any(), any(), anyString(), any(), any());
    }

    @Test
    void legacyOrMissingModelCannotEnterACanonicalCandidateScope() {
        CreateCandidateCommand command = createCommand("legacy-key", "reject legacy");
        when(repository.findCommandByIdempotencyKey(TENANT, "legacy-key")).thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(TENANT, "legacy-key")).thenReturn(Optional.empty());
        when(repository.findCurrentModelReferences(TENANT, PLAN_ID, List.of(MODEL_ID))).thenReturn(Map.of());

        assertThatThrownBy(() -> service.create(TENANT, ACTOR, command))
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo("MODEL_RELEASE_CANDIDATE_SCOPE_STALE")
            );
        verify(repository, never()).insert(any());
    }

    @Test
    void returnsTheCurrentVersionForAStaleExpectedVersion() {
        CandidateView current = candidate(DeliveryStatus.DRAFT, 3, createdAudit(), List.of());
        when(repository.findCommandByIdempotencyKey(TENANT, "scope-key")).thenReturn(Optional.empty());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(current));

        assertThatThrownBy(() ->
            service.replaceScope(
                TENANT,
                ACTOR,
                CANDIDATE_ID,
                new ReplaceScopeCommand(2, List.of(), "scope-key", "replace selection")
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error -> {
                ModelReleaseCandidateException conflict = (ModelReleaseCandidateException) error;
                assertThat(conflict.code()).isEqualTo(ModelReleaseCandidateContract.VERSION_CONFLICT_ERROR_CODE);
                assertThat(conflict.details()).isEqualTo(
                    VersionConflictView.of(CANDIDATE_ID, 2, 3, DeliveryStatus.DRAFT)
                );
            });
    }

    @Test
    void replacesDraftScopeWithTheNextVersionAndAnAppendOnlyReceipt() {
        CandidateView current = candidate(DeliveryStatus.DRAFT, 1, createdAudit(), List.of());
        ScopeEntryCommand selected = new ScopeEntryCommand(
            MODEL_ID,
            0,
            "selected"
        );
        when(repository.findCommandByIdempotencyKey(TENANT, "scope-key")).thenReturn(Optional.empty());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(current));
        when(repository.findCurrentModelReferences(TENANT, PLAN_ID, List.of(MODEL_ID))).thenReturn(
            Map.of(MODEL_ID, currentReference(2, "b".repeat(64)))
        );
        when(repository.replaceDraftScope(any(), anyInt(), anyList(), anyString(), any(), any())).thenReturn(1);

        var result = service.replaceScope(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            new ReplaceScopeCommand(1, List.of(selected), "scope-key", "select one model")
        );

        assertThat(result.candidate().version()).isEqualTo(2);
        assertThat(result.candidate().entries()).extracting(EntryView::modelSpecId).containsExactly(MODEL_ID);
        assertThat(result.candidate().entries())
            .singleElement()
            .satisfies(entry -> {
                assertThat(entry.revision()).isEqualTo(2);
                assertThat(entry.checksum()).isEqualTo("b".repeat(64));
                assertThat(entry.implementationMode()).isEqualTo(ImplementationMode.DESIGNER_GENERATED);
            });
        ArgumentCaptor<CommandEventView> event = ArgumentCaptor.forClass(CommandEventView.class);
        verify(repository).replaceDraftScope(any(), anyInt(), anyList(), anyString(), any(), event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(CommandEventType.SCOPE_REPLACED);
        assertThat(event.getValue().fromStatus()).isEqualTo(DeliveryStatus.DRAFT);
        assertThat(event.getValue().toStatus()).isEqualTo(DeliveryStatus.DRAFT);
        assertThat(event.getValue().reason()).isEqualTo("select one model");
    }

    @Test
    void atomicallyMarksTheCandidateAndEntriesStaleWhenTheModelRevisionDrifts() {
        EntryView locked = entry(DeliveryStatus.DRAFT, 1, CHECKSUM);
        CandidateView current = candidate(DeliveryStatus.DRAFT, 1, createdAudit(), List.of(locked));
        when(repository.findCommandByIdempotencyKey(TENANT, "build-key")).thenReturn(Optional.empty());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(current));
        when(repository.findCurrentModelReferences(TENANT, PLAN_ID, List.of(MODEL_ID))).thenReturn(
            Map.of(MODEL_ID, currentReference(2, "b".repeat(64)))
        );
        when(
            repository.transitionAndAppend(
                any(),
                anyInt(),
                any(),
                any(),
                anyString(),
                any(),
                any()
            )
        )
            .thenReturn(1);

        var result = service.transition(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            new TransitionCommand(1, DeliveryStatus.BUILDING, "build-key", "start build")
        );

        assertThat(result.candidate().status()).isEqualTo(DeliveryStatus.STALE);
        assertThat(result.candidate().version()).isEqualTo(2);
        assertThat(result.candidate().entries()).extracting(EntryView::status).containsOnly(DeliveryStatus.STALE);
        assertThat(result.allowedActions()).containsExactly(DeliveryAction.CREATE_REPLACEMENT_CANDIDATE);
        assertThat(result.driftReasons()).singleElement().satisfies(reason -> {
            assertThat(reason.lockedRevision()).isEqualTo(1);
            assertThat(reason.currentRevision()).isEqualTo(2);
            assertThat(reason.code()).isEqualTo(ModelReleaseCandidateContract.STALE_ERROR_CODE);
        });
        ArgumentCaptor<CommandEventView> event = ArgumentCaptor.forClass(CommandEventView.class);
        verify(repository)
            .transitionAndAppend(
                any(),
                anyInt(),
                any(),
                any(),
                anyString(),
                any(),
                event.capture()
            );
        assertThat(event.getValue().eventType()).isEqualTo(CommandEventType.STALE_DETECTED);
        assertThat(event.getValue().fromStatus()).isEqualTo(DeliveryStatus.DRAFT);
        assertThat(event.getValue().toStatus()).isEqualTo(DeliveryStatus.STALE);
    }

    @Test
    void explicitDriftRefreshCannotMarkACurrentCandidateStale() {
        EntryView locked = entry(DeliveryStatus.REVIEW_PENDING, 1, CHECKSUM);
        CandidateView current = candidate(
            DeliveryStatus.REVIEW_PENDING,
            4,
            submittedAudit(),
            List.of(locked)
        );
        when(repository.findCommandByIdempotencyKey(TENANT, "refresh-key")).thenReturn(Optional.empty());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(current));
        when(repository.findCurrentModelReferences(TENANT, PLAN_ID, List.of(MODEL_ID))).thenReturn(
            Map.of(MODEL_ID, currentReference(1, CHECKSUM))
        );

        assertThatThrownBy(() ->
            service.transition(
                TENANT,
                ACTOR,
                CANDIDATE_ID,
                new TransitionCommand(4, DeliveryStatus.STALE, "refresh-key", "confirm drift")
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo("MODEL_RELEASE_CANDIDATE_DRIFT_REQUIRED")
            );

        verify(repository, never()).transitionAndAppend(
            any(),
            anyInt(),
            any(),
            any(),
            anyString(),
            any(),
            any()
        );
    }

    @Test
    void retrySnapshotDriftUsesTheOriginalCommandReceiptToTransitionStale() {
        EntryView locked = entry(DeliveryStatus.BUILD_FAILED, 1, CHECKSUM);
        CandidateView current = candidate(
            DeliveryStatus.BUILD_FAILED,
            3,
            createdAudit(),
            List.of(locked)
        );
        DriftReasonView implementationDrift = new DriftReasonView(
            MODEL_ID,
            1,
            CHECKSUM,
            1,
            CHECKSUM,
            "MODEL_MATERIALIZATION_RETRY_SNAPSHOT_STALE",
            "Current implementation snapshot changed after attempt 1"
        );
        when(repository.findCommandByIdempotencyKey(TENANT, "retry-drift-key"))
            .thenReturn(Optional.empty());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(current));
        when(repository.findCurrentModelReferences(TENANT, PLAN_ID, List.of(MODEL_ID)))
            .thenReturn(Map.of(MODEL_ID, currentReference(1, CHECKSUM)));
        when(retryDriftGate.detect(current)).thenReturn(List.of(implementationDrift));
        when(repository.transitionAndAppend(
            any(),
            anyInt(),
            any(),
            any(),
            anyString(),
            any(),
            any()
        ))
            .thenReturn(1);

        CommandResult result = service.transition(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            new TransitionCommand(
                3,
                DeliveryStatus.BUILDING,
                "retry-drift-key",
                "retry failed build"
            )
        );

        assertThat(result.candidate())
            .extracting(CandidateView::status, CandidateView::version)
            .containsExactly(DeliveryStatus.STALE, 4);
        assertThat(result.driftReasons()).containsExactly(implementationDrift);
        ArgumentCaptor<CommandEventView> event = ArgumentCaptor.forClass(
            CommandEventView.class
        );
        verify(repository).transitionAndAppend(
            any(),
            anyInt(),
            eq(DeliveryStatus.STALE),
            any(),
            anyString(),
            any(),
            event.capture()
        );
        assertThat(event.getValue())
            .extracting(
                CommandEventView::eventType,
                CommandEventView::fromStatus,
                CommandEventView::toStatus,
                CommandEventView::idempotencyKey
            )
            .containsExactly(
                CommandEventType.STALE_DETECTED,
                DeliveryStatus.BUILD_FAILED,
                DeliveryStatus.STALE,
                "retry-drift-key"
            );
    }

    @Test
    void postBuildTransitionBecomesStaleWhenMaterializationSnapshotDrifts() {
        CandidateView current = materializedCandidate(
            DeliveryStatus.BUILT,
            7,
            createdAudit()
        );
        DriftReasonView implementationDrift = new DriftReasonView(
            MODEL_ID,
            1,
            CHECKSUM,
            1,
            CHECKSUM,
            "MODEL_MATERIALIZATION_RETRY_SNAPSHOT_STALE",
            "Current implementation snapshot changed after build"
        );
        when(repository.findCommandByIdempotencyKey(TENANT, "quality-drift-key"))
            .thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(TENANT, "quality-drift-key"))
            .thenReturn(Optional.empty());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(current));
        when(repository.findCurrentModelReferences(TENANT, PLAN_ID, List.of(MODEL_ID)))
            .thenReturn(Map.of(MODEL_ID, currentReference(1, CHECKSUM)));
        when(retryDriftGate.detect(current))
            .thenReturn(List.of(implementationDrift));
        when(repository.transitionAndAppend(
            any(),
            anyInt(),
            any(),
            any(),
            anyString(),
            any(),
            any()
        ))
            .thenReturn(1);

        CommandResult result = service.transition(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            new TransitionCommand(
                7,
                DeliveryStatus.QUALITY_RUNNING,
                "quality-drift-key",
                "run quality"
            )
        );

        assertThat(result.candidate().status()).isEqualTo(DeliveryStatus.STALE);
        assertThat(result.driftReasons()).containsExactly(implementationDrift);
        verify(retryDriftGate).detect(current);
    }

    @Test
    void readProjectionIncludesMaterializationSnapshotDriftWithoutTakingWriteLocks() {
        CandidateView current = materializedCandidate(
            DeliveryStatus.QUALITY_PASSED,
            9,
            submittedAudit()
        );
        DriftReasonView implementationDrift = new DriftReasonView(
            MODEL_ID,
            1,
            CHECKSUM,
            1,
            CHECKSUM,
            "MODEL_MATERIALIZATION_RETRY_SNAPSHOT_STALE",
            "Current dependency snapshot changed after build"
        );
        when(repository.findCurrentModelReferencesForRead(
            TENANT,
            PLAN_ID,
            List.of(MODEL_ID)
        ))
            .thenReturn(Map.of(MODEL_ID, currentReference(1, CHECKSUM)));
        when(retryDriftGate.detectForRead(current))
            .thenReturn(List.of(implementationDrift));

        assertThat(service.detectDrift(TENANT, current))
            .containsExactly(implementationDrift);
        verify(retryDriftGate).detectForRead(current);
    }

    @Test
    void rejectsAnIllegalTransitionWithoutWritingAnEvent() {
        CandidateView current = candidate(
            DeliveryStatus.DRAFT,
            1,
            createdAudit(),
            List.of(entry(DeliveryStatus.DRAFT, 1, CHECKSUM))
        );
        when(repository.findCommandByIdempotencyKey(TENANT, "publish-key")).thenReturn(Optional.empty());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(current));
        when(repository.findCurrentModelReferences(TENANT, PLAN_ID, List.of(MODEL_ID))).thenReturn(
            Map.of(MODEL_ID, currentReference(1, CHECKSUM))
        );

        assertThatThrownBy(() ->
            service.transition(
                TENANT,
                ACTOR,
                CANDIDATE_ID,
                new TransitionCommand(1, DeliveryStatus.PUBLISHED, "publish-key", "skip gates")
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo(ModelReleaseCandidateContract.INVALID_TRANSITION_ERROR_CODE)
            );
        verify(repository, never()).transitionAndAppend(any(), anyInt(), any(), any(), anyString(), any(), any());
    }

    @Test
    void publicationRequestIsTheSingleAtomicLedgerEventForStartingQuality() {
        CandidateView built = candidate(
            DeliveryStatus.BUILT,
            7,
            createdAudit(),
            List.of(entry(DeliveryStatus.BUILT, 1, CHECKSUM))
        );
        when(repository.findCommandByIdempotencyKey(TENANT, "publish-intent-key")).thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(TENANT, "publish-intent-key")).thenReturn(Optional.empty());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(built));
        when(repository.findCurrentModelReferences(TENANT, PLAN_ID, List.of(MODEL_ID))).thenReturn(
            Map.of(MODEL_ID, currentReference(1, CHECKSUM))
        );
        when(repository.transitionAndAppend(any(), anyInt(), any(), any(), anyString(), any(), any())).thenReturn(1);

        var result = service.publicationRequested(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            new TransitionCommand(
                7,
                DeliveryStatus.QUALITY_RUNNING,
                "publish-intent-key",
                "submit current model for publication"
            )
        );

        assertThat(result.candidate().status()).isEqualTo(DeliveryStatus.QUALITY_RUNNING);
        ArgumentCaptor<CommandEventView> receipt = ArgumentCaptor.forClass(CommandEventView.class);
        verify(repository).transitionAndAppend(
            any(),
            eq(7),
            eq(DeliveryStatus.QUALITY_RUNNING),
            any(),
            eq(ACTOR),
            eq(NOW),
            receipt.capture()
        );
        assertThat(receipt.getValue().eventType()).isEqualTo(CommandEventType.PUBLICATION_REQUESTED);
    }

    @Test
    void emptyDraftCannotBeLockedOrAdvanced() {
        CandidateView current = candidate(DeliveryStatus.DRAFT, 1, createdAudit(), List.of());
        when(repository.findCommandByIdempotencyKey(TENANT, "empty-key")).thenReturn(Optional.empty());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(current));

        assertThatThrownBy(() ->
            service.transition(
                TENANT,
                ACTOR,
                CANDIDATE_ID,
                new TransitionCommand(1, DeliveryStatus.BUILDING, "empty-key", "lock empty scope")
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo(ModelReleaseCandidateContract.SCOPE_EMPTY_ERROR_CODE)
            );
        verify(repository, never()).transitionAndAppend(any(), anyInt(), any(), any(), anyString(), any(), any());
    }

    @Test
    void cancelsAnEmptyDraftAndPersistsActorReasonAndIdempotentReceipt() {
        CandidateView current = candidate(DeliveryStatus.DRAFT, 1, createdAudit(), List.of());
        when(repository.findCommandByIdempotencyKey(TENANT, "cancel-key")).thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(TENANT, "cancel-key")).thenReturn(Optional.empty());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(current));
        when(repository.transitionAndAppend(any(), anyInt(), any(), any(), anyString(), any(), any())).thenReturn(1);

        TransitionCommand command = new TransitionCommand(
            1,
            DeliveryStatus.CANCELLED,
            "cancel-key",
            "no longer required"
        );
        var cancelled = service.transition(TENANT, ACTOR, CANDIDATE_ID, command);

        assertThat(cancelled.candidate().status()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(cancelled.candidate().version()).isEqualTo(2);
        assertThat(cancelled.allowedActions()).isEmpty();
        ArgumentCaptor<CommandEventView> receipt = ArgumentCaptor.forClass(CommandEventView.class);
        verify(repository).transitionAndAppend(
            any(),
            anyInt(),
            any(),
            any(),
            anyString(),
            any(),
            receipt.capture()
        );
        assertThat(receipt.getValue())
            .extracting(
                CommandEventView::fromStatus,
                CommandEventView::toStatus,
                CommandEventView::actorId,
                CommandEventView::reason,
                CommandEventView::idempotencyKey
            )
            .containsExactly(
                DeliveryStatus.DRAFT,
                DeliveryStatus.CANCELLED,
                ACTOR,
                "no longer required",
                "cancel-key"
            );

        when(repository.findCommandByIdempotencyKey(TENANT, "cancel-key")).thenReturn(Optional.of(receipt.getValue()));
        var replay = service.transition(TENANT, ACTOR, CANDIDATE_ID, command);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.candidate()).isEqualTo(cancelled.candidate());
    }

    @Test
    void cancelDoesNotTurnIntoStaleWhenCanonicalScopeHasDrifted() {
        CandidateView failed = candidate(
            DeliveryStatus.BUILD_FAILED,
            4,
            createdAudit(),
            List.of(entry(DeliveryStatus.BUILD_FAILED, 1, CHECKSUM))
        );
        when(repository.findCommandByIdempotencyKey(TENANT, "cancel-drift-key")).thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(TENANT, "cancel-drift-key")).thenReturn(Optional.empty());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(failed));
        when(repository.transitionAndAppend(any(), anyInt(), any(), any(), anyString(), any(), any())).thenReturn(1);

        var result = service.transition(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            new TransitionCommand(4, DeliveryStatus.CANCELLED, "cancel-drift-key", "abandon failed build")
        );

        assertThat(result.candidate().status()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(result.driftReasons()).isEmpty();
        verify(repository, never()).findCurrentModelReferences(anyString(), any(), anyList());
    }

    @Test
    void builtCandidateCannotBeCancelled() {
        CandidateView built = candidate(
            DeliveryStatus.BUILT,
            4,
            createdAudit(),
            List.of(entry(DeliveryStatus.BUILT, 1, CHECKSUM))
        );
        when(repository.findCommandByIdempotencyKey(TENANT, "late-cancel-key")).thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(TENANT, "late-cancel-key")).thenReturn(Optional.empty());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(built));

        assertThatThrownBy(() ->
            service.transition(
                TENANT,
                ACTOR,
                CANDIDATE_ID,
                new TransitionCommand(4, DeliveryStatus.CANCELLED, "late-cancel-key", "too late")
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo(ModelReleaseCandidateContract.INVALID_TRANSITION_ERROR_CODE)
            );
        verify(repository, never()).transitionAndAppend(any(), anyInt(), any(), any(), anyString(), any(), any());
    }

    @Test
    void convergesAConcurrentSamePayloadCommandToTheCommittedReplay() {
        CandidateView current = candidate(
            DeliveryStatus.DRAFT,
            1,
            createdAudit(),
            List.of(entry(DeliveryStatus.DRAFT, 1, CHECKSUM))
        );
        AtomicReference<CommandEventView> committed = new AtomicReference<>();
        when(repository.findCommandByIdempotencyKey(TENANT, "concurrent-key")).thenAnswer(invocation ->
            Optional.ofNullable(committed.get())
        );
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(current));
        when(repository.findCurrentModelReferences(TENANT, PLAN_ID, List.of(MODEL_ID))).thenReturn(
            Map.of(MODEL_ID, currentReference(1, CHECKSUM))
        );
        when(repository.transitionAndAppend(any(), anyInt(), any(), any(), anyString(), any(), any())).thenAnswer(
            invocation -> {
                committed.set(invocation.getArgument(6));
                return 0;
            }
        );

        var result = service.transition(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            new TransitionCommand(1, DeliveryStatus.BUILDING, "concurrent-key", "start once")
        );

        assertThat(result.replayed()).isTrue();
        assertThat(result.candidate().status()).isEqualTo(DeliveryStatus.BUILDING);
        assertThat(result.candidate().version()).isEqualTo(2);
    }

    @Test
    void cannotReadOrMutateACandidateThroughAnotherTenant() {
        String otherTenant = "tenant-b";
        when(repository.findCommandByIdempotencyKey(otherTenant, "cross-tenant-key")).thenReturn(Optional.empty());
        when(repository.find(otherTenant, CANDIDATE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.transition(
                otherTenant,
                ACTOR,
                CANDIDATE_ID,
                new TransitionCommand(1, DeliveryStatus.BUILDING, "cross-tenant-key", "cross tenant")
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo("MODEL_RELEASE_CANDIDATE_NOT_FOUND")
            );
        verify(repository, never()).transitionAndAppend(any(), anyInt(), any(), any(), anyString(), any(), any());
    }

    @Test
    void locksScopeAfterTheCandidateLeavesDraft() {
        CandidateView current = candidate(DeliveryStatus.BUILT, 4, createdAudit(), List.of());
        when(repository.findCommandByIdempotencyKey(TENANT, "scope-key")).thenReturn(Optional.empty());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(current));

        assertThatThrownBy(() ->
            service.replaceScope(
                TENANT,
                ACTOR,
                CANDIDATE_ID,
                new ReplaceScopeCommand(4, List.of(), "scope-key", "late edit")
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo(ModelReleaseCandidateContract.SCOPE_LOCKED_ERROR_CODE)
            );
    }

    @Test
    void terminalCandidateCreatesANewDraftWithoutMutatingOldAudit() {
        DeliveryAuditView oldAudit = fullAudit();
        CandidateView rolledBack = candidate(DeliveryStatus.ROLLED_BACK, 5, oldAudit, List.of());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(rolledBack));
        when(repository.findCommandByIdempotencyKey(TENANT, "replacement-key")).thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(TENANT, "replacement-key")).thenReturn(Optional.empty());
        when(repository.insert(any())).thenReturn(1);
        when(repository.appendCommand(any())).thenReturn(1);

        var result = service.createReplacement(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            5,
            new CreateCandidateCommand(PLAN_ID, "prod", List.of(), "replacement-key", "fix rollback")
        );

        assertThat(result.candidate().id()).isNotEqualTo(CANDIDATE_ID);
        assertThat(result.candidate().status()).isEqualTo(DeliveryStatus.DRAFT);
        assertThat(result.candidate().version()).isEqualTo(1);
        assertThat(result.candidate().audit().submittedAt()).isNull();
        assertThat(result.allowedActions()).isEmpty();
        assertThat(rolledBack.audit()).isEqualTo(oldAudit);
        verify(repository, never()).transitionAndAppend(any(), anyInt(), any(), any(), anyString(), any(), any());
    }

    @Test
    void cancelledCandidateCreatesAReplacementFromCurrentReferences() {
        CandidateView cancelled = candidate(
            DeliveryStatus.CANCELLED,
            5,
            createdAudit(),
            List.of(entry(DeliveryStatus.CANCELLED, 1, CHECKSUM))
        );
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(cancelled));
        when(repository.findCommandByIdempotencyKey(TENANT, "cancelled-replacement-key")).thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(TENANT, "cancelled-replacement-key")).thenReturn(Optional.empty());
        when(repository.findCurrentModelReferences(TENANT, PLAN_ID, List.of(MODEL_ID))).thenReturn(
            Map.of(MODEL_ID, currentReference(2, "b".repeat(64)))
        );
        when(repository.insert(any())).thenReturn(1);
        when(repository.appendCommand(any())).thenReturn(1);

        var result = service.createReplacement(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            5,
            new CreateCandidateCommand(
                PLAN_ID,
                "prod",
                List.of(new ScopeEntryCommand(MODEL_ID, 0, "current revision")),
                "cancelled-replacement-key",
                "restart delivery"
            )
        );

        assertThat(result.candidate().status()).isEqualTo(DeliveryStatus.DRAFT);
        assertThat(result.candidate().entries())
            .singleElement()
            .extracting(EntryView::revision, EntryView::checksum)
            .containsExactly(2, "b".repeat(64));
    }

    @Test
    void staleReplacementTransfersTheExistingClaimToTheNewDraft() {
        CandidateView stale = candidate(
            DeliveryStatus.STALE,
            4,
            createdAudit(),
            List.of(entry(DeliveryStatus.STALE, 1, CHECKSUM))
        );
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(stale));
        when(repository.findCommandByIdempotencyKey(TENANT, "stale-replacement-key"))
            .thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey(TENANT, "stale-replacement-key"))
            .thenReturn(Optional.empty());
        when(repository.findCurrentModelReferences(TENANT, PLAN_ID, List.of(MODEL_ID)))
            .thenReturn(Map.of(MODEL_ID, currentReference(1, CHECKSUM)));
        when(repository.insert(any())).thenReturn(1);
        when(repository.appendCommand(any())).thenReturn(1);

        CommandResult result = service.createReplacement(
            TENANT,
            ACTOR,
            CANDIDATE_ID,
            4,
            new CreateCandidateCommand(
                PLAN_ID,
                "prod",
                List.of(new ScopeEntryCommand(MODEL_ID, 0, "current revision")),
                "stale-replacement-key",
                "replace stale snapshot"
            )
        );

        verify(repository).transferActiveClaims(stale, result.candidate());
        assertThat(result.candidate().status()).isEqualTo(DeliveryStatus.DRAFT);
    }

    @Test
    void replacementRequiresTheTerminalCandidateVersion() {
        CandidateView rolledBack = candidate(DeliveryStatus.ROLLED_BACK, 5, fullAudit(), List.of());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(rolledBack));

        assertThatThrownBy(() ->
            service.createReplacement(
                TENANT,
                ACTOR,
                CANDIDATE_ID,
                4,
                new CreateCandidateCommand(PLAN_ID, "prod", List.of(), "replacement-key", "stale browser")
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo(ModelReleaseCandidateContract.VERSION_CONFLICT_ERROR_CODE)
            );

        verify(repository, never()).insert(any());
    }

    @Test
    void submitterCannotApproveTheSameCandidate() {
        DeliveryAuditView submitted = new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(60),
            ACTOR,
            NOW.minusSeconds(30),
            null,
            null,
            null,
            null
        );
        CandidateView pending = candidate(
            DeliveryStatus.REVIEW_PENDING,
            8,
            submitted,
            List.of(entry(DeliveryStatus.REVIEW_PENDING, 1, CHECKSUM))
        );
        when(repository.findCommandByIdempotencyKey(TENANT, "approve-key")).thenReturn(Optional.empty());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(pending));
        when(repository.findCurrentModelReferences(TENANT, PLAN_ID, List.of(MODEL_ID))).thenReturn(
            Map.of(MODEL_ID, currentReference(1, CHECKSUM))
        );

        assertThatThrownBy(() ->
            service.transition(
                TENANT,
                ACTOR,
                CANDIDATE_ID,
                new TransitionCommand(8, DeliveryStatus.APPROVED, "approve-key", "self approve")
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo("MODEL_RELEASE_CANDIDATE_ACTOR_SEPARATION_REQUIRED")
            );
    }

    @Test
    void submitterCannotRejectTheSameCandidate() {
        DeliveryAuditView submitted = new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(60),
            ACTOR,
            NOW.minusSeconds(30),
            null,
            null,
            null,
            null
        );
        CandidateView pending = candidate(
            DeliveryStatus.REVIEW_PENDING,
            8,
            submitted,
            List.of(entry(DeliveryStatus.REVIEW_PENDING, 1, CHECKSUM))
        );
        when(repository.findCommandByIdempotencyKey(TENANT, "reject-key")).thenReturn(Optional.empty());
        when(repository.find(TENANT, CANDIDATE_ID)).thenReturn(Optional.of(pending));
        when(repository.findCurrentModelReferences(TENANT, PLAN_ID, List.of(MODEL_ID))).thenReturn(
            Map.of(MODEL_ID, currentReference(1, CHECKSUM))
        );

        assertThatThrownBy(() ->
            service.transition(
                TENANT,
                ACTOR,
                CANDIDATE_ID,
                new TransitionCommand(8, DeliveryStatus.REJECTED, "reject-key", "self reject")
            )
        )
            .isInstanceOf(ModelReleaseCandidateException.class)
            .satisfies(error ->
                assertThat(((ModelReleaseCandidateException) error).code())
                    .isEqualTo("MODEL_RELEASE_CANDIDATE_ACTOR_SEPARATION_REQUIRED")
            );
    }

    private static CreateCandidateCommand createCommand(String key, String reason) {
        return new CreateCandidateCommand(
            PLAN_ID,
            "prod",
            List.of(new ScopeEntryCommand(MODEL_ID, 0, "core")),
            key,
            reason
        );
    }

    private static CurrentModelReference currentReference(int revision, String checksum) {
        return new CurrentModelReference(MODEL_ID, PLAN_ID, revision, checksum, ImplementationMode.DESIGNER_GENERATED);
    }

    private static CandidateView candidate(
        DeliveryStatus status,
        int version,
        DeliveryAuditView audit,
        List<EntryView> entries
    ) {
        return new CandidateView(
            CANDIDATE_ID,
            TENANT,
            PLAN_ID,
            "prod",
            status,
            version,
            "original-create-key",
            "f".repeat(64),
            audit,
            ACTOR,
            NOW,
            entries
        );
    }

    private static CandidateView materializedCandidate(
        DeliveryStatus status,
        int version,
        DeliveryAuditView audit
    ) {
        return new CandidateView(
            CANDIDATE_ID,
            TENANT,
            PLAN_ID,
            "prod",
            status,
            version,
            "original-create-key",
            "f".repeat(64),
            audit,
            ACTOR,
            NOW,
            List.of(entry(status, 1, CHECKSUM)),
            CandidateOrigin.SINGLE_MODEL_INTENT,
            "postgres:warehouse:prod",
            "postgres",
            "warehouse",
            "prod"
        );
    }

    private static EntryView entry(DeliveryStatus status, int revision, String checksum) {
        return new EntryView(
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            TENANT,
            CANDIDATE_ID,
            PLAN_ID,
            MODEL_ID,
            revision,
            checksum,
            null,
            ImplementationMode.DESIGNER_GENERATED,
            status,
            0,
            "core"
        );
    }

    private static DeliveryAuditView createdAudit() {
        return new DeliveryAuditView(ACTOR, NOW.minusSeconds(120), null, null, null, null, null, null);
    }

    private static DeliveryAuditView submittedAudit() {
        return new DeliveryAuditView(
            ACTOR,
            NOW.minusSeconds(120),
            "submitter",
            NOW.minusSeconds(90),
            null,
            null,
            null,
            null
        );
    }

    private static DeliveryAuditView fullAudit() {
        return new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(120),
            "submitter",
            NOW.minusSeconds(90),
            "approver",
            NOW.minusSeconds(60),
            "publisher",
            NOW.minusSeconds(30)
        );
    }
}
