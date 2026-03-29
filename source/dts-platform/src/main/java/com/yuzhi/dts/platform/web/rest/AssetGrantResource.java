package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.domain.permission.AssetOwnership;
import com.yuzhi.dts.platform.repository.permission.AssetGrantRepository;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.permission.AssetPermissionAuditService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/asset-grants")
public class AssetGrantResource {

    private static final Logger log = LoggerFactory.getLogger(AssetGrantResource.class);

    private static final String DEPT_MANAGER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).DATA_MAINTAINER_ROLES)";

    private static final Set<String> INST_LEVEL_ROLES = Set.of(
        AuthoritiesConstants.ADMIN,
        AuthoritiesConstants.OP_ADMIN,
        AuthoritiesConstants.INST_DATA_OWNER
    );

    private final AssetGrantRepository grantRepository;
    private final AssetOwnershipRepository ownershipRepository;
    private final AssetPermissionAuditService auditService;

    public AssetGrantResource(AssetGrantRepository grantRepository,
                               AssetOwnershipRepository ownershipRepository,
                               AssetPermissionAuditService auditService) {
        this.grantRepository = grantRepository;
        this.ownershipRepository = ownershipRepository;
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize(DEPT_MANAGER_EXPRESSION)
    public ResponseEntity<List<AssetGrant>> listByAsset(
            @RequestParam String assetType,
            @RequestParam String assetId) {
        return ResponseEntity.ok(grantRepository.findByAssetTypeAndAssetId(assetType, assetId));
    }

    @PostMapping
    @Transactional
    @PreAuthorize(DEPT_MANAGER_EXPRESSION)
    public ResponseEntity<?> create(@RequestBody CreateGrantRequest request) {
        // Validate valid_to > valid_from
        if (request.validFrom() != null && request.validTo() != null
                && request.validTo().isBefore(request.validFrom())) {
            return ResponseEntity.badRequest().body(Map.of("error", "validTo must be after validFrom"));
        }

        // Check if cross-department grant (requires inst-level role)
        Optional<AssetOwnership> ownership = ownershipRepository.findByAssetTypeAndAssetId(
            request.assetType(), request.assetId()
        );
        if (ownership.isPresent() && isCrossDepartment(ownership.orElseThrow())) {
            if (!SecurityUtils.hasCurrentUserAnyOfAuthorities(INST_LEVEL_ROLES.toArray(new String[0]))) {
                return ResponseEntity.status(403).body(
                    Map.of("error", "Cross-department grants require institute-level privileges")
                );
            }
        }

        String currentUser = SecurityUtils.getCurrentUserLogin().orElse("system");

        AssetGrant grant = new AssetGrant();
        grant.setAssetType(request.assetType());
        grant.setAssetId(request.assetId());
        grant.setGranteeType(request.granteeType());
        grant.setGranteeId(request.granteeId());
        grant.setPermission(request.permission());
        grant.setValidFrom(request.validFrom());
        grant.setValidTo(request.validTo());
        grant.setGrantedBy(currentUser);
        grant.setGrantReason(request.grantReason());

        grant = grantRepository.save(grant);

        auditService.recordGrant(
            request.assetType(), request.assetId(),
            request.granteeId(), request.permission(), request.grantReason()
        );

        return ResponseEntity.ok(grant);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize(DEPT_MANAGER_EXPRESSION)
    public ResponseEntity<?> revoke(@PathVariable Long id) {
        return grantRepository.findById(id)
            .map(grant -> {
                grantRepository.delete(grant);
                auditService.recordRevoke(
                    grant.getAssetType(), grant.getAssetId(),
                    grant.getGranteeId(), grant.getPermission()
                );
                return ResponseEntity.ok(Map.of("deleted", true));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/my")
    public ResponseEntity<Page<AssetGrant>> myGrants(Pageable pageable) {
        String username = SecurityUtils.getCurrentUserLogin().orElse("");
        return ResponseEntity.ok(grantRepository.findGrantsForUser(username, pageable));
    }

    @GetMapping("/granted-by-me")
    public ResponseEntity<Page<AssetGrant>> grantedByMe(Pageable pageable) {
        String username = SecurityUtils.getCurrentUserLogin().orElse("");
        return ResponseEntity.ok(grantRepository.findByGrantedByOrderByCreatedDateDesc(username, pageable));
    }

    private boolean isCrossDepartment(AssetOwnership ownership) {
        // Compare asset's dept with current user's dept from SecurityContext
        // For now we rely on the @PreAuthorize to enforce role-based checks
        // A more refined check would extract dept from JWT claims
        return false; // TODO: implement dept comparison when user dept is available in SecurityContext
    }

    public record CreateGrantRequest(
        String assetType, String assetId,
        String granteeType, String granteeId,
        String permission,
        Instant validFrom, Instant validTo,
        String grantReason
    ) {}
}
