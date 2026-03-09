package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.etl.RollbackCascadeService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rollback")
public class RollbackProxyResource {

    private static final Logger LOG = LoggerFactory.getLogger(RollbackProxyResource.class);

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final IngestionServiceClient ingestionClient;
    private final RollbackCascadeService cascadeService;

    public RollbackProxyResource(
        IngestionServiceClient ingestionClient,
        RollbackCascadeService cascadeService
    ) {
        this.ingestionClient = ingestionClient;
        this.cascadeService = cascadeService;
    }

    @PostMapping("/analyze")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> analyze(@RequestBody Map<String, Object> request) {
        ApiResponse<Object> result = ingestionClient.rollbackAnalyze(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/execute")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> execute(@RequestBody Map<String, Object> request) {
        ApiResponse<Object> result = ingestionClient.rollbackExecute(request);

        // After successful execute, trigger platform-side cascade cleanup for Level 3
        int level = 0;
        Object levelObj = request.getOrDefault("level", 0);
        if (levelObj instanceof Number number) {
            level = number.intValue();
        } else if (levelObj != null) {
            try {
                level = Integer.parseInt(String.valueOf(levelObj).trim());
            } catch (NumberFormatException ignored) {
                // keep default 0
            }
        }

        if (level == 3 && result != null && result.getStatus() >= 200 && result.getStatus() < 300) {
            try {
                cascadeService.cascadeCleanup(request, result.getData());
            } catch (Exception ex) {
                LOG.warn("[rollback-cascade] platform cleanup partially failed: {}", ex.getMessage());
            }
        }

        // If Level 2 with rebuildDbt, trigger dbt full-refresh
        if (level == 2 && Boolean.TRUE.equals(request.get("rebuildDbt"))) {
            try {
                cascadeService.triggerDbtFullRefresh(request);
            } catch (Exception ex) {
                LOG.warn("[rollback-cascade] dbt full-refresh trigger failed: {}", ex.getMessage());
            }
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/audit-log")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getAuditLog(
        @RequestParam(required = false) Long taskId,
        @RequestParam(required = false) UUID dataSourceId
    ) {
        ApiResponse<Object> result = ingestionClient.getRollbackAuditLog(taskId, dataSourceId);
        return ResponseEntity.ok(result);
    }
}
