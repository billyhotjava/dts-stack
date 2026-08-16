package com.yuzhi.dts.platform.service.permission;

import com.yuzhi.dts.platform.domain.permission.AssetOwnership;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.GrantCommand;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetAccessRegistrationService {

    private final AssetOwnershipRepository ownershipRepository;
    private final AssetPermissionService permissionService;
    private final AssetPermissionAuditService auditService;

    public AssetAccessRegistrationService(
        AssetOwnershipRepository ownershipRepository,
        AssetPermissionService permissionService,
        AssetPermissionAuditService auditService
    ) {
        this.ownershipRepository = ownershipRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    @Transactional
    public RegistrationResult register(RegistrationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("registration command is required");
        }
        String assetType = normalizeType(command.assetType());
        String assetId = trimToNull(command.assetId());
        String ownerDeptCode = trimToNull(command.ownerDeptCode());
        String ownerUsername = trimToNull(command.ownerUsername());
        String assignedBy = defaultText(command.assignedBy(), ownerUsername, "system");
        if (assetType == null || assetId == null) {
            throw new IllegalArgumentException("assetType and assetId are required");
        }
        if (ownerDeptCode == null && ownerUsername == null) {
            throw new IllegalArgumentException("ownerDeptCode or ownerUsername is required");
        }

        AssetOwnership ownership = null;
        if (ownerDeptCode != null) {
            ownership = ownershipRepository
                .findByAssetTypeAndAssetId(assetType, assetId)
                .orElseGet(AssetOwnership::new);
            String oldDeptCode = trimToNull(ownership.getOwnerDeptCode());
            ownership.setAssetType(assetType);
            ownership.setAssetId(assetId);
            ownership.setOwnerDeptCode(ownerDeptCode);
            ownership.setAssignedBy(assignedBy);
            String sourceId = limit(command.sourceId(), 128);
            if (sourceId != null) {
                ownership.setSourceId(sourceId);
            }
            ownership = ownershipRepository.save(ownership);
            if (!sameText(oldDeptCode, ownerDeptCode)) {
                auditService.recordOwnershipChange(assetType, assetId, oldDeptCode, ownerDeptCode);
            }
        }

        boolean creatorGrantRegistered = false;
        if (ownerUsername != null) {
            permissionService.upsertGrant(
                new GrantCommand(
                    assetType,
                    assetId,
                    "USER",
                    ownerUsername,
                    "MANAGE",
                    false,
                    null,
                    null,
                    "asset creator access registration",
                    assignedBy
                )
            );
            creatorGrantRegistered = true;
        }
        return new RegistrationResult(ownership, creatorGrantRegistered);
    }

    private static String normalizeType(String value) {
        String text = trimToNull(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private static boolean sameText(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static String defaultText(String... values) {
        for (String value : values) {
            String text = trimToNull(value);
            if (text != null) {
                return text;
            }
        }
        return "system";
    }

    private static String limit(String value, int maxLength) {
        String text = trimToNull(value);
        return text == null || text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record RegistrationCommand(
        String assetType,
        String assetId,
        String ownerDeptCode,
        String ownerUsername,
        String assignedBy,
        String sourceId
    ) {}

    public record RegistrationResult(AssetOwnership ownership, boolean creatorGrantRegistered) {
        public boolean ownershipRegistered() {
            return Objects.nonNull(ownership);
        }
    }
}
