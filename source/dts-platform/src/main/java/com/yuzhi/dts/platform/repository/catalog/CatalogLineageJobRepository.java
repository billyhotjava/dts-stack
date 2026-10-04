package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogLineageJobRepository extends JpaRepository<CatalogLineageJob, UUID> {
    Optional<CatalogLineageJob> findByJobKey(String jobKey);

    List<CatalogLineageJob> findByIdIn(Collection<UUID> ids);
}
