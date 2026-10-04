package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CatalogDataProductRepository extends JpaRepository<CatalogDataProduct, UUID> {
	boolean existsByCode(String code);
}
