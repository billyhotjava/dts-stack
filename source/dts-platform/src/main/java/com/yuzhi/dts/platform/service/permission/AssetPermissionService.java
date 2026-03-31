package com.yuzhi.dts.platform.service.permission;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.domain.permission.AssetOwnership;
import com.yuzhi.dts.platform.repository.permission.AssetGrantRepository;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AssetPermissionService {

    private static final Logger log = LoggerFactory.getLogger(AssetPermissionService.class);

    private static final Set<String> SUPERUSER_ROLES = Set.of(
        AuthoritiesConstants.ADMIN,
        AuthoritiesConstants.OP_ADMIN
    );

    private static final Set<String> INST_MANAGE_ROLES = Set.of(
        AuthoritiesConstants.INST_DATA_OWNER
    );

    private static final Set<String> INST_READ_ROLES = Set.of(
        AuthoritiesConstants.INST_LEADER
    );

    private static final Set<String> DEPT_MANAGE_ROLES = Set.of(
        AuthoritiesConstants.DEPT_DATA_OWNER
    );

    private static final Set<String> DEPT_READ_ROLES = Set.of(
        AuthoritiesConstants.DEPT_LEADER
    );

    private static final Set<String> GLOBAL_ACCESS_ROLES;
    static {
        GLOBAL_ACCESS_ROLES = new HashSet<>();
        GLOBAL_ACCESS_ROLES.addAll(SUPERUSER_ROLES);
        GLOBAL_ACCESS_ROLES.addAll(INST_MANAGE_ROLES);
        GLOBAL_ACCESS_ROLES.addAll(INST_READ_ROLES);
    }

    /**
     * Asset types that require explicit grants only.
     * Role-based implicit access (INST/DEPT roles) is bypassed for these types;
     * only superuser roles and explicit grants are honoured.
     */
    private static final Set<String> EXPLICIT_GRANT_ONLY_TYPES = Set.of("SCREEN");

    private static final Map<String, Integer> PERMISSION_RANK = Map.of(
        "MANAGE", 3,
        "EDIT", 2,
        "READ", 1
    );

    private final AssetOwnershipRepository ownershipRepository;
    private final AssetGrantRepository grantRepository;

    public AssetPermissionService(AssetOwnershipRepository ownershipRepository, AssetGrantRepository grantRepository) {
        this.ownershipRepository = ownershipRepository;
        this.grantRepository = grantRepository;
    }

    public PermissionResult check(String username, List<String> roles, String deptCode, String assetType, String assetId) {
        // 1. Superuser roles → MANAGE all (applies to every asset type)
        if (hasAny(roles, SUPERUSER_ROLES)) {
            return PermissionResult.allowed("MANAGE", "superuser");
        }

        boolean explicitOnly = EXPLICIT_GRANT_ONLY_TYPES.contains(assetType);

        if (!explicitOnly) {
            // 2. Institute leader → READ all
            if (hasAny(roles, INST_READ_ROLES)) {
                if (hasAny(roles, INST_MANAGE_ROLES)) {
                    return PermissionResult.allowed("MANAGE", "inst_manage");
                }
                return PermissionResult.allowed("READ", "inst_read");
            }

            // 3. Institute data owner → MANAGE all
            if (hasAny(roles, INST_MANAGE_ROLES)) {
                return PermissionResult.allowed("MANAGE", "inst_manage");
            }

            // 4. Department-level: check ownership
            Optional<AssetOwnership> ownership = ownershipRepository.findByAssetTypeAndAssetId(assetType, assetId);
            if (ownership.isPresent() && deptCode != null && deptCode.equalsIgnoreCase(ownership.orElseThrow().getOwnerDeptCode())) {
                if (hasAny(roles, DEPT_MANAGE_ROLES)) {
                    return PermissionResult.allowed("MANAGE", "dept_ownership");
                }
                if (hasAny(roles, DEPT_READ_ROLES)) {
                    return PermissionResult.allowed("READ", "dept_ownership");
                }
            }
        }

        // 5. Explicit grants (USER / ROLE / DEPT)
        List<String> roleList = roles != null ? roles : List.of();
        String effectiveDeptCode = deptCode != null ? deptCode : "";
        List<AssetGrant> grants = grantRepository.findActiveGrantsForUser(
            assetType, assetId, username, roleList, effectiveDeptCode, Instant.now()
        );
        if (!grants.isEmpty()) {
            String highestPermission = grants.stream()
                .map(AssetGrant::getPermission)
                .max(Comparator.comparingInt(p -> PERMISSION_RANK.getOrDefault(p, 0)))
                .orElse("READ");
            return PermissionResult.allowed(highestPermission, "explicit_grant");
        }

        // 6. Deny
        return PermissionResult.denied();
    }

    public Map<String, PermissionResult> batchCheck(String username, List<String> roles, String deptCode,
                                                     List<AssetRef> assets) {
        // Short-circuit for superuser roles (applies to every asset type)
        if (hasAny(roles, SUPERUSER_ROLES)) {
            return assets.stream().collect(Collectors.toMap(
                a -> a.type() + ":" + a.id(),
                a -> PermissionResult.allowed("MANAGE", "superuser")
            ));
        }

        // Per-asset check — delegates to check() which honours EXPLICIT_GRANT_ONLY_TYPES
        Map<String, PermissionResult> results = new LinkedHashMap<>();
        for (AssetRef asset : assets) {
            PermissionResult result = check(username, roles, deptCode, asset.type(), asset.id());
            results.put(asset.type() + ":" + asset.id(), result);
        }
        return results;
    }

    public AccessibleAssetsResult listAccessibleAssetIds(String username, List<String> roles, String deptCode,
                                                          String assetType, Pageable pageable) {
        boolean explicitOnly = EXPLICIT_GRANT_ONLY_TYPES.contains(assetType);

        // Global roles see everything — but not for explicit-only asset types
        if (!explicitOnly && hasAny(roles, GLOBAL_ACCESS_ROLES)) {
            return AccessibleAssetsResult.all();
        }
        // Superuser always sees everything, regardless of asset type
        if (hasAny(roles, SUPERUSER_ROLES)) {
            return AccessibleAssetsResult.all();
        }

        // Collect IDs from department ownership + explicit grants
        Set<String> assetIds = new LinkedHashSet<>();

        // Department-level roles: add owned assets (skipped for explicit-only types)
        if (!explicitOnly && deptCode != null && hasAny(roles, Stream.concat(DEPT_MANAGE_ROLES.stream(), DEPT_READ_ROLES.stream())
                .collect(Collectors.toSet()))) {
            List<String> deptAssets = ownershipRepository.findAssetIdsByTypeAndDeptCode(assetType, deptCode);
            assetIds.addAll(deptAssets);
        }

        // Explicit grants
        List<String> roleList = roles != null ? roles : List.of();
        String effectiveDeptCode = deptCode != null ? deptCode : "";
        List<String> grantedIds = grantRepository.findAccessibleAssetIdsByGrant(
            assetType, username, roleList, effectiveDeptCode, Instant.now()
        );
        assetIds.addAll(grantedIds);

        List<String> resultList = new ArrayList<>(assetIds);
        int total = resultList.size();

        // Apply pagination
        int offset = (int) pageable.getOffset();
        int size = pageable.getPageSize();
        if (offset >= total) {
            return new AccessibleAssetsResult(List.of(), total, "FILTERED");
        }
        List<String> page = resultList.subList(offset, Math.min(offset + size, total));
        return new AccessibleAssetsResult(page, total, "FILTERED");
    }

    private static boolean hasAny(List<String> userRoles, Set<String> targetRoles) {
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }
        for (String role : userRoles) {
            if (targetRoles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    // --- Inner records ---

    public record PermissionResult(boolean allowed, String permission, String reason) {
        public static PermissionResult allowed(String permission, String reason) {
            return new PermissionResult(true, permission, reason);
        }
        public static PermissionResult denied() {
            return new PermissionResult(false, null, "denied");
        }
    }

    public record AssetRef(String type, String id) {}

    public record AccessibleAssetsResult(List<String> assetIds, long total, String scope) {
        public static AccessibleAssetsResult all() {
            return new AccessibleAssetsResult(List.of(), 0, "ALL");
        }
    }
}
