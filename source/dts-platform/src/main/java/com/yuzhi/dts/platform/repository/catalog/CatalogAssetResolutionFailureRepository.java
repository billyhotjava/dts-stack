package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogAssetResolutionFailureRepository extends JpaRepository<CatalogAssetResolutionFailure, UUID> {
    List<CatalogAssetResolutionFailure> findAllByOrderByRequestedAtDesc(Pageable pageable);

    List<CatalogAssetResolutionFailure> findByRequestedAtGreaterThanEqualOrderByRequestedAtDesc(Instant since, Pageable pageable);
}
