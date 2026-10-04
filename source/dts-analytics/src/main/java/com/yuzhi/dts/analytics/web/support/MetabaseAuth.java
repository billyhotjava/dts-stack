package com.yuzhi.dts.analytics.web.support;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class MetabaseAuth {

    private MetabaseAuth() {}

    public static final Set<String> DATA_ADMIN_ROLES = new HashSet<>(
            Arrays.asList("ROLE_OP_ADMIN", "ROLE_INST_DATA_OWNER", "ROLE_INST_LEADER"));

    /**
     * 大屏密级合规盘点（Sprint-24 F4）允许的角色集合。
     *
     * 与 DATA_ADMIN_ROLES 区分独立维护：DATA_ADMIN_ROLES 受 DatabaseResource 等数据
     * 治理端点共用，扩张其成员会同时放宽数据库管理权限。这里独立一组角色，明确仅
     * 用于大屏盘点这种"读未设密级清单"的合规场景。
     *
     * 包含部门级（DEPT_*）与所级（INST_*）的领导和数据管理员，以及运维管理员
     * （OP_ADMIN）。superuser 通过 AnalyticsUser.isSuperuser() 旁路放行。
     */
    public static final Set<String> SCREEN_AUDITOR_ROLES = new HashSet<>(
            Arrays.asList(
                    "ROLE_OP_ADMIN",
                    "ROLE_INST_DATA_OWNER",
                    "ROLE_DEPT_DATA_OWNER",
                    "ROLE_INST_LEADER",
                    "ROLE_DEPT_LEADER"));

    public static Optional<ResponseEntity<String>> requireUser(AnalyticsSessionService sessionService, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return Optional.of(ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated"));
        }
        return Optional.empty();
    }

    public static Optional<ResponseEntity<String>> requireSuperuser(AnalyticsSessionService sessionService, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return Optional.of(ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated"));
        }
        if (!user.orElseThrow().isSuperuser()) {
            return Optional.of(
                    ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that."));
        }
        return Optional.empty();
    }

    public static Optional<ResponseEntity<String>> requireDataAdmin(AnalyticsSessionService sessionService, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return Optional.of(ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated"));
        }
        if (user.orElseThrow().isSuperuser()) {
            return Optional.empty();
        }
        if (!isDataAdmin(request)) {
            return Optional.of(
                    ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that."));
        }
        return Optional.empty();
    }

    public static boolean isDataAdmin(HttpServletRequest request) {
        String rolesHeader = request.getHeader("X-DTS-Roles");
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return false;
        }
        for (String role : rolesHeader.split(",")) {
            if (DATA_ADMIN_ROLES.contains(role.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 大屏密级合规盘点鉴权：superuser 直放行；否则要求 X-DTS-Roles 命中
     * SCREEN_AUDITOR_ROLES（OP_ADMIN / 所级或部门数据管理员 / 所级或部门领导）。
     */
    public static Optional<ResponseEntity<String>> requireScreenAuditor(
            AnalyticsSessionService sessionService, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return Optional.of(ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated"));
        }
        if (user.orElseThrow().isSuperuser()) {
            return Optional.empty();
        }
        if (!isScreenAuditor(request)) {
            return Optional.of(
                    ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that."));
        }
        return Optional.empty();
    }

    public static boolean isScreenAuditor(HttpServletRequest request) {
        String rolesHeader = request.getHeader("X-DTS-Roles");
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return false;
        }
        for (String role : rolesHeader.split(",")) {
            if (SCREEN_AUDITOR_ROLES.contains(role.trim())) {
                return true;
            }
        }
        return false;
    }

    public static Optional<AnalyticsUser> currentUser(AnalyticsSessionService sessionService, HttpServletRequest request) {
        return sessionService.resolveUser(request);
    }

    public static Optional<Long> getUserId(AnalyticsSessionService sessionService, HttpServletRequest request) {
        return sessionService.resolveUser(request).map(AnalyticsUser::getId);
    }
}

