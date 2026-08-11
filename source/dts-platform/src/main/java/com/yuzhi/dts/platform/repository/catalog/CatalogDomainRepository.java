package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogDomainRepository extends JpaRepository<CatalogDomain, UUID>, JpaSpecificationExecutor<CatalogDomain> {
    java.util.Optional<CatalogDomain> findFirstByNameIgnoreCase(String name);

    java.util.Optional<CatalogDomain> findFirstByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByParentId(UUID parentId);

    @Query("select d from CatalogDomain d where lower(d.code) in :codes")
    List<CatalogDomain> findByCodeLowerIn(@Param("codes") Collection<String> codes);

    Page<CatalogDomain> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCaseOrOwnerContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
        String name,
        String code,
        String owner,
        String description,
        Pageable pageable
    );
}
