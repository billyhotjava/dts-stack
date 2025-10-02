package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetJob;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogDatasetJobRepository extends JpaRepository<CatalogDatasetJob, UUID> {
    List<CatalogDatasetJob> findTop10ByDatasetOrderByCreatedDateDesc(CatalogDataset dataset);
    Optional<CatalogDatasetJob> findTopByDatasetAndJobTypeOrderByCreatedDateDesc(CatalogDataset dataset, String jobType);
}
