package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAccessRepository;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Screen permission service — all decisions delegated to local analytics_screen_access table.
 *
 * <p>Permission levels:
 * <ul>
 *   <li>OWNER   → {@link PermissionSnapshot#all()} (canRead + canEdit + isOwner)</li>
 *   <li>MANAGER → {@link PermissionSnapshot#managerOnly()} (canRead + isOwner, no canEdit)</li>
 *   <li>VIEWER  → {@link PermissionSnapshot#readOnly()} (canRead only)</li>
 *   <li>no grant → {@link PermissionSnapshot#none()}</li>
 * </ul>
 *
 * <p>Superuser bypass: {@code analytics_user.superuser = true} → skip table, full access.
 * No hardcoded role names. No default grants.
 */
@Service
public class ScreenPermissionService {

    /** Sentinel list: first element is -1L, indicates access to ALL screens (superuser). */
    private static final List<Long> ALL_MARKER = List.of(-1L);

    /**
     * Placeholder role used in JPQL IN clause when the user has no roles,
     * to prevent empty collection binding which causes a SQL syntax error.
     */
    private static final String NO_ROLE_PLACEHOLDER = "__NO_ROLE__";

    private final AnalyticsScreenAccessRepository accessRepository;

    public ScreenPermissionService(AnalyticsScreenAccessRepository accessRepository) {
        this.accessRepository = accessRepository;
    }

    // ---- Permission snapshot ----

    public record PermissionSnapshot(boolean canRead, boolean canEdit, boolean isOwner) {

        public static PermissionSnapshot all() {
            return new PermissionSnapshot(true, true, true);
        }

        /** MANAGER: can read and manage grants, but cannot edit screen content. */
        public static PermissionSnapshot managerOnly() {
            return new PermissionSnapshot(true, false, true);
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
     * Build a permission snapshot by querying the local access table.
     *
     * @param roles list of role names from X-DTS-Roles header; may be empty
     */
    public PermissionSnapshot snapshot(AnalyticsScreen screen, AnalyticsUser user, List<String> roles) {
        if (user == null) {
            return PermissionSnapshot.none();
        }
        if (user.isSuperuser()) {
            return PermissionSnapshot.all();
        }

        String userId = String.valueOf(user.getId());
        List<String> safeRoles = safeRoles(roles);

        List<AnalyticsScreenAccess> grants = accessRepository.findGrantsForUser(
                screen.getId(), userId, safeRoles);

        // Highest permission wins: OWNER > MANAGER > VIEWER
        boolean hasOwner = grants.stream().anyMatch(g -> "OWNER".equalsIgnoreCase(g.getPermission()));
        if (hasOwner) {
            return PermissionSnapshot.all();
        }
        boolean hasManager = grants.stream().anyMatch(g -> "MANAGER".equalsIgnoreCase(g.getPermission()));
        if (hasManager) {
            return PermissionSnapshot.managerOnly();
        }
        boolean hasViewer = grants.stream().anyMatch(g -> "VIEWER".equalsIgnoreCase(g.getPermission()));
        if (hasViewer) {
            return PermissionSnapshot.readOnly();
        }
        return PermissionSnapshot.none();
    }

    // ---- Accessible screen IDs ----

    /**
     * Returns IDs of screens accessible to the user.
     * Returns sentinel {@code [-1L]} when the user is a superuser (access to all screens).
     *
     * @param roles list of role names from X-DTS-Roles header; may be empty
     */
    public List<Long> listAccessibleScreenIds(AnalyticsUser user, List<String> roles) {
        if (user == null) {
            return Collections.emptyList();
        }
        if (user.isSuperuser()) {
            return ALL_MARKER;
        }

        String userId = String.valueOf(user.getId());
        List<String> safeRoles = safeRoles(roles);

        List<Long> ids = accessRepository.findAccessibleScreenIds(userId, safeRoles);
        return ids.isEmpty() ? Collections.emptyList() : List.copyOf(ids);
    }

    /**
     * Check whether the returned list represents "all accessible" (superuser sentinel).
     */
    public boolean isAllAccessible(List<Long> ids) {
        return ids != null && ids.size() == 1 && ids.getFirst().equals(-1L);
    }

    // ---- Stub methods for future implementation ----

    /** Always returns true. Classification enforcement is not yet implemented. */
    public boolean checkClassification(AnalyticsScreen screen, String personnelLevel) {
        return true;
    }

    /** Always returns true. Data source access enforcement is not yet implemented. */
    public boolean checkDataSourceAccess(AnalyticsScreen screen, AnalyticsUser user) {
        return true;
    }

    // ---- Private helpers ----

    private List<String> safeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of(NO_ROLE_PLACEHOLDER);
        }
        return roles;
    }
}
