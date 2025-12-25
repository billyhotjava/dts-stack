package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.InceptorIntegrationCoordinator;
import com.yuzhi.dts.platform.service.infra.JdbcIntegrationCoordinator;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/sync")
public class CatalogSyncResource {

    private static final String CATALOG_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final InceptorIntegrationCoordinator inceptorCoordinator;
    private final JdbcIntegrationCoordinator jdbcCoordinator;
    private final AuditService auditService;

    public CatalogSyncResource(
        InceptorIntegrationCoordinator inceptorCoordinator,
        JdbcIntegrationCoordinator jdbcCoordinator,
        AuditService auditService
    ) {
        this.inceptorCoordinator = inceptorCoordinator;
        this.jdbcCoordinator = jdbcCoordinator;
        this.auditService = auditService;
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

        auditService.audit("SYNC", "catalog.sync", "trigger:" + reason);
        return ApiResponses.ok(statusPayload());
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponses.ok(statusPayload());
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
}
