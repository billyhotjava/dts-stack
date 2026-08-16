package com.yuzhi.dts.platform.service.modeling.serving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.SyncCandidate;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogModelSemanticSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void marksClaimedProjectionSyncedAfterAnalyticsAcceptsIt() {
        CatalogModelServingProjectionRepository repository = mock(CatalogModelServingProjectionRepository.class);
        CatalogModelSemanticPayloadFactory payloadFactory = mock(CatalogModelSemanticPayloadFactory.class);
        AnalyticsSemanticPublishClient client = mock(AnalyticsSemanticPublishClient.class);
        SyncCandidate candidate = candidate(0);
        CatalogModelSemanticContract.PublishPayload payload = mock(CatalogModelSemanticContract.PublishPayload.class);
        when(repository.claimSyncCandidates(any(Integer.class), any(Instant.class), any(java.time.Duration.class)))
            .thenReturn(List.of(candidate));
        when(payloadFactory.create(candidate)).thenReturn(payload);
        when(repository.markSyncSucceeded("tenant-a", MODEL_ID, 7)).thenReturn(true);
        CatalogModelSemanticSyncService service = new CatalogModelSemanticSyncService(
            repository,
            payloadFactory,
            client,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = service.synchronizeOnce();

        verify(client).publish(payload);
        verify(repository).markSyncSucceeded("tenant-a", MODEL_ID, 7);
        assertThat(result).isEqualTo(new CatalogModelSemanticSyncService.SyncResult(1, 1, 0, 0));
    }

    @Test
    void recordsBoundedRetryAndStopsSchedulingAfterFifthFailure() {
        CatalogModelServingProjectionRepository repository = mock(CatalogModelServingProjectionRepository.class);
        CatalogModelSemanticPayloadFactory payloadFactory = mock(CatalogModelSemanticPayloadFactory.class);
        AnalyticsSemanticPublishClient client = mock(AnalyticsSemanticPublishClient.class);
        SyncCandidate candidate = candidate(4);
        when(repository.claimSyncCandidates(any(Integer.class), any(Instant.class), any(java.time.Duration.class)))
            .thenReturn(List.of(candidate));
        when(payloadFactory.create(candidate)).thenThrow(
            new CatalogModelSemanticPayloadFactory.SemanticPayloadException("CATALOG_MODEL_SEMANTIC_CLASSIFICATION_NOT_READY")
        );
        when(repository.markSyncFailed("tenant-a", MODEL_ID, 7, "CATALOG_MODEL_SEMANTIC_CLASSIFICATION_NOT_READY", null))
            .thenReturn(true);
        CatalogModelSemanticSyncService service = new CatalogModelSemanticSyncService(
            repository,
            payloadFactory,
            client,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = service.synchronizeOnce();

        verify(repository).markSyncFailed(
            "tenant-a",
            MODEL_ID,
            7,
            "CATALOG_MODEL_SEMANTIC_CLASSIFICATION_NOT_READY",
            null
        );
        assertThat(result).isEqualTo(new CatalogModelSemanticSyncService.SyncResult(1, 0, 1, 0));
    }

    private static SyncCandidate candidate(int attempts) {
        ModelServingProjection projection = new ModelServingProjection(
            "tenant-a",
            MODEL_ID,
            CatalogAssetType.SEMANTIC_MODEL,
            "semantic-model:" + MODEL_ID,
            null,
            null,
            7,
            "SYNC_PENDING",
            NOW
        );
        return new SyncCandidate(projection, attempts);
    }
}
