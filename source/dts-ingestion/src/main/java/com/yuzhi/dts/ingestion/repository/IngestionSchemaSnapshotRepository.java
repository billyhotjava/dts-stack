package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.IngestionSchemaSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionSchemaSnapshotRepository extends JpaRepository<IngestionSchemaSnapshot, Long> {

    List<IngestionSchemaSnapshot> findByTask_IdOrderByCreatedDateDesc(Long taskId);

    List<IngestionSchemaSnapshot> findByExecution_IdOrderByCreatedDateDesc(Long executionId);

    Optional<IngestionSchemaSnapshot> findFirstByTask_IdAndSourceTableAndOdsTableOrderByCreatedDateDesc(
        Long taskId,
        String sourceTable,
        String odsTable
    );
}
