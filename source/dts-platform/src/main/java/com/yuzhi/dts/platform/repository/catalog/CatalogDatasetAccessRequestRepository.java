package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogDatasetAccessRequestRepository extends JpaRepository<CatalogDatasetAccessRequest, UUID> {
    List<CatalogDatasetAccessRequest> findByRequesterUsernameIgnoreCaseOrderByCreatedDateDesc(String requesterUsername);

    List<CatalogDatasetAccessRequest> findByRequesterUsernameIgnoreCaseOrTargetUsernameIgnoreCaseOrderByCreatedDateDesc(
        String requesterUsername,
        String targetUsername
    );
}
