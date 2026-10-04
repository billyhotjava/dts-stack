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

class CatalogModelSemanticSyncQueryDatasetProjectionTest {

    @Test
    void marksProjectionSyncedOnlyAfterAnalyticsAndCanonicalDatasetConverge() {
        Instant now = Instant.parse("2026-08-19T02:00:00Z");
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        CatalogModelServingProjectionRepository repository = mock(CatalogModelServingProjectionRepository.class);
        CatalogModelSemanticPayloadFactory payloadFactory = mock(CatalogModelSemanticPayloadFactory.class);
        AnalyticsSemanticPublishClient analytics = mock(AnalyticsSemanticPublishClient.class);
        ModelQueryDatasetProjectionService datasets = mock(ModelQueryDatasetProjectionService.class);
        var projection = new ModelServingProjection(
            "tenant-a",
            modelId,
            CatalogAssetType.SEMANTIC_MODEL,
            "semantic-model:" + modelId,
            null,
            null,
            7,
            "SYNC_PENDING",
            now
        );
        var candidate = new SyncCandidate(projection, 0);
        var payload = mock(CatalogModelSemanticContract.PublishPayload.class);
        when(repository.claimSyncCandidates(any(Integer.class), any(Instant.class), any(java.time.Duration.class)))
            .thenReturn(List.of(candidate));
        when(payloadFactory.create(candidate)).thenReturn(payload);
        when(datasets.project(candidate, payload))
            .thenReturn(new ModelQueryDatasetProjectionService.ProjectionResult(true, true, UUID.randomUUID(), 1));
        when(repository.markSyncSucceeded("tenant-a", modelId, 7)).thenReturn(true);
        CatalogModelSemanticSyncService service = new CatalogModelSemanticSyncService(
            repository,
            payloadFactory,
            analytics,
            datasets,
            Clock.fixed(now, ZoneOffset.UTC)
        );

        var result = service.synchronizeOnce();

        verify(analytics).publish(payload);
        verify(datasets).project(candidate, payload);
        verify(repository).markSyncSucceeded("tenant-a", modelId, 7);
        assertThat(result.succeeded()).isEqualTo(1);
    }
}
