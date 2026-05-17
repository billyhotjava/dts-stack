package com.yuzhi.dts.platform.service.permission;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.repository.permission.AssetGrantRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 大屏密级与共享门禁。集中决定一个用户能不能查看 / 管理 / 共享指定大屏。
 *
 * 决策树（canView）：
 *   1. superAdmin / report.createdBy == caller / 持有 MANAGE grant → ALLOW
 *   2. classification=PUBLIC → ALLOW
 *   3. roleOk = roleCodes 为空 OR roleCodes 命中 caller.roles
 *      deptOk = deptCodes 为空 OR caller.institutePrivileged OR deptCodes 命中 caller.deptCode
 *      baseAccess = ((配置了 role/dept 约束) AND roleOk AND deptOk) OR 持有 VIEW grant
 *      若 baseAccess=false → DENY (DENY_NO_BASE_ACCESS)
 *   4. 用户人员密级允许 report.classification → ALLOW (BASE_ACCESS_PLUS_LEVEL)
 *   5. 否则若持有 level_override=true 的 VIEW grant → ALLOW (OVERRIDE_USED)
 *      否则 DENY (DENY_LEVEL_BLOCKED)
 *
 * 重要：roleCodes/deptCodes 为 null/空字符串时视为"该维度不限制"，但只有公开大屏
 * 可以无共享全员可见；内部及以上密级仍必须有 role/dept 约束命中或显式 VIEW grant。
 * institutePrivileged 仅豁免 dept 维度，不豁免 role 维度。
 * 同步自 analytics 的 screen-* 大屏不再使用 roleCodes/deptCodes 做非公开放行；
 * 它们统一读取 platform asset_grant 的 SCREEN:{id} 授权。
 *
 * canManage：owner / superAdmin / 持有 MANAGE grant。
 * canGrant：策略 1（不传递）—— 授 MANAGE 仅 owner / superAdmin；授 VIEW 任何 manager。
 * canRevoke：owner / superAdmin 撤一切；MANAGE 持有者只能撤 VIEW。
 */
@Service
public class DashboardAccessGuard {

    public static final String ASSET_TYPE = "DASHBOARD";
    public static final String SCREEN_ASSET_TYPE = "SCREEN";
    public static final String PERM_VIEW = "VIEW";
    public static final String PERM_READ = "READ";
    public static final String PERM_MANAGE = "MANAGE";
    public static final String PERM_EDIT = "EDIT";
    public static final String GRANTEE_USER = "USER";
    private static final String SCREEN_CODE_PREFIX = "screen-";
    private static final String NO_ROLE_PLACEHOLDER = "__NO_ROLE__";

    private final AssetGrantRepository grantRepository;

    public DashboardAccessGuard(AssetGrantRepository grantRepository) {
        this.grantRepository = grantRepository;
    }

    public AccessDecision canView(BiReportLink report, Caller caller) {
        if (caller == null || report == null) {
            return AccessDecision.deny("DENY_INVALID_INPUT");
        }
        if (caller.superAdmin()) {
            return AccessDecision.allow("SUPER_ADMIN");
        }
        if (isOwner(report, caller)) {
            return AccessDecision.allow("OWNER");
        }

        List<AssetGrant> grants = activeUserGrants(report, caller);
        if (hasManageGrant(grants)) {
            return AccessDecision.allow("MANAGE_GRANT");
        }
        if (isPublicClassification(report.getClassification())) {
            return AccessDecision.allow("PUBLIC");
        }

        boolean roleOk = isBlank(report.getRoleCodes()) || matchRole(report.getRoleCodes(), caller.roles());
        boolean deptOk = isBlank(report.getDeptCodes())
            || caller.institutePrivileged()
            || matchDept(report.getDeptCodes(), caller.deptCode());
        boolean viewGrant = hasViewGrant(grants);
        boolean configuredBaseAccess = !isBlank(report.getRoleCodes()) || !isBlank(report.getDeptCodes());
        boolean baseAccess = isScreenReport(report)
            ? viewGrant
            : (configuredBaseAccess && roleOk && deptOk) || viewGrant;
        if (!baseAccess) {
            return AccessDecision.deny("DENY_NO_BASE_ACCESS");
        }

        if (hasLevelClearance(caller.allowedClassifications(), report.getClassification())) {
            return AccessDecision.allow("BASE_ACCESS_PLUS_LEVEL");
        }

        if (hasOverrideViewGrant(grants)) {
            return AccessDecision.allowOverride("OVERRIDE_USED");
        }
        return AccessDecision.deny("DENY_LEVEL_BLOCKED");
    }

    public boolean canManage(BiReportLink report, Caller caller) {
        if (caller == null || report == null) return false;
        if (caller.superAdmin()) return true;
        if (isOwner(report, caller)) return true;
        return hasManageGrant(activeUserGrants(report, caller));
    }

    /**
     * 策略 1：MANAGE 不再传递。授 MANAGE 仅 owner / superAdmin；授 VIEW 任何 manager。
     */
    public boolean canGrant(BiReportLink report, Caller caller, String targetPermission) {
        if (caller == null || report == null || targetPermission == null) return false;
        if (PERM_MANAGE.equalsIgnoreCase(targetPermission)) {
            return caller.superAdmin() || isOwner(report, caller);
        }
        return canManage(report, caller);
    }

    /**
     * owner / superAdmin 可撤销任意 grant；MANAGE 持有者只能撤 VIEW。
     */
    public boolean canRevoke(BiReportLink report, Caller caller, AssetGrant target) {
        if (caller == null || report == null || target == null) return false;
        if (caller.superAdmin()) return true;
        if (isOwner(report, caller)) return true;
        if (PERM_VIEW.equalsIgnoreCase(target.getPermission())) {
            return hasManageGrant(activeUserGrants(report, caller));
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private boolean isOwner(BiReportLink report, Caller caller) {
        String creator = report.getCreatedBy();
        return creator != null
            && !creator.isBlank()
            && caller.username() != null
            && creator.trim().equalsIgnoreCase(caller.username().trim());
    }

    private List<AssetGrant> activeUserGrants(BiReportLink report, Caller caller) {
        if (caller.username() == null || caller.username().isBlank()) return List.of();
        if (report.getCode() == null || report.getCode().isBlank()) return List.of();
        GrantAssetRef ref = grantAssetRef(report);
        if (SCREEN_ASSET_TYPE.equals(ref.assetType())) {
            return grantRepository.findActiveGrantsForUser(
                ref.assetType(),
                ref.assetId(),
                caller.username(),
                safeRoles(caller.roles()),
                caller.deptCode() == null ? "" : caller.deptCode(),
                Instant.now()
            );
        }
        return grantRepository.findActiveUserGrants(ref.assetType(), ref.assetId(), caller.username(), Instant.now());
    }

    private boolean hasManageGrant(List<AssetGrant> grants) {
        return grants.stream().anyMatch(g -> isManagePermission(g.getPermission()) && g.isValid());
    }

    private boolean hasViewGrant(List<AssetGrant> grants) {
        return grants.stream().anyMatch(g -> isViewPermission(g.getPermission()) && g.isValid());
    }

    private boolean hasOverrideViewGrant(List<AssetGrant> grants) {
        return grants
            .stream()
            .anyMatch(g -> isViewPermission(g.getPermission()) && g.isValid() && g.isLevelOverride());
    }

    private boolean isViewPermission(String permission) {
        return PERM_VIEW.equalsIgnoreCase(permission) || PERM_READ.equalsIgnoreCase(permission);
    }

    private boolean isManagePermission(String permission) {
        return PERM_MANAGE.equalsIgnoreCase(permission) || PERM_EDIT.equalsIgnoreCase(permission);
    }

    private GrantAssetRef grantAssetRef(BiReportLink report) {
        String screenId = screenId(report);
        if (screenId != null) {
            return new GrantAssetRef(SCREEN_ASSET_TYPE, screenId);
        }
        return new GrantAssetRef(ASSET_TYPE, report.getCode());
    }

    private boolean isScreenReport(BiReportLink report) {
        return screenId(report) != null;
    }

    private String screenId(BiReportLink report) {
        if (report == null) {
            return null;
        }
        String code = report.getCode();
        if (code != null && code.toLowerCase(Locale.ROOT).startsWith(SCREEN_CODE_PREFIX)) {
            String id = code.substring(SCREEN_CODE_PREFIX.length()).trim();
            return id.isEmpty() ? null : id;
        }
        String type = report.getReportType();
        if (type != null && "SCREEN".equalsIgnoreCase(type.trim()) && code != null && !code.isBlank()) {
            return code.trim();
        }
        return null;
    }

    private List<String> safeRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of(NO_ROLE_PLACEHOLDER);
        }
        Set<String> expanded = new java.util.LinkedHashSet<>();
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isPublicClassification(String classification) {
        return classification != null && "PUBLIC".equalsIgnoreCase(classification.trim());
    }

    /**
     * roleCodes 非空才进来：检查 caller.roles 是否命中其中任一。
     * 角色串都自动加上 ROLE_ 前缀（与 BiReportLinkService.normalizeRole 一致）。
     */
    private boolean matchRole(String roleCodesCsv, Set<String> userRoles) {
        if (userRoles == null || userRoles.isEmpty()) return false;
        Set<String> required = new HashSet<>();
        for (String token : roleCodesCsv.split("[,;\\s]")) {
            String normalized = normalizeRole(token);
            if (normalized != null) required.add(normalized);
        }
        if (required.isEmpty()) return false;
        for (String r : userRoles) {
            String normalized = normalizeRole(r);
            if (normalized != null && required.contains(normalized)) return true;
        }
        return false;
    }

    /**
     * deptCodes 非空才进来：检查 caller.deptCode 是否命中其中任一。
     */
    private boolean matchDept(String deptCodesCsv, String userDept) {
        if (userDept == null || userDept.isBlank()) return false;
        Set<String> allowed = parseCsv(deptCodesCsv);
        return allowed.contains(userDept.trim().toUpperCase(Locale.ROOT));
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) return null;
        String upper = role.trim().toUpperCase(Locale.ROOT);
        return upper.startsWith("ROLE_") ? upper : "ROLE_" + upper;
    }

    private Set<String> parseCsv(String csv) {
        Set<String> out = new HashSet<>();
        for (String token : csv.split("[,;\\s]")) {
            if (token != null && !token.isBlank()) {
                out.add(token.trim().toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    /**
     * Null classification 视同 PUBLIC（不限制）。
     * caller.allowedClassifications 由 ClassificationUtils.currentAllowedClassifications() 注入，
     * 已经融合 personnel_level claim → ROLE_xxx fallback 等三级解析，与原 canAccess 行为一致。
     */
    private boolean hasLevelClearance(Set<String> allowed, String classification) {
        if (classification == null || classification.isBlank()) return true;
        if (allowed == null || allowed.isEmpty()) return false;
        String normalized = classification.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(normalized);
    }

    // ---------------------------------------------------------------------
    // value types
    // ---------------------------------------------------------------------

    /**
     * Caller 把"用户的密级清单 + 角色 + 部门 + 特权位"打包成纯数据，让 Guard 与
     * SecurityContext 解耦、易于单元测试。
     *
     * @param allowedClassifications 用户允许访问的 classification 集合（由 ClassificationUtils
     *                               注入）。null/empty 视为最严，仅 PUBLIC 也得不到。
     */
    public record Caller(
        String username,
        Set<String> allowedClassifications,
        Set<String> roles,
        String deptCode,
        boolean institutePrivileged,
        boolean superAdmin
    ) {
        public Caller {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
            allowedClassifications = allowedClassifications == null
                ? Set.of()
                : Set.copyOf(allowedClassifications);
        }

        public static Caller of(String username, Set<String> allowedClassifications, Set<String> roles, String deptCode) {
            return new Caller(username, allowedClassifications, roles, deptCode, false, false);
        }
    }

    public record AccessDecision(boolean allow, String reason, boolean overrideUsed) {
        public static AccessDecision allow(String reason) {
            return new AccessDecision(true, reason, false);
        }

        public static AccessDecision allowOverride(String reason) {
            return new AccessDecision(true, reason, true);
        }

        public static AccessDecision deny(String reason) {
            return new AccessDecision(false, reason, false);
        }
    }

    private record GrantAssetRef(String assetType, String assetId) {}
}
