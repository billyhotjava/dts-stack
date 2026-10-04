package com.yuzhi.dts.copilot.analytics.service.elt;

import com.yuzhi.dts.copilot.analytics.domain.EltSyncWatermark;
import com.yuzhi.dts.copilot.analytics.repository.EltSyncWatermarkRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "dts.elt.enabled", havingValue = "true")
public class EltWatermarkService {

    private static final Logger log = LoggerFactory.getLogger(EltWatermarkService.class);

    private static final String STATUS_IDLE = "IDLE";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private static final Duration HEALTH_THRESHOLD = Duration.ofHours(3);

    private final EltSyncWatermarkRepository watermarkRepository;

    public EltWatermarkService(EltSyncWatermarkRepository watermarkRepository) {
        this.watermarkRepository = watermarkRepository;
    }

    @Transactional(readOnly = true)
    public Instant getWatermark(String targetTable) {
        return watermarkRepository.findByTargetTable(targetTable)
                .map(EltSyncWatermark::getLastWatermark)
                .orElse(Instant.EPOCH);
    }

    @Transactional
    public void markRunning(String targetTable, String batchId) {
        EltSyncWatermark watermark = getOrCreate(targetTable);
        watermark.setSyncStatus(STATUS_RUNNING);
        watermark.setErrorMessage(null);
        watermarkRepository.save(watermark);
        log.debug("Marked {} as RUNNING, batchId={}", targetTable, batchId);
    }

    @Transactional
    public void markCompleted(String targetTable, String batchId, Instant newWatermark, int rowCount, int durationMs) {
        EltSyncWatermark watermark = getOrCreate(targetTable);
        watermark.setSyncStatus(STATUS_COMPLETED);
        watermark.setLastWatermark(newWatermark);
        watermark.setLastSyncTime(Instant.now());
        watermark.setLastSyncRows(rowCount);
        watermark.setLastSyncDurationMs(durationMs);
        watermark.setErrorMessage(null);
        watermarkRepository.save(watermark);
        log.info("Marked {} as COMPLETED, batchId={}, rows={}, watermark={}", targetTable, batchId, rowCount, newWatermark);
    }

    @Transactional
    public void markFailed(String targetTable, String batchId, String errorMessage) {
        EltSyncWatermark watermark = getOrCreate(targetTable);
        watermark.setSyncStatus(STATUS_FAILED);
        watermark.setLastSyncTime(Instant.now());
        watermark.setErrorMessage(errorMessage != null && errorMessage.length() > 2000
                ? errorMessage.substring(0, 2000) : errorMessage);
        watermarkRepository.save(watermark);
        log.warn("Marked {} as FAILED, batchId={}, error={}", targetTable, batchId, errorMessage);
    }

    @Transactional(readOnly = true)
    public boolean isHealthy(String targetTable) {
        return watermarkRepository.findByTargetTable(targetTable)
                .map(w -> {
                    if (STATUS_FAILED.equals(w.getSyncStatus())) {
                        return false;
                    }
                    Instant lastSyncTime = w.getLastSyncTime();
                    if (lastSyncTime == null) {
                        return true;
                    }
                    return Duration.between(lastSyncTime, Instant.now()).compareTo(HEALTH_THRESHOLD) < 0;
                })
                .orElse(true);
    }

    private EltSyncWatermark getOrCreate(String targetTable) {
        return watermarkRepository.findByTargetTable(targetTable)
                .orElseGet(() -> {
                    EltSyncWatermark w = new EltSyncWatermark();
                    w.setTargetTable(targetTable);
                    w.setLastWatermark(Instant.EPOCH);
                    w.setSyncStatus(STATUS_IDLE);
                    w.setLastSyncRows(0);
                    w.setLastSyncDurationMs(0);
                    return watermarkRepository.save(w);
                });
    }
}
