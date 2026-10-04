package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAccessRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Screen ownership service — manages screen grants through platform asset_grant.
 *
 * <p>The local analytics_screen_access table is retained only as read-only fallback
 * and emergency write fallback for tests/explicit break-glass configuration.
 */
@Service
public class ScreenOwnershipService {

    private static final Logger LOG = LoggerFactory.getLogger(ScreenOwnershipService.class);
    private static final String SCREEN_ASSET_TYPE = "SCREEN";
    private static final String GRANT_REASON_PREFIX = "analytics_screen_permission:";

    private final AnalyticsScreenAccessRepository accessRepository;
    private final AnalyticsUserRepository userRepository;
    private final PlatformPermissionClient platformClient;
    private final boolean platformSourceEnabled;
    private final boolean localFallbackEnabled;
    private final boolean localWriteEnabled;

    public ScreenOwnershipService(AnalyticsScreenAccessRepository accessRepository) {
        this(accessRepository, null, null, false, true, true, false);
    }

    public ScreenOwnershipService(
        AnalyticsScreenAccessRepository accessRepository,
        PlatformPermissionClient platformClient,
        boolean platformSourceEnabled,
        boolean localFallbackEnabled,
        boolean localWriteEnabled
    ) {
        this(accessRepository, null, platformClient, platformSourceEnabled, localFallbackEnabled, localWriteEnabled, !localWriteEnabled);
    }

    public ScreenOwnershipService(
        AnalyticsScreenAccessRepository accessRepository,
        PlatformPermissionClient platformClient,
        boolean platformSourceEnabled,
        boolean localFallbackEnabled,
        boolean localWriteEnabled,
        boolean localIamReadOnly
    ) {
        this(accessRepository, null, platformClient, platformSourceEnabled, localFallbackEnabled, localWriteEnabled, localIamReadOnly);
    }

    ScreenOwnershipService(
        AnalyticsScreenAccessRepository accessRepository,
        AnalyticsUserRepository userRepository,
        PlatformPermissionClient platformClient,
        boolean platformSourceEnabled,
        boolean localFallbackEnabled,
        boolean localWriteEnabled
    ) {
        this(accessRepository, userRepository, platformClient, platformSourceEnabled, localFallbackEnabled, localWriteEnabled, !localWriteEnabled);
    }

    @Autowired
    public ScreenOwnershipService(
        AnalyticsScreenAccessRepository accessRepository,
        AnalyticsUserRepository userRepository,
        PlatformPermissionClient platformClient,
        @Value("${dts.analytics.screen-permission.platform-source-enabled:true}") boolean platformSourceEnabled,
        @Value("${dts.analytics.screen-permission.local-fallback-enabled:true}") boolean localFallbackEnabled,
        @Value("${dts.analytics.screen-permission.local-write-enabled:false}") boolean localWriteEnabled,
        @Value("${analytics.local-iam.read-only:true}") boolean localIamReadOnly
    ) {
        this.accessRepository = accessRepository;
        this.userRepository = userRepository;
        this.platformClient = platformClient;
        this.platformSourceEnabled = platformSourceEnabled;
        this.localFallbackEnabled = localFallbackEnabled;
        this.localWriteEnabled = localWriteEnabled && !localIamReadOnly;
    }

    /**
     * List all grants for a screen, as map objects compatible with the frontend ScreenSharePanel format.
     */
    public List<Map<String, Object>> listGrants(Long screenId) {
        if (platformEnabled()) {
            try {
                List<Map<String, Object>> platformGrants = platformClient.listGrants(SCREEN_ASSET_TYPE, String.valueOf(screenId)).stream()
                        .map(row -> platformGrantToMap(screenId, row))
                        .toList();
                if (!localFallbackEnabled) {
                    return platformGrants;
                }
                return mergeLegacyLocalGrants(screenId, platformGrants);
            } catch (PlatformPermissionClient.PlatformPermissionException ex) {
                if (!localFallbackEnabled) {
                    throw ex;
                }
                LOG.warn("event=analytics_permission_fallback action=list_grants screenId={} reason=platform_unavailable", screenId);
            }
        }
        return localListGrants(screenId);
    }

    private List<Map<String, Object>> localListGrants(Long screenId) {
        return accessRepository.findByScreenId(screenId).stream()
                .map(this::toMap)
                .toList();
    }

    private List<Map<String, Object>> mergeLegacyLocalGrants(Long screenId, List<Map<String, Object>> platformGrants) {
        List<Map<String, Object>> merged = new ArrayList<>(platformGrants);
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> grant : platformGrants) {
            seen.add(grantIdentityKey(grant));
        }
        for (Map<String, Object> localGrant : localListGrants(screenId)) {
            if (seen.add(grantIdentityKey(localGrant))) {
                merged.add(localGrant);
            }
        }
        return merged;
    }

    /**
     * Create or update a grant (UPSERT by screenId + granteeType + granteeId).
     *
     * @param screenId    target screen
     * @param granteeType "USER" or "ROLE"
     * @param granteeId   analytics user.id as String for USER, role name for ROLE
     * @param permission  "OWNER", "MANAGER", or "VIEWER"
     * @param grantedBy   analytics user.id of the granter
     */
    @Transactional
    public AnalyticsScreenAccess createGrant(Long screenId, String granteeType, String granteeId,
                                              String permission, Long grantedBy) {
        return createGrant(screenId, granteeType, granteeId, permission, grantedBy, false);
    }

    /**
     * create / update grant with explicit level_override flag.
     *
     * <p>Semantics:
     * <ul>
     *   <li>{@code levelOverride=true} only carries meaning for VIEWER grants.
     *       OWNER / MANAGER bypass the classification gate by definition, so this
     *       method silently coerces {@code levelOverride} to {@code false} for those
     *       perms — keeps the table from carrying noise that could mislead an
     *       auditor.</li>
     *   <li>UPSERT semantics by (screenId, granteeType, granteeId) preserved. When
     *       updating an existing grant, the new {@code levelOverride} replaces the
     *       prior value.</li>
     * </ul>
     */
    @Transactional
    public AnalyticsScreenAccess createGrant(Long screenId, String granteeType, String granteeId,
                                              String permission, Long grantedBy, boolean levelOverride) {
        if (platformEnabled()) {
            try {
                String analyticsPermission = normalizeAnalyticsPermission(permission);
                Map<String, Object> row = platformClient.upsertGrant(
                    SCREEN_ASSET_TYPE,
                    String.valueOf(screenId),
                    normalizeGranteeType(granteeType),
                    granteeId,
                    toPlatformPermission(analyticsPermission),
                    levelOverride && "VIEWER".equals(analyticsPermission),
                    grantedBy == null ? null : String.valueOf(grantedBy),
                    GRANT_REASON_PREFIX + analyticsPermission
                );
                return platformGrantToAccess(screenId, row, granteeType, granteeId, analyticsPermission, grantedBy);
            } catch (PlatformPermissionClient.PlatformPermissionException ex) {
                if (!localWriteEnabled) {
                    throw new IllegalStateException("platform permission service unavailable", ex);
                }
                LOG.warn("event=analytics_permission_fallback action=create_grant screenId={} granteeType={} granteeId={} reason=platform_unavailable",
                    screenId, granteeType, granteeId);
            }
        }
        return createLocalGrant(screenId, granteeType, granteeId, permission, grantedBy, levelOverride);
    }

    private AnalyticsScreenAccess createLocalGrant(Long screenId, String granteeType, String granteeId,
                                                   String permission, Long grantedBy, boolean levelOverride) {
        String analyticsPermission = normalizeAnalyticsPermission(permission);
        String normalizedGranteeType = normalizeGranteeType(granteeType);
        Optional<AnalyticsScreenAccess> existing =
                accessRepository.findByScreenIdAndGranteeTypeAndGranteeId(screenId, normalizedGranteeType, granteeId);

        AnalyticsScreenAccess record = existing.orElseGet(AnalyticsScreenAccess::new);
        record.setScreenId(screenId);
        record.setGranteeType(normalizedGranteeType);
        record.setGranteeId(granteeId);
        record.setPermission(analyticsPermission);
        record.setGrantedBy(grantedBy);
        record.setLevelOverride(levelOverride && "VIEWER".equals(analyticsPermission));
        if (record.getGrantedAt() == null) {
            record.setGrantedAt(Instant.now());
        }
        return accessRepository.save(record);
    }

    /**
     * Revoke (delete) a specific grant by its local table ID.
     */
    @Transactional
    public void revokeGrant(Long grantId) {
        accessRepository.deleteById(grantId);
    }

    /**
     * Revoke a specific grant, only if it belongs to the given screen.
     * Returns true if the grant was deleted, false if it did not exist or belonged to a different screen.
     */
    @Transactional
    public boolean revokeGrantForScreen(Long grantId, Long screenId) {
        if (platformEnabled()) {
            try {
                boolean revoked = platformClient.revokeGrant(SCREEN_ASSET_TYPE, String.valueOf(screenId), grantId);
                if (revoked || !localFallbackEnabled) {
                    return revoked;
                }
                int legacyDeleted = accessRepository.deleteByIdAndScreenId(grantId, screenId);
                if (legacyDeleted > 0) {
                    LOG.info("event=analytics_permission_legacy_local_revoke screenId={} grantId={}", screenId, grantId);
                    return true;
                }
                return false;
            } catch (PlatformPermissionClient.PlatformPermissionException ex) {
                if (!localWriteEnabled) {
                    throw new IllegalStateException("platform permission service unavailable", ex);
                }
                LOG.warn("event=analytics_permission_fallback action=revoke_grant screenId={} grantId={} reason=platform_unavailable",
                    screenId, grantId);
            }
        }
        return accessRepository.deleteByIdAndScreenId(grantId, screenId) > 0;
    }

    /**
     * Remove all grants for a screen. Call on screen deletion/archival.
     */
    @Transactional
    public void removeAllGrants(Long screenId) {
        if (platformEnabled()) {
            try {
                platformClient.revokeAllGrants(SCREEN_ASSET_TYPE, String.valueOf(screenId));
                return;
            } catch (PlatformPermissionClient.PlatformPermissionException ex) {
                if (!localWriteEnabled) {
                    throw new IllegalStateException("platform permission service unavailable", ex);
                }
                LOG.warn("event=analytics_permission_fallback action=remove_all_grants screenId={} reason=platform_unavailable", screenId);
            }
        }
        accessRepository.deleteByScreenId(screenId);
    }

    private boolean platformEnabled() {
        return platformSourceEnabled && platformClient != null;
    }

    private Map<String, Object> platformGrantToMap(Long screenId, Map<String, Object> row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", longVal(row.get("id")));
        map.put("screenId", screenId);
        map.put("granteeType", stringVal(row.get("granteeType")));
        map.put("granteeId", stringVal(row.get("granteeId")));
        map.put("permission", fromPlatformPermission(stringVal(row.get("permission")), stringVal(row.get("grantReason"))));
        map.put("levelOverride", booleanVal(row.get("levelOverride")));
        map.put("grantedBy", stringVal(row.get("grantedBy")));
        map.put("grantedAt", row.get("grantedAt"));
        map.put("grantSource", "platform");
        putIfPresent(map, "granteeName", firstNonBlank(row.get("granteeName"), row.get("displayName"), row.get("granteeLabel")));
        putIfPresent(map, "granteeUsername", stringVal(row.get("granteeUsername")));
        return withUserDisplayFields(map);
    }

    private AnalyticsScreenAccess platformGrantToAccess(
        Long screenId,
        Map<String, Object> row,
        String granteeType,
        String granteeId,
        String fallbackPermission,
        Long fallbackGrantedBy
    ) {
        AnalyticsScreenAccess access = new AnalyticsScreenAccess();
        access.setId(longVal(row.get("id")));
        access.setScreenId(screenId);
        access.setGranteeType(stringVal(row.get("granteeType")) != null ? stringVal(row.get("granteeType")) : normalizeGranteeType(granteeType));
        access.setGranteeId(stringVal(row.get("granteeId")) != null ? stringVal(row.get("granteeId")) : granteeId);
        access.setPermission(fromPlatformPermission(stringVal(row.get("permission")), stringVal(row.get("grantReason"))));
        if (access.getPermission() == null) {
            access.setPermission(fallbackPermission);
        }
        access.setLevelOverride(booleanVal(row.get("levelOverride")) && "VIEWER".equals(access.getPermission()));
        access.setGrantedBy(fallbackGrantedBy);
        Instant grantedAt = instantVal(row.get("grantedAt"));
        access.setGrantedAt(grantedAt != null ? grantedAt : Instant.now());
        return access;
    }

    private String toPlatformPermission(String analyticsPermission) {
        return switch (normalizeAnalyticsPermission(analyticsPermission)) {
            case "OWNER", "MANAGER" -> "MANAGE";
            case "VIEWER" -> "READ";
            default -> throw new IllegalArgumentException("permission must be OWNER, MANAGER, or VIEWER");
        };
    }

    private String fromPlatformPermission(String platformPermission, String grantReason) {
        String reason = grantReason == null ? "" : grantReason.trim().toUpperCase(Locale.ROOT);
        return switch (normalizeToken(platformPermission)) {
            case "MANAGE", "EDIT" -> reason.endsWith(":OWNER") ? "OWNER" : "MANAGER";
            case "READ" -> "VIEWER";
            default -> null;
        };
    }

    private String normalizeAnalyticsPermission(String permission) {
        return switch (normalizeToken(permission)) {
            case "OWNER" -> "OWNER";
            case "MANAGER", "MANAGE", "EDIT" -> "MANAGER";
            case "VIEWER", "VIEW", "READ" -> "VIEWER";
            default -> throw new IllegalArgumentException("permission must be OWNER, MANAGER, or VIEWER");
        };
    }

    private String normalizeGranteeType(String value) {
        String token = normalizeToken(value);
        if (!"USER".equals(token) && !"ROLE".equals(token) && !"DEPT".equals(token)) {
            throw new IllegalArgumentException("granteeType must be USER, ROLE, or DEPT");
        }
        return token;
    }

    private String normalizeToken(String value) {
        String text = value == null ? null : value.trim();
        return text == null || text.isEmpty() ? "" : text.toUpperCase(Locale.ROOT);
    }

    private String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Long longVal(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        String text = stringVal(value);
        if (text == null) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean booleanVal(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(stringVal(value));
    }

    private Instant instantVal(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        String text = stringVal(value);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private Map<String, Object> toMap(AnalyticsScreenAccess a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", a.getId());
        map.put("screenId", a.getScreenId());
        map.put("granteeType", a.getGranteeType());
        map.put("granteeId", a.getGranteeId());
        map.put("permission", a.getPermission());
        map.put("levelOverride", a.isLevelOverride());
        map.put("grantedBy", a.getGrantedBy());
        map.put("grantedAt", a.getGrantedAt());
        map.put("grantSource", "local");
        return withUserDisplayFields(map);
    }

    private Map<String, Object> withUserDisplayFields(Map<String, Object> map) {
        if (!"USER".equals(normalizeToken(stringVal(map.get("granteeType"))))) {
            return map;
        }
        resolveAnalyticsUser(stringVal(map.get("granteeId"))).ifPresent(user -> {
            putIfMissing(map, "granteeUsername", firstNonBlank(user.getPlatformUsername(), user.getEmail(), user.getId()));
            putIfMissing(map, "granteeName", analyticsUserDisplayName(user));
            putIfMissing(map, "displayName", analyticsUserDisplayName(user));
        });
        return map;
    }

    private Optional<AnalyticsUser> resolveAnalyticsUser(String granteeId) {
        if (userRepository == null) {
            return Optional.empty();
        }
        Long numericId = longVal(granteeId);
        if (numericId != null) {
            Optional<AnalyticsUser> user = userRepository.findById(numericId);
            if (user.isPresent()) {
                return user;
            }
        }
        String text = stringVal(granteeId);
        if (text == null) {
            return Optional.empty();
        }
        Optional<AnalyticsUser> byPlatformUsername = userRepository.findByPlatformUsernameIgnoreCase(text);
        if (byPlatformUsername.isPresent()) {
            return byPlatformUsername;
        }
        return userRepository.findByEmailIgnoreCase(text);
    }

    private String grantIdentityKey(Map<String, Object> grant) {
        String type = normalizeToken(stringVal(grant.get("granteeType")));
        String granteeId = stringVal(grant.get("granteeId"));
        if ("USER".equals(type)) {
            granteeId = resolveAnalyticsUser(granteeId)
                .map(user -> firstNonBlank(user.getPlatformUsername(), user.getEmail(), user.getId()))
                .orElse(granteeId);
        }
        return type + ":" + normalizeLookupKey(granteeId);
    }

    private String normalizeLookupKey(Object value) {
        String text = stringVal(value);
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private String analyticsUserDisplayName(AnalyticsUser user) {
        String fullName = firstNonBlank(
            joinNameParts(user.getFirstName(), user.getLastName()),
            user.getPlatformUsername(),
            user.getEmail(),
            user.getId()
        );
        return fullName == null ? null : fullName;
    }

    private String joinNameParts(String firstName, String lastName) {
        String first = stringVal(firstName);
        String last = stringVal(lastName);
        if (first == null) {
            return last;
        }
        if (last == null) {
            return first;
        }
        return first + " " + last;
    }

    private String firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            String text = stringVal(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        String text = stringVal(value);
        if (text != null) {
            map.put(key, text);
        }
    }

    private void putIfMissing(Map<String, Object> map, String key, Object value) {
        if (stringVal(map.get(key)) == null) {
            putIfPresent(map, key, value);
        }
    }
}
