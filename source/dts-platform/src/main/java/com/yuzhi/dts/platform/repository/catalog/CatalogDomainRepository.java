package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogDomainRepository extends JpaRepository<CatalogDomain, UUID> {
    java.util.Optional<CatalogDomain> findFirstByNameIgnoreCase(String name);
}
