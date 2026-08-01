package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository.PublicationEntryEvidence;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository.PublishedModelBinding;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.event.PlatformEventOutboxService;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventRequest;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PublishCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CandidatePublicationCommitServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String ACTOR = "release-operator";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID RELEASE_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    @Mock
    private CandidatePublicationEvidenceRepository evidence;

    @Mock
    private ModelSpecRepository modelSpecs;

    @Mock
    private ModelLifecycleRepository lifecycle;

    @Mock
    private ModelSpecSnapshotCodec codec;

    @Mock
    private ModelLifecyclePublicationService lifecyclePublication;

    @Mock
    private CandidatePublicationRepository publicationRepository;

    @Mock
    private PlatformEventOutboxService outbox;

    @Mock
    private ModelReleaseCandidateService candidateCommands;

    @Mock
    private ModelExecutionTargetCatalogResolver targetResolver;

    @Mock
    private AuditService auditService;

    private CandidatePublicationCommitService service;

    @BeforeEach
    void setUp() {
        service = new CandidatePublicationCommitService(
            evidence,
            modelSpecs,
            lifecycle,
            codec,
            lifecyclePublication,
            publicationRepository,
            outbox,
            candidateCommands,
            targetResolver,
            Clock.fixed(NOW, ZoneOffset.UTC),
            auditService
        );
    }

    @Test
    void publishesEveryExactModelSnapshotBeforeBindingOutboxAndFinalCandidateTransition() {
        CandidateView publishing = candidate(DeliveryStatus.PUBLISHING, 10, audit(false));
        CandidateView published = candidate(DeliveryStatus.PUBLISHED, 11, audit(true));
        PublicationEntryEvidence observed = observation();
        StoredModelSpec stored = org.mockito.Mockito.mock(StoredModelSpec.class);
        ModelSpecView draft = org.mockito.Mockito.mock(ModelSpecView.class);
        ModelSpecView publishedModel = org.mockito.Mockito.mock(ModelSpecView.class);
        ImplementationView implementation = org.mockito.Mockito.mock(ImplementationView.class);
        LifecycleEventView release = org.mockito.Mockito.mock(LifecycleEventView.class);
        PublishedModelBinding binding = new PublishedModelBinding(
            MODEL_ID,
            RELEASE_ID,
            2,
            observed.dbtUniqueId(),
            observed.targetIdentifier(),
            observed.artifactChecksum(),
            observed.dependencySnapshotChecksum()
        );
        ResolvedCatalogTarget target = target();

        when(targetResolver.resolve(publishing)).thenReturn(target);
        when(evidence.requireCurrent(publishing, true)).thenReturn(List.of(observed));
        when(modelSpecs.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored));
        when(stored.currentHead()).thenReturn(true);
        when(stored.currentSnapshot()).thenReturn("{}");
        when(codec.readView("{}")).thenReturn(draft);
        when(draft.id()).thenReturn(MODEL_ID);
        when(draft.planId()).thenReturn(PLAN_ID);
        when(draft.status()).thenReturn(ModelStatus.DRAFT);
        when(draft.revision()).thenReturn(2);
        when(draft.checksum()).thenReturn("b".repeat(64));
        when(draft.implementationMode()).thenReturn(ImplementationMode.DBT_MANAGED);
        when(lifecycle.findImplementation(TENANT, MODEL_ID)).thenReturn(Optional.of(implementation));
        when(implementation.modelSpecId()).thenReturn(MODEL_ID);
        when(implementation.planId()).thenReturn(PLAN_ID);
        when(implementation.revision()).thenReturn(2);
        when(implementation.modelChecksum()).thenReturn("b".repeat(64));
        when(implementation.ownership()).thenReturn(ImplementationMode.DBT_MANAGED);
        when(implementation.implementationRevision()).thenReturn(3);
        when(implementation.implementationChecksum()).thenReturn("c".repeat(64));
        when(implementation.dbtUniqueId()).thenReturn(observed.dbtUniqueId());
        when(implementation.status()).thenReturn("ACTIVE");
        when(
            lifecyclePublication.publish(
                eq(TENANT),
                eq(ACTOR),
                eq(draft),
                eq(implementation),
                any(PublishCommand.class),
                eq(NOW)
            )
        )
            .thenReturn(new ModelLifecyclePublicationService.Publication(publishedModel, release));
        when(
            publicationRepository.registerModel(
                publishing,
                target,
                observed,
                publishedModel,
                release,
                ACTOR,
                NOW
            )
        )
            .thenReturn(binding);
        when(
            candidateCommands.transitionWithinAuditedCommit(
                eq(TENANT),
                eq(ACTOR),
                eq(CANDIDATE_ID),
                any(TransitionCommand.class)
            )
        )
            .thenReturn(new CommandResult(published, false, List.of()));

        CommandResult result = service.commit(
            TENANT,
            ACTOR,
            publishing,
            "publish-key",
            "publish approved release"
        );

        assertThat(result.candidate().status()).isEqualTo(DeliveryStatus.PUBLISHED);
        InOrder order = inOrder(
            evidence,
            lifecyclePublication,
            publicationRepository,
            outbox,
            candidateCommands,
            auditService
        );
        order.verify(evidence).requireCurrent(publishing, true);
        order
            .verify(lifecyclePublication)
            .publish(eq(TENANT), eq(ACTOR), eq(draft), eq(implementation), any(PublishCommand.class), eq(NOW));
        order
            .verify(publicationRepository)
            .registerModel(
                publishing,
                target,
                observed,
                publishedModel,
                release,
                ACTOR,
                NOW
            );
        order
            .verify(publicationRepository)
            .rebuildManualBinding(publishing, List.of(binding), ACTOR, NOW);
        order.verify(outbox).publishInternal(any(PlatformEventRequest.class));
        order
            .verify(candidateCommands)
            .transitionWithinAuditedCommit(
                eq(TENANT),
                eq(ACTOR),
                eq(CANDIDATE_ID),
                any(TransitionCommand.class)
            );
        order
            .verify(auditService)
            .auditAction(
                eq("MODEL_RELEASE_CANDIDATE_PUBLISH"),
                eq(AuditStage.SUCCESS),
                eq(CANDIDATE_ID.toString()),
                any()
            );

        ArgumentCaptor<TransitionCommand> transition = ArgumentCaptor.forClass(TransitionCommand.class);
        verify(candidateCommands)
            .transitionWithinAuditedCommit(
                eq(TENANT),
                eq(ACTOR),
                eq(CANDIDATE_ID),
                transition.capture()
            );
        assertThat(transition.getValue().expectedVersion()).isEqualTo(10);
        assertThat(transition.getValue().targetStatus()).isEqualTo(DeliveryStatus.PUBLISHED);
        assertThat(transition.getValue().idempotencyKey())
            .isEqualTo("candidate-publication-commit:" + CANDIDATE_ID + ":v10");
        ArgumentCaptor<PlatformEventRequest> event = ArgumentCaptor.forClass(PlatformEventRequest.class);
        verify(outbox).publishInternal(event.capture());
        assertThat(event.getValue().eventId())
            .isEqualTo("model-release-candidate-published:" + CANDIDATE_ID + ":v10");
        assertThat(event.getValue().eventType()).isEqualTo("MODEL_RELEASE_CANDIDATE_PUBLISHED");
        assertThat(event.getValue().action()).isEqualTo("PUBLISH");
        assertThat(event.getValue().auditActionCode()).isEqualTo("MODEL_RELEASE_CANDIDATE_PUBLISH");
        assertThat(event.getValue().payload())
            .containsEntry("scheduleMode", "MANUAL_ONLY")
            .containsEntry("entryCount", 1);
    }

    @Test
    void partialRetryUsesDedicatedRegistrationRetryAuditAction() {
        CandidateView partial = candidate(DeliveryStatus.PARTIAL, 10, audit(true));
        CandidateView published = candidate(DeliveryStatus.PUBLISHED, 11, audit(true));
        when(targetResolver.resolve(partial)).thenReturn(target());
        when(evidence.requireCurrent(partial, true)).thenReturn(List.of());
        when(
            candidateCommands.transitionWithinAuditedCommit(
                eq(TENANT),
                eq(ACTOR),
                eq(CANDIDATE_ID),
                any(TransitionCommand.class)
            )
        )
            .thenReturn(new CommandResult(published, false, List.of()));

        service.commit(TENANT, ACTOR, partial, "registration-retry-key", "retry registration");

        ArgumentCaptor<PlatformEventRequest> event = ArgumentCaptor.forClass(PlatformEventRequest.class);
        verify(outbox).publishInternal(event.capture());
        assertThat(event.getValue().eventType())
            .isEqualTo("MODEL_RELEASE_CANDIDATE_PUBLICATION_RETRIED");
        assertThat(event.getValue().action()).isEqualTo("RETRY");
        assertThat(event.getValue().auditActionCode())
            .isEqualTo("MODEL_RELEASE_CANDIDATE_PUBLICATION_RETRY");
        verify(auditService).auditAction(
            eq("MODEL_RELEASE_CANDIDATE_PUBLICATION_RETRY"),
            eq(AuditStage.SUCCESS),
            eq(CANDIDATE_ID.toString()),
            any()
        );
    }

    @Test
    void replayedFinalTransitionDoesNotDuplicateTheExplicitAudit() {
        CandidateView publishing = candidate(DeliveryStatus.PUBLISHING, 10, audit(false));
        CandidateView published = candidate(DeliveryStatus.PUBLISHED, 11, audit(true));
        when(targetResolver.resolve(publishing)).thenReturn(target());
        when(evidence.requireCurrent(publishing, true)).thenReturn(List.of());
        when(
            candidateCommands.transitionWithinAuditedCommit(
                eq(TENANT),
                eq(ACTOR),
                eq(CANDIDATE_ID),
                any(TransitionCommand.class)
            )
        )
            .thenReturn(new CommandResult(published, true, List.of()));

        CommandResult result = service.commit(
            TENANT,
            ACTOR,
            publishing,
            "publish-key",
            "publish approved release"
        );

        assertThat(result.replayed()).isTrue();
        verify(auditService, never()).auditAction(
            anyString(),
            any(),
            anyString(),
            any()
        );
    }

    private static CandidateView candidate(DeliveryStatus status, int version, DeliveryAuditView audit) {
        return new CandidateView(
            CANDIDATE_ID,
            TENANT,
            PLAN_ID,
            "prod",
            status,
            version,
            "candidate-key",
            "a".repeat(64),
            audit,
            ACTOR,
            NOW,
            List.of(
                new EntryView(
                    UUID.fromString("60000000-0000-0000-0000-000000000001"),
                    TENANT,
                    CANDIDATE_ID,
                    PLAN_ID,
                    MODEL_ID,
                    2,
                    "b".repeat(64),
                    UUID.fromString("50000000-0000-0000-0000-000000000001"),
                    ImplementationMode.DBT_MANAGED,
                    status,
                    0,
                    "release"
                )
            ),
            ModelReleaseCandidateContract.CandidateOrigin.BATCH_WORKBENCH,
            "postgres:warehouse/prod",
            "postgres",
            "warehouse",
            "prod"
        );
    }

    private static ResolvedCatalogTarget target() {
        return new ResolvedCatalogTarget(
            "postgres:warehouse/prod",
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            "postgres"
        );
    }

    private static DeliveryAuditView audit(boolean published) {
        return new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(180),
            "submitter",
            NOW.minusSeconds(120),
            "reviewer",
            NOW.minusSeconds(60),
            published ? ACTOR : null,
            published ? NOW : null
        );
    }

    private static PublicationEntryEvidence observation() {
        return new PublicationEntryEvidence(
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            MODEL_ID,
            2,
            "b".repeat(64),
            3,
            "c".repeat(64),
            "model.finance.fct_payment",
            "fct_payment",
            "d".repeat(64),
            "e".repeat(64),
            UUID.fromString("70000000-0000-0000-0000-000000000001"),
            UUID.fromString("80000000-0000-0000-0000-000000000001"),
            UUID.fromString("90000000-0000-0000-0000-000000000001"),
            "postgres",
            "warehouse",
            "finance",
            "fct_payment",
            ExpectedRelationType.TABLE,
            "f".repeat(64),
            NOW.minusSeconds(240)
        );
    }
}
