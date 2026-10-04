package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationMapping;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogClassificationMappingRepository extends JpaRepository<CatalogClassificationMapping, UUID> {
    List<CatalogClassificationMapping> findByPlatformLevelIgnoreCase(String platformLevel);
}

