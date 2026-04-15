package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogColumnSchemaRepository extends JpaRepository<CatalogColumnSchema, UUID> {
    List<CatalogColumnSchema> findByTable(CatalogTableSchema table);

    List<CatalogColumnSchema> findByTableIn(Collection<CatalogTableSchema> tables);

    void deleteByTable(CatalogTableSchema table);

    // Serializes concurrent writers to the same parent table's columns.
    // Released automatically at transaction commit/rollback.
    @Query(value = "SELECT pg_advisory_xact_lock(:key)", nativeQuery = true)
    Object acquireTableColumnsLock(@Param("key") long key);
}
