package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface OpenMetadataAssetCacheRepository
    extends JpaRepository<OpenMetadataAssetCache, UUID>, JpaSpecificationExecutor<OpenMetadataAssetCache> {
    Optional<OpenMetadataAssetCache> findFirstByFqnIgnoreCase(String fqn);

    Optional<OpenMetadataAssetCache> findFirstByOmEntityId(String omEntityId);
}
