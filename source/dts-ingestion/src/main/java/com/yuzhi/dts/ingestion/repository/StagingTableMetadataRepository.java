package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.StagingTableMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the StagingTableMetadata entity.
 */
@Repository
public interface StagingTableMetadataRepository extends JpaRepository<StagingTableMetadata, UUID> {

    /**
     * Find metadata by table name
     */
    Optional<StagingTableMetadata> findByTableName(String tableName);

    /**
     * Find all ACTIVE staging tables where lastAccessedAt + ttlHours < cutoff
     */
    List<StagingTableMetadata> findByStatusAndLastAccessedAtBefore(String status, Instant cutoff);
}
