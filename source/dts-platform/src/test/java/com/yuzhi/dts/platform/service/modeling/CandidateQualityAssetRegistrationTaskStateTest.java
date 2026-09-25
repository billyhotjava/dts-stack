package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogPhysicalDatasetObservationAdapter;
import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository.State;
import com.yuzhi.dts.platform.repository.modeling.ModelAssetRegistrationTaskRepository.TaskView;
import com.yuzhi.dts.platform.service.modeling.CandidateQualityAssetRegistrationService.RegistrationState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateQualityAssetRegistrationTaskStateTest {

    private static final Instant NOW = Instant.parse("2026-09-25T02:00:00Z");
    private static final UUID CANDIDATE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    private final ModelAssetRegistrationTaskRepository tasks = mock(ModelAssetRegistrationTaskRepository.class);
    private final CandidateView candidate = mock(CandidateView.class);

    CandidateQualityAssetRegistrationTaskStateTest() {
        when(candidate.tenantId()).thenReturn("tenant-a");
        when(candidate.id()).thenReturn(CANDIDATE_ID);
        when(candidate.version()).thenReturn(6);
        when(candidate.planId()).thenReturn(PLAN_ID);
        when(candidate.environment()).thenReturn("prod");
    }

    private CandidateQualityAssetRegistrationService service(ModelAssetRegistrationTaskRepository taskRepository) {
        return new CandidateQualityAssetRegistrationService(
            mock(CandidatePublicationEvidenceRepository.class),
            mock(ModelExecutionTargetCatalogResolver.class),
            mock(ModelSpecReader.class),
            mock(ModelClassificationPublishGate.class),
            mock(CandidatePublicationRepository.class),
            mock(CatalogDatasetRepository.class),
            mock(CatalogPhysicalDatasetObservationAdapter.class),
            Clock.fixed(NOW, ZoneOffset.UTC),
            taskRepository
        );
    }

    @Test
    void aConfirmedBuildQueuesItsOwnCandidateVersion() {
        service(tasks).requestRegistration(candidate);

        verify(tasks).enqueue("tenant-a", CANDIDATE_ID, 6, PLAN_ID, "prod", NOW);
    }

    @Test
    void candidatesBuiltBeforeTheTaskTableAreUntrackedAndKeepInlineRegistration() {
        var withoutTasks = service(null);

        withoutTasks.requestRegistration(candidate);

        assertThat(withoutTasks.registrationStatus(candidate).state()).isEqualTo(RegistrationState.UNTRACKED);
    }

    @Test
    void aBuiltCandidateWithoutATaskIsMissing() {
        when(tasks.findByCandidate("tenant-a", CANDIDATE_ID)).thenReturn(Optional.empty());

        assertThat(service(tasks).registrationStatus(candidate).state()).isEqualTo(RegistrationState.MISSING);
    }

    @Test
    void taskStateAndFailureDetailsAreReportedAsIs() {
        when(tasks.findByCandidate("tenant-a", CANDIDATE_ID)).thenReturn(Optional.of(new TaskView(
            UUID.randomUUID(), "tenant-a", CANDIDATE_ID, 6, PLAN_ID, "prod", State.FAILED, 3,
            "CATALOG_UNAVAILABLE", "目录服务不可用", NOW, null, NOW
        )));

        var status = service(tasks).registrationStatus(candidate);

        assertThat(status.state()).isEqualTo(RegistrationState.FAILED);
        assertThat(status.errorCode()).isEqualTo("CATALOG_UNAVAILABLE");
        assertThat(status.errorMessage()).isEqualTo("目录服务不可用");
        assertThat(status.attempts()).isEqualTo(3);
    }

    @Test
    void aManualRegistrationCompletesTheTask() {
        service(tasks).recordRegistered(candidate);

        verify(tasks).markSucceeded("tenant-a", CANDIDATE_ID, NOW);
        verify(tasks, never()).enqueue(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any(), any());
    }
}
