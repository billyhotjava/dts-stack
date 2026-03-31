package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.PlatformPermissionClient.AccessibleAssetsResult;
import com.yuzhi.dts.analytics.service.PlatformPermissionClient.PermissionResult;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Screen permission service — all decisions delegated to the platform permission system.
 *
 * <p>There are no local ACL rules here. Superuser flags, creator checks, and role-based
 * shortcuts have been removed. The platform is the single source of truth:
 * <ul>
 *   <li>MANAGE grant  → {@link PermissionSnapshot#all()} (canRead + canEdit + isOwner)</li>
 *   <li>EDIT grant    → {@link PermissionSnapshot#editOnly()} (canRead + canEdit)</li>
 *   <li>READ grant    → {@link PermissionSnapshot#readOnly()} (canRead only)</li>
 *   <li>no grant      → {@link PermissionSnapshot#none()}</li>
 * </ul>
 *
 * <p>Role-based global access (ROLE_OP_ADMIN → MANAGE, ROLE_INST_DATA_OWNER → MANAGE, etc.)
 * is handled entirely by {@code AssetPermissionService} on the platform side.
 */
@Service
public class ScreenPermissionService {

    /** Sentinel list indicating access to all screens. */
    private static final List<String> ALL_MARKER = List.of("*");

    private final PlatformPermissionClient platformPermissionClient;

    public ScreenPermissionService(PlatformPermissionClient platformPermissionClient) {
        this.platformPermissionClient = platformPermissionClient;
    }

    /**
     * Invalidate cached permission results for a given username.
     * Call after grant changes so subsequent requests reflect the updated permissions.
     */
    public void invalidateCacheForUser(String username) {
        if (username != null) {
            platformPermissionClient.invalidateCache(username);
        }
    }

    // ---- Permission snapshot ----

    public record PermissionSnapshot(boolean canRead, boolean canEdit, boolean isOwner) {

        public static PermissionSnapshot all() {
            return new PermissionSnapshot(true, true, true);
        }

        public static PermissionSnapshot editOnly() {
            return new PermissionSnapshot(true, true, false);
        }

        public static PermissionSnapshot readOnly() {
            return new PermissionSnapshot(true, false, false);
        }

        public static PermissionSnapshot none() {
            return new PermissionSnapshot(false, false, false);
        }
    }

    // ---- Main permission check ----

    /**
     * Build a permission snapshot by delegating entirely to the platform permission system.
     *
     * <p>Requires {@code platform_username} to be populated on the user entity (set during
     * SSO provisioning via {@code PlatformTrustedUserService}). Falls back to the email
     * local-part for legacy records where {@code platform_username} is not yet set.
     */
    public PermissionSnapshot snapshot(AnalyticsScreen screen, AnalyticsUser user, PlatformContext context) {
        if (user == null) {
            return PermissionSnapshot.none();
        }
        String username = extractUsername(user);
        if (username == null) {
            return PermissionSnapshot.none();
        }

        String deptCode = context != null ? context.dept() : null;
        String screenId = String.valueOf(screen.getId());

        PermissionResult result = platformPermissionClient.check(
                username, context != null ? context.roles() : null, deptCode, "SCREEN", screenId);

        if (!result.allowed()) {
            return PermissionSnapshot.none();
        }

        String permission = result.permission();
        if ("MANAGE".equalsIgnoreCase(permission)) {
            return PermissionSnapshot.all();
        }
        if ("EDIT".equalsIgnoreCase(permission)) {
            return PermissionSnapshot.editOnly();
        }
        return PermissionSnapshot.readOnly();
    }

    // ---- Accessible screen IDs ----

    /**
     * List the IDs of screens accessible to the given user via the platform.
     * Returns a sentinel list {@code ["*"]} when the platform reports global access
     * (e.g. ROLE_OP_ADMIN, ROLE_INST_DATA_OWNER, ROLE_INST_LEADER).
     */
    public List<String> listAccessibleScreenIds(AnalyticsUser user, PlatformContext context) {
        if (user == null) {
            return Collections.emptyList();
        }
        String username = extractUsername(user);
        if (username == null) {
            return Collections.emptyList();
        }

        String deptCode = context != null ? context.dept() : null;

        AccessibleAssetsResult result = platformPermissionClient.listAccessibleAssetIds(
                username, context != null ? context.roles() : null, deptCode, "SCREEN", 0, 10_000);

        if (result.isAll()) {
            return ALL_MARKER;
        }

        return result.assetIds().isEmpty() ? Collections.emptyList() : List.copyOf(result.assetIds());
    }

    /**
     * Check whether the returned list represents "all accessible" (sentinel marker).
     */
    public boolean isAllAccessible(List<String> ids) {
        return ids != null && ids.size() == 1 && "*".equals(ids.getFirst());
    }

    // ---- Stub methods for future implementation ----

    /**
     * Check whether the user's classification level permits viewing this screen.
     * Stub: always returns true.
     */
    public boolean checkClassification(AnalyticsScreen screen, String personnelLevel) {
        return true;
    }

    /**
     * Check whether the user has access to all data sources used by this screen.
     * Stub: always returns true.
     */
    public boolean checkDataSourceAccess(AnalyticsScreen screen, AnalyticsUser user) {
        return true;
    }

    // ---- Private helpers ----

    private String extractUsername(AnalyticsUser user) {
        // Prefer the Keycloak username stored during SSO provisioning.
        // Fall back to the email local-part for legacy records.
        String platformUsername = user.getPlatformUsername();
        if (platformUsername != null && !platformUsername.isBlank()) {
            return platformUsername.trim();
        }
        String email = user.getEmail();
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
