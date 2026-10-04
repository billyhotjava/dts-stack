package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityFailingRow;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovQualityFailingRowRepository extends JpaRepository<GovQualityFailingRow, UUID> {
    List<GovQualityFailingRow> findByRunId(UUID runId);
    Page<GovQualityFailingRow> findByRunId(UUID runId, Pageable pageable);
    Page<GovQualityFailingRow> findByRunIdAndColumnName(UUID runId, String columnName, Pageable pageable);
    void deleteByRunId(UUID runId);
    long countByRunId(UUID runId);
}
