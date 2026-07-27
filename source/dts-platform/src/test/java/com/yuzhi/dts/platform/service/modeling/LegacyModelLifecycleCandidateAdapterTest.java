package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.EventType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PublishCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LegacyModelLifecycleCandidateAdapterTest {

    private static final String TENANT = "tenant-a";
    private static final String ACTOR = "release-operator";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-07-28T14:00:00Z");

    @Mock
    private ModelSpecRepository modelSpecs;

    @Mock
    private ModelSpecSnapshotCodec codec;

    @Mock
    private ModelReleaseCandidateRepository candidates;

    @Mock
    private ModelReleaseCandidateApplicationService candidateApplication;

    @Mock
    private ModelLifecycleRepository lifecycle;

    private LegacyModelLifecycleCandidateAdapter adapter;
    private StoredModelSpec stored;
    private ModelSpecView model;

    @BeforeEach
    void setUp() {
        adapter = new LegacyModelLifecycleCandidateAdapter(
            modelSpecs,
            codec,
            candidates,
            candidateApplication,
            lifecycle,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        stored = org.mockito.Mockito.mock(StoredModelSpec.class);
        model = org.mockito.Mockito.mock(ModelSpecView.class);
        when(modelSpecs.findCurrent(TENANT, MODEL_ID)).thenReturn(Optional.of(stored));
        when(stored.currentHead()).thenReturn(true);
        when(stored.currentSnapshot()).thenReturn("{}");
        when(codec.readView("{}")).thenReturn(model);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.revision()).thenReturn(2);
        when(model.checksum()).thenReturn("b".repeat(64));
    }

    @Test
    void legacyPublishDelegatesOneEntryCandidateAndReturnsCandidateOwnedReleaseEvidence() {
        CandidateView approved = candidate(DeliveryStatus.APPROVED, List.of(entry(MODEL_ID, DeliveryStatus.APPROVED, 0)));
        CandidateView published = candidate(DeliveryStatus.PUBLISHED, List.of(entry(MODEL_ID, DeliveryStatus.PUBLISHED, 0)));
        LifecycleEventView release = org.mockito.Mockito.mock(LifecycleEventView.class);
        when(candidates.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(approved));
        when(
            candidateApplication.publish(
                TENANT,
                ACTOR,
                PLAN_ID,
                CANDIDATE_ID,
                9,
                "legacy-publish",
                "publish candidate"
            )
        )
            .thenReturn(new CommandResult(published, false, List.of()));
        when(lifecycle.findLatestEvent(TENANT, MODEL_ID, 2, EventType.RELEASE, "PUBLISHED"))
            .thenReturn(Optional.of(release));
        when(release.status()).thenReturn("PUBLISHED");
        when(release.revision()).thenReturn(2);
        when(release.modelChecksum()).thenReturn("b".repeat(64));

        var result = adapter.publish(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 2, "b".repeat(64)),
            null,
            new PublishCommand("publish candidate", "legacy-publish")
        );

        assertThat(result.release()).isEqualTo(release);
        verify(candidateApplication).publish(
            TENANT,
            ACTOR,
            PLAN_ID,
            CANDIDATE_ID,
            9,
            "legacy-publish",
            "publish candidate"
        );
    }

    @Test
    void legacyPublishIgnoresHistoricalReplacementSourceAndUsesCurrentCandidate() {
        CandidateView historical = candidate(
            DeliveryStatus.ROLLED_BACK,
            List.of(entry(MODEL_ID, DeliveryStatus.ROLLED_BACK, 0))
        );
        CandidateView approved = candidate(
            DeliveryStatus.APPROVED,
            List.of(entry(MODEL_ID, DeliveryStatus.APPROVED, 0))
        );
        CandidateView published = candidate(
            DeliveryStatus.PUBLISHED,
            List.of(entry(MODEL_ID, DeliveryStatus.PUBLISHED, 0))
        );
        LifecycleEventView release = org.mockito.Mockito.mock(LifecycleEventView.class);
        when(candidates.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(approved, historical));
        when(candidateApplication.publish(TENANT, ACTOR, PLAN_ID, CANDIDATE_ID, 9, "legacy-publish", "publish candidate"))
            .thenReturn(new CommandResult(published, false, List.of()));
        when(lifecycle.findLatestEvent(TENANT, MODEL_ID, 2, EventType.RELEASE, "PUBLISHED"))
            .thenReturn(Optional.of(release));
        when(release.status()).thenReturn("PUBLISHED");
        when(release.revision()).thenReturn(2);
        when(release.modelChecksum()).thenReturn("b".repeat(64));

        adapter.publish(
            TENANT,
            ACTOR,
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 2, "b".repeat(64)),
            null,
            new PublishCommand("publish candidate", "legacy-publish")
        );

        verify(candidateApplication).publish(
            TENANT,
            ACTOR,
            PLAN_ID,
            CANDIDATE_ID,
            9,
            "legacy-publish",
            "publish candidate"
        );
    }

    @Test
    void legacyPerModelRouteFailsClosedForBatchCandidate() {
        CandidateView batch = candidate(
            DeliveryStatus.APPROVED,
            List.of(
                entry(MODEL_ID, DeliveryStatus.APPROVED, 0),
                entry(OTHER_MODEL_ID, DeliveryStatus.APPROVED, 1)
            )
        );
        when(candidates.listForWorkbench(TENANT, PLAN_ID)).thenReturn(List.of(batch));

        assertThatThrownBy(() ->
            adapter.publish(
                TENANT,
                ACTOR,
                MODEL_ID,
                new ExpectedVersion(MODEL_ID, 2, "b".repeat(64)),
                null,
                new PublishCommand("publish candidate", "legacy-publish")
            )
        )
            .isInstanceOf(ModelSpecException.class)
            .satisfies(error ->
                assertThat(((ModelSpecException) error).code())
                    .isEqualTo("MODEL_RELEASE_LEGACY_ROUTE_BATCH_FORBIDDEN")
            );

        verify(candidateApplication, never()).publish(any(), any(), any(), any(), anyInt(), any(), any());
    }

    private static CandidateView candidate(DeliveryStatus status, List<EntryView> entries) {
        boolean released = status == DeliveryStatus.PUBLISHED || status == DeliveryStatus.ROLLED_BACK;
        DeliveryAuditView audit = new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(180),
            "submitter",
            NOW.minusSeconds(120),
            "reviewer",
            NOW.minusSeconds(60),
            released ? ACTOR : null,
            released ? NOW : null
        );
        return new CandidateView(
            CANDIDATE_ID,
            TENANT,
            PLAN_ID,
            "prod",
            status,
            status == DeliveryStatus.PUBLISHED ? 10 : 9,
            "candidate-key",
            "a".repeat(64),
            audit,
            released ? ACTOR : "reviewer",
            released ? NOW : NOW.minusSeconds(60),
            entries,
            ModelReleaseCandidateContract.CandidateOrigin.SINGLE_MODEL_INTENT,
            "postgres:warehouse/prod",
            "postgres",
            "warehouse",
            "prod"
        );
    }

    private static EntryView entry(UUID modelId, DeliveryStatus status, int sortOrder) {
        return new EntryView(
            UUID.nameUUIDFromBytes(("entry:" + modelId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            TENANT,
            CANDIDATE_ID,
            PLAN_ID,
            modelId,
            2,
            "b".repeat(64),
            UUID.nameUUIDFromBytes(("implementation:" + modelId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            ImplementationMode.DBT_MANAGED,
            status,
            sortOrder,
            "release"
        );
    }
}
