package com.yuzhi.dts.platform.service.catalog;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only catalog projection over the canonical modeling owner.
 *
 * <p>The catalog module consumes this port instead of importing modeling entities or repositories.
 */
public interface CanonicalModelIdentityReadPort {

    Optional<ModelIdentity> findById(UUID id);

    Optional<ModelIdentity> findDbtModel(String reference);

    Optional<ModelIdentity> findSemanticModel(String reference);

    Map<String, ModelIdentity> findDbtModelsByResourceNames(Set<String> resourceNames);

    enum ModelIdentityType {
        DBT_MODEL,
        SEMANTIC_MODEL,
    }

    record ModelIdentity(
        ModelIdentityType type,
        UUID assetId,
        UUID modelSpecId,
        UUID implementationId,
        String modelName,
        int modelRevision,
        String dbtUniqueId
    ) {
        public ModelIdentity {
            if (type == null || assetId == null || modelSpecId == null || modelName == null || modelName.isBlank()) {
                throw new IllegalArgumentException("canonical model identity is incomplete");
            }
            if (
                type == ModelIdentityType.DBT_MODEL &&
                (implementationId == null || dbtUniqueId == null || dbtUniqueId.isBlank())
            ) {
                throw new IllegalArgumentException("dbt model identity requires implementationId and dbtUniqueId");
            }
        }

        public CatalogAssetType assetType() {
            return type == ModelIdentityType.DBT_MODEL
                ? CatalogAssetType.DBT_MODEL
                : CatalogAssetType.SEMANTIC_MODEL;
        }

        public String assetKey() {
            return type == ModelIdentityType.DBT_MODEL
                ? CatalogAssetKey.dbtModel(dbtUniqueId, modelName)
                : CatalogAssetKey.semanticModel(modelSpecId.toString());
        }

        public String sourceRef() {
            return type == ModelIdentityType.DBT_MODEL
                ? "model-implementation:" + implementationId
                : "model-spec:" + modelSpecId;
        }

        public String contractVersion() {
            return "r" + modelRevision;
        }
    }
}
