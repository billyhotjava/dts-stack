package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionIdentityResolver.ResolvedPermissionIdentity;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.permission.AssetPermissionAuditService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionDecision;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionResult;
import com.yuzhi.dts.platform.web.rest.catalog.CatalogResourceHelper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogAssetTagWriteGuard {

    private static final String REQUIRED_PERMISSION = "EDIT";
    private static final String ACTION = "TAG";

    private final CatalogAssetTagPermissionIdentityResolver identityResolver;
    private final AssetPermissionService permissionService;
    private final AssetPermissionAuditService permissionAuditService;
    private final CatalogResourceHelper catalogResourceHelper;

    public CatalogAssetTagWriteGuard(
        CatalogAssetTagPermissionIdentityResolver identityResolver,
        AssetPermissionService permissionService,
        AssetPermissionAuditService permissionAuditService,
        CatalogResourceHelper catalogResourceHelper
    ) {
        this.identityResolver = identityResolver;
        this.permissionService = permissionService;
        this.permissionAuditService = permissionAuditService;
        this.catalogResourceHelper = catalogResourceHelper;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuthorizedAssets authorizeAll(List<AssetRef> requestedAssets) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (!StringUtils.hasText(actor)) {
            throw new CatalogAssetTagPermissionException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                null,
                null,
                "请先登录后再执行资产打标"
            );
        }
        List<AssetRef> assets = normalizeAndDeduplicate(requestedAssets);
        List<ResolvedPermissionIdentity> identities;
        try {
            identities = identityResolver.resolveAll(assets);
        } catch (CatalogAssetTagPermissionException exception) {
            recordIdentityDenial(exception, actor);
            throw exception;
        }

        List<String> roles = SecurityUtils.getCurrentUserAuthorities();
        String department = SecurityUtils.getCurrentUserDept().orElse(null);
        List<DeniedAsset> denied = new ArrayList<>();
        List<AssetRef> authorized = new ArrayList<>(identities.size());
        for (ResolvedPermissionIdentity identity : identities) {
            PermissionDecision decision = authorizeIdentity(identity, actor, roles, department);
            permissionAuditService.recordDecision(decision, actor, actor);
            if (decision.allowed()) {
                authorized.add(new AssetRef(identity.requestedType().name(), identity.canonicalAssetKey()));
            } else {
                denied.add(new DeniedAsset(identity, decision));
            }
        }
        if (!denied.isEmpty()) {
            DeniedAsset first = denied.getFirst();
            throw new CatalogAssetTagPermissionException(
                HttpStatus.FORBIDDEN,
                first.decision().reasonCode(),
                first.identity().requestedType(),
                first.identity().canonicalAssetKey(),
                "至少一个资产未通过 WRITE 权限预检，拒绝整个打标请求",
                denied.size()
            );
        }
        return new AuthorizedAssets(List.copyOf(authorized), actor);
    }

    /**
     * Side-effect-free capability probe for read contracts.
     *
     * <p>The write endpoints still call {@link #authorizeAll(List)} as the authoritative
     * enforcement point. A missing authentication, unresolved canonical identity or permission
     * infrastructure failure is deliberately reported as {@code false}.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean canTag(AssetRef requestedAsset) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (!StringUtils.hasText(actor)) {
            return false;
        }
        try {
            AssetRef normalized = normalizeAndDeduplicate(
                List.of(requestedAsset)
            ).getFirst();
            ResolvedPermissionIdentity identity = identityResolver
                .resolveAll(List.of(normalized))
                .getFirst();
            return authorizeIdentity(
                identity,
                actor,
                SecurityUtils.getCurrentUserAuthorities(),
                SecurityUtils.getCurrentUserDept().orElse(null)
            ).allowed();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private PermissionDecision authorizeIdentity(
        ResolvedPermissionIdentity identity,
        String actor,
        List<String> roles,
        String department
    ) {
        if (
            identity.requestedType() == CatalogAssetType.DATASET ||
            identity.dataset() != null
        ) {
            if (identity.dataset() == null) {
                return deniedDecision(
                    identity,
                    null,
                    "INVALID_PERMISSION_IDENTITY",
                    "DATASET 权限身份缺少 legacy dataset 实体",
                    null
                );
            }
            try {
                catalogResourceHelper.ensureDatasetEditPermission(identity.dataset());
                return PermissionDecision.allowed(
                    identity.grantAssetType(),
                    identity.grantAssetId(),
                    identity.canonicalAssetKey(),
                    ACTION,
                    REQUIRED_PERMISSION,
                    REQUIRED_PERMISSION,
                    "dataset_edit_boundary",
                    "NOT_APPLIED",
                    "dataset_edit_boundary"
                );
            } catch (ResponseStatusException exception) {
                return deniedDecision(
                    identity,
                    null,
                    "NO_GRANT",
                    exception.getReason(),
                    "dataset_edit_boundary"
                );
            }
        }

        PermissionResult result = permissionService.check(
            actor,
            roles,
            department,
            identity.grantAssetType(),
            identity.grantAssetId()
        );
        if (result.allowed() && isWritePermission(result.permission())) {
            return PermissionDecision.allowed(
                identity.grantAssetType(),
                identity.grantAssetId(),
                identity.canonicalAssetKey(),
                ACTION,
                result.permission(),
                REQUIRED_PERMISSION,
                result.reason(),
                "NOT_APPLIED",
                result.reason()
            );
        }
        String reasonCode = result.allowed() ? "INSUFFICIENT_PERMISSION" : "NO_GRANT";
        String reason = result.allowed() ? "insufficient_permission" : result.reason();
        return deniedDecision(
            identity,
            result.permission(),
            reasonCode,
            "资产需要 EDIT 或 MANAGE 权限",
            result.reason(),
            reason
        );
    }

    private PermissionDecision deniedDecision(
        ResolvedPermissionIdentity identity,
        String permission,
        String reasonCode,
        String reasonDetail,
        String grantSource
    ) {
        return deniedDecision(identity, permission, reasonCode, reasonDetail, grantSource, "denied");
    }

    private PermissionDecision deniedDecision(
        ResolvedPermissionIdentity identity,
        String permission,
        String reasonCode,
        String reasonDetail,
        String grantSource,
        String reason
    ) {
        return new PermissionDecision(
            false,
            permission,
            reason,
            REQUIRED_PERMISSION,
            ACTION,
            identity.grantAssetType(),
            identity.grantAssetId(),
            identity.canonicalAssetKey(),
            "NOT_APPLIED",
            grantSource,
            reasonCode,
            StringUtils.hasText(reasonDetail) ? reasonDetail : "资产写权限预检失败",
            "请申请该资产的 EDIT 或 MANAGE 权限",
            Instant.now()
        );
    }

    private void recordIdentityDenial(CatalogAssetTagPermissionException exception, String actor) {
        PermissionDecision decision = new PermissionDecision(
            false,
            null,
            "identity_resolution_denied",
            REQUIRED_PERMISSION,
            ACTION,
            exception.assetType() == null ? null : exception.assetType().name(),
            null,
            exception.assetKey(),
            "NOT_APPLIED",
            null,
            exception.reasonCode(),
            exception.getReason(),
            "请使用平台返回的 canonical 资产标识，或先补齐该类型的权限身份链",
            Instant.now()
        );
        permissionAuditService.recordDecision(decision, actor, actor);
    }

    private List<AssetRef> normalizeAndDeduplicate(List<AssetRef> requestedAssets) {
        if (requestedAssets == null || requestedAssets.isEmpty()) {
            throw new CatalogAssetTagPermissionException(
                HttpStatus.BAD_REQUEST,
                "INVALID_ASSET_REQUEST",
                null,
                null,
                "资产不能为空"
            );
        }
        Map<String, AssetRef> unique = new LinkedHashMap<>();
        for (AssetRef asset : requestedAssets) {
            String type = asset == null || asset.assetType() == null
                ? null
                : asset.assetType().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            String key = asset == null || asset.assetKey() == null ? null : asset.assetKey().trim();
            AssetRef normalized = new AssetRef(type, key);
            unique.putIfAbsent(String.valueOf(type) + "\u0000" + String.valueOf(key), normalized);
        }
        return List.copyOf(unique.values());
    }

    private boolean isWritePermission(String permission) {
        return "EDIT".equalsIgnoreCase(permission) || "MANAGE".equalsIgnoreCase(permission);
    }

    private record DeniedAsset(ResolvedPermissionIdentity identity, PermissionDecision decision) {}

    public record AuthorizedAssets(List<AssetRef> assets, String actor) {
        public AuthorizedAssets {
            assets = assets == null ? List.of() : List.copyOf(assets);
        }
    }
}
