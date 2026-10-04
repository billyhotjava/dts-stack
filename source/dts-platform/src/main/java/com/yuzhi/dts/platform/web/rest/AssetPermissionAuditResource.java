package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit;
import com.yuzhi.dts.platform.repository.permission.AssetPermissionAuditRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/asset-permission-audit")
public class AssetPermissionAuditResource {

    private static final String AUDITOR_EXPRESSION =
        "hasAnyAuthority('ROLE_SECURITY_AUDITOR', 'ROLE_SYS_ADMIN', 'ROLE_ADMIN', 'ROLE_OP_ADMIN')";

    private final AssetPermissionAuditRepository auditRepository;

    public AssetPermissionAuditResource(AssetPermissionAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @GetMapping
    @PreAuthorize(AUDITOR_EXPRESSION)
    public ResponseEntity<Page<AssetPermissionAudit>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String targetUser,
            @RequestParam(required = false) String oaReference,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            Pageable pageable) {
        Page<AssetPermissionAudit> page = auditRepository.findByFilters(
            action, operator, targetUser, oaReference, dateFrom, dateTo, pageable
        );
        return ResponseEntity.ok(page);
    }
}
