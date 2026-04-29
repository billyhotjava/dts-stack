package com.yuzhi.dts.platform.service.permission;

import com.yuzhi.dts.platform.domain.permission.AssetGrant;
import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.repository.permission.AssetGrantRepository;
import com.yuzhi.dts.platform.security.policy.PersonnelLevel;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Sprint-18 大屏密级与共享门禁。集中决定一个用户能不能查看 / 管理 / 共享指定大屏。
 *
 * 决策树（canView）：
 *   1. superAdmin / report.createdBy == caller / 持有 MANAGE grant → ALLOW
 *   2. institutePrivileged 角色 → ALLOW（机构特权保留语义，与既有 listPublished 一致）
 *   3. baseAccess = roleCodes / deptCodes 命中 OR 持有 VIEW grant
 *      若 baseAccess=false → DENY (DENY_NO_BASE_ACCESS)
 *   4. 用户人员密级允许 report.classification → ALLOW (BASE_ACCESS_PLUS_LEVEL)
 *   5. 否则若持有 level_override=true 的 VIEW grant → ALLOW (OVERRIDE_USED)
 *      否则 DENY (DENY_LEVEL_BLOCKED)
 *
 * canManage：owner / superAdmin / 持有 MANAGE grant。
 * canGrant：策略 1（不传递）—— 授 MANAGE 仅 owner / superAdmin；授 VIEW 任何 manager。
 * canRevoke：owner / superAdmin 撤一切；MANAGE 持有者只能撤 VIEW。
 */
@Service
public class DashboardAccessGuard {

    public static final String ASSET_TYPE = "DASHBOARD";
    public static final String PERM_VIEW = "VIEW";
    public static final String PERM_MANAGE = "MANAGE";
    public static final String GRANTEE_USER = "USER";

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
        if (caller.institutePrivileged()) {
            return AccessDecision.allow("INSTITUTE_PRIVILEGED");
        }

        boolean roleMatch = matchRole(report.getRoleCodes(), caller.roles());
        boolean deptMatch = matchDept(report.getDeptCodes(), caller.deptCode());
        boolean viewGrant = hasViewGrant(grants);
        boolean baseAccess = roleMatch || deptMatch || viewGrant;
        if (!baseAccess) {
            return AccessDecision.deny("DENY_NO_BASE_ACCESS");
        }

        if (hasLevelClearance(caller.level(), report.getClassification())) {
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
        return grantRepository.findActiveUserGrants(ASSET_TYPE, report.getCode(), caller.username(), Instant.now());
    }

    private boolean hasManageGrant(List<AssetGrant> grants) {
        return grants.stream().anyMatch(g -> PERM_MANAGE.equalsIgnoreCase(g.getPermission()) && g.isValid());
    }

    private boolean hasViewGrant(List<AssetGrant> grants) {
        return grants.stream().anyMatch(g -> PERM_VIEW.equalsIgnoreCase(g.getPermission()) && g.isValid());
    }

    private boolean hasOverrideViewGrant(List<AssetGrant> grants) {
        return grants
            .stream()
            .anyMatch(g -> PERM_VIEW.equalsIgnoreCase(g.getPermission()) && g.isValid() && g.isLevelOverride());
    }

    private boolean matchRole(String roleCodesCsv, Set<String> userRoles) {
        if (roleCodesCsv == null || roleCodesCsv.isBlank()) return false;
        if (userRoles == null || userRoles.isEmpty()) return false;
        Set<String> required = parseCsv(roleCodesCsv);
        if (required.isEmpty()) return false;
        for (String r : userRoles) {
            if (r != null && required.contains(r.trim().toUpperCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private boolean matchDept(String deptCodesCsv, String userDept) {
        if (deptCodesCsv == null || deptCodesCsv.isBlank()) return false;
        if (userDept == null || userDept.isBlank()) return false;
        Set<String> allowed = parseCsv(deptCodesCsv);
        return allowed.contains(userDept.trim().toUpperCase(Locale.ROOT));
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
     * Null personnelLevel 视同 GENERAL（最严）。
     */
    private boolean hasLevelClearance(PersonnelLevel level, String classification) {
        if (classification == null || classification.isBlank()) return true;
        PersonnelLevel effective = level != null ? level : PersonnelLevel.GENERAL;
        String normalized = classification.trim().toUpperCase(Locale.ROOT);
        return effective.allowedClassifications().contains(normalized);
    }

    // ---------------------------------------------------------------------
    // value types
    // ---------------------------------------------------------------------

    public record Caller(
        String username,
        PersonnelLevel level,
        Set<String> roles,
        String deptCode,
        boolean institutePrivileged,
        boolean superAdmin
    ) {
        public Caller {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }

        public static Caller of(String username, PersonnelLevel level, Set<String> roles, String deptCode) {
            return new Caller(username, level, roles, deptCode, false, false);
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
}
