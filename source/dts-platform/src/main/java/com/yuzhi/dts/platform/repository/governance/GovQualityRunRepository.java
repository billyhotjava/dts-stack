package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovQualityRunRepository extends JpaRepository<GovQualityRun, UUID> {
    List<GovQualityRun> findByDatasetId(UUID datasetId, Pageable pageable);
    List<GovQualityRun> findByRuleId(UUID ruleId, Pageable pageable);
    List<GovQualityRun> findByCreatedDateAfterOrderByCreatedDateAsc(Instant since);
    Optional<GovQualityRun> findFirstByDatasetIdOrderByCreatedDateDesc(UUID datasetId);
    long countByRuleIdAndCreatedDateAfter(UUID ruleId, Instant since);

    List<GovQualityRun> findTop100ByStatusOrderByCreatedDateDesc(String status);
}
