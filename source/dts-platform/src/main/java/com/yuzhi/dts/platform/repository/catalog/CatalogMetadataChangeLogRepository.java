package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogMetadataChangeLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogMetadataChangeLogRepository extends JpaRepository<CatalogMetadataChangeLog, UUID> {
    Page<CatalogMetadataChangeLog> findByObjectTypeIgnoreCaseAndObjectId(String objectType, UUID objectId, Pageable pageable);
}

