package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovComplianceBatchItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovComplianceBatchItemRepository extends JpaRepository<GovComplianceBatchItem, UUID> {
    List<GovComplianceBatchItem> findByBatchId(UUID batchId);
    List<GovComplianceBatchItem> findByDatasetId(UUID datasetId);
}

