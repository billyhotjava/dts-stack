package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetIdentityResolutionAuditService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/catalog")
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "')")
public class CatalogAssetResolutionFailureResource {

    private final CatalogAssetIdentityResolutionAuditService auditService;

    public CatalogAssetResolutionFailureResource(CatalogAssetIdentityResolutionAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/asset-resolution-failures")
    public ResponseEntity<List<FailureResponse>> failures(
        @RequestParam(name = "since", required = false) Instant since,
        @RequestParam(name = "limit", required = false, defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(auditService.recentFailures(since, limit).stream().map(FailureResponse::from).toList());
    }

    public record FailureResponse(
        UUID id,
        String ref,
        Instant requestedAt,
        String caller,
        String typeHintGuess,
        String reason
    ) {
        static FailureResponse from(CatalogAssetResolutionFailure failure) {
            return new FailureResponse(
                failure.getId(),
                failure.getRef(),
                failure.getRequestedAt(),
                failure.getCaller(),
                failure.getTypeHintGuess(),
                failure.getReason()
            );
        }
    }
}
