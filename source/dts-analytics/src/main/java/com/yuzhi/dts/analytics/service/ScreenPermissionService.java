package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.service.PlatformPermissionClient.AccessibleAssetsResult;
import com.yuzhi.dts.analytics.service.PlatformPermissionClient.PermissionResult;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Unified screen permission service. Replaces the old ScreenAclService by delegating
 * to the platform's asset permission system via {@link PlatformPermissionClient}.
 */
@Service
public class ScreenPermissionService {

    private static final Logger LOG = LoggerFactory.getLogger(ScreenPermissionService.class);

    private static final String ROLE_OP_ADMIN = "ROLE_OP_ADMIN";
    private static final String ROLE_INST_DATA_OWNER = "ROLE_INST_DATA_OWNER";
    private static final String ROLE_INST_LEADER = "ROLE_INST_LEADER";
    private static final String ROLE_DEPT_DATA_OWNER = "ROLE_DEPT_DATA_OWNER";
    private static final String ROLE_DEPT_LEADER = "ROLE_DEPT_LEADER";

    /** Sentinel list indicating access to all screens. */
    private static final List<String> ALL_MARKER = List.of("*");

    private final PlatformPermissionClient platformPermissionClient;
    private final AnalyticsScreenRepository screenRepository;

    public ScreenPermissionService(PlatformPermissionClient platformPermissionClient,
                                   AnalyticsScreenRepository screenRepository) {
        this.platformPermissionClient = platformPermissionClient;
        this.screenRepository = screenRepository;
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
     * Build a permission snapshot for the given screen, user, and platform context.
     * Follows a priority chain: null user, superuser, creator, role baseline, then
     * delegates to the platform permission system.
     */
    public PermissionSnapshot snapshot(AnalyticsScreen screen, AnalyticsUser user, PlatformContext context) {
        if (user == null) {
            return PermissionSnapshot.none();
        }

        if (user.isSuperuser()) {
            return PermissionSnapshot.all();
        }

        if (isCreator(screen, user)) {
            return PermissionSnapshot.all();
        }

        Set<String> roles = parseRoles(context != null ? context.roles() : null);

        // Role-based baseline
        if (hasAnyRole(roles, ROLE_OP_ADMIN)) {
            return PermissionSnapshot.all();
        }
        if (hasAnyRole(roles, ROLE_INST_DATA_OWNER, ROLE_INST_LEADER)) {
            return PermissionSnapshot.editOnly();
        }
        if (hasAnyRole(roles, ROLE_DEPT_DATA_OWNER, ROLE_DEPT_LEADER)) {
            // Dept-scoped roles: would check ownerDeptCode if available on AnalyticsScreen.
            // Currently screens do not carry dept ownership, so fall through to platform check.
            LOG.trace("Dept role detected but screen has no ownerDeptCode; delegating to platform");
        }

        // Delegate to platform unified permission system
        String username = extractUsername(user);
        String deptCode = context != null ? context.dept() : null;
        String screenId = String.valueOf(screen.getId());

        PermissionResult result = platformPermissionClient.check(
                username, context != null ? context.roles() : null, deptCode, "SCREEN", screenId);

        if (!result.allowed()) {
            return PermissionSnapshot.none();
        }

        String permission = result.permission();
        if (permission != null && ("MANAGE".equalsIgnoreCase(permission) || "EDIT".equalsIgnoreCase(permission))) {
            return PermissionSnapshot.editOnly();
        }

        return PermissionSnapshot.readOnly();
    }

    // ---- Accessible screen IDs ----

    /**
     * List the IDs of screens accessible to the given user.
     * Returns a sentinel list {@code ["*"]} when the user has access to all screens.
     */
    public List<String> listAccessibleScreenIds(AnalyticsUser user, PlatformContext context) {
        if (user == null) {
            return Collections.emptyList();
        }

        Set<String> roles = parseRoles(context != null ? context.roles() : null);

        // Users with broad privileges see everything
        if (user.isSuperuser()
                || hasAnyRole(roles, ROLE_OP_ADMIN, ROLE_INST_DATA_OWNER, ROLE_INST_LEADER)) {
            return ALL_MARKER;
        }

        String username = extractUsername(user);
        String deptCode = context != null ? context.dept() : null;

        AccessibleAssetsResult result = platformPermissionClient.listAccessibleAssetIds(
                username, context != null ? context.roles() : null, deptCode, "SCREEN", 0, 10_000);

        if (result.isAll()) {
            return ALL_MARKER;
        }

        // Merge platform result with creator-owned screens
        Set<String> merged = new LinkedHashSet<>(result.assetIds());

        // Always include screens the user created
        screenRepository.findAllByArchivedFalseOrderByIdDesc().stream()
                .filter(s -> isCreator(s, user))
                .map(s -> String.valueOf(s.getId()))
                .forEach(merged::add);

        return List.copyOf(merged);
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

    private boolean isCreator(AnalyticsScreen screen, AnalyticsUser user) {
        if (screen == null || user == null) {
            return false;
        }
        Long creatorId = screen.getCreatorId();
        return creatorId != null && creatorId.equals(user.getId());
    }

    private Set<String> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String extractUsername(AnalyticsUser user) {
        // Prefer the stored Keycloak username (set during SSO provisioning).
        // Fall back to extracting the local part of the email for legacy records.
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

    private boolean hasAnyRole(Set<String> roles, String... targets) {
        for (String target : targets) {
            if (roles.contains(target)) {
                return true;
            }
        }
        return false;
    }
}
