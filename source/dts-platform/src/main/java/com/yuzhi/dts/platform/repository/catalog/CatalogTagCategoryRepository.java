package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogTagCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogTagCategoryRepository extends JpaRepository<CatalogTagCategory, UUID> {
    boolean existsByCode(String code);

    Optional<CatalogTagCategory> findByCode(String code);

    boolean existsByParentId(UUID parentId);

    List<CatalogTagCategory> findAllByOrderBySortOrderAscNameAsc();
}
