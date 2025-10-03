package com.yuzhi.dts.platform.repository.service;

import com.yuzhi.dts.platform.domain.service.SvcApiMetricHourly;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SvcApiMetricHourlyRepository extends JpaRepository<SvcApiMetricHourly, UUID> {
    List<SvcApiMetricHourly> findTop48ByApiIdOrderByBucketStartDesc(UUID apiId);

    @Query("select coalesce(sum(m.callCount),0) from SvcApiMetricHourly m where m.apiId = :apiId and m.bucketStart >= :since")
    long sumCallsSince(UUID apiId, Instant since);

    @Query("select coalesce(sum(m.maskedHits),0) from SvcApiMetricHourly m where m.apiId = :apiId and m.bucketStart >= :since")
    long sumMaskedSince(UUID apiId, Instant since);

    @Query("select coalesce(sum(m.denyCount),0) from SvcApiMetricHourly m where m.apiId = :apiId and m.bucketStart >= :since")
    long sumDeniesSince(UUID apiId, Instant since);
}
