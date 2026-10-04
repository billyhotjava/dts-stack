package com.yuzhi.dts.platform.repository.infra;

import com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InfraSchemaDiscoverCacheRepository extends JpaRepository<InfraSchemaDiscoverCache, UUID> {
    Optional<InfraSchemaDiscoverCache> findFirstByDataSourceIdAndCacheKeyAndEnabledTrueOrderByRefreshedAtDesc(
        UUID dataSourceId,
        String cacheKey
    );

    List<InfraSchemaDiscoverCache> findTop20ByDataSourceIdAndEnabledTrueOrderByRefreshedAtDesc(UUID dataSourceId);
}
