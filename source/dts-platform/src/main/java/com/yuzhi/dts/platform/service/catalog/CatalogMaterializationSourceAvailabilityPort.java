package com.yuzhi.dts.platform.service.catalog;

import java.util.List;
import java.util.UUID;

/**
 * Catalog-owned transaction guard for materialization source generations.
 *
 * <p>Modeling supplies immutable warehouse-plan source descriptors or previously persisted pins;
 * catalog remains the sole owner of canonical asset identity and availability-table locking.</p>
 */
public interface CatalogMaterializationSourceAvailabilityPort {

    List<CurrentSource> lockAndRead(List<SourceDescriptor> sources);

    List<GenerationDrift> lockAndCompare(List<ExpectedSource> expectedSources);

    record SourceDescriptor(
        UUID sourceBindingId,
        String sourceType,
        String sourceId,
        String locatorJson
    ) {}

    record ExpectedSource(
        UUID sourceBindingId,
        CatalogAssetType assetType,
        String assetKey,
        String expectedStatus,
        long expectedEpoch,
        long expectedSourceSequence,
        String expectedEventId
    ) {}

    record CurrentSource(
        UUID sourceBindingId,
        CatalogAssetType assetType,
        String assetKey,
        String status,
        long epoch,
        long sourceSequence,
        String eventId
    ) {
        public boolean isAvailable() {
            return "AVAILABLE".equals(status);
        }
    }

    record GenerationDrift(
        UUID sourceBindingId,
        CatalogAssetType assetType,
        String assetKey,
        String expectedStatus,
        long expectedEpoch,
        long expectedSourceSequence,
        String expectedEventId,
        String currentStatus,
        long currentEpoch,
        long currentSourceSequence,
        String currentEventId
    ) {}
}
