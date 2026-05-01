package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogAssetMappingRepository extends JpaRepository<CatalogAssetMapping, UUID> {
    Optional<CatalogAssetMapping> findFirstByFqnIgnoreCase(String fqn);

    Optional<CatalogAssetMapping> findFirstByLegacyDatasetId(UUID legacyDatasetId);
}
