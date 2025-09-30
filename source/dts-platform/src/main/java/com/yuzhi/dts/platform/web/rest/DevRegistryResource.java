package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.audit.AuditService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Dev registry: ETL submit/status (registry only, no compute).
 */
@RestController
@RequestMapping("/api")
@Transactional
public class DevRegistryResource {

    private static final Map<UUID, Map<String, Object>> RUNS = new ConcurrentHashMap<>();

    private final AuditService audit;

    public DevRegistryResource(AuditService audit) {
        this.audit = audit;
    }

    @PostMapping("/etl-jobs/{id}/submit")
    public ApiResponse<Map<String, Object>> submit(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        UUID runId = UUID.randomUUID();
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("id", runId);
        run.put("jobId", id);
        run.put("status", "SUBMITTED");
        run.put("submittedAt", Instant.now().toString());
        run.put("note", "Registered only. No computation executed.");
        RUNS.put(runId, run);
        audit.audit("SUBMIT", "etl.job", id.toString());
        return ApiResponses.ok(run);
    }

    @GetMapping("/job-runs/{id}/status")
    public ApiResponse<Map<String, Object>> status(@PathVariable UUID id) {
        Map<String, Object> run = RUNS.get(id);
        if (run == null) {
            run = new LinkedHashMap<>();
            run.put("id", id);
            run.put("status", "NOT_FOUND");
        }
        audit.audit("READ", "etl.run.status", id.toString());
        return ApiResponses.ok(run);
    }
}

