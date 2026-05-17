package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.AccessibleAssetsResult;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.AssetRef;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.GrantCommand;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionCheckCommand;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionDecision;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionResult;
import com.yuzhi.dts.platform.service.permission.dto.AssetGrantDto;
import com.yuzhi.dts.platform.service.permission.AssetPermissionAuditService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/asset-permission")
public class AssetPermissionInternalResource {

    private static final Logger log = LoggerFactory.getLogger(AssetPermissionInternalResource.class);
    private static final int MAX_BATCH_SIZE = 200;

    private final AssetPermissionService permissionService;
    private final AssetPermissionAuditService auditService;

    public AssetPermissionInternalResource(AssetPermissionService permissionService, AssetPermissionAuditService auditService) {
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    @PostMapping("/check")
    public ResponseEntity<CheckResponse> check(@RequestBody CheckRequest request) {
        PermissionDecision result = permissionService.checkAction(
            new PermissionCheckCommand(
                request.username(),
                request.userRoles(),
                request.userDeptCode(),
                request.userClassification(),
                request.asset() != null ? request.asset().type() : null,
                request.asset() != null ? request.asset().id() : null,
                request.asset() != null ? request.asset().key() : null,
                request.action(),
                request.assetClassification()
            )
        );
        auditService.recordDecision(result, request.username(), currentActor());
        return ResponseEntity.ok(
            new CheckResponse(
                result.allowed(),
                result.permission(),
                result.reason(),
                result.requiredPermission(),
                result.action(),
                result.assetType(),
                result.assetId(),
                result.assetKey(),
                result.classificationDecision(),
                result.grantSource()
            )
        );
    }

    @PostMapping("/batch-check")
    public ResponseEntity<?> batchCheck(@RequestBody BatchCheckRequest request) {
        if (request.assets() == null || request.assets().size() > MAX_BATCH_SIZE) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "assets size must be between 1 and " + MAX_BATCH_SIZE)
            );
        }

        List<AssetRef> refs = request.assets().stream()
            .map(a -> new AssetRef(a.type(), a.id()))
            .toList();

        Map<String, PermissionResult> results = permissionService.batchCheck(
            request.username(), request.userRoles(), request.userDeptCode(), refs, request.userClassification()
        );

        Map<String, CheckResponse> responseMap = new java.util.LinkedHashMap<>();
        results.forEach((key, pr) -> responseMap.put(key,
            new CheckResponse(pr.allowed(), pr.permission(), pr.reason(), null, null, null, null, null, null, pr.reason())));

        return ResponseEntity.ok(Map.of("results", responseMap));
    }

    @PostMapping("/accessible-ids")
    public ResponseEntity<AccessibleIdsResponse> accessibleIds(@RequestBody AccessibleIdsRequest request) {
        int page = request.page() != null ? request.page() : 0;
        int size = request.size() != null ? request.size() : 100;

        AccessibleAssetsResult result = permissionService.listAccessibleAssetIds(
            request.username(), request.userRoles(), request.userDeptCode(),
            request.assetType(), PageRequest.of(page, size), request.userClassification()
        );

        return ResponseEntity.ok(new AccessibleIdsResponse(
            result.assetIds(), result.total(), result.scope()
        ));
    }

    @GetMapping("/grants")
    public ResponseEntity<List<AssetGrantDto>> grants(
        @RequestParam String assetType,
        @RequestParam String assetId
    ) {
        return ResponseEntity.ok(permissionService.listGrants(assetType, assetId).stream().map(AssetGrantDto::from).toList());
    }

    @PostMapping("/grants")
    public ResponseEntity<AssetGrantDto> upsertGrant(@RequestBody GrantRequest request) {
        AssetGrant grant = permissionService.upsertGrant(new GrantCommand(
            request.assetType(),
            request.assetId(),
            request.granteeType(),
            request.granteeId(),
            request.permission(),
            request.levelOverride(),
            request.validFrom(),
            request.validTo(),
            request.grantReason(),
            request.grantedBy()
        ));
        return ResponseEntity.ok(AssetGrantDto.from(grant));
    }

    @DeleteMapping("/grants/{grantId}")
    public ResponseEntity<?> revokeGrant(
        @PathVariable Long grantId,
        @RequestParam String assetType,
        @RequestParam String assetId
    ) {
        boolean deleted = permissionService.revokeGrant(assetType, assetId, grantId);
        return deleted ? ResponseEntity.ok(Map.of("deleted", true)) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/grants/by-asset")
    public ResponseEntity<Map<String, Integer>> revokeByAsset(
        @RequestParam String assetType,
        @RequestParam String assetId
    ) {
        int deleted = permissionService.revokeAllGrants(assetType, assetId);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    // --- Request/Response DTOs ---

    public record CheckRequest(
        String username,
        List<String> userRoles,
        String userDeptCode,
        String userClassification,
        String assetClassification,
        String action,
        AssetRefDto asset
    ) {}
    public record BatchCheckRequest(
        String username,
        List<String> userRoles,
        String userDeptCode,
        String userClassification,
        List<AssetRefDto> assets
    ) {}
    public record AccessibleIdsRequest(String username, List<String> userRoles, String userDeptCode,
                                        String userClassification, String assetType, Integer page, Integer size) {}

    public record AssetRefDto(String type, String id, String key) {}
    public record CheckResponse(
        boolean allowed,
        String permission,
        String reason,
        String requiredPermission,
        String action,
        String assetType,
        String assetId,
        String assetKey,
        String classificationDecision,
        String grantSource
    ) {}
    public record AccessibleIdsResponse(List<String> assetIds, long total, String scope) {}
    public record GrantRequest(
        String assetType,
        String assetId,
        String granteeType,
        String granteeId,
        String permission,
        Boolean levelOverride,
        java.time.Instant validFrom,
        java.time.Instant validTo,
        String grantReason,
        String grantedBy
    ) {}

    private String currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }
}
