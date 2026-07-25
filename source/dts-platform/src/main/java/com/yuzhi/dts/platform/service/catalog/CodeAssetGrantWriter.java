package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.domain.permission.AssetOwnership;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.GrantCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CodeAssetGrantWriter {

    private static final String EXTERNAL_SOURCE_PREFIX = "catalog-external:";

    private final AssetOwnershipRepository ownershipRepository;
    private final AssetPermissionService permissionService;

    public CodeAssetGrantWriter(AssetOwnershipRepository ownershipRepository, AssetPermissionService permissionService) {
        this.ownershipRepository = ownershipRepository;
        this.permissionService = permissionService;
    }

    @Transactional
    public void upsertCodeAsset(
        CatalogAssetIdentity identity,
        String ownerDept,
        String grantedBy,
        String classification,
        String lifecycleStatus
    ) {
        if (identity == null) {
            throw new IllegalArgumentException("asset identity is required");
        }
        String assetType = identity.grantAssetType();
        String assetId = identity.grantAssetId();
        String normalizedOwnerDept = trimToNull(ownerDept);
        if (normalizedOwnerDept == null) {
            return;
        }

        AssetOwnership ownership = ownershipRepository.findByAssetTypeAndAssetId(assetType, assetId).orElseGet(AssetOwnership::new);
        writeOwnership(
            ownership,
            identity,
            normalizedOwnerDept,
            grantedBy,
            limit(identity.assetKey(), 128)
        );
        upsertManageGrant(identity, normalizedOwnerDept, grantedBy, classification, lifecycleStatus);
    }

    @Transactional
    public void synchronizeExternalCodeAsset(
        CatalogAssetIdentity identity,
        String ownerDept,
        String grantedBy,
        String classification,
        String lifecycleStatus
    ) {
        if (identity == null) {
            throw new IllegalArgumentException("asset identity is required");
        }
        String assetType = identity.grantAssetType();
        String assetId = identity.grantAssetId();
        String normalizedOwnerDept = trimToNull(ownerDept);
        String normalizedGrantedBy = defaultText(trimToNull(grantedBy), "dts-platform");
        String managedSourceId = externalSourceId(identity.assetKey());
        List<AssetOwnership> managedOwnerships = findManagedExternalOwnerships(
            identity,
            managedSourceId,
            normalizedGrantedBy
        );
        AssetOwnership current = managedOwnerships
            .stream()
            .filter(ownership -> Objects.equals(assetId, ownership.getAssetId()))
            .findFirst()
            .orElse(null);

        for (AssetOwnership ownership : managedOwnerships) {
            if (ownership != current) {
                removeManagedOwnership(identity, ownership);
            }
        }

        if (normalizedOwnerDept == null) {
            if (current != null) {
                removeManagedOwnership(identity, current);
            }
            return;
        }

        AssetOwnership ownership = current;
        if (ownership == null) {
            if (ownershipRepository.findByAssetTypeAndAssetId(assetType, assetId).isPresent()) {
                return;
            }
            ownership = new AssetOwnership();
        } else if (!sameText(ownership.getOwnerDeptCode(), normalizedOwnerDept)) {
            revokeManagedGrant(identity, ownership);
        }
        writeOwnership(
            ownership,
            identity,
            normalizedOwnerDept,
            normalizedGrantedBy,
            managedSourceId
        );
        if (!hasManualDepartmentGrant(identity, normalizedOwnerDept, normalizedGrantedBy)) {
            upsertManageGrant(identity, normalizedOwnerDept, normalizedGrantedBy, classification, lifecycleStatus);
        }
    }

    private void writeOwnership(
        AssetOwnership ownership,
        CatalogAssetIdentity identity,
        String ownerDept,
        String grantedBy,
        String sourceId
    ) {
        String assetType = identity.grantAssetType();
        String assetId = identity.grantAssetId();
        ownership.setAssetType(assetType);
        ownership.setAssetId(assetId);
        ownership.setOwnerDeptCode(ownerDept);
        ownership.setSourceId(sourceId);
        ownership.setAssignedBy(defaultText(trimToNull(grantedBy), "dts-platform"));
        ownershipRepository.save(ownership);
    }

    private void upsertManageGrant(
        CatalogAssetIdentity identity,
        String ownerDept,
        String grantedBy,
        String classification,
        String lifecycleStatus
    ) {
        permissionService.upsertGrant(
            new GrantCommand(
                identity.grantAssetType(),
                identity.grantAssetId(),
                "DEPT",
                ownerDept,
                "MANAGE",
                false,
                null,
                null,
                grantReason(identity.assetKey(), classification, lifecycleStatus),
                defaultText(trimToNull(grantedBy), "dts-platform")
            )
        );
    }

    private List<AssetOwnership> findManagedExternalOwnerships(
        CatalogAssetIdentity identity,
        String managedSourceId,
        String managedAssignedBy
    ) {
        LinkedHashSet<AssetOwnership> ownerships = new LinkedHashSet<>(
            ownershipRepository.findBySourceId(managedSourceId)
        );
        String legacySourceId = limit(identity.assetKey(), 128);
        if (!Objects.equals(managedSourceId, legacySourceId)) {
            ownershipRepository
                .findBySourceId(legacySourceId)
                .stream()
                .filter(ownership -> sameText(managedAssignedBy, ownership.getAssignedBy()))
                .forEach(ownerships::add);
        }
        return ownerships
            .stream()
            .filter(ownership -> Objects.equals(identity.grantAssetType(), ownership.getAssetType()))
            .toList();
    }

    private boolean hasManualDepartmentGrant(
        CatalogAssetIdentity identity,
        String ownerDept,
        String managedGrantedBy
    ) {
        return permissionService
            .listGrants(identity.grantAssetType(), identity.grantAssetId())
            .stream()
            .filter(grant -> sameText("DEPT", grant.getGranteeType()) && sameText(ownerDept, grant.getGranteeId()))
            .anyMatch(grant -> !isManagedGrant(identity, grant, ownerDept, managedGrantedBy));
    }

    private void removeManagedOwnership(CatalogAssetIdentity identity, AssetOwnership ownership) {
        revokeManagedGrant(identity, ownership);
        ownershipRepository.delete(ownership);
    }

    private void revokeManagedGrant(CatalogAssetIdentity identity, AssetOwnership ownership) {
        String assignedBy = trimToNull(ownership.getAssignedBy());
        String ownerDept = trimToNull(ownership.getOwnerDeptCode());
        if (assignedBy == null || ownerDept == null) {
            return;
        }
        for (AssetGrant grant : permissionService.listGrants(ownership.getAssetType(), ownership.getAssetId())) {
            if (
                grant.getId() != null
                && isManagedGrant(identity, grant, ownerDept, assignedBy)
            ) {
                permissionService.revokeGrant(ownership.getAssetType(), ownership.getAssetId(), grant.getId());
            }
        }
    }

    private static boolean isManagedGrant(
        CatalogAssetIdentity identity,
        AssetGrant grant,
        String ownerDept,
        String managedGrantedBy
    ) {
        String reasonPrefix = "code asset sync; assetKey=" + identity.assetKey() + ";";
        return (
            sameText("DEPT", grant.getGranteeType())
            && sameText(ownerDept, grant.getGranteeId())
            && sameText("MANAGE", grant.getPermission())
            && sameText(managedGrantedBy, grant.getGrantedBy())
            && grant.getGrantReason() != null
            && grant.getGrantReason().startsWith(reasonPrefix)
        );
    }

    private static String externalSourceId(String assetKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(assetKey.getBytes(StandardCharsets.UTF_8));
            return EXTERNAL_SOURCE_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static boolean sameText(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static String grantReason(String assetKey, String classification, String lifecycleStatus) {
        return "code asset sync; assetKey="
            + defaultText(trimToNull(assetKey), "unknown")
            + "; classification="
            + defaultText(trimToNull(classification), "PENDING_GOVERNANCE")
            + "; lifecycle="
            + defaultText(trimToNull(lifecycleStatus), "PENDING_GOVERNANCE");
    }

    private static String limit(String value, int maxLength) {
        String text = trimToNull(value);
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
