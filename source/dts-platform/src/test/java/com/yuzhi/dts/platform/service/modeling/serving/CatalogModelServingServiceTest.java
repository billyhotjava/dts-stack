package com.yuzhi.dts.platform.service.modeling.serving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.ProjectionMutation;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.event.PlatformEventOutboxService;
import com.yuzhi.dts.platform.service.modeling.ModelExecutionTargetCatalogResolver.ResolvedCatalogTarget;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogModelServingServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T10:00:00Z");

    @Mock
    private CatalogModelServingProjectionRepository repository;

    @Mock
    private PlatformEventOutboxService outbox;

    @Test
    void projectsSuccessfulPublicationOnOneStableLogicalAssetWithoutRunningDbt() {
        CandidateView candidate = org.mockito.Mockito.mock(CandidateView.class);
        ModelSpecView model = org.mockito.Mockito.mock(ModelSpecView.class);
        ImplementationView implementation = org.mockito.Mockito.mock(ImplementationView.class);
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID candidateId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        when(candidate.tenantId()).thenReturn("tenant-a");
        when(candidate.id()).thenReturn(candidateId);
        when(candidate.version()).thenReturn(11);
        when(model.id()).thenReturn(modelId);
        when(model.revision()).thenReturn(4);
        when(model.checksum()).thenReturn("a".repeat(64));
        when(implementation.implementationRevision()).thenReturn(7);
        when(implementation.implementationChecksum()).thenReturn("b".repeat(64));
        when(repository.projectLatestPublished(any()))
            .thenReturn(new ProjectionMutation(true, false, 2));
        when(repository.promoteServing(any()))
            .thenReturn(new ProjectionMutation(false, true, 3));
        CatalogModelServingService service = new CatalogModelServingService(
            repository,
            outbox,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        service.projectSuccessfulPublication(
            candidate,
            model,
            implementation,
            new ResolvedCatalogTarget(
                "postgres-primary",
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "postgres"
            ),
            UUID.fromString("40000000-0000-0000-0000-000000000001")
        );

        ArgumentCaptor<CatalogModelServingContract.SuccessfulPublicationCommand> command =
            ArgumentCaptor.forClass(CatalogModelServingContract.SuccessfulPublicationCommand.class);
        verify(repository).projectLatestPublished(command.capture());
        verify(repository).promoteServing(any());
        assertThat(command.getValue().catalogAssetType()).isEqualTo(CatalogAssetType.SEMANTIC_MODEL);
        assertThat(command.getValue().catalogAssetKey())
            .isEqualTo(CatalogAssetKey.semanticModel(modelId.toString()));
        assertThat(command.getValue().modelRevision()).isEqualTo(4);
        assertThat(command.getValue().implementationRevision()).isEqualTo(7);
        assertThat(command.getValue().candidateId()).isEqualTo(candidateId);
        ArgumentCaptor<com.yuzhi.dts.platform.service.event.dto.PlatformEventRequest> event =
            ArgumentCaptor.forClass(com.yuzhi.dts.platform.service.event.dto.PlatformEventRequest.class);
        verify(outbox).publishInternal(event.capture());
        assertThat(event.getValue().eventId())
            .isEqualTo("model-catalog:" + modelId + ":v3:sp")
            .hasSizeLessThanOrEqualTo(80);
    }

    @Test
    void governanceOnlyPublicationAdvancesLatestWithoutTryingToPromoteServing() {
        CandidateView candidate = org.mockito.Mockito.mock(CandidateView.class);
        ModelSpecView model = org.mockito.Mockito.mock(ModelSpecView.class);
        ImplementationView implementation = org.mockito.Mockito.mock(ImplementationView.class);
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        when(candidate.tenantId()).thenReturn("tenant-a");
        when(candidate.id()).thenReturn(UUID.fromString("20000000-0000-0000-0000-000000000002"));
        when(candidate.version()).thenReturn(12);
        when(model.id()).thenReturn(modelId);
        when(model.revision()).thenReturn(5);
        when(model.checksum()).thenReturn("c".repeat(64));
        when(implementation.implementationRevision()).thenReturn(8);
        when(implementation.implementationChecksum()).thenReturn("d".repeat(64));
        when(repository.projectLatestPublished(any()))
            .thenReturn(new ProjectionMutation(true, false, 4));
        CatalogModelServingService service = new CatalogModelServingService(
            repository,
            outbox,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        ProjectionMutation mutation = service.projectLatestPublication(
            candidate,
            model,
            implementation,
            new ResolvedCatalogTarget(
                "postgres-primary",
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "postgres"
            ),
            null
        );

        assertThat(mutation.latestPublishedChanged()).isTrue();
        assertThat(mutation.servingChanged()).isFalse();
        verify(repository, never()).promoteServing(any());
    }

    @Test
    void keepsEveryEmittedProjectionOutcomeIdWithinTheOutboxBoundary() {
        CandidateView candidate = org.mockito.Mockito.mock(CandidateView.class);
        ModelSpecView model = org.mockito.Mockito.mock(ModelSpecView.class);
        ImplementationView implementation = org.mockito.Mockito.mock(ImplementationView.class);
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        when(candidate.tenantId()).thenReturn("tenant-a");
        when(candidate.id()).thenReturn(UUID.fromString("20000000-0000-0000-0000-000000000003"));
        when(model.id()).thenReturn(modelId);
        when(model.revision()).thenReturn(6);
        when(model.checksum()).thenReturn("e".repeat(64));
        when(implementation.implementationRevision()).thenReturn(9);
        when(implementation.implementationChecksum()).thenReturn("f".repeat(64));
        when(repository.projectLatestPublished(any()))
            .thenReturn(
                new ProjectionMutation(true, false, Long.MAX_VALUE, "LATEST_PUBLISHED"),
                new ProjectionMutation(false, false, Long.MAX_VALUE, "SERVING_NOT_READY"),
                new ProjectionMutation(false, false, Long.MAX_VALUE, "SERVING_STALE_REJECTED"),
                new ProjectionMutation(false, true, Long.MAX_VALUE, "SERVING_PROMOTED")
            );
        CatalogModelServingService service = new CatalogModelServingService(
            repository,
            outbox,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        ResolvedCatalogTarget target = new ResolvedCatalogTarget(
            "postgres-primary",
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            "postgres"
        );

        for (int attempt = 0; attempt < 4; attempt++) {
            service.projectLatestPublication(candidate, model, implementation, target, null);
        }

        ArgumentCaptor<com.yuzhi.dts.platform.service.event.dto.PlatformEventRequest> events =
            ArgumentCaptor.forClass(com.yuzhi.dts.platform.service.event.dto.PlatformEventRequest.class);
        verify(outbox, times(4)).publishInternal(events.capture());
        assertThat(events.getAllValues())
            .extracting(com.yuzhi.dts.platform.service.event.dto.PlatformEventRequest::eventId)
            .containsExactly(
                "model-catalog:" + modelId + ":v9223372036854775807:lp",
                "model-catalog:" + modelId + ":v9223372036854775807:snr",
                "model-catalog:" + modelId + ":v9223372036854775807:ssr",
                "model-catalog:" + modelId + ":v9223372036854775807:sp"
            )
            .allSatisfy(eventId -> assertThat(eventId).hasSizeLessThanOrEqualTo(80));
    }
}
