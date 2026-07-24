package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.DryRunReport;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.MigrationAccessException;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.MigrationExecution;
import com.yuzhi.dts.platform.service.modeling.migration.DimensionDefinitionMigrationService.MigrationRollback;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Server-tenant-only REST boundary for the dimension-definition migration ledger. */
@RestController
@RequestMapping("/api/modeling/migrations/dimension-definitions")
@PreAuthorize("hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)")
public class DimensionDefinitionMigrationResource {

    private static final Set<String> EXECUTE_FIELDS = Set.of("expectedChecksum");

    private final DimensionDefinitionMigrationService service;
    private final AuditService audit;
    private final WarehousePlanActorProvider actorProvider;
    private final String serverTenantId;

    public DimensionDefinitionMigrationResource(
        DimensionDefinitionMigrationService service,
        AuditService audit,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.audit = audit;
        this.actorProvider = actorProvider;
        this.serverTenantId = serverTenantId;
    }

    @PostMapping("/dry-run")
    public ApiResponse<DryRunReport> dryRun() {
        DryRunReport report = service.dryRun(serverTenant(), actorId());
        audit.auditAction(
            "DIMENSION_DEFINITION_MIGRATION_DRY_RUN",
            AuditStage.SUCCESS,
            report.batchId(),
            Map.of("checksum", report.checksum(), "total", report.total(), "automatic", report.automatic())
        );
        return ApiResponses.ok(report);
    }

    @PostMapping("/{batchId}/execute")
    public ApiResponse<MigrationExecution> execute(@PathVariable String batchId, @RequestBody(required = false) JsonNode body) {
        ExecuteRequest request = decodeExecute(body);
        MigrationExecution result = service.execute(serverTenant(), batchId, request.expectedChecksum(), actorId());
        audit.auditAction(
            "DIMENSION_DEFINITION_MIGRATION_EXECUTE",
            AuditStage.SUCCESS,
            result.batchId(),
            Map.of(
                "checksum", result.checksum(),
                "replayed", result.replayed(),
                "mappingsCreated", result.mappingsCreated(),
                "definitionsCreated", result.definitionsCreated()
            )
        );
        return ApiResponses.ok(result);
    }

    @PostMapping("/{batchId}/rollback")
    public ApiResponse<MigrationRollback> rollback(
        @PathVariable String batchId,
        @RequestBody(required = false) JsonNode body
    ) {
        ExecuteRequest request = decodeExecute(body);
        MigrationRollback result = service.rollback(
            serverTenant(),
            batchId,
            request.expectedChecksum(),
            actorId()
        );
        audit.auditAction(
            "DIMENSION_DEFINITION_MIGRATION_ROLLBACK",
            AuditStage.SUCCESS,
            result.batchId(),
            Map.of(
                "checksum",
                result.checksum(),
                "replayed",
                result.replayed(),
                "mappingsDeleted",
                result.mappingsDeleted(),
                "definitionsDeleted",
                result.definitionsDeleted(),
                "definitionsRetained",
                result.definitionsRetained()
            )
        );
        return ApiResponses.ok(result);
    }

    @ExceptionHandler(MigrationAccessException.class)
    ResponseEntity<ApiResponse<Object>> forbidden(MigrationAccessException exception) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), "MIGRATION_ACCESS_REQUIRED", null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Object>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity
            .badRequest()
            .body(new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), "MIGRATION_REQUEST_INVALID", null));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiResponse<Object>> conflict(IllegalStateException exception) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), "MIGRATION_GATE_BLOCKED", null));
    }

    private ExecuteRequest decodeExecute(JsonNode body) {
        if (body == null || !body.isObject()) throw new IllegalArgumentException("Execute request must be a JSON object");
        LinkedHashSet<String> unexpected = new LinkedHashSet<>();
        body.fieldNames().forEachRemaining(field -> {
            if (!EXECUTE_FIELDS.contains(field)) unexpected.add(field);
        });
        if (!unexpected.isEmpty()) throw new IllegalArgumentException("Execute request contains unsupported fields: " + String.join(", ", unexpected));
        JsonNode checksum = body.get("expectedChecksum");
        if (checksum == null || !checksum.isTextual() || !checksum.asText().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expectedChecksum must be a lowercase SHA-256 checksum");
        }
        return new ExecuteRequest(checksum.asText());
    }

    private String serverTenant() {
        if (serverTenantId == null || serverTenantId.isBlank()) throw new MigrationAccessException("server tenant id is required");
        return serverTenantId.trim();
    }

    private String actorId() {
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        if (actor == null || actor.ownerId() == null || actor.ownerId().isBlank()) {
            throw new MigrationAccessException("authenticated actor is required");
        }
        return actor.ownerId().trim();
    }

    public record ExecuteRequest(String expectedChecksum) {}
}
