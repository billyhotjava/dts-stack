package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogMaskingRule;
import com.yuzhi.dts.platform.domain.catalog.CatalogRowFilterRule;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogRowFilterRuleRepository;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.AccessibleAssetsResult;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.AssetRef;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.GrantCommand;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionCheckCommand;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionDecision;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionResult;
import com.yuzhi.dts.platform.service.permission.dto.AssetGrantDto;
import com.yuzhi.dts.platform.service.permission.AssetPermissionAuditService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
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
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogRowFilterRuleRepository rowFilterRuleRepository;
    private final CatalogMaskingRuleRepository maskingRuleRepository;
    private final MeterRegistry meterRegistry;

    public AssetPermissionInternalResource(
        AssetPermissionService permissionService,
        AssetPermissionAuditService auditService,
        CatalogDatasetRepository datasetRepository,
        CatalogRowFilterRuleRepository rowFilterRuleRepository,
        CatalogMaskingRuleRepository maskingRuleRepository,
        MeterRegistry meterRegistry
    ) {
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.datasetRepository = datasetRepository;
        this.rowFilterRuleRepository = rowFilterRuleRepository;
        this.maskingRuleRepository = maskingRuleRepository;
        this.meterRegistry = meterRegistry;
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

    @PostMapping("/policy")
    public ResponseEntity<PolicyResponse> policy(@RequestBody CheckRequest request) {
        return policyInternal(request, false);
    }

    public ResponseEntity<PolicyResponse> policyV1(@RequestBody CheckRequest request) {
        return policyInternal(request, true);
    }

    ResponseEntity<PolicyResponse> policyInternal(CheckRequest request, boolean forbiddenOnDenied) {
        PermissionDecision decision = permissionService.checkAction(
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
        auditService.recordDecision(decision, request.username(), currentActor());
        if (!decision.allowed()) {
            if (forbiddenOnDenied) {
                return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new PolicyResponse(false, List.of(), List.of(), firstText(decision.reason(), "permission_denied")));
            }
            return ResponseEntity.ok(new PolicyResponse(true, List.of("1 = 0"), List.of(), decision.reason()));
        }
        List<String> predicates = new ArrayList<>();
        List<String> maskedColumns = new ArrayList<>();
        Optional<CatalogDataset> dataset = resolveDataset(request.asset());
        if (dataset.isPresent()) {
            CatalogDataset resolvedDataset = dataset.orElseThrow();
            for (CatalogRowFilterRule rule : rowFilterRuleRepository.findByDataset(resolvedDataset)) {
                if (!rolesMatch(rule.getRoles(), request.userRoles())) {
                    continue;
                }
                String expression = safePredicate(rule.getExpression());
                if (expression != null) {
                    predicates.add(expression);
                }
            }
            for (CatalogMaskingRule rule : maskingRuleRepository.findByDataset(resolvedDataset)) {
                String column = rule != null ? trimToNull(rule.getColumn()) : null;
                if (column != null && safeIdentifier(column)) {
                    maskedColumns.add(column);
                }
            }
        } else if (isDatasetAsset(request.asset())) {
            recordDatasetMiss(request);
            if (forbiddenOnDenied) {
                return ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(new PolicyResponse(false, List.of(), List.of(), "dataset_not_resolved"));
            }
        }
        String source = policySource(predicates, maskedColumns);
        return ResponseEntity.ok(new PolicyResponse(true, List.copyOf(predicates), List.copyOf(maskedColumns), source));
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
    public record PolicyResponse(boolean applyRls, List<String> predicates, List<String> maskedColumns, String policySource) {}
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

    private Optional<CatalogDataset> resolveDataset(AssetRefDto asset) {
        if (asset == null || asset.type() == null || !"DATASET".equalsIgnoreCase(asset.type())) {
            return Optional.empty();
        }
        String id = firstText(asset.id(), asset.key());
        UUID uuid = parseUuid(id);
        if (uuid != null) {
            Optional<CatalogDataset> byId = datasetRepository.findById(uuid);
            if (byId.isPresent()) {
                return byId;
            }
        }
        String table = tableNameFromRef(id);
        if (table == null) {
            return Optional.empty();
        }
        Optional<CatalogDataset> byHiveTable = datasetRepository.findFirstByHiveTableIgnoreCase(table);
        if (byHiveTable.isPresent()) {
            return byHiveTable;
        }
        return datasetRepository.findFirstByNameIgnoreCase(table);
    }

    private void recordDatasetMiss(CheckRequest request) {
        if (meterRegistry != null) {
            meterRegistry.counter("dts.platform.asset_permission.policy.dataset_miss").increment();
        }
        AssetRefDto asset = request != null ? request.asset() : null;
        log.warn(
            "event=asset_permission_policy_dataset_miss actor={} action={} assetType={} assetIdPresent={} assetKeyPresent={}",
            request != null ? request.username() : null,
            request != null ? request.action() : null,
            asset != null ? asset.type() : null,
            asset != null && trimToNull(asset.id()) != null,
            asset != null && trimToNull(asset.key()) != null
        );
    }

    private static boolean isDatasetAsset(AssetRefDto asset) {
        return asset != null && "DATASET".equalsIgnoreCase(asset.type());
    }

    private static boolean rolesMatch(String rolesCsv, List<String> userRoles) {
        if (rolesCsv == null || rolesCsv.isBlank()) {
            return true;
        }
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }
        List<String> normalizedRoles = userRoles.stream().filter(role -> role != null && !role.isBlank()).map(String::trim).map(role -> role.toUpperCase(Locale.ROOT)).toList();
        for (String part : rolesCsv.split(",")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String role = part.trim().toUpperCase(Locale.ROOT);
            if (!role.startsWith("ROLE_")) {
                role = "ROLE_" + role;
            }
            if (normalizedRoles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    private static String safePredicate(String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String trimmed = expression.trim();
        if (trimmed.contains(";") || trimmed.contains("--") || trimmed.contains("/*")) {
            return null;
        }
        return trimmed;
    }

    private static String policySource(List<String> predicates, List<String> maskedColumns) {
        boolean hasPredicates = predicates != null && !predicates.isEmpty();
        boolean hasMasking = maskedColumns != null && !maskedColumns.isEmpty();
        if (hasPredicates && hasMasking) {
            return "platform-row-filter+masking";
        }
        if (hasPredicates) {
            return "platform-row-filter";
        }
        if (hasMasking) {
            return "platform-masking";
        }
        return "platform-permission";
    }

    private static boolean safeIdentifier(String value) {
        return value != null && value.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    private static String tableNameFromRef(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        int table = text.toLowerCase(Locale.ROOT).lastIndexOf("/table:");
        if (table >= 0) {
            return text.substring(table + "/table:".length());
        }
        int model = text.toLowerCase(Locale.ROOT).lastIndexOf("/model:");
        if (model >= 0) {
            return text.substring(model + "/model:".length());
        }
        int slash = text.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < text.length()) {
            return text.substring(slash + 1);
        }
        int colon = text.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < text.length()) {
            return text.substring(colon + 1);
        }
        return text;
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean equalsIgnoreCase(String expected, String value) {
        return expected != null && value != null && expected.equalsIgnoreCase(value.trim());
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
