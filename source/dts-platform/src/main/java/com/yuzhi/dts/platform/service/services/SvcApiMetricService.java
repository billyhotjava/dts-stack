package com.yuzhi.dts.platform.service.services;

import com.yuzhi.dts.platform.domain.service.SvcApiMetricHourly;
import com.yuzhi.dts.platform.repository.service.SvcApiMetricHourlyRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SvcApiMetricService {

    private final SvcApiMetricHourlyRepository repository;

    public SvcApiMetricService(SvcApiMetricHourlyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void recordCall(UUID apiId, int maskedHits, boolean denied) {
        if (apiId == null) {
            return;
        }
        Instant bucket = currentHourBucket();
        SvcApiMetricHourly metric = repository.findByApiIdAndBucketStart(apiId, bucket).orElseGet(() -> {
            SvcApiMetricHourly created = new SvcApiMetricHourly();
            created.setApiId(apiId);
            created.setBucketStart(bucket);
            created.setCallCount(0L);
            created.setMaskedHits(0);
            created.setDenyCount(0);
            created.setQpsPeak(0);
            return created;
        });
        metric.setCallCount((metric.getCallCount() != null ? metric.getCallCount() : 0L) + 1L);
        if (maskedHits > 0) {
            metric.setMaskedHits((metric.getMaskedHits() != null ? metric.getMaskedHits() : 0) + maskedHits);
        }
        if (denied) {
            metric.setDenyCount((metric.getDenyCount() != null ? metric.getDenyCount() : 0) + 1);
        }
        repository.save(metric);
    }

    private Instant currentHourBucket() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime bucket = now.withMinute(0).withSecond(0).withNano(0);
        return bucket.toInstant();
    }
}

