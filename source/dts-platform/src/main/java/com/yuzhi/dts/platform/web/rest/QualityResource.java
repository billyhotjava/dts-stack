package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.time.Instant;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Data Quality APIs (MVP): trigger and fetch latest summary.
 */
@RestController
@RequestMapping("/api")
@Transactional
public class QualityResource {

    private final CatalogDatasetRepository datasetRepo;
    private final AuditService audit;

    public QualityResource(CatalogDatasetRepository datasetRepo, AuditService audit) {
        this.datasetRepo = datasetRepo;
        this.audit = audit;
    }

    /**
     * POST /api/data-quality-runs/trigger?datasetId=...
     * Simulates execution of template SQLs and returns a run id with simple metrics.
     */
    @PostMapping("/data-quality-runs/trigger")
    public ApiResponse<Map<String, Object>> trigger(@RequestParam UUID datasetId) {
        CatalogDataset ds = datasetRepo.findById(datasetId).orElseThrow();
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("id", UUID.randomUUID());
        run.put("datasetId", ds.getId());
        run.put("status", "SUCCESS");
        run.put("startedAt", Instant.now().toString());
        run.put("finishedAt", Instant.now().toString());
        run.put("metrics", Map.of("rowCount", 1000, "nullViolations", 0, "uniqueViolations", 0));
        audit.audit("EXECUTE", "quality.run", String.valueOf(datasetId));
        return ApiResponses.ok(run);
    }

    /**
     * GET /api/data-quality-runs/latest?datasetId=...
     * Returns simple aggregation result for latest run (simulated).
     */
    @GetMapping("/data-quality-runs/latest")
    public ApiResponse<Map<String, Object>> latest(@RequestParam UUID datasetId) {
        CatalogDataset ds = datasetRepo.findById(datasetId).orElseThrow();
        Map<String, Object> latest = new LinkedHashMap<>();
        latest.put("datasetId", ds.getId());
        latest.put("time", Instant.now().toString());
        latest.put("summary", List.of(
            Map.of("rule", "NOT_NULL:id", "status", "PASS"),
            Map.of("rule", "UNIQUE:id", "status", "PASS")
        ));
        audit.audit("READ", "quality.run.latest", String.valueOf(datasetId));
        return ApiResponses.ok(latest);
    }
}

