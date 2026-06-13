package com.yuzhi.dts.metrics.domain.repository;

import com.yuzhi.dts.metrics.domain.MetricRollbackEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link MetricRollbackEvent}. Ordered lookup reproduces the legacy ordered event List;
 * {@code count} feeds {@code eventVersion = "rollback-" + (size + 1)}.
 * Consumed by {@code MetricModelLifecycleService} in T04.
 */
public interface MetricRollbackEventRepository extends JpaRepository<MetricRollbackEvent, UUID> {
    List<MetricRollbackEvent> findByModelIdOrderByEventOrdinalAsc(String modelId);

    long countByModelId(String modelId);
}
