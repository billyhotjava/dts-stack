package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.domain.EltSyncWatermark;
import com.yuzhi.dts.copilot.analytics.repository.EltSyncWatermarkRepository;
import com.yuzhi.dts.copilot.analytics.service.elt.EltSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics/elt")
@ConditionalOnProperty(name = "dts.elt.enabled", havingValue = "true")
public class EltMonitorResource {

    private static final Logger log = LoggerFactory.getLogger(EltMonitorResource.class);

    private final EltSyncWatermarkRepository watermarkRepository;
    private final EltSyncService eltSyncService;

    public EltMonitorResource(EltSyncWatermarkRepository watermarkRepository,
                              EltSyncService eltSyncService) {
        this.watermarkRepository = watermarkRepository;
        this.eltSyncService = eltSyncService;
    }

    public record EltStatusResponse(
            String targetTable,
            String syncStatus,
            Instant lastSyncTime,
            Integer lastSyncRows,
            Long lastSyncDurationMs,
            Long syncDelayMinutes,
            boolean isHealthy
    ) {}

    @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<EltStatusResponse>> listStatus() {
        List<EltSyncWatermark> watermarks = watermarkRepository.findAll();
        List<EltStatusResponse> responses = watermarks.stream()
                .map(this::toStatusResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PostMapping(path = "/trigger/{table}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> triggerSync(@PathVariable String table) {
        if (!eltSyncService.getRegisteredTables().contains(table)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No sync job found for table: " + table));
        }

        String batchId = UUID.randomUUID().toString();
        CompletableFuture.runAsync(() -> {
            try {
                eltSyncService.runSync(table);
            } catch (Exception e) {
                log.error("Triggered sync failed for table {}: {}", table, e.getMessage(), e);
            }
        });

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("table", table);
        body.put("batchId", batchId);
        body.put("status", "TRIGGERED");
        return ResponseEntity.accepted().body(body);
    }

    @PostMapping(path = "/reset/{table}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> resetWatermark(@PathVariable String table) {
        Optional<EltSyncWatermark> wm = watermarkRepository.findByTargetTable(table);
        if (wm.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No watermark found for table: " + table));
        }

        EltSyncWatermark watermark = wm.get();
        watermark.setLastWatermark(Instant.EPOCH);
        watermark.setSyncStatus("RESET");
        watermark.setLastSyncTime(Instant.now());
        watermark.setErrorMessage(null);
        watermarkRepository.save(watermark);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("table", table);
        body.put("status", "RESET");
        body.put("lastWatermark", Instant.EPOCH.toString());
        return ResponseEntity.ok(body);
    }

    private EltStatusResponse toStatusResponse(EltSyncWatermark wm) {
        Instant lastSyncTime = wm.getLastSyncTime();
        long delayMinutes = lastSyncTime == null ? 0 : Duration.between(lastSyncTime, Instant.now()).toMinutes();
        boolean healthy = !"FAILED".equals(wm.getSyncStatus()) && (lastSyncTime == null || delayMinutes <= 120);
        Long durationMs = wm.getLastSyncDurationMs() != null ? wm.getLastSyncDurationMs().longValue() : null;
        return new EltStatusResponse(
                wm.getTargetTable(),
                wm.getSyncStatus(),
                wm.getLastSyncTime(),
                wm.getLastSyncRows(),
                durationMs,
                delayMinutes,
                healthy
        );
    }
}
