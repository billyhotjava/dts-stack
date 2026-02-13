package com.yuzhi.dts.platform.repository.explore;

import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QueryDatasetAssetRepository extends JpaRepository<QueryDatasetAsset, UUID> {
    List<QueryDatasetAsset> findByEnabledTrueOrderByLastModifiedDateDesc();

    List<QueryDatasetAsset> findByCreatedByOrderByLastModifiedDateDesc(String createdBy);

    List<QueryDatasetAsset> findByOwnerDeptIgnoreCaseOrderByLastModifiedDateDesc(String ownerDept);

    List<QueryDatasetAsset> findByOwnerDeptIgnoreCaseAndEnabledTrueOrderByLastModifiedDateDesc(String ownerDept);
}
