package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogTagCategory;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Transaction-scoped PostgreSQL lock for serializing one built-in catalog-tag package install.
 */
public interface CatalogTagInstallLockRepository extends Repository<CatalogTagCategory, UUID> {
    @Query(
        value = "select pg_advisory_xact_lock(hashtextextended(:packageCode, 7171001))",
        nativeQuery = true
    )
    Object acquirePackageLock(@Param("packageCode") String packageCode);
}
