package com.yuzhi.dts.platform.service.catalog;

import java.util.Optional;
import java.util.UUID;

/** Immutable Catalog boundary used by planning and lineage consumers. */
public interface CatalogSourceReferenceReadPort {

    SourceSnapshot resolveTable(UUID tableId, String actorDepartmentId);

    /** Reads current physical evidence for an already-confirmed background execution binding. */
    SourceSnapshot resolveTableForExecution(UUID tableId);

    SourceSnapshot resolveConnectionTable(
        UUID sourceId,
        String namespace,
        String objectName,
        String actorDepartmentId
    );

    /** Reads current physical evidence for an already-confirmed background execution binding. */
    SourceSnapshot resolveConnectionTableForExecution(UUID sourceId, String namespace, String objectName);

    Optional<String> findDatasetAssetKey(UUID datasetId);

    Optional<String> findDatasetAssetKeyByTableId(UUID tableId);

    Optional<String> findDatasetAssetKey(UUID sourceId, String namespace, String objectName);

    enum SourceStatus {
        AVAILABLE,
        MISSING,
        FORBIDDEN,
        PROVIDER_ERROR,
    }

    record SourceSnapshot(
        SourceStatus status,
        CatalogAssetType assetType,
        String assetKey,
        String displayName,
        String schemaFingerprint
    ) {
        public SourceSnapshot {
            if (status == null) {
                throw new IllegalArgumentException("status is required");
            }
            if (status != SourceStatus.AVAILABLE &&
                (assetType != null || assetKey != null || displayName != null || schemaFingerprint != null)) {
                throw new IllegalArgumentException("unavailable source snapshots cannot expose metadata");
            }
            if (status == SourceStatus.AVAILABLE &&
                (assetType == null || assetKey == null || assetKey.isBlank() || displayName == null ||
                    displayName.isBlank() || schemaFingerprint == null || schemaFingerprint.isBlank())) {
                throw new IllegalArgumentException("available source snapshot is incomplete");
            }
        }

        public static SourceSnapshot available(
            CatalogAssetType assetType,
            String assetKey,
            String displayName,
            String schemaFingerprint
        ) {
            return new SourceSnapshot(SourceStatus.AVAILABLE, assetType, assetKey, displayName, schemaFingerprint);
        }

        public static SourceSnapshot missing() {
            return unavailable(SourceStatus.MISSING);
        }

        public static SourceSnapshot forbidden() {
            return unavailable(SourceStatus.FORBIDDEN);
        }

        public static SourceSnapshot providerError() {
            return unavailable(SourceStatus.PROVIDER_ERROR);
        }

        private static SourceSnapshot unavailable(SourceStatus status) {
            return new SourceSnapshot(status, null, null, null, null);
        }
    }
}
