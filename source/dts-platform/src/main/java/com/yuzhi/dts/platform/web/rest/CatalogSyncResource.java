package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun;
import com.yuzhi.dts.platform.repository.infra.InfraCatalogSyncRunRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.InceptorIntegrationCoordinator;
import com.yuzhi.dts.platform.service.infra.JdbcIntegrationCoordinator;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/catalog/sync")
public class CatalogSyncResource {

    private static final String CATALOG_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final InceptorIntegrationCoordinator inceptorCoordinator;
    private final JdbcIntegrationCoordinator jdbcCoordinator;
    private final AuditService auditService;
    private final InfraCatalogSyncRunRepository syncRunRepository;

    public CatalogSyncResource(
        InceptorIntegrationCoordinator inceptorCoordinator,
        JdbcIntegrationCoordinator jdbcCoordinator,
        AuditService auditService,
        InfraCatalogSyncRunRepository syncRunRepository
    ) {
        this.inceptorCoordinator = inceptorCoordinator;
        this.jdbcCoordinator = jdbcCoordinator;
        this.auditService = auditService;
        this.syncRunRepository = syncRunRepository;
    }

    public record SyncRequest(Boolean includePrimary, Boolean includeJdbc, String reason) {}

    @PostMapping
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> triggerSync(@Valid @RequestBody(required = false) SyncRequest body) {
        boolean includePrimary = body == null || body.includePrimary == null || body.includePrimary.booleanValue();
        boolean includeJdbc = body == null || body.includeJdbc == null || body.includeJdbc.booleanValue();
        String reason = body != null && body.reason != null && !body.reason.isBlank() ? body.reason.trim() : "manual";

        if (includePrimary && !inceptorCoordinator.isSyncInProgress()) {
            inceptorCoordinator.synchronizeAsync("api:" + reason);
        }
        if (includeJdbc && !jdbcCoordinator.isSyncInProgress()) {
            jdbcCoordinator.synchronizeAsync("api:" + reason);
        }

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "触发元数据采集");
        auditPayload.put("includePrimary", includePrimary);
        auditPayload.put("includeJdbc", includeJdbc);
        auditPayload.put("reason", reason);
        auditService.auditAction("CATALOG_SYNC_TRIGGER", AuditStage.SUCCESS, reason, auditPayload);
        return ApiResponses.ok(statusPayload());
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        auditService.auditAction("CATALOG_SYNC_STATUS_VIEW", AuditStage.SUCCESS, "status", Map.of("summary", "查看采集状态"));
        return ApiResponses.ok(statusPayload());
    }

    @GetMapping("/runs")
    public ApiResponse<List<Map<String, Object>>> listRuns(
        @RequestParam(name = "integration", required = false, defaultValue = "INCEPTOR") String integration,
        @RequestParam(name = "limit", required = false, defaultValue = "20") int limit,
        @RequestParam(name = "includeDetails", required = false, defaultValue = "false") boolean includeDetails
    ) {
        String normalized = StringUtils.hasText(integration) ? integration.trim().toUpperCase(java.util.Locale.ROOT) : "INCEPTOR";
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<InfraCatalogSyncRun> runs = syncRunRepository.findTop100ByIntegrationOrderByStartedAtDesc(normalized);
        List<Map<String, Object>> payload = runs
            .stream()
            .limit(safeLimit)
            .map(run -> toDto(run, includeDetails))
            .toList();
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看采集运行历史");
        auditPayload.put("integration", normalized);
        auditPayload.put("limit", safeLimit);
        auditPayload.put("includeDetails", includeDetails);
        auditService.auditAction("CATALOG_SYNC_RUN_LIST", AuditStage.SUCCESS, normalized, auditPayload);
        return ApiResponses.ok(payload);
    }

    private Map<String, Object> statusPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("primary", Map.of(
            "inProgress", inceptorCoordinator.isSyncInProgress(),
            "last", inceptorCoordinator.currentStatus()
        ));
        payload.put("jdbc", Map.of(
            "inProgress", jdbcCoordinator.isSyncInProgress(),
            "last", jdbcCoordinator.currentStatus()
        ));
        return payload;
    }

    private Map<String, Object> toDto(InfraCatalogSyncRun run, boolean includeDetails) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (run == null) {
            return dto;
        }
        UUID id = run.getId();
        if (id != null) {
            dto.put("id", id.toString());
        }
        dto.put("integration", run.getIntegration());
        dto.put("reason", run.getReason());
        dto.put("status", run.getStatus());
        dto.put("startedAt", run.getStartedAt());
        dto.put("finishedAt", run.getFinishedAt());
        dto.put("error", run.getError());
        dto.put("catalogDatasetCountBefore", run.getCatalogDatasetCountBefore());
        dto.put("catalogDatasetCountAfter", run.getCatalogDatasetCountAfter());
        dto.put("tablesDiscovered", run.getTablesDiscovered());
        dto.put("datasetsCreated", run.getDatasetsCreated());
        dto.put("datasetsUpdated", run.getDatasetsUpdated());
        dto.put("datasetsRemoved", run.getDatasetsRemoved());
        dto.put("tablesCreated", run.getTablesCreated());
        dto.put("columnsImported", run.getColumnsImported());
        if (includeDetails) {
            dto.put("detailsJson", run.getDetailsJson());
        }
        return dto;
    }
}
