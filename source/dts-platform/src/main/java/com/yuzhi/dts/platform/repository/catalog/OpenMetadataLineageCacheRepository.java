package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.OpenMetadataLineageCache;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpenMetadataLineageCacheRepository extends JpaRepository<OpenMetadataLineageCache, UUID> {
    List<OpenMetadataLineageCache> findByFromOmEntityIdOrToOmEntityId(String fromOmEntityId, String toOmEntityId);

    List<OpenMetadataLineageCache> findByFromFqnIgnoreCaseOrToFqnIgnoreCase(String fromFqn, String toFqn);

    Optional<OpenMetadataLineageCache> findFirstByFromFqnIgnoreCaseAndToFqnIgnoreCaseAndSourceIgnoreCase(
        String fromFqn,
        String toFqn,
        String source
    );

    List<OpenMetadataLineageCache> findByFromOmEntityIdOrToOmEntityIdOrFromFqnIgnoreCaseOrToFqnIgnoreCase(
        String fromOmEntityId,
        String toOmEntityId,
        String fromFqn,
        String toFqn
    );
}
