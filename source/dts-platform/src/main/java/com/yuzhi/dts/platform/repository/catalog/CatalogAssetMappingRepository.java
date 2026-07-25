package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogAssetMappingRepository extends JpaRepository<CatalogAssetMapping, UUID> {
    Optional<CatalogAssetMapping> findFirstByFqnIgnoreCase(String fqn);

    @Query("select mapping from CatalogAssetMapping mapping where lower(mapping.fqn) in :fqns")
    List<CatalogAssetMapping> findByNormalizedFqnIn(@Param("fqns") Collection<String> normalizedFqns);

    Optional<CatalogAssetMapping> findFirstByLegacyDatasetId(UUID legacyDatasetId);
}
