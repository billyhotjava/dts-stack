package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.permission.AssetOwnership;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.GrantCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CodeAssetGrantWriter {

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
        ownership.setAssetType(assetType);
        ownership.setAssetId(assetId);
        ownership.setOwnerDeptCode(normalizedOwnerDept);
        ownership.setSourceId(limit(identity.assetKey(), 128));
        ownership.setAssignedBy(defaultText(trimToNull(grantedBy), "dts-platform"));
        ownershipRepository.save(ownership);

        permissionService.upsertGrant(
            new GrantCommand(
                assetType,
                assetId,
                "DEPT",
                normalizedOwnerDept,
                "MANAGE",
                false,
                null,
                null,
                grantReason(identity.assetKey(), classification, lifecycleStatus),
                defaultText(trimToNull(grantedBy), "dts-platform")
            )
        );
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
