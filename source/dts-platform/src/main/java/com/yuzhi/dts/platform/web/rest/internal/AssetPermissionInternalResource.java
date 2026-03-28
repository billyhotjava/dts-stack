package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.AccessibleAssetsResult;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.AssetRef;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionResult;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/asset-permission")
public class AssetPermissionInternalResource {

    private static final Logger log = LoggerFactory.getLogger(AssetPermissionInternalResource.class);
    private static final int MAX_BATCH_SIZE = 200;

    private final AssetPermissionService permissionService;

    public AssetPermissionInternalResource(AssetPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @PostMapping("/check")
    public ResponseEntity<CheckResponse> check(@RequestBody CheckRequest request) {
        PermissionResult result = permissionService.check(
            request.username(),
            request.userRoles(),
            request.userDeptCode(),
            request.asset().type(),
            request.asset().id()
        );
        return ResponseEntity.ok(new CheckResponse(result.allowed(), result.permission(), result.reason()));
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
            request.username(), request.userRoles(), request.userDeptCode(), refs
        );

        Map<String, CheckResponse> responseMap = new java.util.LinkedHashMap<>();
        results.forEach((key, pr) -> responseMap.put(key,
            new CheckResponse(pr.allowed(), pr.permission(), pr.reason())));

        return ResponseEntity.ok(Map.of("results", responseMap));
    }

    @PostMapping("/accessible-ids")
    public ResponseEntity<AccessibleIdsResponse> accessibleIds(@RequestBody AccessibleIdsRequest request) {
        int page = request.page() != null ? request.page() : 0;
        int size = request.size() != null ? request.size() : 100;

        AccessibleAssetsResult result = permissionService.listAccessibleAssetIds(
            request.username(), request.userRoles(), request.userDeptCode(),
            request.assetType(), PageRequest.of(page, size)
        );

        return ResponseEntity.ok(new AccessibleIdsResponse(
            result.assetIds(), result.total(), result.scope()
        ));
    }

    // --- Request/Response DTOs ---

    public record CheckRequest(String username, List<String> userRoles, String userDeptCode, AssetRefDto asset) {}
    public record BatchCheckRequest(String username, List<String> userRoles, String userDeptCode, List<AssetRefDto> assets) {}
    public record AccessibleIdsRequest(String username, List<String> userRoles, String userDeptCode,
                                        String assetType, Integer page, Integer size) {}

    public record AssetRefDto(String type, String id) {}
    public record CheckResponse(boolean allowed, String permission, String reason) {}
    public record AccessibleIdsResponse(List<String> assetIds, long total, String scope) {}
}
