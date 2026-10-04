package com.yuzhi.dts.copilot.analytics.service.elt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "dts.elt.enabled", havingValue = "true")
public class EltSyncService {

    private static final Logger log = LoggerFactory.getLogger(EltSyncService.class);

    private final Map<String, EltSyncJob> jobRegistry;
    private final EltWatermarkService watermarkService;

    public EltSyncService(List<EltSyncJob> jobs, EltWatermarkService watermarkService) {
        this.jobRegistry = jobs.stream()
                .collect(Collectors.toMap(EltSyncJob::getTargetTable, Function.identity()));
        this.watermarkService = watermarkService;
        log.info("Registered {} ELT sync jobs: {}", jobRegistry.size(), jobRegistry.keySet());
    }

    public void runSync(String targetTable) {
        EltSyncJob job = jobRegistry.get(targetTable);
        if (job == null) {
            log.warn("No ELT sync job registered for target table: {}", targetTable);
            return;
        }

        String batchId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Instant lastWatermark = watermarkService.getWatermark(targetTable);
        long startedAt = System.currentTimeMillis();

        watermarkService.markRunning(targetTable, batchId);
        try {
            int rowCount = job.sync(lastWatermark, batchId);
            Instant newWatermark = Instant.now();
            int durationMs = Math.toIntExact(System.currentTimeMillis() - startedAt);
            watermarkService.markCompleted(targetTable, batchId, newWatermark, rowCount, durationMs);
        } catch (Exception e) {
            watermarkService.markFailed(targetTable, batchId, e.getMessage());
            log.error("ELT sync failed for table={}, batchId={}", targetTable, batchId, e);
        }
    }

    public void runAll() {
        log.info("Starting ELT sync for all registered jobs");
        for (String targetTable : jobRegistry.keySet()) {
            runSync(targetTable);
        }
        log.info("Completed ELT sync for all registered jobs");
    }

    public List<String> getRegisteredTables() {
        return jobRegistry.keySet().stream().sorted().toList();
    }
}
