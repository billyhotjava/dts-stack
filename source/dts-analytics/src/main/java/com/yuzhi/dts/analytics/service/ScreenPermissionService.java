package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAccessRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Screen permission service — all decisions delegated to local analytics_screen_access table,
 * with an optional security-clearance gate on top.
 *
 * <p>Permission levels:
 * <ul>
 *   <li>OWNER   → {@link PermissionSnapshot#all()} (canRead + canEdit + isOwner; original creator)</li>
 *   <li>MANAGER → {@link PermissionSnapshot#managerOnly()} (canRead + canEdit + isOwner; granted manager,
 *       structurally identical to OWNER but tracked separately in the grant table)</li>
 *   <li>VIEWER  → {@link PermissionSnapshot#readOnly()} (canRead only)</li>
 *   <li>no grant → {@link PermissionSnapshot#none()}</li>
 * </ul>
 *
 * <p>Superuser bypass: {@code analytics_user.superuser = true} → skip table, full access.
 * Original creator bypass: {@code screen.creator_id == user.id} → full access.
 * Both bypasses ignore classification clearance.
 *
 * <p>classification gate: callers without OWNER/MANAGER perms also need their
 * personnel-level clearance ≥ {@code screen.classification}. The clearance ladder is
 * PUBLIC &lt; INTERNAL &lt; SECRET &lt; CONFIDENTIAL, sourced from the X-DTS-Classification
 * header (see PlatformContext). When clearance is insufficient, only a VIEWER grant with
 * {@code level_override=true} can still let the caller in — that case is reflected via
 * {@link PermissionSnapshot#overrideUsed()} so callers can write a separate audit entry.
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

    /** Classification ladder, low → high. */
    private static final List<String> CLASSIFICATION_LADDER =
            List.of("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL");

    private final AnalyticsScreenAccessRepository accessRepository;
    private final AnalyticsScreenRepository screenRepository;

    public ScreenPermissionService(
            AnalyticsScreenAccessRepository accessRepository,
            AnalyticsScreenRepository screenRepository) {
        this.accessRepository = accessRepository;
        this.screenRepository = screenRepository;
    }

    // ---- Permission snapshot ----

    public record PermissionSnapshot(boolean canRead, boolean canEdit, boolean isOwner, boolean overrideUsed) {

        public static PermissionSnapshot all() {
            return new PermissionSnapshot(true, true, true, false);
        }

        /**
         * MANAGER: granted full management — can read, edit content, and manage grants.
         * Structurally identical to {@link #all()}; kept as a separate factory to express
         * caller intent (granted manager vs. original owner / superuser).
         */
        public static PermissionSnapshot managerOnly() {
            return new PermissionSnapshot(true, true, true, false);
        }

        public static PermissionSnapshot readOnly() {
            return new PermissionSnapshot(true, false, false, false);
        }

        /**
         * VIEWER admitted only because grantee holds a level_override grant
         * while their personnel-level clearance is below screen.classification.
         */
        public static PermissionSnapshot readOnlyOverride() {
            return new PermissionSnapshot(true, false, false, true);
        }

        public static PermissionSnapshot none() {
            return new PermissionSnapshot(false, false, false, false);
        }
    }

    // ---- Main permission check ----

    /**
     * Build a permission snapshot by querying the local access table.
     *
     * <p>this overload does <strong>not</strong> apply the classification gate
     * because caller's personnel-level is unknown without {@link PlatformContext}.
     * Treated as "callerClassification = null" → no clearance check (caller's
     * responsibility to use the {@link PlatformContext} overload when classification
     * matters).
     *
     * @param roles list of role names from X-DTS-Roles header; may be empty
     */
    public PermissionSnapshot snapshot(AnalyticsScreen screen, AnalyticsUser user, List<String> roles) {
        return snapshot(screen, user, roles, null);
    }

    public PermissionSnapshot snapshot(AnalyticsScreen screen, AnalyticsUser user, PlatformContext context) {
        return snapshot(
                screen,
                user,
                context == null ? List.of() : context.rolesList(),
                context == null ? null : context.classification());
    }

    /**
     * core: snapshot with explicit caller classification. Used internally;
     * also exposed for callers that source classification from somewhere other than
     * {@link PlatformContext}.
     */
    public PermissionSnapshot snapshot(
            AnalyticsScreen screen,
            AnalyticsUser user,
            List<String> roles,
            String callerClassification) {
        if (screen == null || user == null) {
            return PermissionSnapshot.none();
        }
        // Superuser / creator bypass — including classification gate.
        if (user.isSuperuser()) {
            return PermissionSnapshot.all();
        }
        if (screen.getCreatorId() != null && screen.getCreatorId().equals(user.getId())) {
            return PermissionSnapshot.all();
        }

        String userId = resolveUserId(user);
        List<String> safeRoles = safeRoles(roles);

        List<AnalyticsScreenAccess> grants = accessRepository.findGrantsForUser(
                screen.getId(), userId, safeRoles);

        // Highest permission wins: OWNER > MANAGER > VIEWER
        boolean hasOwner = grants.stream().anyMatch(g -> "OWNER".equalsIgnoreCase(g.getPermission()));
        if (hasOwner) {
            // OWNER grant is treated like creator: bypass classification gate.
            return PermissionSnapshot.all();
        }
        boolean hasManager = grants.stream().anyMatch(g -> "MANAGER".equalsIgnoreCase(g.getPermission()));
        if (hasManager) {
            // MANAGER grant also bypasses classification gate (they manage the dashboard).
            return PermissionSnapshot.managerOnly();
        }
        boolean hasViewer = grants.stream().anyMatch(g -> "VIEWER".equalsIgnoreCase(g.getPermission()));
        if (!hasViewer) {
            return PermissionSnapshot.none();
        }

        // VIEWER path → classification gate applies.
        if (isClassificationAllowed(callerClassification, screen.getClassification())) {
            return PermissionSnapshot.readOnly();
        }
        // Clearance insufficient — only level_override VIEWER grant lets the caller in.
        boolean hasLevelOverride = grants
                .stream()
                .anyMatch(g -> "VIEWER".equalsIgnoreCase(g.getPermission()) && g.isLevelOverride());
        if (hasLevelOverride) {
            return PermissionSnapshot.readOnlyOverride();
        }
        return PermissionSnapshot.none();
    }

    /**
     * Compare caller's clearance to the screen's classification on the
     * PUBLIC &lt; INTERNAL &lt; SECRET &lt; CONFIDENTIAL ladder.
     *
     * <ul>
     *   <li>screen has no classification (null/blank/unknown) → treat as PUBLIC, allow.</li>
     *   <li>caller has no classification (null/blank/unknown) → conservative deny.</li>
     *   <li>both recognized → caller_rank ≥ screen_rank.</li>
     * </ul>
     */
    private boolean isClassificationAllowed(String callerLevel, String screenLevel) {
        int screenRank = ladderRank(screenLevel);
        if (screenRank < 0) {
            return true; // unknown / blank screen classification → permissive
        }
        int callerRank = ladderRank(callerLevel);
        if (callerRank < 0) {
            return false; // unknown caller clearance → conservative deny
        }
        return callerRank >= screenRank;
    }

    private int ladderRank(String level) {
        if (level == null) return -1;
        String upper = level.trim().toUpperCase(Locale.ROOT);
        if (upper.isEmpty()) return -1;
        return CLASSIFICATION_LADDER.indexOf(upper);
    }

    // ---- Accessible screen IDs ----

    /**
     * Returns IDs of screens accessible to the user.
     * Returns sentinel {@code [-1L]} when the user is a superuser (access to all screens).
     *
     * @param roles list of role names from X-DTS-Roles header; may be empty
     */
    public List<Long> listAccessibleScreenIds(AnalyticsUser user, PlatformContext context) {
        return listAccessibleScreenIds(user, context == null ? List.of() : context.rolesList());
    }

    public List<Long> listAccessibleScreenIds(AnalyticsUser user, List<String> roles) {
        if (user == null) {
            return Collections.emptyList();
        }
        if (user.isSuperuser()) {
            return ALL_MARKER;
        }

        String userId = resolveUserId(user);
        List<String> safeRoles = safeRoles(roles);

        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (user.getId() != null) {
            ids.addAll(screenRepository.findIdsByCreatorIdAndArchivedFalse(user.getId()));
        }
        ids.addAll(accessRepository.findAccessibleScreenIds(userId, safeRoles));
        return ids.isEmpty() ? Collections.emptyList() : List.copyOf(ids);
    }

    /**
     * Check whether the returned list represents "all accessible" (superuser sentinel).
     */
    public boolean isAllAccessible(List<Long> ids) {
        return ids != null && ids.size() == 1 && ids.getFirst().equals(-1L);
    }

    // ---- Private helpers ----

    private List<String> safeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of(NO_ROLE_PLACEHOLDER);
        }
        return roles;
    }

    /**
     * Resolve the user identifier used for USER-type grant lookups.
     * Prefers platformUsername (Keycloak username, e.g. "test230917") because that is
     * what the ACL panel stores.  Falls back to the numeric string ID for accounts that
     * were created before the platform-username field was populated (e.g. legacy superusers).
     */
    private String resolveUserId(AnalyticsUser user) {
        String pn = user.getPlatformUsername();
        return (pn != null && !pn.isBlank()) ? pn : String.valueOf(user.getId());
    }
}
