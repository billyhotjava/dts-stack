package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CandidatePublicationCoordinatorTest {

    private static final String TENANT = "tenant-a";
    private static final String ACTOR = "release-operator";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    @Mock
    private CandidatePublicationCommitService commits;

    @Mock
    private CandidatePublicationFailureService failures;

    private CandidatePublicationCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new CandidatePublicationCoordinator(commits, failures);
    }

    @Test
    void returnsAtomicCommitResultWithoutFailureConvergence() {
        CandidateView publishing = candidate(DeliveryStatus.PUBLISHING, 10, false);
        CommandResult published = new CommandResult(candidate(DeliveryStatus.PUBLISHED, 11, true), false, List.of());
        when(commits.commit(TENANT, ACTOR, publishing, "publish-key", "publish")).thenReturn(published);

        CommandResult result = coordinator.publish(TENANT, ACTOR, publishing, "publish-key", "publish");

        assertThat(result).isEqualTo(published);
    }

    @Test
    void rollsBackAtomicWorkThenPersistsPartialInASeparateFailureTransaction() {
        CandidateView publishing = candidate(DeliveryStatus.PUBLISHING, 10, false);
        CommandResult partial = new CommandResult(candidate(DeliveryStatus.PARTIAL, 11, true), false, List.of());
        IllegalStateException registrationFailure = new IllegalStateException("catalog write failed");
        when(commits.commit(TENANT, ACTOR, publishing, "publish-key", "publish"))
            .thenThrow(registrationFailure);
        when(failures.markPartial(TENANT, ACTOR, publishing, registrationFailure)).thenReturn(partial);

        CommandResult result = coordinator.publish(TENANT, ACTOR, publishing, "publish-key", "publish");

        assertThat(result).isEqualTo(partial);
        verify(failures).markPartial(TENANT, ACTOR, publishing, registrationFailure);
    }

    private static CandidateView candidate(DeliveryStatus status, int version, boolean published) {
        DeliveryAuditView audit = new DeliveryAuditView(
            "creator",
            NOW.minusSeconds(180),
            "submitter",
            NOW.minusSeconds(120),
            "reviewer",
            NOW.minusSeconds(60),
            published ? ACTOR : null,
            published ? NOW : null
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
            published ? ACTOR : "reviewer",
            published ? NOW : NOW.minusSeconds(60),
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
}
