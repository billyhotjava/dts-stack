package com.yuzhi.dts.platform.repository.explore;

import com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QueryDatasetVersionRepository extends JpaRepository<QueryDatasetVersion, UUID> {
    List<QueryDatasetVersion> findByDataset_IdOrderByVersionNoDesc(UUID datasetId);

    Optional<QueryDatasetVersion> findByDataset_IdAndVersionNo(UUID datasetId, Integer versionNo);

    @Query("select coalesce(max(v.versionNo), 0) from QueryDatasetVersion v where v.dataset.id = :datasetId")
    int findMaxVersionNo(@Param("datasetId") UUID datasetId);
}
