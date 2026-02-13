package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.IngestionIncrementalAudit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestionIncrementalAuditRepository
    extends JpaRepository<IngestionIncrementalAudit, Long>, JpaSpecificationExecutor<IngestionIncrementalAudit> {

    List<IngestionIncrementalAudit> findByTaskIdOrderByCreatedAtDescSourceTableAsc(Long taskId);

    List<IngestionIncrementalAudit> findByTaskIdAndExecutionIdOrderByCreatedAtDescSourceTableAsc(Long taskId, Long executionId);

    void deleteByTaskId(Long taskId);
}
