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
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.event.PlatformEventOutboxService;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventRequest;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RollbackCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CandidateRollbackCommitServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String ACTOR = "release-operator";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-28T13:00:00Z");

    @Mock
    private CandidatePublicationEvidenceRepository evidence;

    @Mock
    private ModelSpecRepository modelSpecs;

    @Mock
    private ModelSpecSnapshotCodec codec;

    @Mock
    private ModelLifecyclePublicationService lifecyclePublication;

    @Mock
    private CandidatePublicationRepository publications;

    @Mock
    private PlatformEventOutboxService outbox;

    @Mock
    private ModelReleaseCandidateService candidateCommands;

    @Mock
    private AuditService auditService;

    private CandidateRollbackCommitService service;

    @BeforeEach
    void setUp() {
        service = new CandidateRollbackCommitService(
            evidence,
            modelSpecs,
            codec,
            lifecyclePublication,
            publications,
            outbox,
            candidateCommands,
            Clock.fixed(NOW, ZoneOffset.UTC),
            auditService
        );
    }

    @Test
    void rollsBackModelsAssetsBindingOutboxAndCandidateInOneOrderedTransaction() {
        CandidateView published = candidate(DeliveryStatus.PUBLISHED, 11);
        CandidateView rolledBack = candidate(DeliveryStatus.ROLLED_BACK, 12);
        PublicationEntryEvidence observed = observation();
        StoredModelSpec stored = org.mockito.Mockito.mock(StoredModelSpec.class);
        ModelSpecView model = org.mockito.Mockito.mock(ModelSpecView.class);
        LifecycleEventView rollback = org.mockito.Mockito.mock(LifecycleEventView.class);
        when(evidence.requireCurrent(published, true)).thenReturn(List.of(observed));
        when(modelSpecs.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored));
        when(stored.currentHead()).thenReturn(true);
        when(stored.currentSnapshot()).thenReturn("{}");
        when(codec.readView("{}")).thenReturn(model);
        when(model.id()).thenReturn(MODEL_ID);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.status()).thenReturn(ModelStatus.PUBLISHED);
        when(model.revision()).thenReturn(2);
        when(model.checksum()).thenReturn("b".repeat(64));
        when(lifecyclePublication.rollback(eq(TENANT), eq(ACTOR), eq(model), any(RollbackCommand.class), eq(NOW)))
            .thenReturn(rollback);
        when(
            candidateCommands.transitionWithinAuditedCommit(
                eq(TENANT),
                eq(ACTOR),
                eq(CANDIDATE_ID),
                any(TransitionCommand.class)
            )
        )
            .thenReturn(new CommandResult(rolledBack, false, List.of()));

        CommandResult result = service.rollback(
            TENANT,
            ACTOR,
            published,
            "rollback-key",
            "withdraw published model"
        );

        assertThat(result.candidate().status()).isEqualTo(DeliveryStatus.ROLLED_BACK);
        InOrder order = inOrder(
            evidence,
            lifecyclePublication,
            publications,
            outbox,
            candidateCommands,
            auditService
        );
        order.verify(evidence).requireCurrent(published, true);
        order
            .verify(lifecyclePublication)
            .rollback(eq(TENANT), eq(ACTOR), eq(model), any(RollbackCommand.class), eq(NOW));
        order.verify(publications).rollbackModel(published, observed, model, rollback, ACTOR, NOW);
        verify(publications, org.mockito.Mockito.never()).rebuildManualBindingAfterRollback(any(), any(), any());
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
                eq("MODEL_RELEASE_CANDIDATE_ROLLBACK"),
                eq(AuditStage.SUCCESS),
                eq(CANDIDATE_ID.toString()),
                any()
            );
    }

    @Test
    void replayedRollbackTransitionDoesNotDuplicateTheExplicitAudit() {
        CandidateView published = candidate(DeliveryStatus.PUBLISHED, 11);
        CandidateView rolledBack = candidate(DeliveryStatus.ROLLED_BACK, 12);
        when(evidence.requireCurrent(published, true)).thenReturn(List.of());
        when(
            candidateCommands.transitionWithinAuditedCommit(
                eq(TENANT),
                eq(ACTOR),
                eq(CANDIDATE_ID),
                any(TransitionCommand.class)
            )
        )
            .thenReturn(new CommandResult(rolledBack, true, List.of()));

        CommandResult result = service.rollback(
            TENANT,
            ACTOR,
            published,
            "rollback-key",
            "withdraw published model"
        );

        assertThat(result.replayed()).isTrue();
        verify(auditService, never()).auditAction(
            anyString(),
            any(),
            anyString(),
            any()
        );
    }

    private static CandidateView candidate(DeliveryStatus status, int version) {
        DeliveryAuditView audit = new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(180),
            "submitter",
            NOW.minusSeconds(120),
            "reviewer",
            NOW.minusSeconds(60),
            ACTOR,
            NOW.minusSeconds(30)
        );
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
            NOW.minusSeconds(30),
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
            ModelReleaseCandidateContract.CandidateOrigin.SINGLE_MODEL_INTENT,
            "postgres:warehouse/prod",
            "postgres",
            "warehouse",
            "prod"
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
