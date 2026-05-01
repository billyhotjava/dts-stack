package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpenMetadataColumnCacheRepository extends JpaRepository<OpenMetadataColumnCache, UUID> {
    List<OpenMetadataColumnCache> findByAssetOrderByOrdinalPositionAsc(OpenMetadataAssetCache asset);

    void deleteByAsset(OpenMetadataAssetCache asset);
}
