package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogAssetExtensionRepository extends JpaRepository<CatalogAssetExtension, UUID> {
    Optional<CatalogAssetExtension> findFirstByOmAsset(OpenMetadataAssetCache omAsset);

    Optional<CatalogAssetExtension> findFirstByLegacyDatasetId(UUID legacyDatasetId);

    List<CatalogAssetExtension> findByOmAssetIn(List<OpenMetadataAssetCache> assets);
}
