package com.yuzhi.dts.platform.web.rest.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionException;
import com.yuzhi.dts.platform.service.catalog.CatalogTagGovernanceGuard;
import com.yuzhi.dts.platform.service.catalog.CatalogTagMigrationConflictException;
import com.yuzhi.dts.platform.service.catalog.CatalogTagMigrationService;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagMigrationExecution;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagMigrationReport;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagMigrationRollback;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import com.yuzhi.dts.platform.web.rest.ResultStatus;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/tag-migrations")
public class CatalogTagMigrationResource {

    private static final Logger log = LoggerFactory.getLogger(
        CatalogTagMigrationResource.class
    );
    private static final Set<String> COMMAND_FIELDS = Set.of("expectedChecksum");
    private static final String DRY_RUN_ACTION = "CATALOG_TAG_MIGRATION_DRY_RUN";
    private static final String EXECUTE_ACTION = "CATALOG_TAG_MIGRATION_EXECUTE";
    private static final String ROLLBACK_ACTION = "CATALOG_TAG_MIGRATION_ROLLBACK";

    private final CatalogTagMigrationService service;
    private final CatalogTagGovernanceGuard governanceGuard;
    private final AuditService audit;

    public CatalogTagMigrationResource(
        CatalogTagMigrationService service,
        CatalogTagGovernanceGuard governanceGuard,
        AuditService audit
    ) {
        this.service = service;
        this.governanceGuard = governanceGuard;
        this.audit = audit;
    }

    @PostMapping("/dry-run")
    public ApiResponse<CatalogTagMigrationReport> dryRun() {
        CatalogTagMigrationReport report;
        try {
            governanceGuard.requireMaintainer();
            report = service.dryRun();
        } catch (RuntimeException exception) {
            auditFailure(DRY_RUN_ACTION, "dry-run", null, exception);
            throw exception;
        }
        auditSafely(
            DRY_RUN_ACTION,
            AuditStage.SUCCESS,
            report.batchId(),
            payload(report),
            "AUDIT_WRITE_FAILED"
        );
        return ApiResponses.ok(report);
    }

    @PostMapping("/{batchId}/execute")
    public ApiResponse<CatalogTagMigrationExecution> execute(
        @PathVariable String batchId,
        @RequestBody(required = false) JsonNode body
    ) {
        String checksum = null;
        CatalogTagMigrationExecution execution;
        try {
            governanceGuard.requireMaintainer();
            checksum = decodeChecksum(body);
            execution = service.execute(batchId, checksum, currentActor());
        } catch (RuntimeException exception) {
            auditFailure(EXECUTE_ACTION, batchId, checksum, exception);
            throw exception;
        }
        auditSafely(
            EXECUTE_ACTION,
            AuditStage.SUCCESS,
            execution.batchId(),
            payload(execution),
            "AUDIT_WRITE_FAILED"
        );
        return ApiResponses.ok(execution);
    }

    @PostMapping("/{batchId}/rollback")
    public ApiResponse<CatalogTagMigrationRollback> rollback(
        @PathVariable String batchId,
        @RequestBody(required = false) JsonNode body
    ) {
        String checksum = null;
        CatalogTagMigrationRollback rollback;
        try {
            governanceGuard.requireMaintainer();
            checksum = decodeChecksum(body);
            rollback = service.rollback(batchId, checksum, currentActor());
        } catch (RuntimeException exception) {
            auditFailure(ROLLBACK_ACTION, batchId, checksum, exception);
            throw exception;
        }
        auditSafely(
            ROLLBACK_ACTION,
            AuditStage.SUCCESS,
            rollback.batchId(),
            payload(rollback),
            "AUDIT_WRITE_FAILED"
        );
        return ApiResponses.ok(rollback);
    }

    @ExceptionHandler(CatalogAssetTagPermissionException.class)
    ResponseEntity<ApiResponse<Object>> forbidden(
        CatalogAssetTagPermissionException exception
    ) {
        return ResponseEntity
            .status(exception.getStatusCode())
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    exception.getReason(),
                    exception.reasonCode(),
                    null
                )
            );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Object>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity
            .badRequest()
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    exception.getMessage(),
                    "MIGRATION_REQUEST_INVALID",
                    null
                )
            );
    }

    @ExceptionHandler(CatalogTagMigrationConflictException.class)
    ResponseEntity<ApiResponse<Object>> conflict(
        CatalogTagMigrationConflictException exception
    ) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    exception.getMessage(),
                    "MIGRATION_GATE_BLOCKED",
                    null
                )
            );
    }

    private String decodeChecksum(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("migration request must be a JSON object");
        }
        LinkedHashSet<String> unexpected = new LinkedHashSet<>();
        body.fieldNames().forEachRemaining(field -> {
            if (!COMMAND_FIELDS.contains(field)) {
                unexpected.add(field);
            }
        });
        if (!unexpected.isEmpty()) {
            throw new IllegalArgumentException(
                "migration request contains unsupported fields: " + String.join(", ", unexpected)
            );
        }
        JsonNode checksum = body.get("expectedChecksum");
        if (
            checksum == null ||
            !checksum.isTextual() ||
            !checksum.asText().matches("[0-9a-f]{64}")
        ) {
            throw new IllegalArgumentException(
                "expectedChecksum must be a lowercase SHA-256 checksum"
            );
        }
        return checksum.asText();
    }

    private String currentActor() {
        return SecurityUtils
            .getCurrentUserLogin()
            .orElseThrow(() -> new IllegalArgumentException("authenticated actor is required"));
    }

    private void auditFailure(
        String action,
        String resourceId,
        String checksum,
        RuntimeException exception
    ) {
        Map<String, Object> payload = emptyPayload(checksum, false);
        String failureReasonCode = reasonCode(exception);
        payload.put("reasonCode", failureReasonCode);
        payload.put(
            "reason",
            exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage()
        );
        auditSafely(
            action,
            AuditStage.FAIL,
            resourceId == null || resourceId.isBlank() ? "unknown" : resourceId,
            payload,
            failureReasonCode
        );
    }

    private void auditSafely(
        String action,
        AuditStage stage,
        String resourceId,
        Map<String, Object> payload,
        String reasonCode
    ) {
        try {
            audit.auditAction(action, stage, resourceId, payload);
        } catch (RuntimeException auditException) {
            log.error(
                "event=catalog_tag_migration_audit_write_failed action={} stage={} resourceId={} reasonCode={} responsePreserved=true auditErrorType={}",
                action,
                stage,
                resourceId,
                reasonCode,
                auditException.getClass().getName(),
                auditException
            );
        }
    }

    private String reasonCode(RuntimeException exception) {
        if (exception instanceof CatalogAssetTagPermissionException permission) {
            return permission.reasonCode();
        }
        if (exception instanceof CatalogTagMigrationConflictException) {
            return "MIGRATION_GATE_BLOCKED";
        }
        if (exception instanceof IllegalArgumentException) {
            return "MIGRATION_REQUEST_INVALID";
        }
        return "MIGRATION_EXECUTION_FAILED";
    }

    private Map<String, Object> payload(CatalogTagMigrationReport report) {
        Map<String, Object> payload = emptyPayload(report.checksum(), false);
        payload.put("matched", report.matchedTokenCount());
        payload.put("unmatched", (long) report.unmatchedTokens().size());
        payload.put("ambiguous", (long) report.ambiguousTokens().size());
        payload.put("protected", (long) report.protectedEvidence().size());
        payload.put("skipped", report.existingRelationCount());
        return payload;
    }

    private Map<String, Object> payload(CatalogTagMigrationExecution execution) {
        Map<String, Object> payload = emptyPayload(
            execution.checksum(),
            execution.replayed()
        );
        payload.put("matched", execution.matched());
        payload.put("unmatched", execution.unmatched());
        payload.put("ambiguous", execution.ambiguous());
        payload.put("protected", execution.protectedEvidence());
        payload.put("created", execution.created());
        payload.put("skipped", execution.skipped());
        return payload;
    }

    private Map<String, Object> payload(CatalogTagMigrationRollback rollback) {
        Map<String, Object> payload = emptyPayload(
            rollback.checksum(),
            rollback.replayed()
        );
        payload.put("matched", rollback.matched());
        payload.put("unmatched", rollback.unmatched());
        payload.put("ambiguous", rollback.ambiguous());
        payload.put("protected", rollback.protectedEvidence());
        payload.put("created", rollback.created());
        payload.put("skipped", rollback.skipped());
        payload.put("deleted", rollback.deleted());
        return payload;
    }

    private Map<String, Object> emptyPayload(String checksum, boolean replayed) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("checksum", checksum == null ? "" : checksum);
        payload.put("matched", 0L);
        payload.put("unmatched", 0L);
        payload.put("ambiguous", 0L);
        payload.put("protected", 0L);
        payload.put("created", 0L);
        payload.put("skipped", 0L);
        payload.put("deleted", 0L);
        payload.put("replayed", replayed);
        return payload;
    }
}
