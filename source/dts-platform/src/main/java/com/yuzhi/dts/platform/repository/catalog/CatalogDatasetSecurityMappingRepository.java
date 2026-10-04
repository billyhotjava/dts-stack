package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetSecurityMapping;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogDatasetSecurityMappingRepository extends JpaRepository<CatalogDatasetSecurityMapping, UUID> {}

