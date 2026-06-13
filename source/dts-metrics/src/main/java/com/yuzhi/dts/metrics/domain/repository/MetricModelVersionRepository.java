package com.yuzhi.dts.metrics.domain.repository;

import com.yuzhi.dts.metrics.domain.MetricModelVersion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link MetricModelVersion}. Ordered lookup reproduces the legacy ordered version List;
 * {@code count} feeds {@code nextVersion()}; {@code save} triggers the {@code @Version} optimistic lock.
 * Consumed by {@code MetricModelLifecycleService} in T04.
 */
public interface MetricModelVersionRepository extends JpaRepository<MetricModelVersion, UUID> {
    List<MetricModelVersion> findByModelIdOrderByVersionOrdinalAsc(String modelId);

    long countByModelId(String modelId);
}
