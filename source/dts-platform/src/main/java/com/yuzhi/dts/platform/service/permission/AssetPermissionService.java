package com.yuzhi.dts.platform.service.permission;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.domain.permission.AssetOwnership;
import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.repository.permission.AssetGrantRepository;
import com.yuzhi.dts.platform.repository.permission.AssetOwnershipRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AssetPermissionService {

    private static final Logger log = LoggerFactory.getLogger(AssetPermissionService.class);

    private static final String SCREEN_ASSET_TYPE = "SCREEN";
    private static final String SCREEN_CODE_PREFIX = "screen-";
    private static final String DEFAULT_SCREEN_CLASSIFICATION = "INTERNAL";
    private static final String NO_ROLE_PLACEHOLDER = "__NO_ROLE__";
    private static final List<String> CLASSIFICATION_LADDER = List.of("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL");

    private static final Set<String> SUPERUSER_ROLES = Set.of(
        AuthoritiesConstants.ADMIN,
        AuthoritiesConstants.OP_ADMIN
    );

    private static final Set<String> INST_MANAGE_ROLES = Set.of(
        AuthoritiesConstants.INST_DATA_OWNER
    );

    private static final Set<String> INST_READ_ROLES = Set.of(
        AuthoritiesConstants.INST_LEADER
    );

    private static final Set<String> DEPT_MANAGE_ROLES = Set.of(
        AuthoritiesConstants.DEPT_DATA_OWNER
    );

    private static final Set<String> DEPT_READ_ROLES = Set.of(
        AuthoritiesConstants.DEPT_LEADER
    );

    private static final Set<String> GLOBAL_ACCESS_ROLES;
    static {
        GLOBAL_ACCESS_ROLES = new HashSet<>();
        GLOBAL_ACCESS_ROLES.addAll(SUPERUSER_ROLES);
        GLOBAL_ACCESS_ROLES.addAll(INST_MANAGE_ROLES);
        GLOBAL_ACCESS_ROLES.addAll(INST_READ_ROLES);
    }

    /**
     * Asset types that require explicit grants only.
     * Role-based implicit access (INST/DEPT roles) is bypassed for these types;
     * only superuser roles and explicit grants are honoured.
     */
    private static final Set<String> EXPLICIT_GRANT_ONLY_TYPES = Set.of(SCREEN_ASSET_TYPE);

    private static final Map<String, Integer> PERMISSION_RANK = Map.of(
        "MANAGE", 3,
        "EDIT", 2,
        "READ", 1
    );

    private final AssetOwnershipRepository ownershipRepository;
    private final AssetGrantRepository grantRepository;
    private final BiReportLinkRepository reportLinkRepository;

    public AssetPermissionService(AssetOwnershipRepository ownershipRepository, AssetGrantRepository grantRepository) {
        this(ownershipRepository, grantRepository, null);
    }

    @Autowired
    public AssetPermissionService(
        AssetOwnershipRepository ownershipRepository,
        AssetGrantRepository grantRepository,
        BiReportLinkRepository reportLinkRepository
    ) {
        this.ownershipRepository = ownershipRepository;
        this.grantRepository = grantRepository;
        this.reportLinkRepository = reportLinkRepository;
    }

    public PermissionResult check(String username, List<String> roles, String deptCode, String assetType, String assetId) {
        return check(username, roles, deptCode, assetType, assetId, null, null);
    }

    public PermissionResult check(
        String username,
        List<String> roles,
        String deptCode,
        String assetType,
        String assetId,
        String userClassification,
        String assetClassification
    ) {
        String normalizedAssetType = normalizeAssetType(assetType);
        if (SCREEN_ASSET_TYPE.equals(normalizedAssetType)) {
            return checkScreen(username, roles, deptCode, assetId, userClassification, assetClassification);
        }

        // 1. Superuser roles → MANAGE all (applies to every asset type)
        if (hasAny(roles, SUPERUSER_ROLES)) {
            return PermissionResult.allowed("MANAGE", "superuser");
        }

        boolean explicitOnly = EXPLICIT_GRANT_ONLY_TYPES.contains(normalizedAssetType);

        if (!explicitOnly) {
            // 2. Institute leader → READ all
            if (hasAny(roles, INST_READ_ROLES)) {
                if (hasAny(roles, INST_MANAGE_ROLES)) {
                    return PermissionResult.allowed("MANAGE", "inst_manage");
                }
                return PermissionResult.allowed("READ", "inst_read");
            }

            // 3. Institute data owner → MANAGE all
            if (hasAny(roles, INST_MANAGE_ROLES)) {
                return PermissionResult.allowed("MANAGE", "inst_manage");
            }

            // 4. Department-level: check ownership
            Optional<AssetOwnership> ownership = ownershipRepository.findByAssetTypeAndAssetId(normalizedAssetType, assetId);
            if (ownership.isPresent() && deptCode != null && deptCode.equalsIgnoreCase(ownership.orElseThrow().getOwnerDeptCode())) {
                if (hasAny(roles, DEPT_MANAGE_ROLES)) {
                    return PermissionResult.allowed("MANAGE", "dept_ownership");
                }
                if (hasAny(roles, DEPT_READ_ROLES)) {
                    return PermissionResult.allowed("READ", "dept_ownership");
                }
            }
        }

        // 5. Explicit grants (USER / ROLE / DEPT)
        List<String> roleList = safeRoles(roles);
        String effectiveDeptCode = deptCode != null ? deptCode : "";
        List<AssetGrant> grants = grantRepository.findActiveGrantsForUser(
            normalizedAssetType, assetId, username, roleList, effectiveDeptCode, Instant.now()
        );
        if (!grants.isEmpty()) {
            String highestPermission = grants.stream()
                .map(AssetGrant::getPermission)
                .max(Comparator.comparingInt(p -> PERMISSION_RANK.getOrDefault(p, 0)))
                .orElse("READ");
            return PermissionResult.allowed(highestPermission, "explicit_grant");
        }

        // 6. Deny
        return PermissionResult.denied();
    }

    public PermissionDecision checkAction(PermissionCheckCommand command) {
        if (command == null) {
            return PermissionDecision.denied(null, null, null, null, null, "READ", "request_required", "NOT_APPLIED", null);
        }
        String assetType = normalizeAssetType(command.assetType());
        String assetId = firstNonBlank(command.assetId(), command.assetKey());
        String action = normalizeAction(command.action());
        String requiredPermission = requiredPermission(action);
        if (assetType == null || assetId == null) {
            return PermissionDecision.denied(
                assetType,
                assetId,
                command.assetKey(),
                action,
                null,
                requiredPermission,
                "asset_required",
                "NOT_APPLIED",
                null
            );
        }
        if (requiredPermission == null) {
            return PermissionDecision.denied(
                assetType,
                assetId,
                command.assetKey(),
                action,
                null,
                null,
                "unsupported_action",
                "NOT_APPLIED",
                null
            );
        }

        PermissionResult base = check(
            command.username(),
            command.userRoles(),
            command.userDeptCode(),
            assetType,
            assetId,
            command.userClassification(),
            command.assetClassification()
        );
        String classificationDecision = classificationDecision(
            command.userRoles(),
            base.reason(),
            command.userClassification(),
            command.assetClassification()
        );
        if (!base.allowed()) {
            return PermissionDecision.denied(
                assetType,
                assetId,
                command.assetKey(),
                action,
                base.permission(),
                requiredPermission,
                base.reason(),
                classificationDecision,
                base.reason()
            );
        }
        if ("MISSING_ASSET_CLASSIFICATION".equals(classificationDecision)) {
            return PermissionDecision.denied(
                assetType,
                assetId,
                command.assetKey(),
                action,
                base.permission(),
                requiredPermission,
                "classification_required",
                classificationDecision,
                base.reason(),
                "Asset classification is missing for " + assetType + ":" + assetId + ".",
                "Set the asset classification in platform governance metadata."
            );
        }
        if ("DENIED".equals(classificationDecision)) {
            return PermissionDecision.denied(
                assetType,
                assetId,
                command.assetKey(),
                action,
                base.permission(),
                requiredPermission,
                "classification_denied",
                classificationDecision,
                base.reason(),
                "User classification " + firstNonBlank(command.userClassification(), "<none>") +
                " is lower than asset classification " + firstNonBlank(command.assetClassification(), "<none>") + ".",
                "Request a classification review or use a lower-sensitivity asset."
            );
        }
        if (!permissionCovers(base.permission(), requiredPermission)) {
            return PermissionDecision.denied(
                assetType,
                assetId,
                command.assetKey(),
                action,
                base.permission(),
                requiredPermission,
                "insufficient_permission",
                classificationDecision,
                base.reason()
            );
        }
        return PermissionDecision.allowed(
            assetType,
            assetId,
            command.assetKey(),
            action,
            base.permission(),
            requiredPermission,
            base.reason(),
            classificationDecision,
            base.reason()
        );
    }

    public Map<String, PermissionResult> batchCheck(String username, List<String> roles, String deptCode,
                                                     List<AssetRef> assets) {
        return batchCheck(username, roles, deptCode, assets, null);
    }

    public Map<String, PermissionResult> batchCheck(String username, List<String> roles, String deptCode,
                                                     List<AssetRef> assets, String userClassification) {
        // Short-circuit for superuser roles (applies to every asset type)
        if (hasAny(roles, SUPERUSER_ROLES)) {
            return assets.stream().collect(Collectors.toMap(
                a -> a.type() + ":" + a.id(),
                a -> PermissionResult.allowed("MANAGE", "superuser")
            ));
        }

        // Per-asset check — delegates to check() which honours EXPLICIT_GRANT_ONLY_TYPES
        Map<String, PermissionResult> results = new LinkedHashMap<>();
        for (AssetRef asset : assets) {
            PermissionResult result = check(username, roles, deptCode, asset.type(), asset.id(), userClassification, null);
            results.put(asset.type() + ":" + asset.id(), result);
        }
        return results;
    }

    public AccessibleAssetsResult listAccessibleAssetIds(String username, List<String> roles, String deptCode,
                                                          String assetType, Pageable pageable) {
        return listAccessibleAssetIds(username, roles, deptCode, assetType, pageable, null);
    }

    public AccessibleAssetsResult listAccessibleAssetIds(
        String username,
        List<String> roles,
        String deptCode,
        String assetType,
        Pageable pageable,
        String userClassification
    ) {
        String normalizedAssetType = normalizeAssetType(assetType);
        if (SCREEN_ASSET_TYPE.equals(normalizedAssetType)) {
            return listAccessibleScreenIds(username, roles, deptCode, pageable, userClassification);
        }

        boolean explicitOnly = EXPLICIT_GRANT_ONLY_TYPES.contains(normalizedAssetType);

        // Global roles see everything — but not for explicit-only asset types
        if (!explicitOnly && hasAny(roles, GLOBAL_ACCESS_ROLES)) {
            return AccessibleAssetsResult.all();
        }
        // Superuser always sees everything, regardless of asset type
        if (hasAny(roles, SUPERUSER_ROLES)) {
            return AccessibleAssetsResult.all();
        }

        // Collect IDs from department ownership + explicit grants
        Set<String> assetIds = new LinkedHashSet<>();

        // Department-level roles: add owned assets (skipped for explicit-only types)
        if (!explicitOnly && deptCode != null && hasAny(roles, Stream.concat(DEPT_MANAGE_ROLES.stream(), DEPT_READ_ROLES.stream())
                .collect(Collectors.toSet()))) {
            List<String> deptAssets = ownershipRepository.findAssetIdsByTypeAndDeptCode(normalizedAssetType, deptCode);
            assetIds.addAll(deptAssets);
        }

        // Explicit grants
        List<String> roleList = safeRoles(roles);
        String effectiveDeptCode = deptCode != null ? deptCode : "";
        List<String> grantedIds = grantRepository.findAccessibleAssetIdsByGrant(
            normalizedAssetType, username, roleList, effectiveDeptCode, Instant.now()
        );
        assetIds.addAll(grantedIds);

        List<String> resultList = new ArrayList<>(assetIds);
        int total = resultList.size();

        // Apply pagination
        int offset = (int) pageable.getOffset();
        int size = pageable.getPageSize();
        if (offset >= total) {
            return new AccessibleAssetsResult(List.of(), total, "FILTERED");
        }
        List<String> page = resultList.subList(offset, Math.min(offset + size, total));
        return new AccessibleAssetsResult(page, total, "FILTERED");
    }

    public List<AssetGrant> listGrants(String assetType, String assetId) {
        return grantRepository.findByAssetTypeAndAssetId(normalizeAssetType(assetType), assetId);
    }

    @Transactional
    public AssetGrant upsertGrant(GrantCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("request body required");
        }
        String assetType = normalizeAssetType(command.assetType());
        String assetId = trimToNull(command.assetId());
        String granteeType = normalizeToken(command.granteeType());
        String granteeId = trimToNull(command.granteeId());
        String permission = normalizePermission(command.permission());
        if (assetType == null || assetId == null || granteeType == null || granteeId == null || permission == null) {
            throw new IllegalArgumentException("assetType, assetId, granteeType, granteeId, and permission are required");
        }
        if (!Set.of("USER", "ROLE", "DEPT").contains(granteeType)) {
            throw new IllegalArgumentException("granteeType must be USER, ROLE, or DEPT");
        }

        List<AssetGrant> existing = grantRepository.findByAssetTypeAndAssetId(assetType, assetId)
            .stream()
            .filter(g -> granteeType.equalsIgnoreCase(g.getGranteeType()) && granteeId.equalsIgnoreCase(g.getGranteeId()))
            .toList();
        AssetGrant grant = existing
            .stream()
            .filter(g -> permission.equalsIgnoreCase(g.getPermission()))
            .findFirst()
            .orElseGet(() -> existing.isEmpty() ? new AssetGrant() : existing.get(0));
        List<AssetGrant> redundant = existing.stream().filter(g -> g != grant).toList();
        if (!redundant.isEmpty()) {
            grantRepository.deleteAll(redundant);
            grantRepository.flush();
        }

        grant.setAssetType(assetType);
        grant.setAssetId(assetId);
        grant.setGranteeType(granteeType);
        grant.setGranteeId(granteeId);
        grant.setPermission(permission);
        grant.setLevelOverride(Boolean.TRUE.equals(command.levelOverride()) && "READ".equals(permission));
        grant.setValidFrom(command.validFrom());
        grant.setValidTo(command.validTo());
        grant.setGrantedBy(trimToNull(command.grantedBy()) != null ? command.grantedBy().trim() : "system");
        grant.setGrantReason(trimToNull(command.grantReason()));
        return grantRepository.save(grant);
    }

    @Transactional
    public boolean revokeGrant(String assetType, String assetId, Long grantId) {
        if (grantId == null) {
            return false;
        }
        String normalizedAssetType = normalizeAssetType(assetType);
        String normalizedAssetId = trimToNull(assetId);
        return grantRepository.findById(grantId)
            .filter(g -> Objects.equals(normalizedAssetType, g.getAssetType()) && Objects.equals(normalizedAssetId, g.getAssetId()))
            .map(g -> {
                grantRepository.delete(g);
                return true;
            })
            .orElse(false);
    }

    @Transactional
    public int revokeAllGrants(String assetType, String assetId) {
        List<AssetGrant> grants = grantRepository.findByAssetTypeAndAssetId(normalizeAssetType(assetType), assetId);
        if (!grants.isEmpty()) {
            grantRepository.deleteAll(grants);
        }
        return grants.size();
    }

    private PermissionResult checkScreen(
        String username,
        List<String> roles,
        String deptCode,
        String assetId,
        String userClassification,
        String assetClassification
    ) {
        if (hasAny(roles, SUPERUSER_ROLES)) {
            return PermissionResult.allowed("MANAGE", "superuser");
        }

        String classification = screenClassification(assetId, assetClassification);
        if (isPublic(classification)) {
            return PermissionResult.allowed("READ", "public");
        }

        List<AssetGrant> grants = grantRepository.findActiveGrantsForUser(
            SCREEN_ASSET_TYPE,
            assetId,
            username,
            safeRoles(roles),
            deptCode != null ? deptCode : "",
            Instant.now()
        );
        if (grants.isEmpty()) {
            return PermissionResult.denied();
        }

        String highestPermission = grants.stream()
            .map(AssetGrant::getPermission)
            .max(Comparator.comparingInt(p -> PERMISSION_RANK.getOrDefault(p, 0)))
            .orElse("READ");
        if (PERMISSION_RANK.getOrDefault(highestPermission, 0) >= PERMISSION_RANK.get("EDIT")) {
            return PermissionResult.allowed(highestPermission, "explicit_grant");
        }
        if (isClassificationAllowed(userClassification, classification)) {
            return PermissionResult.allowed(highestPermission, "explicit_grant");
        }
        boolean hasLevelOverride = grants.stream()
            .anyMatch(g -> "READ".equalsIgnoreCase(g.getPermission()) && Boolean.TRUE.equals(g.isLevelOverride()));
        if (hasLevelOverride) {
            return PermissionResult.allowed("READ", "level_override");
        }
        return PermissionResult.denied("classification_denied");
    }

    private AccessibleAssetsResult listAccessibleScreenIds(
        String username,
        List<String> roles,
        String deptCode,
        Pageable pageable,
        String userClassification
    ) {
        if (hasAny(roles, SUPERUSER_ROLES)) {
            return AccessibleAssetsResult.all();
        }

        Set<String> assetIds = new LinkedHashSet<>();
        if (reportLinkRepository != null) {
            for (BiReportLink link : reportLinkRepository.findEnabledScreensForPermission()) {
                String id = screenAssetIdFromCode(link.getCode());
                if (id == null) {
                    continue;
                }
                if (isPublic(link.getClassification())) {
                    assetIds.add(id);
                }
            }
        }

        List<String> grantedIds = grantRepository.findAccessibleAssetIdsByGrant(
            SCREEN_ASSET_TYPE,
            username,
            safeRoles(roles),
            deptCode != null ? deptCode : "",
            Instant.now()
        );
        for (String id : grantedIds) {
            PermissionResult result = checkScreen(username, roles, deptCode, id, userClassification, null);
            if (result.allowed()) {
                assetIds.add(id);
            }
        }

        List<String> resultList = new ArrayList<>(assetIds);
        int total = resultList.size();
        int offset = (int) pageable.getOffset();
        int size = pageable.getPageSize();
        if (offset >= total) {
            return new AccessibleAssetsResult(List.of(), total, "FILTERED");
        }
        return new AccessibleAssetsResult(resultList.subList(offset, Math.min(offset + size, total)), total, "FILTERED");
    }

    private String screenClassification(String assetId, String fallbackClassification) {
        if (reportLinkRepository != null) {
            Optional<BiReportLink> link = reportLinkRepository.findEnabledScreenByCode(SCREEN_CODE_PREFIX + assetId);
            if (link.isPresent()) {
                String classification = trimToNull(link.orElseThrow().getClassification());
                if (classification != null) {
                    return classification;
                }
            }
        }
        String fallback = trimToNull(fallbackClassification);
        return fallback != null ? fallback : DEFAULT_SCREEN_CLASSIFICATION;
    }

    private boolean isClassificationAllowed(String callerLevel, String assetLevel) {
        int assetRank = classificationRank(assetLevel);
        if (assetRank <= 0) {
            return true;
        }
        int callerRank = classificationRank(callerLevel);
        if (callerRank < 0) {
            return false;
        }
        return callerRank >= assetRank;
    }

    private int classificationRank(String level) {
        String token = normalizeToken(level);
        if (token == null) {
            return -1;
        }
        return CLASSIFICATION_LADDER.indexOf(token);
    }

    private boolean isPublic(String classification) {
        return "PUBLIC".equalsIgnoreCase(trimToNull(classification));
    }

    private String screenAssetIdFromCode(String code) {
        String text = trimToNull(code);
        if (text == null || !text.toLowerCase(Locale.ROOT).startsWith(SCREEN_CODE_PREFIX)) {
            return null;
        }
        String id = text.substring(SCREEN_CODE_PREFIX.length()).trim();
        return id.isEmpty() ? null : id;
    }

    private String normalizeAssetType(String value) {
        return normalizeToken(value);
    }

    private String normalizePermission(String value) {
        String token = normalizeToken(value);
        if (token == null) {
            return null;
        }
        return switch (token) {
            case "OWNER", "MANAGER", "MANAGE" -> "MANAGE";
            case "VIEWER", "VIEW", "READ" -> "READ";
            case "EDIT" -> "EDIT";
            default -> null;
        };
    }

    private String normalizeAction(String value) {
        String token = normalizeToken(value);
        return token == null ? "READ" : token;
    }

    private String requiredPermission(String action) {
        if (action == null) {
            return null;
        }
        return switch (action) {
            case "READ", "VIEW", "PREVIEW" -> "READ";
            case "EDIT", "UPDATE" -> "EDIT";
            case "PUBLISH", "GRANT", "MANAGE", "ADMIN" -> "MANAGE";
            default -> null;
        };
    }

    private boolean permissionCovers(String grantedPermission, String requiredPermission) {
        return PERMISSION_RANK.getOrDefault(grantedPermission, 0) >= PERMISSION_RANK.getOrDefault(requiredPermission, 0);
    }

    private String classificationDecision(List<String> roles, String grantSource, String userClassification, String assetClassification) {
        if (hasAny(roles, SUPERUSER_ROLES)) {
            return "SUPERUSER";
        }
        String assetLevel = trimToNull(assetClassification);
        if (assetLevel == null) {
            return "MISSING_ASSET_CLASSIFICATION";
        }
        if ("level_override".equalsIgnoreCase(trimToNull(grantSource))) {
            return "OVERRIDDEN";
        }
        return isClassificationAllowed(userClassification, assetLevel) ? "ALLOWED" : "DENIED";
    }

    private String normalizeToken(String value) {
        String text = trimToNull(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String text = trimToNull(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private List<String> safeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of(NO_ROLE_PLACEHOLDER);
        }
        Set<String> expanded = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            String trimmed = role.trim();
            expanded.add(trimmed);
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("ROLE_") && trimmed.length() > 5) {
                expanded.add(trimmed.substring(5));
            } else {
                expanded.add("ROLE_" + trimmed);
            }
        }
        return expanded.isEmpty() ? List.of(NO_ROLE_PLACEHOLDER) : List.copyOf(expanded);
    }

    private static boolean hasAny(List<String> userRoles, Set<String> targetRoles) {
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }
        for (String role : userRoles) {
            if (targetRoles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    // --- Inner records ---

    public record PermissionResult(boolean allowed, String permission, String reason) {
        public static PermissionResult allowed(String permission, String reason) {
            return new PermissionResult(true, permission, reason);
        }
        public static PermissionResult denied() {
            return new PermissionResult(false, null, "denied");
        }
        public static PermissionResult denied(String reason) {
            return new PermissionResult(false, null, reason);
        }
    }

    public record AssetRef(String type, String id) {}

    public record PermissionCheckCommand(
        String username,
        List<String> userRoles,
        String userDeptCode,
        String userClassification,
        String assetType,
        String assetId,
        String assetKey,
        String action,
        String assetClassification
    ) {}

    public record PermissionDecision(
        boolean allowed,
        String permission,
        String reason,
        String requiredPermission,
        String action,
        String assetType,
        String assetId,
        String assetKey,
        String classificationDecision,
        String grantSource,
        String reasonCode,
        String reasonDetail,
        String suggestedRemediation,
        Instant deniedAt
    ) {
        public PermissionDecision(
            boolean allowed,
            String permission,
            String reason,
            String requiredPermission,
            String action,
            String assetType,
            String assetId,
            String assetKey,
            String classificationDecision,
            String grantSource
        ) {
            this(
                allowed,
                permission,
                reason,
                requiredPermission,
                action,
                assetType,
                assetId,
                assetKey,
                classificationDecision,
                grantSource,
                allowed ? "ALLOWED" : reasonCodeFor(reason),
                allowed ? reason : reasonDetailFor(reason, action, permission, requiredPermission, classificationDecision),
                allowed ? null : suggestedRemediationFor(reason),
                allowed ? null : Instant.now()
            );
        }

        public static PermissionDecision allowed(
            String assetType,
            String assetId,
            String assetKey,
            String action,
            String permission,
            String requiredPermission,
            String reason,
            String classificationDecision,
            String grantSource
        ) {
            return new PermissionDecision(
                true,
                permission,
                reason,
                requiredPermission,
                action,
                assetType,
                assetId,
                assetKey,
                classificationDecision,
                grantSource,
                "ALLOWED",
                reason,
                null,
                null
            );
        }

        public static PermissionDecision denied(
            String assetType,
            String assetId,
            String assetKey,
            String action,
            String permission,
            String requiredPermission,
            String reason,
            String classificationDecision,
            String grantSource
        ) {
            return denied(
                assetType,
                assetId,
                assetKey,
                action,
                permission,
                requiredPermission,
                reason,
                classificationDecision,
                grantSource,
                null,
                null
            );
        }

        public static PermissionDecision denied(
            String assetType,
            String assetId,
            String assetKey,
            String action,
            String permission,
            String requiredPermission,
            String reason,
            String classificationDecision,
            String grantSource,
            String reasonDetail,
            String suggestedRemediation
        ) {
            return new PermissionDecision(
                false,
                permission,
                reason,
                requiredPermission,
                action,
                assetType,
                assetId,
                assetKey,
                classificationDecision,
                grantSource,
                reasonCodeFor(reason),
                firstNonBlank(reasonDetail, reasonDetailFor(reason, action, permission, requiredPermission, classificationDecision)),
                firstNonBlank(suggestedRemediation, suggestedRemediationFor(reason)),
                Instant.now()
            );
        }

        private static String reasonCodeFor(String reason) {
            String normalized = reason == null ? "" : reason.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "request_required", "asset_required" -> "INVALID_REQUEST";
                case "unsupported_action" -> "UNSUPPORTED_ACTION";
                case "classification_required" -> "CLASSIFICATION_REQUIRED";
                case "classification_denied" -> "CLASSIFICATION_MISMATCH";
                case "insufficient_permission" -> "INSUFFICIENT_PERMISSION";
                case "denied", "missing_grant", "no_grant" -> "NO_GRANT";
                default -> "PERMISSION_DENIED";
            };
        }

        private static String reasonDetailFor(
            String reason,
            String action,
            String permission,
            String requiredPermission,
            String classificationDecision
        ) {
            String normalized = reason == null ? "" : reason.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "request_required" -> "Permission check request is required.";
                case "asset_required" -> "Asset type and asset id or key are required for the permission check.";
                case "unsupported_action" -> "Action " + firstNonBlank(action, "<unknown>") + " is not supported by the asset permission contract.";
                case "classification_required" -> "Asset classification is required before applying protected data access.";
                case "classification_denied" -> "User classification is lower than the asset classification.";
                case "insufficient_permission" -> "Action " + firstNonBlank(action, "<unknown>") + " requires " +
                firstNonBlank(requiredPermission, "<unknown>") + " but current permission is " + firstNonBlank(permission, "<none>") + ".";
                case "denied", "missing_grant", "no_grant" -> "No active grant covers this asset for the requested user, roles, or department.";
                default -> "Permission decision was denied by platform policy: " + firstNonBlank(reason, "unknown") +
                ", classification decision=" + firstNonBlank(classificationDecision, "NOT_APPLIED") + ".";
            };
        }

        private static String suggestedRemediationFor(String reason) {
            String normalized = reason == null ? "" : reason.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "request_required" -> "Retry with a complete permission check request.";
                case "asset_required" -> "Resolve the platform asset identity and retry with asset type plus id or key.";
                case "unsupported_action" -> "Use a supported action or extend the platform permission action matrix first.";
                case "classification_required" -> "Set the asset classification in platform governance metadata.";
                case "classification_denied" -> "Request a classification review or use a lower-sensitivity asset.";
                case "insufficient_permission" -> "Request or grant the required platform asset permission.";
                case "denied", "missing_grant", "no_grant" -> "Create or approve a platform asset grant for the target principal.";
                default -> "Review the platform asset permission policy and grant configuration.";
            };
        }
    }

    public record GrantCommand(
        String assetType,
        String assetId,
        String granteeType,
        String granteeId,
        String permission,
        Boolean levelOverride,
        Instant validFrom,
        Instant validTo,
        String grantReason,
        String grantedBy
    ) {}

    public record AccessibleAssetsResult(List<String> assetIds, long total, String scope) {
        public static AccessibleAssetsResult all() {
            return new AccessibleAssetsResult(List.of(), 0, "ALL");
        }
    }
}
