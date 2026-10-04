package com.yuzhi.dts.ingestion.web.rest;

import com.yuzhi.dts.ingestion.security.AuthoritiesConstants;
import com.yuzhi.dts.ingestion.security.ForwardedUserPrincipal;
import com.yuzhi.dts.ingestion.service.IngestionTaskSecretMigrationService;
import com.yuzhi.dts.ingestion.service.IngestionTaskSecretMigrationService.CompatibilityRestoreBatch;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Explicit, admin-only downgrade compatibility operation. */
@RestController
@RequestMapping("/api/ingestion/operations/secret-compatibility")
@PreAuthorize("hasAuthority(T(com.yuzhi.dts.ingestion.security.AuthoritiesConstants).ADMIN)")
public class IngestionTaskSecretRestoreResource {

    private final IngestionTaskSecretMigrationService migrationService;

    public IngestionTaskSecretRestoreResource(IngestionTaskSecretMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @PostMapping("/restore/dry-run")
    public ResponseEntity<CompatibilityRestoreBatch> dryRun(@RequestBody CompatibilityRestoreRequest request) {
        return ResponseEntity.ok(
            migrationService.dryRunCompatibilityRestore(request.requiredLimit(), requireHumanActor())
        );
    }

    @PostMapping("/restore")
    public ResponseEntity<CompatibilityRestoreBatch> restore(@RequestBody CompatibilityRestoreRequest request) {
        if (!Boolean.TRUE.equals(request.confirmed())) {
            throw new IllegalArgumentException("explicit compatibility restore confirmation is required");
        }
        return ResponseEntity.ok(
            migrationService.restoreCompatibilityBatch(
                request.batchId(),
                request.requiredLimit(),
                requireHumanActor()
            )
        );
    }

    private String requireHumanActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean trustedPlatform = hasAuthority(authentication, AuthoritiesConstants.SERVICE_DTS_PLATFORM);
        boolean administrator = hasAuthority(authentication, AuthoritiesConstants.ADMIN);
        if (
            authentication == null ||
            !authentication.isAuthenticated() ||
            !trustedPlatform ||
            !administrator ||
            !(authentication.getPrincipal() instanceof ForwardedUserPrincipal principal) ||
            !"dts-platform".equals(principal.sourceService())
        ) {
            throw new IllegalStateException("AUTHENTICATED_HUMAN_AUDIT_ACTOR_REQUIRED");
        }
        String actor = principal.getName();
        String normalized = actor.toLowerCase(java.util.Locale.ROOT);
        if (
            normalized.equals("anonymous") ||
            normalized.equals("system") ||
            normalized.equals("scheduler") ||
            normalized.startsWith("service:") ||
            normalized.startsWith("service-account-") ||
            normalized.startsWith("_system:")
        ) {
            throw new IllegalStateException("AUTHENTICATED_HUMAN_AUDIT_ACTOR_REQUIRED");
        }
        return actor;
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    public record CompatibilityRestoreRequest(String batchId, Integer limit, Boolean confirmed) {
        int requiredLimit() {
            if (limit == null) {
                throw new IllegalArgumentException("restore limit is required");
            }
            return limit;
        }
    }
}
