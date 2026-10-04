package com.yuzhi.dts.platform.service.catalog;

import java.util.List;
import java.util.UUID;

/** Catalog-owned publication authorization boundary; no persistence entity escapes this contract. */
public interface CatalogPublicationPolicyPort {

    Decision authorizeUpsert(UUID sourceId, String schemaName, String tableName);

    Decision authorizeArchive(UUID assetId);

    enum Action {
        CREATE,
        UPDATE,
        ARCHIVE,
    }

    enum Status {
        ALLOWED,
        FORBIDDEN,
        POLICY_UNAVAILABLE,
        AMBIGUOUS,
        PUBLISHED_ASSET_REQUIRED,
    }

    record Decision(Status status, Action action, UUID assetId, List<UUID> matchingAssetIds) {
        public Decision {
            if (status == null || action == null || assetId == null) {
                throw new IllegalArgumentException("publication decision is incomplete");
            }
            matchingAssetIds = matchingAssetIds == null ? List.of() : List.copyOf(matchingAssetIds);
        }

        public boolean allowed() {
            return status == Status.ALLOWED;
        }
    }
}
