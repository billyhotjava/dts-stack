package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetGrantRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionIdentityResolver.ResolvedPermissionIdentity;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionResult;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CatalogAssetTagReadVisibilityService {

    private static final Pattern DEPARTMENT_CODE_ALLOWLIST = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private static final Set<CatalogAssetType> SUPPORTED_TYPES = EnumSet.allOf(
        CatalogAssetType.class
    );

    private final CatalogAssetTagPermissionIdentityResolver identityResolver;
    private final AccessChecker accessChecker;
    private final CatalogDatasetGrantRepository datasetGrantRepository;
    private final AssetPermissionService permissionService;

    public CatalogAssetTagReadVisibilityService(
        CatalogAssetTagPermissionIdentityResolver identityResolver,
        AccessChecker accessChecker,
        CatalogDatasetGrantRepository datasetGrantRepository,
        AssetPermissionService permissionService
    ) {
        this.identityResolver = identityResolver;
        this.accessChecker = accessChecker;
        this.datasetGrantRepository = datasetGrantRepository;
        this.permissionService = permissionService;
    }

    public List<AssetRef> filterReadable(List<AssetRef> requested, String activeDept) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (!StringUtils.hasText(actor) || requested == null || requested.isEmpty()) {
            return List.of();
        }
        Map<AssetRef, CatalogAssetType> supported = normalizeSupported(requested);
        if (supported.isEmpty()) {
            return List.of();
        }
        List<ResolvedPermissionIdentity> identities = resolveFailClosed(new ArrayList<>(supported.keySet()));
        if (identities.isEmpty()) {
            return List.of();
        }

        List<String> roles = SecurityUtils.getCurrentUserAuthorities();
        List<String> permissionRoles = roles.isEmpty() ? List.of("__NO_ROLE__") : roles;
        boolean institutePrivileged = SecurityUtils.hasCurrentUserAnyOfAuthorities(
            AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES
        );
        String department = resolveEffectiveDepartment(activeDept);
        Set<UUID> explicitDatasetIds = loadExplicitDatasetIds(actor, identities);
        Map<String, PermissionResult> accessibleByAsset = institutePrivileged
            ? Map.of()
            : loadAccessibleNonDatasetIds(
                  actor,
                  permissionRoles,
                  department,
                  identities
              );
        Set<AssetRef> readable = new LinkedHashSet<>();
        for (ResolvedPermissionIdentity identity : identities) {
            AssetRef ref = new AssetRef(identity.requestedType().name(), identity.canonicalAssetKey());
            if (
                identity.requestedType() == CatalogAssetType.DATASET ||
                identity.dataset() != null
            ) {
                if (isReadableDataset(identity.dataset(), department, explicitDatasetIds)) {
                    readable.add(ref);
                }
                continue;
            }
            if (institutePrivileged) {
                readable.add(ref);
                continue;
            }
            PermissionResult accessible = accessibleByAsset.get(
                permissionKey(identity)
            );
            if (accessible != null && accessible.allowed()) {
                readable.add(ref);
            }
        }
        return requested
            .stream()
            .map(this::normalizeRef)
            .filter(java.util.Objects::nonNull)
            .filter(readable::contains)
            .distinct()
            .toList();
    }

    private Map<AssetRef, CatalogAssetType> normalizeSupported(List<AssetRef> requested) {
        Map<AssetRef, CatalogAssetType> supported = new LinkedHashMap<>();
        for (AssetRef raw : requested) {
            AssetRef normalized = normalizeRef(raw);
            if (normalized == null) {
                continue;
            }
            CatalogAssetType type = CatalogAssetType.from(normalized.assetType());
            if (SUPPORTED_TYPES.contains(type)) {
                supported.putIfAbsent(normalized, type);
            }
        }
        return supported;
    }

    private AssetRef normalizeRef(AssetRef raw) {
        if (raw == null || !StringUtils.hasText(raw.assetType()) || !StringUtils.hasText(raw.assetKey())) {
            return null;
        }
        try {
            return new AssetRef(CatalogAssetType.from(raw.assetType()).name(), raw.assetKey().trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private List<ResolvedPermissionIdentity> resolveFailClosed(List<AssetRef> assets) {
        if (assets.isEmpty()) {
            return List.of();
        }
        try {
            return identityResolver.resolveAll(assets);
        } catch (CatalogAssetTagPermissionException exception) {
            if (assets.size() == 1) {
                return List.of();
            }
            int midpoint = assets.size() / 2;
            List<ResolvedPermissionIdentity> resolved = new ArrayList<>();
            resolved.addAll(resolveFailClosed(assets.subList(0, midpoint)));
            resolved.addAll(resolveFailClosed(assets.subList(midpoint, assets.size())));
            return resolved;
        }
    }

    private Set<UUID> loadExplicitDatasetIds(String actor, List<ResolvedPermissionIdentity> identities) {
        boolean hasDataset = identities
            .stream()
            .anyMatch(identity ->
                identity.requestedType() == CatalogAssetType.DATASET ||
                identity.dataset() != null
            );
        if (!hasDataset) {
            return Set.of();
        }
        String userId = SecurityUtils.getCurrentUserId().orElse(null);
        Set<UUID> ids = datasetGrantRepository.findDatasetIdsByUser(userId, actor);
        return ids == null ? Set.of() : Set.copyOf(ids);
    }

    private Map<String, PermissionResult> loadAccessibleNonDatasetIds(
        String actor,
        List<String> roles,
        String department,
        List<ResolvedPermissionIdentity> identities
    ) {
        List<AssetPermissionService.AssetRef> candidates = identities
            .stream()
            .filter(identity ->
                identity.requestedType() != CatalogAssetType.DATASET &&
                identity.dataset() == null
            )
            .map(identity ->
                new AssetPermissionService.AssetRef(
                    identity.grantAssetType(),
                    identity.grantAssetId()
                )
            )
            .distinct()
            .toList();
        if (candidates.isEmpty()) {
            return Map.of();
        }
        Map<String, PermissionResult> result = permissionService.batchCheck(
            actor,
            roles,
            department,
            candidates
        );
        return result == null ? Map.of() : result;
    }

    private String permissionKey(ResolvedPermissionIdentity identity) {
        return identity.grantAssetType() + ":" + identity.grantAssetId();
    }

    private boolean isReadableDataset(CatalogDataset dataset, String activeDept, Set<UUID> explicitDatasetIds) {
        if (dataset == null || !accessChecker.canRead(dataset)) {
            return false;
        }
        if (dataset.getId() != null && explicitDatasetIds.contains(dataset.getId())) {
            return true;
        }
        return accessChecker.departmentAllowedExact(dataset, activeDept);
    }

    private String resolveEffectiveDepartment(String requested) {
        String current = allowlistedDepartment(SecurityUtils.getCurrentUserDept().orElse(null));
        if (!SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return current;
        }
        String selected = allowlistedDepartment(requested);
        return selected == null ? current : selected;
    }

    private String allowlistedDepartment(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim();
        return DEPARTMENT_CODE_ALLOWLIST.matcher(normalized).matches() ? normalized : null;
    }
}
