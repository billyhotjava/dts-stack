package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityMetric;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovQualityMetricRepository extends JpaRepository<GovQualityMetric, UUID> {
    List<GovQualityMetric> findByRunId(UUID runId);
}

