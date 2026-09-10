package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsTable;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsTableRepository extends JpaRepository<AnalyticsTable, Long> {
    List<AnalyticsTable> findAllByDatabaseIdOrderBySchemaNameAscNameAsc(Long databaseId);

    List<AnalyticsTable> findAllByDatabaseIdAndSchemaNameOrderByNameAsc(Long databaseId, String schemaName);

    Optional<AnalyticsTable> findByDatabaseIdAndSchemaNameAndName(Long databaseId, String schemaName, String name);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from AnalyticsTable t where t.id = :id")
    Optional<AnalyticsTable> lockForSemanticPublish(@org.springframework.data.repository.query.Param("id") Long id);

    long deleteByDatabaseId(Long databaseId);
}

