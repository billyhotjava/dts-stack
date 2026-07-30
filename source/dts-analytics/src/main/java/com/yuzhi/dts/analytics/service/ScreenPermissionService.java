package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAccessRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import com.yuzhi.dts.analytics.web.support.RequestContext;
import com.yuzhi.dts.analytics.web.support.RequestContextHolder;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Screen permission service — platform asset_grant is the source of truth.
 * The local analytics_screen_access table is retained only as a read-only fallback
 * during the migration window.
 *
 * <p>Permission levels:
 * <ul>
 *   <li>OWNER   → {@link PermissionSnapshot#all()} (canRead + canEdit + isOwner; original creator)</li>
 *   <li>MANAGER → {@link PermissionSnapshot#managerOnly()} (canRead + canEdit + isOwner; granted manager,
 *       structurally identical to OWNER but tracked separately in the grant table)</li>
 *   <li>VIEWER  → {@link PermissionSnapshot#readOnly()} (canRead only)</li>
 *   <li>PUBLIC screen without grant → {@link PermissionSnapshot#readOnly()} (canRead only)</li>
 *   <li>non-PUBLIC screen without grant → {@link PermissionSnapshot#none()}</li>
 * </ul>
 *
 * <p>Superuser bypass remains available only after the current classification snapshot
 * is verified and is always returned as {@code overrideUsed=true} plus a strong audit
 * event. Creator, OWNER and MANAGER identities provide base access/management only;
 * they never bypass personnel classification clearance.
 *
 * <p>classification gate: all non-superuser callers need their personnel-level clearance
 * ≥ {@code screen.classification}. The clearance ladder is
 * PUBLIC &lt; INTERNAL &lt; SECRET &lt; CONFIDENTIAL, sourced from the X-DTS-Classification
 * header (see PlatformContext). When clearance is insufficient, only a VIEWER grant with
 * {@code level_override=true} can still let the caller in — that case is reflected via
 * {@link PermissionSnapshot#overrideUsed()} so callers can write a separate audit entry.
 */
@Service
public class ScreenPermissionService {

    private static final Logger LOG = LoggerFactory.getLogger(ScreenPermissionService.class);
    private static final String SCREEN_ASSET_TYPE = "SCREEN";

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
    private final PlatformPermissionClient platformClient;
    private final AnalyticsConsumerClassificationService classificationService;
    private final ScreenAuditService auditService;
    private final boolean platformSourceEnabled;
    private final boolean localFallbackEnabled;

    public ScreenPermissionService(
            AnalyticsScreenAccessRepository accessRepository,
            AnalyticsScreenRepository screenRepository) {
        this(accessRepository, screenRepository, null, false, true, null, null);
    }

    public ScreenPermissionService(
            AnalyticsScreenAccessRepository accessRepository,
            AnalyticsScreenRepository screenRepository,
            PlatformPermissionClient platformClient,
            boolean platformSourceEnabled,
            boolean localFallbackEnabled) {
        this(accessRepository, screenRepository, platformClient, platformSourceEnabled, localFallbackEnabled, null, null);
    }

    public ScreenPermissionService(
            AnalyticsScreenAccessRepository accessRepository,
            AnalyticsScreenRepository screenRepository,
            PlatformPermissionClient platformClient,
            boolean platformSourceEnabled,
            boolean localFallbackEnabled,
            ScreenAuditService auditService) {
        this(accessRepository, screenRepository, platformClient, platformSourceEnabled, localFallbackEnabled, auditService, null);
    }

    @Autowired
    public ScreenPermissionService(
            AnalyticsScreenAccessRepository accessRepository,
            AnalyticsScreenRepository screenRepository,
            PlatformPermissionClient platformClient,
            @Value("${dts.analytics.screen-permission.platform-source-enabled:true}") boolean platformSourceEnabled,
            @Value("${dts.analytics.screen-permission.local-fallback-enabled:true}") boolean localFallbackEnabled,
            ScreenAuditService auditService,
            AnalyticsConsumerClassificationService classificationService) {
        this.accessRepository = accessRepository;
        this.screenRepository = screenRepository;
        this.platformClient = platformClient;
        this.classificationService = classificationService;
        this.auditService = auditService;
        this.platformSourceEnabled = platformSourceEnabled;
        this.localFallbackEnabled = localFallbackEnabled;
    }

    // ---- Permission snapshot ----

    public record PermissionSnapshot(boolean canRead, boolean canEdit, boolean isOwner, boolean overrideUsed) {

        public static PermissionSnapshot all() {
            return new PermissionSnapshot(true, true, true, false);
        }

        public static PermissionSnapshot allOverride() {
            return new PermissionSnapshot(true, true, true, true);
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
     * Build a permission snapshot through platform first, with local fallback when enabled.
     *
     * <p>This overload has no caller personnel-level. For classified screens,
     * callers should use the {@link PlatformContext} overload so VIEWER grants
     * can pass the classification gate.
     *
     * @param roles list of role names from X-DTS-Roles header; may be empty
     */
    public PermissionSnapshot snapshot(AnalyticsScreen screen, AnalyticsUser user, List<String> roles) {
        return snapshot(screen, user, roles, null);
    }

    public PermissionSnapshot snapshot(AnalyticsScreen screen, AnalyticsUser user, PlatformContext context) {
        return snapshotWithPlatformContext(
                screen,
                user,
                context == null ? List.of() : context.rolesList(),
                context == null ? null : context.dept(),
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
        return snapshotWithPlatformContext(screen, user, roles, null, callerClassification);
    }

    private PermissionSnapshot snapshotWithPlatformContext(
            AnalyticsScreen screen,
            AnalyticsUser user,
            List<String> roles,
            String deptCode,
            String callerClassification) {
        if (screen == null || user == null) {
            return PermissionSnapshot.none();
        }
        if (classificationService != null) {
            try {
                classificationService.requireCurrentScreen(screen.getId());
            } catch (RuntimeException ex) {
                PermissionSnapshot unresolvedDraftSnapshot =
                    unresolvedDraftCreatorSnapshot(screen, user, callerClassification);
                if (unresolvedDraftSnapshot.canRead()) {
                    LOG.warn(
                        "event=screen_classification_guard_deferred screenId={} user={} reason=unresolved_draft",
                        screen.getId(),
                        resolveUserId(user)
                    );
                    return unresolvedDraftSnapshot;
                }
                LOG.warn(
                    "event=screen_classification_guard_denied screenId={} user={} reason={}",
                    screen.getId(),
                    resolveUserId(user),
                    ex.getMessage()
                );
                return PermissionSnapshot.none();
            }
        }
        if (ladderRank(screen.getClassification()) < 0) {
            return PermissionSnapshot.none();
        }
        if (user.isSuperuser()) {
            auditSuperuserOverride(screen, user);
            return PermissionSnapshot.allOverride();
        }

        if (platformEnabled()) {
            String userId = resolveUserId(user);
            String rolesCsv = rolesCsv(roles);
            PlatformPermissionClient.PermissionResult result = platformClient.check(
                userId,
                rolesCsv,
                deptCode,
                SCREEN_ASSET_TYPE,
                String.valueOf(screen.getId()),
                callerClassification,
                screen.getClassification()
            );
            if (result.allowed()) {
                if ("superuser_override".equalsIgnoreCase(result.reason())) {
                    auditSuperuserOverride(screen, user);
                }
                return fromPlatformResult(result);
            }
            if (!localFallbackEnabled) {
                return PermissionSnapshot.none();
            }
            PermissionSnapshot fallback = localSnapshot(screen, user, roles, callerClassification);
            if (fallback.canRead()) {
                LOG.warn(
                    "event=analytics_permission_fallback action=snapshot screenId={} user={} platformReason={} fallback=local",
                    screen.getId(),
                    userId,
                    result.reason()
                );
            }
            return fallback;
        }

        return localSnapshot(screen, user, roles, callerClassification);
    }

    private PermissionSnapshot unresolvedDraftCreatorSnapshot(
            AnalyticsScreen screen,
            AnalyticsUser user,
            String callerClassification) {
        if (
            screen.getCreatorId() == null ||
            !screen.getCreatorId().equals(user.getId()) ||
            !isClassificationAllowed(callerClassification, screen.getClassification())
        ) {
            return PermissionSnapshot.none();
        }
        try {
            return classificationService.hasUnresolvedScreenSources(screen)
                ? PermissionSnapshot.all()
                : PermissionSnapshot.none();
        } catch (RuntimeException ex) {
            LOG.warn(
                "event=screen_unresolved_draft_guard_denied screenId={} user={} reason={}",
                screen.getId(),
                resolveUserId(user),
                ex.getMessage()
            );
            return PermissionSnapshot.none();
        }
    }

    private PermissionSnapshot localSnapshot(
            AnalyticsScreen screen,
            AnalyticsUser user,
            List<String> roles,
            String callerClassification) {
        boolean classificationAllowed =
            isClassificationAllowed(callerClassification, screen.getClassification());
        if (screen.getCreatorId() != null && screen.getCreatorId().equals(user.getId())) {
            return classificationAllowed ? PermissionSnapshot.all() : PermissionSnapshot.none();
        }

        String userId = resolveUserId(user);
        List<String> safeRoles = safeRoles(roles);

        List<AnalyticsScreenAccess> grants = accessRepository.findGrantsForUser(
                screen.getId(), userId, safeRoles);

        // Highest permission wins: OWNER > MANAGER > VIEWER
        boolean hasOwner = grants.stream().anyMatch(g -> "OWNER".equalsIgnoreCase(g.getPermission()));
        if (hasOwner) {
            return classificationAllowed ? PermissionSnapshot.all() : PermissionSnapshot.none();
        }
        boolean hasManager = grants.stream().anyMatch(g -> "MANAGER".equalsIgnoreCase(g.getPermission()));
        if (hasManager) {
            return classificationAllowed ? PermissionSnapshot.managerOnly() : PermissionSnapshot.none();
        }
        boolean hasViewer = grants.stream().anyMatch(g -> "VIEWER".equalsIgnoreCase(g.getPermission()));
        if (!hasViewer) {
            return isPublic(screen.getClassification()) ? PermissionSnapshot.readOnly() : PermissionSnapshot.none();
        }

        // VIEWER path → classification gate applies.
        if (classificationAllowed) {
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
     *   <li>screen has no classification (null/blank/unknown) → deny; missing classification must be remediated in platform.</li>
     *   <li>caller has no classification (null/blank/unknown) → conservative deny.</li>
     *   <li>both recognized → caller_rank ≥ screen_rank.</li>
     * </ul>
     */
    private boolean isClassificationAllowed(String callerLevel, String screenLevel) {
        int screenRank = ladderRank(screenLevel);
        if (screenRank < 0) {
            return false;
        }
        if (screenRank == 0) {
            return true;
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

    private void auditSuperuserOverride(AnalyticsScreen screen, AnalyticsUser user) {
        if (auditService == null) {
            LOG.warn(
                "event=screen_classification_superuser_override_audit_unavailable screenId={} user={}",
                screen.getId(),
                resolveUserId(user)
            );
            return;
        }
        RequestContext requestContext = RequestContextHolder.current();
        auditService.log(
            screen.getId(),
            user.getId(),
            "screen.classification.superuser_override",
            null,
            Map.of(
                "classification", screen.getClassification(),
                "overrideUsed", true,
                "reason", "SUPERUSER_EXPLICIT_OVERRIDE"
            ),
            requestContext == null ? null : requestContext.requestId()
        );
    }

    // ---- Accessible screen IDs ----

    /**
     * Returns IDs of screens accessible to the user.
     * Returns sentinel {@code [-1L]} when the user is a superuser (access to all screens).
     *
     * @param roles list of role names from X-DTS-Roles header; may be empty
     */
    public List<Long> listAccessibleScreenIds(AnalyticsUser user, PlatformContext context) {
        return listAccessibleScreenIds(
            user,
            context == null ? List.of() : context.rolesList(),
            context == null ? null : context.dept(),
            context == null ? null : context.classification()
        );
    }

    public List<Long> listAccessibleScreenIds(AnalyticsUser user, List<String> roles) {
        return listAccessibleScreenIds(user, roles, null, null);
    }

    private List<Long> listAccessibleScreenIds(AnalyticsUser user, List<String> roles, String deptCode, String callerClassification) {
        if (user == null) {
            return Collections.emptyList();
        }
        if (user.isSuperuser()) {
            return ALL_MARKER;
        }

        if (platformEnabled()) {
            String userId = resolveUserId(user);
            PlatformPermissionClient.AccessibleAssetsResult result = platformClient.listAccessibleAssetIds(
                userId,
                rolesCsv(roles),
                deptCode,
                SCREEN_ASSET_TYPE,
                0,
                10000,
                callerClassification
            );
            if (result.isAll()) {
                return ALL_MARKER;
            }

            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            if (!result.isError()) {
                for (String id : result.assetIds()) {
                    Long parsed = parseLong(id);
                    if (parsed != null) {
                        ids.add(parsed);
                    }
                }
            }

            if (localFallbackEnabled) {
                List<Long> localIds = localAccessibleScreenIds(user, roles);
                boolean usedFallback = localIds.stream().anyMatch(id -> !ids.contains(id));
                ids.addAll(localIds);
                if (usedFallback || result.isError()) {
                    LOG.warn(
                        "event=analytics_permission_fallback action=list_accessible user={} platformScope={} fallback=local",
                        userId,
                        result.scope()
                    );
                }
            } else if (result.isError()) {
                return Collections.emptyList();
            }

            return ids.isEmpty() ? Collections.emptyList() : List.copyOf(ids);
        }

        return localAccessibleScreenIds(user, roles);
    }

    private List<Long> localAccessibleScreenIds(AnalyticsUser user, List<String> roles) {
        String userId = resolveUserId(user);
        List<String> safeRoles = safeRoles(roles);

        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (user.getId() != null) {
            ids.addAll(screenRepository.findIdsByCreatorIdAndArchivedFalse(user.getId()));
        }
        ids.addAll(screenRepository.findPublicIds());
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

    private boolean isPublic(String classification) {
        return classification != null && "PUBLIC".equalsIgnoreCase(classification.trim());
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

    private boolean platformEnabled() {
        return platformSourceEnabled && platformClient != null;
    }

    private PermissionSnapshot fromPlatformResult(PlatformPermissionClient.PermissionResult result) {
        String permission = result.permission() == null ? "" : result.permission().trim().toUpperCase(Locale.ROOT);
        if ("superuser_override".equalsIgnoreCase(result.reason())) {
            return PermissionSnapshot.allOverride();
        }
        if ("MANAGE".equals(permission) || "EDIT".equals(permission)) {
            return PermissionSnapshot.managerOnly();
        }
        if ("READ".equals(permission) && "level_override".equalsIgnoreCase(result.reason())) {
            return PermissionSnapshot.readOnlyOverride();
        }
        if ("READ".equals(permission)) {
            return PermissionSnapshot.readOnly();
        }
        return result.allowed() ? PermissionSnapshot.readOnly() : PermissionSnapshot.none();
    }

    private String rolesCsv(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        return String.join(",", roles);
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
