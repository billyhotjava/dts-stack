package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAccessRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Screen ownership service — manages grants in the local analytics_screen_access table.
 * No platform API calls. No RestTemplate.
 */
@Service
public class ScreenOwnershipService {

    private final AnalyticsScreenAccessRepository accessRepository;

    public ScreenOwnershipService(AnalyticsScreenAccessRepository accessRepository) {
        this.accessRepository = accessRepository;
    }

    /**
     * List all grants for a screen, as map objects compatible with the frontend ScreenSharePanel format.
     */
    public List<Map<String, Object>> listGrants(Long screenId) {
        return accessRepository.findByScreenId(screenId).stream()
                .map(this::toMap)
                .toList();
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
        Optional<AnalyticsScreenAccess> existing =
                accessRepository.findByScreenIdAndGranteeTypeAndGranteeId(screenId, granteeType, granteeId);

        AnalyticsScreenAccess record = existing.orElseGet(AnalyticsScreenAccess::new);
        record.setScreenId(screenId);
        record.setGranteeType(granteeType);
        record.setGranteeId(granteeId);
        record.setPermission(permission);
        record.setGrantedBy(grantedBy);
        record.setLevelOverride(levelOverride && "VIEWER".equalsIgnoreCase(permission));
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
        return accessRepository.deleteByIdAndScreenId(grantId, screenId) > 0;
    }

    /**
     * Remove all grants for a screen. Call on screen deletion/archival.
     */
    @Transactional
    public void removeAllGrants(Long screenId) {
        accessRepository.deleteByScreenId(screenId);
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
        return map;
    }
}
