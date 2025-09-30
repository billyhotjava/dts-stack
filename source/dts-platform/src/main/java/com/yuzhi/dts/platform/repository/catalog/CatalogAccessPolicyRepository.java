package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogAccessPolicyRepository extends JpaRepository<CatalogAccessPolicy, UUID> {
    Optional<CatalogAccessPolicy> findByDataset(CatalogDataset dataset);
}

