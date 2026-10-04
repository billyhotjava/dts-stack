package com.yuzhi.dts.platform.service.catalog;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Read-only projection of the latest durable governance quality run for catalog assets. */
public interface CatalogAssetQualityStatusReader {

    Map<UUID, QualityStatusSnapshot> readLatest(Collection<UUID> datasetIds);

    record QualityStatusSnapshot(UUID datasetId, UUID runId, String status, Instant observedAt) {}
}
