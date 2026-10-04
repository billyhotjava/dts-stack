package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogLifecycleRequestRepository
    extends JpaRepository<CatalogLifecycleRequest, UUID>, JpaSpecificationExecutor<CatalogLifecycleRequest> {}

