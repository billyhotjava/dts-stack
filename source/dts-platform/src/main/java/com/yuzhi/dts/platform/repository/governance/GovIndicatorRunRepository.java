package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorRun;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovIndicatorRunRepository extends JpaRepository<GovIndicatorRun, UUID> {

    List<GovIndicatorRun> findByIndicatorIdOrderByRunAtDesc(UUID indicatorId);

    Optional<GovIndicatorRun> findTopByIndicatorIdOrderByRunAtDesc(UUID indicatorId);

    List<GovIndicatorRun> findByIndicatorIdAndRunAtBetween(UUID indicatorId, Instant from, Instant to);

    List<GovIndicatorRun> findTop3ByIndicatorIdOrderByRunAtDesc(UUID indicatorId);

    List<GovIndicatorRun> findByAlertLevelIn(List<String> levels);
}
